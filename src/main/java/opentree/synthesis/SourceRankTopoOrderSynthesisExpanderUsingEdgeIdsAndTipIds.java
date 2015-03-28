package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.opentree.utils.GeneralUtils.print;
import static org.opentree.utils.GeneralUtils.getRelationshipsFromTo;
import opentree.constants.NodeProperty;
import opentree.constants.RelProperty;
import opentree.constants.RelType;
import opentree.synthesis.CartesianProduct.PrunableCPSupersetIterator;

import org.mortbay.log.Slf4jLog;
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

public class SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds extends TopologicalOrderSynthesisExpander {

	Map<Integer, Map<Long, EdgeSet>> edgeSetsByRankAndEdgeId;
	Set<Node> children;
	Set<Integer> observedRanks;
	CandidateRelSet bestSet;
	CandidateRelSet bestSetForCurrentRank;
	
	/**
	 * Stores the accumulated information about each completed synthesis subtree that could still be included as
	 * a subtree of some other node (i.e. not all its potential parents have been visited). Does *not* get
	 * reinitialized!
	 */
	Map<Node, SynthesisSubtreeInfo> availableSubtrees = new HashMap<Node, SynthesisSubtreeInfo>();
	// TODO: could use a TreeMap to save memory (probably not a huge amount). Would need to create a ComparableNode
	// class that implemented the Comparable interface in order to do this, the straight neo4j Node class does not.
	
	/**
	 * Stores the set of nodes which have been completely finished. We use this to keep track of nodes that we've
	 * done so we can remove information from the maps for nodes that will never be visited again. Does *not* get
	 * reinitialized!
	 */
	Set<Node> finishedNodes = new HashSet<Node>();

	/**
	 * Stores the accumulated information about the possible synthesis subtrees that could be included as
	 * children of the node currently being examined for synth. This gets reinitialized for each node we visit.
	 */
	Map<Node, SynthesisSubtreeInfo> immediateSubtrees;
	
	public SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds(Node root) {
		VERBOSE = true;
		synthesizeFrom(root);
		System.out.println("using edge ids *and* tip ids.");
	}
	
	private abstract class RelSet implements Set<Relationship> {
		
		SynthesisSubtreeInfo info;
		Set<Relationship> rels;
		
		public SynthesisSubtreeInfo info() {
			return info;
		}

		@Override
		public int size() {
			return rels.size();
		}

		@Override
		public boolean isEmpty() {
			return rels.isEmpty();
		}

		@Override
		public boolean contains(Object o) {
			return rels.contains(o);
		}

		@Override
		public Iterator<Relationship> iterator() {
			return rels.iterator();
		}

		@Override
		public Object[] toArray() {
			return rels.toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return rels.toArray(a);
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends Relationship> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * A set of relationships that are assumed to share the same parent node. This class is used to assess potential 
	 * sets of rels that are being considered for inclusion in synthesis.
	 * @author cody
	 *
	 */
	private class CandidateRelSet extends RelSet {
		
		private final Node parent;
		
		public CandidateRelSet(Node n) {
			rels = new HashSet<Relationship>();
			info = new SynthesisSubtreeInfo(n);
			parent = n;
		}
		
		public CandidateRelSet(Iterable<Relationship> rels) {	
			this.rels = new HashSet<Relationship>();
			Iterator<Relationship> relIter = rels.iterator();
			if (relIter.hasNext()) {
				Relationship first = relIter.next();
				parent = first.getEndNode();
				info = new SynthesisSubtreeInfo(parent);
				add(first);
			} else {
				throw new IllegalArgumentException();
			}
				
			while (relIter.hasNext()) {
				add(relIter.next());
			}
		}

		@Override
		public boolean add(Relationship r) {
			validate(r);
			info.include(r.getStartNode());
			return rels.add(r);
		}
		
		private void validate(Relationship r) {
			assert r != null;
			assert r.getEndNode().equals(parent);
		}
		
		@Override
		public String toString() {
			return rels.toString();
		}
	}
	
	/**
	 * A set of relationships all mapped to the same branch in some source tree. These must all point to the same 
	 * parent, and originate at different children. (Note that there may be other rels in the db that are mapped to
	 * the same source tree edge but point to different parents, but these are outside of our scope here.)
	 */
	private class EdgeSet extends RelSet {
		
		private final Node parent;
		private final int rank;
		private final long edgeId;
		
		public EdgeSet(Node parent, int rank, long edgeId) {
			this.rank = rank;
			this.edgeId = edgeId;
			this.parent = parent;
			info = new SynthesisSubtreeInfo(parent);
			rels = new HashSet<Relationship>();
		}

		/*
		public EdgeSet(Iterable<Relationship> rels) {
			this.rels = new HashSet<Relationship>();

			Iterator<Relationship> relIter = rels.iterator();
			if (relIter.hasNext()) {
				Relationship first = relIter.next();
				rank = SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds.rank(first);
				edgeId = SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds.edgeId(first);
				parent = first.getEndNode();
				info = new SynthesisSubtreeInfo(parent);
				add(first);
			} else {
				throw new IllegalArgumentException();
			}
				
			while (relIter.hasNext()) {
				add(relIter.next());
			}
		} */
		
		private void validate(Relationship r) {
			assert r != null;
			assert rank == SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds.rank(r);
			assert edgeId == SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds.edgeId(r);
			assert r.getEndNode().equals(parent);
		}

		@Override
		public boolean add(Relationship r) {
			validate(r);
			info.include(r.getStartNode());
			return rels.add(r);
		}
				
		/**
		 * The rank of the original source tree branch represented by the rels in this set.
		 * @return
		 */
		public int rank() {
			return rank;
		}
		
		/**
		 * The edge id of the original source tree branch represented by the rels in this set.
		 * @return
		 */
		public long edgeId() {
			return edgeId;
		}
		
		/*
		 * The parent node of the edges in this set (they must all have the same parent).
		 * @return
		 *
		public Node parent() {
			return parent;
		} */
		
		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append(rels);
			s.append(": edge id = " + edgeId());
			s.append(", rank = " + rank());
			return s.toString();
		}
	}
	
