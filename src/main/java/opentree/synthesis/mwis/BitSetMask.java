package opentree.synthesis.mwis;

import java.util.BitSet;
import java.util.Iterator;

public class BitSetMask implements BitMask {

	private class BitSetIterator implements Iterator<Integer> {
		
		BitSet bs;
		Integer nextBit;
		
		public BitSetIterator(BitSet bs) {
			this.bs = bs;
			this.nextBit = bs.nextSetBit(0);
		}

		@Override
		public boolean hasNext() {
			return nextBit >= 0;
		}

		@Override
		public Integer next() {
			int thisBit = nextBit;
			nextBit = bs.nextSetBit(nextBit+1);
			return thisBit;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	private BitSet mask;
	private int size;

	public BitSetMask(int size) {
		mask = new BitSet();
		this.size = size;
	}
	
	public BitSetMask(int size, boolean initVal) {
		mask = new BitSet();
		this.size = size;
		for (int i = 0; i < size; i++) {
			mask.set(i, initVal);
		}
	}
	
	public BitSetMask (BitSetMask original) {
		mask = original.mask;
		size = original.size;
	}

	public void open(int position) {
		mask.set(position);
	}
	
	public void close(int position) {
		mask.clear(position);
	}
	
	public boolean isSet(int position) {
		return mask.get(position);
	}
	
	public int size() {
		return size;
	}
	
	public int openBits() {
		return mask.cardinality();
	}
	
	@Override
	public String toString() {
		return mask.toString();
	}
		
	public Iterator<Integer> iterator() {
		return new BitSetIterator(mask);
	}
	
	public int maxSize() {
		return Integer.MAX_VALUE;
	}
}