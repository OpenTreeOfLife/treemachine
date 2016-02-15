package opentree.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import jade.tree.deprecated.JadeTree;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import opentree.GraphExplorer;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.constants.GeneralConstants;
import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.TaxonNotFoundException;
import org.opentree.exceptions.TreeNotFoundException;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
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
public class tree_of_life extends ServerPlugin {
    
    // temporary for testing
    @Description("Returns list of existing synth tree ids.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation foo (@Source GraphDatabaseService graphDb) {
        // grab existing synth tree ids
        GraphExplorer ge = new GraphExplorer(graphDb);
        ArrayList<String> synthTreeIDs = ge.getSynthTreeIDs(); // these are sorted
        ge.shutdownDB();
        
        return OTRepresentationConverter.convert(synthTreeIDs);
    }
    
    
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
                
                draftTreeInfo.put("tree_id", treeID);
                draftTreeInfo.put("date_completed", meta.getProperty("date_completed"));
                draftTreeInfo.put("taxonomy_version", meta.getProperty("taxonomy_version"));
                
                // root node info
                draftTreeInfo.put("root_taxon_name", meta.getProperty("root_taxon_name"));
                draftTreeInfo.put("root_ott_id", meta.getProperty("root_ott_id"));
                
                // tree constituents
                draftTreeInfo.put("num_tips", meta.getProperty("num_tips"));
                draftTreeInfo.put("num_source_studies", meta.getProperty("num_source_studies"));
                draftTreeInfo.put("num_source_trees", meta.getProperty("num_source_trees"));
                draftTreeInfo.put("root_ot_node_id", meta.getProperty("root_ot_node_id"));
                
