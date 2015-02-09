package opentree.synthesis.mwis;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import opentree.TLongBitArraySet;

public abstract class BaseWeightedIS implements Iterable<Long> {

	protected Long[] ids;
	protected TLongBitArraySet[] descendants;
	protected int count;

	protected List<Long> bestSet;
	protected double bestScore;

	protected final int MAX_LONG_BITMASK_SIZE;
	protected final int MAX_BITSET_MASK_SIZE;

	public BaseWeightedIS(Long[] ids, TLongBitArraySet[] descendants) {
		MAX_LONG_BITMASK_SIZE = new LongBitMask(0).maxSize();
		MAX_BITSET_MASK_SIZE = new BitSetMask(0).maxSize();
		
		this.ids = ids;
		this.descendants = descendants;

		System.out.println("input to " + this.getClass().getName() + ": ");
		System.out.println(this);
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
	
	public Long[] best() {
		Long[] l = new Long[bestSet.size()];
		return this.bestSet.toArray(l);
	}
	
	protected BitMask getEmptyBitMask(int size) {
		if (size > MAX_LONG_BITMASK_SIZE) {
			return new BitSetMask(size);
		} else {
			return new LongBitMask(size);
		}
	}
	
	protected BitMask getBitMask(BitMask mask) {
		if (mask.size() > MAX_LONG_BITMASK_SIZE) {
			return new BitSetMask((BitSetMask) mask);
		} else {
			return new LongBitMask((LongBitMask) mask);
		}
	}

	protected boolean validate(BitMask candidate) {
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
		
	protected static void randomInputTest(int numberOfSets) {
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
		
		GreedyApproximateWeightedIS B = new GreedyApproximateWeightedIS(ids, descendants);

		System.out.println("\nproduced: " + Arrays.toString(B.best()));
	}
	
	protected static long randomInt(int max) {
		return Math.round(Math.random() * max);
	}

	protected static void simpleTest1() {
		Long[] ids = new Long[] {44L, 40L, 1L, 0L};
		long[][] sets = new long[][] {{7}, {3}, {1,2,4,5,6}, {2,3,4,5,6,7}};
		long[] expected = new long[] {44,40,1};
		doSimpleTest(ids, sets, expected);
	}
	
	protected static void simpleTest2() {
		Long[] ids = new Long[] {1L, 2L, 3L, 4L, 5L, 6L, 7L};
		long[][] sets = new long[][] {{1}, {2}, {3}, {4}, {5}, {6}, {7}};
		long[] expected = new long[] {1, 2, 3, 4, 5, 6, 7};
		doSimpleTest(ids, sets, expected);
	}
	
	protected static void doSimpleTest(Long[] ids, long[][] sets, long[] expected) {
		
		TLongBitArraySet[] descendants = new TLongBitArraySet[sets.length];
		for (int i = 0; i < sets.length; i++) {
			descendants[i] = new TLongBitArraySet();
			descendants[i].addAll(sets[i]);
		}

		GreedyApproximateWeightedIS B = new GreedyApproximateWeightedIS(ids, descendants);

		Long[] b = B.best();
		System.out.println("\nexpected: " + Arrays.toString(expected));
		System.out.println("produced: " + Arrays.toString(b) + "\n");
		for (int i = 0; i < b.length; i++) {
			assert b[i] == expected[i];
		}
	}
}
