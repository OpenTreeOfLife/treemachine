package opentree.synthesis.conflictresolution;

import gnu.trove.set.hash.TLongHashSet;

import org.neo4j.graphdb.Relationship;

public interface ResolutionMethod {
	
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> candidateRels);
	public String getDescription();
	TLongHashSet getDupMRCAS();
	public String getReport();
	
}
