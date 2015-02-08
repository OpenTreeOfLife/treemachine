package opentree.synthesis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import scala.actors.threadpool.Arrays;

public class IntegerBitMask implements BitMask {

	private long mask;
	private int size;

	public IntegerBitMask(int size) {
		if (size > maxSize()) {throw new IllegalArgumentException();}
		mask = 0;
		this.size = size;
	}
	
	public IntegerBitMask (IntegerBitMask original) {
		mask = original.mask;
		size = original.size;
	}

	public void open(int position) {
		if (! (position < size())) {throw new IllegalArgumentException();};
		mask = mask | v(position);
	}
	
	public void close(int position) {
		if (! (position < size())) {throw new IllegalArgumentException();};
		open(position);
		mask = mask ^ v(position);
	}
	
	public boolean isSet(int position) {
		return v(position) == 1;
	}
	
	public int size() {
		return size;
	}

	private long v(int i) {
		return (int) Math.pow(2, i);
	}
	
	@Override
	public String toString() {
		StringBuffer s = new StringBuffer();
		s.append("[");
		boolean first = true;
		for (int i : this) {
			if (first) {
				first = false;
			} else {
				s.append(", ");
			}
			s.append(i);
		}
		s.append("]");
		return s.toString();
	}
	
	public Iterator<Integer> iterator() {
		List<Integer> positions = new ArrayList<Integer>();
		for (int j = 0; j <= maxBitPosition(mask); j++) {
			if (getBit(mask, j) > 0) {
				positions.add(j);
			}
		}
		return positions.iterator();
	}
	
	public int maxSize() {
		return maxBitPosition(Integer.MAX_VALUE);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o.getClass() != this.getClass()) {
			return false;
		}
		IntegerBitMask that = (IntegerBitMask) o;
		return this.mask == that.mask && this.size == that.size;
	}
	
	private static int getBit(long n, int k) {
	    return (int) (n >> k) & 1;
	}
		
	private static int maxBitPosition(long n) {
		if (n == 0) {
			return 0;
		} else {
			return (int) Logarithm.logb(Long.highestOneBit(n), 2);
		}
	}
	
	public static void main(String[] args) {
		checkOpenFromOtherIntegerBitMask(1024);
		checkOpenRandom(1000, 30);
	}

	private static void checkOpenRandom(int N, int maxSize) {
		for (int i = 0; i < N; i++) {
			int size = (int) (Math.random() * maxSize);
			IntegerBitMask A = new IntegerBitMask(size);
			TreeSet<Integer> B = new TreeSet<Integer>();
			for (int j = 0; j < size; j++) {
				int x = (int) (Math.random() * size);
				System.out.println("attempting to open position: " + x);
				A.open(x);
				B.add(x);
			}
			int j = 0;
			Object[] Barr = B.toArray();
			System.out.println("expected: " + Arrays.toString(Barr));
			System.out.println("produced: " + A);
			for (int x : A) {
				if ((Integer) Barr[j++] != x) {throw new IllegalArgumentException();};
			}
		}
		System.out.println("\npassed");
	}

	
	private static void checkOpenFromOtherIntegerBitMask(int N) {
		int size = maxBitPosition(N);
		for (int i = 0; i < N; i++) {
			System.out.println(i);
			IntegerBitMask A = new IntegerBitMask(size);
			A.mask = i;
			
			IntegerBitMask B = new IntegerBitMask(size);
			for (int j : A) {
				B.open(j);
			}

			System.out.println("expected: " + A);
			System.out.println("produced: " + B);
			assert A == B;
		}
		
		System.out.println("\npassed");
	}
}