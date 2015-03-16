package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import org.opentree.bitarray.ImmutableCompactLongSet;
import org.opentree.bitarray.LongSet;
import org.opentree.bitarray.MutableCompactLongSet;
import org.opentree.bitarray.TLongBitArraySet;

import scala.actors.threadpool.Arrays;

public class SourceRankTopoOrderSynthesisExpanderUsingExclusiveMrcas extends TopologicalOrderSynthesisExpander {

	/**
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
	 */
	Map<Node, Map<Integer, Set<Long>>> treeEdgesForRankBelowFinishedNode = new HashMap<Node, Map<Integer, Set<Long>>>();

	// these get reinitialized for each node we visit
	Map<Node, Map<Integer, Set<Long>>> treeEdgesForRankForProposedChildNode;
	Map<Integer, Map<Long, Set<Relationship>>> relsByRankAndEdgeId;
	Set<Node> children;
	Set<Integer> observedRanks;
	
	Set<Relationship> bestSet;
	TLongBitArraySet mrcaIdsBestSet;
	Map<Integer, Set<Long>> treeEdgesByRankBestSet;

	Set<Relationship> bestSetForCurrentRank;
	TLongBitArraySet mrcaIdsBestSetForCurrentRank;
	Map<Integer, Set<Long>> treeEdgesByRankBestSetForCurrentRank;
	
	public SourceRankTopoOrderSynthesisExpanderUsingExclusiveMrcas(Node root) {
		VERBOSE = true;
		synthesizeFrom(root);		
	}

	/**
	 * all the rels that have been saved for each rank
	 * top level key is rank
	 * second level key is relationship
	 * set at the bottom level is the exclusive mrca for the specified rel p id *plus* all the
	 * exclusive mrcas for all the higher ranked rels that have been subsumed by p
	 */
	Map<Integer, Map<Relationship, LongSet>> savedRelsByRank = new HashMap<Integer, Map<Relationship, LongSet>>();

	private boolean containsAllExclusiveMrca(Relationship r, int rank, Set<Relationship> proposed) {
		
		// we will be comparing sets of *exclusive mrcas* from higher ranked trees with the *graph node mrca 
		// property* for the rels from the current tree. the notion is that if the rel from the current ranked
		// tree can possibly lead to the nodes included in the higher ranked tree, then it will, because
		// the necessary rels for that to happen will be included in the subtree because of their higher ranks.


		// use savedRelsByRank
		
		
		return false;
	}
	
	/**
	 * Just get the exclusive mrca property and return a LongSet that contains it.
	 * @param r
	 * @return
	 */
	private LongSet exclusiveMrca(Relationship r) {
		return new ImmutableCompactLongSet((long[]) r.getProperty(NodeProperty.EXCLUSIVE_MRCA));
	}
	
