package opentree;

import gnu.trove.list.array.TLongArrayList;

import java.util.BitSet;

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
public class LicaEvaluatorBS implements Evaluator{
	BitSet fullIdBS = null;
	public LicaEvaluatorBS(){}

	public void setfullIDset(TLongArrayList fids){
		fullIdBS = new BitSet(3000000);//could set this to the smallest number
		for(int i=0;i<fids.size();i++){
			fullIdBS.set((int)fids.getQuick(i));
		}
	}
	public Evaluation evaluate(Path arg0) {
		TLongArrayList Ldbnodei = new TLongArrayList((long[]) arg0.endNode().getProperty("mrca"));
		BitSet tBS = new BitSet(3000000);
		for(int i=0;i<Ldbnodei.size();i++){
			tBS.set((int)Ldbnodei.getQuick(i));
		}
		System.out.println("testing "+arg0+" "+arg0.endNode());
		tBS.and(fullIdBS);
		if (tBS.isEmpty() == true) {
			return Evaluation.INCLUDE_AND_CONTINUE;
		}else{
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
	}
}