	private class SynthesisSubtreeInfo {
		
		private MutableCompactLongSet includedNodeIds = new MutableCompactLongSet();
		private Map<Integer, Set<Long>> edgeIdsByRank = new HashMap<Integer, Set<Long>>();
		private Map<Integer, Set<LongSet>> tipIdSetsByRank = new HashMap<Integer, Set<LongSet>>();
		private boolean finalized = false;
		
		/**
		 * The root of this synthesis subtree.<br><br>
		 * 
		 * This is used as a reference point when adding info by calling 
		 * include(Node child)--source tree info is extracted from the rels between the root and the specified
		 * child. If the child does not have a rel to the root, then an error is thrown.<br><br>
		 * 
		 * If the finalize() method has been called on this synthesis subtree info object, then the root is
		 * included in the information returned about the subtree.
		 */
		Node root;
		
		public SynthesisSubtreeInfo(Node root) {
			this.root = root;
		}

		public SynthesisSubtreeInfo(Node root, Node child) {
			this.root = root;
			include(child);
		}

		/** accumulate stree edge and tip id represented in the specified synth subtree info object */
		private void accumulate(SynthesisSubtreeInfo s) {
			for (int rank : s.ranksForIncludedEdges()) {
				updateSetMap(rank, edgeIdsByRank);
				edgeIdsByRank.get(rank).addAll(s.edgeIdsForRank(rank));
			}
			for (int rank : s.ranksForIncludedTips()) {
				updateLongSetMap(rank, tipIdSetsByRank);
				tipIdSetsByRank.get(rank).addAll(s.tipIdSetsForRank(rank));
			}
			includedNodeIds.addAll(s.includedNodeIds());
		}

