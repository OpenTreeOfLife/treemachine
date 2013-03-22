package opentree.plugins;

import opentree.PhylografterConnector;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

public class PhylografterUpdater extends ServerPlugin{

	@Description ("Update the graph studies that should be added from phylografter")
	@PluginTarget (GraphDatabaseService.class)
	public void up(@Source GraphDatabaseService graphDb){
//			,
//			@Description("Last date to check")
//			@Parameter( date = "date", optional= true ) String date){
		PhylografterConnector pc = new PhylografterConnector();
		
	}
}
