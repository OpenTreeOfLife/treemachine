package opentree.synthesis;

import org.neo4j.graphdb.Relationship;

public interface ConflictResolver {
	
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> candidateRels);
	public String getDescription();
	
}
