package opentree.synthesis;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

public class BitSetMask implements BitMask {

	private BitSet mask;
	private int size;

	public BitSetMask(int size) {
		mask = new BitSet();
		this.size = size;
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
	
	public Iterator<Integer> iterator() {
		List<Integer> positions = new ArrayList<Integer>();
		int j = mask.nextSetBit(0);
		while (j > 0) {
			positions.add(j);
			j = mask.nextSetBit(j);
		}
		return positions.iterator();
	}
	
	public int maxSize() {
		return Integer.MAX_VALUE;
	}
}