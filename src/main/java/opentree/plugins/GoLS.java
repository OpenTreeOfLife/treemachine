package opentree.plugins;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import jade.tree.JadeTree;
import jade.JSONMessageLogger;
import opentree.GraphBase;
import opentree.GraphDatabaseAgent;
import opentree.GraphExplorer;
import opentree.MainRunner;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;

import opentree.constants.GeneralConstants;

import opentree.exceptions.MultipleHitsException;
import opentree.exceptions.OttIdNotFoundException;
import opentree.exceptions.TaxonNotFoundException;
import opentree.exceptions.TreeIngestException;
import opentree.exceptions.TreeNotFoundException;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.ArgusonRepresentationConverter;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OTRepresentationConverter;

// Graph of Life Services 
public class GoLS extends ServerPlugin {

	@Description("Returns a list of all source tree IDs")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSourceTreeIDs(
			@Source GraphDatabaseService graphDb) {
		GraphExplorer ge = new GraphExplorer(graphDb);
		ArrayList<String>  sourceArrayList;
		try {
			sourceArrayList = ge.getTreeIDList();
		} finally {
			ge.shutdownDB();
		}
		return OTRepresentationConverter.convert(sourceArrayList);
	}

	@Description("Get the MRCA of a set of nodes in the draft tree. Accepts any combination of node ids and ott ids as input.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeMRCAForNodes(
			@Source GraphDatabaseService graphDb,
			@Description("A set of node ids") @Parameter(name = "nodeIds", optional = true) long[] nodeIds,
			@Description("A set of ott ids") @Parameter(name = "ottIds", optional = true) long[] ottIds) throws MultipleHitsException, TaxonNotFoundException {
		
		if (nodeIds.length < 1 && ottIds.length < 1) {
			throw new IllegalArgumentException("You must supply at least one node or ott id.");
		}
		
		ArrayList<Node> tips = new ArrayList<Node>();
		GraphExplorer ge = new GraphExplorer(graphDb);
		
		if (nodeIds != null && nodeIds.length > 0) {
			for (long nodeId : nodeIds) {
				Node n = graphDb.getNodeById(nodeId);
				if (n != null) {
					tips.add(n);
				}
			}
		}
		
		if (ottIds != null && ottIds.length > 0) {
			for (long ottId : ottIds) {
				Node n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
				if (n != null) {
					tips.add(n);
				}
			}
		}

		if (tips.size() < 1) {
			throw new IllegalArgumentException("Could not find any graph nodes corresponding to the node and/or ott ids provided.");
		} else {
			HashMap<String, Object> vals = new HashMap<String, Object>();
			vals.put("found_nodes", tips);
			vals.put("mrca_node_id", ge.getDraftTreeMRCAForNodes(tips,false).getId());
			return OTRepresentationConverter.convert(vals);
		}
	}
	
	@Description("Get a subtree of the draft tree with tips corresponding to the set of nodes identified by the query"
			+ "input. Accepts any combination of node ids and ott ids as input.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeSubtreeForNodes(
			@Source GraphDatabaseService graphDb,
			@Description("A set of node ids") @Parameter(name = "nodeIds", optional = true) long[] nodeIds,
			@Description("A set of ott ids") @Parameter(name = "ottIds", optional = true) long[] ottIds) throws MultipleHitsException, TaxonNotFoundException {
		
		if ((nodeIds == null || nodeIds.length < 1) && (ottIds == null || ottIds.length < 1)) {
			throw new IllegalArgumentException("You must supply at least one node or ott id.");
		}
		
		ArrayList<Node> tips = new ArrayList<Node>();
		GraphExplorer ge = new GraphExplorer(graphDb);
		
		if (nodeIds != null && nodeIds.length > 0) {
			for (long nodeId : nodeIds) {
				Node n = graphDb.getNodeById(nodeId);
				if (n != null) {
					tips.add(n);
				}
			}
		}
		
		if (ottIds != null && ottIds.length > 0) {
			for (long ottId : ottIds) {
				Node n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
				if (n != null) {
					tips.add(n);
				}
			}
		}

		if (tips.size() < 1) {
			throw new IllegalArgumentException("Could not find any graph nodes corresponding to the node and/or ott ids provided.");
		} else {
			HashMap<String, Object> vals = new HashMap<String, Object>();
			vals.put("found_nodes", tips);
			vals.put("subtree", ge.extractDraftSubtreeForTipNodes(tips).getNewick(true) + ";\n");
			return OTRepresentationConverter.convert(vals);
		}
	}
	
