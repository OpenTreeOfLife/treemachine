package org.opentree.tag.treeimport;

import jade.tree.JadeTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opentree.GraphBase;

import org.opentree.graphdb.GraphDatabaseAgent;
import org.opentree.nexson.io.NexsonTree;

public class BipartInitializer<Tree> extends GraphBase {

	List<NexsonTree> trees = new ArrayList<NexsonTree>();
	Map<Object, Long> taxonIdMap = new HashMap<Object, Long>();
	
	/**
	 * Open the graph db through the given agent object.
	 * @param gdb
	 */
	public BipartInitializer(GraphDatabaseAgent gdb) {
		super(gdb);
	}
	
	public void addNexsonTree(NexsonTree tree) {
		trees.add(tree);
	}
	
	public void importAllTrees() {

		// generate the starting set of bipartitions
		List<Bipartition> treeBiparts = new ArrayList<Bipartition>();
		for (NexsonTree t : trees) {
//			treeBiparts.addAll(t.getBipartitions(t));
		}
		BipartSetSum startingSet = new BipartSetSum(treeBiparts);
		
		// make the paths from bipart sums and the set of imported trees
		
		// process the paths to make the nodes
		
		// import the trees using the lica util to map nodes
	}
	
	private static List<Bipartition> getBipartitions(JadeTree tree) {
		return null;
	}

}
