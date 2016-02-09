package opentree.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import opentree.GraphExplorer;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.constants.GeneralConstants;

import java.net.URL;
import java.net.URLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

import org.opentree.exceptions.TaxonNotFoundException;
import org.opentree.utils.GeneralUtils;
import org.opentree.graphdb.GraphDatabaseAgent;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OTRepresentationConverter;

// Graph of Life Services 
public class graph extends ServerPlugin {
    
    // Don't deprecate: can use to list synthetic trees available
    @Description("Returns summary information about the draft synthetic tree(s) "
        + "currently contained within the graph database.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation about(@Source GraphDatabaseService graphDb) throws IllegalArgumentException {

        HashMap<String, Object> graphInfo = new HashMap<>();
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        ArrayList<String> synthTreeIDs = ge.getSynthTreeIDs();
        
        if (synthTreeIDs.size() > 0) {
            graphInfo.put("num_synth_trees", synthTreeIDs.size());
            LinkedList<HashMap<String, Object>> trees = new LinkedList<>();
            
            for (String treeID : synthTreeIDs) {
                HashMap<String, Object> draftTreeInfo = new HashMap<>();
                Node meta = ge.getSynthesisMetaNodeByName(treeID);
                for (String key : meta.getPropertyKeys()) {
                    draftTreeInfo.put(key, meta.getProperty(key));
                }
                trees.add(draftTreeInfo);
            }
            graphInfo.put("synth_trees", trees);
        } else {
            throw new IllegalArgumentException("Could not find any draft synthetic trees in the graph.");
        }
        ge.shutdownDB();

        return OTRepresentationConverter.convert(graphInfo);
    }
    
    
    // TODO: Possibility of replacing tip label ottids with names?!?
    @Description("Returns a processed source tree (corresponding to a tree in some [study](#studies)) used "
        + "as input for the synthetic tree. Although the result of this service is a tree corresponding directly to a "
        + "tree in a study, the representation of the tree in the graph may differ slightly from its "
        + "canonical representation in the study, due to changes made during tree import: 1) includes "
        + "only the curator-designated ingroup clade, and 2) both unmapped and duplicate tips are pruned "
        + "from the tree. The tree is returned in newick format, with terminal nodes labelled with ott ids.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation source_tree(
        @Source GraphDatabaseService graphDb,

        @Description("The study identifier. Will typically include a prefix (\"pg_\" or \"ot_\")")
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
    
    
    // TODO: only take in nodeid string
    // deprecating the include_lineage option
    @Description("Returns summary information about a node in the graph. The node "
        + "of interest may be specified using its `ott_node_id`. If the specified "
        + "node is not in the graph, an error will be returned.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation node_info(
        @Source GraphDatabaseService graphDb,
        
        @Description("The `ott_node_id` of the node of interest.")
        @Parameter(name = "node_id", optional = false)
        String otNodeID
        
        ) throws IllegalArgumentException, TaxonNotFoundException {
        
        HashMap<String, Object> nodeIfo = new HashMap<>();
        
        String nodeId = otNodeID;
        Node qNode = null;
        
        LinkedList<HashMap<String, Object>> synthSources = new LinkedList<>();
        LinkedList<HashMap<String, Object>> treeSources = new LinkedList<>();
        
        GraphExplorer ge = new GraphExplorer(graphDb);
        
        try {
            qNode = ge.findGraphNodeByOTTID(nodeId);
        } catch (TaxonNotFoundException e) {
        }
        
        if (qNode == null) {
            ge.shutdownDB();
            String ret = "Could not find a graph node corresponding to the 'node_id' arg: '"
                + otNodeID + "'.";
            throw new IllegalArgumentException(ret);
        }
        
        nodeIfo.putAll(ge.getNodeTaxInfo(qNode));
        
        // TODO: add snth-tree-specific metadata (stored in outgoing rels)
        
        // = ge.getNodeTaxInfo(qNode);
        
        // if we want this, have to calculate by traversal of individual tree
        //numMRCA = ((long[]) n.getProperty(NodeProperty.MRCA.propertyName)).length;
        /*
        if (ge.nodeIsInSyntheticTree(n)) {
            numSynthTips = ge.getSynthesisDescendantTips(n).size(); // may be faster to just use stored MRCA
            // get all the unique sources supporting this node
            ArrayList<String> sSources = ge.getSynthesisSupportingSources(n);
            ArrayList<String> tSources = ge.getSupportingTreeSources(n);

            for (String sStudy : sSources) {
                HashMap<String, Object> indStudy = GeneralUtils.reformatSourceID(sStudy);
                synthSources.add(indStudy);
            }

            for (String tStudy : tSources) {
                HashMap<String, Object> indStudy = GeneralUtils.reformatSourceID(tStudy);
                treeSources.add(indStudy);
            }
        }
        
        nodeIfo.put("synth_sources", synthSources);
        nodeIfo.put("tree_sources", treeSources);
        */
        
        ge.shutdownDB();
        
        return OTRepresentationConverter.convert(nodeIfo);
    }


    // fetch the processed input source tree newick from files.opentree.org
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
