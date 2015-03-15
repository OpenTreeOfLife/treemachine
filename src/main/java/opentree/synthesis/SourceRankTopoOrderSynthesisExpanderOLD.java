package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.opentree.utils.GeneralUtils.print;
import static org.opentree.utils.GeneralUtils.getRelationshipsFromTo;
import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;

/**
 * This version of the SourceRank synth expander attempts to be smart by breaking ties in advance based on ranks.
 * It has problems (fails tests). Abandoning it now in favor of a less efficient (but hopefully more reliable) way.
 * @author cody
 *
 */
@Deprecated
public class SourceRankTopoOrderSynthesisExpanderOLD extends TopologicalOrderSynthesisExpander {

	/*
	 * We record the number of unique source tree edges that are represented in the subgraph below each node,
	 * and use this info for tie-breaking. In short, the map contains elements of the form:<br><br>
	 * 
	 * <tt>{ node -> { rank -> { source edge ids }}</tt><br><br>
	 * 
	 * The top-level key is a node. For each node, there is a map for which
	 * the keys are the source tree ranks, and the values are sets of integers identifying the unique source
	 * tree edges, for the tree of that rank, that are represented by graph edges in the synthesis tree below
	 * the given node. We use ranks as keys instead of ids because we will be breaking ties in order of rank,
	 * so using ranks as keys makes for easier lookups.
	 *
	Map<Node, Map<Integer, Set<Long>>> treeEdgesForRankByNode = new HashMap<Node, Map<Integer, Set<Long>>>(); */

	/**
	 * Records the set of all known ranks for all the parallel outgoing edges from a given child node--it is
	 * assumed that the parent node is the current node under visitation, so we must reinitialize this map every
	 * time each time we visit a new node (the selectRelsForNode procedure). We use the information in the map 
	 * to filter the sets of parallel edges in order to retain those with the highest rank.
	 * 
	 */
	Map<Node, Set<Integer>> ranksForRelsFromChildNode;
	
	Map<Integer, Map<Long, Set<Relationship>>> relsByRankAndEdgeId;
	List<Integer> sortedRanks;
	
	public SourceRankTopoOrderSynthesisExpanderOLD(Node root) {
		VERBOSE = true;
		synthesizeFrom(root);		
	}
	
