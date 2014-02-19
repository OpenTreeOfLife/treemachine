package opentree;

import jade.tree.JadeNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;

import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.kernel.Traversal;

/**
 * 
 * This is derived from the LicaBipartEvaluatorBS but will look for Compatible biparts
 * instead of LICA biparts. This is used be the mapcompat
 * 
 * 
 * This uses the mrca and mrca out bits
 */
public class CompatBipartEvaluatorBS implements Evaluator {
	TLongArrayList visited = null;

	TLongBitArray ingroupNodeIds; // this can be larger than smInIdSet and includes the mrca for the matched nodes in the tree (so the dbnodes of the children)
	TLongBitArray outgroupNodeIds; // this is the other part of the bipartition
	TLongArrayList rootnodes;
	GraphDatabaseAgent graphdb = null;
	ArrayList<Relationship> parentRels = null;
	ArrayList<Node> parents = null;
	Node curTreeNode = null;
	
	public CompatBipartEvaluatorBS() {
	}

	public void setOutset(TLongBitArray fids) {
		outgroupNodeIds = new TLongBitArray(fids);
	}
	
	public void setInset(TLongBitArray fids) {
		ingroupNodeIds = new TLongBitArray(fids);
	}
	
	public void setCurTreeNode(Node innode){
		curTreeNode = innode;
	}

	public void setVisitedSet(TLongArrayList fids) {
		visited = fids;
	}
	
	public void setStopNodes(TLongArrayList fids){
		rootnodes = fids;
	}

	public void setgraphdb(GraphDatabaseAgent gb) {
		graphdb = gb;
	}
	
	public void setParentRels(ArrayList<Relationship> parentRels){
		this.parentRels = parentRels;
	}
	
	public void setParentNodes(ArrayList<Node> parents){
		this.parents = parents;
	}
	
	public TLongArrayList getVisitedSet() {
		return visited;
	}
	
	/**
	 * if the parent is also compatible, we don't do the child
	 * curNodeMRCAIds
	 * @return
	 */
	private boolean checkAgainstParents(TLongBitArray curNodeMRCAIds,TLongBitArray curNodeOutMRCAIds,boolean isTaxNode){
		boolean fail = false;
		if(parentRels == null)
			return fail;
		for(Relationship rel1: parentRels){
			//System.out.println("check against parent: "+rel1+ " " + rel1.getStartNode()+" -> "+ rel1.getEndNode());
			TLongBitArray pingroupNodeIds = new TLongBitArray((long[])rel1.getProperty("exclusive_mrca"));
			TLongBitArray poutgroupNodeIds = new TLongBitArray((long[])rel1.getProperty("root_exclusive_mrca"));
			TLongArrayList blowout = new TLongArrayList();
			for(int j=0;j<poutgroupNodeIds.size();j++){
				blowout.add((long[])graphdb.getNodeById(poutgroupNodeIds.get(j)).getProperty("mrca"));
			}
			poutgroupNodeIds.addAll(blowout);
			//basically the same test as below but without the Evaluation
			if (isTaxNode == false) {
				if (curNodeMRCAIds.containsAny(poutgroupNodeIds) == false) {
					if (curNodeOutMRCAIds.containsAny(pingroupNodeIds) == false) {
						if (curNodeMRCAIds.containsAny(pingroupNodeIds) == true) {
							//System.out.println("parent matches, so no match");
							fail = true;
							break;
						}
					}
				}
			} else { // this is a taxonomy node, so the tests are simpler
				if (curNodeMRCAIds.containsAny(poutgroupNodeIds) == false) { // if the node does not contain any of the outgroup
					if (curNodeMRCAIds.containsAll(pingroupNodeIds)) { // and it does contain all of the ingroup
						//System.out.println("parent matches, so no match");
						fail = true;
						break;
					}
				}
			}
		}
		return fail;
	}

	@Override
	public Evaluation evaluate(Path inPath) {
		Node curNode = inPath.endNode();
		if (curNode.getId() == curTreeNode.getId()){
			return Evaluation.EXCLUDE_AND_CONTINUE;
		}
		//System.out.println("path: "+inPath);
		if (visited.contains(curNode.getId())) {
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
		visited.add(curNode.getId());

		TLongBitArray curNodeMRCAIds = new TLongBitArray((long[]) curNode.getProperty("mrca"));
		TLongBitArray curNodeOutMRCAIds = null; // never set for taxon nodes
		
		//System.out.println(curNode + " inIdSet "+ingroupNodeIds.size() + " outIdSet "+outgroupNodeIds.size()+ " mrca: "+curNodeMRCAIds.size()+" "+curNodeMRCAIds.getIntersection(outgroupNodeIds).tl);
		// NOTE: in order to cut down on size, taxnodes outmrca are assumed to be "the rest"
		// they are denoted with not having an outmrca
		boolean isTaxNode = false;
		
		if (curNode.hasProperty("outmrca") == false) {
			isTaxNode = true;
		} else {
			curNodeOutMRCAIds = new TLongBitArray((long[]) curNode.getProperty("outmrca")); // replacing with container
			
		}
		
		if (isTaxNode == false) {
			// System.out.println("passed early quit");
			// if match, extend the mrca and outmrca

			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == false) {
				if (curNodeOutMRCAIds.containsAny(ingroupNodeIds) == false) {
					if (curNodeMRCAIds.containsAny(ingroupNodeIds) == true) {
						//System.out.println("    Potential compat found " + curNode);
						if(rootnodes.contains(curNode.getId())==false){
							//check the parent
							if(checkAgainstParents(curNodeMRCAIds,curNodeOutMRCAIds,isTaxNode)==true)
								return Evaluation.EXCLUDE_AND_PRUNE;
							return Evaluation.INCLUDE_AND_CONTINUE;
						}else{
							//check the parent
							if(checkAgainstParents(curNodeMRCAIds,curNodeOutMRCAIds,isTaxNode)==true)
								return Evaluation.EXCLUDE_AND_PRUNE;
							return Evaluation.INCLUDE_AND_PRUNE;
						}
					}
				}
			} else{
				//System.out.println("stopping the movement!");
				return Evaluation.EXCLUDE_AND_PRUNE;
			}
		} else { // this is a taxonomy node, so the tests are simpler
			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == false) { // if the node does not contain any of the outgroup
				if (curNodeMRCAIds.containsAll(ingroupNodeIds)==true) { // and it does contain all of the ingroup
					//System.out.println("    Potential compat found " + curNode);
					if(rootnodes.contains(curNode.getId())==false){
						//check the parent
						if(checkAgainstParents(curNodeMRCAIds,curNodeOutMRCAIds,isTaxNode)==true)
							return Evaluation.EXCLUDE_AND_PRUNE;
						return Evaluation.INCLUDE_AND_CONTINUE;
					}else{
						//check the parent
						if(checkAgainstParents(curNodeMRCAIds,curNodeOutMRCAIds,isTaxNode)==true)
							return Evaluation.EXCLUDE_AND_PRUNE;
						return Evaluation.INCLUDE_AND_PRUNE;
					}
				}
			}
		}
		if(rootnodes.contains(curNode.getId())==false){
			return Evaluation.EXCLUDE_AND_CONTINUE;
		}else{
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
	}
}
