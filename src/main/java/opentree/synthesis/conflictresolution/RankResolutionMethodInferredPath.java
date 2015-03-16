package opentree.synthesis.conflictresolution;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import opentree.LicaUtil;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

/**
 * This resolution method is deprecated. Use the RankResolutionMethod (without inferred path) instead
 * 
 * This conflict resolution method finds the set of relationships with completely non-overlapping leaf sets,
 * preferring higher-ranked relationships. Ranking order is interpreted as the order of the relationships in
 * the supplied iterable.
 * 
 * This differs from the RankResolutionMethod in that it will assume an inferred path that contains a node but 
 * has more children is compatible.
 * 
 * 
 * @author stephen smith and cody hinchliff
 */
public class RankResolutionMethodInferredPath implements ResolutionMethod {

	// containers used to make decisions about best paths
	HashMap<Relationship, TLongArrayList> candRelDescendantIdsMap;
	LinkedList<Relationship> bestRels;
	//this will include all the mrcas that were found in this round that should be excluded
	TLongHashSet dupMRCAS;
	
	public RankResolutionMethodInferredPath() {
		initialize();
	}

	public enum ConflictType {
		NO_CONFLICT, 			// indicates no overlap
		FIRST_SUBSETOF_SECOND,	// indicates that the second element compared contains all the descendants of the first
		SECOND_SUBSETOF_FIRST,	// indicates that the first element compared contains all the descendants of the second
		INCOMPATIBLE			// indicates that neither element is a subset of the other, but they have overalpping descendent sets
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
		//if(((String)rel.getProperty("source")).equals("taxonomy") == false){
		//	TLongArrayList exclusiveIds = new TLongArrayList((long[]) rel.getProperty("exclusive_mrca"));
		//	descendantIds.retainAll(exclusiveIds);
		//}
		candRelDescendantIdsMap.put(rel, descendantIds);

		// just user feedback for non-terminal nodes
		if (descendantIds.size() > 1) {
			System.out.println("\tobserving internal node " + getIdString(rel.getStartNode()) + " (" + descendantIds.size() + " descendants)");
		}
	}
	
	/**
	 * tests for conflict and returns a ConflictType from the enum above
	 * @param candidate
	 * @param saved
	 * @return 
	 */
	private ConflictType testForConflict(Relationship rel1, Relationship rel2) {

		ConflictType rval = null;
		
		if (!candRelDescendantIdsMap.containsKey(rel1)) {
			storeDescendants(rel1);
		}
		
		if (!candRelDescendantIdsMap.containsKey(rel2)) {
			storeDescendants(rel2);
		}
		
		// if the relationships share any descendant leaves, then there is some kind of conflict
		if (LicaUtil.containsAnyt4jUnsorted(candRelDescendantIdsMap.get(rel2), candRelDescendantIdsMap.get(rel1))) {
			
			// but if one contains all the descendants of the other, then we will indicate that
			if (candRelDescendantIdsMap.get(rel1).containsAll(candRelDescendantIdsMap.get(rel2))) {
				rval = ConflictType.SECOND_SUBSETOF_FIRST;
			} else if (candRelDescendantIdsMap.get(rel2).containsAll(candRelDescendantIdsMap.get(rel1))) { 
				rval = ConflictType.FIRST_SUBSETOF_SECOND;
				
			} else { // otherwise they are incompatible
				rval = ConflictType.INCOMPATIBLE;
			}
			
		} else { // no conflict
			rval = ConflictType.NO_CONFLICT;
		}
			
		return rval;
	}

	/** just used for formatting node info for nice output */
	private String getIdString(Node n) {
		String idStr = "'";
		if (n.hasProperty("name")) {
			idStr = idStr.concat((String) n.getProperty("name"));
		}
		return idStr.concat("' (id=").concat(String.valueOf(n.getId())).concat(")");
	}

	/** just used for formatting rel info for nice output */
	private String getIdString(Relationship rel) {		
		return "[relid=".concat(String.valueOf(rel.getId())).concat(" : ").concat(getIdString(rel.getStartNode())).concat(" child of node ").concat(getIdString(rel.getEndNode())).concat("]");
	}
	