	@Override
	List<Relationship> selectRelsForNode(Node n) {

		if (VERBOSE) { print("\n==== visiting", n, "=================================================================================\n"); }
		
		relsByRankAndEdgeId = new HashMap<Integer, Map<Long, Set<Relationship>>>();
		ranksForRelsFromChildNode = new HashMap<Node, Set<Integer>>();

		// get all the incoming rels and group them by sourcerank and sourceedgeid
		// note, we only care about the rels from the highest ranked source, but we can't be sure what that is until we've
		// processed all the incoming rels, so we just save edges for all ranks
		Set<Integer> observedRanks = new HashSet<Integer>();
		for (Relationship r : getALLStreeAndTaxRels(n)) {

			Node child = r.getStartNode();
			if (! ranksForRelsFromChildNode.containsKey(child)) {
				ranksForRelsFromChildNode.put(child, new HashSet<Integer>());
			}
			ranksForRelsFromChildNode.get(child).add(rank(r));

			int rank = rank(r); // collect the rank
			observedRanks.add(rank);
			if (! relsByRankAndEdgeId.containsKey(rank)) {
				relsByRankAndEdgeId.put(rank, new HashMap<Long, Set<Relationship>>());
			}

			long edgeId = edgeId(r); // collect the edge id
			if (! relsByRankAndEdgeId.get(rank).containsKey(edgeId)) {
				relsByRankAndEdgeId.get(rank).put(edgeId, new HashSet<Relationship>());
			}

			if (VERBOSE) { print("adding", r, "to set of incoming rels. rank =", rank, "edgeid =", edgeId); }
			relsByRankAndEdgeId.get(rank).get(edgeId(r)).add(r);
		}

		// for nodes with no children
		if (observedRanks.size() < 1) {
			return new ArrayList<Relationship>();
		}

		sortedRanks = new ArrayList<Integer>(observedRanks);
		Collections.sort(sortedRanks);
		Collections.reverse(sortedRanks);
		
		Set<Relationship> bestSet = new HashSet<Relationship>();
		Integer bestRank = null;
		for (int currentRank : sortedRanks) {
			Set<Relationship> bestSetAtCurrRank = getBestEdgeSetForWorkingRank(currentRank);
			if (VERBOSE) {
				print("checking if best set X for rank:", currentRank, "includes the previous best set found Y for rank:", bestRank);
				print("X =", bestSetAtCurrRank);
				print("Y =", bestSet);
			}
			if (mrcaTipsAndInternal(bestSetAtCurrRank).containsAll(mrcaTipsAndInternal(bestSet))) {
				if (VERBOSE) { print("it does, so it will be kept."); }
				bestSet = bestSetAtCurrRank;
				bestRank = currentRank;
			}
		}
		
/*		// select rels for inclusion in order of source tree rank
		Set<Relationship> bestSetAtLastRank = new HashSet<Relationship>();
		for (int currentRank : sortedRanks) {
			
			// select the best graph edge to represent each child edge of this node from the original source tree
			Map<Long, Set<Relationship>> sourceEdgeSets = relsByRankAndEdgeId.get(currentRank);
			for (Entry<Long, Set<Relationship>> candidateRelsForEdgeId : sourceEdgeSets.entrySet()) {
				
				// if we don't find any rels that can be added, then we will just recycle the best set from the last rank
				Set<Relationship> bestSetAtCurrentRank = bestSetAtLastRank;
				
				// for each graph edge mapped to this child edge, see if adding it to the set produces a better result
				for (Relationship r : candidateRelsForEdgeId.getValue()) {
					Set<Relationship> candidateSet = updateSet(r, bestSetAtLastRank);
					
					// TODO: need to use the source tree edge counts here for tie breaking
					if (nodeCount(candidateSet) > nodeCount(bestSetAtCurrentRank)) {
						bestSetAtCurrentRank = candidateSet;
					}
					
				}
				bestSetAtLastRank = bestSetAtCurrentRank;
			}
		} 
		recordCoveredTreeEdges(n, bestSetAtLastRank);
		return new ArrayList<Relationship>(bestSetAtLastRank); */
		
//		recordCoveredTreeEdges(n, bestRels);
//		return bestRels;
		return new ArrayList<Relationship>(bestSet);
	}
	
