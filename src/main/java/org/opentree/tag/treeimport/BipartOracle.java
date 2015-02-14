package org.opentree.tag.treeimport;

import jade.tree.JadeBipartition;
import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.NodeOrder;
import jade.tree.TreeParseException;
import jade.tree.TreePrinter;
import jade.tree.TreeReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import opentree.GraphBase;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.opentree.bitarray.TLongBitArraySet;
import org.opentree.graphdb.GraphDatabaseAgent;

/*
 * TODO: create interfaces for Tree and TreeNode, and implement them on JadeTree/JadeNode,
 * NexsonTree/NexsonNode. Then, adjust import procedures to be ambivalent to source of the
 * tree.
 */

public class BipartOracle {

	private final GraphDatabaseAgent gdb;

	// these are just for associating input names with temporary ids. we should be using neo4j node ids.
	Map<String, Long> nodeIdForName = new HashMap<String, Long>();
	Map<Long, String> nameForNodeId = new HashMap<Long, String>();
	
	// indices in these correspond nodes in input trees
	JadeNode[] treeNode;
	int[][] childrenOf;
	TLongBipartition[] original;

	// these map biparts to tree nodes
	ArrayList<Integer>[] treeNodesForBipart;
	ArrayList<Integer>[] bipartsForTreeNode;
	
	// === indices/ints in these correspond to summed bipartitions ============
	TLongBipartition[] bipart;
	HashSet<Path> paths;

	// nestedChildren[i] contains the ids of all the biparts that are nested within bipart i
	// nestedParents[i] contains the ids of all the biparts that bipart i *is* a nested child of
	ArrayList<Integer>[] nestedChildren;
	ArrayList<Integer>[] nestedParents;
	boolean[] isNested; // indicates whether this bipartition is nested at all

	private HashMap<TLongBipartition, Node> nodeForBipart; // neo4j node for each bipartition
	private HashMap<Node,TLongBipartition> bipartForNode; //bipartiton for neo4j node

	int internalNodeCounter = 0;

