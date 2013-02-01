package opentree;

import java.util.HashSet;

import opentree.GraphBase.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * A Neo4j Traversal Evaluator which include:
 *		paths that end with a TAXCHILDOF startNode, and
 *		paths are the TAX parent of other nodes.
 */
public class TaxaListEvaluator implements Evaluator{
	HashSet<Long> taxalist = null;
	public void setTaxaList(HashSet<Long> tl){
		taxalist = tl;
	}
	
	public Evaluation evaluate(Path arg0) {
		boolean match = false;
		Node tnode = arg0.endNode();
		long [] mrcas = (long []) tnode.getProperty("mrca");
		HashSet<Long> tm = new HashSet<Long>();
		for(int i =0;i<mrcas.length;i++){tm.add(mrcas[i]);}
		int tl1 = tm.size();
		tm.removeAll(taxalist);
		if((tl1-tm.size()) > 0){
			match = true;
		}
		if(match != true){
			tm = new HashSet<Long>();
			long [] nmrcas = (long []) tnode.getProperty("nested_mrca");
			for(int i =0;i<nmrcas.length;i++){tm.add(nmrcas[i]);}
			tl1 = tm.size();
			tm.removeAll(taxalist);
			if((tl1-tm.size()) > 0){
				match = true;
			}
		}
		if(match == true){
			return Evaluation.INCLUDE_AND_CONTINUE;
		}
		return Evaluation.EXCLUDE_AND_PRUNE;
	}
}
