package opentree;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import jade.tree.*;
import jade.MessageLogger;

import java.lang.StringBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;

import opentree.constants.RelType;
import opentree.exceptions.AmbiguousTaxonException;
import opentree.exceptions.MultipleHitsException;
import opentree.exceptions.TaxonNotFoundException;
import opentree.exceptions.TreeIngestException;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

import scala.actors.threadpool.Arrays;

/**
 * GraphImporter is intended to control the initial creation 
 * and addition of trees to the tree graph.
 */

public class GraphImporter extends GraphBase {
	//static Logger _LOG = Logger.getLogger(GraphImporter.class);

//	private int transaction_iter = 100000;
//	private int cur_tran_iter = 0;
	
	private static final int commitFrequency = 1000;
	private int nNodesToCommit;

	private JadeTree inputTree; // the jadetree that we are importing; was jt
	private String inputTreeNewick; // original newick string for the inputTree; was treestring
	private String sourceName; // will be recorded in the STREECHILDOF branches we add, and also the metadata node
	private String treeID; // will also be recorded in the db
	private String rawNexsonTreeId = null;
	private String commitSHA = null;
	private String studyId = null;

	private boolean allTreesHaveAllTaxa = false; // this will trigger getalllica if true, otherwise we use the bipartition based code
	private boolean runTestOnly = false; // if set, then nothing is recorded in the db but we run through the process and give lots of output
	
	private ArrayList<Node> updatedNodes;
	private HashSet<Node> updatedSuperLICAs;
	private MessageLogger logger;
	
	private ArrayList<JadeNode> inputJadeTreeLeaves; // just the leaves of the input tree
	// TODO making a Set<Long> or sorted ArrayList<Long> for the ids would make the look ups faster. See comment in testIsMRCA
	private TLongArrayList graphNodeIdsForInputLeaves; // the graph node ids for the nodes matched to the input tree leaves
	private HashMap<JadeNode,Long> inputJadeTreeLeafToMatchedGraphNodeIdMap; // maps each jade node to the id of the graph node it's mapped to
	private TLongArrayList graphDescendantNodeIdsForInputLeaves; // all the ids of the graph nodes descended from the leaves of the input tree
	private HashMap<JadeNode,ArrayList<Long>> jadeNodeToDescendantGraphNodeIdsMap; // maps each jade node to the node ids in the mrca property of its matched graph node
	
	private Transaction	tx;

	// contains the names of the descendant jade tree leaves of the current jade node, recycled as we move through the tree.
	// just used for generating meaningful user output
	private ArrayList<String> namesList;
	
	public GraphImporter(String graphName) {
		super(graphName);
	}

	public GraphImporter(EmbeddedGraphDatabase eg) {
		super(eg);
	}
	
	public GraphImporter(GraphDatabaseService gs) {
		super(gs);
	}
	
	public GraphImporter(GraphDatabaseAgent gdb) {
		super(gdb);
	}

	/*
	 * Sets the jt member by reading a JadeTree from filename.
	 *
	 * This currently reads a tree from a file but this will need to be changed to 
	 * another form later
	 * @param filename name of file with a newick tree representation
	 */
	/*
	 *@Deprecated // This is never used. Should be handled by a different class. Remove unless we find out otherwise.
	public void preProcessTree(String filename, String treeID) {
		// read the tree from a file
		String ts = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			ts = br.readLine();
			inputTreeNewick = ts;
			br.close();
		} catch(IOException ioe) {}
		TreeReader tr = new TreeReader();
		inputTree = tr.readTree(ts);
		inputTree.assocObject("id", treeID);
		System.out.println("tree read");
		// System.exit(0);
	} */
	
	/**
	 * Intialize the importer with a new JadeTree (already read and processed) to be imported.
	 *
	 * @param JadeTree object
	 */
	public void setTree(JadeTree tree) {
		inputTree = tree;
		treeID = (String)inputTree.getObject("id");
		inputTreeNewick = inputTree.getRoot().getNewick(true) + ";";
		initialize();
	}
	
	/**
	 * Ingest the current JadeTree (in the inputTree instance variable) to the GoL.
	 *
	 * This will assume that the JadeNodes all have a property set as ot:ottId
	 * 		that will be the preset ottol id identifier that will be found by index.
	 * 		ALL THE NAMES HAVE TO BE SET FOR THIS FUNCTION
	 *
	 * @param sourcename the name to be registered as the "source" property for every edge in this tree.
	 * @param test don't add to the database
	 * @throws Exception 
	 */
	public void addSetTreeToGraphWIdsSet(String sourceName, boolean allTreesHaveAllTaxa, boolean runTestOnly, MessageLogger msgLogger, String studyId, String treeId, String SHA) throws Exception {

		this.runTestOnly = runTestOnly;
		this.allTreesHaveAllTaxa = allTreesHaveAllTaxa;
		System.out.println("all trees have all taxa: " + this.allTreesHaveAllTaxa);
		if (this.allTreesHaveAllTaxa) {
			System.out.println("\tusing complete mapping technique");
		} else {
			System.out.println("\tusing bipartition technique");
		}
		this.logger = msgLogger;
		this.sourceName = sourceName;
		this.studyId = studyId;
		this.rawNexsonTreeId = treeId;
		this.commitSHA = SHA; 

		// first add the mappings to the eaxct taxon nodes
		matchTaxaUsingTaxUIDs();

		// now remap them
		//should not do this if all trees have taxa
		if (this.allTreesHaveAllTaxa == false) {
			remapTipsToDeepestExemplifiedTaxa(); // remove this line to return to the old method of exact taxon mapping
		}
		loadTree();
	}
	
