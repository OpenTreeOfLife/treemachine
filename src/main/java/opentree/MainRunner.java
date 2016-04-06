package opentree;

import gnu.trove.set.hash.TLongHashSet;;
import jade.deprecated.MessageLogger;
import jade.tree.deprecated.JadeNode;
import jade.tree.deprecated.JadeTree;
import jade.tree.deprecated.NexsonReader;
import jade.tree.deprecated.TreeReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;
import java.util.StringTokenizer;
import opentree.exceptions.TreeIngestException;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.neo4j.graphdb.Node;
import org.opentree.exceptions.DataFormatException;
import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.StoredEntityNotFoundException;
import org.opentree.exceptions.TaxonNotFoundException;
import org.opentree.graphdb.GraphDatabaseAgent;
import org.opentree.utils.GeneralUtils;

public class MainRunner {
    
    public int ingestSynthesisData(String [] args) throws FileNotFoundException, TaxonNotFoundException, TreeIngestException, IOException {
        if (args.length != 5) {
            System.out.println("arguments should be: newickFile jsonFile tsvFile graphName");
            return 1;
        }
        
        String newickFile = args[1];
        String jsonFile   = args[2];
        String taxFile    = args[3]; // taxonomy.tsv from ott
        String graphName  = args[4];
        
        boolean isNewGraph = true;
        boolean multiTreeAllowed = false;
        
        if (new File(graphName).exists()) {
            isNewGraph = false;
            if (!isNewGraph) {
                if (!multiTreeAllowed) {
                    String ret = "\nError: you are trying to add a synth tree to an existing graph. "
                        + "That is currently not desired behaviour. Try with a new graph.";
                    System.out.println(ret);
                    return 1;
                } else {
                    if (checkDuplicateTree(graphName, jsonFile)) {
                        String ret = "Error: trying to add a tree that "
                            + "is already in the graph. Exiting.";
                        System.out.println(ret);
                        System.exit(1);
                    }
                }
            }
        }
        
        if (!new File(newickFile).exists()) {
            System.err.println("Could not open the newick file '" + newickFile + "'. Exiting...");
            return -1;
        }
        if (!new File(jsonFile).exists()) {
            System.err.println("Could not open the json file '" + jsonFile + "'. Exiting...");
            return -1;
        }
        
        IngestSynthesisData tl = new IngestSynthesisData(graphName);
        tl.buildDB(newickFile, jsonFile, taxFile, isNewGraph);

        return 0;
    }
    
