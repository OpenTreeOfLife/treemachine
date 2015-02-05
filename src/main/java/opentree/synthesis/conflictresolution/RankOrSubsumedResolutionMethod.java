//@mth from RankResolutionMethod
package opentree.synthesis.conflictresolution;
import opentree.constants.RelType;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;

import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import opentree.LicaUtil;

import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Direction;


/**
 * This conflict resolution method finds the set of relationships with completely non-overlapping leaf sets,
 * preferring higher-ranked relationships. Ranking order is interpreted as the order of the relationships in
 * the supplied iterable.
 * 
 * Should these resolution methods be implemented in an enum? Or abstracted to make combinations of features available?
 * 
 * @author stephen smith and cody hinchliff and mark holder
 */
public class RankOrSubsumedResolutionMethod implements ResolutionMethod {

	// containers used to make decisions about best paths
	HashMap<Relationship, TLongArrayList> candRelDescendantIdsMap;
	LinkedList<Relationship> bestRels;
	//this will include all the mrcas that were found in this round that should be excluded
	TLongHashSet dupMRCAS;
	
	//@mth these use to be locals for the resolveConflicts, but we now call that method twice!
	//these are all the mrcas that are actually included in the set of saveRels
	TLongHashSet totalIncluded = new TLongHashSet();
	
	
	public RankOrSubsumedResolutionMethod() {
		initialize();
	}
	
	private void initialize() {
		candRelDescendantIdsMap = new HashMap<Relationship, TLongArrayList>();
		bestRels = new LinkedList<Relationship>();
		dupMRCAS = new TLongHashSet();
		totalIncluded = new TLongHashSet();
	}
	
	private void storeDescendants(Relationship rel) {

		TLongArrayList descendantIds = new TLongArrayList((long[]) rel.getStartNode().getProperty("mrca"));
		/* //@mth change is commenting out this restriction the "relationship taxa"
		//this will test only the mrcas that are included in the relationship from the source tree
		//this will not always be the full set from the mrca of the node -- unless it is taxonomy relationship
		//need to verify that the exclusive mrca is correct in this conflict
		//it should be the mapped tip mrcas subtending this node
		if(((String)rel.getProperty("source")).equals("taxonomy") == false
				&& rel.hasProperty("compat")==false){
			TLongArrayList exclusiveIds = new TLongArrayList((long[]) rel.getProperty("exclusive_mrca"));
			descendantIds.retainAll(exclusiveIds);
		}
		*/
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
		return LicaUtil.containsAnyt4jUnsorted(candRelDescendantIdsMap.get(rel2), candRelDescendantIdsMap.get(rel1));
	}
	
	/*
	 * This returns the mrcas that were found to be in not true conflict in these sets
	 */
	@Override
	public TLongHashSet getDupMRCAS(){
		return dupMRCAS;
	}
	
