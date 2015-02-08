package opentree.synthesis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BitMask implements Iterable<Integer> {

	long mask;
	final int size;

	public BitMask(int size) {
		mask = 0;
		this.size = validateSize(size);
	}
	
	public BitMask (BitMask original) {
		mask = original.mask;
		size = original.size;
	}

	public void open(int position) {
		mask = mask | (int) Math.pow(2, position);
	}
	
	public int size() {
		return size;
	}
	
	public Iterator<Integer> iterator() {
		List<Integer> positions = new ArrayList<Integer>();
		for (int j = 0; j < size; j++) {
			if (getBit(mask, j) > 0) {
				positions.add(j);
			}
		}
		return positions.iterator();
	}
	
	private static int getBit(long n, int k) {
	    return (int) (n >> k) & 1;
	}
	
	private static int validateSize(int size) {
		int d = maxBitPosition(Long.MAX_VALUE);
		if (size > d) {
			throw new IllegalArgumentException("specified size " + size + " exceeds the " +
					"available number of bits (in a long integer), which is " + d);
		}
		return size;
	}
	
	private static int maxBitPosition(long n) {
		if (n == 0) {
			return 0;
		} else {
			return (int) Logarithm.logb(Long.highestOneBit(n), 2);
		}
	}
}