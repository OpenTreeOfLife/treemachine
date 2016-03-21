package opentree;

import jade.tree.deprecated.JadeNode;
import jade.tree.deprecated.JadeTree;
import jade.tree.deprecated.TreeReader;
import org.opentree.utils.GeneralUtils;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import opentree.constants.NodeProperty;
import opentree.constants.RelProperty;
import opentree.constants.RelType;
import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.TaxonNotFoundException;
import org.opentree.exceptions.TreeNotFoundException;
import opentree.DraftTreePathExpander;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;

public class GraphExplorer extends GraphBase {
    //static Logger _LOG = Logger.getLogger(GraphExplorer.class);
    //private SpeciesEvaluator se;
    private ChildNumberEvaluator cne;
    //private TaxaListEvaluator tle;
    //private boolean sinkLostChildren;
    private HashSet<Long> knownIdsInTree;
    public boolean verbose = true; // used for controlling logging for plugins
    public boolean newickLabelsAreIDs = false;
    public boolean newickLabelInternalNodes = true;
    
    // turn off System.out calls, as they get logged in plugin calls
    public void setQuiet() {
        verbose = false;
    }
    
    /*
    public void setNewickLabelsToIDs() {
        newickLabelsAreIDs = true;
    }
    */
    
    public void turnOffInternalNewickLabels() {
        newickLabelInternalNodes = false;
    }
    
    // only used in main
    public GraphExplorer(String graphname) {
        super(graphname);
        finishInitialization();
    }
    
    // used in plugins
    public GraphExplorer(GraphDatabaseService gdb) {
        super(gdb);
        finishInitialization();
    }
    
    /*
    public GraphExplorer(GraphDatabaseAgent gdb) {
        super(gdb);
        setDefaultParameters();
        finishInitialization();
    }
    */
    
    private void finishInitialization() {
        cne = new ChildNumberEvaluator();
        cne.setChildThreshold(100);
        //se = new SpeciesEvaluator();
        //tle = new TaxaListEvaluator();
    }
    
    /*
    private void setDefaultParameters() {
        sinkLostChildren = false;
    }
    */
    
    /* -------------------- DEPRECATED info: collapsing children based on graph decisions ----------------------- */
    
    //// THE STRICT SETTING HAS BEEN DEPRECATED. Sinking lost children is now done using the taxonomy.
    
    /* STRICT: Activating the strict option will assign external descendants that are lost during conflict tie-breaking (i.e. "lost children") as children of
     * the node where the tie-breaking event occurred (i.e. they are "sunk" to that level in the tree). This will result in very conservative (in this case
     * meaning deep and imprecise) assignment of "lost child" taxa.
     */
    

    /* --------------------- end info: collapsing children based on taxonomy ---------------------- */
    
    
    /** 
     * Set the boolean whether or not to re-insert leaves into synthetic trees which were lost based on decisions made during synthesis.
     * If this is true, then taxonomy will be used to inform taxon placement in synthetic trees when no non-conflicting phylogenetic
     * information exists to do so. This results in more taxon-inclusive synthesis trees at the potential expense of correct taxon placement.
     * 
     * @param sinkLostChildren
     */
    /*
    public void setSinkLostChildren(boolean sinkLostChildren) {
        this.sinkLostChildren = sinkLostChildren;
        if (sinkLostChildren) {
            this.knownIdsInTree = new HashSet<Long>();
        }
    }
    */
    
    /**
     * Just check the status of the sinkLostChildren option.
     * 
     * @return sinkLostChildren
     */
    /*
    public boolean sinkLostChildrenActive() {
        return sinkLostChildren;
    }
    */
    
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
     * Internal method to get an array of arrays representing the rootward paths of a given set of nodes,
     * used to calculate mrca and associated procedures.
     * 
     * @param tips
     * @return
     */
    /*
    private Map<Node, ArrayList<Node>> getTreeTipRootPathMap(Iterable<Node> tips) {
        return getTreeTipRootPathMap(tips, null);
    }
    */
    
    private Map<Node, ArrayList<Node>> getTreeTipRootPathTaxonomyMap(Iterable<Node> tips) {
        return getTreeTipRootPathTaxonomyMap(tips, null);
    }
    
    
    /**
     * Internal method to get an array of arrays representing the rootward paths of a given set of nodes,
     * used to calculate mrca and associated procedures. stopNode allows to set an mrca beyond which the traversal
     * won't record the path.
     * Unlike getTreeTipRootPathMap, this traverses taxonomy
     * 
     * @param tips
     * @param stopNode
     * @return
     */
    // verbose is used because plugins call this code
    private Map<Node, ArrayList<Node>> getTreeTipRootPathTaxonomyMap(Iterable<Node> tips, Node stopNode) throws UnsupportedOperationException {
        if (stopNode != null) {
            if (verbose) System.out.println("setting stop node to " + stopNode);
        }
        HashMap<Node, ArrayList<Node>> treeTipRootPathMap = new HashMap<Node, ArrayList<Node>>();
        HashSet<Node> removeTips = new HashSet<Node>();
        // populate the tip hash with the paths to the root of the tree
        for (Node curTip : tips) {
            ArrayList<Node> graphPathToRoot = new ArrayList<Node>();
            for (Node m : Traversal.description().relationships(RelType.TAXCHILDOF, Direction.OUTGOING).traverse(curTip).nodes()) {
                if (stopNode != null && m.equals(stopNode)) { // stop recording paths at the stop node (allows us to specify an mrca beyond which we don't go)
                    //                    System.out.println("found stop node " + stopNode);
                    break;
                }
                graphPathToRoot.add(0, m);
                if (m != curTip) {
                    removeTips.add(m);
                }
            }
            if (graphPathToRoot.size() < 1) {
                String ret = "The node " + curTip + " does not seem to be in the draft tree.";
                ret += "; `node_id` is: " + curTip.getProperty("ot_node_id");
                throw new UnsupportedOperationException(ret);
            }
            treeTipRootPathMap.put(curTip, graphPathToRoot);
        }
        for (Node curtip: tips) {
            if (removeTips.contains(curtip)) {
                treeTipRootPathMap.remove(curtip);
            }
        }
        return treeTipRootPathMap;
    }
    
    /*
    public Node getTaxonomyMRCA(Iterable<Node> nodeset) {
        Node mrca = null;
        ArrayList<Node> holder = null;
        int index = 10000000;
        
        for (Node curNode : nodeset) {
            if (holder != null) {
                for (Node m : Traversal.description().relationships(RelType.TAXCHILDOF, Direction.OUTGOING).traverse(curNode).nodes()) {
                    int foo = holder.indexOf(m);
                    if (foo != -1) { // first match. 
                        if (foo < index) {
                            index = foo; // if hit is more rootward than previous hit, record that.
                        }
                        break; // subsequent matches are not informative. bail.
                    }
                }
            } else { // first pass. get full path to root. ideally we would get the shortest path...
                ArrayList<Node> graphPathToRoot = new ArrayList<Node>();
                for (Node m : Traversal.description().relationships(RelType.TAXCHILDOF, Direction.OUTGOING).traverse(curNode).nodes()) {
                    graphPathToRoot.add(0, m);
                }
                holder = graphPathToRoot;
            }
        }
        if (!holder.isEmpty()) {
            if (index == 10000000) { // only a single node passed in, but it *is* in the taxonomy tree
                mrca = holder.get(holder.size() - 1);
            } else {
                mrca = holder.get(index);
            }
        }
        return mrca;
    }
    */
    
    
    /**
     * Get the MRCA of one or more nodes (interpreted as tips in some theoretical tree) according to the
     * topology of the draft tree. If only one tip is provided, then the tip itself is returned. If taxonomy
     * is true it will only traverse taxonomy relationships
     * @param tips
     * @return
     */
    /*
    public Node getDraftTreeMRCAForNodes(Iterable<Node> tips, boolean taxonomy) {
        //Map<Node, ArrayList<Node>> treeTipRootPathMap = null;
        if (taxonomy == false) {
            //treeTipRootPathMap = getTreeTipRootPathMap(tips);
            Node mrca = getDraftTreeMRCA(tips); // redirect to new method
            return mrca;
        } else {
            //treeTipRootPathMap = getTreeTipRootPathTaxonomyMap(tips);
            //Node mrca = getTaxonomyMRCA(tips); // redirect to new method
            return null;
        }
    }
    */
    
    
    
    
    /*
    public JadeNode extractTaxonomySubtreeForTipNodes(Iterable<Node> tips) {
        Node mrca = getDraftTreeMRCAForNodes(tips, true);
        System.out.println("identified mrca " + mrca);
        HashMap<Node,JadeNode> mapnodes = new HashMap<Node,JadeNode>();
        HashMap<JadeNode,Node> mapjnodes = new HashMap<JadeNode,Node>();
        JadeNode jn = new JadeNode();
        jn.setName((String)mrca.getProperty(NodeProperty.TAX_UID.propertyName));
        mapnodes.put(mrca, jn);
        HashSet<Node> givennodes = new HashSet<Node>();
        for (Node curtip: tips) {
            Node lastnode = null;
            for (Node m : Traversal.description().relationships(RelType.TAXCHILDOF, Direction.OUTGOING).traverse(curtip).nodes()) {
                //System.out.println(m + " " + (String)m.getProperty(NodeProperty.TAX_UID.propertyName) + " " + (String)m.getProperty("name"));
                if (mapnodes.containsKey(m)) {
                    if (lastnode != null) {
                        mapnodes.get(m).addChild(mapnodes.get(lastnode));
                        mapnodes.get(lastnode).setParent(mapnodes.get(m));
                        break;
                    } else {
                        break;
                    }
                } else {
                    JadeNode tjn = new JadeNode();
                    tjn.setName((String)m.getProperty(NodeProperty.TAX_UID.propertyName));
                    mapnodes.put(m, tjn);
                    mapjnodes.put(tjn,m);
                    if (lastnode != null) {
                        mapnodes.get(m).addChild(mapnodes.get(lastnode));
                        mapnodes.get(lastnode).setParent(mapnodes.get(m));
                    }
                    lastnode = m;
                }
            }
            givennodes.add(curtip);
        }
        boolean going = true;
        while (going == true) {
            boolean found = false;
            for (JadeNode tjn: jn.getDescendantLeaves()) {
                JadeNode p = tjn;
                while (p != jn) {
                    //knee and not in the set of taxa
                    if (p.getChildCount() == 1 && givennodes.contains(mapjnodes.get(p)) == false) {
                        JadeNode pp = p.getParent();
                        JadeNode ch = p.getChild(0);
                        pp.removeChild(p);
                        p.removeChild(ch);
                        pp.addChild(ch);
                        ch.setParent(pp);
                        break;
                    } else {
                        p = p.getParent();
                    }
                }
            }
            if (found == false) {
                going = false;
                break;
            }
        }
        System.out.println("\n" + jn.getNewick(false) + ";\n");
        
        return jn;
    }
    */
    
    /**
     * Get a subtree out of the draft tree topology for the indicated tips. 
     * @param tips
     * @return draftSubtree
     */
    // TODO: detect if some nodes are ancestors of other nodes
    // verbose is used because plugins call this code
    /*
    public JadeNode extractDraftSubtreeForTipNodes(List<Node> tips) {
        
        if (tips.size() < 2) {
            throw new UnsupportedOperationException("Cannot extract a tree with < 2 tips.");
        }
        
        // get the mrca. should check if the mrca is equal to one of the query nodes
        Node mrca = getDraftTreeMRCA(tips);
        
        if (verbose) System.out.println("identified mrca " + mrca);
        
        // get all the paths from the tips to the mrca
        Map<Node, ArrayList<Node>> treeTipToMRCAPathMap = getTreeTipRootPathMap(tips, mrca);
        
        HashMap<Node, JadeNode> graphNodeTreeNodeMap = new HashMap<>();
        HashMap<JadeNode, Node> treeNodeGraphNodeMap = new HashMap<>();
        HashMap<JadeNode, LinkedList<Node>> treeTipGraphMRCADescendantsMap = new HashMap<>();
        
        for (Node tipNode : treeTipToMRCAPathMap.keySet()) {
            
            JadeNode treeTip = new JadeNode();
            treeTip.assocObject("graphNode", tipNode);
            treeTip.setName(getOttName(tipNode));
            
            graphNodeTreeNodeMap.put(tipNode, treeTip);
            treeNodeGraphNodeMap.put(treeTip, tipNode);
            
            // add this node's MRCA descendants to the hashmap
            LinkedList<Node> tipDescendants = new LinkedList<Node>();
            for (long nid : (long[]) tipNode.getProperty("mrca")) {
                tipDescendants.add(graphDb.getNodeById(nid));
            }
            treeTipGraphMRCADescendantsMap.put(treeTip, tipDescendants);
        }
        
        // initialize containers
        HashMap<JadeNode, LinkedList<JadeNode>> treeNodeTreeTipDescendantsMap = new HashMap<>();
        LinkedList<JadeNode> stack = new LinkedList<>();
        
        // set start conditions (add root to stack)
        JadeNode root = new JadeNode();
        stack.add(root);
        if (verbose) System.out.println("adding root node to stack");
        treeNodeTreeTipDescendantsMap.put(root, new LinkedList<>(graphNodeTreeNodeMap.values()));
        
        while (stack.size() > 0) {
            if (verbose) System.out.println(stack.size() + " nodes in stack");
            //System.out.println("current topology: \n" + root.getNewick(false) + "\n");
            
            JadeNode treeNode = stack.remove(0);
            //System.out.println("processing node from stack named '" + treeNode.getName() + "'");
            
            // get all the children of the tree leaves (which may be deep graph nodes)
            LinkedList<Node> allDescendantGraphTips = new LinkedList<>();
            for (JadeNode treeTip : treeNodeTreeTipDescendantsMap.get(treeNode)) {
                //System.out.println(treeTip.getName());
                allDescendantGraphTips.addAll(treeTipGraphMRCADescendantsMap.get(treeTip));
            }
            
            // get the mrca from the graph and record it
            //    Node graphNode = LicaUtil.getDraftTreeLICA(allDescendantGraphTips);
            Node graphNode = getDraftTreeMRCA(allDescendantGraphTips);
            treeNode.assocObject("graphNode", graphNode);
            if (graphNode.hasProperty("name")) {
                treeNode.setName(getOttName(graphNode));
            }
            
            // testing
            if (verbose) System.out.println("inferred node mapping in graph is " + graphNode + " name = '" + treeNode.getName() + "'");
            
            // make a container to hold mrca tipsets for nodes to be added as children of this tree node
            HashMap<Node, LinkedList<JadeNode>> childNodeTreeTipDescendantsMap = new HashMap<>();
            
            // for each leaf descendant of the current node
            for (JadeNode curDescendantTreeNode : treeNodeTreeTipDescendantsMap.get(treeNode)) {
                // testing
                if (verbose) System.out.println("\tdescendant " + curDescendantTreeNode.getName());
                
                Node curDescendantGraphNode = treeNodeGraphNodeMap.get(curDescendantTreeNode);
                
                // get the deepest remaining ancestor of this leaf, if none remain then use the current node
                Node curDeepestAncestor = null;
                
                if (treeTipToMRCAPathMap.get(curDescendantGraphNode).size() > 0) {
                    // remove this ancestor so we don't see it again
                    curDeepestAncestor = treeTipToMRCAPathMap.get(curDescendantGraphNode).remove(0);
                } else {
                    curDeepestAncestor = (Node) treeNode.getObject("graphNode");
                }
                
                // testing
                /*
                String ancestorName = "";
                if (curDeepestAncestor.hasProperty("name")) {
                    ancestorName = (String) curDeepestAncestor.getProperty("name");
                    System.out.println("\t\tdeepest remaining ancestor: " + ancestorName);
                }
                 
                // make a new entry in the nodes to be added if we haven't seen this ancestor yet
                if (! childNodeTreeTipDescendantsMap.containsKey(curDeepestAncestor)) {
                    //    System.out.println("\t\tthis ancestor is new.");
                    childNodeTreeTipDescendantsMap.put(curDeepestAncestor, new LinkedList<>());
                }
                
                // queue this leaf to be added under the appropriate ancestor
                //System.out.println("\t\tadding " + curDescendantTreeNode.getName() + " to ancestor " + ancestorName);
                childNodeTreeTipDescendantsMap.get(curDeepestAncestor).add(curDescendantTreeNode);
            }
            
            // if this is a knuckle with more than one child
            if (childNodeTreeTipDescendantsMap.size() == 1) {
                Node onlyDescendant = childNodeTreeTipDescendantsMap.keySet().iterator().next();
                if (childNodeTreeTipDescendantsMap.get(onlyDescendant).size() > 1) {
                    if (verbose) System.out.println("\tthe current node has only one child (name='" + treeNode.getName() + "') with multiple descendants. the child will be replace the parent and be reprocessed.");
                    stack.add(0, treeNode);
                    continue;
                }
            }
            
            // for each child node in the set to be added
            for (Entry<Node, LinkedList<JadeNode>> childToAdd : childNodeTreeTipDescendantsMap.entrySet()) {
                
                LinkedList<JadeNode> childTreeTipDescendants = childToAdd.getValue();
                
                //    System.out.println("childTreeTipDescendants");
                //    System.out.println(Arrays.toString(childTreeTipDescendants.toArray()));
                
                // if this is a knuckle with only one descendant, just add that descendant to the current tree node
                if (childTreeTipDescendants.size() == 1) {
                    treeNode.addChild(childTreeTipDescendants.get(0));
                    continue;
                }
                
                // there is more than one descendant, so make a new tree node to be added to cur tree node
                Node childGraphNode = childToAdd.getKey();
                JadeNode childTreeNode = new JadeNode();
                
                //System.out.println("\tmaking a new tree node to be added as a child of tree node named " + treeNode.getName());
                childTreeNode.assocObject("graphNode", childGraphNode);
                if (childGraphNode.hasProperty("name")) {
                    childTreeNode.setName(getOttName(childGraphNode));
                }
                
                // if there are exactly two descendants, add them as children of this child and move on
                if (childTreeTipDescendants.size() == 2) {
                    for (JadeNode treeTip : childTreeTipDescendants) {
                        childTreeNode.addChild(treeTip);
                    }
                    
                    // if there are more than two descendants
                } else {
                    
                    //System.out.println("\t" + treeNode.getName() + " has more than two children, checking if it is a polytomy");
                    //                    System.out.println("treeTipRootPathMap");
                    //                    System.out.println(Arrays.toString(treeTipRootPathMap.keySet().toArray()));
                    
                    Node graphNodeForTip = treeNodeGraphNodeMap.get(childTreeTipDescendants.get(0));
                    
                    // get the *shallowest* ancestor for an arbitrary descendant of this child
                    ArrayList<Node> startNodeAncestors = treeTipToMRCAPathMap.get(graphNodeForTip);
                    Node startShallowestAncestor = null;
                    
                    //    System.out.println("startNodeAncestors");
                    //    System.out.println(Arrays.toString(startNodeAncestors.toArray()));
                    
                    if (startNodeAncestors.size() > 0) {
                        startShallowestAncestor = startNodeAncestors.get(startNodeAncestors.size() - 1);
                    }
                    
                    // if all the descendants have the same *shallowest* ancestor, then this is a polytomy
                    boolean isPolytomy = true;
                    for (JadeNode treeTip : childTreeTipDescendants) {
                        
                        Node graphTip = treeNodeGraphNodeMap.get(treeTip);
                        
                        ArrayList<Node> curAncestors = treeTipToMRCAPathMap.get(graphTip);
                        Node curShallowestAncestor = curAncestors.get(curAncestors.size() - 1);
                        
                        if (! startShallowestAncestor.equals(curShallowestAncestor)) {
                            // if this isn't a polytomy, then we need to resolve it, so add it to the stack
                            //System.out.println("\tThis node is not a polytomy");
                            //System.out.println("\t\tadding node '" + childTreeNode.getName() + "' to the stack");
                            stack.add(0, childTreeNode);
                            treeNodeTreeTipDescendantsMap.put(childTreeNode, childTreeTipDescendants);
                            isPolytomy = false;
                            break;
                        }
                    }
                    
                    // if this child is a polytomy, add all its children
                    if (isPolytomy) {
                        //    System.out.println("\tthis node is a polytomy, all its descendants will be added to the tree");
                        for (JadeNode treeTip : childTreeTipDescendants) {
                            //        System.out.println("adding node " + treeTip.getName() + " to tree");
                            childTreeNode.addChild(treeTip);
                        }
                    }
                }
                treeNode.addChild(childTreeNode);
            }
        }
        
        if (verbose) System.out.println("\n" + root.getNewick(false) + "\n");
        
        return root;
    }
    */
    