	@Description("Return a JSON obj that represents the error and warning messages associated with attempting to ingest a NexSON blob")
	@PluginTarget (GraphDatabaseService.class)
	public Representation getStudyIngestMessagesForNexSON(
				@Source GraphDatabaseService graphDbs,
				@Description("The OTToL id of the node to use as the root for synthesis. If omitted then the root of all life is used.")
				@Parameter(name = "nexsonBlob", optional = true) String nexsonBlob)
				throws Exception {
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(graphDbs);
		ByteArrayInputStream inpStream = new ByteArrayInputStream(nexsonBlob.getBytes("UTF-8"));
		BufferedReader nexsonContentBR = new BufferedReader(new InputStreamReader(inpStream));
		JSONMessageLogger messageLogger = new JSONMessageLogger("pgloadind-ws");
		ByteArrayOutputStream outputJSONStream = new ByteArrayOutputStream();
		PrintStream outputPrintStream = new PrintStream(outputJSONStream);
		messageLogger.setPrintStream(outputPrintStream);
		try {
			boolean onlyTestTheInput = true;
			MainRunner.loadPhylografterStudy(graphDb, nexsonContentBR, null, null,messageLogger, onlyTestTheInput);
		} finally {
			messageLogger.close();
			outputPrintStream.close();
		}
		String jsonResponse = outputJSONStream.toString();
		return OTRepresentationConverter.convert(jsonResponse); // TODO: still double wrapping string. Need to figure out a thin String->Representation wrapper.
	}

	@Description("Returns identifying information for the current draft tree")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeID (
			@Source GraphDatabaseService graphDb,
			@Description( "The OTT id of the intended starting taxon. If not specified, the the root of the draft tree will be used if it is set. If it not set then this will return null.")
//			@Parameter(name = "startingTaxonName", optional = true) String startingTaxonName) throws TaxonNotFoundException, MultipleHitsException {
			@Parameter(name = "startingTaxonOTTId", optional = true) String startingTaxonOTTId) throws TaxonNotFoundException, MultipleHitsException {

		GraphDatabaseAgent gdb = new GraphDatabaseAgent(graphDb);
		GraphExplorer ge = new GraphExplorer(gdb);
		HashMap<String, Object> draftTreeInfo = null;
		Node startNode = null;
		try {

			// caller can request an alternate starting point in the tree
			if (startingTaxonOTTId == null || startingTaxonOTTId.length() == 0) {
				Long draftTreeRootNodeId = (Long) gdb.getGraphProperty("draftTreeRootNodeId");
				if (draftTreeRootNodeId != null) {
					startNode = gdb.getNodeById(draftTreeRootNodeId);
				} else {
					return null;
				}
			} else {
				startNode = ge.findGraphTaxNodeByUID(startingTaxonOTTId);
			}

			draftTreeInfo = new HashMap<String, Object>();
			draftTreeInfo.put("draftTreeName",GraphBase.DRAFTTREENAME);
//			draftTreeInfo.put("lifeNodeID", String.valueOf(ge.findTaxNodeByName("life").getId()));
			draftTreeInfo.put("startNodeTaxName", String.valueOf(startNode.getProperty(NodeProperty.NAME.propertyName)));
			draftTreeInfo.put("startNodeOTTId", Long.valueOf((String) startNode.getProperty(NodeProperty.TAX_UID.propertyName))); //TODO: the taxuids should be stored as longs, not strings... fix this where it happens
			draftTreeInfo.put("startNodeID", startNode.getId());

		} finally {
			ge.shutdownDB();
		}

		return OTRepresentationConverter.convert(draftTreeInfo);
	}
	
	@Description("Returns a list of the synthesis tree source information")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSynthesisSourceList (
			@Source GraphDatabaseService graphDb) throws TaxonNotFoundException, MultipleHitsException {
		GraphExplorer ge = new GraphExplorer(graphDb);
		ArrayList<String> sourceList = new ArrayList<String>();
		try {
			Node meta = ge.getSynthesisMetaNode();
			if (meta != null){
				String [] sourcePrimList = (String []) meta.getProperty("sourcenames");
				for(int i=0;i<sourcePrimList.length;i++){
					sourceList.add(sourcePrimList[i]);
				}
			}
		} finally {
			ge.shutdownDB();
		}
		return OTRepresentationConverter.convert(sourceList);
	}
	
	@Description("Initiate the default synthesis process (and store the synthesized branches) for the subgraph starting from a given root node")
	@PluginTarget(GraphDatabaseService.class)
	public String synthesizeSubtree(
			@Source GraphDatabaseService graphDb,
			@Description("The OTToL id of the node to use as the root for synthesis. If omitted then the root of all life is used.")
			@Parameter(name = "rootottId", optional = true) String rootottId) throws Exception {

		GraphExplorer ge = new GraphExplorer(graphDb);
		
		if (rootottId == null || rootottId.length() == 0)
			rootottId = (String) ge.findTaxNodeByName("life").getProperty("tax_uid");
		
    	// TODO: for now just using very simple list of studies, this will need to be extended for this service to be useful
		LinkedList<String> preferredSources = new LinkedList<String>();
		preferredSources.add("15");
		preferredSources.add("taxonomy");
		
        // find the start node
        Node firstNode = ge.findGraphTaxNodeByUID(rootottId);
        if (firstNode == null) {
            throw new OttIdNotFoundException(rootottId);
        }
		
		if (ge.synthesizeAndStoreDraftTreeBranches(firstNode, preferredSources,false)) {
			return "Success. Synthesized relationships stored for ottId=" + rootottId;
		} else {
			return "Failure. Nothing stored for ottId=" + rootottId;
		}
	}
	
