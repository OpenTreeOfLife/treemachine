package opentree;

import jade.tree.JadeNode;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;

import java.util.Iterator;

import gnu.trove.list.array.TLongArrayList;
import opentree.constants.GeneralConstants;
import opentree.constants.RelType;

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
		HashSet<Node> retaln = new HashSet<Node>();
		Node firstNode = nodeSet.get(0);// should be the node with the fewest outgoing relationships
		int fewestnumrel = 10000000;
		for (int i = 0; i < nodeSet.size(); i++) {
			int num = 0;
			// only way to get number of relationships.
			for (Relationship rel : nodeSet.get(i).getRelationships(Direction.OUTGOING, RelType.MRCACHILDOF)) {
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
		//NOTE:outIdSet  is outmrca		
		LicaEvaluator le = new LicaEvaluator();
		LicaContainsAllEvaluator ca = new LicaContainsAllEvaluator();
		ca.setinIDset(inIdSet);
		le.setfullIDset(outIdSet);
		Node innode = firstNode;
		TLongArrayList visited = new TLongArrayList();
		ca.setVisitedSet(visited);
		for (Node tnode : Traversal.description().depthFirst().evaluator(le).evaluator(ca).relationships(RelType.MRCACHILDOF, Direction.OUTGOING).traverse(innode).nodes()) {
//			System.out.println("adding "+tnode);
			ca.setVisitedSet(visited);
			retaln.add(tnode);
			visited = ca.getVisitedSet();
		}
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
	public static HashSet<Node> getBipart4j(JadeNode jn,List<Node> nodeSetsm, List<Node> nodeSet, TLongArrayList nodeSetinIdSet, TLongArrayList inIdSet, TLongArrayList outIdSet, GraphDatabaseAgent graphdb){
//		System.out.println("starting bipart lica search");
//		System.out.println("smnodeset:"+nodeSetsm.size()+" nodeset:"+nodeSet.size());
		HashSet<Node> retaln = new HashSet<Node>();
		TLongArrayList testnodes = new TLongArrayList();
		LicaBipartEvaluatorBS le = new LicaBipartEvaluatorBS();
		le.setJadeNode(jn);
		le.setgraphdb(graphdb);
//		if(nodeSetinIdSet.size()!= inIdSet.size()){
//			System.out.println("set small set");
//			le.setSmInSet(nodeSetinIdSet);
//		}
		le.setInset(inIdSet);
		le.setOutset(outIdSet);
		for(Node innode: nodeSetsm){			
//			System.out.println("\tstarting "+innode);
//			System.out.println("nodeSetinIdSet "+nodeSetinIdSet.size());
//			System.out.println("inIdSet "+inIdSet.size());
//			System.out.println("outIdSet "+outIdSet.size());
			le.setVisitedSet(testnodes);
			for (Node tnode : Traversal.description().breadthFirst().evaluator(le).relationships(RelType.MRCACHILDOF, Direction.OUTGOING).traverse(innode).nodes()) {
//				System.out.println("\tadding "+tnode);
				retaln.add(tnode);
			}
			testnodes = le.getVisitedSet();
		}
		return retaln;
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
			for (Relationship rel : nodeSet.get(i).getRelationships(Direction.OUTGOING, RelType.MRCACHILDOF)) {
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
		for (Path pa : Traversal.description().depthFirst().relationships(RelType.MRCACHILDOF, Direction.OUTGOING).traverse(innode)) {
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
//			ca.setSmInSet(nodeSetinIdSet);
//		}
		ca.setinIDset(inIdSet);
		TLongArrayList testnodes = new TLongArrayList();
		for(Node innode: nodeSetsm){
			ca.setVisitedSet(testnodes);
//			System.out.println("superlica inidset: "+inIdSet.size());
			for (Node tnode : Traversal.description().depthFirst().evaluator(ca).relationships(RelType.MRCACHILDOF, Direction.OUTGOING).traverse(innode).nodes()) {
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
				innode = innode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
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
		return getSynthTreeLICA(inNodes, (String) GeneralConstants.DRAFT_TREE_NAME.value);
	}

	protected static void checkNodeId(Node n) {
		// eventually need bitset that accepts long ids
		if (Integer.MAX_VALUE < n.getId()) {
			throw new java.lang.ArrayIndexOutOfBoundsException("the node id " + n.getId() + " exceeds the maximum integer value");
		}	
	}
	
	/**
	 * This will return the MRCA using the specified synthesis tree relationships. This only
	 * requires the nodes that we are looking for.
	 * 
	 * @param inNodes
	 * @return
	 */
	public static Node getSynthTreeLICA(Iterable<Node> inNodes, String treeName) {
		
		// TODO: going to need a check somewhere in here to make sure all the nodes are in the draft tree
		
		// get start node
		Iterator<Node> nodeIter = inNodes.iterator();

		if (! nodeIter.hasNext()) {
			throw new java.lang.NullPointerException("attempt to get MRCA of zero taxa");
		}
		Node firstNode = nodeIter.next();

		// just one node, so it is the mrca
		if (! nodeIter.hasNext()) {
			return firstNode;
		}

//		System.out.println(firstNode.toString());
		
		// extract other node ids
//		TLongArrayList nodeSetLongs = new TLongArrayList();
//		LinkedList<Long> ids = new LinkedList<Long>();
		BitSet refIdBits = new BitSet();
		int nNodes = 0;
		while (nodeIter.hasNext()) {
			Node curNode = nodeIter.next();
			if (curNode != null) {
//				System.out.println(curNode.toString());
//				nodeSetLongs.add(curNode.getId());
				checkNodeId(curNode);
				
				refIdBits.set((int) curNode.getId());
				nNodes++;
			}
		}

		Node innode = firstNode;
		Node retaln = null;
		boolean going = true;
		while (going) {
			// get parent
			try {
				boolean found = false;
				for (Relationship rel : innode.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
					if (String.valueOf(rel.getProperty("name")).compareTo(treeName) == 0) {
						found = true;
						innode = rel.getEndNode();
						break;
					}
				}
				
				if (! found) {
					if (innode.getId() != 1) { // test if this is the life node... but is this always going to be true for the life node?
						throw new java.lang.IllegalArgumentException("The node " + innode.getId() + " is not in the tree " + treeName);
					} else {
						return innode;
					}
				}
			} catch(Exception e) {
				e.printStackTrace();
				break;
			}

			TLongArrayList curIds = new TLongArrayList ((long[]) innode.getProperty("mrca"));
			BitSet curIdBits = new BitSet((int) curIds.max());
			for (int i = 0; i < curIds.size(); i++) {
//				Node curNode = nodeIter.next();
//				if (curNode != null) {
	//				System.out.println(curNode.toString());
	//				nodeSetLongs.add(curNode.getId());
					
//					checkNodeId(curNode);
					
					if (Integer.MAX_VALUE < curIds.get(i)) {
						throw new java.lang.ArrayIndexOutOfBoundsException("the node id " + curIds.get(i) + " exceeds the maximum integer value");
					}
					refIdBits.set((int) curIds.get(i));
			}
			
			// this might not be necessary...
//			if (innode.hasProperty("nested_mrca")) {
//				dbnodei.addAll((long[]) innode.getProperty("nested_mrca"));
//			}
			
//			if (dbnodei.containsAll(nodeSetLongs) == true) {
			curIdBits.and(refIdBits);
			if (curIdBits.cardinality() == nNodes) {
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
				innode = innode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
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
}