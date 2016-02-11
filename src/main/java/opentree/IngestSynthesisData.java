package opentree;

import jade.tree.deprecated.JadeNode;
import jade.tree.deprecated.JadeTree;
import jade.tree.deprecated.TreeReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import static opentree.GraphBase.sourceRelIndex;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.exceptions.TreeIngestException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.opentree.graphdb.GraphDatabaseAgent;
import scala.actors.threadpool.Arrays;

public class IngestSynthesisData extends GraphBase {
    
    private int transactionFrequency = 100000;
    private final int commitFrequency = 50000; // can play with this to make faster/efficient
    private int nNodesToCommit;
    private int cur_tran_iter = 0;
    private Transaction tx;
    private Node synthRootNode;
    private Node metadatanode;
    private final boolean verbose = false;
    private boolean newGraph; // simplifies things if new (i.e. no checking of existing nodes)
    
    // containers used during import
    private HashSet<String> ottIDs;
    private String inputNewick;
    private String synthTreeName; // get this from the json: "tree_id": "a synthesis version id"
    private String rootTaxonID;
    private String taxonomyVersion;
    JSONObject jsonObject; // for annotations
    
    JSONObject nodeMetaData;// nodeMetaData = (JSONObject) jsonObject.get("nodes");
    
    
    
    private JadeTree inputJadeTree;
    //private HashMap<String, String> sourceMap; // probably do not need this
    private HashMap<String, HashMap<String, String> > taxNodeInfo;
    
    //private HashMap<String, Node> taxUIDToNodeMap;
    //private HashMap<String, String> childNodeIDToParentNodeIDMap;
    
    public IngestSynthesisData(String graphname) {
        super(graphname);
    }
    
    // these probably are not needed until > 1 tree will be served
    public IngestSynthesisData(EmbeddedGraphDatabase eg) {
        super(eg);
    }
    
    public IngestSynthesisData(GraphDatabaseAgent gdb) {
        super(gdb);
    }
    
    public IngestSynthesisData(GraphDatabaseService gs) {
        super(gs);
    }
    
    private void initilaize() {
        inputJadeTree = new JadeTree();
        ottIDs = new HashSet<>();
        nNodesToCommit = 0;
        synthTreeName = "";
        rootTaxonID = "";
        taxNodeInfo = new HashMap<>();
        
        //taxUIDToNodeMap = new HashMap<String, Node>();
        //childNodeIDToParentNodeIDMap = new HashMap<String, String>();
    }
    
    public void buildDB (String newickFile, String jsonFile, String taxFile, boolean isNewGraph) throws TreeIngestException, IOException {
        /*
        Need to:
        0) Check if:
            a) DB is empty
            b) if not, if current tree is already in DB
        1) read in newick, collecting ott ids along the way
        2) read in json info
        3) read in taxonomy tsv, retaining only ott ids from above
        4) make db nodes/edges
        
        Will need:
        1) source index - separate for each tree
        2) metadata node for root
        3) synth edge index - separate for each tree
        
        */
        initilaize();
        
        newGraph = isNewGraph;
        if (newGraph) {
            System.out.println("Constructing new graph db");
        } else {
            System.out.println("Adding to existing graph db");
        }
        
        readAnnotations(jsonFile);
        
        synthTreeName = (String) jsonObject.get("tree_id");
        System.out.println("synthTreeName = " + synthTreeName);
        
        // annotations currently does not prepend 'ott' to "root_ott_id"
        rootTaxonID = "ott" + String.valueOf(jsonObject.get("root_ott_id"));
        System.out.println("rootTaxonID = " + rootTaxonID);
        taxonomyVersion = String.valueOf(jsonObject.get("taxonomy_version"));
        
        readNewick(newickFile);
        collectOTTIDs(); // don't think i need this anymore...
        readTaxonomyTSV(taxFile);
        
        tx = graphDb.beginTx();
        postOrderAddTreeToGraph(inputJadeTree.getRoot());
        tx.success();
        
        System.out.println("Committing nodes: " + nNodesToCommit);
        tx.finish();
        
        setRootMetadata();
    }
    
