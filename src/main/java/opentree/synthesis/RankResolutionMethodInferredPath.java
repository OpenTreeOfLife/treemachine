package opentree.synthesis;

import gnu.trove.list.array.TLongArrayList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import opentree.LicaUtil;

import org.neo4j.graphdb.Relationship;

/**
 * This conflict resolution method finds the set of relationships with completely non-overlapping leaf sets,
 * preferring higher-ranked relationships. Ranking order is interpreted as the order of the relationships in
 * the supplied iterable.
 * 
 * This differs from the RankResolutionMethod in that it will assume an inferred path that contains a node but 
 * has more children is compatible.
 * 
 * Should these resolution methods be implemented in an enum? Or abstracted to make combinations of features available?
 * 
 * @author stephen smith and cody hinchliff
 */
public class RankResolutionMethodInferredPath implements ResolutionMethod {

	// containers used to make decisions about best paths
	HashMap<Relationship, TLongArrayList> candRelDescendantIdsMap;
	LinkedList<Relationship> bestRels;
	
	public RankResolutionMethodInferredPath() {
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
	
	/**
	 * the return codes are
	 * 0 - no conflict
	 * 1 - regular conflict
	 * 2 - candidate contains saved and is compatible
	 * @param candidate
	 * @param saved
	 * @return 
	 */
	private int testForConflict(Relationship candidate, Relationship saved) {

		if (!candRelDescendantIdsMap.containsKey(candidate)) {
			storeDescendants(candidate);
		}
		
		if (!candRelDescendantIdsMap.containsKey(saved)) {
			storeDescendants(saved);
		}

		// if the relationships share any descendant leaves, then they are in conflict
		if (LicaUtil.containsAnyt4jUnsorted(candRelDescendantIdsMap.get(saved), candRelDescendantIdsMap.get(candidate))) {
			if (candRelDescendantIdsMap.get(candidate).containsAll(candRelDescendantIdsMap.get(saved))) {
				return 2;
			}
			return 1;
		} else {
			return 0;
		}
	}
	
	@Override
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels) {

		initialize();
		Iterator<Relationship> relsIter = rels.iterator();
		HashSet <Relationship> removeSaved = new HashSet<Relationship> ();
	    // for every candidate relationship
	    while (relsIter.hasNext()) {
	    	
	    	Relationship candidate = relsIter.next();
	    	System.out.println("\ttesting rel " + candidate.getId() + " for conflicts");

	    	boolean saveRel = true;
	    	// test for conflict between candidate against all saved
	    	HashSet <Relationship> tremoveSaved = new HashSet<Relationship> ();	
	    	for (Relationship saved : bestRels) {
		    	//System.out.println("\t\tagainst rel " + saved.getId());
	    		int tfc = testForConflict(candidate, saved);
	    		if (tfc == 1) {
			    	
	    			// testing
	    			//System.out.println("\t\tconflict found! offending rel=" + saved.getId());
	    			
			    	saveRel = false;
	    			break;
	    		}else if(tfc == 2){
	    			System.out.println("remove saved relationship "+saved+" because it is contained within "+candidate);
	    			tremoveSaved.add(saved);
	    		}
	    	}
	    	
	    	// if no conflict was found, add this rel to the saved set
	    	if (saveRel) {
		    	System.out.println("\t\t++rel " + candidate.getId() + " passed, it will be added");
	    		bestRels.add(candidate);
	    		removeSaved.addAll(tremoveSaved);
	    	}else{
	    		System.out.println("\t\t--rel " + candidate.getId() + " failed, it will NOT be added");
	    	}
	    }
	    for(Relationship rel: removeSaved){
	    	System.out.println("removing "+rel);
	    	bestRels.remove(rel);
	    }
		return bestRels;
	}
	
	public String getDescription() {
		return "prefer relationships with higher ranking, and guarantee a fully acyclic result";
	}
}
