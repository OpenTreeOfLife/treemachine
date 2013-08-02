package opentree;

import java.util.BitSet;
import java.util.LinkedList;

import gnu.trove.list.array.TLongArrayList;

import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.kernel.Traversal;

/**
 * This uses the mrca and mrca out bits
 */
public class LicaBipartEvaluatorBS implements Evaluator {
	TLongArrayList visited = null;
	BitSet inIdBS = null;
	TLongArrayList inIdSet = null; // this can be larger than smInIdSet and includes the mrca for the matched nodes in the tree (so the dbnodes of the children)
	BitSet outIdBS = null;
	TLongArrayList outIdSet = null; // this is the other part of the bipartition
	GraphDatabaseAgent graphdb = null;

	public LicaBipartEvaluatorBS() {
	}

	public void setOutset(TLongArrayList fids) {
		outIdSet = fids;
		if (fids.size() > 0) {
			outIdBS = new BitSet((int) fids.max());// could set this to the smallest number
			for (int i = 0; i < fids.size(); i++) {
				outIdBS.set((int) fids.getQuick(i));
			}
		} else {
			outIdBS = new BitSet(0);
		}
	}

	public void setInset(TLongArrayList fids) {
		inIdSet = fids;
		inIdBS = new BitSet((int) fids.max());// could set this to the smallest number
		for (int i = 0; i < fids.size(); i++) {
			inIdBS.set((int) fids.getQuick(i));
		}
	}

	public void setVisitedSet(TLongArrayList fids) {
		visited = fids;
	}

	public void setgraphdb(GraphDatabaseAgent gb) {
		graphdb = gb;
	}

	public TLongArrayList getVisitedSet() {
		return visited;
	}

