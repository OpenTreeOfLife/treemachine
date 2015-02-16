package org.opentree.tag.treeimport;

import jade.tree.TreeBipartition;
import jade.tree.TreeNode;
import jade.tree.NodeOrder;
import jade.tree.Tree;
import jade.tree.TreeParseException;
import jade.tree.TreeReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.opentree.bitarray.TLongBitArraySet;
import org.opentree.graphdb.GraphDatabaseAgent;

public class BipartOracle {

	private final GraphDatabaseAgent gdb;

	// these are just for associating input names with temporary ids. we should be using neo4j node ids.
	Map<Object, Long> nodeIdForName = new HashMap<Object, Long>();
	Map<Long, Object> nameForNodeId = new HashMap<Long, Object>();
	
	// indices in these correspond nodes in input trees
	Map<TreeNode, Integer> treeNodeIds;
	TreeNode[] treeNode;
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
	boolean[] isNested; // indicates whether this bipartition is nested at all // might not be used?

	// maps of graph nodes to various things
	private Map<TLongBipartition, Node> nodeForBipart; // neo4j node for each bipartition
	private Map<Node,TLongBipartition> bipartForNode; //bipartiton for neo4j node
	private Map<TreeNode, HashSet<Node>> graphNodesForTreeNode;

	int nodeId = 0;

	public BipartOracle(List<Tree> trees, GraphDatabaseAgent gdb) throws Exception {
		
		this.gdb = gdb;

		gatherTreeData(trees); // populate 'treeNode' and 'original' arrays with nodes and corresponding biparts
		
		bipart = new BipartSetSum(original).toArray(); // get pairwise sums of all tree biparts
		
		for (int i = 0; i < bipart.length; i++) {
			System.out.println(i + " " + bipart[i].toString(nameForNodeId));
		}

		createLicaNodesFromBiparts(); // creates nodes and MRCACHILDOF rels corresponding to nested bipartitions
		
		// now import the trees
		Transaction tx;
		graphNodesForTreeNode = new HashMap<TreeNode, HashSet<Node>>();
		for (Tree tree : trees) {
			
			// one transaction for the entire tree
			tx = gdb.beginTx();
			
			// first map the root to all relevant graph nodes
			graphNodesForTreeNode.put(tree.getRoot(), (HashSet<Node>) mapGraphNodesToRoot(tree));

			// now map the internal nodes other than the root
			for(TreeNode treeNode : tree.internalNodes(NodeOrder.PREORDER)){
				if (! treeNode.isTheRoot()){
					
					Set<Node> graphNodes = mapGraphNodesToInternalNode(treeNode);
					// THERE has to be at least one node match unless the root where one may need to be added
					assert graphNodes.size() > 0;
					graphNodesForTreeNode.put(treeNode, (HashSet<Node>) graphNodes);
				}
			}

			// now connect all the tips to their parent nodes
			for(TreeNode treeTip : tree.externalNodes()) {
				mapGraphNodesToTip(treeTip);
			}
			tx.success();
			tx.finish();
		}	
	}
	
	private Set<Node> mapGraphNodesToRoot(Tree tree) {
		TreeNode treeRoot = tree.getRoot();
		TLongBipartition rootBipart = getGraphBipartForTreeNode(treeRoot, tree);
		
		HashSet<Node> graphNodes = new HashSet<Node>();
		for (TLongBipartition b : nodeForBipart.keySet()) {
			if (b.containsAll(rootBipart)) {
				graphNodes.add(nodeForBipart.get(b));
			}
		}

		// should really be replaced with taxonomy anyway ... ?
		if (graphNodes.size() < 1) { // TODO: what if we don't map to a node in the graph?
			System.out.println("need to make a node for the root");
			System.out.println("\t"+rootBipart);
			Node graphRoot = createNode(rootBipart);
			graphNodes.add(graphRoot);
		}
		return graphNodes;
	}
	
