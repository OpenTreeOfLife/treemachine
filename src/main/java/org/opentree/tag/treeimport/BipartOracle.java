package org.opentree.tag.treeimport;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toList;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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

//	private final List<Tree> trees;
	private final GraphDatabaseAgent gdb;
	private final boolean USING_TAXONOMY;
	
	boolean VERBOSE = false;

	// associate input labels with neo4j node ids
	Map<Object, Long> nodeIdForLabel = new HashMap<Object, Long>();
	Map<Long, Object> labelForNodeId = new HashMap<Long, Object>();
	
	// indices in these correspond to nodes in input trees
	Map<TreeNode, Integer> treeNodeIds = new HashMap<TreeNode, Integer>();
	TreeNode[] treeNode;
	TLongBipartition[] original;
	List<Collection<TLongBipartition>> bipartsByTree = new ArrayList<Collection<TLongBipartition>>();
	List<Integer> summedBipartIds = new ArrayList<Integer>(); // just the indices for the summed biparts so we can access them easily
	
	// indices/ints in these correspond to summed bipartitions
	TLongBipartition[] bipart;
	HashSet<Path> paths;
	
	// key is id for tip, value is hashset of ids that are exploded with this id
	Map<Object, Collection<Object>> explodedTipsHash;
	Map<Object, Collection<Object>> explodedTipsHashReduced;
	Map<Object, Collection<Object>> shrunkSet;//this will have the key as the id used in the shrunk set and the collection as the full set
	
	// nestedChildren[i] contains the ids of all the biparts that are nested within bipart i
	// nestedParents[i] contains the ids of all the biparts that bipart i *is* a nested child of
	List<Integer>[] nestedChildren;
	List<Integer>[] nestedParents;
	List<Integer>[] nestedAugmentingParents;
