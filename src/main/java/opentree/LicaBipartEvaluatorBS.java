package opentree;

import jade.tree.JadeNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;

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

	TLongBitArray ingroupNodeIds; // this can be larger than smInIdSet and includes the mrca for the matched nodes in the tree (so the dbnodes of the children)
	TLongBitArray outgroupNodeIds; // this is the other part of the bipartition
	JadeNode jadenode;
	GraphDatabaseAgent graphdb = null;
	//by default this is set to true because that is the basic usage
	boolean updateDB = true; // you can set to false if you don't want to update the database on this

	public LicaBipartEvaluatorBS() {
	}

	public void setOutset(TLongArrayList fids) {
		outgroupNodeIds = new TLongBitArray(fids);
	}
	
	public void setInset(TLongArrayList fids) {
		ingroupNodeIds = new TLongBitArray(fids);
	}

	public void setVisitedSet(TLongArrayList fids) {
		visited = fids;
	}

	public void setgraphdb(GraphDatabaseAgent gb) {
		graphdb = gb;
	}

	public void setJadeNode(JadeNode jn){
		jadenode = jn;
	}
	
	public TLongArrayList getVisitedSet() {
		return visited;
	}

	public void setUpdateDB(boolean update){
		updateDB = update;
	}
	
	@Override
	public Evaluation evaluate(Path inPath) {
		Node curNode = inPath.endNode();

		if (visited.contains(curNode.getId())) {
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
		visited.add(curNode.getId());

		TLongBitArray curNodeMRCAIds = new TLongBitArray((long[]) curNode.getProperty("mrca"));
		TLongBitArray curNodeOutMRCAIds = null; // never set for taxon nodes
		
		// System.out.println("inIdSet "+ingroupNodeIds.size());
		// System.out.println("outIdSet "+outgroupNodeIds.size());
		// System.out.println("mrca: "+curNodeMRCAIds.size());

		// NOTE: in order to cut down on size, taxnodes outmrca are assumed to be "the rest"
		// they are denoted with not having an outmrca
		boolean isTaxNode = false;
		
		if (curNode.hasProperty("outmrca") == false) {
			isTaxNode = true;
		} else {
			curNodeOutMRCAIds = new TLongBitArray((long[]) curNode.getProperty("outmrca")); // replacing with container
			
		}
		
		if (isTaxNode == false) {
			// System.out.println("passed early quit");
			// if match, extend the mrca and outmrca

			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == false) {
				if (curNodeOutMRCAIds.containsAny(ingroupNodeIds) == false) {
					if (curNodeMRCAIds.containsAny(ingroupNodeIds) == true) {
						
//						System.out.println("    Potential LICA found " + curNode);

						boolean passed = false;
						TLongHashSet visitedrels = new TLongHashSet();
						//checking the relationships to make sure that this is a good mapping
						//this will check each set of relationships from a mapping in the database to each of the children in 
						//     the source tree. there has to be a match in at least 2 of the database relationships and all of the 
						//     source tree relationships must have mappings
						for(Relationship rel: curNode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)){
//							System.out.println(rel);
							if(visitedrels.contains(rel.getId()) || rel.hasProperty("compat")){
								continue;
							}
							TLongArrayList inids = new TLongArrayList((long []) rel.getProperty("inclusive_relids"));
							visited.addAll(inids);
							TLongHashSet relmatched = new TLongHashSet();
							HashSet<Integer> childmatched = new HashSet<Integer>();
							for(int j=0;j<inids.size();j++){
								TLongBitArray trelj = new TLongBitArray((long[])graphdb.getRelationshipById(inids.get(j)).getProperty("exclusive_mrca"));
//								System.out.print("\t\t\ttesting rel "+inids.get(j)+" ex_mrca: ");
//								for(Long tl: trelj){
//									System.out.print(" "+tl);
//								}
//								System.out.print("\n");
								for(int i=0;i<jadenode.getChildCount();i++){
									TLongBitArray chndi = new TLongBitArray((long[])jadenode.getChild(i).getObject("exclusive_mrca"));
//									System.out.print("\t\t\t\ttesting jn child:"+i+" ex_mrca: ");
//									for(Long tl: chndi){
//										System.out.print(" "+tl);
//									}
//									System.out.print("\n");
//									System.out.println("\t\t\t\t"+jadenode.getChild(i).getNewick(false));
									if(chndi.containsAny(trelj) == true){
										relmatched.add(inids.get(j));
										childmatched.add(i);
										break;
									}
								}
							}
							//have to match at least 2 relationships from the set in the database sources
							//have to match all of the lineages from the sources tree
							if(relmatched.size() >=2 && childmatched.size() == jadenode.getChildCount()){
								passed = true;
								break;
							}
						}
						if (passed == false){
							return Evaluation.EXCLUDE_AND_CONTINUE;
						}

						// =========================== propose to move everything between these lines (look down) to the GraphImporter class ==========================
						
						// get the node ids that are in our search ingroup but not in the current node's mrca property
						TLongBitArray mrcaSearchIdsNotSetForThisNode = ingroupNodeIds.andNot(curNodeMRCAIds);
						
						// if we found any then update the node mrca property
						if (mrcaSearchIdsNotSetForThisNode.size() > 0) {
							curNodeMRCAIds.addAll(mrcaSearchIdsNotSetForThisNode);
							curNodeMRCAIds.sort();
							if(updateDB){
								curNode.setProperty("mrca", curNodeMRCAIds.toArray());
							}
						}
						
						// get the node ids that are in our search outgroup but not in the current node's outmrca property
						TLongBitArray outmrcaSearchIdsNotSetForThisNode = outgroupNodeIds.andNot(curNodeOutMRCAIds);

						// if we found any then update the node outmrca property
						if (outmrcaSearchIdsNotSetForThisNode.size() > 0) {
							curNodeOutMRCAIds.addAll(outmrcaSearchIdsNotSetForThisNode);
							curNodeOutMRCAIds.sort();
							if(updateDB){
								curNode.setProperty("outmrca", curNodeOutMRCAIds.toArray());
							}
						}
						
						// check to see if the STREECHILDOF ancestors of this node in the graph have all the node ids in their mrca properties that this node
						// does, and do the same for this node's graph descendants and their outmrca properties. sometimes we need to update parents/children
						// even when we're not updating the current node (not sure why but in the updating tests this seems to be the case). so we always check.
						if(updateDB){
							// start the updating traversals independently from each parent of the current node, otherwise we prune the start node (and never go
							// anywhere), because this node has the exact set of lica ids and always passes the tests
							for (Relationship parentRel : curNode.getRelationships(RelType.STREECHILDOF, Direction.OUTGOING)) {
								for (Node ancestor : Traversal.description().breadthFirst().evaluator(new LongArrayPropertyContainsAllEvaluator("mrca", curNodeMRCAIds)).
										relationships(RelType.STREECHILDOF, Direction.OUTGOING).traverse(parentRel.getEndNode()).nodes()) {
									System.out.println("updating ancestor " + ancestor + " with new ingroup lica mappings");
	
									// sanity check, if this fails then mapping is not working right
									TLongBitArray outmrcaAncestor = new TLongBitArray((long[]) ancestor.getProperty("outmrca"));
									if (outmrcaAncestor.containsAny(ingroupNodeIds)) {
										System.out.println("attempt to add a descendant with a taxon in its ingroup, which has an ancestor with that taxon in its outgroup");
	
										LinkedList <String> names = new LinkedList<String>();
										for (Long l : outmrcaAncestor.getIntersection(ingroupNodeIds)) {
											names.add((String) graphdb.getNodeById(l).getProperty("name"));
										}
										System.out.println(curNode + " would have been a descendant of " + ancestor + ", but " + curNode + " contains " + Arrays.toString(names.toArray()) + ", which are in the outgroup of " + ancestor);
										throw new java.lang.IllegalStateException();
									}
										
									// add all the new ingroup ids to the parent
									TLongBitArray mrcaNew = new TLongBitArray((long[]) ancestor.getProperty("mrca"));
									mrcaNew.addAll(curNodeMRCAIds);
									if(updateDB){
										ancestor.setProperty("mrca", mrcaNew.toArray());
									}
								}
							}
	
							// start the updating traversals independently from each child of the current node for the same reason as above
							for (Relationship childRel : curNode.getRelationships(RelType.STREECHILDOF, Direction.INCOMING)) {
								for (Node descendant : Traversal.description().breadthFirst().evaluator(new LongArrayPropertyContainsAllEvaluator("outmrca", curNodeOutMRCAIds)).
										relationships(RelType.STREECHILDOF, Direction.INCOMING).traverse(childRel.getStartNode()).nodes()) {
									System.out.println("updating descendant " + descendant + " with new outgroup lica mappings");
	
									// sanity check, if this fails then mapping is not working right
									TLongBitArray mrcaDescendant = new TLongBitArray((long[]) descendant.getProperty("mrca"));
									if (mrcaDescendant.containsAny(outgroupNodeIds)) {
										System.out.println("attempt to add an ancestor with a taxon in its outgroup, which has a descendant with that taxon in its ingroup");
										
										LinkedList <String> names = new LinkedList<String>();
										for (Long l : mrcaDescendant.getIntersection(outgroupNodeIds)) {
											names.add((String) graphdb.getNodeById(l).getProperty("name"));
										}
										System.out.println(curNode + " would have been an ancestor of " + descendant + ", but " + curNode + " excludes " + Arrays.toString(names.toArray()) + ", which are in the ingroup of " + descendant);
										throw new java.lang.IllegalStateException();
									}
									
									// add all the new outgroup ids to the descendant
									TLongBitArray outMrcaNew = new TLongBitArray((long[]) descendant.getProperty("outmrca"));
									outMrcaNew.addAll(curNodeOutMRCAIds);
									if(updateDB){
										descendant.setProperty("outmrca", outMrcaNew.toArray());
									}
								}
							}
						}
							
						// =========================== propose to move everything between these lines (look up) the GraphImporter class ==========================
							
						return Evaluation.INCLUDE_AND_PRUNE;
					}
				}
			}
		} else { // this is a taxonomy node, so the tests are simpler
			if (curNodeMRCAIds.containsAny(outgroupNodeIds) == false) { // if the node does not contain any of the outgroup
				if (curNodeMRCAIds.containsAll(ingroupNodeIds)) { // and it does contain all of the ingroup
					return Evaluation.INCLUDE_AND_PRUNE;
				}
			}
		}
		return Evaluation.EXCLUDE_AND_CONTINUE;
	}
}