	/**
	 * Ingest the current JadeTree (in the jt data member) to the GoL.
	 *
	 * this should be done as a preorder traversal
	 *
	 * @param focalgroup a taxonomic name of the ancestor of the leaves in the tree
	 *		this is only used in disambiguating taxa when there are multiple hits 
	 *		for a leaf's taxonomic name
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @throws Exception 
	 * @todo we probably want a node in the graph representing the tree with an 
	 *		ISROOTOF edge from its root to the tree. We could attach annotations
	 *		about the tree to this node. We have the index of the root node, but
	 *		need to having and isroot would also be helpful. Unless we are indexing
	 *		this we could just randomly choose one of the edges that is connected
	 *		to the root node that is in the index
	 */
	public void addSetTreeToGraph(String focalgroup, String sourceName, boolean allTreesHaveAllTaxa, MessageLogger msgLogger) throws Exception {

		this.runTestOnly = false;
		this.allTreesHaveAllTaxa = allTreesHaveAllTaxa;
		System.out.println("all trees have all taxa: " + this.allTreesHaveAllTaxa);
		if (this.allTreesHaveAllTaxa) {
			System.out.println("\tusing complete mapping technique");
		} else {
			System.out.println("\tusing bipartition technique");
		}
		this.logger = msgLogger;
		this.sourceName = sourceName;

		// first add the mappings to the exact taxon nodes
		matchTaxaUsingNames(focalgroup);
		
		// now remap them
		//should not do this if all trees have taxa
		if (this.allTreesHaveAllTaxa == false) {
			remapTipsToDeepestExemplifiedTaxa(); // remove this line to return to the old method of exact taxon mapping
		}
		loadTree();
	}
	
	/**
	 * Resets instance variables used to contain information about the current tree to be imported. Is called by the setTree
	 * method, and should be called before every attempt to load a new tree.
	 */
	private void initialize() {
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();
		allTreesHaveAllTaxa = false;
		runTestOnly = false;
		sourceName = null;
		nNodesToCommit = 0;
		
		inputJadeTreeLeaves = inputTree.getRoot().getTips();
		graphNodeIdsForInputLeaves = new TLongArrayList();  // was ndids
		inputJadeTreeLeafToMatchedGraphNodeIdMap = new HashMap<JadeNode,Long>(); // was hashnodeids
		graphDescendantNodeIdsForInputLeaves = new TLongArrayList(); // was ndidssearch
		jadeNodeToDescendantGraphNodeIdsMap = new HashMap<JadeNode,ArrayList<Long>>(); // hashnodeidssearch
		
	}

