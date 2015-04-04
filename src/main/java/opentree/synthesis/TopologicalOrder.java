package opentree.synthesis;

import static org.opentree.utils.GeneralUtils.print;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
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

	private final Node root;
	private final GraphDatabaseAgent G;
	private final boolean usingAllNodes;
	
	private Set<Node> unmarked = new HashSet<Node>();
	private Set<Node> temporaryMarked = new HashSet<Node>();
//	private Set<Long> unmarked = new TreeSet<Long>();
//	private Set<Long> temporaryMarked = new TreeSet<Long>();
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
		this.G = new GraphDatabaseAgent(root.getGraphDatabase());
		this.usingAllNodes = false;
		this.relTypes = relTypes;
		print ("starting at", root);	
	}

	public TopologicalOrder(GraphDatabaseAgent G, RelationshipType... relTypes) {
		this.root = null;
		this.G = G;
		this.usingAllNodes = true;
		this.relTypes = relTypes;
	}

	public TopologicalOrder validateWith(Predicate<Node> validateNode) {
		this.validateNode = validateNode;
		return this;
	}
	
	private Iterable<Node> descendants(Node n) {
		return new Iterable<Node> () {
			public Iterator<Node> iterator() {
//				return new BreadthFirstIterator(n);
				return new DescendantIterator(n);
			}
		};
	}
	
	/**
	 * Personalized implementation
	 */
	private class DescendantIterator implements Iterator<Node> {
		
		private Stack<Node> toVisit = new Stack<Node>();
		private Set<Node> observed = new HashSet<Node>();
		private Node next = null;
		
		public DescendantIterator (Node root) {
//			if (! validate(root)) { throw new IllegalArgumentException("the root " + root + " does not pass the validation criteria specified by " + validateNode); }
			queue(root);
			loadNext();
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public Node next() {
			Node n = next;
			loadNext();
			return n;
		}
		
		private void queue(Node n) {
//			print("adding", n, "to queue");
			toVisit.add(n);
			observed.add(n);
		}
		
		private void loadNext() {
			Node n = toVisit.isEmpty() ? null : toVisit.pop();
			if (n != null) {
				for (Relationship r : n.getRelationships(Direction.INCOMING, relTypes)) {
					Node c = r.getStartNode();
					if (! observed.contains(c)) {
						queue(c);
					}
				}
			}
			next = n;
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
	
	private Iterable<Node> getNodes() {
		assert ! (usingAllNodes == false && root == null);

		Iterable<Node> nodes;
		if (usingAllNodes) {
			nodes = G.getAllNodes();
		} else {
			nodes = descendants(root);
			/*
			TraversalDescription d = Traversal.description().breadthFirst().uniqueness(Uniqueness.NONE);
			for (int i = 0; i < relTypes.length; i++) {
				d = d.relationships(relTypes[i], Direction.INCOMING);
			}
			nodes = d.traverse(root).nodes(); */
		}
		return nodes;
	}

	private void sort() {
		for (Node n : getNodes()) {
			if (n.hasRelationship(relTypes)) {
				unmarked.add(n);
			}
		}
		while (! unmarked.isEmpty()) {
			visit(unmarked.iterator().next());
		}
	}
	
	private void visit(Node n) {
//		long nid = n.getId();
//		print("visiting", n);
		if (temporaryMarked.contains(n)) {
			throw new IllegalArgumentException("The graph contains a directed cycle that includes the node: " + n);
		}

		if (unmarked.contains(n)) {
			temporaryMarked.add(n);
			for (Relationship m : n.getRelationships(Direction.INCOMING, relTypes)) {
//				print("looking for children of", n);
//				if (! excludedRels.contains(m)) {
					visit(m.getStartNode());
//				}
			}
			
			unmarked.remove(n);
			temporaryMarked.remove(n);
			
			if (validate(n)) {
				nodes.add(n);
//				print("found a valid node:", n);
			}
			
//			print ("done with", n + ", adding to sorted nodes");
		}
		// testing
	}
	
	@Override
	public Iterator<Node> iterator() {
		sort();
		print("sorted", nodes.size(), "in topological order.");
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
