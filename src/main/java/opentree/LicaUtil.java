package opentree;

//import java.util.Arrays;
//import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import java.util.Iterator;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.*;
import gnu.trove.set.hash.TLongHashSet;

import opentree.RelTypes;

import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.Traversal;

/**
 * 
 * @author Stephen Smith
 * 
 *         The idea of this class is to find not a single common ancestor (as AncestorUtil)
 *         but instead to find all the non-nested Least Inclusive Common Ancestors.
 * 
 *         Note: In order for nested names to be found, nodeset, inIdSet, and fullIdSet
 *         should include not the nested names (Lonicera), but all the current children
 *         of that higher taxon name.
 */

public class LicaUtil {

	/**
	 * This will assume that the taxon sampling is complete for the trees being added. It is significantly 
	 * faster than the bipart calculator below.
	 * @param nodeSet
	 * @param inIdSet
	 * @param outIdSet
	 * @return
	 */
	public static HashSet<Node> getAllLICAt4j(List<Node> nodeSet, TLongArrayList inIdSet, TLongArrayList outIdSet) {
//		System.out.println("starting LICA search");
//		System.out.println(nodeSet);
//		System.out.println(inIdSet+" "+outIdSet);
		//System.out.println("b: "+nodeSet.size()+" - "+inIdSet.size() +" - "+fullIdSet.size());
		HashSet<Node> retaln = new HashSet<Node>();
		Node firstNode = nodeSet.get(0);// should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		long start = System.currentTimeMillis();
		for (int i = 0; i < nodeSet.size(); i++) {
			int num = 0;
			// only way to get number of relationships.
			for (Relationship rel : nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
				num++;
			}
			// exit on first node (if any) with only one relationship; same result, potentially fewer iterations
			if (num == 1) {
				firstNode = nodeSet.get(i);
				break;
			}
			if (num < fewestnumrel) {
				fewestnumrel = num;
				firstNode = nodeSet.get(i);
			}
		}
		long elapsedTimeMillis = System.currentTimeMillis()-start;
		float elapsedTimeSec = elapsedTimeMillis/1000F;
		//System.out.println("elapsed 1: "+elapsedTimeSec);
		//NOTE:outIdSet  is outmrca
		
		LicaEvaluator le = new LicaEvaluator();
		LicaContainsAllEvaluator ca = new LicaContainsAllEvaluator();
		ca.setinIDset(inIdSet);
		le.setfullIDset(outIdSet);
		Node innode = firstNode;
		start = System.currentTimeMillis();
		HashSet<Node> visited = new HashSet<Node>();
		for (Node tnode : Traversal.description().depthFirst().evaluator(le).evaluator(ca).relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode).nodes()) {
//			System.out.println("adding "+tnode);
			retaln.add(tnode);
		}
		elapsedTimeMillis = System.currentTimeMillis()-start;
		elapsedTimeSec = elapsedTimeMillis/1000F;
		//System.out.println("elapsed inloop: "+elapsedTimeSec);
		return retaln;
	}

	/**
	 * This will calculate the licas based on bipart assumption and that each of the trees being added
	 * is not complete in terms of taxa
	 * @param nodeSetsm these are the actual named nodes (not expanded by mrca)
	 * @param nodeSet these are the tip node mrcas nodes
	 * @param nodeSetinIdSet just the mrca for the nodeSet, this should be equal or smaller than inIdSet
	 * @param inIdSet this will be the mrca for the dbnodes that subtend the tree node (preorder so the children). this can be larger
	 * 			than the nodeSetinIdSet
	 * @param outIdSet this is the outIdSet for the tree
	 * @param graphdb
	 * @return
	 */
	public static HashSet<Node> getBipart4j(List<Node> nodeSetsm, List<Node> nodeSet, TLongArrayList nodeSetinIdSet, TLongArrayList inIdSet, TLongArrayList outIdSet, GraphDatabaseAgent graphdb){
		System.out.println("starting bipart lica search");
		System.out.println("smnodeset:"+nodeSetsm.size()+" nodeset:"+nodeSet.size());
		HashSet<Node> retaln = new HashSet<Node>();
		TLongArrayList testnodes = new TLongArrayList();
		LicaBipartEvaluator le = new LicaBipartEvaluator();
		le.setgraphdb(graphdb);
		if(nodeSetinIdSet.size()!= inIdSet.size()){
//			System.out.println("set small set");
			le.setSmInSet(nodeSetinIdSet);
		}
		le.setInset(inIdSet);
		le.setOutset(outIdSet);
		long start = System.currentTimeMillis();
		long start2 = System.currentTimeMillis();
		for(Node innode: nodeSetsm){			
			System.out.println("\tstarting "+innode);
			System.out.println("nodeSetinIdSet "+nodeSetinIdSet.size());
			System.out.println("inIdSet "+inIdSet.size());
			System.out.println("outIdSet "+outIdSet.size());
			le.setVisitedSet(testnodes);
			for (Node tnode : Traversal.description().breadthFirst().evaluator(le).relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode).nodes()) {
				System.out.println("\tadding "+tnode);
				retaln.add(tnode);
			}
			//long elapsedTimeMillis2 = System.currentTimeMillis()-start2;
			//float elapsedTimeSec = elapsedTimeMillis2/1000F;
			//System.out.println("\telapsed inloop1: "+elapsedTimeSec);
			start2 = System.currentTimeMillis();			
			testnodes = le.getVisitedSet();
		}
		//long elapsedTimeMillis = System.currentTimeMillis()-start;
		//float elapsedTimeSec = elapsedTimeMillis/1000F;
		//System.out.println("elapsed inloop2: "+elapsedTimeSec);
		return retaln;
	}
	
	/**
	 * This will check for a contains any of ar1 contains any ar2. Does not assume that
	 * arrays are sorted, so should be slightly slower than method for sorted arrays,
	 * but will always provide the correct answer.
	 * 
	 * @param ar1
	 * @param ar2
	 * @return true if ar1 contains any elements of ar2
	 */
	public static boolean containsAnyt4jUnsorted(TLongArrayList ar1, TLongArrayList ar2) {
		if (ar1.size() < ar2.size()) {
			return containsAnyt4jUnsortedOrdered(ar1, ar2);
		} else {
			return containsAnyt4jUnsortedOrdered(ar2, ar1);
		}
	}

	/**
	 * Internal method that is faster when the relative sizes of the inputs are known.
	 * 
	 * @param shorter
	 * @param longer
	 * @return boolean
	 */
	private static boolean containsAnyt4jUnsortedOrdered(TLongArrayList shorter, TLongArrayList longer) {
		boolean retv = false;
		for (int i = 0; i < shorter.size(); i++) {
			if (longer.contains(shorter.getQuick(i))) {
				retv = true;
				break;
			}
		}
		return retv;
	}

	/**
	 * This will check for a contains any of ar1 contains any ar2. Assumes arrays are
	 * sorted, which allows a speed improvement, but will provide wrong answers if they
	 * are not.
	 * 
	 * @param ar1
	 * @param ar2
	 * @return true if ar1 contains any elements of ar2
	 */
	public static boolean containsAnyt4jSorted(TLongArrayList ar1, TLongArrayList ar2) {
		if (ar1.size() < ar2.size()) {
			return containsAnyt4jSortedOrdered(ar1, ar2);
		} else {
			return containsAnyt4jSortedOrdered(ar2, ar1);
		}
	}

	/**
	 * Internal method that is faster when the relative sizes of the inputs are known.
	 * 
	 * @param shorter
	 * @param longer
	 * @return
	 */
	private static boolean containsAnyt4jSortedOrdered(TLongArrayList shorter, TLongArrayList longer) {
		boolean retv = false;
		shorterLoop: for (int i = 0; i < shorter.size(); i++) {
			longerLoop: for (int j = 0; j < longer.size(); j++) {
				if (longer.getQuick(j) > shorter.getQuick(i)) {
					break longerLoop;
				}
				if (longer.getQuick(j) == shorter.getQuick(i)) {
					retv = true;
					break shorterLoop;
				}
			}
		}
		return retv;
	}

	/**
	 * This should find all the least inclusive common ancestors (LICA) ignoring
	 * the sampling of the outgroup or other sampling in the source tree. The idea
	 * is to walk all the paths from one tip to and query at each mrca list as to
	 * whether it has at least the tip values necessary and none of the ones not
	 * necessary.
	 * 
	 * @param nodeSet
	 *            list of all the nodes for which we are looking for the LICA
	 * @param inIdSet
	 *            list of the ingroup ids
	 * @return an ArrayList<Node> of all the nodes that are feasible LICA
	 */
	public static HashSet<Node> getSuperLICA(List<Node> nodeSet, HashSet<Long> inIdSet) {
		HashSet<Node> retaln = new HashSet<Node>();
		Node firstNode = nodeSet.get(0);// should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		for (int i = 0; i < nodeSet.size(); i++) {
			int num = 0;
			// only way to get number of relationships
			for (Relationship rel : nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
				num++;
			}
			// exit on first node (if any) with only one relationship; same result, potentially fewer iterations
			if (num == 1) {
				firstNode = nodeSet.get(i);
				break;
			}
			if (num < fewestnumrel) {
				fewestnumrel = num;
				firstNode = nodeSet.get(i);
			}
		}
		Node innode = firstNode;
		for (Path pa : Traversal.description().depthFirst().relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode)) {
			boolean going = true;
			for (Node tnode : pa.nodes()) {
				long[] dbnodei = (long[]) tnode.getProperty("mrca");
				HashSet<Long> Ldbnodei = new HashSet<Long>();
				for (long temp : dbnodei) {
					Ldbnodei.add(temp);
				}
				// should look to apache commons primitives for a better solution to this
				// this gets all, but we want to only include the exact if one exists
				boolean containsall = Ldbnodei.containsAll(inIdSet);
				if (containsall && inIdSet.size() == Ldbnodei.size()) {
					// NOT SURE IF WE SHOULD EMPTY THE LIST IF IT IS EXACT OR RETAIN ALL THE LICAS
					// retaln.clear();
					retaln.add(tnode);
					going = false;
				} else if (containsall) {
					retaln.add(tnode);
					going = false;
				}
				if (going == false) {
					break;
				}
			}
		}
		return retaln;
	}

	public static HashSet<Node> getSuperLICAt4j(List<Node> nodeSetsm,List<Node> nodeSet, TLongArrayList nodeSetinIdSet, TLongArrayList inIdSet) {
		HashSet<Node> retaln = new HashSet<Node>();
		//changing from looking at one route to each route from the tips
		 /* Node firstNode = nodeSet.get(0);// should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		for (int i = 0; i < nodeSet.size(); i++) {
			int num = 0;
			// only way to get number of relationships
			for (Relationship rel : nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
				num++;
			}
			// exit on first node (if any) with only one relationship; same result, potentially fewer iterations
			if (num == 1) {
				firstNode = nodeSet.get(i);
				break;
			}
			if (num < fewestnumrel) {
				fewestnumrel = num;
				firstNode = nodeSet.get(i);
			}
		}
		Node innode = firstNode;*/
		LicaContainsAllEvaluatorBS ca = new LicaContainsAllEvaluatorBS();
//		if(nodeSetinIdSet.size()!= inIdSet.size()){
//			System.out.println("set small set");
//			ca.setSmInSet(nodeSetinIdSet);
//		}
		ca.setinIDset(inIdSet);
		TLongArrayList testnodes = new TLongArrayList();
		for(Node innode: nodeSetsm){
			ca.setVisitedSet(testnodes);
			System.out.println("superlica inidset: "+inIdSet.size());
			for (Node tnode : Traversal.description().depthFirst().evaluator(ca).relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode).nodes()) {
				retaln.add(tnode);
			}
			testnodes = ca.getVisitedSet();
		}
		return retaln;
	}

	/**
	 * This will return the MRCA using the taxonomic relationships. This only
	 * requires the nodes that we are looking for.
	 * 
	 * @param nodeSet
	 * @return
	 */
	public static Node getTaxonomicLICA(List<Node> nodeSet) {
		Node retaln = null;
		Node firstNode = nodeSet.get(0);// taxonomy should have only one parent so no matter which one
		Node innode = firstNode;
		TLongArrayList nodeSetLongs = new TLongArrayList();
		for (Node nd : nodeSet) {
			nodeSetLongs.add(nd.getId());
		}
		boolean going = true;
		while (going) {
			// get parent
			try{
				innode = innode.getSingleRelationship(RelTypes.TAXCHILDOF, Direction.OUTGOING).getEndNode();
			}catch(Exception e){
				e.printStackTrace();
				break;
			}
			TLongArrayList dbnodei = new TLongArrayList ((long[]) innode.getProperty("mrca"));
			dbnodei.addAll((long[]) innode.getProperty("nested_mrca"));
			if (dbnodei.containsAll(nodeSetLongs) == true) {
				retaln = innode;
				going = false;
			}
			if (going == false) {
				break;
			}
		}
		return retaln;
	}
	
	/**
	 * This will return the MRCA using the current draft tree relationships. This only
	 * requires the nodes that we are looking for.
	 * 
	 * @param inNodes
	 * @return
	 */
	public static Node getDraftTreeLICA(Iterable<Node> inNodes) {
		
		// get start node
		Iterator<Node> nodeSet = inNodes.iterator();
		Node firstNode = nodeSet.next();
		
		// extract other node ids
		TLongArrayList nodeSetLongs = new TLongArrayList();
		while (nodeSet.hasNext()) {
			nodeSetLongs.add(nodeSet.next().getId());
		}

		Node innode = firstNode;
		Node retaln = null;
		boolean going = true;
		while (going) {
			// get parent
			try {
				for (Relationship rel : innode.getRelationships(RelTypes.SYNTHCHILDOF, Direction.OUTGOING)) {
					if (String.valueOf(rel.getProperty("name")).compareTo((String) Constants.DRAFTTREENAME.value) == 0) {
						innode = rel.getEndNode();
					}
				}
			} catch(Exception e) {
				e.printStackTrace();
				break;
			}

			TLongArrayList dbnodei = new TLongArrayList ((long[]) innode.getProperty("mrca"));
			dbnodei.addAll((long[]) innode.getProperty("nested_mrca"));
			if (dbnodei.containsAll(nodeSetLongs) == true) {
				retaln = innode;
				going = false;

			} if (going == false) {
				break;
			}
		}

		return retaln;
	}
	
	/**
	 * This will return the MRCA using the taxonomic relationships. This only
	 * requires the nodes that we are looking for.
	 * 
	 * @param nodeSet
	 * @return
	 */
	public static Node getTaxonomicLICA(TLongArrayList nodeSet, GraphDatabaseAgent graphDb) {
		Node retaln = null;
		Node firstNode = graphDb.getNodeById(nodeSet.get(0));// taxonomy should have only one parent so no matter which one
		Node innode = firstNode;
		boolean going = true;
		while (going) {
			// get parent
			try{
				innode = innode.getSingleRelationship(RelTypes.TAXCHILDOF, Direction.OUTGOING).getEndNode();
			}catch(Exception e){
				break;
			}
			TLongArrayList dbnodei = new TLongArrayList ((long[]) innode.getProperty("mrca"));
			dbnodei.addAll((long[]) innode.getProperty("nested_mrca"));
			if(dbnodei.containsAll(nodeSet) == true){
				retaln = innode;
				going = false;
				break;
			}
			if (going == false) {
				break;
			}
		}
		return retaln;
	}
}