package opentree;

import java.util.BitSet;
import gnu.trove.list.array.TLongArrayList;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

/**
 * This uses the mrca and mrca out bits
 */
public class LicaBipartEvaluatorBS implements Evaluator{
	TLongArrayList visited = null;
	BitSet inIdBS = null;
	TLongArrayList inIdSet = null; //this can be larger than smInIdSet and includes the mrca for the matched nodes in the tree (so the dbnodes of the children)
	BitSet outIdBS = null;
	TLongArrayList outIdSet = null; //this is the other part of the bipartition
	GraphDatabaseAgent graphdb = null;
	public LicaBipartEvaluatorBS(){}
	public void setOutset(TLongArrayList fids){
		outIdSet = fids;
		if(fids.size()>0){
		outIdBS = new BitSet((int) fids.max());//could set this to the smallest number
		for(int i=0;i<fids.size();i++){
			outIdBS.set((int)fids.getQuick(i));
		}
		}else{
			outIdBS = new BitSet(0);
		}
	}
	public void setInset(TLongArrayList fids){
		inIdSet = fids;
		inIdBS = new BitSet((int) fids.max());//could set this to the smallest number
		for(int i=0;i<fids.size();i++){
			inIdBS.set((int)fids.getQuick(i));
		}
	}
	public void setVisitedSet(TLongArrayList fids){
		visited = fids;
	}
	public void setgraphdb(GraphDatabaseAgent gb){
		graphdb = gb;
	}
	public TLongArrayList getVisitedSet(){
		return visited;
	}
	
	@Override
	public Evaluation evaluate(Path arg0) {
		//System.out.println(arg0);
		Node tn = arg0.endNode();
		if(visited.contains(tn.getId())){
			return Evaluation.EXCLUDE_AND_PRUNE;
		}
		visited.add(tn.getId());
		TLongArrayList ttm = new TLongArrayList((long[])tn.getProperty("mrca"));
		BitSet tm = new BitSet((int) ttm.max());
		for(int i=0;i<ttm.size();i++){
			tm.set((int)ttm.getQuick(i));
		}
//		System.out.println("inIdSet "+inIdSet.size());
//		System.out.println("outIdSet "+outIdSet.size());
//		System.out.println("mrca: "+tm.size());

		//NOTE: in order to cut down on size, taxnodes outmrca are assumed to be "the rest"
		//		they are denoted with not having an outmrca
		boolean taxnode =false;
		BitSet to = null;
		TLongArrayList tto = null;
		if (tn.hasProperty("outmrca") == false){
			taxnode = true;
		}else{
			tto = new TLongArrayList((long[])tn.getProperty("outmrca"));
			to = new BitSet((int) tto.max());//could set this to the smallest number
			for(int i=0;i<tto.size();i++){
				to.set((int)tto.getQuick(i));
			}
//			System.out.println("mrca o: "+to.size());
		}
		if(taxnode == false){
//			System.out.println("passed early quit");
			//if match, extend the mrca and outmrca
			
			if(tm.intersects(outIdBS) == false){//no overlap of outgroup and ingroup of dbnode
				if(to.intersects(inIdBS) == false){//no overlap in ingroup and outgroup of dbnode
					if(tm.intersects(inIdBS)==true){//some overlap in inbipart -- //LARGEST one, do last
						boolean tmt = false;
						BitSet inIdBS2 = (BitSet) inIdBS.clone();
						inIdBS2.andNot(tm);//any missing ones we want to add
						for (int i=0;i<inIdBS2.length();i++){
							if (inIdBS2.get(i) == true){
								ttm.add(i);
								tmt = true;
							}
						}
						if(tmt){
							ttm.sort();
							tn.setProperty("mrca",ttm.toArray());
						}
						tmt = false;
						BitSet outIdBS2 = (BitSet) outIdBS.clone();
						outIdBS2.andNot(to);//any missing ones we want to add
						for (int i=0;i<outIdBS2.length();i++){
							if (outIdBS2.get(i) == true){
								tto.add(i);
								tmt = true;
							}
						}
						if(tmt){
							tto.sort();
							tn.setProperty("outmrca",tto.toArray());
						}
						return Evaluation.INCLUDE_AND_PRUNE;
					}
				}
			}
		}else{
			if(outIdBS.intersects(tm)==false){//containsany
				tm.and(inIdBS);
				if(inIdBS.cardinality()==tm.cardinality()){//containsall
						return Evaluation.INCLUDE_AND_PRUNE;
				}
			}
		}
		return Evaluation.EXCLUDE_AND_CONTINUE;
	}
}
