package opentree;

import gnu.trove.list.array.TLongArrayList;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Neo4j Traversal Evaluator which prunes paths when it finds a node with 
 *  an indegree greater or equal the threshold set in `setChildThreshold`
 */
public class LicaContainsAllEvaluator implements Evaluator{
	TLongArrayList inIdSet = null;
	TLongArrayList smInIdSet = null;
	TLongArrayList visited = null;
	public LicaContainsAllEvaluator(){}
	public void setinIDset(TLongArrayList fids){
		inIdSet = fids;
	}
	public void setSmInSet(TLongArrayList fids){
		smInIdSet = fids;
	}
	public void setVisitedSet(TLongArrayList fids){
		visited = fids;
	}
	public TLongArrayList getVisitedSet(){
		return visited;
	}
	@Override
	public Evaluation evaluate(Path arg0) {
		Node tn = arg0.endNode();
		if(visited.contains(tn.getId())){
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
		visited.add(tn.getId());
		TLongArrayList Ldbnodei = new TLongArrayList((long[]) tn.getProperty("mrca"));
		//Ldbnodei.sort();
		//try the small one first if it exists
		if(smInIdSet!= null){
			if(((smInIdSet.size()*2)-1)<inIdSet.size()){//only do this if we have smaller than half the size of the array
				if(Ldbnodei.containsAll(smInIdSet)==false){//some overlap in inbipart
					return Evaluation.EXCLUDE_AND_CONTINUE;
				}
			}
		}
		if (Ldbnodei.containsAll(inIdSet)) {
			return Evaluation.INCLUDE_AND_PRUNE;
		}else{
			return Evaluation.EXCLUDE_AND_CONTINUE;
		}
	}
}
