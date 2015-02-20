package opentree.synthesis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opentree.bitarray.CompactLongSet;
import org.opentree.bitarray.TLongBitArraySet;

import opentree.constants.RelType;
import opentree.synthesis.mwis.BaseWeightedIS;
import opentree.synthesis.mwis.BruteWeightedIS;
import opentree.synthesis.mwis.GreedyApproximateWeightedIS;
import opentree.synthesis.mwis.TopologicalOrder;
import opentree.synthesis.mwis.WeightedUndirectedGraph;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;
import org.opentree.graphdb.GraphDatabaseAgent;

import scala.actors.threadpool.Arrays;

public class RootwardSynthesisExpander extends SynthesisExpander implements PathExpander {

		private TopologicalOrder topologicalOrder;
	private Map<Long, HashSet<Relationship>> childRels;
	private Map<Long, TLongBitArraySet> nodeMrca;
	private GraphDatabaseAgent gdb;

	private boolean trivialTestCase = false;
	
	private boolean VERBOSE = true;
	
	public RootwardSynthesisExpander(Node root) {
		topologicalOrder = new TopologicalOrder(root, RelType.MRCACHILDOF);
		childRels = new HashMap<Long, HashSet<Relationship>>();

		// holds the ids of *all* the descendant nodes (not just terminals)
		nodeMrca = new HashMap<Long, TLongBitArraySet>();
		gdb = new GraphDatabaseAgent(root.getGraphDatabase());
		
		for (Node n : topologicalOrder) {
			
			if (VERBOSE) { System.out.println("\nvisiting node " + n);
			System.out.println("looking for best non-overlapping rels"); }

			// find the maximum-weight independent set of the incoming rels
			HashSet<Relationship> incomingRels = new HashSet<Relationship>();
			Iterable<Long> X = findBestNonOverlapping(getCandidateRelationships(n));

			if (VERBOSE) { System.out.println("selected relationships for " + n); }

			// record the rels that were identified as the best set
			TLongBitArraySet descendants = new TLongBitArraySet();
			for (Long relId : X) {

				Relationship r = n.getGraphDatabase().getRelationshipById(relId);
				long childId = r.getStartNode().getId();
				
				if (VERBOSE) { System.out.println("Relationship[" + relId + "] between " + r.getStartNode() + " and " + r.getEndNode()); }
				
				incomingRels.add(n.getGraphDatabase().getRelationshipById(relId));
				descendants.add(childId);
				descendants.addAll(nodeMrca.get(childId));
			}

			long nodeId = n.getId();
			descendants.add(nodeId);
			nodeMrca.put(nodeId, descendants); 

			childRels.put(nodeId, incomingRels); 
		}
	}
	
	public String getDescription() {
		return "rootward synthesis method";
	}

	public Iterable<Long> findBestNonOverlapping(Long[] relIds) {

		if (relIds.length < 1) {
			if (VERBOSE) { System.out.println("(no incoming rels)"); }
			return new ArrayList<Long>();
		}
		
		CompactLongSet[] mrcaSetsForRels = new CompactLongSet[relIds.length];
		double[] weights = new double[relIds.length];
		
		// TODO only remember the highest ranked relationship from each child node (all others are redundant)
		// TODO exclude taxonomy relationships -- these should be added at the end if they don't conflict with MWIS rels
		
		for (int i = 0; i < relIds.length; i++) {
			Long childNodeId = getStartNodeId(relIds[i]);
			mrcaSetsForRels[i] = new CompactLongSet();
			System.out.println("Relationship[" + relIds[i] + "]: " + nodeMrca.get(childNodeId));
			mrcaSetsForRels[i].addAll(nodeMrca.get(childNodeId));

			// get the score
			Node childNode = gdb.getRelationshipById(relIds[i]).getStartNode();
			weights[i] = getRelScore(relIds[i]);
		}
		
		Iterable<Long> S = null;
		if (relIds.length <= BruteWeightedIS.MAX_TRACTABLE_N) {
			S = new BruteWeightedIS(relIds, weights, mrcaSetsForRels);
		} else {
			S = new GreedyApproximateWeightedIS(relIds, weights, mrcaSetsForRels);
		}

		List<Long> bestSet = new ArrayList<Long>();
		for (Long id : S) { bestSet.add(id); }

		// TODO add the taxonomy rels here that don't conflict
		
		return bestSet;
	}

	/**
	 * This score is the weight for the MWIS. Right now we are using a very simple one based on 
	 * the number of descendants and the rank, but Joseph is working on a better one.
	 * @param node
	 * @return
	 */
	private double getRelScore(long relId) {
		Relationship rel = gdb.getRelationshipById(relId);
		return nodeMrca.get(rel.getStartNode().getId()).cardinality() * (1.0 / ((int) rel.getProperty("sourcerank")));
	}
		
	private static Long[] getCandidateRelationships(Node n) {
		
		// TODO should do this in a smarter way so we can store the taxonomy rels and loop over them later
		// instead of having to loop over everything again later to find them
		
		List<Long> c = new ArrayList<Long>();
		Iterable<Relationship> R = n.getRelationships(RelType.STREECHILDOF, Direction.INCOMING);
		for (Relationship r : R) {
			if (r.hasProperty("sourcerank")) { c.add(r.getId()); } // only taxonomy streechildofs don't have this property
		}
		return c.toArray(new Long[c.size()]);
	}
	
	/**
	 * Just a very simple helper function to improve code clarity.
	 * @param relId
	 * @return
	 */
	private long getStartNodeId(Long relId) {
		return gdb.getRelationshipById(relId).getStartNode().getId();
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

	/* THIS SECTION IN STASIS. The plan is to use the WeightedDirectedGraph class with
	 * an optimized exact solution to the MWIS. Not there yet though. **/
	public Iterable<Long> findBestNonOverlappingGraph(Long[] relIds) {

		if (trivialTestCase) {
			return Arrays.asList(relIds);
		}
		
		// vertices in this graph represent candidate relationships
		// edges represent overlap in the mrca sets between pairs of candidate relationships
		WeightedUndirectedGraph G = new WeightedUndirectedGraph();

		for (long relId : relIds) {
			G.addNode(relId, getWeight(relId));
		}
		
		// attach this candidate rel to any others with overlapping descendant sets
		for (int i = 0; i < relIds.length; i++) {
			long a = getStartNodeId(relIds[i]);
			for (int j = i+1; j < relIds.length; j++) {
				long b = getStartNodeId(relIds[j]);
				if (nodeMrca.get(a).containsAny(nodeMrca.get(b))) {
					G.getNode(a).attachTo(b);
				}
			}
		}

		// find a set S' of vertices such that the total weight of S' is maximized, and no two vertices in S' share an edge
		// this corresponds to a set of non-overlapping candidate relationships with maximum weight
//		return new LPWeightedIS(G);
		return null; // garbage...
	}
	
	/** simple weight based only on the size of the mrca set of the node */
	private int getWeight(long id) {
		TLongBitArraySet m = nodeMrca.get(id);
		if (m == null) {
			return 1;
		} else {
			return m.size();
		}
	}

	// =============== END STASIS ===================
}
