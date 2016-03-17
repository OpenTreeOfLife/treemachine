/** This is an adapter that emulates the v2 API methods using v3 API calls */

package opentree.plugins;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
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

import opentree.plugins.tree_of_life_v3;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Tree of Life Services 
public class tree_of_life extends ServerPlugin {
    
    tree_of_life_v3 v3 = new tree_of_life_v3();

    @Description("Returns summary information about the tree of life, "
        + "including information about the list of source "
        + "trees and the taxonomy used to build it.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation about (@Source GraphDatabaseService graphDb,
        
//        @Description("Synthetic tree identifier (defaults to most recent).")
//        @Parameter(name = "synth_id", optional = true)
//        String synthID,
        
        @Description("Return a list of source studies.")
        @Parameter(name = "include_source_list", optional = true)
        Boolean source_list
        
        ) throws TaxonNotFoundException, MultipleHitsException {

        /*
          /v2/tree_of_life/about 
            in: 
              study_list : boolean   e.g. true
            out: 
              date : string 
              num_tips : integer   e.g. 2424255
              num_source_studies : integer 
              taxonomy_version : string 
              root_node_id : nodeid-integer   e.g. 1
              root_ott_id : ottid-integer 
              root_taxon_name : string   e.g. "cellular organisms"
              study_list : list-of 
                  git_sha : sha-string 
                  tree_id : treeid-string 
                  study_id : studyid-string 
              tree_id : synthid-string   e.g. "opentree4.0"

          /v3/tree_of_life/about 
            in: 
              study_list : boolean   e.g. true
            out: 
              date_created : string 
              num_source_studies : integer 
              num_trees : integer 
              taxonomy_version : string
              filtered_flags : ...
              root : node-blob
              study_list : list-of 
                  git_sha : sha-string 
                  tree_id : treeid-string 
                  study_id : studyid-string 
              source_id_map : ...
              synth_id : synthid-string   e.g. "opentree4.0"
        */

        // Does default value of source_list flip from v2 to v3?

        Map<String, Object> result = v3.doAbout(graphDb, source_list);
        Map<String, Object> root = (Map<String, Object>)result.get("root");
        Map<String, Object> res = new HashMap<>();
        res.put("date", result.get("date_created"));
        res.put("num_tips", root.get("num_tips"));
        res.put("num_source_studies", result.get("num_source_studies"));
        res.put("taxonomy_version", result.get("taxonomy_version"));
        res.put("root_node_id", stringIdToLongId((String)(root.get("node_id"))));
        res.put("root_taxon_name", root.get("name"));
        res.put("root_ott_id", root.get("ott_id"));

        // Map over result study_list (strings), getting blobs from
        // the source_id_map.
        // res.put("study_list", map ... root.get("study_list"));    // worry about @ vs. _ ?

        Map<String, Object> sourceIdMap = (Map<String, Object>)(result.get("source_id_map"));
        List<Object> trees = new ArrayList<>();
        for (String sourceid : (List<String>)(root.get("study_list")))
            trees.add(sourceIdMap.get(sourceid));

        res.put("tree_id", result.get("synth_id"));

        return OTRepresentationConverter.convert(res);
    }