//	List<Integer>[] nestedAugmentingChildren;

	// maps of graph nodes to various things
	private Map<TLongBipartition, Node> nodeForBipart = new HashMap<TLongBipartition, Node>(); // neo4j node for bipartition
	private Map<Node, TLongBipartition> bipartForNode = new HashMap<Node, TLongBipartition>(); // bipartition for neo4j node
	// all the graph nodes that have been mapped to the tree node
	private Map<TreeNode, HashSet<Node>> graphNodesForTreeNode = new HashMap<TreeNode, HashSet<Node>>();
	
	// keep track of rels we've made to cut down on database queries
	private Map<Long, HashSet<Long>> hasMRCAChildOf = new HashMap<Long, HashSet<Long>>();
	
	//map for the list of relevant taxonomy nodes
	Map<Node,TLongBipartition> taxonomyGraphNodesMap;
	Map<TreeNode,Integer> rankForTreeNode;//this is just a map of node and the order of the tree it came from in the list
	Map<TreeNode,Integer> sourceForTreeNode; // same as above but the order of the source
	
 	int nodeId = 0;

	/**
	 * instantiation runs the entire analysis
	 * @param trees
	 * @param gdb
	 * @param mapInternalNodesToTax
	 * @throws Exception
	 */
	public BipartOracle(List<Tree> trees, GraphDatabaseAgent gdb, boolean useTaxonomy) throws Exception {
		
//		this.trees = trees;
		this.gdb = gdb;
		this.USING_TAXONOMY = useTaxonomy;
		
		long w = new Date().getTime(); // for timing 
		
		//just associate the rank with the treenodes
		createTreeIdRankMap(trees);

		// if we are using taxonomy then tree tip labels must correspond to taxon ids. for tips that are
		// matched to *higher* (i.e. non-terminal) taxa, this will gather the taxon ids of all the terminal
		// taxa contained by that higher taxon. these 'exploded' sets are used later during various steps.
		if (USING_TAXONOMY) { 
			explodedTipsHash = TipExploder.explodeTipsReturnHash(trees, gdb); 
			 reduceExplodedTipsHash();
		}
		
		// populate class members: treeNode, original, treeNodeIds, nodeIdForName, nameForNodeId, bipartsByTree
		gatherTreeData(trees);
		gatherBipartitions(); // populate class member: bipart
				
		// make the lica nodes in the graph for all the nested bipartitions
		// populate class members: nestedChildren, nestedParents, paths, nodeForBipart, bipartForNode
		createLicaNodesFromBiparts(trees);

		// need to get the relevant nodes from taxonomy in a set to be used for later analyses
		if (USING_TAXONOMY) { populateTaxonomyGraphNodesMap(trees); }
		
		// now process the trees
		// populate class members: graphNodesForTreeNode, hasMRCAChildOf
		mapTreeRootNodes(trees); // creates new nodes if necessary
		generateMRCAChildOfs();  // connect all nodes, must be done *after* all nodes are created!
		mapNonRootNodes(trees);	 // use the mrca rels to map the trees into the graph
		
		// setting this for use eventually in the mapInternalNodes 
		// because taxonomy is added above, this isn't necessary
		//if (USING_TAXONOMY) { mapInternalNodesToTaxonomy(trees); }
		
		System.out.println("loading is complete. total time: " + (new Date().getTime() - w) / 1000 + " seconds.");
	}
	
	/**
	 * Get pairwise sums of all tree biparts and put all biparts into a single array for future use.
	 */
	private void gatherBipartitions() {
		TLongBipartition[] summed = new BipartSetSum(bipartsByTree).toArray();
		bipart = new TLongBipartition[original.length + summed.length];
		
		// add all the originals to the front of the bipart array
		for (int i = 0; i < original.length; i++) { bipart[i] = original[i]; }
		
		// add all the summed biparts afterward. keep track of their ids in a list so we can use them later
		for (int i = 0; i < summed.length; i++) {
			int k = i + original.length;
			bipart[k] = summed[i];
			summedBipartIds.add(k);
		}
		System.out.println("retained " + original.length + " biparts and created " + summedBipartIds.size() + " new combinations. total: " + bipart.length);
	}
	
	private void createTreeIdRankMap(List<Tree> trees){
		rankForTreeNode = new HashMap<TreeNode,Integer>();
		sourceForTreeNode = new HashMap<TreeNode,Integer>();
		int curt = trees.size();
		int cust = 1; // starting at 1 not zero to avoid confusion with taxonomy
		for (Tree t: trees) {
			for (TreeNode tn: t.internalNodes(NodeOrder.PREORDER)) { rankForTreeNode.put(tn, curt); sourceForTreeNode.put(tn, cust);}
			for (TreeNode tn: t.externalNodes()) { rankForTreeNode.put(tn, curt); sourceForTreeNode.put(tn, cust);}
			curt--;
			cust++;
		}
	}
	
	private void reduceExplodedTipsHash(){
		explodedTipsHashReduced = new HashMap<Object, Collection<Object>>();
		shrunkSet = new HashMap<Object,Collection<Object>>();
		HashSet<Object> totallist = new HashSet<Object>();
		for(Object tip: explodedTipsHash.keySet()){
			HashSet<Object> tlist= (HashSet<Object>) explodedTipsHash.get(tip);
			if(tlist.size() == 1){
				totallist.addAll(tlist);
			}
		}
		for(Object tip: explodedTipsHash.keySet()){
			HashSet<Object> tlist = (HashSet<Object>) explodedTipsHash.get(tip);
			HashSet<Object> newlist= new HashSet<Object>();
			if(tlist.size() == 1){
				explodedTipsHashReduced.put(tip, tlist);
			}else{
				Object templab = tlist.iterator().next();//save one to put in if empty
				newlist = new HashSet<Object>();
				for(Object t: tlist){
					if (totallist.contains(t)){
						newlist.add(t);
						shrunkSet.put(t, tlist);
					}
				}
				if (newlist.size() == 0){
					newlist.add(templab);
					shrunkSet.put(templab, tlist);
				}
				explodedTipsHashReduced.put(tip, newlist);
			}
			//System.out.println(tip+" "+explodedTipsHash.get(tip)+" "+explodedTipsHashReduced.get(tip));
		}
	}
	
	/**
	 * Trivial convenience function just made for clarity.
	 * @param n
	 * @return
	 */
	private Node getParentTaxNode(Node n) {
		return n.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
	}

	/**
	 * Collect information from trees, including tip labels, nodes that are mapped to those tip labels,
	 * original bipartitions from trees, and their original internal nodes. Objects and associated information
	 * are stored in various class members for later use.
	 * @param trees
	 */
	private void gatherTreeData(List<Tree> trees) {

		System.out.print("collecting information from " + trees.size() + " trees...");
		long z = new Date().getTime();
		
		// process the tips. what to do depends on whether we are using taxonomy or not
		if (USING_TAXONOMY) { mapTipsToTaxa(trees); }
		else { importTipsFromTrees(trees); }

		int treeNodeCount = getInternalNodeCount(trees);
		
		// gather the tree nodes, tree structure
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
		
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
	}

	/**
	 * Create a node in the database for each unique tip label that occurs in at least one tree.
	 * @param trees
	 */
	private void importTipsFromTrees(List<Tree> trees) {
		Transaction tx = gdb.beginTx();
		for (Tree t : trees) {
			for (TreeNode l : t.externalNodes()) {
				Object label = l.getLabel();
				if (! nodeIdForLabel.containsKey(label)) {
					Node tip = gdb.createNode();
					tip.setProperty(NodeProperty.NAME.propertyName, label);
					nodeIdForLabel.put(label, tip.getId());
					labelForNodeId.put(tip.getId(), label);
				}
			}
		}
		tx.success();
		tx.finish(); 
	}

	/**
	 * If we are using taxonomy, then all incoming tip labels must correspond to a taxon.
	 * This will just find them and store their node ids.
	 * @param trees
	 */
	private void mapTipsToTaxa(List<Tree> trees) {
	
		Index<Node> ottIdIndex = gdb.getNodeIndex("graphTaxUIDNodes", "type", "exact", "to_lower_case", "true");
		for (Tree t : trees) {
			for (TreeNode l : t.externalNodes()) {
				try {
					for (Object label: explodedTipsHash.get(l)) {
						collectTaxonNodeForTipLabel(label, ottIdIndex);
					}
					//for (Object label: explodedTipsHashReduced.get(l)) {
					//	collectTaxonNodeForTipLabel(label, ottIdIndex);
					//}
	
					// doing this for the actual tip name as well if it isn't already in the database
					// might be the case if there are exploded tips... is this to cover tips that weren't exploded?
					Object label = l.getLabel();
					collectTaxonNodeForTipLabel(label, ottIdIndex);
				} catch (NoSuchElementException ex) {
					System.out.println("\nERROR: " + ex +". Aborting import procedure on tree: " + t);
					System.exit(1);
				}
			}
		}
	}
	
	private void collectTaxonNodeForTipLabel(Object label, Index<Node> ottIdIndex) {
		if (! nodeIdForLabel.containsKey(label)) {
			Node tip = ottIdIndex.get(NodeProperty.TAX_UID.propertyName, label).getSingle();
			if (tip == null) { throw new NoSuchElementException("no taxon could be found with id: " + label); }
			nodeIdForLabel.put(label, tip.getId());
			labelForNodeId.put(tip.getId(), label);
		}
	}
	
	private int getInternalNodeCount(List<Tree> trees) {
		int i = 0;
		for (Tree t : trees) { i += t.internalNodeCount() - 1; } // don't count the root
		return i;
	}
	
	private void gatherTreeDataPrevious(List<Tree> trees) {
/*
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
					if (! nodeIdForLabel.containsKey(lab)) {
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
						nodeIdForLabel.put(lab, tip.getId());
						labelForNodeId.put(tip.getId(), lab);
					}
				}
				//doing this for the actual tip name as well if it isn't already in the database
				//might be the case if there are exploded tips
				Object n = l.getLabel();
				if (! nodeIdForLabel.containsKey(n)) {
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
					nodeIdForLabel.put(n, tip.getId());
					labelForNodeId.put(tip.getId(), n);
				}
				/*
				if (! nodeIdForName.containsKey(n)) {
					Node tip = gdb.createNode();
					tip.setProperty(NodeProperty.NAME.propertyName, n);
					nodeIdForName.put(n, tip.getId());
					nameForNodeId.put(tip.getId(), n);
				}
			} *
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
		} */
	}
	
	@SuppressWarnings("unchecked")
	private List<Integer>[] newIntegerListArray(int size) {
		List<Integer>[] list = new ArrayList[size];
		for (int i = 0; i < size; i++) { list[i] = new ArrayList<Integer>(); }
		return list;
	}
	
	
	private void createLicaNodesFromBiparts(List<Tree> trees) {
		
		/* first: do a pairwise all-by-all on the input trees to find all nested bipartitions between them.
		 * we will be recording information on nestedchildof relationships for two purposes:
		 * 
		 * 1. to create MRCACHILDOF rels between the appropriate nodes. all nestedchildof relationships need
		 * to be recorded as MRCACHILDOFs in the graph (including those for nodes within a single tree).
		 * 
		 * 2. for the findPaths procedure, which walks paths through the graph and accumulates ingroup/outgroup
		 * taxa of child/parent nodes from trees with different taxon sets. only nestedChildOf pairs from trees
		 * with non-overlapping taxon sets need to be sent to findPaths, because no two nodes from trees with
		 * identical taxon sets could possibly augment one anothers' ingroup/outgroup composition. See the
		 * findPaths procedure itself for more information.
		 */
		
		// these will *not* be used by the findPaths procedure (but will be used to make MRCACHILDOFs)
		nestedParents = newIntegerListArray(bipart.length);

		// i don't know if we ever actually need this 
		nestedChildren = newIntegerListArray(bipart.length);

		// these will *only* be used by the findPaths procedure
		nestedAugmentingParents = newIntegerListArray(bipart.length);

		// i don't think we actually need this...
//		nestedAugmentingChildren = newIntegerListArray(bipart.length);
		
		System.out.println("beginning all-by-all treewise bipart comparison to identify nested biparts for paths...");
		long z = new Date().getTime();
		
		for (int i = 0; i < trees.size(); i++) {
			System.out.println("on tree " + i + " -- comparing " + trees.get(i).internalNodeCount() + " biparts to other trees");
			
			// add all the nestedchildof rels implied among the nodes within the tree itself
			for (TreeNode c : trees.get(i).internalNodes(NodeOrder.POSTORDER)) {
				// apparently we don't want to include tips (which are not in the iterator)
				// or the root here... this is how it was before
				if (c.isTheRoot()) { continue; }

				int cid = treeNodeIds.get(c);
				for (TreeNode p = c.getParent(); ! p.isTheRoot(); p = p.getParent()) {
					int pid = treeNodeIds.get(p);
					nestedParents[pid].add(cid);
					nestedChildren[cid].add(pid);
					
				}
			}

			Tree P = trees.get(i);
			List<TreeNode> pRootChildren = P.getRoot().getChildren(); // skip the root (and the tips, but we do that below)

			// do the treewise comparisons between this tree and all other trees
			for (int j = 0; j < trees.size(); j++) {
				if (i == j) { continue; } // don't attempt to compare a tree to itself
								
				// skip the root (and the tips, but we do that below)
				List<TreeNode> qRootChildren = trees.get(j).getRoot().getChildren();

				boolean treesHaveIdenticalTaxa = original[treeNodeIds.get(pRootChildren.get(0))]
				                                          .hasIdenticalTaxonSetAs(original[treeNodeIds.get(qRootChildren.get(0))]);
				
				LinkedList<TreeNode> pStack = new LinkedList<TreeNode>();
//				pStack.push(pRootChildren);
//				while (! pStack.isEmpty()) {
				for (pStack.addAll(pRootChildren); ! pStack.isEmpty(); ) {
					
					TreeNode p = pStack.pop();
					if (p.isExternal()) { continue; } // don't process tips
					int pid = treeNodeIds.get(p);
					TLongBipartition bp = original[pid];

					LinkedList<TreeNode> qStack = new LinkedList<TreeNode>();
//					qStack.push(qRoot);
//					while (! qStack.isEmpty()) {
					for (qStack.addAll(qRootChildren); ! qStack.isEmpty(); ) {
						
						TreeNode q = qStack.pop();
						if (q.isExternal()) { continue; } // don't process tips
						int qid = treeNodeIds.get(q);
						TLongBipartition bq = original[qid];

						if (bq.isNestedPartitionOf(bp)) {
							// record this nestedchildof relationship
							nestedParents[qid].add(pid);
							nestedChildren[pid].add(qid);
							if (! treesHaveIdenticalTaxa) {
								nestedAugmentingParents[qid].add(pid);
//								nestedAugmentingChildren[qid].add(pid);
							}
						}
						
						if (bq.ingroup().containsAny(bp.ingroup())) {
							// if bq's children may be nested partitions of p
							for (TreeNode qc : q.getChildren()) { qStack.push(qc); }
						}
					}
				}
			}
		
			// now do the all-by-all of this tree's nodes against the biparts from the bipart set sum
			for (TreeNode node : P.internalNodes(NodeOrder.PREORDER)) {
				if (node.isTheRoot()) { continue; }
			
				final int child = treeNodeIds.get(node);
				
//				if (nestedChildren[k] == null) { nestedChildren[k] = new ArrayList<Integer>(); }
	
				List<Integer> moreParents = summedBipartIds.parallelStream() // use a parallel stream to explore all other biparts
						.map(parent -> { return bipart[child].isNestedPartitionOf(bipart[parent]) ? parent : null; })
						.collect(toList()).stream()
						.filter(a -> a != null)
						.collect(toList());
								
				for (Integer parent : moreParents) {
//					if (nestedChildren[p] == null) { nestedChildren[p] = new ArrayList<Integer>(); }
					nestedParents[child].add(parent);
					nestedChildren[parent].add(child);
				}
			}	
		}
		
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
				
		// now walk through the implied graph of bipart nestings and build the potential paths that could be followed through this
		// graph. these will be used to generate nodes in the actual neo4j database in the next step.
		// here we considered just walking from each bipart mapped to a given tree node x, to only those biparts mapped to
		// parents/children of x, but there should be cases where this would not find all the relevant paths, so we do all of them.
		paths = new HashSet<Path>();
		System.out.print("traversing potential paths through the graph to determine node ingroup/outgroup composition...");
		z = new Date().getTime();
		Transaction tx = gdb.beginTx();
		for (int i = 0; i < bipart.length; i++) {
			if (bipart[i].outgroup().size() > 0) {
				CompactLongSet pathResult = findPaths(i, new CompactLongSet(), new ArrayList<Integer>(), 0,i);
				if (pathResult == null) {
					// biparts that are not nested in any others won't be saved in any of the paths, so we need to make graph nodes
					// for them now. we will need these nodes for mapping trees
					createNode(bipart[i]);
				}
			}
		}
		tx.success();
		tx.finish();
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds paths:"+paths.size());

		// report results
		if (VERBOSE) {
			for (Path p : paths) {
				System.out.println(p);
			}
		}		
		
		// create nodes based on paths
		System.out.print("now creating all lica nodes in the graph...");
		z = new Date().getTime();
		tx = gdb.beginTx();
		for (Path p : paths) {
			generateNodesFromPaths(0, new CompactLongSet(), p.toArray());
		}
		tx.success();
		tx.finish();
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");

	}

	/**
	 * This creates all the MRCACHILDOF rels among all graph nodes. It uses an all by all pairwise
	 * comparison, which has quadratic order of growth. We do this because it makes tree loading trivial:
	 * after mapping a tree node x just to graph node(s) g(x), just look at all the graph nodes connected
	 * to g(x) by MRCACHILDOF rels to find all the graph nodes that may possibly be mapped to x's
	 * parent/child tree nodes.
	 */
	private void generateMRCAChildOfs(){

		// gather information about which rels to create. this is the full n^2 comparisons among all nodes (i.e. biparts)
		// TODO: this has the same name as a class member variable. if it is the same thing, it should not be declared here
		// if it isn't the same thing, then it should have a different name. this whole section might go away so i am not
		// going to bother trying to figure this out right now.
		Map<TLongBipartition, Set<TLongBipartition>> nestedChildren = new HashMap<TLongBipartition, Set<TLongBipartition>>();
		
		
		System.out.print("beginning all-by-all node comparison ("+nodeForBipart.size()+") to identify possible MRCACHILDOF rels...");
		long z = new Date().getTime();
		for (TLongBipartition parent: nodeForBipart.keySet()) {

			Set<TLongBipartition> children = nodeForBipart.keySet().parallelStream()
				.map( child -> { // map all biparts that can be nested within this parent
					if (child.isNestedPartitionOf(parent) && (! child.equals(parent))) { return child; }
					else { return null; }})
				.collect(() -> new HashSet<TLongBipartition>(), (a,b) -> a.add(b), (a,b) -> a.addAll(b)).stream()
				.filter(r -> r != null).collect(toSet()); // filter nulls out of results

			nestedChildren.put(parent, children);
		}
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
		
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
		
		// now create the rels. not trying to do this in parallel because concurrent db ops seem unwise. but we could try.
		System.out.print("recording MRCACHILDOF rels ("+nestedChildren.size()+") in the db...");
		Transaction tx = gdb.beginTx();
		for (TLongBipartition parent : nestedChildren.keySet()) {
			for (TLongBipartition child : nestedChildren.get(parent)) {
				updateMRCAChildOf(nodeForBipart.get(child), nodeForBipart.get(parent));
			}
		}
		tx.success();
		tx.finish();
		
		if(USING_TAXONOMY){
			System.out.print(" (also recording MRCACHILDOF rels for taxonomy)...");
			//sequential for debugging right now
			// now create the rels. not trying to do this in parallel because concurrent db ops seem unwise. but we could try.
			tx = gdb.beginTx();
			//slower but works
			for (Node taxnd : taxonomyGraphNodesMap.keySet()) {
				TLongBipartition taxbp = taxonomyGraphNodesMap.get(taxnd);
				for (TLongBipartition ndbp: nodeForBipart.keySet()) {
					//System.out.println(nodeForBipart.get(ndbp)+" "+taxbp+" "+ndbp+" "+taxbp.ingroup().containsAll(ndbp.ingroup())+" "+taxbp.ingroup().containsAny(ndbp.outgroup())+" "+ndbp.ingroup().containsAny(taxbp.ingroup()));
					//check as parent
					if (taxbp.ingroup().containsAll(ndbp.ingroup())){
						if(taxbp.ingroup().containsAny(ndbp.outgroup()))
							updateMRCAChildOf(nodeForBipart.get(ndbp), taxnd);
						//else if(taxbp.ingroup().size() > ndbp.ingroup().size())
						//	updateMRCAChildOf(nodeForBipart.get(ndbp), taxnd);
					}else if(ndbp.ingroup().containsAny(taxbp.ingroup())
							&& taxbp.ingroup().containsAny(ndbp.outgroup())==false && 
							taxbp.ingroup().containsAll(ndbp.ingroup()) == false ){//check as child;
						updateMRCAChildOf(taxnd, nodeForBipart.get(ndbp));
					}
				}
			}
			
			//could be faster, needs more testing
			/*
			for(TLongBipartition ndbp: nodeForBipart.keySet()){
				Long ndid = ((long[])nodeForBipart.get(ndbp).getProperty("mrca"))[0];
				Node taxNode = gdb.getNodeById(ndid);
				while (taxNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
					taxNode = getParentTaxNode(taxNode);
					if (taxonomyGraphNodesMap.keySet().contains(taxNode)){
						TLongBipartition taxbp = taxonomyGraphNodesMap.get(taxNode);
						if (taxbp.ingroup().containsAll(ndbp.ingroup()) && taxbp.ingroup().containsAny(ndbp.outgroup())) {
							updateMRCAChildOf(nodeForBipart.get(ndbp), taxNode);
							while (taxNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
								taxNode = getParentTaxNode(taxNode);
								updateMRCAChildOf(nodeForBipart.get(ndbp), taxNode);
							}
							break;
						}else if(ndbp.ingroup().containsAny(taxbp.ingroup())
								&& taxbp.ingroup().containsAny(ndbp.outgroup())==false && 
								taxbp.ingroup().containsAll(ndbp.ingroup()) == false ){//check as child;
							updateMRCAChildOf(taxNode, nodeForBipart.get(ndbp));
							
						}
					}
				}
			}*/
			
			
			tx.success();
			tx.finish();
		}
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");

	}
	
	/**
	 * 
	 */
	private void populateTaxonomyGraphNodesMap(List<Tree> trees){
		taxonomyGraphNodesMap = new HashMap<Node,TLongBipartition>();
		for(Tree tree: trees){
			for(TreeNode treeLf : tree.getRoot().getDescendantLeaves()){
				Node taxNode = gdb.getNodeById(nodeIdForLabel.get(treeLf.getLabel()));
				while (taxNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
					taxNode = getParentTaxNode(taxNode);
					if (taxonomyGraphNodesMap.containsKey(taxNode.getId())){break;}
					CompactLongSet taxonIngroup = new CompactLongSet((long[]) taxNode.getProperty(NodeProperty.MRCA.propertyName));
					TLongBipartition taxonBipart = new TLongBipartition(taxonIngroup, new CompactLongSet());
					taxonomyGraphNodesMap.put(taxNode, taxonBipart);
				}
			}
		}
		//System.out.println(taxonomyGraphNodesMap);
	}
	
	/**
	 * Map all the tree root nodes into the graph. This will create new nodes in the graph for any root nodes
	 * that cannot be mapped to preexisting nodes. This *must* be done before loading trees--tree loading uses a
	 * preorder traversal.
	 * @param trees
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
			if(USING_TAXONOMY){
				for(Node b : taxonomyGraphNodesMap.keySet()){
					if(taxonomyGraphNodesMap.get(b).containsAll(rootBipart)){
						graphNodes.add(b);
					}
				}
				if (VERBOSE) { System.out.println(root.getNewick(false)+" "+graphNodes); }
			}
		
			if (graphNodes.size() < 1) {
				if (VERBOSE) { System.out.println("could not find a suitable node to map to the root, will make a new one\n\t"+rootBipart); }
				graphNodes.add(createNode(rootBipart));
			}

			tx.success();
			tx.finish();
	
			graphNodesForTreeNode.put(root, graphNodes);
		}
	}

	private void mapNonRootNodes(List<Tree> trees) {
		
		for (int k = 0; k < trees.size(); k++) {
			Tree tree = trees.get(k);

			System.out.print("mapping tree " + k + " (recording rels in the db)...");
			long z = new Date().getTime();

			Transaction tx = gdb.beginTx(); // one transaction to rule the tree
			
			// now map the internal nodes other than the root
			for (TreeNode treeNode : tree.internalNodes(NodeOrder.PREORDER)) {
				if (! treeNode.isTheRoot()) {
					
					Set<Node> graphNodes = mapGraphNodes(treeNode, tree, false);
					if (graphNodes.size() == 0) { // every internal node must map to at least one node in the graph.
						System.out.println("could not map node: " + treeNode.getNewick(false)+ ".\ngraphNodes: " + graphNodes);
						throw new AssertionError();
					}
					graphNodesForTreeNode.put(treeNode, (HashSet<Node>) graphNodes);
				}
			}

			// now connect all the tips to their parent nodes
			for (TreeNode treeTip : tree.externalNodes()) {
				//this will map to compatible "internal" graph nodes
				mapGraphNodes(treeTip,tree,true);
				//this will map to terminal graph nodes directly from the tips
				Node tip = gdb.getNodeById(nodeIdForLabel.get(treeTip.getLabel()));
				for (Node parent : graphNodesForTreeNode.get(treeTip.getParent())){
					updateMRCAChildOf(tip, parent);
					Relationship rel = tip.createRelationshipTo(parent, RelType.STREECHILDOF);
					rel.setProperty("source", sourceForTreeNode.get(treeTip).intValue());
					// this is a temporary property to enable rapid re-synthesis
					rel.setProperty("sourcerank", rankForTreeNode.get(treeTip).intValue());
				}
			}

			// TODO add tree metadata node
			
			tx.success();
			tx.finish();
			System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
		}	
		System.out.println("all trees have been mapped.");
	}
	
	private Set<Node> mapGraphNodes(TreeNode treeNode, Tree tree, boolean external) {
		// get the graph nodes that match this node's parent
		HashSet<Node> graphNodesForParent = graphNodesForTreeNode.get(treeNode.getParent());
		
		// get the graph nodes that match this node
		TLongBipartition nodeBipart;
		if (external == false) { nodeBipart = original[treeNodeIds.get(treeNode)]; }
		else { nodeBipart = getGraphBipartForTreeNode(treeNode,tree); }
		//System.out.println(treeNode.getNewick(false)+" "+rankForTreeNode.get(treeNode));
		//System.out.println("\t"+nodeBipart);

		HashSet<Node> graphNodes = new HashSet<Node>();
		HashSet<Node> taxNodesMatched = new HashSet<Node>();
		
		// if you create the mrcachildofs before, then you can do this
		for (Node parent : graphNodesForParent){
			for (Relationship r: parent.getRelationships(Direction.INCOMING, RelType.MRCACHILDOF)){
				Node potentialChild = r.getStartNode();
				TLongBipartition childBipart;
				if(USING_TAXONOMY == false || taxonomyGraphNodesMap.containsKey(potentialChild)==false){
					childBipart = bipartForNode.get(potentialChild);
				}else{
					childBipart = taxonomyGraphNodesMap.get(potentialChild);
				}	
				//System.out.println("\t\t"+potentialChild+"\t"+childBipart);
				if(USING_TAXONOMY && taxonomyGraphNodesMap.containsKey(parent)){
					if(taxonomyGraphNodesMap.containsKey(potentialChild)){
						if(childBipart != null && parent.equals(potentialChild) == false && 
								childBipart.ingroup().containsAll(nodeBipart.ingroup()) && 
								childBipart.ingroup().containsAny(nodeBipart.outgroup())==false &&
								taxonomyGraphNodesMap.get(parent).ingroup().containsAll(childBipart.ingroup())){
							graphNodes.add(potentialChild);
							taxNodesMatched.add(potentialChild);
							Relationship rel = potentialChild.createRelationshipTo(parent, RelType.STREECHILDOF);
							rel.setProperty("source", sourceForTreeNode.get(treeNode).intValue());
							rel.setProperty("sourcerank", rankForTreeNode.get(treeNode).intValue());
							//Go through for the taxonomy
							//if we match taxonomy then we might check all the children of the taxonomy and map through
							// we don't have to do this for the other nodes because they will be connected by MRCACHILDOFs
							boolean going = true;
							Node curchild = potentialChild;
							while(going){
								going = false;
								for(Relationship rc: curchild.getRelationships(RelType.TAXCHILDOF, Direction.INCOMING)){
									Node tch = rc.getStartNode();
									if(taxonomyGraphNodesMap.containsKey(tch)==false)
										continue;
									TLongBipartition tchb = taxonomyGraphNodesMap.get(tch);
									if(parent.equals(tch) == false && 
											tchb.ingroup().containsAll(nodeBipart.ingroup()) && 
											tchb.ingroup().containsAny(nodeBipart.outgroup())==false &&
											taxonomyGraphNodesMap.get(parent).ingroup().containsAll(tchb.ingroup())){
										graphNodes.add(tch);
										taxNodesMatched.add(tch);
										Relationship rel2 = tch.createRelationshipTo(curchild, RelType.STREECHILDOF);
										rel2.setProperty("source", sourceForTreeNode.get(treeNode).intValue());
										rel2.setProperty("sourcerank", rankForTreeNode.get(treeNode).intValue());
										going = true;
										curchild = tch;
										break;
									}
								}
							}
						}
					}else{
						if(childBipart != null &&  childBipart.containsAll(nodeBipart) &&
								taxonomyGraphNodesMap.get(parent).ingroup().containsAll(childBipart.ingroup()) &&
								taxonomyGraphNodesMap.get(parent).ingroup().containsAny(childBipart.outgroup())){
							graphNodes.add(potentialChild);
							Relationship rel = potentialChild.createRelationshipTo(parent, RelType.STREECHILDOF);
							rel.setProperty("source", sourceForTreeNode.get(treeNode).intValue());
							rel.setProperty("sourcerank", rankForTreeNode.get(treeNode).intValue());
						}
					}
				}else if (USING_TAXONOMY && taxonomyGraphNodesMap.containsKey(potentialChild)){
					if(childBipart != null &&  childBipart.ingroup().containsAll(nodeBipart.ingroup()) && 
							childBipart.ingroup().containsAny(nodeBipart.outgroup())==false){// &&
									//nodeBipart.ingroup().containsAll(childBipart.ingroup())){// &&
									//nodeBipart.ingroup().containsAny(childBipart.outgroup())){
						graphNodes.add(potentialChild);
						taxNodesMatched.add(potentialChild);
						Relationship rel = potentialChild.createRelationshipTo(parent, RelType.STREECHILDOF);
						rel.setProperty("source", sourceForTreeNode.get(treeNode).intValue());
						rel.setProperty("sourcerank", rankForTreeNode.get(treeNode).intValue());
						//Go through for the taxonomy
						//if we match taxonomy then we might check all the children of the taxonomy and map through
						// we don't have to do this for the other nodes because they will be connected by MRCACHILDOFs
						boolean going = true;
						Node curchild = potentialChild;
						while(going){
							going = false;
							for(Relationship rc: curchild.getRelationships(RelType.TAXCHILDOF, Direction.INCOMING)){
								Node tch = rc.getStartNode();
								if(taxonomyGraphNodesMap.containsKey(tch)==false)
									continue;
								TLongBipartition tchb = taxonomyGraphNodesMap.get(tch);
								if(childBipart.ingroup().containsAll(nodeBipart.ingroup()) && 
										childBipart.ingroup().containsAny(nodeBipart.outgroup())==false){
									graphNodes.add(tch);
									taxNodesMatched.add(tch);
									Relationship rel2 = tch.createRelationshipTo(curchild, RelType.STREECHILDOF);
									rel2.setProperty("source", sourceForTreeNode.get(treeNode).intValue());
									rel2.setProperty("sourcerank", rankForTreeNode.get(treeNode).intValue());
									going = true;
									curchild = tch;
									break;
								}
							}
						}
					}
				}else{
					// BE CAREFUL containsAll is directional
					if (childBipart != null && childBipart.containsAll(nodeBipart) 
							&& childBipart.isNestedPartitionOf(bipartForNode.get(parent))){
						graphNodes.add(potentialChild);
						Relationship rel = potentialChild.createRelationshipTo(parent, RelType.STREECHILDOF);
						rel.setProperty("source", sourceForTreeNode.get(treeNode).intValue());
						rel.setProperty("sourcerank", rankForTreeNode.get(treeNode).intValue());
					}	
				}
				//System.out.println("\t\t"+graphNodes);
			}
		}
		//connect equivalent tax nodes to the nodes
		for(Node gn: taxNodesMatched){
			for (Relationship trel: gn.getRelationships(Direction.INCOMING, RelType.MRCACHILDOF)){
				if (graphNodes.contains(trel.getStartNode())){
					Relationship rel = trel.getStartNode().createRelationshipTo(gn, RelType.STREECHILDOF);
					rel.setProperty("source", sourceForTreeNode.get(treeNode).intValue());
					rel.setProperty("sourcerank", rankForTreeNode.get(treeNode).intValue());
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
	 * @param originalParentId
	 * @return
	 */
	private CompactLongSet findPaths(int parentId, CompactLongSet cumulativeIngroup, ArrayList<Integer> path, int level, int originalParentId) {

		TLongBipartition parent = bipart[parentId];
		
		// three cases where we exit without defining a node:
		if (hasNoParentBiparts(parentId) || 					// found a dead end path
			cumulativeIngroup.containsAny(parent.outgroup()) || // conflict between potential node and its parent
			cumulativeIngroup.containsAll(parent.ingroup())) { 	// child that already contains entire parent
        	return null;
		}
		
		// otherwise prepare to define a new bipart
		path.add(parentId);

		if (VERBOSE) {
			System.out.println("\n" + indent(level) + "current path is: " + path);
			System.out.println(indent(level) + "on parent" + parentId + ": " + parent.toString(labelForNodeId));
			System.out.println(indent(level) + "incoming ingroup: " + cumulativeIngroup.toString(labelForNodeId));
		}

		// collect the ingroup from all downstream (child) biparts' ingroups'
		cumulativeIngroup.addAll(parent.ingroup());

		if (VERBOSE) {
			System.out.println(indent(level) + "cumulative ingroup is: " + cumulativeIngroup.toString(labelForNodeId));
		}

		// collect the outgroup from all upstream (parent) biparts' outgroups
		CompactLongSet cumulativeOutgroup = new CompactLongSet(parent.outgroup());
	    boolean newline = false;
	    for (int nextParentId : nestedAugmentingParents[parentId]) {
			if (path.contains(nextParentId)) { continue; }
			//TODO: CHECK THESE TWO BITS. THEY WORK IN PYTHON BUT STILL NEED MORE TESTING
			//		DELETE IF THERE ARE PROBLEMS
			//can continue if we already have all the outgroup and ingroup in here
			CompactLongSet testset = new CompactLongSet(bipart[nextParentId].ingroup());
			testset.addAll(bipart[nextParentId].outgroup());
			testset.removeAll(cumulativeIngroup);
			testset.removeAll(cumulativeOutgroup);
			if (testset.size() == 0){continue;}
			//can continue if the nextid isn't in the original one because that means original bipart on this path can't be a nested child of the next parent
			if(nestedAugmentingParents[originalParentId].contains(nextParentId)==false){continue;}
			CompactLongSet outgroupsToAdd = findPaths(nextParentId, new CompactLongSet(cumulativeIngroup), path, level+1, originalParentId);
			if (outgroupsToAdd != null) { // did not encounter a dead end path
		    	newline = true;
				cumulativeOutgroup.addAll(outgroupsToAdd);
			}
		}
	    
	    if (VERBOSE) {
			System.out.println((newline ? "\n" : "") + indent(level) + "cumulative outgroup is: " + cumulativeOutgroup.toString(labelForNodeId));
		    System.out.println(indent(level) + "done with parent " + parent.toString(labelForNodeId));
			System.out.println(indent(level) + "Will create node " + cumulativeIngroup.toString(labelForNodeId) + " | " + cumulativeOutgroup.toString(labelForNodeId) + " based on path " + path);
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
		if (hasNoParentBiparts(parentId) || 					// found a dead end path
			cumulativeIngroup.containsAny(parent.outgroup()) || // conflict between potential node and its parent
			cumulativeIngroup.containsAll(parent.ingroup())) { 	// child that already contains entire parent
        	return null;
		}

		// otherwise prepare to define a new node

		if (VERBOSE) {
			System.out.println("\n" + indent(position) + "path is: " + Arrays.toString(path) + " and current bipart id is: " + path[position]);
			System.out.println(indent(position) + "on parent" + parentId + ": " + parent.toString(labelForNodeId));
			System.out.println(indent(position) + "incoming ingroup: " + cumulativeIngroup.toString(labelForNodeId));
		}

		// collect the ingroup from all downstream (child) biparts' ingroups'
		cumulativeIngroup.addAll(parent.ingroup());

		if (VERBOSE) {
			System.out.println(indent(position) + "cumulative ingroup is: " + cumulativeIngroup.toString(labelForNodeId));
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
			System.out.println((newline ? "\n" : "") + indent(position) + "cumulative outgroup is: " + cumulativeOutgroup.toString(labelForNodeId));
		    System.out.println(indent(position) + "done with parent " + parent.toString(labelForNodeId));
			System.out.println(indent(position) + "Will create node " + cumulativeIngroup.toString(labelForNodeId) + " | " + cumulativeOutgroup.toString(labelForNodeId) + " based on path " + Arrays.toString(path));
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
		if (VERBOSE) { System.out.println(node); }
		//this is all here because of the exploded reduced
		if(USING_TAXONOMY){
			//expand from the shrunken set of exploded tips
			CompactLongSet fullset = new CompactLongSet();
			for(Long s: b.ingroup()){
				if(shrunkSet.containsKey(labelForNodeId.get(s))){
					for(Object x: shrunkSet.get(labelForNodeId.get(s))){
						fullset.add(nodeIdForLabel.get(x));
					}
				}else{
					fullset.add((Long) s);
				}
			}
			node.setProperty(NodeProperty.MRCA.propertyName, fullset.toArray());
			fullset = new CompactLongSet();
			for(Long s: b.outgroup()){
				if(shrunkSet.containsKey(labelForNodeId.get(s))){
					for(Object x: shrunkSet.get(labelForNodeId.get(s))){
						fullset.add(nodeIdForLabel.get(x));
					}
				}else{
					fullset.add((Long) s);
				}
			}
			node.setProperty(NodeProperty.OUTMRCA.propertyName, fullset.toArray());
		}else{
			node.setProperty(NodeProperty.MRCA.propertyName, b.ingroup().toArray());
			node.setProperty(NodeProperty.OUTMRCA.propertyName, b.outgroup().toArray());
		}
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
	
	private boolean hasNoParentBiparts(int child) {
		return nestedParents[child].size() == 0;
	}

	private TLongBipartition getGraphBipartForTreeNode(TreeNode p, Tree t) {
		CompactLongSet ingroup = new CompactLongSet();
		CompactLongSet outgroup = new CompactLongSet();
		TreeBipartition b = t.getBipartition(p);

		if (USING_TAXONOMY) {
			for (TreeNode n : b.ingroup())  {
				//for	(Object s: explodedTipsHash.get(n)) { ingroup.add(nodeIdForLabel.get(s)); }
				for	(Object s: explodedTipsHashReduced.get(n)) { ingroup.add(nodeIdForLabel.get(s)); }
			}
			for (TreeNode n : b.outgroup()) {
				//for	(Object s: explodedTipsHash.get(n)) { outgroup.add(nodeIdForLabel.get(s)); }
				for	(Object s: explodedTipsHashReduced.get(n)) { outgroup.add(nodeIdForLabel.get(s)); }
			}
		} else {
			for (TreeNode n : b.ingroup())  { ingroup.add(nodeIdForLabel.get(n.getLabel()));  }
			for (TreeNode n : b.outgroup()) { outgroup.add(nodeIdForLabel.get(n.getLabel())); }
		}
		return new TLongBipartition(ingroup, outgroup);
	}
	
	private class Path {
		
		private final int[] path;

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
		
		// these are tests for order
		String dbname = "test.db";
//		runSimpleTest(nestedOverlap2(), dbname);
//		runSimpleTest(conflictingAugmenting(), dbname);
		runSimpleTest(cycleConflictTrees(), dbname);
//		runSimpleTest(nonOverlapTrees(), dbname);
//		runSimpleTest(test4Trees(), dbname);
//		runSimpleTest(test3Trees(), dbname);
//		runSimpleTest(testInterleavedTrees(), dbname);
//		runSimpleTest(completeOverlap(), dbname);

		// this is a stress test for the loading procedure -- 100 trees with 600 tips each.
		// lots of duplicate biparts though so it should only take a few mins
//		loadATOLTreesTest(dbname);
		
		// these are tests for taxonomy
//		runDipsacalesTest(dbname);
	}
	
	/**
	 * Stress test the loading procedure. If everything is working properly then this should be fast.
	 * If this test is not fast (i.e. a few minutes or less), then there is probably a bug somewhere.
	 */
	@SuppressWarnings("unused")
	private static void loadATOLTreesTest(String dbname) throws Exception {

		FileUtils.deleteDirectory(new File(dbname));
		
		List<Tree> t = new ArrayList<Tree>();
		BufferedReader br = new BufferedReader(new FileReader("test-atol/atol_bootstrap.tre"));
		String str;
		while((str = br.readLine())!=null){
			t.add(TreeReader.readTree(str));
		}
		br.close();
		System.out.println("trees read");
	
		GraphDatabaseAgent gdb = new GraphDatabaseAgent(dbname);

		BipartOracle bi = new BipartOracle(t, gdb, false);

	}
	
	@SuppressWarnings("unused")
	private static void runDipsacalesTest(String dbname) throws Exception {
		String version = "1";
		
		FileUtils.deleteDirectory(new File(dbname));
		
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
		BipartOracle bi = new BipartOracle(t, new GraphDatabaseAgent(new EmbeddedGraphDatabase(dbname)), false);

		System.out.println("original bipartitions: ");
		for (int i = 0; i < bi.bipart.length; i++) {
			System.out.println(i + ": " + bi.bipart[i].toString(bi.labelForNodeId));
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

		BipartOracle bi = new BipartOracle(t, new GraphDatabaseAgent(new EmbeddedGraphDatabase(dbname)), true);

		System.out.println("original bipartitions: ");
		for (int i = 0; i < bi.bipart.length; i++) {
			System.out.println(i + ": " + bi.bipart[i].toString(bi.labelForNodeId));
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
	private static List<Tree> completeOverlap() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("(((((A,B),C),D),E),((((F,G),H),I),J));"));
		t.add(TreeReader.readTree("(((((A,B),(C,D)),E),F),((G,H),(I,J)));"));
		t.add(TreeReader.readTree("(((A,H),(C,E)),(((D,F),(I,J)),(G,B)));"));
		t.add(TreeReader.readTree("(((((((((A,E),D),C),F),G),H),I),B),J);"));
		return t;
	}
	
	@SuppressWarnings("unused")
	private static List<Tree> nestedOverlap() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("((((A,B),C),D),E);"));
		t.add(TreeReader.readTree("((G,H),(I,(J,F)));"));
		t.add(TreeReader.readTree("(((D,F),(I,J)),(G,B));"));
		t.add(TreeReader.readTree("(((((((((A,E),D),C),F),G),H),I),B),J);"));
		return t;
	}

	@SuppressWarnings("unused")
	private static List<Tree> nestedOverlap2() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("((A,B),C);"));
		t.add(TreeReader.readTree("((B,D),C);"));
		t.add(TreeReader.readTree("((E,B),(G,H));"));
		t.add(TreeReader.readTree("((A,E),B);"));
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
	private static List<Tree> conflictingAugmenting() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("(((A,B),C),D);"));
		t.add(TreeReader.readTree("(((((A,E),C),B),F),D);"));
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
