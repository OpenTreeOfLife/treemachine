package opentree.synthesis.mwis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import opentree.constants.RelType;
import opentree.synthesis.GraphGenerator;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.opentree.graphdb.GraphDatabaseAgent;

public class TopologicalOrder implements Iterable<Node> {

	private Set<Node> unmarked = new HashSet<Node>();
	private Set<Node> temporaryMarked = new HashSet<Node>();
	private List<Node> nodes = new LinkedList<Node>();
	
	private final RelationshipType[] relTypes;
	
	public TopologicalOrder(GraphDatabaseAgent G, RelationshipType... relTypes) {

		this.relTypes = relTypes;
		
		// collect all the nodes
		for (Node n : G.getAllNodes()) { unmarked.add(n); }

		//	while there are unmarked nodes do
		//	    select an unmarked node n
		//	    visit(n) 
		while (! unmarked.isEmpty()) {
			visit(unmarked.iterator().next());
		}
	}
	
	//	function visit(node n)
	private void visit(Node n) {
		if (temporaryMarked.contains(n)) {
			//	    if n has a temporary mark then stop (not a DAG)
			throw new IllegalArgumentException("The specified graph contains a directed cycle (containing the node: " + n + ")");
		}
		
		//	    if n is not marked (i.e. has not been visited yet) then
		//	        mark n temporarily
		temporaryMarked.add(n);

		//	        for each node m with an edge from n to m do
		//	            visit(m)
		for (Relationship m : n.getRelationships(Direction.INCOMING, relTypes)) {
			visit(m.getStartNode());
		}
		
		//	        mark n permanently
		//	        unmark n temporarily
		//	        add n to head of L
		unmarked.remove(n);
		temporaryMarked.remove(n);
		nodes.add(n);
	}
	
	/*
	RelationshipType[] relTypes;
	Set<Node> nodes = new LinkedHashSet<Node>();   // iterates over items in order of addition to set
	Set<Node> visited = new LinkedHashSet<Node>(); // records those visited so as to not revisit
	
	public TopologicalOrder(Node root, RelationshipType... relTypes) {
		this.relTypes = relTypes;
		addNodesRecursive(root);
	}
	
	private void addNodesRecursive(Node n) {
		for (Relationship r : n.getRelationships(Direction.INCOMING, relTypes)) {
			Node startNode = r.getStartNode();
			if (! visited.contains(startNode)) {
				visited.add(startNode);
				addNodesRecursive(startNode);
			}
		}
		if (nodes.contains(n) == false) {
			nodes.add(n); // first occurrence is used for ordering, subsequent ones have no effect
		}
	}
	
//	private void addNodesRecursive(Node n) {
//		for (Relationship r : n.getRelationships(Direction.INCOMING, relTypes)) {
//			addNodesRecursive(r.getStartNode());
//		}
//		nodes.add(n); // first occurrence is used for ordering, subsequent ones have no effect
//	} */

	@Override
	public Iterator<Node> iterator() {
		return nodes.iterator();
	}
	
	public static void main(String[] args) throws IOException {
		
		GraphDatabaseAgent G = GraphGenerator.randomTree(100, 4, "test.db");
		System.out.println("input graph: \n" + GraphGenerator.getSTREEAdjacencyList(G));

		TopologicalOrder order = new TopologicalOrder(G, RelType.STREECHILDOF);
		System.out.println("nodes in topological order: \n");
		for (Node n : order) {
			System.out.println(n);
		}
	}
}
