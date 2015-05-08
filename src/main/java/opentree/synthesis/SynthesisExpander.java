package opentree.synthesis;

import java.util.HashSet;
import java.util.LinkedList;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import opentree.constants.RelType;
import opentree.synthesis.conflictresolution.RelationshipConflictResolver;
import opentree.synthesis.filtering.RelationshipFilter;
import opentree.synthesis.ranking.RelationshipRanker;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;
import org.neo4j.kernel.Traversal;
import org.opentree.bitarray.MutableCompactLongSet;

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
public class SynthesisExpander implements PathExpander {

	private RelationshipFilter filter;
	private RelationshipRanker ranker;
	private RelationshipConflictResolver resolver;
	private Iterable<Relationship> candidateRels;
	private Iterable<Relationship> candidateTaxRels;
	private Iterable<Relationship> bestRels;
	private TLongHashSet dupMRCAS;
	private TLongHashSet deadnodes;
	
	public SynthesisExpander() {
		this.filter = null;
		this.ranker = null;
		this.resolver = null;
		dupMRCAS = new TLongHashSet();
		deadnodes = new TLongHashSet();
	}
	
	public SynthesisExpander setFilter(RelationshipFilter filter) {
		this.filter = filter;
		return this;
	}

	public SynthesisExpander setRanker(RelationshipRanker ranker) {
		this.ranker = ranker;
		return this;
	}
	
	public void setDupMRCAS(TLongHashSet mrcas){
		dupMRCAS = mrcas;
	}
	
	public TLongHashSet getDupMRCAS(){
		return dupMRCAS;
	}

	/** whether or not to print verbose error messages. could be extended to allow various verbosity levels. */
	boolean VERBOSE = false;
	public enum Verbosity { SILENT, EXTREME }
	
	public SynthesisExpander setVerbosity(Verbosity v) {
		if (v != Verbosity.SILENT) { VERBOSE = true; }
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
	public SynthesisExpander setConflictResolver(RelationshipConflictResolver resolver) {
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
		System.out.println("working with node: "+inNode);
		
		Iterable<Relationship> allRels = inNode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF);
		candidateTaxRels = inNode.getRelationships(Direction.INCOMING, RelType.TAXCHILDOF);
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
		System.out.println("candidates: "+candidateRels);
		if (resolver != null) {
			bestRels = resolver.resolveConflicts(candidateRels,true);
			dupMRCAS.addAll(resolver.getDupMRCAS());
			System.out.println("dups from method: "+dupMRCAS.size());
			//System.out.println(dupMRCAS);
		} else {
			bestRels = candidateRels;
		}
		//worst way to do taxonomy
		MutableCompactLongSet ms = new MutableCompactLongSet();
		for(Relationship rel: bestRels){
			MutableCompactLongSet ts = new MutableCompactLongSet((long[])rel.getStartNode().getProperty("mrca"));
			ms.addAll(ts);
		}
		HashSet<Relationship> toadd = new HashSet<Relationship>();
		for(Relationship rel: candidateTaxRels){
			MutableCompactLongSet ts = new MutableCompactLongSet((long[])rel.getStartNode().getProperty("mrca"));
			if(ts.containsAny(ms)==false){
				toadd.add(rel);
			}
		}if(toadd.size()>0){
			for(Relationship b: bestRels){
				toadd.add(b);
			}
			bestRels = new LinkedList<Relationship>(toadd);
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
	
	public TLongHashSet getDeadNodes(){
		return deadnodes;
	}

	/**
	 * Return a textual report of the status of the synthesis procedures. This could be called during or after synthesis to describe general results.
	 * @return
	 */
	public String getReport() {
		String report = "";
		
		if (filter != null) {
			report = report.concat(filter.getReport() + "\n");
		} else {
			report = report.concat("No filtering to report: this synthesis method does not perform relationship filtering\n");
		}
		
		if (ranker != null) {
			report = report.concat(ranker.getReport() + "\n");
		} else {
			report = report.concat("No ranking to report: this synthesis method does not perform relationship ranking.\n");
		}
		
		if (resolver != null) {
			report = report.concat(resolver.getReport() + "\n");
		} else {
			report = report.concat("No conflict resolution to report: this synthesis method does not perform conflict resolution.\n");
		}
		
		return report;
	}
	
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
		// TESTING
//		System.out.println("synthesis path expander preparing to work with path starting at (end) node: " + inPath.endNode().getId());
		
		// perform the steps to determine which relationships to include in synthesis.
		// logic for these steps
		filter(inPath.endNode());
		rank();
		resolveConflicts();
		//we need to take out the relationships that just go to the dupmrcas
		HashSet<Relationship> rels = new HashSet<Relationship>();
		boolean alldead = true;
		for (Relationship rel:bestRels){
			TLongArrayList tem = new TLongArrayList((long[]) rel.getStartNode().getProperty("mrca"));
			//this will test only the mrcas that are included in the relationship from the source tree
			//this will not always be the full set from the mrca of the node -- unless it is taxonomy relationship
			//need to verify that the exclusive mrca is correct in this conflict
			//it should be the mapped tip mrcas subtending this node
			if (((String)rel.getProperty("source")).equals("taxonomy") == false && rel.isType(RelType.TAXCHILDOF)==false && 
					rel.hasProperty("compat") == false) {
				TLongArrayList exclusiveIds = new TLongArrayList((long[]) rel.getProperty("exclusive_mrca"));
				tem.retainAll(exclusiveIds);
			}
			tem.removeAll(dupMRCAS);
			if (tem.size() > 0) {
				alldead = false;
				rels.add(rel);
			} else {
				System.out.println("not adding relationship "+rel+" because of overlap taxa");
			}
		}
		if(rels.size() == 0 && alldead == true){
			//TODO: make this cleaner -- shouldn't even go down these roads
			//get parent
			Node curnode = inPath.endNode();
			System.out.println("cleaning up dead ends "+curnode);
			deadnodes.add(curnode.getId());
		}
		return rels;
	}

	@Override
	public PathExpander reverse() {
		throw new java.lang.UnsupportedOperationException("reverse method not supported for synthesis expander");
	}
}