	private Set<Relationship> getBestEdgeSetForWorkingRank(Integer rank) {
		// now just retain the rels that represent edges from the highest ranked source
		Map<Long, Set<Relationship>> relsByEdgeId = relsByRankAndEdgeId.get(rank);

		// first select the best incoming edge for all the edge sets representing branches from the highest ranked source tree
		if (VERBOSE) { print("\nattempting to reduce parallel edges corresponding to branches from tree with rank " + rank); }
		for (Entry<Long, Set<Relationship>> candidateRelsForEdgeId : relsByEdgeId.entrySet()) {

			Set<Relationship> rels = candidateRelsForEdgeId.getValue();
			if (VERBOSE) { print("\nset for edgeid = " + candidateRelsForEdgeId.getKey() + " is " + rels); }
			
			int rankIndex = 1;
			while(rels.size() > 1 && rankIndex < sortedRanks.size()) {
				int workingRank = sortedRanks.get(rankIndex++);
				if (VERBOSE) { print("attempting to rels compatible with tree of rank ", workingRank); }
				Set<Relationship> flagged = new HashSet<Relationship>();
				for (Relationship e : rels) {
					if (! hasParallelEdgeOfRank(e, workingRank)) {
						flagged.add(e);
					}
				}
				// only remove the flagged rels if there was at least one that represents a branch
				// in the tree with this rank (if there was not then this tree can't help us here)
				if (flagged.size() < rels.size()) {
					rels.removeAll(flagged);
				}
			}
			if (VERBOSE) { print("reduced to " + rels); }
		}
		
		if (VERBOSE) { print("\nbreaking ties for equally ranked edges based on nodecount"); }
		Set<Relationship> bestRels = new HashSet<Relationship>();
		for (Entry<Long, Set<Relationship>> e : relsByEdgeId.entrySet()) {
			Long edgeId = e.getKey();
			Set<Relationship> candidateRels = e.getValue(); 
			Relationship best = null;
			if (candidateRels.size() < 2) {
				if (VERBOSE) { print("\nonly one rel for source edge id " + edgeId); }
				best = candidateRels.iterator().next();
			} else {
				if (VERBOSE) { print("\nselecting best for source edge id", edgeId); }
				for (Relationship r : candidateRels) {
					if (best == null || nodeCount(r) > nodeCount(best)) {
						if (VERBOSE) { print(r, "=", mrcaTipsAndInternal(r), "is better than previous best", best, (best == null ? "" : ("=" + mrcaTipsAndInternal(best)))); }
						best = r;
					}
				}
			}
			bestRels.add(best);
			if (VERBOSE) { print("selected", best); }
		}
		
		return bestRels;
	}
	
	private boolean hasParallelEdgeOfRank(Relationship rel, int rank) {
		return ranksForRelsFromChildNode.get(rel.getStartNode()).contains(rank);
	}
	
	/*
	 * For tie breaking, we will accumulate a count, for each source tree, of the number of edges from that
	 * source tree that are represented within the synthesized subtree below each node as we finish it.
	 * 
	 * @param n
	 * @param bestRels
	 *
	private void recordCoveredTreeEdges(Node n, Set<Relationship> bestRels) {

		Map<Integer, Set<Long>> currNodeEdgesForRank = new HashMap<Integer, Set<Long>>();
		for (Relationship r : bestRels) {

			Node child = r.getStartNode();

			// add *all* the stree/tax edges congruent with this rel (not just this rel, but parallel rels as well)
			for (Relationship p : getRelationshipsFromTo(child, n, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {
				int rank = rank(p);
				updateSetMap(rank, currNodeEdgesForRank);
				currNodeEdgesForRank.get(rank).add(edgeId(p));
			}

			// accumulate stree edges represented by this node's children (which contain all their children's edges, etc.)
			for (Entry<Integer, Set<Long>> childEdgesForRank : treeEdgesForRankByNode.get(child).entrySet()) {
				int rank = childEdgesForRank.getKey();
				updateSetMap(rank, currNodeEdgesForRank);
				currNodeEdgesForRank.get(rank).addAll(childEdgesForRank.getValue());
			}
		}
		
		treeEdgesForRankByNode.put(n, currNodeEdgesForRank);
		if (VERBOSE) { print(n + " contains source tree edges (by rank) " + treeEdgesForRankByNode.get(n), "\n"); }
	} 
		
	/**
	 * trivial convenience function for code simplification.
	 * @param v
	 * @param m
	 *
	private void updateSetMap(int v, Map<Integer, Set<Long>> m) {
		if (! m.containsKey(v)) { m.put(v, new HashSet<Long>()); }
	} */
	
	private int nodeCount(Set<Relationship> rels) {
		int n = 0;
		for (Relationship r : rels) {
			n += nodeCount(r);
		}
		return n;
	}
	
	private int nodeCount(Relationship r) {
		return mrcaTipsAndInternal(r).size();
	}
	
