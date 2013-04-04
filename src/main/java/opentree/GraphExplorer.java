package opentree;

import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.TreeReader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Stack;

//import opentree.GraphBase.RelTypes;
import opentree.synthesis.TreeMakingBandB;
import opentree.synthesis.TreeMakingExhaustivePairs;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
//import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.IndexHits;
//import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

public class GraphExplorer extends GraphBase {
    private SpeciesEvaluator se;
    private ChildNumberEvaluator cne;
    private TaxaListEvaluator tle;

    /* -------------------- begin info: collapsing children based on taxonomy ----------------------- */

    /*
     * settings and variables below are related to features controlling the heuristic placement of external taxa that would otherwise not be observed in
     * synthetic phylogenies.
     * 
     * Currently there are two options: "strict" and "relaxed".
     * 
     * STRICT: Activating the strict option will assign external descendants that are lost during conflict tie-breaking (i.e. "lost children") as children of
     * the node where the tie-breaking event occurred (i.e. they are "sunk" to that level in the tree). This will result in very conservative (in this case
     * meaning deep and imprecise) assignment of "lost child" taxa.
     * 
     * RELAXED: External descendants of each node in the taxonomy that are not present in the synthesized tree will be sunk into the MRCA (in the synthesized
     * tree) of all the present external descendants of that taxonomy node. This essentially assumes that in the absence of phylogenetic information placing a
     * given taxon in a tree, we can use taxonomic information as an estimate of its phylogenetic position. May or may not be reasonable, depending on the use
     * of the tree and the phylogenetic accuracy of the taxonomic classification. Certainly results in more precise placement of "lost child" taxa (at the
     * potential expense of correct placement in some cases.)
     */

    // used when sinking children using the strict setting
    private HashSet<Long> knownIdsInTree;

    // only one of these should be turned on at once
    private static final boolean sinkLostChildrenStrict = false;
    private static final boolean sinkLostChildrenRelaxed = true;

    /* --------------------- end info: collapsing children based on taxonomy ---------------------- */

    public GraphExplorer(String graphname) {
        graphDb = new GraphDatabaseAgent(graphname);
        finishInitialization();
    }

    public GraphExplorer(GraphDatabaseService gdb) {
        graphDb = new GraphDatabaseAgent(gdb);
        finishInitialization();
    }

    private void finishInitialization() {
        cne = new ChildNumberEvaluator();
        cne.setChildThreshold(100);
        se = new SpeciesEvaluator();
        tle = new TaxaListEvaluator();
        graphNodeIndex = graphDb.getNodeIndex("graphNamedNodes");
        sourceRootIndex = graphDb.getNodeIndex("sourceRootNodes");
        sourceRelIndex = graphDb.getRelIndex("sourceRels");
        sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
    }

    public void printLicaNames(String nodeid) {
        Node gn = graphDb.getNodeById(Long.valueOf(nodeid));
        if (gn == null) {
            System.out.println("can't find " + nodeid + ". is it more than just a number?");
            return;
        }

        long[] mrcas = (long[]) gn.getProperty("mrca");
        for (int i = 0; i < mrcas.length; i++) {
            Node tn = graphDb.getNodeById(mrcas[i]);
            if (tn.hasProperty("name")) {
                System.out.println(tn.getProperty("name"));
            }
        }
    }