	@SuppressWarnings("unchecked")
	public BipartOracle(List<JadeTree> trees, GraphDatabaseAgent gdb) throws Exception {
		
		this.gdb = gdb;

		Transaction tx = gdb.beginTx();
		gatherTreeData(trees); // populate 'treeNode', 'childrenOf', and 'original' arrays
		tx.success();
		tx.finish();
		
		// get pairwise sums of all tree biparts
		bipart = new BipartSetSum(original).toArray();
		
		for (int i = 0; i < bipart.length; i++) {
			System.out.println(i + " " + bipart[i].toString(nameForNodeId));
		}

		// find compatible child mappings among the summed biparts.
		// could be smarter about only looking for these when required during tree traversal
		nestedChildren = new ArrayList[bipart.length];
		nestedParents = new ArrayList[bipart.length];
		isNested = new boolean[bipart.length];
		for (int i = 0; i < bipart.length; i++) {

			// find all biparts nested within this one
			nestedChildren[i] = new ArrayList<Integer>();

			if (bipart[i].outgroup().size() < 1) { // don't check roots of input trees
				continue;
			}
			for (int j = 0; j < bipart.length; j++) {
				if (nestedParents[j] == null) {
					nestedParents[j] = new ArrayList<Integer>();
				}
				if (i != j && bipart[j].isNestedPartitionOf(bipart[i])) {
					nestedChildren[i].add(j);
					nestedParents[j].add(i);
					isNested[j] = true;
				}
			}
		}
		
//		System.out.println("nested parents: " + Arrays.toString(nestedParents));
//		System.out.println("nested children: " + Arrays.toString(nestedChildren));
//		System.exit(0);
		
		// map all tree nodes to all summed biparts
		treeNodesForBipart = new ArrayList[bipart.length];
		bipartsForTreeNode = new ArrayList[original.length];
		for (int i = 0; i < bipart.length; i++) {
			treeNodesForBipart[i] = new ArrayList<Integer>();
			for (int j = 0; j < treeNode.length; j++) {
				if (bipartsForTreeNode[j] == null) {
					bipartsForTreeNode[j] = new ArrayList<Integer>();
				}
				if (bipart[i].isCompatibleWith(original[j])) {
					treeNodesForBipart[i].add(j);
					bipartsForTreeNode[j].add(i);
				}
			}
		}
		
		nodeForBipart = new HashMap<TLongBipartition, Node>();
		bipartForNode = new HashMap<Node,TLongBipartition>();

		
		// now walk the bipart nestings and build the potential paths.
		// here we could just walk from any given bipart to the biparts mapped to that tree node.
		// this would reduce the size of the graph but not sure whether it would find all the relevant paths.
		paths = new HashSet<Path>();
		for (int i = 0; i < bipart.length; i++) {
			if (bipart[i].outgroup().size() > 0) {
				TLongBitArraySet retval = findPaths(i, new TLongBitArraySet(), new ArrayList<Integer>(), 0);
				//these that have no pairwise relationships also need to be made into nodes
				if (retval == null){
					tx = gdb.beginTx();
					Node node = gdb.createNode();
					node.setProperty(NodeProperty.MRCA.propertyName, bipart[i].ingroup().toArray());
					node.setProperty(NodeProperty.OUTMRCA.propertyName, bipart[i].outgroup().toArray());
					nodeForBipart.put(bipart[i], node);
					bipartForNode.put(node, bipart[i]);
					//System.out.println();
					tx.success();
					tx.finish();
				}
			}
		}
		
		for (Path p : paths) {
			System.out.println(p);
		}
		
		tx = gdb.beginTx();
//		try {
			for (Path p : paths) {
				generateNodesFromPaths(0, new TLongBitArraySet(), p.toArray());
			}
			tx.success();
			tx.finish();
//		} catch (Exception ex) {
//			tx.failure();
//			throw ex;
//		}// finally {
//			tx.finish();
//		}
			
		/*
		 * seems like the trees should be processed here to make the relationships to the tips
		 * and potentially other mrca relationships
		 * 
		 * I think a postorder traversal through each tree, a list of all the nodes at each jadenode
		 * only connect the parent to child that are possible
		 */
		for(JadeTree t: trees){
			//connect the internal nodes to the database nodes
			for(JadeNode in: t.internalNodes(NodeOrder.PREORDER)){
				if (in == t.getRoot()){
					TLongBitArraySet ingroup = new TLongBitArraySet();
					JadeBipartition b = t.getBipartition(in);
					for (JadeNode n : b.ingroup())  { ingroup.add(nodeIdForName.get(n.getName()));  }
					TLongBipartition tlb = new TLongBipartition(ingroup,new TLongBitArraySet());
					HashSet<Node> hs = new HashSet<Node>();
					for(TLongBipartition nfbtlb: nodeForBipart.keySet()){
						if(nfbtlb.isEquivalentTo(tlb)){
							hs.add(nodeForBipart.get(nfbtlb));
						}
					}
					/*
					 * probably should do this first, but should really be replaced with taxonomy anyway
					 */
					if (hs.size()==0){
						System.out.println("need to make a node for the root");
						System.out.println("\t"+tlb);
						tx = gdb.beginTx();
						Node node = gdb.createNode();
						node.setProperty(NodeProperty.MRCA.propertyName, tlb.ingroup().toArray());
						node.setProperty(NodeProperty.OUTMRCA.propertyName, tlb.outgroup().toArray());
						//System.out.println();
						hs.add(node);
						nodeForBipart.put(tlb, node);
						bipartForNode.put(node, tlb);
						tx.success();
						tx.finish();
					}
					in.assocObject("dbnodes",hs);
					//need to do a check here that says that if you don't find one, you need to make one. It will likely 
					//be replaced by taxonomy but not necessarily
					//steps: make the bipartition
				}else{
					//get the parent biparts
					HashSet<Node> parenths = (HashSet<Node>) in.getParent().getAssoc().get("dbnodes");
					System.out.println(parenths);
					
					//find the bipart nodes that match
					TLongBipartition tlb = original[(Integer) in.getAssoc().get("nodeId")];
					HashSet<Node> hs = new HashSet<Node>();
					for(TLongBipartition nfbtlb: nodeForBipart.keySet()){
						if(nfbtlb.isEquivalentTo(tlb)){
							for(Node phsn:parenths){
								if(nfbtlb.isNestedPartitionOf(bipartForNode.get(phsn))){
									hs.add(nodeForBipart.get(nfbtlb));
									System.out.println("would make relationship between "+nfbtlb+" "+bipartForNode.get(phsn));
									//make sure that there is the MRCACHILDOF
									boolean relpresent = false;
									for(Relationship rel: phsn.getRelationships(Direction.INCOMING, RelType.MRCACHILDOF)){
										if (rel.getStartNode() == nodeForBipart.get(nfbtlb)){
											relpresent = true;
											break;
										}
									}
									tx = gdb.beginTx();
									if(relpresent == false){
										nodeForBipart.get(nfbtlb).createRelationshipTo(phsn, RelType.MRCACHILDOF);
									}
									//make the STREECHILDOF
									nodeForBipart.get(nfbtlb).createRelationshipTo(phsn, RelType.STREECHILDOF);
									tx.success();
									tx.finish();
								}
							}
						}
					}
					//THERE has to be at least one node match unless the root where one may need to be added
					assert hs.size() > 0;
					in.assocObject("dbnodes", hs);
				}
			}
		}
		for(JadeTree t: trees){
			//connect the external nodes to the database nodes and make the STREECHILDOFs
			for(JadeNode ex: t.externalNodes()){
				HashSet<Node> hs = new HashSet<Node>();
				Node tnode = gdb.getNodeById(nodeIdForName.get(ex.getIdentifier()));
				HashSet<Node> parenths = (HashSet<Node>) ex.getParent().getAssoc().get("dbnodes");
				for(Node phsn: parenths){
					boolean relpresent = false;
					for(Relationship rel: phsn.getRelationships(Direction.INCOMING, RelType.MRCACHILDOF)){
						if (rel.getStartNode() == tnode){
							relpresent = true;
							break;
						}
					}
					tx = gdb.beginTx();
					if(relpresent == false){
						tnode.createRelationshipTo(phsn, RelType.MRCACHILDOF);
					}
					tnode.createRelationshipTo(phsn, RelType.STREECHILDOF);
					tx.success();
					tx.finish();
				}
			}
		}
		
	}
	
