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
	
	/**
	 * Create a cycle with N nodes in the graph G. Return the set of nodes in the cycle.
	 * @param G
	 * @param N
	 * @return
	 * @throws IOException
	 */
	private static List<Node> createNCycle(GraphDatabaseAgent G, int N) throws IOException {
				
		Transaction tx = G.beginTx();
		List<Node> nodes = new ArrayList<Node>();
		for (int i = 0; i < N; i++) {
			Node n = G.createNode();
			nodes.add(n);
			if (0 < i && i < N) {
				nodes.get(i-1).createRelationshipTo(n, RelType.STREECHILDOF);
			}
		}
		nodes.get(N-1).createRelationshipTo(nodes.get(0), RelType.STREECHILDOF);
		tx.success();
		tx.finish();

		return nodes;
	}
	
	/**
	 * Create a cycle with N nodes and c random chords in the graph G. Return the set of nodes in the cycle.
	 */
	private static List<Node> createChordedNCycle(GraphDatabaseAgent G, int N, int c) throws IOException {
		List<Node> cycle = createNCycle(G, N);

		// need to check max number chords possible for a cycle of length N
		//	N	maxChords		(N-3) * N
		//	2	0
		//	3	0
		//	4	4				1 * 4 = 4
		//	5	20
		//	6	36		
//		if (c > maxChords) { throw new IllegalArgumentException(); }

		Transaction tx = G.beginTx();
		for (int i = 0; i <  c; i++) {
			Random r = new Random();
			int p = r.nextInt(N);
			int q = -1;
			while (p == q || q < 0) { q = r.nextInt(N); }
			G.getNodeById((long) p).createRelationshipTo(G.getNodeById((long) q), RelType.STREECHILDOF);
		}
		tx.success();
		tx.finish();
		
		return cycle;
	}
	
	private static GraphDatabaseAgent getCleanDatabase(String dbname) throws IOException {
		FileUtils.deleteDirectory(new File(dbname));
		return new GraphDatabaseAgent(new EmbeddedGraphDatabase(dbname));
	}
	
	private static GraphDatabaseAgent simpleCycle(int N) throws IOException {
		GraphDatabaseAgent G = getCleanDatabase("test.db");
		createNCycle(G, N);
		return G;
	}

	private static GraphDatabaseAgent chordedCycle(int N, int c) throws IOException {
		GraphDatabaseAgent G = getCleanDatabase("test.db");
		createChordedNCycle(G, N, c);
		return G;
	}
	
	private static GraphDatabaseAgent chainOfSimpleCycles(int N, int m) throws IOException {
		GraphDatabaseAgent G = getCleanDatabase("test.db");
		Random r = new Random();
		List<Node> lastCycle = null;
		Transaction tx = G.beginTx();
		for (int i = 0; i < m; i++) {
			Node toConnect = null;
			if (lastCycle != null) { toConnect = lastCycle.get(r.nextInt(lastCycle.size())); }
			lastCycle = createNCycle(G, N);
			if (toConnect != null) {
				lastCycle.get(r.nextInt(lastCycle.size())).createRelationshipTo(toConnect, RelType.STREECHILDOF);
			}
		}
		tx.success();
		tx.finish();
		return G;
	}
	
	private static GraphDatabaseAgent cycleOfSimpleCycles(int N, int m) throws IOException {
		GraphDatabaseAgent G = getCleanDatabase("test.db");
		Random r = new Random();
		List<Node> lastCycle = null;
		List<Node> firstCycle = null;
		Transaction tx = G.beginTx();
		for (int i = 0; i < m; i++) {
			Node toConnect = null;
			if (lastCycle != null) { toConnect = lastCycle.get(r.nextInt(lastCycle.size())); }
			lastCycle = createNCycle(G, N);
			if (toConnect != null) {
				lastCycle.get(r.nextInt(lastCycle.size())).createRelationshipTo(toConnect, RelType.STREECHILDOF);
			}
			if (firstCycle == null) { firstCycle = lastCycle; }
		}
		// connect the cycle of cycles
		firstCycle.get(r.nextInt(firstCycle.size())).createRelationshipTo(lastCycle.get(r.nextInt(lastCycle.size())), RelType.STREECHILDOF);
		tx.success();
		tx.finish();
		return G;
	}
	
	private static void simpleTest(GraphDatabaseAgent G) {		
		System.out.println(getSTREEAdjacencyList(G));
		for (Set<Node> component : new TarjanSCC(G, RelType.STREECHILDOF)) {
			System.out.println(component);
		}
	}
	
	private static String getSTREEAdjacencyList(GraphDatabaseAgent G) {
		StringBuilder s = new StringBuilder();
		for (Node v : G.getAllNodes()) {
			s.append(v.getId() + "\t");
			for (Relationship w : v.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
				s.append(w.getEndNode().getId() + "\t");
			}
			s.append('\n');
		}
		return s.toString();
	}
	
	public static void main(String[] args) throws IOException {
		simpleTest(simpleCycle(10));
		simpleTest(chordedCycle(10, 4)); // should return one connected component with everything in it
		simpleTest(chainOfSimpleCycles(5, 5)); // should return each cycle as an independent connected component
		simpleTest(cycleOfSimpleCycles(5, 5)); // should return one connected component with everything in it
	}

}
