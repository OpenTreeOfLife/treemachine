package opentree.synthesis;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.synthesis.mwis.TopologicalOrder;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.kernel.Traversal;
import org.opentree.bitarray.TLongBitArraySet;
import org.opentree.graphdb.GraphDatabaseAgent;

/**
 * A non-instantiable base class that is inherited by the topological ordered synthesis expander classes.
 * 
 * @author cody
 *
 */
public class RootwardSynthesisExpander extends SynthesisExpander {

	TopologicalOrder topologicalOrder;
	Map<Long, HashSet<Relationship>> childRels;
	Map<Long, TLongBitArraySet> nodeMrca;
	GraphDatabaseAgent G;

	/**
	 * Helper method that returns an iterable of all incoming STREECHILDOF and TAXCHILDOF rels at a given node.
	 * @param n
	 * @return
	 */
	Iterable<Relationship> getALLStreeAndTaxRels(Node n) {
		return Traversal.description()
		.relationships(RelType.STREECHILDOF, Direction.INCOMING)
		.relationships(RelType.TAXCHILDOF, Direction.INCOMING)
		.evaluator(Evaluators.toDepth(1))
		.breadthFirst().traverse(n).relationships();
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
	long[] mrcaTips(Relationship rel) {
		return (long[]) rel.getStartNode().getProperty(NodeProperty.MRCA.propertyName);
	}

	/**
	 * Record the specified rels as the selected set for the given node.
	 * 
	 * @param n
	 * @param bestRelIds
	 */
	void recordRels(Node n, Iterable<Relationship> bestRels) {
		TLongBitArraySet descendants = new TLongBitArraySet();
		HashSet<Relationship> incomingRels = new HashSet<Relationship>();

		for (Relationship r : bestRels) {

//			Relationship r = n.getGraphDatabase().getRelationshipById(relId);
			long childId = r.getStartNode().getId();
		
//			incomingRels.add(n.getGraphDatabase().getRelationshipById(relId));
			incomingRels.add(r);
			descendants.add(childId);
			descendants.addAll(nodeMrca.get(childId));
			System.out.println("adding descendants of child " + childId + ": "+nodeMrca.get(childId)+" to nodeMrca["+n.getId()+"]");
		}

		long nodeId = n.getId();
		descendants.add(nodeId);
		nodeMrca.put(nodeId, descendants);
		
		System.out.println("nodeMrca["+n.getId()+"] = " + nodeMrca.get(n.getId()));
		
		childRels.put(nodeId, incomingRels);
		System.out.println("childRels["+n.getId()+"] = " + childRels.get(n.getId()));
	}
	
	/**
	 * Get *all* the graph nodes--tips as well as internal--that are descended from this node in synthesis.
	 * @param rel
	 * @return
	 */
	Set<Long> mrcaTipsAndInternal(Iterable<Long> relIds) {
		HashSet<Long> included = new HashSet<Long>();
		for (Long relId : relIds) { included.addAll(mrcaTipsAndInternal(relId)); }
		return included;
	}	

	/**
	 * Get *all* the graph nodes--tips as well as internal--that are descended from this node in synthesis.
	 * @param rel
	 * @return
	 */
	TLongBitArraySet mrcaTipsAndInternal(Long relId) {
		return nodeMrca.get(G.getRelationshipById(relId).getStartNode().getId());
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
