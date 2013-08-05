package opentree;

import java.util.Arrays;
import java.util.LinkedList;

import gnu.trove.list.array.TLongArrayList;

import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.kernel.Traversal;

/**
 * This uses the mrca and mrca out bits
 */
public class LicaBipartEvaluatorBS implements Evaluator {
	TLongArrayList visited = null;

//	BitSet ingroupMRCAIdsBS = null;
//	TLongArrayList ingroupMRCAIds = null; // this can be larger than smInIdSet and includes the mrca for the matched nodes in the tree (so the dbnodes of the children)
	TLongBitArray ingroupNodeIds; // replacing with BitArray
	
//	BitSet outgroupMRCAIdsBS = null;
//	TLongArrayList outgroupMRCAIds = null; // this is the other part of the bipartition
	TLongBitArray outgroupNodeIds;
	
	GraphDatabaseAgent graphdb = null;

	public LicaBipartEvaluatorBS() {
	}

	public void setOutset(TLongArrayList fids) {
		outgroupNodeIds = new TLongBitArray(fids);
/*		outgroupMRCAIds = fids;
		if (fids.size() > 0) {
			outgroupMRCAIdsBS = new BitSet((int) fids.max());// could set this to the smallest number
			for (int i = 0; i < fids.size(); i++) {
				outgroupMRCAIdsBS.set((int) fids.getQuick(i));
			}
		} else {
			outgroupMRCAIdsBS = new BitSet(0);
		} */
	}

