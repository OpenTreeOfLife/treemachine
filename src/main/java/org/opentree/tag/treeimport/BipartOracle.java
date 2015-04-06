	package org.opentree.tag.treeimport;

import jade.tree.JadeNode;
import jade.tree.TreeBipartition;
import jade.tree.TreeNode;
import jade.tree.NodeOrder;
import jade.tree.Tree;
import jade.tree.TreeParseException;
import jade.tree.TreeReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;

import opentree.GraphExplorer;
import opentree.GraphInitializer;
import opentree.constants.NodeProperty;
import opentree.constants.RelProperty;
import opentree.constants.RelType;
import opentree.synthesis.TarjanSCC;
import opentree.synthesis.TopologicalOrder;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.opentree.bitarray.ImmutableCompactLongSet;
import org.opentree.bitarray.LongSet;
import org.opentree.bitarray.MutableCompactLongSet;
import org.opentree.graphdb.GraphDatabaseAgent;

public class BipartOracle {

	private final GraphDatabaseAgent gdb;
	private final boolean USING_TAXONOMY;
	
	boolean VERBOSE = false;
	
	boolean subset = false;
	
	boolean mapdeepest = true;
	
	// maps of tip names for higher taxon tips to sets of tip names for their included taxa

	/** key is id for tip, value is hashset of ids that are exploded with this id */
	Map<Object, Collection<Object>> explodedTipsHash;
	/** key is id for tip, value is hashset of ids that are exploded with this id */
	Map<Object, Collection<Object>> explodedTipsHashReduced;
	/** this will have the key as the id used in the shrunk set and the collection as the full set*/
	Map<Object, Collection<Object>> shrunkSet;
	
	// tree nodes and bipartitions
	
	/**
	 * A mapping of tree nodes directly onto their original bipartitions stored in the bipart array (so no futzing with
	 * ids should be required. The bipart array should not contain any duplicates, so the many tree nodes may map to the
	 * same bipart.
	 */
	Map<TreeNode, LongBipartition> bipartForTreeNode = new HashMap<TreeNode, LongBipartition>();
	Map<TreeNode, LongBipartition> bipartForTreeNodeExploded = new HashMap<TreeNode, LongBipartition>();

	/**
	 * A mapping of the biparts in the bipart array that correspond exactly to original tree nodes, onto those tree nodes.
	 * The bipart array should not contain any duplicates, so the many tree nodes may map to the same bipart.
	 */
	Map<LongBipartition, Set<TreeNode>> treeNodesForBipart = new HashMap<LongBipartition, Set<TreeNode>>();
	
	/**
	 * Sets of bipartitions extracted from each input tree and grouped according to the tree from which they were drawn.
	 * This is used for the bipart set sum operation. These groups should contain no duplicates -- only the group for the
	 * first tree in which a bipart is observed will contain that bipart.
	 */
	List<Collection<LongBipartition>> bipartsByTreeNoDuplicates = new ArrayList<Collection<LongBipartition>>();

	/**
	 * Just a list of the indices for the sum-result biparts so we can easily access them independently of originals. This
	 * list is used (and reused) for stream operations confined to the sum-results.
	 * <br/><br/>
	 * <tt>summedBipartIds[i] == bipart[i]</tt>
	 */
	List<Integer> summedBipartIds = new ArrayList<Integer>();
	Map<Integer,ArrayList<Integer>> summedBipartSourceBiparts = new HashMap<Integer,ArrayList<Integer>>();

	/**
	 * Just a list of the indices for the biparts generated during the path traversal procedure (i.e. not original tree biparts
	 * nor sum-result biparts). Currently we don't use this for anything.
	 * <br/><br/>
	 * <tt>generatedBipartIds[i] == bipart[i]</tt>
	 */
	List<Integer> generatedBipartIds = new ArrayList<Integer>();

	/**
	 * The list of *all* biparts: originals, sums, and those generated from paths. We append to this list as we find new
	 * bipartitions. Indices in this list essentially function as bipart ids and we use them in other data structures such
	 * as <tt>nestedChildren</tt>, <tt>nestedParents</tt>, <tt>paths</tt>, <tt>summedBipartIds</tt>, and <tt>generatedBipartIds</tt>.
	 */
	List<LongBipartition> bipart = new ArrayList<LongBipartition>(); 

	/** Just a set containing all the known biparts for fast lookups. */
	Map<LongBipartition, Integer> bipartId = new HashMap<LongBipartition, Integer>();
	
	/** This map associates new biparts created during path traversal with the preexisting biparts that were used to generate them.
	 * The new biparts are more restrictive, because their ingroups/outgroups are augmented by the path traversal procedure, but they
	 * may still be compatible parents/children of the parents/children of the preexisting biparts that gave rise to them, so we
	 * keep track of them here (associated with the preexisting biparts that were used to generate them) so that we can check whether
	 * or not we need to make the appropriate MRCACHILDOF rels. */
	List<Set<Integer>> analogousBiparts;

	/** The *valid* paths found during the findPaths procedure, each of which represents a linear sequence
	 * of a nested child bipartitions to a parent, which is a nested child of another parent, and so on. */
	Set<Path> paths;
	
	/** <tt>nestedChildren[i]</tt> contains the ids of all the biparts that are nested within <tt>bipart[i]</tt> */
	List<Set<Integer>> nestedChildren;
	
	/** <tt>nestedParents[i]</tt> contains the ids of all the biparts that <tt>bipart[i]</tt> *is* a nested child of */
	List<Set<Integer>> nestedParents;
	
	List<Set<Integer>> compatibleBiparts;


	/** <tt>nestedAugmentingParents[i]</tt> contains the ids of all the biparts that are nested within <tt>bipart[i]</tt> AND which
	 * have a different taxonomic composition from <tt>bipart[i]</tt>, i.e. for all b in <tt>nestedChildren[i]</tt>, (ingroup(b)
	 * ∪ outgroup(b)) ≠ (ingroup(<tt>bipart[i]</tt>) ∪ outgroup(<tt>bipart[i]</tt>)). This difference in taxonomic composition is
	 * a requirement for bipartitions to augment one another's ingroups and outgroups during path traversals. Thus, by not adding
	 * bipartitions with identical taxonomic composition to this list, we can avoid unnecessary path traversals. */
	List<Set<Integer>> nestedAugmentingParents;

	// these associate input labels with node ids
	
	/** The neo4j node id for the node corresponding to each unique tip label from the set of input trees. */
	Map<Object, Long> nodeIdForLabel = new HashMap<Object, Long>();

	/** The unique tip label from the set of input trees for each neo4j node that represents a tip node from the trees. */
	Map<Long, Object> labelForNodeId = new HashMap<Long, Object>();

	// maps of graph nodes to various things

	/** neo4j node for bipartition */
	private Map<LongBipartition, Node> graphNodeForBipart = new HashMap<LongBipartition, Node>();
	
	/** bipartition for neo4j node */
	private Map<Node, LongBipartition> bipartForGraphNode = new HashMap<Node, LongBipartition>();
	private Map<Node, LongBipartition> bipartForGraphNodeExploded = new HashMap<Node, LongBipartition>();
	
	
	/** all the graph nodes that have been mapped to the tree node */
	private Map<TreeNode, HashSet<Node>> graphNodesForTreeNode = new HashMap<TreeNode, HashSet<Node>>(); 
	
	/** these are the graph nodes that actually are used. this way we can remove the nodes and rels that aren't */
	private HashSet<Long> nodesWithSTREERels = new HashSet<Long>();
	
	/** Just a simple container to keep track of rels we know we've made so we can cut down on database queries to find out of they exist. */
	private Map<Long, HashSet<Long>> hasMRCAChildOf = new HashMap<Long, HashSet<Long>>();
	private Map<Long, HashMap<Long,HashSet<String>>> hasSTREEChildOf = new HashMap<Long, HashMap<Long,HashSet<String>>>();
	private Map<Long, HashSet<Long>> notValidSTREEChildOf = new HashMap<Long, HashSet<Long>>();
	private Map<Long, HashSet<Long>> testedSTREEChildOf = new HashMap<Long, HashSet<Long>>();

	
	/** map for the list of relevant taxonomy nodes */
	Map<Node,LongBipartition> taxonomyGraphNodesMap;
	JadeNode taxonomyJadeRoot;
	
	
	/** this is just a map of node and the RANK of the tree it came from in the list. higher is better--earlier in the list */
	Map<TreeNode,Integer> rankForTreeNode;

	/** this is just a map of node and the ORDER (higher is later in the list) of the tree it came from in the list */
	Map<TreeNode,String> sourceForTreeNode;
	
	//these are subset tips and roots to be connected to other tips
	Map<TreeNode, String> subsetTipInfo;
	
	//these are the source labels
	Map<Tree, String> sourceForTrees;
	
 	int nodeId = 0;
 	String ottidFromSubset = null;
 	Node taxnodeFromSubset = null;
	/**
	 * instantiation runs the entire analysis
	 * @param trees
	 * @param gdb
	 * @param mapInternalNodesToTax
	 * @throws Exception
	 */
    public BipartOracle(List<Tree> trees, GraphDatabaseAgent gdb, boolean useTaxonomy,
            Map<Tree, String> sources, Map<TreeNode, String> subsetInfo, boolean subset, String subsetFileName) throws Exception {
        this.subset = subset;
        //store the subset in the index
        if (subset) {
        	//mapdeepest = false;
            Transaction tx = gdb.beginTx();
            Index<Node> ottIdIndex = gdb.getNodeIndex("graphTaxUIDNodes", "type", "exact", "to_lower_case", "true");
            
            if (subsetFileName.contains("/")) {
                ottidFromSubset = subsetFileName.split("/")[1];
            } else {
            	ottidFromSubset = subsetFileName;
            }
            ottidFromSubset = ottidFromSubset.replace(".tre", "").replace("ott", "");
            taxnodeFromSubset = ottIdIndex.get(NodeProperty.TAX_UID.propertyName, ottidFromSubset).getSingle();
            Index<Node> ottIdIndexss = gdb.getNodeIndex("subproblemRoots", "type", "exact", "to_lower_case", "true");
            ottIdIndexss.add(taxnodeFromSubset, "subset", ottidFromSubset);
            tx.success();

            tx.finish();
        }
        this.gdb = gdb;
        this.USING_TAXONOMY = useTaxonomy;
        this.subsetTipInfo = subsetInfo;
        this.sourceForTrees = sources;

        long w = new Date().getTime(); // for timing 

        validateTrees(trees);

        //just associate the rank with the treenodes
        createTreeIdRankMap(trees);

		// if we are using taxonomy then tree tip labels must correspond to taxon ids. for tips that are
        // matched to *higher* (i.e. non-terminal) taxa, this will gather the taxon ids of all the terminal
        // taxa contained by that higher taxon. these 'exploded' sets are used later during various steps.
        if (USING_TAXONOMY) {
            explodedTipsHash = TipExploder.explodeTipsReturnHash(trees, gdb);
            reduceExplodedTipsHash();
        }

		// first process the incoming trees and collect bipartitions
        // populate class members: treeNode, original, treeNodeIds, nodeIdForName, nameForNodeId, bipartsByTree
        gatherTreeData(trees);
        if (USING_TAXONOMY) {
            mapTreeNodesToTaxa(trees);
        }

        // need to get the relevant nodes from taxonomy in a set to be used for later analyses
        if (USING_TAXONOMY) {
            populateTaxonomyGraphNodesMap(trees);
        }

        gatherBipartitions(trees); // populate class member: bipart

        // identify all the pairwise bipart nestings and find the valid hierarchical nestings (paths)
        identifyNestedChildBiparts(trees); // populate class members: nestedChildren, nestedParents, nodeForBipart, bipartForNode
        findAllPaths(); // populate class member: paths

		// revisit all the valid paths to:
        // 1) identify new ingroup/outgroup composition for nested biparts and make corresponding nodes
        // 2) record all bipart nesting information for generating MRCACHILDOF rels among nodes
        createNodesUsingPaths();

		// connect all the appropriate nodes with MRCACHILDOF rels
        // populate class members: graphNodesForTreeNode, hasMRCAChildOf
        mapTreeRootNodes(trees); // creates new nodes if necessary
        generateMRCAChildOfs();  // connect all nodes, must be done *after* all nodes are created!

        // now we map the trees using the MRCACHILDOF rels to find suitable parents/children
        mapNonRootNodes(trees);

		// setting this for use eventually in the mapInternalNodes 
        // because taxonomy is added above, this isn't necessary
        //if (USING_TAXONOMY) { mapInternalNodesToTaxonomy(trees); }
        //clean up the nodes and rels that aren't used at all
        removeUnusedNodesAndRels();

        System.out.println("loading is complete. total time: " + (new Date().getTime() - w) / 1000 + " seconds.");
    }
	
	private void removeUnusedNodesAndRels() {
		System.out.println("cleaning the ununsed nodes and relationships");
		int removedNs = 0;
		int removedRs = 0;
		Transaction tx = gdb.beginTx();
		for(Node nd: bipartForGraphNode.keySet()){
			if(nodesWithSTREERels.contains(nd.getId()) == false){
				boolean dont = false;
				for(Relationship rel: nd.getRelationships()){
					if(rel.getType().equals(RelType.STREECHILDOF) || 
							rel.getType().equals(RelType.TAXCHILDOF)) {
						dont = true;
					}
				}
				if(dont == false){
					for(Relationship rel: nd.getRelationships()){
						rel.delete();
						removedRs += 1;
					}
					nd.delete();
					removedNs += 1;
				}
			}
		}
		tx.success();
		tx.finish();
		System.out.println("done cleaning "+removedNs+" nodes and "+removedRs+" relationships");
	}

