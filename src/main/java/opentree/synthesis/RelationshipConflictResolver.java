package opentree.synthesis;

import org.neo4j.graphdb.Relationship;

/**
 * The class provides access to conflict resolution methods.
 * 
 * @author cody hinchliff
 *
 */
public class RelationshipConflictResolver {

	private ConflictResolver method;
	
	public RelationshipConflictResolver(ConflictResolver method) {
		this.method = method;
	}
	
	public RelationshipConflictResolver setResolutionMethod (ConflictResolver method) {
		this.method = method;
		return this;
	}
	
	public ConflictResolver getResolutionMethod () {
		return method;
	}
	
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> candidateRels) {
		return method.resolveConflicts(candidateRels);
	}
	
	public String getDescription() {
		return "Conflicts resolution will " + method.getDescription();
	}
}