	public void setInset(TLongArrayList fids) {
		ingroupNodeIds = new TLongBitArray(fids);
/*		ingroupMRCAIds = fids;
		ingroupMRCAIdsBS = new BitSet((int) fids.max());// could set this to the smallest number
		for (int i = 0; i < fids.size(); i++) {
			ingroupMRCAIdsBS.set((int) fids.getQuick(i));
		} */
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
	public Evaluation evaluate(Path inPath) {
		// System.out.println(arg0);
		Node curNode = inPath.endNode();

		if (visited.contains(curNode.getId())) {
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
		visited.add(curNode.getId());

/*		TLongArrayList ttm = new TLongArrayList((long[]) tn.getProperty("mrca"));
		BitSet tm = new BitSet((int) ttm.max());
		for (int i = 0; i < ttm.size(); i++) {
			tm.set((int) ttm.getQuick(i));
		} */
		TLongBitArray curNodeMRCAIds = new TLongBitArray((long[]) curNode.getProperty("mrca")); // replacing with container
		
		// System.out.println("inIdSet "+inIdSet.size());
		// System.out.println("outIdSet "+outIdSet.size());
		// System.out.println("mrca: "+tm.size());

		// NOTE: in order to cut down on size, taxnodes outmrca are assumed to be "the rest"
		// they are denoted with not having an outmrca
		boolean isTaxNode = false;
//		BitSet to = null;

//		TLongArrayList tto = null;
		TLongBitArray curNodeOutMRCAIds = null; // replacing with container
		
		if (curNode.hasProperty("outmrca") == false) {
			isTaxNode = true;
		} else {
			
/*			tto = new TLongArrayList((long[]) tn.getProperty("outmrca"));
			to = new BitSet((int) tto.max());// could set this to the smallest number
			for (int i = 0; i < tto.size(); i++) {
				to.set((int) tto.getQuick(i));
			}
			// System.out.println("mrca o: "+to.size()); */
			curNodeOutMRCAIds = new TLongBitArray((long[]) curNode.getProperty("outmrca")); // replacing with container
			
		}
		
		if (isTaxNode == false) {
			// System.out.println("passed early quit");
			// if match, extend the mrca and outmrca
			
			// ===
//			if (tm.intersects(outIdBS) == false) {// no overlap of outgroup and ingroup of dbnode

			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == false) {
//				if (to.intersects(inIdBS) == false) {// no overlap in ingroup and outgroup of dbnode
				if (curNodeOutMRCAIds.containsAny(ingroupNodeIds) == false) {
//					if (tm.intersects(inIdBS) == true) {// some overlap in inbipart -- //LARGEST one, do last
					if (curNodeMRCAIds.containsAny(ingroupNodeIds) == true) {
						
						System.out.println("LICA search found " + curNode);

						// =========================== everything between these lines (look down) should move to the GraphImporter class ==========================
						
//						boolean tmt = false;
//						boolean updateParents = false;
//						boolean updateChildren = false;
//						BitSet inIdBS2 = (BitSet) inIdBS.clone();

						// get the node ids that are in our search ingroup but not in the current node's mrca property
//						inIdBS2.andNot(tm);// any missing ones we want to add 
//						inIdBS2.andNot(ttm.getBitSet()); // any missing ones we want to add  // replacing with container // was getting the same thing as 
						TLongBitArray mrcaSearchIdsNotSetForThisNode = ingroupNodeIds.andNot(curNodeMRCAIds);
						
						// if we found any then update the node mrca property
//						for (int i = 0; i < inIdBS2.length(); i++) {
//							if (inIdBS2.get(i) == true) {
//								ttm.add(i);
//								tmt = true;
//							}
//						}
//						if (tmt) {
//							curNodeMRCAIds.sort();
//							curNode.setProperty("mrca", curNodeMRCAIds.toArray());
//							updateParents = true;
//						}
//						tmt = false;
						if (mrcaSearchIdsNotSetForThisNode.size() > 0) {
							curNodeMRCAIds.addAll(mrcaSearchIdsNotSetForThisNode); // new, replacing with container
							curNodeMRCAIds.sort();
							curNode.setProperty("mrca", curNodeMRCAIds.toArray());
//							updateParents = true;
						}
						
						// get the node ids that are in our search outgroup but not in the current node's outmrca property
//						BitSet outIdBS2 = (BitSet) outIdBS.clone();
//						outIdBS2.andNot(to);// any missing ones we want to add
//						outIdBS2.andNot(tto.getBitSet());// any missing ones we want to add // replacing with container
						TLongBitArray outmrcaSearchIdsNotSetForThisNode = outgroupNodeIds.andNot(curNodeOutMRCAIds);

						// if we found any then update the node outmrca property
//						for (int i = 0; i < outIdBS2.length(); i++) {
//							if (outIdBS2.get(i) == true) {
//								tto.add(i);
//								tmt = true;
//							}
//						}
//						if (tmt) {
//							curNodeOutMRCAIds.sort();
//							curNode.setProperty("outmrca", curNodeOutMRCAIds.toArray());
//							updateChildren = true;
//						}
						if (outmrcaSearchIdsNotSetForThisNode.size() > 0) {
							curNodeOutMRCAIds.addAll(outmrcaSearchIdsNotSetForThisNode); // new, replacing with container
							curNodeOutMRCAIds.sort();
							curNode.setProperty("outmrca", curNodeOutMRCAIds.toArray());
//							updateChildren = true;
						}
						
						// ok, now we will check to see if the STREECHILDOF ancestors of this node in the graph have all the node ids
						// in their mrca properties that this node does, and do the same for this node's graph descendants and their
						// outmrca properties. sometimes we need to update parents/children even when we're not updating the current
						// node (not sure why but in the updating test this is the case). so we *always* check.

//						System.out.println("checking if we need to update parents of " + tn);
						
//						if (updateParents) {
							// have to be tricky here, if we start traversing on the current node the traversal ends immediately, because the
							// current node has all the values we're looking for. so we get all its parents and traverse independently from each one
							for (Relationship parentRel : curNode.getRelationships(RelType.STREECHILDOF, Direction.OUTGOING)) {
								for (Node ancestor : Traversal.description().breadthFirst().evaluator(new LongArrayPropertyContainsAllEvaluator("mrca", curNodeMRCAIds)).
										relationships(RelType.STREECHILDOF, Direction.OUTGOING).traverse(parentRel.getEndNode()).nodes()) {
									System.out.println("updating ancestor " + ancestor + " with new ingroup lica mappings");
	
									// test, validate that the child ingroup doesn't contain things that are already in the outgroup of the parent
	/*								TLongArrayList outmrcaAncestor = new TLongArrayList((long[]) ancestor.getProperty("outmrca"));
									BitSet outmrcaParentBS = new BitSet((int) outmrcaAncestor.max());
									for (int i = 0; i < outmrcaAncestor.size(); i++) {
										outmrcaParentBS.set((int) outmrcaAncestor.getQuick(i));
									} */
									
									TLongBitArray outmrcaAncestor = new TLongBitArray((long[]) ancestor.getProperty("outmrca"));
	//								if (outmrcaParentBS.intersects(inIdBS2)) {
									if (outmrcaAncestor.containsAny(ingroupNodeIds)) {
										System.out.println("attempt to add a descendant with a taxon in its ingroup, which has an ancestor with that taxon in its outgroup");
	
										LinkedList <String> names = new LinkedList<String>();
										for (Long l : outmrcaAncestor.getIntersection(ingroupNodeIds)) {
											names.add((String) graphdb.getNodeById(l).getProperty("name"));
										}
										System.out.println(curNode + " would have been a descendant of " + ancestor + ", but " + curNode + " contains " + Arrays.toString(names.toArray()) + ", which are in the outgroup of " + ancestor);
										// not throwing exception during testing, otherwise this should be on
//										throw new java.lang.IllegalStateException();
										
	//									System.out.println("the ancestor (already in the graph) was: " + ancestor);
	//									System.out.println("the overlapping ids from the mrca of the descendant and the outmrca of the ancestor were: " + Arrays.toString(names.toArray()));
	

									}
									
									// add all the new ingroup ids to the parent
									TLongBitArray mrcaNew = new TLongBitArray((long[]) ancestor.getProperty("mrca"));
									mrcaNew.addAll(curNodeMRCAIds);
	//								System.out.println("setting mrca property to: " + Arrays.toString(mrcaNew.toArray()));
									ancestor.setProperty("mrca", mrcaNew.toArray());
									System.out.println("done");
								}
							}
//						}

//						System.out.println("checking if we need to update children of " + tn);

//						if (updateChildren) {
							// here we want descendants instead of ancestors so we go the other direction
							for (Relationship childRel : curNode.getRelationships(RelType.STREECHILDOF, Direction.INCOMING)) {
								for (Node descendant : Traversal.description().breadthFirst().evaluator(new LongArrayPropertyContainsAllEvaluator("outmrca", curNodeOutMRCAIds)).
										relationships(RelType.STREECHILDOF, Direction.INCOMING).traverse(childRel.getStartNode()).nodes()) {
		
									System.out.println("updating descendant " + descendant + " with new outgroup lica mappings");
									
									// test, validate that the descendant outgroup doesn't contain things that are in the ingroup of the parent
	/*								TLongArrayList mrcaDescendant = new TLongArrayList((long[]) descendant.getProperty("mrca"));
									BitSet mrcaDescendantBS = new BitSet((int) mrcaDescendant.max());
									for (int i = 0; i < mrcaDescendant.size(); i++) {
										mrcaDescendantBS.set((int) mrcaDescendant.getQuick(i));
									} */
	
									TLongBitArray mrcaDescendant = new TLongBitArray((long[]) descendant.getProperty("mrca"));
	//								if (mrcaDescendantBS.intersects(outIdBS2)) {
									if (mrcaDescendant.containsAny(outgroupNodeIds)) {
										
										System.out.println("attempt to add an ancestor with a taxon in its outgroup, which has a descendant with that taxon in its ingroup");
										
										LinkedList <String> names = new LinkedList<String>();
										for (Long l : mrcaDescendant.getIntersection(outgroupNodeIds)) {
											names.add((String) graphdb.getNodeById(l).getProperty("name"));
										}
										System.out.println(curNode + " would have been an ancestor of " + descendant + ", but " + curNode + " excludes " + Arrays.toString(names.toArray()) + ", which are in the ingroup of " + descendant);
										// not throwing exception during testing, otherwise this should be on
//										throw new java.lang.IllegalStateException();

										/*
										System.out.println("attempt to add an ancestor with a taxon in its ingroup, which has a descendant with that taxon in its outgroup"); // something may be wrong with this sentence....
										System.out.println("the ancestor (the node we were adding) was: " + curNode);
										System.out.println("the descendant (already in the graph) was: " + descendant);
										System.out.println("the overlapping ids from the outmrca of the ancestor and the mrca of the descendant were: " + Arrays.toString(mrcaDescendant.getIntersection(outmrcaSearchIdsNotSetForThisNode).toArray()));  */
									}
									
									// add all the new outgroup ids to the descendant
									TLongBitArray outMrcaNew = new TLongBitArray((long[]) descendant.getProperty("outmrca"));
									outMrcaNew.addAll(curNodeOutMRCAIds);
									descendant.setProperty("outmrca", outMrcaNew.toArray());
									System.out.println("updating descendant " + descendant + ": added " + Arrays.toString(curNodeOutMRCAIds.toArray()) + " to outmrca");
	
								}
							}
//						}
							
						// =========================== everything between these lines (look up) should move to the GraphImporter class ==========================
							
						return Evaluation.INCLUDE_AND_PRUNE;
					}
				}
			}

		} else { // this is a taxonomy node, so the tests are simpler
//			if (outIdBS.intersects(tm) == false) {// containsany
			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == false) { // if the node does not contain any of the outgroup
	//			tm.and(inIdBS);
	//			if (inIdBS.cardinality() == tm.cardinality()) { // containsall
				if (curNodeMRCAIds.containsAll(ingroupNodeIds)) { // and it does contain all of the ingroup
					return Evaluation.INCLUDE_AND_PRUNE;
				}
			}
		}
		return Evaluation.EXCLUDE_AND_CONTINUE;
	}
}
