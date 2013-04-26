package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.neo4j.graphdb.Relationship;

/**
 * This class performs ranking and selection of best candidate relationships during synthesis. It accepts any number
 * of SelectRankingCriterion objects, to which it refers the actual ranking decisions. When more than one Criterion
 * object is supplied, ranking is performed in the order of addition of the Criterion objects. In this case, subsequent
 * ranking steps are only used to break ties resulting from previous ranking steps.
 * 
 * @author cody hinchliff
 *
 */
public class RelationshipRanker {

	ArrayList<RankingCriterion> criteria;
	ArrayList<Relationship> bestRels;
	
	public RelationshipRanker() {
		criteria = new ArrayList<RankingCriterion>();
	}
	
	/**
	 * Add the indicated ranking criterion to the ordered set of criteria used to select rels.
	 * 
	 * @param rankCriterion
	 */
	public RelationshipRanker addCriterion(RankingCriterion rankCriterion) {
		criteria.add(rankCriterion);
		return this;
	}
	
	/**
	 * Rank the relationships in the provided iterable according to the defined criteria, and return
	 * them in ranked order.
	 * 
	 * @param rels
	 * @return rankedRels
	 */
	public Iterable<Relationship> rankRelationships(Iterable<Relationship> rels) {

		ArrayList<Relationship> rankedRels = new ArrayList<Relationship>();
		
		if (criteria.isEmpty()) {
			throw new java.util.NoSuchElementException("no ranking criteria have been assigned");
		}
		
		for (Relationship rel : rels) {
			rankedRels.add(rel);
		}

		for (int i = criteria.size() - 1; i >= 0; i--) {
			// iterate through criteria in reverse order and sort the relationships in that order
			criteria.get(i).sort(rankedRels); // TEST THIS
		}		
		
		return rankedRels;
	}

	public String getDescription() {
		String description = "Relationships will be ranked (rankings listed in order of priority):\n";
		for (RankingCriterion rc : criteria) {
			description = description.concat(rc.getDescription()+"\n");
		}
		return description;
	}
	
	/**
	 * Return the highest ranking relationship from the set of incoming rels. Ranking is performed according to the set of
	 * SelectRankingCriterion objects that have been previously supplied to this RelationshipSelector.
	 * 
	 * THIS PROBABLY SHOULD NOT BE USED, we should probably order and return all relationships so that they can be considered
	 * for conflict tie-breaking.
	 * 
	 * OR maybe we can use select best and select next best to try and identify pairs of relationships for conflict tie-breaking,
	 * to try and avoid the need to rank everything. Not sure if this would be faster.
	 * 
	 * @param rels
	 * @return bestRel
	 */
	@Deprecated
	public Iterable<Relationship> selectBest(Iterable<Relationship> rels) {

		if (criteria.isEmpty()) {
			throw new java.util.NoSuchElementException("cannot select when no ranking criteria have been defined");
		}
		
		// put first rel in bestRels
		Iterator<Relationship> relsIter = rels.iterator();
		if (relsIter.hasNext()) {
			bestRels.add(relsIter.next());
		} else {
			throw new java.lang.IllegalArgumentException("no relationships contained in the incoming set");
		}

		// compare all rels using first criterion
		while (relsIter.hasNext()) {

			Relationship thisRel = relsIter.next();

			if (criteria.get(0).compare(thisRel, bestRels.get(0)) == 1) {
				// the next rel is better
				bestRels.clear();

			} else if (criteria.get(0).compare(thisRel, bestRels.get(0)) == -1) {
				// the current bestRels are better
				continue;
			}
			
			// if we got here, the next rel is either as good or better than the previous bestRels
			bestRels.add(thisRel);		
		}

		// for each additional ranking criterion
		for (int i = 1; i < criteria.size(); i++) {
			
			if (bestRels.size() == 1) {
				// no more ranking decisions to be made
				break;
			}

			// copy previous bestRels
			ArrayList<Relationship> prevBest = new ArrayList<Relationship>(bestRels);
			bestRels.clear();

			// find new best rels using current criterion
			bestRels.add(prevBest.get(0));
			for (int j = 1; j < prevBest.size(); j++) {
				
				if (criteria.get(i).compare(prevBest.get(j), bestRels.get(0)) == 1) {
					// the next rel is better
					bestRels.clear();

				} else if (criteria.get(0).compare(prevBest.get(j), bestRels.get(0)) == -1) {
					// the current bestRels are better
					continue;
				}

				bestRels.add(prevBest.get(j));
			}
		}
		
		return bestRels;		
	}
}