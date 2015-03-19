package opentree.synthesis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import opentree.constants.RelType;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.opentree.graphdb.GraphDatabaseAgent;

public class GraphGenerator {
	
	private static <T> T popLast(List<T> x) {
		if (x.size() < 1) {
			throw new NoSuchElementException();
		}
		int i = lastIndex(x);
		T t = x.get(i);
		x.remove(i);
		return t;
	}
	
	private static int lastIndex(List<?> x) {
		return x.size() - 1;
	}
	
	/**
	 * Create a cycle with N nodes in the graph G. Return the set of nodes in the cycle.
	 * @param G
	 * @param N
	 * @return
	 * @throws IOException
	 */
	private static List<Node> createNCycle(GraphDatabaseAgent G, int N) {
				
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
	private static List<Node> createChordedNCycle(GraphDatabaseAgent G, int N, int c) {
		List<Node> cycle = createNCycle(G, N);

		// need to check max number chords possible for a cycle of length N
		//	N	maxChords		(N-3) * N
		//	2	0
		//	3	0				0 * 3 = 0
		//	4	4				1 * 4 = 4
		//	5	10				2 * 5 = 10
		//	6	18				3 * 6 = 18
		int maxChords = (N-3) * N;
		if (c > maxChords) { throw new IllegalArgumentException("unattainable number of chords " + c + " for a cycle of size " + N); }

		Transaction tx = G.beginTx();
		for (int i = 0; i <  c; i++) {
			Random r = new Random();
			int p = r.nextInt(N-1) + 1;
			int q = -1;
			while (p == q || q < 1) { q = r.nextInt(N-1)+1; }
			G.getNodeById((long) p).createRelationshipTo(G.getNodeById((long) q), RelType.STREECHILDOF);
		}
		tx.success();
		tx.finish();
		
		return cycle;
	}
	
	/**
	 * Create a random tree with the specified number of tips and return the root node. The maxChildren argument
	 * specifies the maximum size for a polytomy (minimum 2, cannot be greater than N).
	 */
	private static Node createRandomNTree(GraphDatabaseAgent G, int N, int maxChildren) {
		
		if (maxChildren < 2 || maxChildren > N) { throw new IllegalArgumentException(); }
		
		maxChildren -= 2; // account for the fact that we will always add at least two children
		Random r = new Random();
		
		Transaction tx = G.beginTx();
		List<Node> unplaced = new ArrayList<Node>();
		for (int i = 0; i < N; i++) {
			Node n = G.createNode();
			unplaced.add(n);
		}
		
		while (unplaced.size() > 1) {
			Node p = G.createNode();
			int nChildren = r.nextInt(maxChildren + 1) + 2; // ensure that we add at least two children
			for (int i = 0; i < nChildren && !unplaced.isEmpty(); i++) {
				int b = r.nextInt(unplaced.size());
				unplaced.get(b).createRelationshipTo(p, RelType.STREECHILDOF);
				unplaced.remove(b);
			}
			unplaced.add(p);
		}
		tx.success();
		tx.finish();
		
		return unplaced.get(0); // return the root
	}
	
	public static GraphDatabaseAgent emptyGraph(String dbname) throws IOException {
		FileUtils.deleteDirectory(new File(dbname));
		return new GraphDatabaseAgent(new EmbeddedGraphDatabase(dbname));
	}
	
	/**
	 * return a graph containing a tree
	 * @throws IOException 
	 */
	public static GraphDatabaseAgent randomTree(int N, int maxChildren, String dbname) throws IOException {
		GraphDatabaseAgent G = emptyGraph(dbname);
		createRandomNTree(G, N, maxChildren);
		return G;
	}
	
	/**
	 * return a graph containing a tree with N tips and nBackEdges backward edges.
	 * @throws IOException 
	 */
	public static GraphDatabaseAgent randomTreeWithBackEdges(int nTips, int maxChildren, int nBackEdges, String dbname) throws IOException {
		if (nTips < 3) { throw new IllegalArgumentException(); }
		
		// generate a tree
		GraphDatabaseAgent G = emptyGraph(dbname);
		createRandomNTree(G, nTips, maxChildren);
		List<Node> all = new ArrayList<Node>();
		for (Node n : new TopologicalOrder(G, new HashSet<Relationship>(), RelType.STREECHILDOF)) { if (n.getId() != 0) all.add(n); }
		int N = all.size() - 1;
		System.out.println(N);
		System.out.println(N-nTips);

		// make back edges
		Transaction tx = G.beginTx();
		Random r = new Random();
		for (int i = 0; i < nBackEdges; i++) {
		
			// pick a random internal node `parent` and gather all its descendants
			Node p = G.getNodeById((long) r.nextInt(N - nTips) + nTips);
			LinkedList<Node> toVisit = new LinkedList<Node>();
			toVisit.add(p);
			List<Node> descendants = new ArrayList<Node>();
			while (toVisit.size() > 0) {
				addChildren(toVisit.pop(), descendants);
			}
			
			// connect parent as a child of a random descendants
			p.createRelationshipTo(descendants.get(r.nextInt(descendants.size())), RelType.STREECHILDOF);
		}
		tx.success();
		tx.finish();
		
		return G;
	}
	
	private static void addChildren(Node p, List<Node> toAdd) {
		for (Relationship s : p.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
			toAdd.add(s.getStartNode());
		}
	}

	
	public static GraphDatabaseAgent simpleCycle(int N, String dbname) throws IOException {
		GraphDatabaseAgent G = emptyGraph(dbname);
		createNCycle(G, N);
		return G;
	}

	public static GraphDatabaseAgent chordedCycle(int N, int c, String dbname) throws IOException {
		GraphDatabaseAgent G = emptyGraph(dbname);
		createChordedNCycle(G, N, c);
		return G;
	}
	
	public static GraphDatabaseAgent chainOfSimpleCycles(int N, int m, String dbname) throws IOException {
		GraphDatabaseAgent G = emptyGraph(dbname);
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
	
	public static String getSTREEAdjacencyList(GraphDatabaseAgent G) {
		StringBuilder s = new StringBuilder();
		for (Node v : G.getAllNodes()) {
			s.append(v.getId() + "\t");

			// really, this should be collecting Direction.OUTGOING relationships.
			// our graphs are reversed because we create *CHILDOF rels instead of *PARENTOF (which is more standard)
			for (Relationship w : v.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
				s.append(w.getStartNode().getId() + "\t");
			}
			s.append('\n');
		}
		return s.toString();
	}
	
	public static GraphDatabaseAgent cycleOfSimpleCycles(int N, int m, String dbname) throws IOException {
		GraphDatabaseAgent G = emptyGraph(dbname);
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
}
