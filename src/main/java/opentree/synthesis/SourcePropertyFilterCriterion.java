package opentree.synthesis;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;

/**
 * This class makes filtering decisions based on relationship properties as defined in the SourceProperty class, using
 * comparison methods defined in the ComparisonMethod class.
 * 
 * @author cody hinchliff
 *
 */
public class SourcePropertyFilterCriterion implements FilterCriterion {

	private SourceProperty property;
	private FilterComparisonType c;
	private TestValue t;
	private Index<Node> metadataNodeIndex;
	
	/**
	 * Will compare the values of the source property defined by `p` to the test value `t` using the comparison method `c`.
	 * @param property
	 * @param c
	 * @param t
	 */
	public SourcePropertyFilterCriterion(SourceProperty p, FilterComparisonType c, TestValue t, Index<Node> sourceMetaNodes) {
		this.property = p;
		this.c = c;
		this.t = t;
	}
	
	public boolean test(Relationship r) {
		
		Node m1 = metadataNodeIndex.get("source", r.getProperty("source")).getSingle();
		
		SourcePropertyValue v = new SourcePropertyValue(property, m1.getProperty(property.name()));
		
		if (c == FilterComparisonType.EQUALTO) {
			return t.compareTo(v) == 0;

		} else if (c == FilterComparisonType.UNEQUALTO) {
			return t.compareTo(v) != 0;
			
		} else if (c == FilterComparisonType.GREATERTHAN) {
			// do the comparison in reverse, so return the opposite
			return t.compareTo(v) < 0;

		} else if (c == FilterComparisonType.LESSTHAN) {
			// do the comparison in reverse, so return the opposite
			return t.compareTo(v) > 0;

		} else if (c == FilterComparisonType.GREATEROREQUAL) {
			// do the comparison in reverse, so return the opposite
			return t.compareTo(v) <= 0;
			
		} else if (c == FilterComparisonType.LESSOREQUAL) {
			// do the comparison in reverse, so return the opposite
			return t.compareTo(v) >= 0;
			
		} else {
			throw new java.lang.UnsupportedOperationException("the comparison method " + c.toString() + " is not recognized");
		}
	}
	
	public String getDescription() {
		return "source property " + property.propertyName + " " + c.name() + " " + t.getValue();
	}
}