    private void readNewick (String newickFile) {
        System.out.println("Reading tree from file: " + newickFile);
        try {
            BufferedReader br = new BufferedReader(new FileReader(newickFile));
            inputNewick =  br.readLine();
            br.close();
        } catch (IOException ioe) {}
        
        TreeReader tr = new TreeReader();
        inputJadeTree = tr.readTree(inputNewick);
    }
    
    
    // collect all ott ids from the ingested jade tree
    // used when processing taxonomy tsv file
    private void collectOTTIDs () {
        // collect all ottids
        Iterable<JadeNode> terp = inputJadeTree.iterateExternalNodes();
        for (JadeNode tt : terp) {
            String str = tt.getName();
            if (str.startsWith("ott")) {
                ottIDs.add(str.replace("ott", ""));
            }
        }
        System.out.println("Collected " + ottIDs.size() + " terminal ottIDs.");
        
        terp = inputJadeTree.iterateInternalNodes();
        for (JadeNode tt : terp) {
            String str = tt.getName();
            if (str.startsWith("ott")) {
                ottIDs.add(str.replace("ott", ""));
            }
        }
        System.out.println("Collected " + ottIDs.size() + " total ottIDs.");
    }
    
    
    // Reads a taxonomy file with rows formatted as:
    //     uid, parent_uid, name, rank, sourceinfo, uniqname, flags
    // store taxonomy info only for taxa appearing in the tree
    private void readTaxonomyTSV (String fileName) {
        
        ArrayList<String> templines = new ArrayList<String>();
        
        int count = 0;
        long start = 0;
        long time = 0;
        System.out.println("Reading taxonomy from file: " + fileName);
        try {
            
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            String str = "";
            
            while ((str = br.readLine()) != null) {
                if (!str.trim().equals("")) {
                    if (str.startsWith("uid")) {
                        //System.out.println("Skipping taxonomy header");
                        continue;
                    }
                    count += 1;
                    
                    // does taxon appear in tree?
                    if (ottIDs.contains(str.substring(0, str.indexOf("\t")))) {
                        templines.add(str);
                    }
                }
            }
            br.close();
            
        } catch (IOException ioe) {}
        System.out.println("Read " + count + " taxa; retained " + templines.size() + ".");
        
        start = System.nanoTime();
        // process lines
        for (int i = 0; i < templines.size(); i++) {
            
            StringTokenizer st = new StringTokenizer(templines.get(i), "|");
            String tid = null;
            String pid = null; // don't want to keep
            String name = null;
            String rank = null;
            String srce = null;
            String uniqname = null;
            String flag = null; // don't want to keep
            
            try {
                tid = st.nextToken().trim();
                pid = st.nextToken().trim();
                name = st.nextToken().trim();
                rank = st.nextToken().trim();
                srce = st.nextToken().trim();
                uniqname = st.nextToken().trim();
                flag = st.nextToken().trim();
            } catch (NoSuchElementException ex) {
                throw new NoSuchElementException("The taxonomy file appears to be missing some fields.");
            }
            HashMap<String, String> taxonInfo = new HashMap<>();
            taxonInfo.put("name", name);
            taxonInfo.put("rank", rank);
            taxonInfo.put("srce", srce);
            taxonInfo.put("uniqname", uniqname);
            taxonInfo.put("tid", tid);

            String ottID = "ott" + tid;
            taxNodeInfo.put(ottID, taxonInfo);
        }
        
        time = System.nanoTime() - start;
        System.out.println("Processed taxa in: " + time / 1000000000.0 + " seconds.");
        
        System.out.println("Stored " + taxNodeInfo.size() + " taxa from taxonomy TSV.");
    }
    
    
    private void readAnnotations (String fileName) {
        JSONParser jsonParser = new JSONParser();
        
        try {
            FileReader fileReader = new FileReader(fileName);
            jsonObject = (JSONObject) jsonParser.parse(fileReader);
            fileReader.close();
        } catch (IOException | ParseException e) {
        }
        nodeMetaData = (JSONObject) jsonObject.get("nodes");
        System.out.println("nodeMetaData is of size: " + nodeMetaData.size());
    }
    
    
    // this will all come from the top leval of the json
    private void setRootMetadata () {
        
        tx = graphDb.beginTx();
        
        System.out.println("Setting root metadata node");
        
        metadatanode = graphDb.createNode();
        metadatanode.createRelationshipTo(synthRootNode, RelType.SYNTHMETADATAFOR);
        
        System.out.println("Metadatanode = " + metadatanode.getId());
        
        // *** TODO: loop over properties rather than add hard-coded ones (may change)
        
        Iterator baseIter = jsonObject.entrySet().iterator();
        while (baseIter.hasNext()) {
            Map.Entry entry = (Map.Entry)baseIter.next();
            
            if (!(entry.getValue() instanceof JSONObject) && !(entry.getValue() instanceof JSONArray)) {
                System.out.println("Dealing with simple property: " + entry.getKey());
                metadatanode.setProperty(entry.getKey().toString(), entry.getValue());
                
        // nested arrays are a problem, as neo4j doesn't store them as properties
            } else if (entry.getValue() instanceof JSONArray) {
                /*
                String arrayName = entry.getKey().toString();
                System.out.println("Property '" + entry.getKey() + "' is an array.");
                List<String> arrList = (ArrayList<String>) jsonObject.get(arrayName);
                String[] strArr = arrList.toArray(new String[arrList.size()]);
                metadatanode.setProperty(arrayName, strArr);
                */
                //metadatanode.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
            /*
            if (entry.getValue() instanceof String) {
                System.out.println(entry.getKey() + " is of class String.");
            } else if (entry.getValue() instanceof Long) {
                System.out.println(entry.getKey() + " is of class Long.");
            }
            */
            //System.out.println("Value of '" + entry.getKey() + " is of class: " + entry.getValue().getClass());
        }
        // TODO: deal with arrays better
        List<String> flagList = (ArrayList<String>) jsonObject.get("filtered_flags");
        String[] flist = flagList.toArray(new String[flagList.size()]);
        metadatanode.setProperty("filtered_flags", flist);
        
        List<String> sourceList = (ArrayList<String>) jsonObject.get("sources");
        String[] slist = sourceList.toArray(new String[sourceList.size()]);
        metadatanode.setProperty("sources", slist);
        
        // store root ot_node_id here for fast retrieval
        metadatanode.setProperty("root_ot_node_id", synthRootNode.getProperty("ot_node_id"));
        synthMetaIndex.add(metadatanode, "name", synthTreeName);
        
        // put sources in separate node for easier retrieval. points to metadatanode
        Node sourceMeta = graphDb.createNode();
        storeSourceMetaData(sourceMeta);
        sourceMeta.createRelationshipTo(metadatanode, RelType.SOURCEMETADATAFOR);
        
        System.out.println("Sources node = " + sourceMeta.getId());
        
        sourceMapIndex.add(sourceMeta, "name", synthTreeName);
        
        tx.success();
        tx.finish();
    }
    
    
    // recursive
    // TODO: if newGraph == false, need to check existing nodes
    // TODO: add tax nodes earlier, check existing here
    private void postOrderAddTreeToGraph (JadeNode curJadeNode) throws TreeIngestException {
        if (nNodesToCommit % commitFrequency == 0 && nNodesToCommit > 0) {
            System.out.println("Committing nodes " + (nNodesToCommit - commitFrequency + 1) + " through " + nNodesToCommit);
            tx.success();
            tx.finish();
            tx = graphDb.beginTx();
        }
        
        // increment for the transaction frequency
        nNodesToCommit++;
        
        // postorder traversal via recursion
        for (int i = 0; i < curJadeNode.getChildCount(); i++) {
            postOrderAddTreeToGraph(curJadeNode.getChild(i));
        }
        
        Node newGraphNode = graphDb.createNode();
        
        String otNodeID = curJadeNode.getName();
        
        //newGraphNode.setProperty("ot_node_id", curJadeNode.getName());
        newGraphNode.setProperty(NodeProperty.OT_NODE_ID.propertyName, otNodeID);
        if (verbose) {
            System.out.print("Added " + newGraphNode + ": " + otNodeID);
            if (curJadeNode.isTheRoot()) {
                System.out.print("\n");
            } else {
                System.out.print(". Parent node: " + curJadeNode.getParent().getName() + "\n");
            }
        }
        
        curJadeNode.assocObject("gid", newGraphNode.getId());
        
        // taxonomy node; add info from hashmap
        if (curJadeNode.getName().startsWith("ott")) {
            HashMap<String, String> taxDat = taxNodeInfo.get(otNodeID);
            newGraphNode.setProperty(NodeProperty.NAME.propertyName, taxDat.get("name"));
            newGraphNode.setProperty(NodeProperty.TAX_UID.propertyName, taxDat.get("tid"));
            newGraphNode.setProperty(NodeProperty.TAX_RANK.propertyName, taxDat.get("rank"));
            newGraphNode.setProperty(NodeProperty.TAX_SOURCE.propertyName, taxDat.get("srce"));
            String uName = taxDat.get("uniqname");
            if ("".equals(uName)) {
                uName = taxDat.get("name");
            }
            newGraphNode.setProperty(NodeProperty.NAME_UNIQUE.propertyName, uName);
            
            graphNodeIndex.add(newGraphNode, NodeProperty.NAME.propertyName, taxDat.get("name"));
            graphTaxUIDNodeIndex.add(newGraphNode, NodeProperty.TAX_UID.propertyName, taxDat.get("tid"));
        }
        
        graphOTTNodeIDIndex.add(newGraphNode, NodeProperty.OT_NODE_ID.propertyName, otNodeID);
        
        // add relationships 
        //System.out.print("   Child nodes:");
        for (int i = 0; i < curJadeNode.getChildCount(); i++) {
            
            Node childNode = graphDb.getNodeById((Long) curJadeNode.getChild(i).getObject("gid"));
            Relationship newRel = childNode.createRelationshipTo(newGraphNode, RelType.SYNTHCHILDOF);
            newRel.setProperty("name", synthTreeName);
            
            // TODO: add metadata properties (for childnode) here
            // changing from storing in individual metadata nodes to within (outgoing) rels
            // - ind. metadata nodes: doubles nodes and rels for each tree
            
            String childID = curJadeNode.getChild(i).getName();
            HashMap<String, String> res = getAnnotations(childID);
            for (Map.Entry<String, String> entry : res.entrySet()) {
                newRel.setProperty(entry.getKey(), entry.getValue());
            }
            
            synthRelIndex.add(newRel, "draftTreeID", synthTreeName);
            
            // print out info for Aves
            /*
            if (newGraphNode.getProperty("ot_node_id") == "ott81461") {
                for (Map.Entry<String, String> entry : res.entrySet()) {
                    System.out.println(entry.getKey() + " : " + entry.getValue());
                }
            }
            */
            if (verbose) {
                System.out.println("   Created " + newRel + ": " + childNode + "(" + 
                    childNode.getProperty("ot_node_id") + ")"
                    + " -> " + newGraphNode + "(" + newGraphNode.getProperty("ot_node_id") + ")");
            }
        }
        
        
        
        if (curJadeNode.getName().equals(rootTaxonID)) {
            System.out.println("This is the ROOT");
            synthRootNode = newGraphNode;
            System.out.println("Root node = " + synthRootNode.getId());
        }
    }
    
    
    // convert to strings, because: neo4j does not support nested values
    // taxonomy not annotated; add in here (maybe?)
    private HashMap<String, String> getAnnotations (String otNodeID) {
        
        HashMap<String, String> res = new HashMap<>();
        // will not have annotations if just taxonomy
        if (nodeMetaData.containsKey(otNodeID)) {
            JSONObject indNodeInfo = (JSONObject) nodeMetaData.get(otNodeID);
            Iterator it = indNodeInfo.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry)it.next();
                String prop = (String) entry.getKey();
                JSONArray info = (JSONArray) indNodeInfo.get(prop);
                String str = "";
                for (int i=0; i < info.size(); i++) {
                    JSONArray terp = (JSONArray) info.get(i);
                    if (str != "") {
                        str += ",";
                    }
                    str += terp.get(0) + ":" + terp.get(1);
                }
                res.put(prop, str);
            }
        }
        
