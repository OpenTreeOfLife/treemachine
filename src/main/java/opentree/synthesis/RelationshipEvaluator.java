package opentree.synthesis;

import java.util.LinkedList;

import opentree.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

/**
 * Filters and selects relationships based on the provided RelationshipFilter and RelationshipSelector objects.
 * 
 * @author cody hinchliff
 *
 */
public class RelationshipEvaluator {

	private RelationshipFilter filter;
	private RelationshipRanker ranker;
	private RelationshipConflictResolver resolver;
	private Iterable<Relationship> candidateRels;
	private Iterable<Relationship> bestRels;
	
	public RelationshipEvaluator() {
		this.filter = null;
		this.ranker = null;
		this.resolver = null;
	}
	
	public RelationshipEvaluator setFilter(RelationshipFilter filter) {
		this.filter = filter;
		return this;
	}

	public RelationshipEvaluator setRanker(RelationshipRanker ranker) {
		this.ranker = ranker;
		return this;
	}

	/**
	 * Set the conflict resolution method. For now there is only one method of resolving conflicts:
	 * AcyclicRankPriorityResolution. Other methods could be implemented, e.g. branch and bound method
	 * (which could possibly find better relationships than rank priority), or methods allowing the 
	 * creation of cyclic synthetic graphs.
	 * 
	 * @param resolver
	 * @return RelationshipEvaluator
	 */
	public RelationshipEvaluator setConflictResolver(RelationshipConflictResolver resolver) {
		this.resolver = resolver;
		return this;
	}

	private void filter() {
		if (filter != null) {
			candidateRels = filter.filterRelationships(candidateRels);
		}
	}

	private void rank() {
		if (ranker != null) {
			candidateRels = ranker.rankRelationships(candidateRels);
		}
	}
	
	/**
	 * TODO: this needs to include the branch and bound
	 * TODO: can there be multiple resolvers? If there is no source preference, can there be branch and bound?
	 */
	private void resolveConflicts() {
		if (resolver != null) {
			bestRels = resolver.resolveConflicts(candidateRels);
		} else {
			bestRels = candidateRels;
		}
	}

	/**
	 * Return a textual description of the procedures that will be performed by this synthesis method.
	 * @return description
	 */
	public String getDescription() {
		String desc = "";

		if (filter != null) {
			desc = desc.concat(filter.getDescription() + "\n");
		} else {
			desc = desc.concat("No filtering will be applied\n");
		}

		if (ranker != null) {
			desc = desc.concat(ranker.getDescription() + "\n");
		} else {
			desc = desc.concat("No ranking will be applied (rank will be the order returned by the graph db)\n");
		}

		if (resolver != null) {
			desc = desc.concat(resolver.getDescription() + "\n");
		} else {
			desc = desc.concat("No conflict resolution will be applied (all rels passing filters will be returned)\n");
		}
		
		return desc;
	}
	
	/**
	 * Filters and ranks the candidate relationships according to the relationship filter and ranker assigned to this
	 * evaluator, and then picks the best set that do not conflict according to the assigned conflict resolver.
	 * 
	 * @param node
	 * @return best relationship
	 */
	public Iterable<Relationship> evaluateBestPaths(Node node) {
		
		//TODO: why aren't these filtered at this stage
		//TODO: we should rank at this stage as well
		LinkedList<Relationship> allRels = new LinkedList<Relationship>();
		for (Relationship rel : node.getRelationships(Direction.INCOMING, RelTypes.STREECHILDOF)) {
			allRels.add(rel);
		}

		candidateRels = allRels;
				
		filter();
		//TODO: this is likely to be very slow unless we only rank the relationships that we care about
		//		in other words, we shouldn't rank all of them, just the ones we care about
		rank();
		resolveConflicts();
		
		return bestRels;
	}
}
