package opentree.synthesis;

import gnu.trove.list.array.TLongArrayList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import opentree.LicaUtil;
import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
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

	public enum ConflictType {
		NO_CONFLICT, 			// indicates no overlap
		FIRST_SUBSETOF_SECOND,	// indicates that the second element compared contains all the descendants of the first
		SECOND_SUBSETOF_FIRST,	// indicates that the first element compared contains all the descendants of the second
		INCOMPATIBLE			// indicates that neither element is a subset of the other, but they have overalpping descendent sets
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
			System.out.println("\tobserving internal node " + getIdString(rel.getStartNode()) + " (" + descendantIds.size() + " descendants) for the first time");
		}
	}
	
	/**
	 * tests for conflict and returns a ConflictType from the enum above
	 * @param candidate
	 * @param saved
	 * @return 
	 */
	private ConflictType testForConflict(Relationship rel1, Relationship rel2) {

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
				return ConflictType.SECOND_SUBSETOF_FIRST;
			} else if (candRelDescendantIdsMap.get(rel2).containsAll(candRelDescendantIdsMap.get(rel1))) { 
				return ConflictType.FIRST_SUBSETOF_SECOND;
			}
			
			// otherwise they are incompatible
			return ConflictType.INCOMPATIBLE;
			
		} else { // no conflict
			return ConflictType.NO_CONFLICT;
		}
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
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels) {

		initialize();
		Iterator<Relationship> relsIter = rels.iterator();
		
		// this keeps track of rels we've added that we subsequently want to remove, which we do
		// when we find a relationship that is more inclusive than a previously saved conflicting rel,
		// but which does not represent a biologically incompatible mapping of taxa. see testForConflict()
		HashSet <Relationship> savedRelsToRemove = new HashSet<Relationship> ();

		// for every candidate relationship
	    while (relsIter.hasNext()) {
	    	
	    	Relationship candidate = relsIter.next();
	    	System.out.println("testing rel " + getIdString(candidate) + " for conflicts");

	    	// will record any previously saved rels that are conflicting/compatible but less inclusive
//	    	HashSet <Relationship> tempSavedRelsToRemove = new HashSet<Relationship> ();

	    	// test candidate against all saved
	    	boolean candidatePassed = true;
	    	for (Relationship saved : bestRels) {

	    		ConflictType tfc = testForConflict(candidate, saved);

	    		// skip this candidate if it is incompatible with any saved rel
	    		if (tfc == ConflictType.INCOMPATIBLE) {
	    			System.out.println("\tconflict found! offending rel " + getIdString(saved));
		    		System.out.println("\t-- rel " + candidate.getId() + " will NOT be added");
			    	candidatePassed = false;
	    			break;

	    		// candidate is in conflict with a saved rel that is more inclusive than the candidate. we will not add it
	    		} else if (tfc == ConflictType.FIRST_SUBSETOF_SECOND) {

	    			System.out.println("\t-- rel" + candidate.getId() + " will NOT be added, it is included in rel " + getIdString(saved) + ". This might leave node " + getIdString(candidate.getEndNode()) + " as an empty tip...?");
	    			candidatePassed = false;
	    			break;
	    			
	    			/* 
	    			 * this is all very hacky and doesn't seem to be working....
	    			 */

/*	    			// check this candidate's parent node to be sure it isn't an empty tip
	    			Node curNode = candidate.getEndNode();
	    			boolean curNodeIsEmpty = true;
	    			while (true) {
	    				
	    				// if this node doesn't have any SYNTHCHILDOF children
	    				for (Relationship siblingRel : curNode.getRelationships(Direction.INCOMING)) {
	    					if (siblingRel.isType(RelType.SYNTHCHILDOF)) {
	    						System.out.println("\t\tparent node " + getIdString(curNode) + " has SYNTHCHILDOF children. stopping search.");
	    						curNodeIsEmpty = false;
	    						break;
	    					}
	    				}
	    				
	    				// remove it and continue checking parents until we find one that does
	    				if (curNodeIsEmpty) {
	    					for (Relationship parentRel : curNode.getRelationships(Direction.OUTGOING)) {
	    						if (parentRel.isType(RelType.SYNTHCHILDOF)) {
		    						System.out.println("\t\tnode " + getIdString(curNode) + " is an empty tip. its outgoing SYNTHCHILDOF rel " + getIdString(parentRel) + " will be removed.");
	    							curNode = parentRel.getEndNode();
	    							savedRelsToRemove.add(parentRel);
	    						}
	    					}
	    				}
	    			} */
	    			
	    		// candidate is in conflict with a saved rel but is compatible and more inclusive.
	    		} else if (tfc == ConflictType.SECOND_SUBSETOF_FIRST) {
	    			
	    			// replace the saved with the candidate and keep checking to see if there are other saved rels that conflict
	    			System.out.println("\twill remove saved relationship " + getIdString(saved) + " because it is contained within " + getIdString(candidate));
	    			savedRelsToRemove.add(saved);
	    		}
	    	}
	    	
	    	if (candidatePassed) {
		    	System.out.println("\t++ rel " + candidate.getId() + " passed, it will be added");
	    		bestRels.add(candidate);
//	    		savedRelsToRemove.addAll(tempSavedRelsToRemove);
	    	}
	    	
//	    	} else { // candidate failed
//	    		System.out.println("\t--rel " + candidate.getId() + " failed, it will NOT be added");
//	    	}
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
}
