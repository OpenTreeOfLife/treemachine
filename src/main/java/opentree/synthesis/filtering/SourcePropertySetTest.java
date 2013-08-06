package opentree.synthesis.filtering;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import opentree.constants.SourceProperty;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

import scala.actors.threadpool.Arrays;

/**
 * This class makes filtering decisions based on relationship properties as defined in the SourceProperty class, using
 * comparison methods defined in the ComparisonMethod class.
 * 
 * @author senor hinchliff and esteban 
 *
 */
public class SourcePropertySetTest implements Test {
	
	private SourceProperty property;
	private SetComparison c;
//	private TestValue t;
	private Set<?> t;
	private Index<Node> metadataNodeIndex;
	
	/**
	 * Will compare the values of the source property defined by `p` to the test value `t` using the comparison method `c`, and include or exclude the ones that match based on the directive `d`.
	 * @param <T>
	 * @param property
	 * @param c
	 * @param t
	 */
	public SourcePropertySetTest(Set<?> testValues, SetComparison c, SourceProperty p, Index<Node> sourceMetaNodes) {
		this.property = p;
		this.c = c;
		this.t = testValues;
		metadataNodeIndex = sourceMetaNodes;
	}
	
	@Override
	public boolean test(Relationship r) {
		// there can be multiple given multiple LICA mappings 
		if(r.hasProperty("source") == false){
			return false;
		}
		// System.out.println(r+" "+r.getProperty("source"));
		IndexHits<Node> ih = metadataNodeIndex.get("source", r.getProperty("source"));
		if(ih.size() == 0){
			return false;
		}
		Node m1 = ih.next();
		ih.close();
		if (m1.hasProperty(property.propertyName) == false){
			return false;
		}
	
		// get the property from the node and cast it to the correct type
		Object p = property.type.cast(m1.getProperty(property.propertyName));

		// make a hashset to store the property value(s)
		HashSet<Object> sourceValues = new HashSet<Object>();

		// if this property stores an array, then extract the values and cast them to the type stored by the array
		if (property.type.isArray()) {
			for (Object o : (Object[]) p) {
				sourceValues.add(property.type.getClass().getComponentType().cast(o));
			}
		} else { // otherwise just store the value
			sourceValues.add(p);
		}

		// TODO: comparisons when the source property is an array have not yet been tested, because we do
		// not currently store any source properties as arrays
		
		if (c == SetComparison.IS_EQUAL_TO) {
			return t.equals(sourceValues);
				 
		} else if (c == SetComparison.IS_UNEQUAL_TO) {
			return !t.equals(sourceValues);
		
		} else if (c == SetComparison.CONTAINS_ALL) {
			return t.containsAll(sourceValues);

		} else if (c == SetComparison.CONTAINS_ANY) {
			for (Object o : sourceValues) {
				if (t.contains(o)) {
					return true;
				}
			}
			return false;
			
//		} else if (c == ComparisonMethodForSet.DOES_NOT_CONTAIN) { // same as using the EXCLUDE directive with CONTAINS_ANY
//			return !t.containsAll(sourceValues);

		} else {
			// if we made it here then we didn't see a recognized method
			throw new java.lang.UnsupportedOperationException("the comparison method " + c.toString() + " is not recognized");
			
		}
	}
	
	@Override
	public String getDescription() {
		String description = "(unsupported comparison method)";
		if (c == SetComparison.CONTAINS_ALL || c == SetComparison.CONTAINS_ANY) {
			description = "the set " + Arrays.toString(t.toArray()) + " " + c.name() + " of the values in the source property '" + property.propertyName + '"';
		} else if (c == SetComparison.IS_EQUAL_TO || c == SetComparison.IS_UNEQUAL_TO) {
			description = "the set " + Arrays.toString(t.toArray()) + " " + c.name() + " the set of values in the source property '" + property.propertyName + '"';
		}
		return description;
	}
	
	@Override
	public String getReport() {
		// TODO Auto-generated method stub
		return "";
	}

}