	@Override
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels,boolean reinitialize) {
		if(reinitialize)
			initialize();
		Iterator<Relationship> relsIter = rels.iterator();
		//these are all the mrcas that are actually included in the set of saveRels
		TLongHashSet totalIncluded = new TLongHashSet();
		//these are all the mrcas that are included in the subtending nodes
		TLongHashSet totalMRCAS = new TLongHashSet();
		
		// this keeps track of rels we've added that we subsequently want to remove, which we do
		// when we find a relationship that is more inclusive than a previously saved conflicting rel,
		// but which does not represent a biologically incompatible mapping of taxa. see testForConflict()
		HashSet <Relationship> savedRelsToRemove = new HashSet<Relationship> ();

		// for every candidate relationship
	    while (relsIter.hasNext()) {
	    	
	    	Relationship candidate = relsIter.next();
	    	System.out.println("testing " + getIdString(candidate) + " for conflicts");

	    	// test candidate against all saved
	    	boolean candidatePassed = true;
	    	for (Relationship saved : bestRels) {

	    		ConflictType tfc = testForConflict(candidate, saved);

	    		// skip this candidate if it is incompatible with any saved rel
	    		if (tfc == ConflictType.INCOMPATIBLE) {
	    			System.out.println("\tconflict found! offending rel " + getIdString(saved));
			    	candidatePassed = false;
	    			break;

	    		// candidate is in conflict with a saved rel that is more inclusive than the candidate. we will not add it
	    		} else if (tfc == ConflictType.FIRST_SUBSETOF_SECOND) {
	    			System.out.println("\tcandidate " + candidate.getId() + " is included in previously saved " + getIdString(saved) + ".");
	    			candidatePassed = false;
	    			break;
	    			    			
	    		// candidate is in conflict with a saved rel but is compatible and more inclusive.
	    		} else if (tfc == ConflictType.SECOND_SUBSETOF_FIRST) {
	    			
	    			// replace the saved with the candidate and keep checking to see if there are other saved rels that conflict
	    			System.out.println("\twill remove saved relationship " + getIdString(saved) + " because it is contained within candidate " + candidate.getId());
	    			savedRelsToRemove.add(saved);
	    		}
	    	}
	    	
	    	if (candidatePassed) {
		    	System.out.println("\t++ rel " + candidate.getId() + " passed, it will be added");
	    		bestRels.add(candidate);
	    		//THIS COULD POTENTIALLY BE MADE FASTER
	    		//add the exclusive mrcas from the relationship to the totalIncluded
	    		//System.out.println(candRelDescendantIdsMap.get(candidate));
	    		if (!candRelDescendantIdsMap.containsKey(candidate)) {
	    			storeDescendants(candidate);
	    		}
	    		totalIncluded.addAll(candRelDescendantIdsMap.get(candidate));
	    		//get the full mrcas identify dups and add to dups
	    		TLongHashSet fullmrcas = new TLongHashSet((long[])candidate.getStartNode().getProperty("mrca"));
	    		fullmrcas.retainAll(totalMRCAS);
	    		dupMRCAS.addAll(fullmrcas);
	    		//System.out.println("dups:"+dupMRCAS.size());
	    		//get the full mrcas and add any new ones to the totalmrcas
	    		TLongHashSet fullmrcas2 = new TLongHashSet((long[])candidate.getStartNode().getProperty("mrca"));
	    		totalMRCAS.addAll(fullmrcas2);

	    	} else { // candidate failed
	    		System.out.println("\t-- rel " + candidate.getId() + " failed, it will NOT be added");
	    	}

	    	System.out.println("");
	    }
	    
	    for(Relationship rel: savedRelsToRemove){
	    	System.out.println("removing "+ getIdString(rel));
	    	bestRels.remove(rel);
	    }
		return bestRels;
	}
	
	@Override
	public String getDescription() {
		return "prefer relationships with higher ranking, but take paths with more descendants as long as they don't indicate relationships incompatible with preferred rels. Result will be fully acyclic.";
	}
	
	@Override
	public String getReport() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	public TLongHashSet getDupMRCAS() {
		return dupMRCAS;
	}
}