	@Override
	List<Relationship> selectRelsForNode(Node n) {

		if (VERBOSE) { print("\n==== visiting", n, "=================================================================================\n"); }
		initialize();
		
		// get all the incoming rels and group them by sourcerank and sourceedgeid
		for (Relationship r : getALLStreeAndTaxRels(n)) {
			processIncomingRel(r);
		}
				
//		collectCoveredTreeEdges(n, children);
		
		List<Integer> sortedRanks = new ArrayList<Integer>(observedRanks);
		Collections.sort(sortedRanks);
		Collections.reverse(sortedRanks);

		if (VERBOSE) { print("selecting edges in order of ranks:", sortedRanks); }
				
		// select rels for inclusion from each source tree in order of rank
		recordBestSet(new HashSet<Relationship>());
		for (int i = 0; i < sortedRanks.size(); i++) {
			int currentRank = sortedRanks.get(i);
			if (VERBOSE) { print("now working with rank:", currentRank); }

			Set<Relationship> remainingRelsForCurrentRank = relsByRank.get(currentRank);

			// for each tree with a higher rank (that we've already visited)
			for (int j = 0; j < i; j++) {
				int higherRank = sortedRanks.get(j);

				Map<Relationship, LongSet> savedRelsForHigherRank = savedRelsByRank.get(higherRank);
				
				// 1. replace any combinations of rels that are included by any single rels from the tree for the current rank
				findHigherRankedCombinationsSubsumbedByThisRel:
				for (Relationship p : remainingRelsForCurrentRank) {

					// replace all combinations of rels from tree with higherRank if they are covered by p
					// TODO: this would most efficient if it considered larger combinations of rels first (for early exit)
					for (Set<Relationship> combination : new Combinations<Relationship>(savedRelsByRank.get(higherRank))) {
												
						// if we replace combination with rel p, then:
						if (containsAllExclusiveMrca(p, higherRank, combination)) {
							// - remove all the rels in `combination` from the saved rels
							// - accumulate all the excl_mrca(q) for each q in `combination` into excl_mrca(p)
							MutableCompactLongSet cumulativeMrca = new MutableCompactLongSet();
							cumulativeMrca.addAll(exclusiveMrca(p));
							for (Relationship r : combination) {
								cumulativeMrca.addAll(savedRelsForHigherRank.get(r));
								savedRelsForHigherRank.remove(r);
							}
							// - remove p from currentRankRels
							remainingRelsForCurrentRank.remove(p);
							savedRelsByRank.get(currentRank).put(p, cumulativeMrca);
							break findHigherRankedCombinationsSubsumbedByThisRel;
						}
					}
				}
			
				// 2. replace any tips from higher ranked trees that are included by any remaining single rels from this tree
				for (Relationship p : remainingRelsForCurrentRank) {
					
					// replace each *single* rel q from the tree with higherRank if nodemrca(p) contains excl_mrca(q)

					// if we added p, then remove it from currentRankRels
				}
				
				// 3. add best rels from the set of remaining rels for this tree:
				// 3.1 update edge sets for currentRank to exclude rels already in recordedRels
				// 3.2 pick best set from cartesian product of remaining rels in edge ids sets for current ranked tree
					
				}
				
				
				
			
			for (;;) {}
			
			
			
			
			
			// first find edge sets for the current rank that overlap with the previous best set
			// those are the only ones for which we need to check the combinations
			Set<Set<Relationship>> overlappingSets = new HashSet<Set<Relationship>>();
			Set<Set<Relationship>> nonOverlappingSets = new HashSet<Set<Relationship>>();
			for (Set<Relationship> edgeSet : relsByRankAndEdgeId.get(currentRank).values()) {
				if (overlapsWithBestSet(edgeSet, currentRank)) {
					overlappingSets.add(edgeSet);
				} else {
					nonOverlappingSets.add(edgeSet);
				}
			}
			
			// now attempt to augment the previous best rels set with rels from the edge sets for this rank
			recordBestSetForCurrentRank(bestSet);
			while (overlappingSets.size() > 0) {

				// first see if we can update the best set with any rels from edge sets that overlap with the best set
				CartesianProduct<Relationship> combinations = new CartesianProduct<Relationship>(overlappingSets);
				if (VERBOSE) { print("picking best combinations of rels to add from overlapping edge sets"); }
				for (Set<Relationship> proposed : combinations.withMissingElements()) {
					
					if (! internallyConsistent(proposed)) { continue; }

					if (VERBOSE) { print("\nbest set so far is:", bestSetForCurrentRank + ", which exemplifies", scoreForRank(bestSetForCurrentRank, currentRank), "edges from tree of rank", currentRank); }

					// will return the original bestSet unless the proposed rels can improve it
					Set<Relationship> candidate = updateSet(n, proposed, bestSet, currentRank);
	
					// replace the previous best candidate if this one has more rels representing edges from the current ranked tree
					// or if it has the same number of rels from the current ranked tree but contains more nodes
					int candidateScore = scoreForRank(candidate, currentRank);
					int previousBestScore = scoreForRank(bestSetForCurrentRank, currentRank);
					if ((candidateScore > previousBestScore) || 
					   ((candidateScore == previousBestScore) 
							   && (mrcaTipsAndInternal(candidate).size() > mrcaIdsBestSetForCurrentRank.size()))) {
						if (VERBOSE) { print("found a new best set for this rank: ", candidate); }
						recordBestSetForCurrentRank(candidate);
					}
				}
			
				// find any previously nonoverlapping sets that still don't overlap with the *updated* best set
				overlappingSets = new HashSet<Set<Relationship>>();
				Iterator<Set<Relationship>> nonOverlappingIter = nonOverlappingSets.iterator();
				while (nonOverlappingIter.hasNext()) {
					Set<Relationship> edgeSet = nonOverlappingIter.next();
					if (overlapsWithBestSetForCurrentRank(edgeSet, currentRank)) {
						overlappingSets.add(edgeSet);
						nonOverlappingIter.remove();
					} 
				}
				
				// for the edge sets that still don't overlap, find the best individual edge from each set to add to the best set
				recordBestSetForCurrentRank(augmentFromNonOverlappingSets(bestSetForCurrentRank, nonOverlappingSets, currentRank));
				
				if (VERBOSE) { print("best set for this rank so far: ", bestSetForCurrentRank); }

				// for the edge sets that do overlap with the best set, repeat the procedure
			}

			// in case there were no overlapping rels, find the best individual edge each edge set
			recordBestSet(augmentFromNonOverlappingSets(bestSetForCurrentRank, nonOverlappingSets, currentRank));
		}

		for (Relationship t : taxonomySingletonRels) {
			if (! overlapsWithBestSet(t, 0)) {
				bestSet.add(t);
			}
		}
		
		recordCoveredTreeEdges(n, bestSet);
		return new ArrayList<Relationship>(bestSet);
	}
	
