package opentree.synthesis;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;
import org.neo4j.kernel.Uniqueness;
import org.opentree.graphdb.GraphDatabaseAgent;

public class TopologicalOrder implements Iterable<Node> {

	Node root = null;
	GraphDatabaseAgent G = null;
	
	private Set<Node> unmarked = new HashSet<Node>();
	private Set<Node> temporaryMarked = new HashSet<Node>();
	private List<Node> nodes = new LinkedList<Node>();
	private Predicate<Node> validateNode = null;
//	private Set<Relationship> excludedRels;
	
	private final RelationshipType[] relTypes;

	/*
	public TopologicalOrder(Node root, Set<Relationship> excludedRels, RelationshipType... relTypes) {
		this.excludedRels = excludedRels;
		this.relTypes = relTypes;
		sort(breadthFirst(root));
	}
	
	public TopologicalOrder(GraphDatabaseAgent G, Set<Relationship> excludedRels, RelationshipType... relTypes) {
		this.excludedRels = excludedRels;
		this.relTypes = relTypes;
		sort(G.getAllNodes());
	} */
	
	public TopologicalOrder(Node root, RelationshipType... relTypes) {
		this.root = root;
		this.relTypes = relTypes;
	}

	public TopologicalOrder(GraphDatabaseAgent G, RelationshipType... relTypes) {
		this.G = G;
		this.relTypes = relTypes;
	}

	public TopologicalOrder validateWith(Predicate<Node> validateNode) {
		this.validateNode = validateNode;
		return this;
	}
		
	private Iterable<Node> validDescendants(Node n) {
		return new Iterable<Node> () {
			public Iterator<Node> iterator() {
//				return new BreadthFirstIterator(n);
				return new ValidatingIterator(n);
			}
		};
	}
	
	/**
	 * Personalized implementation
	 */
	private class ValidatingIterator implements Iterator<Node> {
		
		private LinkedList<Node> toVisit = new LinkedList<Node>();
		private Set<Long> visited = new HashSet<Long>();
		
		public ValidatingIterator (Node root) {
			if (! validate(root)) { throw new IllegalArgumentException("the root " + root + " does not pass the validation criteria specified by " + validateNode); }
			toVisit.add(root);
		}

		@Override
		public boolean hasNext() {
			return ! toVisit.isEmpty();
		}

		@Override
		public Node next() {
			Node p = toVisit.pollFirst();
			visited.add(p.getId());
			for (Relationship r : p.getRelationships(Direction.INCOMING, relTypes)) {
				Node c = r.getStartNode();
				if (! visited.contains(c.getId()) && validate(c)) {
					
					// Can we exclude the tips here? I think we can, they should still be visited by the sort() procedure...
					if (c.hasRelationship(Direction.INCOMING, relTypes)) {
						toVisit.addLast(c);
					}
				}
			}
			return p;
		}
	}

	/*
	/**
	 * Standard neo4j implementation.
	 *
	private class ValidatingIterator implements Iterator<Node> {
		
		private Node nextValidNode = null;
		private Iterator<Node> nodes;
		
		public ValidatingIterator(Node root) {
			if (! validate(root)) { throw new IllegalArgumentException("the root " + root + " does not pass the validation criteria specified by " + validateNode); }

			TraversalDescription d = Traversal.description().breadthFirst().uniqueness(Uniqueness.NONE);
			for (int i = 0; i < relTypes.length; i++) {
				d = d.relationships(relTypes[i], Direction.INCOMING);
			}
			nodes = d.traverse(root).nodes().iterator();
			loadNextValid();
		}
		
		private void loadNextValid() {
			while (true) {
				if (! nodes.hasNext()) { nextValidNode = null; break;}
				Node n = nodes.next();
				if (validate(n)) { nextValidNode = n; break; }
			}
		}
		
		public boolean hasNext() {
			return nextValidNode != null;
		}
		
		public Node next() {
			Node n = nextValidNode;
			loadNextValid();
			return n;
		}
	} */
	
	private boolean validate(Node n) {
		return validateNode == null ? true : validateNode.test(n);
	}

	private void sort() {
		if (G == null && root == null) { throw new NullPointerException(); }

		Iterable<Node> nodes = G == null ? validDescendants(root) : G.getAllNodes();
				
		for (Node n : nodes) {
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
//				if (! excludedRels.contains(m)) {
					visit(m.getStartNode());
//				}
			}
			
			unmarked.remove(n);
			temporaryMarked.remove(n);
			nodes.add(n);
			
			// testing
			System.out.println(nodes.size() + " nodes sorted.");
		}
	}
	
	@Override
	public Iterator<Node> iterator() {
		sort();
		return nodes.iterator();
	}
	
	public static void main(String[] args) throws IOException {
		
		GraphDatabaseAgent G = GraphGenerator.randomTree(10, 2, "test.db");
		System.out.println("input graph: \n" + GraphGenerator.getSTREEAdjacencyList(G));

		TopologicalOrder order = new TopologicalOrder(G, RelType.STREECHILDOF);
		System.out.println("nodes in topological order: \n");
		for (Node n : order) {
			System.out.println(n);
		}
	}
}
