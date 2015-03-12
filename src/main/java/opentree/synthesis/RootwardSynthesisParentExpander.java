package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opentree.bitarray.LongSet;
import org.opentree.bitarray.TLongBitArraySet;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.synthesis.mwis.BaseWeightedIS;
import opentree.synthesis.mwis.BruteWeightedIS;
import opentree.synthesis.mwis.GreedyApproximateWeightedIS;
import opentree.synthesis.mwis.WeightedUndirectedGraph;

import org.apache.commons.lang.ArrayUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.kernel.Traversal;
import org.opentree.graphdb.GraphDatabaseAgent;

import scala.actors.threadpool.Arrays;

public class RootwardSynthesisParentExpander extends SynthesisExpander implements PathExpander {
	
	private Map<Long, HashSet<Relationship>> parentRels; // key is parent, value is incoming rels.
	// key is childnodeid, value is parentnodeid
	HashMap<Long, Long> childParentMap;
	private Node root;
	private List<Long> tips;
	private List<Long> taxOnlyTips; // tips that have only taxonomy rels. do last.
	private List<Long> goodTaxa;    // taxonomy nodes that are traversed.
	// for nonmonophyletic taxa, where to place unsampled tips
	Map<Node, Node> taxOnlyRemap; // key is initial (invalid) parent, value is valid parent.
	private GraphDatabaseAgent gdb;
	
	private boolean VERBOSE = true;

	private boolean USING_RANKS = true;
	
