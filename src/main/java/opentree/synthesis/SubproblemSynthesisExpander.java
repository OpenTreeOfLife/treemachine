package opentree.synthesis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import static org.opentree.utils.GeneralUtils.print;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.traversal.BranchState;
import org.opentree.graphdb.GraphDatabaseAgent;

public class SubproblemSynthesisExpander extends SynthesisExpander {
	
	private final Index<Node> subproblemIndex;
	private final TopologicalOrderSynthesisExpander subExpander;
	private final Node root;
	private final GraphDatabaseAgent G;
	private Map<Long, Set<Relationship>> childRels = new TreeMap<Long, Set<Relationship>>();
	
	private boolean VERBOSE = true;
	
	public SubproblemSynthesisExpander(SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds subExpander, Node root) throws InstantiationException, IllegalAccessException, IOException {
		
		this.subExpander = subExpander;
		this.root = root;
		this.G = new GraphDatabaseAgent(root.getGraphDatabase());

		// first, we want just the subproblem root nodes in topological order
		subproblemIndex = new GraphDatabaseAgent(root.getGraphDatabase()).getNodeIndex("subproblemRoots", "type", "exact", "to_lower_case", "true");
		// TODO: should put the node indexes into an enum to keep them consistent and easily tracked
		
		TopologicalOrder subproblemRoots = new TopologicalOrder(root, RelType.TAXCHILDOF)
			.validateWith(new Predicate<Node> () {
				@Override
				public boolean test(Node n) {
					return isSubproblemRoot(n);
				}
		});
		
		Map<Long, SynthesisSubtreeInfo> preservedSubtrees = new TreeMap<Long, SynthesisSubtreeInfo>();
		
		for (Node s : subproblemRoots) {

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
		boolean passes = n.equals(root);
		if (! passes) {
			Object ottId = n.getProperty(NodeProperty.TAX_UID.propertyName, null);
			passes = ottId == null ? false : subproblemIndex.get("subset", ottId).hasNext();
		}
		return passes;
	}
	
	@Override
	public Iterable expand(Path path, BranchState state) {
		return childRels.get(path.endNode().getId());
	}
}
