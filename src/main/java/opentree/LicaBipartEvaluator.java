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
	TLongArrayList inIdSet = null;
	TLongArrayList outIdSet = null;
	GraphDatabaseAgent graphdb = null;
	public LicaBipartEvaluator(){}
	public void setOutset(TLongArrayList fids){
		outIdSet = fids;
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
		//NOTE: in order to cut down on size, taxnodes outmrca are assumed to be "the rest"
		//		they are denoted with not having an outmrca
		boolean taxnode =false;
		TLongArrayList to = null;
		if (tn.hasProperty("outmrca") == false){
			taxnode = true;
		}else{
			to = new TLongArrayList((long[])tn.getProperty("outmrca"));
		}
		if(taxnode == false){
			//if match, extend the mrca and outmrca
			if(containsAnyt4jSorted(inIdSet,tm)){//some overlap in inbipart
				if(containsAnyt4jSorted(inIdSet,to)==false){//no overlap in ingroup and outgroup of dbnode
					if(containsAnyt4jSorted(outIdSet,tm) == false){//no overlap of outgroup and ingroup of dbnode
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
			if(tm.containsAll(inIdSet)==true){
				if(containsAnyt4jSorted(outIdSet,tm)==false){
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