	/**
	 * Searches the graph for nodes matching the names of the input tree leaves, and uses the `focalgroup` string to disambiguate when
	 * multiple matches (homonyms) are found.
	 * @param focalgroup
	 * @throws TaxonNotFoundException 
	 * @throws MultipleHitsException 
	 */
	private void matchTaxaUsingNames(String focalgroup) throws MultipleHitsException, TaxonNotFoundException {
		
		Node focalnode = findTaxNodeByName(focalgroup);
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelType.TAXCHILDOF, Direction.OUTGOING), 1000);

		// gather information for each leaf in the input tree prior to import
		// TODO: this could be modified to account for internal node name mapping
		for (JadeNode curLeaf : inputJadeTreeLeaves) {
			
			String processedname = curLeaf.getName();

			// find all the tip taxa and with doubles pick the taxon closest to the focal group
			Node matchedGraphNode = null;
			IndexHits<Node> hits = null;
			try {
				hits = graphNodeIndex.get("name", processedname);
				int numh = hits.size();
				if (numh == 1) {
					matchedGraphNode = hits.getSingle();
				} else if (numh > 1) {
					logger.indentMessageIntStr(2, "multiple graphNamedNodes hits", "number of hits", numh, "name", processedname);
					int shortest = 1000; // this is shortest to the focal, could reverse this
					Node shortn = null;
					for (Node tnode : hits) {
						Path tpath = pf.findSinglePath(tnode, focalnode);
						if (tpath != null) {
							if (shortn == null) {
								shortn = tnode;
							}
							if (tpath.length()<shortest) {
								shortest = tpath.length();
								shortn = tnode;
							}
	//						System.out.println(shortest + " " + tpath.length());
						} else {
							logger.indentMessageStr(3, "graphNamedNodes hit not within focalgroup", "focalgroup", focalgroup);
						}
					}
					if (shortn == null) {
						// if there are multiple hits outside the focalgroup, and none inside the focalgroup
						throw new AmbiguousTaxonException(processedname);
					}
					matchedGraphNode = shortn;
				}
				if (matchedGraphNode == null) {
					assert numh == 0;
					throw new TaxonNotFoundException(processedname);
				}
			} finally {
				hits.close();
			}
			inputJadeTreeLeafToMatchedGraphNodeIdMap.put(curLeaf, matchedGraphNode.getId());
		}
		gatherInfoForLicaSearches();
	}
	
	/**
	 * Searches the graph for taxa identified using the ottIds stored in the input tree leaves. Will throw exceptions if it
	 * cannot find taxa or finds multiple taxa with the same uid.
	 * @throws TaxonNotFoundException
	 */
	private void matchTaxaUsingTaxUIDs() throws TaxonNotFoundException {
		
		// gather information for each leaf in the input tree prior to starting the import
		// TODO: this could be modified to account for internal node name mapping
		for (JadeNode curLeaf : inputJadeTreeLeaves) {
			
			Long ottId = (Long) curLeaf.getObject("ot:ottId");

			// use the ottId to match to a graph node, or throw an exception if we can't
			Node matchedGraphNode = null;
			IndexHits<Node> hits = null;
			try {
				hits = graphTaxUIDNodeIndex.get("tax_uid", ottId);
				int numh = hits.size();
				if (numh < 1) {
					throw new TaxonNotFoundException(String.valueOf(ottId));
				} else if (numh > 1) {
					throw new AmbiguousTaxonException("tax_uid=" + ottId);
				} else {
					matchedGraphNode = hits.getSingle();
				}
			} finally {
				hits.close();
			}

			inputJadeTreeLeafToMatchedGraphNodeIdMap.put(curLeaf, matchedGraphNode.getId());
		}
		gatherInfoForLicaSearches();
	}

	/**
	 * Uses the exact taxon mappings to find the deepest exemplified taxonomic ancestors that each tip could
	 * represent, and remaps the tip taxa. This implements the assumption that input trees cannot inform
	 * relationships among taxa which they do not contain. This should improve the results of synthesis methods.
	 * @throws Exception 
	 */
	private void remapTipsToDeepestExemplifiedTaxa() throws Exception {
		
		// preserve the original taxon mappings so we can use these to generate sets of outgroup ids for each tip
		HashMap<JadeNode, Long> shallowTaxonMappings = new HashMap<JadeNode, Long>(inputJadeTreeLeafToMatchedGraphNodeIdMap);
		System.out.println("attempting to remap tips to deepest exemplified taxa");

		tx = graphDb.beginTx();
		try {
			for (JadeNode curLeaf : shallowTaxonMappings.keySet()) {
				
				Long originalMatchedNodeId = shallowTaxonMappings.get(curLeaf);
				Node originalMatchedNode = graphDb.getNodeById(originalMatchedNodeId);
//				System.out.println("attempting to remap tip " + curLeaf.getName() + " (was mapped to " + getIdString(originalMatchedNode) +")");
				
				// get the outgroup set for this node, which is *all* the mrca descendendants of all the nodes mapped to all the input tips except this one.
				// we do this here because we get the mrca properties from the original taxon mappings.
				TLongArrayList outgroupIds = new TLongArrayList();
				for (Long tid : shallowTaxonMappings.values()) {
					if (tid.equals(originalMatchedNodeId) == false) {
						outgroupIds.addAll((long[]) graphDb.getNodeById(tid).getProperty("mrca"));
					}
				}
				
				Node newMatch = getDeepestExemplifiedTaxon(graphDb.getNodeById(originalMatchedNodeId), outgroupIds);
				
				if (originalMatchedNodeId.equals(newMatch.getId())) {
//					System.out.println("\t" + curLeaf.getName() + " was not remapped");
	
				} else { // we remapped the leaf to a deeper taxon
					
					// update the map
					inputJadeTreeLeafToMatchedGraphNodeIdMap.put(curLeaf, newMatch.getId());
	
					// add a relationship that records the mapping
					Relationship exemplarRel = originalMatchedNode.createRelationshipTo(newMatch, RelType.STREEEXEMPLAROF);
					sourceRelIndex.add(exemplarRel, "source", sourceName);
					exemplarRel.setProperty("source", sourceName);
					exemplarRel.setProperty("source_tree_id", treeID);
	
					System.out.println("\t" + curLeaf.getName() + " was remapped to " + getIdString(newMatch));
				}
			}
			
		} catch (Exception ex) { // dump the transaction if remapping fails
			tx.failure();
			tx.finish();
			throw ex;
		} finally {
			tx.success();
			tx.finish();
		}
	}
	
	/**
	 * Gets the deepest taxonomic ancestor that the given inNode can exemplify, using the DeepestTaxAncestorEvaluator class.
	 * There must be one and only one such deepest exemplified taxon, so this will throw an exception if it finds otherwise.
	 * @param inNode
	 * @return
	 */
	private Node getDeepestExemplifiedTaxon(Node inNode, TLongArrayList outgroupIds) {
		
		BitSet exemplarOutgroupIdsBS = new BitSet((int) outgroupIds.max());
		for (int i = 0; i < outgroupIds.size(); i++) {
			exemplarOutgroupIdsBS.set((int) outgroupIds.getQuick(i));
		}

		Node taxChild = inNode;
		Node taxParent = taxChild.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
		boolean stillLookingForDeepest = true;
		while (stillLookingForDeepest) {
			
			// get all the mrca descendants of the parent
			TLongArrayList parentIngroupIds = new TLongArrayList((long[]) taxParent.getProperty("mrca"));
			BitSet parentIngroupIdsBS = new BitSet((int) parentIngroupIds.max());
			for (int i = 0; i < parentIngroupIds.size(); i++) {
				parentIngroupIdsBS.set((int) parentIngroupIds.getQuick(i));
			}
			
			// if this node's parent taxon node contains anything from the outgroup, then this node is the deepest exemplified taxon
			if (parentIngroupIdsBS.intersects(exemplarOutgroupIdsBS)) {
				stillLookingForDeepest = false;

			} else { // move up to the next node
				taxChild = taxParent;
				try {
					taxParent = taxChild.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
				} catch (NullPointerException ex) {
					throw new IllegalStateException("hit the root of the graph while trying to remap " + getIdString(inNode) + ". this should not have happened.");
				}
			}
		}

		return taxChild;
	}
	
	/** just used for formatting node info for nice output */
	private String getIdString(Node n) {
		String idStr = "'";
		if (n.hasProperty("name")) {
			idStr = idStr.concat((String) n.getProperty("name"));
		}
		return idStr.concat("' (id=").concat(String.valueOf(n.getId())).concat(")");
	}
	
	/**
	 * Prepopulates several container-class instance variables that will be used during lica searching. Called by the
	 * `matchTaxaUsing...` methods, and assumes that the jade node tips have already been matched to graph nodes.
	 */
	private void gatherInfoForLicaSearches() {
		for (Entry<JadeNode, Long> match : inputJadeTreeLeafToMatchedGraphNodeIdMap.entrySet()) {
			
			JadeNode curLeaf = match.getKey();
			Node matchedGraphNode = graphDb.getNodeById(match.getValue());
			
			// add information on node mappings and descendants to instance variables for easy access during import
			// these are used for the lica calculations, etc.
			long [] mrcaArray = (long[])matchedGraphNode.getProperty("mrca");
			ArrayList<Long> descendantIdsForCurMatchedGraphNode = new ArrayList<Long>(); 
			for (int k = 0; k < mrcaArray.length; k++) {
				graphDescendantNodeIdsForInputLeaves.add(mrcaArray[k]);
				descendantIdsForCurMatchedGraphNode.add(mrcaArray[k]);
			}
			jadeNodeToDescendantGraphNodeIdsMap.put(curLeaf, descendantIdsForCurMatchedGraphNode);
			graphNodeIdsForInputLeaves.add(matchedGraphNode.getId());
		}
		graphDescendantNodeIdsForInputLeaves.sort();
		graphNodeIdsForInputLeaves.sort();		
	}
	
	/**
	 * Initiates the recursive tree-loading procedure. Called by the public methods for loading trees; exists only to avoid code duplication.
	 * @throws TreeIngestException
	 */
	private void loadTree() throws TreeIngestException {

		tx = graphDb.beginTx();
//		try {
		if(runTestOnly == false) { // actually load the tree into the db
			postOrderAddProcessedTreeToGraph(inputTree.getRoot());

		} else { // just test the loading process
			postOrderAddProcessedTreeToGraphNoAdd(inputTree.getRoot());
		}

		tx.success();

//		} finally {

		System.out.println("Committing nodes: " + nNodesToCommit);
		tx.finish();
//		}
	}
	
	/**
	 * Finish ingest a tree into the GoL. This is called after the names in the tree
	 *	have been mapped to IDs for the nodes in the Taxonomy graph. The mappings are stored
	 *	as an object associated with the root node, as are the list of node ID's. 
	 *
	 * This will update the class member updatedNodes so they can be used for updating 
	 * existing relationships.
	 *
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @param test don't add the tree to the database, just run through as though you would add it
	 * @todo note that if a TreeIngestException the database will not have been reverted
	 *		back to its original state. At minimum at least some relationships
	 *		will have been created. It is also possible that some nodes will have
	 *		been created. We should probably add code to assure that we won't get
	 *		a TreeIngestException, or rollback the db modifications.
	 *		
	 */
	@SuppressWarnings("unchecked")
	private void postOrderAddProcessedTreeToGraph(JadeNode curJadeNode) throws TreeIngestException {

		if (nNodesToCommit % commitFrequency == 0 && nNodesToCommit > 0) {
			System.out.println("Committing nodes " + (nNodesToCommit - commitFrequency + 1) + " through " + nNodesToCommit);
			tx.success();
			tx.finish();
			tx = graphDb.beginTx();
		}

		// increment for the transaction frequency
		nNodesToCommit++;
		
		// postorder traversal via recursion
		for (int i = 0; i < curJadeNode.getChildCount(); i++) {
			postOrderAddProcessedTreeToGraph(curJadeNode.getChild(i));
		}

		// testing
		namesList = new ArrayList<String>();
		for (JadeNode child : curJadeNode.getDescendantLeaves()) {
			namesList.add(child.getName());
		}
		//System.out.println("working on node: " + Arrays.toString(namesList.toArray()));
		if (curJadeNode.isTheRoot()) {
			System.out.println("this is the ROOT");
		}

		if (curJadeNode.getChildCount() == 0) { // this is a tip, not much to do here
			
			// there is only one lica match: the node to which we mapped this input node
			HashSet<Node> licaMatches = new HashSet<Node>();
			licaMatches.add(graphDb.getNodeById(inputJadeTreeLeafToMatchedGraphNodeIdMap.get(curJadeNode)));
			curJadeNode.assocObject("dbnodes", licaMatches);
			curJadeNode.assocObject("graph_nodes_mapped_to_descendant_leaves", new LinkedList<Node>(licaMatches));

			TLongArrayList mrcaDescendantIds = new TLongArrayList((long[]) graphDb.getNodeById(inputJadeTreeLeafToMatchedGraphNodeIdMap.get(curJadeNode)).getProperty("mrca"));
//			long[] mrca = (long[]) graphDb.getNodeById(inputLeafJadeNodeToMatchedGraphNodeIdMap.get(curJadeNode)).getProperty("mrca");
//			for (long id : mrca) {
//				mrcaDescendantIds.add(id);
//			}
			
//			System.out.println("Mapping tip [" + curJadeNode.getName() + "] to " + Arrays.toString(licaMatches.toArray()));
						
			// add all the ids for the mrca descendants of the mapped node to the 'exclusive_mrca' field
			curJadeNode.assocObject("exclusive_mrca", mrcaDescendantIds.toArray());
			
		} else { // this is an internal node

			LinkedList<String> childNames = new LinkedList<String>();
			for (JadeNode child : curJadeNode.getDescendantLeaves()) {
				childNames.add(child.getName());
			}

			// testing
//			System.out.println("looking for descendants of input node : \'" + curJadeNode.getName() + "\' " + Arrays.toString(childNames.toArray()));

			// NOTE: the following several variables contain similar information in different combinations and formats.
			// They are used to optimize the LICA searches by attempting to perform less exhaustive tests whenever possible.
			// For ease of interpretation, the variable names are constructed using the names of other related variables.
			// An underscore in the name indicates that what follows is a direct reference to another variable (with that name).
			
			// the nodes mapped to the descendant tips of the current node in the input tree
			LinkedList<Node> graphNodesMappedToDescendantLeavesOfThisJadeNode = new LinkedList<Node>();
			// the graph nodes corresponding to all the mrca descendants of the nodes mapped to all the tips descended from the current node in the input tree
			LinkedList<Node> graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode = new LinkedList<Node>();
			// the node ids of the nodes in graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode
			TLongArrayList nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode = new TLongArrayList();

			// begin alternative block 1
/*			// summarize the lica mapping information as we recursive move up the tree to the root
			for (JadeNode child : curJadeNode.getChildren()) {
				graphNodesMappedToDescendantLeavesOfThisJadeNode.addAll((List<Node>) child.getObject("graph_nodes_mapped_to_descendant_leaves"));
				nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.addAll((long[]) child.getObject("exclusive_mrca"));

				TLongIterator descIter = nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.iterator();
				while (descIter.hasNext()) {
					graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphDb.getNodeById(descIter.next()));
				}
			}
			// end alternative block 1  */

			// begin alternative block 2
 			// gather lica mapping information independently for each node as we see it. this is slightly slower than recording this
			// info recursively in the jadenode objects as we traverse, but it uses *much* less memory
			for (JadeNode curLeaf : curJadeNode.getDescendantLeaves()) {

				// remember the ids for the the graph nodes mapped to each jade tree leaf descended from the current node
				graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphDb.getNodeById(inputJadeTreeLeafToMatchedGraphNodeIdMap.get(curLeaf)));

				// get the mrca descendants for each leaf descended from this jade node (same info as the mrca fields from the graph nodes we matched to these jade tree leaves)
				ArrayList<Long> descendantGraphNodeIdsForCurLeaf = jadeNodeToDescendantGraphNodeIdsMap.get(curLeaf);

				// remember the node ids for all the mrca descendants of this jade node's descendant leaves (just from the input tree)
				nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.addAll(descendantGraphNodeIdsForCurLeaf);

				// also remember all the nodes themselves for for mrca descendant ids that we encounter
				for (Long descId : descendantGraphNodeIdsForCurLeaf) {
					graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphDb.getNodeById(descId));
				}
			}
			// end alternative block 2  */

			curJadeNode.assocObject("graph_nodes_mapped_to_descendant_leaves", graphNodesMappedToDescendantLeavesOfThisJadeNode);
			curJadeNode.assocObject("exclusive_mrca", nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.toArray());
		
			// testing
