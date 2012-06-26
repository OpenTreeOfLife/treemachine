package opentree;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;

public abstract class GraphBase {
	GraphDatabaseService graphDb;
	protected static Index<Node> taxNodeIndex;
	protected static Index<Node> graphNodeIndex;
	protected static Index<Relationship> sourceRelIndex;
	
	protected static enum RelTypes implements RelationshipType{
	    MRCACHILDOF, //standard rel for graph db, from node to parent
	    TAXCHILDOF, //standard rel for tax db, from node to parent
	    STREECHILDOF, //standard rel for input tree, from node to parent  
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
}