	private void recordBestSet(Set<Relationship> rels) {
		bestSet = rels;
		mrcaIdsBestSet = gatherMrcaIds(bestSet);
		treeEdgesByRankBestSet = gatherTreeEdgesByRank(bestSet);
	}
	
	private void recordBestSetForCurrentRank(Set<Relationship> rels) {
		bestSetForCurrentRank = rels;
		mrcaIdsBestSetForCurrentRank = gatherMrcaIds(bestSetForCurrentRank);
		treeEdgesByRankBestSetForCurrentRank = gatherTreeEdgesByRank(bestSetForCurrentRank);
	}
	
	private boolean internallyConsistent(Set<Relationship> rels) {
		boolean valid = true;
		TLongBitArraySet cumulativeMrca = new TLongBitArraySet();
		for (Relationship r : rels) {
			TLongBitArraySet currMrca = mrcaTipsAndInternal(r);
			if (cumulativeMrca.containsAny(currMrca)) {
				valid = false;
				if (VERBOSE) { print("set:", rels, "has internal overlap and will not be considered."); }
				break;
			}
			cumulativeMrca.addAll(currMrca);
		}
		return valid;
	}
	
	private Set<Relationship> augmentFromNonOverlappingSets(Set<Relationship> toUpdate, Set<Set<Relationship>> augmentingSets, int workingRank) {
		if (VERBOSE) { print("picking best rels to add from edge sets that don't overlap with the best set"); }
		
		// first collect the mrca ids for all the edge sets
		List<Set<Relationship>> augmentingSetList = new ArrayList<Set<Relationship>>(augmentingSets);
		Map<Integer, LongSet> idsForOverlapping = new HashMap<Integer, LongSet>();
		for (int i = 0; i < augmentingSetList.size(); i++) {
			idsForOverlapping.put(i, new ImmutableCompactLongSet(mrcaTipsAndInternal(augmentingSetList.get(i))));
		}
		if (VERBOSE) { print("starting sets:"); for(int i = 0; i < idsForOverlapping.size(); i++) print(i, "=", augmentingSetList.get(i), "=", idsForOverlapping.get(i)); }
		
		// isolate overlapping subsets of the incoming rels (use a union-find structure to do this fast)
		// so we only need to propose combinations from within each overlapping subset of edge sets
		WeightedQuickUnionUF overlapping = new WeightedQuickUnionUF(augmentingSetList.size());
		MutableCompactLongSet cumulativeIds = new MutableCompactLongSet();
		for (int i = 0; i < augmentingSetList.size(); i++) {
			if (! idsForOverlapping.containsKey(i)) {
				if (VERBOSE) { print("set", i, "has already been added to an overlapping set. moving on..."); }
				continue;
			}
			
			MutableCompactLongSet edgeSetIds = new MutableCompactLongSet(gatherMrcaIds(augmentingSetList.get(i)));
			if (VERBOSE) { print("checking set", i); }

			if (cumulativeIds.containsAny(edgeSetIds)) { // this edge set overlaps with at least one other
				if (VERBOSE) { print(i, "overlaps with: "); }
				
				// find the (sets of) other edge sets that this one overlaps with and gather all their ids
				List<Integer> setsToCombine = new ArrayList<Integer>();
				MutableCompactLongSet idsForCombinedSet = new MutableCompactLongSet(idsForOverlapping.get(i));
				for (int setId : overlapping) {
					if (idsForOverlapping.get(setId).containsAny(edgeSetIds)) {
						setsToCombine.add(setId);
						idsForCombinedSet.addAll(idsForOverlapping.get(setId));
//						setsToRemove.add(setId);
					}
					if (VERBOSE) { print(setId+": cumulative union=", idsForCombinedSet); }
				}
				assert setsToCombine.size() > 0;

				// connect all the overlapping sets in the uf object
				int newSetId = -1;
				for (Integer setId : setsToCombine) {
					newSetId = overlapping.union(i, setId);
					idsForOverlapping.remove(setId);
				}
				assert newSetId > 0;
				
				// record the cumulative id set for the newly combined set
				idsForOverlapping.put(newSetId, idsForCombinedSet);

			} else { // no overlap with any earlier sets so make a new disjoint set
				idsForOverlapping.put(i, edgeSetIds);
			}
			cumulativeIds.addAll(edgeSetIds);
		}
		
		for (Integer setId : overlapping) {
		
			Set<Set<Relationship>> currOverlappingSetOfAugmentingEdgeSets = new HashSet<Set<Relationship>>();
			for (Long l : overlapping.getSet(setId)) {
				currOverlappingSetOfAugmentingEdgeSets.add(augmentingSetList.get(l.intValue()));
			}
		
			if (VERBOSE) { print("now picking rels from overlapping edge set", currOverlappingSetOfAugmentingEdgeSets); }

			Set<Relationship> bestCandidate = null;
			int bestScore = -1;
			Set<Long> mrcaIdsBestCandidate = new HashSet<Long>();
			for (Set<Relationship> candidate : new CartesianProduct<Relationship>(currOverlappingSetOfAugmentingEdgeSets).withMissingElements()) {
				if (internallyConsistent(candidate)) {
					int candidateScore = scoreForRank(candidate, workingRank);
					if ((candidateScore > bestScore) ||
					   ((candidateScore == bestScore) 
							   && (mrcaTipsAndInternal(candidate).size() > mrcaIdsBestCandidate.size()))) {
						bestCandidate = candidate;
						bestScore = candidateScore;
						mrcaIdsBestCandidate = mrcaTipsAndInternal(bestCandidate);
					}
				}
			}
			if (VERBOSE) { print("selected best candidate", bestCandidate); }
			
			if (bestCandidate != null) {
				for (Relationship r : bestCandidate) {
					if (VERBOSE) { print("adding", r, "to saved set"); }
					toUpdate.add(r);
				}
			}
		
		}

		return toUpdate;
	}
	
