package opentree.synthesis;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.traversal.BranchState;
import org.opentree.graphdb.GraphDatabaseAgent;

public class SubproblemSynthesisExpander extends SynthesisExpander {
	
	private final Index<Node> subproblemIndex;
	private final TopologicalOrderSynthesisExpander subExpander;
	private final Node root;
	private Map<Long, Set<Relationship>> childRels = new TreeMap<Long, Set<Relationship>>();
	
	public <T extends TopologicalOrderSynthesisExpander> SubproblemSynthesisExpander(T subExpander, Node root) throws InstantiationException, IllegalAccessException {
		
		this.subExpander = subExpander;
		this.root = root;

		// first, we want just the subproblem root nodes in topological order
		subproblemIndex = new GraphDatabaseAgent(root.getGraphDatabase()).getNodeIndex("subproblemRoots", "type", "exact", "to_lower_case", "true");
		// TODO: should put the node indexes into an enum to keep them consistent and easily tracked
		
		TopologicalOrder subproblemRoots = new TopologicalOrder(root, RelType.STREECHILDOF, RelType.TAXCHILDOF)
			.validateWith(new Predicate<Node> () {
				@Override
				public boolean test(Node n) {
					return isSubproblemRoot(n);
				}
		});
		
		for (Node s : subproblemRoots) {
			subExpander.synthesizeFrom(s);
			childRels.putAll(subExpander.childRels);
			subExpander.clear();
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
