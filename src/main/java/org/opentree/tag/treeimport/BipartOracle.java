package org.opentree.tag.treeimport;

import static java.util.stream.Collectors.toSet;
import jade.tree.TreeBipartition;
import jade.tree.TreeNode;
import jade.tree.NodeOrder;
import jade.tree.Tree;
import jade.tree.TreeParseException;
import jade.tree.TreeReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import opentree.GraphInitializer;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.opentree.bitarray.CompactLongSet;
import org.opentree.graphdb.GraphDatabaseAgent;

public class BipartOracle {

	private final GraphDatabaseAgent gdb;
	private final boolean USING_TAXONOMY;
	
	boolean VERBOSE = false;

	// associate input names with neo4j node ids
	Map<Object, Long> nodeIdForName = new HashMap<Object, Long>();
	Map<Long, Object> nameForNodeId = new HashMap<Long, Object>();
	
	// indices in these correspond to nodes in input trees
	Map<TreeNode, Integer> treeNodeIds = new HashMap<TreeNode, Integer>();
	TreeNode[] treeNode;
	TLongBipartition[] original;
	List<Collection<TLongBipartition>> bipartsByTree = new ArrayList<Collection<TLongBipartition>>();
	
	// indices/ints in these correspond to summed bipartitions
	TLongBipartition[] bipart;
	HashSet<Path> paths;
	
	// hashmap for the exploded tips. key is id for tip, value is hashset of ids that are exploded with this id
	HashMap<Object, HashSet<String>> explodedTipsHash;
	
	// nestedChildren[i] contains the ids of all the biparts that are nested within bipart i
	// nestedParents[i] contains the ids of all the biparts that bipart i *is* a nested child of
	ArrayList<Integer>[] nestedChildren;
	ArrayList<Integer>[] nestedParents;

	// maps of graph nodes to various things
	private Map<TLongBipartition, Node> nodeForBipart = new HashMap<TLongBipartition, Node>(); // neo4j node for bipartition
	private Map<Node, TLongBipartition> bipartForNode = new HashMap<Node, TLongBipartition>(); // bipartition for neo4j node
	private Map<TreeNode, HashSet<Node>> graphNodesForTreeNode; // all the graph nodes that have been mapped to the tree node
	
	// keep track of rels we've made to cut down on database queries
	private Map<Long, HashSet<Long>> hasMRCAChildOf = new HashMap<Long, HashSet<Long>>();
	
 	int nodeId = 0;

	/**
	 * instantiation runs the entire analysis
	 * @param trees
	 * @param gdb
	 * @param mapInternalNodesToTax
	 * @throws Exception
	 */
	public BipartOracle(List<Tree> trees, GraphDatabaseAgent gdb, boolean useTaxonomy) throws Exception {
		
		this.gdb = gdb;
		this.USING_TAXONOMY = useTaxonomy;

		// if the trees have tips whose labels are ott ids than can be mapped to taxon nodes in the database,
		// this will add the id to the hash so that it can be referenced for making the bipartitions but 
		// original ids can be kept for when mapping the trees at the end
		//creating the hashmap of the taxa with exploded ids
		explodedTipsHash = TipExploder.explodeTipsReturnHash(trees, gdb);
		//System.out.println(explodedTipsHash);
		
		gatherTreeData(trees); // populate class members: treeNode, original, treeNodeIds, bipartsByTree
		
		bipart = new BipartSetSum(bipartsByTree).toArray(); // get pairwise sums of all tree biparts
		
		// make the lica nodes in the graph for all the nested bipartitions
		// populate class members: nodeForBipart, bipartForNode
		createLicaNodesFromBiparts();

		// now process the trees
		graphNodesForTreeNode = new HashMap<TreeNode, HashSet<Node>>();
		mapTreeRootNodes(trees); // creates new nodes if necessary
		generateMRCAChildOfs();  // connect all nodes, must be done *after* all nodes are created!
		mapNonRootNodes(trees);	 // use the mrca rels to map the trees into the graph
		
		// setting this for use eventually in the mapInternalNodes 
//		if (mapInternalNodesToTax) { mapInternalNodesToTaxonomy(trees); }
		if (USING_TAXONOMY) { mapInternalNodesToTaxonomy(trees); }
	}
	
