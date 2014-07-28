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

		if (embedded) {
			index = embeddedGraphDb.index().forNodes(indexName, indexPars);
		} else {
			index = graphDbService.index().forNodes(indexName, indexPars);
		}
		return index;
	}

	public Index<Relationship> getRelIndex(String indexName) {
		Index<Relationship> index; 
		Map<String,String> indexPars = MapUtil.stringMap( "type", "exact", "to_lower_case", "true" );

		if (embedded) {
			index = embeddedGraphDb.index().forRelationships(indexName, indexPars);
		} else {
			index = graphDbService.index().forRelationships(indexName, indexPars);
		}
		return index;
	}
	
	public Node createNode() {
		if (embedded) {
			return embeddedGraphDb.createNode();
		} else {
			return graphDbService.createNode();
		}
	}

	public Transaction beginTx() {
		if (embedded) {
			return embeddedGraphDb.beginTx();
		} else {
			return graphDbService.beginTx();
		}
	}
	
	public Node getNodeById(Long arg0) {
		if (embedded) {
			return embeddedGraphDb.getNodeById(arg0);
		} else {
			return graphDbService.getNodeById(arg0);
		}
	}

	public Relationship getRelationshipById(Long arg0) {
		if (embedded) {
			return embeddedGraphDb.getRelationshipById(arg0);
		} else {
			return graphDbService.getRelationshipById(arg0);
		}
	}

	public void shutdownDb() {
		registerShutdownHook();
	}

	/**
	 * These assume that the graph properties will be stored at the 
	 * root node as properties. The root node should always be node 0
	 * @param propname
	 * @return
	 */
	public Object getGraphProperty(String propname){
		if (embedded) {
			if (embeddedGraphDb.getNodeById(0).hasProperty(propname)) {
				return embeddedGraphDb.getNodeById(0).getProperty(propname);
			} else {
				return null;
			}
		} else {
			if (graphDbService.getNodeById(0).hasProperty(propname)) {
				return graphDbService.getNodeById(0).getProperty(propname);
			} else {
				return null;
			}
		}
	}
	
	/**
	 * These assume that the graph properties will be stored at the actual graph db root node as properties. The root node of the
	 * graph db itself should always be node 0, although this is not necessarily of the tree alignment graph stored within the graphdb.
	 * @param propname, prop
	 * @return
	 */
	public void setGraphProperty(String propname, Object prop){
		if (embedded) {
			Transaction tx = embeddedGraphDb.beginTx();
			try {
				embeddedGraphDb.getNodeById(0).setProperty(propname,prop);
				tx.success();
			} finally {
				tx.finish();
			}
		} else {
			Transaction tx = graphDbService.beginTx();
			try {
				graphDbService.getNodeById(0).setProperty(propname,prop);
				tx.success();
			} finally {
				tx.finish();
			}
		}
	}
	
	protected /*static */ void registerShutdownHook() {
		
		
		if (embedded) {
			embeddedGraphDb.shutdown();

		// Apparently the use of an anonymous instance of Thread.class is required when the graph is running as a service (at least on localhost).
		// If we just call the shutdown() method directly from within a plugin (via GraphExplorer.shutdownDB() for instance), the server fails.
		} else {
			Runtime.getRuntime().addShutdownHook(new Thread() {
	
				@Override
				public void run() {
					graphDbService.shutdown();
			   }
			});
		}
	}
}
