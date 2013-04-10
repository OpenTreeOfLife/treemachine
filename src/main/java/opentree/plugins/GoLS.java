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
			sourceArrayList = ge.getSourceList();
		} finally {
			ge.shutdownDB();
		}
		return OpenTreeMachineRepresentationConverter.convert(sourceArrayList);
	}

	@Description("Returns newick for the specified source tree ID")
	@PluginTarget(GraphDatabaseService.class)
	public Representation getSourceTreeNewick(
			@Source GraphDatabaseService graphDb,
			@Description( "The identifier for the source tree to return")
			@Parameter(name = "souretreeid", optional = false) String sourceName) throws TreeNotFoundException {
		GraphExplorer ge = new GraphExplorer(graphDb);
		JadeTree tree;
		String newick;
	try {
			tree = ge.reconstructSource(sourceName);
			newick = tree.getRoot().getNewick(tree.getHasBranchLengths());
		} finally {
			ge.shutdownDB();
		}

		return OpenTreeMachineRepresentationConverter.convert(newick);
	}
}
