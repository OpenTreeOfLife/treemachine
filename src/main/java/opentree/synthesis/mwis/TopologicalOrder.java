package opentree.synthesis.mwis;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

public class TopologicalOrder implements Iterable<Node> {

	RelationshipType relType;
	Set<Node> nodes = new LinkedHashSet<Node>(); // iterates over items in order of addition to set
	Set<Node> visited = new LinkedHashSet<Node>(); // records those visited so as to not revisit
	
	public TopologicalOrder(Node root, RelationshipType relType) {
		this.relType = relType;
		addNodesRecursive(root);
	}
	
	private void addNodesRecursive(Node n) {
		for (Relationship r : n.getRelationships(relType, Direction.INCOMING)) {
			if(visited.contains(r.getStartNode())==false){
				visited.add(r.getStartNode());
				addNodesRecursive(r.getStartNode());
			}
		}
		if(visited.contains(n)==false){
			nodes.add(n); // first occurrence is used for ordering, subsequent ones have no effect
			visited.add(n);
		}
	}

	@Override
	public Iterator<Node> iterator() {
		return nodes.iterator();
	}
}