	private void initialize() {
		treeEdgesForRankForProposedChildNode = new HashMap<Node, Map<Integer, Set<Long>>>();
		relsByRankAndEdgeId = new HashMap<Integer, Map<Long, Set<Relationship>>>();
		children = new HashSet<Node>();
		observedRanks = new HashSet<Integer>();
	}
	
	private void processIncomingRel(Relationship r) {
		children.add(r.getStartNode());
		
		int rank = rank(r); // collect the rank
		observedRanks.add(rank);
		if (! relsByRankAndEdgeId.containsKey(rank)) {
			relsByRankAndEdgeId.put(rank, new HashMap<Long, Set<Relationship>>());
		}

		long edgeId = edgeId(r); // collect the edge id
		if (! relsByRankAndEdgeId.get(rank).containsKey(edgeId)) {
			relsByRankAndEdgeId.get(rank).put(edgeId, new HashSet<Relationship>());
		}
		
		if (VERBOSE) { print("adding non-singleton", r, " (" + mrcaTipsAndInternal(r) + ") to set of incoming rels. rank =", rank, "edgeid =", edgeId); }
		relsByRankAndEdgeId.get(rank).get(edgeId(r)).add(r);
	}
	
	private int scoreForRank(Set<Relationship> rels, int rank) {
		int score = 0;
		for (Relationship r : rels) {
			Map<Integer, Set<Long>> edgesForRank = treeEdgesForRankForProposedChildNode.get(r.getStartNode());
			if (edgesForRank.containsKey(rank)) {
				score += edgesForRank.get(rank).size();
			}
		}
		return score;
	}
	
