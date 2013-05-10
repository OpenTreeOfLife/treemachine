package opentree;

import gnu.trove.list.array.TLongArrayList;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Neo4j Traversal Evaluator which prunes paths when it finds a node with 
 *  an indegree greater or equal the threshold set in `setChildThreshold`
 */
public class LicaEvaluator implements Evaluator{
	int child_threshold = 100;
	Node startNode = null;
	TLongArrayList fullIdSet = null;
	public LicaEvaluator(){}
	public void setChildThreshold(int n){
		child_threshold = n;
	}
	public void setStartNode(Node n){
		startNode = n;
	}
	public void setfullIDset(TLongArrayList fids){
		fullIdSet = fids;
	}
	public Evaluation evaluate(Path arg0) {
		TLongArrayList Ldbnodei = new TLongArrayList((long[]) arg0.endNode().getProperty("mrca"));
		Ldbnodei.sort();
		if (containsAnyt4jSorted(Ldbnodei, fullIdSet) == false) {
			return Evaluation.INCLUDE_AND_CONTINUE;
		}else{
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
	}
	public static boolean containsAnyt4jSorted(TLongArrayList ar1, TLongArrayList ar2) {
		if (ar1.size() < ar2.size()) {
			return containsAnyt4jSortedOrdered(ar1, ar2);
		} else {
			return containsAnyt4jSortedOrdered(ar2, ar1);
		}
	}

	/**
	 * Internal method that is faster when the relative sizes of the inputs are known.
	 * 
	 * @param shorter
	 * @param longer
	 * @return
	 */
	private static boolean containsAnyt4jSortedOrdered(TLongArrayList shorter, TLongArrayList longer) {
		boolean retv = false;
		shorterLoop: for (int i = 0; i < shorter.size(); i++) {
			longerLoop: for (int j = 0; j < longer.size(); j++) {
				if (longer.getQuick(j) > shorter.getQuick(i)) {
					break longerLoop;
				}
				if (longer.getQuick(j) == shorter.getQuick(i)) {
					retv = true;
					break shorterLoop;
				}
			}
		}
		return retv;
	}
}
