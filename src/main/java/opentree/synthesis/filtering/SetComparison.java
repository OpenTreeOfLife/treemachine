package opentree.synthesis.filtering;

/**
 * Comparison methods that are defined for sets of values. Used when creating tests to be applied during filtering.
 * @author cody
 *
 */
public enum SetComparison {

	IS_EQUAL_TO,
	IS_UNEQUAL_TO,
	CONTAINS_ALL,
	CONTAINS_ANY
//	DOES_NOT_CONTAIN // this is equivalent to combining Directive.EXCLUDE with Method.ContainsAny
	
}