		public void include(Node child) {
			
			if (finalized) { throw new UnsupportedOperationException(); }
			
			boolean hasPath = false;
			
			// add information for *all* the stree/tax edges parallel with this rel
			for (Relationship s : getRelationshipsFromTo(child, root, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {

				hasPath = true;
				
				// avoid rels excluded by cycle removal? it's not clear whether we actually want to do avoid these here.
				// visiting these rels here just records information about source tree edges, and should not introduce
				// technical errors in the procedure. but it could do weird things to make synth decisions based on
				// information about source trees from rels that we're not actually including in the synthesis. so for
				// now i am leaving this here.
				if (excludedRels.contains(s)) { continue; } 

				int rank = rank(s);
				if (childIsTip(s)) {
					updateLongSetMap(rank, tipIdSetsByRank);
					tipIdSetsByRank.get(rank).add(exclusiveMrca(s));
				} else {
					updateSetMap(rank, edgeIdsByRank);
					edgeIdsByRank.get(rank).add(edgeId(s));
				}
				
				// gather information already stored for the completed subtree below this rel
				accumulate(completedSubtree(s));
			}
			
			if (! hasPath) {
				throw new IllegalArgumentException("cannot include child " + child + "--there are no rels connecting to the root " + root + " of this synthesis subtree info object");
			}
		}
		
		public boolean overlapsWith(SynthesisSubtreeInfo that, int workingRank) {
			return includedNodeIds.containsAny(that.includedNodeIds) ? true : containsAnyStreeElementsOf(that, workingRank); 
		}
		
		public boolean containsAnyStreeElementsOf(SynthesisSubtreeInfo that, int workingRank) {
			boolean containsAny = false;
			for (int rank : ranksForIncludedEdges()) {
				if (rank < workingRank || ! that.edgeIdsByRank.containsKey(rank)) { continue; } // should this be <=? | 2015 03 28: no, i don't think so
				for (long edgeId : that.edgeIdsForRank(rank)) {
					if (this.edgeIdsForRank(rank).contains(edgeId)) {
						containsAny = true;
						break;
					}
				}
			}
			if (! containsAny) {
				outer:
				for (int rank : ranksForIncludedTips()) {
					if (rank < workingRank || ! that.tipIdSetsByRank.containsKey(rank)) { continue; } // should this be <=? | 2015 03 28: no, i don't think so
					
					// TODO: This is quadratic and could be slow for large numbers of tips, especially
					// for tips mapped to higher taxa. Should try to do something better here.
					
					// also, this may be wrong. right now we are checking just for tips between two trees from just one rank.
					// but it could be that we should be checking if this contains any tips from *any* tree of rank higher
					// than working rank that are also in *any* tree contained by that.
					
					for (LongSet thoseTips : that.tipIdSetsForRank(rank)) {
						for (LongSet theseTips : this.tipIdSetsForRank(rank)) {
							if (theseTips.containsAny(thoseTips)) {
								containsAny = true;
								break outer;
							}
						}
					}
				}
			}
			return containsAny;
		}
		
		public boolean improvesUpon(SynthesisSubtreeInfo that, int testRank) {
			if (that == null) { throw new NullPointerException(); }

			if (VERBOSE) { print("X =", this, "and\nY =", that); }

			// until we have enough info to either accept or reject this candidate, or we run out of trees to use
			Boolean result = null;
			while (result == null && testRank > 0) {
				int candidateScore = this.edgeIdsByRank.containsKey(testRank) ? this.edgeIdsForRank(testRank).size() : 0;
				int bestScore = that.edgeIdsByRank.containsKey(testRank) ? that.edgeIdsForRank(testRank).size() : 0;
				if (VERBOSE) { print("for tree of rank", testRank + ", X includes", candidateScore, "edges; Y includes", bestScore, "edges."); }

				if (candidateScore > bestScore) {
					if (VERBOSE) { print("X contains more edges from tree of rank", testRank, "than Y. preferring X."); }
					result = true;
				} else if (candidateScore < bestScore) {
					if (VERBOSE) { print("X contains fewer edges from tree of rank", testRank, "than Y. preferring Y."); }
					result = false;
				} else if (candidateScore == bestScore) {
					if (VERBOSE) { print("X and Y contain the same number of edges from tree of rank", testRank + "; moving on to next lowest rank..."); }
					testRank--;
				}
			}
			
			if (result == null) { // we ran out of trees without being able to break the tie; try to use candidate size
				long candidateSize = this.includedNodeIds().size();
				long bestSize = that.includedNodeIds().size();
				if (VERBOSE) { print("all ranks have been checked. both and X and Y contain the same number of tree edges for all trees.\nin terms of total included graph nodes, X contains", candidateSize, "nodes; Y contains", bestSize, "nodes."); }

				if (candidateSize > bestSize) {
					if (VERBOSE) { print("X includes more nodes than Y. preferring X."); }
					result = true;
				} else if (candidateSize < bestSize) {
					if (VERBOSE) { print("X includes fewer nodes than Y. preferring Y."); }
					result = false;
				} else {
					if (VERBOSE) { print("X and Y include the same number of graph nodes and the same number of tree edges for all trees. arbitrarily preferring Y."); }
					result = false; // arbitrary! logically just as justifiable as true
				}
			}
			return result;
		}
		
		/*
		 * returns -1 if the info object contains no edges or tips
		 *
		public int maxExemplifiedTreeRank() {
			int maxEdgeRank = ranksForIncludedEdges().isEmpty() ? -1 : new TreeSet<Integer>(ranksForIncludedEdges()).last();
			int maxTipRank = ranksForIncludedTips().isEmpty() ? -1 : new TreeSet<Integer>(ranksForIncludedTips()).last();
			return Math.max(maxEdgeRank, maxTipRank);
		} */
				
		public boolean containsAllStreeElementsOf(SynthesisSubtreeInfo that, int workingRank) {
			boolean containsAll = true;
			for (int rank : that.ranksForIncludedEdges()) {
				if (rank < workingRank) { continue; } // should this be <= ?
				if (! this.ranksForIncludedEdges().contains(rank)) {
					containsAll = false;
					print("did not find rank " + rank + " in ranks for edge ids"); // testing
					break;
				}
				for (long edgeId : that.edgeIdsForRank(rank)) {
					if (! this.edgeIdsForRank(rank).contains(edgeId)) {
						containsAll = false;
						print("did not find edge id " + edgeId + " for rank " + rank); // testing
						break;
					}
				}
			}
			if (containsAll) {
				for (int rank : that.ranksForIncludedTips()) {
					if (rank < workingRank) { continue; } // should this be <= ? | 2015 03 28: no, i don't think so.
					
					// NOTE: I think this is as simple as verifying that includedNodeIds for the 
					// proposed containing info object contains (at least one of) all the relevant tree
					// tips from the proposed contained. I don't think we need to check the actual
					// tipsByRank map itself for the proposed containing info object (this)
					
					for (LongSet thoseTips : that.tipIdSetsForRank(rank)) {
						// i think this is contains any, because any kind of overlap means the set can exemplify the same taxa
						// but this could be wrong... should it actually be containsAll?
						if (! this.includedNodeIds.containsAny(thoseTips)) {
							containsAll = false;
							print("did not find any tip node ids in " + thoseTips + " for rank " + rank); // testing
							break;
						}
					}
				}
			}
			return containsAll;
		}
		
		/*
		public int scoreForRank(int rank) {
			int score = 0;
			score = edgeIdsByRank.containsKey(rank) ? edgeIdsByRank.get(rank).size() : 0;
			if (tipIdSetsByRank.containsKey(rank)) {
				for (LongSet s : tipIdSetsByRank.get(rank)) {
					score += s.size();
				}
			}
			return score;
		} */
		
		@Override
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append("included node ids: ");
			s.append(includedNodeIds);
			s.append(", internal edge ids by rank: ");
			s.append(edgeIdsByRank);
			s.append(", tip id sets by rank: ");
			s.append(tipIdSetsByRank);
			return s.toString();
		}
		
		public void finalize() {
			finalized = true;
			includedNodeIds.add(root.getId());
		}
		
		public LongSet includedNodeIds() {
			return includedNodeIds;
		}
		
		public Set<Integer> ranksForIncludedEdges() {
			return edgeIdsByRank.keySet();
		}

		public Set<Integer> ranksForIncludedTips() {
			return tipIdSetsByRank.keySet();
		}

		public Set<LongSet> tipIdSetsForRank(int rank) {
			return tipIdSetsByRank.get(rank);
		}
		
		public Set<Long> edgeIdsForRank(int rank) {
			return edgeIdsByRank.get(rank);
		}
	}
	
