package opentree;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

public class ChildNumberEvaluator implements Evaluator{
	int child_threshold = 100;
	Node startNode = null;
	public ChildNumberEvaluator(){}
	public void setChildThreshold(int n){
		child_threshold = n;
	}
	public void setStartNode(Node n){
		startNode = n;
	}
	public Evaluation evaluate(Path arg0) {
		boolean tthresh = false;
		int count = 0;
		for(Relationship rel: arg0.endNode().getRelationships(Direction.INCOMING)){
			count += 1;
			if (count >= child_threshold){
				tthresh = true;
				break;
			}
		}
		if(tthresh == false || arg0.endNode().getId() == startNode.getId()){
			return Evaluation.INCLUDE_AND_CONTINUE;
		}
		return Evaluation.INCLUDE_AND_PRUNE;
	}
}