	/**
	 * the procedure here is to examine each node mapped to each tree and find
	 * the taxonomy nodes that match those
	 * then create relationships (streechild of ) from the children and parents
	 */
	public void mapInternalNodesToTaxonomy(List<Tree> trees){
		Transaction tx = gdb.beginTx();
		for(Tree t: trees){
			for(TreeNode tn: t.internalNodes(NodeOrder.POSTORDER)){
				//get the original tlongbipatition
				TLongBipartition tnbp;
				if(tn != t.getRoot()){
					tnbp = original[treeNodeIds.get(tn)];
				}else{
					tnbp = getGraphBipartForTreeNode(tn, t);
				}
				//only need to start from one leaf
				TreeNode sample_leaf = tn.getDescendantLeaves().iterator().next();
				Node tip = gdb.getNodeById(nodeIdForName.get(sample_leaf.getLabel()));
				Node curnode = tip;
				while (curnode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)){
					curnode = curnode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
					CompactLongSet tlb = new CompactLongSet((long[])curnode.getProperty("mrca"));
					if(tlb.containsAll(tnbp.ingroup())==true){
						if(tlb.containsAny(tnbp.outgroup())==false){
							//go through the relationships connecting each of the nodes at this node to the parents 
							// and children. when the taxonomy is fine with this, make a relationship
							System.out.println(tn+" matches "+curnode);
							System.out.println("\twill connect " );
							HashSet<Node> childnds = new HashSet<Node>();
							HashSet<Node> parnds = new HashSet<Node>();
							for(TreeNode tcn : tn.getChildren()){
								//System.out.println("tcn: "+tcn);
								HashSet<Node> pgn = graphNodesForTreeNode.get(tcn);
								if(pgn == null){//it is a tip
									Node pn = gdb.getNodeById(nodeIdForName.get(tcn.getLabel()));
									CompactLongSet ctlb = new CompactLongSet((long[])pn.getProperty("mrca"));
									if(tlb.containsAll(ctlb)){
										childnds.add(pn);
										System.out.println("\t"+pn+" -> "+curnode);
									}
								}else{
									for(Node pn : pgn){
										CompactLongSet ctlb = new CompactLongSet((long[])pn.getProperty("mrca"));
										if(tlb.containsAll(ctlb)==false){
											continue;
										}
										childnds.add(pn);
										System.out.println("\t"+pn+" -> "+curnode);
									}
								}
							}
							System.out.println(childnds);
							if(childnds.size() > 0){
								graphNodesForTreeNode.get(tn).add(curnode);
								for(Node cn: childnds){
									cn.createRelationshipTo(curnode, RelType.STREECHILDOF);
								}
							}
							if(tn != t.getRoot()){
								HashSet<Node> pgn = graphNodesForTreeNode.get(tn.getParent());
								for(Node pn : pgn){
									if(tlb.containsAll(bipartForNode.get(pn).ingroup())==true || 
											tlb.containsAny(bipartForNode.get(pn).outgroup())==true){
										continue;
									}
									parnds.add(pn);
									System.out.println("\t"+curnode+" -> "+pn);
								}
								if(parnds.size() > 0){
									for(Node cn: parnds){
										curnode.createRelationshipTo(cn, RelType.STREECHILDOF);
									}
								}
							}
							//set the node to the object in the tree
							break;
						}
					}
				}	
			}
		}
		tx.success();
		tx.finish();
	}

	private void gatherTreeData(List<Tree> trees) {

		int treeNodeCount = 0;
		for (Tree t : trees) { 
			treeNodeCount += t.internalNodeCount() - 1; // don't count the root
		}
		
		Transaction tx = gdb.beginTx();
		
		// make nodes for all unique tip names and remember them
		//we are either making new nodes or we are matching those already in the database
		boolean makingnewnodes = false;
		boolean matchingids = false;
		Index<Node> ottIdIndex = gdb.getNodeIndex("graphTaxUIDNodes", "type", "exact", "to_lower_case", "true");
		for (Tree t : trees) {
			for (TreeNode l : t.externalNodes()) {
				for(String lab: explodedTipsHash.get(l)){
					if (! nodeIdForName.containsKey(lab)) {
						Node tip = null;
						try {
							tip = ottIdIndex.get(NodeProperty.TAX_UID.propertyName, lab).getSingle();
							if(tip != null){
								matchingids = true;
								if(makingnewnodes == true){
									System.err.println("you were making nodeIds in the database and this one matched: "+lab);
									System.exit(1);
								}
							}
						} catch (NoSuchElementException ex) {}
						if(tip == null){
							makingnewnodes = true;
							if (matchingids == true){
								System.err.println("you were matching nodeIds in the database and this one didn't match: "+lab);
								System.exit(1);
							}
							tip = gdb.createNode();
							tip.setProperty(NodeProperty.NAME.propertyName, lab);
						}
						nodeIdForName.put(lab, tip.getId());
						nameForNodeId.put(tip.getId(), lab);
					}
				}
				//doing this for the actual tip name as well if it isn't already in the database
				//might be the case if there are exploded tips
				Object n = l.getLabel();
				if (! nodeIdForName.containsKey(n)) {
					Node tip = null;
					try {
						tip = ottIdIndex.get(NodeProperty.TAX_UID.propertyName, n).getSingle();
						if(tip != null){
							matchingids = true;
							if(makingnewnodes == true){
								System.err.println("you were making nodeIds in the database and this one matched: "+n);
								System.exit(1);
							}
						}
					} catch (NoSuchElementException ex) {}
					if(tip == null){
						makingnewnodes = true;
						if (matchingids == true){
							System.err.println("you were matching nodeIds in the database and this one didn't match: "+n);
							System.exit(1);
						}
						tip = gdb.createNode();
						tip.setProperty(NodeProperty.NAME.propertyName, n);
					}
					nodeIdForName.put(n, tip.getId());
					nameForNodeId.put(tip.getId(), n);
				}
				/*
				if (! nodeIdForName.containsKey(n)) {
					Node tip = gdb.createNode();
					tip.setProperty(NodeProperty.NAME.propertyName, n);
					nodeIdForName.put(n, tip.getId());
					nameForNodeId.put(tip.getId(), n);
				}
				*/
			}
		}
		tx.success();
		tx.finish();
			
		// gather the tree nodes, tree structure, and tip labels.
		// for nexson, we would want ott ids instead of tip labels.
		treeNode = new TreeNode[treeNodeCount];
		original = new TLongBipartition[treeNodeCount];
		nodeId = 0;
		for (Tree tree : trees) {
			Collection<TLongBipartition> treeBiparts = new ArrayList<TLongBipartition>();
			for (TreeNode node: tree.internalNodes(NodeOrder.PREORDER)) {
				if (! node.isTheRoot()) { // we deal with the root later
					treeNode[nodeId] = node;
					treeNodeIds.put(node, nodeId);
					TLongBipartition b = getGraphBipartForTreeNode(node, tree);
					original[nodeId++] = b;
					treeBiparts.add(b);
				}
			}
			bipartsByTree.add(treeBiparts);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void createLicaNodesFromBiparts() {
		
		// first do a pairwise all-by-all comparison to find all nested bipartitions
		nestedChildren = new ArrayList[bipart.length];
		nestedParents = new ArrayList[bipart.length];
		for (int i = 0; i < bipart.length; i++) {

			nestedChildren[i] = new ArrayList<Integer>();

			if (bipart[i].outgroup().size() < 1) { // don't check roots of input trees
				continue;
			}
			for (int j = 0; j < bipart.length; j++) {
				if (nestedParents[j] == null) {
					nestedParents[j] = new ArrayList<Integer>();
				}
				if (i != j && bipart[j].isNestedPartitionOf(bipart[i])) {
					// found a nested bipart
					nestedChildren[i].add(j);
					nestedParents[j].add(i);
				}
			}
		}
				
		// now walk through the bipart nestings and build the potential paths.
		// here we considered just walking from any given bipart to the biparts mapped to that tree node, but
		// there should be cases where this would not find all the relevant paths, so we do all of them.
		paths = new HashSet<Path>();
		Transaction tx = gdb.beginTx();
		for (int i = 0; i < bipart.length; i++) {
			if (bipart[i].outgroup().size() > 0) {
				CompactLongSet pathResult = findPaths(i, new CompactLongSet(), new ArrayList<Integer>(), 0);
				if (pathResult == null) {
					// make nodes now for any biparts that are not nested in any others.
					// will need them them for mapping trees
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
			generateNodesFromPaths(0, new CompactLongSet(), p.toArray());
		}
		tx.success();
		tx.finish();
	}

	/**
	 * This creates all the MRCACHILDOF rels among all graph nodes. It uses an all by all pairwise
	 * comparison, which has quadratic order of growth. We do this because it makes tree loading trivial:
	 * after mapping a tree node x just to graph node(s) g(x), just look at all the graph nodes connected
	 * to g(x) by MRCACHILDOF rels to find all the graph nodes that may possibly be mapped to the
	 * parent/child nodes of x in the tree.
	 */
	private void generateMRCAChildOfs(){

		// gather information about which rels to create. this is the full n^2 comparisons among all nodes (i.e. biparts)
		Map<TLongBipartition, Set<TLongBipartition>> nestedChildren = new HashMap<TLongBipartition, Set<TLongBipartition>>();
		for (TLongBipartition parent: nodeForBipart.keySet()) {

			Set<TLongBipartition> children = nodeForBipart.keySet().parallelStream()
				.map( child -> { // map all biparts that can be nested within this parent
					if (child.isNestedPartitionOf(parent) && (! child.equals(parent))) { return child; }
					else { return null; }})
				.collect(() -> new HashSet<TLongBipartition>(), (a,b) -> a.add(b), (a,b) -> a.addAll(b)).stream()
				.filter(r -> r != null).collect(toSet()); // filter nulls out of results

			nestedChildren.put(parent, children);
		}
		
		// now create the rels. not trying to do this in parallel because concurrent db ops seem unwise. but we could try.
		Transaction tx = gdb.beginTx();
		for (TLongBipartition parent : nestedChildren.keySet()) {
			for (TLongBipartition child : nestedChildren.get(parent)) {
				updateMRCAChildOf(nodeForBipart.get(child), nodeForBipart.get(parent));
			}
		}
		tx.success();
		tx.finish();
		
		
		/* old code left here for convenience in case the parallel stream borks
		 * 
		Transaction tx = gdb.beginTx();
		for (TLongBipartition parent: nodeForBipart.keySet()) {
			for (TLongBipartition nestedChild: nodeForBipart.keySet()) { // potential nested
				if (nestedChild.isNestedPartitionOf(parent) && (! nestedChild.equals(parent))) {
					updateMRCAChildOf(nodeForBipart.get(nestedChild),nodeForBipart.get(parent));
				}
			}
		tx.success();
		tx.finish();
		} */

	}
	
	/**
	 * Map all the tree root nodes into the graph. Must be done before loading trees.
	 */
	private void mapTreeRootNodes(List<Tree> trees) {
		for (Tree tree : trees) {
			Transaction tx = gdb.beginTx();

			TreeNode root = tree.getRoot();
			TLongBipartition rootBipart = getGraphBipartForTreeNode(root, tree);
	
			HashSet<Node> graphNodes = new HashSet<Node>();
			for(TLongBipartition b : nodeForBipart.keySet()){
				if(b.containsAll(rootBipart)){
					graphNodes.add(nodeForBipart.get(b));
				}
			}
		
			if (graphNodes.size() < 1) {
				System.out.println("could not find a suitable node to map to the root, will make a new one");
				System.out.println("\t"+rootBipart);
				graphNodes.add(createNode(rootBipart));
			}

			tx.success();
			tx.finish();
	
			graphNodesForTreeNode.put(root, graphNodes);
		}
	}

	private void mapNonRootNodes(List<Tree> trees) {
		for (Tree tree : trees) {
			
			// one transaction for the entire tree
			Transaction tx = gdb.beginTx();
			
			// now map the internal nodes other than the root
			for (TreeNode treeNode : tree.internalNodes(NodeOrder.PREORDER)) {
				if (! treeNode.isTheRoot()) {
					
					Set<Node> graphNodes = mapGraphNodesToInternalNode(treeNode);
					// THERE has to be at least one node match unless the root where one may need to be added
					if (graphNodes.size() == 0) {
						System.out.println("graphNodes: " + graphNodes + " " + treeNode.getNewick(false));
						throw new AssertionError();
					}
					graphNodesForTreeNode.put(treeNode, (HashSet<Node>) graphNodes);
				}
			}

			// now connect all the tips to their parent nodes
			for (TreeNode treeTip : tree.externalNodes()) {
				Node tip = gdb.getNodeById(nodeIdForName.get(treeTip.getLabel()));
				for (Node parent : graphNodesForTreeNode.get(treeTip.getParent())){
					updateMRCAChildOf(tip, parent);
					tip.createRelationshipTo(parent, RelType.STREECHILDOF);
				}
			}

			// TODO add tree metadata node
			
			tx.success();
			tx.finish();
		}	
	}
	
	private Set<Node> mapGraphNodesToInternalNode(TreeNode treeNode) {

		// get the graph nodes that match this node's parent
		HashSet<Node> graphNodesForParent = graphNodesForTreeNode.get(treeNode.getParent());
		
		// get the graph nodes that match this node
		TLongBipartition nodeBipart = original[treeNodeIds.get(treeNode)];
		HashSet<Node> graphNodes = new HashSet<Node>();
		
		// if you create the mrcachildofs before, then you can do this
		for (Node parent : graphNodesForParent){
			for (Relationship r: parent.getRelationships(Direction.INCOMING, RelType.MRCACHILDOF)){
				Node potentialChild = r.getStartNode();
				TLongBipartition childBipart = bipartForNode.get(potentialChild);
				// BE CAREFUL containsAll is directional
				if (childBipart != null && childBipart.containsAll(nodeBipart) 
						&& childBipart.isNestedPartitionOf(bipartForNode.get(parent))){
					graphNodes.add(potentialChild);
					potentialChild.createRelationshipTo(parent, RelType.STREECHILDOF);
				}	
			}
		}

		/*
		// if you don't create the mrca child ofs earlier then you need to do this.
		// it's slower than making all the pairwise mrcachildofs earlier
		for (TLongBipartition b: nodeForBipart.keySet()){
			if (b.containsAll(nodeBipart)) {
				for (Node parentNode : graphNodesForParent){
					if (b.isNestedPartitionOf(bipartForNode.get(parentNode))){
						graphNodes.add(nodeForBipart.get(b));
						System.out.println("would make relationship between " + b + " "+bipartForNode.get(parentNode));
						updateMRCAChildOf(nodeForBipart.get(b), parentNode);
						nodeForBipart.get(b).createRelationshipTo(parentNode, RelType.STREECHILDOF);
					}
				}
			}
		} */
		
		return graphNodes;
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
	 * the union O of all the ingroups of all bipartitions "below" this, i.e. those that have previously been finished
	 * before the return to this node. When a node is completed, define a bipartition X = I | O representing a node 
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
	private CompactLongSet findPaths(int parentId, CompactLongSet cumulativeIngroup, ArrayList<Integer> path, int level) {

		TLongBipartition parent = bipart[parentId];
		
		// three cases where we exit without defining a node:
		if (hasNoNestedBiparts(parentId) || 					// found a dead end path
			cumulativeIngroup.containsAny(parent.outgroup()) || // conflict between potential node and its parent
			cumulativeIngroup.containsAll(parent.ingroup())) { 	// child that already contains entire parent
        	return null;
		}
		
		// otherwise prepare to define a new bipart
		path.add(parentId);

		if (VERBOSE) {
			System.out.println("\n" + indent(level) + "current path is: " + path);
			System.out.println(indent(level) + "on parent" + parentId + ": " + parent.toString(nameForNodeId));
			System.out.println(indent(level) + "incoming ingroup: " + cumulativeIngroup.toString(nameForNodeId));
		}

		// collect the ingroup from all downstream (child) biparts' ingroups'
		cumulativeIngroup.addAll(parent.ingroup());

		if (VERBOSE) {
			System.out.println(indent(level) + "cumulative ingroup is: " + cumulativeIngroup.toString(nameForNodeId));
		}

		// collect the outgroup from all upstream (parent) biparts' outgroups
		CompactLongSet cumulativeOutgroup = new CompactLongSet(parent.outgroup());
	    boolean newline = false;
	    for (int nextParentId : nestedParents[parentId]) {
			if (path.contains(nextParentId)) { continue; }
			CompactLongSet outgroupsToAdd = findPaths(nextParentId, new CompactLongSet(cumulativeIngroup), path, level+1);
			if (outgroupsToAdd != null) { // did not encounter a dead end path
		    	newline = true;
				cumulativeOutgroup.addAll(outgroupsToAdd);
			}
		}
	    
	    if (VERBOSE) {
			System.out.println((newline ? "\n" : "") + indent(level) + "cumulative outgroup is: " + cumulativeOutgroup.toString(nameForNodeId));
		    System.out.println(indent(level) + "done with parent " + parent.toString(nameForNodeId));
			System.out.println(indent(level) + "Will create node " + cumulativeIngroup.toString(nameForNodeId) + " | " + cumulativeOutgroup.toString(nameForNodeId) + " based on path " + path);
	    }
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
	private Object[] generateNodesFromPaths(int position, CompactLongSet cumulativeIngroup, int[] path) {

		int parentId = path[position];
		TLongBipartition parent = bipart[parentId];

		// three cases where we exit without making a node:
		if (hasNoNestedBiparts(parentId) || 					// found a dead end path
			cumulativeIngroup.containsAny(parent.outgroup()) || // conflict between potential node and its parent
			cumulativeIngroup.containsAll(parent.ingroup())) { 	// child that already contains entire parent
        	return null;
		}

		// otherwise prepare to define a new node

		if (VERBOSE) {
			System.out.println("\n" + indent(position) + "path is: " + Arrays.toString(path) + " and current bipart id is: " + path[position]);
			System.out.println(indent(position) + "on parent" + parentId + ": " + parent.toString(nameForNodeId));
			System.out.println(indent(position) + "incoming ingroup: " + cumulativeIngroup.toString(nameForNodeId));
		}

		// collect the ingroup from all downstream (child) biparts' ingroups'
		cumulativeIngroup.addAll(parent.ingroup());

		if (VERBOSE) {
			System.out.println(indent(position) + "cumulative ingroup is: " + cumulativeIngroup.toString(nameForNodeId));
		}

	    // collect the outgroup this node
		CompactLongSet cumulativeOutgroup = new CompactLongSet(parent.outgroup());

	    // perform the recursion down all the childen, returning each child node (once it has been created) along
	    // with its outgroups. using an Object[] as a return value to provide two object in the return is weird.
	    // Should update this to use a private class container for the return value or figure out something else.
//	    Node parentNode = null;
	    boolean newline = false;
		if (position < path.length - 1) {
			Object[] result = generateNodesFromPaths(position+1, new CompactLongSet(cumulativeIngroup), path);
			if (result != null) {
//				parentNode = (Node) result[0];
				CompactLongSet outgroupsToAdd = (CompactLongSet) result[1];
				cumulativeOutgroup.addAll(outgroupsToAdd);
				newline = true;
			}
		}

		if (VERBOSE) {
			System.out.println((newline ? "\n" : "") + indent(position) + "cumulative outgroup is: " + cumulativeOutgroup.toString(nameForNodeId));
		    System.out.println(indent(position) + "done with parent " + parent.toString(nameForNodeId));
			System.out.println(indent(position) + "Will create node " + cumulativeIngroup.toString(nameForNodeId) + " | " + cumulativeOutgroup.toString(nameForNodeId) + " based on path " + Arrays.toString(path));
		}
		
		assert ! cumulativeIngroup.containsAny(cumulativeOutgroup);

		TLongBipartition b = new TLongBipartition(cumulativeIngroup, cumulativeOutgroup);
		Node node = null;
		if (nodeForBipart.containsKey(b)) {
			node = nodeForBipart.get(b);
		} else {
			node = createNode(new TLongBipartition(cumulativeIngroup, cumulativeOutgroup));
			if (VERBOSE) { System.out.println(); }
		}

		/*
		//This won't make all the possible mrca child ofs. We need more than what is created here. 
		//I would suggest pulling this part out and creating them above in an all by all comparison
		//The you can use the tree structure to lower the number of things you compare to when 
		//mapping the trees. 
		//Going to leave this here but doing the all by all now
 		if (parentNode != null) {
 			System.out.println("Creating relationship from " + node + " to " + parentNode);
 			updateMRCAChildOf(node, parentNode);
 		} */
 		
		return new Object[] { node, cumulativeOutgroup };
	}
	
	private Node createNode(TLongBipartition b) {
		Node node = gdb.createNode();
		System.out.println(node);
		node.setProperty(NodeProperty.MRCA.propertyName, b.ingroup().toArray());
		node.setProperty(NodeProperty.OUTMRCA.propertyName, b.outgroup().toArray());
		nodeForBipart.put(b, node);
		bipartForNode.put(node, b);
		return node;
	}

	/**
	 * This will check to see if an MRCACHILDOF rel already exists between the child and the
	 * parent, and if not it will make one.
	 * @param child
	 * @param parent
	 */
	private void updateMRCAChildOf(Node child, Node parent) {
		if (hasMRCAChildOf.get(child.getId()) == null) {
			hasMRCAChildOf.put(child.getId(), new HashSet<Long>());
		} else {
			if (hasMRCAChildOf.get(child.getId()).contains(parent.getId())) {
				return;
			}
		}
		boolean alreadyExists = false;
		for (Relationship r : child.getRelationships(Direction.OUTGOING, RelType.MRCACHILDOF)){
			if (r.getEndNode().equals(parent)){
				alreadyExists = true;
				break;
			}
		}
		if (! alreadyExists) {
			child.createRelationshipTo(parent, RelType.MRCACHILDOF);
		}
		hasMRCAChildOf.get(child.getId()).add(parent.getId());
	}
	
	private boolean hasNoNestedBiparts(int parent) {
		return nestedChildren[parent].size() == 0;
	}

	private TLongBipartition getGraphBipartForTreeNode(TreeNode p, Tree t) {
		CompactLongSet ingroup = new CompactLongSet();
		CompactLongSet outgroup = new CompactLongSet();
		TreeBipartition b = t.getBipartition(p);
		/*
		 * this is using the explodedTipsHash now
		 */
		//for (TreeNode n : b.ingroup())  { ingroup.add(nodeIdForName.get(n.getLabel()));  }
		//for (TreeNode n : b.outgroup()) { outgroup.add(nodeIdForName.get(n.getLabel())); }
		for (TreeNode n : b.ingroup())  { 
			for(String s: explodedTipsHash.get(n)){
				ingroup.add(nodeIdForName.get(s));  
			}
		}
		for (TreeNode n : b.outgroup()) {
			for(String s: explodedTipsHash.get(n)){
				outgroup.add(nodeIdForName.get(s));  
			}
		}
		return new TLongBipartition(ingroup, outgroup);
	}
	
	private class Path {
		
		private final int[] path;
//		public Path (int[] path) { this.path = path; }

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

	public static void main(String[] args) throws Exception {
		
		String dbname = "test.db";

		// these are tests for order
/*		runSimpleTest(cycleConflictTrees(), dbname);
		runSimpleTest(nonOverlapTrees(), dbname);
		runSimpleTest(test4Trees(), dbname);
		runSimpleTest(test3Trees(), dbname);
		runSimpleTest(testInterleavedTrees(), dbname); */

		// these are tests for taxonomy
		dbname = "tax.db";
//		loadTaxonomyAndTreesTest();
	}
	
	/**
	 * be careful, this one takes a while
	 */
	@SuppressWarnings("unused")
	private static void loadATOLTrees(List<Tree> t) throws TreeParseException {
		try {
			// TODO is this file in the treemachine github repo? i didn't see it... 
			BufferedReader br = new BufferedReader(new FileReader("data/examples/atol/RAxML_bootstrap.ONLY_CP_BS100.rr"));
			String str;
			while((str = br.readLine())!=null){
				t.add(TreeReader.readTree(str));
			}
			br.close();
			System.out.println("trees read");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@SuppressWarnings("unused")
	private static void loadTaxonomyAndTreesTest() throws Exception {
		String dbname = "test.db";
		String version = "1";
		
		FileUtils.deleteDirectory(new File(dbname));
		
		// TODO need to add these files to the github repo
		String taxonomy = "test-taxonomy-bipart/Dipsacales.tax";
		String synonyms = "";
		
		GraphInitializer tl = new GraphInitializer(dbname);
		tl.addInitialTaxonomyTableIntoGraph(taxonomy, synonyms, version);
		tl.shutdownDB();

		List <Tree> t = new ArrayList<Tree>();
		

		BufferedReader br = new BufferedReader(new FileReader("test-taxonomy-bipart/tree1.tre"));
		String str = br.readLine();
		t.add(TreeReader.readTree(str));
		br.close();
		br = new BufferedReader(new FileReader("test-taxonomy-bipart/tree2.tre"));
		str = br.readLine();
		t.add(TreeReader.readTree(str));
		br.close();
		br = new BufferedReader(new FileReader("test-taxonomy-bipart/tree3.tre"));
		str = br.readLine();
		t.add(TreeReader.readTree(str));
		br.close();

		GraphDatabaseAgent gdb = new GraphDatabaseAgent(dbname);

		BipartOracle bi = new BipartOracle(t, gdb, true);

	}
	
	@SuppressWarnings("unused")
	private static void runSimpleTest(List<Tree> t, String dbname) throws Exception {

		FileUtils.deleteDirectory(new File(dbname));
		BipartOracle bi = new BipartOracle(t, new GraphDatabaseAgent(new EmbeddedGraphDatabase(dbname)),false);

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
		bi.gdb.shutdownDb();
	}
	
	@SuppressWarnings("unused")
	private static void runSimpleOTTTest(List<Tree> t, String dbname) throws Exception {

		BipartOracle bi = new BipartOracle(t, new GraphDatabaseAgent(new EmbeddedGraphDatabase(dbname)),true);

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
		bi.gdb.shutdownDb();
	}
	
	@SuppressWarnings("unused")
	private static List<Tree> cycleConflictTrees() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("((A,C),D);"));
		t.add(TreeReader.readTree("((A,D),B);"));
		t.add(TreeReader.readTree("((A,B),C);"));
		return t;
	}

	@SuppressWarnings("unused")
	private static List<Tree> nonOverlapTrees() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("((A,C),D);"));
		t.add(TreeReader.readTree("((E,F),G);"));
		return t;
	}
	
	@SuppressWarnings("unused")
	private static List<Tree> test4Trees() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("((((A,B),C),D),E);"));
		t.add(TreeReader.readTree("((((A,C),B),F),D);"));
		t.add(TreeReader.readTree("((A,F),C);"));
		t.add(TreeReader.readTree("((A,E),D);"));
		return t;
	}
	
	@SuppressWarnings("unused")
	private static List<Tree> test3Trees() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("(((((A,B),C),D),E),F);"));
		t.add(TreeReader.readTree("(((A,G),H),I);"));
		t.add(TreeReader.readTree("(((B,D),Q,I);"));
		return t;
	}
	
	@SuppressWarnings("unused")
	private static List<Tree> testInterleavedTrees() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("(((((A,C),E),G),I),K);"));
		t.add(TreeReader.readTree("((((A,B),C),D),E);"));
		t.add(TreeReader.readTree("((((D,G),H),I),J);"));
		t.add(TreeReader.readTree("((I,J),K);"));
		return t;
	}
}
