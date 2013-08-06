package opentree.synthesis.filtering;

import opentree.constants.SourceProperty;
import opentree.synthesis.SourcePropertyValue;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

	/**
	 * This class makes filtering decisions based on relationship properties as defined in the SourceProperty class, using
	 * comparison methods defined in the ComparisonMethod class.
	 * 
	 * @author senor hinchliff and esteban 
	 *
	 */
	public class SourcePropertySingleValueTest implements Test {

		private SourceProperty property;
		private SingleValueComparison c;
		private TestValue t;
		private Index<Node> metadataNodeIndex;
		
		/**
		 * Will compare the values of the source property defined by `p` to the test value `t` using the comparison method `c`, and include or exclude the ones that match based on the directive `d`.
		 * @param property
		 * @param c
		 * @param t
		 */
		public SourcePropertySingleValueTest(SourceProperty p, SingleValueComparison c, TestValue t, Index<Node> sourceMetaNodes) {
			this.property = p;
			this.c = c;
			this.t = t;
			metadataNodeIndex = sourceMetaNodes;
		}
		
		@Override
		public boolean test(Relationship r) {
			// there can be multiple given multiple LICA mappings 
			if(r.hasProperty("source") == false){
				return false;
			}
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
			
			if (c == SingleValueComparison.IS_EQUAL_TO) {
				return t.compareTo(v) == 0;
				
			} else if (c == SingleValueComparison.IS_UNEQUAL_TO) {
				return t.compareTo(v) != 0;
				
			} else if (c == SingleValueComparison.IS_GREATER_THAN) {
				// do the comparison in reverse, so return the opposite
				return t.compareTo(v) < 0;
	
			} else if (c == SingleValueComparison.IS_LESS_THAN) {
				// do the comparison in reverse, so return the opposite
				return t.compareTo(v) > 0;
	
			} else if (c == SingleValueComparison.IS_GREATER_THAN_OR_EQUAL_TO) {
				// do the comparison in reverse, so return the opposite
				return t.compareTo(v) <= 0;
				
			} else if (c == SingleValueComparison.IS_LESS_THAN_OR_EQUAL_TO) {
				// do the comparison in reverse, so return the opposite
				return t.compareTo(v) >= 0;

			} else {
				// if we made it here then we didn't see a recognized method
				throw new java.lang.UnsupportedOperationException("the comparison method " + c.toString() + " is not recognized");

			}
		}
		
		@Override
		public String getDescription() {
			return "source property " + property.propertyName + " " + c.name() + " " + t.getValue();
		}

		@Override
		public String getReport() {
			// TODO Auto-generated method stub
			return "";
		}
}
