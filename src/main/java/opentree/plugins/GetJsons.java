package opentree.plugins;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;


import opentree.GraphExplorer;

import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.Expander;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

import jade.tree.*;

public class GetJsons extends ServerPlugin {
	protected static enum RelTypes implements RelationshipType{
	    MRCACHILDOF, //standard rel for graph db, from node to parent
	    TAXCHILDOF, //standard rel for tax db, from node to parent
	    STREECHILDOF, //standard rel for input tree, from node to parent  
	    ISCALLED // is called ,from node in graph of life to node in tax graph 
	}
	
	@Description( "Return a JSON with alternative parents presented" )
	@PluginTarget( Node.class )
    public String getConflictJson(@Source Node source){
		Node firstNode = source;
		GraphExplorer ge = new GraphExplorer();
		return ge.constructJSONAltParents(firstNode);
	}
	
	@Description ("Return a JSON with alternative TAXONOMIC relationships noted and returned")
	@PluginTarget (Node.class)
	public String getConflictTaxJsonAltRel(@Source Node source,
			@Description( "The dominant source.")
			@Parameter(name = "domsource", optional = true) String domsource,
			@Description( "The list of alternative relationships to prefer." )
			@Parameter( name = "altrels", optional = true ) Long[] altrels,
			@Description( "A new relationship nub." )
    		@Parameter( name = "nubrel", optional = true ) Long nubrel ){
		String retst="";
		GraphExplorer ge = new GraphExplorer();
		if(nubrel != null){
			Relationship rel = source.getGraphDatabase().getRelationshipById(nubrel);
		}else{
			ArrayList<Long> rels = new ArrayList<Long>();
			if(altrels != null)
				for (int i=0;i<altrels.length;i++){rels.add(altrels[i]);}
			retst = ge.constructJSONAltRels(source,domsource,rels);
		}
		return retst;
	}
}
