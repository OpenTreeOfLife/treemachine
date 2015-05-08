package opentree.addanalyses;

import gnu.trove.list.array.TLongArrayList;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.opentree.bitarray.TLongBitArray;

public class ConflictEvaluator implements Evaluator{
	TLongBitArray ingroupNodeIds; 
	TLongBitArray outgroupNodeIds;
	TLongArrayList visited = null;
	Node stopNode = null;
	
	public ConflictEvaluator(){
		
	}
	
	public void setOutset(TLongArrayList fids) {
		outgroupNodeIds = new TLongBitArray(fids);
	}
	
	public void setInset(TLongArrayList fids) {
		ingroupNodeIds = new TLongBitArray(fids);
	}
	
	public void setStopNode(Node sn){
		stopNode = sn;
	}
	
	public TLongArrayList getVisitedSet() {
		return visited;
	}
	
	public void setVisitedSet(TLongArrayList fids) {
		visited = fids;
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

		// NOTE: in order to cut down on size, taxnodes outmrca are assumed to be "the rest"
		// they are denoted with not having an outmrca
		boolean isTaxNode = false;
		
		if (curNode.hasProperty("outmrca") == false) {
			isTaxNode = true;
		} else {
			curNodeOutMRCAIds = new TLongBitArray((long[]) curNode.getProperty("outmrca")); // replacing with container
			
		}
		
		if (isTaxNode == false) {
			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == true) {
				if (curNodeOutMRCAIds.containsAny(ingroupNodeIds) == true) {
					if (curNodeMRCAIds.containsAny(ingroupNodeIds) == true) {
						if(curNode.equals(stopNode))
							return Evaluation.INCLUDE_AND_PRUNE;
						return Evaluation.INCLUDE_AND_CONTINUE;
					}
				}
			}
		}else{//it is a taxnode
			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == true) { // if the node does not contain any of the outgroup
				if (curNodeMRCAIds.containsAll(ingroupNodeIds) == false) { // and it does contain all of the ingroup
					return Evaluation.INCLUDE_AND_CONTINUE;
				}
			}
		}
		if(curNode.equals(stopNode))
			return Evaluation.EXCLUDE_AND_PRUNE;
		return Evaluation.EXCLUDE_AND_CONTINUE;
	}

}
