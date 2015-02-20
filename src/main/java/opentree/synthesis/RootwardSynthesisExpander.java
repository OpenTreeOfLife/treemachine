package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opentree.bitarray.CompactLongSet;
import org.opentree.bitarray.TLongBitArraySet;

import opentree.constants.NodeProperty;
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
			
			// collect the relationships
			Map<Node, Relationship> treeRels = new HashMap<Node, Relationship>();
			List<Relationship> taxonomyOnlyRels = new ArrayList<Relationship>();
			List<Relationship> singletons = new ArrayList<Relationship>();
			for (Relationship r : n.getRelationships(RelType.STREECHILDOF, Direction.INCOMING)) {
				if (mrcaTips(r).length < 2) {
					singletons.add(r);
				} else if (! isTaxonomyRel(r)) { 
					Node child = r.getStartNode();
					// for each potential child, only save the rel with the *best* (i.e. lowest) rank--the others redundant
					if ( (! treeRels.containsKey(child)) || rank(treeRels.get(child)) > rank(r)) { treeRels.put(child, r); }
				} else {
					taxonomyOnlyRels.add(r); // record taxonomy rels separately, we will just add in non conflicting ones at the end
					// TODO: if we don't make the streechildofs for taxonomy rels, we can just look for them separately instead of
					// testing for them like this.
				}
			}

			// It seems like we should be able to treat taxonomy nodes just like all other nodes iff all appropriate parents/children
			// are mapped to/from them. But that doesn't seem to be happening (could just be a bug). But connecting them with STREECHILDOFs
			// to/from all parents/children also increases the number of relationships at any given node, which could create problems
			// for synth (not just a bug).

			// get all the best sourcetree rels and collect the ids of all descendant tips
			List<Long> bestRelIds = findBestNonOverlapping(treeRels.values());
			TLongBitArraySet included = new TLongBitArraySet (mrcaTipsAndInternal(bestRelIds));
			
			if (VERBOSE) { for (Long b : bestRelIds) { Relationship r = gdb.getRelationshipById(b); System.out.println("selected source tree rel " + r + ": " + r.getStartNode() + " -> " + r.getEndNode()); }}

			// add all the taxonomy rels that don't conflict with anything in the set
			for (Relationship t : taxonomyOnlyRels) {
				long[] taxRelTipIds = mrcaTips(t);
				if (! included.containsAny(taxRelTipIds)) {
					included.addAll(taxRelTipIds);
					bestRelIds.add(t.getId());
					if (VERBOSE) { System.out.println("adding taxonomy only rel " + t + ": " + t.getStartNode() + " -> " + t.getEndNode()); }
				}
			}
			
			// add the singletons that aren't included in any other rels
			for (Relationship s : singletons) {
				long[] l = mrcaTips(s);
				assert l.length == 1;
				long singleTipId = l[0];
				if (! included.contains(singleTipId)) {
					included.add(singleTipId);
					bestRelIds.add(s.getId());
					if (VERBOSE) { System.out.println("adding singleton rel " + s + ": " + s.getStartNode() + " -> " + s.getEndNode()); }
				}
			}
			
			// record the rels that were identified as the best set
			TLongBitArraySet descendants = new TLongBitArraySet();
			for (Long relId : bestRelIds) {

				Relationship r = n.getGraphDatabase().getRelationshipById(relId);
				long childId = r.getStartNode().getId();
			
				incomingRels.add(n.getGraphDatabase().getRelationshipById(relId));
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
	}
	
	/**
	 * Return a list containing all the *graph tip nodes* (which will be terminal taxa if taxonomy is being used) that
	 * are descended from the child node of this relationship. This should be used for assessing taxonomic overlap among
	 * nodes, but *SHOULD NOT* be used directly for scoring--use mrcaTipsAndInternal for that.
	 * @param rel
	 * @return
	 */
	private long[] mrcaTips(Relationship rel) {
		return (long[]) rel.getStartNode().getProperty(NodeProperty.MRCA.propertyName);
	}

	/**
	 * Get all the tip nodes and internal nodes descended from this node in synthesis. We use this for scoring.
	 * This method *SHOULD NOT* be used to assess overlap. Use the mrcaTips method for that.
	 * @param rel
	 * @return
	 */

	private Set<Long> mrcaTipsAndInternal(Iterable<Long> relIds) {
		HashSet<Long> included = new HashSet<Long>();
		for (Long relId : relIds) { included.addAll((Collection<Long>) nodeMrca.get(gdb.getRelationshipById(relId).getStartNode().getId())); }
		return included;
	}
	
	/**
	 * Get the rank for this relationship relative to relationships from other trees.
	 */
	private int rank(Relationship r) {
		// 'sourcerank' this is a temporary property. we should be accepting a set of source rankings on construction
		// and using that information here to return the rank for a given rel based on it's 'source' property
		return (int) r.getProperty("sourcerank"); 
	}
		
	/**
	 * A simple helper method for code clarity
	 * @param r
	 * @return
	 */
	private static boolean isTaxonomyRel(Relationship r) {
		// taxonomy streechildofs don't have this property. this is a temporary property though. in general we should
		// probably not be making streechildofs for taxonomy and then we won't have to worry about differentiating them
		return ! r.hasProperty("sourcerank");
	}
	
	public String getDescription() {
		return "rootward synthesis method";
	}

	public List<Long> findBestNonOverlapping(Collection<Relationship> rels) {

		if (rels.size() < 1) {
			if (VERBOSE) { System.out.println("(no incoming rels)"); }
			return new ArrayList<Long>();
		} /* else if (rels.size() == 1) {
			Relationship rel = rels.iterator().next();
			if (VERBOSE) { System.out.println("found a knuckle. " + rel + ": " + rel.getStartNode() + " -> " + rel.getEndNode()); }
			List<Long> l = new ArrayList<Long>();
			l.add(rel.getId());
			return l;
		} */
		
		TLongBitArraySet[] mrcaSetsForRels = new TLongBitArraySet[rels.size()];
		double[] weights = new double[rels.size()];
		Long[] relIds = new Long[rels.size()];
		
		Iterator<Relationship> relsIter = rels.iterator();
		for (int i = 0; relsIter.hasNext(); i++) {
			Relationship rel = relsIter.next();
			relIds[i] = rel.getId();
			mrcaSetsForRels[i] = new TLongBitArraySet(mrcaTips(rel));
//			weights[i] = getScoreRankedNodeCount(rel);
			weights[i] = getScoreNodeCount(rel);
//			weights[i] = getScoreRanked(rel);

			if (VERBOSE) { System.out.println(rel.getId() + ": nodeMrca(" + rel.getStartNode().getId() + ") = " + nodeMrca.get(rel.getStartNode().getId())); }
		}
		
		if (relIds.length <= BruteWeightedIS.MAX_TRACTABLE_N) {
			return new BruteWeightedIS(relIds, weights, mrcaSetsForRels).best();
		} else {
			return new GreedyApproximateWeightedIS(relIds, weights, mrcaSetsForRels).best();
		}
	}

	/**
	 * This score is the weight for the MWIS. This is a very simple one based on 
	 * the number of descendants and the rank, but Joseph is working on a better one.
	 * @param node
	 * @return
	 */
	private double getScoreRankedNodeCount(Relationship rel) {
		return getScoreNodeCount(rel) * getScoreRanked(rel);
	}

	/**
	 * This score is the weight for the MWIS. This one is just the number of descendants.
	 * Joseph is working on a better one.
	 * @param node
	 * @return
	 */
	private double getScoreNodeCount(Relationship rel) {
//		System.out.println("nodeMrca(" + rel.getStartNode() + ") = " + nodeMrca.get(rel.getStartNode().getId()));
		return nodeMrca.get(rel.getStartNode().getId()).cardinality();
	}
	
	/**
	 * This score is the weight for the MWIS. This one is just the tree rank. higher ranked rels always trump every lower ranked one.
	 * Joseph is working on a better one.
	 * @param node
	 * @return
	 */
	private double getScoreRanked(Relationship rel) {
		return 1.0 / Math.pow(2, rank(rel));
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
