package opentree;

import gnu.trove.list.array.TLongArrayList;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Stack;

import opentree.constants.RelType;
import opentree.constants.SourceProperty;
import opentree.exceptions.MultipleHitsException;
import opentree.exceptions.OttolIdNotFoundException;
import opentree.exceptions.TaxonNotFoundException;
import opentree.exceptions.TreeNotFoundException;
import opentree.synthesis.DraftTreePathExpander;
import opentree.synthesis.FilterComparisonType;
import opentree.synthesis.RankResolutionMethodInferredPath;
import opentree.synthesis.RankingOrder;
import opentree.synthesis.RelationshipConflictResolver;
import opentree.synthesis.RelationshipFilter;
import opentree.synthesis.RelationshipRanker;
import opentree.synthesis.ResolvingExpander;
import opentree.synthesis.SourcePropertyFilterCriterion;
import opentree.synthesis.SourcePropertyPrioritizedRankingCriterion;
import opentree.synthesis.SourcePropertyRankingCriterion;
import opentree.synthesis.TestValue;
import opentree.synthesis.TreeMakingBandB;


import jade.MessageLogger;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;

public class GraphExplorer extends GraphBase {
    //static Logger _LOG = Logger.getLogger(GraphExplorer.class);
    private SpeciesEvaluator se;
    private ChildNumberEvaluator cne;
    private TaxaListEvaluator tle;
    private boolean sinkLostChildren;
    private HashSet<Long> knownIdsInTree;

    public GraphExplorer(String graphname) {
    	super(graphname);
        setDefaultParameters();
        finishInitialization();
    }

    public GraphExplorer(GraphDatabaseService gdb) {
    	super(gdb);
        setDefaultParameters();
        finishInitialization();
    }

    private void finishInitialization() {
        cne = new ChildNumberEvaluator();
        cne.setChildThreshold(100);
        se = new SpeciesEvaluator();
        tle = new TaxaListEvaluator();
    }

    private void setDefaultParameters() {
    	sinkLostChildren = false;
    }
    
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
    public void setSinkLostChildren(boolean sinkLostChildren) {
    	this.sinkLostChildren = sinkLostChildren;
    	if (sinkLostChildren)
    		this.knownIdsInTree = new HashSet<Long>();
    }

