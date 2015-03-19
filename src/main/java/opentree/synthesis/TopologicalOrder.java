package opentree.synthesis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.opentree.graphdb.GraphDatabaseAgent;

public class TopologicalOrder implements Iterable<Node> {

	private Set<Node> unmarked = new HashSet<Node>();
	private Set<Node> temporaryMarked = new HashSet<Node>();
	private List<Node> nodes = new LinkedList<Node>();
	private Set<Relationship> excludedRels;
	
	private final RelationshipType[] relTypes;
	
	public TopologicalOrder(GraphDatabaseAgent G, Set<Relationship> excludedRels, RelationshipType... relTypes) {

		this.excludedRels = excludedRels;

		this.relTypes = relTypes;
		
		for (Node n : G.getAllNodes()) {
			if (n.hasRelationship(relTypes)) {
				unmarked.add(n);
			}
		}

		while (! unmarked.isEmpty()) {
			visit(unmarked.iterator().next());
		}
	}
	
	private void visit(Node n) {
		if (temporaryMarked.contains(n)) {
			throw new IllegalArgumentException("The graph contains a directed cycle that includes the node: " + n);
		}

		if (unmarked.contains(n)) {
			temporaryMarked.add(n);
			for (Relationship m : n.getRelationships(Direction.INCOMING, relTypes)) {
				if (! excludedRels.contains(m)) {
					visit(m.getStartNode());
				}
			}
			
			unmarked.remove(n);
			temporaryMarked.remove(n);
			nodes.add(n);
		}
	}
	
	@Override
	public Iterator<Node> iterator() {
		return nodes.iterator();
	}
	
	public static void main(String[] args) throws IOException {
		
		GraphDatabaseAgent G = GraphGenerator.randomTree(10, 2, "test.db");
		System.out.println("input graph: \n" + GraphGenerator.getSTREEAdjacencyList(G));

		TopologicalOrder order = new TopologicalOrder(G, new HashSet<Relationship>(), RelType.STREECHILDOF);
		System.out.println("nodes in topological order: \n");
		for (Node n : order) {
			System.out.println(n);
		}
	}
}
