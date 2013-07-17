package opentree;

import gnu.trove.list.array.TLongArrayList;

import java.util.BitSet;

import org.neo4j.graphdb.Path;
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
		fullIdBS = new BitSet((int)fids.max());//could set this to the smallest number
		for(int i=0;i<fids.size();i++){
			fullIdBS.set((int)fids.getQuick(i));
		}
	}
	@Override
	public Evaluation evaluate(Path arg0) {
		TLongArrayList Ldbnodei = new TLongArrayList((long[]) arg0.endNode().getProperty("mrca"));
		BitSet tBS = new BitSet((int) Ldbnodei.max());
		for(int i=0;i<Ldbnodei.size();i++){
			tBS.set((int)Ldbnodei.getQuick(i));
		}
		if (tBS.intersects(fullIdBS)) {//contains any
			return Evaluation.INCLUDE_AND_CONTINUE;
		}else{
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
	}
}