	// look at parents rather than children. only want one relationship
	// only quirk is looking at compatible relationships: given a choice, if they join up, take more resolved
	public RootwardSynthesisParentExpander(Node startNode) {
		
		// key is childnodeid, value is parentnodeid
		childParentMap = new HashMap<Long, Long>();
		
		root = startNode;
		gdb = new GraphDatabaseAgent(root.getGraphDatabase());
		
		tips = Arrays.asList(ArrayUtils.toObject((long[]) root.getProperty(NodeProperty.MRCA.propertyName)));
		taxOnlyTips = new ArrayList<Long>();
		goodTaxa = new ArrayList<Long>();
		taxOnlyRemap = new HashMap<Node, Node>();
		
		long w = new Date().getTime(); // for timing
		parentRels = new HashMap<Long, HashSet<Relationship>>();
		
		System.out.println("Starting synthesis at root: " + root + " [" + root.getProperty("name") +
				"] with: " + tips.size() + " tip descendants.");
		
		for (long tip : tips) {
			Node n = gdb.getNodeById(tip);
			// hacky: terminal nodes need to be in map with empty hashset, or traversal doesn't know it is ok to stop
			parentRels.put(n.getId(), new HashSet<Relationship>());
			
			if (taxonomyOnlyTip(n)) { // process after tips with STREE rels
				System.out.println("\nNode " + n + " [" + n.getProperty("name") + "] has only taxonomy relationships. Process later.");
				taxOnlyTips.add(tip);
				//continue;
			}
			
			boolean done = false;
			while (!done) {
				
				System.out.print("\nVisiting node " + n);
				if (n.hasProperty("name")) {
					System.out.println(" [" + n.getProperty("name") + "]");
				} else {
					System.out.print("\n");
				}
				System.out.println("Looking for best parent relationship");
				
				// collect the best relationships for each potential parent
				Map<Node, Relationship> bestRelForParent = new HashMap<Node, Relationship>();
				
				if (USING_RANKS) {
					int counter = 0;
					for (Relationship r : n.getRelationships(RelType.STREECHILDOF, Direction.OUTGOING)) {
						updateBestRankedRel(bestRelForParent, r);
						counter++;
					}
					System.out.println("Found " + counter + " STREE relationships");
					// if there are > 0 STREE rels, probably don't want to look at any TAX rels
					counter = 0;
					for (Relationship r : n.getRelationships(RelType.TAXCHILDOF, Direction.OUTGOING)) {
						updateBestRankedRel(bestRelForParent, r);
						counter++;
					}
					// don't think you can get more than one of these
					System.out.println("Found " + counter + " TAX relationships");
				} else { // not using ranks
					for (Relationship r : getALLStreeAndTaxRels(n)) {
						bestRelForParent.put(r.getStartNode(), r);
					}
				}
				
				// get all the best nontrivial rels and collect the ids of all descendant tips
				Node bestParent = (Node) bestRelForParent.keySet().toArray()[0];
				if (bestRelForParent.size() > 1) {
					bestParent = findBestParent(bestRelForParent);
				} else {
					System.out.println("Only one parent! Easy-peasy.");
				}
				
				Long parentId = bestParent.getId();
				Long childId = n.getId();
				if (isTaxonomyNode(bestParent)) {
					if (!goodTaxa.contains(parentId)) {
						System.out.println("Adding " + bestParent + " [" + bestParent.getProperty("name") + "] to list of good taxa.");
						goodTaxa.add(parentId);
					}
				}
				childParentMap.put(childId, parentId);
				n = bestParent;
				Relationship parentRel = bestRelForParent.get(bestParent);
				
				if (!parentRels.containsKey(parentId)) {
					HashSet<Relationship> incomingRel = new HashSet<Relationship>();
					incomingRel.add(parentRel);
					parentRels.put(parentId, incomingRel);
				} else {
					parentRels.get(parentId).add(parentRel);
				}
				
				System.out.print("Best parent = " + n);	
				if (n.hasProperty("name")) {
					System.out.println(" [" + n.getProperty("name") + "]");
				} else {
					System.out.print("\n");
				}
				if (childParentMap.containsKey(n.getId())) {
					System.out.println("Hit an existing path. Exit!");
					done = true;
				} else if (n.getId() == root.getId()) {
					System.out.println("Hit root. Exit!");
					done = true;
				}
			}
			if (n == root) {
				System.out.println("Finished at the root");
			} else {
				System.out.println("Finished with: " + n);
			}
		}
		
		// now process taxonomy-only tips, given the results of the other tips.
		/*
		if (taxOnlyTips.size() > 0) {
			System.out.println("/nProcessing " + taxOnlyTips.size() + " taxonomy-only tips.");
			for (int i = 0; i < taxOnlyTips.size(); i++) {
				
				Node n = gdb.getNodeById(taxOnlyTips.get(i));
				Map<Node, Relationship> taxParent = placeTaxonomyOnlyTip(n);
				Long parentID = 
				if (!taxParent.isEmpty()) {
					System.out.println("Adding: " + n);
					if (!childParentMap.containsKey(parentId)) {
						HashSet<Relationship> incomingRel = new HashSet<Relationship>();
						incomingRel.add(parentRel);
						parentRels.put(parentId, incomingRel);
					} else {
						parentRels.get(parentId).add(parentRel);
					}
				}
			}
		}
		*/
		System.out.println("\nFinished with synthesis traversal. Total time: " + (new Date().getTime() - w) / 1000 + " seconds.");
		System.out.println("Synthesis set contains " + childParentMap.size() + " child-parent relationships.");
	}
	
	// not working/completed at the moment
	public Map<Node, Relationship> placeTaxonomyOnlyTip (Node n) {
		Map<Node, Relationship> bestParentNode = new HashMap<Node, Relationship>();;
		
		Relationship taxRel = n.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING);
		Node parent = taxRel.getEndNode();
		