	private static String indent(int level) {
		StringBuffer s = new StringBuffer();
		for (int i = 0; i < level; i++) {
			s.append("\t");
		}
		return s.toString();
	}
	
	/** 
	 * Starting at each bipartition B, follow all paths to all bipartitions for which this B could be a nested child.
	 * At each level on the recursion down this tree, record the union I of all the ingroups of all bipartitions "above"
	 * this, i.e. those previously visited on the traversal. At each level on the recursion back up the tree, record
	 * the union O of all the ingroups of all bipartitions "below" this, i.e. those that have previously finished been
	 * finished before the return to this node. When a node is completed, define a bipartition X = I | O representing a node 
	 * to be created in the graph, children {X'1, X'2, ..., X'N} corresponding to the bipartitions defined in this way at
	 * each of this bipartition's N *completed* nested children. Sometimes the recursion stops without creating a node
	 * (see cases below).
	 * 
	 * @param parentId
	 * @param nested
	 * @param path
	 * @param level
	 * @return
	 */
	private TLongBitArraySet findPaths(int parentId, TLongBitArraySet cumulativeIngroup, ArrayList<Integer> path, int level) {

		TLongBipartition parent = bipart[parentId];
		
		// three cases where we exit without defining a node:
		if (hasNoNestedBiparts(parentId) || 					// found a dead end path
			cumulativeIngroup.containsAny(parent.outgroup()) || // conflict between potential node and its parent
			cumulativeIngroup.containsAll(parent.ingroup())) { 	// child that already contains entire parent
        	return null;
		}
		
		// otherwise prepare to define a new bipart
		path.add(parentId);
	    System.out.println("\n" + indent(level) + "current path is: " + path);

		// collect the ingroup from all downstream (child) biparts' ingroups'
		System.out.println(indent(level) + "on parent" + parentId + ": " + parent.toString(nameForNodeId));
		System.out.println(indent(level) + "incoming ingroup: " + cumulativeIngroup.toString(nameForNodeId));
		cumulativeIngroup.addAll(parent.ingroup());
		System.out.println(indent(level) + "cumulative ingroup is: " + cumulativeIngroup.toString(nameForNodeId));

		// collect the outgroup from all upstream (parent) biparts' outgroups
	    TLongBitArraySet cumulativeOutgroup = new TLongBitArraySet(parent.outgroup());
	    boolean newline = false;
	    for (int nextParentId : nestedParents[parentId]) {
			if (path.contains(nextParentId)) { continue; }
			TLongBitArraySet outgroupsToAdd = findPaths(nextParentId, new TLongBitArraySet(cumulativeIngroup), path, level+1);
			if (outgroupsToAdd != null) { // did not encounter a dead end path
		    	newline = true;
				cumulativeOutgroup.addAll(outgroupsToAdd);
			}
		}
		System.out.println((newline ? "\n" : "") + indent(level) + "cumulative outgroup is: " + cumulativeOutgroup.toString(nameForNodeId));
	    System.out.println(indent(level) + "done with parent " + parent.toString(nameForNodeId));
		System.out.println(indent(level) + "Will create node " + cumulativeIngroup.toString(nameForNodeId) + " | " + cumulativeOutgroup.toString(nameForNodeId) + " based on path " + path);

		assert ! cumulativeIngroup.containsAny(cumulativeOutgroup);

		paths.add(new Path(path));
		return cumulativeOutgroup;
	}
	
	
	private Object[] generateNodesFromPaths(int position, TLongBitArraySet cumulativeIngroup, int[] path) {

		int parentId = path[position];
		TLongBipartition parent = bipart[parentId];

		// three cases where we exit without making a node:
		if (hasNoNestedBiparts(parentId) || 					// found a dead end path
			cumulativeIngroup.containsAny(parent.outgroup()) || // conflict between potential node and its parent
			cumulativeIngroup.containsAll(parent.ingroup())) { 	// child that already contains entire parent
        	return null;
		}
		
		// otherwise prepare to define a new node
	    System.out.println("\n" + indent(position) + "path is: " + Arrays.toString(path) + " and current bipart id is: " + path[position]);

		// collect the ingroup from all downstream (child) biparts' ingroups'
		System.out.println(indent(position) + "on parent" + parentId + ": " + parent.toString(nameForNodeId));
		System.out.println(indent(position) + "incoming ingroup: " + cumulativeIngroup.toString(nameForNodeId));
		cumulativeIngroup.addAll(parent.ingroup());
		System.out.println(indent(position) + "cumulative ingroup is: " + cumulativeIngroup.toString(nameForNodeId));

	    // collect the outgroup this node
	    TLongBitArraySet cumulativeOutgroup = new TLongBitArraySet(parent.outgroup());

	    // perform the recursion down all the childen, returning each child node (once it has been created) along
	    // with its outgroups. using an Object[] as a return value to provide two object in the return is weird.
	    // Should update this to use a private class container for the return value or figure out something else.
	    Node parentNode = null;
	    boolean newline = false;
		if (position < path.length - 1) {
			Object[] result = generateNodesFromPaths(position+1, new TLongBitArraySet(cumulativeIngroup), path);
			if (result != null) {
				parentNode = (Node) result[0];
				TLongBitArraySet outgroupsToAdd = (TLongBitArraySet) result[1];
				cumulativeOutgroup.addAll(outgroupsToAdd);
				newline = true;
			}
		} else {
			
		}

		System.out.println((newline ? "\n" : "") + indent(position) + "cumulative outgroup is: " + cumulativeOutgroup.toString(nameForNodeId));
	    System.out.println(indent(position) + "done with parent " + parent.toString(nameForNodeId));
		System.out.println(indent(position) + "Will create node " + cumulativeIngroup.toString(nameForNodeId) + " | " + cumulativeOutgroup.toString(nameForNodeId) + " based on path " + Arrays.toString(path));

		assert ! cumulativeIngroup.containsAny(cumulativeOutgroup);

		// warning: mrca and outmrca must contain neo4j node ids, but for now just using the
		// temp ids from the ingroup/outgroup bipartitions. Need yet another lookup table. nodeForTipLabel or something like that.
		TLongBipartition b = new TLongBipartition(cumulativeIngroup, cumulativeOutgroup);
		Node node = null;
		if (nodeForBipart.containsKey(b)) {
			node = nodeForBipart.get(b);
		} else {
			node = gdb.createNode();
			node.setProperty(NodeProperty.MRCA.propertyName, cumulativeIngroup.toArray());
			node.setProperty(NodeProperty.OUTMRCA.propertyName, cumulativeOutgroup.toArray());
			nodeForBipart.put(b, node);
			bipartForNode.put(node, b);
			System.out.println();
		}
		//This won't make all the possible mrca child ofs. Not sure if we need that. Might need to 
		// A) make them all (all by all comparison) or B) use the trees to create additional ones
		// Preference for B if we can do it as obviously more efficient
		if (parentNode != null) {
			System.out.println("Creating relationship from " + node + " to " + parentNode);
			//this is trying not to replcate the relationships from each node, without it there are many duplicates created
			boolean testrelexist=false;
			for(Relationship trel: node.getRelationships(Direction.OUTGOING, RelType.MRCACHILDOF)){
				if(trel.getEndNode().equals(parentNode)){
					testrelexist = true;
					break;
				}
			}
			if (testrelexist == false)
				node.createRelationshipTo(parentNode, RelType.MRCACHILDOF);
		}
		/*
		 * this should probably be done just by loading the trees themselves
		 *
		if (position == 0) { // connect terminal node in path to ingroup tips
			for (Long tipId : parent.ingroup()) {
				//this is trying not to replicate the relationships from each node, without it there are many duplicates created
				boolean testrelexist=false;
				for(Relationship trel: gdb.getNodeById(tipId).getRelationships(Direction.OUTGOING, RelType.MRCACHILDOF)){
					if(trel.getEndNode().equals(node)){
						testrelexist = true;
						break;
					}
				}
				if (testrelexist == false)
					gdb.getNodeById(tipId).createRelationshipTo(node, RelType.MRCACHILDOF);
			}
		}
		*/
		
		return new Object[] {node, cumulativeOutgroup};
	}
	