        // add taxonomy 'support'
        if (otNodeID.startsWith("ott")) {
            String taxSupport = "taxonomy:" + taxonomyVersion;
            if (res.containsKey("supported_by")) {
                taxSupport = res.get("supported_by") + "," + taxSupport;
            }
            res.put("supported_by", taxSupport);
        }
        return res;
    }
    
    
    /*
    Will look like:
    "pg_2044_tree4212": {
        "git_sha":"c6ce2f9067e9c74ca7b1f770623bde9b6de8bd1f",
        "tree_id":"tree4212",
        "study_id":"pg_2044"
    }
    will become:
    "pg_2044_tree4212" : "git_sha:c6ce2f9067e9c74ca7b1f770623bde9b6de8bd1f,tree_id:tree4212,study_id:pg_2044"
    */
    // store source_id_map in node. hacky; will clean up later
    private void storeSourceMetaData (Node metaNode) {
        
        JSONObject sourceIDMap = (JSONObject) jsonObject.get("source_id_map");
        //System.out.println("source_id_map length: " + sourceIDMap.size());
        
        HashMap<String, String> res = new HashMap<>();
        Iterator srcIter = sourceIDMap.keySet().iterator();
        
        while (srcIter.hasNext()) {
            String srcID = (String) srcIter.next();
            JSONObject indSrc = (JSONObject) sourceIDMap.get(srcID);
            Iterator iter = indSrc.entrySet().iterator();
            String str = "";
            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry)iter.next();
                
                if (str != "") {
                    str += ",";
                }
                str += entry.getKey() + ":" + (String) entry.getValue();
            }
            metaNode.setProperty(srcID, str);
        }
    }
}

