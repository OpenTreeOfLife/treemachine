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
	
	private void resolveConflicts() {
		if (resolver != null) {
			bestRels = resolver.resolveConflicts(candidateRels);
		} else {
			bestRels = candidateRels;
		}
	}

	/**
	 * Filters and ranks the candidate relationships according to the relationship filter and ranker assigned to this
	 * evaluator, and then picks the best set that do not conflict according to the assigned conflict resolver.
	 * 
	 * @param node
	 * @return best relationship
	 */
	public Iterable<Relationship> evaluateBestPaths(Node node) {
		
		LinkedList<Relationship> allRels = new LinkedList<Relationship>();
		for (Relationship rel : node.getRelationships(Direction.INCOMING, RelTypes.STREECHILDOF)) {
			allRels.add(rel);
		}

		candidateRels = allRels;
		
//		System.out.println("found " + allRels.size() + " relationships");
				
		filter();
		rank();
		resolveConflicts();
		
		return bestRels;
	}
}
