package opentree;

import opentree.constants.RelType;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * A simple hacky evaluator to find return only nodes which do not have children in the synthesis tree. Note: if provided with a start node 
 * that is not within the synthesis tree, this will return that node and stop.
 */
public class SynthesisTipEvaluator implements Evaluator {
	public SynthesisTipEvaluator() {}
	@Override
	public Evaluation evaluate(Path arg0) {
		if (arg0.endNode().hasRelationship(Direction.INCOMING, RelType.SYNTHCHILDOF)){
			return Evaluation.EXCLUDE_AND_CONTINUE;
		} else {
			return Evaluation.INCLUDE_AND_PRUNE;
		}
	}
}