	private boolean hasNoNestedBiparts(int parent) {
		return nestedChildren[parent].size() == 0;
	}
	
	private void gatherTreeData(List<JadeTree> trees) {
		int treeNodeCount = 0;
		//if we don't need the root, this is the right way to get the count
		for (JadeTree t : trees) { 
			treeNodeCount += t.internalNodeCount()-1;
			//System.out.println(t.getRoot().getNewick(false)+" "+(t.internalNodeCount()-1));

		}
		
		// make nodes for all unique tip names and remember them
		for (JadeTree t : trees) {
			for (JadeNode l : t.externalNodes()) {
				String n = l.getName();
				if (! nodeIdForName.containsKey(n)) {
					Node tip = gdb.createNode();
					tip.setProperty(NodeProperty.NAME.propertyName, n); // should store different info if we are using nexson
					nodeIdForName.put(n, tip.getId());
					nameForNodeId.put(tip.getId(), n);
				}
			}
		}
			
		// gather the tree nodes, tree structure, and tip labels.
		// for nexson, we would want ott ids instead of tip labels.
		// we should have treeNodeCount be the internal nodes - root
		treeNode = new JadeNode[treeNodeCount];
		childrenOf = new int[treeNodeCount][];
		original = new TLongBipartition[treeNodeCount];
		internalNodeCounter = -1;
		for (JadeTree t : trees) {
			//don't need to include root as far as I can tell, so you can do the children of the root
			for (JadeNode child: t.getRoot().getChildren()){
				if (child.isInternal())
					recurRecordTree(child, ++internalNodeCounter, t);
			}
		}
	}

