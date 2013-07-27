package opentree;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import jade.tree.*;
import jade.MessageLogger;

import java.lang.StringBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
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

	private JadeTree inputTree; // the jadetree that we are importing; was jt
	private String inputTreeNewick; // original newick string for the inputTree; was treestring
	private String sourceName; // will be recorded in the STREECHILDOF branches we add, and also the metadata node
	private String treeID; // will also be recorded in the db

	private boolean allTreesHaveAllTaxa = false; // this will trigger getalllica if true, otherwise we use the bipartition based code
	private boolean runTestOnly = false; // if set, then nothing is recorded in the db but we run through the process and give lots of output
	
	private ArrayList<Node> updatedNodes;
	private HashSet<Node> updatedSuperLICAs;
	private MessageLogger logger;
	
	private ArrayList<JadeNode> inputJadeTreeLeaves; // just the leaves of the input tree
	// TODO making a Set<Long> or sorted ArrayList<Long> for the ids would make the look ups faster. See comment in testIsMRCA
	private TLongArrayList graphNodeIdsForInputLeaves; // the graph node ids for the nodes matched to the input tree leaves
	private HashMap<JadeNode,Long> jadeNodeToMatchedGraphNodeIdMap; // maps each jade node to the id of the graph node it's mapped to
	private TLongArrayList graphDescendantNodeIdsForInputLeaves; // all the ids of the graph nodes descended from the leaves of the input tree
	private HashMap<JadeNode,ArrayList<Long>> jadeNodeToDescendantGraphNodeIdsMap; // maps each jade node to the node ids in the mrca property of its matched graph node

	private Transaction	tx;
	
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
	 * This will assume that the JadeNodes all have a property set as ot:ottolid
	 * 		that will be the preset ottol id identifier that will be found by index.
	 * 		ALL THE NAMES HAVE TO BE SET FOR THIS FUNCTION
	 *
	 * @param sourcename the name to be registered as the "source" property for every edge in this tree.
	 * @param test don't add to the database
	 */
	public void addSetTreeToGraphWIdsSet(String sourceName, boolean allTreesHaveAllTaxa, boolean runTestOnly, MessageLogger msgLogger) throws TaxonNotFoundException, TreeIngestException {

		this.runTestOnly = runTestOnly;
		this.allTreesHaveAllTaxa = allTreesHaveAllTaxa;
		this.logger = msgLogger;
		this.sourceName = sourceName;

		matchTaxaUsingTaxUIDs();
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
	 * @throws TaxonNotFoundException 
	 * @throws MultipleHitsException 
	 * @todo we probably want a node in the graph representing the tree with an 
	 *		ISROOTOF edge from its root to the tree. We could attach annotations
	 *		about the tree to this node. We have the index of the root node, but
	 *		need to having and isroot would also be helpful. Unless we are indexing
	 *		this we could just randomly choose one of the edges that is connected
	 *		to the root node that is in the index
	 */
	public void addSetTreeToGraph(String focalgroup, String sourceName, boolean allTreesHaveAllTaxa, MessageLogger msgLogger) throws TreeIngestException, MultipleHitsException, TaxonNotFoundException {

		this.runTestOnly = false;
		this.allTreesHaveAllTaxa = allTreesHaveAllTaxa;
		this.logger = msgLogger;
		this.sourceName = sourceName;
		
		matchTaxaUsingNames(focalgroup);
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
		
		inputJadeTreeLeaves = inputTree.getRoot().getTips();
		graphNodeIdsForInputLeaves = new TLongArrayList();  // was ndids
		jadeNodeToMatchedGraphNodeIdMap = new HashMap<JadeNode,Long>(); // was hashnodeids
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
			
			jadeNodeToMatchedGraphNodeIdMap.put(curLeaf, matchedGraphNode.getId());
		}
		gatherInfoForLicaSearches();
	}
	
	/**
	 * Searches the graph for taxa identified using the ottolids stored in the input tree leaves. Will throw exceptions if it
	 * cannot find taxa or finds multiple taxa with the same uid.
	 * @throws TaxonNotFoundException
	 */
	private void matchTaxaUsingTaxUIDs() throws TaxonNotFoundException {
		
		// gather information for each leaf in the input tree prior to starting the import
		// TODO: this could be modified to account for internal node name mapping
		for (JadeNode curLeaf : inputJadeTreeLeaves) {
			
			Long ottolid = (Long) curLeaf.getObject("ot:ottolid");

			// use the ottolid to match to a graph node, or throw an exception if we can't
			Node matchedGraphNode = null;
			IndexHits<Node> hits = null;
			try {
				hits = graphTaxUIDNodeIndex.get("tax_uid", ottolid);
				int numh = hits.size();
				if (numh < 1) {
					throw new TaxonNotFoundException(String.valueOf(ottolid));
				} else if (numh > 1) {
					throw new AmbiguousTaxonException("tax_uid=" + ottolid);
				} else {
					matchedGraphNode = hits.getSingle();
				}
			} finally {
				hits.close();
			}

			jadeNodeToMatchedGraphNodeIdMap.put(curLeaf, matchedGraphNode.getId());
		}
		gatherInfoForLicaSearches();
	}
	
	/**
	 * Prepopulates several container-class instance variables that will be used during lica searching. Called by the
	 * `matchTaxaUsing...` methods, and assumes that the jade node tips have already been matched to graph nodes.
	 */
	private void gatherInfoForLicaSearches() {
		for (Entry<JadeNode, Long> match : jadeNodeToMatchedGraphNodeIdMap.entrySet()) {
			
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
		try {
			tx = graphDb.beginTx();
			if(runTestOnly == false) {
				postOrderAddProcessedTreeToGraph(inputTree.getRoot(), inputTree.getRoot(), sourceName);
			} else {
				postOrderAddProcessedTreeToGraphNoAdd(inputTree.getRoot(), inputTree.getRoot(), sourceName);
			}
			tx.success();
		} finally {
			tx.finish();
		}
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
	private void postOrderAddProcessedTreeToGraph(JadeNode curJadeNode, JadeNode root, String sourcename) throws TreeIngestException {

		// postorder traversal via recursion
		for (int i = 0; i < curJadeNode.getChildCount(); i++) {
			postOrderAddProcessedTreeToGraph(curJadeNode.getChild(i), root, sourcename);
		}

		if (curJadeNode.getChildCount() == 0) { // this is a tip, not much to do here
			HashSet<Node> nar = new HashSet<Node>();
			nar.add(graphDb.getNodeById(jadeNodeToMatchedGraphNodeIdMap.get(curJadeNode)));
			curJadeNode.assocObject("dbnodes", nar);

		} else { // this is an internal node

			// NOTE: the following several variables contain similar information in different combinations and formats.
			// They are used to optimize the LICA searches by attempting to perform less exhaustive tests whenever possible.
			// For ease of interpretation, the variable names are constructed using the names of other related variables.
			// An underscore in the name indicates that what follows is a direct reference to another variable (with that name).
			
			// the nodes mapped to the descendant tips of the current node in the input tree
			ArrayList<Node> graphNodesMappedToDescendantLeavesOfThisJadeNode = new ArrayList<Node>();

			// the graph nodes corresponding to all the mrca descendants of the nodes mapped to all the tips descended from the current node in the input tree
			ArrayList<Node> graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode = new ArrayList<Node>();

			// the node ids of the nodes in graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode
			TLongArrayList nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode = new TLongArrayList();

			for (JadeNode curLeaf : inputJadeTreeLeaves) {

				// get all the graph nodes for the jade tree leaves of this jade node
				graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphDb.getNodeById(jadeNodeToMatchedGraphNodeIdMap.get(curLeaf)));

				// get the node ids for all the mrca descendants of the current graph node (same info as the mrca field from the graph node we matched to this jadenode)
				ArrayList<Long> descendantGraphNodeIdsForCurLeaf = jadeNodeToDescendantGraphNodeIdsMap.get(curLeaf);

				// get all the ids from the mrca fields of the graph nodes mapped to all the jade tree leaves descended from this jade node in the input tree
				nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.addAll(descendantGraphNodeIdsForCurLeaf);

				// also remember all the nodes themselves for for mrca descendant ids that we encounter
				for (Long descId : descendantGraphNodeIdsForCurLeaf) {
					graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphDb.getNodeById(descId));
				}
			}
			nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.sort();

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
			
			// get the outgroup ids, which is the set of mrca descendent ids for all the graph nodes mapped to jade nodes in the
			// input tree that are *not* descended from the current jade node
			TLongArrayList licaOutgroupDescendantIdsForCurrentJadeNode = new TLongArrayList();
			for (int i = 0; i < graphNodeIdsForInputLeaves.size(); i++) {
				if(licaDescendantIdsForCurrentJadeNode_Hash.contains(graphNodeIdsForInputLeaves.getQuick(i))==false) {
					licaOutgroupDescendantIdsForCurrentJadeNode.addAll((long[])graphDb.getNodeById(graphNodeIdsForInputLeaves.get(i)).getProperty("mrca"));
				}
			}
			
			// in case of multiple, partially overlapping lica mappings (?), make sure we don't put any ingroup descendants in the outgroup
			licaOutgroupDescendantIdsForCurrentJadeNode.removeAll(licaDescendantIdsForCurrentJadeNode);
			licaOutgroupDescendantIdsForCurrentJadeNode.sort();

			// find all the compatible lica mappings for this jade node to existing graph nodes
			HashSet<Node> licaMatches = null;
			if(allTreesHaveAllTaxa == true) { // use a simpler calculation if we can assume that all trees have completely overlapping taxon sampling (including taxonomy)
				licaMatches = LicaUtil.getAllLICAt4j(graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						licaDescendantIdsForCurrentJadeNode,
						licaOutgroupDescendantIdsForCurrentJadeNode);

			} else { // when taxon sets don't completely overlap, the lica calculator needs more info
				licaMatches = LicaUtil.getBipart4j(graphNodesMappedToDescendantLeavesOfThisJadeNode,
						graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						licaDescendantIdsForCurrentJadeNode,
						licaOutgroupDescendantIdsForCurrentJadeNode, graphDb);
			}
						
			//			_LOG.trace("ancestor "+ancestor);
			// _LOG.trace(ancestor.getProperty("name"));

			if (licaMatches.size() > 0) { // if we found any compatible mrca mappings to nodes already in the graph

				// remember all the lica mappings
				curJadeNode.assocObject("dbnodes", licaMatches);

				// remember the ids of the graph nodes mapped to all the jade tree leaves descended from this jade node
				long[] nodeIdsFor_graphNodesMappedToDescendantLeavesOfThisJadeNode = new long[graphNodesMappedToDescendantLeavesOfThisJadeNode.size()];
				for (int i = 0; i < graphNodesMappedToDescendantLeavesOfThisJadeNode.size(); i++) {
					nodeIdsFor_graphNodesMappedToDescendantLeavesOfThisJadeNode[i] = graphNodesMappedToDescendantLeavesOfThisJadeNode.get(i).getId();
				}
				Arrays.sort(nodeIdsFor_graphNodesMappedToDescendantLeavesOfThisJadeNode);
				curJadeNode.assocObject("exclusive_mrca", nodeIdsFor_graphNodesMappedToDescendantLeavesOfThisJadeNode);
								
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
				//System.out.println("\t\tmrca: "+childndids);

				// set outmrcas
				newLicaNode.setProperty("outmrca", licaOutgroupDescendantIdsForCurrentJadeNode.toArray());
				//System.out.println("\t\toutmrca: "+outndids);

				// set exclusive relationships
				long[] rete = new long[graphNodesMappedToDescendantLeavesOfThisJadeNode.size()];
				for (int j = 0; j < graphNodesMappedToDescendantLeavesOfThisJadeNode.size(); j++) {
					rete[j] = graphNodesMappedToDescendantLeavesOfThisJadeNode.get(j).getId();
				}
				Arrays.sort(rete);
				curJadeNode.assocObject("exclusive_mrca", rete);
				
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

			// now related nodes are prepared and we have the information we need to make relationships
			addProcessedNodeRelationships(curJadeNode);
		} 
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
	private void postOrderAddProcessedTreeToGraphNoAdd(JadeNode inode, JadeNode root, String sourcename) throws TreeIngestException {
		// postorder traversal via recursion
		for (int i = 0; i < inode.getChildCount(); i++) {
			postOrderAddProcessedTreeToGraphNoAdd(inode.getChild(i), root, sourcename);
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
				hit_nodes.add(graphDb.getNodeById(jadeNodeToMatchedGraphNodeIdMap.get(nds.get(j))));
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

			//add all the children of the mapped nodes to the outgroup as well
			for (int i = 0; i < graphNodeIdsForInputLeaves.size(); i++) {
				if (childndids.contains(graphNodeIdsForInputLeaves.getQuick(i)) == false) {
					outndids.addAll((long[])graphDb.getNodeById(graphNodeIdsForInputLeaves.get(i)).getProperty("mrca"));
				}
			}
			childndids.sort();
			outndids.removeAll(childndids);
			outndids.sort();

			HashSet<Node> ancestors = null;
			/*
			 * we can use a simpler calculation if we can assume that the 'trees that come in 
			 * are complete in their taxa
			 */
			if (allTreesHaveAllTaxa) {
				ancestors = LicaUtil.getAllLICAt4j(hit_nodes_search, childndids, outndids);
			} else {
				ancestors = LicaUtil.getBipart4j(hit_nodes,hit_nodes_search, hit_nodes_small_search,childndids, outndids,graphDb);
			}
			for (Node tnd : ancestors) {
				if (tnd.hasProperty("name")) {
					logger.indentMessageStrStr(3, "matched anc", "node", tnd.toString(), "name", (String)tnd.getProperty("name"));
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

		HashSet<Node> allGraphNodesMappedToThisJadeNode = (HashSet<Node>) (inputJadeNode.getObject("dbnodes")); // attempting to switch from array to hashset
		
		// preload the licaids to be stored in rels
		long [] licaids = new long[allGraphNodesMappedToThisJadeNode.size()];
		int m = 0;
		for (Node golNode : allGraphNodesMappedToThisJadeNode) {
			licaids[m] = golNode.getId();
		}

		// for use if this node will be an incluchildof and we want to store the relationships for faster retrieval
		ArrayList<Relationship> inclusiverelationships = new ArrayList<Relationship>();
		for (Node currGoLNode : allGraphNodesMappedToThisJadeNode) {

			// add the root index for the source trail
			if (inputJadeNode.isTheRoot()) {
				
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
					ot:ottolid - integer: ot:ottolid 783941

				   TREE-wide fields used at present:
					ot:branchLengthMode - string: ot:branchLengthMode "ot:substitutionCount"
					ot:inGroupClade - string: ot:inGroupClade node208482 <- this might not be desired anymore */
				
				// create metadata node
				Node metadataNode = null;
				metadataNode = graphDb.createNode();
				metadataNode.createRelationshipTo(currGoLNode, RelType.METADATAFOR); // TODO: doesn't account for multiple root nodes (I don't think this is true anymore)
				sourceMetaIndex.add(metadataNode, "source", sourceName);

				// set metadat from tree
				metadataNode.setProperty("source", sourceName);
				metadataNode.setProperty("newick", inputTreeNewick);
				metadataNode.setProperty("original_taxa_map", graphNodeIdsForInputLeaves.toArray()); // node ids for the taxon mappings
				if (treeID != null) {
					metadataNode.setProperty("treeID", treeID);
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

			for (JadeNode childJadeNode : inputJadeNode.getChildren()) {
				HashSet<Node> allChildGoLNodes = (HashSet<Node>)(childJadeNode.getObject("dbnodes"));

				for (Node childGoLNode : allChildGoLNodes) {
					Relationship rel = childGoLNode.createRelationshipTo(currGoLNode, RelType.STREECHILDOF);
					sourceRelIndex.add(rel, "source", sourceName);
					rel.setProperty("exclusive_mrca", inputJadeNode.getObject("exclusive_mrca"));

					rel.setProperty("root_exclusive_mrca", graphNodeIdsForInputLeaves.toArray());
					rel.setProperty("licas", licaids);
					inclusiverelationships.add(rel);

					// check to make sure the parent and child nodes are distinct entities...
					if (rel.getStartNode().getId() == rel.getEndNode().getId()) {
						StringBuffer errbuff = new StringBuffer();
						errbuff.append("A node and its child map to the same GoL node.\nTips:\n");
						for (int j = 0; j < inputJadeNode.getTips().size(); j++) {
							errbuff.append(inputJadeNode.getTips().get(j).getName() + "\n");
							errbuff.append("\n");
						}
						if (currGoLNode.hasProperty("name")) {
							errbuff.append(" ancestor taxonomic name: " + currGoLNode.getProperty("name"));
						}
						errbuff.append("\nThe tree has been partially imported into the db.\n");
						throw new TreeIngestException(errbuff.toString());
					}

					// METADATA ENTRY
					rel.setProperty("source", sourceName);
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
				}
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
	
	/**
	 * TODO: update this for the new method
	 * @throws MultipleHitsException 
	 */
	public void deleteAllTreesAndReprocess() {
		IndexHits<Node> hits  = sourceMetaIndex.query("source", "*");
		System.out.println(hits.size());
		for (Node itrel : hits) {
			String source = (String)itrel.getProperty("source");
			String trees = (String)itrel.getProperty("newick");
			String treeID = (String)itrel.getProperty("treeID");
			deleteTreeBySource(source);
			TreeReader tr = new TreeReader();
			inputTree = tr.readTree(trees);
			inputTree.assocObject("id", treeID);
			System.out.println("tree read");
//			setTree(inputTree,trees);
			setTree(inputTree);
			try {
				addSetTreeToGraph("life",source,false, null);
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