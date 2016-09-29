/** This is an adapter that emulates the v2 API methods using v3 API calls */

package opentree.plugins;

/// this is way more imports than we actually need
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

public class graph extends ServerPlugin {
    
    tree_of_life_v3 v3 = new tree_of_life_v3();

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
        Long nodeID,
        
        @Description("The `ott_id` of the node of interest. This argument may not be "
            + "combined with `node_id`.")
        @Parameter(name = "ott_id", optional = true)
        Long ottID,
        
        @Description("Include the ancestral lineage of the node in the draft tree. If "
            + "this argument is `true`, then a list of all the ancestors of this node "
            + "in the draft tree, down to the root of the tree itself, will be included "
            + "in the results. Higher list indices correspond to more incluive (i.e. "
            + "deeper) ancestors, with the immediate parent of the specified node "
            + "occupying position 0 in the list.")
        @Parameter(name = "include_lineage", optional = true)
        Boolean includeLineage
        
        ) throws IllegalArgumentException, TaxonNotFoundException {
        
        /*
          /v2/graph/node_info 
            in: 
              node_id : nodeid-integer   e.g. 656910
              ott_id : ottid-integer   e.g. 810751
              include_lineage : boolean 
            out: 
              node_id : nodeid-integer 
              num_tips : integer   e.g. 388
              num_synth_tips : integer   e.g. 388
              in_synth_tree : boolean 
              synth_sources : list-of source-tree-blob = 
                    git_sha : sha-string 
                    tree_id : treeid-string 
                    study_id : studyid-string 
              tree_sources : list-of source-tree-blob 
              tree_id : synthid-string 
              draft_tree_lineage : list-of v2-lineage-taxon-blob 
              ott_id : ottid-integer   -- the string "null" if not a taxon node 
              name : taxon-name-string 
              rank : rank-string 
              tax_source : string   e.g. "ncbi:9242,gbif:5289,irmng:104628"

          /v3/tree_of_life/node_info
            in: 
              node_id : nodeid-string   e.g. "mrcaott3504ott396446"
              ott_id : ottid-integer   e.g. 810751  -- mutually exclusive with node_id 
              include_lineage : boolean   -- default false 
            out: 
              node_id : nodeid-string   -- canonical id, not nec same as argument 
              num_tips : integer   e.g. 388  -- v2: num_synth_tips 
              ... support/conflict fields ...
              taxon : taxon-blob
              lineage : list-of blob   -- not draft_tree_lineage 
              source_id_map : dict   -- gives definitions of tree ids 
        */

        String stringNodeID = null;
        if (nodeID != null)
            stringNodeID = tree_of_life.longIdToStringId(nodeID);
        Map<String, Object> result = v3.doNodeInfo(graphDb, stringNodeID, ottID, includeLineage);
        Map<String, Object> taxon = (Map<String, Object>)result.get("taxon");

        Map<String, Object> res = new HashMap<>();
        String canonicalId = (String)result.get("node_id");
        res.put("node_id", tree_of_life.stringIdToLongId(canonicalId));
        res.put("num_tips", result.get("num_tips")); // ???
        res.put("in_synth_tree", Boolean.TRUE);
        res.put("num_synth_tips", result.get("num_tips"));

        if (taxon != null) {
            res.put("ott_id", taxon.get("ott_id"));
            res.put("name", taxon.get("name"));
            res.put("rank", taxon.get("rank"));
            res.put("tax_source", String.join(",", (List<String>)taxon.get("tax_sources")));
        }

        res.put("tree_id", tree_of_life.treeId);

        // res.put("synth_sources", ...);  list of {git_sha, tree_id, study_id} - from supported_by ?
        // res.put("tree_sources", ...);   same as synth_sources ...

        // Iterate over trees in the v3 support/conflict maps.
        // Look each one up in the v3 source_id_map.
        // Accumulate values from source_id_map.
        Map<String, Object> sourceIdMap = (Map<String, Object>)result.get("source_id_map");
        List<Object> v2SourceList = new ArrayList<>();
        Object supportedBy = result.get("supported_by");
        if (supportedBy != null)
            for (String sourceIdAsObj : ((Map<String, Object>)supportedBy).keySet()) {
                String sourceId = (String)sourceIdAsObj;
                // Transfer a sourceblob from the v3 map to the v2 list
                v2SourceList.add(sourceIdMap.get(sourceId));
            }
        Object partialPathOf = result.get("partial_path_of");
        if (partialPathOf != null)
            for (String sourceIdAsObj : ((Map<String, Object>)partialPathOf).keySet()) {
                String sourceId = (String)sourceIdAsObj;
                // Transfer a sourceblob from the v3 map to the v2 list
                v2SourceList.add(sourceIdMap.get(sourceId));
            }
        res.put("synth_sources", v2SourceList);
        res.put("tree_sources", v2SourceList);

        /*
          "draft_tree_lineage" : [ {
            "unique_name" : "Mutisieae",
            "name" : "Mutisieae",
            "rank" : "tribe",
            "ott_id" : 557775,
            "node_id" : 656644
          }, {
            "unique_name" : "",
            "name" : "",
            "rank" : "",
            "ott_id" : "null",
            "node_id" : 3446538
          }, ...
        */

        if (includeLineage != null && includeLineage.booleanValue()) {
            // We have a list of v3 node-blobs
            List<Object> lineage = (List<Object>)result.get("lineage");
            // We want a list of v2 taxonlike-blobs
            List<Object> v2lineage = new ArrayList<>();
            for (Object nodeBlobAsObj : lineage) {
                Map<String, Object> nodeBlob = (Map<String, Object>)nodeBlobAsObj;
                Map<String, Object> taxonlike = new HashMap<>();
                taxonlike.put("node_id", tree_of_life.stringIdToLongId((String)(nodeBlob.get("node_id"))));
                Map<String, Object> ltaxon = (Map<String, Object>)(nodeBlob.get("taxon"));
                if (ltaxon != null) {
                    taxonlike.put("ott_id", ltaxon.get("ott_id"));
                    taxonlike.put("name", ltaxon.get("name"));
                    taxonlike.put("rank", ltaxon.get("rank"));
                    taxonlike.put("unique_name", ltaxon.get("unique_name"));
                } else {
                    taxonlike.put("ott_id", "null");
                    taxonlike.put("name", "");
                    taxonlike.put("rank", "");
                    taxonlike.put("unique_name", "");
                }
                v2lineage.add(taxonlike);
            }
            res.put("draft_tree_lineage", v2lineage);
        }
        return OTRepresentationConverter.convert(res);
    }

    
}
