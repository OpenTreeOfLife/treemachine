package opentree;

import java.util.Map;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.EmbeddedGraphDatabase;

/**
 * An abstraction of the Neo4J database that provides identical modes of access to both embedded and served databases,
 * and which defines some convenience methods for accessing certain database features (e.g. common types of index queries).
 *
 * @author cody hinchliff and stephen smith
 *
 */
public class GraphDatabaseAgent {

    /*
    private static EmbeddedGraphDatabase embeddedGraphDb;
    private static GraphDatabaseService graphDbService;
    private static boolean embedded;
    */
    
    private EmbeddedGraphDatabase embeddedGraphDb;
    private GraphDatabaseService graphDbService;
    private boolean embedded;

    public GraphDatabaseAgent(GraphDatabaseService gdbs) {
        graphDbService = gdbs;
        embedded = false;
    }

    public GraphDatabaseAgent(EmbeddedGraphDatabase egdb) {
        embeddedGraphDb = egdb;
        embedded = true;
    }

    public GraphDatabaseAgent(String graphDbName) {
        embeddedGraphDb = new EmbeddedGraphDatabase(graphDbName);
        embedded = true;
    }

    public Index<Node> getNodeIndex(String indexName) {
        Index<Node> index; 
        Map<String,String> indexPars = MapUtil.stringMap( "type", "exact", "to_lower_case", "true" );

        if (embedded)
            index = embeddedGraphDb.index().forNodes(indexName, indexPars);
        else
            index = graphDbService.index().forNodes(indexName, indexPars);
        
        return index;
    }

    public Index<Relationship> getRelIndex(String indexName) {
        Index<Relationship> index; 
        Map<String,String> indexPars = MapUtil.stringMap( "type", "exact", "to_lower_case", "true" );

        if (embedded)
            index = embeddedGraphDb.index().forRelationships(indexName, indexPars);
        else
            index = graphDbService.index().forRelationships(indexName, indexPars);
        
        return index;
    }
    
    public Node createNode() {
        if (embedded)
            return embeddedGraphDb.createNode();
        else
            return graphDbService.createNode();
    }

    public Transaction beginTx() {
        if (embedded)
            return embeddedGraphDb.beginTx();
        else
            return graphDbService.beginTx();
    }

    public Node getNodeById(Long arg0) {
        if (embedded)
            return embeddedGraphDb.getNodeById(arg0);
        else
            return graphDbService.getNodeById(arg0);
    }

    public void shutdownDb() {
        registerShutdownHook();
    }

    protected /*static */ void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                if (embedded)
                    embeddedGraphDb.shutdown();
                else
                    graphDbService.shutdown();
            }
        });
    }
}
