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
	private int best_reward;
	private HashMap<Integer,HashSet<Long>> S;
	private HashMap<Long,HashSet<Integer>> E;
	private HashSet<Long> alltaxa;
	private int S_size;
	private HashSet<Long> s_current; //current set of Longs
	private HashSet<Long> curbest; //current best set of Longs
	
	public HashSet<Integer> runSearch(int tipnumbers,HashMap<Integer,HashSet<Long>> inS){
		S = inS;
		//TODO: calculate E
		alltaxa = new HashSet<Long>();
		for(Integer hs : inS.keySet()){
			alltaxa.addAll(inS.get(hs));
		}
		E = new HashMap<Long,HashSet<Integer>>();
		for (Long lng: alltaxa){
			HashSet<Integer> ths = new HashSet<Integer>();
			for(Integer hs : inS.keySet()){
				if(inS.get(hs).contains(lng))
					ths.add(hs);
			}
			E.put(lng, ths);
		}
		//finish calculating E
		possibleTipNumber = tipnumbers;
		S_size = S.size();
		s_current = new HashSet<Long>();
		curbest = new HashSet<Long>();
		x = new HashSet<Integer>();
		bestx = new HashSet<Integer>();
		best_cost = Integer.MAX_VALUE;
		best_reward = 0;
		//search(0,0);
		upper_search(0,0);
		return bestx;
	}
	
	/**
	 * 
	 * @param index
	 * @param cost
	 */
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
	
	/**
	 * 
	 * @param index
	 * @param reward
	 */
	private void upper_search(int index,int reward){
		//calculate future reward
		HashSet<Long> future_reward_hs = new HashSet<Long>();
		HashSet<Integer> cs = new HashSet<Integer>();
		for (Long j: s_current){
			cs.addAll(E.get(j));
		}
		for (Long j:alltaxa){
			if (s_current.contains(j) == false){
				HashSet<Integer> e_j = new HashSet<Integer>(E.get(j));
				e_j.removeAll(cs);
				HashSet<Long> ts = new HashSet<Long>();
				int max_v = 0;
				for(Integer tej:e_j){
					if(tej >= index && S.get(tej).size()>=max_v){
						max_v = S.get(tej).size();
						ts = S.get(tej);
					}
				}
				future_reward_hs.addAll(ts);
			}
		}
		int future_reward = future_reward_hs.size();
		//System.out.println(reward+" "+future_reward+" "+best_reward);
		//end calculate future reward
		if (reward+future_reward < best_reward){
//			System.out.println("stop short");
			return;
		}
		if(index+1 > S_size || s_current.size() == possibleTipNumber){
			if(reward >= best_reward && s_current.size() > curbest.size()){
				best_reward = reward;
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
			upper_search(index+1,s_current.size());
			s_current.removeAll(S.get(index));
			x.remove(index);
		}
		//TODO: OR reward+S.get(index).size()
		upper_search(index+1,reward);
	}
	
}
