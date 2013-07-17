package opentree;

import java.util.HashSet;
import java.util.NoSuchElementException;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.kernel.EmbeddedGraphDatabase;

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
 * 		STREECHILDOF
 * 			exclusive_mrca - the mrcas (with nested ids not the full ids when applicable, 
 * 										so these need to be expanded for searches) for this inclusive relationship
 * 			root_exclusive_mrca - the root exclusive mrcas for the tree, necessary for future lica searching
 * 			lica - node ids for all the potential licas (could be just one or more if there are ambiguities)
 * 			inclusive_relids - the list of other relationship ids involved in this set
 * 			source - name of the source tree
 * 			branch_length - branch length
 * 
 * 		SYNTHCHILDOF
 */

public abstract class GraphBase {
	GraphDatabaseAgent graphDb;
	
	protected static Index<Node> graphNodeIndex;
	protected static Index<Node> synNodeIndex;
	protected static Index<Relationship> sourceRelIndex;
	protected static Index<Node> sourceRootIndex;
	protected static Index<Node> sourceMetaIndex;
	protected static Index<Node> graphTaxUIDNodeIndex; //tax_uid is the key, the uid from the taxonomy points to this node
	protected static Index<Node> synTaxUIDNodeIndex; //tax_uid is the key, this points to the synonymn node, to get the tax that this points to you need to travel synonymof
	protected static Index<Node> graphTaxNewNodes;

	// this is clunky, might be a better way to do this
	public static final String DRAFTTREENAME = (String) Constants.DRAFT_TREE_NAME.value;
	
	// all constructor methods require some kind of reference to a graph database that will be used
	public GraphBase(String graphName) {
		graphDb = new GraphDatabaseAgent(graphName);
		initNodeIndexes();
	}
	
    public GraphBase(GraphDatabaseService graphService) {
		graphDb = new GraphDatabaseAgent(graphService);
		initNodeIndexes();
    }
    
    public GraphBase(EmbeddedGraphDatabase embeddedGraph) {
    	graphDb = new GraphDatabaseAgent(embeddedGraph);
    	initNodeIndexes();
    }
    
    public GraphBase(GraphDatabaseAgent gdb) {
    	graphDb = gdb;
    	initNodeIndexes();
    }
    
    /**
     * Just initialize the indexes.
     */
	public void initNodeIndexes() {
		// TODO: should move this to an enum to make management/naming easier to deal with
        graphNodeIndex = graphDb.getNodeIndex("graphNamedNodes");
		synNodeIndex = graphDb.getNodeIndex("graphNamedNodesSyns");
        sourceRelIndex = graphDb.getRelIndex("sourceRels");
        sourceRootIndex = graphDb.getNodeIndex("sourceRootNodes");
        sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
    	graphTaxUIDNodeIndex = graphDb.getNodeIndex("graphTaxUIDNodes");
    	synTaxUIDNodeIndex = graphDb.getNodeIndex("graphNamedNodesSyns");
    	graphTaxNewNodes = graphDb.getNodeIndex("");	
	}
	
	public void shutdownDB(){
		graphDb.shutdownDb();
	}
	
	/* DEPRECATED, identical functionality to findTaxNodeByName
	 * @return Checks graphNodeIndex for `name` and returns null (if the name is not found) or 
	 *  the node using IndexHits<Node>.getSingle()
	 * helper function primarily written to avoid forgetting to call hits.close();
	 *
    public Node findGraphNodeByName(final String name) {
        IndexHits<Node> hits = GraphBase.graphNodeIndex.get("name", name);
		Node firstNode = hits.getSingle();
		
		
        Node firstNode = null;
        try {
        	firstNode = hits.getSingle();
        } catch (NoSuchElementException ex) {
        	throw new MultipleHitsWhenOneExpectedException(taxUID);
        } finally {
        	hits.close();
        }
		
		
		hits.close();
		return firstNode;
	} */
    
	/**
	 * Wrapper function for taxUID searches on the graphTaxUIDNodes index. Throws TaxonNotFoundException if the search fails,
	 * or a MultipleHitsWhenOneExpectedException if the uid matches multiple taxa.
	 * @return taxNode
	 * @throws MultipleHitsWhenOneExpectedException, TaxonNotFoundException
	 */
    public Node findGraphTaxNodeByUID(final String taxUID) throws MultipleHitsWhenOneExpectedException, TaxonNotFoundException {
        IndexHits<Node> hits = GraphBase.graphTaxUIDNodeIndex.get("tax_uid", taxUID);
        Node firstNode = null;
        try {
        	firstNode = hits.getSingle();
        } catch (NoSuchElementException ex) {
        	throw new MultipleHitsWhenOneExpectedException(taxUID);
        } finally {
        	hits.close();
        }
		if (firstNode == null) {
			throw new TaxonNotFoundException("uid = " + String.valueOf(taxUID));
		}
		return firstNode;
	}
    
	/**
	 * Wrapper function for simple name searches on the graphNamedNodes index. Throws TaxonNotFoundException if the search fails,
	 * or a MultipleHitsWhenOneExpectedException if the name matches multiple taxa.
	 * @return taxNode
	 * @throws MultipleHitsWhenOneExpectedException, TaxonNotFoundException
	 */
    public Node findTaxNodeByName(final String name) throws TaxonNotFoundException {
        IndexHits<Node> hits = GraphBase.graphNodeIndex.get("name", name);
        Node firstNode = null;
        try {
        	firstNode = hits.getSingle();
        } catch (NoSuchElementException ex) {
        	throw new MultipleHitsWhenOneExpectedException(name);
        } finally {
        	hits.close();
        }
		if (firstNode == null) {
			throw new TaxonNotFoundException(name);
		}
		return firstNode;
	}

    /**
     * A wrapper for the index call to find the life node. Supplies a meaningful error message if it fails.
     * @return lifenode
     * @throws TaxonNotFoundException
     */
    public Node getLifeNode() throws TaxonNotFoundException {
    	try {
    		return (graphNodeIndex.get("name", Constants.GRAPH_ROOT_NODE_NAME.value)).next();
    	} catch (NoSuchElementException ex) {
    		throw new TaxonNotFoundException((String) Constants.GRAPH_ROOT_NODE_NAME.value);
    	}
    }
    
	public HashSet<Node> idArrayToNodeSet(long [] nodeIDArr) {
		HashSet<Node> s = new HashSet<Node>();
		for (int i = 0; i < nodeIDArr.length; i++) {
			Node n = graphDb.getNodeById(nodeIDArr[i]);
			s.add(n);
		}
		return s;
	}
	
	/**
	 * Helper function that returns adjacent the node connected by the first
	 *		relationship with the source property equal to `src` 
	 * @param nd the focal node (serves as the source for all potential relationships
	 * @param relType the type of Relationship to check
	 * @param dir the direction of the relationship's connection to `nd`
	 * @param src the string that must match the `source` property
	 * @return adjacent node from the first relationship satisfying the criteria or null
	 * @todo could be moved to a more generic class (this has nothing to do with GraphImporter).
	 */
	static public Node getAdjNodeFromFirstRelationshipBySource(Node nd, RelationshipType relType, Direction dir,  String src) {
		for (Relationship rel: nd.getRelationships(relType, dir)) {
			if (((String)rel.getProperty("source")).equals(src)) {
				if (dir == Direction.OUTGOING) {
					return rel.getEndNode();
				} else {
					return rel.getStartNode();
				}
			}
		}
		return null;
	}
	
}
