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

import jade.deprecated.JSONMessageLogger;
import jade.tree.deprecated.JadeTree;
import opentree.GraphBase;
import opentree.GraphExplorer;
import opentree.MainRunner;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.constants.GeneralConstants;

import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.TaxonNotFoundException;

import opentree.exceptions.TreeIngestException;

import org.opentree.exceptions.TreeNotFoundException;
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
import org.opentree.properties.OTVocabularyPredicate;
import org.opentree.graphdb.GraphDatabaseAgent;

// Graph of Life Services 
public class GoLS extends ServerPlugin {
    
    // TODO: not doing taxonomy MRCA anymore, as entire taxonomy is not ingested
    // refactor as synth-only; need to know which synth tree (default = most recent)
    @Description("Get the MRCA of a set of nodes. MRCA is calculated from the treeSource, which may be 'taxonomy' or 'synth' (the current " +
        "synthetic tree). come from either the 1) taxonomy, or 2) synthetic tree (which could be quite different)in the taxonomy. " +
        "Accepts any combination of nodeIds and ottIds as input. Returns the following information about the MRCA: 1) name, 2) ottId, " +
        "3) rank, and 4) nodeId. Also returns the nodeIds of the query taxa, the query target (treeSource), and any unmatched nodeIds/ottIds.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getMRCA(@Source GraphDatabaseService graphDb,
            
           @Description("Synthetic tree identifier (defaults to most recent).")
            @Parameter(name = "tree_id", optional = true)
            String treeID,
            
            @Description("A set of open tree node ids")
            @Parameter(name = "ot_node_ids", optional = true)
            String[] otNodeIDs,
            
            @Description("A set of node ids")
            @Parameter(name = "nodeIds", optional = true)
            long[] nodeIds,
            
            @Description("A set of ott ids")
            @Parameter(name = "ottIds", optional = true)
            long[] ottIds,
            
            @Description("Tree source (either 'taxonomy' or 'synth'")
            @Parameter(name = "treeSource",
            optional = false
            
            ) String treeSource) throws MultipleHitsException, TaxonNotFoundException {
        
        boolean taxonomyOnly = true;
        
        if (treeSource == null) {
            throw new IllegalArgumentException("You must supply a treeSource, either 'taxonomy' or 'synth'.");
        } else if (!treeSource.equalsIgnoreCase("synth") && !treeSource.equalsIgnoreCase("taxonomy")) {
            throw new IllegalArgumentException("treeSource must be either 'taxonomy' or 'synth'.");
        }
        if (treeSource.equalsIgnoreCase("synth")) {
            taxonomyOnly = false;
        }
        if ((nodeIds == null || nodeIds.length < 1) && (ottIds == null || ottIds.length < 1)) {
            throw new IllegalArgumentException("You must supply at least one nodeId or ottId.");
        }
        
        ArrayList<Node> tips = new ArrayList<Node>();
        ArrayList<Long> unmatchedOtts = new ArrayList<Long>();
        ArrayList<Long> unmatchedNodes = new ArrayList<Long>();
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        if (nodeIds != null && nodeIds.length > 0) {
            for (long nodeId : nodeIds) {
                Node n = graphDb.getNodeById(nodeId);
                if (n != null) {
                    if (taxonomyOnly) {
                        tips.add(n);
                    } else { // need to check if taxon is in the synthetic tree. 
                        if (n.hasRelationship(RelType.SYNTHCHILDOF)) {
                            tips.add(n);
                        } else { // if not in synth (i.e. not monophyletic), grab descendant tips, which *should* be in synth tree
                            tips.addAll(ge.getTaxonomyDescendantTips(n));
                        }
                    }
                } else {
                    unmatchedNodes.add(nodeId);
                }
            }
        }
        
        if (ottIds != null && ottIds.length > 0) {
            for (long ottId : ottIds) {
                Node n = null;
                try {
                    n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
                } catch (TaxonNotFoundException e) {}
                if (n != null) {
                    if (taxonomyOnly) {
                        tips.add(n);
                    } else { // need to check if taxon is in the synthetic tree. 
                        if (n.hasRelationship(RelType.SYNTHCHILDOF)) {
                            tips.add(n);
                        } else { // if not in synth (i.e. not monophyletic), grab descendant tips, which *should* be in synth tree
                            tips.addAll(ge.getTaxonomyDescendantTips(n));
                        }
                    }
                } else {
                    unmatchedOtts.add(ottId);
                }
            }
        }

        if (tips.size() < 1) {
            throw new IllegalArgumentException("Could not find any graph nodes corresponding to the ottIds provided.");
        } else {
            HashMap<String, Object> vals = new HashMap<String, Object>();
            Node mrca = ge.getDraftTreeMRCAForNodes(tips, taxonomyOnly);
            
            vals.put("tree_source", treeSource);
            vals.put("mrca_node_id", mrca.getId());
            vals.put("found_nodes", tips);
            
            if (!unmatchedOtts.isEmpty()) {
                vals.put("unmatched_ott_ids", unmatchedOtts);
            }
            if (!unmatchedNodes.isEmpty()) {
                vals.put("unmatched_node_ids", unmatchedNodes);
            }
            
            // now attempt to find the most recent taxonomic ancestor
            Node mrta = mrca;
            
            if (!taxonomyOnly) {
                while (!mrta.hasProperty(NodeProperty.TAX_UID.propertyName)) {
                    mrta = mrta.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
                }
                vals.put("nearest_taxon_mrca_name", mrta.getProperty(NodeProperty.NAME.propertyName));
                vals.put("nearest_taxon_mrca_unique_name", mrta.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
                vals.put("nearest_taxon_mrca_rank", mrta.getProperty(NodeProperty.TAX_RANK.propertyName));
                vals.put("nearest_taxon_mrca_ott_id", mrta.getProperty(NodeProperty.TAX_UID.propertyName));
                vals.put("nearest_taxon_mrca_node_id", mrta.getId());
            } else {
                vals.put("mrca_name", mrca.getProperty(NodeProperty.NAME.propertyName));
                vals.put("mrca_unique_name", mrca.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
                vals.put("mrca_rank", mrca.getProperty(NodeProperty.TAX_RANK.propertyName));
                vals.put("mrca_ott_id", mrca.getProperty(NodeProperty.TAX_UID.propertyName));
            }
            ge.shutdownDB();
            return OTRepresentationConverter.convert(vals);
        }
    }
    
    
    // is this being used?
    @Description("Get a subtree of the draft tree with tips corresponding to the set of nodes identified by the query"
            + "input. Accepts any combination of node ids and ott ids as input.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getDraftTreeSubtreeForNodes(
            @Source GraphDatabaseService graphDb,
            
            @Description("Synthetic tree identifier (defaults to most recent).")
            @Parameter(name = "tree_id", optional = true)
            String treeID,
            
            @Description("A set of open tree node ids")
            @Parameter(name = "ot_node_ids", optional = true)
            String[] otNodeIDs,
            
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
            vals.put("subtree", ge.extractDraftSubtreeForTipNodes(tips).getNewick(false) + ";\n");
            return OTRepresentationConverter.convert(vals);
        }
    }
    
    
    // is this being used?
    @Description("Return a JSON obj that represents the error and warning messages associated with attempting to ingest a NexSON blob")
    @PluginTarget (GraphDatabaseService.class)
    public Representation getStudyIngestMessagesForNexSON(
        @Source GraphDatabaseService graphDbs,
        
        @Description("The ottId of the node to use as the root for synthesis. If omitted then the root of all life is used.")
        @Parameter(name = "nexsonBlob", optional = true)
        String nexsonBlob)
        
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

    
    
    // this isn't really useful; all services have treeid arg, default to most recent
    @Description("Returns identifying information for the most recent draft tree")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getDraftTreeID (@Source GraphDatabaseService graphDb) {

        GraphDatabaseAgent gdb = new GraphDatabaseAgent(graphDb);
        GraphExplorer ge = new GraphExplorer(gdb);
        HashMap<String, Object> draftTreeInfo = null;
        Node meta = null;
        
        try {
            meta = ge.getMostRecentSynthesisMetaNode();
            draftTreeInfo = new HashMap<String, Object>();
            draftTreeInfo.put("tree_id", meta.getProperty("tree_id"));
            draftTreeInfo.put("root_taxon_name", meta.getProperty("root_taxon_name"));
            draftTreeInfo.put("root_ott_id", meta.getProperty("root_ott_id"));
            draftTreeInfo.put("taxonomy_version", meta.getProperty("taxonomy_version"));
            draftTreeInfo.put("root_ot_node_id", meta.getProperty("root_ot_node_id"));
        } finally {
            ge.shutdownDB();
        }
        return OTRepresentationConverter.convert(draftTreeInfo);
    }
    
    // is this used?
    @Description("Returns the version of the taxonomy used to initialize the graph")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getTaxonomyVersion (@Source GraphDatabaseService graphDb,
        
        @Description("Synthetic tree identifier")
        @Parameter(name = "tree_id", optional = true)
        String treeID
        
        ) {

        GraphExplorer ge = new GraphExplorer(graphDb);
        String synthTreeID = null;
        Node meta = null;
        String taxVersion = "";
        
        if (treeID != null) {
            synthTreeID = treeID;
        }
        
        try {
            if (synthTreeID != null) {
                meta = ge.getSynthesisMetaNodeByName(synthTreeID);
                // invalid treeid
                if (meta == null) {
                    ge.shutdownDB();
                    String ret = "Could not find a synthetic tree corresponding to the 'tree_id' arg: '"
                        + synthTreeID + "'.";
                    throw new IllegalArgumentException(ret);
                }
            } else {
                // default to most recent
                meta = ge.getMostRecentSynthesisMetaNode();
            }
            taxVersion = (String)meta.getProperty("taxonomy_version");
        } finally {
            ge.shutdownDB();
        }
        return OTRepresentationConverter.convert(taxVersion);
    }
    
    
    // is this used? if so, will need to be updated i.e. which synth tree?
    @Description("Returns a list of the synthesis tree source information")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getSynthesisSourceList (@Source GraphDatabaseService graphDb,
        
        @Description("Synthetic tree identifier (defaults to most recent).")
        @Parameter(name = "tree_id", optional = true)
        String treeID
        
        ) throws TaxonNotFoundException, MultipleHitsException {
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        ArrayList<String> sourceList = new ArrayList<String>();
        try {
            Node meta = ge.getMostRecentSynthesisMetaNode();
            if (meta != null){
                String [] sourcePrimList = (String []) meta.getProperty("sourcenames");
                for (int i = 0; i < sourcePrimList.length; i++){
                    sourceList.add(sourcePrimList[i]);
                }
            }
        } finally {
            ge.shutdownDB();
        }
        return OTRepresentationConverter.convert(sourceList);
    }
    
    
    
    
    
    

    
    
    // TODO: use new node IDs: ot_node_id
    // should this be two different queries? what is the advantage of having the arguson and newick served from the same query? - ceh
        // avoiding code duplication? what is the advantage of having separate queries?
    // subtreeNodeID is a string in case we use stable node identifiers at some point. Currently we just convert it to the db node id.
    @Description("Returns a synthetic tree if format is \"newick\" then return JSON will "
        + "have two fields: newick and treeID. If format = \"arguson\" then the return "
        + "object will be the form of JSON expected by argus")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getSyntheticTree(@Source GraphDatabaseService graphDb,
        
        @Description("The identifier for the synthetic tree (e.g. \"opentree4.0\") "
            + "(default is most current synthetic tree)")
        @Parameter(name = "tree_id", optional = true)
        String treeID,
        
        @Description("The name of the return format (default is newick)")
        @Parameter(name = "format", optional = true)
        String format,
        
        @Description("The ot node id of the node in the tree that should serve as the "
            + "root of the tree returned")
        @Parameter(name = "ot_node_id", optional = true)
        String otNodeID, 
        
        @Description("The nodeid of the node in the tree that should serve as the root of the tree returned")
        @Parameter(name = "subtreeNodeID", optional = true)
        String subtreeNodeIDStr, 
        
        @Description("An integer controlling the max number of edges between the leaves and the node. A negative number specifies that no depth limit will be applied. The default is 5.")
        @Parameter(name = "maxDepth", optional = true)
        Integer maxDepthArg
        
        ) throws TreeNotFoundException, TaxonNotFoundException {

        // set default param values
        int maxDepth = 3;
        long subtreeNodeID = 0;
        boolean emitNewick = false;
        //String synthTreeID = (String)GeneralConstants.DRAFT_TREE_NAME.value; // don't use this
        String synthTreeID = "";
        String startOTNodeID = "";
        Node meta = null;
        
        HashMap<String, Object> responseMap = new HashMap<String, Object>();
        
        // grab existing synth tree ids
        GraphExplorer ge = new GraphExplorer(graphDb);
        ArrayList<String> synthTreeIDs = ge.getSynthTreeIDs(); // these are sorted
        
        // synthetic tree identifier. check against synth meta index, as the hope is to serve multiple trees at once
        if (treeID != null) {
            if (synthTreeIDs.contains(treeID)) {
                synthTreeID = treeID;
            } else {
                ge.shutdownDB();
                String ret = "Could not find a synthetic tree corresponding to the 'tree_id' arg: '"
                    + synthTreeID + "'. Leave blank to default to the current synthetic tree.";
                throw new IllegalArgumentException(ret);
            }
        } else { // default to most recent
            if (!synthTreeIDs.isEmpty()) {
                synthTreeID = synthTreeIDs.get(synthTreeIDs.size() - 1);
            } else { // no synth trees in graph
                ge.shutdownDB();
                responseMap.put("error", "No synthetic trees found.");
                return OTRepresentationConverter.convert(responseMap);
            }
        }
        
        meta = ge.getSynthesisMetaNodeByName(synthTreeID);
        
        if (otNodeID != null) {
            startOTNodeID = otNodeID;
            // check if this node is in the tree of interest
        } else {
            // get root node id from metadata node
            startOTNodeID = (String) meta.getProperty("root_ot_node_id");
        }
        
        // determine output format
        if (format == null || format.length() == 0 || format.equalsIgnoreCase("newick")) {
            emitNewick = true;
        } else if (!format.equalsIgnoreCase("arguson")) {
            String ret = "Unrecognized 'format' arg: " + format + ". Expecting either "
                + "\"newick\" or \"arguson\" for the 'format' arg.";
            throw new IllegalArgumentException(ret);
        }

        // override defaults if user-specified
        if (maxDepthArg != null) {
            maxDepth = maxDepthArg;
        }
        
        /*
        // update this to get root of whichever tree is desired
        if (subtreeNodeIDStr == null || subtreeNodeIDStr.length() == 0) { // get root of draft tree
            GraphDatabaseAgent gdb = new GraphDatabaseAgent(graphDb);
            subtreeNodeID = (Long) gdb.getGraphProperty("draftTreeRootNodeId");
        } else {
            subtreeNodeID = ge.findGraphNodeByOTTNodeID(subtreeNodeIDStr).getId();
        }
        */
        
        // get the subtree for export
        JadeTree tree = null;
        try {
            tree = ge.reconstructSyntheticTree(synthTreeID, startOTNodeID, maxDepth);
        } finally {
            ge.shutdownDB();
        }

        if (emitNewick) {
            responseMap.put("newick", tree.getRoot().getNewick(false) + ";");
            responseMap.put("treeID", synthTreeID);
            return OTRepresentationConverter.convert(responseMap);
        } else { // emit arguson
            return ArgusonRepresentationConverter.getArgusonRepresentationForJadeNode(tree.getRoot());
        }
    }
    
    
    
    
    
    
    
    
    // are either of the 2 following used?
    
    @Description("Returns a newick string of the current draft tree (see GraphExplorer) for the node identified by `ottId`.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getDraftTreeForottId( // TODO: should be renamed getDraftTreeNewickForottId, will need to be updated in argus
            @Source GraphDatabaseService graphDb,
            @Description("The ottId of the taxon to be used as the root for the tree.")
            @Parameter(name = "ottId", optional = false) String ottId) throws TaxonNotFoundException, MultipleHitsException { //,
//            @Description("DEPRECATED. Has no effect. Previously, was an integer controlling the maximum depth to which the graph will be traversed when building the tree.")
//            @Parameter(name = "maxDepth", optional = true) Integer maxDepthArg) { // TODO: Remove this parameter if it is unused
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        Node startNode = ge.findGraphTaxNodeByUID(ottId);
        
        JadeTree tree = ge.extractDraftTree(startNode, GraphBase.DRAFTTREENAME);

        HashMap<String, String> response = new HashMap<String, String>();
        response.put("tree", tree.getRoot().getNewick(false));

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
        response.put("tree", tree.getRoot().getNewick(false));

        return OTRepresentationConverter.convert(response);
    }

    
    
    
    // ============================== arbor interoperability services ==================================
    
    // is this being used? who to ask?
    @Description("returns the ids of the immediate SYNTHCHILDOF children of the indidcated node in the draft tree. Temporary, for interoperability testing with the arbor project.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getDraftTreeChildNodesForNodeID(
            @Source GraphDatabaseService graphDb,
            @Description("The Neo4j node id of the node to be used as the root for the tree.")
            @Parameter(name = "nodeID", optional = false) Long nodeID) {
                
        Node startNode = graphDb.getNodeById(nodeID);
        HashSet<Long> childIds = new HashSet<Long>();
        
        for (Relationship synthChildRel : startNode.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)) {
            if (GraphBase.DRAFTTREENAME.equals(String.valueOf(synthChildRel.getProperty("name"))))    {
                childIds.add(synthChildRel.getStartNode().getId());
            }
        }
        return OTRepresentationConverter.convert(childIds);
    }
    
    
    // ============================== deprecated services ==================================
    
