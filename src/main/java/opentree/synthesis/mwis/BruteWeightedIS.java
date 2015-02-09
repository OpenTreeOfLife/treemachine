package opentree.synthesis.mwis;

import java.util.ArrayList;

import opentree.TLongBitArraySet;
import opentree.synthesis.BitMask;

public class BruteWeightedIS extends BaseWeightedIS {
	
	private int count;
	public static final int MAX_TRACTABLE_N = 25;
	
	public BruteWeightedIS(Long[]ids, TLongBitArraySet[] descendants) {
		super(ids, descendants);
		findBestSet();
	}
	
	/** 
	 * Trivial scoring based only on total set size. Adjust this to address conflict with trees.
	 */
	protected void scoreSizeOnly(BitMask candidate) {
		
		// test output
		ArrayList<Long> curr = new ArrayList<Long>();
		for (int j : candidate) { curr.add(ids[j]); }
		System.out.print("checking set " + count + ": " + curr);
		
		double s = 0;
		ArrayList<Long> R = new ArrayList<Long>();
		for (int j : candidate) {
			s += descendants[j].size();
			R.add(ids[j]);
		}
		
		System.out.println(", score = " + s);
		
		if (s > bestScore) {
			bestScore = s;
			bestSet = R;
		}
	}

	/**
	 * Initialize recursive set sampling with empty bitmask, starting from first position.
	 */
	private void findBestSet() {
		count = 0;
		sample(getEmptyBitMask(ids.length), -1);
		System.out.println("combinations tried: " + count);
	}
	
	/**
	 * Recursive procedure takes incoming bitmask, opens each additional position (to the
	 * right of the last position), and checks if each updated bitmask still represents a
	 * valid set (no internal conflict). If it does, the candidate is scored and passed to
	 * the next recursive step.
	 */
	private void sample(BitMask incoming, int lastPos) {
		count++;
		for (int i = 1; i + lastPos < incoming.size(); i++) {
			int nextPos = lastPos + i;
			BitMask candidate = getBitMask(incoming);
			candidate.open(nextPos);
			
			if (validate(candidate)) {
				scoreSizeOnly(candidate); // switch this to a smarter scoring mechanism
				sample(candidate, nextPos);
			}
		}
/*		if (count++ % 100000 == 0) {
			System.out.println("(now on combination " + count + ")");
		} */
	}
	
	public static void main(String[] args) {
		simpleTest1();
		simpleTest2();
		randomInputTest(Integer.valueOf(args[0]));
	}
}
