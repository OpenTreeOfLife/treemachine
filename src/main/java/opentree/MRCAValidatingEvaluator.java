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
public class MRCAValidatingEvaluator implements Evaluator {

	TLongArrayList mrcaTest;
	TLongArrayList outmrcaTest;

	/**
	 * Set the mrca and outmrca properties that we will use to validate nodes in the traversal. Any nodes we
	 * visit that do not contain all the ids of the passed mrca variable in the corresponding properties
	 * will be returned by the traversal.
	 * @param mrca
	 * @param outmrca
	 */
	public MRCAValidatingEvaluator(TLongArrayList mrca) {
		this.mrcaTest = mrca;
	}

	@Override
	public Evaluation evaluate(Path inPath) {

		Node curNode = inPath.endNode();

		// pass over taxonomy nodes, their mrca never changes 
		if (curNode.hasProperty("name")) {
			return Evaluation.EXCLUDE_AND_CONTINUE;
		}
		
		// include the node if it is missing any mrca ids
		TLongArrayList curNodeDescendantIds = new TLongArrayList((long[]) curNode.getProperty("mrca"));
		if (!curNodeDescendantIds.containsAll(mrcaTest)) {
			return Evaluation.INCLUDE_AND_CONTINUE;
		}

		// if we passed the tests, this node has all the right descendant ids, so we won't include it
		return Evaluation.EXCLUDE_AND_PRUNE;
	}
}
