package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.opentree.utils.GeneralUtils.print;

import opentree.synthesis.mwis.BitMask;
import opentree.synthesis.mwis.BitSetMask;
import opentree.synthesis.mwis.LongBitMask;

public class Combinations<T> {
	
	private final int MAX_LONG_BITMASK_SIZE = new LongBitMask(0).maxSize();
	private final int MAX_BITSET_MASK_SIZE = new BitSetMask(0).maxSize();

	private final List<T> set;
	
	public Combinations(Set<T> set) {
		this.set = new ArrayList<T>(set);
	}
	
	public Iterable<Set<T>> mostInclusiveFirst() {
		return new Iterable<Set<T>>() {
			@Override
			public Iterator<Set<T>> iterator() {
				return new PrunableIterator(true);
			}
		};
	}

	public Iterable<Set<T>> leastInclusiveFirst() {
		return new Iterable<Set<T>>() {
			@Override
			public Iterator<Set<T>> iterator() {
				return new PrunableIterator(false);
			}
		};
	}
	
	public class PrunableIterator implements Iterator<Set<T>> {
	
		/** simple container class */
		private class Sample {
			BitMask bitmask;
			int nextPosition;
			public Sample(BitMask bitmask, int nextPosition) {
				this.bitmask = bitmask;
				this.nextPosition = nextPosition;
			}
		}
		
		int count = 0;
		boolean mostInclusiveFirst;
		LinkedList<Sample> approved = new LinkedList<Sample>();
		List<Sample> proposed = new ArrayList<Sample>();
		
		PrunableIterator(boolean mostInclusiveFirst) {
			this.mostInclusiveFirst = mostInclusiveFirst;
			BitMask start = mostInclusiveFirst ? getFullBitMask(set.size()) : getEmptyBitMask(set.size());
			approved.add(new Sample(start, -1));
		}
		
		@Override
		public boolean hasNext() {
			// approve all the proposed samples derived from the last combination if they have not been pruned
			if (proposed != null) { approved.addAll(proposed); }
			proposed = new ArrayList<Sample>();
			return approved.size() > 0;
		}
		
		/**
		 * Return the next set in the series of combinations.
		 */
		@Override
		public Set<T> next() {

			// get the next sample from the approved queue
			Sample s = approved.pop();
			BitMask bitmask = s.bitmask;
			int lastPos = s.nextPosition;
			
			// collect the set that corresponds to this sample
			Set<T> t = new HashSet<T>();
			for (int i : bitmask) {
				t.add(set.get(i));
			}
			
			// queue the proposed upcoming samples derived from this one
			for (int i = 1; i + lastPos < bitmask.size(); i++) {
				int nextPos = lastPos + i;
				BitMask candidate = getBitMask(bitmask);
				if (mostInclusiveFirst) {
					candidate.close(nextPos);
				} else {
					candidate.open(nextPos);
				}
				proposed.add(new Sample(candidate, nextPos));
			}
			
			count++;
			return t;
		}
		
		/**
		 * Remove the last returned combination from the set to be used as future starting points for
		 * finding more combinations.
		 */
		public void prune() {
			proposed = null;
		}
		
		private BitMask getEmptyBitMask(int size) {
			BitMask b;
			if (size > MAX_LONG_BITMASK_SIZE) {
				b = new BitSetMask(size, false);
			} else {
				b = new LongBitMask(size, false);
			}
			return b;
		}
		
		private BitMask getFullBitMask(int size) {
			BitMask b;
			if (size > MAX_LONG_BITMASK_SIZE) {
				b = new BitSetMask(size, true);
			} else {
				b = new LongBitMask(size, true);
			}
			return b;
		}
		
		private BitMask getBitMask(BitMask mask) {
			BitMask b;
			if (mask.size() > MAX_LONG_BITMASK_SIZE) {
				b = new BitSetMask((BitSetMask) mask);
			} else {
				b = new LongBitMask((LongBitMask) mask);
			}
			return b;
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void simpleTest(Object[] set) {
		
		Set input = new HashSet();
		for (Object o : set) { input.add(o); }
		
		Combinations c = new Combinations(input);
		for (Object s : c.mostInclusiveFirst()) {
			print(s);
		}
		
		for (Object s : c.leastInclusiveFirst()) {
			print(s);
		}
	}
	
	public static void main(String[] args) {
		simpleTest(new Long[] {1L, 2L, 3L, 4L});
	}
	
}
