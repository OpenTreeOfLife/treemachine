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
		candRelDescendantIdsMap = new HashMap<Relationship, TLongArrayList>();
		reset();
	}

	public enum ConflictType {
		NO_CONFLICT, 			// indicates no overlap
		FIRST_SUBSETOF_SECOND,	// indicates that the second element compared contains all the descendants of the first
		SECOND_SUBSETOF_FIRST,	// indicates that the first element compared contains all the descendants of the second
		INCOMPATIBLE			// indicates that neither element is a subset of the other, but they have overalpping descendent sets
	}

	/**
	 * clears the saved best rels
	 */
	private void reset() {
		bestRels = new LinkedList<Relationship>();
	}

	private void storeDescendants(Relationship rel) {

		TLongArrayList descendantIds = new TLongArrayList((long[]) rel.getStartNode().getProperty("mrca"));
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

		// == begin testing code

		// setting up a test for case involving Thamnidium elegans
		Long testNodeId = 337420L;
		boolean testing = false;
		Long forkPointNodeId = 337050L;

		Long badRelId1 = 959223L; // Thamnidiaceae id=337362 STREECHILDOF Mucorales id=337050
		Long badRelId2 = 981143L; // source tree node id=340862 STREECHILDOF Mucorales id=337050 from source 1162_5805
		
		if (forkPointNodeId.equals((Long) rel1.getEndNode().getId()) && forkPointNodeId.equals((Long) rel2.getEndNode().getId())) {
			
			System.out.println("\n\n\nFound forking point. Comparing:");
			System.out.println(getIdString(rel1));
			System.out.println(getIdString(rel2));
			
			testing = true;
			
			if ((badRelId1.equals((Long) rel1.getId()) && badRelId2.equals((Long) rel2.getId())) ||
				(badRelId2.equals((Long) rel1.getId()) && badRelId1.equals((Long) rel2.getId()))) {

				System.out.println("\n\nFound test case. Attempting comparison of known conflicting rels");
				
				System.out.println("Candidate rel 1 : " + getIdString(rel1));
				TLongArrayList rel1Descendants = candRelDescendantIdsMap.get(rel1);
				for (int i = 0; i < rel1Descendants.size(); i++) {
					System.out.println("\t" + rel1Descendants.get(i));
					
					if (rel1Descendants.get(i) == testNodeId) {
						System.out.println("\nFound the id of Thamnidium elegans in relationship 1 MRCA");
					}
				}
	
				System.out.println("Candidate rel 2 : " + getIdString(rel2));
				TLongArrayList rel2Descendants = candRelDescendantIdsMap.get(rel2);
				for (int i = 0; i < rel2Descendants.size(); i++) {
					System.out.println("\t" + rel2Descendants.get(i));
					
					if (rel2Descendants.get(i) == testNodeId) {
						System.out.println("\nFound the id of Thamnidium elegans in relationship 2 MRCA");
					}
				}
			}
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

		// == begin testing code
		if (testing) {
			System.out.println(rval.toString());
			System.out.println("\n\n");
		}
		// == end testing code
			
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
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels) {

		reset();
		Iterator<Relationship> relsIter = rels.iterator();
		
		// this keeps track of rels we've added that we subsequently want to remove, which we do
		// when we find a relationship that is more inclusive than a previously saved conflicting rel,
		// but which does not represent a biologically incompatible mapping of taxa. see testForConflict()
		HashSet <Relationship> savedRelsToRemove = new HashSet<Relationship> ();

		// for every candidate relationship
	    while (relsIter.hasNext()) {
	    	
	    	Relationship candidate = relsIter.next();
	    	System.out.println("testing " + getIdString(candidate) + " for conflicts");

	    	// will record any previously saved rels that are conflicting/compatible but less inclusive
//	    	HashSet <Relationship> tempSavedRelsToRemove = new HashSet<Relationship> ();

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
	    			System.out.println("\twill remove saved relationship " + getIdString(saved) + " because it is contained within " + getIdString(candidate));
	    			savedRelsToRemove.add(saved);
	    		}
	    	}
	    	
	    	if (candidatePassed) {
		    	System.out.println("\t++ rel " + candidate.getId() + " passed, it will be added");
	    		bestRels.add(candidate);
//	    		savedRelsToRemove.addAll(tempSavedRelsToRemove);
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
}
