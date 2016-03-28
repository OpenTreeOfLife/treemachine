package opentree;

import jade.tree.deprecated.JadeNode;
import jade.tree.deprecated.JadeTree;
import org.opentree.utils.GeneralUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import org.opentree.exceptions.TaxonNotFoundException;
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
    
    private ChildNumberEvaluator cne;
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
    
    
    // ================================= CURRENT METHODS ==================================== //
    
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
     * @return nd Node for most recent synthetic tree
     */
    public Node getMostRecentSynthesisMetaNode () {
        String treeid = getMostRecentSynthTreeID();
        Node nd = getSynthesisMetaNodeByName(treeid);
        return nd;
    }
    
    
    /**
     * is the node in the specified synthetic tree? only used for multi-tree dbs
     * @param nd the graph node of interest
     * @param treeID the synthetic tree identifier e.g. "opentree4.1"
     * @return inTree whether node is in specified synthetic tree
     */
    public boolean nodeIsInSyntheticTree (Node nd, String treeID) {
        boolean inTree = false;
        for (Relationship rel : nd.getRelationships(RelType.SYNTHCHILDOF)) {
            if (String.valueOf(rel.getProperty("name")).equals(treeID)) {
                inTree = true;
                break;
            }
        }
        return inTree;
    }
    
    
    /**
     * return a map of all taxonomic information stored at node
     * @param nd a graph node
     * @return results a hashmap containing a taxon 'blob'
     */
    public HashMap<String, Object> getTaxonBlob (Node nd) {
        HashMap<String, Object> results = new HashMap<>();
        if (nd.hasProperty(NodeProperty.NAME.propertyName)) {
            results.put("name", nd.getProperty(NodeProperty.NAME.propertyName));
            results.put("unique_name", nd.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
            results.put("rank", nd.getProperty(NodeProperty.TAX_RANK.propertyName));
            results.put("ott_id", Long.valueOf((String) nd.getProperty(NodeProperty.TAX_UID.propertyName)));
            // taxonomic sources
            // will have format: "silva:0,ncbi:1,worms:1,gbif:0,irmng:0"
            List<String> taxList = new ArrayList<>(Arrays.asList(String.valueOf(nd.getProperty(NodeProperty.TAX_SOURCE.propertyName)).split(",")));
            results.put("tax_sources", taxList);
        }
        return results;
    }
    
    
    /**
     * uniqueSources is passed along to pick up unique sources so results do not have to be reread
     * @param nd a graph node
     * @param treeID the synthetic tree 'tree_id' identifier
     * @param uniqueSources a hashset collecting unique sources (trees) across nodes
     * @return 
     */
    public HashMap<String, Object> getNodeBlob (Node nd, String treeID, HashSet<String> uniqueSources) {
        HashMap<String, Object> results = new HashMap<>();
        results.put("node_id", nd.getProperty("ot_node_id"));
        if (nd.hasProperty("name")) {
            results.put("taxon", getTaxonBlob(nd));
        }
        results.put("num_tips", getNumTipDescendants(nd, treeID));
        // support/conflict/etc. properties
        HashMap<String, Object> props = getSynthMetadataAndUniqueSources(nd, treeID, uniqueSources);
        results.putAll(props);
        return results;
    }
    
    
    public HashMap<String, Object> getSourceIDMap (HashSet<String> uniqueSources, String treeID) {
        HashMap<String, Object> sourceIDMap = new HashMap<>();
        for (String ind : uniqueSources) {
            HashMap<String, String> formatSource = getSourceMapIndSource(ind, treeID);
            sourceIDMap.put(ind, formatSource);
        }
        return sourceIDMap;
    }
    
    
    // get lineage back to root of synthetic tree from some internal node
    public LinkedList<HashMap<String, Object>> getLineage (Node nd, String treeID, HashSet<String> uniqueSources) {
        LinkedList<HashMap<String, Object>> lineage = new LinkedList<>();
        List<Node> nodeList = getPathToRoot(nd, RelType.SYNTHCHILDOF, treeID);
        for (Node cn : nodeList) {
            HashMap<String, Object> indInfo = getNodeBlob(cn, treeID, uniqueSources);
            lineage.add(indInfo);
        }
        return lineage;
    }
    
    
    /**
     * if node doesn't have an outgoing synth rel it is a root; num_tips stored in metadata node
     * @param nd a graph node
     * @param treeID the synthetic tree identifier
     * @return numTips the number of descendant tips in the specified synthetic tree
     */
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
    
    private static final Set<String> releasedFields = new HashSet<String>();
    static {
        for (String f :
                 new String[]{"supported_by",
                              "conflicts_with",
                              "resolves",
                              "resolved_by",
                              "partial_path_of",
                              "terminal"})
            // excludes "name", "tip_descendants", maybe others

            releasedFields.add(f);
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
                        if (releasedFields.contains(key)) {
                            
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
    
    
    /**
     * Collect all info with which to generate arguson json
     * @param startNode a graph node
     * @param treeID the synthetic tree identifier
     * @param maxDepth the maximum depth of the tree
     * @return results a hashmap containing all relevant info
     */
    public HashMap<String, Object> getArgusonData (Node startNode, String treeID, int maxDepth) {
        HashMap<String, Object> results = new HashMap<>();
        HashSet<String> uniqueSources = new HashSet<>();
        JadeTree tree = reconstructDepthLimitedSubtree(treeID, startNode, maxDepth, "id");
        JadeNode root = tree.getRoot();
        root.assocObject("graph_node", startNode);
        results = processArgusonTree(root, treeID, uniqueSources);
        LinkedList<HashMap<String, Object>> lineage = getLineageArguson(startNode, treeID, uniqueSources);
        results.put("lineage", lineage);
        HashMap<String, Object> sourceMap = getSourceIDMap(uniqueSources, treeID);
        results.put("source_id_map", sourceMap);
        return results;
    }
    
    
    // like getLineage above, but need extra stuff for arguson
    public LinkedList<HashMap<String, Object>> getLineageArguson (Node nd, String treeID, HashSet<String> uniqueSources) {
        LinkedList<HashMap<String, Object>> lineage = new LinkedList<>();
        List<Node> nodeList = getPathToRoot(nd, RelType.SYNTHCHILDOF, treeID);
        for (Node cn : nodeList) {
            HashMap<String, Object> indInfo = getNodeBlobArguson(cn, treeID, uniqueSources);
            lineage.add(indInfo);
        }
        return lineage;
    }
    
    
    // like getNodeBlob above, except non-taxon nodes receive a label
    public HashMap<String, Object> getNodeBlobArguson (Node nd, String treeID, HashSet<String> uniqueSources) {
        HashMap<String, Object> results = new HashMap<>();
        results.put("node_id", nd.getProperty("ot_node_id"));
        if (nd.hasProperty("name")) {
            results.put("taxon", getTaxonBlob(nd));
        } else {
            ArrayList<String> subNameList = getNamesOfRepresentativeDescendants(nd, RelType.SYNTHCHILDOF, treeID);
            results.put("descendant_name_list", subNameList);
        }
        results.put("num_tips", getNumTipDescendants(nd, treeID));
        // support/conflict/etc. properties
        HashMap<String, Object> props = getSynthMetadataAndUniqueSources(nd, treeID, uniqueSources);
        results.putAll(props);
        return results;
    }
    
    
    // recursive traversal
    // each jade node has an associated graph node property
    private HashMap<String, Object> processArgusonTree (JadeNode inNode, String treeID, HashSet<String> uniqueSources) {
        HashMap<String, Object> res = new HashMap<>();
        Node gNode = (Node) inNode.getObject("graph_node");
        HashMap<String, Object> nodeBlob = getNodeBlobArguson(gNode, treeID, uniqueSources);
        res.putAll(nodeBlob);
        ArrayList<Object> children = new ArrayList<>();
        for (int i = 0; i < inNode.getChildCount(); i++) {
            children.add(processArgusonTree(inNode.getChild(i), treeID, uniqueSources));
        }
        if (children.size() > 0) {
            res.put("children", children);
        }
        return res;
    }
    
    
    // for arguson: find descendant taxon names for non-taxon nodes
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
     * a quick way to get tree size without building that actual tree
     * used to bail early if requested tree is too large
     * @param treeID the synthetic tree identifier
     * @param rootnode the graph node that acts as the root of the subtree
     * @param maxDepth the maximum depth of the tree
     * @return numTips the number of terminals in the depth-limited subtree
     */
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
        return reconstructDepthLimitedSubtree(treeID, rootnode, maxDepth, labelFormat, true);
    }

    public JadeTree reconstructDepthLimitedSubtree (String treeID, Node rootnode, int maxDepth, String labelFormat, boolean idsForUnnamed) {
        JadeNode root = new JadeNode();
        root.setName(getNodeLabel(rootnode, labelFormat, idsForUnnamed));
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
                    jChild.setName(getNodeLabel(childNode, labelFormat, idsForUnnamed));
                    jChild.assocObject("graph_node", childNode);
                    node2JadeNode.get(parNode).addChild(jChild);
                    node2JadeNode.put(childNode, jChild);
                }
            }
        }
        JadeTree tree = new JadeTree(root);
        return tree;
    }
    
    
    // basically parse the source string into components
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
            ArrayList<String> nodelist = new ArrayList<>(Arrays.asList(nodes));
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
    
    
    // find closest (rootward) ancestral taxon
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
    
    
    /**
     * Get a node label to be applied to a exported newick
     * @param curNode a graph node
     * @param labelFormat valid label formats are: `name`, `id`, or `name_and_id`
     * @return 
     */
    private String getNodeLabel (Node curNode, String labelFormat) {
        return getNodeLabel(curNode, labelFormat, true);
    }

    private String getNodeLabel (Node curNode, String labelFormat, boolean idsForUnnamed) {
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
        } else if (idsForUnnamed) {
            name = String.valueOf(curNode.getProperty("ot_node_id"));
        }
        // make name newick-compliant
        name = GeneralUtils.newickName(name);
        return name;
    }
    
    
    /**
     * all nodes confirmed to be in specified tree before coming here
     * @param nodeset query nodes. most will be terminals in returned tree
     * @param treeID the synthetic tree identifier
     * @param labelFormat valid label formats are: `name`, `id`, or `name_and_id`
     * @return root the root of the constructed JadeTree
     */
    public JadeNode getInducedSubtree (List<Node> nodeset, String treeID, String labelFormat, boolean idsForUnnamed) {
        
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
        
        root.setName(getNodeLabel(mrca, labelFormat, idsForUnnamed));
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
                    childTreeNode.setName(getNodeLabel(workingGraphNode, labelFormat, idsForUnnamed));
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
    
    
    /**
     * Used to construct 'lineage' objects
     * @param startNode a graph node representing the root of a subtree
     * @param relType relationship type, currently only SYNTHCHILDOF
     * @param nameToFilterBy a filter, currently only the synthetic tree identifier
     * @return path a list of nodes from subtree root to synthetic tree root
     */
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
    
    
    
    
    // ================================= OLD METHODS ==================================== //
    
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
    
    
}

