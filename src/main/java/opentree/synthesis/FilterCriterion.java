package opentree.synthesis;

import org.neo4j.graphdb.Relationship;

public interface FilterCriterion { 
	abstract boolean test(Relationship r);
	abstract String getDescription();
	
}
