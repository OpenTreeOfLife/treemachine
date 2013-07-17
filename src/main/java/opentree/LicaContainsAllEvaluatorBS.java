package opentree;

import java.util.BitSet;

import gnu.trove.list.array.TLongArrayList;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * Neo4j Traversal Evaluator which prunes paths when it finds a node with 
 *  an indegree greater or equal the threshold set in `setChildThreshold`
 */
public class LicaContainsAllEvaluatorBS implements Evaluator{
	BitSet inIdBS = null;
	TLongArrayList visited = null;
	public LicaContainsAllEvaluatorBS(){}
	public void setinIDset(TLongArrayList fids){
		inIdBS = new BitSet((int) fids.max());//could set this to the smallest number
		for(int i=0;i<fids.size();i++){
			inIdBS.set((int)fids.getQuick(i));
		}
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
		BitSet tBS = new BitSet((int) Ldbnodei.max());
		for(int i=0;i<Ldbnodei.size();i++){
			tBS.set((int)Ldbnodei.getQuick(i));
		}
		tBS.and(inIdBS);
		if (tBS.cardinality()==inIdBS.cardinality()) {//contains all
			return Evaluation.INCLUDE_AND_PRUNE;
		}else{
			return Evaluation.EXCLUDE_AND_CONTINUE;
		}
	}
}