    /*
    // not useful: ottids *are* nodeids now
    @Description("Returns the the node id of the named node identified by `ottId`.")
    @PluginTarget(GraphDatabaseService.class)
    public Long getNodeIDForottId(
            @Source GraphDatabaseService graphDb,
            @Description("The ottId of the taxon to be used as the root for the tree.")
            @Parameter(name = "ottId", optional = false) String ottId) throws TaxonNotFoundException, MultipleHitsException {
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        return ge.findGraphTaxNodeByUID(ottId).getId();
    }
    */
    
    /*
    // don't think this is being used anymore
    // default synthesis start should be cellular organisms, not life
    @Description("Initiate the default synthesis process (and store the synthesized branches) for the subgraph starting from a given root node")
    @PluginTarget(GraphDatabaseService.class)
    public String synthesizeSubtree(
            @Source GraphDatabaseService graphDb,
            @Description("The ottId of the node to use as the root for synthesis. If omitted then the root of all life is used.")
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
            throw new TaxonNotFoundException(rootottId);
        }
        
        if (ge.synthesizeAndStoreDraftTreeBranches(firstNode, preferredSources,false)) {
            return "Success. Synthesized relationships stored for ottId=" + rootottId;
        } else {
            return "Failure. Nothing stored for ottId=" + rootottId;
        }
    }
    */
    