	/**
	 * check if the trees are appropriate. throws exceptions if errors are found.
	 * @param trees
	 * @return
	 */
	private boolean validateTrees(List<Tree> trees) {
		for (Tree t : trees) {
			if (t.internalNodeCount() < 2) { // we don't conside the root
				throw new IllegalArgumentException("Trees must contain at least one internal node (i.e. not the tip nor the root). The tree " + t + " is not valid.");
			}
		}
		return true;
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

//		int treeNodeCount = getInternalNodeCount(trees);
		
		// gather the tree nodes, tree structure
		// for nexson, we would want ott ids instead of tip labels.
//		treeNode = new TreeNode[treeNodeCount];
//		original = new LongBipartition[treeNodeCount];
//		original = new ArrayList<LongBipartition>();
//		observedBiparts = new HashSet<LongBipartition>();
		nodeId = 0;
		int count = 0;
		for (Tree tree : trees) {
			System.out.println("gatherTreeData:"+count);
			Collection<LongBipartition> treeBiparts = new ArrayList<LongBipartition>();
			for (TreeNode node: tree.internalNodes(NodeOrder.PREORDER)) {
				LongBipartition b = getGraphBipartForTreeNode(node, tree);
				treeBiparts.add(b); //add here so the root can be included in the sum
				//if (! node.isTheRoot()) { // we deal with the root later
//					treeNode[nodeId] = node;
//					treeNodeIds.put(node, nodeId);
					//LongBipartition b = getGraphBipartForTreeNode(node, tree);
					//treeBiparts.add(b);
					if (! bipartId.containsKey(b)) { // ensure we only add unique biparts to the list
						bipart.add(b);
						bipartId.put(b, bipart.size() - 1);
					} else {
						b = bipart.get(bipartId.get(b)); // otherwise use the one we already have stored
					}
					if (treeNodesForBipart.get(b) == null) { treeNodesForBipart.put(b, new HashSet<TreeNode>()); }
					treeNodesForBipart.get(b).add(node);
					bipartForTreeNode.put(node, b);
					if(USING_TAXONOMY){
						LongBipartition be = getExpandedTaxonomyBipart(b);
						bipartForTreeNodeExploded.put(node, be);
					}
				//}
			}
			bipartsByTreeNoDuplicates.add(treeBiparts);
			count += 1;
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
	
	/**
	 * 
	 * @param label
	 * @param ottIdIndex
	 */
	HashMap<TreeNode,ImmutableCompactLongSet> deepestNodeTaxa = new HashMap<TreeNode,ImmutableCompactLongSet>();
	HashMap<TreeNode,ImmutableCompactLongSet> shallowestNodeTaxa = new HashMap<TreeNode,ImmutableCompactLongSet>();
	private void mapTreeNodesToTaxa(List<Tree> trees){
		GraphExplorer ge = new GraphExplorer(gdb);
		for(Tree t: trees){
			for(TreeNode l: t.internalNodes(NodeOrder.PREORDER)){
				LongBipartition be = bipartForTreeNodeExploded.get(l);
				HashSet<Node> benodes = new HashSet<Node> ();
				for(Long bel: be.ingroup()){
					benodes.add(gdb.getNodeById(bel));
				}
				Node shallow = ge.getTaxonomyMRCA(benodes);
				System.out.println(shallow+" "+be);
				ImmutableCompactLongSet icls = new ImmutableCompactLongSet((long [] ) shallow.getProperty("mrca"));
				shallowestNodeTaxa.put(l, icls);
				if(l == t.getRoot()){
					deepestNodeTaxa.put(l, icls);
				}else{
					boolean going = true;
					Node deepest = shallow;
					while(going){
						if(deepest.hasRelationship(RelType.TAXCHILDOF, Direction.OUTGOING)==false){
							going = false;
							break;
						}
						Node next = deepest.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
						ImmutableCompactLongSet tests = new ImmutableCompactLongSet((long [] ) next.getProperty("mrca"));
						if(tests.containsAny(be.outgroup())){
							going = false;
							break;
						}else{
							deepest = next;
						}
					}
					ImmutableCompactLongSet icld = new ImmutableCompactLongSet((long [] ) deepest.getProperty("mrca"));
					deepestNodeTaxa.put(l, icld);
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
	
	/**
	 * Get pairwise sums of all tree biparts and put all biparts into a single array for future use.
	 */
	Set<LongBipartition> observedOriginals;
	private void gatherBipartitions(List<Tree> trees) {
		//we don't want to do it like this because we want to retain which is being summed
		/*
		LongBipartition[] summed = new BipartSetSum(bipartsByTreeNoDuplicates).toArray();
		*/
		//here is the long form so we can retain where these are coming from
		System.out.print("removing duplicate bipartitions across/within groups...");
		long z = new Date().getTime();
		observedOriginals = new HashSet<LongBipartition>();
		List<Collection<LongBipartition>> filteredGroups = new ArrayList<Collection<LongBipartition>>();
		List<LongBipartition> filteredRoots = new ArrayList<LongBipartition>();
		int n = 0;
		int d = 0;
		for (int i = 0; i < bipartsByTreeNoDuplicates.size(); i++) {
			LongBipartition filteredRoot = null;
			Collection<LongBipartition> filteredCurTree = new ArrayList<LongBipartition>();
			for (LongBipartition b : bipartsByTreeNoDuplicates.get(i)) {
				if(b.outgroup().size()==0)
					filteredRoot=b;
				if (! observedOriginals.contains(b)) {
					filteredCurTree.add(b);
					observedOriginals.add(b);
					n++;
				} else {
					d++;
				}
			}
			filteredGroups.add(filteredCurTree);
			filteredRoots.add(filteredRoot);
		}
		//observedOriginals = null; // free resource for garbage collector
		System.out.println(" done. found " + d + " duplicate biparts. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");

//		System.out.println("now summing " + n + " unique biparts across " + filteredGroups.size() + " groups...");
		System.out.println("starting treewise bipart sums");
		z = new Date().getTime();

		//first we do the root sums. we do the others later
		int originalCount = bipart.size();
		/*
		for(int i=0; i<filteredRoots.size();i++){
			for (int j = 0; j < filteredGroups.size(); j++) {
				if(j == i)
					continue;
				LongBipartition tls = filteredRoots.get(i);
//				for(LongBipartition tls: filteredGroups.get(i)){
					for(LongBipartition tlb: filteredGroups.get(j)){
						//TODO: make sure strict Sum is 1) faster than sum 2) doesn't create any issues
						LongBipartition newsum = tlb.strictSum(tls);
						if(newsum == null)
							continue;
						if(newsum.outgroup().size()==0)
							continue;
						if (! bipartId.containsKey(newsum)) {
							bipart.add(newsum);
							int k = bipart.size() - 1;
							bipartId.put(newsum, k);
							summedBipartIds.add(k);
							ArrayList<Integer> pars = new ArrayList<Integer>();
							pars.add(bipartId.get(tls));pars.add(bipartId.get(tlb));
							summedBipartSourceBiparts.put(k, pars);
						}
					}
				//}
			}
		}*/
		/*
		 * CAN ADD THESE TREE AWARE TAXA AWARE TESTS TO THE OTHER SUMS
		 * 
		 * IT WOULD BE IN processBipartsForTree
		 */
		int i=0;
		for(Tree t: trees){
			System.out.println("starting tree "+ i++ +". nodecount: " + t.internalNodeCount() + ". total biparts: " + bipart.size());
			int j = 0;
			//comparing root first
			TreeNode root = t.getRoot();
			ImmutableCompactLongSet rootshallow = shallowestNodeTaxa.get(root);
			LongBipartition tlb = getGraphBipartForTreeNode(root, t);
			for(Tree x: trees){
				j++;
				if (t == x) { continue; }
				System.out.println("comparing to tree " + j);
				for(TreeNode tnx : x.internalNodes(NodeOrder.POSTORDER)){
					ImmutableCompactLongSet otherdeep = deepestNodeTaxa.get(tnx);
					if(rootshallow.containsAll(otherdeep) && rootshallow.size() > otherdeep.size()){
						continue;
					}
					LongBipartition tls = getGraphBipartForTreeNode(tnx, x);
					LongBipartition newsum = tlb.strictSum(tls);
					if(newsum == null)
						continue;
					if(newsum.outgroup().size()==0)
						continue;
					if (! bipartId.containsKey(newsum)) {
						if(VERBOSE)
							System.out.println("generating new summed bipart "+newsum);
						bipart.add(newsum);
						int k = bipart.size() - 1;
						bipartId.put(newsum, k);
						summedBipartIds.add(k);
						ArrayList<Integer> pars = new ArrayList<Integer>();
						pars.add(bipartId.get(tls));pars.add(bipartId.get(tlb));
						summedBipartSourceBiparts.put(k, pars);
						//break;
					}
				}
			}
		}
		
		
		//Trying a different approach with each node from each tree in a postorder fashion
		// when you match you stop and move to the next node for a potential sum
		// THIS SHOULD BE PARALLELIZED
		/*int i = 0;
		for(Tree t: trees){
			System.out.println("starting tree " + i++ + ". nodecount: " + t.internalNodeCount() + ". total biparts: " + bipart.size());
			int j = 0;
			for(Tree x: trees){
				j++;
				if (t == x) { continue; }
				System.out.println("comparing to tree " + j);
				//change this to a stack so you can break earlier
				HashSet<TreeNode> done = new HashSet<TreeNode>();

				for(TreeNode tnt : t.internalNodes(NodeOrder.POSTORDER)){
					//if (done.contains(tnt)) { continue; }
					LongBipartition tlb = getGraphBipartForTreeNode(tnt, t);

					// don't compare trees with identical taxon sets -- these are not the nodes you are looking for.
//					TreeNode childOfRootX = x.internalNodes(NodeOrder.PREORDER).iterator().next();
//					if (tlb.hasIdenticalTaxonSetAs(getGraphBipartForTreeNode(childOfRootX, x))) { continue; }
					
					for(TreeNode tnx : x.internalNodes(NodeOrder.POSTORDER)){
						LongBipartition tls = getGraphBipartForTreeNode(tnx, x);
						LongBipartition newsum = testSum(tls,tlb,observedOriginals);
						//LongBipartition newsum = tlb.strictSum(tls);
						if(newsum == null)
							continue;
						if(newsum.outgroup().size()==0)
							continue;
						if (! bipartId.containsKey(newsum)) {
							if(VERBOSE)
								System.out.println("generating new summed bipart "+newsum);
							bipart.add(newsum);
							int k = bipart.size() - 1;
							bipartId.put(newsum, k);
							summedBipartIds.add(k);
							ArrayList<Integer> pars = new ArrayList<Integer>();
							pars.add(bipartId.get(tls));pars.add(bipartId.get(tlb));
							summedBipartSourceBiparts.put(k, pars);
							break;
						}
					}
				}
			}
		}
		*/
		
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
		
/*		// add all the originals to the front of the bipart array
		for (int i = 0; i < original.length; i++) {
			bipart.add(original[i]);
			bipartId.put(original[i], i);
		} */
		
		// add all the summed biparts afterward. keep track of their ids in a list so we can use them later
		/*int originalCount = bipart.size();
		for (int i = 0; i < summed.length; i++) {
//			int k = i + bipart.size();
			if (! bipartId.containsKey(summed[i])) {
				bipart.add(summed[i]);
				int k = bipart.size() - 1;
				bipartId.put(summed[i], k);
				summedBipartIds.add(k);
			}
		}*/
		System.out.println("retained " + originalCount + " biparts and created " + summedBipartIds.size() + " new combinations. total: " + bipart.size());
	}
	
	/**
	 * This differs from sum in that it doesn't not return a bipart if it is equal
	 * and it requires overlap with the ingroups not ingroups or outgroups. No guarantee is made about the type of the
	 * returned bipartition--it may be mutable or not. To ensure it is the correct type, pass it to a constructor for
	 * the desired object type.
	 * 
	 * @param that
	 * @return
	 */
	public LongBipartition strictSumPhylo(LongBipartition t1, LongBipartition t2) {

		if (! t1.isCompatibleWith(t2))
			return null;
		
		if (! t1.ingroup().containsAny(t2.ingroup()))
			return null;

		if (t1.equals(t2))
			return null;
		
		MutableCompactLongSet sumIn = new MutableCompactLongSet();
		sumIn.addAll(t1.ingroup());
		sumIn.addAll(t2.ingroup());

		MutableCompactLongSet sumOut = new MutableCompactLongSet();
		sumOut.addAll(LSintersection(t1.outgroup(),t2.outgroup()));
		if(sumOut.size()==0)
			return null;

		return new ImmutableLongBipartition(sumIn, sumOut);
	}
	
    private LongBipartition testSum(LongBipartition par1, LongBipartition par2, Set<LongBipartition> originalBiparts) {
        LongBipartition xor = par1.xor(par2);
        LongBipartition ss = par1.strictSum(par2);
        //System.out.println(ss);
        if (ss == null) {
            return null;
        }
        // System.out.println(par1+" "+par2);
        //else
        //	return ss;
        if (xor.ingroup().size() == 0 || xor.outgroup().size() == 0 || par1.outgroup().size() == 0 || par2.outgroup().size() == 0) {
            //System.out.println("would make " + ss);
            return ss;
        } else {
            HashMap<Long, HashMap<Long, MutableCompactLongSet>> Q = new HashMap<Long, HashMap<Long, MutableCompactLongSet>>();
            for (Long l : ss.ingroup()) {
                Q.put(l, new HashMap<>());
                for (Long l2 : ss.ingroup()) {
                    if (l == l2) {
                        continue;
                    }
                    Q.get(l).put(l2, new MutableCompactLongSet());
                }
            }
            for (Long l : ss.outgroup()) {
                Q.put(l, new HashMap<>());
                for (Long l2 : ss.outgroup()) {
                    if (l == l2) {
                        continue;
                    }
                    Q.get(l).put(l2, new MutableCompactLongSet());
                }
            }
            //populate Q and reduce bipartition set
            HashSet<LongBipartition> totest = new HashSet<LongBipartition>();
            for (LongBipartition testBi : originalBiparts) {
                //System.out.println(testBi);
                //TODO: make this a phylogenetic compatible comparison instead
                if (testBi.ingroup().containsAny(ss.outgroup()) && testBi.outgroup().containsAny(ss.ingroup())
                        && testBi.ingroup().containsAny(ss.ingroup())) {
                } else {
                        //populate Q
                    //intersection ingroup bipart with ss ingroup
                    //intersection outgroup bipart with ss ingroup
                    //for each ingroup intersection, add the others and add the outgroup intersection to the mutable set
                    LongSet ing1 = LSintersection(testBi.ingroup(), ss.ingroup());
                    LongSet ing2 = LSintersection(testBi.outgroup(), ss.ingroup());
                    //System.out.println(testBi);
                    //System.out.println("i: "+ing1+" "+ing2);
                    for (Long l1 : ing1) {
                        for (Long l2 : ing1) {
                            if (l1 == l2) {
                                continue;
                            }
                            Q.get(l1).get(l2).addAll(ing2);
                        }
                    }
                    //outgroup
                    LongSet out1 = LSintersection(testBi.ingroup(), ss.outgroup());
                    LongSet out2 = LSintersection(testBi.outgroup(), ss.outgroup());
                    //System.out.println("o: "+out1+" "+out2);
                    for (Long l1 : out1) {
                        for (Long l2 : out1) {
                            if (l1 == l2) {
                                continue;
                            }
                            Q.get(l1).get(l2).addAll(out2);
                        }
                    }
                    if(out1.size()>0 && ing1.size() > 0){
                        for (Long l1 : out1) {
                            for (Long l2 : ing1) {
                                if(Q.get(l1).containsKey(l2)==false)
                                    Q.get(l1).put(l2, new MutableCompactLongSet());
                                Q.get(l1).get(l2).addAll(out2);
                            }
                        }
                    }
                    //System.out.println(Q);
                    //System.out.println("=======");
                }
            }
            //System.out.println(Q);
            HashMap<Long, MutableCompactLongSet> R = new HashMap<Long, MutableCompactLongSet>();
            for (Long l : ss.ingroup()) {
                R.put(l, new MutableCompactLongSet());
            }
            for (LongBipartition testBi : originalBiparts) {
                if (testBi.isCompatibleWith(ss) == false) {
                    continue;
                }
                LongSet ing = LSintersection(testBi.ingroup(), ss.ingroup());
                LongSet out = LSintersection(testBi.outgroup(), ss.outgroup());
                HashMap<Long, MutableCompactLongSet> outtoadd = new HashMap<Long, MutableCompactLongSet>();
                //TODO: do the outgroup
                for (Long l1 : out) {
                    outtoadd.put(l1, new MutableCompactLongSet());
                    outtoadd.get(l1).add(l1);
                    for (Long l2 : Q.get(l1).keySet()) {
                        if (ss.ingroup().contains(l2)==false && testBi.outgroup().containsAny(Q.get(l1).get(l2))) {
                            outtoadd.get(l1).add(l2);
                        }if (ss.ingroup().contains(l2) && testBi.ingroup().contains(l2)) {
                            outtoadd.get(l1).addAll(Q.get(l1).get(l2));
                        }
                    }
                }

                //TODO: change to a while and keep updating
                for (Long l1 : ing) {
                    R.get(l1).addAll(out);
                    for (Long o : outtoadd.keySet()) {
                        R.get(l1).addAll(outtoadd.get(o));
                    }
                    for (Long l2 : Q.get(l1).keySet()) {
                        if (testBi.ingroup().containsAny(Q.get(l1).get(l2))) {
                            R.get(l2).addAll(out);
                            for (Long o : outtoadd.keySet()) {
                                R.get(l2).addAll(outtoadd.get(o));
                            }
                        }
                    }
                }
            }
            //System.out.println(R);
            for (Long l : ss.ingroup()) {
                if (R.get(l).size() != ss.outgroup().size()) {
                    System.out.println("wouldn't make " + ss);
                    //System.exit(0);
                    return null;
                }
            }
            System.out.println("would make " + ss);
            //System.exit(0);
            return ss;
        }
        //return null;
    }

	private LongSet LSintersection(LongSet x,LongSet y){
		MutableCompactLongSet in = new MutableCompactLongSet();
		for(Long l1:x){
			for(Long l2: y){
				if (l1==l2)
					in.add(l1);
			}
		}
		return in;
	}
	
	private void createTreeIdRankMap(List<Tree> trees){
		rankForTreeNode = new HashMap<TreeNode,Integer>();
		sourceForTreeNode = new HashMap<TreeNode,String>();
		int curt = trees.size();
		int cust = 1; // starting at 1 not zero to avoid confusion with taxonomy
		for (Tree t: trees) {
			for (TreeNode tn: t.internalNodes(NodeOrder.PREORDER)) {
				rankForTreeNode.put(tn, curt); 
				if(sourceForTrees == null){
					sourceForTreeNode.put(tn, String.valueOf(cust));
				}else{
					sourceForTreeNode.put(tn,sourceForTrees.get(t));
				}
			}
			for (TreeNode tn: t.externalNodes()) {
				rankForTreeNode.put(tn, curt); 
				if(sourceForTrees == null){
					sourceForTreeNode.put(tn, String.valueOf(cust));
				}else{
					sourceForTreeNode.put(tn,sourceForTrees.get(t));
				}
			}
			curt--;
			cust++;
		}
	}
	
	private void reduceExplodedTipsHash(){
		//explodedTipsHashReduced = explodedTipsHash;
		explodedTipsHashReduced = new HashMap<Object, Collection<Object>>();
		shrunkSet = new HashMap<Object,Collection<Object>>();
		HashSet<Object> totallist = new HashSet<Object>();
		for(Object tip: explodedTipsHash.keySet()){
			HashSet<Object> tlist= (HashSet<Object>) explodedTipsHash.get(tip);
			if(tlist.size() == 1){
				totallist.addAll(tlist);
			}
		}
		//System.out.println(totallist);
		for(Object tip: explodedTipsHash.keySet()){
			HashSet<Object> tlist = (HashSet<Object>) explodedTipsHash.get(tip);
			HashSet<Object> newlist= new HashSet<Object>();
			if(tlist.size() == 1){
				explodedTipsHashReduced.put(tip, tlist);
			}else{
				newlist = new HashSet<Object>();
				for(Object t: tlist){
					if (totallist.contains(t)){
						newlist.add(t);
					}
				}
				//System.out.println(tip+" "+newlist+" "+newlist.size());
				if(newlist.size() != tlist.size()){
					shrunkSet.put(((TreeNode)tip).getLabel(), tlist);
				}//else if newlist.size() == tlist.size() we can just put it all in there, it isn't shrunk
				explodedTipsHashReduced.put(tip, newlist);
			}
			//System.out.println(tip+" "+explodedTipsHash.get(tip)+" "+explodedTipsHashReduced.get(tip));
		}
		//checking for overlap to add things
		for(Object tip: explodedTipsHashReduced.keySet()){
			if(shrunkSet.containsKey(((TreeNode)tip).getLabel())== false)
				continue;
			explodedTipsHashReduced.get(tip).add(((TreeNode)tip).getLabel());
			for(Object tip2: explodedTipsHashReduced.keySet()){
				if(shrunkSet.containsKey(((TreeNode)tip2).getLabel())== false)
					continue;
				if(tip == tip2 || ((TreeNode)tip).getLabel().equals(((TreeNode)tip2).getLabel())){
					continue;
				}else{
					//tip2 contained within tip
					if(shrunkSet.get(((TreeNode)tip).getLabel()).containsAll(shrunkSet.get(((TreeNode)tip2).getLabel()))){
						explodedTipsHashReduced.get(tip).add(((TreeNode)tip2).getLabel());
					}
				}
			}	
		}
		if(VERBOSE){
			System.out.println(shrunkSet);
			System.out.println(explodedTipsHashReduced);
		}
		//System.exit(0);
	}
	
	/**
	 * Trivial convenience function just made for clarity.
	 * @param n
	 * @return
	 */
	private Node getParentTaxNode(Node n) {
		return n.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
	}
	
	private int getInternalNodeCount(List<Tree> trees) {
		int i = 0;
		for (Tree t : trees) { i += t.internalNodeCount() - 1; } // don't count the root
		return i;
	}
	
	private List<Set<Integer>> newSynchronizedIntegerSetList(int size) {
		List<Set<Integer>> list = Collections.synchronizedList(new ArrayList<Set<Integer>>());
		for (int i = 0; i < size; i++) { list.add(Collections.synchronizedSet(new HashSet<Integer>())); }
		return list;
	}
	
	/**
	 * This is a pairwise allxall comparison of the nodes of the input trees and also the summed biparts,
	 * to find all nested-bipartition relationships among them. The procedure uses the structure of the input
	 * trees to limit the actual number of comparisons needed--for instance no comparisons are necessary within
	 * a single tree because the bipart nesting information is encoded in the tree itself.<br><br>
	 * 
	 * We record this information for two purposes: (1) to create MRCACHILDOF rels between the appropriate
	 * nodes. *All* nestedChildOf relationships need to be recorded as MRCACHILDOFs in the graph (including
	 * those for nodes within a single tree), and (2) to use for traversing paths to identify potential 
	 * additional bipartitions with different ingroup/outgroup composition (the findPaths procedure). The 
	 * bipartition-nesting information that we record for findPaths is limited to those biparts with
	 * non-identical taxonomic composition--no ingroup/outgroup augmentation is possible for the rest.
	 */
	
	@SuppressWarnings("unchecked")
	private void identifyNestedChildBiparts(List<Tree> trees) {
		
		// these will *not* be used by the findPaths procedure (but will be used to make MRCACHILDOFs)
		nestedParents = newSynchronizedIntegerSetList(bipart.size());
		nestedChildren = newSynchronizedIntegerSetList(bipart.size());
		compatibleBiparts = newSynchronizedIntegerSetList(bipart.size());

		// these will *only* be used by the findPaths procedure
		nestedAugmentingParents = newSynchronizedIntegerSetList(bipart.size());

		// this won't be used until the generateNodesFromBiparts procedure but we make it now for consistency
		analogousBiparts = newSynchronizedIntegerSetList(bipart.size());
		
		System.out.println("beginning allxall treewise bipart comparison to identify nested biparts for paths...");
		long z = new Date().getTime();

		List<Integer> treeIds = new ArrayList<Integer>();
		for (int i = 0; i < trees.size(); i++) { treeIds.add(i); }

		// parallel implementation for *synchronized* lists
		treeIds.parallelStream().forEach(i -> processBipartsForTree(i, trees));
		
		//doing the rest of the sums but taking advantage of the work from the processBiparts
		//TODO: can reduce this by half because you don't have to do 1 and 2 and 2 and 1
		HashMap<Integer,HashSet<Integer>> sdone = new HashMap<Integer,HashSet<Integer>> ();
		for(int s = 0; s<compatibleBiparts.size();s++){
			if(sdone.containsKey(s)==false)
				sdone.put(s, new HashSet<Integer>());
			System.out.println("summing bipart: "+s+" of "+compatibleBiparts.size() +" (set size "+compatibleBiparts.get(s).size()+")");
			for(Integer j: compatibleBiparts.get(s)){
				if(sdone.get(s).contains(j))
					continue;
				else{
					sdone.get(s).add(j);
					if(sdone.containsKey(j)==false){
						sdone.put(j,new HashSet<Integer>());
					}
					sdone.get(j).add(s);
				}
				LongBipartition newsum = testSum(bipart.get(s),bipart.get(j),observedOriginals);
				//System.out.println("\t"+newsum+" "+bipart.get(s)+" "+bipart.get(j));
				//LongBipartition newsum = tlb.strictSum(tls);
				if(newsum == null)
					continue;
				if(newsum.outgroup().size()==0)
					continue;
				if (! bipartId.containsKey(newsum)) {
					if(VERBOSE)
						System.out.println("generating new summed bipart "+newsum);
					bipart.add(newsum);
					nestedParents.add(Collections.synchronizedSet(new HashSet<Integer>()));
					nestedChildren.add(Collections.synchronizedSet(new HashSet<Integer>()));
					nestedAugmentingParents.add(Collections.synchronizedSet(new HashSet<Integer>()));
					analogousBiparts.add(Collections.synchronizedSet(new HashSet<Integer>()));
					int k = bipart.size() - 1;
					bipartId.put(newsum, k);
					summedBipartIds.add(k);
					ArrayList<Integer> pars = new ArrayList<Integer>();
					pars.add(s);pars.add(j);
					summedBipartSourceBiparts.put(k, pars);
				}
			}
		}sdone = null;//observedOriginals = null;compatibleBiparts=null; //garbage collection
		
		// parallel implementation for *synchronized* lists
		treeIds.parallelStream().forEach(i -> processSummedBipartsForTree(i, trees));
		
		// serial implementation -- keep for debugging
//		for (Integer i : treeIds) { processBipartsForTree(i, trees); }

		// parallel implementation for collecting sub-results and collating at the end. historical but keeping for now
/*		List<Object[]> bipartResults = treeIds.parallelStream()
				.map(i -> processBipartsForTree(i, trees))
				.collect(Collectors.toList()); */

		
		System.out.println(" done with allxall treewise bipart comparison. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
		
		/*
		 * we don't want to do this for the sums.
		 * instead we want to make the sums and keep the id of the bipart for each because
		 * the union of the nestedChildren and nestedParents are all we need , we don't have to do an 
		 * all by all. these will be at least sufficient and as a safety check you could check that all of these
		 * are actually correct
		 * the nodes subtending in the trees will take the bipart as parent and child as well
		 */
		System.out.print("starting allxall nestedchildof comparison for " + summedBipartIds.size() + " sum-result biparts...");
		z = new Date().getTime();
		for (Integer p : summedBipartIds) {
			ArrayList<Integer> parsbi = summedBipartSourceBiparts.get(p);
			LongBipartition tlp = bipart.get(p);
			//get the parent ids of these
			//get the intersection of the parets and add
			//TODO: make sure that we don't need augmented
			for(Integer pi: nestedParents.get(parsbi.get(0))){
				if(tlp.isNestedPartitionOf(bipart.get(pi))){
					nestedParents.get(p).add(pi);
					nestedChildren.get(pi).add(p);
					nestedAugmentingParents.get(p).add(pi);
				}
			}for(Integer pi: nestedParents.get(parsbi.get(1))){
				if(tlp.isNestedPartitionOf(bipart.get(pi))){
					nestedParents.get(p).add(pi);
					nestedChildren.get(pi).add(p);
					nestedAugmentingParents.get(p).add(pi);
				}
			}
		}
		
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");

	}
	
	/**
	 * This will walk through the implied graph of bipart nestings (i.e. the pairwise nestedChildOf relationships)
	 * and accumulates ingroup/outgroup taxa of child/parent nodes from trees with different taxon sets. Only nestedChildOf
	 * pairs from trees with non-overlapping taxon sets are used, because no two nodes from trees with identical taxon sets 
	 * could possibly augment one anothers' ingroup/outgroup composition.<br><br>
	 * 
	 * This procedure records all valid paths in the <tt>paths</tt> class member. It will attempt to follow all paths 
	 * through the bipart-nestings, but will stop under certain conditions such as when the ingroup of a new potential 
	 * bipart on a nested path contains something from the outgroup of a previous bipart on the path (which would be 
	 * illegal). The valid paths recorded will be used to generate nodes in the actual neo4j database in the next step.<br><br>
	 * 
	 * Here we considered just walking from each bipart mapped to a given tree node x, to only those biparts mapped to
	 * parents/children of x, but for one thing we would have to identify all the biparts mapped to each tree node beforehand
	 * (quadratic) and there should be cases where this would not find all the relevant paths, so we just do all of them.
	 * There are probably more efficient ways to do this.
	 */
	private void findAllPaths() {
				
		paths = Collections.synchronizedSet(new HashSet<Path>());
		System.out.print("traversing potential paths through the graph to determine node ingroup/outgroup composition...");
		long z = new Date().getTime();

		Set<LongBipartition> nodesWithoutPaths = Collections.synchronizedSet(new HashSet<LongBipartition>());
		
		// just make a list of bipart ids to iterate through
		List<Integer> bipartIds = new ArrayList<Integer>();
		for (int i = 0; i < bipart.size(); i++) { bipartIds.add(i); }
		
		bipartIds.parallelStream().forEach(i -> {
			
			System.out.println("paths: "+i+" / "+bipart.size()+" "+paths.size());
			if (bipart.get(i).outgroup().size() > 0) {
				MutableCompactLongSet pathResult = findPaths(i, new MutableCompactLongSet(), new ArrayList<Integer>(), 0,i);
				//System.out.println(pathResult);
				if (pathResult == null) {
					// biparts that are not nested in any others won't be saved in any of the paths, so we need to make graph nodes
					// for them now. we will need these nodes for mapping trees
					// actually, I thought none of these were null because itself would be in the path?
					nodesWithoutPaths.add(bipart.get(i));
				}
			}

		});
		
		Transaction tx = gdb.beginTx();
		for (LongBipartition b : nodesWithoutPaths) {
			createNode(b);
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
	}

	/**
	 * Walk back over all the valid paths that we found in the previous step, but this time create graph nodes at each
	 * position in the path.
	 */
	private void createNodesUsingPaths() {
		// create nodes based on paths
		System.out.print("now creating all lica nodes in the graph...");
		long z = new Date().getTime();
		Transaction tx = gdb.beginTx();
		System.out.println("on path:");
		int pn=0;
		for (Path p : paths) {
			System.out.println(pn+" / "+paths.size());
			generateNodesFromPaths(0, new MutableCompactLongSet(), p.toArray());
			//System.out.println(nestedChildren.size());
			pn++;
		}
		tx.success();
		tx.finish();
		for(int newBipartId = 0; newBipartId<bipart.size(); newBipartId++){
			HashSet<Integer> compareset1 = new HashSet<Integer>();
			HashSet<Integer> compareset2 = new HashSet<Integer>();
			for (int eq : analogousBiparts.get(newBipartId)) {
				compareset1.addAll(nestedParents.get(eq));
				compareset2.addAll(nestedChildren.get(eq));
			}
			LongBipartition newBipart = bipart.get(newBipartId);
			for (int pid : compareset1) {
				if(nestedParents.get(newBipartId).contains(pid) == false){
					if (newBipart.isNestedPartitionOf(bipart.get(pid))) {
						nestedParents.get(newBipartId).add(pid);
						nestedChildren.get(pid).add(newBipartId);
					}
				}
			}
			for (int cid : compareset2) {
				if(nestedChildren.get(newBipartId).contains(cid) == false){
					if (bipart.get(cid).isNestedPartitionOf(newBipart)) {
						nestedChildren.get(newBipartId).add(cid);
						nestedParents.get(cid).add(newBipartId);
					}
				}
			}
		}
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");

	}

	/**
	 * A parallel method to find nestedChildOf rels across input trees, so that many can be processed
	 * concurrently. The idea here is that we just process each tree independently, comparing it to all
	 * other trees, looking for all nestedChildOf rels where this tree could be a parent. For this, we can
	 * use the structure of the trees to stop short of doing a complete allxall comparison of tree nodes,
	 * which saves some time. We then perform a (seemingly unavoidable) allxall comparison of each tree's
	 * nodes against all the summed biparts. We store the resulting parent/child information in the form of
	 * a map where they keys are the bipart ids of child biparts and the values are sets of bipart ids for
	 * their parents.
	 * @param i
	 * @param trees
	 * @return
	 */
	private void processBipartsForTree(int i, List<Tree> trees) {
		// these are the temporary containers to store ids we find, we will return these so they can be combined
//		Map<Integer, Set<Integer>> nestedChildren = new HashMap<Integer, Set<Integer>>();
//		Map<Integer, Set<Integer>> nestedParents = new HashMap<Integer, Set<Integer>>();
//		Map<Integer, Set<Integer>> nestedAugmentingParents = new HashMap<Integer, Set<Integer>>();
		
		// first, walk from every node back to the root of the tree and record all
		// the ancestor-descendant nestedChildOf rels implied within the tree itself
		for (TreeNode c : trees.get(i).internalNodes(NodeOrder.POSTORDER)) {
			//if there are connectivity problems, this may need to be removed. though tests do not suggest that
			if (c.isTheRoot()) { continue; }
//			int cid = treeNodeIds.get(c);
			int cid = bipartId.get(bipartForTreeNode.get(c));
			for (TreeNode p = c.getParent(); ! p.isTheRoot(); p = p.getParent()) {
//				int pid = treeNodeIds.get(p);
				int pid = bipartId.get(bipartForTreeNode.get(p));
//				if (nestedParents.get(cid) == null) { nestedParents.put(cid, new HashSet<Integer>()); }
				nestedParents.get(cid).add(pid);  // add parent p of child c
				nestedChildren.get(pid).add(cid); // add child c of parent p
			}
		}

		Tree P = trees.get(i);
		List<TreeNode> pRootChildren = P.getRoot().getChildren(); // skip the root
		//make sure that we dont' have a tip as the child of the root
		pRootChildren.removeIf(isTip());

		// do the treewise comparisons between this tree and all other trees. here we just consider
		// whether this tree's nodes could be *parents* (not children) of other tree nodes. we'll 
		// visit each tree once, so the rels where this tree's nodes could be children we'll find later
		for (int j = 0; j < trees.size(); j++) {
			if (i == j) { continue; } // don't attempt to compare a tree to itself
							
			List<TreeNode> qRootChildren = trees.get(j).getRoot().getChildren(); // skip the root
			//make sure that we dont' have a tip as the child of the root
			qRootChildren.removeIf(isTip());

//			boolean treesHaveIdenticalTaxa = bipart.get(treeNodeIds.get(pRootChildren.get(0)))
//			         .hasIdenticalTaxonSetAs(bipart.get(treeNodeIds.get(qRootChildren.get(0))));
			
			boolean treesHaveIdenticalTaxa = bipartForTreeNode.get(pRootChildren.get(0))
					 .hasIdenticalTaxonSetAs(bipartForTreeNode.get(qRootChildren.get(0)));
			
			LinkedList<TreeNode> pStack = new LinkedList<TreeNode>();
			HashSet<TreeNode> pvisited = new HashSet<TreeNode>();
			for (pStack.addAll(pRootChildren); ! pStack.isEmpty(); ) {
				
				TreeNode p = pStack.pop();
				if (p.isExternal()) { continue; } // don't process tips
				if(pvisited.contains(p)) { continue;}else{pvisited.add(p);}
//				int pid = treeNodeIds.get(p);
				int pid = bipartId.get(bipartForTreeNode.get(p));
				LongBipartition bp = bipart.get(pid);
				ImmutableCompactLongSet pdeep = deepestNodeTaxa.get(p);
				//get the parent bipart for compatibel checking
				LongBipartition bpParent = bipart.get(bipartId.get(bipartForTreeNode.get(p.getParent())));


				LinkedList<TreeNode> qStack = new LinkedList<TreeNode>();
				HashSet<TreeNode> qvisited = new HashSet<TreeNode>();

				for (qStack.addAll(qRootChildren); ! qStack.isEmpty(); ) {
					
					TreeNode q = qStack.pop();
					if (q.isExternal()) { continue; } // don't process tips
					if(qvisited.contains(q)) { continue;}else{qvisited.add(q);}

//					int qid = treeNodeIds.get(q);
					int qid = bipartId.get(bipartForTreeNode.get(q));
					LongBipartition bq = bipart.get(qid);
					ImmutableCompactLongSet qshallow = shallowestNodeTaxa.get(p);
					
					//TODO: this needs to be tested or checked / meant to remove cycles but may not be succesful
					if (bq.isNestedPartitionOf(bp) && (qshallow.containsAll(pdeep) && qshallow.size() > pdeep.size())==false) {
						// record this nestedchildof relationship
//						if (nestedParents.get(qid) == null) { nestedParents.put(qid, new HashSet<Integer>()); }
						nestedParents.get(qid).add(pid);
						nestedChildren.get(pid).add(qid);
						if (! treesHaveIdenticalTaxa) {
//							if (nestedAugmentingParents.get(qid) == null) { nestedAugmentingParents.put(qid, new HashSet<Integer>()); }
							nestedAugmentingParents.get(qid).add(pid);
						}
					}
					LongBipartition bqParent = bipart.get(bipartId.get(bipartForTreeNode.get(q.getParent())));

					if(bq.isCompatibleWith(bp) && (bq.isCompatibleWith(bpParent) == false)
							&& (bp.isCompatibleWith(bqParent) == false)){
						compatibleBiparts.get(qid).add(pid);
					}
					
					if (bq.ingroup().containsAny(bp.ingroup())) {
						// if bq's children may be nested partitions of p
						for (TreeNode qc : q.getChildren()) { qStack.push(qc); }
					}
				}

				// add any children of p with ingroups that overlap with the qTree
				for (TreeNode qn : qRootChildren) {
					// if any child of the q root has taxa in p's ingroup, then we need to
					// add all children of p to the pStack so we can check them against tree Q
//					if (bp.ingroup().containsAny(bipart.get(treeNodeIds.get(qn)).ingroup())) {
					boolean pOverlapsWithTreeQ = false;
					if (qn.isExternal()) {
						if (bp.ingroup().contains(this.nodeIdForLabel.get(qn.getLabel()))) {
							pOverlapsWithTreeQ = true;
						}
					} else if (bp.ingroup().containsAny(bipartForTreeNode.get(qn).ingroup())) {
						pOverlapsWithTreeQ = true;
					}
					if (pOverlapsWithTreeQ) {
						for (TreeNode pc : p.getChildren()) { pStack.push(pc); }
					}
				}
			}
		}
		
		// we have now found all the nestedParents 
		System.out.println("finished tree biparts " + i);
//		return new Object[] { nestedParents, nestedAugmentingParents };
	}
	
	private void processSummedBipartsForTree(int i, List<Tree> trees) {
		Tree P = trees.get(i);
		// now do the all-by-all of this tree's nodes against the biparts from the bipart set sum
		//use to be in the processBipartsForTree
		for (TreeNode node : P.internalNodes(NodeOrder.PREORDER)) {
			if (node.isTheRoot()) { continue; }
		
//					Integer originalId = treeNodeIds.get(node);
			int originalId = bipartId.get(bipartForTreeNode.get(node));
			LongBipartition nodeBipart = bipart.get(originalId);
			
			// here we have to check in both directions, since we'll compare this tree to the summed biparts again
			//TODO: make sure that we don't need augmented
			for (Integer sid : summedBipartIds) {
				LongBipartition summedBipart = bipart.get(sid);
//						boolean identicalTaxa = summedBipart.hasIdenticalTaxonSetAs(nodeBipart);
				if (summedBipart.isNestedPartitionOf(nodeBipart)) {
//							if (nestedParents.get(originalId) == null) { nestedParents.put(originalId, new HashSet<Integer>()); }
					nestedParents.get(sid).add(originalId);
					nestedChildren.get(originalId).add(sid);
					//if (identicalTaxa) { nestedAugmentingParents.get(sid).add(originalId); }
				}
				if (nodeBipart.isNestedPartitionOf(summedBipart)) {
//							if (nestedParents.get(sid) == null) { nestedParents.put(sid, new HashSet<Integer>()); }
					nestedParents.get(originalId).add(sid);
					nestedChildren.get(sid).add(originalId);
					//if (identicalTaxa) { nestedAugmentingParents.get(originalId).add(sid); }
				}
			}
		}
		System.out.println("finished summed biparts " + i);
	}
	
	
	/*
	 * predicate used for removeIf with List<TreeNode>
	 */
	private static Predicate<TreeNode > isTip(){
		return p -> p.getChildCount() == 0;
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
/*		Map<LongBipartition, Set<LongBipartition>> nestedChildren = new HashMap<LongBipartition, Set<LongBipartition>>(); */
				
//		System.out.print("beginning path-based node traversal to identify possible MRCACHILDOF rels...");

/*		for (LongBipartition parent: nodeForBipart.keySet()) {

			Set<LongBipartition> children = nodeForBipart.keySet().parallelStream()
				.map( child -> { // map all biparts that can be nested within this parent
					if (child.isNestedPartitionOf(parent) && (! child.equals(parent))) { return child; }
					else { return null; }})
				.collect(() -> new HashSet<LongBipartition>(), (a,b) -> a.add(b), (a,b) -> a.addAll(b)).stream()
				.filter(r -> r != null).collect(toSet()); // filter nulls out of results

			nestedChildren.put(parent, children);
		}
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds"); */
		
		/* old code left here for convenience in case the parallel stream borks
		 * 
		Transaction tx = gdb.beginTx();
		for (LongBipartition parent: nodeForBipart.keySet()) {
			for (LongBipartition nestedChild: nodeForBipart.keySet()) { // potential nested
				if (nestedChild.isNestedPartitionOf(parent) && (! nestedChild.equals(parent))) {
					updateMRCAChildOf(nodeForBipart.get(nestedChild),nodeForBipart.get(parent));
				}
			}
		tx.success();
		tx.finish();
		} */
		
		// now create the rels. not trying to do this in parallel because concurrent db ops seem unwise. but we could try.
		System.out.println("identifying MRCACHILDOF rels (biparts: " +bipart.size()+","+ " nested children: " + nestedChildren.size() + ") to be created in the db...");
		long z = new Date().getTime();
//		for (LongBipartition parent : nestedChildren.keySet()) {
//			for (LongBipartition child : nestedChildren.get(parent)) {
		//TODO: wow! why is this so slow. I guess we are making tons of relationships but I wonder if we can just get them in a big 
		//		list
		
		// first make a data structure to remember which rels to create and a list of all bipart ids to iterate through
		Map<Node, Set<Node>> relsToCreate = Collections.synchronizedMap(new HashMap<Node, Set<Node>>());
		List<Integer> bipartIds = new ArrayList<Integer>();
		for (int parentId = 0; parentId < bipart.size(); parentId++) {
			bipartIds.add(parentId);
			relsToCreate.put(graphNodeForBipart.get(bipart.get(parentId)), Collections.synchronizedSet(new HashSet<Node>()));
		}
		
//		for (int parentId = 0; parentId < bipart.size(); parentId++) {
		bipartIds.parallelStream().forEach(parentId -> {
			System.out.println("for parent " + parentId + " there are " + nestedChildren.get(parentId).size() + " rels");
			Node parent = graphNodeForBipart.get(bipart.get(parentId));
			for (Integer childId : nestedChildren.get(parentId)) {
				Node child = graphNodeForBipart.get(bipart.get(childId));
				/*
				 * this null check is added because there are nodes that don't need to be created
				 * once they are updated so they don't get graph nodes. 
				 * if there are issues, there is commented code in the generateallnodesfrompaths
				 */
				if (child == null || parent == null) { continue; }
				if (child.equals(parent)) { continue; }

				relsToCreate.get(parent).add(child);
			}
		});
		
		int n = 0;
		for (Node parent : relsToCreate.keySet()) {
			n += relsToCreate.get(parent).size();
		}
		System.out.println("done. Identified " + n + " MRCACHILDOF rels to be created. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
		
		// now actually make the rels
		System.out.print("recording " + n + " rels in the db...");
		z = new Date().getTime();
		Transaction tx = gdb.beginTx();
		for (Node parent : relsToCreate.keySet()) {
			for (Node child : relsToCreate.get(parent)) {
				updateMRCAChildOf(child, parent);
			}
		}
		tx.success();
		tx.finish();
		System.out.println(" done recording MRCACHILDOF rels. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
		
		if (USING_TAXONOMY) {
			System.out.print("Also recording MRCACHILDOF rels for taxonomy["+taxonomyGraphNodesMap.keySet().size()+","+graphNodeForBipart.keySet().size()+"])...");
			//sequential for debugging right now
			// now create the rels. not trying to do this in parallel because concurrent db ops seem unwise. but we could try.
			tx = gdb.beginTx();
			//slower but works
			/*for (Node taxnd : taxonomyGraphNodesMap.keySet()) {
				System.out.println(taxnd);
				LongBipartition taxbp = taxonomyGraphNodesMap.get(taxnd);
				for (LongBipartition ndbp: graphNodeForBipart.keySet()) {
					if(graphNodeForBipart.get(ndbp).equals(taxnd))
						continue;
					LongBipartition ndbpExp = getExpandedTaxonomyBipart(ndbp);
					if(taxbp.ingroup().containsAny(ndbpExp.ingroup())==false)
						continue;
					//check as parent
					if (taxbp.ingroup().containsAll(ndbpExp.ingroup())){//(ndbp.ingroup())){
						if(taxbp.ingroup().containsAny(ndbpExp.outgroup()))//(ndbp.outgroup()))
							updateMRCAChildOf(graphNodeForBipart.get(ndbp), taxnd);
						//else if(taxbp.ingroup().size() > ndbp.ingroup().size())
						//	updateMRCAChildOf(nodeForBipart.get(ndbp), taxnd);
					}else if(ndbpExp.ingroup().containsAny(taxbp.ingroup())//(ndbp.ingroup().containsAny(taxbp.ingroup())
							&& taxbp.ingroup().containsAny(ndbpExp.outgroup())==false && //&& taxbp.ingroup().containsAny(ndbp.outgroup())==false &&
							taxbp.ingroup().containsAll(ndbpExp.ingroup()) == false ){//taxbp.ingroup().containsAll(ndbp.ingroup()) == false ){//check as child;
						updateMRCAChildOf(taxnd, graphNodeForBipart.get(ndbp));
					}
				}
			}*/
			
			//faster, needs more testing
			
			for(LongBipartition ndbp: graphNodeForBipart.keySet()){
				LongBipartition ndbpExp = getExpandedTaxonomyBipart(ndbp);
				Long ndid = ((long[])graphNodeForBipart.get(ndbp).getProperty("mrca"))[0];
				Node taxNode = gdb.getNodeById(ndid);
				while (taxNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
					taxNode = getParentTaxNode(taxNode);
					if (taxonomyGraphNodesMap.keySet().contains(taxNode)){
						LongBipartition taxbp = taxonomyGraphNodesMap.get(taxNode);
						if (taxbp.ingroup().containsAll(ndbpExp.ingroup())){
							if(taxbp.ingroup().containsAny(ndbpExp.outgroup())) {//check as parent;
								updateMRCAChildOf(graphNodeForBipart.get(ndbp), taxNode);
								while (taxNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
									taxNode = getParentTaxNode(taxNode);
									updateMRCAChildOf(graphNodeForBipart.get(ndbp), taxNode);
								}
								break;
							}else if(taxbp.ingroup().containsAny(ndbpExp.outgroup()) == false){//check as equivalent
								//get children of ndbp and connect them
								for (Integer childId : nestedChildren.get(bipartId.get(ndbp))) {
									if(taxbp.ingroup().containsAll(bipart.get(childId).ingroup()) &&
											taxbp.ingroup().containsAny(bipart.get(childId).outgroup())){
										Node child = graphNodeForBipart.get(bipart.get(childId));
										if(child == null)
											continue;
										updateMRCAChildOf(child,taxNode);
									}
								}//get parents of ndbp
								for (Integer parentId : nestedParents.get(bipartId.get(ndbp))) {
									if(bipart.get(parentId).ingroup().containsAny(taxbp.ingroup()) &&
											bipart.get(parentId).outgroup().containsAny(taxbp.ingroup())== false &&
											taxbp.ingroup().containsAll(bipart.get(parentId).ingroup())==false){
										Node parent = graphNodeForBipart.get(bipart.get(parentId));
										if(parent == null)
											continue;
										updateMRCAChildOf(taxNode,parent);
									}
								}								
							}
						}else if(ndbpExp.ingroup().containsAny(taxbp.ingroup())
								&& taxbp.ingroup().containsAny(ndbpExp.outgroup())==false && 
								taxbp.ingroup().containsAll(ndbpExp.ingroup()) == false ){//check as child;
							updateMRCAChildOf(taxNode, graphNodeForBipart.get(ndbp));
							
						}
					}
				}
			}
			
			tx.success();
			tx.finish();
			System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
		}

	}
	
	/**
	 * 
	 */
	private void populateTaxonomyGraphNodesMap(List<Tree> trees){
		JadeNode root =null;
		boolean firstTimeThrough = true;
		taxonomyGraphNodesMap = new HashMap<Node,LongBipartition>();
		HashMap<Node,JadeNode> taxTreeMap = new HashMap<Node,JadeNode>();
		for(Tree tree: trees){
			for(TreeNode treeLf : tree.getRoot().getDescendantLeaves()){
				Node taxNode = gdb.getNodeById(nodeIdForLabel.get(treeLf.getLabel()));
				JadeNode lastNode = null;
				while (taxNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
					taxNode = getParentTaxNode(taxNode);
					if (taxonomyGraphNodesMap.containsKey(taxNode)){
						if(lastNode != null)
							taxTreeMap.get(taxNode).addChild(lastNode);
						break;
					}
					MutableCompactLongSet taxonIngroup = new MutableCompactLongSet((long[]) taxNode.getProperty(NodeProperty.MRCA.propertyName));
					LongBipartition taxonBipart = new ImmutableLongBipartition(taxonIngroup, new MutableCompactLongSet()); // made immutable
					taxonomyGraphNodesMap.put(taxNode, taxonBipart);
					JadeNode newNode = new JadeNode();
					newNode.assocObject("bipart", taxonBipart);
					newNode.assocObject("dbnode", taxNode);
					newNode.setName(String.valueOf(taxNode.getId()));
					taxTreeMap.put(taxNode, newNode);
					if(lastNode != null)
						newNode.addChild(lastNode);
					lastNode = newNode;
				}
				if(firstTimeThrough){
					firstTimeThrough = false;
					root = lastNode;
				}
			}
		}
		taxonomyJadeRoot = root;
		System.out.println("gathered "+taxonomyGraphNodesMap.size()+" taxonomy nodes for additional mapping");
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
            LongBipartition rootBipart = getGraphBipartForTreeNode(root, tree);
			//need to expand the rootBipart for the searching

            HashSet<Node> graphNodes = new HashSet<Node>();
            if (subset == false) {//subset assumes you are connecting the taxonomy because you subset at taxonomy
                for (LongBipartition b : graphNodeForBipart.keySet()) {
                    if (b.containsAll(rootBipart)) {
                        System.out.println("mapping root to " + graphNodeForBipart.get(b));
                        graphNodes.add(graphNodeForBipart.get(b));
                    }
                }
            }
            if (USING_TAXONOMY) {
                LongBipartition rootBipartExp = getExpandedTaxonomyBipart(rootBipart);
                /* trying the more intelligent one below
                 * for(Node b : taxonomyGraphNodesMap.keySet()){
                 if(taxonomyGraphNodesMap.get(b).containsAll(rootBipartExp)){ //had been rootBipart
                 graphNodes.add(b);
                 }
                 }*/
                /*
                 * for the sake of speed, lets remove all but the shallowest
                 * can add those rels later
                 */
                Long ndid = rootBipartExp.ingroup().toArray()[0];
                Node taxNode = gdb.getNodeById(ndid);
                while (taxNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
                    taxNode = getParentTaxNode(taxNode);
                    if (taxonomyGraphNodesMap.keySet().contains(taxNode)) {
                        LongBipartition taxbp = taxonomyGraphNodesMap.get(taxNode);
                        if (taxbp.ingroup().containsAll(rootBipartExp.ingroup())) {
                            graphNodes.add(taxNode);
                            /*while (taxNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
                             taxNode = getParentTaxNode(taxNode);
                             graphNodes.add(taxNode);
                             }*/
                            break;
                        }
                    }
                }

                if (VERBOSE) {
                    System.out.println(root.getNewick(false) + " " + graphNodes);
                }
            }

            if (graphNodes.size() < 1) {
                if (VERBOSE) {
                    System.out.println("could not find a suitable node to map to the root, will make a new one\n\t" + rootBipart);
                }
                Node rootNode = createNode(rootBipart);
                graphNodes.add(rootNode);

                // create necessary MRCACHILDOF rels for newly created nodes
                for (LongBipartition b : graphNodeForBipart.keySet()) {
                    if (rootBipart.isNestedPartitionOf(b)) {
                        rootNode.createRelationshipTo(graphNodeForBipart.get(b), RelType.MRCACHILDOF);
                    }
                    if (b.isNestedPartitionOf(rootBipart)) {
                        graphNodeForBipart.get(b).createRelationshipTo(rootNode, RelType.MRCACHILDOF);
                    }
                }
            }
            /*
             * store the root nodes in the index
             * these can be used later for connectivity between subsets
             */
            Index<Node> ottIdIndex = gdb.getNodeIndex("sourceTreeRoots", "type", "exact", "to_lower_case", "true");
            Index<Node> ottIdIndexss = gdb.getNodeIndex("sourceTreeRootsSubsets", "type", "exact", "to_lower_case", "true");

            for (Node gn : graphNodes) {
                ottIdIndex.add(gn, "source", String.valueOf(sourceForTreeNode.get(root)));
                //add a property for the subset
                if (subsetTipInfo != null) {
                    if (subsetTipInfo.containsKey(root)) {
                        String subset = subsetTipInfo.get(root);
                        ottIdIndexss.add(gn, "subset", String.valueOf(sourceForTreeNode.get(root) + subset));
                    }
                }
            }

            tx.success();
            tx.finish();

            graphNodesForTreeNode.put(root, graphNodes);
        }
    }
	
	/**
	 * This is to get the expanded taxonomy bipartition after it has been reduced
	 * This is used for creating nodes and for checking against taxonomy nodes
	 * @param inbipart
	 * @return
	 */
	private LongBipartition getExpandedTaxonomyBipart(LongBipartition inbipart){
		MutableCompactLongSet fullsetin = new MutableCompactLongSet();
		MutableCompactLongSet fullsetout = new MutableCompactLongSet();

		HashMap<Object,MutableCompactLongSet> toexpandingroup = new HashMap<Object,MutableCompactLongSet>();
		HashMap<Object,MutableCompactLongSet> toexpandoutgroup = new HashMap<Object,MutableCompactLongSet>();
		for(Long s: inbipart.ingroup()){
			if(shrunkSet.containsKey(labelForNodeId.get(s))){
				MutableCompactLongSet tempset = new MutableCompactLongSet();
				for(Object x: shrunkSet.get(labelForNodeId.get(s))){
					tempset.add(nodeIdForLabel.get(x));
				}
				toexpandingroup.put(labelForNodeId.get(s),tempset);
			}else{
				fullsetin.add((Long) s);
			}
		}
		for(Long s: inbipart.outgroup()){
			if(shrunkSet.containsKey(labelForNodeId.get(s))){
				MutableCompactLongSet tempset = new MutableCompactLongSet();
				for(Object x: shrunkSet.get(labelForNodeId.get(s))){
					tempset.add(nodeIdForLabel.get(x));
				}
				toexpandoutgroup.put(labelForNodeId.get(s),tempset);
			}else{
				fullsetout.add((Long) s);
			}
		}
		//check for overlap
		for(Object tip: toexpandingroup.keySet()){
			toexpandingroup.get(tip).removeAll(fullsetout);
			for(Object tip2: toexpandoutgroup.keySet()){
				//tip2 contained within tip
				if(shrunkSet.get(tip).containsAll(shrunkSet.get(tip2))){
					toexpandingroup.get(tip).removeAll(toexpandoutgroup.get(tip2));
				}
			}
			fullsetin.addAll(toexpandingroup.get(tip));
		}for(Object tip: toexpandoutgroup.keySet()){
			toexpandoutgroup.get(tip).removeAll(fullsetin);
			for(Object tip2: toexpandingroup.keySet()){
				//tip2 contained within tip
				if(shrunkSet.get(tip).containsAll(shrunkSet.get(tip2))){
					toexpandoutgroup.get(tip).removeAll(toexpandingroup.get(tip2));
				}
			}fullsetout.addAll(toexpandoutgroup.get(tip));	
		}
		
		/*
		//nned to expand for the taxonomy search
		CompactLongSet fullsetin = new CompactLongSet();
		CompactLongSet fullsetout = new CompactLongSet();
		HashMap<Object,CompactLongSet> toexpandingroup = new HashMap<Object,CompactLongSet>();
		HashMap<Object,CompactLongSet> toexpandoutgroup = new HashMap<Object,CompactLongSet>();

		for(Long s: inbipart.ingroup()){
			if(shrunkSet.containsKey(labelForNodeId.get(s))){
				CompactLongSet tempset = new CompactLongSet();
				for(Object x: shrunkSet.get(labelForNodeId.get(s))){
					tempset.add(nodeIdForLabel.get(x));
				}
				toexpandingroup.put(labelForNodeId.get(s),tempset);
			}else{
				fullsetin.add((Long) s);
			}
		}for(Object tip: toexpandingroup.keySet()){
			fullsetin.addAll(toexpandingroup.get(tip));
		}for(Long s:inbipart.outgroup()){
			if(shrunkSet.containsKey(labelForNodeId.get(s))){
				CompactLongSet tempset = new CompactLongSet();
				for(Object x: shrunkSet.get(labelForNodeId.get(s))){
					tempset.add(nodeIdForLabel.get(x));
				}
				toexpandoutgroup.put(labelForNodeId.get(s),tempset);
			}else{
				fullsetout.add((Long) s);
			}
		}for(Object tip: toexpandoutgroup.keySet()){
			fullsetout.addAll(toexpandoutgroup.get(tip));
		}*/
		LongBipartition retBipart = new ImmutableLongBipartition(fullsetin,fullsetout); // made immutable
		//if(VERBOSE)
		//	System.out.println(inbipart+" -> "+retBipart);
		return retBipart;
	}
	

	private void mapNonRootNodes(List<Tree> trees) {
		
		for (int k = 0; k < trees.size(); k++) {
			Tree tree = trees.get(k);

			System.out.print("mapping tree " + k + " (recording rels in the db)...");
			long z = new Date().getTime();

			Transaction tx = gdb.beginTx(); // one transaction to rule the tree
			Map<TreeNode, Integer> edgeIdForTreeNode = new HashMap<TreeNode, Integer>();

			// now map the internal nodes other than the root
			int i = 0;
			for (TreeNode treeNode : tree.internalNodes(NodeOrder.PREORDER)) {
				if (! treeNode.isTheRoot()) {
					
					// give each node (its parent edge actually) a unique id, which is used for synth
					int edgeId = ++i;
					edgeIdForTreeNode.put(treeNode, edgeId);
					
					Set<Node> graphNodes = mapGraphNodes(treeNode, tree, edgeId, false);
					if (graphNodes.size() == 0) { // every internal node must map to at least one node in the graph.
						System.out.println("could not map node: " + treeNode.getNewick(false)+ ".\ngraphNodes: " + graphNodes);
						throw new AssertionError();
					}
					graphNodesForTreeNode.put(treeNode, (HashSet<Node>) graphNodes);
				}
			}

			// now connect all the tips to their parent nodes
			for (TreeNode treeTip : tree.externalNodes()) {
				
				// give each node (its parent edge actually) a unique id, which is used for synth
				int edgeId = ++i;
				edgeIdForTreeNode.put(treeTip, edgeId);

				//this will map to compatible "internal" graph nodes
				mapGraphNodes(treeTip, tree, edgeIdForTreeNode.get(treeTip), true);
				//this will map to terminal graph nodes directly from the tips
				Node tip = gdb.getNodeById(nodeIdForLabel.get(treeTip.getLabel()));
				LongBipartition lb = getExpandedTaxonomyBipart(getGraphBipartForTreeNode(treeTip,tree));
				for (Node parent : graphNodesForTreeNode.get(treeTip.getParent())){
					if(tip.equals(parent))
						continue;
					updateMRCAChildOf(tip, parent);
					updateSTREEChildOf(tip,parent,sourceForTreeNode.get(treeTip),rankForTreeNode.get(treeTip), 
							edgeId,lb,true);
					/*
					 * now we want to map to the "deepest" taxon node before the split and the 
					 * 	nodes in between. so amborella,aster split will map to aster, asteraceae, asterales, etc.
					 */
					//TODO: this has an error in it when the clade is not monophyletic
					if(mapdeepest == true){
						LongBipartition nodeBipartExp = bipartForTreeNodeExploded.get(treeTip.getParent());
						MutableCompactLongSet alsoExclude = new MutableCompactLongSet();
						//need to make sure that this doesn't overlap with the sisters
						for(TreeNode othertip: treeTip.getParent().getChildren()){
							//System.out.println(othertip+" "+treeTip+" "+othertip.equals(treeTip));
							if(othertip.equals(treeTip))
								continue;
							if(othertip.isExternal()){
								alsoExclude.add(nodeIdForLabel.get(othertip.getLabel()));
							}else{
								alsoExclude.addAll(bipartForTreeNodeExploded.get(othertip).ingroup());
							}
						}
						
						Node startTip = tip;
						//System.out.println("mappeddeepest: "+startTip);
						boolean going = true;
						while(going == true){
							startTip = startTip.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
							//System.out.println("mappeddeepest next: "+startTip);

							if(taxonomyGraphNodesMap.containsKey(startTip)){
								//System.out.println("mappeddeepest contained: "+startTip);

								LongBipartition pbip = null;
								LongBipartition cbip = taxonomyGraphNodesMap.get(startTip);
								//System.out.println("mappeddeepest cbip: "+cbip);

								if(bipartForGraphNodeExploded.containsKey(parent)){
									pbip = bipartForGraphNodeExploded.get(parent);
								}else{
									pbip = taxonomyGraphNodesMap.get(parent);
								}
								//System.out.println("mappeddeepest pbip: "+pbip);
								//System.out.println("node parent bipart: "+nodeBipartExp);
								//System.out.println("cbip ingroup "+cbip.ingroup()+" "+alsoExclude);
								//System.out.println("cbip ingroup "+cbip.ingroup().containsAll(alsoExclude));
								if(cbip.ingroup().containsAny(nodeBipartExp.outgroup())){
									break;
								}if(cbip.ingroup().containsAny(alsoExclude)){
									break;
								}
								
								if(pbip.ingroup().containsAny(cbip.ingroup())
										&& cbip.ingroup().containsAny(pbip.outgroup()) == false
										&& cbip.ingroup().containsAll(pbip.ingroup()) == false){//it is compatible with the parent
									updateMRCAChildOf(tip, startTip);
									updateSTREEChildOf(tip,startTip,sourceForTreeNode.get(treeTip),rankForTreeNode.get(treeTip), 
											edgeId,lb,true);
									if(startTip.equals(parent))
										continue;
									//System.out.println("mappeddeepest: yup "+startTip+" "+parent);
									updateMRCAChildOf(startTip, parent);
									updateSTREEChildOf(startTip,parent,sourceForTreeNode.get(treeTip),rankForTreeNode.get(treeTip), 
											edgeId,lb,true);
								}else{
									going = false;
									break;
								}
							}else{
								break;
							}
						}
					}
				}
			}
			/*
			 * for nodes that are subset, we also connect to the roots of the nested sets
			 * 	to those subset tips
			 * this information is stored in the tips as subset and in the ottIdIndexss
			 */
			if(subsetTipInfo != null){
				for(TreeNode treeTip: tree.externalNodes()){

					int edgeId = edgeIdForTreeNode.get(treeTip);
					
					if(subsetTipInfo.containsKey(treeTip)){
						Index<Node> ottIdIndexss = gdb.getNodeIndex("sourceTreeRootsSubsets", "type", "exact", "to_lower_case", "true");
						IndexHits<Node> hitroots= ottIdIndexss.get("subset", sourceForTreeNode.get(treeTip)+subsetTipInfo.get(treeTip));
						for(Node tip:hitroots){
							for (Node parent : graphNodesForTreeNode.get(treeTip.getParent())){
								if(tip.equals(parent))
									continue;
								updateMRCAChildOf(tip, parent);
								updateSTREEChildOf(tip,parent,sourceForTreeNode.get(treeTip),rankForTreeNode.get(treeTip), edgeId,null,false);
							}
						}
					}
				}
			}
			// TODO add tree metadata node
			
			tx.success();
			tx.finish();
			System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
			// CHECKING FOR CYCLES NOW
			//TopologicalOrder to = new TopologicalOrder(gdb, new HashSet<Relationship>(),RelType.STREECHILDOF);
			//for(Node n: to){
				
			//}
			//
			
			
		}	
		System.out.println("all trees have been mapped.");
	}
	
	private Set<Node> mapGraphNodes(TreeNode treeNode, Tree tree, int edgeId, boolean external) {
		// get the graph nodes that match this node's parent
		HashSet<Node> graphNodesForParent = graphNodesForTreeNode.get(treeNode.getParent());
		
		// get the graph nodes that match this node
		LongBipartition nodeBipart;
		LongBipartition nodeBipartExp=null;
		
//		if (external == false) { nodeBipart = original[treeNodeIds.get(treeNode)]; }
		if (external == false) { 
			nodeBipart = bipartForTreeNode.get(treeNode);
			int bpid = bipartId.get(nodeBipart);
			if(USING_TAXONOMY)
				nodeBipartExp = bipartForTreeNodeExploded.get(treeNode);
		}else { 
			nodeBipart = getGraphBipartForTreeNode(treeNode,tree); 
			if(USING_TAXONOMY)
				nodeBipartExp = getExpandedTaxonomyBipart(nodeBipart);
		}
		//System.out.println(treeNode.getNewick(false)+" "+rankForTreeNode.get(treeNode));
		//System.out.println("\t"+nodeBipart);
		//System.out.println("\t"+graphNodesForParent);

		HashSet<Node> graphNodes = new HashSet<Node>();
		HashSet<Node> taxNodesMatched = new HashSet<Node>();
		
		// if you create the mrcachildofs before, then you can do this
		for (Node parent : graphNodesForParent){
			//System.out.println(parent);
			for (Relationship r: parent.getRelationships(Direction.INCOMING, RelType.MRCACHILDOF)){
				//System.out.println(" "+r);
				Node potentialChild = r.getStartNode();
				LongBipartition childBipart;
				LongBipartition childBipartExp=null;
				LongBipartition nodeParent = bipartForTreeNode.get(treeNode.getParent());
				LongBipartition nodeParentExp = null;
				if(USING_TAXONOMY)
					nodeParentExp = bipartForTreeNodeExploded.get(treeNode.getParent());

				if(USING_TAXONOMY == false || taxonomyGraphNodesMap.containsKey(potentialChild)==false){
					childBipart = bipartForGraphNode.get(potentialChild);
				}else{
					childBipart = taxonomyGraphNodesMap.get(potentialChild);
					childBipartExp = childBipart;
				}
				if(childBipart == null)
					continue;
				if(USING_TAXONOMY){
					if(taxonomyGraphNodesMap.containsKey(potentialChild)==false && taxonomyGraphNodesMap.containsKey(parent)){
						childBipartExp = bipartForGraphNodeExploded.get(potentialChild);	
					}
				}
				//System.out.println("\t\t"+potentialChild+"\t"+childBipart+"\t"+nodeBipart);
				//System.out.println("\t\t\t"+taxonomyGraphNodesMap.containsKey(parent)+" "+taxonomyGraphNodesMap.containsKey(potentialChild));
				if(USING_TAXONOMY && taxonomyGraphNodesMap.containsKey(parent)){
					if(taxonomyGraphNodesMap.containsKey(potentialChild)){
						if(parent.equals(potentialChild) == false && 
								childBipartExp.ingroup().containsAll(nodeBipartExp.ingroup()) && 
								childBipartExp.ingroup().containsAny(nodeBipartExp.outgroup())==false &&
								taxonomyGraphNodesMap.get(parent).ingroup().containsAll(childBipartExp.ingroup())){
							graphNodes.add(potentialChild);
							taxNodesMatched.add(potentialChild);
							updateSTREEChildOf(potentialChild,parent,sourceForTreeNode.get(treeNode), rankForTreeNode.get(treeNode), edgeId,
									nodeBipartExp,external);
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
									LongBipartition tchb = taxonomyGraphNodesMap.get(tch);
									if(parent.equals(tch) == false && 
											tchb.ingroup().containsAll(nodeBipartExp.ingroup()) && 
											tchb.ingroup().containsAny(nodeBipartExp.outgroup())==false &&
											taxonomyGraphNodesMap.get(parent).ingroup().containsAll(tchb.ingroup())){
										graphNodes.add(tch);
										taxNodesMatched.add(tch);
										updateSTREEChildOf(tch,curchild,sourceForTreeNode.get(treeNode), rankForTreeNode.get(treeNode), 
												edgeId,nodeBipartExp,external);
										going = true;
										curchild = tch;
										break;
									}
								}
							}
						}
					}else{
						if(childBipartExp.containsAll(nodeBipartExp) &&
								taxonomyGraphNodesMap.get(parent).ingroup().containsAll(childBipartExp.ingroup()) &&
								taxonomyGraphNodesMap.get(parent).ingroup().containsAny(childBipartExp.outgroup()) //){
								&& childBipartExp.isNestedPartitionOf(nodeParentExp)){
							graphNodes.add(potentialChild);
							updateSTREEChildOf(potentialChild,parent,sourceForTreeNode.get(treeNode), rankForTreeNode.get(treeNode), 
									edgeId,nodeBipartExp,external);
						}
					}
				}else if (USING_TAXONOMY && taxonomyGraphNodesMap.containsKey(potentialChild)){
					if(childBipartExp.ingroup().containsAll(nodeBipartExp.ingroup()) && 
							childBipartExp.ingroup().containsAny(nodeBipartExp.outgroup())==false){
						graphNodes.add(potentialChild);
						taxNodesMatched.add(potentialChild);
						updateSTREEChildOf(potentialChild,parent,sourceForTreeNode.get(treeNode), rankForTreeNode.get(treeNode), 
								edgeId,nodeBipartExp,external);
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
								LongBipartition tchb = taxonomyGraphNodesMap.get(tch);
								if(tchb.ingroup().containsAll(nodeBipartExp.ingroup()) && 
										tchb.ingroup().containsAny(nodeBipartExp.outgroup())==false){
									graphNodes.add(tch);
									taxNodesMatched.add(tch);
									updateSTREEChildOf(tch,curchild,sourceForTreeNode.get(treeNode), rankForTreeNode.get(treeNode), 
											edgeId,nodeBipartExp,external);
									going = true;
									curchild = tch;
									break;
								}
							}
						}
					}
				}else{
					//if neither is taxonomy then it should be in nestedParents bit
					// BE CAREFUL containsAll is directional
					LongBipartition testParent = bipartForGraphNode.get(parent);
					/*int cpid = bipartId.get(childBipart);
					int ppid = bipartId.get(testParent);
					if(nestedParents.get(cpid).contains(ppid)==false)
						continue;
					*/
					if (childBipart.containsAll(nodeBipart)  
							&& childBipart.isNestedPartitionOf(nodeParent)){//testParent)){
						graphNodes.add(potentialChild);
						updateSTREEChildOf(potentialChild,parent,sourceForTreeNode.get(treeNode), rankForTreeNode.get(treeNode), 
								edgeId,nodeBipartExp,external);
					}	
				}
				//System.out.println("\t\t"+graphNodes);
			}
		}
		//connect equivalent tax nodes to the nodes
		for(Node gn: taxNodesMatched){
			for (Relationship trel: gn.getRelationships(Direction.INCOMING, RelType.MRCACHILDOF)){
				if (graphNodes.contains(trel.getStartNode())){
					updateSTREEChildOf(trel.getStartNode(),gn,sourceForTreeNode.get(treeNode), rankForTreeNode.get(treeNode), 
							edgeId,nodeBipartExp,external);
				}
			}
		}
		
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
	 * 
	 * @param parentId
	 * @param nested
	 * @param path
	 * @param level
	 * @param originalParentId
	 * @return
	 */
	private MutableCompactLongSet findPaths(int parentId, MutableCompactLongSet cumulativeIngroup, ArrayList<Integer> path, int level, int originalParentId) {

		LongBipartition parent = bipart.get(parentId);
		
		// three cases where we exit without defining a node:
		if (/*hasNoParentBiparts(parentId) || 					// found a dead end path */
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
		MutableCompactLongSet cumulativeOutgroup = new MutableCompactLongSet(parent.outgroup());
	    boolean newline = false;
	    for (int nextParentId : nestedAugmentingParents.get(parentId)) {
			if (path.contains(nextParentId)) { continue; }
			//TODO: CHECK THESE TWO BITS. THEY WORK IN PYTHON BUT STILL NEED MORE TESTING
			//		DELETE IF THERE ARE PROBLEMS
			//can continue if we already have all the outgroup and ingroup in here
			MutableCompactLongSet testset = new MutableCompactLongSet(bipart.get(nextParentId).ingroup());
			testset.addAll(bipart.get(nextParentId).outgroup());
			testset.removeAll(cumulativeIngroup);
			testset.removeAll(cumulativeOutgroup);
			if (testset.size() == 0){continue;}
			//can continue if the nextid isn't in the original one because that means original bipart on this path can't be a nested child of the next parent
			if(nestedAugmentingParents.get(originalParentId).contains(nextParentId)==false){continue;}
			MutableCompactLongSet outgroupsToAdd = findPaths(nextParentId, new MutableCompactLongSet(cumulativeIngroup), path, level+1, originalParentId);
			if (outgroupsToAdd != null) { // did not encounter a dead end path
		    	newline = true;
				cumulativeOutgroup.addAll(outgroupsToAdd);
			}
		}
	    
	    if (VERBOSE) {
			System.out.println((newline ? "\n" : "") + indent(level) + "cumulative outgroup is: " + cumulativeOutgroup.toString(labelForNodeId));
		    System.out.println(indent(level) + "done with parent " + parent.toString(labelForNodeId));
			System.out.println(indent(level) + "Implied augmented bipartition (not creating this yet): " + cumulativeIngroup.toString(labelForNodeId) + " | " + cumulativeOutgroup.toString(labelForNodeId) + " based on path " + path);
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
	private Object[] generateNodesFromPaths(int position, MutableCompactLongSet cumulativeIngroup, int[] path) {

		int parentId = path[position];
		LongBipartition parent = bipart.get(parentId);

		// three cases where we exit without making a node:
		if (/*hasNoParentBiparts(parentId) || 					// found a dead end path*/
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
		MutableCompactLongSet cumulativeOutgroup = new MutableCompactLongSet(parent.outgroup());

	    // perform the recursion down all the childen, returning each child node (once it has been created) along
	    // with its outgroups. using an Object[] as a return value to provide two object in the return is weird.
	    // Should update this to use a private class container for the return value or figure out something else.
	    boolean newline = false;
	    List<Integer> ancestorsOnPath = new ArrayList<Integer>();
		if (position < path.length - 1) {
			Object[] result = generateNodesFromPaths(position+1, new MutableCompactLongSet(cumulativeIngroup), path);
			if (result != null) {
				ancestorsOnPath = (List<Integer>) result[0];
				MutableCompactLongSet outgroupsToAdd = (MutableCompactLongSet) result[1];
				cumulativeOutgroup.addAll(outgroupsToAdd);
				newline = true;
			}
		}

		if (VERBOSE) {
			System.out.println((newline ? "\n" : "") + indent(position) + "cumulative outgroup is: " + cumulativeOutgroup.toString(labelForNodeId));
		    System.out.println(indent(position) + "done with parent " + parent.toString(labelForNodeId));
		}
		
		assert ! cumulativeIngroup.containsAny(cumulativeOutgroup);

		LongBipartition newBipart = new ImmutableLongBipartition(cumulativeIngroup, cumulativeOutgroup); // made immutable

		// TODO we should probably not be creating nodes yet. we should really just record which nodes need to be created 
		// (which we could do in parallel) and then do batch processing to create them all afterward. and then make the rels.
		if (! graphNodeForBipart.containsKey(newBipart)) {
			if (VERBOSE) { System.out.println(indent(position) + "Found a new bipartition based on path " + Arrays.toString(path) + ".\n"
					+ "Creating a node for new augmented bipart: " + cumulativeIngroup.toString(labelForNodeId) + " | " + cumulativeOutgroup.toString(labelForNodeId)); }
			createNode(new ImmutableLongBipartition(cumulativeIngroup, cumulativeOutgroup)); // made immutable
			if (VERBOSE) { System.out.println(); }
		}
		
		// apparently we also need to ensure that a node exists for bipart from the path node
		// WARNING: I am not sure why we need to do this here. I thought all original/summed
		// biparts would have nodes created for them before this...
		// THIS HAS BEEN COMMENTED OUT as it is not needed. If a node is not getting created, 
		// it shouldn't be here that it is created and instead should be somewhere else, after
		// finding paths and before generating would be my guess
		if (! graphNodeForBipart.containsKey(parent)) {
			if(VERBOSE)
				System.out.println("Found a preexisting bipartition without a db node. Creating a node for:\n" + parent);
			//createNode(parent);
		}

		int newBipartId;
		if (bipartId.containsKey(newBipart)) {
			newBipartId = bipartId.get(newBipart);
		} else { // this is a new bipart, so we need to record it and update the data structures
			bipart.add(newBipart);
			newBipartId = bipart.size() - 1;
			bipartId.put(newBipart, newBipartId);
			generatedBipartIds.add(newBipartId);
			analogousBiparts.add(new HashSet<Integer>());
			nestedChildren.add(new HashSet<Integer>());
			nestedParents.add(new HashSet<Integer>());
			// have to make sure keep indices consistent across all arrays/hashsets
			assert newBipartId == analogousBiparts.size() - 1;
			assert newBipartId == nestedChildren.size() - 1;
			assert newBipartId == nestedParents.size() - 1;
		}
		
		
		for (int pid : ancestorsOnPath) {
			// don't need to check for nested status here, we know the new bipart is
			// nested in all ancestors we've generated along this path
			nestedParents.get(newBipartId).add(pid);
			nestedChildren.get(pid).add(newBipartId);

			// also add each newly generated ancestor as a parent of the path node itself
			nestedParents.get(parentId).add(pid);
			nestedChildren.get(pid).add(parentId);
		}
		
		//wondering if we can do all this after because it is really slow here. 
		
		// associate the new node with appropriate parents of the current path node
		for (int pid : nestedParents.get(parentId)) {
			if(nestedParents.get(newBipartId).contains(pid) == false){
				if (newBipart.isNestedPartitionOf(bipart.get(pid))) {
					nestedParents.get(newBipartId).add(pid);
					nestedChildren.get(pid).add(newBipartId);
				}
			}
		}

		// associate the new node with appropriate children of the current path node
		for (int cid : nestedChildren.get(parentId)) {
			if(nestedChildren.get(newBipartId).contains(cid) == false){
				if (bipart.get(cid).isNestedPartitionOf(newBipart)) {
					nestedParents.get(cid).add(newBipartId);
					nestedChildren.get(newBipartId).add(cid);
				}
			}
		}
		
		// now check related nodes to make sure we connect the new bipartition to all the appropriate parents/children
		// CHANGED THIS TO BE after the generate nodes step for less redundancy
		// TODO: more testing to make sure this is good
		// add any appropriate parents/children of analogous biparts
		/*HashSet<Integer> compareset1 = new HashSet<Integer>();
		HashSet<Integer> compareset2 = new HashSet<Integer>();
		for (int eq : analogousBiparts.get(parentId)) {
			compareset1.addAll(nestedParents.get(eq));
			compareset2.addAll(nestedChildren.get(eq));
		}
		for (int pid : compareset1) {
			if(nestedParents.get(newBipartId).contains(pid) == false){
				if (newBipart.isNestedPartitionOf(bipart.get(pid))) {
					nestedParents.get(newBipartId).add(pid);
					nestedChildren.get(pid).add(newBipartId);
				}
			}
		}
		for (int cid : compareset2) {
			if(nestedChildren.get(newBipartId).contains(cid) == false){
				if (bipart.get(cid).isNestedPartitionOf(newBipart)) {
					nestedChildren.get(newBipartId).add(cid);
					nestedParents.get(cid).add(newBipartId);
				}
			}
		}
		*/
		
		// remember that this new bipart is analogous to this path node bipart so that if
		// we make another analogous node we will check these ones for parent/children
		analogousBiparts.get(newBipartId).add(parentId);
		analogousBiparts.get(parentId).add(newBipartId);

		// remember the new node so we can add it as a parent of all descendant nodes on this path
		ancestorsOnPath.add(newBipartId);
 		
		return new Object[] { ancestorsOnPath, cumulativeOutgroup };
	}
	
	
	
	private Node createNode(LongBipartition b) {
		Node node = gdb.createNode();
		if (VERBOSE) { System.out.println(node); }
		//this is all here because of the exploded reduced
		if(USING_TAXONOMY){
			LongBipartition tlb = getExpandedTaxonomyBipart(b);
			bipartForGraphNodeExploded.put(node,tlb);
			if(tlb.ingroup().size()==0){
				System.out.println("ingroup size=0:"+tlb);
				System.exit(0);
			}
			node.setProperty(NodeProperty.MRCA.propertyName, tlb.ingroup().toArray());
			node.setProperty(NodeProperty.OUTMRCA.propertyName, tlb.outgroup().toArray());
		}else{
			node.setProperty(NodeProperty.MRCA.propertyName, b.ingroup().toArray());
			node.setProperty(NodeProperty.OUTMRCA.propertyName, b.outgroup().toArray());
		}
		graphNodeForBipart.put(b, node);
		bipartForGraphNode.put(node, b);
		
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
		child.createRelationshipTo(parent, RelType.MRCACHILDOF);
		hasMRCAChildOf.get(child.getId()).add(parent.getId());
	}
	
	private boolean testChildOf(Node child,Node parent){
		ImmutableCompactLongSet cin = new ImmutableCompactLongSet((long[])child.getProperty("mrca"));
		ImmutableCompactLongSet cout = null;
		if(child.hasProperty("outmrca"))
			cout = new ImmutableCompactLongSet((long[])child.getProperty("outmrca"));

		ImmutableCompactLongSet pin = new ImmutableCompactLongSet((long[])parent.getProperty("mrca"));
		ImmutableCompactLongSet pout = null;
		if(parent.hasProperty("outmrca"))
			pout = new ImmutableCompactLongSet((long[])parent.getProperty("outmrca"));
		
		//TODO: put the test here
		return true;
	}
	
	/**
	 * This will check to see if an STREECHILDOF rel already exists for that source between the child and the
	 * parent, and if not it will make one.
	 * @param child
	 * @param parent
	 * @param childBipart 
	 */
	private void updateSTREEChildOf(Node child, Node parent, String source, Integer sourcerank, int edgeId, 
			LongBipartition childBipart, boolean istip ) {
		if (hasSTREEChildOf.get(child.getId()) == null) {
			hasSTREEChildOf.put(child.getId(), new HashMap<Long,HashSet<String>>());
			notValidSTREEChildOf.put(child.getId(), new HashSet<Long>());
			testedSTREEChildOf.put(child.getId(), new HashSet<Long>());
		} else {
			if(testedSTREEChildOf.get(child.getId()).contains(parent.getId())==false){
				boolean test = testChildOf(child,parent);
				testedSTREEChildOf.get(child.getId()).add(parent.getId());
				if(test == false)
					notValidSTREEChildOf.get(child.getId()).add(parent.getId());
			}
			if(notValidSTREEChildOf.get(child.getId()).contains(parent.getId())){
				return;
			}
			if (hasSTREEChildOf.get(child.getId()).containsKey(parent.getId())) {
				if(hasSTREEChildOf.get(child.getId()).get(parent.getId()).contains(source))
					return;
			}
		}
		Relationship rel = child.createRelationshipTo(parent, RelType.STREECHILDOF);
		rel.setProperty("source", source);
		rel.setProperty("sourcerank", sourcerank);
		if(this.subset){
			rel.setProperty("subset", ottidFromSubset);
			rel.setProperty(RelProperty.SOURCE_EDGE_ID.propertyName, source+"_"+ottidFromSubset+"_"+edgeId);
		}else{
			rel.setProperty(RelProperty.SOURCE_EDGE_ID.propertyName, source+"_"+edgeId);
		}
		if (istip) { rel.setProperty(RelProperty.CHILD_IS_TIP.propertyName, true); }
		if (childBipart != null) {
			rel.setProperty("exclusive_mrca", childBipart.ingroup().toArray());
			rel.setProperty("exclusive_outmrca", childBipart.outgroup().toArray());
		}
		if(hasSTREEChildOf.get(child.getId()).containsKey(parent.getId())==false)
			hasSTREEChildOf.get(child.getId()).put(parent.getId(), new HashSet<String>());
		hasSTREEChildOf.get(child.getId()).get(parent.getId()).add(source);
		nodesWithSTREERels.add(child.getId()); nodesWithSTREERels.add(parent.getId());
	}
	
	private LongBipartition getGraphBipartForTreeNode(TreeNode p, Tree t) {
		MutableCompactLongSet ingroup = new MutableCompactLongSet();
		MutableCompactLongSet outgroup = new MutableCompactLongSet();
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
		return new ImmutableLongBipartition(ingroup, outgroup); // made immutable
	}
	
	/**
	 * Paths are just integer arrays. This private micro class ensures that they
	 * are compared properly and well behaved in hash maps.
	 * @author cody
	 */
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

	////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//  below here lie tests.
	//
	////////////////////////////////////////////////////////////////////////////////////////////////////
	
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

		BipartOracle bi = new BipartOracle(t, gdb, false,null,null,false,null);

	}
	
	private static void loadFabalesTreesTest(String dbname) throws Exception {

		FileUtils.deleteDirectory(new File(dbname));
		
		String taxonomy = "test-synth/fabales/taxonomy.tsv";
		String synonyms = "";
		
		GraphInitializer tl = new GraphInitializer(dbname);
		tl.addInitialTaxonomyTableIntoGraph(taxonomy, synonyms, "2.8");
		tl.shutdownDB();
		
		List<Tree> t = new ArrayList<Tree>();
		BufferedReader br = new BufferedReader(new FileReader("test-synth/fabales/fab.tre"));
		String str;
		while((str = br.readLine())!=null){
			t.add(TreeReader.readTree(str));
		}
		br.close();
		System.out.println("trees read");
	
		GraphDatabaseAgent gdb = new GraphDatabaseAgent(dbname);

		BipartOracle bi = new BipartOracle(t, gdb, true, null, null,false,null);

	}
	
	@SuppressWarnings("unused")
	private static void runDipsacalesTest(String dbname) throws Exception {
		String version = "1";
		
		FileUtils.deleteDirectory(new File(dbname));
		
		String taxonomy = "test-synth/dipsacales/taxonomy.tsv";
		String synonyms = "";
		
		GraphInitializer tl = new GraphInitializer(dbname);
		tl.addInitialTaxonomyTableIntoGraph(taxonomy, synonyms, version);
		tl.shutdownDB();

		List <Tree> t = new ArrayList<Tree>();
		

		BufferedReader br = new BufferedReader(new FileReader("test-synth/dipsacales/tree1.tre"));
		String str = br.readLine();
		t.add(TreeReader.readTree(str));
		br.close();
		br = new BufferedReader(new FileReader("test-synth/dipsacales/tree2.tre"));
		str = br.readLine();
		t.add(TreeReader.readTree(str));
		br.close();
		br = new BufferedReader(new FileReader("test-synth/dipsacales/tree3.tre"));
		str = br.readLine();
		t.add(TreeReader.readTree(str));
		br.close();

		GraphDatabaseAgent gdb = new GraphDatabaseAgent(dbname);

		BipartOracle bi = new BipartOracle(t, gdb, true,null,null,false,null);

	}
	
	@SuppressWarnings("unused")
	private static void runSimpleTest(List<Tree> t, String dbname) throws Exception {

		FileUtils.deleteDirectory(new File(dbname));
		BipartOracle bi = new BipartOracle(t, new GraphDatabaseAgent(new EmbeddedGraphDatabase(dbname)), false,null,null,false,null);

		System.out.println("original bipartitions: ");
		for (int i = 0; i < bi.bipart.size(); i++) {
			System.out.println(i + ": " + bi.bipart.get(i).toString(bi.labelForNodeId));
		}
		
		System.out.println("node for bipart:");
		
		for (LongBipartition tlb: bi.graphNodeForBipart.keySet()){
			System.out.println(tlb+" "+bi.graphNodeForBipart.get(tlb));
		}
		
		System.out.println("paths through: ");
		for (Path p : bi.paths) {
			System.out.println(p);
		}
		bi.gdb.shutdownDb();
	}
	
	@SuppressWarnings("unused")
	private static void runSimpleOTTTest(List<Tree> t, String dbname) throws Exception {

		BipartOracle bi = new BipartOracle(t, new GraphDatabaseAgent(new EmbeddedGraphDatabase(dbname)), true,null,null,false,null);

		System.out.println("original bipartitions: ");
		for (int i = 0; i < bi.bipart.size(); i++) {
			System.out.println(i + ": " + bi.bipart.get(i).toString(bi.labelForNodeId));
		}
		
		System.out.println("node for bipart:");
		
		for (LongBipartition tlb: bi.graphNodeForBipart.keySet()){
			System.out.println(tlb+" "+bi.graphNodeForBipart.get(tlb));
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
	private static List<Tree> trivialConflict() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("((A,E),B);"));
		t.add(TreeReader.readTree("(A,B);"));
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
	private static List<Tree> complexCompatible() throws TreeParseException {
		List<Tree> t = new ArrayList<Tree>();
		t.add(TreeReader.readTree("(((((A1,A2),A3),A4),(A5,A6)),(((B1,B2),((B3,B4),B5)),(((C1,C2),(C3,C4)),(D1,D2))));"));
		t.add(TreeReader.readTree("((A1,P),(A6,(Q1,Q2)),(((B1,B3),(S1,S2)),((C1,D1),(T,U,V))));"));
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
	
public static void main(String[] args) throws Exception {
		
		// these are tests for order
		String dbname = "test.db";
//		runSimpleTest(nestedOverlap2(), dbname);
//		runSimpleTest(conflictingAugmenting(), dbname);
//		runSimpleTest(complexCompatible(), dbname);
//		runSimpleTest(cycleConflictTrees(), dbname);
//		runSimpleTest(trivialConflict(), dbname);
//		runSimpleTest(nonOverlapTrees(), dbname);
//		runSimpleTest(test4Trees(), dbname);
//		runSimpleTest(test3Trees(), dbname);
//		runSimpleTest(testInterleavedTrees(), dbname);
//		runSimpleTest(completeOverlap(), dbname);

		// this is a stress test for the loading procedure -- 100 trees with 600 tips each.
		// lots of duplicate biparts though so it should only take a few mins (if allowed to be decently parallel)
//		loadATOLTreesTest(dbname);
		loadFabalesTreesTest(dbname);
		
		// these are tests for taxonomy
//		runDipsacalesTest(dbname);
	}
}
