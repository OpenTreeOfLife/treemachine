package opentree;

//import java.util.Arrays;
//import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.*;
import gnu.trove.set.hash.TLongHashSet;

import opentree.RelTypes;

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
	 * This should find all the least inclusive common ancestors (LICA). The idea
	 * is to walk all the paths from one tip to and query at each mrca list as to
	 * whether it has at least the tip values necessary and none of the ones not
	 * necessary.
	 * 
	 * @param nodeSet
	 *            list of all the nodes for which we are looking for the LICA
	 * @param inIdSet
	 *            list of the ingroup ids
	 * @param fullIdSet
	 *            list of all the node ids for the tree of interest
	 * @return an ArrayList<Node> of all the nodes that are feasible LICA
	 */
	public static HashSet<Node> getAllLICA(List<Node> nodeSet, HashSet<Long> inIdSet, HashSet<Long> fullIdSet) {
		HashSet<Node> retaln = new HashSet<Node>();
		Node firstNode = nodeSet.get(0);// should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		// long start = System.currentTimeMillis();
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
		// long elapsedTimeMillis = System.currentTimeMillis()-start;
		// float elapsedTimeSec = elapsedTimeMillis/1000F;
		// System.out.println("elapsed 1: "+elapsedTimeSec);
		// remove everything but that which is in the outgroup
		fullIdSet.removeAll(inIdSet);

		Node innode = firstNode;
		// start = System.currentTimeMillis();
		for (Path pa : Traversal.description().depthFirst().relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode)) {
			boolean going = true;
			for (Node tnode : pa.nodes()) {
				// as long as these are sorted we can do a faster comparison
				long[] dbnodei = (long[]) tnode.getProperty("mrca");
				HashSet<Long> Ldbnodei = new HashSet<Long>();
				for (long temp : dbnodei) {
					Ldbnodei.add(temp);
				}
				// should look to apache commons primitives for a better solution to this
				int beforesize = Ldbnodei.size();
				Ldbnodei.removeAll(fullIdSet);
				if (Ldbnodei.size() == beforesize) {
					// this gets all, but we want to only include the exact if one exists
					boolean containsall = Ldbnodei.containsAll(inIdSet);
					if (containsall) {
						retaln.add(tnode);
						going = false;
					}
				} else {
					going = false;
				}
				if (going == false) {
					break;
				}
			}
		}
		// elapsedTimeMillis = System.currentTimeMillis()-start;
		// elapsedTimeSec = elapsedTimeMillis/1000F;
		// System.out.println("elapsed inloop: "+elapsedTimeSec);
		return retaln;
	}

	public static HashSet<Node> getAllLICAt4j(List<Node> nodeSet, TLongArrayList inIdSet, TLongArrayList fullIdSet) {
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
		System.out.println("elapsed 1: "+elapsedTimeSec);
		//remove everything but that which is in the outgroup
		fullIdSet.removeAll(inIdSet);
		LicaEvaluator le = new LicaEvaluator();
		le.setfullIDset(fullIdSet);
		Node innode = firstNode;
		start = System.currentTimeMillis();
		HashSet<Node> visited = new HashSet<Node>();
		for (Node tnode : Traversal.description().depthFirst().evaluator(le).relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode).nodes()) {
		//for (Path pa : Traversal.description().depthFirst().relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode)) {	
			boolean going = true;
			//for (Node tnode : pa.nodes()) {
				if (visited.contains(tnode))
					continue;
				else
					visited.add(tnode);
		//		System.out.println(tnode.getId()+" "+tnode.getProperty("name"));
				TLongArrayList Ldbnodei = new TLongArrayList((long[]) tnode.getProperty("mrca"));
				System.out.println(Ldbnodei.size()+" "+fullIdSet.size()+" "+inIdSet.size());
				//Ldbnodei.sort();
				// should look to apache commons primitives for a better solution to this
				//if (containsAnyt4jSorted(Ldbnodei, fullIdSet) == false) {
					boolean containsall = Ldbnodei.containsAll(inIdSet);
					if (containsall) {
						retaln.add(tnode);
						going = false;
					}
				//} else {
				//	going = false;
				//}
				System.out.println(Ldbnodei.size()+" "+fullIdSet.size()+" "+inIdSet.size()+" "+going);
				//if (going == false) {
				//	break;
				//}
			//}
		}
		elapsedTimeMillis = System.currentTimeMillis()-start;
		elapsedTimeSec = elapsedTimeMillis/1000F;
		System.out.println("elapsed inloop: "+elapsedTimeSec);
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

	public static HashSet<Node> getSuperLICAt4j(List<Node> nodeSet, TLongArrayList inIdSet) {
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
		HashSet<Node> visited = new HashSet<Node>();
		//for (Path pa : Traversal.description().depthFirst().relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode)) {
		for (Node tnode : Traversal.description().depthFirst().relationships(RelTypes.MRCACHILDOF, Direction.OUTGOING).traverse(innode).nodes()) {
			boolean going = true;
			//for (Node tnode : pa.nodes()) {
				if (visited.contains(tnode))
					continue;
				else
					visited.add(tnode);
				TLongSet Ldbnodei = new TLongHashSet((long[]) tnode.getProperty("mrca"));
				boolean containsall = Ldbnodei.containsAll(inIdSet);
				if (containsall && inIdSet.size() == Ldbnodei.size()) {
					// NOT SURE IF WE SHOULD EMPTY THE LIST IF IT IS EXACT OR RETAIN ALL THE LICAS
					retaln.add(tnode);
					going = false;
				} else if (containsall) {
					retaln.add(tnode);
					going = false;
				}
				//if (going == false) {
				//	break;
				//}
			//}
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
		ArrayList<Long> nodeSetLongs = new ArrayList<Long>();
		for (Node nd : nodeSet) {
			nodeSetLongs.add(nd.getId());
		}
		boolean going = true;
		while (going) {
			// get parent
			try{
				innode = innode.getSingleRelationship(RelTypes.TAXCHILDOF, Direction.OUTGOING).getEndNode();
			}catch(Exception e){
				break;
			}
			long[] dbnodei = (long[]) innode.getProperty("mrca");
			HashSet<Long> Ldbnodei = new HashSet<Long>();
			for (long temp : dbnodei) {
				Ldbnodei.add(temp);
			}
			// should look to apache commons primitives for a better solution to this
			// this gets all, but we want to only include the exact if one exists
			boolean containsall = Ldbnodei.containsAll(nodeSetLongs);
			if (containsall == true) {
				retaln = innode;
				going = false;
			}
			if (going == false) {
				break;
			}
		}
		return retaln;
	}
}