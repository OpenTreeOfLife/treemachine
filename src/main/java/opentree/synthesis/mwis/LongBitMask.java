package opentree.synthesis.mwis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import scala.actors.threadpool.Arrays;

public class LongBitMask implements BitMask {

	private long mask;
	private int size;

	public LongBitMask(int size) {
		if (size > maxSize()) {throw new IllegalArgumentException();}
		mask = 0;
		this.size = size;
	}
	
	public LongBitMask (LongBitMask original) {
		mask = original.mask;
		size = original.size;
	}

	public void open(int i) {
		if (i >= size()) {throw new IllegalArgumentException();};
		mask = mask | i2b(i);
	}
	
	public void close(int i) { // need to test this more
		if (i >= size()) {throw new IllegalArgumentException();};
		open(i);
		mask = mask ^ i2b(i);
	}
	
	public boolean isSet(int i) {
		return getBit(mask, i) == 1;
	}
	
	public int size() {
		return size;
	}
	
	public int openBits() {
		return Long.bitCount(mask);
	}

	/**
	 * Return the integer where the bit at position i is 1 and
	 * all other bits are zero.
	 */
	private long i2b(int i) {
		return (long) Math.pow(2, i);
	}
	
	@Override
	public String toString() {
		StringBuffer s = new StringBuffer();
		s.append("{");
		boolean first = true;
		for (int i : this) {
			if (first) {
				first = false;
			} else {
				s.append(", ");
			}
			s.append(i);
		}
		s.append("}");
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
		return maxBitPosition(Long.MAX_VALUE);
	}
	
	@Override
	public boolean equals(Object o) {
		if (o.getClass() != this.getClass()) {
			return false;
		}
		LongBitMask that = (LongBitMask) o;
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
		openAndCloseTest();
		basicLongBitTest();
		checkOpenFromOtherIntegerBitMask(1024);
		checkOpenRandom(1000, maxBitPosition(Long.MAX_VALUE) - 1);
	}
	
	private static void basicLongBitTest() {
		LongBitMask A = new LongBitMask(maxBitPosition(Long.MAX_VALUE));
		A.open(30);
		System.out.println(A);

		for (int i = 30; i < A.maxSize(); i++) {
			System.out.println("attempting to open " + i);
			A.open(i);
			System.out.println(A);
		}
	}
	
	private static void openAndCloseTest() {
		LongBitMask A = new LongBitMask(maxBitPosition(Long.MAX_VALUE));
		A.open(1);
		A.open(2);
		A.open(3);
		A.open(4);
		A.open(5);
		System.out.println(A);
		A.close(1);
		System.out.println(A);
		A.close(2);
		System.out.println(A);
		A.close(3);
		System.out.println(A);
		A.close(4);
		System.out.println(A);
		A.close(5);
		System.out.println(A);
	}

	private static void checkOpenRandom(int N, int maxSize) {
		for (int i = 0; i < N; i++) {
			int size = (int) (Math.random() * maxSize);
			LongBitMask A = new LongBitMask(size);
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
			LongBitMask A = new LongBitMask(size);
			A.mask = i;
			
			LongBitMask B = new LongBitMask(size);
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