	/**
	 * For each candidate rel r, we must check whether or not setToUpdate contains *any* rels which are
	 * completely contained by r. If this is true, and if r does not conflict with any other rels in
	 * setToUpdate, then we return a new set that contains r, all of the rels from setToUpdate that are
	 * not contained by r and do not overlap with r, and none of the rels from setToUpdate that are
	 * completely contained by r.
	 */
	private Set<Relationship> updateSet(Relationship candidate, Set<Relationship> setToUpdate) {

		if (VERBOSE) { print("assessing candidate ", candidate, ":", mrcaTips(candidate.getStartNode())); }
		
		Set<Relationship> updated = new HashSet<Relationship>();
		
		Set<Relationship> containedByCandidate = new HashSet<Relationship>();
		boolean addCandidate = true;

		for (Relationship s : setToUpdate) {
			if (VERBOSE) { print("checking for overlap with", s, ":", mrcaTips(s.getStartNode())); }
			
			if (containsAllTips(s, candidate)) {
				containedByCandidate.add(s);
				if (VERBOSE) { print("candidate", candidate, "contains all of previously saved", s); }
			} else if (overlapsWith(candidate, s)) {
				addCandidate = false;
				if (VERBOSE) { print("candidate", candidate, "overlaps with but does not contain all of previously saved", s); }
				break;
			} else {
				updated.add(s);
			}
		}
		
		if (addCandidate) {
			if (VERBOSE) { print("did not find any overlapping but not completely contained rels. candidate", candidate, "will be saved."); }
			updated.add(candidate);
		} else {
			if (VERBOSE) { print("set will not be updated."); }
			updated = setToUpdate;
		}

		if (VERBOSE) { print(); }
		return updated;
	}
	
	@Override
	void breakCycles() {
		System.out.println("currently not breaking cycles! topological order should fail if it encounters one.");
	}

	
	/**
	 * Returns true if and only if r contains no descendant tips that are shared by any of the rels in
	 * others.<br><br>
	 * 
	 * The reliability of this method for use in this class depends on the topological order--this will throw
	 * NullPointerException if the topological order has not been observed and an attempt is made to check
	 * for inclusion of a child that has not yet been visited.
	 * 
	 * @param r
	 * @param others
	 * @return
	 */
	private boolean overlapsWith(Relationship r, Iterable<Relationship> others) {
		boolean result = false;
		for (Relationship s : others) {
			if (overlapsWith(r,s)) {
				result = true;
				break;
			}
		}
		return result;
	}

	/**
	 * Returns true if and only if r contains no descendant tips that are shared by any of the rels in
	 * others.<br><br>
	 */
	private boolean overlapsWith(Relationship r, Relationship s) {
		return mrcaTipsAndInternal(s).containsAny(mrcaTipsAndInternal(r));
	}
	
	/**
	 * Is anc an ancestor of desc? Returns true if and only if following edge anc necessarily leads to the
	 * inclusion of all tips of desc in the synthetic tree. TODO: this description needs work.
	 * 
	 * @param r
	 * @param s
	 */
	boolean containsAllTips(Relationship desc, Relationship anc) {
//		return mrcaTipsAndInternal(anc.getId()).contains(desc.getStartNode().getId());
		return mrcaTips(anc.getStartNode()).containsAll(mrcaTips(desc.getStartNode()));
	}
	
	/**
	 * Get the rank for this relationship relative to relationships from other trees.
	 */
	private int rank(Relationship r) {
		return isTaxonomyRel(r) ? 0 : (int) r.getProperty("sourcerank");
	}

	/**
	 * Get the unique edge id for this relationship within its source tree. For taxonomy rels, we just use the
	 * database id of the rel (which are unique) since each taxonomy rel is only represented once in the db.
	 */
	private long edgeId(Relationship r) {
		return isTaxonomyRel(r) ? r.getId() : (int) r.getProperty("sourceedgeid");
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
	
	@Override
	public Iterable expand(Path path, BranchState state) {

		// testing
		System.out.println("looking for rels starting at: " + path.endNode());
		System.out.println(childRels.get(path.endNode().getId()));
		
		return childRels.get(path.endNode().getId());
	}

	@Override
	public PathExpander reverse() {
		throw new UnsupportedOperationException();
	}
}