    /*
    // source trees no longer stored in graph
    // should this be two different queries? what is the advantage of having the arguson and newick served from the same query? - ceh
        // avoiding code duplication? what is the advantage of having separate queries?
    // subtreeNodeID is a string in case we use stable node identifiers at some point. Currently we just convert it to the db node id.
    @Description("Returns a source tree if format is \"newick\" then return JSON will have two fields: newick and treeID. " +
            "If format = \"arguson\" then the return object will be the form of JSON expected by argus")
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
    */
    
    /*
    @Deprecated
    // NOTE: this isn't as useful anymore, since the curator gives trees IDs 1, 2, etc. for each study
    //    If all studies have just one tree, each will have the treeID '1'
    //    i.e. treeIDs are no longer unique
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
    */
    /*
    @Deprecated
    @Description("Get the MRCA of a set of nodes in the draft tree. Accepts any combination of node ids and ott ids as input." +
        "If a query taxon is not present in the synthetic tree (i.e. it is not monophyletic), the tip descendants of the taxon " +
        "are used for the MRCA calculation. Returns the nodeId of the mrca node as well as the nodeId, name, and information " +
        "about the most recent taxonomic ancestor (the mrta, which may be the mrca or may be an ancestor of the mrca itself. " +
        "Also returns any unmatched nodeIds/ottIds.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getDraftTreeMRCAForNodes(
            @Source GraphDatabaseService graphDb,
            @Description("A set of node ids") @Parameter(name = "nodeIds", optional = true) long[] nodeIds,
            @Description("A set of ott ids") @Parameter(name = "ottIds", optional = true) long[] ottIds) throws MultipleHitsException, TaxonNotFoundException {

        if ((nodeIds == null || nodeIds.length < 1) && (ottIds == null || ottIds.length < 1)) {
            throw new IllegalArgumentException("You must supply at least one node or ott id.");
        }
        
        ArrayList<Node> tips = new ArrayList<Node>();
        ArrayList<Long> unmatchedOtts = new ArrayList<Long>();
        ArrayList<Long> unmatchedNodes = new ArrayList<Long>();
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        if (nodeIds != null && nodeIds.length > 0) {
            for (long nodeId : nodeIds) {
                Node n = graphDb.getNodeById(nodeId);
                if (n != null) {
                    if (n.hasRelationship(RelType.SYNTHCHILDOF)) {
                        tips.add(n);
                    } else { // if not in synth (i.e. not monophyletic), grab descendant tips, which *should* be in synth tree
                        tips.addAll(ge.getTaxonomyDescendantTips(n));
                    }
                } else {
                    unmatchedNodes.add(nodeId);
                }
            }
        }
        
        if (ottIds != null && ottIds.length > 0) {
            for (long ottId : ottIds) {
                Node n = null;
                try {
                    n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
                } catch (TaxonNotFoundException e) {}
                if (n != null) {
                    if (n.hasRelationship(RelType.SYNTHCHILDOF)) {
                        tips.add(n);
                    } else { // if not in synth (i.e. not monophyletic), grab descendant tips, which *should* be in synth tree
                        tips.addAll(ge.getTaxonomyDescendantTips(n));
                    }
                } else {
                    unmatchedOtts.add(ottId);
                }
            }
        }

        if (tips.size() < 1) {
            throw new IllegalArgumentException("Could not find any graph nodes corresponding to the node and/or ott ids provided.");
        } else {
            HashMap<String, Object> vals = new HashMap<String, Object>();
            Node mrca = ge.getDraftTreeMRCAForNodes(tips, false);
            
            vals.put("mrca_node_id", mrca.getId());
            vals.put("found_nodes", tips);
            
            if (!unmatchedOtts.isEmpty()) {
                vals.put("unmatched_ott_ids", unmatchedOtts);
            }
            if (!unmatchedNodes.isEmpty()) {
                vals.put("unmatched_node_ids", unmatchedNodes);
            }
            
            // now attempt to find the most recent taxonomic ancestor
            Node mrta = mrca;
            while (!mrta.hasProperty(NodeProperty.TAX_UID.propertyName)) {
                mrta = mrta.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
            }

            vals.put("nearest_taxon_mrca_name", mrta.getProperty(NodeProperty.NAME.propertyName));
            vals.put("nearest_taxon_mrca_unique_name", mrta.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
            vals.put("nearest_taxon_mrca_rank", mrta.getProperty(NodeProperty.TAX_RANK.propertyName));
            vals.put("nearest_taxon_mrca_ott_id", mrta.getProperty(NodeProperty.TAX_UID.propertyName));
            vals.put("nearest_taxon_mrca_node_id", mrta.getId());

            return OTRepresentationConverter.convert(vals);
        }
    }
    */
    /*
    @Deprecated
    @Description("Get the MRCA of a set of nodes in the taxonomy. Accepts any combination of node ids and ott ids as input. Returns the " +
        "following information about the MRCA: 1) name, 2) ottId, 3) rank, and 4) nodeId. Also returns the nodeIds of the query taxa, as " +
        "well as any unmatched nodeIds/ottIds.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation getTaxonomyMRCAForNodes(
        @Source GraphDatabaseService graphDb,
        @Description("A set of node ids") @Parameter(name = "nodeIds", optional = true) long[] nodeIds,
        @Description("A set of ott ids") @Parameter(name = "ottIds", optional = true) long[] ottIds) throws MultipleHitsException, TaxonNotFoundException {
    
        if ((nodeIds == null || nodeIds.length < 1) && (ottIds == null || ottIds.length < 1)) {
            throw new IllegalArgumentException("You must supply at least one nodeId or ottId.");
        }
        
        ArrayList<Node> tips = new ArrayList<Node>();
        ArrayList<Long> unmatchedOtts = new ArrayList<Long>();
        ArrayList<Long> unmatchedNodes = new ArrayList<Long>();
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        if (nodeIds != null && nodeIds.length > 0) {
            for (long nodeId : nodeIds) {
                Node n = graphDb.getNodeById(nodeId);
                if (n != null) {
                    tips.add(n);
                } else {
                    unmatchedNodes.add(nodeId);
                }
            }
        }
        
        if (ottIds != null && ottIds.length > 0) {
            for (long ottId : ottIds) {
                Node n = null;
                try {
                    n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
                } catch (TaxonNotFoundException e) {}
                if (n != null) {
                    tips.add(n);
                } else {
                    unmatchedOtts.add(ottId);
                }
            }
        }
    
        if (tips.size() < 1) {
            throw new IllegalArgumentException("Could not find any graph nodes corresponding to the node and/or ott ids provided.");
    
        } else {
            HashMap<String, Object> vals = new HashMap<String, Object>();
            Node mrca = ge.getDraftTreeMRCAForNodes(tips, true);
            
            vals.put("found_nodes", tips);
            
            if (!unmatchedOtts.isEmpty()) {
                vals.put("unmatched_ott_ids", unmatchedOtts);
            }
            if (!unmatchedNodes.isEmpty()) {
                vals.put("unmatched_node_ids", unmatchedNodes);
            }
            
            vals.put("mrca_node_id", mrca.getId());
            vals.put("mrca_name", mrca.getProperty(NodeProperty.NAME.propertyName));
            vals.put("mrca_unique_name", mrca.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
            vals.put("mrca_rank", mrca.getProperty(NodeProperty.TAX_RANK.propertyName));
            vals.put("mrca_ott_id", mrca.getProperty(NodeProperty.TAX_UID.propertyName));
    
            return OTRepresentationConverter.convert(vals);
        }
    }
    */
}