	private void recurRecordTree(JadeNode p, int nodeId, JadeTree tree) {
		treeNode[nodeId] = p;

		// collect the internal children and send the tips for processing
		ArrayList<JadeNode> internalChildren = new ArrayList<JadeNode>();
		for (JadeNode child : p.getChildren()) {
			if (child.isInternal()) { 
				internalChildren.add(child); 
			}
		}
		
		// process the internal children
		childrenOf[nodeId] = new int[internalChildren.size()];
//		childrenOf[nodeId] = new int[p.getChildCount()];
		int k = 0;
		for (JadeNode child : internalChildren) {
//		for (JadeNode child : p.getChildren()) {
			childrenOf[nodeId][k++] = ++internalNodeCounter;
			recurRecordTree(child, internalNodeCounter, tree);
		}
		
		// record the bipartition
		TLongBitArraySet ingroup = new TLongBitArraySet();
		TLongBitArraySet outgroup = new TLongBitArraySet();
		JadeBipartition b = tree.getBipartition(p);
		for (JadeNode n : b.ingroup())  { ingroup.add(nodeIdForName.get(n.getName()));  }
		for (JadeNode n : b.outgroup()) { outgroup.add(nodeIdForName.get(n.getName())); }
		p.assocObject("nodeId", nodeId);
		original[nodeId] = new TLongBipartition(ingroup, outgroup);
	}

