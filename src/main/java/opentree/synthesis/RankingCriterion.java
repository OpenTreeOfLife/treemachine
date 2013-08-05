package opentree.synthesis;

import java.util.Comparator;
import java.util.List;

import org.neo4j.graphdb.Relationship;

public interface RankingCriterion extends Comparator<Relationship> {

	abstract void sort(List<Relationship> rels);
	abstract String getDescription();
	abstract String getReport();
	
}