    /**
     * Just check the status of the sinkLostChildren option.
     * 
     * @return sinkLostChildren
     */
    public boolean sinkLostChildrenActive() {
    	return sinkLostChildren;
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

    JadeNode extractDraftSubtreeForTipNodes(Iterable<Node> tips) {
    	
    	HashMap<JadeNode, ArrayList<Node>> treeTipRootPathMap = new HashMap<JadeNode, ArrayList<Node>>();
    	HashMap<Node, JadeNode> graphNodeTreeNodeMap = new HashMap<Node, JadeNode>();
    	HashMap<JadeNode, LinkedList<Node>> treeTipGraphMRCADescendantsMap = new HashMap<JadeNode, LinkedList<Node>>();
    	
    	// populate the tip hash with the paths to the root of the tree
    	for (Node curTip : tips) {
    		
    		JadeNode treeTip = new JadeNode();
    		treeTip.assocObject("graphNode", curTip);
    		treeTip.setName((String) curTip.getProperty("name"));
    		graphNodeTreeNodeMap.put(curTip, treeTip);

    		// testing
    		System.out.println("\ngetting rootward path for " + curTip.getProperty("name"));

    		ArrayList<Node> graphPathToRoot = new ArrayList<Node>();
    		for (Node m : Traversal.description().expand(new DraftTreePathExpander(Direction.OUTGOING)).traverse(curTip).nodes()) {

    			// testing
    			if (m.hasProperty("name")) {
    				System.out.println(m.getProperty("name"));
    			}
    			
    			graphPathToRoot.add(0, m);
    		}    		
    		treeTipRootPathMap.put(treeTip, graphPathToRoot);

    		// add this node's MRCA descendants to the hashmap
    		LinkedList<Node> tipDescendants = new LinkedList<Node>();
    		for (long nid : (long[]) curTip.getProperty("mrca")) {
    			tipDescendants.add(graphDb.getNodeById(nid));
    		}
    		treeTipGraphMRCADescendantsMap.put(treeTip, tipDescendants);
    	}

    	// initialize containers
    	HashMap<JadeNode, LinkedList<JadeNode>> treeNodeTreeTipDescendantsMap = new HashMap<JadeNode, LinkedList<JadeNode>>();
    	LinkedList<JadeNode> stack = new LinkedList<JadeNode>();

    	// set start conditions (add root to stack)
    	JadeNode root = new JadeNode();
    	stack.add(root);
    	treeNodeTreeTipDescendantsMap.put(root, new LinkedList<JadeNode>(treeTipRootPathMap.keySet()));

    	while (stack.size() > 0) {

    		System.out.println(stack.size() + " nodes remaining");
    		
    		JadeNode treeNode = stack.remove(0);

    		// get all the children of the tree leaves (which may be deep graph nodes)
    		LinkedList<Node> allDescendantGraphTips = new LinkedList<Node>();
        	for (JadeNode treeTip : treeNodeTreeTipDescendantsMap.get(treeNode)) {
        		System.out.println(treeTip.getName());
        		allDescendantGraphTips.addAll(treeTipGraphMRCADescendantsMap.get(treeTip));
        	}
        	
        	// get the mrca from the graph and record it
    		Node graphNode = LicaUtil.getDraftTreeLICA(allDescendantGraphTips);
    		treeNode.assocObject("graphNode", graphNode);
    		if (graphNode.hasProperty("name")) {
    			treeNode.setName((String) graphNode.getProperty("name"));
    		}
    		
    		// testing
        	System.out.println("processing " + treeNode.getName());
    		
    		// make a container to hold mrca tipsets for nodes to be added as children of this tree node
    		HashMap<Node, LinkedList<JadeNode>> childNodeTreeTipDescendantsMap = new HashMap<Node, LinkedList<JadeNode>>();
    		
    		// for each leaf descendant of the current node
    		for (JadeNode curDescendant : treeNodeTreeTipDescendantsMap.get(treeNode)) {

    			// testing
    			System.out.println("on descendant " + curDescendant.getName());     			

    			// get the deepest remaining ancestor of this leaf, if none remain then use the current node
    			Node curDeepestAncestor = null;
    			if (treeTipRootPathMap.get(curDescendant).size() > 0) {
    				// remove this ancestor so we don't see it again
    				curDeepestAncestor = treeTipRootPathMap.get(curDescendant).remove(0);
    			} else {
    				curDeepestAncestor = (Node) treeNode.getObject("graphNode");
    			}
    			
    			// testing
    			if (curDeepestAncestor.hasProperty("name")) {
    				System.out.println("deepest remaining ancestor :" + curDeepestAncestor.getProperty("name"));
    			}

    			// make a new entry in the nodes to be added if we haven't seen this ancestor yet
    			if (! childNodeTreeTipDescendantsMap.containsKey(curDeepestAncestor)) {
    				childNodeTreeTipDescendantsMap.put(curDeepestAncestor, new LinkedList<JadeNode>());
    			}

    			// queue this leaf to be added under the appropriate ancestor
    			childNodeTreeTipDescendantsMap.get(curDeepestAncestor).add(curDescendant);
    		}
    		
    		// for each child node in the set to be added
    		for (Entry<Node, LinkedList<JadeNode>> childToAdd : childNodeTreeTipDescendantsMap.entrySet()) {

    			LinkedList<JadeNode> childTreeTipDescendants = childToAdd.getValue();
    			
    			// if there is just one descendant, just add it to the current tree node
    			if (childTreeTipDescendants.size() == 1) {
    				treeNode.addChild(childTreeTipDescendants.get(0));
    				continue;
    			}

    			// there is more than one descendant, so make a new tree node to be added to cur tree node
    			Node childGraphNode = childToAdd.getKey();
    			JadeNode childTreeNode = new JadeNode();
    			childTreeNode.assocObject("graphNode", childGraphNode);
    			if (childGraphNode.hasProperty("name")) {
    				childTreeNode.setName((String) childGraphNode.getProperty("name"));
    			}

    			// if there are exactly two descendants, add them as children of this child and move on
				if (childTreeTipDescendants.size() == 2) {
	    			for (JadeNode treeTip : childTreeTipDescendants) {
						childTreeNode.addChild(treeTip);
					}
					
	    		// if there are more than two descendants
				} else {
					
					System.out.println(treeNode.getName() + " has more than two children, checking if it is a polytomy");

					// get the *shallowest* ancestor for an arbitrary descendant of this child
	    			ArrayList<Node> startNodeAncestors = treeTipRootPathMap.get(childTreeTipDescendants.get(0));
	    			Node startShallowestAncestor = null;
	    			if (startNodeAncestors.size() > 0) {
	    				startShallowestAncestor = startNodeAncestors.get(startNodeAncestors.size() - 1);
	    			}

					// if all the descendants have the same *shallowest* ancestor, then this is a polytomy
	    			boolean isPolytomy = true;
	    			for (JadeNode treeTip : childTreeTipDescendants) {
	    				ArrayList<Node> curAncestors = treeTipRootPathMap.get(treeTip);
	    				Node curShallowestAncestor = curAncestors.get(curAncestors.size() - 1);
	    				
	    				if (! startShallowestAncestor.equals(curShallowestAncestor)) {
	    					// if this isn't a polytomy, then we need to resolve it, so add it to the stack
	    					stack.add(0, childTreeNode);
	    					treeNodeTreeTipDescendantsMap.put(childTreeNode, childTreeTipDescendants);
	    					isPolytomy = false;
	    					break;
	    				}
	    			}
	    			
    				// if this child is a polytomy, add all its children
	    			if (isPolytomy) {
		    			for (JadeNode treeTip : childTreeTipDescendants) {
							childTreeNode.addChild(treeTip);
						}
	    			}
				}
				
				treeNode.addChild(childTreeNode);
				
    		}
    	}
    	
    	// TODO: remove knuckles (keep deepest ancestor?)
    	
    	return root;
	}
    
    
    /**
     * Given a taxonomic name, construct a json object of the subgraph of MRCACHILDOF relationships that are rooted at the specified node. Names that appear in
     * the JSON are taken from the corresponding nodes in the taxonomy graph (using the ISCALLED relationships).
     * 
     * @param name
     *            the name of the root node (should be the name in the graphNodeIndex)
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
            if (friendnode.hasRelationship(Direction.INCOMING, RelType.MRCACHILDOF) == false)
                tips.add(friendnode);
            HashMap<Node, Integer> conflicts_count = new HashMap<Node, Integer>();
            child_parents_map.put(friendnode, new HashSet<Node>());
            int count = 0;
            for (Relationship rel : friendnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
                if (rel.getProperty("source").equals("taxonomy") == true || rel.getProperty("source").equals("ottol") == true)
                    continue;
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
                if (friendnode.hasProperty("name"))
                    pnode.setName((String) friendnode.getProperty("name"));
                else
                    pnode.setName(String.valueOf(friendnode.getId()));
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
                            tnode1.setName(String.valueOf(bnode.getId()));
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

    // ===================================== draft tree synthesis methods ======================================
    
    /**
     * The synthesis method for creating the draft tree. Uses the refactored synthesis classes. This will store the synthesized
     * topology as SYNTHCHILDOF relationships in the graph.
     * 
     * @param startNode this is the beginning node for analysis
     * @param preferredSourceIds this includes the list of preferred sources
     * @param test this will just run through the motions but won't store the synthesis 
     * @throws OttolIdNotFoundException 
     */
    public boolean synthesizeAndStoreDraftTreeBranches(Node startNode, Iterable<String> preferredSourceIds, boolean test) throws OttolIdNotFoundException {        
    	
    	// build the list of ids, have to use generic objects
        ArrayList<Object> sourceIdPriorityList = new ArrayList<Object>();
        for (String sourceId : preferredSourceIds) {
        	sourceIdPriorityList.add(sourceId);
        }
        
        // build the list of ids, have to use generic objects
        ArrayList<Object> justSourcePriorityList = new ArrayList<Object>();
        for (String sourceId : preferredSourceIds) {
        	justSourcePriorityList.add(sourceId.split("_")[0]);
        }
        
        // define the synthesis protocol
        ResolvingExpander draftSynthesisMethod = new ResolvingExpander();

        // set filtering criteria
        //RelationshipFilter rf = new RelationshipFilter();
        //rf.addCriterion(new SourcePropertyFilterCriterion(SourceProperty.YEAR, FilterComparisonType.GREATEROREQUAL, new TestValue(2000), sourceMetaIndex));
        RelationshipFilter rf = new RelationshipFilter();
        HashSet<String> filteredsources = new HashSet<String>();
        //filteredsources.add("26");
        //ignore any source that isn't in our preferred list
        IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
        while (hits.hasNext()) {
            Node n = hits.next();
            if (n.hasProperty("ot:studyId")){
            	if (justSourcePriorityList.contains(n.getProperty("ot:studyId")) == false) {
            		filteredsources.add((String)n.getProperty("ot:studyId"));
            	}
            } else {
            	if (justSourcePriorityList.contains(n.getProperty("source")) == false) {
            		filteredsources.add((String) n.getProperty("source"));
            	}
            }
        }
        System.out.println("filtered: "+filteredsources);
        if (filteredsources.size() > 0) {
        	rf.addCriterion(new SourcePropertyFilterCriterion(SourceProperty.STUDY_ID,FilterComparisonType.CONTAINS,new TestValue(filteredsources),sourceMetaIndex));
        	draftSynthesisMethod.setFilter(rf);
        }
        //if(true == true)
        //	return true;
        // set ranking criteria
        RelationshipRanker rs = new RelationshipRanker();
        rs.addCriterion(new SourcePropertyPrioritizedRankingCriterion(SourceProperty.STUDY_ID, sourceIdPriorityList, sourceMetaIndex));
        rs.addCriterion(new SourcePropertyRankingCriterion(SourceProperty.YEAR, RankingOrder.DECREASING, sourceMetaIndex));
        draftSynthesisMethod.setRanker(rs);

        // set conflict resolution criteria
        RelationshipConflictResolver rcr = new RelationshipConflictResolver(new RankResolutionMethodInferredPath());
        draftSynthesisMethod.setConflictResolver(rcr);
        
        // user feedback
        System.out.println("\n" + draftSynthesisMethod.getDescription());
        
        // set empty parameters for initial recursion
        Node originalParent = null; // hmm. not used.

        // recursively build the tree structure
        knownIdsInTree = new HashSet<Long>();
        Transaction tx = graphDb.beginTx();
        String synthTreeName = DRAFTTREENAME;
        try {
        	for (Relationship rel: Traversal.description().breadthFirst().expand(draftSynthesisMethod).traverse(startNode).relationships()) {
                // store the relationship
        		Node parentNode = rel.getEndNode();
        		Node curNode = rel.getStartNode();
        		
            	if (parentNode != null && test == false) {
            		Relationship newRel = curNode.createRelationshipTo(parentNode, RelType.SYNTHCHILDOF);
            		newRel.setProperty("name", synthTreeName);

            		// get all the sources supporting this relationship
            		HashSet<String> sources = new HashSet<String>();
            		for (Relationship rel2 : curNode.getRelationships(RelType.STREECHILDOF)) {
            			if (rel2.hasProperty("source")) {
            				sources.add(String.valueOf(rel2.getProperty("source")));
            			}
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
        	tx.success();
        } catch (Exception ex) {
        	tx.failure();
        	ex.printStackTrace();
        } finally {
        	tx.finish();
        }
        if (!test) {
	        tx = graphDb.beginTx();
	        try {
	            addMissingChildrenToDraftTreeWhile(startNode,startNode);
	        	tx.success();
	        } catch (Exception ex) {
	        	tx.failure();
	        	ex.printStackTrace();
	        } finally {
	        	tx.finish();
	        }
        }
        System.out.println("exiting the sythesis");
        return true;
    }
    
    /**
     * Creates and returns a JadeTree object containing the structure defined by the SYNTHCHILDOF relationships present below a given node.
     * External function that uses the ottol id to find the root node in the db.
     * 
     * @param nodeId
     * @throws OttolIdNotFoundException 
     */
    public JadeTree extractDraftTree(Node startNode, String synthTreeName) {
    	
        // empty parameters for initial recursion
        JadeNode parentJadeNode = null;
        Relationship incomingRel = null;
        
        return new JadeTree(extractStoredSyntheticTreeRecur(startNode, parentJadeNode, incomingRel, DRAFTTREENAME));
    }

    private List<Node> getPathToRoot(Node startNode, RelType relType, String nameToFilterBy) {
        ArrayList<Node> path = new ArrayList<Node>();
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

    /**
     * Return a List<Node> containing the nodes on the path to the root along the draft tree branches. Will be screwy
     * if there are multiple draft tree branches bearing the current draft tree name.
     * 
     * @param startNode
     * @return path to root
     */
    private List<Long> getDraftTreePathToRoot(Node startNode) {

    	ArrayList<Long> path = new ArrayList<Long>();

    	// testing
//    	System.out.println("getting path to root");
    	
    	Node curParent = startNode;
	    boolean atRoot = false;
	    while (!atRoot) {

	    	// testing
//	    	System.out.println("looking for parents of " + curParent.toString());

	    	Iterable<Relationship> parentRels = curParent.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING);
        	atRoot = true; // assume we have hit the root until proven otherwise
        	for (Relationship m : parentRels) {
        		
        		// testing
//        		System.out.println("current rel name = " + m.getProperty("name") + "; drafttreename = " + DRAFTTREENAME);
        		
        		if (String.valueOf(m.getProperty("name")).equals(DRAFTTREENAME)) {

        	    	atRoot = false; // if we found an acceptable relationship to a parent then we're not done yet
        			curParent = m.getEndNode();

        			// testing
//        			System.out.println("found a parent! " + curParent.toString());
        			
        			path.add(curParent.getId());
        			break;
        		}
        	}
	    }
	    
	    return path;
    }
    
    /**
     * Find the MRCA of the graph nodes using the draft tree topology (requires an acyclic structure to behave properly)
     * 
     * @param descNodes
     * @return licaNode
     */
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
//		 System.out.println("first path");
//		 	for (long nid : referencePathNodeIds) {
//		 	System.out.println(nid);
//		 }
		
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
    
    /**
     * Used to add missing external nodes to the draft tree stored in the graph.
     * @param startNode
     * @param taxRootNode
     */
    private void addMissingChildrenToDraftTree(Node startNode, Node taxRootNode) {
    	
    	// will hold nodes from the taxonomy to check
//        LinkedList<Node> taxNodes = new LinkedList<Node>();

        // to be stored as the 'supporting_sources' property of newly created rels
        String[] supportingSources = new String[1];
        supportingSources[0] = "taxonomy";
        
        // walk taxonomy and save nodes in postorder, WHY? just going to traverse like normal without saving
        TraversalDescription TAXCHILDOF_TRAVERSAL = Traversal.description().relationships(RelType.TAXCHILDOF, Direction.INCOMING);
       // for (Node taxChild : TAXCHILDOF_TRAVERSAL.breadthFirst().traverse(taxRootNode).nodes()) {
         //   taxNodes.add(0, taxChild);
        //}

        // walk taxa from tips down
        for (Node taxNode :  TAXCHILDOF_TRAVERSAL.breadthFirst().traverse(taxRootNode).nodes()) {
            if (taxNode.hasRelationship(Direction.INCOMING, RelType.TAXCHILDOF) == false) {
                // only consider taxa that are not tips
                continue;
            }

            System.out.println(taxNode.getProperty("name"));
            
            LinkedList<Node> nodesToAdd = new LinkedList<Node>();
            ArrayList<Node> nodesInTree = new ArrayList<Node>();
            
            // get all external descendants of this taxon, remember if they're in the tree or not
            for (long cid : (long[]) taxNode.getProperty("mrca")) {
            	Node childNode = graphDb.getNodeById(cid);
//                String childName = GeneralUtils.cleanName((String) childNode.getProperty("name"));

                // `knownIdsInTree` should be populated during synthesis
                if (knownIdsInTree.contains(cid)) {
//                    namesInTree.add(childName);
                    nodesInTree.add(childNode);
//                    System.out.println("name in tree: " + name);

                } else {
//                  taxaToAddNamesNodesMap.put(childName, childNode);
                	nodesToAdd.add(childNode);
                }
            }
            
            // find the mrca of the names in the tree
            Node mrca = null;
//            if (namesInTree.size() > 0) {
            if (nodesInTree.size() > 0) {
                mrca = getLICAForDraftTreeNodes(nodesInTree);
//                System.out.println("found mrca: " + mrca);

            } else {
//                System.out.println("zero names in tree!");
                continue;
            }
            
            // TODO: CURRENTLY THERE ARE SCREWY THINGS HAPPENING WITH GENERA GETTING LUMPED INTO
            // FAMILIES. NEED TO FIX!
            
            // add any children that are not already in tree
            for (Node childNode : nodesToAdd) {

                System.out.println("attempting to add child: " + childNode.getProperty("name")+" "+childNode);

                Relationship newRel = childNode.createRelationshipTo(mrca, RelType.SYNTHCHILDOF);
                newRel.setProperty("name", DRAFTTREENAME);
                newRel.setProperty("supporting_sources", supportingSources);
                knownIdsInTree.add(childNode.getId());
            }            
        }
    }
    
    /**
     * Used to add missing external nodes to the draft tree stored in the graph.
     * @param startNode
     * @param taxRootNode
     */
    private void addMissingChildrenToDraftTreeWhile(Node startNode, Node taxRootNode) {
    	
    	// will hold nodes from the taxonomy to check
//        LinkedList<Node> taxNodes = new LinkedList<Node>();

        // to be stored as the 'supporting_sources' property of newly created rels
        String[] supportingSources = new String[1];
        supportingSources[0] = "taxonomy";
        TLongArrayList taxaleft = new TLongArrayList ((long [])startNode.getProperty("mrca"));
        taxaleft.removeAll(knownIdsInTree);
        
        System.out.println("have to add "+taxaleft.size());
        
        while(taxaleft.size() > 0){
        	System.out.println("taxaleft: "+taxaleft.size());
        	long tid = taxaleft.removeAt(0);
        	Node taxNode = graphDb.getNodeById(tid);
        	if(taxNode.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF))
        		continue;
        	//if it is a tip, get the parent
        	//if(taxNode.hasRelationship(Direction.INCOMING, RelType.TAXCHILDOF)==false){
        	Node ptaxNode = taxNode.getSingleRelationship(RelType.TAXCHILDOF,Direction.OUTGOING).getEndNode();
        	//}
        	 
            LinkedList<Node> nodesToAdd = new LinkedList<Node>();
            TLongArrayList removeLongs = new TLongArrayList();
            ArrayList<Node> nodesInTree = new ArrayList<Node>();
            System.out.println(taxNode.getProperty("name"));
            
        	for (long cid : (long[]) ptaxNode.getProperty("mrca")) {
            	Node childNode = graphDb.getNodeById(cid);
            	// `knownIdsInTree` should be populated during synthesis
                if (knownIdsInTree.contains(cid)) {
                    nodesInTree.add(childNode);
                } /*else {
                	nodesToAdd.add(childNode);
                	removeLongs.add(childNode.getId());
                }*/
            }
        	// find the mrca of the names in the tree
            if (nodesInTree.size() > 1) {
            	Node mrca = null;
                mrca = getLICAForDraftTreeNodes(nodesInTree);
                if (mrca.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF)==true)
                	mrca = mrca.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
                System.out.println("1) attempting to add child: " + taxNode.getProperty("name")+" "+taxNode);
                Relationship newRel = taxNode.createRelationshipTo(mrca, RelType.SYNTHCHILDOF);
                newRel.setProperty("name", DRAFTTREENAME);
                newRel.setProperty("supporting_sources", supportingSources);
                knownIdsInTree.add(taxNode.getId());
                taxaleft.removeAll(removeLongs);
            } else {
            	System.out.println("2) attempting to add child: " + taxNode.getProperty("name")+" "+taxNode);
            	Relationship newRel = taxNode.createRelationshipTo(ptaxNode, RelType.SYNTHCHILDOF);
            	newRel.setProperty("name", DRAFTTREENAME);
            	newRel.setProperty("supporting_sources", supportingSources);
            	//knownIdsInTree.add(taxNode.getId());
            	taxaleft.add(ptaxNode.getId());
            }            
        }
    }
    
    
    // ====================================== extracting Synthetic trees from the db ==========================================

    /**
     * Recursively creates a JadeNode hierarchy containing the tree structure defined by the SYNTHCHILDOF relationships present below a given node,
     * and returns the root JadeNode. Internal function that requires a Neo4j Node object for the start node.
     * 
     * @param nodeId
     */
    private JadeNode extractStoredSyntheticTreeRecur(Node curGraphNode, JadeNode parentJadeNode, Relationship incomingRel, String synthTreeName) {
    	
        JadeNode curNode = new JadeNode();
        
    	// testing
//		System.out.println("child graph node: " + curGraphNode.getId());
//    	if (parentJadeNode != null) {
//    		System.out.println("parent jade node: " + parentJadeNode.toString());
//    	}
        
        if (curGraphNode.hasProperty("name")) {
            curNode.setName(GeneralUtils.cleanName(String.valueOf(curGraphNode.getProperty("name"))));
//            curNode.setName(GeneralUtils.cleanName(curNode.getName()));
        }

        // add the current node to the tree we're building
        if (parentJadeNode != null) {
        	parentJadeNode.addChild(curNode);
            if (incomingRel.hasProperty("branch_length")) {
                curNode.setBL((Double) incomingRel.getProperty("branch_length"));
            }
        }
        
        // get the immediate synth children of the current node
        LinkedList<Relationship> synthChildRels = new LinkedList<Relationship>();
        for (Relationship synthChildRel : curGraphNode.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)) {
        	        	
        	// TODO: here is where we would filter synthetic trees using metadata (or in the traversal itself)
        	if (synthTreeName.equals(String.valueOf(synthChildRel.getProperty("name"))))	{
        		// currently just filtering on name
        		synthChildRels.add(synthChildRel);
        	}
        }

        // recursively add the children to the tree we're building
    	for (Relationship synthChildRel : synthChildRels) {
        	extractStoredSyntheticTreeRecur(synthChildRel.getStartNode(), curNode, synthChildRel, synthTreeName);
        }
        
        return curNode;

    }
    
    // =============================== Synthesis methods using source metadata decisions ONLY ===============================
    
    /**
     * Constructs a newick tree based on the sources. There are currently no other criteria considered in this particular function.
     * 
     * Deprecated. Should be updated to use new synthesis methods.
     * 
     * @param taxName
     * @param sourcesArray
     * @throws MultipleHitsException 
     */
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
    @Deprecated
    private JadeNode sourceSynthesisRecur(Node curGraphNode, JadeNode parentJadeNode, LinkedList<String> sourcesArray, Relationship incomingRel, boolean useTaxonomy) {

        boolean ret = false;
        JadeNode newNode = new JadeNode();

        if (curGraphNode.hasProperty("name")) {
            newNode.setName((String) curGraphNode.getProperty("name"));
            newNode.setName(GeneralUtils.cleanName(newNode.getName()));
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

        /* @Deprecated
        HashSet<Long> putativeLostChildIds = null;
        if (sinkLostChildrenStrict) {
            // will be used to record children that may be lost during conflict tie-breaking
            putativeLostChildIds = new HashSet<Long>();
        } */

        // compare all candidate nodes to choose which to remove/keep
        HashSet<Long> nodesToExclude = new HashSet<Long>();
        for (Long cid_i : candidateNodeIds) {
            if (nodesToExclude.contains(cid_i))
                continue;
            HashSet<Long> descendantIds_i = candNodeDescendantIdsMap.get(cid_i);

            for (Long cid_j : candidateNodeIds) {
                if (cid_j == cid_i || nodesToExclude.contains(cid_j))
                    continue;
                HashSet<Long> descIds_j = candNodeDescendantIdsMap.get(cid_j);

                // get difference of descendant sets for candidates i and j
                HashSet<Long> descIds_iMinusj = new HashSet<Long>(descendantIds_i);
                descIds_iMinusj.removeAll(descIds_j);

                // candidates i and j are in conflict if they overlap
                if ((descendantIds_i.size() - descIds_iMinusj.size()) > 0) {

                    // use source ranking to break ties
                    if (candNodeRankingMap.get(cid_i) < candNodeRankingMap.get(cid_j)) {
                        nodesToExclude.add(cid_j);

                        /* @Deprecated
                        if (sinkLostChildrenStrict) {
                            // record eventual descendants that may be lost by this choice
                            putativeLostChildIds.addAll(descIds_j);
                        } */

                    } else {

                    	// exclude node i, and don't bother seeing if it is better than remaining nodes
                        nodesToExclude.add(cid_i);
                        break;

                        /* @Deprecated
                        if (sinkLostChildrenStrict) {
                            // record eventual descendants that may be lost by this choice
                            putativeLostChildIds.addAll(descIds_i);
                        } else {
                            // we're excluding node i, so don't bother seeing if it is better than remaining nodes
                            break;
                        } */
                    }
                }
            }
        }

        // remove all nodes that lost the tie-breaking
        candidateNodeIds.removeAll(nodesToExclude);
        if (candidateNodeIds.size() == 0) {
            return null;
        }

        /* @Deprecated
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
        } */


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
    
    // ==================================== Synthesis methods using graph decisions ONLY ===================================
    

    /**
     * Constructs a JadeTree object containing a synthetic tree, breaking ties based on branch and bound or exhaustive search.
     * Stores the synthetic tree in the graph as SYNTHCHILDOF relationships, bearing the value of `syntheticTreeName` in their "name" property.
     * 
     * Deprecated. Needs to be reimplemented in the new synthesis methods.
     * 
     * @param nodeId
     * @param useTaxonomy
     * @param useBranchAndBound
     * @param syntheticTreeName
     */
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
    	
        // System.out.println("starting +"+curnode.getId());
        boolean ret = false;
        JadeNode newJadeNode = new JadeNode();

        if (curGraphNode.hasProperty("name")) {
            newJadeNode.setName((String) curGraphNode.getProperty("name"));
            newJadeNode.setName(GeneralUtils.cleanName(newJadeNode.getName()));
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

        int totalmrcas = ((long[]) curGraphNode.getProperty("mrca")).length;
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
            }/* else {
                System.out.println("Exhaustive Pairs");
                TreeMakingExhaustivePairs tmep = new TreeMakingExhaustivePairs();
                ArrayList<Long> testnodesal = new ArrayList<Long>(originaltest);
                ArrayList<Integer> testindices = tmep.calculateExhaustivePairs(testnodesal, storedmrcas);
                for (Integer i : testindices) {
                    temptestnodes.add(testnodesal.get(i));
                }
            }*/
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

    // ========================== Methods for re-adding missing children to synthetic trees/subgraphs =============================
    
    
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

    // ========================================== other methods ==============================================

    /**
     * Get the list of sources that have been loaded in the graph
     * @returns array of strings that are the values of the "source" property of nodes stored in sourceMetaIndex
     */
    public ArrayList<String> getTreeIDList() {
        IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
        ArrayList<String> sourceArrayList = new ArrayList<String>(hits.size());
        while (hits.hasNext()) {
            Node n = hits.next();
            if (n.hasProperty("treeID"))
            	sourceArrayList.add((String) n.getProperty("treeID"));
        }
        return sourceArrayList;
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
            try{
            	sourceArrayList.add((String) n.getProperty("source"));
            }catch(Exception e){
            	System.out.println("source property not found for "+n);
            }
        }
        return sourceArrayList;
    }
    
    public ArrayList<String> getDetailedSourceList() {
        IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
        ArrayList<String> sourceList = new ArrayList<String>();
        while (hits.hasNext()) {
            Node n = hits.next();
            if(n.hasProperty("ot:studyPublicationReference") && n.hasProperty("ot:studyId")){
            	if (sourceList.contains((String)n.getProperty("ot:studyId")+"\t"+(String)n.getProperty("ot:studyPublicationReference")) == false)
            		sourceList.add((String)n.getProperty("ot:studyId")+"\t"+(String)n.getProperty("ot:studyPublicationReference"));
            }else{
            	if (sourceList.contains(n.getProperty("source")) == false)
            		sourceList.add((String) n.getProperty("source"));
            }
        }
        return sourceList;
    }
    

    /**
     * @returns the source tree with the specified treeID
     * @param maxDepth is the max number of edges between the root and an included node
     *      if non-negative this can be used to prune off subtrees that exceed the threshold
     *      distance from the root. If maxDepth is negative, no threshold is applied
     */
    public JadeTree reconstructSourceByTreeID(String treeID, int maxDepth) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeID(treeID);
        Node metadataNode = findTreeMetadataNodeFromTreeID(treeID);
        return reconstructSourceTreeHelper(metadataNode, rootnode, maxDepth);
    }

    /**
     * @returns a subtree of the source tree with the specified treeID
     * @param subtreeNodeID the ID of the node that will be used as the root of the returned tree.
     *      the node must be a node in the tree
     * @param maxDepth is the max number of edges between the root and an included node
     *      if non-negative this can be used to prune off subtrees that exceed the threshold
     *      distance from the root. If maxDepth is negative, no threshold is applied
     */
    public JadeTree reconstructSourceByTreeID(String treeID, long subtreeNodeID, int maxDepth) throws TreeNotFoundException {
        Node rootnode = graphDb.getNodeById(subtreeNodeID);
        Node metadataNode = findTreeMetadataNodeFromTreeID(treeID);
        return reconstructSourceTreeHelper(metadataNode, rootnode, maxDepth);
    }

    // TODO: we should store an index of synthesis "name" -> root node. Here we'll just rely on the fact that
    //      the root of the synthesis tree will be "life"...
    private Node getSynthesisRoot(String treeID) throws TaxonNotFoundException {
        return getGraphRootNode();
    }
    /**
     * @returns a JadeTree representation of the synthesis tree with the specified treeID
     * @param maxDepth is the max number of edges between the root and an included node
     *      if non-negative this can be used to prune off subtrees that exceed the threshold
     *      distance from the root. If maxDepth is negative, no threshold is applied
     */
    public JadeTree reconstructSyntheticTree(String treeID, int maxDepth) throws TreeNotFoundException, TaxonNotFoundException {
        Node rootnode = getSynthesisRoot(treeID);
        return reconstructSyntheticTreeHelper(treeID, rootnode, maxDepth);
    }

    /**
     * @returns a JadeTree representation of a subtree of the synthesis tree with the specified treeID
     * @param subtreeNodeID the ID of the node that will be used as the root of the returned tree.
     *      the node must be a node in the tree
     * @param maxDepth is the max number of edges between the root and an included node
     *      if non-negative this can be used to prune off subtrees that exceed the threshold
     *      distance from the root. If maxDepth is negative, no threshold is applied
     */
    public JadeTree reconstructSyntheticTree(String treeID, long subtreeNodeID, int maxDepth) throws TreeNotFoundException {
        Node rootnode = graphDb.getNodeById(subtreeNodeID);
        return reconstructSyntheticTreeHelper(treeID, rootnode, maxDepth);
    }

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

    public String findSourceNameFromTreeID(String treeID) throws TreeNotFoundException {
        Node metadataNode = findTreeMetadataNodeFromTreeID(treeID);
        assert metadataNode != null;
        String s = (String)metadataNode.getProperty("source");
        //_LOG.debug("Found source = \"" + s + "\" for treeID = \"" + treeID + "\"");
        return s;
    }

    public Node findTreeMetadataNodeFromTreeID(String treeID) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeID(treeID);
        Node metadataNode = null;
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

    public Node findTreeMetadataNodeFromTreeSourceName (String sourcename) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeSourceName(sourcename);
        Node metadataNode = null;
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

    /**
     * 
     */
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

    /**
     * This will recreate the original source from the graph. At this point this is just a demonstration that it can be done.
     * 
     * @param sourcename
     *            the name of the source
     * @param maxDepth is the max number of edges between the root and an included node
     *      if non-negative this can be used to prune off subtrees that exceed the threshold
     *      distance from the root. If maxDepth is negative, no threshold is applied
     */
    public JadeTree reconstructSource(String sourcename, int maxDepth) throws TreeNotFoundException {
        Node rootnode = getRootNodeByTreeSourceName(sourcename);
        Node metadataNode = findTreeMetadataNodeFromTreeSourceName(sourcename);
        return reconstructSourceTreeHelper(metadataNode, rootnode, maxDepth);
    }
    /**
     * This will recreate the original source from the graph. At this point this is just a demonstration that it can be done.
     * 
     * @param sourcename
     *            the name of the source
     * @param maxDepth is the max number of edges between the root and an included node
     *      if non-negative this can be used to prune off subtrees that exceed the threshold
     *      distance from the root. If maxDepth is negative, no threshold is applied
     */
    public JadeTree reconstructSource(String sourcename, long subtreeNodeID, int maxDepth) throws TreeNotFoundException {
        Node rootnode = graphDb.getNodeById(subtreeNodeID);
        Node metadataNode = findTreeMetadataNodeFromTreeSourceName(sourcename);
        return reconstructSourceTreeHelper(metadataNode, rootnode, maxDepth);
    }

    /**
     * @param maxDepth is the max number of edges between the root and an included node
     *      if non-negative this can be used to prune off subtrees that exceed the threshold
     *      distance from the root. If maxDepth is negative, no threshold is applied
     */
    private JadeTree reconstructSourceTreeHelper(Node metadataNode, Node rootnode, int maxDepth) {
        JadeNode root = new JadeNode();
        if (rootnode.hasProperty("name")) {
            root.setName((String) rootnode.getProperty("name"));
        }
        root.assocObject("nodeid", rootnode.getId());
        boolean printlengths = false;
        HashMap<Node, JadeNode> node2JadeNode = new HashMap<Node, JadeNode>();
        node2JadeNode.put(rootnode, root);
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
                    jChild.setName((String) childNode.getProperty("name"));
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
                // System.out.println(trel.getStartNode()+" "+trel.getEndNode());
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
                // if(tnode.hasRelationship(Direction.OUTGOING, RelTypes.ISCALLED)){
                // System.out.println(tnode + " "+tnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
                // }else{
                // System.out.println(tnode);
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
                                            //_LOG.debug("ignoring: "+licas[k]);
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
                                tchild.setName((String) tnodechild.getProperty("name"));
                            }
                            tchild.assocObject("nodeid", tnodechild.getId());
                            if (endnode_rel_map.get(tnode).get(i).hasProperty("branch_length")) {
                                printlengths = true;
                                tchild.setBL((Double) endnode_rel_map.get(tnode).get(i).getProperty("branch_length"));
                            }
                            node2JadeNode.get(tnode).addChild(tchild);
                            node2JadeNode.put(tnodechild, tchild);
                            // System.out.println("pushing: "+endnode_rel_map.get(tnode).get(i).getStartNode());
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

    ///@TODO @TEMP inefficient recursive impl as a placeholder...
    public static ArrayList<String> getNamesOfRepresentativeDescendants(Node subtreeRoot, RelType relType, String treeID) {
        ArrayList<String> toReturn = new ArrayList<String>();
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
    private static void decorateJadeNodeWithCoreProperties(JadeNode jNd, Node nd) {
        if (nd.hasProperty("name")) {
            jNd.setName((String) nd.getProperty("name"));
        }
        final long nid = nd.getId();
        jNd.assocObject("nodeid", nid);
        if (nd.hasProperty("uniqname")) {
            jNd.assocObject("uniqname", nd.getProperty("uniqname"));
        }
        if (nd.hasProperty("tax_source")) {
            jNd.assocObject("taxSource", nd.getProperty("tax_source"));
        }
        if (nd.hasProperty("tax_sourceid")) {
            jNd.assocObject("taxSourceId", nd.getProperty("tax_sourceid"));
        }
        if (nd.hasProperty("tax_rank")) {
            jNd.assocObject("taxRank", nd.getProperty("tax_rank"));
        }
        if (nd.hasProperty("tax_uid")) {
            jNd.assocObject("ottolId", nd.getProperty("tax_uid"));
        }
    }
    /**
     * @param maxDepth is the max number of edges between the root and an included node
     *      if non-negative this can be used to prune off subtrees that exceed the threshold
     *      distance from the root. If maxDepth is negative, no threshold is applied
     */
    private JadeTree reconstructSyntheticTreeHelper(String treeID, Node rootnode, int maxDepth) {
        JadeNode root = new JadeNode();
        decorateJadeNodeWithCoreProperties(root, rootnode);
        root.assocObject("pathToRoot", getPathToRoot(rootnode, RelType.SYNTHCHILDOF, treeID));
        boolean printlengths = false;
        HashMap<Node, JadeNode> node2JadeNode = new HashMap<Node, JadeNode>();
        node2JadeNode.put(rootnode, root);
        TraversalDescription synthEdgeTraversal = Traversal.description().relationships(RelType.SYNTHCHILDOF, Direction.INCOMING);
        //@TEMP should create an evaluator to check the name of the SYNTHCHILDOF rel and not follow paths with the wrong name...
        synthEdgeTraversal = synthEdgeTraversal.depthFirst();
        if (maxDepth >= 0) {
            synthEdgeTraversal = synthEdgeTraversal.evaluator(Evaluators.toDepth(maxDepth));
        }
        HashSet<Node> internalNodes = new HashSet<Node>();
        ArrayList<Node> unnamedChildNodes = new ArrayList<Node>();
        ArrayList<Node> namedChildNodes = new ArrayList<Node>();
        HashMap<String, Node> mentionedSources = new HashMap<String, Node>();
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
                    decorateJadeNodeWithCoreProperties(jChild, childNode);
                    if (furshestRel.hasProperty("branch_length")) {
                        printlengths = true;
                        jChild.setBL((Double) furshestRel.getProperty("branch_length"));
                    }
                    if (furshestRel.hasProperty("supporting_sources")) {
                        String [] supportingSources = (String []) furshestRel.getProperty("supporting_sources");
                        jChild.assocObject("supporting_sources", supportingSources);
                        for (String s : supportingSources) {
                            if (!mentionedSources.containsKey(s)) {
                            	IndexHits<Node> metanodes = null;
                            	try {
                            		metanodes = sourceMetaIndex.get("source", s);
	                            	Node m1 = null;
	                            	if (metanodes.hasNext()) {
	                            		m1 = metanodes.next();
	                            	}
	                        		mentionedSources.put(s, m1);
                            	} finally {
                            		metanodes.close();
                            	}
                            }
                        }
                    }
                    node2JadeNode.get(parNode).addChild(jChild);
                    node2JadeNode.put(childNode, jChild);
                }
            }
        }
        if (internalNodes.isEmpty()) {
            root.assocObject("hasChildren", false);
        }
        for (Node ucn : unnamedChildNodes) {
            if (!internalNodes.contains(ucn)) {
                ArrayList<String> subNameList = getNamesOfRepresentativeDescendants(ucn, RelType.SYNTHCHILDOF, treeID);
                String [] dnA = subNameList.toArray(new String[subNameList.size()]);
                JadeNode cjn = node2JadeNode.get(ucn);
                cjn.assocObject("descendantNameList", dnA);
                Boolean hc = new Boolean(hasIncomingRel(ucn, RelType.SYNTHCHILDOF, treeID));
                cjn.assocObject("hasChildren", hc);
            }
        }
        for (Node ncn : namedChildNodes) {
            if (!internalNodes.contains(ncn)) {
                JadeNode cjn = node2JadeNode.get(ncn);
                Boolean hc = new Boolean(hasIncomingRel(ncn, RelType.SYNTHCHILDOF, treeID));
                cjn.assocObject("hasChildren", hc);
            }
        }

