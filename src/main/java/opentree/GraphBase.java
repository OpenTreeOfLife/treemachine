package opentree;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

/**
 * 
 * @author Stephen Smith
 *
 *
 * Node properties
 * 		mrca - all the subtending children ids
 * 		nested_mrcas - all the subtending children ids that would be nested (higher taxa)
 * 
 * Relationship properties
 * 		STREEEXACTCHILDOF
 * 			source - name of the source tree
 * 			branch_length - branch length
 * 		STREEINCLUCHILDOF
 * 			exclusive_mrca - the mrcas (with nested ids not the full ids when applicable, 
 * 										so these need to be expanded for searches) for this inclusive relationship
 * 			root_exclusive_mrca - the root exclusive mrcas for the tree, necessary for future lica searching
 * 			lica - node ids for all the potential licas (could be just one or more if there are ambiguities)
 * 			inclusive_relids - the list of other relationship ids involved in this set
 * 			source - name of the source tree
 * 			branch_length - branch length
 */

public abstract class GraphBase {
	GraphDatabaseService graphDb;
	protected static Index<Node> taxNodeIndex;
	protected static Index<Node> graphNodeIndex;
	protected static Index<Relationship> sourceRelIndex;
	protected static Index<Node> sourceRootIndex;
	
	protected static enum RelTypes implements RelationshipType{
		MRCACHILDOF, //standard rel for graph db, from node to parent
		TAXCHILDOF, //standard rel for tax db, from node to parent
		STREECHILDOF, //@deprecated!! standard rel for input tree, from node to parent
		STREEEXACTCHILDOF, //these refer to branches from the input tree that have NO ADDITIONAL 
						   // inclusive children (all taxa subtending are present in the tree)
		STREEINCLUCHILDOF,//these refer to branches from the input tree that have ADDITIONAL 
		   				  // inclusive children (NOT all taxa subtending are present in the tree)
		ISCALLED // is called ,from node in graph of life to node in tax graph 
	}
	
	protected static void registerShutdownHook( final GraphDatabaseService graphDb ){
		Runtime.getRuntime().addShutdownHook( new Thread(){
			@Override
			public void run(){
				graphDb.shutdown();
			}
		});
	}
	
	public void shutdownDB(){
		 registerShutdownHook( graphDb );
	}
	
	/**
	 * @return Checks graphNodeIndex for `name` and returns null (if the name is not found) or 
	 *  the node using IndexHits<Node>.getSingle()
	 * helper function primarily written to avoid forgetting to call hits.close();
	 */
    Node findGraphNodeByName(final String name) {
        IndexHits<Node> hits = this.graphNodeIndex.get("name", name);
		Node firstNode = hits.getSingle();
		hits.close();
		return firstNode;
	}
	/**
	 * @return Checks taxNodeIndex for `name` and returns null (if the name is not found) or 
	 *  the node using IndexHits<Node>.getSingle()
	 * helper function primarily written to avoid forgetting to call hits.close();
	 */
    Node findTaxNodeByName(final String name) {
        IndexHits<Node> hits = this.taxNodeIndex.get("name", name);
		Node firstNode = hits.getSingle();
		hits.close();
		return firstNode;
	}

}