                trees.add(draftTreeInfo);
            }
            graphInfo.put("synth_trees", trees);
        } else {
            ge.shutdownDB();
            throw new IllegalArgumentException("Could not find any draft synthetic trees in the graph.");
        }

        return OTRepresentationConverter.convert(graphInfo);
    }
    
    
    @Description("Returns summary information about a node in the graph. The node "
        + "of interest may be specified using its `ott_node_id`. If the specified "
        + "node is not in the graph, an exception will be thrown.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation node_info (
        @Source GraphDatabaseService graphDb,
        
        @Description("The `ot_node_id` of the node of interest.")
        @Parameter(name = "ot_node_id", optional = false)
        String otNodeID
        
        ) throws IllegalArgumentException, TaxonNotFoundException {
        
        HashMap<String, Object> nodeIfo = new HashMap<>();
        
        String nodeId = otNodeID;
        Node qNode = null;
        String synthTreeID = null;
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        try {
            qNode = ge.findGraphNodeByOTTNodeID(nodeId);
        } catch (TaxonNotFoundException e) {
        }
        
        if (qNode == null) {
            ge.shutdownDB();
            String ret = "Could not find a graph node corresponding to the 'ot_node_id' arg: '"
                + otNodeID + "'.";
            throw new IllegalArgumentException(ret);
        }
        
        nodeIfo.putAll(ge.getNodeTaxInfo(qNode));
        
        // loop over all synth trees this node is in
        if (qNode.hasRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
            for (Relationship rel : qNode.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
                HashMap<String, Object> treeInfo = new HashMap<>();
                
                int nTips = (int) rel.getProperty("tip_descendants");
                synthTreeID = (String) rel.getProperty("name");
                treeInfo.put("tip_descendants", nTips);
                
                HashMap<String, Object> props = ge.getSynthMetadata(qNode, synthTreeID);
                treeInfo.putAll(props);
                // todo: source to meta map
                HashMap<String, Object> sourceMap = new HashMap<>();
                for (String key : props.keySet()) {
                    HashMap<String, Object> ind = (HashMap<String, Object>) props.get(key);
                    for (String src : ind.keySet()) {
                        HashMap<String, String> fsrc = ge.getSourceMapIndSource(src, synthTreeID);
                        sourceMap.put(src, fsrc);
                    }
                }
                treeInfo.put("source_id_map", sourceMap);
                nodeIfo.put(synthTreeID, treeInfo);
            }
        }
        ge.shutdownDB();
        return OTRepresentationConverter.convert(nodeIfo);
    }
    
    
    // NEW: add treeid as a optional argument, default to most recent
    @Description("Returns summary information about a draft tree of life (by default "
        + "the most recent version), including information about the list of source "
        + "trees and the taxonomy used to build it.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation about (@Source GraphDatabaseService graphDb,
        
        @Description("Return a list of source studies.")
        @Parameter(name = "study_list", optional = true)
        Boolean study_list,
        
        @Description("Synthetic tree identifier (defaults to most recent).")
        @Parameter(name = "tree_id", optional = true)
        String treeID
        
        ) throws TaxonNotFoundException, MultipleHitsException {

        GraphExplorer ge = new GraphExplorer(graphDb);
        HashMap<String, Object> draftTreeInfo = new HashMap<>();
        Boolean returnStudyList = true; // default to true for now
        String synthTreeID = null;
        Node meta = null;
        
        if (study_list != null && study_list == false) {
            returnStudyList = false;
        }
        if (treeID != null) {
            synthTreeID = treeID;
        }
        
        // Most information will come from the synthesis metadata node
        try {
            if (synthTreeID != null) {
                meta = ge.getSynthesisMetaNodeByName(synthTreeID);
                // invalid treeid
                if (meta == null) {
                    ge.shutdownDB();
                    String ret = "Could not find a synthetic tree corresponding to the 'tree_id' arg: '"
                        + synthTreeID + "'. Leave blank to default to the current synthetic tree.";
                    throw new IllegalArgumentException(ret);
                }
            } else {
                // default to most recent
                meta = ge.getMostRecentSynthesisMetaNode();
                synthTreeID = (String) meta.getProperty("tree_id");
            }
            
            if (meta != null) {
                // general info
                draftTreeInfo.put("tree_id", synthTreeID);
                draftTreeInfo.put("date_completed", meta.getProperty("date_completed"));
                draftTreeInfo.put("taxonomy_version", meta.getProperty("taxonomy_version"));
                
                // root node info
                draftTreeInfo.put("root_taxon_name", meta.getProperty("root_taxon_name"));
                draftTreeInfo.put("root_ott_id", meta.getProperty("root_ott_id"));
                
                // tree constituents
                draftTreeInfo.put("num_tips", meta.getProperty("num_tips"));
                draftTreeInfo.put("num_source_studies", meta.getProperty("num_source_studies"));
                draftTreeInfo.put("num_source_trees", meta.getProperty("num_source_trees"));
                draftTreeInfo.put("root_ot_node_id", meta.getProperty("root_ot_node_id"));
                
                if (returnStudyList) {
                    Node sourceMapNode = ge.getSourceMapNodeByName(synthTreeID);
                    draftTreeInfo.put("sources", Arrays.asList((String[]) meta.getProperty("sources")));
                    
                    HashMap<String, Object> sourceMap = new HashMap<>();
                    for (String key : sourceMapNode.getPropertyKeys()) {
                        HashMap<String, String> formatSource = ge.stringToMap((String) sourceMapNode.getProperty(key));
                        sourceMap.put(key, formatSource);
                    }
                    draftTreeInfo.put("source_id_map", sourceMap);
                }
                draftTreeInfo.put("filtered_flags", Arrays.asList((String[]) meta.getProperty("filtered_flags")));
                
            } else {
                ge.shutdownDB();
                draftTreeInfo.put("error", "No synthetic tree found in the graph.");
                return OTRepresentationConverter.convert(draftTreeInfo);
            }    
        } finally {
            ge.shutdownDB();
        }
        return OTRepresentationConverter.convert(draftTreeInfo);
    }

    
    // *** TODO: update to specific tree; will need to check if nodes are in _that_ tree
    @Description("Get the MRCA of a set of nodes on the current draft tree. Accepts any combination of node ids and ott "
        + "ids as input. Returns information about the most recent common ancestor (MRCA) node as well as the most recent "
        + "taxonomic ancestor (MRTA) node (the smallest taxon that encompasses the query; the MRCA "
        + "and MRTA may be the same node). Node ids that are not in the synthetic tree are dropped from the MRCA "
        + "calculation. For a valid ott id that is not in the synthetic tree (i.e. it is not recovered as monophyletic "
        + "from the source tree information), the taxonomic descendants of the node are used in the MRCA calculation. "
        + "Returns any unmatched node ids / ott ids.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation mrca (@Source GraphDatabaseService graphDb,
        
        @Description("A set of node ids")
        @Parameter(name = "node_ids", optional = true)
        long[] nodeIds,
        
        @Description("A set of ott ids")
        @Parameter(name = "ott_ids", optional = true)
        long[] ottIds,
        
        @Description("Synthetic tree identifier (defaults to most recent).")
        @Parameter(name = "tree_id", optional = true)
        String treeID,
        
        @Description("A set of open tree node ids")
        @Parameter(name = "ot_node_ids", optional = true)
        String[] otNodeIDs
        
        ) throws IllegalArgumentException {

        if ((nodeIds == null || nodeIds.length < 1) && (ottIds == null || ottIds.length < 1)) {
            throw new IllegalArgumentException("You must supply at least one node_id or ott_id.");
        }
        
        // keep this one
        ArrayList<Node> tips = new ArrayList<Node>();
        
        // no longer dealing with longs
        ArrayList<Long> invalidNodesIds = new ArrayList<Long>();
        ArrayList<Long> invalidOttIds = new ArrayList<Long>();
        ArrayList<Long> nodeIdsNotInSynth = new ArrayList<Long>();
        ArrayList<Long> ottIdsNotInSynth = new ArrayList<Long>();
        
        // store results in ArrayList<String> instead:
        //ArrayList<String> 
        
        GraphExplorer ge = new GraphExplorer(graphDb);

        // *** provided nodeIds MUST be in the synthesis tree
        if (nodeIds != null && nodeIds.length > 0) {
            for (long nodeId : nodeIds) {
                Node n = null;
                try {
                    n = graphDb.getNodeById(nodeId);
                } catch (NotFoundException e) {}
                if (n != null) {
                    if (ge.nodeIsInSyntheticTree(n)) {
                        tips.add(n);
                    } else {
                        nodeIdsNotInSynth.add(nodeId);
                    }
                } else {
                    invalidNodesIds.add(nodeId);
                }
            }
        }
        
        // if ottIds are not in the synthesis tree, walk to taxonomic descendants, find MRCA of all
        if (ottIds != null && ottIds.length > 0) {
            for (long ottId : ottIds) {
                Node n = null;
                try {
                    n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
                } catch (TaxonNotFoundException e) {}
                if (n != null) {
                    if (ge.nodeIsInSyntheticTree(n)) {
                        tips.add(n);
                    } else { // if not in synth (i.e. not monophyletic), grab descendant tips, which *should* be in synth tree
                        ottIdsNotInSynth.add(ottId);
                        tips.addAll(ge.getTaxonomyDescendantTips(n));
                    }
                } else {
                    invalidOttIds.add(ottId);
                }
            }
        }
        
        
        
        if (tips.size() < 1) {
            throw new IllegalArgumentException("Could not find any graph nodes corresponding to the ids provided.");
        } else {
            HashMap<String, Object> vals = new HashMap<String, Object>();
            //Node mrca = ge.getDraftTreeMRCAForNodes(tips, false);
            Node mrca = ge.getDraftTreeMRCA(tips);
            Node mrta = mrca;

            while (!mrta.hasProperty(NodeProperty.TAX_UID.propertyName)) {
                mrta = mrta.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
            }
            vals.put("mrca_node_id", mrca.getId());

            String name = "";
            String unique = "";
            String rank = "";
            Long ottID = null;

            if (mrca.hasProperty(NodeProperty.TAX_UID.propertyName)) {
                name = (String) mrca.getProperty(NodeProperty.NAME.propertyName);
                unique = (String) mrca.getProperty(NodeProperty.NAME_UNIQUE.propertyName);
                rank = (String) mrca.getProperty(NodeProperty.TAX_RANK.propertyName);
                ottID = Long.valueOf((String)mrca.getProperty(NodeProperty.TAX_UID.propertyName));
            }
            vals.put("mrca_name", name);
            vals.put("mrca_unique_name", unique);
            vals.put("mrca_rank", rank);
            // a hack, since OTRepresentationConverter apparently cannot use null values
            if (ottID != null) {
                vals.put("ott_id", ottID);
            } else {
                vals.put("ott_id", "null");
            }

            vals.put("nearest_taxon_mrca_name", mrta.getProperty(NodeProperty.NAME.propertyName));
            vals.put("nearest_taxon_mrca_unique_name", mrta.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
            vals.put("nearest_taxon_mrca_rank", mrta.getProperty(NodeProperty.TAX_RANK.propertyName));
            vals.put("nearest_taxon_mrca_ott_id", Long.valueOf((String)mrta.getProperty(NodeProperty.TAX_UID.propertyName)));
            vals.put("nearest_taxon_mrca_node_id", mrta.getId());

            // report 'bad' ids
            vals.put("invalid_node_ids", invalidNodesIds);
            vals.put("invalid_ott_ids", invalidOttIds);
            vals.put("node_ids_not_in_tree", nodeIdsNotInSynth);
            vals.put("ott_ids_not_in_tree", ottIdsNotInSynth);

            // report treeID
            Node meta = ge.getMostRecentSynthesisMetaNode();
            vals.put("tree_id", meta.getProperty("name"));

            ge.shutdownDB();
            return OTRepresentationConverter.convert(vals);
        }
    }
    
    
    @Description("Return a tree with tips corresponding to the nodes identified in the input set(s), that is "
    + "consistent with topology of the current draft tree. This tree is equivalent to the minimal subtree "
    + "induced on the draft tree by the set of identified nodes. Any combination of node ids and ott ids may "
    + "be used as input. Nodes or ott ids that do not correspond to any found nodes in the graph, or which "
    + "are in the graph but are absent from the synthetic tree, will be identified in the output (but will "
    + "of course not be present in the resulting induced tree). Branch lengths in the result may be arbitrary, "
    + "and the leaf labels of the tree may either be taxonomic names or (for nodes not corresponding directly "
    + "to named taxa) node ids.\n\n**WARNING: there is currently a known bug if any of the input nodes is the "
    + "parent of another, the returned tree may be incorrect.** Please avoid this input case.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation induced_subtree (@Source GraphDatabaseService graphDb,

        @Description("Node ids indicating nodes to be used as tips in the induced tree")
        @Parameter(name = "node_ids", optional = true)
        long[] nodeIds,

        @Description("OTT ids indicating nodes to be used as tips in the induced tree")
        @Parameter(name = "ott_ids", optional = true)
        long[] ottIds,
        
        @Description("Synthetic tree identifier (defaults to most recent).")
        @Parameter(name = "tree_id", optional = true)
        String treeID,
        
        @Description("A set of open tree node ids")
        @Parameter(name = "ot_node_ids", optional = true)
        String[] otNodeIDs
        
        ) throws IllegalArgumentException {
        
        if ((nodeIds == null || nodeIds.length < 1) && (ottIds == null || ottIds.length < 1)) {
            throw new IllegalArgumentException("You must supply at least two node or ott ids.");
        }
        
        ArrayList<Node> tips = new ArrayList<Node>();
        ArrayList<Long> invalidNodesIds = new ArrayList<Long>();
        ArrayList<Long> invalidOttIds = new ArrayList<Long>();
        ArrayList<Long> nodeIdsNotInSynth = new ArrayList<Long>();
        ArrayList<Long> ottIdsNotInSynth = new ArrayList<Long>();
        
        Integer numQueryNodes = 0;
        HashMap<String, Object> vals = null;
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        if (nodeIds != null && nodeIds.length > 0) {
            numQueryNodes += nodeIds.length;
            for (long nodeId : nodeIds) {
                Node n = null;
                try {
                    n = graphDb.getNodeById(nodeId);
                } catch (NotFoundException e) {
                    
                }
                
                if (n != null) {
                    if (ge.nodeIsInSyntheticTree(n)) {
                        tips.add(n);
                    } else {
                        nodeIdsNotInSynth.add(nodeId);
                    }
                } else {
                    invalidNodesIds.add(nodeId);
                }
            }
        }

        if (ottIds != null && ottIds.length > 0) {
            numQueryNodes += ottIds.length;
            for (long ottId : ottIds) {
                Node n = null;
                try {
                    n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
                } catch (TaxonNotFoundException e) {
                    
                }
                
                if (n != null) {
                    if (ge.nodeIsInSyntheticTree(n)) {
                        tips.add(n);
                    } else {
                        ottIdsNotInSynth.add(ottId);
                    }
                } else {
                    invalidOttIds.add(ottId);
                }
            }
        }
        
        if (numQueryNodes < 2) { // too few nodes given
            throw new IllegalArgumentException("Must supply 2 or more node or ott ids.");
        }
        
        vals = new HashMap<String, Object>();
        // 'bad' nodes
        vals.put("node_ids_not_in_graph", invalidNodesIds);
        vals.put("ott_ids_not_in_graph", invalidOttIds);
        vals.put("node_ids_not_in_tree", nodeIdsNotInSynth);
        vals.put("ott_ids_not_in_tree", ottIdsNotInSynth);
        
        // report treeID
        Node meta = ge.getMostRecentSynthesisMetaNode();
        vals.put("tree_id", meta.getProperty("name"));
        
        if (tips.size() < 2) {
            throw new IllegalArgumentException("Not enough valid node or ott ids provided to construct a subtree (there must be at least two).");
        } else {
            vals.put("newick", ge.extractDraftSubtreeForTipNodes(tips).getNewick(false) + ";");
            return OTRepresentationConverter.convert(vals);
        }
    }
    
    
    @Description("Return a complete subtree of the draft tree descended from some specified node. "
        + "The draft tree version is specified by the `tree_id` arg (defaults to most recent). "
        + "The node to use as the start node must specified using an ot node id. If the specified node "
        + "is not in the synthetic tree (or is entirely absent from the graph), an error will be returned.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation subtree (@Source GraphDatabaseService graphDb,
        
        @Description("Synthetic tree identifier (defaults to most recent).")
        @Parameter(name = "tree_id", optional = true)
        String treeID,
        
        @Description("The ot node id of the node in the tree that should serve as the "
            + "root of the tree returned.")
        @Parameter(name = "ot_node_id", optional = false)
        String otNodeID
        
        ) throws TreeNotFoundException, IllegalArgumentException {
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        HashMap<String, Object> responseMap = new HashMap<>();

        // set default param values
        long startNodeID = -1;
        Integer maxNumTips = 25000; // TODO: is this the best value? Test this. ***
        //String synthTreeID = (String)GeneralConstants.DRAFT_TREE_NAME.value; // don't use this.
        
        String synthTreeID = null;
        String rootNodeID = otNodeID;
        
        // synthetic tree identifier. check against synth meta index, as the hope is to serve multiple trees at once
        if (treeID != null) {
            ArrayList<String> synthTreeIDs = ge.getSynthTreeIDs();
            if (synthTreeIDs.contains(treeID)) {
                synthTreeID = treeID;
            } else {
                ge.shutdownDB();
                String ret = "Unrecognized \"tree_id\" argument. Leave blank to default "
                    + "to the current synthetic tree.";
                throw new IllegalArgumentException(ret);
            }
        } else {
            // default to most recent
            Node meta = ge.getMostRecentSynthesisMetaNode();
            synthTreeID = (String) meta.getProperty("tree_id");
        }
        
        try {
            startNodeID = ge.findGraphNodeByOTTNodeID(rootNodeID).getId();
        } catch (MultipleHitsException e) {
        } catch (TaxonNotFoundException e) {
        }
        
        if (startNodeID == -1) {
            ge.shutdownDB();
            String ret = "Could not find any graph nodes corresponding to the arg `ot_node_id` provided.";
            throw new IllegalArgumentException(ret);
        }
        
        // check that startNode is indeed in the synthetic tree
        Node n = graphDb.getNodeById(startNodeID);
        if (!ge.nodeIsInSyntheticTree(n, synthTreeID)) {
            ge.shutdownDB();
            String ret = "Queried `ot_node_id`: " + rootNodeID + " is in the graph, but "
                + "not in the draft tree: " + synthTreeID;
            throw new IllegalArgumentException(ret);
        }
        
        // check that the returned tree is not too large
        Integer numMRCA = ge.getNumTipDescendants(n, synthTreeID);
        
        if (numMRCA > maxNumTips) {
            ge.shutdownDB();
            String ret = "Requested tree is larger than currently allowed by this service "
                + "(" + maxNumTips + " tips). For larger trees, please download the full "
                + "tree directly from: http://files.opentreeoflife.org/trees/";
            throw new IllegalArgumentException(ret);
        }
        
        // get the subtree for export
        JadeTree tree = null;
        try {
            tree = ge.extractDraftTreeByName(n, synthTreeID);
        } finally {
            ge.shutdownDB();
        }
        
        responseMap.put("newick", tree.getRoot().getNewick(false) + ";");
        responseMap.put("tree_id", synthTreeID);
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
        @Parameter(name = "synth_tree_id", optional = true)
        String synthTreeID,

        @Description("The name of the return format. The only currently supported format is newick.")
        @Parameter(name = "format", optional = true)
        String format

        ) throws IllegalArgumentException {

        HashMap<String, Object> responseMap = new HashMap<>();
        String source = studyID + "_" + treeID;
        String synTreeID = null;
        Node meta = null;
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        if (synthTreeID != null) {
            synTreeID = synthTreeID;
            // check
            meta = ge.getSynthesisMetaNodeByName(synthTreeID);
            // invalid treeid
            if (meta == null) {
                ge.shutdownDB();
                String ret = "Could not find a synthetic tree corresponding to the 'synth_tree_id' arg: '"
                        + synTreeID + "'.";
                throw new IllegalArgumentException(ret);
            }
        } else {
            // get most recent tree
            synTreeID = ge.getMostRecentSynthTreeID();
        }
        ge.shutdownDB();
        
        String tree = getSourceTree(source, synTreeID);

        if (tree == null) {
            throw new IllegalArgumentException("Invalid source id '" + source + "' provided.");
        } else {
            responseMap.put("newick", tree);
            responseMap.put("synth_tree_id", synTreeID);
        }
        
        return OTRepresentationConverter.convert(responseMap);
    }
    
    
    // fetch the processed input source tree newick from files.opentree.org
    // source has format: studyID + "_" + treeID
    public String getSourceTree(String source, String synTreeID) {
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