	private Set<Node> mapGraphNodesToInternalNode(TreeNode treeNode) {

		// get the graph nodes that match this node's parent
		HashSet<Node> graphNodesForParent = graphNodesForTreeNode.get(treeNode.getParent());
		System.out.println(graphNodesForParent);
		
		// get the graph nodes that match this node
		TLongBipartition nodeBipart = original[treeNodeIds.get(treeNode)];

		HashSet<Node> graphNodes = new HashSet<Node>();
		for (TLongBipartition b: nodeForBipart.keySet()) {
			if (b.containsAll(nodeBipart)) {
				for (Node parent : graphNodesForParent) {
					if (b.isNestedPartitionOf(bipartForNode.get(parent))) {
						Node child = nodeForBipart.get(b);
						graphNodes.add(child);
						System.out.println("would make relationship between " + b + " " + bipartForNode.get(parent));
						updateMRCAChildOf(child, parent);
						child.createRelationshipTo(parent, RelType.STREECHILDOF);
					}
				}
			}
		}
		return graphNodes;
	}
	
	private void mapGraphNodesToTip(TreeNode treeTip) {
		Node tip = gdb.getNodeById(nodeIdForName.get(treeTip.getLabel()));
		for (Node parent : graphNodesForTreeNode.get(treeTip.getParent())){
			updateMRCAChildOf(tip, parent);
			tip.createRelationshipTo(parent, RelType.STREECHILDOF);
		}
	}
	
	private Node createNode(TLongBipartition b) {
		Node node = gdb.createNode();
		node.setProperty(NodeProperty.MRCA.propertyName, b.ingroup().toArray());
		node.setProperty(NodeProperty.OUTMRCA.propertyName, b.outgroup().toArray());
		nodeForBipart.put(b, node);
		bipartForNode.put(node, b);
		return node;
	}
	
	private static String indent(int level) {
		StringBuffer s = new StringBuffer();
		for (int i = 0; i < level; i++) {
			s.append("\t");
		}
		return s.toString();
	}
	
	@SuppressWarnings("unchecked")
	private void createLicaNodesFromBiparts() {
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
		
		nodeForBipart = new HashMap<TLongBipartition, Node>();
		bipartForNode = new HashMap<Node,TLongBipartition>();
		
		// now walk the bipart nestings and build the potential paths.
		// here we could just walk from any given bipart to the biparts mapped to that tree node.
		// this would reduce the size of the graph but not sure whether it would find all the relevant paths.
		paths = new HashSet<Path>();
		Transaction tx = gdb.beginTx();
		for (int i = 0; i < bipart.length; i++) {
			if (bipart[i].outgroup().size() > 0) {
				TLongBitArraySet pathResult = findPaths(i, new TLongBitArraySet(), new ArrayList<Integer>(), 0);
				if (pathResult == null) {
					// no paths found/saved for this bipart, so just make a node for it
					createNode(bipart[i]);
				}
			}
		}
		tx.success();
		tx.finish();

		// report results
		for (Path p : paths) {
			System.out.println(p);
		}
		
		// create nodes based on paths
		tx = gdb.beginTx();
		for (Path p : paths) {
			generateNodesFromPaths(0, new TLongBitArraySet(), p.toArray());
		}
		tx.success();
		tx.finish();
	}
	