    @Description("Get the MRCA of a set of nodes on a the most current draft tree. Accepts "
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
        long[] nodeIDs,
        
        @Description("A set of ott ids")
        @Parameter(name = "ott_ids", optional = true)
        long[] ottIDs
        
        ) throws IllegalArgumentException
    {
        // If there are any long node ids, error
        // or convert them to string node ids

        /**
           /v2/tree_of_life/mrca 
             in: 
               node_ids : list-of nodeid-integer   e.g. [1,2,3]
               ott_ids : list-of ottid-integer   e.g. [4,5,6]
             out: 
               mrca_node_id : nodeid-integer 
               invalid_node_ids : list-of nodeid-integer 
               invalid_ott_ids : list-of ottid-integer 
               node_ids_not_in_tree : list-of nodeid-integer 
               ott_ids_not_in_tree : list-of ottid-integer 
               tree_id : synthid-string 
               ott_id : ottid-integer 
               mrca_name : taxon-name-string 
               mrca_rank : rank-string 
               mrca_unique_name : uniqname-string 
               nearest_taxon_mrca_ott_id : ottid-integer 
               nearest_taxon_mrca_name : taxon-name-string 
               nearest_taxon_mrca_rank : rank-string 
               nearest_taxon_mrca_unique_name : uniqname-string 
               nearest_taxon_mrca_node_id : nodeid-integer 

           /v3/tree_of_life/mrca 
             in:
               node_ids : list-of nodeid-string 
               ott_ids : list-of ottid-integer 
             out:
               mrca
                 node_id
                 taxon
                 num_tips
                 (conflict info)
               nearest_taxon
                 ott_id
                 name, rank, tax_sources, unique_name
               ott_ids_not_in_tree

            */

        Map<String, Object> result = v3.doMrca(graphDb, longIdsToStringIds(nodeIDs), ottIDs);
        Map<String, Object> mrca = (Map<String, Object>)result.get("mrca");
        Map<String, Object> nearest = (Map<String, Object>)result.get("nearest_taxon");

        Map<String, Object> res = new HashMap<>();
        String nodeId = (String)(mrca.get("node_id"));
        res.put("mrca_node_id", stringIdToLongId(nodeId));
        res.put("invalid_node_ids", new long[0]);
        res.put("invalid_ott_ids", new long[0]);
        res.put("node_ids_not_in_tree", new long[0]);
        res.put("ott_ids_not_in_tree", new long[0]);
        res.put("tree_id", "unclear");

        if (nodeId.startsWith("ott")) {
            res.put("ott_id", Long.parseLong(nodeId.substring(3)));
            Map<String, Object> taxon = (Map<String, Object>)mrca.get("taxon");
            res.put("mrca_name", taxon.get("name"));
            res.put("mrca_rank", taxon.get("rank"));
            // Maybe change to "" when equal
            res.put("mrca_unique_name", taxon.get("unique_name"));
        }

        if (nearest != null) {
            res.put("nearest_taxon_mrca_ott_id", nearest.get("ott_id"));
            res.put("nearest_taxon_mrca_name", nearest.get("name"));
            res.put("nearest_taxon_mrca_rank", nearest.get("rank"));
            res.put("nearest_taxon_mrca_unique_name", nearest.get("unique_name"));
            // Omit nearest_taxon_mrca_node_id and hope no one notices!
        }

        return OTRepresentationConverter.convert(res);
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
        long[] nodeIDs,
        
        @Description("A set of ott ids")
        @Parameter(name = "ott_ids", optional = true)
        long[] ottIDs
        
        ) throws IllegalArgumentException
    {

        /*
            /v2/tree_of_life/induced_subtree 
              in: 
                node_ids : list-of nodeid-integer 
                ott_ids : list-of ottid-integer 
              out: 
                newick : newick-string 
                node_ids_not_in_tree : list-of nodeid-integer 
                node_ids_not_in_graph : list-of nodeid-integer 
                ott_ids_not_in_tree : list-of ottid-integer 
                ott_ids_not_in_graph : list-of ottid-integer 
                tree_id : synthid-string 

            /v3/tree_of_life/induced_subtree 
              in: 
                node_ids : list-of nodeid-string   e.g. ["mrcaott3504ott396446","mrcaott320ott55033"]
                ott_ids : list-of ottid-integer 
                label_format : string   e.g. "name"  -- name, id, name_and_id, original_name 
              out: 
                newick : newick-string 

        */

        Map<String, Object> result = v3.doInducedSubtree(graphDb, longIdsToStringIds(nodeIDs), ottIDs, "name_and_id");
        result.put("newick", trimNewick((String)result.get("newick")));
        result.put("node_ids_not_in_tree", new long[0]);
        result.put("node_ids_not_in_graph", new long[0]);
        result.put("ott_ids_not_in_tree", new long[0]);
        result.put("ott_ids_not_in_graph", new long[0]);
        // Put nothing for tree_id and hope no one notices?

        return OTRepresentationConverter.convert(result);
    }

    
    @Description("Return a complete subtree of the draft tree descended from some specified node. "
        + "The draft tree version is specified by the `synth_id` arg (defaults to most recent). "
        + "The node to use as the start node must specified using *either* a node id or an ott id, "
        + "**but not both**. If the specified node is not found an error will be returned.")
    @PluginTarget(GraphDatabaseService.class)
    public Representation subtree (@Source GraphDatabaseService graphDb,
        @Description("The `node_id` of the node of interest. This argument may not be "
            + "combined with `ott_id`.")
        @Parameter(name = "node_id", optional = true)
        Long nodeID,
        
        @Description("The `ott_id` of the node of interest. This argument may not be "
            + "combined with `node_id`.")
        @Parameter(name = "ott_id", optional = true)
        Long ottID
        
        ) throws TreeNotFoundException, IllegalArgumentException, TaxonNotFoundException
    {
        /*
            /v2/tree_of_life/subtree 
              in: 
                node_id : nodeid-integer   e.g. 72276
                ott_id : ottid-integer 
                tree_id : synthid-string   e.g. "opentree4.0"
              out: 
                newick : newick-string 
                tree_id : synthid-string 

            /v3/tree_of_life/subtree 
              in: 
                node_id : nodeid-string   e.g. "mrcaott320ott55033"
                ott_id : ottid-integer 
                format : string   e.g. "newick"  -- for arguson. review 
                label_format : ...
              out: 
                newick : string
         */

        String stringNodeId = null;
        if (nodeID != null)
            stringNodeId = longIdToStringId((long)nodeID);

        Map<String, Object> result = v3.doSubtree(graphDb, stringNodeId, ottID, "name_and_id");
        result.put("newick", trimNewick((String)result.get("newick")));
        result.put("tree_id", "unclear");

        return OTRepresentationConverter.convert(result);
    }

    static Pattern v3nodeid = Pattern.compile(")mrcaott[0-9]+ott[0-9]+");

    String trimNewick(String newick) {
        return v3nodeid.matcher(newick).replaceAll(")");
    }

    // Mapping between v2-style node ids (longs) and v3-style node ids (strings)

    static final long idLimit = 10000000L;

    static String longIdToStringId(long id) {
        if (id < idLimit)
            return String.format("ott%s", id);
        else
            return String.format("mrcaott%sott%s",
                                 id % idLimit,
                                 id / idLimit);
    }

    static String[] longIdsToStringIds(long[] nodeIDs) {
        String[] newNodeIDs = new String[nodeIDs.length];
        for (int i = 0; i < nodeIDs.length; ++i)
            newNodeIDs[i] = longIdToStringId(nodeIDs[i]);
        return newNodeIDs;
    }


    static long stringIdToLongId(String id) {
        if (id.startsWith("ott"))
            return Long.parseLong(id.substring(3));
        else if (id.startsWith("mrcaott")) {
            int i = id.indexOf("ott", 7);
            if (i <= 0) return -1;
            return Long.parseLong(id.substring(7,i))
                + (idLimit * Long.parseLong(id.substring(i+3)));
        } else
            return -1;
    }

}