		if (goodTaxa.contains(parent.getId())) { // good parent, as has others passing through
			bestParentNode.put(parent, taxRel);
			return bestParentNode;
		} 
		/*
		else if (taxOnlyRemap.containsKey(parent)) { // bad parent, but previously re-mapped
			parent = taxOnlyRemap.get(parent);
			return parent;
		} else {
			boolean done = false;
			while (!done) { // keep walking back on taxonomy until good parent found
				
			}
		}
		*/
		return bestParentNode;
	}
	
	// very simple at the moment: just take highest ranked parent
	// if a tie (must be from same source), take closest one (unless it has no parents of its own)
	// TODO: check compatibility i.e. if passing through one immediate parent will also (eventually) get you through another
	public Node findBestParent (Map<Node, Relationship> candidateParents) {
		
		System.out.println("Node has " + candidateParents.size() + " potential parents.");
		
		Node bestParentNode = null;
		int bestRank = 0;
		List<Long> bestMRCA = null;
		List<Long> bestOutMRCA = null;
		
		for (Map.Entry<Node, Relationship> entry : candidateParents.entrySet()) {
			boolean accept = false;
			Node candParent = entry.getKey();
			System.out.println("Considering candidate parent: "+ candParent);
			// a hacky check to see if we reach a "deadend"
			if (!hasParents(candParent)) {
				if (candParent.getId() != root.getId()) {
					System.out.println("Candidate " + candParent + " has no parents of its own. Skipping.");
					continue;
				}
			}
			
			Relationship r = entry.getValue();
		    int candRank = getRank(r);
		    List<Long> candMRCA = Arrays.asList(ArrayUtils.toObject((long[]) candParent.getProperty("mrca")));
	    	List<Long> candOutMRCA = null;
	    	if (candParent.hasProperty("outmrca")) {
	    		candOutMRCA = Arrays.asList(ArrayUtils.toObject((long[]) candParent.getProperty("outmrca")));
	    	}
	    	if (bestParentNode == null) { // first valid parent. accept as tentative solution
	    		System.out.println(candParent + " is the first viable candidate parent.");
	    		accept = true;
			}
	    	if (!accept) {
	    		boolean nesting = false; // does nesting status determine decision to be made?
	    		
	    		System.out.println("bestMRCA: " + bestMRCA.toString());
	    		if (bestOutMRCA != null) System.out.println("bestOutMRCA: " + bestOutMRCA.toString());
	    		System.out.println("candMRCA: " + candMRCA.toString());
	    		if (candOutMRCA != null) System.out.println("candOutMRCA: " + candOutMRCA.toString());
	    		
	    		// check if out of new contains any of ingroup of old
	    		System.out.println("Checking for nesting of nodes.");
	    		// is candidate parent a nested child of prevailing parent? if so, take it
	    		if (nestedChildOf(bestMRCA, bestOutMRCA, candMRCA, candOutMRCA)) {
	    			System.out.println(candParent + " is nested child of prevailing parent. Accept!");
	    			nesting = true;
    				accept = true;
	    		}
	    		
	    		// need to check other direction of nesting, so that prevailing parent is not thrown out because of rank alone
	    		if (!nesting) {
	    			if (nestedChildOf(candMRCA, candOutMRCA, bestMRCA, bestOutMRCA)) {
	    				System.out.println("Prevailing parent is nested child of " + candParent + ". Reject candidate parent.");
	    				nesting = true;
	    			}
	    		}
	    		
		    	if (!nesting) {
		    		System.out.println("Nodes are not nested. Looking at ranks.");
		    		if (candRank > bestRank) {
		    			System.out.println("Candidate parent has a higher rank. Accept!");
		    			accept = true;
		    		} else if (candRank == bestRank) {
		    			System.out.println("Candidate parent has same rank as prevailing parent.");
		    			if (candMRCA.size() > bestMRCA.size()) {
							System.out.println("Candidate parent has larger ingroup. Accept!");
							accept = true;
						} else if (candMRCA.size() < bestMRCA.size()) {
							System.out.println("Candidate parent has smaller ingroup. Reject.");
						} else {
							System.out.println("Candidate parent has same size ingroup. What to do?!?");
						}
		    		} else {
		    			System.out.println("Candidate parent has lower rank than prevailing parent. Reject.");
		    		}
		    	}
	    	}
		    if (accept) {
		    	System.out.println("Tentatively accepting " + candParent + " as the best candidate parent.");
		    	bestParentNode = candParent;
		    	bestRank = candRank;
		    	bestMRCA = candMRCA;
		    	//System.out.println("bestMRCA: " + bestMRCA.toString());
		    	bestOutMRCA = candOutMRCA;
		    	//System.out.println("bestOutMRCA: " + bestOutMRCA.toString());
		    }
		}
		return bestParentNode;
	}
	
	private int getRank (Relationship r) {
		int rank = 0;
		if (!isTaxonomyRel(r)) {
			rank = (Integer) r.getProperty("sourcerank");
		}
		return rank;
	}
	
	/**
	 * A simple helper method for code clarity
	 * @param r
	 * @return
	 */
	private boolean isTaxonomyRel (Relationship r) {
		// taxonomy streechildofs don't have this property. this is a temporary property though. in general we should
		// probably not be making streechildofs for taxonomy and then we won't have to worry about differentiating them
		return ! r.hasProperty("sourcerank");
	}
	
	private boolean taxonomyOnlyTip (Node n) {
		boolean taxonomyOnly = true;
		if (n.hasRelationship(RelType.STREECHILDOF, Direction.OUTGOING)) {
			return false;
		}
		return taxonomyOnly;
	}
	
	private boolean isTaxonomyNode (Node n) {
		boolean taxonomy = true;
		if (n.hasProperty("outmrca")) {
			return false;
		}
		return taxonomy;
	}
	
	// is node2 a nested child of node1?
	public boolean nestedChildOf (List<Long> node1MRCA, List<Long> node1OutMRCA, List<Long> node2MRCA, List<Long> node2OutMRCA) {
		boolean nested = false;
		if (node1OutMRCA != null && node2OutMRCA != null) {
			if (!Collections.disjoint(node2OutMRCA, node1MRCA)) {
				if (Collections.disjoint(node1OutMRCA, node2MRCA)) {
					return true;
				}
			}
		} else if (node1OutMRCA == null && node2OutMRCA == null) { // two taxonomy nodes
			if (node1MRCA.containsAll(node2MRCA)) {
				return true;
			}
		} else if (node1OutMRCA == null && node2OutMRCA != null) { // first node is taxonomy, second is not
			List<Long> temp = new ArrayList<Long>(node2MRCA);
			temp.addAll(node2OutMRCA);
			if (node1MRCA.containsAll(temp)) {
				return true;
			}
		} else if (node1OutMRCA != null && node2OutMRCA == null) { // just second node is taxonomy
			if (node1MRCA.containsAll(node2MRCA)) {
				return true;
			}
		}
		return nested;
	}
	
	// check if proposed parent has parents of its own (i.e. not a dead end).
	// doesn't check any further than immediate parent.
	public boolean hasParents (Node n) {
		if (n.hasRelationship(Direction.OUTGOING, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {
			return true;
		}
		return false;
	}

	// high rank is better
	private void updateBestRankedRel (Map<Node, Relationship> bestRelForNode, Relationship rel) {
		Node parent = rel.getEndNode();
		if ( (! bestRelForNode.containsKey(parent)) || getRank(bestRelForNode.get(parent)) < getRank(rel)) {
			bestRelForNode.put(parent, rel); 
		}
	}
	
	private Iterable<Relationship> getALLStreeAndTaxRels (Node n) {
		return Traversal.description()
		.relationships(RelType.STREECHILDOF, Direction.OUTGOING)
		.relationships(RelType.TAXCHILDOF, Direction.OUTGOING)
		.evaluator(Evaluators.toDepth(1))
		.breadthFirst().traverse(n).relationships();
	}
	
	public String getDescription() {
		return "Parent-wise rootward synthesis method";
	}
	
	@Override
	public Iterable<Relationship> expand(Path arg0, BranchState arg1) {
		
		System.out.print("Looking for rels starting a " + arg0.endNode());	
		if (arg0.endNode().hasProperty("name")) {
			System.out.println(" [" + arg0.endNode().getProperty("name") + "]");
		} else {
			System.out.print("\n");
		}
		System.out.println(parentRels.get(arg0.endNode().getId()));
		
		return parentRels.get(arg0.endNode().getId());
	}
	
//	@Override
//	public PathExpander reverse() {
//		throw new UnsupportedOperationException();
//	}
	
	/**
	 * Just a very simple helper function to improve code clarity.
	 * @param relId
	 * @return
	 */
	private long getStartNodeId(Long relId) {
		return gdb.getRelationshipById(relId).getStartNode().getId();
	}

	// =============== END STASIS ===================
}
