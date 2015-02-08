package opentree.synthesis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import opentree.TLongBitArraySet;

public class BruteWeightedIS implements Iterable<Long> {
	
	private Long[] ids;
	private TLongBitArraySet[] descendants;
	private int count;

	private List<Long> bestSet;
	private double bestScore;

	private final int MAX_LONG_BITMASK_SIZE;
	private final int MAX_BITSET_MASK_SIZE;

	public BruteWeightedIS(Long[] ids, TLongBitArraySet[] descendants) {
		MAX_LONG_BITMASK_SIZE = new LongBitMask(0).maxSize();
		MAX_BITSET_MASK_SIZE = new BitSetMask(0).maxSize();
		
		this.ids = ids;
		this.descendants = descendants;

		System.out.println("input to brute force method:");
		System.out.println(this);
		
		findBestSet();
	}
	
	@Override
	public String toString() {
		StringBuffer s = new StringBuffer();
		for (int i = 0; i < ids.length; i++) {
			s.append(ids[i] + ": " + this.descendants[i] + "\n");
		}
		return s.toString();
	}
	
	@Override
	public Iterator<Long> iterator() {
		return bestSet.iterator();
	}
	
	public int size() {
		return bestSet.size();
	}
	
	/**
	 * Initialize recursive set sampling with empty bitmask, starting from first position.
	 */
	private void findBestSet() {
		count = 0;
		sample(getEmptyBitMask(ids.length), -1);
		System.out.println("combinations tried: " + count);
	}
		
	public Long[] best() {
		Long[] l = new Long[bestSet.size()];
		return this.bestSet.toArray(l);
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
	
	private BitMask getEmptyBitMask(int size) {
		if (size > MAX_LONG_BITMASK_SIZE) {
			return new BitSetMask(size);
		} else {
			return new LongBitMask(size);
		}
	}
	
	private BitMask getBitMask(BitMask mask) {
		if (mask.size() > MAX_LONG_BITMASK_SIZE) {
			return new BitSetMask((BitSetMask) mask);
		} else {
			return new LongBitMask((LongBitMask) mask);
		}
	}

	private boolean validate(BitMask candidate) {
		TLongBitArraySet S = new TLongBitArraySet();

		for (int j : candidate) {
			if (S.containsAny(descendants[j])) {
				return false; // internal conflict
			} else {
				S.addAll(descendants[j]);
			}
		}
		return true;
	}
	
	/** 
	 * Trivial scoring based only on total set size. Adjust this to address conflict with trees.
	 */
	private void scoreSizeOnly(BitMask candidate) {
		
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

	private static long randomInt(int max) {
		return Math.round(Math.random() * max);
	}
	
	public static void main(String[] args) {
		simpleTest1();
		simpleTest2();
		randomInputTest(Integer.valueOf(args[0]));
	}
	
	private static void randomInputTest(int numberOfSets) {
		int setSize = 10;
		int maxItemValue = 1000;
		
		System.out.println("number of combinations = " + Math.pow(2, numberOfSets));
		
		Long[] ids = new Long[numberOfSets];
		for (int i = 0; i < numberOfSets; i++) {
			ids[i] = (long) i;
		}
		
		TLongBitArraySet[] descendants = new TLongBitArraySet[ids.length];
		for (int i = 0; i < ids.length; i++) {
			TLongBitArraySet a = new TLongBitArraySet();
			for (int j = 0; j < setSize; j++) {
				a.add(randomInt(maxItemValue));
			}
			descendants[i] = a;
		}
		
		BruteWeightedIS B = new BruteWeightedIS(ids, descendants);

		System.out.println("\nproduced: " + Arrays.toString(B.best()));
	}
		
	private static void simpleTest1() {
		Long[] ids = new Long[] {44L, 40L, 1L, 0L};
		long[][] sets = new long[][] {{7}, {3}, {1,2,4,5,6}, {2,3,4,5,6,7}};
		long[] expected = new long[] {44,40,1};
		doSimpleTest(ids, sets, expected);
	}
	
	private static void simpleTest2() {
		Long[] ids = new Long[] {1L, 2L, 3L, 4L, 5L, 6L, 7L};
		long[][] sets = new long[][] {{1}, {2}, {3}, {4}, {5}, {6}, {7}};
		long[] expected = new long[] {1, 2, 3, 4, 5, 6, 7};
		doSimpleTest(ids, sets, expected);
	}
	
	private static void doSimpleTest(Long[] ids, long[][] sets, long[] expected) {
		
		TLongBitArraySet[] descendants = new TLongBitArraySet[sets.length];
		for (int i = 0; i < sets.length; i++) {
			descendants[i] = new TLongBitArraySet();
			descendants[i].addAll(sets[i]);
		}

		BruteWeightedIS B = new BruteWeightedIS(ids, descendants);

		Long[] b = B.best();
		System.out.println("\nexpected: " + Arrays.toString(expected));
		System.out.println("produced: " + Arrays.toString(b) + "\n");
		for (int i = 0; i < b.length; i++) {
			assert b[i] == expected[i];
		}
	}
}