	private Map<Integer, Set<Long>> gatherTreeEdgesByRank(Relationship r) {
		Map<Integer, Set<Long>> treeEdgesByRank = new HashMap<Integer, Set<Long>>();
		Node childNode = r.getStartNode();
		for (Entry<Integer, Set<Long>> childTreeEdges : treeEdgesForRankForProposedChildNode.get(childNode).entrySet()) {
			int rank = childTreeEdges.getKey();
			if (! treeEdgesByRank.containsKey(rank)) { treeEdgesByRank.put(rank, new HashSet<Long>()); }
			treeEdgesByRank.get(rank).addAll(childTreeEdges.getValue());
		}
		return treeEdgesByRank;
	}
	
	private Map<Integer, Set<Long>> gatherTreeEdgesByRank(Set<Relationship> rels) {
		Map<Integer, Set<Long>> treeEdgesByRank = new HashMap<Integer, Set<Long>>();
		for (Relationship r : rels) { 
			for (Entry<Integer, Set<Long>> childTreeEdges : gatherTreeEdgesByRank(r).entrySet()) {
				int rank = childTreeEdges.getKey();
				if (! treeEdgesByRank.containsKey(rank)) { treeEdgesByRank.put(rank, new HashSet<Long>()); }
				treeEdgesByRank.get(rank).addAll(childTreeEdges.getValue());
			}
		}
		return treeEdgesByRank;
	}
	
	private TLongBitArraySet gatherMrcaIds(Set<Relationship> rels) {
		TLongBitArraySet mrcaIds = new TLongBitArraySet();
		for (Relationship r : rels) {
			mrcaIds.addAll(mrcaTipsAndInternal(r));
		}
		return mrcaIds;
	}
	
	private boolean overlapsWithBestSet(Relationship r, int workingRank) {
		Map<Integer, Set<Long>> treeEdgesByRank = gatherTreeEdgesByRank(r);
		TLongBitArraySet mrcaIds = mrcaTipsAndInternal(r);
		return overlaps(treeEdgesByRank, treeEdgesByRankBestSet, mrcaIds, mrcaIdsBestSet, workingRank);
	}

	private boolean overlapsWithBestSet(Set<Relationship> rels, int workingRank) {
		Map<Integer, Set<Long>> treeEdgesByRank = gatherTreeEdgesByRank(rels);
		TLongBitArraySet mrcaIds = gatherMrcaIds(rels);
		return overlaps(treeEdgesByRank, treeEdgesByRankBestSet, mrcaIds, mrcaIdsBestSet, workingRank);
	}

	private boolean overlapsWithBestSetForCurrentRank(Set<Relationship> rels, int workingRank) {
		Map<Integer, Set<Long>> treeEdgesByRank = gatherTreeEdgesByRank(rels);
		TLongBitArraySet mrcaIds = gatherMrcaIds(rels);
		return overlaps(treeEdgesByRank, treeEdgesByRankBestSetForCurrentRank, mrcaIds, mrcaIdsBestSetForCurrentRank, workingRank);
	}

