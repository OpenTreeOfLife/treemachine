package opentree.synthesis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.neo4j.graphdb.Transaction;

import opentree.constants.RelType;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.opentree.graphdb.GraphDatabaseAgent;

/**
 * Tarjan's strongly connected components algorithm.
 * 
 * Based on pseudocode from: http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
 * 
 * input: graph G = (V, E)
 * output: set of strongly connected components (sets of vertices)
 */
public class TarjanSCC implements Iterable<Set<Node>> {
	
	int i = 0;
	LinkedList<Node> S = new LinkedList<Node>();

	// these could be more efficiently implemented as arrays if we knew |V|
	Map<Node, Integer> index = new HashMap<Node, Integer>();
	Map<Node, Integer> lowLink = new HashMap<Node, Integer>();
	Map<Node, Boolean> onStack = new HashMap<Node, Boolean>();
	
	Set<Set<Node>> components = new HashSet<Set<Node>>();

	private final RelType[] relTypes;

	public TarjanSCC(GraphDatabaseAgent G, RelType ... relTypes) {
		this.relTypes = relTypes;
		for (Node v : G.getAllNodes()) {
			if (index.get(v) == null) { // if we have not yet visited this node
				strongConnect(v);
			}
		}
	}
	
	private void strongConnect(Node v) {
		
		// Set the depth index for v to the smallest unused index
		index.put(v, i);
		lowLink.put(v, i++);
		S.push(v);
		onStack.put(v, true);
		
	    // Consider successors of v
		for (Relationship r : v.getRelationships(Direction.INCOMING, relTypes)) {
			Node w = r.getStartNode();

			if (index.get(w) == null) { // Successor w has not yet been visited; recurse on it
				strongConnect(w);
				lowLink.put(v, minLink(v, w));

			} else if (onStack.get(w) != null && onStack.get(w)) { // Successor w is in stack S and hence in the current SCC
				lowLink.put(v, minLink(v, w));
			}
		}

		// If v is a root node, pop the stack and generate an SCC
		if (lowLink.get(v) == index.get(v)) {
			
			// start a new SCC and add all the nodes that belong to it
			Set<Node> C = new HashSet<Node>();
			Node w = null;
			while (w != v) {
				w = S.pop();
				onStack.put(w, false);
				C.add(w);
			}
			
			components.add(C); // save this SCC

		}
	}
	
	private int minLink(Node v, Node w) {
		int lv = lowLink.get(v);
		int lw = lowLink.get(w);
		return lv < lw ? lv : lw;
	}

	@Override
	public Iterator<Set<Node>> iterator() {
		return components.iterator();
	}
	
	private static void simpleTest(GraphDatabaseAgent G, int e) {

		System.out.println("input graph:");
		System.out.println(GraphGenerator.getSTREEAdjacencyList(G));

		System.out.println("strongly connected components:");
		int i = 0;
		for (Set<Node> component : new TarjanSCC(G, RelType.STREECHILDOF)) {
			System.out.println(i++ + ", " + component);
		}
		System.out.println();
		
		if (e + 1 != i) { // account for the SCC containing only the node 0
			throw new AssertionError("found " + i + " cycles but expected " + e);
		}
	}
	
	public static void main(String[] args) throws IOException {
		String dbname = "test.db";
		simpleTest(GraphGenerator.simpleCycle(10, dbname), 1);
		simpleTest(GraphGenerator.chordedCycle(10, 4, dbname), 1);
		simpleTest(GraphGenerator.chainOfSimpleCycles(5, 5, dbname), 5);
		simpleTest(GraphGenerator.cycleOfSimpleCycles(5, 5, dbname ), 1);
	}

}