	/* should this be two different queries? what is the advantage of having the arguson and newick served from the same query? - ceh */
	// subtreeNodeID is a string in case we use stable node identifiers at some point. Currently we just convert it to the db node id.
	@Description("Returns a source tree if format is \"newick\" then return JSON will have two fields: newick and treeID. If format = \"arguson\" then the return object will be the form of JSON expected by argus")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSourceTree(
			@Source GraphDatabaseService graphDb,
			@Description("The identifier for the source tree to return")
			@Parameter(name = "treeID", optional = false) String treeID,
			@Description("The name of the return format (default is newick)")
			@Parameter(name = "format", optional = true) String format,
			@Description("The nodeid of the a node in the tree that should serve as the root of the tree returned")
			@Parameter(name = "subtreeNodeID", optional = true) String subtreeNodeIDStr, 
			@Description("An integer controlling the max number of edges between the leaves and the node. A negative number specifies that no depth limit will be applied. The default is -1 (no limit).")
			@Parameter(name = "maxDepth", optional = true) Integer maxDepthArg) throws TreeNotFoundException {

		// set defaults
		int maxDepth = -1;
		long subtreeNodeID = 0;
		boolean emitNewick = false;

		// override defaults if user-specified
		if (maxDepthArg != null) {
			maxDepth = maxDepthArg;
		}
		if (subtreeNodeIDStr != null) {
			subtreeNodeID = Long.parseLong(subtreeNodeIDStr, 10);
		}

		// determine format
		if (format == null || format.length() == 0 || format.equalsIgnoreCase("newick")) {
			emitNewick = true;
		} else if (!format.equalsIgnoreCase("arguson")) {
			throw new IllegalArgumentException("Expecting either \"newick\" or \"arguson\" as the format.");
		}

		// get the tree
		GraphExplorer ge = new GraphExplorer(graphDb);
		JadeTree tree = null;
		try {
			if (subtreeNodeIDStr == null) {
				tree = ge.reconstructSourceByTreeID(treeID, maxDepth);
			} else {
				tree = ge.reconstructSourceByTreeID(treeID, subtreeNodeID, maxDepth);
			}
		} finally {
			ge.shutdownDB();
		}
		
		// return results
		if (emitNewick) {
			HashMap<String, Object> responseMap = new HashMap<String, Object>();
			responseMap.put("newick", tree.getRoot().getNewick(tree.getHasBranchLengths()));
			responseMap.put("treeID", treeID);
			return OTRepresentationConverter.convert(responseMap);

		} else { // emit arguson
			return ArgusonRepresentationConverter.getArgusonRepresentationForJadeNode(tree.getRoot());
		}
	}


