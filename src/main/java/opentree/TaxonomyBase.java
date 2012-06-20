package opentree;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;

public abstract class TaxonomyBase {
	GraphDatabaseService graphDb;
	protected static Index<Node> nodeIndex;
	
	protected static enum RelTypes implements RelationshipType{
	    MRCACHILDOF,
	    TAXCHILDOF,
	    CHILDOF
	}
	
	protected static void registerShutdownHook( final GraphDatabaseService graphDb ){
	    Runtime.getRuntime().addShutdownHook( new Thread()
	    {
	        @Override
	        public void run()
	        {
	            graphDb.shutdown();
	        }
	    } );
	}
	
	public void shutdownDB(){
		 registerShutdownHook( graphDb );
	}
}
