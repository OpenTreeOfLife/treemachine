package opentree;

import jade.tree.JadeNode;

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
	GraphDatabaseAgent graphdb = null;

	public CompatBipartEvaluatorBS() {
	}

	public void setOutset(TLongArrayList fids) {
		outgroupNodeIds = new TLongBitArray(fids);
	}
	
	public void setInset(TLongArrayList fids) {
		ingroupNodeIds = new TLongBitArray(fids);
	}

	public void setVisitedSet(TLongArrayList fids) {
		visited = fids;
	}

	public void setgraphdb(GraphDatabaseAgent gb) {
		graphdb = gb;
	}
	
	public TLongArrayList getVisitedSet() {
		return visited;
	}

	@Override
	public Evaluation evaluate(Path inPath) {
		Node curNode = inPath.endNode();

		if (visited.contains(curNode.getId())) {
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
		visited.add(curNode.getId());

		TLongBitArray curNodeMRCAIds = new TLongBitArray((long[]) curNode.getProperty("mrca"));
		TLongBitArray curNodeOutMRCAIds = null; // never set for taxon nodes
		
		System.out.println(curNode + " inIdSet "+ingroupNodeIds.size() + " outIdSet "+outgroupNodeIds.size()+ " mrca: "+curNodeMRCAIds.size());

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
						System.out.println("    Potential compat found " + curNode);
						return Evaluation.INCLUDE_AND_CONTINUE;
					}
				}
			} else{
				System.out.println("stopping the movement!");
				return Evaluation.EXCLUDE_AND_PRUNE;
			}
		} else { // this is a taxonomy node, so the tests are simpler
			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == false) { // if the node does not contain any of the outgroup
				if (curNodeMRCAIds.containsAll(ingroupNodeIds)) { // and it does contain all of the ingroup
					return Evaluation.INCLUDE_AND_CONTINUE;
				}
			}
		}
		return Evaluation.EXCLUDE_AND_CONTINUE;
	}
}
