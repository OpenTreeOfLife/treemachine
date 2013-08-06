package opentree.synthesis.filtering;

import org.neo4j.graphdb.Relationship;

/**
 * This class is used to specify criteria that will be used during filtering. It requires a FilterDirective and a FilterTest.
 * Relationships will be tested against the test and will be included or excluded based on the specified criterion.
 * 
 * @author senor hinchliff and esteban 
 *
 */
public class FilterCriterion {

	private Directive d;
	private Test t;
	
	/**
	 * Will test relationships against the test `t` and then validate them based on the directive `d`. Returns true for relationships
	 * that pass this filter criterion, and false for those that do not.
	 * @param d
	 * @param t
	 */
	public FilterCriterion(Directive d, Test t) {
		this.d = d;
		this.t = t;
	}
	
	public boolean validate(Relationship r) {

		boolean relPassesTest = t.test(r);

		if (d == Directive.INCLUDE) {
			// include the rel if it passed the test
			return relPassesTest;

		} else if (d == Directive.EXCLUDE) {
			// exclude the rel if it passed the test
			return !relPassesTest;

		} else {
			// if we made it here then the directive is not supported
			throw new java.lang.UnsupportedOperationException("the filter directive " + d + " is not recognized");
		}
	}
	
	public String getDescription() {
		return "rels will be " + d + " if " + t.getDescription();
	}

	public String getReport() {
		return "(report method not yet implemented for filter)";
	}
}
