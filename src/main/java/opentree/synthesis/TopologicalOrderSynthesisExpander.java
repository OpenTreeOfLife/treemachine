package opentree.synthesis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * upon construction. This will run the entire analysis and populate the <tt>Iterable\<Relationship\></tt> object
 * that will be used to return the relationships afterward.
 * 
 * @author cody
 *
 */
public abstract class TopologicalOrderSynthesisExpander extends SynthesisExpander {

	/** all nodes in the graph in topological order */
	TopologicalOrder topologicalOrder;

	/** stores the rels to be included in the final synthetic tree, indexed by node id */
	Map<Long, HashSet<Relationship>> childRels = new HashMap<Long, HashSet<Relationship>>();;
	
	/** stores the node ids of all the descendants (not just terminals) in the synthesis tree for a given ancestor node's id */
	Map<Long, TLongBitArraySet> nodeMrcaTipsAndInternal = new HashMap<Long, TLongBitArraySet>();
	Map<Long, TLongBitArraySet> nodeMrcaTips = new HashMap<Long, TLongBitArraySet>();

	Set<Relationship> excludedRels;
	
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
	abstract Set<Relationship> breakCycles();
	
	/**
	 * This method must be implemented by each class extending the TopologicalOrderSynthesisExpander. It must accept a node, and
	 * return a list containing all the child rels of this node that should be included in the synthetic tree. Obviously, none of
	 * these should overlap!
	 * @param n
	 * @return
	 */
	abstract List<Relationship> selectRelsForNode(Node n);
	
	/**
	 * Initialize the topological order and run the synthesis procedure. This should be called in the constructor of each class
	 * that extends TopologicalOrderSynthesisExpander.
	 * @param root
	 */
	void synthesizeFrom(Node root) {
		G = new GraphDatabaseAgent(root.getGraphDatabase());
		excludedRels = breakCycles(); // find and flag rels to be excluded from the topological order
//		topologicalOrder = new TopologicalOrder(G, excludedRels, RelType.STREECHILDOF, RelType.TAXCHILDOF);
		topologicalOrder = new TopologicalOrder(root, excludedRels, RelType.STREECHILDOF, RelType.TAXCHILDOF);
		
		// now process all the nodes
		for (Node n : topologicalOrder) {
			recordRels(n, selectRelsForNode(n));
		}
	}
	
	/*
	 * Helper method that returns an iterable of all incoming STREECHILDOF and TAXCHILDOF rels at a given node.
	 * @param n
	 * @return
	 *
	Iterable<Relationship> getALLStreeAndTaxRels(Node n) {
		return n.getRelationships(Direction.INCOMING, RelType.STREECHILDOF, RelType.TAXCHILDOF);
	} */
	
	Iterable<Relationship> availableRelsForSynth(Node n, RelationshipType ... relTypes) {
		List<Relationship> rels = new ArrayList<Relationship>();
		for (Relationship r : n.getRelationships(Direction.INCOMING, relTypes)) {
			if (! excludedRels.contains(r)) {
				rels.add(r);
			}
		}
		return rels;
	}
	
	/**
	 * Return a list containing all the *graph tip nodes* (which will be terminal taxa if taxonomy is being used) that
	 * are descended from the child node of this relationship. This should be used for assessing taxonomic overlap among
	 * nodes.<br><br>
	 * 
	 * WARNING: this may not provide the expected results when taxonomy nodes are ancestors/descendants of a given
	 * node: we don't update mrca properties to contain taxonomy so it is possible for a node x to have descendant tips
	 * that are not in x.mrca!
	 * 
	 * @param rel
	 * @return
	 */
	long[] getMrcaProperty(Relationship rel) {
		return (long[]) rel.getStartNode().getProperty(NodeProperty.MRCA.propertyName);
	}

	/**
	 * Record the specified rels as the selected set for the given node.
	 * 
	 * @param n
	 * @param bestRelIds
	 */
	private void recordRels(Node n, Iterable<Relationship> bestRels) {
		TLongBitArraySet descendants = new TLongBitArraySet();
		TLongBitArraySet descendantTips = new TLongBitArraySet();
		HashSet<Relationship> incomingRels = new HashSet<Relationship>();

		print();
		
		for (Relationship r : bestRels) {
			long childId = r.getStartNode().getId();
			incomingRels.add(r);
			descendants.add(childId);
			descendants.addAll(nodeMrcaTipsAndInternal.get(childId));
			descendantTips.addAll(nodeMrcaTips.get(childId));
//			if (VERBOSE) { print("adding descendants of rel " + r + ": " + nodeMrcaTipsAndInternal.get(childId) + " to nodeMrca["+n.getId()+"]"); }
		}
		
		if (! n.hasRelationship(Direction.INCOMING, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {
			descendantTips.add(n.getId());
		}

		long nodeId = n.getId();
		descendants.add(nodeId);
		nodeMrcaTipsAndInternal.put(nodeId, descendants);
		nodeMrcaTips.put(nodeId, descendantTips);
		
		childRels.put(nodeId, incomingRels);
//		if (VERBOSE) {
//			print("nodeMrca["+n.getId()+"] = " + nodeMrcaTipsAndInternal.get(n.getId()));
//			print("childRels["+n.getId()+"] = " + childRels.get(n.getId()));
//		};
	}
	
	/*
	 * Get *all* the graph nodes--tips as well as internal--that are descended from this node in synthesis.
	 * @param rel
	 * @return
	 *
	Set<Long> mrcaTipsAndInternal(Iterable<Node> nodes) {
		HashSet<Long> included = new HashSet<Long>();
		for (Node n : nodes) { included.addAll(mrcaTipsAndInternal(n)); }
		return included;
	} */
	
	/**
	 * Get *all* the graph nodes--tips as well as internal--that are descended from this node in synthesis.
	 * @param rel
	 * @return
	 */
	Set<Long> mrcaTipsAndInternal(Iterable<Relationship> rels) {
		HashSet<Long> included = new HashSet<Long>();
		for (Relationship r : rels) { included.addAll(mrcaTipsAndInternal(r)); }
		return included;
	}

	/**
	 * Get *all* the graph nodes--tips as well as internal--that are descended from the start node of the 
	 * passed relId in the synthetic topology. <strong>Clarification:</strong> The argument should be a 
	 * neo4j relationship id.
	 * @param rel
	 * @return
	 */
	TLongBitArraySet mrcaTipsAndInternal(Relationship r) {
		return nodeMrcaTipsAndInternal.get(r.getStartNode().getId());
	}
	
	/**
	 * Get *all* the graph nodes--tips as well as internal--that are descended from the start node of the 
	 * passed relId in the synthetic topology. <strong>Clarification:</strong> The argument should be a 
	 * neo4j relationship id.
	 * @param rel
	 * @return
	 */
	TLongBitArraySet mrcaTips(Node n) {
		return nodeMrcaTips.get(n.getId());
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

		// testing
		System.out.println("looking for rels starting at: " + arg0.endNode());
		System.out.println(childRels.get(arg0.endNode().getId()));
		
		return childRels.get(arg0.endNode().getId());
	}

	@Override
	public PathExpander reverse() {
		throw new UnsupportedOperationException();
	}

}
