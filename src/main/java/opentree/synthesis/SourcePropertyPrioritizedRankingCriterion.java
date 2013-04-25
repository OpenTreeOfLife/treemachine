package opentree.synthesis;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;

/**
 * This class ranks relationships by property based on the priority defined in the priortyListIter.
 * 
 * @author cody hinchliff
 *
 */
public class SourcePropertyPrioritizedRankingCriterion implements RankingCriterion {

	private SourceProperty property;
	private HashMap<String, Integer> priorityMapString;
	private HashMap<Long, Integer> priorityMapLong;
	private HashMap<Double, Integer> priorityMapDouble;
	Index<Node> metadataNodeIndex;
	
	private final static Integer NOTRANKED = -999999999;
	
	/**
	 * Define a ranking order for relationships based on the order defined by the priorityList `l` for the property `p`.
	 * @param property
	 * @param c
	 * @param t
	 */
	public SourcePropertyPrioritizedRankingCriterion(SourceProperty property, Iterable<Object> priortyListIterable, Index<Node> sourceMetaNodes) {
		this.property = property;
		this.metadataNodeIndex = sourceMetaNodes;

		int i = 0;
		if (property.type == String.class) {
			this.priorityMapString = new HashMap<String, Integer>();
			for (Object o : priortyListIterable) {
				this.priorityMapString.put((String) o, i++);
			}
				
		} else if (property.type == Long.class || property.type == Integer.class) {
			this.priorityMapLong = new HashMap<Long, Integer>();
			for (Object o : priortyListIterable) {
				this.priorityMapLong.put((Long) o, i++);
			}
		
		} else if (property.type == Double.class) {
			this.priorityMapDouble= new HashMap<Double, Integer>();
			for (Object o : priortyListIterable) {
				this.priorityMapDouble.put((Double) o, i++);
			}
		}
	}

	/**
	 * Compare the specified source property of the two provided relationships.
	 */
	@Override
	public int compare(Relationship rel1, Relationship rel2) {
		
		Object v1 = null;
		Object v2 = null;
		
		Node m1 = metadataNodeIndex.get("source", rel1.getProperty("source")).getSingle();
		Node m2 = metadataNodeIndex.get("source", rel2.getProperty("source")).getSingle();

		if (m1.hasProperty(property.propertyName))
			v1 = m1.getProperty(property.propertyName);

		if (m2.hasProperty(property.propertyName))
			v2 = m2.getProperty(property.propertyName);

		if (v1 == null && v2 == null) {
			return 0;
		} else if (v2 == NOTRANKED) {
			return 1;
		} else if (v1 == NOTRANKED) {
			return -1;
		}

		Integer rank1 = NOTRANKED;
		Integer rank2 = NOTRANKED;

		if (property.type == String.class) {
			if (priorityMapString.containsKey((String) v1))
				rank1 = priorityMapString.get((String) v1);

			if (priorityMapString.containsKey((String) v2))
				rank2 = priorityMapString.get((String) v2);
			
		} else if (property.type == Long.class || property.type == Integer.class) {
			if (priorityMapString.containsKey((Long) v1))
				rank1 = priorityMapLong.get((Long) v1);
			
			if (priorityMapString.containsKey((Long) v2))
				rank2 = priorityMapLong.get((Long) v2);

		} else if (property.type == Double.class) {
			if (priorityMapString.containsKey((Double) v1))
				rank1 = priorityMapDouble.get((Double) v1);

			if (priorityMapString.containsKey((Double) v2))
				rank2 = priorityMapDouble.get((Double) v2);

		} else {
			throw new java.lang.UnsupportedOperationException("the source property datatype " + String.valueOf(property.type) + " is unrecognized");
		}
		
		if (rank1 == NOTRANKED && rank2 == NOTRANKED) {
			return 0;
		} else if (rank2 == NOTRANKED) {
			return 1;
		} else if (rank1 == NOTRANKED) {
			return -1;
		}
			
		return rank1.compareTo(rank2);
	}

	public void sort(List<Relationship> rels) {
		Collections.sort(rels, this);
	}
}
