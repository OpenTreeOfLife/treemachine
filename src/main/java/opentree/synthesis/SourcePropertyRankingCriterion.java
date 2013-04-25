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

		// order relationships that have the property higher than ones that do not
		if (v1 == null && v2 == null) {
			return 0;
		} else if (v1 == null) {
			return -1;
		} else if (v2 == null) {
			return 1;

		// if both rels have the property, then compare them
		} else {

			if (order == RankingOrder.INCREASING)
				return v1.compareTo(v2);
			
			else if (order == RankingOrder.DECREASING)
				return v1.compareTo(v2) * -1; // reverse the comparison direction
	
				// could this also be? :
				// v2.compareTo(v1);
	
			else
				throw new java.lang.UnsupportedOperationException("the ranking method " + order.toString() + " is not recognized");
		}
	}

	@Override
	public void sort(List<Relationship> rels) {
		Collections.sort(rels, this);
	}
}