    /**
     * Given a taxonomic name, construct a json object of the subgraph of MRCACHILDOF relationships that are rooted at the specified node. Names that appear in
     * the JSON are taken from the corresponding nodes in the taxonomy graph (using the ISCALLED relationships).
     * 
     * @param name
     *    the name of the root node (should be the name in the graphNodeIndex)
     * @throws MultipleHitsException 
     */
    public void constructJSONGraph(String name) throws TaxonNotFoundException, MultipleHitsException {
        Node firstNode = findTaxNodeByName(name);
        if (firstNode == null) {
            System.out.println("name not found");
            return;
        }
        // System.out.println(firstNode.getProperty("name"));
        TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
                .relationships(RelType.MRCACHILDOF, Direction.INCOMING);
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
                if (tnode.hasProperty("name")) {
                    outFile.write("{\"name\":\"" + (tnode.getProperty("name")) + "");
                } else {
                    outFile.write("{\"name\":\"");
                }
                outFile.write("\",\"group\":" + nodenumbers.get(tnode) + "");
                if (i + 1 < count) {
                    outFile.write("},");
                } else {
                    outFile.write("}");
                }
            }
            outFile.write("],\"links\":[");
            String outs = "";
            for (Node tnode : nodenumbers.keySet()) {
                Iterable<Relationship> it = tnode.getRelationships(RelType.MRCACHILDOF, Direction.OUTGOING);
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
                .relationships(RelType.MRCACHILDOF, Direction.INCOMING);
        HashMap<Long, String> id_to_name = new HashMap<Long, String>();
        HashSet<Node> tips = new HashSet<Node>();
        HashMap<Node, HashMap<Node, Integer>> childs_scores = new HashMap<Node, HashMap<Node, Integer>>();
        HashMap<Node, HashMap<Node, Integer>> scores = new HashMap<Node, HashMap<Node, Integer>>();
        HashMap<Node, HashSet<Node>> child_parents_map = new HashMap<Node, HashSet<Node>>();
        HashMap<Node, Integer> node_score = new HashMap<Node, Integer>();
        HashSet<Node> allnodes = new HashSet<Node>();
        for (Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(startnode).nodes()) {
            if (friendnode.hasRelationship(Direction.INCOMING, RelType.MRCACHILDOF) == false) {
                tips.add(friendnode);
            }
            HashMap<Node, Integer> conflicts_count = new HashMap<Node, Integer>();
            child_parents_map.put(friendnode, new HashSet<Node>());
            int count = 0;
            for (Relationship rel : friendnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
                
                // TODO: I don't believe that "ottol" exists anywhere anymore
                if (rel.getProperty("source").equals("taxonomy") == true || rel.getProperty("source").equals("ottol") == true) {
                    continue;
                }
                if (conflicts_count.containsKey(rel.getEndNode()) == false) {
                    conflicts_count.put(rel.getEndNode(), 0);
                }
                Integer tint = conflicts_count.get(rel.getEndNode()) + 1;
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
                    if (id_to_name.containsKey(mrcas[i]) == false) {
                        id_to_name.put((Long) mrcas[i], (String) graphDb.getNodeById(mrcas[i]).getProperty("name"));
                    }
                    System.out.print(id_to_name.get(mrcas[i]) + " ");
                    mrname += id_to_name.get(mrcas[i]) + " ";
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
                    if (id_to_name.containsKey(mrcas[i]) == false) {
                        id_to_name.put((Long) mrcas[i], (String) graphDb.getNodeById(mrcas[i]).getProperty("name"));
                    }
                    System.out.print(id_to_name.get(mrcas[i]) + " ");
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
     * @throws MultipleHitsException 
     */
    public void getMapTreeSupport(String infile, String outfile) throws TaxonNotFoundException, MultipleHitsException {
        PathFinder<Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelType.TAXCHILDOF, Direction.OUTGOING), 1000);
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
                        if (shortn == null) {
                            shortn = tnode;
                        }
                        if (tpath.length() < shortest) {
                            shortest = tpath.length();
                            shortn = tnode;
                        }
                        // System.out.println(shortest + " " + tpath.length());
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
                    tset.add(mrcas[k]);
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
    
    
    //TODO: update to t4j
    public void postorderMapTree(JadeNode inode, JadeNode root, Node focalnode, HashSet<JadeNode> skiptips) {
        for (int i = 0; i < inode.getChildCount(); i++) {
            postorderMapTree(inode.getChild(i), root, focalnode, skiptips);
        }
        
        HashMap<JadeNode, Long> roothash = ((HashMap<JadeNode, Long>) root.getObject("hashnodeids"));
        HashMap<JadeNode, ArrayList<Long>> roothashsearch = ((HashMap<JadeNode, ArrayList<Long>>) root.getObject("hashnodeidssearch"));
        // get the licas
        if (inode.getChildCount() > 0) {
            ArrayList<JadeNode> nds = inode.getTips();
            while (nds.removeAll(skiptips) == true) {
                continue;
            }
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
            HashSet<Long> childndids = new HashSet<>();
            
            // System.out.println(inode.getNewick(false));
            for (int i = 0; i < inode.getChildCount(); i++) {
                if (skiptips.contains(inode.getChild(i))) {
                    continue;
                }
                Node[] dbnodesob = (Node[]) inode.getChild(i).getObject("dbnodes");
                for (int k = 0; k < dbnodesob.length; k++) {
                    long[] mrcas = ((long[]) dbnodesob[k].getProperty("mrca"));
                    for (int j = 0; j < mrcas.length; j++) {
                        if (childndids.contains(mrcas[j]) == false) {
                            childndids.add(mrcas[j]);
                        }
                    }
                }
            }
            // _LOG.trace("finished names");
            HashSet<Long> rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndidssearch"));
            HashSet<Node> ancestors = null;//LicaUtil.getAllLICA(hit_nodes_search, childndids, rootids);
            if (ancestors.size() > 0) {
                int count = 0;
                for (Node tnode : ancestors) {
                    // calculate the number of supported branches
                    for (Relationship rel : tnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
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
     * starts from the root and goes to the tips picking the best traveled routes. if you want a more resolved tree, send the child_scores that are cumulative
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
                if (friendnode.hasProperty("name")) {
                    pnode.setName((String) friendnode.getProperty("name"));
                } else {
                    pnode.setName(String.valueOf(friendnode.getId()));
                }
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
                                if (curmrcas.contains(mrcas2[i])) {
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
                    while (pmrcas.removeAll(curmrcas) == true) {
                        continue;
                    }
                    JadeNode tnode1 = null;
                    if (node_jade_map.containsKey(bnode.getId())) {
                        tnode1 = node_jade_map.get(bnode.getId());
                    } else {
                        tnode1 = new JadeNode(pnode);
                        if (bnode.hasProperty("name")) {
                            tnode1.setName((String) bnode.getProperty("name"));
                        } else {
                            tnode1.setName(String.valueOf(bnode.getId()));
                            tnode1.setName(tnode1.getName() + "_" + String.valueOf(node_score.get(bnode)));
                        }
                    }
                    pnode.addChild(tnode1);
                    node_jade_map.put(bnode.getId(), tnode1);
                    if (childs_scores.containsKey(bnode)) {
                        st.push(bnode);
                    }
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
    
    
    
    // ===================================== draft tree synthesis methods ======================================
    
    /**
     * This will map the compatible nodes for particular Lica mappings and will make for
     * better synthesis (or possibly other analyses)
     * @param treeid
     */
    /*
    public void mapcompat(String treeid, boolean test) {
        // initialize db access variables
        Transaction tx = null;
        IndexHits <Relationship> rels = null;
        IndexHits <Node> rootnodes = null;
        rootnodes = sourceRootIndex.get("rootnode", treeid);
        TLongArrayList rootnodesTLAL = new TLongArrayList();
        while (rootnodes.hasNext()) {
            rootnodesTLAL.add(rootnodes.next().getId());
        }
        //System.out.println(rootnodesTLAL);
        //for each of the root nodes, get the tip nodes
        for (int tll = 0; tll < rootnodesTLAL.size(); tll++) {
            Node sn = graphDb.getNodeById(rootnodesTLAL.get(tll));
            Node mn = null;
            for (Relationship rel1 : sn.getRelationships(Direction.INCOMING, RelType.METADATAFOR)) {
                if (rel1.getStartNode().getProperty("source").equals(treeid)) {
                    mn = rel1.getStartNode();
                }
            }
            TLongArrayList ndmap = new TLongArrayList((long[]) mn.getProperty("original_taxa_map"));//need to check and see if this is the deeper mapping
            //if we have exemplar, remove the regular and add these
            TLongArrayList remndmap = new TLongArrayList();
            for (int i = 0; i < ndmap.size(); i++) {
                long tid = ndmap.get(i);
                Node stnd = graphDb.getNodeById(tid);
                if (stnd.hasRelationship(Direction.OUTGOING, RelType.STREEEXEMPLAROF) == true) {
                    for (Relationship ttrel: stnd.getRelationships(Direction.OUTGOING, RelType.STREEEXEMPLAROF)) {
                        if (((String)ttrel.getProperty("source")).equals(treeid)) {
                            remndmap.add(tid);
                            ndmap.add(ttrel.getEndNode().getId());
                        }
                    }
                }
            }
            while (ndmap.removeAll(remndmap) == true) {
                continue;
            }
            TLongArrayList skids = new TLongArrayList();//already visiited relationship ids
            TLongArrayList licas = new TLongArrayList();
            HashSet<Node> allnodes = new HashSet<Node>();
            //for each of the original taxa map nodes
            for (int i = 0; i < ndmap.size(); i++) {
                long tid = ndmap.get(i);
                //                System.out.println("currently at tip: " + tid);
                Node stnd = graphDb.getNodeById(tid);
                //                for (Relationship rel1: Traversal.description().depthFirst().relationships(RelType.STREECHILDOF, Direction.OUTGOING).traverse(stnd).relationships()) {
                //traverse the tip down
                //nd1 will be the current node while walking from a tip to the root
                for (Node nd1: Traversal.description().depthFirst().relationships(RelType.STREECHILDOF, Direction.OUTGOING).traverse(stnd).nodes()) {
                    Relationship rel1 = null;
                    for (Relationship relt: nd1.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
                        //System.out.println("\t" + relt + " " + relt.getProperty("source") + " " + treeid + " " + skids.contains(relt.getId()) + " " + relt.hasProperty("compat"));
                        if (relt.getProperty("source").equals(treeid) && skids.contains(relt.getId()) == false && relt.hasProperty("compat") == false) {
                            rel1 = relt;
                        }
                    }
                    //                    System.out.println("at nd1: " + nd1);
                    //                    System.out.println("traversing " + rel1);
                    if (rel1 == null) {
                        continue;
                    }
                    //this is a repeat of the above for some reason, but basically if the relationship is from the tree
                    if (rel1.getProperty("source").equals(treeid) && skids.contains(rel1.getId()) == false && rel1.hasProperty("compat") == false) {
                        skids.add(rel1.getId());
                        TLongBitArray mrcas = new TLongBitArray((long[])rel1.getProperty("exclusive_mrca"));
                        licas.addAll((long[])rel1.getProperty("licas"));
                        if (mrcas.size() == 1) {
                            continue;
                        }
                        TLongBitArray rt_mrcas = new TLongBitArray((long[])rel1.getProperty("root_exclusive_mrca"));//need to blow these out
                        TLongBitArray blowout = new TLongBitArray();
                        for (int j = 0; j < rt_mrcas.size(); j++) {
                            blowout.addAll((long[])graphDb.getNodeById(rt_mrcas.get(j)).getProperty("mrca"));
                        }
                        rt_mrcas.addAll(blowout);
                        rt_mrcas.removeAll(mrcas);
                        //                        System.out.println("current rel1 " + rel1);
                        //                        System.out.println("mrcas:" + mrcas);
                        //                        System.out.println("rt_mrcas:" + rt_mrcas.size() + ": " + rt_mrcas);
                        //                        System.out.println("licas:" + licas);
                        if (rt_mrcas.size() == 0) {
                            //                            System.out.println("hit the root");
                            continue;
                        }
                        //get the parent rels for the current node nd1
                        ArrayList<Relationship> parentRels = new ArrayList<Relationship>();
                        for (Relationship prel: rel1.getEndNode().getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
                            //                            for (Relationship prel: nd1.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
                            if (prel.getProperty("source").equals(treeid) && prel.hasProperty("compat") == false) {
                                parentRels.add(prel);
                            }
                        }
                        //search for the additional licas
                        CompatBipartEvaluatorBS ce = new CompatBipartEvaluatorBS();
                        ce.setgraphdb(graphDb);
                        ce.setCurTreeNode(nd1);
                        ce.setInset(mrcas);
                        ce.setOutset(rt_mrcas);
                        ce.setStopNodes(rootnodesTLAL);
                        ce.setParentRels(parentRels);
                        ce.setVisitedSet(new TLongArrayList());
                        HashSet<Node> lastnodes = new HashSet<Node>();
                        //traverse from the tip node back through the taxchild ofs
                        for (Node tnode : Traversal.description().breadthFirst().evaluator(ce).relationships(RelType.TAXCHILDOF, Direction.OUTGOING).traverse(stnd).nodes()) {
                            //                            System.out.println("ce visiting: " + tnode);
                            //end the check of the parent
                            if (licas.contains(tnode.getId()) == false) {
                                if (tnode.hasProperty(NodeProperty.TAX_UID.propertyName) == false) {
                                    System.out.println("\tadding " + tnode + " as compatible with " + nd1);
                                } else {
                                    System.out.println("\tadding " + tnode + " " + (String)tnode.getProperty(NodeProperty.NAME.propertyName) + " " + (String)tnode.getProperty(NodeProperty.TAX_UID.propertyName) + " as compatible with " + nd1);
                                }
                                if (test == false) {
                                    try {
                                        tx = graphDb.beginTx();
                                        Relationship trel = nd1.createRelationshipTo(tnode, RelType.STREECHILDOF);
                                        System.out.println("\t\tadding " + trel);
                                        trel.setProperty("compat", "compat");
                                        trel.setProperty("compattype", "fromlicatocompat");
                                        trel.setProperty("lica", nd1.getId());
                                        trel.setProperty("source", treeid);
                                        sourceRelIndex.add(trel, "source", treeid);
                                        
                                        //check if this is the one before the root
                                        //if (rootnodesTLAL.contains(rel1.getEndNode().getId())) {
                                        Relationship trel2 = tnode.createRelationshipTo(rel1.getEndNode(), RelType.STREECHILDOF);
                                        System.out.println("\t\tadding to root " + trel2);
                                        trel2.setProperty("compat", "compat");
                                        trel2.setProperty("compattype", "fromcompattoparent");
                                        trel2.setProperty("lica",nd1.getId());
                                        trel2.setProperty("source", treeid);
                                        sourceRelIndex.add(trel2, "source", treeid);
                                        //}
                                        allnodes.add(rel1.getEndNode());
                                        allnodes.add(tnode);
                                        //}
                                        tx.success();
                                    } finally {
                                        tx.finish();
                                    }
                                    
                                }
                                //play with the clear
                                //lastnodes.clear();
                                //lastnodes.add(tnode);
                            } else {
                                //lastnodes.add(tnode);
                            }
                            //retaln.add(tnode);
                        }
                    }
                }
            }
            //System.out.println(allnodes);
            
            for (Node tnode: allnodes) {
                HashSet<Node> already = new HashSet<Node> ();
                //System.out.println("\t" + tnode);
                for (Relationship trel: tnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
                    if (already.contains(trel.getEndNode())) {
                        continue;
                    } else {
                        already.add(trel.getEndNode());
                        if (allnodes.contains(trel.getEndNode())) {
                            if (trel.hasProperty("compat") && trel.getProperty("source").equals(treeid)) {
                                continue;
                            }
                            if (test == false) {
                                try {
                                    tx = graphDb.beginTx();
                                    Relationship ttrel = tnode.createRelationshipTo(trel.getEndNode(), RelType.STREECHILDOF);
                                    System.out.println("\t\tadding " + ttrel + " from " + tnode + " to " + ttrel.getEndNode());
                                    ttrel.setProperty("compat", "compat");
                                    ttrel.setProperty("compattype", "connector");
                                    ttrel.setProperty("source", treeid);
                                    sourceRelIndex.add(ttrel, "source", treeid);
                                    tx.success();
                                } finally {
                                    tx.finish();
                                }
                            }
                        }
                    }
                }
            }
            
        }
        rootnodes.close();
    }
    */
    
    /**
     * The synthesis method for creating the draft tree. Uses the refactored synthesis classes. This will store the synthesized
     * topology as SYNTHCHILDOF relationships in the graph.
     * 
     * @param startNode this is the beginning node for analysis
     * @param preferredSourceIds this includes the list of preferred sources
     * @param test this will just run through the motions but won't store the synthesis 
     * @throws Exception 
     */
    /*
    public boolean synthesizeAndStoreDraftTreeBranches(Node startNode, Iterable<String> preferredSourceIds, boolean test) throws Exception {
        
        // build the list of ids, have to use generic objects. not used currently
        //ArrayList<Object> sourceIdPriorityList = new ArrayList<Object>();
        //for (String sourceId : preferredSourceIds) {
        //    sourceIdPriorityList.add(sourceId);
        //}
        
        ArrayList<String> allSources = getSourceList();
        System.out.println("Collected " + allSources.size() + " sources");
        
        ArrayList<Object> sourceIdPriorityList = new ArrayList<Object>();
        for (String sourceId : allSources) {
            sourceIdPriorityList.add(sourceId);
        }
        
        // alternative way. not using now.
        //String synthName = "";
        
        // build the list of ids, have to use generic objects
        String [] sourceIdPriorityListString = new String [sourceIdPriorityList.size()];
        int iii = 0;
        ArrayList<Object> justSourcePriorityList = new ArrayList<Object>();
        //for (String sourceId : preferredSourceIds) {
        for (String sourceId : allSources) {
            if (sourceId.startsWith("pg")) {
                justSourcePriorityList.add("pg_" + sourceId.split("_")[1]);
            } else if (sourceId.startsWith("ot")) {
                justSourcePriorityList.add("ot_" + sourceId.split("_")[1]);
            } else {
                justSourcePriorityList.add(sourceId.split("_")[0]);
            }
            sourceIdPriorityListString[iii] = sourceId;
            iii++;
        }
        
        String tempSynthTreeName = DRAFTTREENAME;
    
        int jj = 0;
        while (!done) {
            String terp = tempSynthTreeName;
            if (jj > 0) {
                terp += "_" + jj;
            }
            IndexHits<Node> hits  = synthMetaIndex.query("name", terp);
            if (hits.size() > 0) {
                System.out.println("Synthname '" + terp + "' already used.");
            } else {
                tempSynthTreeName = terp;
                System.out.println("Setting synthname to: '" + terp + "'.");
                done = true;
            }
            jj++;
        }
    
        // define the synthesis protocol
        SynthesisExpander draftSynthesisMethod = new SynthesisExpander();
        
        
        
        // *** NOTE: filtering is not being used at the moment *** //
        
        
        // set filtering criteria
        //RelationshipFilter rf = new RelationshipFilter();
        RelationshipFilter rf = new RelationshipFilter();
        //rf.addCriterion(new SourcePropertyFilterCriterion(SourceProperty.YEAR, FilterComparisonType.GREATEROREQUAL, new TestValue(2000), sourceMetaIndex));
        HashSet<String> filteredsources = new HashSet<String>();
        //ignore any source that isn't in our preferred list
        IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
        boolean studyids = false;
        while (hits.hasNext()) {
            Node n = hits.next();
            if (n.hasProperty("ot:studyId")) {
                if (justSourcePriorityList.contains(n.getProperty("ot:studyId")) == false) {
                    filteredsources.add((String)n.getProperty("ot:studyId"));
                    studyids = true;
                }
            } else {
                if (sourceIdPriorityList.contains(n.getProperty("source")) == false) {
                    filteredsources.add((String) n.getProperty("source"));
                }
            }
        }
        System.out.println("filtered: " + filteredsources);
        if (filteredsources.size() > 0) {
            if (studyids == true) {
                rf.addCriterion(new FilterCriterion(Directive.INCLUDE, new SourcePropertySetTest(filteredsources, SetComparison.CONTAINS_ANY, SourceProperty.STUDY_ID, sourceMetaIndex)));
            } else {
                rf.addCriterion(new FilterCriterion(Directive.INCLUDE, new SourcePropertySetTest(filteredsources, SetComparison.CONTAINS_ANY, SourceProperty.SOURCE, sourceMetaIndex)));
            }
            draftSynthesisMethod.setFilter(rf);
        }
        
        
        
        //if (true == true)
            //    return true;
        // set ranking criteria
        RelationshipRanker rs = new RelationshipRanker();
        if (studyids == true) {
            rs.addCriterion(new SourcePropertyPrioritizedRankingCriterion(SourceProperty.STUDY_ID, sourceIdPriorityList, sourceMetaIndex));
        } else {
            rs.addCriterion(new SourcePropertyPrioritizedRankingCriterion(SourceProperty.SOURCE, sourceIdPriorityList, sourceMetaIndex));
        }
        //rs.addCriterion(new SourcePropertyRankingCriterion(SourceProperty.YEAR, RankingOrder.DECREASING, sourceMetaIndex));
        draftSynthesisMethod.setRanker(rs);
        
        // set conflict resolution criteria
        RelationshipConflictResolver rcr = new RelationshipConflictResolver(new RankResolutionMethod());//new RankResolutionMethodInferredPath());
        draftSynthesisMethod.setConflictResolver(rcr);
        
// ================================ TESTING =================================
// 
//            draftSynthesisMethod = new NodeCountTopoOrderSynthesisExpander(startNode);
//            draftSynthesisMethod = new SourceRankTopoOrderSynthesisExpanderUsingExclusiveMrcas(startNode);
//            draftSynthesisMethod = new RootwardSynthesisParentExpander(startNode);
//            draftSynthesisMethod = new SourceRankTopoOrderSynthesisExpanderUsingEdgeIdsAndTipIds().synthesizeFrom(startNode);
            draftSynthesisMethod = new RankedSynthesisSubproblemExpander(startNode, Verbosity.SILENT);//changed from Verbosity.EXTREME
//
// ================================ TESTING =================================
        
        // user feedback
        System.out.println("\n" + draftSynthesisMethod.getDescription());
        
        //make the metadatanode
        Transaction tx = graphDb.beginTx();
        //String synthTreeName = DRAFTTREENAME; // needs to be changed to the name that gets passed
        
        String synthTreeName = tempSynthTreeName;
        
        
        
        // TODO: add all sources
        try {
            Node metadatanode = graphDb.createNode();
            metadatanode.createRelationshipTo(startNode, RelType.SYNTHMETADATAFOR);
            metadatanode.setProperty("name", synthTreeName);
            //Date date = new Date();
            //metadatanode.setProperty("date", date.toString());
            String date = GeneralUtils.getTimestamp(); // use ot-base standardized format: "yyyy-MM-dd HH:mm:ss.SSS". helps with sorting.
            metadatanode.setProperty("date", date);
            //    metadatanode.setProperty("synthmethod", arg1);
            //    metadatanode.setProperty("command", command);
            
            metadatanode.setProperty("sourcenames", sourceIdPriorityListString); // need to make sure that this list is processed correctly
            // Adding 1) taxonomy version and 2) start node to the metadata node too, as it seems convenient to have everything together
            if (getTaxonomyVersion() != null) {
                metadatanode.setProperty("taxonomy", getTaxonomyVersion());
            } else {
                metadatanode.setProperty("taxonomy", "0.0");
            }
            metadatanode.setProperty("startnode", startNode.getId()); // even though it is directly attached
            synthMetaIndex.add(metadatanode, "name", synthTreeName);
            this.graphDb.setGraphProperty("draftTreeRootNodeId", startNode.getId()); // hmm. do we want this i.e. if storing multiple trees?
            
            tx.success();
        } catch (Exception ex) {
            tx.failure();
            throw ex;
        } finally {
            tx.finish();
        }
        
        // set empty parameters for initial recursion
        //    Node originalParent = null; // hmm. not used.
        
        // recursively build the tree structure
        knownIdsInTree = new HashSet<Long>();
        tx = graphDb.beginTx();
        try {
            for (Relationship rel: Traversal.description().breadthFirst().expand(draftSynthesisMethod).traverse(startNode).relationships()) {
                // testing
                System.out.println("now attempting to store rel " + rel.getId());
                
                // store the relationship
                Node parentNode = rel.getEndNode();
                Node curNode = rel.getStartNode();
                
                if (parentNode != null && test == false) {
                    Relationship newRel = curNode.createRelationshipTo(parentNode, RelType.SYNTHCHILDOF);
                    newRel.setProperty("name", synthTreeName);
                    
                    // add to synthesis index
                    //synthRelIndex.add(newRel, "draftTreeID", DRAFTTREENAME);
                    synthRelIndex.add(newRel, "draftTreeID", synthTreeName);
                    
                    // get all the sources supporting this relationship
                    HashSet<String> sources = new HashSet<String>();
                    if (curNode.hasRelationship(RelType.STREECHILDOF, Direction.OUTGOING)) {
                        for (Relationship rel2 : curNode.getRelationships(RelType.STREECHILDOF, Direction.OUTGOING)) {
                            if (rel2.getEndNode().getId() == parentNode.getId()) {
                                if (rel2.hasProperty("source")) {
                                    sources.add(String.valueOf(rel2.getProperty("source")));
                                }
                            }
                        }
                    }
                    
                    // include taxonomy as a source as well
                    if (curNode.hasRelationship(RelType.TAXCHILDOF)) {
                        sources.add("taxonomy");
                    }
                    
                    // store the sources in a string array
                    String[] sourcesArray = new String[sources.size()];
                    Iterator<String> sourcesIter = sources.iterator();
                    for (int i = 0; i < sources.size(); i++) {
                        sourcesArray[i] = sourcesIter.next();
                    }
                    
                    // set the string array as a property of the relationship
                    newRel.setProperty("supporting_sources", sourcesArray);
                }
                // remember the ids of taxa we add, this is when sinking lost children
                knownIdsInTree.add(curNode.getId());
            }
            System.out.println("number of potential dead nodes " + draftSynthesisMethod.getDeadNodes().size());
            TLongArrayList deadnodes = new TLongArrayList(draftSynthesisMethod.getDeadNodes());
            // System.out.println("dead nodes " + deadnodes);
            // should do this cleaner
            //This cleans up dead nodes
            System.out.println("cleaning dead nodes");
            TLongHashSet vd = new TLongHashSet();
            int actual = 0;
            for (int i = 0; i < deadnodes.size(); i++) {
                long cnd = deadnodes.get(i);
                if (vd.contains(cnd)) {
                    continue;
                } else {
                    vd.add(cnd);
                }
                // check to see if this has any other children
                Node cn = graphDb.getNodeById(cnd);
                if (cn.hasRelationship(RelType.STREECHILDOF, Direction.INCOMING) == false) {
                    vd.add(cnd);
                } else {
                    System.out.println("actual: " + cnd);
                    actual++;
                    boolean going = true;
                    Node curnode = cn;
                    while (going) {
                        if (curnode.hasRelationship(RelType.SYNTHCHILDOF, Direction.INCOMING) == false) {
                            vd.add(curnode.getId());
                            Relationship tr = curnode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING);
                            curnode = tr.getEndNode();
                            System.out.println("deleting: " + tr);
                            //synthRelIndex.remove(tr, "draftTreeID", DRAFTTREENAME);
                            synthRelIndex.remove(tr, "draftTreeID", synthTreeName);
                            tr.delete();
                        } else {
                            break;
                        }
                    }
                }
            }
            System.out.println("number of actual deadnodes:" + actual);
            // end the cleaning
            
            System.out.println("Synthesis traversal complete. Results:\n");
            System.out.println("\n" + draftSynthesisMethod.getReport());
            
            tx.success();
        } catch (Exception ex) {
            tx.failure();
            throw ex;
        } finally {
            tx.finish();
        }

        // ============================== add missing children
        tx = graphDb.beginTx();
        try {
            // uncommented for testing with new synth method
            addMissingChildrenToDraftTreeWhile (startNode, startNode);
            tx.success();
        } catch (Exception ex) {
            tx.failure();
            ex.printStackTrace();
        } finally {
            tx.finish();
        }
        // ============================== end add missing children

        System.out.println("exiting the sythesis");
        return true;
    }
    */
    
    
    
    
    /**
     * Find the MRCA of the graph nodes using the draft tree topology (requires an acyclic structure to behave properly)
     * 
     * @param descNodes
     * @return licaNode
     */
    /*
    private Node getLICAForDraftTreeNodes(Iterable<Node> descNodes) {
        
        if (!descNodes.iterator().hasNext()) {
            throw new java.lang.NullPointerException("Attempt to find the MRCA of zero taxa");
        }
        
        Iterator<Node> descNodesIter = descNodes.iterator();
        Node firstNode = descNodesIter.next();
        
        if (!descNodesIter.hasNext()) {
            // there is only one descendant in the set; it is its own mrca
            return firstNode;
        }
        
        // first get the full path to root from an arbitrary taxon in the set
        List<Long> referencePathNodeIds = getDraftTreePathToRoot(firstNode);
        
        // testing
        //         System.out.println("first path");
        //            for (long nid : referencePathNodeIds) {
        //            System.out.println(nid);
        //         }
        
        // compare paths from all other taxa to find the mrca
        int i = 0;
        while (descNodesIter.hasNext()) {
            Node descNode = descNodesIter.next();
            
            // testing
            // System.out.println("next path");
            
            for (long pid : getDraftTreePathToRoot(descNode)) {
                
                // testing
                // System.out.println("looking for " + pid + " in first path");
                
                if (referencePathNodeIds.contains(pid)) {
                    int j = referencePathNodeIds.indexOf(pid);
                    
                    // testing
                    // System.out.println("found parent in first path, position " + j);
                    if (i < j) {
                        i = j;
                    }
                    break;
                }
            }
        }
        //System.out.println("i currently equals: " + i);
        // return the lica
        return graphDb.getNodeById(referencePathNodeIds.get(i));
    }
    */
    
    /**
     * Used to add missing external nodes to the draft tree stored in the graph.
     * @param startNode
     * @param taxRootNode
     */
    /*
    private void addMissingChildrenToDraftTreeWhile (Node startNode, Node taxRootNode) {
        // to be stored as the 'supporting_sources' property of newly created rels
        String[] supportingSources = new String[1];
        supportingSources[0] = "taxonomy";
        TLongArrayList taxaleft = new TLongArrayList ((long [])startNode.getProperty("mrca"));
        while (taxaleft.removeAll(knownIdsInTree) == true) {
                    continue;
        }
        
        System.out.println("have to add " + taxaleft.size());
        
        while (taxaleft.size() > 0) {
            
            long tid = taxaleft.removeAt(0);
            Node taxNode = graphDb.getNodeById(tid);
            
            TLongArrayList ttmrca = new TLongArrayList((long [])taxNode.getProperty("mrca"));
            if (taxNode.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF)) {
                continue;
            }
            
            System.out.print("taxaleft: " + (taxaleft.size() + 1));
            
            //if it is a tip, get the parent
            Node ptaxNode = taxNode.getSingleRelationship(RelType.TAXCHILDOF,Direction.OUTGOING).getEndNode();
            ArrayList<Node> nodesInTree = new ArrayList<Node>();
            //    System.out.println(taxNode.getProperty("name"));
            
            for (long cid : (long[]) ptaxNode.getProperty("mrca")) {
                Node childNode = graphDb.getNodeById(cid);
                // `knownIdsInTree` should be populated during synthesis
                if (knownIdsInTree.contains(cid)) {
                    nodesInTree.add(childNode);
                } 
            }
            
            System.out.println(" working with " + ((String)taxNode.getProperty("name")));
            System.out.println("\tNode " + ((String)taxNode.getProperty("name")) + " has " + nodesInTree.size() + " sister taxa in the tree.");

            // easiest: direct parent is in tree, just add rel
            if (ptaxNode.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF)) {
                System.out.println("\tParent node " + ((String)ptaxNode.getProperty("name")) + " is already in the tree.");
                System.out.println("\tAdding rel from: " + ((String)taxNode.getProperty("name")) + " to: " + ((String)ptaxNode.getProperty("name")));
                
                Relationship newRel = taxNode.createRelationshipTo(ptaxNode, RelType.SYNTHCHILDOF);
                synthRelIndex.add(newRel, "draftTreeID", DRAFTTREENAME);
                newRel.setProperty("name", DRAFTTREENAME);
                newRel.setProperty("supporting_sources", supportingSources);
                knownIdsInTree.add(taxNode.getId());
                
            // find the mrca of the names in the tree
            } else if (nodesInTree.size() > 0) {
                
                Node mrca = null;
                mrca = getLICAForDraftTreeNodes(nodesInTree);
                
                //Node taxmrca = null;
                //taxmrca = getTaxonomyMRCA(nodesInTree);
                
                ArrayList<Node> nodesInTreePlusNew = new ArrayList<Node>();
                nodesInTreePlusNew.addAll(nodesInTree);
                nodesInTreePlusNew.add(taxNode);
                Node taxmrca = getTaxonomyMRCA(nodesInTreePlusNew); // *** ah! didn't take into account the new tip (ever?) before ***
                // the change above seems to fix everything; all bulk-adding of taxa is just for efficiency
                
                boolean going = true;
                ImmutableCompactLongSet ints = new ImmutableCompactLongSet((long[])taxmrca.getProperty("mrca"));
                while (going == true) {
                    if (mrca.hasProperty("outmrca")) {
                        ImmutableCompactLongSet outs = new ImmutableCompactLongSet((long[])mrca.getProperty("outmrca"));
                        if (outs.containsAny(ints)) {
                            mrca = mrca.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
                        } else {
                            break;
                        }
                    } else {
                        ImmutableCompactLongSet ins = new ImmutableCompactLongSet((long[])mrca.getProperty("mrca"));
                        if (ins.containsAll(ints) == true) {
                            break;
                        } else {
                            mrca = mrca.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
                        }
                    }
                }
                
                Relationship newRel = taxNode.createRelationshipTo(mrca, RelType.SYNTHCHILDOF);
                synthRelIndex.add(newRel, "draftTreeID", DRAFTTREENAME);
                newRel.setProperty("name", DRAFTTREENAME);
                newRel.setProperty("supporting_sources", supportingSources);
                knownIdsInTree.add(taxNode.getId());
                
                if (nodesInTree.size() == 1) {
                    // bulk add any remaining unsampled taxa, as there is no chance for conflict
                    System.out.println("\tAttempting to add ALL " + ((long[])ptaxNode.getProperty("mrca")).length + " unsampled descendant nodes of " + ((String)ptaxNode.getProperty("name")) + "...");
                    for (long cid : (long[]) ptaxNode.getProperty("mrca")) {
                        Node curChild = graphDb.getNodeById(cid);
                        if (!curChild.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF)) {
                            System.out.println("\tCurrent node: " + ((String)curChild.getProperty("name")));
                        }
                        boolean done = false;
                        while (!done) {
                            Node curParent = curChild.getSingleRelationship(RelType.TAXCHILDOF,Direction.OUTGOING).getEndNode();
                            if (curParent.getId() == ptaxNode.getId()) {
                                curParent = mrca;
                            }
                            if (!curChild.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF)) {
                                Relationship cleanupRel = curChild.createRelationshipTo(curParent, RelType.SYNTHCHILDOF);
                                synthRelIndex.add(cleanupRel, "draftTreeID", DRAFTTREENAME);
                                cleanupRel.setProperty("name", DRAFTTREENAME);
                                cleanupRel.setProperty("supporting_sources", supportingSources);
                                if (curParent.hasProperty("name")) {
                                    System.out.println("\t\tAdding rel from " + ((String)curChild.getProperty("name")) + " to " + ((String)curParent.getProperty("name")));
                                } else {
                                    System.out.println("\t\tAdding rel from " + ((String)curChild.getProperty("name")) + " to " + curParent);
                                }
                            }
                            if (curParent.getId() == mrca.getId()) {
                                done = true;
                                break;
                            } else {
                                curChild = curParent;
                            }
                        }
                        knownIdsInTree.add(cid);
                        taxaleft.remove(cid);
                    }
                }
            } else {

                // ptaxNode has no descendants in the synthetic tree
                System.out.println("\tNode " + ((String)taxNode.getProperty("name")) + " has no sampled sister taxa");
                System.out.println("\tAttempting to add ALL " + ((long[])ptaxNode.getProperty("mrca")).length + " unsampled descendant nodes of " + ((String)ptaxNode.getProperty("name")) + "...");
                for (long cid : (long[]) ptaxNode.getProperty("mrca")) {
                    Node curChild = graphDb.getNodeById(cid);
                    System.out.println("\tCurrent node: " + ((String)curChild.getProperty("name")));
                    boolean done = false;
                    while (!done) {
                        Node curParent = curChild.getSingleRelationship(RelType.TAXCHILDOF,Direction.OUTGOING).getEndNode();
                        if (!curChild.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF)) {
                            Relationship newRel = curChild.createRelationshipTo(curParent, RelType.SYNTHCHILDOF);
                            synthRelIndex.add(newRel, "draftTreeID", DRAFTTREENAME);
                            newRel.setProperty("name", DRAFTTREENAME);
                            newRel.setProperty("supporting_sources", supportingSources);
                            System.out.println("\t\tAdding rel from " + ((String)curChild.getProperty("name")) + " to " + ((String)curParent.getProperty("name")));
                        }
                        if (curParent.getId() == ptaxNode.getId()) {
                            done = true;
                            break;
                        } else {
                            curChild = curParent;
                        }
                    }
                    taxaleft.remove(cid);
                }
                if (!taxaleft.contains(ptaxNode.getId())) {
                    taxaleft.add(ptaxNode.getId());
                    System.out.println("\tAdding " + ((String)ptaxNode.getProperty("name")) + " to taxaleft list");
                }
            }
        }
    }
    */
    
    // ====================================== extracting Synthetic trees from the db ==========================================
    
    /**
     * Creates and returns a JadeTree object containing the structure defined by the SYNTHCHILDOF relationships present below a given node.
     * External function that uses the ottid to find the root node in the db.
     * 
     * This includes mapping the relationships based on a list that is input
     * 
     * @param startNode
     * @param synthTreeName
     * @param rellist
     * @throws OttIdNotFoundException 
     */
    /*
    public JadeTree extractDraftTreeMap(Node startNode, String synthTreeName, HashMap<Long,Integer> rellist) {
        
        // empty parameters for initial recursion
        JadeNode parentJadeNode = null;
        Relationship incomingRel = null;
        
        return new JadeTree(extractStoredSyntheticTreeRecurMap(startNode, parentJadeNode, incomingRel, DRAFTTREENAME, rellist));
    }
    */
    
    
    // extract a specific synthetic tree by name
    // if startNode is null, will return the whole tree (if present)
    /**
     * @param startNode
     * @param synthTreeName
     * @return JadeTree
     */
    /*
    public JadeTree extractDraftTreeByName(Node startNode, String synthTreeName) {
        
        // empty parameters for initial recursion
        System.out.println("Attempting to extract draft tree name: '" + synthTreeName + "'");
        JadeNode parentJadeNode = null;
        Relationship incomingRel = null;
        
        // will need a check if a node id is passed but is not in the desired synth tree
        if (startNode == null) {
            Node synthMeta = getSynthesisMetaNodeByName(synthTreeName);
            System.out.println("Found metadatanode: " + synthMeta);
            startNode = graphDb.getNodeById((Long) synthMeta.getProperty("startnode"));
            System.out.println("Found start node for tree '" + synthTreeName + "': " + startNode);
        }
        
        return new JadeTree(extractStoredSyntheticTreeRecur(startNode, parentJadeNode, incomingRel, synthTreeName));
    }
    */
    
    
    /**
     * Recursively creates a JadeNode hierarchy containing the tree structure defined by the SYNTHCHILDOF relationships present below a given node,
     * and returns the root JadeNode. Internal function that requires a Neo4j Node object for the start node.
     * 
     * Includes the mapping of relationships based on the list provided
     * 
     * @param nodeId
     */
    /*
    private JadeNode extractStoredSyntheticTreeRecurMap(Node curGraphNode, JadeNode parentJadeNode, Relationship incomingRel, String synthTreeName, HashMap<Long,Integer> rellist) {
        
        JadeNode curNode = new JadeNode();
        
        // testing
        //    System.out.println("child graph node: " + curGraphNode.getId());
        //    if (parentJadeNode != null) {
        //        System.out.println("parent jade node: " + parentJadeNode.toString());
        //    }
        
        // set the names for the newick string
        //only external names for now
        //if(!curGraphNode.hasRelationship(Direction.INCOMING, RelType.SYNTHCHILDOF)){
            if (curGraphNode.hasProperty("name")) {
                curNode.setName(getOttName(curGraphNode));
            }
        //}
        
        if(incomingRel != null){
            if (rellist.containsKey(incomingRel.getId())){
                curNode.assocObject("relmap", rellist.get(incomingRel.getId()));
                //if(curGraphNode.hasRelationship(Direction.INCOMING, RelType.SYNTHCHILDOF)){
                //    curNode.setName(String.valueOf(rellist.get(incomingRel.getId()))+".0"); //add the decimal just for figtree plotting
                //}
            }
        }
        
        curNode.assocObject("nodeID", String.valueOf(curGraphNode.getId()));
        
        // add the current node to the tree we're building
        if (parentJadeNode != null) {
            if (curGraphNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).hasProperty("supporting_sources")) {
                curNode.assocObject("supporting_sources", (String [] ) curGraphNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getProperty("supporting_sources"));
            }
            parentJadeNode.addChild(curNode);
            if (incomingRel.hasProperty("branch_length")) {
                curNode.setBL((Double) incomingRel.getProperty("branch_length"));
            }
        }
        
        // get the immediate synth children of the current node
        LinkedList<Relationship> synthChildRels = new LinkedList<Relationship>();
        for (Relationship synthChildRel : curGraphNode.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)) {
            
            // TODO: here is where we would filter synthetic trees using metadata (or in the traversal itself)
            if (synthTreeName.equals(String.valueOf(synthChildRel.getProperty("name")))) {
                // currently just filtering on name
                synthChildRels.add(synthChildRel);
            }
        }
        
        // recursively add the children to the tree we're building
        for (Relationship synthChildRel : synthChildRels) {
            extractStoredSyntheticTreeRecurMap(synthChildRel.getStartNode(), curNode, synthChildRel, synthTreeName,rellist);
        }
        
        return curNode;
    }
    */
    
    // =============================== Synthesis methods using source metadata decisions ONLY ===============================
    
    /**
     * Constructs a newick tree based on the sources. There are currently no other criteria considered in this particular function.
     * 
     * Deprecated. Should be updated to use new synthesis methods.
     * 
     * THIS IS STILL USED FOR CURRENT JUSTTREES SYNTH!!
     * 
     * @param taxName
     * @param sourcesArray
     * @throws MultipleHitsException 
     */
    /*
    @Deprecated
    public JadeTree sourceSynthesis(Node startNode, LinkedList<String> sourcesArray, boolean useTaxonomy) throws TaxonNotFoundException, MultipleHitsException {
        
        // initial (empty) parameters for recursion
        JadeNode parentJadeNode = null;
        Relationship incomingRel = null;
        
        JadeNode root = sourceSynthesisRecur(startNode, parentJadeNode, sourcesArray, incomingRel, useTaxonomy);
        JadeTree tree = new JadeTree(root);
        
        if (sinkLostChildren) {
            // TODO: find the taxonomy base node to use for sinking children?
            String taxName = null;
            addMissingChildrenToJadeTreeRelaxed(tree, taxName);
        }
        return new JadeTree(root);
    }
    */
    
    /**
     * This is the preorder function for constructing newick trees given a list of sources. This ONLY considers the sources, not
     * the properties of the graph (i.e. it does not use branch and bound or exhaustive search to improve taxon coverage in the
     * synthetic tree.
     * 
     * Deprecated. Has been superseded by the new synthesis methods.
     * 
     * @param curGraphNode
     * @param parentJadeNode
     * @param sourcesArray
     * @param incomingRel
     * @return
     */
    
    /*
    @Deprecated
    private JadeNode sourceSynthesisRecur(Node curGraphNode, JadeNode parentJadeNode, LinkedList<String> sourcesArray, Relationship incomingRel, boolean useTaxonomy) {
        
        boolean ret = false;
        JadeNode newNode = new JadeNode();
        
        if (curGraphNode.hasProperty("name")) {
            newNode.setName((String) curGraphNode.getProperty("name"));
//            newNode.setName(GeneralUtils.cleanName(newNode.getName()));
            newNode.setName(GeneralUtils.scrubName(newNode.getName()));
        }
        
        if (parentJadeNode == null) {
            // this is the root of this tree, so set the flag to return it
            ret = true;
            
        } else {
            // add this node as a child of the passed-in parent
            parentJadeNode.addChild(newNode);
            
            if (sinkLostChildren) {
                knownIdsInTree.add(curGraphNode.getId());
            }
            
            if (incomingRel.hasProperty("branch_length")) {
                newNode.setBL((Double) incomingRel.getProperty("branch_length"));
            }
        }
        
        // variables to store information used to make decisions about best paths
        HashMap<Long, HashSet<Long>> candNodeDescendantIdsMap = new HashMap<Long, HashSet<Long>>();
        HashMap<Long, Integer> candNodeRankingMap = new HashMap<Long, Integer>();
        HashMap<Long, Relationship> candNodeRelationshipMap = new HashMap<Long, Relationship>();
        HashSet<Long> candidateNodeIds = new HashSet<Long>();
        
        // for every candidate (incoming childof) relationship
        for (Relationship candRel : curGraphNode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
            if (useTaxonomy == false) {
                // skip taxonomy relationships if specified
                if (candRel.getProperty("source").equals("taxonomy")) {
                    continue;
                }
            }
            
            // candidate child node id
            Long cid = candRel.getStartNode().getId();
            
            // if we haven't seen this node yet
            if (candidateNodeIds.contains(cid) == false) {
                candidateNodeIds.add(cid);
                
                // save this candidate's mrca descendants
                HashSet<Long> descIds = new HashSet<Long>();
                for (long descId : (long[]) graphDb.getNodeById(cid).getProperty("mrca")) {
                    descIds.add(descId);
                }
                candNodeDescendantIdsMap.put(cid, descIds);
                
                // save the current candidate relationship we used to reach this node
                candNodeRelationshipMap.put(cid, candRel);
                
                // the first time we see a node, set its ranking to the lowest possible rank
                candNodeRankingMap.put(cid, sourcesArray.size());
            }
            
            // update the ranking for this node, based on the current source. if already ranked at the top (rank 0) then don't bother
            if (candNodeRankingMap.get(cid) != 0) {
                String sourceName = (String) candRel.getProperty("source");
                for (int i = 0; i < sourcesArray.size(); i++) {
                    // update if the rank of the sourcetree for the current candidate relationship is better than the last saved rank
                    if (sourceName.compareTo(sourcesArray.get(i)) == 0) {
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
        
        @Deprecated
        HashSet<Long> putativeLostChildIds = null;
        if (sinkLostChildrenStrict) {
            // will be used to record children that may be lost during conflict tie-breaking
            putativeLostChildIds = new HashSet<Long>();
        }
        
        // compare all candidate nodes to choose which to remove/keep
        HashSet<Long> nodesToExclude = new HashSet<Long>();
        for (Long cid_i : candidateNodeIds) {
            if (nodesToExclude.contains(cid_i)) {
                continue;
            }
            HashSet<Long> descendantIds_i = candNodeDescendantIdsMap.get(cid_i);
            
            for (Long cid_j : candidateNodeIds) {
                if (cid_j == cid_i || nodesToExclude.contains(cid_j)) {
                    continue;
                }
                HashSet<Long> descIds_j = candNodeDescendantIdsMap.get(cid_j);
                
                // get difference of descendant sets for candidates i and j
                HashSet<Long> descIds_iMinusj = new HashSet<Long>(descendantIds_i);
                descIds_iMinusj.removeAll(descIds_j);
                
                // candidates i and j are in conflict if they overlap
                if ((descendantIds_i.size() - descIds_iMinusj.size()) > 0) {
                    
                    // use source ranking to break ties
                    if (candNodeRankingMap.get(cid_i) < candNodeRankingMap.get(cid_j)) {
                        nodesToExclude.add(cid_j);
                        
                        @Deprecated
                        if (sinkLostChildrenStrict) {
                            // record eventual descendants that may be lost by this choice
                            putativeLostChildIds.addAll(descIds_j);
                        }
                        
                    } else {
                        
                        // exclude node i, and don't bother seeing if it is better than remaining nodes
                        nodesToExclude.add(cid_i);
                        break;
                        
                        @Deprecated
                        if (sinkLostChildrenStrict) {
                            // record eventual descendants that may be lost by this choice
                            putativeLostChildIds.addAll(descIds_i);
                        } else {
                            // we're excluding node i, so don't bother seeing if it is better than remaining nodes
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
        
        @Deprecated
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
        
        // continue recursion
        for (Long cid : candidateNodeIds) {
            sourceSynthesisRecur(graphDb.getNodeById(cid), newNode, sourcesArray, candNodeRelationshipMap.get(cid), useTaxonomy);
        }
        
        // hit root of subtree, return it
        if (ret == true) {
            return newNode;
        }
        
        // satisfy java's desire for an explicit return
        return null;
    }
    */
    
    // ==================================== Synthesis methods using graph decisions ONLY ===================================
    
    
    /**
     * Constructs a JadeTree object containing a synthetic tree, breaking ties based on branch and bound or exhaustive search.
     * Stores the synthetic tree in the graph as SYNTHCHILDOF relationships, bearing the value of `syntheticTreeName` in their "name" property.
     * 
     * Deprecated. Needs to be reimplemented in the new synthesis methods.
     * 
     * THIS IS STILL USED CURRENTLY FOR JUSTTREES SYNTHS!
     * 
     * @param nodeId
     * @param useTaxonomy
     * @param useBranchAndBound
     * @param syntheticTreeName
     */
    /*
    @Deprecated
    public JadeTree graphSynthesis(Node startNode, boolean useTaxonomy, boolean useBranchAndBound, String syntheticTreeName) {
        
        // enable storing trees
        boolean recordSyntheticRels = true;
        
        // set empty starting parameters for recursion
        Node parentGraphNode = null;
        JadeNode parentJadeNode = null;
        Relationship incomingRel = null;
        String altName = "";        
        
        // need to start a transaction in order to store branches
        Transaction tx = null;
        tx = graphDb.beginTx();
        
        // get the tree
        JadeNode root = graphSynthesisRecur(startNode, parentGraphNode, parentJadeNode, incomingRel, altName, useBranchAndBound, useTaxonomy, recordSyntheticRels, syntheticTreeName);
        
        tx.success();
        tx.finish();
        
        // return the tree wrapped in a JadeTree object
        return new JadeTree(root);
    }
    */
    
    /**
     * This is the preorder function for constructing a newick tree based ONLY on decisions using actual graph properties (e.g. number of tips, etc.),
     * i.e. no decisions will can be made using source metadata. If the resulting decisions do not contain all of the taxa, either a branch and bound
     * search or exhaustive pair search will attempt to find a better (more complete) scenario.
     * 
     * DEPRECATED. Use new synthesis methods.
     *  
     * @param curGraphNode
     * @param parentGraphNode
     * @param parentJadeNode
     * @param altName
     * @param useTaxonomy
     * @param useBranchAndBound
     * @param recordSyntheticRels
     * @param synthTreeName
     * @return JadeNode containing the synthetic tree hierarchy
     */
    // TODO: need to be able to ignore taxonomy
    /*
    @Deprecated
    private JadeNode graphSynthesisRecur(Node curGraphNode, Node parentGraphNode, JadeNode parentJadeNode, Relationship incomingRel, String altName, boolean useTaxonomy,
            boolean useBranchAndBound, boolean recordSyntheticRels, String synthTreeName) {
        
        if (parentGraphNode != null && recordSyntheticRels) {
            if (synthTreeName == null) {
                throw new java.lang.IllegalStateException("Attempt to store synthetic tree relationships in the graph without a name for the synthetic tree.");
            } else {
                curGraphNode.createRelationshipTo(parentGraphNode, RelType.SYNTHCHILDOF).setProperty("name", synthTreeName);
            }
        }
        
        // System.out.println("starting " + curnode.getId());
        boolean ret = false;
        JadeNode newJadeNode = new JadeNode();
        
        if (curGraphNode.hasProperty("name")) {
            newJadeNode.setName((String) curGraphNode.getProperty("name"));
//            newJadeNode.setName(GeneralUtils.cleanName(newJadeNode.getName()));
            newJadeNode.setName(GeneralUtils.scrubName(newJadeNode.getName()));
        }
        
        if (newJadeNode.getName().length() == 0) {
            newJadeNode.setName(altName);
        }
        
        if (parentJadeNode == null) {
            ret = true;
        } else {
            parentJadeNode.addChild(newJadeNode);
            if (incomingRel.hasProperty("branch_length")) {
                newJadeNode.setBL((Double) incomingRel.getProperty("branch_length"));
            }
        }
        
        // decide which nodes to continue on
        HashSet<Long> testnodes = new HashSet<Long>();
        HashSet<Long> originaltest = new HashSet<Long>();
        HashMap<Long, Integer> testnodes_scores = new HashMap<Long, Integer>();
        HashMap<Long, HashSet<Long>> storedmrcas = new HashMap<Long, HashSet<Long>>();
        HashMap<Long, Relationship> bestrelrel = new HashMap<Long, Relationship>();
        
        for (Relationship rel : curGraphNode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
            
            if (useTaxonomy == false) {
                if (rel.getProperty("source").equals("taxonomy")) {
                    continue;
                }
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
            if (deletenodes.contains(tn)) {
                continue;
            }
            HashSet<Long> mrcas1 = storedmrcas.get(tn);
            int compint1 = testnodes_scores.get(tn);
            for (Long tn2 : testnodes) {
                if (tn2 == tn || deletenodes.contains(tn2)) {
                    continue;
                }
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
        
        int totalmrcas = ((long[]) curGraphNode.getProperty("mrca")).length;
        int total = 0;
        for (Long nd : testnodes) {
            total += storedmrcas.get(nd).size();
        }
        
        // If the totalmrcas is not complete then we search for a more complete one despite lower scores
        //This is a specific knapsack problem
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
            graphSynthesisRecur(graphDb.getNodeById(nd), curGraphNode, newJadeNode, bestrelrel.get(nd), _altName, useTaxonomy, useBranchAndBound, recordSyntheticRels, synthTreeName);
        }
        
        if (ret == true) {
            return newJadeNode;
        }
        
        return null;
    }
    */
    
    // ========================== Methods for re-adding missing children to synthetic trees/subgraphs =============================
    
    
    /**
     * Used to add missing external nodes from a JadeNode tree that has just been built by `constructNewickTieBreakerSOURCE`.
     * Adds nodes based on taxnomy; identifies all the external descendants of each taxon that are absent from the tree, and adds them
     * at the base of the MRCA of all the external descendants of that taxon that are present in the tree.
     * 
     * @param inputRoot
     *        the tree to be added
     * @param taxRootName
     *        the name of the inclusive taxon to add missing descendants of (will include all descendant taxa)
     * @return JadeNode tree (the root of a JadeNode tree) with missing children added
     * @throws MultipleHitsException 
     */
    private void addMissingChildrenToJadeTreeRelaxed(JadeTree tree, String taxRootName) throws TaxonNotFoundException, MultipleHitsException {
        
        // TODO: make a version of this that adds these to the graph instead of a JadeTree
        
        // will hold nodes from the taxonomy to check
        LinkedList<Node> taxNodes = new LinkedList<Node>();
        
        // walk taxonomy and save nodes in postorder
        Node taxRoot = findTaxNodeByName(taxRootName);
        TraversalDescription TAXCHILDOF_TRAVERSAL = Traversal.description().relationships(RelType.TAXCHILDOF, Direction.INCOMING);
        for (Node taxChild : TAXCHILDOF_TRAVERSAL.breadthFirst().traverse(taxRoot).nodes()) {
            taxNodes.add(0, taxChild);
        }
        
        // walk taxa from tips down
        for (Node taxNode : taxNodes) {
            if (taxNode.hasRelationship(Direction.INCOMING, RelType.TAXCHILDOF) == false) {
                // only consider taxa that are not tips
                continue;
            }
            
            //    System.out.println(taxNode.getProperty("name"));
            
            // will record descendant taxa not in the tree so we can add them
            HashMap<String, Long> taxaToAdd = new HashMap<String, Long>();
            
            // will just record the names that are already in the tree
            ArrayList<String> namesInTree = new ArrayList<String>();
            
            // get all external descendants of this taxon, remember if they're in the tree or not
            for (long cid : (long[]) taxNode.getProperty("mrca")) {
//                String name = GeneralUtils.cleanName((String) graphDb.getNodeById(cid).getProperty("name"));
                String name = GeneralUtils.scrubName((String) graphDb.getNodeById(cid).getProperty("name"));
                
                // `knownIdsInTree` should already have been started during original construction of `tree`
                if (knownIdsInTree.contains(cid)) {
                    namesInTree.add(name);
                    //    System.out.println("name in tree: " + name);
                    
                } else {
                    taxaToAdd.put(name, cid);
                }
            }
            
            // find the mrca of the names in the tree
            JadeNode mrca = null;
            if (namesInTree.size() > 0) {
                mrca = tree.getMRCAAnyDepthDescendants(namesInTree);
                //    System.out.println("found mrca: " + mrca);
            } else {
                //    System.out.println("zero names in tree!");
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
                //    System.out.println("attempting to add child: " + taxName + " to " + mrca.getName());
                JadeNode newChild = new JadeNode();
                newChild.setName(taxName);
                mrca.addChild(newChild);
                knownIdsInTree.add(taxId);
            }
            // update the JadeTree, otherwise we won't always find newly added taxa when we look for mrcas
            tree.processRoot();
        }
    }
    
    // ========================================== other methods ==============================================
    
    /**
     * Get the list of sources that have been loaded in the graph
     * @returns array of strings that are the values of the "source" property of nodes stored in sourceMetaIndex
     */
    // NOTE: this isn't as useful anymore, since the curator gives trees IDs 1, 2, etc. for each study
    //    If all studies have just one tree, each will have the treeID '1'
    //    i.e. treeIDs are no longer unique
    /*
    @Deprecated
    public ArrayList<String> getTreeIDList() {
        IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
        ArrayList<String> sourceArrayList = new ArrayList<String>(hits.size());
        while (hits.hasNext()) {
            Node n = hits.next();
            if (n.hasProperty("treeID")) {
                sourceArrayList.add((String) n.getProperty("treeID"));
            }
        }
        return sourceArrayList;
    }
    */
    
    /**
     * Get the list of sources that have been loaded in the graph
     * @returns array of strings that are the values of the "source" property of nodes stored in sourceMetaIndex
     */
    /*
    public ArrayList<String> getSourceList() {
        IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
        ArrayList<String> sourceArrayList = new ArrayList<String>(hits.size());
        while (hits.hasNext()) {
            Node n = hits.next();
            try {
                if (!sourceArrayList.contains((String) n.getProperty("source"))) {
                    sourceArrayList.add((String) n.getProperty("source"));
                }
            } catch(Exception e) {
                System.out.println("source property not found for " + n);
            }
        }
        return sourceArrayList;
    }
    */
    
    // This doesn't seem to be used anywhere (JWB)
    /*
    public ArrayList<String> getDetailedSourceList() {
        IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
        ArrayList<String> sourceList = new ArrayList<String>();
        while (hits.hasNext()) {
            Node n = hits.next();
            if (n.hasProperty("ot:studyPublicationReference") && n.hasProperty("ot:studyId")) {
                if (sourceList.contains((String)n.getProperty("ot:studyId") + "\t" + (String)n.getProperty("ot:studyPublicationReference")) == false) {
                    sourceList.add((String)n.getProperty("ot:studyId") + "\t" + (String)n.getProperty("ot:studyPublicationReference"));
                }
            } else {
                if (sourceList.contains(n.getProperty("source")) == false) {
                    sourceList.add((String) n.getProperty("source"));
                }
            }
        }
        return sourceList;
    }
    */
    
    // TODO: we should store an index of synthesis "name" -> root node. Here we'll just rely on the fact that
    //      the root of the synthesis tree will be "life"...
    /*
    private Node getSynthesisRoot(String treeID) throws TaxonNotFoundException {
        return getGraphRootNode();
    }
    */
    
    /**
     * @returns a JadeTree representation of the synthesis tree with the specified treeID
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     */
    /*
    public JadeTree reconstructSyntheticTree(String treeID, int maxDepth) throws TreeNotFoundException, TaxonNotFoundException {
        Node rootnode = getSynthesisRoot(treeID);
        return reconstructSyntheticTreeHelper(treeID, rootnode, maxDepth);
    }
    */
    
    /**
     * @returns a JadeTree representation of a subtree of the synthesis tree with the specified treeID
     * @param subtreeNodeID the ID of the node that will be used as the root of the returned tree.
     *        the node must be a node in the tree
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     */
    public JadeTree reconstructSyntheticTree(String treeID, long subtreeNodeID, int maxDepth) throws TreeNotFoundException {
        Node rootnode = graphDb.getNodeById(subtreeNodeID);
        return reconstructSyntheticTreeHelper(treeID, rootnode, maxDepth);
    }
    
    /*
    public String findSourceNameFromTreeID(String treeID) throws TreeNotFoundException {
        Node metadataNode = findTreeMetadataNodeFromTreeID(treeID);
        assert metadataNode != null;
        String s = (String)metadataNode.getProperty("source");
        //_LOG.debug("Found source = \"" + s + "\" for treeID = \"" + treeID + "\"");
        return s;
    }
    */
    
    public Node findTreeMetadataNodeFromTreeID(String treeID) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeID(treeID);
        //    Node metadataNode = null;    // not used
        Iterable<Relationship> it = rootnode.getRelationships(RelType.METADATAFOR, Direction.INCOMING);
        for (Relationship rel : it) {
            Node m = rel.getStartNode();
            String mtid = (String) m.getProperty("treeID");
            if (mtid.compareTo(treeID) == 0) {
                return m;
            }
        }
        assert false; // if we find a rootnode, we should be able to get the metadata...
        return null;
    }
    
    /*
    public Node findTreeMetadataNodeFromTreeSourceName (String sourcename) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeSourceName(sourcename);
        //    Node metadataNode = null;    // not used
        Iterable<Relationship> it = rootnode.getRelationships(RelType.METADATAFOR, Direction.INCOMING);
        for (Relationship rel : it) {
            Node m = rel.getStartNode();
            String mtid = (String) m.getProperty("source");
            if (mtid.equals(sourcename)) {
                return m;
            }
        }
        assert false; // if we find a rootnode, we should be able to get the metadata...
        return null;
    }
    */
    
    public Node getRootNodeByTreeID(String treeID) throws TreeNotFoundException {
        IndexHits<Node> hits = sourceRootIndex.get("rootnodeForID", treeID);
        if (hits == null || hits.size() == 0) {
            throw new TreeNotFoundException(treeID);
        }
        // really only need one
        Node rootnode = hits.next();
        hits.close();
        return rootnode;
    }

    
    public Node getRootNodeByTreeSourceName(String sourcename) throws TreeNotFoundException {
        IndexHits<Node> hits = sourceRootIndex.get("rootnode", sourcename);
        if (hits == null || hits.size() == 0) {
            throw new TreeNotFoundException(sourcename);
        }
        // really only need one
        Node rootnode = hits.next();
        hits.close();
        return rootnode;
    }
    
    
    
    
    // ========================================== Reconstruct source trees ==============================================
    
    
    /**
     * @returns a subtree of the source tree with the specified treeID
     * @param subtreeNodeID the ID of the node that will be used as the root of the returned tree.
     *        the node must be a node in the tree
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     */
    /*
    public JadeTree reconstructSourceByTreeID(String treeID, long subtreeNodeID, int maxDepth) throws TreeNotFoundException {
        Node rootnode = graphDb.getNodeById(subtreeNodeID);
        Node metadataNode = findTreeMetadataNodeFromTreeID(treeID);
        return reconstructSourceTreeHelper(metadataNode, rootnode, maxDepth);
    }
    */
    
    /**
     * This will recreate the original source from the graph. At this point this is just a demonstration that it can be done.
     * 
     * @param sourcename
     *        the name of the source
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     */
    /*
    public JadeTree reconstructSource(String sourcename, long subtreeNodeID, int maxDepth) throws TreeNotFoundException {
        Node rootnode = graphDb.getNodeById(subtreeNodeID);
        Node metadataNode = findTreeMetadataNodeFromTreeSourceName(sourcename);
        return reconstructSourceTreeHelper(metadataNode, rootnode, maxDepth);
    }
    */
    
    /**
     * This will recreate the original source from the graph. At this point this is just a demonstration that it can be done.
     * 
     * @param sourcename
     *        the name of the source
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     */
    /*
    public JadeTree reconstructSource(String sourcename, int maxDepth) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeSourceName(sourcename);
        Node metadataNode = findTreeMetadataNodeFromTreeSourceName(sourcename);
        return reconstructSourceTreeHelper(metadataNode, rootnode, maxDepth);
    }
    */
    
    /**
     * @returns the source tree with the specified treeID
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     */
    /*
    public JadeTree reconstructSourceByTreeID(String treeID, int maxDepth) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeID(treeID);
        Node metadataNode = findTreeMetadataNodeFromTreeID(treeID);
        return reconstructSourceTreeHelper(metadataNode, rootnode, maxDepth);
    }
    */
    
    
    // just get the stored original newick string (rather than reconstructing it). mostly for testing pruposes.
    /*
    public String originalNewickBySourceName(String sourcename) throws TreeNotFoundException {
        String tree;
        Node metadataNode = findTreeMetadataNodeFromTreeSourceName(sourcename);
        
        tree = (String) metadataNode.getProperty("newick");
        return (tree);
    }
    */
    
    /**
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     */
    /*
    private JadeTree reconstructSourceTreeHelper(Node metadataNode, Node rootnode, int maxDepth) {
        JadeNode root = new JadeNode();
//        System.out.println("Hiya!");
        if (rootnode.hasProperty("name")) { 
//            System.out.println("root name: " + rootnode.getProperty("name") + "\n");
            root.setName((String)rootnode.getProperty("name") + "______" + (String)rootnode.getProperty(NodeProperty.TAX_UID.propertyName));
        } else {
//            System.out.println("rootnode has no associated name.");
        }
        
        root.assocObject("nodeid", rootnode.getId());
        boolean printlengths = false;
        HashMap<Node, JadeNode> node2JadeNode = new HashMap<Node, JadeNode>();
        node2JadeNode.put(rootnode, root);
        // the following is dead code
        if (false) {  // new way  -- does not (yet) ignore cycles, but does only build requested part of the tree. 
            // does not use the sourceRelIndex...
            FilterByPropertyRelIterator stri;
            stri = FilterByPropertyRelIterator.getSourceTreeRelIterator(rootnode, maxDepth, this, metadataNode);
            while (stri.hasNext()) {
                Relationship r = stri.next();
                Node parNode = r.getEndNode();
                Node childNode = r.getStartNode();
                //_LOG.debug("returned path rel: " + r.toString());
                JadeNode jChild = new JadeNode();
                final long cid = childNode.getId();
                if (childNode.hasProperty("name")) {
                    jChild.setName((String) childNode.getProperty("name") + "______" + (String)childNode.getProperty(NodeProperty.TAX_UID.propertyName));
                } else {
                    //jChild.setName("_unnamed_node_id_" + String.valueOf(cid));
                }
                jChild.assocObject("nodeid", cid);
                if (r.hasProperty("branch_length")) {
                    printlengths = true;
                    jChild.setBL((Double) r.getProperty("branch_length"));
                }
                node2JadeNode.get(parNode).addChild(jChild);
                node2JadeNode.put(childNode, jChild);
            }
        } else {
            // OLD version - deals with cycles via an ignoreCycles set of logic. Need to add this to the FilterByPropertyRelIterator
            // OLD version build the entire tree (though we could easily prune it to return a subtree of the desired start node and depth)
            //TraversalDescription
            String sourcename = (String)metadataNode.getProperty("source");
            IndexHits<Relationship> hitsr = sourceRelIndex.get("source", sourcename);
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
                // System.out.println(trel.getStartNode() + " " + trel.getEndNode());
                startnode_rel_map.get(trel.getStartNode()).add(trel);
                endnode_rel_map.get(trel.getEndNode()).add(trel);
            }
            hitsr.close();
            Stack<Node> treestack = new Stack<Node>();
            Stack<Integer> depthStack = new Stack<Integer>();
            treestack.push(rootnode);
            depthStack.push(new Integer(0));
            HashSet<Node> ignoreCycles = new HashSet<Node>();
            while (treestack.isEmpty() == false) {
                Node tnode = treestack.pop();
                Integer currDepth = depthStack.pop();
                // if (tnode.hasRelationship(Direction.OUTGOING, RelTypes.ISCALLED)) {
                //    System.out.println(tnode + " " + tnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
                // } else {
                //    System.out.println(tnode);
                // }
                // TODO: move down one more node
                if (endnode_rel_map.containsKey(tnode)) {
                    for (int i = 0; i < endnode_rel_map.get(tnode).size(); i++) {
                        Node childNode = endnode_rel_map.get(tnode).get(i).getStartNode();
                        if (endnode_rel_map.containsKey(childNode)) {
                            ArrayList<Relationship> rels = endnode_rel_map.get(childNode);
                            for (int j = 0; j < rels.size(); j++) {
                                if (rels.get(j).hasProperty("licas")) {
                                    long[] licas = (long[]) rels.get(j).getProperty("licas");
                                    if (licas.length > 1) {
                                        for (int k = 1; k < licas.length; k++) {
                                            ignoreCycles.add(graphDb.getNodeById(licas[k]));
                                            //_LOG.debug("ignoring: " + licas[k]);
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
                            if (maxDepth < 0 || currDepth < maxDepth) {
                                treestack.push(tnodechild);
                                depthStack.push(new Integer(1 + currDepth));
                            }
                            JadeNode tchild = new JadeNode();
                            if (tnodechild.hasProperty("name")) {
                                tchild.setName((String) tnodechild.getProperty("name") + "______" + (String)tnodechild.getProperty(NodeProperty.TAX_UID.propertyName));
                            }
                            tchild.assocObject("nodeid", tnodechild.getId());
                            if (endnode_rel_map.get(tnode).get(i).hasProperty("branch_length")) {
                                printlengths = true;
                                tchild.setBL((Double) endnode_rel_map.get(tnode).get(i).getProperty("branch_length"));
                            }
                            node2JadeNode.get(tnode).addChild(tchild);
                            node2JadeNode.put(tnodechild, tchild);
                            // System.out.println("pushing: " + endnode_rel_map.get(tnode).get(i).getStartNode());
                        }
                    }
                }
            } 
        }
        // print the newick string
        JadeTree tree = new JadeTree(root);
        root.assocObject("nodedepth", root.getNodeMaxDepth());
        tree.setHasBranchLengths(printlengths);
        return tree;
    }
    */
    
    public static boolean hasIncomingRel(Node subtreeRoot, RelType relType, String treeID) {
        for (Relationship rel : subtreeRoot.getRelationships(Direction.INCOMING, relType)) {
            boolean matches = false;
            if (treeID == null || treeID.length() == 0) {
                return true;
            } else if (rel.hasProperty("name") && treeID.equals(rel.getProperty("name"))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * This will get information and taxonomy for a tree and the taxonomy
     *      
     **/
    /*
    public void getInformationAndMonophylyTreeVsTaxonomy(String sourcename) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeSourceName(sourcename);
        Node metadataNode = findTreeMetadataNodeFromTreeSourceName(sourcename);
        JadeNode root = new JadeNode();
        if (rootnode.hasProperty("name")) {
            root.setName((String) rootnode.getProperty("name") + "______" + (String)rootnode.getProperty(NodeProperty.TAX_UID.propertyName));
        }
        root.assocObject("nodeid", rootnode.getId());
        boolean printlengths = false;
        HashMap<Node, JadeNode> node2JadeNode = new HashMap<Node, JadeNode>();
        node2JadeNode.put(rootnode, root);
        // the following is dead code
        
        // OLD version - deals with cycles via an ignoreCycles set of logic. Need to add this to the FilterByPropertyRelIterator
        // OLD version build the entire tree (though we could easily prune it to return a subtree of the desired start node and depth)
        //TraversalDescription
        IndexHits<Relationship> hitsr = sourceRelIndex.get("source", sourcename);
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
            // System.out.println(trel.getStartNode() + " " + trel.getEndNode());
            startnode_rel_map.get(trel.getStartNode()).add(trel);
            endnode_rel_map.get(trel.getEndNode()).add(trel);
        }
        hitsr.close();
        Stack<Node> treestack = new Stack<Node>();
        Stack<Integer> depthStack = new Stack<Integer>();
        treestack.push(rootnode);
        depthStack.push(new Integer(0));
        HashSet<Node> ignoreCycles = new HashSet<Node>();
        HashSet<Node> done = new HashSet<Node>();
        while (treestack.isEmpty() == false) {
            Node tnode = treestack.pop();
            if (done.contains(tnode)) {
                continue;
            }
            done.add(tnode);
            // TODO: move down one more node
            if (endnode_rel_map.containsKey(tnode)) {
                for (int i = 0; i < endnode_rel_map.get(tnode).size(); i++) {
                    Node childNode = endnode_rel_map.get(tnode).get(i).getStartNode();
                    if (endnode_rel_map.containsKey(childNode)) {
                        ArrayList<Relationship> rels = endnode_rel_map.get(childNode);
                        for (int j = 0; j < rels.size(); j++) {
                            if (rels.get(j).hasProperty("licas")) {
                                long[] licas = (long[]) rels.get(j).getProperty("licas");
                                if (licas.length > 1) {
                                    for (int k = 1; k < licas.length; k++) {
                                        ignoreCycles.add(graphDb.getNodeById(licas[k]));
                                        //_LOG.debug("ignoring: " + licas[k]);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (endnode_rel_map.containsKey(tnode)) {
                //CHECK IF IT IS TAXONOMY
                //    boolean mn_taxonomy = false;    // NOT USED
                TLongHashSet lhs = new TLongHashSet((long [])tnode.getProperty("mrca"));
                ArrayList<Node> taxon_child = new ArrayList<Node> ();
                if (tnode.hasProperty("name")) {
                    //    mn_taxonomy = true;
                    for (Relationship rel1: tnode.getRelationships(Direction.INCOMING, RelType.TAXCHILDOF)) {
                        Node tttn = rel1.getStartNode();
                        boolean match = false;
                        for (Relationship ttrel1: tttn.getRelationships(Direction.OUTGOING,RelType.STREECHILDOF)) {
                            if (((String)ttrel1.getProperty("source")).equalsIgnoreCase(sourcename)) {
                                match = true;
                                break;
                            }
                        }
                        if (match == false) {
                            taxon_child.add(tttn);
                        }
                    }
                }
                HashSet<Node> donechilds = new HashSet<Node> ();
                for (int i = 0; i < endnode_rel_map.get(tnode).size(); i++) {
                    if (ignoreCycles.contains(endnode_rel_map.get(tnode).get(i).getStartNode()) == false) {
                        Node tnodechild = endnode_rel_map.get(tnode).get(i).getStartNode();
                        if (donechilds.contains(tnodechild) == true) {
                            continue;
                        }
                        donechilds.add(tnodechild);
                        treestack.push(tnodechild);
                        JadeNode tchild = new JadeNode();
                        if (tnodechild.hasProperty("name")) {
                            tchild.setName((String) tnodechild.getProperty("name") + "______" + (String)tnodechild.getProperty(NodeProperty.TAX_UID.propertyName));
                        } else {
                            //GET INFORMATION HERE and NON MONOPHYLY
                            TLongHashSet tlhs = new TLongHashSet((long [])tnodechild.getProperty("mrca"));
                            System.out.println("information: (" + tnode + "->" + tnodechild + ") " + tlhs.size() + "," + lhs.size());
                            for (Node ttn: taxon_child) {
                                TLongHashSet tlhs1 = new TLongHashSet((long [])ttn.getProperty("mrca"));
                                int origsize = tlhs1.size();
                                while (tlhs1.removeAll(tlhs) == true)
                                    continue;
                                if (tlhs1.size() < origsize) {
                                    System.out.println("nonmono: " + ttn.getProperty("tax_uid") + "," + ttn.getProperty("name"));
                                }
                            }
                        }
                        tchild.assocObject("nodeid", tnodechild.getId());
                        if (endnode_rel_map.get(tnode).get(i).hasProperty("branch_length")) {
                            printlengths = true;
                            tchild.setBL((Double) endnode_rel_map.get(tnode).get(i).getProperty("branch_length"));
                        }
                        node2JadeNode.get(tnode).addChild(tchild);
                        node2JadeNode.put(tnodechild, tchild);
                        // System.out.println("pushing: " + endnode_rel_map.get(tnode).get(i).getStartNode());
                    }
                }
            }
        }
    }
    */
    
    // this is the deprecated version
    // get all unique sources supporting a node in the synthetic tree. sorted in alphabetical order.
    // only outgoing rels are reported
    public ArrayList<String> getSynthesisSupportingSources (Node startNode) {
        HashSet<String> sourceSet = new HashSet<String>(); // only want unique sources
        if (startNode.hasRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
            for (Relationship rel : startNode.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
                if (rel.hasProperty("supporting_sources")) {
                    String[] sources = (String[]) rel.getProperty(RelProperty.SUPPORTING_SOURCES.propertyName);
                    for (String s : sources) {
                        sourceSet.add(s);
                    }
                }
            }
        }
        ArrayList<String> sources = new ArrayList<String>(sourceSet);
        return sources;
    }
    
    
    // ================================= methods for trees ====================================
    /*
    public void labelInternalNodesTax(JadeTree tree, MessageLogger logger) {
        //first get the unequivocal ones
        ArrayList<JadeNode> nds = tree.getRoot().getTips();
        for (int j = 0; j < nds.size(); j++) {
            // find all the tip taxa and with doubles pick the taxon closest to the focal group
            Node hitnode = null;
            String processedname = nds.get(j).getName(); //.replace("_", " ");
            // TODO processing syntactic rules like '_' -> ' ' should be done on input parsing. 
            IndexHits<Node> hits = graphNodeIndex.get("name", processedname);
            int numh = hits.size();
            if (numh == 1) {
                hitnode = hits.getSingle();
                //    System.out.println(hitnode);
                nds.get(j).assocObject("ot:ottId", Long.valueOf((String)hitnode.getProperty("tax_uid")));
            }
            hits.close();
        }
        
        //then get the ones that need fixing
        ArrayList<JadeTree> al = new ArrayList<JadeTree> ();
        al.add(tree);
        try {
            PhylografterConnector.fixNamesFromTrees(al, graphDb, false, logger);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        for (int i = 0; i < al.get(0).getInternalNodeCount(); i++) {
            ArrayList<JadeNode> tnds = tree.getInternalNode(i).getTips();
            TLongArrayList nodeSet = new TLongArrayList();
            for (int j = 0; j < tnds.size(); j++) { 
                Long tid = ((Long)tnds.get(j).getObject("ot:ottId"));
                Node tnd = graphTaxUIDNodeIndex.get("tax_uid", tid).getSingle();
                nodeSet.add(tnd.getId());
            }
            //System.out.println(nodeSet);
            if (nodeSet.size() > 1) {
                Node tnd = LicaUtil.getTaxonomicLICA(nodeSet,graphDb);
                //    System.out.println(tnd);
                tree.getInternalNode(i).setName((String)tnd.getProperty("name"));
            }
        }
    }
    */
    
    // ================================= methods needing work ====================================
    
    /**
     * Make a newick tree for just a list of taxa. This needs some work!
     * 
     * TODO: make the newick production external
     * TODO: refactor and combine with the similar method in taxomachine to improve this.
     * 
     * @param innodes
     * @param sources
     * @throws MultipleHitsException 
     */
    /*
    @Deprecated
    public void constructNewickTaxaListTieBreaker(HashSet<Long> innodes, String[] sources) throws TaxonNotFoundException, MultipleHitsException {
        Node lifeNode = findTaxNodeByName("life");
        if (lifeNode == null) {
            System.out.println("name not found");
            return;
        }
        tle.setTaxaList(innodes);
        TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
                .relationships(RelType.MRCACHILDOF, Direction.INCOMING);
        HashSet<Long> visited = new HashSet<Long>();
        Long firstparent = null;
        HashMap<Long, JadeNode> nodejademap = new HashMap<Long, JadeNode>();
        HashMap<Long, Integer> childcount = new HashMap<Long, Integer>();
        System.out.println("traversing");
        for (Node friendnode : MRCACHILDOF_TRAVERSAL.breadthFirst().evaluator(tle).traverse(lifeNode).nodes()) {
            Long parent = null;
            for (Relationship tn : friendnode.getRelationships(Direction.OUTGOING, RelType.MRCACHILDOF)) {
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
                    if (firstparent == null) {
                        firstparent = parent;
                    }
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
    */
    
    // this will have had to check that the node is taxonomic beforehand
    public boolean nodeIsTerminal(Node n) {
        boolean terminal = false;
        if (!n.hasRelationship(RelType.TAXCHILDOF, Direction.INCOMING)) {
            terminal = true;
        }
        return terminal;
    }
    
    /*
    public int getNumberSynthesisTips(Node startNode) {
        int tcount = 0;
        int ncount = 0;
        int onlytax = 0;
        int onlytree = 0;
        int bothtaxtree = 0;
        Node lifeNode = startNode;
        if (lifeNode == null)
            lifeNode = this.getSynthesisMetaNode()
            .getSingleRelationship(RelType.SYNTHMETADATAFOR, Direction.OUTGOING).getEndNode();
        TraversalDescription SYNTHCHILDOF_TRAVERSAL = Traversal.description()
                .relationships(RelType.SYNTHCHILDOF, Direction.INCOMING);
        for (Node friendnode : SYNTHCHILDOF_TRAVERSAL.breadthFirst().traverse(lifeNode).nodes()) {
            ncount += 1;
            boolean tax = false;
            boolean tree = false;
            if (friendnode.hasRelationship(RelType.SYNTHCHILDOF, Direction.INCOMING) == false) {
                tcount += 1;
            }
            if (friendnode.hasRelationship(RelType.TAXCHILDOF, Direction.OUTGOING) == true) {
                tax = true;
            }
            for (Relationship rel: friendnode.getRelationships(RelType.STREECHILDOF, Direction.OUTGOING)) {
                if (rel.getProperty("source").equals("taxonomy") == false) {
                    tree = true;
                    break;
                }
            }
            if (tax == true && tree == false) {
                onlytax += 1;
            }
            if (tax == false && tree == true) {
                onlytree += 1;
            }
            if (tax == true && tree == true) {
                bothtaxtree += 1;
            }
        }
        System.out.println("number of tips: " + tcount);
        System.out.println("number of nodes: " + ncount);
        System.out.println("number of edges supported only by tax: " + onlytax);
        System.out.println("number of edges supported only by tree: " + onlytree);
        System.out.println("number of edges supported both by tax and tree: " + bothtaxtree);
        return tcount;
    }
    */
    
    // From a given node, return all taxonomic descendants which are tips
    /*
    public ArrayList<Node> getTaxonomyDescendantTips(Node startNode) {
            ArrayList<Node> tips = new ArrayList<Node>();
            TraversalDescription TAXCHILDOF_TRAVERSAL = Traversal.description().relationships(RelType.TAXCHILDOF, Direction.INCOMING);
            for (Node curnode : TAXCHILDOF_TRAVERSAL.breadthFirst().traverse(startNode).nodes()) {
                if (curnode.hasRelationship(RelType.TAXCHILDOF, Direction.INCOMING) == false) { // tip
                    tips.add(curnode);
                }
            }
            return tips;
    }
    */
    
    // From a given node, return all taxonomic descendants which are tips
    /*
    public ArrayList<Node> getSynthesisDescendantTips(Node startNode) {
        ArrayList<Node> tips = new ArrayList<Node>();
        HashSet<Node> tipSet = new HashSet<Node>();
        TraversalDescription SYNTHCHILDOF_TRAVERSAL = Traversal.description().relationships(RelType.SYNTHCHILDOF, Direction.INCOMING);
        for (Node curnode : SYNTHCHILDOF_TRAVERSAL.breadthFirst().traverse(startNode).nodes()) {
            if (curnode.hasRelationship(RelType.SYNTHCHILDOF, Direction.INCOMING) == false) { // tip
                tips.add(curnode);
                tipSet.add(curnode);
            }
        }
        System.out.println("Counted " + tipSet.size() + " unique tips.");
        return tips;
    }
    */
    
    // unlike above, this looks at STREE support (including taxonomy), not sources from synthesis alone
    /*
    public ArrayList<String> getSupportingTreeSources (Node startNode) {
        HashSet<String> sourceSet = new HashSet<String>(); // only want unique sources
        if (startNode.hasRelationship(RelType.STREECHILDOF, Direction.OUTGOING)) {
            for (Relationship rel : startNode.getRelationships(RelType.STREECHILDOF, Direction.OUTGOING)) {
                if (rel.hasProperty("source")) {
                    sourceSet.add((String) rel.getProperty(RelProperty.SOURCE.propertyName));
                }
            }
        }
        if (startNode.hasRelationship(RelType.TAXCHILDOF, Direction.INCOMING)) {
            sourceSet.add("taxonomy");
        }
        ArrayList<String> sources = new ArrayList<String>(sourceSet);
        return sources;
    }
    */
    
    /*
    // not useful: assumes only 1 such node, assumes hardcoded constant
    public Node getSynthesisMetaNode() {
        IndexHits<Node> hits = synthMetaIndex.query("name", DRAFTTREENAME);
        Node nd = null;
        if (hits.hasNext()) {
            nd = hits.next();
        }
        hits.close();
        return nd;
    }
    */
    // is the node in the current synthetic tree?
    /*
    public boolean nodeIsInSyntheticTree (Node startNode) {
        boolean inTree = false;
        if (startNode.hasRelationship(RelType.SYNTHCHILDOF)) {
            inTree = true;
        }
        return inTree;
    }
    */
    
    /**
     * Return a List<Node> containing the nodes on the path to the root along the draft tree branches
     * 
     * @param startNode graph node
     * @param treeID synthetic tree identifier
     * @return path to root
     */
    /*
    public List<Node> getDraftTreePathToRoot(Node startNode, String treeID) {
        ArrayList<Node> path = new ArrayList<>();
        Node curParent = startNode;
        boolean atRoot = false;
        while (!atRoot) {
            Iterable<Relationship> parentRels = curParent.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING);
            atRoot = true; // assume we have hit the root until proven otherwise
            for (Relationship m : parentRels) {
                if (String.valueOf(m.getProperty("name")).equals(treeID)) {
                    atRoot = false; // if we found an acceptable relationship to a parent then we're not done yet
                    curParent = m.getEndNode();
                    path.add(curParent);
                    break;
                }
            }
        }
        return path;
    }
    */
    
    /**
     * Internal method to get an array of arrays representing the rootward paths of a given set of nodes,
     * used to calculate mrca and associated procedures. stopNode allows to set an mrca beyond which the traversal
     * won't record the path.
     * 
     * @param tips
     * @param stopNode
     * @return
     */
    // verbose is used because plugins call this code
    /*
    private Map<Node, ArrayList<Node>> getTreeTipRootPathMap(Iterable<Node> tips, Node stopNode, String treeID) {
        
        if (stopNode != null) {
            if (verbose) System.out.println("Setting stop node to " + stopNode);
        }
        // TODO: check if there is only 1 tip, and if it is the same as the mrca. if so, throw UnsupportedOperationException
        // TODO: probably more efficient to use neo4j paths directly instead of java collections to hold the paths
        HashMap<Node, ArrayList<Node>> treeTipRootPathMap = new HashMap<>();
        // populate the tip hash with the paths to the root of the tree
        for (Node curTip : tips) { 
            ArrayList<Node> graphPathToRoot = new ArrayList<>();
            for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING, treeID))
                    .traverse(curTip).nodes()) {
                // stop recording paths at the stop node (allows us to specify an mrca beyond which we don't go)
                if (stopNode != null && m.equals(stopNode)) {
                    //System.out.println("found stop node " + stopNode);
                    break;
                }
                graphPathToRoot.add(0, m);
            }
            if (graphPathToRoot.size() < 1) {
                String ret = "The node " + curTip + " does not seem to be in the draft tree.";
                ret += "; `node_id` is: " + curTip.getProperty("ot_node_id");
                throw new UnsupportedOperationException(ret);
            }
            treeTipRootPathMap.put(curTip, graphPathToRoot);
        }
        
        return treeTipRootPathMap;
    }
    */
    
    // Assumes all query nodes are in the synthetic tree (i.e. should be determined earlier).
    // Doesn't calculate all paths.
    /**
     * Get the MRCA of one or more nodes (interpreted as tips in some theoretical tree) 
     * according to the topology of the draft tree. If only one tip is provided, then 
     * the tip itself is returned.
     * @param nodeset
     * @return
     */
    /*
    public Node getDraftTreeMRCA (Iterable<Node> nodeset) {
        Node mrca = null;
        ArrayList<Node> holder = null;
        int index = 10000000;
        for (Node curNode : nodeset) {
            if (holder != null) {
                for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING)).traverse(curNode).nodes()) {
                    int foo = holder.indexOf(m);
                    if (foo != -1) { // first match. 
                        if (foo < index) {
                            index = foo; // if hit is more rootward than previous hit, record that.
                        }
                        break; // subsequent matches are not informative. bail.
                    }
                }
            } else { // first pass. get full path to root. ideally we would get the shortest path...
                ArrayList<Node> graphPathToRoot = new ArrayList<Node>();
                for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING)).traverse(curNode).nodes()) {
                    graphPathToRoot.add(0, m);
                }
                holder = graphPathToRoot;
            }
        }
        if (!holder.isEmpty()) {
            if (index == 10000000) { // only a single node passed in, but it *is* in the synthetic tree
                mrca = holder.get(holder.size() - 1);
            } else {
                mrca = holder.get(index);
            }
        }
        return mrca;
    }
    */
    
    /*
        // TODO: update to allow multiple synth trees
    // return all sources used in the construction of a synthetic tree
    public ArrayList<String> getSynthesisSourceList () {
        ArrayList<String> sourceList = new ArrayList<>();
        
        Node meta = getSynthesisMetaNode();
        if (meta != null) {
            String [] sourcePrimList = (String []) meta.getProperty("sourcenames");
            // sourceList.addAll(java.util.Arrays.asList(sourcePrimList));
            for (int i = 0; i < sourcePrimList.length; i++) {
                sourceList.add(sourcePrimList[i]);
            }
        }
        return (sourceList);
    }
    */
    
    /*
    // appends '_ottNNNNN' to name, and ensures it is newick-compliant
    public String getOttName (Node curNode) {
        String name;
        try {
            name = String.valueOf(curNode.getProperty("name")) + "_ott" + String.valueOf(curNode.getProperty("tax_uid"));
        } catch(Exception e) {
            name = String.valueOf(curNode.getProperty("name"));
        }
        // make name newick-valid
        name = GeneralUtils.newickName(name);
        return name;
    }
    */
    
    // ================================= current methods ====================================
    
    // check if user-provided synth_id is in the db
    /**
     * @param treeID is the synthetic tree 'tree_id' identifier
     * @return true if in tree, false if not
     */
    public boolean checkExistingSynthTreeID (String treeID) {
        boolean good = false;
        Node test = getSynthesisMetaNodeByName(treeID);
        if (test != null) {
            good = true;
        }
        return good;
    }
    
    
    // synth tree ids are returned sorted, so assumes a certain numbering scheme
    public ArrayList<String> getSynthTreeIDs () {
        HashSet<String> trees = new HashSet<>();
        IndexHits<Node> hits  = synthMetaIndex.query("name", "*");
        for (Node hit : hits) {
            if (hit.hasProperty("tree_id")) {
                trees.add((String) hit.getProperty("tree_id"));
            }
        }
        hits.close();
        ArrayList<String> synthTreeList = new ArrayList<>(trees);
        Collections.sort(synthTreeList);
        return (synthTreeList);
    }
    
    
    public String getMostRecentSynthTreeID () {
        ArrayList<String> allTrees = getSynthTreeIDs();
        String tree = allTrees.get((allTrees.size() - 1));
        return tree;
    }
    
    
    /**
     * @param synthTreeName is the synthetic tree 'tree_id' identifier
     * @return metadata Node for queried synthetic tree id
     */
    public Node getSynthesisMetaNodeByName (String synthTreeName) {
        IndexHits<Node> hits = synthMetaIndex.query("name", synthTreeName);
        Node metaDataNode = null;
        if (hits.hasNext()) {
            metaDataNode = hits.next();
        }
        hits.close();
        return metaDataNode;
    }
    
    
    /**
     * @param synthTreeName is the synthetic tree 'tree_id' identifier
     * @return sourceMapNode Node for queried synthetic tree id
     */
    public Node getSourceMapNodeByName (String synthTreeName) {
        IndexHits<Node> hits = sourceMapIndex.query("name", synthTreeName);
        Node sourceMapNode = null;
        if (hits.hasNext()) {
            sourceMapNode = hits.next();
        }
        hits.close();
        return sourceMapNode;
    }
    
    
    /**
     * @return metadata Node for most recent synthetic tree
     */
    public Node getMostRecentSynthesisMetaNode () {
        String treeid = getMostRecentSynthTreeID();
        Node nd = getSynthesisMetaNodeByName(treeid);
        return nd;
    }
    
    
    // is the node in the specified synthetic tree?
    public boolean nodeIsInSyntheticTree (Node startNode, String treeID) {
        boolean inTree = false;
        for (Relationship rel : startNode.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
            if (String.valueOf(rel.getProperty("name")).equals(treeID)) {
                inTree = true;
                break;
            }
        }
        return inTree;
    }
    
    
    // return a map of all taxonomic information stored at node. a 'blob'
    public HashMap<String, Object> getTaxonBlob (Node n) {
        
        HashMap<String, Object> results = new HashMap<>();
        
        if (n.hasProperty(NodeProperty.NAME.propertyName)) {
            results.put("name", n.getProperty(NodeProperty.NAME.propertyName));
            results.put("unique_name", n.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
            results.put("rank", n.getProperty(NodeProperty.TAX_RANK.propertyName));
            results.put("ott_id", Long.valueOf((String) n.getProperty(NodeProperty.TAX_UID.propertyName)));
            
            // taxonomic sources
            // will have format: "silva:0,ncbi:1,worms:1,gbif:0,irmng:0"
            List<String> taxList = new ArrayList<>(Arrays.asList(String.valueOf(n.getProperty(NodeProperty.TAX_SOURCE.propertyName)).split(",")));
            results.put("tax_sources", taxList);
        }
        return results;
    }
    
    
    public HashMap<String, Object> getNodeBlob (String nodeID, String treeID) throws TaxonNotFoundException {
        Node qnode = findGraphNodeByOTTNodeID(nodeID);
        HashMap<String, Object> results = getNodeBlob(qnode, treeID);
        return results;
    }
    
    
    
    
    // TODO: add in support. currently done in service itself
    public HashMap<String, Object> getNodeBlob (Node n, String treeID) {
        
        HashMap<String, Object> results = new HashMap<>();
        
        results.put("node_id", n.getProperty("ot_node_id"));
        if (n.hasProperty("name")) {
            results.put("taxon", getTaxonBlob(n));
        }
        results.put("num_tips", getNumTipDescendants(n, treeID));
        
        return results;
    }
    
    
    
    
    
    // if node doesn't have an outgoing synth rel it is a root
    public Integer getNumTipDescendants (Node nd, String treeID) {
        Integer numTips = null;
        if (nd.hasRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
            for (Relationship rel : nd.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
                if (String.valueOf(rel.getProperty("name")).equals(treeID)) {
                    if (rel.hasProperty("tip_descendants")) {
                        numTips = (Integer) rel.getProperty("tip_descendants");
                    }
                }
            }
        } else {
            Node meta = getSynthesisMetaNodeByName(treeID);
            numTips = (int) (long) meta.getProperty("num_tips");
        }
        return numTips;
    }
    
    
    // annotations are stored in outgoing rels
    // this works for a single node
    public HashMap<String, Object> getSynthMetadata (Node curNode, String treeID) {
        HashMap<String, Object> results = new HashMap<>();
        if (curNode.hasRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
            for (Relationship rel : curNode.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
                if (String.valueOf(rel.getProperty("name")).equals(treeID)) {
                    // loop over properties
                    for (String key : rel.getPropertyKeys()) {
                        if (!"name".equals(key) && !"tip_descendants".equals(key)) {
                            HashMap<String, String> mapProp = stringToMap((String) rel.getProperty(key));
                            results.put(key, mapProp);
                        }
                    }
                }
            }
        }
        return results;
    }
    
    
    // annotations are stored in outgoing rels
    // same as above, except unique sources also collected (for source id map)
    // i.e. expect to loop over nodes
    public HashMap<String, Object> getSynthMetadataAndUniqueSources (Node curNode,
        String treeID, HashSet<String> uniqueSources) {
        HashMap<String, Object> results = new HashMap<>();
        if (curNode.hasRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
            for (Relationship rel : curNode.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
                if (String.valueOf(rel.getProperty("name")).equals(treeID)) {
                    // loop over properties
                    for (String key : rel.getPropertyKeys()) {
                        if (!"name".equals(key) && !"tip_descendants".equals(key)) {
                            
                            // note: conflicts_with is the only non-key-value pair (instead is an array)
                            // add other properties here if needed ('resolves'?)
                            
                            if ("conflicts_with".equals(key) || "resolved_by".equals(key)) {
                                HashMap <String, ArrayList<String>> arrayProp = stringToMapArray((String) rel.getProperty(key));
                                results.put(key, arrayProp);
                                for (String i : arrayProp.keySet()) {
                                    uniqueSources.add(i);
                                }
                            } else {
                                HashMap<String, String> mapProp = stringToMap((String) rel.getProperty(key));
                                results.put(key, mapProp);
                                for (String i : mapProp.keySet()) {
                                    uniqueSources.add(i);
                                }
                            } 
                        }
                    }
                }
            }
        }
        return results;
    }
    
    
    ///@TODO @TEMP inefficient recursive impl as a placeholder...
    public static ArrayList<String> getNamesOfRepresentativeDescendants(Node subtreeRoot, RelType relType, String treeID) {
        ArrayList<String> toReturn = new ArrayList<>();
        Node firstChild = null;
        Node lastChild = null;
        for (Relationship rel : subtreeRoot.getRelationships(Direction.INCOMING, relType)) {
            boolean matches = false;
            if (treeID == null || treeID.length() == 0) {
                matches = true;
            } else if (rel.hasProperty("name") && treeID.equals(rel.getProperty("name"))) {
                matches = true;
            }
            if (matches) {
                lastChild = rel.getStartNode();
                if (firstChild == null) {
                    firstChild = lastChild;
                }
            }
        }
        if (firstChild != null) {
            if (firstChild.hasProperty("name")) {
                toReturn.add((String) firstChild.getProperty("name"));
            } else {
                ArrayList<String> fc = getNamesOfRepresentativeDescendants(firstChild, relType, treeID);
                if (fc.size() > 0) {
                    toReturn.add(fc.get(0));
                }
            }
            if (firstChild != lastChild) {
                if (lastChild.hasProperty("name")) {
                    toReturn.add((String) lastChild.getProperty("name"));
                } else {
                    ArrayList<String> lc = getNamesOfRepresentativeDescendants(lastChild, relType, treeID);
                    if (lc.size() > 0) {
                        toReturn.add(lc.get(lc.size() -1));
                    }
                }
            }
        }
        return toReturn;
    }
    
    
    /**
     * @return a JadeTree representation of a subtree of the synthesis tree with the specified treeID
     * @param treeID the synthetic tree identifier
     * @param startOTNodeID the node ID of the node that will be used as the root of the returned tree.
     *        the node must be a node in the tree
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     * @throws TaxonNotFoundException
     */
    public JadeTree reconstructSyntheticTree (String treeID, String startOTNodeID, int maxDepth) throws TaxonNotFoundException {
        Node rootnode = findGraphNodeByOTTNodeID(startOTNodeID);
        return reconstructSyntheticTreeHelper(treeID, rootnode, maxDepth);
    }
    
    
    // this is what is used for the old arguson
    /**
     * @return a JadeTree representation of a subtree of the synthesis tree
     * @param maxDepth is the max number of edges between the root and an included node
     *        if non-negative this can be used to prune off subtrees that exceed the threshold
     *        distance from the root. If maxDepth is negative, no threshold is applied
     * @param treeID the synthetic tree identifier
     * @param rootnode the root of the returned tree
     */
    private JadeTree reconstructSyntheticTreeHelper (String treeID, Node rootnode, int maxDepth) {
        
        HashSet<String> uniqueSources = new HashSet<>();
        
        JadeNode root = new JadeNode();
        addCorePropertiesToJadeNode(root, rootnode, treeID);
        
        List<Node> pathToRoot = getPathToRoot(rootnode, RelType.SYNTHCHILDOF, treeID);
        root.assocObject("path_to_root", pathToRoot);
        root.assocObject("treeID", treeID);
        
        HashMap<String, Object> rootProps = getSynthMetadataAndUniqueSources(rootnode, treeID, uniqueSources);
        root.assocObject("annotations", rootProps);
        
        // really just to update uniqueSources with path_to_root nodes
        for (Node n : pathToRoot) { HashMap<String, Object> temp = getSynthMetadataAndUniqueSources(n, treeID, uniqueSources); }
        
        //boolean printlengths = false;
        HashMap<Node, JadeNode> node2JadeNode = new HashMap<>();
        node2JadeNode.put(rootnode, root);
        TraversalDescription synthEdgeTraversal = Traversal.description().relationships(RelType.SYNTHCHILDOF, Direction.INCOMING);
        
        synthEdgeTraversal = synthEdgeTraversal.depthFirst();
        if (maxDepth >= 0) { synthEdgeTraversal = synthEdgeTraversal.evaluator(Evaluators.toDepth(maxDepth)); }
        HashSet<Node> internalNodes = new HashSet<>();
        ArrayList<Node> unnamedChildNodes = new ArrayList<>();
        ArrayList<Node> namedChildNodes = new ArrayList<>();
        
        for (Path path : synthEdgeTraversal.traverse(rootnode)) {
            Relationship furshestRel = path.lastRelationship();
            if (furshestRel != null && furshestRel.hasProperty("name")) {
                String rn = (String) furshestRel.getProperty("name");
                if (rn.equals(treeID)) {
                    Node parNode = furshestRel.getEndNode();
                    Node childNode = furshestRel.getStartNode();
                    internalNodes.add(parNode);
                    JadeNode jChild = new JadeNode();
                    if (childNode.hasProperty("name")) {
                        namedChildNodes.add(childNode);
                    } else {
                        unnamedChildNodes.add(childNode);
                    }
                    addCorePropertiesToJadeNode(jChild, childNode, treeID);
                    HashMap<String, Object> indProps = getSynthMetadataAndUniqueSources(childNode, treeID, uniqueSources);
                    jChild.assocObject("annotations", indProps);
                    node2JadeNode.get(parNode).addChild(jChild);
                    node2JadeNode.put(childNode, jChild);
                }
            }
        }
        if (internalNodes.isEmpty()) {
            root.assocObject("has_children", false);
        }
        for (Node ucn : unnamedChildNodes) {
            if (!internalNodes.contains(ucn)) {
                ArrayList<String> subNameList = getNamesOfRepresentativeDescendants(ucn, RelType.SYNTHCHILDOF, treeID);
                String [] dnA = subNameList.toArray(new String[subNameList.size()]);
                JadeNode cjn = node2JadeNode.get(ucn);
                cjn.assocObject("descendantNameList", dnA);
                Boolean hc = hasIncomingRel(ucn, RelType.SYNTHCHILDOF, treeID);
                cjn.assocObject("has_children", hc);
            }
        }
        for (Node ncn : namedChildNodes) {
            if (!internalNodes.contains(ncn)) {
                JadeNode cjn = node2JadeNode.get(ncn);
                Boolean hc = hasIncomingRel(ncn, RelType.SYNTHCHILDOF, treeID);
                cjn.assocObject("has_children", hc);
            }
        }
        
        // get source id map for all unique sources
        HashMap<String, Object> sourceMap = new HashMap<>();
        for (String ind : uniqueSources) {
            HashMap<String, String> formatSource = getSourceMapIndSource(ind, treeID);
            sourceMap.put(ind, formatSource);
        }
        if (!sourceMap.isEmpty()) {
            root.assocObject("sourceMap", sourceMap);
        }
        JadeTree tree = new JadeTree(root);
        root.assocObject("nodedepth", root.getNodeMaxDepth());
        return tree;
    }
    
    
    private void addCorePropertiesToJadeNode (JadeNode jNd, Node nd, String treeID) {
        if (nd.hasProperty("name")) {
            jNd.setName((String) nd.getProperty("name"));
        }
        
        // this basically replaces (neo4j) nodeid from before
        jNd.assocObject("node_id", nd.getProperty("ot_node_id"));
        
        if (nd.hasProperty("unique_name")) {
            jNd.assocObject("unique_name", nd.getProperty("unique_name"));
        }
        if (nd.hasProperty("tax_source")) {
            String tSrc = (String) nd.getProperty("tax_source");
            HashMap<String, String> taxSources = stringToMap(tSrc);
            jNd.assocObject("tax_sources", taxSources);
        }
        if (nd.hasProperty("tax_rank")) {
            jNd.assocObject("tax_rank", nd.getProperty("tax_rank"));
        }
        if (nd.hasProperty("tax_uid")) {
            jNd.assocObject("ott_id", Long.valueOf((String) nd.getProperty("tax_uid")));
        }
        jNd.assocObject("tip_descendants", getNumTipDescendants(nd, treeID));
    }
    
    
    // a quick way to get tree size without building that actual tree
    public Integer getSubtreeNumTips (String treeID, Node rootnode, int maxDepth) {
        
        Integer numTips = null;
        
        // must be a way to get _just_ the path terminal nodes, but for now this works
        HashSet<Node> childNodes = new HashSet<>();
        HashSet<Node> internalNodes = new HashSet<>();
        
        TraversalDescription synthEdgeTraversal = Traversal.description().
            relationships(RelType.SYNTHCHILDOF, Direction.INCOMING);
        
        synthEdgeTraversal = synthEdgeTraversal.depthFirst();
        //synthEdgeTraversal = synthEdgeTraversal.breadthFirst();
        if (maxDepth >= 0) {
            synthEdgeTraversal = synthEdgeTraversal.evaluator(Evaluators.toDepth(maxDepth));
        }
        for (Path path : synthEdgeTraversal.traverse(rootnode)) {
            Relationship furthestRel = path.lastRelationship();
            if (furthestRel != null && furthestRel.hasProperty("name")) {
                String rn = (String) furthestRel.getProperty("name");
                if (rn.equals(treeID)) {
                    childNodes.add(furthestRel.getStartNode());
                    internalNodes.add(furthestRel.getEndNode());
                }
            }
        }
        childNodes.removeAll(internalNodes);
        numTips = childNodes.size();
        return numTips;
    }
    
    
    public JadeTree extractDepthLimitedSubtree (String treeID, String startOTNodeID, int maxDepth, String labelFormat) throws TaxonNotFoundException {
        Node rootnode = findGraphNodeByOTTNodeID(startOTNodeID);
        return reconstructDepthLimitedSubtree(treeID, rootnode, maxDepth, labelFormat);
    }
    
    
    public JadeTree reconstructDepthLimitedSubtree (String treeID, Node rootnode, int maxDepth, String labelFormat) {
        
        JadeNode root = new JadeNode();
        root.setName(getNodeLabel(rootnode, labelFormat));
        HashMap<Node, JadeNode> node2JadeNode = new HashMap<>();
        node2JadeNode.put(rootnode, root);
        TraversalDescription synthEdgeTraversal = Traversal.description().
            relationships(RelType.SYNTHCHILDOF, Direction.INCOMING);
        
        synthEdgeTraversal = synthEdgeTraversal.depthFirst();
        if (maxDepth >= 0) {
            synthEdgeTraversal = synthEdgeTraversal.evaluator(Evaluators.toDepth(maxDepth));
        }
        
        for (Path path : synthEdgeTraversal.traverse(rootnode)) {
            Relationship furshestRel = path.lastRelationship();
            if (furshestRel != null && furshestRel.hasProperty("name")) {
                String rn = (String) furshestRel.getProperty("name");
                if (rn.equals(treeID)) {
                    Node parNode = furshestRel.getEndNode();
                    Node childNode = furshestRel.getStartNode();
                    JadeNode jChild = new JadeNode();
                    jChild.setName(getNodeLabel(childNode, labelFormat));
                    node2JadeNode.get(parNode).addChild(jChild);
                    node2JadeNode.put(childNode, jChild);
                }
            }
        }
        JadeTree tree = new JadeTree(root);
        return tree;
    }
    
    
    /**
     * Creates and returns a JadeTree object containing the structure defined by the SYNTHCHILDOF relationships present below a given node.
     * External function that uses the ottid to find the root node in the db.
     * 
     * @param startNode
     * @param synthTreeName
     * @param labelFormat
     * @return JadeTree
     */
    public JadeTree extractDraftTree (Node startNode, String synthTreeName, String labelFormat) {
        
        // empty parameters for initial recursion
        System.out.println("Attempting to extract draft tree name: '" + synthTreeName + "'");
        JadeNode parentJadeNode = null;
        Relationship incomingRel = null;
        
        return new JadeTree(extractStoredSyntheticTreeRecur(startNode, parentJadeNode, incomingRel, synthTreeName, labelFormat));
    }
    
    
    /**
     * Recursively creates a JadeNode hierarchy containing the tree structure defined by the SYNTHCHILDOF relationships present below a given node,
     * and returns the root JadeNode. Internal function that requires a Neo4j Node object for the start node.
     */
    private JadeNode extractStoredSyntheticTreeRecur (Node curGraphNode, JadeNode parentJadeNode,
        Relationship incomingRel, String synthTreeName, String labelFormat) {
        
        JadeNode curNode = new JadeNode();
        
        // set the names for the newick string
        curNode.setName(getNodeLabel(curGraphNode, labelFormat));
        curNode.assocObject("nodeID", String.valueOf(curGraphNode.getId()));
        
        // add the current node to the tree we're building
        if (parentJadeNode != null) {
            boolean done = false;
            while (!done) {
                // better way to do this:
                // for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING, treeID)).traverse(curTip).nodes())
                for (Relationship synthChildRel : curGraphNode.getRelationships(Direction.OUTGOING, RelType.SYNTHCHILDOF)) {
                    if (synthTreeName.equals(String.valueOf(synthChildRel.getProperty("name")))) {
                        done = true;
                    }
                }
            }
            parentJadeNode.addChild(curNode);
            if (incomingRel.hasProperty("branch_length")) {
                curNode.setBL((Double) incomingRel.getProperty("branch_length"));
            }
        }
        
        // get the immediate synth children of the current node
        LinkedList<Relationship> synthChildRels = new LinkedList<>();
        for (Relationship synthChildRel : curGraphNode.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)) {
            
            // TODO: here is where we would filter synthetic trees using metadata (or in the traversal itself)
            if (synthTreeName.equals(String.valueOf(synthChildRel.getProperty("name")))) {
                // currently just filtering on name
                synthChildRels.add(synthChildRel);
                //System.out.println("Current rel matches synth tree '" + synthTreeName + "'");
            }
        }
        // recursively add the children to the tree we're building
        for (Relationship synthChildRel : synthChildRels) {
            extractStoredSyntheticTreeRecur(synthChildRel.getStartNode(), curNode, synthChildRel, synthTreeName, labelFormat);
        }
        return curNode;
    }
    
    
    // basically parse the source string into component
    public HashMap<String, String> getSourceMapIndSource (String source, String treeID) {
        HashMap<String, String> res = stringToMap((String) getSourceMapNodeByName(treeID).getProperty(source));
        return res;
    }
    
    
    // conflicts_with is weird, not key-value pair but array
    // pg_2594@tree6014:node1021750,node1021751,node1021752,node1021753,node1021754&pg_1337@tree6167:node1053387
    public HashMap<String, ArrayList<String>> stringToMapArray (String source) {
        HashMap<String, ArrayList<String>> res = new HashMap<>();
        String [] props = source.split("&");
        for (String s : props) {
            String[] indsrc = s.split(":");
            String srcname = indsrc[0];
            String[] nodes = indsrc[1].split(",");
            //List<String> nodes = new
            //Arrays.asList((String[]) meta.getProperty("sources")))
            
            // taxonomic sources
            // will have format: "silva:0,ncbi:1,worms:1,gbif:0,irmng:0"
            //List<String> taxList = new ArrayList<>(Arrays.asList(String.valueOf(n.getProperty(NodeProperty.TAX_SOURCE.propertyName)).split(",")));
            //List<String> taxList = new ArrayList<>(Arrays.asList((String[])indsrc[1].split(",")));
            ArrayList<String> nodelist = new ArrayList<>(Arrays.asList(nodes));
            //Arrays.asList(nodes);
            
            res.put(srcname, nodelist);
        }
        return res;
    }
    
    
    // lots of stuff stored like this bc neo4j cannot have nested properties
    public HashMap<String, String> stringToMap (String source) {
        HashMap<String, String> res = new HashMap<>();
        // format will be: git_sha:c6ce2f9067e9c74ca7b1f770623bde9b6de8bd1f,tree_id:tree1,study_id:ot_157
        String [] props = source.split(",");
        for (String s : props) {
            String[] indsrc = s.split(":");
            if (indsrc.length == 2) {
                res.put(indsrc[0], indsrc[1]);
            }
        }
        return res;
    }
    
    
// Assumes all query nodes are in the synthetic tree (i.e. should be determined earlier).
    // Doesn't calculate all paths.
    public Node getDraftTreeMRCA (Iterable<Node> nodeset, String treeID) {
        Node mrca = null;
        ArrayList<Node> holder = null;
        int index = 10000000;
        for (Node curNode : nodeset) {
            if (holder != null) {
                for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING, treeID))
                        .traverse(curNode).nodes()) {
                    int foo = holder.indexOf(m);
                    if (foo != -1) { // first match. 
                        if (foo < index) {
                            index = foo; // if hit is more rootward than previous hit, record that.
                        }
                        break; // subsequent matches are not informative. bail.
                    }
                }
            } else { // first pass. get full path to root. ideally we would get the shortest path...
                ArrayList<Node> graphPathToRoot = new ArrayList<>();
                for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING, treeID))
                        .traverse(curNode).nodes()) {
                    graphPathToRoot.add(0, m);
                }
                holder = graphPathToRoot;
            }
        }
        if (!holder.isEmpty()) {
            if (index == 10000000) { // only a single node passed in, but it *is* in the synthetic tree
                mrca = holder.get(holder.size() - 1);
            } else {
                mrca = holder.get(index);
            }
        }
        return mrca;
    }
    
    
    public Node getDraftTreeMRTA (Node startNode, String treeID) {
        Node mrta = null;
        for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING, treeID))
                .traverse(startNode).nodes()) {
            if (m.hasProperty(NodeProperty.TAX_UID.propertyName)) {
                mrta = m;
                break;
            }
        }
        return mrta;
    }
    
    
    // valid label formats are: `name`, `id`, or `name_and_id`
    public String getNodeLabel (Node curNode, String labelFormat) {
        String name = "";
        
        // taxonomy node
        if (curNode.hasProperty("name")) {
            if ("name".equals(labelFormat)) {
                name = String.valueOf(curNode.getProperty("name"));
            } else if ("id".equals(labelFormat)) {
                name = String.valueOf(curNode.getProperty("ot_node_id"));
            } else if ("name_and_id".equals(labelFormat)) {
                name = String.valueOf(curNode.getProperty("name")) + "_ott" + String.valueOf(curNode.getProperty("tax_uid"));
            }
        } else {
            name = String.valueOf(curNode.getProperty("ot_node_id"));
        }
        // make name newick-compliant
        name = GeneralUtils.newickName(name);
        return name;
    }
    
    
    // all nodes confirmed to be in specified tree before coming here
    // completely new version. seems to handle knuckles well
    // TODO: associate synthesis data
    public JadeNode getInducedSubtree (List<Node> nodeset, String treeID, String labelFormat) {
        
        if (nodeset.size() < 2) {
            throw new UnsupportedOperationException("Cannot extract a tree with < 2 tips.");
        }
        
        HashMap<Node, ArrayList<Node>> treeTipRootPathMap = new HashMap<>();
        Node mrca = getDraftTreeMRCA(nodeset, treeID);
        
        // only want nodes along the path that are mrcas of the query
        HashSet<Node> uniqueMRCAs = new HashSet<>();
        
        // populate the tip hash with the paths to the root of the tree
        for (Node curTip : nodeset) { 
            // queries (mostly terminals, probably) all need to retained, obviously
            uniqueMRCAs.add(curTip);
            ArrayList<Node> graphPathToRoot = new ArrayList<>();
            for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING, treeID))
                    .traverse(curTip).nodes()) {
                // stop recording paths at the stop node (allows us to specify an mrca beyond which we don't go)
                if (mrca != null && m.equals(mrca)) {
                    graphPathToRoot.add(m); // want to record mrca node
                    break;
                }
                graphPathToRoot.add(m); // don't want it reversed as in previous function
            }
            if (graphPathToRoot.size() < 1) { // probably not possible (nodes have to be in tree, at least 2)
                String ret = "The node " + curTip + " does not seem to be in the draft tree.";
                ret += "; `node_id` is: " + curTip.getProperty("ot_node_id");
                throw new UnsupportedOperationException(ret);
            }
            // collect pairwise mrcas here instead of looping again
            if (!treeTipRootPathMap.isEmpty()) {
                for (Node n : treeTipRootPathMap.keySet()) {
                    ArrayList<Node> existingNodes = treeTipRootPathMap.get(n);
                    for (int i = 0; i < graphPathToRoot.size(); i++) { // only record first match
                        if (existingNodes.contains(graphPathToRoot.get(i))) {
                            uniqueMRCAs.add(graphPathToRoot.get(i));
                            break;
                        }
                    }
                }
            }
            treeTipRootPathMap.put(curTip, graphPathToRoot);
        }
        
        // remove from each path any node not in the mrca set
        for (ArrayList<Node> value : treeTipRootPathMap.values()) {
            value.retainAll(uniqueMRCAs);
        }
        
        HashMap<Node, JadeNode> graphNodeTreeNodeMap = new HashMap<>();
        HashSet<Node> addedNodes = new HashSet<>();
        
        JadeNode root = new JadeNode(); // add names, etc.
        root.assocObject("graphNode", mrca);
        
        root.setName(getNodeLabel(mrca, labelFormat));
        addedNodes.add(mrca); // collect processed graph nodes
        graphNodeTreeNodeMap.put(mrca, root);
        
        // build the root
        for (ArrayList<Node> futureTreeNodes : treeTipRootPathMap.values()) {
            Node curGraphParent = mrca;
            Collections.reverse(futureTreeNodes); // ack. do want it in the other order. fix later
            for (int i = 0; i < futureTreeNodes.size(); i++) {
                Node workingGraphNode = futureTreeNodes.get(i);
                if (!addedNodes.contains(workingGraphNode)) { // skip existing nodes
                    JadeNode childTreeNode = new JadeNode();
                    childTreeNode.assocObject("graphNode", workingGraphNode);
                    childTreeNode.setName(getNodeLabel(workingGraphNode, labelFormat));
                    // add child node to current parent
                    JadeNode parent = graphNodeTreeNodeMap.get(curGraphParent);
                    parent.addChild(childTreeNode);
                    addedNodes.add(workingGraphNode);
                    graphNodeTreeNodeMap.put(workingGraphNode, childTreeNode);
                }
                curGraphParent = workingGraphNode;
            }
        }
        return root;
    }
    
    
    public List<Node> getPathToRoot (Node startNode, RelType relType, String nameToFilterBy) {
        ArrayList<Node> path = new ArrayList<>();
        Node curNode = startNode;
        while (true) {
            Node nextNode = null;
            Iterable<Relationship> parentRels = curNode.getRelationships(relType, Direction.OUTGOING);
            for (Relationship m : parentRels) {
                if (String.valueOf(m.getProperty("name")).equals(nameToFilterBy)) {
                    nextNode = m.getEndNode();
                    break;
                }
            }
            if (nextNode != null) {
                path.add(nextNode);
                curNode = nextNode;
            } else {
                return path;
            }
        }
    }
    
}

