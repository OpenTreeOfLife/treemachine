package opentree.plugins;

import jade.tree.JadeNode;
import jade.tree.JadeTree;
import opentree.GraphExplorer;
import opentree.GraphExporter;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
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
	 * Is this service used? For now I have marked it as deprecated.
	 * @param source
	 * @return
	 */
	@Description( "Return a JSON with alternative parents presented" )
	@PluginTarget( GraphDatabaseService.class )
	public String getSynthJson(@Source GraphDatabaseService graphDb,
			@Description( "The Neo4j node id of the node to be used as the root for the tree.")
			@Parameter(name = "nodeID", optional = false) Long nodeID) {
		Node firstNode = graphDb.getNodeById(nodeID);
		GraphExporter ge = new GraphExporter(graphDb);
		JadeTree t = ge.buildSyntheticTreeForWeb(firstNode, ge.DRAFTTREENAME,100);
		return t.getRoot().getJSON(false);
	}

}
