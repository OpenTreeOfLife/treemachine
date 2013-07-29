package opentree.synthesis;

import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.kernel.Traversal;

/**
 * A PathExpander class that performs all the steps to make decisions about which relationships to
 * include in synthesis. These decisions involve three steps: filtering relationships using
 * RelationshipFilter, ranking the remaining relationships using RelationshipRanker, and finally
 * resolving conflicts among the ranked, filtered relationships using various ResolutionMethods (which
 * need some work).
 * 
 * All the logic for these decisions is farmed out to the individual classes that do the filtering,
 * ranking, and conflict resolution. This keeps the high level logic clean and should also help
 * isolate problems.
 * 
 * @author cody hinchliff and stephen smith
 *
 */
public class ResolvingExpander implements PathExpander {

	private RelationshipFilter filter;
	private RelationshipRanker ranker;
	private RelationshipConflictResolver resolver;
	private Iterable<Relationship> candidateRels;
	private Iterable<Relationship> bestRels;
	
	public ResolvingExpander() {
		this.filter = null;
		this.ranker = null;
		this.resolver = null;
	}
	
	public ResolvingExpander setFilter(RelationshipFilter filter) {
		this.filter = filter;
		return this;
	}

	public ResolvingExpander setRanker(RelationshipRanker ranker) {
		this.ranker = ranker;
		return this;
	}

	/**
	 * Set the conflict resolution method. The resolvers need some thought/work. Need to think about
	 * how we would add multiple resolvers. E.g if there is no source preference, can there be branch and
	 * bound? Can there be size preference? Methods allowing the creation of cyclic synthetis graphs could
	 * also be implemented.
	 * 
	 * @param resolver
	 * @return RelationshipEvaluator
	 */
	public ResolvingExpander setConflictResolver(RelationshipConflictResolver resolver) {
		this.resolver = resolver;
		return this;
	}

	/**
	 * Filter the incoming STREECHILDOF relationships from the end node of the path (i.e. first node in the path),
	 * and record the ones that pass in candidateRels so we can perform ranking and resolving on them.
	 * 
	 * @param inNode
	 */
	private void filter(Node inNode) {
		
		Iterable<Relationship> allRels = inNode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF);
		
		if (filter != null) {
			candidateRels = filter.filterRelationships(allRels);
		} else {
			candidateRels = allRels;
		}
	}

	/**
	 * Rank the relationships saved in the candidateRels using the applied ranking methods.
	 */
	private void rank() {
		if (ranker != null) {
			candidateRels = ranker.rankRelationships(candidateRels);
		}
	}
	
	/**
	 * Resolve conflicts among the relationships stored in candidateRels. For more info, see description
	 * above for setConflictResolver, and also the ResolutionMethod classes.
	 * 
	 * TODO: include the branch and bound option (new resolver?), size option (new resolver?), others?
	 */
	private void resolveConflicts() {
		if (resolver != null) {
			bestRels = resolver.resolveConflicts(candidateRels);
		} else {
			bestRels = candidateRels;
		}
		
		// testing
//		System.out.println("now passing an array containing the following rels to GraphExplorer.");
//		for (Relationship rel : bestRels) {
    		// testing
//    		System.out.println("\t" + rel.getId());
    		
			// print each rel, check this against accepted rels output from the resolution method to
			// be sure that the rels that are getting accepted are also getting passed. They don't
			// seem to be getting picked up in GraphExplorer...
//		}
		
	}

	/**
	 * Return a textual description of the procedures that will be performed by this synthesis method.
	 * @return description
	 */
	public String getDescription() {
		String desc = "";

		if (filter != null) {
			desc = desc.concat(filter.getDescription() + "\n");
		} else {
			desc = desc.concat("No filtering will be applied\n");
		}

		if (ranker != null) {
			desc = desc.concat(ranker.getDescription() + "\n");
		} else {
			desc = desc.concat("No ranking will be applied (rank will be the order returned by the graph db)\n");
		}

		if (resolver != null) {
			desc = desc.concat(resolver.getDescription() + "\n");
		} else {
			desc = desc.concat("No conflict resolution will be applied (all rels passing filters will be returned)\n");
		}
		
		return desc;
	}
	
	/*
	 * DEAD CODE, replaced by the expand() method when refactored into a PathExpander class
	 * 
	 * Filters and ranks the candidate relationships according to the relationship filter and ranker assigned to this
	 * evaluator, and then picks the best set that do not conflict according to the assigned conflict resolver.
	 * 
	 * @param node
	 * @return best relationship
	 *
	public Iterable<Relationship> evaluateBestPaths(Node node) {
		
		//TODO: why aren't these filtered at this stage
		//TODO: we should rank at this stage as well
		LinkedList<Relationship> allRels = new LinkedList<Relationship>();
		for (Relationship rel : node.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
			if (filter != null){
				boolean t = filter.filterRelationship(rel);
				if (t == true)
					allRels.add(rel);
			}else
				allRels.add(rel);
		}

		candidateRels = allRels;
				
		//filter();
		//TODO: this is likely to be very slow unless we only rank the relationships that we care about
		//		in other words, we shouldn't rank all of them, just the ones we care about
		rank();
		resolveConflicts();
		
		return bestRels;
	} */
	

	/**
	 * The essential PathExpander method that performs all the steps to make decisions about
	 * which relationships to traverse, which in this case means deciding which rels to include in
	 * synthesis. All the logic for these decisions is farmed out to the individual classes that do the
	 * filtering, ranking, and conflict resolution. This keeps the high level logic clean and should
	 * also help isolate problems.
	 * 
	 * @params inPath
	 */
	@Override
	public Iterable<Relationship> expand(Path inPath, BranchState arg1) {
		
//		LinkedList<Relationship> allRels = new LinkedList<Relationship>();

		/*		// first filter the set of incoming rels (if we have a filter)
		for (Relationship rel : inPath.endNode().getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
			if (filter != null) {
				boolean t = filter.filterRelationship(rel);
				if (t == true)
//					allRels.add(rel);
					candidateRels.add(rel);
			} else {
				candidateRels.add(rel);
			}
		} */
		
//		candidateRels = allRels;
				
		//filter();
		//TODO: this is likely to be very slow unless we only rank the relationships that we care about
		//		in other words, we shouldn't rank all of them, just the ones we care about
		
		// TESTING
		System.out.println("synthesis path expander preparing to work with path starting at (end) node: " + inPath.endNode().getId());
		
		// perform the steps to determine which relationships to include in synthesis.
		// logic for these steps
		filter(inPath.endNode());
		rank();
		resolveConflicts();
		
		return bestRels;
	}

	@Override
	public PathExpander reverse() {
		throw new java.lang.UnsupportedOperationException("reverse method not supported for synthesis expander");
	}
}
