package opentree.synthesis.mwis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.opentree.bitarray.TLongBitArraySet;

public class GreedyApproximateWeightedIS extends BaseWeightedIS {

	public GreedyApproximateWeightedIS(Long[] ids, double[] weights, TLongBitArraySet[] constituents) {
		super(ids, weights, constituents);
		findBestSet();
	}
	
	/**
	 * Repeatedly pick the most heavily weighted set from among the remaining sets
	 * and save it, then eliminate it and its adjacent vertices. Continue until no
	 * sets remain.
	 */
	private void findBestSet() {

		bestSet = new ArrayList<Long>();

		// all sets begin available -- open all sites in a bitmask
		BitMask available = getEmptyBitMask(ids.length);
		for (int i = 0; i < ids.length; i++) {
			available.open(i);
		}
		
		// repeatedly find the best set out of all the available sets
		while (available.openBits() > 0) {
			Integer bestRel = null;
			double bestScore = 0;

			// find the rel with the highest score
			for (int i : available) {
//				double s = getScoreForRel(i);
				double s = weights[i];
				if (s > bestScore) {
					bestRel = i;
					bestScore = s;
				}
			}
			
			// found a best rel, now save it and exclude it and all overlapping rels
			bestSet.add(ids[bestRel]);
			available.close(bestRel);
			for (int i : available) {
				if (contituents[bestRel].containsAny(contituents[i])) {
					available.close(i);
				}
			}
		}

		Collections.sort(bestSet);
	}
	
//	private double getScoreForRel(int i) {
//		if (! scores.containsKey(ids[i])) {
//			scores.put(ids[i], (double) contituents[i].size());
//			scores.put(ids[i], weights[i]);
//		}
//		return scores.get(ids[i]);
//	}
		
	public static void main(String[] args) {
		simpleTest1(); // fails greedy approximation!
		simpleTest2();
		randomInputTest(Integer.valueOf(args[0]));
	}
	
}
