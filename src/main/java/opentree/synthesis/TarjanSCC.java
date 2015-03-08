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
import java.util.Set;

import org.neo4j.graphdb.Transaction;

import opentree.constants.RelType;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;
import org.opentree.graphdb.GraphDatabaseAgent;

/**
 * Tarjan's strongly connected components algorithm.
 * 
 * input: graph G = (V, E)
 * output: set of strongly connected components (sets of vertices)
 */
public class TarjanSCC implements Iterable<Set<Node>> {
	
	// index := 0
	int i = 0;
	Map<Node, Integer> index = new HashMap<Node, Integer>();
	Map<Node, Integer> lowLink = new HashMap<Node, Integer>();
	Map<Node, Boolean> onStack = new HashMap<Node, Boolean>();
	
	// S := empty
	LinkedList<Node> S = new LinkedList<Node>();
	
	Set<Set<Node>> components = new HashSet<Set<Node>>();

	public TarjanSCC(GraphDatabaseAgent G) {
		
		// for each v in V do
		//	if (v.index is undefined) then
	    //  	strongconnect(v)
	    //	end if
		// end for
		for (Node v : G.getAllNodes()) {
			if (index.get(v) == null) { // if we have not yet visited this node
				strongConnect(v);
			}
		}
	}
	
	// function strongconnect(v)
	private void strongConnect(Node v) {
		
		// Set the depth index for v to the smallest unused index
		// v.index := index
		// v.lowlink := index
		// index := index + 1
		// S.push(v)
		// v.onStack := true
		index.put(v, i);
		lowLink.put(v, i++);
		S.push(v);
		onStack.put(v, true);
		
	    // Consider successors of v
		for (Relationship r : v.getRelationships(Direction.INCOMING, RelType.STREECHILDOF, RelType.TAXCHILDOF)) { // for each (v, w) in E do
			Node w = r.getStartNode();

			// if (w.index is undefined) then
			if (index.get(w) == null) { // Successor w has not yet been visited; recurse on it
				strongConnect(w); // strongconnect(w)
				lowLink.put(v, minLink(v, w)); // v.lowlink  := min(v.lowlink, w.lowlink)

			// else if (w.onStack) then
			} else if (onStack.get(w) != null && onStack.get(w)) { // Successor w is in stack S and hence in the current SCC
				lowLink.put(v, minLink(v, w)); // v.lowlink  := min(v.lowlink, w.index)

			} // end if
		} // end for

		// If v is a root node, pop the stack and generate an SCC
		// if (v.lowlink = v.index) then
		if (lowLink.get(v) == index.get(v)) {
			
			// start a new strongly connected component
			Set<Node> C = new HashSet<Node>();
			
			// repeat until (w = v)
			Node w = null;
			while (w != v) {
				w = S.pop(); // w := S.pop()
				onStack.put(w, false); // w.onStack := false
				C.add(w); // add w to current strongly connected component
			}
			
			components.add(C); // output the current strongly connected component

		} // end if
	} // end function
	
	private int minLink(Node v, Node w) {
		int lv = lowLink.get(v);
		int lw = lowLink.get(w);
		return lv < lw ? lv : lw;
	}

	@Override
	public Iterator<Set<Node>> iterator() {
		return components.iterator();
	}
	
	private static GraphDatabaseAgent nCycle(int N) throws IOException {
		
		FileUtils.deleteDirectory(new File("test.db"));
		GraphDatabaseAgent G = new GraphDatabaseAgent(new EmbeddedGraphDatabase("test.db"));
		
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
		return G;
	}
	
	private static void simpleTest(GraphDatabaseAgent G) {
		System.out.println(getSTREEAdjacencyList(G));
		for (Set<Node> component : new TarjanSCC(G)) {
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
		simpleTest(nCycle(10));
	}

}
