package opentree.synthesis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class TreeMakingExhaustivePairs {

	
	public ArrayList<Integer> calculateExhaustivePairs(ArrayList<Long> testnodesal,HashMap<Long,HashSet<Long>> storedmrcas){
		HashMap<Integer,HashMap<Integer,Integer>> pairmap = new HashMap<Integer,HashMap<Integer,Integer>>();
		HashMap<Integer,ArrayList<Integer>> pairlist = new HashMap<Integer,ArrayList<Integer>>();
		for(int i=0;i<testnodesal.size();i++){
			HashMap<Integer,Integer>tmap = new HashMap<Integer,Integer>();
			ArrayList<Integer> tlist = new ArrayList<Integer>();
			pairmap.put(i,tmap);
			pairlist.put(i,tlist);
		}
		for(int i=0;i<testnodesal.size();i++){
			HashSet<Long> mrcas1 = storedmrcas.get(testnodesal.get(i));
			for(int j=0;j<testnodesal.size();j++){
				if (j > i){
					HashSet<Long> mrcas2 = new HashSet<Long>(storedmrcas.get(testnodesal.get(j)));
					//test intersection
					int sizeb = mrcas2.size();
					mrcas2.removeAll(mrcas1);
					if ((sizeb - mrcas2.size()) == 0){
						pairmap.get(i).put(j, mrcas2.size());
						pairmap.get(j).put(i, mrcas2.size());
						pairlist.get(i).add(j);
						pairlist.get(j).add(i);
					}else{
						pairmap.get(i).put(j,0);
					}
				}
				if(i==j){
					pairmap.get(i).put(j,mrcas1.size());
				}
			}
		}
		
		int bestscore = 0;
		ArrayList<Integer> bestscenario = null;
		for(int i=0;i<testnodesal.size();i++){
			if(pairmap.get(i).get(i) > bestscore){
				bestscore = pairmap.get(i).get(i);
				bestscenario = new ArrayList<Integer>();
				bestscenario.add(i);
			}
			int tsize = pairlist.get(i).size()-1;
//			System.out.println("i: "+i+" "+tsize);
			for(int j=1;j<=tsize;j++){
//				System.out.println("=============");
				ArrayList<ArrayList<Integer>> its = iterate(tsize, j);
				for(int k=0;k<its.size();k++){
					int tscore = pairmap.get(i).get(i);
					for(int m=0;m<its.get(k).size();m++){
						boolean fail = false;
						for(int n=0;n<its.get(k).size();n++){
							if(n >= m){
								int tpairmapsc = pairmap.get(pairlist.get(i).get(its.get(k).get(m)))
										.get(pairlist.get(i).get(its.get(k).get(n)));
								if(tpairmapsc == 0){
									fail = true;
									tscore = 0;
									break;
								}
							}
						}
						if(fail == true){
							tscore = 0;
							break;
						}else{
							tscore += pairmap.get(pairlist.get(i).get(its.get(k).get(m)))
									.get(pairlist.get(i).get(its.get(k).get(m)));
						}
					}
//					System.out.println(its.get(k)+" "+tscore);
					
					if(tscore > bestscore){
						bestscore = tscore;
						bestscenario.clear();
						bestscenario.add(i);
						for(int n=0;n<its.get(k).size();n++){
							bestscenario.add(pairlist.get(i).get(its.get(k).get(n)));
						}
					}
				}
			}
		}
		return bestscenario;
	}

	private int combinations(int m, int n){
		if(!(m >= n)&&!(n >= 0))
			System.out.println("m >= n >= 0 required");
		if (n > (m >> 1))
			n = m-n;
		if (n == 0)
			return 1;
		int result = m;
		int i=2;
		m=m-1;n=n-1;
		while(n>0){
			//assert (result * m) % i == 0
			result = result * m / i;
			i = i+1;
			n = n-1;
			m = m-1;
		}
		return result;
	}

	private ArrayList<ArrayList<Integer>> iterate(int M, int N){
		if(!(M >= N)&&!(N >= 1))
			System.out.println("M >= N >= 1 required");
		int ncombs = combinations(M,N);
		//	        int [][] result = new int[ncombs][0];
		ArrayList<ArrayList<Integer>> resultal = new ArrayList<ArrayList<Integer>>();
		for(int x=0;x<ncombs;x++){
			resultal.add(new ArrayList<Integer>(0));
			int i = x; int n = N; int m = M;
			int c = ncombs * n / m;
			int element=0;
			while(m>0){
				//System.out.println(element+" "+i+" "+c+" "+m+" "+n);
				if (i < c){
					//	                    result[x] = Utils.addToArray(result[x], element);
					resultal.get(x).add(element);
					n = n-1;
					if (n == 0)
						break;
					c = c*n/(m-1);
				}
				else{
					i = i-c;
					c = c*(m-n)/(m-1);
				}
				element++;
				m = m-1;
			}  
		}
		return resultal;
	}

	private ArrayList<ArrayList<Integer>> idx2bitvect(ArrayList<ArrayList<Integer>> indices, int M){
		ArrayList<ArrayList<Integer>> resultal = new ArrayList<ArrayList<Integer>>(indices.size());
		for(int i=0;i<indices.size();i++){
			resultal.add(new ArrayList<Integer>(M));
			for(int j=0;j<M;j++){
				indices.get(i).set(j,0);
			}
		}
		for(int i=0;i<indices.size();i++){
			for(int j=0;j<indices.get(i).size();j++){
				resultal.get(i).set(indices.get(i).get(j), 1);
			}
		}
		return resultal;
	}

}
