package opentree.synthesis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import opentree.GraphDatabaseAgent;

import org.neo4j.graphdb.Relationship;

/**
 * This conflict resolution method finds the set of relationships with completely non-overlapping leaf sets,
 * preferring higher-ranked relationships. Ranking order is interpreted as the order of the relationships in
 * the supplied iterable.
 * 
 * Should these resolution methods be implemented in an enum? Or abstracted to make combinations of features available?
 * 
 * @author stephen smith and cody hinchliff
 */
public class AcyclicRankPriorityResolution implements ConflictResolutionMethod {

	// containers used to make decisions about best paths
	HashMap<Relationship, HashSet<Long>> candRelDescendantIdsMap;
	LinkedList<Relationship> bestRels;
	
	public AcyclicRankPriorityResolution() {
		initialize();
	}
	
	private void initialize() {
		candRelDescendantIdsMap = new HashMap<Relationship, HashSet<Long>>();
		bestRels = new LinkedList<Relationship>();
	}

	private void storeDescendants(Relationship rel) {
		HashSet<Long> descendantIds = new HashSet<Long>();
		for (long descId : (long[]) rel.getStartNode().getProperty("mrca")) {
			descendantIds.add(descId);
		}
		candRelDescendantIdsMap.put(rel, descendantIds);
	}
	
	private boolean testForConflict(Relationship rel1, Relationship rel2) {

		if (!candRelDescendantIdsMap.containsKey(rel1)) {
			storeDescendants(rel1);
		}
		
		if (!candRelDescendantIdsMap.containsKey(rel2)) {
			storeDescendants(rel2);
		}

		// if the relationships share any descendant leaves, then they are in conflict
		for (long descId1 : candRelDescendantIdsMap.get(rel1)) {
			for (long descId2 : candRelDescendantIdsMap.get(rel2)) {
//				System.out.println("descendant1 = " + descId1 + "; descendant2 = " + descId2);
				if (descId1 == descId2) {
					return true;
				}
			}
		}

		// no conflict was found
		return false;
	}
	
	@Override
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels) {

		initialize();
		Iterator<Relationship> relsIter = rels.iterator();
		
	    // for every candidate relationship
	    while (relsIter.hasNext()) {

	    	Relationship candidate = relsIter.next();
//	    	System.out.println("testing rel " + candidate.getId() + " for conflicts");

	    	boolean saveRel = true;

	    	// test for conflict between candidate against all saved
	    	for (Relationship saved : bestRels) {
//		    	System.out.println("\tagainst rel " + saved.getId());

	    		if (testForConflict(candidate, saved) == true) {
			    	
	    			// testing
//	    			System.out.println("\tconflict found! offending rel=" + saved.getId());
	    			
			    	saveRel = false;
	    			break;
	    		}
	    	}
	    	
	    	// if no conflict was found, add this rel to the saved set
	    	if (saveRel) {
//		    	System.out.println("\trel " + candidate.getId() + " passed, it will be added");
	    		bestRels.add(candidate);
	    	}
	    }

		return bestRels;
	}
}
