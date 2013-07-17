package opentree.synthesis;

import gnu.trove.list.array.TLongArrayList;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import opentree.LicaUtil;

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
public class RankResolutionMethod implements ResolutionMethod {

	// containers used to make decisions about best paths
	HashMap<Relationship, TLongArrayList> candRelDescendantIdsMap;
	LinkedList<Relationship> bestRels;
	
	public RankResolutionMethod() {
		initialize();
	}
	
	private void initialize() {
		candRelDescendantIdsMap = new HashMap<Relationship, TLongArrayList>();
		bestRels = new LinkedList<Relationship>();
	}

	private void storeDescendants(Relationship rel) {

		TLongArrayList descendantIds = new TLongArrayList((long[]) rel.getStartNode().getProperty("mrca"));
		candRelDescendantIdsMap.put(rel, descendantIds);

		// just user feedback for non-terminal nodes
		if (descendantIds.size() > 1) {
			String name = null;
			if (rel.getStartNode().hasProperty("name")) {
				name = String.valueOf(rel.getStartNode().getProperty("name"));
			} else {
				name = rel.getStartNode().toString();
			}
			System.out.println(name + " has " + descendantIds.size() + " mrcas");
		}
	}
	
	private boolean testForConflict(Relationship rel1, Relationship rel2) {

		if (!candRelDescendantIdsMap.containsKey(rel1)) {
			storeDescendants(rel1);
		}
		
		if (!candRelDescendantIdsMap.containsKey(rel2)) {
			storeDescendants(rel2);
		}

		// if the relationships share any descendant leaves, then they are in conflict
		if (LicaUtil.containsAnyt4jUnsorted(candRelDescendantIdsMap.get(rel2), candRelDescendantIdsMap.get(rel1))) {
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels) {

		initialize();
		Iterator<Relationship> relsIter = rels.iterator();
		
	    // for every candidate relationship
	    while (relsIter.hasNext()) {
	    	
	    	Relationship candidate = relsIter.next();
	    	//System.out.println("\ttesting rel " + candidate.getId() + " for conflicts");

	    	boolean saveRel = true;
	    	// test for conflict between candidate against all saved
	    	for (Relationship saved : bestRels) {
		    	//System.out.println("\t\tagainst rel " + saved.getId());

	    		if (testForConflict(candidate, saved) == true) {
			    	
	    			// testing
	    			//System.out.println("\t\tconflict found! offending rel=" + saved.getId());
	    			
			    	saveRel = false;
	    			break;
	    		}
	    	}
	    	
	    	// if no conflict was found, add this rel to the saved set
	    	if (saveRel) {
		    	System.out.println("\t\t++rel " + candidate.getId() + " passed, it will be added");
	    		bestRels.add(candidate);
	    	}else{
	    		System.out.println("\t\t--rel " + candidate.getId() + " failed, it will NOT be added");
	    	}
	    }

		return bestRels;
	}
	
	@Override
	public String getDescription() {
		return "prefer relationships with higher ranking, and guarantee a fully acyclic result";
	}
}
