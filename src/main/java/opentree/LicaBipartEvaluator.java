package opentree;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * This uses the mrca and mrca out bits
 */
public class LicaBipartEvaluator implements Evaluator{
	TLongArrayList visited = null;
	TLongArrayList smInIdSet = null; //this will be smaller or equal to inIdSet and only includes the mrca for the nodes in the tree
	TLongArrayList inIdSet = null; //this can be larger than smInIdSet and includes the mrca for the matched nodes in the tree (so the dbnodes of the children)
	TLongArrayList outIdSet = null; //this is the other part of the bipartition
	GraphDatabaseAgent graphdb = null;
	public LicaBipartEvaluator(){}
	public void setOutset(TLongArrayList fids){
		outIdSet = fids;
	}
	public void setSmInSet(TLongArrayList fids){
		smInIdSet = fids;
	}
	public void setInset(TLongArrayList fids){
		inIdSet = fids;
	}
	public void setVisitedset(TLongArrayList fids){
		visited = fids;
	}
	public void setgraphdb(GraphDatabaseAgent gb){
		graphdb = gb;
	}
	public TLongArrayList getVisitedset(){
		return visited;
	}
	
	public Evaluation evaluate(Path arg0) {
		//System.out.println(arg0);
		Node tn = arg0.endNode();
		if(visited.contains(tn.getId())){
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
		visited.add(tn.getId());
		
		TLongArrayList tm = new TLongArrayList((long[])tn.getProperty("mrca"));
//		if (smInIdSet != null)
//			System.out.println("nodeSetinIdSet "+smInIdSet.size());
//		System.out.println("inIdSet "+inIdSet.size());
//		System.out.println("outIdSet "+outIdSet.size());
//		System.out.println("mrca: "+tm.size());

		//NOTE: in order to cut down on size, taxnodes outmrca are assumed to be "the rest"
		//		they are denoted with not having an outmrca
		boolean taxnode =false;
		TLongArrayList to = null;
		if (tn.hasProperty("outmrca") == false){
			taxnode = true;
		}else{
			to = new TLongArrayList((long[])tn.getProperty("outmrca"));
//			System.out.println("mrca o: "+to.size());
		}
		if(taxnode == false){
			long start = System.currentTimeMillis();
			boolean fail = false;
			//try the small one first if it exists
			if(smInIdSet!= null){
				if(containsAnyt4jSorted(smInIdSet,tm)){//some overlap in inbipart
					if(containsAnyt4jSorted(smInIdSet,to) == true){//overlap in ingroup and outgroup of dbnode	
						fail = true;
					}
				}else{
					fail = true;
				}
			}
			long elapsedTimeMillis2 = System.currentTimeMillis()-start;
			float elapsedTimeSec = elapsedTimeMillis2/1000F;
//			System.out.println("\telapsed 1: "+elapsedTimeSec);
			if(fail == true){
//				System.out.println("early quit");
				return Evaluation.EXCLUDE_AND_CONTINUE;
			}
			long start2 = System.currentTimeMillis();
//			System.out.println("passed early quit");
			//if match, extend the mrca and outmrca
			if(containsAnyt4jSorted(outIdSet,tm) == false){//no overlap of outgroup and ingroup of dbnode
				elapsedTimeMillis2 = System.currentTimeMillis()-start2;
				elapsedTimeSec = elapsedTimeMillis2/1000F;
//				System.out.println("\telapsed 2: "+elapsedTimeSec);
				if(containsAnyt4jSorted(inIdSet,to) == false){//no overlap in ingroup and outgroup of dbnode
					elapsedTimeMillis2 = System.currentTimeMillis()-start2;
					elapsedTimeSec = elapsedTimeMillis2/1000F;
//					System.out.println("\telapsed 2: "+elapsedTimeSec);
					if(containsAnyt4jSorted(inIdSet,tm)){//some overlap in inbipart -- //LARGEST one, do last
						elapsedTimeMillis2 = System.currentTimeMillis()-start2;
						elapsedTimeSec = elapsedTimeMillis2/1000F;
//						System.out.println("\telapsed 3: "+elapsedTimeSec);
						boolean tmt = false;
						for (int i=0;i<inIdSet.size();i++){
							if (tm.contains(inIdSet.getQuick(i))==false){
								tm.add(inIdSet.getQuick(i));
								tmt = true;
							}
						}
						if(tmt){
							tm.sort();
							tn.setProperty("mrca",tm.toArray());
						}
						tmt = false;
						for (int i=0;i<outIdSet.size();i++){
							if (to.contains(outIdSet.getQuick(i))==false){
								to.add(outIdSet.getQuick(i));
								tmt = true;
							}
						}
						if(tmt){
							to.sort();
							tn.setProperty("outmrca",to.toArray());
						}
						return Evaluation.INCLUDE_AND_PRUNE;
					}
				}
			}
		}else{
			boolean fail = false;
			//trying the small one first
			if(smInIdSet!= null){
				if(tm.containsAll(smInIdSet)==false){
					fail = true;
				}
			}
			if(fail == true){
//				System.out.println("early quit2");
				return Evaluation.EXCLUDE_AND_CONTINUE;
			}
//			System.out.println("passed early quit2");
			if(containsAnyt4jSorted(outIdSet,tm)==false){//smaller one usually
				if(tm.containsAll(inIdSet)==true){
						return Evaluation.INCLUDE_AND_PRUNE;
				}
			}
		}
		return Evaluation.EXCLUDE_AND_CONTINUE;
	}
	
	public static boolean containsAnyt4jSorted(TLongArrayList ar1, TLongArrayList ar2) {
		if (ar1.size() < ar2.size()) {
			return containsAnyt4jSortedOrdered(ar1, ar2);
		} else {
			return containsAnyt4jSortedOrdered(ar2, ar1);
		}
	}

	/**
	 * Internal method that is faster when the relative sizes of the inputs are known.
	 * 
	 * @param shorter
	 * @param longer
	 * @return
	 */
	private static boolean containsAnyt4jSortedOrdered(TLongArrayList shorter, TLongArrayList longer) {
		boolean retv = false;
		shorterLoop: for (int i = 0; i < shorter.size(); i++) {
			longerLoop: for (int j = 0; j < longer.size(); j++) {
				if (longer.getQuick(j) > shorter.getQuick(i)) {
					break longerLoop;
				}
				if (longer.getQuick(j) == shorter.getQuick(i)) {
					retv = true;
					break shorterLoop;
				}
			}
		}
		return retv;
	}
}