	/** 
	 * Starting at each bipartition B, follow all paths to all bipartitions for which this B could be a nested child.
	 * At each level on the recursion down this tree, record the union I of all the ingroups of all bipartitions "above"
	 * this, i.e. those previously visited on the traversal. At each level on the recursion back up the tree, record
	 * the union O of all the ingroups of all bipartitions "below" this, i.e. those that have previously finished been
	 * finished before the return to this node. When a node is completed, define a bipartition X = I | O representing a node 
	 * to be created in the graph, with children {X'1, X'2, ..., X'N} corresponding to the bipartitions defined in this way at
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
	
	/**
	 * Process the paths generated by findPaths and create the nodes in the database that are defined by these paths. 
	 * @param position
	 * @param cumulativeIngroup
	 * @param path
	 * @return
	 */
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
		}

		System.out.println((newline ? "\n" : "") + indent(position) + "cumulative outgroup is: " + cumulativeOutgroup.toString(nameForNodeId));
	    System.out.println(indent(position) + "done with parent " + parent.toString(nameForNodeId));
		System.out.println(indent(position) + "Will create node " + cumulativeIngroup.toString(nameForNodeId) + " | " + cumulativeOutgroup.toString(nameForNodeId) + " based on path " + Arrays.toString(path));

		assert ! cumulativeIngroup.containsAny(cumulativeOutgroup);

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
		
		// This won't make all the possible mrca child ofs. Not sure if we need that. Might need to 
		// A) make them all (all by all comparison) or B) use the trees to create additional ones
		// Preference for B if we can do it as obviously more efficient
		if (parentNode != null) {
			System.out.println("Creating relationship from " + node + " to " + parentNode);
			updateMRCAChildOf(node, parentNode);
		}
		
		return new Object[] {node, cumulativeOutgroup};
	}
	
	private void updateMRCAChildOf(Node child, Node parent) {
		boolean alreadyExists = false;
		for(Relationship trel: child.getRelationships(Direction.OUTGOING, RelType.MRCACHILDOF)){
			if(trel.getEndNode().equals(parent)){
				alreadyExists = true;
				break;
			}
		}
		if (! alreadyExists) {
			child.createRelationshipTo(parent, RelType.MRCACHILDOF);
		}
	}
	
	private boolean hasNoNestedBiparts(int parent) {
		return nestedChildren[parent].size() == 0;
	}
	
	private void gatherTreeData(List<Tree> trees) {

		int treeNodeCount = 0;
		for (Tree t : trees) { 
			treeNodeCount += t.internalNodeCount() - 1; // don't count the root
		}
		
		Transaction tx = gdb.beginTx();
		
		// make nodes for all unique tip names and remember them
		for (Tree t : trees) {
			for (TreeNode l : t.externalNodes()) {
				Object n = l.getLabel();
				if (! nodeIdForName.containsKey(n)) {
					Node tip = gdb.createNode();
					
					// TODO here is where would store different info if we were using nexson
					tip.setProperty(NodeProperty.NAME.propertyName, n);
					
					nodeIdForName.put(n, tip.getId());
					nameForNodeId.put(tip.getId(), n);
				}
			}
		}
		tx.success();
		tx.finish();
			
		// gather the tree nodes, tree structure, and tip labels.
		// for nexson, we would want ott ids instead of tip labels.
		// we should have treeNodeCount be the internal nodes - root
		treeNode = new TreeNode[treeNodeCount];
		original = new TLongBipartition[treeNodeCount];
		treeNodeIds = new HashMap<TreeNode, Integer>();
		nodeId = 0;
		for (Tree tree : trees) {
			for (TreeNode node: tree.internalNodes(NodeOrder.PREORDER)){
				if (! node.isTheRoot()) { // we deal with the root later
					treeNode[nodeId] = node;
					treeNodeIds.put(node, nodeId);
					original[nodeId++] = getGraphBipartForTreeNode(node, tree);
				}
			}
		}
	}

	private TLongBipartition getGraphBipartForTreeNode(TreeNode p, Tree t) {
		TLongBitArraySet ingroup = new TLongBitArraySet();
		TLongBitArraySet outgroup = new TLongBitArraySet();
		TreeBipartition b = t.getBipartition(p);
		for (TreeNode n : b.ingroup())  { ingroup.add(nodeIdForName.get(n.getLabel()));  }
		for (TreeNode n : b.outgroup()) { outgroup.add(nodeIdForName.get(n.getLabel())); }
		return new TLongBipartition(ingroup, outgroup);
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

	private static void loadTreesCycleConflict(ArrayList<Tree> t) throws TreeParseException {
		t.add(TreeReader.readTree("((A,C),D);"));
		t.add(TreeReader.readTree("((A,D),B);"));
		t.add(TreeReader.readTree("((A,B),C);"));
	}

	private static void loadTreesTest4(ArrayList<Tree> t) throws TreeParseException {
		t.add(TreeReader.readTree("((((A,B),C),D),E);"));
		t.add(TreeReader.readTree("((((A,C),B),F),D);"));
		t.add(TreeReader.readTree("((A,F),C);"));
		t.add(TreeReader.readTree("((A,E),D);"));
	}
	
	private static void loadTreesTest5(ArrayList<Tree> t) throws TreeParseException {
		t.add(TreeReader.readTree("(((((A,B),C),D),E),F);"));
		t.add(TreeReader.readTree("(((A,G),H),I);"));
		t.add(TreeReader.readTree("(((B,D),Q,I);"));
	}

	public static void main(String[] args) throws Exception {

		ArrayList<Tree> t = new ArrayList<Tree>();

		loadTreesCycleConflict(t);

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