	private static LongSet exclusiveMrca(Relationship r) {
		return new ImmutableCompactLongSet((long[]) r.getProperty(RelProperty.EXCLUSIVE_MRCA.propertyName));
	}
	
	private static boolean childIsTip(Relationship r) {
		return r.hasProperty(RelProperty.CHILD_IS_TIP.propertyName) ? (boolean) r.getProperty(RelProperty.CHILD_IS_TIP.propertyName) : false;
	}
	
	@Override
	List<Relationship> selectRelsForNode(Node n) {

		if (VERBOSE) { print("\n==== visiting", n, "=================================================================================\n"); }
		initialize(n);
		
		// get all the incoming rels and group them by sourcerank and sourceedgeid
		boolean hasChildren= false;
		for (Relationship r : availableRelsForSynth(n, RelType.STREECHILDOF)) {
			hasChildren = true;
			processIncomingRel(r);
		}
		if (VERBOSE && hasChildren) { print(); }

		
		Set<Relationship> taxonomySingletonRels = new HashSet<Relationship>();
		Set<Node> taxonomySingletonNodes = new HashSet<Node>();
		for (Relationship r : availableRelsForSynth(n, RelType.TAXCHILDOF)) {
			Node childNode = r.getStartNode();
			if (childNode.hasRelationship(Direction.INCOMING, RelType.TAXCHILDOF, RelType.STREECHILDOF)) {
				processIncomingRel(r);
			} else {
				taxonomySingletonRels.add(r);
				taxonomySingletonNodes.add(childNode);
			}
		}
		
		for (Node child : children) { immediateSubtrees.put(child, new SynthesisSubtreeInfo(n, child)); }

		for (Node child : taxonomySingletonNodes) { immediateSubtrees.put(child, new SynthesisSubtreeInfo(n, child)); }
		
		List<Integer> sortedRanks = new ArrayList<Integer>(observedRanks);
		Collections.sort(sortedRanks);
		Collections.reverse(sortedRanks);
		
		if (VERBOSE) { print("incoming rels will be visited/selected in decreasing ranked order:", sortedRanks); }
		
		// select rels for inclusion in order of source tree rank
		bestSet = new CandidateRelSet(n);
		for (int currentRank : sortedRanks) {

			if (VERBOSE) { print("\nnow working with: rank = " + currentRank); }
			
			// first find edge sets for the current rank that overlap with the previous best set
			// those are the only ones for which we need to check the combinations
			Set<EdgeSet> overlappingSets = new HashSet<EdgeSet>();
			Set<EdgeSet> nonOverlappingSets = new HashSet<EdgeSet>();
			for (EdgeSet edgeSet : edgeSetsByRankAndEdgeId.get(currentRank).values()) {
				if (bestSet.info().overlapsWith(edgeSet.info(), currentRank)) {
					overlappingSets.add(edgeSet);
				} else {
					nonOverlappingSets.add(edgeSet);
				}
			}
			
			// now attempt to augment the previous best rels set with rels from the edge sets for this rank
			bestSetForCurrentRank = bestSet;
			while (overlappingSets.size() > 0) {

				// first see if we can update the best set with any rels from edge sets that overlap with the best set
				if (VERBOSE) { print("\nthe following subtrees overlap with the higher ranked set. checking if any of these can be included:"); for (EdgeSet x : overlappingSets) { print(x, x.info()); }}
				
				// this critter allows us to iterate over sets S_1, S_2, etc from the Cartesian product, stopping short
				// (here called 'pruning') when we encounter any S_i for which we don't want to visit any set S | S.supersetof(S_i).
				PrunableCPSupersetIterator<Relationship> combinations = new CartesianProduct<Relationship>((Set<Set<Relationship>>)(Set<?>)overlappingSets)
						.withMissingElements()
						.prunableIterator();
				while (combinations.hasNext()) {

					Set<Relationship> c = combinations.next();
					if (c.size() < 1) { continue; }
					
					CandidateRelSet proposed = new CandidateRelSet(c);
					
					if (! internallyDisjoint(proposed)) { combinations.prune(); continue; }

					if (VERBOSE) { print("\nbest set so far is:", bestSetForCurrentRank, ":", bestSetForCurrentRank.info(), "\nassessing proposed set", proposed, ":", proposed.info()); }

					// will return null if bestSet cannot be updated by proposed (because of partial overlap)
					CandidateRelSet candidate = (CandidateRelSet) updateSet(n, proposed, bestSet, currentRank);

					// if this set overlaps with bestSet, then so will all of its supersets, so don't visit them
					if (candidate == null) { combinations.prune(); continue; }
					
					// replace the previous best candidate if this one has more rels representing edges from the current ranked tree
					// or if it has the same number of rels from the current ranked tree but contains more nodes
					if (VERBOSE) { print("\nfound a new viable set X to compare to previous best set Y. comparing:\nX =", candidate, "and\nY =", bestSetForCurrentRank); }
					if (candidate.info().improvesUpon(bestSetForCurrentRank.info(), currentRank)) {
						bestSetForCurrentRank = candidate;
					}
				}
			
				// find any previously nonoverlapping sets that still don't overlap with the (now updated) best set
				overlappingSets = new HashSet<EdgeSet>();
				Iterator<EdgeSet> nonOverlappingIter = nonOverlappingSets.iterator();
				while (nonOverlappingIter.hasNext()) {
					EdgeSet edgeSet = nonOverlappingIter.next();
					if (bestSetForCurrentRank.info().overlapsWith(edgeSet.info(), currentRank)) {
						overlappingSets.add(edgeSet);
						nonOverlappingIter.remove();
					} 
				}
				
				// for the edge sets that still don't overlap, find the best individual edge from each set to add to the best set
				if (! nonOverlappingSets.isEmpty()) {
					if (VERBOSE) { print("\nnow attempting to add remaining subtrees that don't overlap with those already selected"); }
					bestSetForCurrentRank = augmentFromNonOverlappingSets(bestSetForCurrentRank, nonOverlappingSets, currentRank);
				}
				
				if (VERBOSE) { print("\nbest set so far is: ", bestSetForCurrentRank, ":", bestSetForCurrentRank.info()); }

				// for the edge sets that do overlap with the best set, repeat the procedure
			}

			// in case there were no overlapping rels, find the best individual edge each edge set
			if (! nonOverlappingSets.isEmpty()) {
				if (VERBOSE) { print("\nno subtrees found that overlap with previously selected subtrees."); }
				bestSet = augmentFromNonOverlappingSets(bestSetForCurrentRank, nonOverlappingSets, currentRank);
			}
		}

		for (Relationship t : taxonomySingletonRels) {
			if (! bestSet.info().overlapsWith(completedSubtree(t), 0)) {
				bestSet.add(t);
			}
		}
		
		bestSet.info().finalize();
		print("\n" + n, "completed. the synthesized subtree below this node contains:\n" + bestSet.info());
		updateCompletedSubtreeInfo(n, bestSet.info());
		return new ArrayList<Relationship>(bestSet);
	}
	
