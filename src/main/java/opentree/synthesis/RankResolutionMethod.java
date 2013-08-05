package opentree.synthesis;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;

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
	//this will include all the mrcas that were found in this round that should be excluded
	TLongHashSet dupMRCAS;
	
	public RankResolutionMethod() {
		initialize();
	}
	
	private void initialize() {
		candRelDescendantIdsMap = new HashMap<Relationship, TLongArrayList>();
		bestRels = new LinkedList<Relationship>();
		dupMRCAS = new TLongHashSet();
	}
	
	private void storeDescendants(Relationship rel) {

		TLongArrayList descendantIds = new TLongArrayList((long[]) rel.getStartNode().getProperty("mrca"));
		//this will test only the mrcas that are included in the relationship from the source tree
		//this will not always be the full set from the mrca of the node -- unless it is taxonomy relationship
		//need to verify that the exclusive mrca is correct in this conflict
		//it should be the mapped tip mrcas subtending this node
		if(((String)rel.getProperty("source")).equals("taxonomy") == false){
			TLongArrayList exclusiveIds = new TLongArrayList((long[]) rel.getProperty("exclusive_mrca"));
			descendantIds.retainAll(exclusiveIds);
		}
		candRelDescendantIdsMap.put(rel, descendantIds);
		
		// just user feedback for non-terminal nodes
		if (descendantIds.size() > 1) {
			String name = null;
			if (rel.getStartNode().hasProperty("name")) {
				name = String.valueOf(rel.getStartNode().getProperty("name"));
			} else {
				name = rel.getStartNode().toString();
			}
			System.out.println(name + " has " + descendantIds.size() + " terminal descendants");
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
	
	/*
	 * This returns the mrcas that were found to be in not true conflict in these sets
	 */
	public TLongHashSet getDupMRCAS(){
		return dupMRCAS;
	}
	
	@Override
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels) {

		initialize();
		Iterator<Relationship> relsIter = rels.iterator();
		//these are all the mrcas that are actually included in the set of saveRels
		TLongHashSet totalIncluded = new TLongHashSet();
		//these are all the mrcas that are included in the subtending nodes
		TLongHashSet totalMRCAS = new TLongHashSet();
		
	    // for every candidate relationship
	    while (relsIter.hasNext()) {
	    	
	    	Relationship candidate = relsIter.next();
	    	System.out.println("\ttesting rel " + candidate.getId() + " for conflicts");

	    	boolean saveRel = true;
	    	// test for conflict between candidate against all saved
	    	for (Relationship saved : bestRels) {
//		    	System.out.println("\t\tagainst rel " + saved.getId());

	    		if (testForConflict(candidate, saved) == true) {
			    	
	    			// testing
	    			System.out.println("\t\tconflict found! offending rel=" + saved.getId());
	    			
			    	saveRel = false;
	    			break;
	    		}
	    	}
	    	
	    	// if no conflict was found, add this rel to the saved set
	    	if (saveRel) {
		    	System.out.println("\t\t++rel " + candidate.getId() + " passed, it will be added");
	    		bestRels.add(candidate);
	    		//THIS COULD POTENTIALLY BE MADE FASTER
	    		//add the exclusive mrcas from the relationship to the totalIncluded
	    		//System.out.println(candRelDescendantIdsMap.get(candidate));
	    		if (!candRelDescendantIdsMap.containsKey(candidate)) {
	    			storeDescendants(candidate);
	    		}
	    		totalIncluded.addAll(candRelDescendantIdsMap.get(candidate));
	    		//get the full mrcas identify dups and add to dups
	    		TLongHashSet fullmrcas = new TLongHashSet((long[])candidate.getEndNode().getProperty("mrca"));
	    		fullmrcas.retainAll(totalMRCAS);
	    		dupMRCAS.addAll(fullmrcas);
	    		//get the full mrcas and add any new ones to the totalmrcas
	    		TLongHashSet fullmrcas2 = new TLongHashSet((long[])candidate.getEndNode().getProperty("mrca"));
	    		totalMRCAS.addAll(fullmrcas2);
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

	@Override
	public String getReport() {
		// TODO Auto-generated method stub
		return null;
	}
}