	@Override
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels, boolean reinitialize) {
		if (reinitialize) {
			initialize();
			return this.addNonConflictingOrSubsumed(rels);
		} else {
			return this.addNonConflictingStrict(rels);
		}
	}
	private boolean nodeHasAllMRCAElements(Node tn, Set<Long> mrcaSet) {
		long nmrcas[] = (long[]) tn.getProperty("mrca");
		Set<Long> nms = new HashSet<Long>();
		for (int i = 0 ; i < nmrcas.length; ++i) {
			nms.add(nmrcas[i]);
		}
		if (!nms.containsAll(mrcaSet)) {
			System.out.println("\t\t\tnodeHasAllMRCAElements returning FALSE");
			return false;
		} else {
			System.out.println("\t\t\tnodeHasAllMRCAElements returning TRUE");
			return true;
		}
	}
	private boolean isSTreeReachable(Node tn, Node possibleStart, Set<Long> mrcaSet) {
		for (Relationship rel : tn.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
			System.out.println("\t\t\tcomparing " + possibleStart.getId() + " and " + rel.getStartNode().getId());
			if (rel.getStartNode().getId() == possibleStart.getId()) {
				return true;
			}
			System.out.println("\t\t\t   darn. unequal");
			
		}
		for (Relationship rel : tn.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
			Node sn = rel.getStartNode();
			System.out.println("\t\t\tchecking if we should pursue " + sn.getId());
			if (nodeHasAllMRCAElements(sn, mrcaSet)) {
				if (isSTreeReachable(sn, possibleStart, mrcaSet)) {
					return true;
				}
			}
		}
		return false;
	}
	private boolean subsumes(Relationship possibleBigger, Relationship possibleSmaller) {
		Node psNode = possibleSmaller.getStartNode();
		long possibleSmallerMRCAs[] = (long[]) psNode.getProperty("mrca");
		Set<Long> psMRCASet = new HashSet<Long>();
		for (int i = 0 ; i < possibleSmallerMRCAs.length; ++i) {
			psMRCASet.add(possibleSmallerMRCAs[i]);
		}
		System.out.println("\t\t\tchecking if we should consider looking under " + possibleBigger.getId());
		Node pbNode = possibleBigger.getStartNode();
		if (!nodeHasAllMRCAElements(pbNode, psMRCASet)) {
			return false;
		}
		if (pbNode.getId() == psNode.getId()) {
			return true;
		}
		return isSTreeReachable(pbNode, psNode, psMRCASet);
	}
	public Iterable<Relationship> addNonConflictingOrSubsumed(Iterable<Relationship> rels) {
		Iterator<Relationship> relsIter = rels.iterator();
		// for every candidate relationship
		while (relsIter.hasNext()) {
			Relationship candidate = relsIter.next();
			System.out.println("\ttesting rel " + candidate.getId() + " for conflicts");
			Relationship firstConflicting = null;
			Relationship secondConflicting = null;
			// test for conflict between candidate against all saved
			for (Relationship saved : bestRels) {
				if (testForConflict(candidate, saved) == true) {
					if (firstConflicting == null) {
						System.out.println("\t\tfirst conflict found! offending rel=" + saved.getId());
						firstConflicting = saved;
					} else {
						System.out.println("\t\tsecond conflict found! offending rel=" + saved.getId());
						secondConflicting = saved;
						break;
					}
				}
			}
			boolean subsuming = false;
			if (firstConflicting != null && secondConflicting == null && subsumes(candidate, firstConflicting)) {
				subsuming = true;
			}
			// if no conflict was found, add this rel to the saved set
			if (firstConflicting == null || subsuming) {
				if (subsuming) {
					System.out.println("\t\t++rel " + candidate.getId() + " will be added. It subsumes " + firstConflicting.getId());
					this.backOutSubsumedCandidate(firstConflicting);
				} else {
					System.out.println("\t\t++rel " + candidate.getId() + " passed, it will be added");
				}
				this.addApprovedCandidate(candidate);
			}else{
				System.out.println("\t\t--rel " + candidate.getId() + " had >1 conflict, or did not subsume the 1 conflict that it had. It will not be added.");
			}
		}
		System.out.println("bestRels check:"+bestRels);
		return bestRels;
	}
	private void recalculateDups() {
		dupMRCAS = new TLongHashSet();
		TLongHashSet enc = new TLongHashSet();
		for (Relationship rel : bestRels) {
			System.out.println("\tChecking for dups from rel " + rel.getId() + "...");
			assert candRelDescendantIdsMap.containsKey(rel);
			TLongArrayList fromRel = candRelDescendantIdsMap.get(rel);
			TLongHashSet fromThisRel = new TLongHashSet(); // should not be necessary - mrcas should be sets! #TODO TMP
			for(int i = 0; i < fromRel.size(); ++i) {
				long taxonid = fromRel.get(i);
				if (!fromThisRel.contains(taxonid)) {
					fromThisRel.add(taxonid);
					if (enc.contains(taxonid)) {
						System.out.println("TAXON ID duplicated: " + taxonid);
						assert false;
						dupMRCAS.add(taxonid);
					} else {
						System.out.println("\t adding taxonid " + taxonid);
						enc.add(taxonid);
					}
				}
			}
		}
	}
	private void backOutSubsumedCandidate(Relationship candidate) {
		bestRels.remove(candidate);
		//THIS COULD POTENTIALLY BE MADE FASTER
		//add the exclusive mrcas from the relationship to the totalIncluded
		//System.out.println(candRelDescendantIdsMap.get(candidate));
		assert candRelDescendantIdsMap.containsKey(candidate);
		totalIncluded.removeAll(candRelDescendantIdsMap.get(candidate));
	}

	private void addApprovedCandidate(Relationship candidate) {
		bestRels.add(candidate);
		//THIS COULD POTENTIALLY BE MADE FASTER
		//add the exclusive mrcas from the relationship to the totalIncluded
		//System.out.println(candRelDescendantIdsMap.get(candidate));
		if (!candRelDescendantIdsMap.containsKey(candidate)) {
			storeDescendants(candidate);
		}
		totalIncluded.addAll(candRelDescendantIdsMap.get(candidate));
	}

	private Iterable<Relationship> addNonConflictingStrict(Iterable<Relationship> rels) {
		Iterator<Relationship> relsIter = rels.iterator();
		// for every candidate relationship
		while (relsIter.hasNext()) {
			Relationship candidate = relsIter.next();
			System.out.println("\ttesting rel " + candidate.getId() + " for conflicts");
			boolean saveRel = true;
			// test for conflict between candidate against all saved
			for (Relationship saved : bestRels) {
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
				this.addApprovedCandidate(candidate);
			} else {
				System.out.println("\t\t--rel " + candidate.getId() + " failed, it will NOT be added");
			}
		}
		this.recalculateDups();
		return bestRels;
	}
	
	@Override
	public String getDescription() {
		return "prefer relationships with higher ranking, and guarantee a fully acyclic result";
	}

	@Override
	public String getReport() {
		// TODO Auto-generated method stub
		return "";
	}
}
