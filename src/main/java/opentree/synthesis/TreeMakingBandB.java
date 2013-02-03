package opentree.synthesis;

import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.collections.*;

/*
 * This implements a branch and bound algorithm
 * for determining what nodes to include when 
 * constructing a tree.
 * 
 * There are a number of optimizations that should be 
 * implemented once this is complete
 */

public class TreeMakingBandB {
	private int possibleTipNumber = 0;
	private HashSet<Integer> bestx; //the indices of the best sets
	private HashSet<Integer> x; //the indices of the current sets
	private int best_cost;
	private HashMap<Integer,HashSet<Long>> S;
	private int S_size;
	private HashSet<Long> s_current; //current set of Longs
	private HashSet<Long> curbest; //current best set of Longs
	
	public HashSet<Integer> runSearch(int tipnumbers,HashMap<Integer,HashSet<Long>> inS){
		S = inS;
		possibleTipNumber = tipnumbers;
		S_size = S.size();
		s_current = new HashSet<Long>();
		curbest = new HashSet<Long>();
		x = new HashSet<Integer>();
		bestx = new HashSet<Integer>();
		best_cost = Integer.MAX_VALUE;
		search(0,0);
		return bestx;
	}
	
	private void search(int index,int cost){
		if (cost > best_cost)
			return;
		if(index+1 > S_size || s_current.size() == possibleTipNumber){
			if(cost <= best_cost && s_current.size() > curbest.size()){
				best_cost = cost;
				curbest = new HashSet<Long>(s_current);
				bestx = new HashSet<Integer> (x);
				return;
			}else{
				return;
			}
		}
		if(CollectionUtils.intersection(S.get(index),s_current).size() == 0){
			s_current.addAll(S.get(index));
			x.add(index);
			search(index+1,cost);
			s_current.removeAll(S.get(index));
			x.remove(index);
		}
		search(index+1,cost);
	}
	
}
