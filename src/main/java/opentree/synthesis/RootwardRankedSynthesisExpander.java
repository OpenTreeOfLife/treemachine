package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opentree.constants.RelType;
import opentree.synthesis.mwis.TopologicalOrder;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;
import org.opentree.bitarray.TLongBitArraySet;
import org.opentree.graphdb.GraphDatabaseAgent;

public class RootwardRankedSynthesisExpander extends RootwardSynthesisExpander {

	private boolean VERBOSE = true;

	public RootwardRankedSynthesisExpander(Node root) {

		// TODO: the topological order will die if we have cycles. 
		// first we need to find strongly connected components (SCCs) and identify edges
		// whose exclusion will remove the cycles. use TarjanSCC to find the SCCs.
		
		G = new GraphDatabaseAgent(root.getGraphDatabase());
		topologicalOrder = new TopologicalOrder(G, RelType.STREECHILDOF, RelType.TAXCHILDOF);

		childRels = new HashMap<Long, HashSet<Relationship>>();

		// holds the ids of *all* the descendant nodes (not just terminals)
		nodeMrca = new HashMap<Long, TLongBitArraySet>();
		
		for (Node n : topologicalOrder) {

			// get all the incoming rels and group them by sourcerank and sourceedgeid
			Map<Integer, Map<Long, Set<Relationship>>> relsByRankAndEdgeId = new HashMap<Integer, Map<Long, Set<Relationship>>>();
			Set<Integer> observedRanks = new HashSet<Integer>();
			for (Relationship r : getALLStreeAndTaxRels(n)) {
				int rank = rank(r);
				if (! relsByRankAndEdgeId.containsKey(rank)) {
					relsByRankAndEdgeId.put(rank, new HashMap<Long, Set<Relationship>>());
				}
				long edgeId = edgeId(r);
				if (! relsByRankAndEdgeId.get(rank).containsKey(edgeId)) {
					relsByRankAndEdgeId.get(rank).put(edgeId, new HashSet<Relationship>());
				}
				relsByRankAndEdgeId.get(rank).get(edgeId(r)).add(r);
			}
			List<Integer> sortedRanks = new ArrayList<Integer>(observedRanks);
			Collections.sort(sortedRanks);
			Collections.reverse(sortedRanks);
			
			// pick the best single rel from each set in order of source rank
			List<Relationship> bestRels = new ArrayList<Relationship>();
			for (int workingRank : observedRanks) {
				Map<Long, Set<Relationship>> sourceEdgeSets = relsByRankAndEdgeId.get(workingRank);
				Relationship best = null;
				for (Set<Relationship> candidates : sourceEdgeSets.values()) { // for each child edge of this node in the source tree
					for (Relationship r : candidates) { // for each graph edge mapped to this child edge
						if ((best == null || ancestorOf(best, r)) && ! overlapsWith(r, bestRels)) {
							best = r;
						}
					}
				}
				if (best != null) {
					bestRels.add(best);
				}
			}
			
			recordRels(n, bestRels);
		}
	}
	
	/**
	 * Returns true if and only if r contains no descendant tips that are shared by any of the rels in
	 * others.<br><br>
	 * 
	 * This depends on the topological order--this will throw NullPointerException if the topological order
	 * has not been observed and an attempt is made to check for inclusion of a child that has not yet been
	 * visited.
	 * 
	 * @param r
	 * @param others
	 * @return
	 */
	boolean overlapsWith(Relationship r, Iterable<Relationship> others) {
		boolean result = true;
		for (Relationship s : others) {
			if (mrcaTipsAndInternal(s.getId()).containsAny(mrcaTipsAndInternal(r.getId()))) {
				result = false;
				break;
			}
		}
		return result;
	}
	
	/**
	 * Is anc an ancestor of desc? Returns true if and only if following edge anc necessarily leads to the
	 * inclusion in the synthetic tree of (the child node of) desc.<br><br>
	 * 
	 * This depends on the topological order--this will throw NullPointerException if the topological order
	 * has not been observed and an attempt is made to check for inclusion of a child that has not yet been
	 * visited.
	 * 
	 * @param r
	 * @param s
	 */
	boolean ancestorOf(Relationship desc, Relationship anc) {
		return mrcaTipsAndInternal(anc.getId()).contains(desc.getStartNode().getId());
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
