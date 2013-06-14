package opentree.synthesis;

import java.util.HashSet;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

/**
 * This class makes filtering decisions based on relationship properties as defined in the SourceProperty class, using
 * comparison methods defined in the ComparisonMethod class.
 * 
 * @author cody hinchliff and esteban 
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
		metadataNodeIndex = sourceMetaNodes;
	}
	
	public boolean test(Relationship r) {
		//TODO: there can be multiple given multiple LICA mappings 
//		Node m1 = metadataNodeIndex.get("source", r.getProperty("source")).getSingle();
		if(r.hasProperty("source") == false){
			return false;
		}
		//System.out.println(r+" "+r.getProperty("source"));
		IndexHits<Node> ih = metadataNodeIndex.get("source", r.getProperty("source"));
		if(ih.size() == 0){
			return false;
		}
		Node m1 = ih.next();
		ih.close();
		if (m1.hasProperty(property.propertyName) == false){
			return false;
		}
		SourcePropertyValue v = new SourcePropertyValue(property, m1.getProperty(property.propertyName));
		
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
			
		}else if (c == FilterComparisonType.CONTAINS) {
			return t.compareTo(v) == 1;
		} else {
			throw new java.lang.UnsupportedOperationException("the comparison method " + c.toString() + " is not recognized");
		}
	}
	
	public String getDescription() {
		return "source property " + property.propertyName + " " + c.name() + " " + t.getValue();
	}
}
