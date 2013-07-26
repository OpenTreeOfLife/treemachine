package opentree;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import jade.tree.*;
import jade.MessageLogger;

import java.lang.StringBuffer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import opentree.constants.RelType;
import opentree.exceptions.AmbiguousTaxonException;
import opentree.exceptions.MultipleHitsException;
import opentree.exceptions.TaxonNotFoundException;
import opentree.exceptions.TreeIngestException;
//import opentree.RelTypes;

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
//import org.apache.log4j.Logger;

import scala.actors.threadpool.Arrays;

/**
 * GraphImporter is intended to control the initial creation 
 * and addition of trees to the tree graph.
 */

public class GraphImporter extends GraphBase {
	//static Logger _LOG = Logger.getLogger(GraphImporter.class);

	private int transaction_iter = 100000;
	private int cur_tran_iter = 0;
	private JadeTree jt;
	private String treestring; // original newick string for the jt
	private ArrayList<Node> updatedNodes;
	private HashSet<Node> updatedSuperLICAs;
	private Transaction	tx;
	//THIS IS FOR PERFORMANCE
	private TLongArrayList root_ndids;
	boolean allTreesHaveAllTaxa = false;//this will trigger getalllica if true (getbipart otherwise)
	
	

	// MOVED TO INSTANCE VARIABLES FROM addSetTreeToGraphWithIdsSet
	private ArrayList<JadeNode> inputJadeTreeLeaves; // = jt.getRoot().getTips();
	/* TODO making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
	  would make the look ups faster. See comment in testIsMRCA */
	private TLongArrayList idsForGraphNodesMatchedToInputLeaves; // = new TLongArrayList(); 
	// We'll map each Jade node to the internal ID of its taxonomic node.
	private HashMap<JadeNode,Long> jadeNodeToMatchedGraphNodeIdMap; // = new HashMap<JadeNode,Long>();
	// same as above but added for nested nodes, so more comprehensive and 
	//		used just for searching. the others are used for storage
	//HashSet<Long> ndidssearch = new HashSet<Long>();
	private TLongArrayList descendantIdsForAllTreeLeaves; // = new TLongArrayList();
	private HashMap<JadeNode,ArrayList<Long>> jadeNodeToDescendantGraphNodeIdsMap; // = new HashMap<JadeNode,ArrayList<Long>>();
	// this loop fills ndids and hashnodeids or throws an Exception (for 
	//		errors in matching leaves to the taxonomy). No other side effects.
	// END MOVING TO INSTANCE VARIABLES

	
	
	public GraphImporter(String graphName) {
		super(graphName);
//		graphDb = new GraphDatabaseAgent(graphname);
//		this.initializeIndices();
	}

	public GraphImporter(EmbeddedGraphDatabase eg) {
		super(eg);
//		graphDb = new GraphDatabaseAgent(graphn);
//		this.initializeIndices();
	}
	
	public GraphImporter(GraphDatabaseService gs) {
		super(gs);
	}
	
	public GraphImporter(GraphDatabaseAgent gdb) {
		super(gdb);
//		graphDb = graphn;
//		this.initializeIndices();
	}
	
	/*
	 * Helper function called by constructors so that we can update the list of indices in one place.
	 *
	private void initializeIndices() {
		graphNodeIndex = graphDb.getNodeIndex( "graphNamedNodes" ); // name is the key
		graphTaxUIDNodeIndex = graphDb.getNodeIndex( "graphTaxUIDNodes" ); // tax_uid is the key
		synTaxUIDNodeIndex = graphDb.getNodeIndex("synTaxUIDNodes");
		synNodeIndex = graphDb.getNodeIndex("graphNamedNodesSyns");
		sourceRelIndex = graphDb.getRelIndex("sourceRels");
		sourceRootIndex = graphDb.getNodeIndex("sourceRootNodes");
		sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
	} */