    /**
     * Given a taxonomic name, construct a json object of the subgraph of MRCACHILDOF relationships that are rooted at the specified node. Names that appear in
     * the JSON are taken from the corresonding nodes in the taxonomy graph (using the ISCALLED relationships).
     * 
     * @param name
     *            the name of the root node (should be the name in the graphNodeIndex)
     */
    public void constructJSONGraph(String name) {
        Node firstNode = findGraphNodeByName(name);
        if (firstNode == null) {
            System.out.println("name not found");
            return;
        }
        // System.out.println(firstNode.getProperty("name"));
        TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
                .relationships(RelTypes.MRCACHILDOF, Direction.INCOMING);
        HashMap<Node, Integer> nodenumbers = new HashMap<Node, Integer>();
        HashMap<Integer, Node> numbernodes = new HashMap<Integer, Node>();
        int count = 0;
        for (Node friendnode : CHILDOF_TRAVERSAL.traverse(firstNode).nodes()) {
            nodenumbers.put(friendnode, count);
            numbernodes.put(count, friendnode);
            count += 1;
        }
        PrintWriter outFile;
        try {
            outFile = new PrintWriter(new FileWriter("graph_data.js"));
            outFile.write("{\"nodes\":[");
            for (int i = 0; i < count; i++) {
                Node tnode = numbernodes.get(i);
                if (tnode.hasProperty("name"))
                    outFile.write("{\"name\":\"" + (tnode.getProperty("name")) + "");
                else
                    outFile.write("{\"name\":\"");
                // outFile.write("{\"name\":\""+tnode.getProperty("name")+"");
                outFile.write("\",\"group\":" + nodenumbers.get(tnode) + "");
                if (i + 1 < count)
                    outFile.write("},");
                else
                    outFile.write("}");
            }
            outFile.write("],\"links\":[");
            String outs = "";
            for (Node tnode : nodenumbers.keySet()) {
                Iterable<Relationship> it = tnode.getRelationships(RelTypes.MRCACHILDOF, Direction.OUTGOING);
                for (Relationship trel : it) {
                    if (nodenumbers.get(trel.getStartNode()) != null && nodenumbers.get(trel.getEndNode()) != null) {
                        outs += "{\"source\":" + nodenumbers.get(trel.getStartNode()) + "";
                        outs += ",\"target\":" + nodenumbers.get(trel.getEndNode()) + "";
                        outs += ",\"value\":" + 1 + "";
                        outs += "},";
                    }
                }
            }
            outs = outs.substring(0, outs.length() - 1);
            outFile.write(outs);
            outFile.write("]");
            outFile.write("}\n");
            outFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This will walk the graph and attempt to report the bipartition information
     */

    public void getBipartSupport(String starttaxon) {
        Node startnode = (graphNodeIndex.get("name", starttaxon)).next();
        TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
                .relationships(RelTypes.MRCACHILDOF, Direction.INCOMING);
        HashMap<Long, String> id_to_name = new HashMap<Long, String>();
        HashSet<Node> tips = new HashSet<Node>();
        HashMap<Node, HashMap<Node, Integer>> childs_scores = new HashMap<Node, HashMap<Node, Integer>>();
        HashMap<Node, HashMap<Node, Integer>> scores = new HashMap<Node, HashMap<Node, Integer>>();
        HashMap<Node, HashSet<Node>> child_parents_map = new HashMap<Node, HashSet<Node>>();
        HashMap<Node, Integer> node_score = new HashMap<Node, Integer>();
        HashSet<Node> allnodes = new HashSet<Node>();
        for (Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(startnode).nodes()) {
            if (friendnode.hasRelationship(Direction.INCOMING, RelTypes.MRCACHILDOF) == false)
                tips.add(friendnode);
            HashMap<Node, Integer> conflicts_count = new HashMap<Node, Integer>();
            child_parents_map.put(friendnode, new HashSet<Node>());
            int count = 0;
            for (Relationship rel : friendnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)) {
                if (rel.getProperty("source").equals("taxonomy") == true || rel.getProperty("source").equals("ottol") == true)
                    continue;
                if (conflicts_count.containsKey(rel.getEndNode()) == false) {
                    conflicts_count.put(rel.getEndNode(), 0);
                }
                Integer tint = (Integer) conflicts_count.get(rel.getEndNode()) + 1;
                conflicts_count.put(rel.getEndNode(), tint);
                child_parents_map.get(friendnode).add(rel.getEndNode());
                count += 1;
            }
            node_score.put(friendnode, count);
            for (Node tnode : conflicts_count.keySet()) {
                if (childs_scores.containsKey(tnode) == false) {
                    childs_scores.put(tnode, new HashMap<Node, Integer>());
                }
                childs_scores.get(tnode).put(friendnode, conflicts_count.get(tnode));
            }
            scores.put(friendnode, conflicts_count);
            allnodes.add(friendnode);
        }

        HashMap<Node, String> node_names = new HashMap<Node, String>();
        for (Node friendnode : allnodes) {
            String mrname = "";
            System.out.println("========================");
            if (friendnode.hasProperty("name")) {
                id_to_name.put((Long) friendnode.getId(), (String) friendnode.getProperty("name"));
                System.out.println(friendnode.getProperty("name") + " (" + node_score.get(friendnode) + ") " + friendnode);
                mrname = (String) friendnode.getProperty("name");
            } else {
                long[] mrcas = (long[]) friendnode.getProperty("mrca");
                for (int i = 0; i < mrcas.length; i++) {
                    if (id_to_name.containsKey((Long) mrcas[i]) == false) {
                        id_to_name.put((Long) mrcas[i], (String) graphDb.getNodeById(mrcas[i]).getProperty("name"));
                    }
                    System.out.print(id_to_name.get((Long) mrcas[i]) + " ");
                    mrname += id_to_name.get((Long) mrcas[i]) + " ";
                }
                System.out.print(friendnode + " \n");
            }
            System.out.println("\t" + scores.get(friendnode).size());

            node_names.put(friendnode, mrname);

            for (Node tnode : scores.get(friendnode).keySet()) {
                System.out.println("\t\t" + tnode + " " + scores.get(friendnode).get(tnode));
                System.out.print("\t\t");
                long[] mrcas = (long[]) tnode.getProperty("mrca");
                for (int i = 0; i < mrcas.length; i++) {
                    if (id_to_name.containsKey((Long) mrcas[i]) == false) {
                        id_to_name.put((Long) mrcas[i], (String) graphDb.getNodeById(mrcas[i]).getProperty("name"));
                    }
                    System.out.print(id_to_name.get((Long) mrcas[i]) + " ");
                }
                System.out.print("\n");
            }
        }
        // all calculations are done, this is just for printing
        for (Node friendnode : allnodes) {
            System.out.println(friendnode + " " + node_score.get(friendnode) + " " + node_names.get(friendnode));
        }

        // write out the root to tip stree weight tree
        // construct_root_to_tip_stree_weight_tree(startnode,node_score,childs_scores);
    }

    /**
     * map the support (or the number of subtending source trees that support a particular node in the given tree
     * 
     * @param infile
     * @param outfile
     * @throws TaxonNotFoundException
     */
    public void getMapTreeSupport(String infile, String outfile) throws TaxonNotFoundException {
        PathFinder<Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 1000);
        Node focalnode = findTaxNodeByName("life");
        String ts = "";
        JadeTree jt = null;
        TreeReader tr = new TreeReader();
        try {
            BufferedReader br = new BufferedReader(new FileReader(infile));
            while ((ts = br.readLine()) != null) {
                if (ts.length() > 1)
                    jt = tr.readTree(ts);
            }
            br.close();
        } catch (IOException ioe) {
        }
        System.out.println("trees read");

        // need to map the tips of the trees to the map
        ArrayList<JadeNode> nds = jt.getRoot().getTips();
        HashSet<Long> ndids = new HashSet<Long>();
        // We'll map each Jade node to the internal ID of its taxonomic node.
        HashMap<JadeNode, Long> hashnodeids = new HashMap<JadeNode, Long>();
        // same as above but added for nested nodes, so more comprehensive and
        // used just for searching. the others are used for storage
        HashSet<Long> ndidssearch = new HashSet<Long>();
        HashMap<JadeNode, ArrayList<Long>> hashnodeidssearch = new HashMap<JadeNode, ArrayList<Long>>();
        HashSet<JadeNode> skiptips = new HashSet<JadeNode>();
        for (int j = 0; j < nds.size(); j++) {
            // find all the tip taxa and with doubles pick the taxon closest to the focal group
            Node hitnode = null;
            String processedname = nds.get(j).getName();// .replace("_", " "); //@todo processing syntactic rules like '_' -> ' ' should be done on input
                                                        // parsing.
            IndexHits<Node> hits = graphNodeIndex.get("name", processedname);
            int numh = hits.size();
            if (numh == 1) {
                hitnode = hits.getSingle();
            } else if (numh > 1) {
                System.out.println(processedname + " gets " + numh + " hits");
                int shortest = 1000;// this is shortest to the focal, could reverse this
                Node shortn = null;
                for (Node tnode : hits) {
                    Path tpath = pf.findSinglePath(tnode, focalnode);
                    if (tpath != null) {
                        if (shortn == null)
                            shortn = tnode;
                        if (tpath.length() < shortest) {
                            shortest = tpath.length();
                            shortn = tnode;
                        }
                        // System.out.println(shortest+" "+tpath.length());
                    } else {
                        System.out.println("one taxon is not within life");
                    }
                }
                assert shortn != null; // @todo this could happen if there are multiple hits outside the focalgroup, and none inside the focalgroup. We should
                                       // develop an AmbiguousTaxonException class
                hitnode = shortn;
            }
            hits.close();
            if (hitnode == null) {
                System.out.println("Skipping taxon not found in graph: " + processedname);
                skiptips.add(nds.get(j));
                // assert numh == 0;
                // throw new TaxonNotFoundException(processedname);
            } else {
                // added for nested nodes
                long[] mrcas = (long[]) hitnode.getProperty("mrca");
                ArrayList<Long> tset = new ArrayList<Long>();
                for (int k = 0; k < mrcas.length; k++) {
                    ndidssearch.add(mrcas[k]);
                    tset.add((Long) mrcas[k]);
                }
                hashnodeidssearch.put(nds.get(j), tset);
                ndids.add(hitnode.getId());
                hashnodeids.put(nds.get(j), hitnode.getId());
            }
        }
        // Store the list of taxonomic IDs and the map of JadeNode to ID in the root.
        jt.getRoot().assocObject("ndids", ndids);
        jt.getRoot().assocObject("hashnodeids", hashnodeids);
        jt.getRoot().assocObject("ndidssearch", ndidssearch);
        jt.getRoot().assocObject("hashnodeidssearch", hashnodeidssearch);

        if (skiptips.size() >= (nds.size() * 0.8)) {
            System.out.println("too many tips skipped");
            return;
        }

        postorderMapTree(jt.getRoot(), jt.getRoot(), focalnode, skiptips);
        PrintWriter outFile;
        try {
            outFile = new PrintWriter(new FileWriter(outfile));
            outFile.write(jt.getRoot().getNewick(true) + ";\n");
            outFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void postorderMapTree(JadeNode inode, JadeNode root, Node focalnode, HashSet<JadeNode> skiptips) {
        for (int i = 0; i < inode.getChildCount(); i++) {
            postorderMapTree(inode.getChild(i), root, focalnode, skiptips);
        }

        HashMap<JadeNode, Long> roothash = ((HashMap<JadeNode, Long>) root.getObject("hashnodeids"));
        HashMap<JadeNode, ArrayList<Long>> roothashsearch = ((HashMap<JadeNode, ArrayList<Long>>) root.getObject("hashnodeidssearch"));
        // get the licas
        if (inode.getChildCount() > 0) {
            ArrayList<JadeNode> nds = inode.getTips();
            nds.removeAll(skiptips);
            ArrayList<Node> hit_nodes = new ArrayList<Node>();
            ArrayList<Node> hit_nodes_search = new ArrayList<Node>();
            // store the hits for each of the nodes in the tips
            for (int j = 0; j < nds.size(); j++) {
                hit_nodes.add(graphDb.getNodeById(roothash.get(nds.get(j))));
                ArrayList<Long> tlist = roothashsearch.get(nds.get(j));
                for (int k = 0; k < tlist.size(); k++) {
                    hit_nodes_search.add(graphDb.getNodeById(tlist.get(k)));
                }
            }
            // get all the childids even if they aren't in the tree, this is the postorder part
            HashSet<Long> childndids = new HashSet<Long>();

            // System.out.println(inode.getNewick(false));
            for (int i = 0; i < inode.getChildCount(); i++) {
                if (skiptips.contains(inode.getChild(i))) {
                    continue;
                }
                Node[] dbnodesob = (Node[]) inode.getChild(i).getObject("dbnodes");
                for (int k = 0; k < dbnodesob.length; k++) {
                    long[] mrcas = ((long[]) dbnodesob[k].getProperty("mrca"));
                    for (int j = 0; j < mrcas.length; j++) {
                        if (childndids.contains(mrcas[j]) == false)
                            childndids.add(mrcas[j]);
                    }
                }
            }
            // _LOG.trace("finished names");
            HashSet<Long> rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndidssearch"));
            HashSet<Node> ancestors = LicaUtil.getAllLICA(hit_nodes_search, childndids, rootids);
            if (ancestors.size() > 0) {
                int count = 0;
                for (Node tnode : ancestors) {
                    // calculate the number of supported branches
                    for (Relationship rel : tnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)) {
                        count += 1;
                    }
                }
                inode.setName(String.valueOf(count));
                inode.assocObject("dbnodes", ancestors.toArray(new Node[ancestors.size()]));
                long[] ret = new long[hit_nodes.size()];
                for (int i = 0; i < hit_nodes.size(); i++) {
                    ret[i] = hit_nodes.get(i).getId();
                }
                rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndids"));
                long[] ret2 = new long[rootids.size()];
                Iterator<Long> chl2 = rootids.iterator();
                int i = 0;
                while (chl2.hasNext()) {
                    ret2[i] = chl2.next().longValue();
                    i++;
                }
                inode.assocObject("exclusive_mrca", ret);
                inode.assocObject("root_exclusive_mrca", ret2);
            } else {
                System.out.println("node not found");
                Node[] nar = {};
                inode.assocObject("dbnodes", nar);
            }
        } else {
            if (skiptips.contains(inode)) {
                return;
            }
            Node[] nar = { graphDb.getNodeById(roothash.get(inode)) };
            inode.assocObject("dbnodes", nar);
        }
    }

    /*
     * starts from the root and goes to the tips picking the best traveled routes if you want a more resolved tree, send the child_scores that are cumulative
     */
    public void constructRootToTipSTreeWeightTree(Node startnode, HashMap<Node, Integer> node_score, HashMap<Node, HashMap<Node, Integer>> childs_scores) {
        Stack<Node> st = new Stack<Node>();
        st.push(startnode);
        JadeNode root = new JadeNode();
        HashMap<Long, JadeNode> node_jade_map = new HashMap<Long, JadeNode>();
        node_jade_map.put(startnode.getId(), root);
        HashSet<Node> visited = new HashSet<Node>();
        root.setName(String.valueOf(startnode.getId()));
        visited.add(startnode);
        while (st.isEmpty() == false) {
            Node friendnode = st.pop();
            JadeNode pnode = null;
            if (node_jade_map.containsKey(friendnode.getId())) {
                pnode = node_jade_map.get(friendnode.getId());
            } else {
                pnode = new JadeNode();
                if (friendnode.hasProperty("name"))
                    pnode.setName((String) friendnode.getProperty("name"));
                else
                    pnode.setName(String.valueOf((long) friendnode.getId()));
                pnode.setName(pnode.getName() + "_" + String.valueOf(node_score.get(friendnode)));
            }
            long[] mrcas = (long[]) friendnode.getProperty("mrca");
            HashSet<Long> pmrcas = new HashSet<Long>();
            for (int i = 0; i < mrcas.length; i++) {
                pmrcas.add(mrcas[i]);
            }
            boolean going = true;
            HashSet<Long> curmrcas = new HashSet<Long>();
            while (going) {
                boolean nomatch = false;
                int highest = 0;
                int smdiff = 1000000000;
                Node bnode = null;
                // pick one
                for (Node tnode : childs_scores.get(friendnode).keySet()) {
                    try {
                        int tscore = childs_scores.get(friendnode).get(tnode);
                        if (tscore >= highest) {// could specifically choose the equal weighted by resolution
                            boolean br = false;
                            long[] mrcas2 = (long[]) tnode.getProperty("mrca");
                            for (int i = 0; i < mrcas2.length; i++) {
                                if (curmrcas.contains((Long) mrcas2[i])) {
                                    br = true;
                                    break;
                                }
                            }

                            if (br == false) {
                                highest = tscore;
                                bnode = tnode;
                            }
                        }
                    } catch (Exception e) {
                    }
                }
                if (bnode == null) {
                    nomatch = true;
                } else {
                    long[] mrcas1 = (long[]) bnode.getProperty("mrca");
                    for (int i = 0; i < mrcas1.length; i++) {
                        curmrcas.add(mrcas1[i]);
                    }
                    pmrcas.removeAll(curmrcas);
                    JadeNode tnode1 = null;
                    if (node_jade_map.containsKey(bnode.getId())) {
                        tnode1 = node_jade_map.get(bnode.getId());
                    } else {
                        tnode1 = new JadeNode(pnode);
                        if (bnode.hasProperty("name")) {
                            tnode1.setName((String) bnode.getProperty("name"));
                        }
                        else {
                            tnode1.setName(String.valueOf((long) bnode.getId()));
                            tnode1.setName(tnode1.getName() + "_" + String.valueOf(node_score.get(bnode)));
                        }
                    }
                    pnode.addChild(tnode1);
                    node_jade_map.put(bnode.getId(), tnode1);
                    if (childs_scores.containsKey(bnode))
                        st.push(bnode);
                }
                if (pmrcas.size() == 0 || nomatch == true) {
                    going = false;
                    break;
                }
            }
            node_jade_map.put(friendnode.getId(), pnode);
        }
        JadeTree tree = new JadeTree(root);
        System.out.println(tree.getRoot().getNewick(false) + ";");
    }

    /**
     * Constructs a newick breaking ties based on support (or random)
     * 
     * TODO: instead of random, needs to be based on nested or not or other factors
     * 
     * @param taxname
     */
    public void constructNewickTieBreakerDEFAULT(String taxname, boolean useTaxonomy, boolean useBranchAndBound) {

        // TODO: make this user-settable
        boolean reportBranchLength = false;

        // find the start node
        Node firstNode = findGraphNodeByName(taxname);
        if (firstNode == null) {
            System.out.println("name not found");
            return;
        }

        // get the tree structure as a JadeTree object
        JadeNode root = preorderConstructNewickTieBreakerDEFAULT(firstNode, null, null, "", useBranchAndBound, useTaxonomy);
        JadeTree tree = new JadeTree(root);

        // write newick tree to a file
        PrintWriter outFile;
        try {
            outFile = new PrintWriter(new FileWriter(taxname + ".tre"));
            outFile.write(tree.getRoot().getNewick(reportBranchLength));
            outFile.write(";\n");
            outFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This is the preorder function for constructing a newick tree based only on graph decisions. If the resulting decisions do not contain all of the taxa,
     * either a branch and bound search or exhaustive pair search will attempt to find a better (more complete) scenario.
     * 
     * TODO: add more criteria
     * 
     * @param curnode
     * @param parent
     * @param relcoming
     * @param nodename
     * @param bandb
     * @return
     */
    // TODO: need to be able to ignore taxonomy
    private JadeNode preorderConstructNewickTieBreakerDEFAULT(Node curnode, JadeNode parent, Relationship relcoming, String altName, boolean useTaxonomy,
            boolean useBranchAndBound) {

        // System.out.println("starting +"+curnode.getId());
        boolean ret = false;
        JadeNode newnode = new JadeNode();

        if (curnode.hasProperty("name")) {
            newnode.setName((String) curnode.getProperty("name"));
            newnode.setName(GeneralUtils.cleanName(newnode.getName()));
        }

        if (newnode.getName().length() == 0) {
            newnode.setName(altName);
        }

        if (parent == null) {
            ret = true;
        } else {
            parent.addChild(newnode);
            if (relcoming.hasProperty("branch_length")) {
                newnode.setBL((Double) relcoming.getProperty("branch_length"));
            }
        }

        // decide which nodes to continue on
        HashSet<Long> testnodes = new HashSet<Long>();
        HashSet<Long> originaltest = new HashSet<Long>();
        HashMap<Long, Integer> testnodes_scores = new HashMap<Long, Integer>();
        HashMap<Long, HashSet<Long>> storedmrcas = new HashMap<Long, HashSet<Long>>();
        HashMap<Long, Relationship> bestrelrel = new HashMap<Long, Relationship>();

        for (Relationship rel : curnode.getRelationships(Direction.INCOMING, RelTypes.STREECHILDOF)) {

            if (useTaxonomy == false) {
                if (rel.getProperty("source").equals("taxonomy"))
                    continue;
            }

            Long tnd = rel.getStartNode().getId();
            testnodes.add(tnd);
            originaltest.add(tnd);
            if (testnodes_scores.containsKey(tnd) == false) {
                testnodes_scores.put(tnd, 0);
                HashSet<Long> mrcas1 = new HashSet<Long>();
                long[] dbnodei = (long[]) graphDb.getNodeById(tnd).getProperty("mrca");
                for (long temp : dbnodei) {
                    mrcas1.add(temp);
                }
                storedmrcas.put(tnd, mrcas1);
                // TODO: make this relationship more meaningful
                bestrelrel.put(tnd, rel);
            }
            // trying to get scores directly from the node
            testnodes_scores.put(tnd, testnodes_scores.get(tnd) + 1);
        }

        if (testnodes.size() == 0) {
            return null;
        }

        HashSet<Long> deletenodes = new HashSet<Long>();
        for (Long tn : testnodes) {
            if (deletenodes.contains(tn))
                continue;
            HashSet<Long> mrcas1 = storedmrcas.get(tn);
            int compint1 = testnodes_scores.get(tn);
            for (Long tn2 : testnodes) {
                if (tn2 == tn || deletenodes.contains(tn2))
                    continue;
                HashSet<Long> mrcas2 = storedmrcas.get(tn2);
                int compint2 = testnodes_scores.get(tn2);
                // test intersection
                int sizeb = mrcas1.size();
                HashSet<Long> cmrcas1 = new HashSet<Long>(mrcas1);
                cmrcas1.removeAll(mrcas2);
                if ((sizeb - cmrcas1.size()) > 0) {
                    if (compint2 < compint1) {
                        deletenodes.add(tn2);
                    } else if (compint2 == compint1) {
                        if (mrcas2.size() < mrcas1.size()) {
                            deletenodes.add(tn2);
                        } else {
                            deletenodes.add(tn);
                            break;
                        }
                    } else {
                        deletenodes.add(tn);
                        break;
                    }
                }
            }
        }

        testnodes.removeAll(deletenodes);
        if (testnodes.size() == 0) {
            return null;
        }

        int totalmrcas = ((long[]) curnode.getProperty("mrca")).length;
        int total = 0;
        for (Long nd : testnodes) {
            total += storedmrcas.get(nd).size();
        }

        /*
         * If the totalmrcas is not complete then we search for a more complete one despite lower scores
         * 
         * This is a specific knapsack problem
         */
        if (totalmrcas - total != 0) {
            System.out.println("more extensive search needed, some tips may be missing");
            System.out.println("before: " + totalmrcas + " " + total);
            HashSet<Long> temptestnodes = new HashSet<Long>();
            int ntotal = 0;
            // trying the branch and bound
            if (useBranchAndBound) {
                System.out.println("Branch and Bound");
                TreeMakingBandB tmbb = new TreeMakingBandB();
                ArrayList<Long> testnodesal = new ArrayList<Long>(originaltest);
                HashMap<Integer, HashSet<Long>> inS = new HashMap<Integer, HashSet<Long>>();
                for (int i = 0; i < testnodesal.size(); i++) {
                    inS.put(i, storedmrcas.get(testnodesal.get(i)));
                }
                HashSet<Integer> testindices = tmbb.runSearch(totalmrcas, inS);
                for (Integer i : testindices) {
                    temptestnodes.add(testnodesal.get(i));
                }
            } else {
                System.out.println("Exhaustive Pairs");
                TreeMakingExhaustivePairs tmep = new TreeMakingExhaustivePairs();
                ArrayList<Long> testnodesal = new ArrayList<Long>(originaltest);
                ArrayList<Integer> testindices = tmep.calculateExhaustivePairs(testnodesal, storedmrcas);
                for (Integer i : testindices) {
                    temptestnodes.add(testnodesal.get(i));
                }
            }
            for (Long nd : temptestnodes) {
                ntotal += storedmrcas.get(nd).size();
            }
            if (ntotal > total) {
                testnodes = new HashSet<Long>(temptestnodes);
                total = ntotal;
            }
            System.out.println("after: " + totalmrcas + " " + total + " " + testnodes.size());
        }

        // continue on the nodes
        for (Long nd : testnodes) {

            // provide an alternative name to be applied to unnamed nodes
            String _altName = ""; // String.valueOf(testnodes_scores.get(nd));

            // go to next node
            preorderConstructNewickTieBreakerDEFAULT(graphDb.getNodeById(nd), newnode, bestrelrel.get(nd), _altName, useTaxonomy, useBranchAndBound);
        }

        if (ret == true) {
            return newnode;
        }

        return null;
    }

    /**
     * Used to add missing external nodes from a JadeNode tree that has just been built by `constructNewickTieBreakerSOURCE`.
     * Adds nodes based on taxnomy; identifies all the external descendants of each taxon that are absent from the tree, and adds them
     * at the base of the MRCA of all the external descendants of that taxon that are present in the tree.
     * 
     * @param inputRoot -
     *        the tree to be added
     * @param taxRootName
     *        the name of the inclusive taxon to add missing descendants of (will include all descendant taxa)
     * @return JadeNode tree (the root of a JadeNode tree) with missing children added
     */
    private void addMissingChildrenRelaxed(JadeTree tree, String taxRootName) {

        // will hold nodes from the taxonomy to check
        LinkedList<Node> taxNodes = new LinkedList<Node>();

        // walk taxonomy and save nodes in postorder
        Node taxRoot = findGraphNodeByName(taxRootName);
        TraversalDescription TAXCHILDOF_TRAVERSAL = Traversal.description().relationships(RelTypes.TAXCHILDOF, Direction.INCOMING);
        for (Node taxChild : TAXCHILDOF_TRAVERSAL.breadthFirst().traverse(taxRoot).nodes()) {
            taxNodes.add(0, taxChild);
        }

        // walk taxa from tips down
        for (Node taxNode : taxNodes) {
            if (taxNode.hasRelationship(Direction.INCOMING, RelTypes.TAXCHILDOF) == false) {
                // only consider taxa that are not tips
                continue;
            }

//            System.out.println(taxNode.getProperty("name"));
            
            // will record descendant taxa not in the tree so we can add them
            HashMap<String, Long> taxaToAdd = new HashMap<String, Long>();

            // will just record the names that are already in the tree
            ArrayList<String> namesInTree = new ArrayList<String>();
            
            // get all external descendants of this taxon, remember if they're in the tree or not
            for (long cid : (long[]) taxNode.getProperty("mrca")) {
                String name = GeneralUtils.cleanName((String) graphDb.getNodeById(cid).getProperty("name"));

                // `knownIdsInTree` should already have been started during original construction of `tree`
                if (knownIdsInTree.contains(cid)) {
                    namesInTree.add(name);
//                    System.out.println("name in tree: " + name);

                } else {
                    taxaToAdd.put(name, cid);
                }
            }
            
            // find the mrca of the names in the tree
            JadeNode mrca = null;
            if (namesInTree.size() > 0) {
                mrca = tree.getMRCAAnyDepthDescendants(namesInTree);
//                System.out.println("found mrca: " + mrca);

            } else {
//                System.out.println("zero names in tree!");
                continue;
            }
            
            if (namesInTree.size() == 1) {
                // make a new parent if the mrca is a tip
                // TODO: what to do about the branch length of the single exemplar tip for this mrca?
                JadeNode newMRCA = new JadeNode();
                JadeNode parentOfNewMRCA = mrca.getParent();
                parentOfNewMRCA.addChild(newMRCA);
                parentOfNewMRCA.removeChild(mrca);
                newMRCA.addChild(mrca);
                mrca = newMRCA;
            }
            
            // add any children that are not already in tree
            for (Entry<String, Long> entry: taxaToAdd.entrySet()) {

                String taxName = entry.getKey();
                Long taxId = entry.getValue();

//                System.out.println("attempting to add child: " + taxName + " to " + mrca.getName());

                JadeNode newChild = new JadeNode();
                newChild.setName(taxName);

                mrca.addChild(newChild);
                
                knownIdsInTree.add(taxId);
            }
            
            // update the JadeTree, otherwise we won't always find newly added taxa when we look for mrcas
            tree.processRoot();
        }
    }
    
    
    /**
     * Constructs a newick tree based on the sources. There are currently no other criteria considered in this particular function.
     * 
     * @param taxName
     * @param sources
     */
    public void constructNewickTieBreakerSOURCE(String taxName, String[] sources, boolean useTaxonomy, boolean useBranchAndBound) {

        // note, currently the branch and bound feature is not supported when preferring source
        
        Node firstNode = findGraphNodeByName(taxName);
        if (firstNode == null) {
            System.out.println("name not found");
            return;
        }
        
        if (sinkLostChildrenStrict || sinkLostChildrenRelaxed) {
            knownIdsInTree = new HashSet<Long>();
        }
        
        JadeNode root = preorderConstructNewickTieBreakerSOURCE(firstNode, null, sources, null, useTaxonomy);
        JadeTree tree = new JadeTree(root);
        PrintWriter outFile;

        if (sinkLostChildrenRelaxed) {
            addMissingChildrenRelaxed(tree, taxName);
        }
        
        try {
            outFile = new PrintWriter(new FileWriter(taxName + ".tre"));
            outFile.write(tree.getRoot().getNewick(true));
            outFile.write(";\n");
            outFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This function is the preorder function for constructing newick trees given a list of sources.
     * 
     * This ONLY considers the sources. There should be a general function that considers some sort of hierarchy of decisions
     * 
     * TODO: this will need to eventually incorporate more functionality for deciding based on more than the sources
     * 
     * @param curnode
     * @param parent
     * @param sources
     * @param relcoming
     * @return
     */
    private JadeNode preorderConstructNewickTieBreakerSOURCE(Node curnode, JadeNode parent, String[] sources, Relationship relcoming, boolean useTaxonomy /* , boolean useBranchAndBound, */) {

        boolean ret = false;
        JadeNode newNode = new JadeNode();

        if (curnode.hasProperty("name")) {
            newNode.setName((String) curnode.getProperty("name"));
            newNode.setName(GeneralUtils.cleanName(newNode.getName()));
        }

        if (parent == null) {
            // this is the root of this tree, so set the flag to return it
            ret = true;

        } else {
            // add this node as a child of the passed-in parent
            parent.addChild(newNode);

            if (sinkLostChildrenStrict || sinkLostChildrenRelaxed) {
                knownIdsInTree.add(curnode.getId());
            }

            if (relcoming.hasProperty("branch_length")) {
                newNode.setBL((Double) relcoming.getProperty("branch_length"));
            }
        }

        // variables to store information used to make decisions about best paths
        HashMap<Long, HashSet<Long>> candNodeDescendantIdsMap = new HashMap<Long, HashSet<Long>>();
        HashMap<Long, Integer> candNodeRankingMap = new HashMap<Long, Integer>();
        HashMap<Long, Relationship> candNodeRelationshipMap = new HashMap<Long, Relationship>();
        HashSet<Long> candidateNodeIds = new HashSet<Long>();

        // for every candidate (incoming childof) relationship
        for (Relationship candRel : curnode.getRelationships(Direction.INCOMING, RelTypes.STREECHILDOF)) {

            if (useTaxonomy == false) {
                // skip taxonomy relationships if specified
                if (candRel.getProperty("source").equals("taxonomy"))
                    continue;
            }

            // candidate child node id
            Long cid = candRel.getStartNode().getId();

            // if we haven't seen this node yet
            if (candidateNodeIds.contains(cid) == false) {
                candidateNodeIds.add(cid);

                // save this candidate's mrca descendants
                HashSet<Long> descIds = new HashSet<Long>();
                for (long descId : (long[]) graphDb.getNodeById(cid).getProperty("mrca"))
                    descIds.add(descId);
                candNodeDescendantIdsMap.put(cid, descIds);

                // save the current candidate relationship we used to reach this node
                candNodeRelationshipMap.put(cid, candRel);

                // the first time we see a node, set its ranking to the lowest possible rank
                candNodeRankingMap.put(cid, sources.length);
            }

            // update the ranking for this node, based on the current source. if already ranked at the top (rank 0) then don't bother
            if (candNodeRankingMap.get(cid) != 0) {
                String sourceName = (String) candRel.getProperty("source");
                for (int i = 0; i < sources.length; i++) {

                    // update if the rank of the sourcetree for the current candidate relationship is better than the last saved rank
                    if (sourceName.compareTo(sources[i]) == 0) {
                        if (candNodeRankingMap.get(cid) > i) {
                            candNodeRankingMap.put(cid, i);
                            candNodeRelationshipMap.put(cid, candRel);
                        }
                        break;
                    }
                }
            }
        }

        // found no candidate relationships; this is an external node so no decisions to be made
        if (candidateNodeIds.size() == 0) {
            return null;
        }

        HashSet<Long> putativeLostChildIds = null;
        if (sinkLostChildrenStrict) {
            // will be used to record children that may be lost during conflict tie-breaking
            putativeLostChildIds = new HashSet<Long>();
        }

        // compare all candidate nodes to choose which to remove/keep
        HashSet<Long> nodesToExclude = new HashSet<Long>();
        for (Long cid_i : candidateNodeIds) {
            if (nodesToExclude.contains(cid_i))
                continue;
            HashSet<Long> descIds_i = candNodeDescendantIdsMap.get(cid_i);

            for (Long cid_j : candidateNodeIds) {
                if (cid_j == cid_i || nodesToExclude.contains(cid_j))
                    continue;
                HashSet<Long> descIds_j = candNodeDescendantIdsMap.get(cid_j);

                // get difference of descendant sets for candidates i and j
                HashSet<Long> descIds_iMinusj = new HashSet<Long>(descIds_i);
                descIds_iMinusj.removeAll(descIds_j);

                // candidates i and j are in conflict if they overlap
                if ((descIds_i.size() - descIds_iMinusj.size()) > 0) {

                    // use source ranking to break ties
                    if (candNodeRankingMap.get(cid_i) < candNodeRankingMap.get(cid_j)) {
                        nodesToExclude.add(cid_j);

                        if (sinkLostChildrenStrict) {
                            // record eventual descendants that may be lost by this choice
                            putativeLostChildIds.addAll(descIds_j);
                        }

                    } else {
                        nodesToExclude.add(cid_i);

                        if (sinkLostChildrenStrict) {
                            // record eventual descendants that may be lost by this choice
                            putativeLostChildIds.addAll(descIds_i);
                        } else {
                            // if we're not recording lost children, move on? (@sas, why do we break here?)
                            break;
                        }
                    }
                }
            }
        }

        // remove all nodes that lost the tie-breaking
        candidateNodeIds.removeAll(nodesToExclude);
        if (candidateNodeIds.size() == 0) {
            return null;
        }

        if (sinkLostChildrenStrict) {

            // get ids of all descendants of accepted candidates
            HashSet<Long> impliedDescendantIds = new HashSet<Long>();
            for (Long cid : candidateNodeIds) {
                for (long descId : (long[]) graphDb.getNodeById(cid).getProperty("mrca"))
                    impliedDescendantIds.add(descId);
            }

            for (Long childId : putativeLostChildIds) {

                // only add lost children if they aren't already contained by approved candidate nodes or elsewhere in the tree
                if (!impliedDescendantIds.contains(childId) && !knownIdsInTree.contains(childId)) {

                    // add as a child of current node
                    JadeNode addlChild = new JadeNode();
                    Node graphNodeForChild = graphDb.getNodeById(childId);
                    if (graphNodeForChild.hasProperty("name")) {
                        addlChild.setName((String) graphNodeForChild.getProperty("name"));
                        addlChild.setName(GeneralUtils.cleanName(addlChild.getName()));// + "___" + String.valueOf(curnode.getId()));
                    }
                    newNode.addChild(addlChild);

                    // record id so we don't add it again
                    knownIdsInTree.add(childId);
                }
            }
        }

        // ---------------------- MAY WANT TO ADD BRANCH AND BOUND OPTIMIZATION --------------------------

        // continue recursion
        for (Long cid : candidateNodeIds) {
            preorderConstructNewickTieBreakerSOURCE(graphDb.getNodeById(cid), newNode, sources, candNodeRelationshipMap.get(cid), useTaxonomy /*, useBranchAndBound */);
        }

        // hit root of subtree, return it
        if (ret == true) {
            return newNode;
        }

        // @sas, what is this case for?
        return null;
    }

    /**
     * TODO: This doesn't not yet include the sources differences. It is just a demonstration of how to use the evaluator to construct a pruned tree TODO: Add
     * ability to work with conflicts
     * 
     * @param innodes
     * @param sources
     */
    public void constructNewickTaxaListTieBreaker(HashSet<Long> innodes, String[] sources) {
        Node lifeNode = findGraphNodeByName("life");
        if (lifeNode == null) {
            System.out.println("name not found");
            return;
        }
        tle.setTaxaList(innodes);
        TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
                .relationships(RelTypes.MRCACHILDOF, Direction.INCOMING);
        HashSet<Long> visited = new HashSet<Long>();
        Long firstparent = null;
        HashMap<Long, JadeNode> nodejademap = new HashMap<Long, JadeNode>();
        HashMap<Long, Integer> childcount = new HashMap<Long, Integer>();
        System.out.println("traversing");
        for (Node friendnode : MRCACHILDOF_TRAVERSAL.breadthFirst().evaluator(tle).traverse(lifeNode).nodes()) {
            Long parent = null;
            for (Relationship tn : friendnode.getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
                if (visited.contains(tn.getEndNode().getId())) {
                    parent = tn.getEndNode().getId();
                    break;
                }
            }
            if (parent == null && friendnode.getId() != lifeNode.getId()) {
                System.out.println("disconnected tree");
                return;
            }
            JadeNode newnode = new JadeNode();
            if (friendnode.getId() != lifeNode.getId()) {
                JadeNode jparent = nodejademap.get(parent);
                jparent.addChild(newnode);
                Integer value = childcount.get(parent);
                value += 1;
                if (value > 1) {
                    if (firstparent == null)
                        firstparent = parent;
                }
                childcount.put(parent, value);
            }
            if (friendnode.hasProperty("name")) {
                newnode.setName((String) friendnode.getProperty("name"));
                newnode.setName(newnode.getName().replace("(", "_").replace(")", "_").replace(" ", "_").replace(":", "_"));
            }
            nodejademap.put(friendnode.getId(), newnode);
            visited.add(friendnode.getId());
            childcount.put(friendnode.getId(), 0);
        }
        System.out.println("done traversing");
        // could prune this at the first split
        JadeTree tree = new JadeTree(nodejademap.get(firstparent));
        PrintWriter outFile;
        try {
            outFile = new PrintWriter(new FileWriter("pruned.tre"));
            outFile.write(tree.getRoot().getNewick(true));
            outFile.write(";\n");
            outFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the list of sources that have been loaded in the graph
     * @returns array of strings that are the values of the "source" property of nodes stored in sourceMetaIndex
     */
    public ArrayList<String> getSourceList() {
        IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
        ArrayList<String> sourceArrayList = new ArrayList<String>(hits.size());
        while (hits.hasNext()) {
            Node n = hits.next();
            sourceArrayList.add((String) n.getProperty("source"));
        }
        return sourceArrayList;
    }

    /**
     * This will recreate the original source from the graph. At this point this is just a demonstration that it can be done.
     * 
     * @param sourcename
     *            the name of the source
     */
    public void reconstructSource(String sourcename) {
        boolean printlengths = false;
        IndexHits<Node> hits = sourceRootIndex.get("rootnode", sourcename);
        IndexHits<Relationship> hitsr = sourceRelIndex.get("source", sourcename);
        // really only need one
        HashMap<Node, JadeNode> jadenode_map = new HashMap<Node, JadeNode>();
        JadeNode root = new JadeNode();
        Node rootnode = hits.next();
        jadenode_map.put(rootnode, root);
        if (rootnode.hasProperty("name"))
            root.setName((String) rootnode.getProperty("name"));
        hits.close();
        // System.out.println(hitsr.size());
        HashMap<Node, ArrayList<Relationship>> startnode_rel_map = new HashMap<Node, ArrayList<Relationship>>();
        HashMap<Node, ArrayList<Relationship>> endnode_rel_map = new HashMap<Node, ArrayList<Relationship>>();
        while (hitsr.hasNext()) {
            Relationship trel = hitsr.next();
            if (startnode_rel_map.containsKey(trel.getStartNode()) == false) {
                ArrayList<Relationship> trels = new ArrayList<Relationship>();
                startnode_rel_map.put(trel.getStartNode(), trels);
            }
            if (endnode_rel_map.containsKey(trel.getEndNode()) == false) {
                ArrayList<Relationship> trels = new ArrayList<Relationship>();
                endnode_rel_map.put(trel.getEndNode(), trels);
            }
            // System.out.println(trel.getStartNode()+" "+trel.getEndNode());
            startnode_rel_map.get(trel.getStartNode()).add(trel);
            endnode_rel_map.get(trel.getEndNode()).add(trel);
        }
        hitsr.close();
        Stack<Node> treestack = new Stack<Node>();
        treestack.push(rootnode);
        HashSet<Node> ignoreCycles = new HashSet<Node>();
        while (treestack.isEmpty() == false) {
            Node tnode = treestack.pop();
            // if(tnode.hasRelationship(Direction.OUTGOING, RelTypes.ISCALLED)){
            // System.out.println(tnode + " "+tnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
            // }else{
            // System.out.println(tnode);
            // }
            // TODO: move down one more node
            if (endnode_rel_map.containsKey(tnode)) {
                for (int i = 0; i < endnode_rel_map.get(tnode).size(); i++) {
                    if (endnode_rel_map.containsKey(endnode_rel_map.get(tnode).get(i).getStartNode())) {
                        ArrayList<Relationship> rels = endnode_rel_map.get(endnode_rel_map.get(tnode).get(i).getStartNode());
                        for (int j = 0; j < rels.size(); j++) {
                            if (rels.get(j).hasProperty("licas")) {
                                long[] licas = (long[]) rels.get(j).getProperty("licas");
                                if (licas.length > 1) {
                                    for (int k = 1; k < licas.length; k++) {
                                        ignoreCycles.add(graphDb.getNodeById(licas[k]));
                                        // System.out.println("ignoring: "+licas[k]);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (endnode_rel_map.containsKey(tnode)) {
                for (int i = 0; i < endnode_rel_map.get(tnode).size(); i++) {
                    if (ignoreCycles.contains(endnode_rel_map.get(tnode).get(i).getStartNode()) == false) {
                        Node tnodechild = endnode_rel_map.get(tnode).get(i).getStartNode();
                        treestack.push(tnodechild);
                        JadeNode tchild = new JadeNode();
                        if (tnodechild.hasProperty("name"))
                            tchild.setName((String) tnodechild.getProperty("name"));
                        if (endnode_rel_map.get(tnode).get(i).hasProperty("branch_length")) {
                            printlengths = true;
                            tchild.setBL((Double) endnode_rel_map.get(tnode).get(i).getProperty("branch_length"));
                        }
                        jadenode_map.get(tnode).addChild(tchild);
                        jadenode_map.put(tnodechild, tchild);
                        // System.out.println("pushing: "+endnode_rel_map.get(tnode).get(i).getStartNode());
                    }
                }
            }
        }
        // print the newick string
        JadeTree tree = new JadeTree(root);
        System.out.println(tree.getRoot().getNewick(printlengths) + ";");
    }
}