	/* should this be two different queries? what is the advantage of having the arguson and newick served from the same query? - ceh */
	// subtreeNodeID is a string in case we use stable node identifiers at some point. Currently we just convert it to the db node id.
	@Description("Returns a synthetic tree if format is \"newick\" then return JSON will have two fields: newick and treeID. If format = \"arguson\" then the return object will be the form of JSON expected by argus")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSyntheticTree(
			@Source GraphDatabaseService graphDb,
			@Description("The identifier for the synthesis (e.g. \"otol.draft.22\") (default is most current synthetic tree)")
			@Parameter(name = "treeID", optional = true) String treeID,
			@Description("The name of the return format (default is newick)")
			@Parameter(name = "format", optional = true) String format,
			@Description("The nodeid of the a node in the tree that should serve as the root of the tree returned")
			@Parameter(name = "subtreeNodeID", optional = true) String subtreeNodeIDStr, 
			@Description("An integer controlling the max number of edges between the leaves and the node. A negative number specifies that no depth limit will be applied. The default is 5.")
			@Parameter(name = "maxDepth", optional = true) Integer maxDepthArg) throws TreeNotFoundException, TaxonNotFoundException {

		// set default param values
		int maxDepth = 5;
		long subtreeNodeID = 0;
		boolean emitNewick = false;
		String synthTreeID = (String)GeneralConstants.DRAFT_TREE_NAME.value;

		// override defaults if user-specified
		if (maxDepthArg != null) {
			maxDepth = maxDepthArg;
		}
		
		if (subtreeNodeIDStr == null || subtreeNodeIDStr.length() == 0) { // get root of draft tree
			GraphDatabaseAgent gdb = new GraphDatabaseAgent(graphDb);
			subtreeNodeID = (Long) gdb.getGraphProperty("draftTreeRootNodeId");
		} else {
			subtreeNodeID = Long.parseLong(subtreeNodeIDStr, 10);
		}

		// determine output format
		if (format == null || format.length() == 0 || format.equalsIgnoreCase("newick")) {
			emitNewick = true;
		} else if (!format.equalsIgnoreCase("arguson")) {
			throw new IllegalArgumentException("Expecting either \"newick\" or \"arguson\" as the format.");
		}
		
		// synthetic tree identifier
		if (treeID != null) {
			synthTreeID = treeID;
		}

		// get the subtree for export
		GraphExplorer ge = new GraphExplorer(graphDb);
		JadeTree tree = null;
		try {
			tree = ge.reconstructSyntheticTree(synthTreeID, subtreeNodeID, maxDepth);
		} finally {
			ge.shutdownDB();
		}

		if (emitNewick) {
			HashMap<String, Object> responseMap = new HashMap<String, Object>();
//			responseMap.put("newick", tree.getRoot().getNewick(tree.getHasBranchLengths())); // commented because it seems to be failing with newer versions of the jade code
			responseMap.put("newick", tree.getRoot().getNewick(false) + ";");
			responseMap.put("treeID", synthTreeID);
			return OTRepresentationConverter.convert(responseMap);
		} else { // emit arguson
			return ArgusonRepresentationConverter.getArgusonRepresentationForJadeNode(tree.getRoot());
		}
	}
	
	@Description("Returns a newick string of the current draft tree (see GraphExplorer) for the node identified by `ottId`.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeForottId( // TODO: should be renamed getDraftTreeNewickForottId, will need to be updated in argus
			@Source GraphDatabaseService graphDb,
			@Description("The ottol id of the taxon to be used as the root for the tree.")
			@Parameter(name = "ottId", optional = false) String ottId) throws TaxonNotFoundException, MultipleHitsException { //,
//			@Description("DEPRECATED. Has no effect. Previously, was an integer controlling the maximum depth to which the graph will be traversed when building the tree.")
//			@Parameter(name = "maxDepth", optional = true) Integer maxDepthArg) { // TODO: Remove this parameter if it is unused
		
		GraphExplorer ge = new GraphExplorer(graphDb);
		Node startNode = ge.findGraphTaxNodeByUID(ottId);
		
		JadeTree tree = ge.extractDraftTree(startNode, GraphBase.DRAFTTREENAME);

		HashMap<String, String> response = new HashMap<String, String>();
		response.put("tree", tree.getRoot().getNewick(true));

		return OTRepresentationConverter.convert(response);
	}

	@Description("returns a newick string of the current draft tree (see GraphExplorer) for the node identified by `nodeID`.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeForNodeID( // TODO: should be renamed getDraftTreeNewickForNodeID, will need to be updated in argus
			@Source GraphDatabaseService graphDb,
			@Description("The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
		
		GraphExplorer ge = new GraphExplorer(graphDb);
		Node startNode = graphDb.getNodeById(nodeID);
		
		JadeTree tree = ge.extractDraftTree(startNode, GraphBase.DRAFTTREENAME);

		HashMap<String, String> response = new HashMap<String, String>();
		response.put("tree", tree.getRoot().getNewick(true));

		return OTRepresentationConverter.convert(response);
	}

	@Description("Returns the the node id of the named node identified by `ottId`.")
	@PluginTarget(GraphDatabaseService.class)
	public Long getNodeIDForottId(
			@Source GraphDatabaseService graphDb,
			@Description("The ottol id of the taxon to be used as the root for the tree.")
			@Parameter(name = "ottId", optional = false) String ottId) throws TaxonNotFoundException, MultipleHitsException {
		
		GraphExplorer ge = new GraphExplorer(graphDb);
		return ge.findGraphTaxNodeByUID(ottId).getId();
	}
	
	// ============================== arbor interoperability services ==================================

	@Description("returns the ids of the immediate SYNTHCHILDOF children of the indidcated node in the draft tree. Temporary, for interoperability testing with the arbor project.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeChildNodesForNodeID(
			@Source GraphDatabaseService graphDb,
			@Description("The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
				
		Node startNode = graphDb.getNodeById(nodeID);
		
		HashSet<Long> childIds = new HashSet<Long>();
		
        for (Relationship synthChildRel : startNode.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)) {
        	if (GraphBase.DRAFTTREENAME.equals(String.valueOf(synthChildRel.getProperty("name"))))	{
        		childIds.add(synthChildRel.getStartNode().getId());
        	}
        }
		return OTRepresentationConverter.convert(childIds);
	}
}

