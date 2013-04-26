package opentree.synthesis;

import java.util.Collections;
import java.util.List;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;

/**
 * This class ranks relationships based on properties defined in the SourceProperty enum.
 * 
 * DIFFERENT FROM THE RANKING CRITERION, WHICH USES A PRIORITY LIST, BUT WE NEED BETTER NAMES
 * 
 * @author cody hinchliff
 *
 */
public class SourcePropertyRankingCriterion implements RankingCriterion {

	private SourceProperty property;
	private RankingOrder order;
	private Index<Node> metadataNodeIndex;
	
	/**
	 * Define a ranking order based on property `p` and sort order `o`.
	 * @param property
	 * @param c
	 * @param t
	 */
	public SourcePropertyRankingCriterion(SourceProperty p, RankingOrder o, Index<Node> sourceMetaNodes) {
		this.property = p;
		this.order = o;
		this.metadataNodeIndex = sourceMetaNodes;
	}
	
	public String getDescription() {
		return "by source property " + property.propertyName + " in " + order.name() + " order";
	}
	
	@Override
	public int compare(Relationship rel1, Relationship rel2) {
		
		Node m1 = metadataNodeIndex.get("source", rel1.getProperty("source")).getSingle();
		Node m2 = metadataNodeIndex.get("source", rel2.getProperty("source")).getSingle();
		
//		System.out.println("metadata node 1 " + m1.toString());
//		System.out.println("metadata node 2 " + m2.toString());

		SourcePropertyValue v1 = null;
		SourcePropertyValue v2 = null;
		
		if (m1.hasProperty(property.propertyName))
			v1 = new SourcePropertyValue(property, m1.getProperty(property.propertyName));

		if (m2.hasProperty(property.propertyName))
			v2 = new SourcePropertyValue(property, m2.getProperty(property.propertyName));

		Integer retval = null;
		
		// Relationships that have the specified property should be order higher than ones that do not.
		// NOTE: Collections.sort() sorts in ascending order, but we actually want higher priority elements
		// at the beginning of the list, so we reverse the order of the comparison here.
		if (v1 == null && v2 == null) {
			retval = 0;
		} else if (v1 == null) {
			retval = 1; // reverse
		} else if (v2 == null) {
			retval = -1; // reverse

		// if both rels have the property, then compare them
		} else {

			if (order == RankingOrder.INCREASING)
				retval = v1.compareTo(v2);
			
			else if (order == RankingOrder.DECREASING)
				retval = v2.compareTo(v1); // reverse the comparison direction

			else
				throw new java.lang.UnsupportedOperationException("the ranking method " + order.toString() + " is not recognized");
		}
		
		return retval;
	}

	@Override
	public void sort(List<Relationship> rels) {
		Collections.sort(rels, this);
	}
}
