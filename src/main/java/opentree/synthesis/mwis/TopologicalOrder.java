package opentree.synthesis.mwis;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

public class TopologicalOrder implements Iterable<Node> {

	RelationshipType[] relTypes;
	Set<Node> nodes = new LinkedHashSet<Node>(); // iterates over items in order of addition to set
	
	public TopologicalOrder(Node root, RelationshipType... relTypes) {
		this.relTypes = relTypes;
		addNodesRecursive(root);
	}
	
	private void addNodesRecursive(Node n) {
		for (Relationship r : n.getRelationships(Direction.INCOMING, relTypes)) {
			addNodesRecursive(r.getStartNode());
		}
		nodes.add(n); // first occurrence is used for ordering, subsequent ones have no effect
	}

	@Override
	public Iterator<Node> iterator() {
		return nodes.iterator();
	}
}