	private boolean overlaps(
			Map<Integer, Set<Long>> treeEdgesByRankA,
			Map<Integer, Set<Long>> treeEdgesByRankB,
			TLongBitArraySet mrcaIdsA,
			TLongBitArraySet mrcaIdsB,
			int workingRank) {
			
		// check for node overlap
		boolean containsAny = mrcaIdsA.containsAny(mrcaIdsB);

		if (! containsAny) {
			// check for stree edge id overlap
			for (int rank : treeEdgesByRankA.keySet()) {
				if (rank <= workingRank) { continue; } // should this be < instead of <= ?
				for (long edgeId : treeEdgesByRankA.get(rank)) {
					if (treeEdgesByRankB.containsKey(rank) && treeEdgesByRankB.get(rank).contains(edgeId)) {
						containsAny = true;
						break;
					}
				}
			}
		}
		
		return containsAny;
	}
	
	/**
	 * Basically, check if the incoming set of relationships can be added to toUpdate. The incoming set will be
	 * added as long as it does not *partially* overlap with any rels already in toUpdate. A proposed set
	 * Y overlaps with an existing rel X if the synthesis subtree of any rel in Y contains any nodes that are in
	 * the synthesis subtree below X, or if the subtree of any rel in Y contains any edges that represent the same
	 * source tree edge as any rel in the subtree of X. Y completely contains X if the set of all nodes contained in
	 * subtrees of Y contains *all* the nodes in the subtree of X, and if all set set of all source tree edges
	 * represented by rels in the subtrees of all rels in Y contains all the source tree edges represented by rels
	 * in the subtree of X.<br><br>
	 * If there are no rels in Y that partly (but do not completely) overlap with any rels in toUpdate, then the rels
	 * in Y will be added to toUpdate. In this case, any rels in toUpdate that are contained by Y will be removed
	 * from toUpdate.
	 * @param proposed
	 * @param toUpdate
	 */
	private Set<Relationship> updateSet(Node n, Set<Relationship> proposed, Set<Relationship> toUpdate, int workingRank) {

		if (proposed.size() < 1) { return toUpdate; }
		
		// accumulate all the stree edges that would be represented in the subtree below the proposed rel set
		Map<Integer, Set<Long>> proposedInclTreeEdgesByRank = gatherTreeEdgesByRank(proposed);
		TLongBitArraySet mrcaIdsProposed = gatherMrcaIds(proposed);
		
		if (VERBOSE) { print("assessing proposed set ", proposed, ":", proposedInclTreeEdgesByRank); }
		
		Set<Relationship> containedByProposed = new HashSet<Relationship>();
		boolean addProposed = true;
		
		for (Relationship s : toUpdate) {
			
			Map<Integer, Set<Long>> savedTreeEdgesByRank = treeEdgesForRankForProposedChildNode.get(s.getStartNode());
			
			if (VERBOSE) { print("checking for overlap with", s, ":", savedTreeEdgesByRank); }
			
			boolean proposedContainsAny = mrcaIdsProposed.containsAny(mrcaTipsAndInternal(s));
			boolean proposedContainsAll = true; // until proven false

			// check for overlap between the proposed set and each rel in toUpdate
			// exit early if the proposed set contains some but not all of any rel in toUpdate (fail condition)
			for (int rank : savedTreeEdgesByRank.keySet()) {
				if (rank <= workingRank) { continue; }
				
				for (long edgeId : savedTreeEdgesByRank.get(rank)) {
					if (proposedInclTreeEdgesByRank.containsKey(rank) && proposedInclTreeEdgesByRank.get(rank).contains(edgeId)) {
						proposedContainsAny = true;
						if (! proposedContainsAll) { break; } 
					} else {
						proposedContainsAll = false;
						if (proposedContainsAny) { break; }
					}
				}
			}
			
			if (proposedContainsAll) {
				containedByProposed.add(s);
				if (VERBOSE) { print("proposed set", proposed, "contains all of previously saved", s); }
			} else if (proposedContainsAny) {
				addProposed = false;
				if (VERBOSE) { print("proposed set", proposed, "overlaps with but does not contain all of previously saved", s); }
				break;
			} 
		}
		
		Set<Relationship> updated;
		if (addProposed) {
			if (VERBOSE) { print("did not find any overlapping but not completely contained rels. proposed set", proposed, "will", (containedByProposed.size() > 0 ? "replace " + containedByProposed : "be added to set")); }
			updated = proposed;
			for (Relationship r : toUpdate) {
				if (! containedByProposed.contains(r)) {
					updated.add(r);
				}
			}
		} else {
			if (VERBOSE) { print("set will not be updated."); }
			updated = toUpdate;
		}

//		if (VERBOSE) { print(); }
		return updated;
	}