    public boolean checkDuplicateTree (String graphName, String jsonFile) {
        boolean duplicate = false;
        // graph exists. check if trying to ingest same tree
        
        // first get synth tree name
        JSONObject jsonObject = null;
        JSONParser jsonParser = new JSONParser();
        try {
            FileReader fileReader = new FileReader(jsonFile);
            jsonObject = (JSONObject) jsonParser.parse(fileReader);
            fileReader.close();
        } catch (IOException | ParseException e) {
        }
        String synthTreeName = (String) jsonObject.get("tree_id");
        
        // now, check if already in graph
        GraphExplorer ge = new GraphExplorer(graphName);
        if (ge.checkExistingSynthTreeID(synthTreeName)) {
            duplicate = true;
            System.out.println("\nError: tree '" + synthTreeName + "' already in graph.");
        }
        ge.shutdownDB();
        return duplicate;
    }
    
    
    // @returns 0 for success, 1 for poorly formed command
    public int listSynthTrees(String [] args) {
        boolean listIDs = false;
        String graphname;
        if (args.length != 2) {
            System.out.println("arguments should be: graphdbfolder");
            return 1;
        }
        graphname = args[1];
        GraphExplorer ge = new GraphExplorer(graphname);
        try {
            ArrayList<String> result;
            result = ge.getSynthTreeIDs();
            System.out.println(StringUtils.join(result, "\n"));
        } finally {
            ge.shutdownDB();
        }
        return 0;
    }
    
    
    // @returns 0 for success, 1 for poorly formed command
    public int dotGraphExporter(String [] args) throws TaxonNotFoundException {
        String usageString = "arguments should be name outfile usetaxonomy[T|F] graphdbfolder";
        if (args.length != 5) {
            System.out.println(usageString);
            return 1;
        }
        String taxon = args[1];
        String outfile = args[2];
        String _useTaxonomy = args[3];
        String graphname = args[4];
        
        boolean useTaxonomy = false;
        if (_useTaxonomy.equals("T")) {
            useTaxonomy = true;
        } else if (!(_useTaxonomy.equals("F"))) {
            System.out.println(usageString);
            return 1;
        }
        
        GraphExporter ge = new GraphExporter(graphname);
        try {
            ge.writeGraphDot(taxon, outfile, useTaxonomy);
        } finally {
            ge.shutdownDB();
        }
        return 0;
    }
    
    
    public int extractDraftTreeByName(String [] args) throws MultipleHitsException, TaxonNotFoundException {
        if (args.length < 4) {
            System.out.println("arguments should be syntheTreeName outFileName (startNodeID) graphdbfolder");
            return 1;
        }
        String synthName = args[1];
        String outFileName = args[2];
        String graphname = "";
        Long startNodeId = (long) -1;
        if (args.length == 5) {
            startNodeId = Long.valueOf(args[3]);
            graphname = args[4];
        } else {
            graphname = args[3];
        }
        
        GraphExplorer ge = new GraphExplorer(graphname);
        
        Node firstNode = null;
        if (startNodeId != -1) {
            firstNode = ge.graphDb.getNodeById(startNodeId);
        }
        
        JadeTree synthTree = null;
        String labelFormat = "name_and_id";
        synthTree = ge.extractDraftTree(firstNode, synthName, labelFormat);

        if (synthTree == null) {
            return -1;
        }
        
        PrintWriter outFile = null;
        try {
            outFile = new PrintWriter(new FileWriter(outFileName));
            outFile.write(synthTree.getRoot().getNewick(false) + ";\n");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            outFile.close();
            ge.shutdownDB();
        }
        
        return 0;
    }
    
    
    public int nodeInfo(String [] args) {
        if (args.length != 3) {
            System.out.println("arguments should be: node graphdb");
            return 1;
        }
        String node = args[1];
        GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[2]);
        System.out.println("Getting information about node " + node);
        Long nodel = Long.valueOf(node);
        Node tn = graphDb.getNodeById(nodel);
        System.out.println("properties\n================\n");
        for (String ts:tn.getPropertyKeys()) {
            if (ts.equals("mrca") || ts.equals("outmrca") || ts.equals("nested_mrca")) {
                System.out.print(ts + "\t");
                long [] m = (long[])tn.getProperty(ts);
                if (m.length < 100000) {
                    for (int i = 0; i < m.length; i++) {
                        System.out.print(m[i] + " ");
                    }
                    System.out.print("\n");
                }
                System.out.println(m.length);
                TLongHashSet ths = new TLongHashSet(m);
                System.out.println(ths.size());
            } else if (ts.equals("name")) {
                System.out.println(ts + "\t" + (String)tn.getProperty(ts));
            } else {
                System.out.println(ts + "\t" + (String)tn.getProperty(ts));
            }
        }
        graphDb.shutdownDb();
        return 0;
    }
    
    
    /**
     * Constructs a newick tree file from a passed in taxonomy file
     * arguments are:
     * taxonomy_filename output_tree_filename uids_as_labels[T|F]
     * @param args
     * @return
     * @throws Exception
     */
    // @returns 0 for success, 1 for poorly formed command
    public int convertTaxonomy(String []args) {
        if (args.length != 3 & args.length != 4) {
            System.out.println("arguments should be: taxonomyfile treefile (optional:labels=UIDs [T|F])");
            return 1;
        }
        JadeTree tree = null;
        JadeNode root = null;
        String cellular = "93302"; // default. for full tree.
        Boolean cellularHit = false;
        String taxonomyRoot = "";
        String taxonomyfile = args[1];
        Boolean uidLabels = false;
        if (args.length == 4) {
            String uids = args[3];
            if (uids.toLowerCase().equals("t")) {
                uidLabels = true;
            }
        }
        
        try {
            BufferedReader br = new BufferedReader(new FileReader(taxonomyfile));
            String str;
            int count = 0;
            HashMap<String,JadeNode> id_node_map = new HashMap<String,JadeNode>();
            HashMap<String,ArrayList<String>> id_childs = new HashMap<String,ArrayList<String>>();
            while ((str = br.readLine()) != null) {
                // check the first line to see if it the file has a header that we should skip
                if (count == 0) {
                    if (str.startsWith("uid")) { // file contains a header. skip line
                        System.out.println("Skipping taxonomy header line: " + str);
                        continue;
                    }
                }
                if (!str.trim().equals("")) {
                    // collect sets of lines until we reach the transaction frequency
                    count += 1;
                    StringTokenizer st = new StringTokenizer(str,"|");
                    String tid = null;
                    String pid = null;
                    String name = null;
                    String rank = null;
                    String srce = null;
                    String uniqname = null;
                    String flag = null; 
                    tid = st.nextToken().trim();
                    pid = st.nextToken().trim();
                    name = st.nextToken().trim();
                    rank = st.nextToken().trim();
                    srce = st.nextToken().trim();
                    uniqname = st.nextToken().trim();
                    flag = st.nextToken().trim(); //for dubious
                    if (pid.trim().equals("")) {
                        taxonomyRoot = tid;
                    }
                    if (tid == cellular) {
                        cellularHit = true;
                    }
                    if (id_childs.containsKey(pid) == false) {
                        id_childs.put(pid, new ArrayList<String>());
                    }
                    id_childs.get(pid).add(tid);
                    JadeNode tnode = new JadeNode();
                    if (uidLabels) {
                        tnode.setName(("ott").concat(tid));
                    } else {
                        tnode.setName(GeneralUtils.scrubName(name).concat("_ott").concat(tid));
                    }
                    tnode.assocObject("id", tid);
                    id_node_map.put(tid, tnode);
                }
            }
            br.close();
            count = 0;
            // construct tree
            Stack <JadeNode> nodes = new Stack<JadeNode>();
            if (cellularHit) {
                taxonomyRoot = cellular;
            }
            System.out.println("Setting root to: " + taxonomyRoot);
            root = id_node_map.get(taxonomyRoot);
            
            nodes.add(root);
            while (nodes.empty() == false) {
                JadeNode tnode = nodes.pop();
                count += 1;
                ArrayList<String> childs = id_childs.get((String)tnode.getObject("id"));
                for (int i = 0; i < childs.size(); i++) {
                    JadeNode ttnode = id_node_map.get(childs.get(i));
                    tnode.addChild(ttnode);
                    if (id_childs.containsKey(childs.get(i))) {
                        nodes.add(ttnode);
                    }
                }
                if (count%10000 == 0) {
                    System.out.println(count);
                }
            }
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        tree = new JadeTree(root);
        tree.processRoot();
        String outfile = args[2];
        FileWriter fw;
        try {
            fw = new FileWriter(outfile);
            fw.write(tree.getRoot().getNewick(false) + ";");
            fw.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return 0;
    }
    
    
    /*
     * Read in nexson-formatted tree, export as newick
     */
    /// @returns 0 for success, 1 for poorly formed command
    public int nexson2newick(String [] args) throws DataFormatException, TaxonNotFoundException, TreeIngestException {
        if (args.length > 3 | args.length < 2) {
            System.out.println("arguments should be: filename.nexson [outname.newick]");
            return 1;
        }
        
        String filename = args[1];
        String outFilename = "";
        
        if (args.length == 3) {
            outFilename = args[2];
        } else {
            outFilename = filename + ".tre";
        }
        
        int treeCounter = 0;
        ArrayList<JadeTree> jt = new ArrayList<>();
        MessageLogger messageLogger = new MessageLogger("nexson2newick:");
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            if (GeneralUtils.divineTreeFormat(br).compareTo("nexson") != 0) {
                System.out.println("tree does not appear to be in nexson format");
                return 1;
            } else { // nexson
                System.out.println("Reading nexson file...");
                for (JadeTree tree : NexsonReader.readNexson(filename, false, messageLogger)) {
                    if (tree == null) {
                        messageLogger.indentMessage(1, "Skipping null tree.");
                    } else {
                        jt.add(tree);
                        treeCounter++;
                    }
                }
            }
            br.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println(treeCounter + " trees read.");
        
        PrintWriter outFile = null;
        try {
            outFile = new PrintWriter(new FileWriter(outFilename));
            
            for (int i = 0; i < jt.size(); i++) {
                outFile.write(jt.get(i).getRoot().getNewick(false) + ";\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            outFile.close();
        }
        
        System.out.println("Sucessfully wrote " + treeCounter + " newick trees to file '" + outFilename + "'");        
        return 0;
    }
    
    
    private JadeTree readNewick (String newickFile) {
        String inputNewick = "";
        System.out.println("Reading tree from file: " + newickFile);
        try {
            BufferedReader br = new BufferedReader(new FileReader(newickFile));
            inputNewick =  br.readLine();
            br.close();
        } catch (IOException ioe) {}
        
        TreeReader tr = new TreeReader();
        JadeTree tree = tr.readTree(inputNewick);
        return tree;
    }
    
    
    private void recursiveTreeTraversal (JadeNode curJadeNode) {
        for (int i = 0; i < curJadeNode.getChildCount(); i++) {
            recursiveTreeTraversal(curJadeNode.getChild(i));
        }
        String name = curJadeNode.getName();
        
        if ("ott1085739".equals(name)) {
            int ntips = curJadeNode.getDescendantLeavesNumbers();
            System.out.println("Hit \"Gavia arctica\". ntips = " + ntips);
        }
    }
    
    
    public int test (String [] args) {
        
        // put stuff to test in me
        String filename = args[1];
        JadeTree tree = readNewick(filename);
        JadeNode root = tree.getRoot();
        recursiveTreeTraversal(root);
        return 0;
    }
    
    
    public static void printHelp() {
        System.out.println("==========================");
        System.out.println("usage: treemachine is run as:");
        System.out.println("");
        System.out.println("ingestsynth newick_tree json_annotations tsv_taxonomy DB_name\n");
    }
    
    
    /**
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {
        //PropertyConfigurator.configure(System.getProperties());
        //System.err.println("treemachine version alpha.alpha.prealpha");
        if (args.length < 1) {
            printHelp();
            System.exit(1);
        }
        String command = args[0];
        if (command.compareTo("help") == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            printHelp();
            System.exit(0);
        }
        System.err.println("things will happen here");
        int cmdReturnCode = 0;
        try {
            MainRunner mr = new MainRunner();
            
            if (command.compareTo("listsynthtrees") == 0) { 
                cmdReturnCode = mr.listSynthTrees(args);
            } else if (command.compareTo("exporttodot") == 0) {
                cmdReturnCode = mr.dotGraphExporter(args);
            } else if (command.compareTo("converttaxonomy") == 0) {
                cmdReturnCode = mr.convertTaxonomy(args);
            } else if (command.compareTo("extractdrafttree_name") == 0) {
                cmdReturnCode = mr.extractDraftTreeByName(args);
            } else if (command.compareTo("nexson2newick") == 0) {
                cmdReturnCode = mr.nexson2newick(args);
            } else if (command.equals("nodeinfo")) {
                cmdReturnCode = mr.nodeInfo(args);
            } else if (command.compareTo("ingestsynth") == 0) {
                cmdReturnCode = mr.ingestSynthesisData(args);
            } 
            
            // test function
            else if (command.compareTo("test") == 0) {
                cmdReturnCode = mr.ingestSynthesisData(args);
            }
            
            else {
                System.err.println("Unrecognized command \"" + command + "\"");
                cmdReturnCode = 2;
            }
        } catch (StoredEntityNotFoundException tnfx) {
            String action = "Command \"" + command + "\"";
            tnfx.reportFailedAction(System.err, action);
        } catch (TreeIngestException tix) {
            String action = "Command \"" + command + "\"";
            tix.reportFailedAction(System.err, action);
        } catch (DataFormatException dfx) {
            String action = "Command \"" + command + "\"";
            dfx.reportFailedAction(System.err, action);
        }
        if (cmdReturnCode == 2) {
            printHelp();
        }
        System.exit(cmdReturnCode);
    }

}
