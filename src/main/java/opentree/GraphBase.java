package opentree;

import java.util.HashSet;
import java.util.NoSuchElementException;

import opentree.constants.GeneralConstants;
import opentree.constants.GraphProperty;
import opentree.constants.NodeProperty;
import opentree.constants.RelProperty;
import opentree.constants.RelType;
import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.TaxonNotFoundException;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
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
	
	protected static Index<Node> synthMetaIndex;
	protected static Index<Relationship> synthRelIndex;

	// this is clunky, might be a better way to do this. could use the date here.
	public static final String DRAFTTREENAME = (String) GeneralConstants.DRAFT_TREE_NAME.value;
	
	
	// all constructor methods require a graph database
	/**
	 * Access the graph db at the given filename path.
	 * @param graphName
	 */
	public GraphBase(String graphName) {
		graphDb = new GraphDatabaseAgent(graphName);
		initNodeIndexes();
	}

	/**
	 * Access the graph db through the given service object. 
	 * @param graphService
	 */
	public GraphBase(GraphDatabaseService graphService) {
		graphDb = new GraphDatabaseAgent(graphService);
		initNodeIndexes();
	}
	
	
	/**
	 * Access the graph db through the given embedded db object.
	 * @param embeddedGraph
	 */
	public GraphBase(EmbeddedGraphDatabase embeddedGraph) {
		graphDb = new GraphDatabaseAgent(embeddedGraph);
		initNodeIndexes();
	}
	
	
	/**
	 * Open the graph db through the given agent object.
	 * @param gdb
	 */
	public GraphBase(GraphDatabaseAgent gdb) {
		graphDb = gdb;
		initNodeIndexes();
	}
	
	
	/**
	 * Just close the db.
	 */
	public void shutdownDB() {
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
		return graphDb.getNodeById((Long) graphDb.getGraphProperty(GraphProperty.GRAPH_ROOT_NODE_ID.propertyName));
	}
	
	
	
	public String getTaxonomyVersion () {
		return (String)graphDb.getGraphProperty(GraphProperty.GRAPH_ROOT_NODE_TAXONOMY.propertyName);
	}
	
	
	/**
	 * Used to set graph properties identifying the root node, so that is will always be known.
	 */
	public void setGraphRootNode(Node rootNode, String taxonomyversion) {
		
		System.out.println("setting root node");
		System.out.println("name: " + rootNode.getProperty(NodeProperty.NAME.propertyName));
		graphDb.setGraphProperty(GraphProperty.GRAPH_ROOT_NODE_NAME.propertyName, rootNode.getProperty(NodeProperty.NAME.propertyName));
		
		System.out.println("id: " + rootNode.getId());
		graphDb.setGraphProperty(GraphProperty.GRAPH_ROOT_NODE_ID.propertyName, rootNode.getId());
		
		System.out.println("setting taxonomy version: " + taxonomyversion);
		graphDb.setGraphProperty(GraphProperty.GRAPH_ROOT_NODE_TAXONOMY.propertyName, taxonomyversion);
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
	
	/**
	 * Just checks if the named source tree is in the graph
	 * @param sourcename
	 * @return
	 */
	public boolean hasSourceTreeName(String sourcename) {

		IndexHits<Node> hits = null;
		boolean hasSTree = false;
		try {
			hits = sourceRootIndex.get("rootnode", sourcename);
			if (hits != null && hits.size() > 0) {
				hasSTree = true;
			}
		} finally {
			hits.close();
		}

		return hasSTree;
	}
	
	/**
	 * Remove all the loaded trees from the graph.
	 */
	public void deleteAllTrees() {
		IndexHits<Node> hits = sourceMetaIndex.query("source", "*");
		System.out.println(hits.size());
		for (Node itrel : hits) {
			String source = (String)itrel.getProperty("source");
			deleteTreeBySource(source);
		}
	}
	
	/**
	 * Removes the indicated source tree from the graph.
	 * @param source
	 */
	public void deleteTreeBySource(String source) {
		System.out.println("deleting tree: " + source);

		// initialize db access variables
		Transaction tx = null;
		IndexHits <Relationship> relsToRemove = null;
		IndexHits <Node> nodesToRemove = null;

		// first remove the relationships
		try {
			relsToRemove = sourceRelIndex.get("source", source);
			tx = graphDb.beginTx();
			try {
				for (Relationship itrel : relsToRemove) {
					itrel.delete();
					sourceRelIndex.remove(itrel, "source", source);
				}
				tx.success();
			} finally {
				tx.finish();
			}
		} finally {
			relsToRemove.close();
		}

		// then remove the root nodes
		try {
			nodesToRemove = sourceRootIndex.get("rootnode", source);
			tx = graphDb.beginTx();
			try {
				for (Node itnode : nodesToRemove) {
					sourceRootIndex.remove(itnode, "rootnode", source);
				}
				tx.success();
			} finally {
				tx.finish();
			}
		} finally {
			nodesToRemove.close();
		}

		// then remove the metadata nodes
		try {
			nodesToRemove = sourceMetaIndex.get("source", source);
			tx = graphDb.beginTx();
			try {
				for (Node itnode : nodesToRemove) {
					sourceMetaIndex.remove(itnode, "source", source);
					itnode.getRelationships(RelType.METADATAFOR).iterator().next().delete();
					itnode.delete();
				}
				tx.success();
			} finally {
				tx.finish();
			}
		} finally {
			nodesToRemove.close();
		}
	}	
	
	/**
	 * Removes the synthetic tree from the graph.
	 * @param source
	 */
	public void deleteSynthesisTree() {
		System.out.println("deleting synthetic tree from db...");

		// initialize db access variables
		Transaction tx = null;
		IndexHits <Relationship> relsToRemove = null;
		IndexHits <Node> nodesToRemove = null;

		// first remove the relationships
		try {
			relsToRemove = synthRelIndex.get("draftTreeID", DRAFTTREENAME);
			tx = graphDb.beginTx();
			try {
				for (Relationship itrel : relsToRemove) {
					itrel.delete();
					synthRelIndex.remove(itrel, "draftTreeID", DRAFTTREENAME);
				}
				tx.success();
			} finally {
				tx.finish();
			}
		} finally {
			relsToRemove.close();
		}

		// then remove the metadata nodes
		try {
			nodesToRemove = synthMetaIndex.get("name", DRAFTTREENAME);
			tx = graphDb.beginTx();
			try {
				for (Node itnode : nodesToRemove) {
					synthMetaIndex.remove(itnode, "name", DRAFTTREENAME);
					itnode.getRelationships(RelType.SYNTHMETADATAFOR).iterator().next().delete();
					itnode.delete();
				}
				tx.success();
			} finally {
				tx.finish();
			}
		} finally {
			nodesToRemove.close();
		}
	}
	
	/**
	 * Just initialize the standard GoL indexes used for searching.
	 */
	private void initNodeIndexes() {
		// TODO: should move this to an enum to make management/index access easier to deal with
		graphNodeIndex = graphDb.getNodeIndex("graphNamedNodes");
		synNodeIndex = graphDb.getNodeIndex("graphNamedNodesSyns");
		sourceRelIndex = graphDb.getRelIndex("sourceRels");
		sourceRootIndex = graphDb.getNodeIndex("sourceRootNodes");
		sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
		graphTaxUIDNodeIndex = graphDb.getNodeIndex("graphTaxUIDNodes");
		synTaxUIDNodeIndex = graphDb.getNodeIndex("graphNamedNodesSyns");
		graphTaxNewNodes = graphDb.getNodeIndex(""); // not sure what the name of this one is in the graphdb. it doesn't seem to be used (for now)
		
	// synthetic tree indices
		synthMetaIndex = graphDb.getNodeIndex("synthMetaNodes");
		synthRelIndex = graphDb.getRelIndex("synthRels");
	}
}