	@Override
	public Evaluation evaluate(Path arg0) {
		// System.out.println(arg0);
		Node tn = arg0.endNode();
		if (visited.contains(tn.getId())) {
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
		visited.add(tn.getId());
		TLongArrayList ttm = new TLongArrayList((long[]) tn.getProperty("mrca"));
		BitSet tm = new BitSet((int) ttm.max());
		for (int i = 0; i < ttm.size(); i++) {
			tm.set((int) ttm.getQuick(i));
		}
		// System.out.println("inIdSet "+inIdSet.size());
		// System.out.println("outIdSet "+outIdSet.size());
		// System.out.println("mrca: "+tm.size());

		// NOTE: in order to cut down on size, taxnodes outmrca are assumed to be "the rest"
		// they are denoted with not having an outmrca
		boolean taxnode = false;
		BitSet to = null;
		TLongArrayList tto = null;
		if (tn.hasProperty("outmrca") == false) {
			taxnode = true;
		} else {
			tto = new TLongArrayList((long[]) tn.getProperty("outmrca"));
			to = new BitSet((int) tto.max());// could set this to the smallest number
			for (int i = 0; i < tto.size(); i++) {
				to.set((int) tto.getQuick(i));
			}
			// System.out.println("mrca o: "+to.size());
		}
		if (taxnode == false) {
			// System.out.println("passed early quit");
			// if match, extend the mrca and outmrca

			if (tm.intersects(outIdBS) == false) {// no overlap of outgroup and ingroup of dbnode
				if (to.intersects(inIdBS) == false) {// no overlap in ingroup and outgroup of dbnode
					if (tm.intersects(inIdBS) == true) {// some overlap in inbipart -- //LARGEST one, do last
						boolean tmt = false;
//						boolean updateParents = false;
//						boolean updateChildren = false;
						BitSet inIdBS2 = (BitSet) inIdBS.clone();
						inIdBS2.andNot(tm);// any missing ones we want to add
						for (int i = 0; i < inIdBS2.length(); i++) {
							if (inIdBS2.get(i) == true) {
								ttm.add(i);
								tmt = true;
							}
						}
						if (tmt) {
							ttm.sort();
							tn.setProperty("mrca", ttm.toArray());
//							updateParents = true;
						}
						tmt = false;
						BitSet outIdBS2 = (BitSet) outIdBS.clone();
						outIdBS2.andNot(to);// any missing ones we want to add
						for (int i = 0; i < outIdBS2.length(); i++) {
							if (outIdBS2.get(i) == true) {
								tto.add(i);
								tmt = true;
							}
						}
						if (tmt) {
							tto.sort();
							tn.setProperty("outmrca", tto.toArray());
//							updateChildren = true;
						}
						
						// ok, now we will check to see if the STREECHILDOF ancestors of this node in the graph have all the node ids
						// in their mrca properties that this node does, and do the same for this node's graph descendants and their
						// outmrca properties. sometimes we need to update parents/children even when we're not updating the current
						// node (not sure why but in the updating test this is the case). so we *always* check.

//						System.out.println("checking if we need to update parents of " + tn);
						
						// have to be tricky here, if we start traversing on the current node the traversal ends immediately, because the
						// current node has all the values we're looking for. so we get all its parents and traverse independently from each one
						for (Relationship parentRel : tn.getRelationships(RelType.STREECHILDOF, Direction.OUTGOING)) {
							for (Node ancestor : Traversal.description().breadthFirst().evaluator(new LongArrayPropertyContainsAllEvaluator("mrca", ttm)).
									relationships(RelType.STREECHILDOF, Direction.OUTGOING).traverse(parentRel.getEndNode()).nodes()) {
								System.out.println("updating parent node " + ancestor + " with new ingroup lica mappings");

								// test, validate that the child ingroup doesn't contain things that are already in the outgroup
								// of the parent as this indicates failure on the part of the lica-identification code
								TLongArrayList outmrcaParent = new TLongArrayList((long[]) ancestor.getProperty("outmrca"));
								BitSet outmrcaParentBS = new BitSet((int) outmrcaParent.max());
								for (int i = 0; i < outmrcaParent.size(); i++) {
									outmrcaParentBS.set((int) outmrcaParent.getQuick(i));
								}
								if (outmrcaParentBS.intersects(inIdBS2)) {
									throw new java.lang.IllegalStateException("attempt to add a child containing a descendant to a parent with that descendant in its outgroup");
									// TODO: put in output about the nodes involved in the failed mapping
								}
								
								// add all the new ingroup ids to the parent
								TLongArrayList mrcaNew = new TLongArrayList((long[]) ancestor.getProperty("mrca"));
								mrcaNew.addAll(ttm);
								ancestor.setProperty("mrca", mrcaNew.toArray());
							}
						}

//						System.out.println("checking if we need to update children of " + tn);

						// here we want descendants instead of ancestors so we go the other direction
						for (Relationship childRel : tn.getRelationships(RelType.STREECHILDOF, Direction.INCOMING)) {
							for (Node descendant : Traversal.description().breadthFirst().evaluator(new LongArrayPropertyContainsAllEvaluator("outmrca", tto)).
									relationships(RelType.STREECHILDOF, Direction.INCOMING).traverse(childRel.getStartNode()).nodes()) {
								System.out.println("updating child node " + descendant + " with new outgroup lica mappings");
	
								// test, validate that the child outgroup doesn't contain things that are in the ingroup
								// of the parent as this indicates failure on the part of the lica-identification code
								TLongArrayList mrcaParent = new TLongArrayList((long[]) descendant.getProperty("mrca"));
								BitSet mrcaParentBS = new BitSet((int) mrcaParent.max());
								for (int i = 0; i < mrcaParent.size(); i++) {
									mrcaParentBS.set((int) mrcaParent.getQuick(i));
								}
								if (mrcaParentBS.intersects(outIdBS2)) {
									throw new java.lang.IllegalStateException("attempt to add a child containing a taxon in its outgroup, which is descended from a parent with that taxon in its ingroup");
									// TODO: put in output about the nodes involved in the failed mapping
								}
								
								// add all the new ingroup ids to the parent
								TLongArrayList outMrcaNew = new TLongArrayList((long[]) descendant.getProperty("outmrca"));
								outMrcaNew.addAll(tto);
								descendant.setProperty("outmrca", outMrcaNew.toArray());
							}
						}
							
						return Evaluation.INCLUDE_AND_PRUNE;
					}
				}
			}
		} else {
			if (outIdBS.intersects(tm) == false) {// containsany
				tm.and(inIdBS);
				if (inIdBS.cardinality() == tm.cardinality()) {// containsall
					return Evaluation.INCLUDE_AND_PRUNE;
				}
			}
		}
		return Evaluation.EXCLUDE_AND_CONTINUE;
	}
}
