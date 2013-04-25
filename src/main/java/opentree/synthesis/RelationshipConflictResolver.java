package opentree.synthesis;

import org.neo4j.graphdb.Relationship;

/**
 * The class provides access to conflict resolution methods.
 * 
 * @author cody hinchliff
 *
 */
public class RelationshipConflictResolver {

	private ConflictResolutionMethod method;
	
	public RelationshipConflictResolver(ConflictResolutionMethod method) {
		this.method = method;
	}
	
	public RelationshipConflictResolver setResolutionMethod (ConflictResolutionMethod method) {
		this.method = method;
		return this;
	}
	
	public ConflictResolutionMethod getResolutionMethod () {
		return method;
	}
	
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> candidateRels) {
		return method.resolveConflicts(candidateRels);
	}
}
