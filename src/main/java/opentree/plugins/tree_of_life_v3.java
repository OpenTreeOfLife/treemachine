package opentree.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import jade.tree.deprecated.JadeTree;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashSet;
import opentree.GraphExplorer;
import opentree.constants.RelType;
import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.TaxonNotFoundException;
import org.opentree.exceptions.TreeNotFoundException;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OTRepresentationConverter;

// Graph of Life Services 
public class tree_of_life_v3 extends ServerPlugin {
    
    // not currently advertized, but should be. well, not until multiple trees gets the go ahead
    /*
    @Description("Returns brief summary information about the draft synthetic tree(s) "
        + "currently contained within the graph database.") 
    @PluginTarget(GraphDatabaseService.class)
    public Representation draft_trees (@Source GraphDatabaseService graphDb) throws IllegalArgumentException {

        HashMap<String, Object> graphInfo = new HashMap<>();
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        ArrayList<String> synthTreeIDs = ge.getSynthTreeIDs();
        
        if (synthTreeIDs.size() > 0) {
            graphInfo.put("num_synth_trees", synthTreeIDs.size());
            LinkedList<HashMap<String, Object>> trees = new LinkedList<>();
            
            // trying not to hardcode things here; arrays make it difficult
            for (String treeID : synthTreeIDs) {
                HashMap<String, Object> draftTreeInfo = new HashMap<>();
                Node meta = ge.getSynthesisMetaNodeByName(treeID);
                
                draftTreeInfo.put("synth_id", treeID);
                draftTreeInfo.put("date_completed", meta.getProperty("date_completed"));
                draftTreeInfo.put("taxonomy_version", meta.getProperty("taxonomy_version"));
                
                // root node info
                draftTreeInfo.put("root_taxon_name", meta.getProperty("root_taxon_name"));
                draftTreeInfo.put("root_ott_id", meta.getProperty("root_ott_id"));
                
                // tree constituents
                draftTreeInfo.put("num_tips", meta.getProperty("num_tips"));
                draftTreeInfo.put("num_source_studies", meta.getProperty("num_source_studies"));
                draftTreeInfo.put("num_source_trees", meta.getProperty("num_source_trees"));
                draftTreeInfo.put("root_node_id", meta.getProperty("root_ot_node_id"));
                
                trees.add(draftTreeInfo);
            }
            graphInfo.put("synth_trees", trees);
        } else {
            ge.shutdownDB();
            throw new IllegalArgumentException("Could not find any draft synthetic trees in the graph.");
        }

        return OTRepresentationConverter.convert(graphInfo);
    }
    */
    
    
    // NEW: add treeid as a optional argument, default to most recent
    @Description("Returns summary information about the most recent draft tree of life, "
        + "including information about the list of source trees and the taxonomy used to build it.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation about (@Source GraphDatabaseService graphDb,
        
//        @Description("Synthetic tree identifier (defaults to most recent).")
//        @Parameter(name = "synth_id", optional = true)
//        String synthID,
        
        @Description("Return a list of source studies.")
        @Parameter(name = "include_source_list", optional = true)
        Boolean source_list
        
        ) throws TaxonNotFoundException, MultipleHitsException {

        GraphExplorer ge = new GraphExplorer(graphDb);
        HashMap<String, Object> draftTreeInfo = new HashMap<>();
        Boolean returnSourceList = false;
        String synthTreeID = null;
        
        if (source_list != null && source_list == true) {
            returnSourceList = true;
        }
        
        // temporary, for hiding the multitree stuff
        String synthID = null;
        
        // get synthetic tree identifier
        if (synthID != null) {
            synthTreeID = synthID;
            if (!ge.checkExistingSynthTreeID(synthID)) {
                ge.shutdownDB();
                String ret = "Could not find a synthetic tree corresponding to the 'synth_id' arg: '"
                    + synthTreeID + "'. Leave blank to default to the current synthetic tree.";
                throw new IllegalArgumentException(ret);
            }
        } else { // default to most recent
            synthTreeID = ge.getMostRecentSynthTreeID();
        }
        
        try {
            // Most information will come from the synthesis metadata node
            Node meta = ge.getSynthesisMetaNodeByName(synthTreeID);
            
            // general info
            draftTreeInfo.put("synth_id", synthTreeID);
            draftTreeInfo.put("date_created", meta.getProperty("date_completed"));
            draftTreeInfo.put("taxonomy_version", meta.getProperty("taxonomy_version"));

            // root node info - collect into separate object ('blob')
            HashMap<String, Object> rootInfo = ge.getNodeBlob((String)meta.getProperty("root_ot_node_id"), synthTreeID);
            // have to do this separately, as for all other nodes this is stored in outgoing rel
            //rootInfo.put("num_tips", meta.getProperty("num_tips"));
            draftTreeInfo.put("root", rootInfo);
            
            // tree constituents
            //draftTreeInfo.put("num_tips", meta.getProperty("num_tips"));
            draftTreeInfo.put("num_source_studies", meta.getProperty("num_source_studies"));
            draftTreeInfo.put("num_source_trees", meta.getProperty("num_source_trees"));

            if (returnSourceList) {
                Node sourceMapNode = ge.getSourceMapNodeByName(synthTreeID);
                draftTreeInfo.put("source_list", Arrays.asList((String[]) meta.getProperty("sources")));

                HashMap<String, Object> sourceMap = new HashMap<>();
                for (String key : sourceMapNode.getPropertyKeys()) {
                    HashMap<String, String> formatSource = ge.stringToMap((String) sourceMapNode.getProperty(key));
                    sourceMap.put(key, formatSource);
                }
                draftTreeInfo.put("source_id_map", sourceMap);
            }
            draftTreeInfo.put("filtered_flags", Arrays.asList((String[]) meta.getProperty("filtered_flags")));
                
        } finally {
            ge.shutdownDB();
        }
        return OTRepresentationConverter.convert(draftTreeInfo);
    }
    
    
    @Description("Returns summary information about a node in the graph. The node "
        + "of interest may be specified using *either* a `node_id`, or an `ott_id`, "
        + "**but not both**. If the specified node is not in the graph, an exception "
        + "will be thrown.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation node_info (
        @Source GraphDatabaseService graphDb,
        
        @Description("The `node_id` of the node of interest. This argument may not be "
            + "combined with `ott_id`.")
        @Parameter(name = "node_id", optional = true)
        String nodeID,
        
        @Description("The `ott_id` of the node of interest. This argument may not be "
            + "combined with `node_id`.")
        @Parameter(name = "ott_id", optional = true)
        Long ottID,
        
        @Description("Include the ancestral lineage of the node in the draft tree. If "
            + "this argument is \"true\", then a list of all the ancestors of this node "
            + "in the draft tree, down to the root of the tree itself, will be included "
            + "in the results. Higher list indices correspond to more incluive (i.e. "
            + "deeper) ancestors, with the immediate parent of the specified node "
            + "occupying position 0 in the list.")
        @Parameter(name = "include_lineage", optional = true)
        Boolean includeLineage
        
        ) throws IllegalArgumentException, TaxonNotFoundException {
        
        if (nodeID == null && ottID == null) {
            String ret = "Must provide a \"node_id\" or \"ott_id\" argument.";
            throw new IllegalArgumentException(ret);
        } else if (nodeID != null && ottID != null) {
            String ret = "Provide only one \"node_id\" or \"ott_id\" argument.";
            throw new IllegalArgumentException(ret);
        }
        
        HashMap<String, Object> nodeIfo = new HashMap<>();
        
        Node qNode = null;
        String synthTreeID = null; // will loop if there are multiple
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        if (ottID != null) {
            Node n = null;
            try {
                n = ge.findGraphTaxNodeByUID(String.valueOf(ottID));
            } catch (TaxonNotFoundException e) {
            }
            if (n != null) {
                qNode = n;
            } else {
                String ret = "Could not find any graph nodes corresponding to the \"ott_id\" provided.";
                throw new TaxonNotFoundException(ret);
            }
        } else if (nodeID != null) {
            Node n = null;
            try {
                n = ge.findGraphNodeByOTTNodeID(nodeID);
            } catch (TaxonNotFoundException e) {
            }
            if (n != null) {
                qNode = n;
            } else {
                String ret = "Could not find any graph nodes corresponding to the \"node_id\" provided.";
                throw new TaxonNotFoundException(ret);
            }
        }
        
        //nodeIfo.put("node_id", qNode.getProperty("ot_node_id"));
        
        // get all taxonomic information
        //HashMap<String, Object> taxInfo = ge.getTaxonBlob(qNode);
        //nodeIfo.put("taxon", taxInfo);
        
        
        // Relationship tr = curnode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING);
        
        // loop over all synth trees this node is in. for the moment, there will only be one (well, ready for more...)
        if (qNode.hasRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
            
    // NOTE:
    // this will behave as expected if there is only 1 tree in the graph
    // when multiple trees are finally served this will need to be reverted to earlier version
    
            for (Relationship rel : qNode.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
                HashMap<String, Object> treeInfo = new HashMap<>();
                
                synthTreeID = (String) rel.getProperty("name");
                
                HashMap<String, Object> nodeBlob = ge.getNodeBlob(qNode, synthTreeID);
                nodeIfo.putAll(nodeBlob);
                
                HashSet<String> uniqueSources = new HashSet<>();
                HashMap<String, Object> props = ge.getSynthMetadataAndUniqueSources(qNode, synthTreeID, uniqueSources);
                treeInfo.putAll(props);
                
                if (includeLineage != null && includeLineage == true) {
                    LinkedList<HashMap<String, Object>> lineage = new LinkedList<>();
                    List<Node> nodeList = ge.getPathToRoot(qNode, RelType.SYNTHCHILDOF, synthTreeID);
                    // need to collect supporting sources here, add them to the list from the focal node
                    for (Node cn : nodeList) {
                        HashMap<String, Object> indInfo = ge.getNodeBlob(cn, synthTreeID);
                        HashMap<String, Object> indProps = ge.getSynthMetadataAndUniqueSources(cn, synthTreeID, uniqueSources);
                        indInfo.putAll(indProps);
                        
                        lineage.add(indInfo);
                    }
                    treeInfo.put("lineage", lineage);
                }
                
                // source to meta map
                HashMap<String, Object> sourceMap = new HashMap<>();
                for (String ind : uniqueSources) {
                    HashMap<String, String> formatSource = ge.getSourceMapIndSource(ind, synthTreeID);
                    sourceMap.put(ind, formatSource);
                }
                treeInfo.put("source_id_map", sourceMap);
                //treeInfo.put("synth_id", synthTreeID);
                nodeIfo.putAll(treeInfo);
            }
        }
        ge.shutdownDB();
        return OTRepresentationConverter.convert(nodeIfo);
    }
    
    
    @Description("Get the MRCA of a set of nodes on the most current draft tree. Accepts "
        + "any combination of node ids and ott ids as input. Returns information about "
        + "the most recent common ancestor (MRCA) node as well as the most recent "
        + "taxonomic ancestor (MRTA) node (the smallest taxon in the synthetic tree that "
        + "encompasses the query; the MRCA and MRTA may be the same node). Both node ids and "
        + "ott ids that are not in the synthetic tree are dropped from the MRCA calculation. "
        + "Returns any unmatched node ids / ott ids.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation mrca (@Source GraphDatabaseService graphDb,
        
//        @Description("Synthetic tree identifier (defaults to most recent).")
//        @Parameter(name = "synth_id", optional = true)
//        String synthID,
        
        @Description("A set of open tree node ids")
        @Parameter(name = "node_ids", optional = true)
        String[] nodeIDs,
        
        @Description("A set of ott ids")
        @Parameter(name = "ott_ids", optional = true)
        long[] ottIDs
        
        ) throws IllegalArgumentException {
        
        ArrayList<Node> tips = new ArrayList<>();
        ArrayList<Long> ottIdsNotInTree = new ArrayList<>();
        ArrayList<String> nodesIDsNotInTree = new ArrayList<>();
        
        if ((nodeIDs == null || nodeIDs.length < 1) && (ottIDs == null || ottIDs.length < 1)) {
            String ret = "You must supply at least one node_id or ott_id.";
            throw new IllegalArgumentException(ret);
        }
        
        String synthTreeID = null;
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        // temporary, for hiding the multitree stuff
        String synthID = null;
        
        // get synthetic tree identifier
        if (synthID != null) {
            synthTreeID = synthID;
            if (!ge.checkExistingSynthTreeID(synthID)) {
                ge.shutdownDB();
                String ret = "Could not find a synthetic tree corresponding to the 'synth_id' arg: '"
                    + synthTreeID + "'. Leave blank to default to the current synthetic tree.";
                throw new IllegalArgumentException(ret);
            }
        } else { // default to most recent
            synthTreeID = ge.getMostRecentSynthTreeID();
        }
        
        // node_ids
        if (nodeIDs != null && nodeIDs.length > 0) {
            for (String nodeId : nodeIDs) {
                Node n = null;
                try {
                    n = ge.findGraphNodeByOTTNodeID(nodeId);
                } catch (TaxonNotFoundException e) {}
                if (n != null) {
                    // need to check if taxon is in the relevant synthetic tree
                    if (ge.nodeIsInSyntheticTree(n, synthTreeID)) {
                        tips.add(n);
                    } else {
                        nodesIDsNotInTree.add(nodeId);
                    }
                } else {
                    // could not find node at all
                    nodesIDsNotInTree.add(nodeId);
                }
            }
        }
        
        // ott_ids
        if (ottIDs != null && ottIDs.length > 0) {
            for (long ottId : ottIDs) {
                Node n = null;
                try {
                    n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
                } catch (TaxonNotFoundException e) {}
                if (n != null) {
                    if (ge.nodeIsInSyntheticTree(n, synthTreeID)) {
                        tips.add(n);
                    } else { 
                        ottIdsNotInTree.add(ottId);
                    }
                } else {
                    ottIdsNotInTree.add(ottId);
                }
            }
        }

        if (tips.size() < 1) {
            String ret = "Could not find any graph nodes corresponding to the arg "
                + "\"node_ids\" provided.";
            throw new IllegalArgumentException(ret);
        } else {
            HashMap<String, Object> res = new HashMap<>();
            Node mrca = ge.getDraftTreeMRCA(tips, synthTreeID);
            
            //res.put("synth_id", synthTreeID);
            
            HashMap<String, Object> mrcaInfo = ge.getNodeBlob(mrca, synthTreeID);
            res.put("mrca", mrcaInfo);
            
            //res.put("node_id", mrca.getProperty("ot_node_id"));
            
            if (!ottIdsNotInTree.isEmpty()) {
                res.put("ott_ids_not_in_tree", ottIdsNotInTree);
            }
            if (!nodesIDsNotInTree.isEmpty()) {
                res.put("node_ids_not_in_tree", nodesIDsNotInTree);
            }
            
            // now find the most recent taxonomic ancestor (in tree), if mrca is not a taxon
            if (!mrca.hasProperty("name")) {
                Node mrta = ge.getDraftTreeMRTA(mrca, synthTreeID);

                HashMap<String, Object> mrtaInfo = ge.getTaxonBlob(mrta);
                res.put("nearest_taxon", mrtaInfo);
            }
            
            ge.shutdownDB();
            return OTRepresentationConverter.convert(res);
        }
    }
    
    
    @Description("Return a tree with tips corresponding to the nodes identified in the "
        + "input set, that is consistent with topology of the most current draft tree. This "
        + "tree is equivalent to the minimal subtree induced on the draft tree by the set "
        + "of identified nodes. Nodes ids that do not correspond to any found nodes in the "
        + "graph, or which are in the graph but are absent from the particualr synthetic "
        + "tree, will be identified in the output (but will of course not be present in "
        + "the resulting induced tree). Branch lengths are not currently returned, and "
        + "the leaf labels of the tree may either be taxonomic names or (for nodes not "
        + "corresponding directly to named taxa) node ids.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation induced_subtree (@Source GraphDatabaseService graphDb,
        
//        @Description("Synthetic tree identifier (defaults to most recent).")
//        @Parameter(name = "synth_id", optional = true)
//        String synthID,
        
        @Description("A set of open tree node ids")
        @Parameter(name = "node_ids", optional = true)
        String[] nodeIDs,
        
        @Description("A set of ott ids")
        @Parameter(name = "ott_ids", optional = true)
        long[] ottIDs,
        
        @Description("Label format. Valid formats: \"name\", \"id\", or \"name_and_id\" (default)")
        @Parameter(name = "label_format", optional = true)
        String labFormat
        
        ) throws IllegalArgumentException {
        
        ArrayList<Node> tips = new ArrayList<>();
        ArrayList<Long> ottIdsNotInTree = new ArrayList<>();
        ArrayList<String> nodesIDsNotInTree = new ArrayList<>();
        String labelFormat = null;
        
        if ((nodeIDs == null || nodeIDs.length < 1) && (ottIDs == null || ottIDs.length < 1)) {
            String ret = "You must supply at least one node_id or ott_id.";
            throw new IllegalArgumentException(ret);
        }
        
        if (labFormat == null) {
            labelFormat = "name_and_id";
        } else {
            if (!labFormat.matches("name|id|name_and_id")) {
                String ret = "Invalid 'label_format' arg: '" + labFormat + "'. "
                    + "Valid formats: \"name\", \"id\", or \"name_and_id\" (default).";
                throw new IllegalArgumentException(ret);
            } else {
                labelFormat = labFormat;
            }
        }
        
        String synthTreeID = null;
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        // temporary, for hiding the multitree stuff
        String synthID = null;
        
        // get synthetic tree identifier
        if (synthID != null) {
            synthTreeID = synthID;
            if (!ge.checkExistingSynthTreeID(synthID)) {
                ge.shutdownDB();
                String ret = "Could not find a synthetic tree corresponding to the 'synth_id' arg: '"
                    + synthTreeID + "'. Leave blank to default to the current synthetic tree.";
                throw new IllegalArgumentException(ret);
            }
        } else { // default to most recent
            synthTreeID = ge.getMostRecentSynthTreeID();
        }
        
        // node_ids
        if (nodeIDs != null && nodeIDs.length > 0) {
            for (String nodeId : nodeIDs) {
                Node n = null;
                try {
                    n = ge.findGraphNodeByOTTNodeID(nodeId);
                } catch (TaxonNotFoundException e) {}
                if (n != null) {
                    // need to check if taxon is in the relevant synthetic tree
                    if (ge.nodeIsInSyntheticTree(n, synthTreeID)) {
                        tips.add(n);
                    } else {
                        nodesIDsNotInTree.add(nodeId);
                    }
                } else {
                    // could not find node at all
                    nodesIDsNotInTree.add(nodeId);
                }
            }
        }
        
        // ott_ids
        if (ottIDs != null && ottIDs.length > 0) {
            for (long ottId : ottIDs) {
                Node n = null;
                try {
                    n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
                } catch (TaxonNotFoundException e) {}
                if (n != null) {
                    if (ge.nodeIsInSyntheticTree(n, synthTreeID)) {
                        tips.add(n);
                    } else { 
                        ottIdsNotInTree.add(ottId);
                    }
                } else {
                    ottIdsNotInTree.add(ottId);
                }
            }
        }
        
        if (tips.size() < 2) {
            String ret = "Not enough valid node ids provided to construct a subtree "
                + "(there must be at least two).";
            throw new IllegalArgumentException(ret);
        } else {
            HashMap<String, Object> res = new HashMap<>();
            
            //res.put("synth_id", synthTreeID);
            if (!ottIdsNotInTree.isEmpty()) {
                res.put("ott_ids_not_in_tree", ottIdsNotInTree);
            }
            if (!nodesIDsNotInTree.isEmpty()) {
                res.put("node_ids_not_in_tree", nodesIDsNotInTree);
            }
            
            res.put("newick", ge.getInducedSubtree(tips, synthTreeID, labelFormat).getNewick(false) + ";");
            return OTRepresentationConverter.convert(res);
        }
    }
    
    
    // TODO: add relevant sources; need design input
    @Description("Return a subtree of the draft tree descended from some node specified "
        + "using *either* a node id or an ott id, **but not both**. If the specified node "
        + "is not found an error will be returned.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation subtree (@Source GraphDatabaseService graphDb,
        
//        @Description("Synthetic tree identifier (defaults to most recent).")
//        @Parameter(name = "synth_id", optional = true)
//        String synthID,
        
        @Description("The `node_id` of the node of interest. This argument may not be "
            + "combined with `ott_id`.")
        @Parameter(name = "node_id", optional = true)
        String nodeID,
        
        @Description("The `ott_id` of the node of interest. This argument may not be "
            + "combined with `node_id`.")
        @Parameter(name = "ott_id", optional = true)
        Long ottID,
        
        @Description("Label format. Newick only (ignored for arguson format). "
            + "Valid formats: \"name\", \"id\", or \"name_and_id\" (default)")
        @Parameter(name = "label_format", optional = true)
        String labFormat,
        
        @Description("Tree format. Valid formats: \"newick\" (default) or \"arguson\"")
        @Parameter(name = "format", optional = true)
        String tFormat,
        
        @Description("An integer controlling the max number of edges between the leaves and "
            + "the root node. A negative number specifies that no depth limit will be applied. "
            + "The default is 5.")
        @Parameter(name = "height_limit", optional = true)
        Integer hLimit
        
        ) throws TreeNotFoundException, IllegalArgumentException, TaxonNotFoundException {
        
        if (nodeID == null && ottID == null) {
            String ret = "Must provide a \"node_id\" or \"ott_id\" argument.";
            throw new IllegalArgumentException(ret);
        } else if (nodeID != null && ottID != null) {
            String ret = "Provide only one \"node_id\" or \"ott_id\" argument.";
            throw new IllegalArgumentException(ret);
        }
        
        HashMap<String, Object> responseMap = new HashMap<>();
        
        // so. very. clunky. what a terrible design...
        int newickDepth = -1; // negative is no limit
        int argusonDepth = 5;
        Integer maxNumTipsNewick = 25000; // TODO: is this the best value? Test this. ***
        Integer maxNumTipsArguson = 25000; // splitting out since will likely have to be much smaller
        String labelFormat = null;
        String treeFormat = null;
        
        // temporary, for hiding the multitree stuff
        String synthID = null;
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        Node qNode = null;
        String synthTreeID = null;
        
        // set node label format. never gets sent to arguson, though...
        if (labFormat == null) {
            labelFormat = "name_and_id";
        } else {
            if (!labFormat.matches("name|id|name_and_id")) {
                String ret = "Invalid 'label_format' arg: '" + labFormat + "'. "
                    + "Valid formats: \"name\", \"id\", or \"name_and_id\" (default).";
                throw new IllegalArgumentException(ret);
            } else {
                labelFormat = labFormat;
            }
        }
        
        // set output tree format
        if (tFormat == null) {
            treeFormat = "newick";
        } else {
            if (!tFormat.matches("newick|arguson")) {
                String ret = "Invalid 'format' arg: '" + tFormat + "'. "
                    + "Valid formats: \"newick\" (default) or \"arguson\".";
                throw new IllegalArgumentException(ret);
            } else {
                treeFormat = tFormat;
            }
        }
        
        // set depth limit
        if (hLimit != null) {
            if ("newick".equals(treeFormat)) {
                newickDepth = hLimit;
            } else {
                argusonDepth = hLimit;
            }
        }
        
        // get synthetic tree identifier
        if (synthID != null) {
            synthTreeID = synthID;
            if (!ge.checkExistingSynthTreeID(synthID)) {
                ge.shutdownDB();
                String ret = "Could not find a synthetic tree corresponding to the 'synth_id' arg: '"
                    + synthTreeID + "'. Leave blank to default to the current synthetic tree.";
                throw new IllegalArgumentException(ret);
            }
        } else { // default to most recent
            synthTreeID = ge.getMostRecentSynthTreeID();
        }
        
        // get start node
        if (ottID != null) {
            Node n = null;
            try {
                n = ge.findGraphTaxNodeByUID(String.valueOf(ottID));
            } catch (TaxonNotFoundException e) {
                String ret = "Could not find any graph nodes corresponding to the \"ott_id\" provided.";
                throw new IllegalArgumentException(ret);
                //throw new TaxonNotFoundException(ret);
            }
            if (n != null) {
                qNode = n;
            } else {
                String ret = "Could not find any graph nodes corresponding to the \"ott_id\" provided.";
                throw new TaxonNotFoundException(ret);
            }
            // check that startNode is indeed in the synthetic tree
            if (!ge.nodeIsInSyntheticTree(qNode, synthTreeID)) {
                ge.shutdownDB();
                String ret = "Queried \"ott_id\": " + ottID + " is in the graph, but "
                    + "not in the draft tree: " + synthTreeID;
                throw new IllegalArgumentException(ret);
            }
        } else if (nodeID != null) {
            Node n = null;
            try {
                n = ge.findGraphNodeByOTTNodeID(nodeID);
            } catch (TaxonNotFoundException e) {
            }
            if (n != null) {
                qNode = n;
            } else {
                String ret = "Could not find any graph nodes corresponding to the \"node_id\" provided.";
                throw new TaxonNotFoundException(ret);
            }
            // check that startNode is indeed in the synthetic tree
            if (!ge.nodeIsInSyntheticTree(qNode, synthTreeID)) {
                ge.shutdownDB();
                String ret = "Queried \"node_id\": " + nodeID + " is in the graph, but "
                    + "not in the draft tree: " + synthTreeID;
                throw new IllegalArgumentException(ret);
            }
        }
        
        
        
        
        
        // check that the returned tree is not too large
        // this needs to be changed since truncated trees can now be returned.
        // this can at least be considered a maxiumum possible size
        Integer numMRCA = ge.getNumTipDescendants(qNode, synthTreeID);
        
        
        
        
        if ("newick".equals(treeFormat)) {
            if (numMRCA > maxNumTipsNewick && newickDepth == -1) {
                ge.shutdownDB();
                String ret = "Requested tree (" + numMRCA + " tips) is larger than currently "
                    + "allowed by this service (" + maxNumTipsNewick + " tips). For larger trees, "
                    + "please download the full tree directly from: http://files.opentreeoflife.org/trees/";
                throw new IllegalArgumentException(ret);
            }
        }
        
        
        
        
        
        // get the subtree for export
        JadeTree tree = null;
        try {
            if (newickDepth == -1) {
                tree = ge.extractDraftTree(qNode, synthTreeID, labelFormat);
            } else {
                tree = ge.reconstructDepthLimitedSubtree(synthTreeID, qNode, newickDepth, labelFormat);
            }
        } finally {
            ge.shutdownDB();
        }
        
        responseMap.put("newick", tree.getRoot().getNewick(false) + ";");
        //responseMap.put("newickDepth", newickDepth);
        return OTRepresentationConverter.convert(responseMap);
    }
    
    
    // TODO: Possibility of replacing tip label ottids with names?!?
    // is there a plan to do something with the "format" arg? if not, deprecate
    @Description("Returns a processed source tree (corresponding to a tree in some [study](#studies)) used "
        + "as input for the synthetic tree. Although the result of this service is a tree corresponding directly to a "
        + "tree in a study, the representation of the tree in the graph may differ slightly from its "
        + "canonical representation in the study, due to changes made during tree import: 1) includes "
        + "only the curator-designated ingroup clade, and 2) both unmapped and duplicate tips are pruned "
        + "from the tree. The tree is returned in newick format, with terminal nodes labelled with ott ids.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation source_tree (
        @Source GraphDatabaseService graphDb,

        @Description("The study identifier. Will typically include a prefix (\"pg_\" or \"ot_\").")
        @Parameter(name = "study_id", optional = false)
        String studyID,

        @Description("The tree identifier for a given study.")
        @Parameter(name = "tree_id", optional = false)
        String treeID,

        @Description("The synthetic tree identifier (defaults to most recent).")
        @Parameter(name = "synth_id", optional = true)
        String synthID,

        @Description("The name of the return format. The only currently supported format is newick.")
        @Parameter(name = "format", optional = true)
        String format

        ) throws IllegalArgumentException {

        HashMap<String, Object> responseMap = new HashMap<>();
        String source = studyID + "_" + treeID;
        String synthTreeID = null;
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        // get synthetic tree identifier
        if (synthID != null) {
            synthTreeID = synthID;
            if (!ge.checkExistingSynthTreeID(synthID)) {
                ge.shutdownDB();
                String ret = "Could not find a synthetic tree corresponding to the 'synth_id' arg: '"
                    + synthTreeID + "'. Leave blank to default to the current synthetic tree.";
                throw new IllegalArgumentException(ret);
            }
        } else { // default to most recent
            synthTreeID = ge.getMostRecentSynthTreeID();
        }
        ge.shutdownDB();
        
        String tree = getSourceTree(source, synthTreeID);

        if (tree == null) {
            throw new IllegalArgumentException("Invalid source id '" + source + "' provided.");
        } else {
            responseMap.put("newick", tree);
            responseMap.put("synth_id", synthTreeID);
        }
        
        return OTRepresentationConverter.convert(responseMap);
    }
    
    
    // fetch the processed input source tree newick from files.opentree.org
    // source has format: studyID + "_" + treeID
    // this should be private (i think)
    private String getSourceTree(String source, String synTreeID) {
        String tree = null;
        
        // synTreeID will be of format: "opentree4.0"
        String version = synTreeID.replace("opentree", "");
        
        String urlbase = "http://files.opentreeoflife.org/preprocessed/v"
            + version + "/trees/" + source + ".tre";
        System.out.println("Looking up study: " + urlbase);

        try {
            URL phurl = new URL(urlbase);
            URLConnection conn = (URLConnection) phurl.openConnection();
            conn.connect();
            try (BufferedReader un = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                tree = un.readLine();
            }
            return tree;
        } catch (Exception e) {
        }
        return tree;
    }
    
    
}