	private class Path {
		
		private final int[] path;
		public Path (int[] path) { this.path = path; }

		public Path (List<Integer> path) {
			this.path = new int[path.size()];
			for (int i = 0; i < path.size(); i++) {
				this.path[i] = path.get(i);
			}
		}

		@Override
		public boolean equals(Object other) {
			if (other == null || ! (other instanceof Path)) { return false; }

			int[] o = ((Path) other).path;
			if (path.length != o.length) { return false; }

			for (int i = 0; i < path.length; i++) {
				if (path[i] != o[i]) { return false; }
			}
			return true;
		}
		
		@Override 
		public int hashCode() {
			int h = 1;
			for (int i : path) { h = h * (i + 31) + i; }
			return h;
		}
		
		@Override
		public String toString() {
			return Arrays.toString(path);
		}
		
		public int[] toArray() {
			return path;
		}
	}

	private static void loadTreesCycleConflict(ArrayList<JadeTree> t) throws TreeParseException {
		t.add(TreeReader.readTree("((A,C),D);"));
		t.add(TreeReader.readTree("((A,D),B);"));
		t.add(TreeReader.readTree("((A,B),C);"));
	}

	private static void loadTreesTest4(ArrayList<JadeTree> t) throws TreeParseException {
		t.add(TreeReader.readTree("((((A,B),C),D),E);"));
		t.add(TreeReader.readTree("((((A,C),B),F),D);"));
		t.add(TreeReader.readTree("((A,F),C);"));
		t.add(TreeReader.readTree("((A,E),D);"));
	}

	public static void main(String[] args) throws Exception {

		ArrayList<JadeTree> t = new ArrayList<JadeTree>();

		loadTreesTest4(t);

		BipartOracle bi = new BipartOracle(t, new GraphDatabaseAgent(new EmbeddedGraphDatabase("test.db")));

		System.out.println("original bipartitions: ");
		for (int i = 0; i < bi.bipart.length; i++) {
			System.out.println(i + ": " + bi.bipart[i].toString(bi.nameForNodeId));
		}
		
		System.out.println("node for bipart:");
		
		for (TLongBipartition tlb: bi.nodeForBipart.keySet()){
			System.out.println(tlb+" "+bi.nodeForBipart.get(tlb));
		}
		
		System.out.println("paths through: ");
		for (Path p : bi.paths) {
			System.out.println(p);
		}
		
		//map the trees to connect the trees and create the streechildof and the roots
	}
}