	/**
	 * Trivial convenience method with type checking for map lookups, helps avoid silly errors like attempting
	 * to query the map using a relationship as the key instead of the relationship's start node.
	 * @param r
	 * @return
	 */
	private SynthesisSubtreeInfo completedSubtree(Relationship r) {
		return availableSubtrees.get(r.getStartNode());
	}
	
	/**
	 * Update the subtree info storage when we finish a subtree.
	 * @param r
	 * @return
	 */
	private void updateCompletedSubtreeInfo(Node n, SynthesisSubtreeInfo s) {
		availableSubtrees.put(n, bestSet.info());
		finishedNodes.add(n);
		
		// see if any of this node's children can be removed from the available subtrees map
		for (Relationship r : n.getRelationships(Direction.INCOMING, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {
			Node child = r.getStartNode();

			// check whether all the possible parents of this child have been visited
			boolean allParentsCompleted = true;
			for (Relationship t : child.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {
				if (excludedRels.contains(t)) { continue; }
				Node parent = t.getEndNode();
				if (! finishedNodes.contains(parent)) {
					allParentsCompleted = false;
					break;
				}
			}

			// if there are no unvisited parents, then we will never see this node again, so we can free up space in the map
			if (allParentsCompleted) {
				availableSubtrees.remove(child);
			}
		}
	}
		
	private boolean internallyDisjoint(Set<Relationship> rels) {
		boolean valid = true;
		MutableCompactLongSet cumulativeMrca = new MutableCompactLongSet();
		for (Relationship r : rels) {
			LongSet currMrca = completedSubtree(r).includedNodeIds();
			if (cumulativeMrca.containsAny(currMrca)) {
				valid = false;
				if (VERBOSE) { print("set:", rels, "has internal overlap and will not be considered."); }
				break;
			} else {
				cumulativeMrca.addAll(currMrca);
			}
		}
		return valid;
	}
	
	private CandidateRelSet augmentFromNonOverlappingSets(CandidateRelSet toUpdate, Set<EdgeSet> augmentingSets, int workingRank) {
		
		if (VERBOSE) { print("\nattempting to select the best non-overlapping subtrees from the list:"); }
		
		// first collect the mrca ids for all the edge sets
		List<EdgeSet> augmentingSetList = new ArrayList<EdgeSet>(augmentingSets);
		Map<Integer, LongSet> idsForOverlapping = new HashMap<Integer, LongSet>();
		for (int i = 0; i < augmentingSetList.size(); i++) {
			idsForOverlapping.put(i, augmentingSetList.get(i).info().includedNodeIds());
		}
		if (VERBOSE) { for (int i = 0; i < idsForOverlapping.size(); i++) print(i, "=", augmentingSetList.get(i), augmentingSetList.get(i).info()); }
		
		// isolate overlapping subsets of the incoming rels (use a union-find structure to do this fast)
		// so we only need to propose combinations from within each overlapping subset of edge sets
		WeightedQuickUnionUF overlapping = new WeightedQuickUnionUF(augmentingSetList.size());
		MutableCompactLongSet cumulativeIds = new MutableCompactLongSet();
		for (int i = 0; i < augmentingSetList.size(); i++) {
			if (! idsForOverlapping.containsKey(i)) {
				if (VERBOSE) { print("subtree", i, "has already been added to an overlapping set. moving on..."); }
				continue;
			}
			
			MutableCompactLongSet edgeSetIds = new MutableCompactLongSet(augmentingSetList.get(i).info().includedNodeIds());
			if (VERBOSE) { print("looking for subtrees that overlap with", i); }

			if (cumulativeIds.containsAny(edgeSetIds)) { // this edge set overlaps with at least one other
				if (VERBOSE) { print(i, "overlaps with: "); }
				
				// find the (sets of) other edge sets that this one overlaps with and gather all their ids
				List<Integer> setsToCombine = new ArrayList<Integer>();
				MutableCompactLongSet idsForCombinedSet = new MutableCompactLongSet(idsForOverlapping.get(i));
				for (int setId : overlapping) {
					if (idsForOverlapping.get(setId).containsAny(edgeSetIds)) {
						setsToCombine.add(setId);
						idsForCombinedSet.addAll(idsForOverlapping.get(setId));
					}
					if (VERBOSE) { print(setId + ": cumulative union=", idsForCombinedSet); }
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
		
		// find the best set of non-overlapping rels from each set of overlapping edge sets
		for (Integer setId : overlapping) {
		
			Set<EdgeSet> currOverlappingSetOfAugmentingEdgeSets = new HashSet<EdgeSet>();
			for (Long l : overlapping.getSet(setId)) {
				currOverlappingSetOfAugmentingEdgeSets.add(augmentingSetList.get(l.intValue()));
			}
		
			if (VERBOSE) { print("\nnow picking the best subtree from the overlapping set", currOverlappingSetOfAugmentingEdgeSets); }

			CandidateRelSet bestCandidate = null;
			PrunableCPSupersetIterator<Relationship> combinations = new CartesianProduct<Relationship>((Set<Set<Relationship>>)(Set<?>)currOverlappingSetOfAugmentingEdgeSets)
					.withMissingElements()
					.prunableIterator();
			
			while (combinations.hasNext()) {
				Set<Relationship> c = combinations.next();
				if (c.size() < 1) { continue; }
				
				CandidateRelSet candidate = new CandidateRelSet(c);
				
				if (! internallyDisjoint(candidate)) { combinations.prune(); continue; }

				if (VERBOSE) { print("best candidate from this set so far is Y, new candidate is X. comparing:\nX =", candidate, "and\nY =", bestCandidate); }
				if (bestCandidate == null || candidate.info().improvesUpon(bestCandidate.info(), workingRank)) {
					bestCandidate = candidate;
				}
			}
			if (VERBOSE) { print("overall best candidate =", bestCandidate); }
			
			if (bestCandidate != null) {
				for (Relationship r : bestCandidate) {
					toUpdate.add(r);
					if (VERBOSE) { print("adding", r, "to saved set. saved set is now:", toUpdate); }
				}
			}
		}

		return toUpdate;
	}
	
	private void initialize(Node n) {
		immediateSubtrees = new HashMap<Node, SynthesisSubtreeInfo>();
		edgeSetsByRankAndEdgeId = new HashMap<Integer, Map<Long, EdgeSet>>();
		children = new HashSet<Node>();
		observedRanks = new HashSet<Integer>();
	}
	
	private void processIncomingRel(Relationship r) {
		children.add(r.getStartNode());
		
		int rank = rank(r); // collect the rank and create a map entry if necessary
		observedRanks.add(rank);
		if (! edgeSetsByRankAndEdgeId.containsKey(rank)) {
			edgeSetsByRankAndEdgeId.put(rank, new HashMap<Long, EdgeSet>());
		}

		long edgeId = edgeId(r); // collect the edge id and create a map entry if necessary
		if (! edgeSetsByRankAndEdgeId.get(rank).containsKey(edgeId)) {
			edgeSetsByRankAndEdgeId.get(rank).put(edgeId, new EdgeSet(r.getEndNode(), rank, edgeId));
		}
		
		if (VERBOSE) { print("adding non-singleton", r, " (" + mrcaTipsAndInternal(r) + ") to set of incoming rels. rank =", rank, "edgeid =", edgeId); }
		edgeSetsByRankAndEdgeId.get(rank).get(edgeId(r)).add(r);
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
	private RelSet updateSet(Node n, RelSet proposed, RelSet toUpdate, int workingRank) {

		if (proposed.size() < 1) { return toUpdate; }
		
		Set<Relationship> containedByProposed = new HashSet<Relationship>();
		boolean addProposed = true;
		
		// find rels in the set to be updated that can be equally well represented (i.e. are contained) by the proposed set
		for (Relationship s : toUpdate) {
			
			SynthesisSubtreeInfo subtreeInfoForSavedRel = new SynthesisSubtreeInfo(n, s.getStartNode());			
			
			if (VERBOSE) { print("checking for overlap with", s, ":", subtreeInfoForSavedRel); }

			if (proposed.info().overlapsWith(subtreeInfoForSavedRel, workingRank)) {
				if (proposed.info().containsAllStreeElementsOf(subtreeInfoForSavedRel, workingRank)) {
					containedByProposed.add(s);
					if (VERBOSE) { print("proposed set", proposed, "contains all of previously saved", s); }
				} else {
					addProposed = false;
					if (VERBOSE) { print("proposed set", proposed, "overlaps with but does not contain all of previously saved", s); }
					break;
				}
			} else {
				if (VERBOSE) { print("proposed set", proposed, "does not overlap with previously saved", s); }
			}
		}
		
		RelSet updated = null;
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
		}
		return updated;
	}
	
	/**
	 * trivial convenience function for code simplification.
	 * @param v
	 * @param m
	 */
	private void updateSetMap(int v, Map<Integer, Set<Long>> m) {
		if (! m.containsKey(v)) { m.put(v, new HashSet<Long>()); }
	}

	private void updateLongSetMap(int v, Map<Integer, Set<LongSet>> m) {
		if (! m.containsKey(v)) { m.put(v, new HashSet<LongSet>()); }
	}
	
	private Set<Node> unmarkedForCycle = new HashSet<Node>();
	private Set<Node> temporaryMarkedForCycle = new HashSet<Node>();
	private HashSet<Relationship> excludedRels = new HashSet<Relationship>();

	@Override
	Set<Relationship> breakCycles() {
		System.out.println("breaking cycles");
		boolean breaking = true;
		while(breaking){
			breaking = false;
			unmarkedForCycle = new HashSet<Node>();
			temporaryMarkedForCycle = new HashSet<Node>();
			for (Node n : G.getAllNodes()) {
				if (n.hasRelationship(RelType.STREECHILDOF)) {
					unmarkedForCycle.add(n);
				}
			}

			while (! unmarkedForCycle.isEmpty()) {
				breaking = visitNodeForBreakCycles(unmarkedForCycle.iterator().next(),null);
				if (breaking == true){
					System.out.println("cycle found. Total excluded rels: "+excludedRels.size());
					break;
				}
			}
		}
		//System.out.println("currently not breaking cycles! topological order should fail if it encounters one.");
		return excludedRels;
	}
	

	private boolean visitNodeForBreakCycles(Node n,Node p){
		if (temporaryMarkedForCycle.contains(n)) {
			if(p == null)
				throw new IllegalArgumentException("The graph contains a directed cycle that includes the node: " + n+" with no parent, change implementation");
			else{
				System.out.println("cycle at :"+n+" "+p);
				for(Relationship r: getRelationshipsFromTo(n, p, RelType.STREECHILDOF,RelType.TAXCHILDOF)){
					System.out.println(r.getProperty("source"));
					excludedRels.add(r);
				}
				return true;
			}
		}

		if (unmarkedForCycle.contains(n)) {
			temporaryMarkedForCycle.add(n);
			for (Relationship m : n.getRelationships(Direction.INCOMING, RelType.STREECHILDOF,RelType.TAXCHILDOF)) {
				if(excludedRels.contains(m)==true)
					continue;
				boolean ret = visitNodeForBreakCycles(m.getStartNode(),n);
				if (ret)
					return true;
			}
			unmarkedForCycle.remove(n);
			temporaryMarkedForCycle.remove(n);
		}
		return false;
	}
	
	/**
	 * Get the rank for this relationship relative to relationships from other trees.
	 */
	private static int rank(Relationship r) {
		return isTaxonomyRel(r) ? 0 : (int) r.getProperty("sourcerank");
	}

	/**
	 * Get the unique edge id for this relationship within its source tree. For taxonomy rels, we just use the
	 * database id of the rel (which are unique) since each taxonomy rel is only represented once in the db.
	 */
	private static long edgeId(Relationship r) {
		return isTaxonomyRel(r) ? r.getId() : (int) r.getProperty(RelProperty.SOURCE_EDGE_ID.propertyName);
	}

	/**
	 * A simple helper method for code clarity
	 * @param r
	 * @return
	 */
	private static boolean isTaxonomyRel(Relationship r) {
		// TODO: taxonomy streechildofs don't have this property. this is a temporary property though
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