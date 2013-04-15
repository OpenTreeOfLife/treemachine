package opentree.plugins;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import jade.tree.JadeTree;

import opentree.GraphExplorer;
import opentree.GraphExporter;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OpenTreeMachineRepresentationConverter;
import opentree.TreeNotFoundException;
// Graph of Life Services 
public class GoLS extends ServerPlugin {

	@Description("Returns a list of all source tree IDs")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSourceTreeIDs(
			@Source GraphDatabaseService graphDb) {
		GraphExplorer ge = new GraphExplorer(graphDb);
		ArrayList<String>  sourceArrayList;
		try {
			sourceArrayList = ge.getTreeIDList();
		} finally {
			ge.shutdownDB();
		}
		return OpenTreeMachineRepresentationConverter.convert(sourceArrayList);
	}

	@Description("Returns if format is \"newick\" then return JSON will have two fields: newick and treeID. If format = \"arguson\" then the return object will be the form of JSON expected by argus")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSourceTree(
			@Source GraphDatabaseService graphDb,
			@Description( "The identifier for the source tree to return")
			@Parameter(name = "treeID", optional = false) String treeID,
			@Description( "The name of the return format (default is newick)")
			@Parameter(name = "format", optional = true) String format) throws TreeNotFoundException {
		boolean emitNewick = false;
		if (format == null || format.length() == 0 || format.equalsIgnoreCase("newick")) {
			emitNewick = true;
		} else if (!format.equalsIgnoreCase("arguson")) {//@TEMP what is the name of the format argus likes???
			throw new IllegalArgumentException("Expecting either \"newick\" or \"arguson\" as the format.");
		}
		String newick = "";
		String retst = ""; // suppressing uninit warning
		GraphExplorer ge = new GraphExplorer(graphDb);
		GraphExporter gExporter = null;
		try {
			if (emitNewick) {
				JadeTree tree = ge.reconstructSourceByTreeID(treeID);
				newick = tree.getRoot().getNewick(tree.getHasBranchLengths());
			} else {
				// Code from GetJsons.java getConflictTaxJsonAltRel
				ArrayList<Long> rels = new ArrayList<Long>();
				gExporter = new GraphExporter(graphDb);
				Node rootNode = ge.getRootNodeByTreeID(treeID);
				int maxdepth = 3;
				String sourcename = ge.findSourceNameFromTreeID(treeID);
				retst = gExporter.constructJSONAltRels(rootNode, sourcename, rels, maxdepth);
			}
		} finally {
			ge.shutdownDB();
			if (gExporter != null) {
				gExporter.shutdownDB();
			}
		}
		if (emitNewick) {
			HashMap<String, Object> responseMap = new HashMap<String, Object>();
			responseMap.put("newick", newick);
			responseMap.put("treeID", treeID);
			return OpenTreeMachineRepresentationConverter.convert(responseMap);
		} else {
			return OpenTreeMachineRepresentationConverter.convert(retst); // double wrapping string
		}
	}
}