        // print the newick string
        JadeTree tree = new JadeTree(root);
        root.assocObject("nodedepth", root.getNodeMaxDepth());
        if (!mentionedSources.isEmpty()) {
            root.assocObject("sourceMetaList", mentionedSources);
        }
        tree.setHasBranchLengths(printlengths);
        return tree;
    }

    
    // ================================= methods for trees ====================================
    public void labelInternalNodesTax(JadeTree tree, MessageLogger logger){
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
			//	System.out.println(hitnode);
				nds.get(j).assocObject("ot:ottolid", Long.valueOf((String)hitnode.getProperty("tax_uid")));
			}
			hits.close();
		}
    	
    	//then get the ones that need fixing
    	ArrayList<JadeTree> al = new ArrayList<JadeTree> ();
    	al.add(tree);
    	try {
			PhylografterConnector.fixNamesFromTrees(al,graphDb,false, logger);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	for(int i=0;i<al.get(0).getInternalNodeCount();i++){
    		ArrayList<JadeNode> tnds = tree.getInternalNode(i).getTips();
    		TLongArrayList nodeSet = new TLongArrayList();
    		for(int j=0;j<tnds.size();j++){ 
    			Long tid = ((Long)tnds.get(j).getObject("ot:ottolid"));
    			Node tnd = graphTaxUIDNodeIndex.get("tax_uid", tid).getSingle();
    			nodeSet.add(tnd.getId());
    		}
    		//System.out.println(nodeSet);
    		if(nodeSet.size() > 1){
    			Node tnd = LicaUtil.getTaxonomicLICA(nodeSet,graphDb);
    		//	System.out.println(tnd);
    			tree.getInternalNode(i).setName((String)tnd.getProperty("name"));
    		}
    	}
    }
    
    
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
}
