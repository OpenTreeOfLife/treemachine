package opentree.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import jade.tree.deprecated.JadeTree;

import opentree.GraphExplorer;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.constants.GeneralConstants;

import org.opentree.utils.GeneralUtils;
import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.TaxonNotFoundException;
import org.opentree.exceptions.TreeNotFoundException;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OTRepresentationConverter;
import org.opentree.graphdb.GraphDatabaseAgent;

// Graph of Life Services 
public class tree_of_life extends ServerPlugin {
	
    @Description("Returns summary information about the current draft tree of life, including information "
        + "about the list of source trees and the taxonomy used to build it.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation about (

        @Source GraphDatabaseService graphDb,
        @Description("Return a list of source studies")
        @Parameter(name = "study_list", optional = true)
        Boolean study_list) throws TaxonNotFoundException, MultipleHitsException {

        GraphDatabaseAgent gdb = new GraphDatabaseAgent(graphDb);
        GraphExplorer ge = new GraphExplorer(gdb);
        HashMap<String, Object> draftTreeInfo = null;
        Boolean returnStudyList = true; // default to true for now
        
        if (study_list != null && study_list == false) {
            returnStudyList = false;
        }
        
        // Most information will come from the synthesis metadata node
        try {
            Node meta = ge.getMostRecentSynthesisMetaNode();
            if (meta != null) {
    //		String [] sourceList = (String []) meta.getProperty("sourcenames");
                ArrayList<String> sourceList = ge.getSynthesisSourceList();
                Node startNode = gdb.getNodeById((Long) gdb.getGraphProperty("draftTreeRootNodeId"));
                Integer numMRCA = ((long[]) startNode.getProperty(NodeProperty.MRCA.propertyName)).length;
                Integer numStudies = sourceList.size();
                draftTreeInfo = new HashMap<String, Object>();
                
                // general info
                draftTreeInfo.put("tree_id", meta.getProperty("name"));
                draftTreeInfo.put("date", meta.getProperty("date"));
                
                // currently comes from root node of graph. should bake into metadatanode of synth tree 
                draftTreeInfo.put("taxonomy_version", ge.getTaxonomyVersion());
                
                // root node info
                draftTreeInfo.put("root_node_id", startNode.getId());
                draftTreeInfo.put("root_taxon_name", String.valueOf(startNode.getProperty(NodeProperty.NAME.propertyName)));
                draftTreeInfo.put("root_ott_id", Long.valueOf((String) startNode.getProperty(NodeProperty.TAX_UID.propertyName)));
                
                // tree constituents
                draftTreeInfo.put("num_tips", numMRCA);
                draftTreeInfo.put("num_source_studies", numStudies);
                if (returnStudyList) {
                    LinkedList<HashMap<String, Object>> sources = new LinkedList<HashMap<String, Object>>();
                    for (String study : sourceList) {
                        HashMap<String, Object> indStudy = GeneralUtils.reformatSourceID(study);
                        sources.add(indStudy);
                    }
                    draftTreeInfo.put("study_list", sources);
                }
            } else {
                draftTreeInfo = new HashMap<String, Object>();
                draftTreeInfo.put("error", "No synthetic tree found in the graph.");
                return OTRepresentationConverter.convert(draftTreeInfo);
            }	
        } finally {
                ge.shutdownDB();
        }
        return OTRepresentationConverter.convert(draftTreeInfo);
    }


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
        long[] ottIds) throws IllegalArgumentException {

        if ((nodeIds == null || nodeIds.length < 1) && (ottIds == null || ottIds.length < 1)) {
            throw new IllegalArgumentException("You must supply at least one node_id or ott_id.");
        }

        ArrayList<Node> tips = new ArrayList<Node>();

        ArrayList<Long> invalidNodesIds = new ArrayList<Long>();
        ArrayList<Long> invalidOttIds = new ArrayList<Long>();
        ArrayList<Long> nodeIdsNotInSynth = new ArrayList<Long>();
        ArrayList<Long> ottIdsNotInSynth = new ArrayList<Long>();

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
        long[] ottIds) throws IllegalArgumentException {
        
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
    
    
    @Description("Return a complete subtree of the draft tree descended from some specified node. The node to use as the "
        + "start node may be specified using *either* a node id or an ott id, **but not both**. If the specified node "
        + "is not in the synthetic tree (or is entirely absent from the graph), an error will be returned.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation subtree (
        
        @Source GraphDatabaseService graphDb,
        @Description("The identifier for the synthesis tree. We currently only support a single draft tree "
            + "in the db at a time, so this argument is superfluous and may be safely ignored.")
        @Parameter(name = "tree_id", optional = true)
        String treeID,
        
        @Description("The node id of the node in the tree that should serve as the root of the tree returned. This "
            + "argument may not be used in combination with `ott_id`.")
        @Parameter(name = "node_id", optional = true)
        Long subtreeNodeId,
        
        @Description("The ott id of the node in the tree that should serve as the root of the tree returned. This "
            + "argument may not be used in combination with `node_id`.")
        @Parameter(name = "ott_id", optional = true)
        Long subtreeOttId) throws TreeNotFoundException, IllegalArgumentException {
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        HashMap<String, Object> responseMap = new HashMap<String, Object>();

        // set default param values
        long startNodeID = -1;
        Integer maxNumTips = 25000; // TODO: is this the best value? Test this. ***
        String synthTreeID = (String)GeneralConstants.DRAFT_TREE_NAME.value; // don't use this.

        // get start node
        if (subtreeNodeId != null && subtreeOttId != null) {
            throw new IllegalArgumentException("Provide only one \"node_id\" or \"ott_id\" argument.");
        }
        if (subtreeNodeId != null) {
            startNodeID = subtreeNodeId;
        } else if (subtreeOttId != null) {
            try {
                startNodeID = ge.findGraphTaxNodeByUID(String.valueOf(subtreeOttId)).getId();
            } catch (MultipleHitsException e) {
                
            } catch (TaxonNotFoundException e) {
                
            }
            if (startNodeID == -1) {
                throw new IllegalArgumentException("Invalid \"ott_id\" argument.");
            }
        } else {
            throw new IllegalArgumentException("Must provide a \"node_id\" or \"ott_id\" argument to indicate the location of the root "
                + "node of the subtree.");
        }
        
        // check that startNode is indeed in the synthetic tree
        Node n = graphDb.getNodeById(startNodeID);
        if (n == null) {
            if (subtreeNodeId != null) {
                throw new IllegalArgumentException("Invalid \"node_id\" argument.");
            } else {
                throw new IllegalArgumentException("Invalid \"ott_id\" argument.");
            }
        } else {
            if (!ge.nodeIsInSyntheticTree(n)) {
                if (subtreeNodeId != null) {
                    throw new IllegalArgumentException("Provided \"node_id\" is in the graph, but not part of the current synthetic tree.");
                } else {
                    throw new IllegalArgumentException("Provided \"ott_id\" is in the graph, but not part of the current synthetic tree.");
                }
            }
        }
        
        // check that the returned tree is not too large
        Integer numMRCA = ((long[]) n.getProperty(NodeProperty.MRCA.propertyName)).length;
        if (numMRCA > maxNumTips) {
            throw new IllegalArgumentException("Requested tree is larger than currently allowed by this service (" + maxNumTips 
                + " tips). For larger trees, please download the full tree directly from: http://files.opentreeoflife.org/trees/");
        }
        
        // synthetic tree identifier. check against synth meta index, as the hope is to serve multiple trees at once
        if (treeID != null) {
            ArrayList<String> synthTreeIDs = ge.getSynthTreeIDs();
            if (synthTreeIDs.contains(treeID)) {
                synthTreeID = treeID;
            } else {
                throw new IllegalArgumentException("Unrecognized \"tree_id\" argument. Leave blank to default to the current synthetic tree.");
            }
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
}
