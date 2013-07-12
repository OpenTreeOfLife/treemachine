package opentree.plugins;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import jade.tree.JadeTree;
import jade.MessageLogger;
import jade.JSONMessageLogger;

import opentree.GraphBase;
import opentree.GraphDatabaseAgent;
import opentree.GraphExplorer;
import opentree.GraphExporter;
import opentree.MainRunner;
import opentree.OttolIdNotFoundException;
import opentree.RelTypes;
import opentree.TaxonNotFoundException;
import opentree.TreeIngestException;
import opentree.TreeNotFoundException;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.ArgusonRepresentationConverter;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OpenTreeMachineRepresentationConverter;

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
		return OpenTreeMachineRepresentationConverter.convert(sourceArrayList);
	}

	@Description("Return a JSON obj that represents the error and warning messages associated with attempting to ingest a NexSON blob")
	@PluginTarget (GraphDatabaseService.class)
	public Representation getStudyIngestMessagesForNexSON(
				@Source GraphDatabaseService graphDbs,
				@Description( "The OTToL id of the node to use as the root for synthesis. If omitted then the root of all life is used.")
				@Parameter(name = "nexsonBlob", optional = true) String nexsonBlob)
				throws UnsupportedEncodingException, 
					   IOException,
					   TaxonNotFoundException,
					   TreeIngestException {
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(graphDbs);
		ByteArrayInputStream inpStream = new ByteArrayInputStream(nexsonBlob.getBytes("UTF-8"));
		BufferedReader nexsonContentBR = new BufferedReader(new InputStreamReader(inpStream));
		JSONMessageLogger messageLogger = new JSONMessageLogger("pgloadind-ws");
		ByteArrayOutputStream outputJSONStream = new ByteArrayOutputStream();
		PrintStream outputPrintStream = new PrintStream(outputJSONStream);
		messageLogger.setPrintStream(outputPrintStream);
		try {
			boolean onlyTestTheInput = true;
			MainRunner.loadPhylografterStudy(graphDb, nexsonContentBR, messageLogger, onlyTestTheInput);
		} finally {
			messageLogger.close();
			outputPrintStream.close();
		}
		String jsonResponse = outputJSONStream.toString();
		return OpenTreeMachineRepresentationConverter.convert(jsonResponse); // double wrapping string. Need to figure out a thin String->Representation wrapper.
/*
*/
	}

	@Description("Returns identifying information for the current draft tree")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeID(
			@Source GraphDatabaseService graphDb) {
		GraphExplorer ge = new GraphExplorer(graphDb);
		HashMap<String,String> draftTreeInfo = new HashMap<String,String>();
		try {
			draftTreeInfo.put("draftTreeName",GraphExplorer.DRAFTTREENAME);
			draftTreeInfo.put("lifeNodeID", String.valueOf(ge.findGraphNodeByName("life").getId()));
		} finally {
			ge.shutdownDB();
		}
		return OpenTreeMachineRepresentationConverter.convert(draftTreeInfo);
	}
	
	@Description("Initiate the default synthesis process (and store the synthesized branches) for the subgraph starting from a given root node")
	@PluginTarget(GraphDatabaseService.class)
	public String synthesizeSubtree(
			@Source GraphDatabaseService graphDb,
			@Description( "The OTToL id of the node to use as the root for synthesis. If omitted then the root of all life is used.")
			@Parameter(name = "rootOttolID", optional = true) String rootOttolID) throws OttolIdNotFoundException {

		GraphExplorer ge = new GraphExplorer(graphDb);
		
		if (rootOttolID == null || rootOttolID.length() == 0)
			rootOttolID = (String) ge.findGraphNodeByName("life").getProperty("tax_uid");
		
    	// TODO: need to build the ordered list of studies, refer to the study list on the dev server to create this
		LinkedList<String> preferredSources = new LinkedList<String>();
		preferredSources.add("15");
		preferredSources.add("taxonomy");
		
        // find the start node
        Node firstNode = ge.findGraphTaxNodeByUID(rootOttolID);
        if (firstNode == null) {
            throw new opentree.OttolIdNotFoundException(rootOttolID);
        }
		
		if (ge.synthesizeAndStoreDraftTreeBranches(firstNode, preferredSources,false)) {
			return "Success. Synthesized relationships stored for ottolid=" + rootOttolID;
		} else {
			return "Failure. Nothing stored for ottolid=" + rootOttolID;
		}
	}
	
	/* should this be two different queries? what is the advantage of having the arguson and newick served from the same query? - ceh */
	// subtreeNodeID is a string in case we use stable node identifiers at some point. Currently we just convert it to the db node id.
	@Description("Returns a source tree if format is \"newick\" then return JSON will have two fields: newick and treeID. If format = \"arguson\" then the return object will be the form of JSON expected by argus")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSourceTree(
			@Source GraphDatabaseService graphDb,
			@Description( "The identifier for the source tree to return")
			@Parameter(name = "treeID", optional = false) String treeID,
			@Description( "The name of the return format (default is newick)")
			@Parameter(name = "format", optional = true) String format,
			@Description( "The nodeid of the a node in the tree that should serve as the root of the tree returned")
			@Parameter(name = "subtreeNodeID", optional = true) String subtreeNodeIDStr, 
			@Description( "An integer controlling the max number of edges between the leaves and the node. The default is -1; a negative number corresponds to no pruning of the tree.")
			@Parameter(name = "maxDepth", optional = true) Integer maxDepthArg) throws TreeNotFoundException {
		int maxDepth = -1;
		if (maxDepthArg != null) {
			maxDepth = maxDepthArg;
		}
		long subtreeNodeID = 0;
		if (subtreeNodeIDStr != null) {
			subtreeNodeID = Long.parseLong(subtreeNodeIDStr, 10);
		}
		boolean emitNewick = false;
		if (format == null || format.length() == 0 || format.equalsIgnoreCase("newick")) {
			emitNewick = true;
		} else if (!format.equalsIgnoreCase("arguson")) {//@TEMP what is the name of the format argus likes???
			throw new IllegalArgumentException("Expecting either \"newick\" or \"arguson\" as the format.");
		}
		String newick = "";
		String retst = ""; // suppressing uninit warning
		GraphExplorer ge = new GraphExplorer(graphDb);
		GraphExporter gExporter = null;
		try {
			JadeTree tree;
			if (subtreeNodeIDStr == null) {
				tree = ge.reconstructSourceByTreeID(treeID, maxDepth);
			} else {
				tree = ge.reconstructSourceByTreeID(treeID, subtreeNodeID, maxDepth);
			}
			if (emitNewick) {
				newick = tree.getRoot().getNewick(tree.getHasBranchLengths());
			} else {
				// Code from GetJsons.java getConflictTaxJsonAltRel
				
				//	ArrayList<Long> rels = new ArrayList<Long>();
				//	gExporter = new GraphExporter(graphDb);
				//	Node rootNode = ge.getRootNodeByTreeID(treeID);
				//	int maxdepth = 5;
				//	String sourcename = ge.findSourceNameFromTreeID(treeID);
				//	retst = gExporter.constructJSONAltRels(rootNode, sourcename, rels, maxdepth);
				
				// not sure why argus wants a list...
				retst = "[" + tree.getRoot().getJSON(false) + "]";
			}
		} finally {
			ge.shutdownDB();
			if (gExporter != null) {
				gExporter.shutdownDB();
			}
		}
		if (emitNewick) {
			HashMap<String, Object> responseMap = new HashMap<String, Object>();
			responseMap.put("newick", newick);
			responseMap.put("treeID", treeID);
			return OpenTreeMachineRepresentationConverter.convert(responseMap);
		} else {
			return OpenTreeMachineRepresentationConverter.convert(retst); // double wrapping string
		}
	}


	/* should this be two different queries? what is the advantage of having the arguson and newick served from the same query? - ceh */
	// subtreeNodeID is a string in case we use stable node identifiers at some point. Currently we just convert it to the db node id.
	@Description("Returns a synthetic tree if format is \"newick\" then return JSON will have two fields: newick and treeID. If format = \"arguson\" then the return object will be the form of JSON expected by argus")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSyntheticTree(
			@Source GraphDatabaseService graphDb,
			@Description( "The identifier for the synthesis (e.g. \"otol.draft.22\")")
			@Parameter(name = "treeID", optional = false) String treeID,
			@Description( "The name of the return format (default is newick)")
			@Parameter(name = "format", optional = true) String format,
			@Description( "The nodeid of the a node in the tree that should serve as the root of the tree returned")
			@Parameter(name = "subtreeNodeID", optional = true) String subtreeNodeIDStr, 
			@Description( "An integer controlling the max number of edges between the leaves and the node. The default is 5. A negative number corresponds to no pruning of the tree.")
			@Parameter(name = "maxDepth", optional = true) Integer maxDepthArg) throws TreeNotFoundException {

		// set default param values
		int maxDepth = 5;
		long subtreeNodeID = 0;
		boolean emitNewick = false;

		// override defaults if user-specified
		if (maxDepthArg != null) {
			maxDepth = maxDepthArg;
		}
		if (subtreeNodeIDStr != null) {
			subtreeNodeID = Long.parseLong(subtreeNodeIDStr, 10);
		}

		// determine output format
		if (format == null || format.length() == 0 || format.equalsIgnoreCase("newick")) {
			emitNewick = true;
		} else if (!format.equalsIgnoreCase("arguson")) {//@TEMP what is the name of the format argus likes???
			throw new IllegalArgumentException("Expecting either \"newick\" or \"arguson\" as the format.");
		}

		// get the subtree for export
		GraphExplorer ge = new GraphExplorer(graphDb);
//		GraphExporter gExporter = null;
		JadeTree tree = null;
		try {
			if (subtreeNodeIDStr == null) {
				tree = ge.reconstructSyntheticTree(treeID, maxDepth);
			} else {
				tree = ge.reconstructSyntheticTree(treeID, subtreeNodeID, maxDepth);
			}
		} finally {
			ge.shutdownDB();
//			if (gExporter != null) {
//				gExporter.shutdownDB();
//			}
		}

		// initialize containers for possible return values
//		String newickStr = "";
//		Representation argusonRepresentation = null;
//		String retst = ""; // suppressing uninit warning

		if (emitNewick) {
			String newickStr = tree.getRoot().getNewick(tree.getHasBranchLengths());
			HashMap<String, Object> responseMap = new HashMap<String, Object>();
			responseMap.put("newick", newickStr);
			responseMap.put("treeID", treeID);
			return OpenTreeMachineRepresentationConverter.convert(responseMap);
			
		} else {
			
			// begin A ================ this section appears to be completely unused... should it be removed? ==============
			
			// Code from GetJsons.java getConflictTaxJsonAltRel
			//	ArrayList<Long> rels = new ArrayList<Long>();
			//	gExporter = new GraphExporter(graphDb);
			//	Node rootNode = ge.getRootNodeByTreeID(treeID);
			//	int maxdepth = 5;
			//	String sourcename = ge.findSourceNameFromTreeID(treeID);
			//	retst = gExporter.constructJSONAltRels(rootNode, sourcename, rels, maxdepth);

			// end A ==================

			// begin B ================ this section to be switched for opentreeconverter methods =================
			
			// not sure why argus wants a list...
//			StringBuffer retB = new StringBuffer("[");
//			retB.append(tree.getRoot().getJSON(false));
//			retB.append("]");
//			retst = retB.toString();
//			return OpenTreeMachineRepresentationConverter.convert(retst); // double wrapping string
			
			// end B =================
			
			return ArgusonRepresentationConverter.getArgusonRepresentationForJadeNode(tree.getRoot());
		}
	}
	
	@Description("returns a newick string of the current draft tree (see GraphExplorer) for the node identified by `ottolID`.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeForOttolID(
			@Source GraphDatabaseService graphDb,
			@Description( "The ottol id of the taxon to be used as the root for the tree.")
			@Parameter(name = "ottolID", optional = false) String ottolID,
			@Description( "DEPRECATED. Has no effect. Previously, was an integer controlling the maximum depth to which the graph will be traversed when building the tree.")
			@Parameter(name = "maxDepth", optional = true) Integer maxDepthArg) {
		
		GraphExplorer ge = new GraphExplorer(graphDb);
		Node startNode = ge.findGraphTaxNodeByUID(ottolID);
		
		JadeTree tree = ge.extractDraftTree(startNode, GraphBase.DRAFTTREENAME);

		HashMap<String, String> response = new HashMap<String, String>();
		response.put("tree", tree.getRoot().getNewick(true));

		return OpenTreeMachineRepresentationConverter.convert(response);
	}

	@Description("returns a newick string of the current draft tree (see GraphExplorer) for the node identified by `nodeID`.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeForNodeID(
			@Source GraphDatabaseService graphDb,
			@Description( "The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
		
		GraphExplorer ge = new GraphExplorer(graphDb);
		Node startNode = graphDb.getNodeById(nodeID);
		
		JadeTree tree = ge.extractDraftTree(startNode, GraphBase.DRAFTTREENAME);

		HashMap<String, String> response = new HashMap<String, String>();
		response.put("tree", tree.getRoot().getNewick(true));

		return OpenTreeMachineRepresentationConverter.convert(response);
	}

	// ============================== arbor interoperability services ==================================

	@Description("returns the ids of the immediate SYNTHCHILDOF children of the indidcated node in the draft tree. Temporary, for interoperability testing with the arbor project.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getDraftTreeChildNodesForNodeID(
			@Source GraphDatabaseService graphDb,
			@Description( "The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
				
		Node startNode = graphDb.getNodeById(nodeID);
		
		HashSet<Long> childIds = new HashSet<Long>();
		
        for (Relationship synthChildRel : startNode.getRelationships(Direction.INCOMING, RelTypes.SYNTHCHILDOF)) {
        	if (GraphBase.DRAFTTREENAME.equals(String.valueOf(synthChildRel.getProperty("name"))))	{
        		childIds.add(synthChildRel.getStartNode().getId());
        	}
        }

		return OpenTreeMachineRepresentationConverter.convert(childIds);
	}
	
	@Description("Returns the the node id of the named node identified by `ottolID`. Temporary, for interoperability testing with the arbor project.")
	@PluginTarget(GraphDatabaseService.class)
	public Long getNodeIDForOttolID(
			@Source GraphDatabaseService graphDb,
			@Description( "The ottol id of the taxon to be used as the root for the tree.")
			@Parameter(name = "ottolID", optional = false) String ottolID) {
		
		GraphExplorer ge = new GraphExplorer(graphDb);
		return ge.findGraphTaxNodeByUID(ottolID).getId();
	}
}

