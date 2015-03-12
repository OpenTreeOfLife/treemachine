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

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;

public class SourceRankTopoOrderSynthesisExpander extends TopologicalOrderSynthesisExpander {

	public SourceRankTopoOrderSynthesisExpander(Node root) {
		VERBOSE = true;
		synthesizeFrom(root);		
	}
	
	@Override
	List<Relationship> selectRelsForNode(Node n) {

		if (VERBOSE) { print("\nvisiting", n); }
		
		// get all the incoming rels and group them by sourcerank and sourceedgeid
		Map<Integer, Map<Long, Set<Relationship>>> relsByRankAndEdgeId = new HashMap<Integer, Map<Long, Set<Relationship>>>();
		Set<Integer> observedRanks = new HashSet<Integer>();
		for (Relationship r : getALLStreeAndTaxRels(n)) {

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
		
		List<Integer> sortedRanks = new ArrayList<Integer>(observedRanks);
		Collections.sort(sortedRanks);
		Collections.reverse(sortedRanks);
		
		if (VERBOSE) { print("selecting edges in order of ranks:", sortedRanks); }
		
		// select rels for inclusion in order of source tree rank
//		List<Relationship> bestRels = new ArrayList<Relationship>();
		Set<Relationship> bestSetForLastRank = new HashSet<Relationship>();
		for (int currentRank : sortedRanks) {
			
			// select the best graph edge to represent each child edge of this node from the original source source tree
			Map<Long, Set<Relationship>> sourceEdgeSets = relsByRankAndEdgeId.get(currentRank);
			for (Entry<Long, Set<Relationship>> candidateRelsForEdgeId : sourceEdgeSets.entrySet()) {
				if (VERBOSE) { print("now working with: rank = " + currentRank + ", edge id = " + candidateRelsForEdgeId.getKey()); }
				
				// if we don't find any rels that can be added, then we will just recycle the best set from the last rank
				Set<Relationship> bestSetForCurrentRank = bestSetForLastRank;

				// for each graph edge mapped to this child edge, see if adding it to the set produces a better result
				for (Relationship r : candidateRelsForEdgeId.getValue()) {
					Set<Relationship> candidateSet = updateSet(r, bestSetForLastRank);
//					print(candidateSet);
//					print(bestSetForCurrentRank);
					if (nodeCount(candidateSet) > nodeCount(bestSetForCurrentRank)) {
						bestSetForCurrentRank = candidateSet;
					}
				}
				bestSetForLastRank = bestSetForCurrentRank;
			}
		}
		return new ArrayList<Relationship>(bestSetForLastRank);
	}
	
	private int nodeCount(Set<Relationship> rels) {
		int n = 0;
		for (Relationship r : rels) {
			n += mrcaTipsAndInternal(r.getStartNode()).size();
		}
		return n;
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
		return mrcaTipsAndInternal(s.getStartNode()).containsAny(mrcaTipsAndInternal(r.getStartNode()));
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
