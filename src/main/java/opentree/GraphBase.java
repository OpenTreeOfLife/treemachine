package opentree;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import opentree.constants.GeneralConstants;
import opentree.constants.NodeProperty;
import opentree.constants.RelProperty;
import opentree.exceptions.MultipleHitsException;
import opentree.exceptions.TaxonNotFoundException;

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
 * @author Stephen Smith, cody hinchliff
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
	public static final String DRAFTTREENAME = (String) GeneralConstants.DRAFT_TREE_NAME.value;
	
	// all constructor methods require a graph database
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
		// TODO: should move this to an enum to make management/index access easier to deal with
        graphNodeIndex = graphDb.getNodeIndex("graphNamedNodes");
		synNodeIndex = graphDb.getNodeIndex("graphNamedNodesSyns");
        sourceRelIndex = graphDb.getRelIndex("sourceRels");
        sourceRootIndex = graphDb.getNodeIndex("sourceRootNodes");
        sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
    	graphTaxUIDNodeIndex = graphDb.getNodeIndex("graphTaxUIDNodes");
    	synTaxUIDNodeIndex = graphDb.getNodeIndex("graphNamedNodesSyns");
    	graphTaxNewNodes = graphDb.getNodeIndex(""); // not sure what the name of this one is in the graphdb. it doesn't seem to be used (for now)
	}
	
	public void shutdownDB(){
		graphDb.shutdownDb();
	}
    
	/**
	 * Wrapper function for taxUID searches on the graphTaxUIDNodes index. Throws TaxonNotFoundException if the search fails,
	 * or a MultipleHitsWhenOneExpectedException if the uid matches multiple taxa.
	 * @return taxNode
	 * @throws MultipleHitsException, TaxonNotFoundException
	 */
    public Node findGraphTaxNodeByUID(final String taxUID) throws MultipleHitsException, TaxonNotFoundException {
        IndexHits<Node> hits = GraphBase.graphTaxUIDNodeIndex.get(NodeProperty.TAX_UID.propertyName, taxUID);
        Node firstNode = null;
        try {
        	firstNode = hits.getSingle();
        } catch (NoSuchElementException ex) {
        	throw new MultipleHitsException(taxUID);
        } finally {
        	hits.close();
        }
		if (firstNode == null) {
			throw new TaxonNotFoundException(NodeProperty.TAX_UID.propertyName + " = " + String.valueOf(taxUID));
		}
		return firstNode;
	}
    
	/**
	 * Wrapper function for simple name searches on the graphNamedNodes index. Throws TaxonNotFoundException if the search fails,
	 * or a MultipleHitsWhenOneExpectedException if the name matches multiple taxa.
	 * @return taxNode
	 * @throws MultipleHitsException, TaxonNotFoundException
	 */
    public Node findTaxNodeByName(final String name) throws TaxonNotFoundException, MultipleHitsException {
        IndexHits<Node> hits = GraphBase.graphNodeIndex.get(NodeProperty.NAME.propertyName, name);
        Node firstNode = null;
        try {
        	firstNode = hits.getSingle();
        } catch (NoSuchElementException ex) {
        	throw new MultipleHitsException(name);
        } finally {
        	hits.close();
        }
		if (firstNode == null) {
			throw new TaxonNotFoundException(name);
		}
		return firstNode;
	}

    /**
     * A wrapper for the index call to find the life node.
     * @return rootnode
     */
    public Node getGraphRootNode() {
    	return graphDb.getNodeById((Long) graphDb.getGraphProperty(NodeProperty.GRAPH_ROOT_NODE_ID.propertyName));
    }
    
    /**
     * Used to set graph properties identifying the root node, so that is will always be known.
     */
    public void setGraphRootNode(Node rootNode) {
    	
    	System.out.println("setting root node");
    	System.out.println("name: " + rootNode.getProperty(NodeProperty.NAME.propertyName));
    	graphDb.setGraphProperty(NodeProperty.GRAPH_ROOT_NODE_NAME.propertyName, rootNode.getProperty(NodeProperty.NAME.propertyName));
    	
    	System.out.println("id: " + rootNode.getId());
    	graphDb.setGraphProperty(NodeProperty.GRAPH_ROOT_NODE_ID.propertyName, rootNode.getId());
    }
    
    /**
     * Return a HashSet containing the nodes corresponding to the passed long array nodeIDArr.
     * @param nodeIDArr
     * @return nodes
     */
	public HashSet<Node> getNodesForIds(long [] nodeIDArr) {
		HashSet<Node> s = new HashSet<Node>();
		for (int i = 0; i < nodeIDArr.length; i++) {
			Node n = graphDb.getNodeById(nodeIDArr[i]);
			s.add(n);
		}
		return s;
	}

    /**
     * Return a HashSet containing the nodes corresponding to the passed Iterable<Long> nodeIDIter.
     * @param nodeIDIter
     * @return nodes
     */
	public HashSet<Node> getNodesForIds(Iterable<Long> nodeIDIter) {
		HashSet<Node> s = new HashSet<Node>();
		for (Long nid : nodeIDIter) {
			Node n = graphDb.getNodeById(nid);
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
			if (((String)rel.getProperty(RelProperty.SOURCE.propertyName)).equals(src)) {
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
