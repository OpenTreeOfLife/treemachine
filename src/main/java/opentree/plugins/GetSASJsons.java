package opentree.plugins;

import jade.tree.deprecated.JadeTree;
import opentree.GraphExporter;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.server.plugins.*;

/**
 * This is for making the D3 viz 
 * 
 */

public class GetSASJsons extends ServerPlugin {
	
	protected static enum RelTypes implements RelationshipType{
		TAXCHILDOF, 
		STREECHILDOF,
		SYNTHCHILDOF 
	} 
	
	/**
	 * @param nodeid
	 * @return
	 */
	@Description( "Return a JSON with alternative parents presented" )
	@PluginTarget( GraphDatabaseService.class )
	public String getSynthJson(@Source GraphDatabaseService graphDb,
			@Description( "The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
		Node firstNode = graphDb.getNodeById(nodeID);
		GraphExporter ge = new GraphExporter(graphDb);
		JadeTree t = ge.buildSyntheticTreeForWeb(firstNode, ge.DRAFTTREENAME,100,false);
		return t.getRoot().getJSON(false);
	}

	/**
	 * @param nodeid
	 * @return
	 */
	@Description( "Return a JSON with relationship IDs present" )
	@PluginTarget( GraphDatabaseService.class )
	public String getSynthRelIDJson(@Source GraphDatabaseService graphDb,
			@Description( "The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
		Node firstNode = graphDb.getNodeById(nodeID);
		GraphExporter ge = new GraphExporter(graphDb);
		JadeTree t = ge.buildSyntheticTreeForWeb(firstNode, ge.DRAFTTREENAME,100,true);
		return t.getRoot().getJSON(false);
	}

	
	/**
	 * @param nodeid
	 * @return
	 */
	@Description( "Return a JSON with alternative parents presented" )
	@PluginTarget( GraphDatabaseService.class )
	public String getTaxonJson(@Source GraphDatabaseService graphDb,
			@Description( "The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
		Node firstNode = graphDb.getNodeById(nodeID);
		GraphExporter ge = new GraphExporter(graphDb);
		JadeTree t = ge.buildTaxonomyTreeForWeb(firstNode,150,false);
		return t.getRoot().getJSON(false);
	}
	
	/**
	 * @param nodeid
	 * @return
	 */
	@Description( "Return a JSON with relationship IDs present" )
	@PluginTarget( GraphDatabaseService.class )
	public String getTaxRelIDJson(@Source GraphDatabaseService graphDb,
			@Description( "The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
		Node firstNode = graphDb.getNodeById(nodeID);
		GraphExporter ge = new GraphExporter(graphDb);
		JadeTree t = ge.buildTaxonomyTreeForWeb(firstNode,100,true);
		return t.getRoot().getJSON(false);
	}
	
}
