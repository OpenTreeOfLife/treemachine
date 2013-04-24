package opentree;

//import java.util.Arrays;
//import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.*;
import gnu.trove.set.hash.TLongHashSet;

import opentree.GraphBase.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.Traversal;

/**
 * 
 * @author Stephen Smith
 *
 * The idea of this class is to find not a single common ancestor (as AncestorUtil)
 * but instead to find all the non-nested Least Inclusive Common Ancestors. 
 * 
 * Note: In order for nested names to be found, nodeset, inIdSet, and fullIdSet 
 * should include not the nested names (Lonicera), but all the current children 
 * of that higher taxon name. 
 */

public class LicaUtil {
	/**
	 * This should find all the least inclusive common ancestors (LICA). The idea
	 * is to walk all the paths from one tip to and query at each mrca list as to 
	 * whether it has at least the tip values necessary and none of the ones not
	 * necessary. 
	 * 
	 * @param nodeSet list of all the nodes for which we are looking for the LICA
	 * @param inIdSet list of the ingroup ids
	 * @param fullIdSet list of all the node ids for the tree of interest
	 * @return an ArrayList<Node> of all the nodes that are feasible LICA
	 */
	public static HashSet<Node> getAllLICA(List<Node> nodeSet, HashSet<Long> inIdSet, HashSet<Long> fullIdSet) {
		HashSet<Node> retaln = new HashSet<Node>();
		Node firstNode = nodeSet.get(0);//should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		//long start = System.currentTimeMillis();
		for (int i = 0; i < nodeSet.size(); i++) {
			int num = 0;
			//only way to get number of relationships.
			for (Relationship rel: nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
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
		//long elapsedTimeMillis = System.currentTimeMillis()-start;
		//float elapsedTimeSec = elapsedTimeMillis/1000F;
		//System.out.println("elapsed 1: "+elapsedTimeSec);
		//remove everything but that which is in the outgroup
		fullIdSet.removeAll(inIdSet);
		
		Node innode = firstNode;
		//start = System.currentTimeMillis();
		for ( Path pa : Traversal.description()
			.depthFirst()
			.relationships( RelTypes.MRCACHILDOF, Direction.OUTGOING )
			.traverse( innode ) ) {
			boolean going = true;
			for (Node tnode: pa.nodes()) {
				//as long as these are sorted we can do a faster comparison
				long [] dbnodei = (long []) tnode.getProperty("mrca");
				HashSet<Long> Ldbnodei = new HashSet<Long>();
				for(long temp:dbnodei) {Ldbnodei.add(temp);}
				//should look to apache commons primitives for a better solution to this
				int beforesize = Ldbnodei.size();
				Ldbnodei.removeAll(fullIdSet);
				if (Ldbnodei.size() == beforesize) {
					//this gets all, but we want to only include the exact if one exists
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
		//elapsedTimeMillis = System.currentTimeMillis()-start;
		//elapsedTimeSec = elapsedTimeMillis/1000F;
		//System.out.println("elapsed inloop: "+elapsedTimeSec);
		return retaln;
	}
	
	public static HashSet<Node> getAllLICAt4j(List<Node> nodeSet, TLongArrayList inIdSet, TLongArrayList fullIdSet) {
		HashSet<Node> retaln = new HashSet<Node>();
		Node firstNode = nodeSet.get(0);//should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		//long start = System.currentTimeMillis();
		for (int i = 0; i < nodeSet.size(); i++) {
			int num = 0;
			//only way to get number of relationships.
			for (Relationship rel: nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
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
		//long elapsedTimeMillis = System.currentTimeMillis()-start;
		//float elapsedTimeSec = elapsedTimeMillis/1000F;
		//System.out.println("elapsed 1: "+elapsedTimeSec);
		//remove everything but that which is in the outgroup
		fullIdSet.removeAll(inIdSet);
		
		Node innode = firstNode;
		//start = System.currentTimeMillis();
		for ( Path pa : Traversal.description()
			.breadthFirst()//was depthFirst
			.relationships( RelTypes.MRCACHILDOF, Direction.OUTGOING )
			.traverse( innode ) ) {
			boolean going = true;
			for (Node tnode: pa.nodes()) {
				//as long as these are sorted we can do a faster comparison
				//long [] dbnodei = (long []) tnode.getProperty("mrca");
				//HashSet<Long> Ldbnodei = new HashSet<Long>();
				//for(long temp:dbnodei) {Ldbnodei.add(temp);}
				TLongArrayList Ldbnodei = new TLongArrayList((long[]) tnode.getProperty("mrca"));
				Ldbnodei.sort();
				//should look to apache commons primitives for a better solution to this
//				int beforesize = Ldbnodei.size();
//				Ldbnodei.removeAll(fullIdSet);
				if(containsAnySortedt4j(Ldbnodei,fullIdSet) == false){
//				if (Ldbnodei.size() == beforesize) {
					//this gets all, but we want to only include the exact if one exists
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
		//elapsedTimeMillis = System.currentTimeMillis()-start;
		//elapsedTimeSec = elapsedTimeMillis/1000F;
		//System.out.println("elapsed inloop: "+elapsedTimeSec);
		return retaln;
	}
	
	/**
	 * This will check for a contains any of ar1 contains any ar2. This currently doesn't 
	 * account for sorted arrays
	 * @return
	 */
	private static boolean containsAnyt4j(TLongArrayList ar1, TLongArrayList ar2){
		boolean retv = false;
		for (int i=0;i<ar2.size();i++){
			if(ar1.contains(ar2.getQuick(i))){
				retv = true;
				break;
			}
		}
		return retv;
	}
	
	/**
	 * This will check for a contains any of ar1 contains any ar2. This currently doesn't 
	 * account for sorted arrays
	 * @return
	 */
	private static boolean containsAnySortedt4j(TLongArrayList ar1, TLongArrayList ar2){
		boolean retv = false;
		for (int i=0;i<ar2.size();i++){
			for(int j=0;j<ar1.size();j++){
				if (ar2.getQuick(i) == ar1.getQuick(j)){
					retv = true;
					return retv;
				}if(ar1.getQuick(j) > ar2.getQuick(i)){
					break;
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
	 * @param nodeSet list of all the nodes for which we are looking for the LICA
	 * @param inIdSet list of the ingroup ids
	 * @return an ArrayList<Node> of all the nodes that are feasible LICA
	 */
	public static HashSet<Node> getSuperLICA(List<Node> nodeSet, HashSet<Long> inIdSet) {
		HashSet<Node> retaln = new HashSet<Node>();
		Node firstNode = nodeSet.get(0);//should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		for (int i = 0; i < nodeSet.size(); i++) {
			int num = 0;
			//only way to get number of relationships
			for (Relationship rel: nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
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
		for ( Path pa : Traversal.description()
			.depthFirst()
			.relationships( RelTypes.MRCACHILDOF, Direction.OUTGOING )
			.traverse( innode ) ) {
			boolean going = true;
			for (Node tnode: pa.nodes()) {
				long [] dbnodei = (long []) tnode.getProperty("mrca");
				HashSet<Long> Ldbnodei = new HashSet<Long>();
				for (long temp:dbnodei) {
					Ldbnodei.add(temp);
				}
				//should look to apache commons primitives for a better solution to this
				//this gets all, but we want to only include the exact if one exists
				boolean containsall = Ldbnodei.containsAll(inIdSet);
				if (containsall && inIdSet.size() == Ldbnodei.size()) {
					//NOT SURE IF WE SHOULD EMPTY THE LIST IF IT IS EXACT OR RETAIN ALL THE LICAS
					//retaln.clear();
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
		Node firstNode = nodeSet.get(0);//should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		for (int i = 0; i < nodeSet.size(); i++) {
			int num = 0;
			//only way to get number of relationships
			for (Relationship rel: nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
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
		for ( Path pa : Traversal.description()
			.depthFirst()
			.relationships( RelTypes.MRCACHILDOF, Direction.OUTGOING )
			.traverse( innode ) ) {
			boolean going = true;
			for (Node tnode: pa.nodes()) {
				TLongSet Ldbnodei = new TLongHashSet((long[]) tnode.getProperty("mrca"));
				//should look to apache commons primitives for a better solution to this
				//this gets all, but we want to only include the exact if one exists
				boolean containsall = Ldbnodei.containsAll(inIdSet);
				if (containsall && inIdSet.size() == Ldbnodei.size()) {
					//NOT SURE IF WE SHOULD EMPTY THE LIST IF IT IS EXACT OR RETAIN ALL THE LICAS
					//retaln.clear();
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
	
	/**
	 * This will return the MRCA using the taxonomic relationships. This only
	 * requires the nodes that we are looking for.
	 * 
	 * @param nodeSet
	 * @return
	 */
	public static Node getTaxonomicLICA(List<Node> nodeSet){
		Node retaln = null;
		Node firstNode = nodeSet.get(0);//taxonomy should have only one parent so no matter which one
		Node innode = firstNode;
		ArrayList<Long> nodeSetLongs = new ArrayList<Long>();
		for(Node nd:nodeSet){
			nodeSetLongs.add(nd.getId());
		}
		boolean going = true;
		while(going){
			//get parent
			innode = innode.getSingleRelationship(RelTypes.TAXCHILDOF, Direction.OUTGOING).getEndNode();
			long [] dbnodei = (long []) innode.getProperty("mrca");
			HashSet<Long> Ldbnodei = new HashSet<Long>();
			for (long temp:dbnodei) {
				Ldbnodei.add(temp);
			}
			//should look to apache commons primitives for a better solution to this
			//this gets all, but we want to only include the exact if one exists
			boolean containsall = Ldbnodei.containsAll(nodeSetLongs);
			if (containsall ==true) {
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