	/**
	 * 
	 */
	private void collectCoveredTreeEdges(Node parent, Set<Node> children) {

		for (Node child : children) {

			Map<Integer, Set<Long>> currNodeEdgesForRank = new HashMap<Integer, Set<Long>>();

			// add *all* the stree/tax edges congruent with this rel (not just this rel, but parallel rels as well)
			for (Relationship r : getRelationshipsFromTo(child, parent, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {
				int rank = rank(r);
				updateSetMap(rank, currNodeEdgesForRank);
				currNodeEdgesForRank.get(rank).add(edgeId(r));
			}
			
			// accumulate stree edges represented by this node's children (which contain all their children's edges, etc.)
			for (Entry<Integer, Set<Long>> childEdgesForRank : treeEdgesForRankBelowFinishedNode.get(child).entrySet()) {
				int rank = childEdgesForRank.getKey();
				updateSetMap(rank, currNodeEdgesForRank);
				currNodeEdgesForRank.get(rank).addAll(childEdgesForRank.getValue());
			}
			treeEdgesForRankForProposedChildNode.put(child, currNodeEdgesForRank);
		}
	}

	
	/**
	 * For tie breaking, we will accumulate a count, for each source tree, of the number of edges from that
	 * source tree that are represented within the synthesized subtree below each node as we finish it.
	 * 
	 * @param n
	 * @param bestRels
	 */
	private void recordCoveredTreeEdges(Node n, Set<Relationship> bestRels) {

		print("recording covered source tree edges");
		
		Map<Integer, Set<Long>> currNodeEdgesForRank = new HashMap<Integer, Set<Long>>();
		for (Relationship r : bestRels) {

			Node child = r.getStartNode();

			// TODO: change this to just use the information gathered by collectCoveredTreeEdges
			/*
			// add *all* the stree/tax edges congruent with this rel (not just this rel, but parallel rels as well)
			for (Relationship p : getRelationshipsFromTo(child, n, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {
				print("on rel", p, ":", rank(p), ":", edgeId(p));
				int rank = rank(p);
				updateSetMap(rank, currNodeEdgesForRank);
				currNodeEdgesForRank.get(rank).add(edgeId(p));
			}

			// accumulate stree edges represented by this node's children (which contain all their children's edges, etc.)
			for (Entry<Integer, Set<Long>> childEdgesForRank : treeEdgesForRankBelowFinishedNode.get(child).entrySet()) {
				int rank = childEdgesForRank.getKey();
				updateSetMap(rank, currNodeEdgesForRank);
				currNodeEdgesForRank.get(rank).addAll(childEdgesForRank.getValue());
			} */
			for (Entry<Integer, Set<Long>> childEdgesForRank: treeEdgesForRankForProposedChildNode.get(child).entrySet()) {
				int rank = childEdgesForRank.getKey();
				updateSetMap(rank, currNodeEdgesForRank);
				currNodeEdgesForRank.get(rank).addAll(childEdgesForRank.getValue());
			}
		}
		
		treeEdgesForRankBelowFinishedNode.put(n, currNodeEdgesForRank);
		if (VERBOSE) { print(n + " contains source tree edges (by rank) " + treeEdgesForRankBelowFinishedNode.get(n), "\n"); }
	}
	
	/**
	 * trivial convenience function for code simplification.
	 * @param v
	 * @param m
	 */
	private void updateSetMap(int v, Map<Integer, Set<Long>> m) {
		if (! m.containsKey(v)) { m.put(v, new HashSet<Long>()); }
	}
	
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