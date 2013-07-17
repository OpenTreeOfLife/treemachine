package opentree.synthesis;

import java.util.ArrayList;

import opentree.Constants;
import opentree.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;

public class DraftTreePathExpander implements PathExpander {
	
	Direction direction;
	
	public DraftTreePathExpander(Direction direction) {
		this.direction = direction;
	}

	@Override
	public Iterable<Relationship> expand(Path arg0, BranchState arg1) {
		ArrayList<Relationship> rels = new ArrayList<Relationship>();
		for (Relationship rel : arg0.endNode().getRelationships(direction, RelTypes.SYNTHCHILDOF)) {
			if (rel.hasProperty("name")) {
				if (String.valueOf(rel.getProperty("name")).equals(Constants.DRAFT_TREE_NAME.value)) {
					rels.add(rel);
				}
			}
		}
		return rels;
	}

	@Override
	public PathExpander reverse() {
		throw new java.lang.UnsupportedOperationException("reverse method not supported for draft tree expander");
	}

}
