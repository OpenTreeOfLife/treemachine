package opentree;

import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Neo4j Traversal Evaluator that checks nodes to make sure their mrca and outmrca properties are set. Nodes that need
 * these properties to be updated will be returned as part of the path.
 */
public class LongArrayPropertyContainsAllEvaluator implements Evaluator {

	String propertyName;
	TLongBitArray testArray;
	boolean visitTaxNodes = false;

	/**
	 * Set the mrca property that we will use to validate nodes in the traversal. Any nodes we visit that do not contain
	 * all the ids of the passed mrca variable in the corresponding properties will be returned by the traversal.
	 * @param testArray
	 * @param outmrca
	 */
	public LongArrayPropertyContainsAllEvaluator(String propertyName, TLongBitArray testArray) {
		this.propertyName = propertyName;
		this.testArray = testArray;
	}

	/**
	 * Set the behavior for visiting taxonomy nodes. The default is not to visit them.
	 * @param visitTaxNodes
	 * @return
	 */
	public LongArrayPropertyContainsAllEvaluator setVisitTaxonomyNodes(boolean visitTaxNodes) {
		this.visitTaxNodes = visitTaxNodes;
		return this;
	}

	@Override
	public Evaluation evaluate(Path inPath) {

		Node curNode = inPath.endNode();

		// pass over taxonomy nodes if this boolean is set (their mrca never changes so we may not need to see them)
		if (!visitTaxNodes) {
			if (curNode.hasProperty("name")) {
				return Evaluation.EXCLUDE_AND_CONTINUE;
			}
		}

		TLongBitArray obsArray = new TLongBitArray((long[]) curNode.getProperty(propertyName));
		
		// testing
//		System.out.println("testing " + propertyName + " of " + curNode + ": " + Arrays.toString(obsArray.toArray()));
//		System.out.println("against test values: " + Arrays.toString(testArray.toArray()));
			
		// include the node if it is missing any mrca ids
		if (!obsArray.containsAll(testArray)) {

			// testing
//			System.out.println("the \"" + propertyName + "\" property of " + curNode + " did not contain all the test values, it will be returned");

			return Evaluation.INCLUDE_AND_CONTINUE;
		}

		// testing
//		System.out.println("the \"" + propertyName + "\" property of " + curNode + " contained all the test values, it will be skipped");

		// if we passed the tests, this node has all the right descendant ids, so we won't include it
		return Evaluation.EXCLUDE_AND_CONTINUE; // was: return Evaluation.EXCLUDE_AND_PRUNE;
	}
}
