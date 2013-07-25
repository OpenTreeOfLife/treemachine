package opentree;

import gnu.trove.list.array.TLongArrayList;

import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Neo4j Traversal Evaluator that checks nodes to make sure their mrca and outmrca properties are set. Nodes that need
 * these properties to be updated will be returned as part of the path.
 */
public class MRCAPropertyValidatingEvaluator implements Evaluator {

	public MRCAPropertyValidatingEvaluator(){}
	
	@Override
	public Evaluation evaluate(Path inPath) {

		Node curNode = inPath.endNode();
		
		// get all incoming STREECHILDOF rels
		Iterable<Relationship> childRels = curNode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF);
		
		// check if this node's mrca property has all the node ids that its children do
		TLongArrayList curNodeDescendantIds = new TLongArrayList((long[]) curNode.getProperty("mrca"));
		for (Relationship r : childRels) {
			if (!curNodeDescendantIds.containsAll((long[]) r.getStartNode().getProperty("mrca"))) {

				// if not then we need to include it
				return Evaluation.INCLUDE_AND_CONTINUE;
			}
		}
		
		// check if this node's outmrca property has all the node ids that its children do
		TLongArrayList curNodeOutgroupIds = new TLongArrayList((long[]) curNode.getProperty("outmrca"));
		for (Relationship r : childRels) {
			if (!curNodeOutgroupIds.containsAll((long[]) r.getStartNode().getProperty("outmrca"))) {

				// if not then we need to include it
				return Evaluation.INCLUDE_AND_CONTINUE;
			}
		}

		// if we passed the tests, this node has all the right descendant ids, so we won't include it
		return Evaluation.EXCLUDE_AND_PRUNE;
	}
}