//			System.out.println("mrca descendants for input node \'" + curJadeNode.getName() + "\' include: " + Arrays.toString(nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.toArray()));
			
			nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.sort();
			curJadeNode.assocObject("exclusive_mrca", nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.toArray()); // moved up from below

			// Get the union of all node ids from the mrca properties (i.e. descendant node ids) for every graph node mapped as a lica to every jade node
			// child of the current jade node. This accumulates descendant node ids as the postorder traversal moves down the input tree toward the root.
			TLongHashSet licaDescendantIdsForCurrentJadeNode_Hash = new TLongHashSet(); // use a hashset to avoid duplicate entries
			for (JadeNode childNode : curJadeNode.getChildren()) {

				HashSet<Node> childNodeLicaMappings = (HashSet<Node>) childNode.getObject("dbnodes"); // the graph nodes for the mrca mappings for this child node

				for (Node licaNode : childNodeLicaMappings) {
					licaDescendantIdsForCurrentJadeNode_Hash.addAll((long[]) licaNode.getProperty("mrca"));
				}
			}
			// convert hashset to arraylist so we can use it in the lica calculations
			TLongArrayList licaDescendantIdsForCurrentJadeNode = new TLongArrayList(licaDescendantIdsForCurrentJadeNode_Hash);
			licaDescendantIdsForCurrentJadeNode.sort();

			// ================ this section needs review
			
			// get the outgroup ids, which is the set of mrca descendent ids for all the graph nodes mapped to jade nodes in the
			// input tree that are *not* descended from the current jade node
			TLongArrayList licaOutgroupDescendantIdsForCurrentJadeNode = new TLongArrayList();
			for (int i = 0; i < graphNodeIdsForInputLeaves.size(); i++) {
				if(licaDescendantIdsForCurrentJadeNode_Hash.contains(graphNodeIdsForInputLeaves.getQuick(i))==false) {
					licaOutgroupDescendantIdsForCurrentJadeNode.addAll((long[])graphDb.getNodeById(graphNodeIdsForInputLeaves.get(i)).getProperty("mrca"));
				}
			}
			
			// things that are in the ingroup of any child of this node are in the ingroup of this node (even if they're in the outgroup of another child)
			while(licaOutgroupDescendantIdsForCurrentJadeNode.removeAll(licaDescendantIdsForCurrentJadeNode)==true)
				continue;
			licaOutgroupDescendantIdsForCurrentJadeNode.sort();

			// =================
			
			LinkedList<String> names = new LinkedList<String>();
			for (JadeNode d : curJadeNode.getDescendantLeaves()) {
				names.add(d.getName());
			}
			//System.out.println("Looking for LICA Nodes of " + Arrays.toString(names.toArray()));
			
			// find all the compatible lica mappings for this jade node to existing graph nodes
			HashSet<Node> licaMatches = null;
			if (allTreesHaveAllTaxa == true) { // use a simpler calculation if we can assume that all trees have completely overlapping taxon sampling (including taxonomy)
				licaMatches = LicaUtil.getAllLICAt4j(graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						licaDescendantIdsForCurrentJadeNode,
						licaOutgroupDescendantIdsForCurrentJadeNode);

			} else { // when taxon sets don't completely overlap, the lica calculator needs more info
				licaMatches = LicaUtil.getBipart4j(curJadeNode,graphNodesMappedToDescendantLeavesOfThisJadeNode,
						graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						licaDescendantIdsForCurrentJadeNode,
						licaOutgroupDescendantIdsForCurrentJadeNode, graphDb);
			}
			
			//			_LOG.trace("ancestor "+ancestor);
			// _LOG.trace(ancestor.getProperty("name"));

			if (licaMatches.size() > 0) { // if we found any compatible lica mappings to nodes already in the graph

				// remember all the lica mappings
				curJadeNode.assocObject("dbnodes", licaMatches);

				// remember the ids of the graph nodes mapped to all the jade tree leaves descended from this jade node
//				long[] nodeIdsFor_graphNodesMappedToDescendantLeavesOfThisJadeNode = new long[graphNodesMappedToDescendantLeavesOfThisJadeNode.size()];
//				for (int i = 0; i < graphNodesMappedToDescendantLeavesOfThisJadeNode.size(); i++) {
//					nodeIdsFor_graphNodesMappedToDescendantLeavesOfThisJadeNode[i] = graphNodesMappedToDescendantLeavesOfThisJadeNode.get(i).getId();
//				}
//				Arrays.sort(nodeIdsFor_graphNodesMappedToDescendantLeavesOfThisJadeNode);
//				curJadeNode.assocObject("exclusive_mrca", nodeIdsFor_graphNodesMappedToDescendantLeavesOfThisJadeNode);

				
			} else { // if there were no compatible lica mappings found for this jade node, then we need to make a new one

				// === step 1. create an new node in the graph
				
				Node newLicaNode = graphDb.createNode();
				//System.out.println("\t\tnewnode: "+dbnode);

				// === step 2. store the mrca information in the graph node properties

				// the dbnodes array contains compatible lica mappings. there is only one here (the node we are making)
				HashSet<Node> nar = new HashSet<Node>();
				nar.add(newLicaNode);
				curJadeNode.assocObject("dbnodes", nar);
				newLicaNode.setProperty("mrca", licaDescendantIdsForCurrentJadeNode.toArray());
				/**
				 * Get some information about this new node
				 */
				
				/**
				 * END get some information
				 */
				//System.out.println("\t\tmrca: "+childndids);

				// set outmrcas
				newLicaNode.setProperty("outmrca", licaOutgroupDescendantIdsForCurrentJadeNode.toArray());
				//System.out.println("\t\toutmrca: "+outndids);

				// this is duplicated above
				// set exclusive relationships
//				long[] rete = new long[graphNodesMappedToDescendantLeavesOfThisJadeNode.size()];
//				for (int j = 0; j < graphNodesMappedToDescendantLeavesOfThisJadeNode.size(); j++) {
//					rete[j] = graphNodesMappedToDescendantLeavesOfThisJadeNode.get(j).getId();
//				}
//				Arrays.sort(rete);
//				curJadeNode.assocObject("exclusive_mrca", rete);
				
				// === step 3. assoc the jade node with the new graph node

				// first get the super licas, which is what would be the licas if we didn't have the other taxa in the tree
				// this will be used to connect the new nodes to their licas for easier traversals
				HashSet<Node> superlicas = LicaUtil.getSuperLICAt4j(graphNodesMappedToDescendantLeavesOfThisJadeNode,
						graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						licaDescendantIdsForCurrentJadeNode);
				//System.out.println("\t\tsuperlica: "+superlica);
				
				Iterator<Node> itrsl = superlicas.iterator();
				while (itrsl.hasNext()) {
					Node itrnext = itrsl.next();
					newLicaNode.createRelationshipTo(itrnext, RelType.MRCACHILDOF);
					updatedSuperLICAs.add(itrnext);
				}
				tx.success();
				
				// add new nodes so they can be used for updating after tree ingest
				updatedNodes.add(newLicaNode);
			}

