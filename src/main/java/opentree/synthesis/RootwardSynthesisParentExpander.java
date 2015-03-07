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
import opentree.synthesis.mwis.TopologicalOrder;
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

	private Map<Long, HashSet<Relationship>> parentRels;
	private GraphDatabaseAgent gdb;

	private boolean trivialTestCase = false;
	
	private boolean VERBOSE = true;

	private boolean USING_RANKS = true;
	
	// look at parents rather than children. only want one relationship
	// only quirk is looking at compatible relationships: given a choice, if they join up, take more resolved
	public RootwardSynthesisParentExpander(Node root) {
		
		//topologicalOrder = new TopologicalOrder(root, RelType.STREECHILDOF, RelType.TAXCHILDOF);
		
		// key is childnodeid, value is parentnodeid
		HashMap<Long, Long> childParentMap = new HashMap<Long, Long>();
		gdb = new GraphDatabaseAgent(root.getGraphDatabase());
		long[] tips = (long[]) root.getProperty(NodeProperty.MRCA.propertyName);
		
		long w = new Date().getTime(); // for timing
		parentRels = new HashMap<Long, HashSet<Relationship>>();
		
		System.out.println("Starting synthesis at root: " + root + " [" + root.getProperty("name") +
				"] with: " + tips.length + " tip descendants.");
		
		for (long tip : tips) {
			Node n = gdb.getNodeById(tip);
			
			// hacky: terminal nodes need to be in map with empty hashset, or traversal doesn't know it is ok to stop
			HashSet<Relationship> incomingRels = new HashSet<Relationship>();
			parentRels.put(n.getId(), incomingRels);
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
		System.out.println("\nFinished with synthesis traversal. Total time: " + (new Date().getTime() - w) / 1000 + " seconds.");
		System.out.println("Synthesis set contains " + childParentMap.size() + " child-parent relationships.");
	}
	
	// high rank is better
	private void updateBestRankedRel(Map<Node, Relationship> bestRelForNode, Relationship rel) {
		Node parent = rel.getEndNode();
		if ( (! bestRelForNode.containsKey(parent)) || getRank(bestRelForNode.get(parent)) < getRank(rel)) {
			bestRelForNode.put(parent, rel); 
		}
	}
	
	private Iterable<Relationship> getALLStreeAndTaxRels(Node n) {
		return Traversal.description()
		.relationships(RelType.STREECHILDOF, Direction.OUTGOING)
		.relationships(RelType.TAXCHILDOF, Direction.OUTGOING)
		.evaluator(Evaluators.toDepth(1))
		.breadthFirst().traverse(n).relationships();
	}
		
	private int getRank(Relationship r) {
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
	private static boolean isTaxonomyRel(Relationship r) {
		// taxonomy streechildofs don't have this property. this is a temporary property though. in general we should
		// probably not be making streechildofs for taxonomy and then we won't have to worry about differentiating them
		return ! r.hasProperty("sourcerank");
	}
	
	public String getDescription() {
		return "parent-wise rootward synthesis method";
	}
	
	// very simple at the moment: just take highest ranked parent
	// if a tie (must be from same source), take closest one (unless it has no parents of its own)
	// TODO: check compatibility i.e. if passing through one immediate parent will also (eventually) get you through another
	public Node findBestParent(Map<Node, Relationship> candidateParents) {
		
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
				System.out.println("Candidate " + candParent + " has no parents of its own. Skipping.");
				continue;
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
	    		boolean nested = false;
	    		
	    		// check if out of new contains any of ingroup of old
		    	if (candOutMRCA != null && bestOutMRCA != null) { // doesn't allow taxonomy nodes. ignore for now (probably not a problem)
		    		System.out.println("Checking for nesting of nodes.");
		    		System.out.println("bestMRCA: " + bestMRCA.toString());
		    		System.out.println("bestOutMRCA: " + bestOutMRCA.toString());
		    		System.out.println("candMRCA: " + candMRCA.toString());
		    		System.out.println("candOutMRCA: " + candOutMRCA.toString());
		    		
		    		if (!Collections.disjoint(candOutMRCA, bestMRCA)) {
		    			if (Collections.disjoint(bestOutMRCA, candMRCA)) {
		    				System.out.println(candParent + " is nested child of prevailing parent. Accept!");
		    				nested = true;
		    				accept = true;
		    			}
					}
		    	}
		    	if (!nested) {
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
//		    	
//			    System.out.println(candParent + "; currRank = " + candRank + "; source = " + r.getProperty("source"));
//			    if (candRank > bestRank) {
//			    	bestParentNode = candParent;
//			    	bestRank = candRank;
//			    	
//			    	bestMRCA = Arrays.asList(ArrayUtils.toObject((long[]) candParent.getProperty("mrca")));
//			    	System.out.println("bestMRCA: " + bestMRCA.toString());
//			    	
//			    	if (candParent.hasProperty("outmrca")) {
//			    		bestOutMRCA = Arrays.asList(ArrayUtils.toObject((long[]) candParent.getProperty("outmrca")));
//			    		System.out.println("bestOutMRCA: " + bestOutMRCA.toString());
//			    	}
//	
//			    } else if (candRank == bestRank) {
//			    	System.out.println("candMrca = " + candMRCA.toString());
//			    	if (candParent.hasProperty("outmrca")) {
//			    		candOutMRCA = Arrays.asList(ArrayUtils.toObject((long[]) candParent.getProperty("outmrca")));
//			    		System.out.println("candOutMrca = " + candOutMRCA.toString());
//			    	}
//			    	
//			    	
//			    }
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
	
	// check if proposed parent has parents of its own (i.e. not a dead end).
	// doesn't check any further than immediate parent.
	public boolean hasParents (Node n) {
		if (n.hasRelationship(Direction.OUTGOING, RelType.STREECHILDOF, RelType.TAXCHILDOF)) {
			return true;
		}
		return false;
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
