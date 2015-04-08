package opentree.synthesis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import static org.opentree.utils.GeneralUtils.print;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.kernel.Traversal;
import org.opentree.bitarray.TLongBitArraySet;
import org.opentree.graphdb.GraphDatabaseAgent;

/**
 * A non-instantiable base class that is inherited by the topological ordered synthesis expander classes.<br><br>
 * 
 * IMPORTANT: All classes that extend this abstract class should call the <tt>synthesizeFrom()</tt> method immediately
 * upon construction. This will run the entire analysis and populate the <tt>Iterable&ltRelationship&gt</tt> object
 * that will be used to return the relationships afterward.
 * 
 * @author cody
 *
 */
public abstract class TopologicalOrderSynthesisExpander extends SynthesisExpander {

	/** all nodes in the graph in topological order */
	TopologicalOrder topologicalOrder;

	/** stores the rels to be included in the final synthetic tree, indexed by node id */
	Map<Long, Set<Relationship>> childRels = new TreeMap<Long, Set<Relationship>>();;
	
//	Set<Relationship> excludedRels;
	
	/** the graph */
	GraphDatabaseAgent G;
	
	/** the root node for synthesis */
	Node root;
	
	/** whether or not to print verbose error messages */
	boolean VERBOSE;

	/**
	 * The topological sort will fail if there are cycles in the graph. Thus, cycles must be identified and broken before the
	 * topological sort can be performed. However, the optimal method for breaking cycles may depend on the synthesis criteria,
	 * so this method must be implemented individually by each class that extends the abstract TopologicalOrderSynthesisExpander.
	 * If a given implementation of this method is ineffective and does not remove all cycles, then the topological sort should
	 * throw an IllegalArgumentException on the first cycle it encounters.
	 * @return 
	 */
//	abstract Set<Relationship> breakCycles();
	
	/**
	 * This method must be implemented by each class extending the TopologicalOrderSynthesisExpander. It must accept a node, and
	 * return a list containing all the child rels of this node that should be included in the synthetic tree. Obviously, none of
	 * these should overlap!
	 * @param n
	 * @return
	 */
	abstract List<Relationship> selectRelsForNode(Node n);
	
	/*
	 * A bit hacky. Allows us to clear out the data for finished subproblems, but keep their root nodes so that we can attach
	 * those where appropriate inside of higher order subproblems.
	 * @param subtreesToKeep
	 * @return 
	 *
	abstract TopologicalOrderSynthesisExpander reset(Object subtreesToKeep); */
	
	abstract SynthesisSubtreeInfo completedRootInfo();
	
	/**
	 * Initialize the topological order and run the synthesis procedure. This should be called in the constructor of each class
	 * that extends TopologicalOrderSynthesisExpander.
	 * @param root
	 * @return 
	 */
	public SynthesisExpander synthesizeFrom(Node root) {
		this.root = root;
		G = new GraphDatabaseAgent(root.getGraphDatabase());
		if (VERBOSE) { print("will only visit those nodes in the subgraph below", root + ". collecting them now..."); }

		// turned off excluded rels because subproblems + node validation seems like it should obviate the need
//		excludedRels = breakCycles(); // find and flag rels to be excluded from the topological order
//		topologicalOrder = new TopologicalOrder(root, excludedRels, RelType.STREECHILDOF, RelType.TAXCHILDOF);
		
		topologicalOrder = new TopologicalOrder(root, RelType.STREECHILDOF, RelType.TAXCHILDOF).validateWith(new Predicate<Node> () {
			@Override
			public boolean test(Node n) {
				return ! synthesisCompleted(n);
			}
			@Override
			public String toString() {
				return "validator: must not have already been visited during synthesis. If a node fails this check, there may be a problem with the subproblems, or with the topological sort of the subproblem roots.";
			}
		});
		
		// now process all the nodes
		for (Node n : topologicalOrder) {
			recordCompleted(n, selectRelsForNode(n));
		}
		
		return this;
	}
	
	Iterable<Relationship> availableRelsForSynth(Node n, RelationshipType ... relTypes) {
		List<Relationship> rels = new ArrayList<Relationship>();
		for (Relationship r : n.getRelationships(Direction.INCOMING, relTypes)) {
//			if (! excludedRels.contains(r)) {
				rels.add(r);
//			}
		}
		return rels;
	}
	
	/**
	 * Whether or not the node has been completed. Currently this will just support a single synthesis tree. This would need
	 * to change to use a String[] property on nodes that would contain ids for all the synthesis trees for which this node
	 * has been completed. NOTE: the ids in the String[] array should be keep sorted so Arrays.binarySearch() can be used
	 * to locate specific ids.
	 * @param n
	 * @return
	 */
	private static boolean synthesisCompleted(Node n) {
		return n.hasProperty(NodeProperty.SYNTHESIZED.propertyName) && (boolean) n.getProperty(NodeProperty.SYNTHESIZED.propertyName);
	}
	
	/**
	 * Record the specified rels as the selected set for the given node.
	 * 
	 * @param n
	 * @param bestRelIds
	 */
	private void recordCompleted(Node n, Iterable<Relationship> bestRels) {

		HashSet<Relationship> incomingRels = new HashSet<Relationship>();
		for (Relationship r : bestRels) { incomingRels.add(r); }

		childRels.put(n.getId(), incomingRels);

		// TODO: to support multiple synthesis trees, this would need to change to use String[] property
		// that contains the ids of all synth trees for which this node has been completed. Best keep it sorted
		// to enable binary search for completed trees.
		n.setProperty(NodeProperty.SYNTHESIZED.propertyName, true);
	}
	
	/**
	 * Just a very simple helper function to improve code clarity.
	 * @param relId
	 * @return
	 */
	long getStartNodeId(Long relId) {
		return G.getRelationshipById(relId).getStartNode().getId();
	}
	
	@Override
	public Iterable<Relationship> expand(Path arg0, BranchState arg1) {

/*		// testing
		System.out.println("looking for rels starting at: " + arg0.endNode());
		System.out.println(childRels.get(arg0.endNode().getId())); */
		
		return childRels.get(arg0.endNode().getId());
	}

	@Override
	public PathExpander reverse() {
		throw new UnsupportedOperationException();
	}

}
