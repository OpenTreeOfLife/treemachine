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
import opentree.constants.NodeProperty;
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

	// these get reinitialized for each node we visit
	Map<Integer, Map<Long, Set<Relationship>>> relsByRankAndEdgeId;
	Set<Integer> observedRanks;
	List<Integer> sortedRanks;
	MutableCompactLongSet savedMrcas;
	
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
	 * 
	 * <tt>{ rank -> { relationship -> { exclusive mrcas for this rel }}</tt><br><br>
	 */
	Map<Integer, Map<Relationship, LongSet>> savedRelsByRank = new HashMap<Integer, Map<Relationship, LongSet>>();

	Set<Relationship> remainingRelsForCurrentRank;
	
	@Override
	List<Relationship> selectRelsForNode(Node n) {

		if (VERBOSE) { print("\n==== visiting", n, "=================================================================================\n"); }
		initialize();
		
		// get all the incoming rels and group them by sourcerank and sourceedgeid
		for (Relationship r : getALLStreeAndTaxRels(n)) {
			processIncomingRel(r);
		}
				
		sortedRanks = new ArrayList<Integer>(observedRanks);
		Collections.sort(sortedRanks);
		Collections.reverse(sortedRanks);

		if (VERBOSE) { print("selecting edges in order of ranks:", sortedRanks); }
				
		// select rels for inclusion from each source tree in order of rank
		for (int i = 0; i < sortedRanks.size(); i++) {
			int currentRank = sortedRanks.get(i);
			if (VERBOSE) { print("now working with rank:", currentRank); }

			remainingRelsForCurrentRank = allRelsForRank(currentRank);

			for (int j = 0; j < i; j++) {
				int higherRank = sortedRanks.get(j);

				// check if any rels from this rank can replace any saved higher ranked rels
				findContainedCombinations(currentRank, higherRank);
				findContainedTerminalRels(currentRank, higherRank);

				// record any remaining compatible rels
				addBestNonOverlapping(currentRank);
				
				updateSavedMrcas(currentRank);
			}
		}
		
		Set<Relationship> bestSet = new HashSet<Relationship>();
		for (Map<Relationship, LongSet> savedRels : savedRelsByRank.values()) {
			bestSet.addAll(savedRels.keySet());
		}
		return new ArrayList<Relationship>(bestSet);
	}

	
	private void findContainedCombinations(int currentRank, int higherRank) {

		Map<Relationship, LongSet> savedRelsForHigherRank = savedRelsByRank.get(higherRank);
		
		forRelP:
		for (Relationship p : remainingRelsForCurrentRank) {

			/* &&&
			 * A note on efficiency of combinations: see @@@ below for an alternative case.
			 * 
			 * Here, it is more efficient to iterate over combinations in order from most inclusive to the least,
			 * because (1) we know that all combinations are internally consistent, and (2) if we can approve a more
			 * inclusive combination then we don't need to consider any of its subsets.
			 */

			// replace any combinations of rels from tree(higherRank) that are covered by p
			for (Set<Relationship> combination : new Combinations<Relationship>(getRelsForRank(higherRank)).mostInclusiveFirst()) {
			
				if (containsAllExclusiveMrca(p, higherRank, combination)) {
					// replace the rels in combination with rel p
					
					MutableCompactLongSet cumulativeMrca = new MutableCompactLongSet();
					cumulativeMrca.addAll(exclusiveMrca(p));
					for (Relationship r : combination) {
						// remove all the rels in `combination` from the saved rels, and accumulate all their exclusive mrcas
						cumulativeMrca.addAll(savedRelsForHigherRank.get(r));
						savedRelsForHigherRank.remove(r);
					}
					
					// update the lists
					savedRelsByRank.get(currentRank).put(p, cumulativeMrca);
					remainingRelsForCurrentRank.remove(p);
					break forRelP;
				}
			}
		}
	}
	
	private void findContainedTerminalRels(int currentRank, int higherRank) {

		Map<Relationship, LongSet> savedRelsForHigherRank = savedRelsByRank.get(higherRank);

		forRelP:
		for (Relationship p : remainingRelsForCurrentRank) {
			
			// replace each *single* rel q from the tree with higherRank if nodemrca(p) contains excl_mrca(q)
			Iterator<Entry<Relationship, LongSet>> savedForHigherIter = savedRelsForHigherRank.entrySet().iterator();
			while (savedForHigherIter.hasNext()) {
				LongSet exclusiveMrcaHigher = savedForHigherIter.next().getValue();
				if (mrca(p).containsAll(exclusiveMrcaHigher)) {
					savedForHigherIter.remove();
					MutableCompactLongSet cumulativeMrca = new MutableCompactLongSet();
					cumulativeMrca.addAll(exclusiveMrca(p));
					cumulativeMrca.addAll(exclusiveMrcaHigher);

					// update the lists
					savedRelsByRank.get(currentRank).put(p, cumulativeMrca);
					remainingRelsForCurrentRank.remove(p);
					break forRelP;
				}
			}
		}
	}
		
	private void addBestNonOverlapping(int currentRank) {
			
		Set<Set<Relationship>> edgeSetsForRemainingRels = new HashSet<Set<Relationship>>();
		for (Set<Relationship> edgeSet : relsByRankAndEdgeId.get(currentRank).values()) {

			// identify edge sets that aren't represented by any of the previously saved edges
			boolean edgeSetAlreadyRepresented = false;
			for (Relationship r : edgeSet) {
				if (! remainingRelsForCurrentRank.contains(r)) {
					edgeSetAlreadyRepresented = true; // all the others from this edge set will thus overlap
					break;
				}
			}
			
			// gather suitable rels (i.e. don't overlap with any previously saved) from the unrepresented edge sets
			if (! edgeSetAlreadyRepresented) {
				Set<Relationship> nonOverlappingEdges = new HashSet<Relationship>();
				for (Relationship r : edgeSet) {
					if (! savedMrcas.containsAny(mrca(r))) {
						nonOverlappingEdges.add(r);
					}
				}
				if (nonOverlappingEdges.size() > 0) {
					edgeSetsForRemainingRels.add(nonOverlappingEdges);
				}
			}
			
			/* @@@
			 * A note on efficiency of combinations: see &&& above for an alternative case.
			 * 
			 * Here it is more efficient to iterate over combinations starting with less inclusive ones and pruning
			 * the generator whenever we find a combination of rels that is internally inconsistent (i.e. rel mrcas
			 * overlap). This allows us to stop short of visiting all combinations in the Cartesian product, because
			 * once we've identified an inconsistent combination, we'll never make any more combinations that include
			 * it.
			 */
			
			// pick the best set of non-overlapping rels from the unrepresented edge sets
			Set<Relationship> bestSetNonOverlapping = new HashSet<Relationship>();
			CartesianProduct<Relationship> combinations = new CartesianProduct<Relationship>(edgeSetsForRemainingRels);
			if (VERBOSE) { print("picking best combinations of rels to add from non-overlapping edge sets"); }
			for (Set<Relationship> proposed : combinations.withMissingElements()) {
				
				if (! internallyConsistent(proposed)) { continue; } // TODO: replace this with a prunable cartesian product generator
				
				if (VERBOSE) { print("\nbest set so far is:", bestSetNonOverlapping + ", with total mrca size:", mrca(bestSetNonOverlapping).size()); }

				// replace the previous best candidate if this one has more rels representing edges from the current ranked tree
				// or if it has the same number of rels from the current ranked tree but contains more nodes
				if (mrca(proposed).size() > mrca(bestSetNonOverlapping).size()) {
					if (VERBOSE) { print("found a new best set: ", proposed); }
					bestSetNonOverlapping = proposed;
				}
			}
		}
	}
	
	/**
	 * Compare the accumulated sets of exclusive mrcas from higher ranked trees with the <em>graph node mrca 
	 * property</em> for the given relationship r.<br><br>
	 * 
	 * Note that the exclusive mrca that we are interested in for higher ranked rels here is <strong>not</strong> the
	 * <tt>exclusive_mrca</tt> graph property itself, but rather the union of the ids from this property for the rel
	 * in question along with the corresponding <tt>exclusive_mrca</tt> properties for any even higher-ranked rels
	 * that it may have subsumed. exclusive mrcas that have been accumulated from even higher ranked trees which they
	 * subsumed.<br><br>
	 * 
	 * The notion is that if a rel from the current ranked tree can possibly lead to the same tips that are included
	 * in the higher ranked tree, then it will, because the higher ranked rels (corresponding to branches in the original
	 * higher ranked source tree) will be included in the subtree below the specified lower ranked rel.
	 */
	private boolean containsAllExclusiveMrca(Relationship p, int rankHigher, Set<Relationship> proposedHigher) {

		LongSet mrcaLower = mrca(p);
		
		boolean result = true;
		for (Relationship q : proposedHigher) {
			if (! mrcaLower.containsAll(savedRelsByRank.get(rankHigher).get(q))) {
				result = false;
				break;
			}
		}
		
		return result;
	}
	
	/**
	 * Just get the exclusive mrca property and return a LongSet that contains it.
	 * @param r
	 * @return
	 */
	private LongSet exclusiveMrca(Relationship r) {
		return new ImmutableCompactLongSet((long[]) r.getProperty(NodeProperty.EXCLUSIVE_MRCA.propertyName));
	}
	
	/**
	 * Just get the mrca property and return a LongSet that contains it.
	 * @param r
	 * @return
	 */
	private LongSet mrca(Relationship r) {
		return new ImmutableCompactLongSet((long[]) r.getProperty(NodeProperty.MRCA.propertyName));
	}
	
	/**
	 * Just get the mrca property and return a LongSet that contains it.
	 * @param r
	 * @return
	 */
	private LongSet mrca(Iterable<Relationship> rels) {
		MutableCompactLongSet mrcaIds = new MutableCompactLongSet();
		for (Relationship r : rels) {
			mrcaIds.addAll((long[]) r.getProperty(NodeProperty.MRCA.propertyName));
		}
		return mrcaIds;
	}
	
	/**
	 * Just return a set containing all the STREE rels for this rank that are incoming to the current node.
	 * @param rank
	 * @return
	 */
	private Set<Relationship> allRelsForRank(int rank) {
		Set<Relationship> s = new HashSet<Relationship>();
		for (Set<Relationship> rels : relsByRankAndEdgeId.get(rank).values()) {
			s.addAll(rels);
		}
		return s;
	}
	
	private Set<Relationship> getRelsForRank(int rank) {
		Set<Relationship> rels = new HashSet<Relationship>();
		for (Relationship r : savedRelsByRank.get(rank).keySet()) {
			rels.add(r);
		}
		return rels;
	}
	
	private void updateSavedMrcas(int rank) {
		for (LongSet mrcaIds : savedRelsByRank.get(rank).values()) {
			savedMrcas.addAll(mrcaIds);
		}
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
	
	private void initialize() {
		relsByRankAndEdgeId = new HashMap<Integer, Map<Long, Set<Relationship>>>();
		observedRanks = new HashSet<Integer>();
		savedMrcas = new MutableCompactLongSet();
	}
	
	private void processIncomingRel(Relationship r) {
		
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
	
	@Override
	void breakCycles() {
		System.out.println("currently not breaking cycles! topological order should fail if it encounters one.");
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
		return ! (r.getType() == RelType.TAXCHILDOF);
	}
	
	@Override
	public Iterable<Relationship> expand(Path path, BranchState state) {

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