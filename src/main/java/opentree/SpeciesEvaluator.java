package opentree;

import opentree.TaxonomyBase.RelTypes;

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
public class SpeciesEvaluator implements Evaluator{
	Node startNode = null;
	public void setStartNode(Node n){
		startNode = n;
	}
	public Evaluation evaluate(Path arg0) {
		//TODO: take in whether this is a taxonomy tree or not
		boolean parent_startnode = false;
		
		for(Relationship rel: arg0.endNode().getRelationships(Direction.OUTGOING, RelTypes.TAXCHILDOF)){
			//System.out.println(startNode.getProperty("name") +"  "+rel.getEndNode().getProperty("name")+ "  "+ rel.getStartNode().getProperty("name"));
			if (rel.getEndNode().getId()==startNode.getId()){
				parent_startnode = true;
				break;
			}
		}
		if(parent_startnode == true){
			return Evaluation.INCLUDE_AND_CONTINUE;
		}else if(arg0.endNode().hasRelationship(Direction.INCOMING,RelTypes.TAXCHILDOF)){
			return Evaluation.INCLUDE_AND_CONTINUE;
		}
		return Evaluation.EXCLUDE_AND_PRUNE;
	}
}
