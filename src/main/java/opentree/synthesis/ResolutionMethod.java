package opentree.synthesis;

import org.neo4j.graphdb.Relationship;

public interface ResolutionMethod {
	
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> candidateRels);
	public String getDescription();
	
}
