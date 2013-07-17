package opentree.plugins;

import java.util.ArrayList;
import opentree.GraphExporter;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

/**
 * This class appears to be a relict of a code-transfer from taxomachine, and if this is true then it should probably be removed.
 * 
 * ADDENDUM: I have commented out the entire class since it was causing compilation errors. If necessary, it can be reinstated
 * and the errors can be fixed.
 * 
 * @author cody
 *
 */
@Deprecated
public class GetJsons extends ServerPlugin {
	
	/* This enum exists in its own class file
	protected static enum RelTypes implements RelationshipType{
		MRCACHILDOF, //standard rel for graph db, from node to parent
		TAXCHILDOF, //standard rel for tax db, from node to parent
		STREECHILDOF, //standard rel for input tree, from node to parent  
		ISCALLED // is called ,from node in graph of life to node in tax graph 
	} *
	
	/**
	 * Is this service used? For now I have marked it as deprecated.
	 * @param source
	 * @return
	 *
	@Description( "Return a JSON with alternative parents presented" )
	@PluginTarget( Node.class )
	@Deprecated
	public String getConflictJson(@Source Node source) {
		Node firstNode = source;
		GraphExporter ge = new GraphExporter();
		return ge.constructJSONAltParents(firstNode);
	}

	/**
	 * Is this service used? For now I have marked it as deprecated, if it should not be then please fix (and provide some indication of where it is used?)
	 * @param source
	 * @param domsource
	 * @param altrels
	 * @param nubrel
	 * @return
	 *
	@Description ("Return a JSON with alternative TAXONOMIC relationships noted and returned")
	@PluginTarget (Node.class)
	@Deprecated
	public String getConflictTaxJsonAltRel(@Source Node source,
			@Description( "The dominant source.")
			@Parameter(name = "domsource", optional = true) String domsource,
			@Description( "The list of alternative relationships to prefer." )
			@Parameter( name = "altrels", optional = true ) Long[] altrels,
			@Description( "A new relationship nub." )
			@Parameter( name = "nubrel", optional = true ) Long nubrel ) {
		String retst="";
		int maxdepth = 3;
		GraphExporter ge = new GraphExporter();
		if (nubrel != null){
			Relationship rel = source.getGraphDatabase().getRelationshipById(nubrel);
			ArrayList<Long> rels = new ArrayList<Long>();
			if (altrels != null) {
				for (int i = 0; i < altrels.length; i++) {
					rels.add(altrels[i]);
				}
			}
			retst = ge.constructJSONAltRels(rel.getEndNode(),
											(String)rel.getProperty("source"),
											rels,
											maxdepth);
		} else {
			ArrayList<Long> rels = new ArrayList<Long>();
			if(altrels != null) {
				for (int i = 0; i < altrels.length; i++) {
					rels.add(altrels[i]);
				}
			}
			retst = ge.constructJSONAltRels(source, domsource, rels, maxdepth);
		}
		return retst;
	}
	
	/**
	 * Shouldn't this functionality be accessed using the TNRS? For now I have marked this as deprecated, if this is wrong please fix and indicate what this is for.
	 * @param graphDb
	 * @param nodename
	 * @return
	 *
	@Deprecated
	@Description ("Return a JSON with the node id given a name")
	@PluginTarget (GraphDatabaseService.class)
	public String getNodeIDJSONFromName(@Source GraphDatabaseService graphDb,
			@Description("Name of node to find.")
			@Parameter( name = "nodename", optional= true ) String nodename ) {
		String retst = "";
		System.out.println(nodename);
		IndexHits<Node> hits = graphDb.index().forNodes("taxNamedNodes").get("name",nodename);
		try {
			Node firstNode = hits.next();
			hits.close();
			if(firstNode == null) {
				retst = "[]";
			} else {
				retst="[{\"nodeid\":"+firstNode.getId()+"}]";
			}
		} catch (java.lang.Exception jle) {
			retst = "[]";
		}
		return retst;
	} */
}
