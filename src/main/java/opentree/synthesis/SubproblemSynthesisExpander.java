package opentree.synthesis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import static org.opentree.utils.GeneralUtils.print;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.kernel.Traversal;
import org.neo4j.kernel.Uniqueness;
import org.opentree.graphdb.GraphDatabaseAgent;

public class SubproblemSynthesisExpander extends SynthesisExpander {
	
	private final Set<Comparable<?>> subproblemIds;
//	private final TopologicalOrderSynthesisExpander subExpander;
	private final Node root;
	private final GraphDatabaseAgent G;

	// synchronized collections updated during concurrent/parallel subproblem processing
	private final Map<Object, Set<Object>> subproblemTree = Collections.synchronizedMap(new HashMap<Object, Set<Object>>());
	private final Map<Long, SynthesisSubtreeInfo> preservedSubtrees = Collections.synchronizedMap(new HashMap<Long, SynthesisSubtreeInfo>());
	private final Set<Object> finishedSubproblemIds = Collections.synchronizedSet(new HashSet<Object>());
	private final Map<Long, Set<Relationship>> childRels = Collections.synchronizedMap(new TreeMap<Long, Set<Relationship>>());
	
	private boolean VERBOSE = true;
	
	public SubproblemSynthesisExpander(Node root) throws InstantiationException, IllegalAccessException, IOException {
		
//		this.subExpander = subExpander;
		this.root = root;
		this.G = new GraphDatabaseAgent(root.getGraphDatabase());

		// first get all the subproblem ids from the index
		// TODO: should put the node indexes into an enum to keep them consistent and easily tracked
		Index<Node> subproblemIndex = new GraphDatabaseAgent(root.getGraphDatabase()).getNodeIndex("subproblemRoots", "type", "exact", "to_lower_case", "true");
		subproblemIds = new TreeSet<Comparable<?>>();
		for (Node n : subproblemIndex.query(new MatchAllDocsQuery())) {
			subproblemIds.add((String) n.getProperty(NodeProperty.TAX_UID.propertyName));
		}
		if (VERBOSE) { print("found", subproblemIds.size(), "subproblems:", subproblemIds); }
		
		collectSubproblemTree(root);
		
		// define the topological order that will sort the subproblems
		TopologicalOrder orderedSubproblems = new TopologicalOrder(root, RelType.TAXCHILDOF)
			.validateWith(new Predicate<Node> () {
				@Override
				public boolean test(Node n) {
					return n.equals(SubproblemSynthesisExpander.this.root) ||
						   subproblemIds.contains(n.getProperty(NodeProperty.TAX_UID.propertyName, null));
				}
				@Override
				public String toString() {
					return "validator: the node must be either (1) a taxon node recorded in the 'subproblemRoots' index (i.e. it is an uncontested taxon), or (2) the synthesis root.";
				}
		});
		
		// gather the subproblems and put them in a list so they will be processed in topological order
		print("attempting topo sort on subproblems");
		List<Node> subproblems = Collections.synchronizedList(new ArrayList<Node>());
		for (Node s : orderedSubproblems) {
			subproblems.add(s);
		}
		
		// process the list until we finish all the subproblems
		Set<Node> clearedProblems = Collections.synchronizedSet(new HashSet<Node>());
		while (! subproblems.isEmpty()) {
			subproblems.forEach(s -> {
				
				// only proceed with this subproblem if all its child subproblems have been finished
				// otherwise we will try again on the next loop over the list
				for (Object childId : childSubproblemIds(s)) {
					if (! finishedSubproblemIds.contains(childId)) { return; }
				}
			
				// do synthesis
				if (VERBOSE) { print("\n***** starting subproblem", s.getProperty(NodeProperty.NAME.propertyName) + ", ott id=" + s.getProperty(NodeProperty.TAX_UID.propertyName), "\n"); System.out.print("child subproblem ott ids: "); boolean first = true; for (Object cid : childSubproblemIds(s)) { String l; if (first) {l=""; first=false;} else {l=", ";} System.out.print(l + cid); } print(); }
				SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds subExpander = new SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds(preservedSubtrees);
				Transaction tx = G.beginTx();
				try {
					subExpander.synthesizeFrom(s);
					tx.success();
				} finally {
					tx.finish();
				}
				
				// record results
				childRels.putAll(subExpander.childRels);
				preservedSubtrees.put(s.getId(), subExpander.completedRootInfo());
				finishedSubproblemIds.add(s.getProperty(NodeProperty.TAX_UID.propertyName));
				clearedProblems.add(s);
			});
			
			// remove all the subproblems we just finished from the list
			Iterator<Node> subproblemIter = subproblems.iterator();
			while (subproblemIter.hasNext()) {
				Node n = subproblemIter.next();
				if (clearedProblems.contains(n)) {
					subproblemIter.remove();
				}
			}
		}
	}
	
	private Iterable<Object> childSubproblemIds(Node parent) {
		Object pid = parent.getProperty(NodeProperty.TAX_UID.propertyName);
		assert subproblemTree.containsKey(pid);
		return new Iterable<Object>() {
			@Override
			public Iterator<Object> iterator() {
				return new TreeChildIterator(pid);
			}
		};
	}
	
	private class TreeChildIterator implements Iterator<Object> {

		Stack<Object> childIds = new Stack<Object>();
		
		public TreeChildIterator(Object parentId) {
			loadChildren(parentId);
		}
		
		public boolean hasNext() {
			return ! childIds.isEmpty();
		}
		
		public Object next() {
			Object child = childIds.pop();
			loadChildren(child);
			return child;
		}
		
		private void loadChildren(Object pid) {
			for (Object c : subproblemTree.get(pid)) { childIds.push(c); }
		}
	}
	
	private void collectSubproblemTree(Node parent) {
		Object pid = parent.getProperty(NodeProperty.TAX_UID.propertyName);
		subproblemTree.put(pid, Collections.synchronizedSet(new HashSet<Object>()));
		for (Node child : Traversal.description()
				.breadthFirst()
				.relationships(RelType.TAXCHILDOF, Direction.INCOMING)
				.evaluator(new Evaluator() {
					@Override
					public Evaluation evaluate(Path path) {
						Node n = path.endNode();
//						print("checking", n);
						String ottId = (String) n.getProperty(NodeProperty.TAX_UID.propertyName, null);
						if (n.equals(parent) || ottId == null || ! subproblemIds.contains(ottId)) {
//							print("excluding and continuing");
							return Evaluation.EXCLUDE_AND_CONTINUE;
						} else {
//							print("including and pruning");
							return Evaluation.INCLUDE_AND_PRUNE;
						}
					}})
				.traverse(parent).nodes()) {
			subproblemTree.get(pid).add(child.getProperty(NodeProperty.TAX_UID.propertyName));
			collectSubproblemTree(child);
		}
	}
	
	@Override
	public Iterable expand(Path path, BranchState state) {
		return childRels.get(path.endNode().getId());
	}
}
