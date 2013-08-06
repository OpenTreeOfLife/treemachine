package opentree.synthesis.filtering;

import org.neo4j.graphdb.Relationship;

public interface Test {
	
	public boolean test(Relationship r);
	public String getReport();
	public String getDescription();

}
