package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opentree.bitarray.MutableCompactLongSet;
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
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.kernel.Traversal;
import org.opentree.graphdb.GraphDatabaseAgent;

import scala.actors.threadpool.Arrays;

public class RootwardNodeCountSynthesisExpander extends RootwardSynthesisExpander {

	private boolean VERBOSE = true;
	
	public RootwardNodeCountSynthesisExpander(Node root) {

		// TODO: the topological order will die if we have cycles. 
		// first we need to find strongly connected components (SCCs) and identify edges
		// whose exclusion will remove the cycles. use TarjanSCC to find the SCCs.
		
		G = new GraphDatabaseAgent(root.getGraphDatabase());
		topologicalOrder = new TopologicalOrder(G, RelType.STREECHILDOF, RelType.TAXCHILDOF);

		childRels = new HashMap<Long, HashSet<Relationship>>();

		// holds the ids of *all* the descendant nodes (not just terminals)
		nodeMrca = new HashMap<Long, TLongBitArraySet>();
		
		for (Node n : topologicalOrder) {
			
			if (VERBOSE) { System.out.println("\nvisiting node " + n);
				System.out.println("looking for best non-overlapping rels"); }

			// collect the relationships
			Map<Node, Relationship> bestRelForNode = new HashMap<Node, Relationship>();
			List<Relationship> singletons = new ArrayList<Relationship>();

			for (Relationship r : getALLStreeAndTaxRels(n)) {
				if (mrcaTips(r).length == 1) { singletons.add(r); continue; } // skip *all* singletons
				// for non-singletons, just keep one (arbitrary) rel pointing at each child node -- the others are redundant
				bestRelForNode.put(r.getStartNode(), r);
			}
			
			if (bestRelForNode.isEmpty() && VERBOSE) {
				if (singletons.isEmpty()) { System.out.println("this must be a tip."); }
				else { System.out.println("only singletons found."); }}
				
			// collect the set of non-redundant rels
			List<Relationship> relsForMWIS = new ArrayList<Relationship>(bestRelForNode.values());
			
			// get all the best non-singleton rels and collect the ids of all descendant tips
			// TODO: should convert this to return relationship objects instead of ids, rels are easier
			// to work with directly and the only reason we return ids is because that's what's used by the mwis
			List<Long> bestRelIds = findBestNonOverlapping(relsForMWIS);
			List<Relationship> bestRels = new ArrayList<Relationship>();
			for (Long id : bestRelIds) { bestRels.add(G.getRelationshipById(id)); }
			
			TLongBitArraySet included = new TLongBitArraySet(mrcaTipsAndInternal(bestRelIds));
			
			if (VERBOSE) { for (Relationship r : bestRels) { System.out.println("selected source tree rel " + r + ": " + r.getStartNode() + " -> " + r.getEndNode()); }}

			// add the singleton rels that aren't included in any rels already selected
			for (Relationship s : singletons) {
				long[] l = mrcaTips(s);
				assert l.length == 1;
				long singleTipId = l[0];
				if (! included.contains(singleTipId)) {
					included.add(singleTipId);
					bestRels.add(s);
					if (VERBOSE) { System.out.println("adding singleton rel " + s + ": " + s.getStartNode() + " -> " + s.getEndNode()); }
				}
			}
			
			recordRels(n, bestRels);
		}
	}
		
	public String getDescription() {
		return "rootward synthesis method: maximize node count";
	}

	public List<Long> findBestNonOverlapping(Collection<Relationship> rels) {

		if (rels.size() < 1) {
			if (VERBOSE) { System.out.println("(no non-singleton rels to select from)"); }
			return new ArrayList<Long>();
		}
		
		TLongBitArraySet[] mrcaSetsForRels = new TLongBitArraySet[rels.size()];
		double[] weights = new double[rels.size()];
		Long[] relIds = new Long[rels.size()];
		
		// used to check if no overlap exists among any rels, if not don't bother with the mwis
		// really we could be smarter about this and find the rels that have no overlap and exclude them
		int taxSum = 0;

		HashSet<Long> uniqueTips = new HashSet<Long>();
		
		Iterator<Relationship> relsIter = rels.iterator();
		for (int i = 0; relsIter.hasNext(); i++) {
			Relationship rel = relsIter.next();
			
			TLongBitArraySet currDesc = mrcaTipsAndInternal(rel.getId());
			
			// this represents a pathological case, so die horribly
			if (currDesc == null) { throw new IllegalStateException("Found a rel with no descendants: " + rel); }

			relIds[i] = rel.getId();
			mrcaSetsForRels[i] = currDesc;
			weights[i] = getScoreNodeCount(rel);

			long [] currTips = mrcaTips(rel);
			taxSum += currTips.length;
			for (int j = 0; j < currTips.length; j++) {
				uniqueTips.add(currTips[j]);
			}

			if (VERBOSE) { System.out.println(rel.getId() + ": nodeMrca(" + rel.getStartNode().getId() + ") = " + nodeMrca.get(rel.getStartNode().getId()) + ". score = " + weights[i]); }
		}
		
		System.out.println("taxSum = " + taxSum + "; uniqueTips size = " + uniqueTips.size() + "; incoming rels = " + relIds.length);

		if (taxSum == uniqueTips.size()) { // if there is no conflict, skip the set comparisons and just add everything (saves major time)
			System.out.println("no conflict! saving all incoming rels.");
			return new ArrayList<Long>(Arrays.asList(relIds));
		
		} else if (relIds.length <= BruteWeightedIS.MAX_TRACTABLE_N) { // otherwise if the set is small enough find the exact MWIS solution
			return new BruteWeightedIS(relIds, weights, mrcaSetsForRels).best();
		
		} else { // otherwise we need to use the approximation method
			return new GreedyApproximateWeightedIS(relIds, weights, mrcaSetsForRels).best();
		}
	}

	/**
	 * This is the simplest node scoring criterion we could come up with: just the number of descendants.
	 * Joseph has done some work coming up with a better one that included the structure of the tree.
	 * @param node
	 * @return
	 */
	private double getScoreNodeCount(Relationship rel) {
		return nodeMrca.get(rel.getStartNode().getId()).cardinality();
	}

	/* THIS SECTION IN STASIS. The plan is to use the WeightedDirectedGraph class with
	 * an optimized exact solution to the MWIS. Not there yet though. **/
	public Iterable<Long> findBestNonOverlappingGraph(Long[] relIds) {

		boolean trivialTestCase = false;		

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