	public boolean hasSoureTreeName(String sourcename) {
		IndexHits<Node> hits = sourceRootIndex.get("rootnode", sourcename);
		return (hits != null && hits.size() > 0);
	}
	/**
	 * Sets the jt member by reading a JadeTree from filename.
	 *
	 * This currently reads a tree from a file but this will need to be changed to 
	 * another form later
	 * @param filename name of file with a newick tree representation
	 */
	public void preProcessTree(String filename, String treeID) {
		// read the tree from a file
		String ts = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			ts = br.readLine();
			treestring = ts;
			br.close();
		} catch(IOException ioe) {}
		TreeReader tr = new TreeReader();
		jt = tr.readTree(ts);
		jt.assocObject("id", treeID);
		System.out.println("tree read");
		// System.exit(0);
	}
	
	/**
	 * Sets the jt member by a JadeTree already read and processed.
	 *
	 * @param JadeTree object
	 */
	public void setTree(JadeTree tree) {
		jt = tree;
		treestring = jt.getRoot().getNewick(true) + ";";
		System.out.println("tree set");
		initialize();
	}
	
	public void setTree(JadeTree tree, String ts) {
		jt = tree;
		treestring = ts;
		System.out.println("tree set");
		initialize();
	}

	/**
	 * Resets instance variables used to contain information about the input tree that is used during import.
	 */
	private void initialize() {
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();

		// newly added, just initializing instance variables
		inputJadeTreeLeaves = jt.getRoot().getTips();
		idsForGraphNodesMatchedToInputLeaves = new TLongArrayList(); 
		jadeNodeToMatchedGraphNodeIdMap = new HashMap<JadeNode,Long>();
		descendantIdsForAllTreeLeaves = new TLongArrayList();
		jadeNodeToDescendantGraphNodeIdsMap = new HashMap<JadeNode,ArrayList<Long>>();
		// end newly added instance var init
		
	}
	
	/**
	 * Ingest the current JadeTree (in the jt data member) to the GoL.
	 *
	 * This will assume that the JadeNodes all have a property set as ot:ottolid
	 * 		that will be the preset ottol id identifier that will be found by index.
	 * 		ALL THE NAMES HAVE TO BE SET FOR THIS FUNCTION
	 *
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @param test don't add to the database
	 */
	public void addSetTreeToGraphWIdsSet(String sourcename, boolean allTreesHaveAllTaxa, boolean test, MessageLogger msgLogger) throws TaxonNotFoundException, TreeIngestException {

		this.allTreesHaveAllTaxa = allTreesHaveAllTaxa;

		/* MOVED TO INITIALIZE FUNCTION
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();

		// newly added, just initializing instance variables
		inputJadeTreeLeaves = jt.getRoot().getTips();
		ndids = new TLongArrayList(); 
		hashnodeids = new HashMap<JadeNode,Long>();
		ndidssearch = new TLongArrayList();
		hashnodeidssearch = new HashMap<JadeNode,ArrayList<Long>>();
		// end newly added instance var init
		 END MOVED TO INITIALIZE FUNCTION */
		
		/* MOVING THESE TO INSTANCE VARIABLES
		ArrayList<JadeNode> nds = jt.getRoot().getTips();
		/* TODO making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
		  would make the look ups faster. See comment in testIsMRCA *
		TLongArrayList ndids = new TLongArrayList(); 
		// We'll map each Jade node to the internal ID of its taxonomic node.
		HashMap<JadeNode,Long> hashnodeids = new HashMap<JadeNode,Long>();
		// same as above but added for nested nodes, so more comprehensive and 
		//		used just for searching. the others are used for storage
		//HashSet<Long> ndidssearch = new HashSet<Long>();
		TLongArrayList ndidssearch = new TLongArrayList();
		HashMap<JadeNode,ArrayList<Long>> hashnodeidssearch = new HashMap<JadeNode,ArrayList<Long>>();
		// END MOVING TO INSTANCE VARIABLES
		 */

		// this loop fills ndids and hashnodeids or throws an Exception (for 
		//		errors in matching leaves to the taxonomy). No other side effects.
		// TODO: this could be modified to account for internal node name mapping
//		for (int j = 0; j < inputJadeTreeLeaves.size(); j++) {
		for (JadeNode curLeaf : inputJadeTreeLeaves) {
			
//			Long ottolid = (Long) inputJadeTreeLeaves.get(j).getObject("ot:ottolid");
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

			// add all the descendant Ids to containers for easy access during import
			// added for nested nodes 
			long [] mrcaArray = (long[]) matchedGraphNode.getProperty("mrca");
			ArrayList<Long> descendantIdsForCurMatchedGraphNode = new ArrayList<Long>(); 
			for (int k = 0; k < mrcaArray.length; k++) {
				descendantIdsForAllTreeLeaves.add(mrcaArray[k]);
				descendantIdsForCurMatchedGraphNode.add(mrcaArray[k]);
			}
//			jadeNodeToDescendantGraphNodeIdsMap.put(inputJadeTreeLeaves.get(j), descendantIdsForCurMatchedGraphNode);
			jadeNodeToDescendantGraphNodeIdsMap.put(curLeaf, descendantIdsForCurMatchedGraphNode);
			idsForGraphNodesMatchedToInputLeaves.add(matchedGraphNode.getId());
//			jadeNodeToMatchedGraphNodeIdMap.put(inputJadeTreeLeaves.get(j), matchedGraphNode.getId());
			jadeNodeToMatchedGraphNodeIdMap.put(curLeaf, matchedGraphNode.getId());
		}
		
		// Store the list of taxonomic IDs and the map of JadeNode to ID in the root.
		//jt.getRoot().assocObject("ndids", ndids);
		jt.getRoot().assocObject("hashnodeids", jadeNodeToMatchedGraphNodeIdMap); // might not need to store this, just use the instance variable in other methods

		descendantIdsForAllTreeLeaves.sort();
		jt.getRoot().assocObject("ndidssearch", descendantIdsForAllTreeLeaves);
		jt.getRoot().assocObject("hashnodeidssearch", jadeNodeToDescendantGraphNodeIdsMap); // might not need to store this, just use the instance variable in other methods
		
		// if neither of these things ever get changed after this assignment, then one is redundant and should be removed
		idsForGraphNodesMatchedToInputLeaves.sort();
		root_ndids = idsForGraphNodesMatchedToInputLeaves;

		try {
			tx = graphDb.beginTx();
			if(test == false) {
				postOrderAddProcessedTreeToGraph(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"), msgLogger);
			} else {
				postOrderAddProcessedTreeToGraphNoAdd(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"), msgLogger);
			}
			tx.success();
		} finally {
			tx.finish();
		}
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
	 * @todo we probably want a node in the graph representing the tree with an 
	 *		ISROOTOF edge from its root to the tree. We could attach annotations
	 *		about the tree to this node. We have the index of the root node, but
	 *		need to having and isroot would also be helpful. Unless we are indexing
	 *		this we could just randomly choose one of the edges that is connected
	 *		to the root node that is in the index
	 */
	public void addSetTreeToGraph(String focalgroup,
								  String sourcename,
								  boolean taxacompletelyoverlap,
								  MessageLogger msgLogger) throws TaxonNotFoundException, TreeIngestException, MultipleHitsException {
		boolean test = false;
		Node focalnode = findTaxNodeByName(focalgroup);
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();
		allTreesHaveAllTaxa = taxacompletelyoverlap;
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelType.TAXCHILDOF, Direction.OUTGOING), 1000);
		ArrayList<JadeNode> nds = jt.getRoot().getTips();

		// TODO: could take this out and make it a separate procedure
		/* TODO making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
		  would make the look ups faster. See comment in testIsMRCA */
		TLongArrayList ndids = new TLongArrayList(); 
		// We'll map each Jade node to the internal ID of its taxonomic node.
		HashMap<JadeNode,Long> hashnodeids = new HashMap<JadeNode,Long>();
		// same as above but added for nested nodes, so more comprehensive and 
		//		used just for searching. the others are used for storage
		//HashSet<Long> ndidssearch = new HashSet<Long>();
		TLongArrayList ndidssearch = new TLongArrayList();
		HashMap<JadeNode,ArrayList<Long>> hashnodeidssearch = new HashMap<JadeNode,ArrayList<Long>>();
		// this loop fills ndids and hashnodeids or throws an Exception (for 
		//		errors in matching leaves to the taxonomy). No other side effects.
		// TODO: this could be modified to account for internal node name mapping
		for (int j = 0; j < nds.size(); j++) {
			// find all the tip taxa and with doubles pick the taxon closest to the focal group
			Node hitnode = null;
			String processedname = nds.get(j).getName(); //.replace("_", " ");
			// TODO processing syntactic rules like '_' -> ' ' should be done on input parsing. 
			IndexHits<Node> hits = graphNodeIndex.get("name", processedname);
			int numh = hits.size();
			if (numh == 1) {
				hitnode = hits.getSingle();
			} else if (numh > 1) {
				msgLogger.indentMessageIntStr(2, "multiple graphNamedNodes hits", "number of hits", numh, "name", processedname);
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
						msgLogger.indentMessageStr(3, "graphNamedNodes hit not within focalgroup", "focalgroup", focalgroup);
					}
				}
				assert shortn != null; // TODO this could happen if there are multiple hits outside the focalgroup, and none inside the focalgroup.  We should develop an AmbiguousTaxonException class
				hitnode = shortn;
			}
			hits.close();
			if (hitnode == null) {
				assert numh == 0;
				throw new TaxonNotFoundException(processedname);
			}
			// added for nested nodes 
			long [] mrcas = (long[])hitnode.getProperty("mrca");
			ArrayList<Long> tset = new ArrayList<Long>(); 
			for (int k = 0; k < mrcas.length; k++) {
				ndidssearch.add(mrcas[k]);
				tset.add(mrcas[k]);
			}

			hashnodeidssearch.put(nds.get(j), tset);
			ndids.add(hitnode.getId());
			hashnodeids.put(nds.get(j), hitnode.getId());
		}
		// Store the list of taxonomic IDs and the map of JadeNode to ID in the root.
		//jt.getRoot().assocObject("ndids", ndids);
		jt.getRoot().assocObject("hashnodeids", hashnodeids);
		ndidssearch.sort();
		jt.getRoot().assocObject("ndidssearch", ndidssearch);
		jt.getRoot().assocObject("hashnodeidssearch", hashnodeidssearch);
		ndids.sort();
		root_ndids = ndids;
		try {
			tx = graphDb.beginTx();
			if(test == false) {
				postOrderAddProcessedTreeToGraph(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"), msgLogger);
			} else {
				postOrderAddProcessedTreeToGraphNoAdd(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"), msgLogger);
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
	private void postOrderAddProcessedTreeToGraph(JadeNode curJadeNode,
												  JadeNode root,
												  String sourcename,
												  String treeID,
												  MessageLogger msgLogger) throws TreeIngestException {
		// postorder traversal via recursion
		for (int i = 0; i < curJadeNode.getChildCount(); i++) {
			postOrderAddProcessedTreeToGraph(curJadeNode.getChild(i), root, sourcename, treeID, msgLogger);
		}
		//		_LOG.trace("children: "+inode.getChildCount());
		// roothash are the actual ids with the nested names -- used for storing
		// roothashsearch are the ids with nested exploded -- used for searching
//		HashMap<JadeNode, Long> roothash = ((HashMap<JadeNode, Long>)root.getObject("hashnodeids"));
//		HashMap<JadeNode, ArrayList<Long>> roothashsearch = ((HashMap<JadeNode, ArrayList<Long>>)root.getObject("hashnodeidssearch"));

		if (curJadeNode.getChildCount() > 0) {
//			System.out.println(inode.getNewick(false));
//			ArrayList<JadeNode> nds = inode.getTips();
			
			// NOTE: the following several variables contain similar information in different combinations
			// and formats. This information (in these various formats) is used to optimize the LICA
			// searches by attempting to perform less exhaustive tests whenever possible. For ease of
			// interpretation, these variable names are constructed using the names of other variables
			// to which they are related. An underscore in the name indicates that what follows is a direct
			// reference to another variable (using the exact name of the referenced variable.
			
			// the nodes mapped to the descendant tips of the current node in the input tree
			ArrayList<Node> graphNodesMappedToDescendantLeavesOfThisJadeNode = new ArrayList<Node>();

			// the nodes corresponding to the node ids in all the mrca descendants of the nodes mapped to all the tips descended from the current node in the input tree
			ArrayList<Node> graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode = new ArrayList<Node>();

			// the node ids of the nodes in all_graph_nodes_descendant_from_this_jade_nodes_descendant_leaves
			TLongArrayList nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode = new TLongArrayList();

			// store the hits for each of the leaves in the input tree
//			for (int j = 0; j < nds.size(); j++) {
			for (JadeNode curLeaf : inputJadeTreeLeaves) {

				// get all the graph nodes for the jade tree leaves of this jade node
				graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphDb.getNodeById(jadeNodeToMatchedGraphNodeIdMap.get(curLeaf))); // attempt to use instance var directly instead of re-accessing stored object
//				hit_nodes.add(graphDb.getNodeById(roothash.get(nds.get(j)))); // was

				// get the node ids for all the mrca descendants of the current graph node (essentially the mrca field from the graph ndoe we matched to this jadenode)
				ArrayList<Long> descendantGraphNodeIdsForCurLeaf = jadeNodeToDescendantGraphNodeIdsMap.get(curLeaf); // attempt to use instance var directly instead of re-accessing stored object
//				ArrayList<Long> tlist = roothashsearch.get(nds.get(j)); // was

				// get all the ids from the mrca fields of the graph nodes mapped to all the jade tree leaves descended from this jade node in the input tree
				nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.addAll(descendantGraphNodeIdsForCurLeaf);
				for (Long descId : descendantGraphNodeIdsForCurLeaf) {
//				for (int k = 0; k < descendantGraphNodeIdsForCurLeaf.size(); k++) { // was
					
					// also remember all the nodes themselves for for mrca descendant ids that we encounter
					graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphDb.getNodeById(descId));
//					all_graph_nodes_descendant_from_this_jade_nodes_descendant_leaves.add(graphDb.getNodeById(descendantGraphNodeIdsForCurLeaf.get(k))); // was
				}
			}
			nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.sort();

			// get all the childids even if they aren't in the tree, this is the postorder part

			// Get the union of all node ids from the mrca properties (i.e. descendant node ids) for every graph node mapped as a lica to every jade node
			// child of the current jade node. This accumulates descendant node ids as the postorder traversal moves down the input tree toward the root.
			TLongHashSet licaDescendantIdsForCurrentJadeNode_Hash = new TLongHashSet(); // use a hashset to avoid duplicate entries
			for (JadeNode childNode : curJadeNode.getChildren()) {
//			for (int i = 0; i < curJadeNode.getChildCount(); i++) { // was
				// TODO: question: why is dbnodes an array instead of a collection? it is originally a hashset of the ancestors, we could just store that
				Node [] childNodeLicaMappings = (Node []) childNode.getObject("dbnodes"); // the graph nodes for the mrca mappings for this child node
//				Node [] dbnodesob = (Node [])curJadeNode.getChild(i).getObject("dbnodes"); // was
				for (int k = 0; k < childNodeLicaMappings.length; k++) {
					// TODO: question: again with the array vs. hashset, just a ref here in case it is changed
					licaDescendantIdsForCurrentJadeNode_Hash.addAll((long[]) childNodeLicaMappings[k].getProperty("mrca"));
				}
			}

			// convert hashset to arraylist so we can sort it
			TLongArrayList licaDescendantIdsForCurrentJadeNode = new TLongArrayList(licaDescendantIdsForCurrentJadeNode_Hash);
			licaDescendantIdsForCurrentJadeNode.sort();		// replacing this duplicate variable
			
			// ***** all of the node ids from the mrca 
			//			_LOG.trace("finished names");
//			TLongArrayList rootids = new TLongArrayList((TLongArrayList) root.getObject("ndidssearch")); // unused, unclear what it is for

			// put together the outgroup ids, which are the set of 
			TLongArrayList licaOutgroupDescendantIdsForCurrentJadeNode = new TLongArrayList();
			//add all the children of the mapped nodes to the outgroup as well
			for (int i = 0; i < root_ndids.size(); i++) {
				if(licaDescendantIdsForCurrentJadeNode.contains(root_ndids.getQuick(i))==false)
					licaOutgroupDescendantIdsForCurrentJadeNode.addAll((long[])graphDb.getNodeById(root_ndids.get(i)).getProperty("mrca"));
			}
			licaDescendantIdsForCurrentJadeNode.sort();
			licaOutgroupDescendantIdsForCurrentJadeNode.removeAll(licaDescendantIdsForCurrentJadeNode);
			licaOutgroupDescendantIdsForCurrentJadeNode.sort();

			// find all the compatible lica mappings for this jade node to existing graph nodes
			HashSet<Node> ancestors = null;
			if(allTreesHaveAllTaxa == true) { // use a simpler calculation if we can assume that all trees have completely overlapping taxon sampling (including taxonomy)
				ancestors = LicaUtil.getAllLICAt4j(graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode, licaDescendantIdsForCurrentJadeNode, licaOutgroupDescendantIdsForCurrentJadeNode);

			} else { // when taxon sets don't completely overlap, the lica calculator needs more info
				ancestors = LicaUtil.getBipart4j(graphNodesMappedToDescendantLeavesOfThisJadeNode, graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
												 nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode, licaDescendantIdsForCurrentJadeNode, licaOutgroupDescendantIdsForCurrentJadeNode, graphDb);
				// QUESTION:
				// is this where we update the ancestors with the full lica information? (make sure to check short first)
			}
						
			//			_LOG.trace("ancestor "+ancestor);
			// _LOG.trace(ancestor.getProperty("name"));

			if (ancestors.size() > 0) { // if we found any compatible mrca mappings to nodes already in the graph

				// remember all the lica mappings
				// TODO: question: why is dbnodes an array instead of just storing the hashset
				curJadeNode.assocObject("dbnodes", ancestors.toArray(new Node[ancestors.size()])); // use preset size to avoid reflection call when converting to array

				// get all the ids of the graph nodes mapped to the jade tree leaves descended from this jade node
				long[] ret = new long[graphNodesMappedToDescendantLeavesOfThisJadeNode.size()];
				for (int i = 0; i < graphNodesMappedToDescendantLeavesOfThisJadeNode.size(); i++) {
					ret[i] = graphNodesMappedToDescendantLeavesOfThisJadeNode.get(i).getId();
				}

				Arrays.sort(ret);
				
				// contains the ids of just the graph nodes mapped to leaves in the jade tree descended from this jade node
				curJadeNode.assocObject("exclusive_mrca", ret);
				
				// contains the ids of the graph nodes mapped to all the leaves in the jade tree
				// QUESTION: this is the same for every node? should we just reference this from the instance variable every time we need it instead of storing duplicate copies in each node?
				curJadeNode.assocObject("root_exclusive_mrca", root_ndids.toArray());

			} else { // if there were no compatible lica mappings found for this jade node, then we need to make a new one

				// steps for making a new graph node to map this jade node onto
				// 1. create an new node in the graph
				// 2. store the mrca information in the graph node properties
				// 3. assoc the jade node with the new graph node

				// first get the super licas, which is what would be the licas if we didn't have the other taxa in the tree
				// this will be used to connect the new nodes to their licas for easier traversals
				HashSet<Node> superlicas = LicaUtil.getSuperLICAt4j(graphNodesMappedToDescendantLeavesOfThisJadeNode,
						graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode, nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode, licaDescendantIdsForCurrentJadeNode);
				//HashSet<Node> superlica = LicaUtil.getSuperLICA(hit_nodes_search, childndids); // much earlier version of this
				//System.out.println("\t\tsuperlica: "+superlica);

				// step 1
				Node newLicaNode = graphDb.createNode();
				//System.out.println("\t\tnewnode: "+dbnode);

				// the dbnodes array contains compatible lica mappings. there is the only here (the node we are making)
				// TODO: question: why is dbnodes an array instead of just storing the hashset?
				Node [] nar = {newLicaNode};
				curJadeNode.assocObject("dbnodes", nar);

				newLicaNode.setProperty("mrca", licaDescendantIdsForCurrentJadeNode.toArray());

				//System.out.println("\t\tmrca: "+childndids);
				//set outmrcas
				newLicaNode.setProperty("outmrca",licaOutgroupDescendantIdsForCurrentJadeNode.toArray());
				//System.out.println("\t\toutmrca: "+outndids);
				//set exclusive relationships
				long[] rete = new long[graphNodesMappedToDescendantLeavesOfThisJadeNode.size()];
				for (int j = 0; j < graphNodesMappedToDescendantLeavesOfThisJadeNode.size(); j++) {
					rete[j] = graphNodesMappedToDescendantLeavesOfThisJadeNode.get(j).getId();
				}
				Arrays.sort(rete);
				curJadeNode.assocObject("exclusive_mrca",rete);
				root_ndids.sort();
				curJadeNode.assocObject("root_exclusive_mrca",root_ndids.toArray());
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
			addProcessedNodeRelationships(curJadeNode, sourcename, treeID);
		} else {
//			inode.assocObject("dbnode", graphDb.getNodeById(roothash.get(inode)));
//			Node [] nar = {graphDb.getNodeById(roothash.get(inode))};

			Node [] nar = {graphDb.getNodeById(jadeNodeToMatchedGraphNodeIdMap.get(curJadeNode))};
			
			curJadeNode.assocObject("dbnodes", nar);
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
	private void postOrderAddProcessedTreeToGraphNoAdd(JadeNode inode, 
													   JadeNode root,
													   String sourcename,
													   String treeID,
													   MessageLogger msgLogger) throws TreeIngestException {
		// postorder traversal via recursion
		for (int i = 0; i < inode.getChildCount(); i++) {
			postOrderAddProcessedTreeToGraphNoAdd(inode.getChild(i), root, sourcename, treeID, msgLogger);
		}
		//		_LOG.trace("children: "+inode.getChildCount());
		// roothash are the actual ids with the nested names -- used for storing
		// roothashsearch are the ids with nested exploded -- used for searching
		HashMap<JadeNode, Long> roothash = ((HashMap<JadeNode, Long>)root.getObject("hashnodeids"));
		HashMap<JadeNode, ArrayList<Long>> roothashsearch = ((HashMap<JadeNode, ArrayList<Long>>)root.getObject("hashnodeidssearch"));

		if (inode.getChildCount() > 0) {
			msgLogger.indentMessageStr(2, "subtree", "newick", inode.getNewick(false));
			ArrayList<JadeNode> nds = inode.getTips();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			ArrayList<Node> hit_nodes_search = new ArrayList<Node> ();
			TLongArrayList hit_nodes_small_search = new TLongArrayList ();
			// store the hits for each of the nodes in the tips
			for (int j = 0; j < nds.size(); j++) {
				hit_nodes.add(graphDb.getNodeById(roothash.get(nds.get(j))));
				ArrayList<Long> tlist = roothashsearch.get(nds.get(j));
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
			for (int i = 0; i < root_ndids.size(); i++) {
				if (childndids.contains(root_ndids.getQuick(i)) == false) {
					outndids.addAll((long[])graphDb.getNodeById(root_ndids.get(i)).getProperty("mrca"));
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
			if (allTreesHaveAllTaxa == true) {
				ancestors = LicaUtil.getAllLICAt4j(hit_nodes_search, childndids, outndids);
			} else {
				ancestors = LicaUtil.getBipart4j(hit_nodes,hit_nodes_search, hit_nodes_small_search,childndids, outndids,graphDb);
			}
			for (Node tnd : ancestors) {
				if (tnd.hasProperty("name")) {
					msgLogger.indentMessageStrStr(3, "matched anc", "node", tnd.toString(), "name", (String)tnd.getProperty("name"));
				} else {
					msgLogger.indentMessageStr(3, "matched anc", "node", tnd.toString());
				}
			}
		}
	}
	
	
	/**
	 * This should be called from within postOrderaddProcessedTreeToGraph
	 * to create relationships between nodes that have already been identified
	 * 
	 * 
	 * @param inode current focal node from postorderaddprocessedtreetograph 
	 * @param source source name for the tree
	 */
	private void addProcessedNodeRelationships(JadeNode inode, String sourcename, String treeID) throws TreeIngestException{
		// At this point the inode is guaranteed to be associated with a dbnode
		// add the actual branches for the source
		// Node currGoLNode = (Node)(inode.getObject("dbnode"));
		Node [] allGoLNodes = (Node [])(inode.getObject("dbnodes"));
		// for use if this node will be an incluchildof and we want to store the relationships for faster retrieval
		ArrayList<Relationship> inclusiverelationships = new ArrayList<Relationship>();
		for (int k = 0; k < allGoLNodes.length; k++) {
			Node currGoLNode = allGoLNodes[k];
			// add the root index for the source trail
			if (inode.isTheRoot()) {
				// TODO: this will need to be updated when trees are updated
				System.out.println("placing root in index");
				sourceRootIndex.add(currGoLNode, "rootnode", sourcename);
				if (treeID != null) {
					sourceRootIndex.add(currGoLNode, "rootnodeForID", treeID);
				}
				
				// Note: setProperty throws IllegalArgumentException - if value is of an unsupported type (including null)
				
			// add metadata node
				Node metadatanode = null;
				metadatanode = graphDb.createNode();
				
				HashMap<String,Object> assoc = jt.getAssoc();
				//System.out.println("assoc = " + assoc.size() + ".");
				/* Add metadata (if present) from jadetree coming from nexson.
				   STUDY-wide fields used at present:
					ot:studyPublicationReference - string: ot:studyPublicationReference "long string"
					ot:studyPublication - URI: ot:studyPublication <http://dx.doi.org/...>
					ot:curatorName - string: ot:curatorName "Jane Doe"
					ot:dataDeposit - string: ot:dataDeposit <http://purl.org/phylo/treebase/phylows/study/TB2:S1925>
					ot:studyId - string / integer ot:studyId "123"
					ot:ottolid - integer: ot:ottolid 783941
				   TREE-wide fields used at present:
					ot:branchLengthMode - string: ot:branchLengthMode "ot:substitutionCount"
					ot:inGroupClade - string: ot:inGroupClade node208482 <- this might not be desired anymore
				*/
				
				// Note: setProperty throws IllegalArgumentException - if value is of an unsupported type (including null)
				
				for (Map.Entry<String, Object> entry : assoc.entrySet()) {
				    String key = entry.getKey();
				    System.out.println("Dealing with metadata property: " + key);
				    Object value = entry.getValue();
				    if (key.startsWith("ot:")) {
				    	System.out.println("Adding property '" + key + "': " + value);
						metadatanode.setProperty(key, value);
					}
				}
				
				metadatanode.setProperty("source", sourcename);
				metadatanode.setProperty("newick", treestring);
				if (treeID != null) {
					metadatanode.setProperty("treeID", treeID);
				}
				sourceMetaIndex.add(metadatanode, "source", sourcename);
				//add the source taxa ids
				long[]ret2 = root_ndids.toArray();
				metadatanode.setProperty("original_taxa_map",ret2);
				//end add source taxa ids
				// TODO: doesn't account for multiple root nodes
				metadatanode.createRelationshipTo(currGoLNode, RelType.METADATAFOR);
			}
			for (int i = 0; i < inode.getChildCount(); i++) {
				JadeNode childJadeNode = inode.getChild(i);
//				Node childGoLNode = (Node)childJadeNode.getObject("dbnode");
				Node [] allChildGoLNodes = (Node [])(childJadeNode.getObject("dbnodes"));
				for (int m = 0; m < allChildGoLNodes.length; m++) {
					Node childGoLNode = allChildGoLNodes[m];
					Relationship rel = childGoLNode.createRelationshipTo(currGoLNode, RelType.STREECHILDOF);
					sourceRelIndex.add(rel, "source", sourcename);
					rel.setProperty("exclusive_mrca", inode.getObject("exclusive_mrca"));
					rel.setProperty("root_exclusive_mrca", inode.getObject("root_exclusive_mrca"));
					long [] licaids = new long[allGoLNodes.length];
					for (int n = 0; n < licaids.length; n++) {
						licaids[n] = allGoLNodes[n].getId();
					}
					rel.setProperty("licas", licaids);
					inclusiverelationships.add(rel);

					// check to make sure the parent and child nodes are distinct entities...
					if (rel.getStartNode().getId() == rel.getEndNode().getId()) {
						StringBuffer errbuff = new StringBuffer();
						errbuff.append("A node and its child map to the same GoL node.\nTips:\n");
						for (int j = 0; j < inode.getTips().size(); j++) {
							errbuff.append(inode.getTips().get(j).getName() + "\n");
							errbuff.append("\n");
						}
						if (currGoLNode.hasProperty("name")) {
							errbuff.append(" ancestor taxonomic name: " + currGoLNode.getProperty("name"));
						}
						errbuff.append("\nThe tree has been partially imported into the db.\n");
						throw new TreeIngestException(errbuff.toString());
					}
					// METADATA ENTRY
					rel.setProperty("source", sourcename);
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
	
	public void deleteAllTrees() {
		IndexHits<Node> hits  = sourceMetaIndex.query("source", "*");
		System.out.println(hits.size());
		for (Node itrel : hits) {
			String source = (String)itrel.getProperty("source");
			deleteTreeBySource(source);
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
			jt = tr.readTree(trees);
			jt.assocObject("id", treeID);
			System.out.println("tree read");
			setTree(jt,trees);
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
	
	public void deleteTreeBySource(String source) {
		System.out.println("deleting tree: " + source);
		IndexHits <Relationship> hits = sourceRelIndex.get("source", source);
		Transaction	tx = graphDb.beginTx();
		try {
//			Iterator<Relationship> itrel = tobedeleted.iterator();
			for (Relationship itrel : hits) {
				itrel.delete();
				sourceRelIndex.remove(itrel, "source", source);
			}
			tx.success();
		} finally {
			tx.finish();
		}
		hits.close();
		IndexHits <Node> shits = sourceRootIndex.get("rootnode", source);
		tx = graphDb.beginTx();
		try {
			for (Node itrel : shits) {
				sourceRootIndex.remove(itrel, "rootnode", source);
			}
			tx.success();
		} finally {
			tx.finish();
		}
		shits.close();
		shits = sourceMetaIndex.get("source", source);
		tx = graphDb.beginTx();
		try {
			for (Node itrel : shits) {
				sourceMetaIndex.remove(itrel, "source", source);
				itrel.getRelationships(RelType.METADATAFOR).iterator().next().delete();
				itrel.delete();
			}
			tx.success();
		} finally {
			tx.finish();
		}
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Something!");
	}
}