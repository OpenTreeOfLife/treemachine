package opentree.synthesis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import static org.opentree.utils.GeneralUtils.print;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.traversal.BranchState;
import org.opentree.graphdb.GraphDatabaseAgent;

public class SubproblemSynthesisExpander extends SynthesisExpander {
	
//	private final Index<Node> subproblemIndex;
	private final Set<Comparable> subproblemIds;
	private final TopologicalOrderSynthesisExpander subExpander;
	private final Node root;
	private final GraphDatabaseAgent G;
	private Map<Long, Set<Relationship>> childRels = new TreeMap<Long, Set<Relationship>>();
	
	private boolean VERBOSE = true;
	
	public SubproblemSynthesisExpander(SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds subExpander, Node root) throws InstantiationException, IllegalAccessException, IOException {
		
		this.subExpander = subExpander;
		this.root = root;
		this.G = new GraphDatabaseAgent(root.getGraphDatabase());

		// first get all the subproblem ids from the index
		// TODO: should put the node indexes into an enum to keep them consistent and easily tracked
		Index<Node> subproblemIndex = new GraphDatabaseAgent(root.getGraphDatabase()).getNodeIndex("subproblemRoots", "type", "exact", "to_lower_case", "true");
		subproblemIds = new TreeSet<Comparable>();
		for (Node n : subproblemIndex.query(new MatchAllDocsQuery())) {
			subproblemIds.add((String) n.getProperty(NodeProperty.TAX_UID.propertyName));
		}
		if (VERBOSE) { print("found", subproblemIds.size(), "subproblems:", subproblemIds); }
		
		TopologicalOrder orderedSubproblems = new TopologicalOrder(root, RelType.TAXCHILDOF)
			.validateWith(new Predicate<Node> () {
				@Override
				public boolean test(Node n) {
					return isSubproblemRoot(n);
				}
				@Override
				public String toString() {
					return "validator: the node must be a taxon node recorded in the 'subproblemRoots' index (i.e. it is an uncontested taxon).";
				}
		});
		
		Map<Long, SynthesisSubtreeInfo> preservedSubtrees = new TreeMap<Long, SynthesisSubtreeInfo>();
		print("attempting topo sort on subproblems");
		for (Node s : orderedSubproblems) {

//			System.err.println("about to begin subproblem " + s.getProperty(NodeProperty.NAME.propertyName) + ". press enter to continue");
//			System.in.read();

			if (VERBOSE) { print("\n***** starting subproblem", s.getProperty(NodeProperty.NAME.propertyName) + ", ott id=" + s.getProperty(NodeProperty.TAX_UID.propertyName, "\n")); }
			Transaction tx = G.beginTx();
			try {
				subExpander.synthesizeFrom(s);
				tx.success();
			} finally {
				tx.finish();
			}
			childRels.putAll(subExpander.childRels);
			preservedSubtrees.put(s.getId(), subExpander.completedRootInfo());
			
//			System.err.println("subproblem " + s.getProperty(NodeProperty.NAME.propertyName) + " completed. press enter to clear data and continue");
//			System.in.read();

			subExpander.reset(preservedSubtrees);
		}
	}
	
	private boolean isSubproblemRoot(Node n) {
//		print("attempting to validate TEST", n);
		boolean passes = n.equals(root);
		if (! passes) {
/*			Object ottId = n.getProperty(NodeProperty.TAX_UID.propertyName, null);
//			print("ott id = " + ottId);
			passes = ottId == null ? false : subproblemIndex.get("subset", ottId).hasNext(); */
			return subproblemIds.contains((String) n.getProperty(NodeProperty.TAX_UID.propertyName, null));
		}
//		print(passes ? "validated" + n : "did not validate!");
		return passes;
	}
	
	@Override
	public Iterable expand(Path path, BranchState state) {
		return childRels.get(path.endNode().getId());
	}
}
