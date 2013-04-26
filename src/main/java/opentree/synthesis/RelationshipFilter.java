package opentree.synthesis;

import java.util.LinkedList;

import org.neo4j.graphdb.Relationship;

/**
 * This class performs filtering on candidate relationships during synthesis. It accepts any number
 * of FilterCriterion objects, to which it refers the actual filtering decisions. Filters are applied in the order
 * added, so it is better (faster) to add more exclusive filters first, to reduce the number of comparisons.
 * 
 * @author cody hinchliff
 *
 */
public class RelationshipFilter {

	private LinkedList<FilterCriterion> filters;
	
	public RelationshipFilter () {
		filters = new LinkedList<FilterCriterion>();
	}

	/**
	 * Will return an iterable containing all incoming STREECHILDOF relationships for `n` that pass the filter tests.
	 * @param n
	 * @return acceptedRels
	 */
	public Iterable<Relationship> filterRelationships(Iterable<Relationship> incomingRels) {

		LinkedList<Relationship> acceptedRels = new LinkedList<Relationship>();

		// only evaluate the immediate child relationships
		for (Relationship r : incomingRels) { 
			for (FilterCriterion fc : filters) {
				if (fc.test(r) == false) {
					break;
				}
			}
			acceptedRels.add(r);
		}

		return acceptedRels;
	}

	/**
	 * Add the indicated criterion to the list of filters that will be applied. Filters are applied in the order added,
	 * so it is better (faster) to add more exclusive filters first, to reduce the number of comparisons.
	 * @param fc
	 */
	public RelationshipFilter addCriterion(FilterCriterion fc) {
		filters.add(fc);
		return this;
	}
	
	/**
	 * Just return a description of this filter will be applied
	 * @return
	 */
	public String getDescription() {
		String description = "Relationships will be accepted only if they meet the following criteria:\n";
		for (FilterCriterion fc : filters) {
			description = description.concat(fc.getDescription()+"\n");
		}
		return description;
	}
}