//			System.out.println("Mapping " + Arrays.toString(names.toArray()) + " to " + Arrays.toString(((HashSet<Node>) curJadeNode.getObject("dbnodes")).toArray()));
			
			// now related nodes are prepared and we have the information we need to make relationships
			addProcessedNodeRelationships(curJadeNode);
		}
		//System.out.println("done with node " + Arrays.toString(namesList.toArray()));
	}

	/**
	 * Finish ingest a tree into the GoL. This is called after the names in the tree
	 *	have been mapped to IDs for the nodes in the Taxonomy graph. The mappings are stored
	 *	as an object associated with the root node, as are the list of node ID's. 
	 *
	 * This will update the class member updatedNodes so they can be used for updating 
	 * existing relationships.
	 *
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @param test don't add the tree to the database, just run through as though you would add it
	 * @todo note that if a TreeIngestException the database will not have been reverted
	 *		back to its original state. At minimum at least some relationships
	 *		will have been created. It is also possible that some nodes will have
	 *		been created. We should probably add code to assure that we won't get
	 *		a TreeIngestException, or rollback the db modifications.
	 *		
	 */
//	@SuppressWarnings("unchecked")
	private void postOrderAddProcessedTreeToGraphNoAdd(JadeNode inode) throws TreeIngestException {
	// postorder traversal via recursion
		for (int i = 0; i < inode.getChildCount(); i++) {
			postOrderAddProcessedTreeToGraphNoAdd(inode.getChild(i));
		}
		//		_LOG.trace("children: "+inode.getChildCount());

		if (inode.getChildCount() > 0) {
			logger.indentMessageStr(2, "subtree", "newick", inode.getNewick(false));
			ArrayList<JadeNode> nds = inode.getTips();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			ArrayList<Node> hit_nodes_search = new ArrayList<Node> ();
			TLongArrayList hit_nodes_small_search = new TLongArrayList ();

			// store the hits for each of the nodes in the tips
			for (int j = 0; j < nds.size(); j++) {
				hit_nodes.add(graphDb.getNodeById(inputJadeTreeLeafToMatchedGraphNodeIdMap.get(nds.get(j))));
				ArrayList<Long> tlist = jadeNodeToDescendantGraphNodeIdsMap.get(nds.get(j));
				hit_nodes_small_search.addAll(tlist);
				for (int k = 0; k < tlist.size(); k++) {
					hit_nodes_search.add(graphDb.getNodeById(tlist.get(k)));
				}
			}
			hit_nodes_small_search.sort();
			// because we don't associate nodes from the database to this, we have to search based on just the short names			
			TLongArrayList childndids = new TLongArrayList(hit_nodes_small_search);
			TLongArrayList outndids = new TLongArrayList();

			// add all the children of the mapped nodes to the outgroup as well
			for (int i = 0; i < graphNodeIdsForInputLeaves.size(); i++) {
				if (childndids.contains(graphNodeIdsForInputLeaves.getQuick(i)) == false) {
					outndids.addAll((long[])graphDb.getNodeById(graphNodeIdsForInputLeaves.get(i)).getProperty("mrca"));
				}
			}
			childndids.sort();
			while(outndids.removeAll(childndids)==true)
				continue;
			outndids.sort();

			HashSet<Node> ancestors = null;
			if (allTreesHaveAllTaxa) {
				//we can use a simpler calculation if we can assume that the 'trees that come in are complete in their taxa
				ancestors = LicaUtil.getAllLICAt4j(hit_nodes_search, childndids, outndids);
			} else {
				ancestors = LicaUtil.getBipart4j(inode,hit_nodes,hit_nodes_search, hit_nodes_small_search,childndids, outndids,graphDb);
			}
			for (Node tnd : ancestors) {
				if (tnd.hasProperty("name")) {
					logger.indentMessageStrStr(3, "matched anc", "node", tnd.toString(), "name", (String)tnd.getProperty("name"));
					logger.indentMessageStrStr(3, "matched anc info", "tax_uid", (String)tnd.getProperty("tax_uid"), "name", (String)tnd.getProperty("name"));
				} else {
					logger.indentMessageStr(3, "matched anc", "node", tnd.toString());
				}
			}
		}
	}
	
	
	/**
	 * This should be called from within postOrderaddProcessedTreeToGraph to create relationships between nodes that have already
	 * been identified. At this point the nodes should be guaranteed to exist.
	 * 
	 * @param inputJadeNode current focal node from postorderaddprocessedtreetograph 
	 * @param source source name for the tree
	 */
	@SuppressWarnings("unchecked")
	private void addProcessedNodeRelationships(JadeNode inputJadeNode) throws TreeIngestException {

		//System.out.println("installing " + Arrays.toString(namesList.toArray()));
		HashSet<Node> allGraphNodesMappedToThisJadeNode = (HashSet<Node>) inputJadeNode.getObject("dbnodes");
		
		// preload the licaids to be stored in rels
		long [] licaids = new long[allGraphNodesMappedToThisJadeNode.size()];
		int m = 0;
		for (Node golNode : allGraphNodesMappedToThisJadeNode) {
			licaids[m] = golNode.getId();
			m++;
		}

		// for use if this node will be an incluchildof and we want to store the relationships for faster retrieval
		for (Node currGoLNode : allGraphNodesMappedToThisJadeNode) {
			ArrayList<Relationship> inclusiverelationships = new ArrayList<Relationship>();
			if (inputJadeNode.isTheRoot()) {
				addRootProperties(currGoLNode);
			}

			//System.out.println("adding relationships to licas for children");

			// for each child of the jade node we are installing
			for (JadeNode childJadeNode : inputJadeNode.getChildren()) {
				HashSet<Node> allChildGoLNodes = (HashSet<Node>)(childJadeNode.getObject("dbnodes"));
				
				// testing
				ArrayList<String> childNamesList = new ArrayList<String>();
				for (JadeNode child : childJadeNode.getDescendantLeaves()) {
					childNamesList.add(child.getName());
				}
				//System.out.println("\ton child1: " + Arrays.toString(childNamesList.toArray()));
				
				for (Node childGoLNode : allChildGoLNodes) {
					
					if (childGoLNode.getId() == currGoLNode.getId()) {
						System.out.println(childJadeNode.getNewick(false));
						throw new IllegalStateException("this child is mapped the same graph node (" + currGoLNode + ") as its parent " + Arrays.toString(namesList.toArray()));
					}
					
					Relationship rel = childGoLNode.createRelationshipTo(currGoLNode, RelType.STREECHILDOF);
					sourceRelIndex.add(rel, "source", sourceName);
					
//					rel.setProperty("exclusive_mrca", inputJadeNode.getObject("exclusive_mrca"));
					
//					System.out.println("setting exclusive mrca to: " + Arrays.toString((long[]) childJadeNode.getObject("exclusive_mrca")));
					rel.setProperty("exclusive_mrca", (long[]) childJadeNode.getObject("exclusive_mrca")); // switching from holding info for parent to holding info for child

					rel.setProperty("root_exclusive_mrca", graphNodeIdsForInputLeaves.toArray());
					rel.setProperty("licas", licaids);
					inclusiverelationships.add(rel);

/*					System.out.println("checking to make sure the rel doesn't point to its parent");
					
					// check to make sure the parent and child nodes are distinct entities...
					if (rel.getStartNode().getId() == rel.getEndNode().getId()) {
						StringBuffer errbuff = new StringBuffer();
						errbuff.append("error: the node and its child map to the same GoL node.\nTips:\n");
						for (int j = 0; j < inputJadeNode.getTips().size(); j++) {
							errbuff.append(inputJadeNode.getTips().get(j).getName() + "\n");
							errbuff.append("\n");
						}
						if (currGoLNode.hasProperty("name")) {
							errbuff.append(" ancestor taxonomic name: " + currGoLNode.getProperty("name"));
						}
						
						System.out.println("the graph node is: " + rel.getEndNode());
						
						errbuff.append("\nThe tree has been partially imported into the db.\n");
//						throw new TreeIngestException(errbuff.toString());
						throw new IllegalStateException(errbuff.toString());
					}
					System.out.println("passed"); */

					//System.out.println("setting metadata");

					// METADATA ENTRY
					rel.setProperty("source", sourceName);
					rel.setProperty("source_tree_id", treeID);
					
					// TODO this if will cause us to drop 0 length branches. We probably need a "has branch length" flag in JadeNode...
					if (childJadeNode.getBL() > 0.0) {
						rel.setProperty("branch_length", childJadeNode.getBL());
					}
					boolean mrca_rel = false;
					for (Relationship trel: childGoLNode.getRelationships(Direction.OUTGOING, RelType.MRCACHILDOF)) {
						if (trel.getOtherNode(childGoLNode).getId() == currGoLNode.getId()) {
							mrca_rel = true;
							break;
						}
					}
					if (mrca_rel == false) {
						Relationship rel2 = childGoLNode.createRelationshipTo(currGoLNode, RelType.MRCACHILDOF);
						// I'm not sure how this assert could ever trip, given that we create a 
						// childGoLNode -> currGoLNode relationship above and raise an exception
						// if the endpoints have the same ID.
						assert rel2.getStartNode().getId() != rel2.getEndNode().getId();
					}
					updateLICAProperties(childGoLNode); // this function should do the actual loading of mrca properties and updating ancestors/descendants
				}
			}
			long [] relids = new long[inclusiverelationships.size()];
			for (int n = 0; n < inclusiverelationships.size(); n++) {
				relids[n] = inclusiverelationships.get(n).getId();
			}

			for (int n = 0; n < inclusiverelationships.size(); n++) {
				inclusiverelationships.get(n).setProperty("inclusive_relids", relids);
			}
		}

	}
	
	private void updateLICAProperties(Node n) {

		// this function should do the actual loading of mrca properties and updating ancestors/descendants
		
		// all this stuff is currently in the LicaBipartEvaluatorBS class. But it should be here.
		
	}
	
	/**
	 * Add metadata and create index entries for the root of the tree
	 * @param currGoLNode
	 */
	private void addRootProperties(Node currGoLNode) {
		// TODO: this will need to be updated when trees are updated
		System.out.println("placing root in index");
		sourceRootIndex.add(currGoLNode, "rootnode", sourceName);
		if (treeID != null) {
			sourceRootIndex.add(currGoLNode, "rootnodeForID", treeID);
		}
		
		/* Add metadata (if present) from jadetree coming from nexson.

		   STUDY-wide fields used at present (2013 07 24):
			ot:studyPublicationReference - string: ot:studyPublicationReference "long string"
			ot:studyPublication - URI: ot:studyPublication <http://dx.doi.org/...>
			ot:curatorName - string: ot:curatorName "Jane Doe"
			ot:dataDeposit - string: ot:dataDeposit <http://purl.org/phylo/treebase/phylows/study/TB2:S1925>
			ot:studyId - string / integer ot:studyId "123"
			ot:ottId - integer: ot:ottId 783941

		   TREE-wide fields used at present:
			ot:branchLengthMode - string: ot:branchLengthMode "ot:substitutionCount"
			ot:inGroupClade - string: ot:inGroupClade node208482 <- this might not be desired anymore */
		
		// create metadata node
		Node metadataNode = null;
		metadataNode = graphDb.createNode();
		metadataNode.createRelationshipTo(currGoLNode, RelType.METADATAFOR); // TODO: doesn't account for multiple root nodes (I don't think this is true anymore)
		sourceMetaIndex.add(metadataNode, "source", sourceName);

		// set metadata from tree
		metadataNode.setProperty("source", sourceName);
		metadataNode.setProperty("newick", inputTreeNewick);
		metadataNode.setProperty("original_taxa_map", graphNodeIdsForInputLeaves.toArray()); // node ids for the taxon mappings
		if (treeID != null) {
			metadataNode.setProperty("treeID", treeID);
		}
		if (rawNexsonTreeId != null) {
			metadataNode.setProperty("raw_nexson_treeId", rawNexsonTreeId);
		}
		if (studyId != null) {
				metadataNode.setProperty("ot:studyId", studyId);
		}
		if (commitSHA != null) {
				metadataNode.setProperty("commit_SHA", commitSHA);
		}

		// Set metadata from NEXSON
		HashMap<String,Object> assoc = inputTree.getAssoc();
		for (Entry<String, Object> entry : assoc.entrySet()) {
		    String key = entry.getKey();
		    System.out.println("Dealing with metadata property: " + key);
		    Object value = entry.getValue();

		    if (key.startsWith("ot:")) {
		    	System.out.println("Adding property '" + key + "': " + value);

		    	// Note: setProperty() throws IllegalArgumentException if value is of an unsupported type (including null)
				metadataNode.setProperty(key, value);
			}
		}
	}
	
	/**
	 * TODO: update this for the new method
	 * @throws Exception 
	 * @throws MultipleHitsException 
	 */
	public void deleteAllTreesAndReprocess() throws Exception {
		IndexHits<Node> hits  = sourceMetaIndex.query("source", "*");
		System.out.println(hits.size());
		for (Node itrel : hits) {
			String source = (String)itrel.getProperty("source");
			String trees = (String)itrel.getProperty("newick");
			String treeID = (String)itrel.getProperty("treeID");

			String studyId = (String)itrel.getProperty("ot:studyId");
			String treeId = (String)itrel.getProperty("treeId");
			String commitSHA = (String)itrel.getProperty("commitSHA");
			
			deleteTreeBySource(source);
			TreeReader tr = new TreeReader();
			inputTree = tr.readTree(trees);
			inputTree.assocObject("id", treeID);
			
			System.out.println("tree read");
//			setTree(inputTree,trees);
			setTree(inputTree);
			try {
				addSetTreeToGraph("life", source,  false, null);
			} catch (TaxonNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TreeIngestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
		
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Something!");
	}
}