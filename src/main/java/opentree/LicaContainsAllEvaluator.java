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
public class LicaContainsAllEvaluator implements Evaluator{
	TLongArrayList inIdSet = null;
	public LicaContainsAllEvaluator(){}
	public void setinIDset(TLongArrayList fids){
		inIdSet = fids;
	}
	public Evaluation evaluate(Path arg0) {
		TLongArrayList Ldbnodei = new TLongArrayList((long[]) arg0.endNode().getProperty("mrca"));
		Ldbnodei.sort();
		boolean containsall = Ldbnodei.containsAll(inIdSet);
		if (containsall) {
			return Evaluation.INCLUDE_AND_PRUNE;
		}else{
			return Evaluation.EXCLUDE_AND_CONTINUE;
		}
	}
}
