package opentree.synthesis.conflictresolution;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import opentree.LicaUtil;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.opentree.bitarray.ImmutableCompactLongSet;
import org.opentree.bitarray.MutableCompactLongSet;

import scala.collection.immutable.Stack;

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
		if(((String)rel.getProperty("source")).equals("taxonomy") == false
				&& rel.hasProperty("compat")==false){
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
	@Override
	public TLongHashSet getDupMRCAS(){
		return dupMRCAS;
	}
	
	
	@Override
	public Iterable<Relationship> resolveConflicts(Iterable<Relationship> rels,boolean reinitialize) {
		if (reinitialize) {
			initialize();
		}
		Iterator<Relationship> relsIter = rels.iterator();
		//these are all the mrcas that are actually included in the set of saveRels
		TLongHashSet totalIncluded = new TLongHashSet();
		//these are all the mrcas that are included in the subtending nodes
		TLongHashSet totalMRCAS = new TLongHashSet();
		//pick best rel from the ranks and edges
		
		if(relsIter.hasNext() == false)
			return bestRels;

		HashMap<Integer,HashMap<Integer,HashSet<Node>>> ranksets = new HashMap<Integer,HashMap<Integer,HashSet<Node>>>();
		HashMap<Integer,HashMap<Integer,HashSet<Relationship>>> ranksetsrels = new HashMap<Integer,HashMap<Integer,HashSet<Relationship>>>();
		for(Relationship rel: rels){
			Integer sourcerank = (Integer) rel.getProperty("sourcerank");
			Integer edgeid = (Integer) rel.getProperty("sourceedgeid");
			if(ranksets.containsKey(sourcerank)==false){
				ranksets.put(sourcerank,new HashMap<Integer,HashSet<Node>>());
				ranksetsrels.put(sourcerank,new HashMap<Integer,HashSet<Relationship>>());
			}
			if(ranksets.get(sourcerank).containsKey(edgeid) == false){
				ranksets.get(sourcerank).put(edgeid, new HashSet<Node>());
				ranksetsrels.get(sourcerank).put(edgeid, new HashSet<Relationship>());
			}
			ranksets.get(sourcerank).get(edgeid).add(rel.getStartNode());
			ranksetsrels.get(sourcerank).get(edgeid).add(rel);
		}
		HashSet<Node> bestSet = new HashSet<Node>();
		HashSet<Relationship> bestSetRel = new HashSet<Relationship>();
		HashMap<Relationship,MutableCompactLongSet> bestRelCLS = new HashMap<Relationship,MutableCompactLongSet>();
		
		List<Integer> ranks = new ArrayList<Integer>(ranksetsrels.keySet());
		Collections.reverse(ranks);

		
		
		/*
		 * for each rank
		 */
		for(Integer ti: ranks){
			System.out.println("working on rank: "+ti);
			HashMap<MutableCompactLongSet,HashSet<Relationship>> combos = new HashMap<MutableCompactLongSet,HashSet<Relationship>>();
			for(Relationship ed: bestRelCLS.keySet()){
				MutableCompactLongSet combinations = new MutableCompactLongSet();
				combinations.addAll(bestRelCLS.get(ed));
				HashSet<Relationship> edges = new HashSet<Relationship>();
				edges.add(ed);
				for(Relationship ed2: bestRelCLS.keySet()){
					if (ed != ed2){
						combinations.addAll(bestRelCLS.get(ed2));
						edges.add(ed2);
						combos.put(combinations,edges);
					}
				}
			}
			
			//for each rel in this rank, check to see if there is overlap, if so, only keep if they would completely
			//    encompass a set
			for(Integer edge: ranksetsrels.get(ti).keySet()){
				MutableCompactLongSet ics = new MutableCompactLongSet(
						(long [])ranksetsrels.get(ti).get(edge).iterator().next().getProperty("exclusive_mrca"));
				Relationship bestRelforEdge = null;
				boolean replace = false;
				HashSet<Relationship> toReplace = new HashSet<Relationship>();
				int bestinternal = 0;
				int highestedges = 0;
				int highestmrca = 0;
				boolean overlap = false;
				for(Relationship rel: ranksetsrels.get(ti).get(edge)){
					MutableCompactLongSet mcs = new MutableCompactLongSet((long[])rel.getStartNode().getProperty("mrca"));
					for(Relationship rel2: bestRelCLS.keySet()){
						MutableCompactLongSet mcs2 = new MutableCompactLongSet((long[])rel2.getStartNode().getProperty("mrca"));
						if(mcs.containsAny(bestRelCLS.get(rel2)) || mcs.containsAny(mcs2) ){
							System.out.println("\t\toverlap with "+rel +" "+rel2);
							overlap = true;
							//find whether there is more than one edge
							for(MutableCompactLongSet mmcs: combos.keySet()){
								if(mcs.containsAll(mmcs)){
									System.out.println("\t\t\t will replace "+combos.get(mmcs) +" with "+rel);
									replace = true;
									if(mcs.size() > highestmrca && combos.get(mmcs).size()>=highestedges){
										highestedges = combos.get(mmcs).size();
										highestmrca = (int) mcs.size();
										bestRelforEdge = rel;
										toReplace = combos.get(mmcs);
									}
								}
							}
						}
					}if(overlap == false){
						Node nd = rel.getStartNode();
						if(((long[])nd.getProperty("mrca")).length > bestinternal){
							bestRelforEdge = rel;
							bestinternal = ((long[])nd.getProperty("mrca")).length;
						}
					}
				}
				/*
				 * need to add the tip bit
				 */
				System.out.println("    edge:"+edge+" "+bestRelforEdge);
				if(bestRelforEdge != null){
					if(replace == true){
						MutableCompactLongSet ns = new MutableCompactLongSet(ics);
						for(Relationship r: toReplace){
							bestSet.remove(r.getStartNode());
							bestSetRel.remove(r);
							ns.addAll(bestRelCLS.get(r));
							bestRelCLS.remove(r);
						}
						bestSetRel.add(bestRelforEdge);
						bestRelCLS.put(bestRelforEdge, ns);
						bestSet.add(bestRelforEdge.getStartNode());
					}else if(overlap == false){
						bestSet.add(bestRelforEdge.getStartNode());
						bestSetRel.add(bestRelforEdge);
						bestRelCLS.put(bestRelforEdge, ics);
					}
				}
				
			}
		}
		System.out.println(" bestRels:"+bestSetRel);
		bestRels = new LinkedList<Relationship>(bestSetRel);
		
		return bestRels;
		
	}
	//@Override
	public Iterable<Relationship> resolveConflictsOLD(Iterable<Relationship> rels,boolean reinitialize) {
		if (reinitialize) {
			initialize();
		}
		Iterator<Relationship> relsIter = rels.iterator();
		//these are all the mrcas that are actually included in the set of saveRels
		TLongHashSet totalIncluded = new TLongHashSet();
		//these are all the mrcas that are included in the subtending nodes
		TLongHashSet totalMRCAS = new TLongHashSet();
		//pick best rel from the ranks and edges
		
		if(relsIter.hasNext() == false)
			return bestRels;
		/*
		 * because we need to make sure that we have the best ranked thing accounted for we are going to do that first
		 */
		HashMap<Integer,ImmutableCompactLongSet> rank1requirements = new HashMap<Integer,ImmutableCompactLongSet>();
		HashMap<Integer,HashMap<Integer,HashSet<Node>>> ranksets = new HashMap<Integer,HashMap<Integer,HashSet<Node>>>();
		HashMap<Integer,HashMap<Integer,HashSet<Relationship>>> ranksetsrels = new HashMap<Integer,HashMap<Integer,HashSet<Relationship>>>();
		Integer highestrank = 0;
		for(Relationship rel: rels){
			Integer sourcerank = (Integer) rel.getProperty("sourcerank");
			if (sourcerank > highestrank)
				highestrank = sourcerank;
			Integer edgeid = (Integer) rel.getProperty("sourceedgeid");
			if(ranksets.containsKey(sourcerank)==false){
				ranksets.put(sourcerank,new HashMap<Integer,HashSet<Node>>());
				ranksetsrels.put(sourcerank,new HashMap<Integer,HashSet<Relationship>>());
			}
			if(ranksets.get(sourcerank).containsKey(edgeid) == false){
				ranksets.get(sourcerank).put(edgeid, new HashSet<Node>());
				ranksetsrels.get(sourcerank).put(edgeid, new HashSet<Relationship>());
			}
			ranksets.get(sourcerank).get(edgeid).add(rel.getStartNode());
			ranksetsrels.get(sourcerank).get(edgeid).add(rel);
		}
		System.out.println("highest:"+highestrank+" "+ranksets);
		HashMap<Integer,Node> bestSet = new HashMap<Integer,Node>();
		HashMap<Integer,Relationship> bestSetRel = new HashMap<Integer,Relationship>();
		
		for(Integer edge: ranksetsrels.get(highestrank).keySet()){
			ImmutableCompactLongSet ics = new ImmutableCompactLongSet(
					(long [])ranksetsrels.get(highestrank).get(edge).iterator().next().getProperty("exclusive_mrca"));
			rank1requirements.put(edge, ics);
			int bestinternal = 0;
			Node bestnode = null;
			Relationship bestrel = null;
			for(Relationship rel: ranksetsrels.get(highestrank).get(edge)){
				Node nd = rel.getStartNode();
				if (((long[])nd.getProperty("mrca")).length > bestinternal){
					bestinternal = ((long[])nd.getProperty("mrca")).length;
					bestnode = nd;
					bestrel = rel;
				}
			}
			bestSet.put(edge, bestnode);
			bestSetRel.put(edge, bestrel);
		}
		System.out.println(bestSetRel);
		/*
		 * NEED TO MAKE UNIQUE COMBINTAIONS OF EXCLUSIVE MRCAS FOR TOP RANK TREE COMPARISON BELOW
		 * now all we are checking are per rel! whether there is something that can be a parent of a set of the best rels
		 */
		HashMap<MutableCompactLongSet,HashSet<Integer>> combos = new HashMap<MutableCompactLongSet,HashSet<Integer>>();
		for(Integer ed: rank1requirements.keySet()){
			MutableCompactLongSet combinations = new MutableCompactLongSet();
			combinations.addAll(rank1requirements.get(ed));
			HashSet<Integer> edges = new HashSet<Integer>();
			edges.add(ed);
			for(Integer ed2: rank1requirements.keySet()){
				if (ed != ed2){
					combinations.addAll(rank1requirements.get(ed2));
					edges.add(ed2);
					combos.put(combinations,edges);
				}
			}
		}
		System.out.println(combos);
		
		
		/*
		 * for each other rel, we want to check whether it is the parent of each 
		 * combination of relexclusivemrcas
		 */
		List<Integer> ranks = new ArrayList(ranksetsrels.keySet());
		Collections.sort(ranks);

		for(Integer ti: ranks){
			if(ti == highestrank)
				continue;
			for(Integer ti2: ranksetsrels.get(ti).keySet()){
				int highestedges = 0;
				int highestmrca = 0;
				MutableCompactLongSet replace = null;
				Relationship replacerel = null;
				for(Relationship rel: ranksetsrels.get(ti).get(ti2)){
					Node nd = rel.getStartNode();
					ImmutableCompactLongSet ics = new ImmutableCompactLongSet(((long[])nd.getProperty("mrca")));
					/*
					 * we want the most all encompassing (has the most edges included and then the largest set of mrcas
					 */
					for(MutableCompactLongSet mcs: combos.keySet()){
						if(ics.containsAll(mcs)){
							System.out.print(rel+" may be better than ");
							for(Integer edge: combos.get(mcs)){
								System.out.print(bestSetRel.get(edge));
							}
							System.out.print("\n");
							if(ics.size() > highestmrca && combos.get(mcs).size()>=highestedges){
								highestedges = combos.get(mcs).size();
								highestmrca = (int) ics.size();
								replace = mcs;
								replacerel = rel;
							}
						}
					}
				}
				/*
				 * NEED TO MAKE SURE THIS IS IN THE RIGHT ORDER OF RANK AND STOP AT SOME POINT
				 */
				if(highestedges > 0){
					System.out.println(highestedges+" "+highestmrca+" would replace "+replace+" with Rel "+replacerel);
					for(Integer edge: combos.get(replace)){
						bestSetRel.put(edge,replacerel);
					}
					combos.remove(replace);
				}
			}
		}
		/* AFTER CHECKING TO SEE IF THERE IS A REL IN LOWER RANK THAT IS BETTER NESTED
		 * NOW CHECK TO SEE IF THERE IS A BETTER ONE THAT JUST HAS MORE MRCAS, SHOULD BE JUST TIPS HERE
		 * check for individual edges that may be left
		 * for(Integer edge: rank1requirements.keySet()){                                                                           
			HashSet<Integer> contains = new HashSet<Integer>();
			MutableCompactLongSet curset = new MutableCompactLongSet(rank1requirements.get(edge));
			if(ics.containsAll(curset)){
				System.out.println(rel+" may be better than "+bestSetRel.get(edge));
			}
		}*/
		
		/*
		 * NEED TO ADD THE OTHER, NONOVERLAPPING RELS INCLUDING TAXONOMY
		 */
		bestRels = new LinkedList<Relationship>(bestSetRel.values());
		//need to add the ones that don't overlap with highest rank with the same idea as above
		return bestRels;
	    // for every candidate relationship
	    /*while (relsIter.hasNext()) {
	    	
	    	Relationship candidate = relsIter.next();
	    	System.out.println("\ttesting rel " + candidate.getId() + " rank:"+candidate.getProperty("sourcerank")+" for conflicts");

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
	    		TLongHashSet fullmrcas = new TLongHashSet((long[])candidate.getStartNode().getProperty("mrca"));
	    		fullmrcas.retainAll(totalMRCAS);
	    		dupMRCAS.addAll(fullmrcas);
	    		//System.out.println("dups:"+dupMRCAS.size());
	    		//get the full mrcas and add any new ones to the totalmrcas
	    		TLongHashSet fullmrcas2 = new TLongHashSet((long[])candidate.getStartNode().getProperty("mrca"));
	    		totalMRCAS.addAll(fullmrcas2);
	    	}else{
	    		System.out.println("\t\t--rel " + candidate.getId() + " failed, it will NOT be added");
	    	}
	    }

		return bestRels;*/
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
