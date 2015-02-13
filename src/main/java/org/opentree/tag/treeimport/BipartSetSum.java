package org.opentree.tag.treeimport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.opentree.bitarray.TLongBitArraySet;

public class BipartSetSum implements Iterable<TLongBipartition> {
	
	private final TLongBipartition[] bipart;
	
	public BipartSetSum(TLongBipartition[] original) {
		
		// keep track of biparts compatible with others.
		// in the future we may not keep these.
		boolean[] compatible = new boolean[original.length];

		// combine every pair of compatible biparts
		List<TLongBipartition> sumResults = new ArrayList<TLongBipartition>();
		for (int i = 0; i < original.length; i++) {
			for (int j = i+1; j < original.length; j++) {
				TLongBipartition b = original[i].sum(original[j]);
				if (b != null && !sumResults.contains(b)) {
					sumResults.add(b);
					compatible[i] = true;
					compatible[j] = true;
				}
			}
		}

		// record the originals in the summed set. if we chose to exclude
		// originals that have been combined with others, it would happen here.
		bipart = new TLongBipartition[original.length + sumResults.size()];
		for (int i = 0; i < original.length; i++) {
			bipart[i] = original[i];
		}

		// record the results of the sum operation
		for (int i = 0; i < sumResults.size(); i++) {
			bipart[i + original.length] = sumResults.get(i);
		}
	}

	@Override
	public Iterator<TLongBipartition> iterator() {
		return new ArrayIterator();
	}
	
	public TLongBipartition[] toArray() {
		return bipart;
	}
	
	public static void main(String[] args) {
		testSimpleConflict();
		testSimplePartialOverlap();
		testLargerOutgroup();
		testFiveSymmetrical();
		testDuplicateSum();
		testDuplicateInputs();
		// need test for duplicates from equivalent overlapping
	}

	private static void testSimplePartialOverlap() {
		long[][] in = new long[][]   {{1,3},     {1,5},   {1,3}};
		long[][] out = new long[][]  {{4},       {4},     {2}};
		long[][] inE = new long[][]  {{1, 3, 5}, {1, 3},  {1, 3, 5}};
		long[][] outE = new long[][] {{4}, 	     {2, 4},  {2, 4}};
		test("simple partial overlap", in, out, inE, outE);
	}

	private static void testLargerOutgroup() {
		long[][] in = new long[][]   {{2},   {3},   {1}};
		long[][] out = new long[][]  {{1,3,5,6,7}, {1,2,5,6,7}, {2,3,6,7}};
		long[][] inE = new long[][] {};
		long[][] outE = new long[][] {};
		test("bigger outgroups", in, out, inE, outE);
	}
	
	private static void testSimpleConflict() {
		long[][] in = new long[][]   {{1,2}, {1,3}, {2,3}};
		long[][] out = new long[][]  {{3},   {2},   {1}};
		long[][] inE = new long[][] {};
		long[][] outE = new long[][] {};
		test("simple conflict", in, out, inE, outE);
	}

	private static void testFiveSymmetrical() {
		long[][] in = new long[][]   {{1,2},  {3,4},  {5,6},  {7,8}, {9,10}};
		long[][] out = new long[][]  {{5,6},  {7,8},  {9,10}, {1,2}, {3,4}};
		long[][] inE = new long[][]  {{1,2,3,4}, {1,2,9,10}, {3,4,5,6},  {5,6,7,8},  {7,8,9,10}};
		long[][] outE = new long[][] {{5,6,7,8}, {3,4,5,6},  {7,8,9,10}, {1,2,9,10}, {1,2,3,4}};
		test("five symmetrical sets", in, out, inE, outE);
	}
	
	private static void testDuplicateSum() {
		long[][] in = new long[][]   {{1,2}, {3,4}, {1,3}, {2,4}};
		long[][] out = new long[][]  {{5},   {5},   {5},   {5}};
		long[][] inE = new long[][]  {{1,2,3,4}, {1,2,3}, {1,2,4}, {1,3,4}, {2,3,4}};
		long[][] outE = new long[][] {{5},       {5},     {5},     {5},     {5},};
		test("duplication - looking for 2 instances of {1,2,3,4} | {5}", in, out, inE, outE);
	}
	
	private static void testDuplicateInputs() {
		long[][] in = new long[][]   {{1}, {2}, {1}, {2}};
		long[][] out = new long[][]  {{5}, {5}, {5}, {5}};
		long[][] inE = new long[][]  {{1,2}};
		long[][] outE = new long[][] {{5}};
		test("duplication - looking for more than one instance of {1,2} | {5}", in, out, inE, outE);
	}

	private static void test(String name, long[][] in, long[][] out, long[][] inE, long[][] outE) {
		
		System.out.println("testing: " + name);
		
		List<TLongBipartition> inSet = makeBipartList(in, out);
		HashSet<TLongBipartition> expected = new HashSet<TLongBipartition>(makeBipartList(inE, outE));
		for (TLongBipartition bi : inSet) {
			expected.add(bi);
		}
		
		BipartSetSum b = new BipartSetSum(inSet.toArray(new TLongBipartition[0]));
		Set<TLongBipartition> observed = new HashSet<TLongBipartition>();
		for (TLongBipartition bi : b) {
			observed.add(bi);
		}
		
		for (TLongBipartition o : observed) {
			boolean found = false;
			for (TLongBipartition e : expected) {
				if (o.equals(e)) { found = true; break; }
			}
			if (! found) {
				System.out.println(o + " not in expected set.");
				throw new AssertionError();
			}
		}

		for (TLongBipartition e : expected) {
			boolean found = false;
			for (TLongBipartition o : observed) {
				if (o.equals(e)) { found = true; break; }
			}
			if (! found) {
				System.out.println(e + " not in expected set.");
				throw new AssertionError();
			}
		}
		
		if (observed.size() != expected.size()) {
			System.out.println("observed contains " + observed.size() + " but expected contains " + expected.size());
			throw new AssertionError();
		}

		printBipartSum(b);
	}
		
	private static void printBipartSum(BipartSetSum b) {
		for (TLongBipartition bi : b) {
			System.out.println(bi);
		}
		System.out.println();
	}
	
	private static List<TLongBipartition> makeBipartList(long[][] ins, long[][] outs) {
		ArrayList<TLongBipartition> biparts = new ArrayList<TLongBipartition>();
		assert ins.length == outs.length;
		for (int i = 0; i < ins.length; i++) {
			biparts.add(makeBipart(ins[i],outs[i]));
		}
		return biparts;
	}
	
	private static TLongBipartition makeBipart(long[] ingroup, long[] outgroup) {
		return new TLongBipartition(new TLongBitArraySet(ingroup), new TLongBitArraySet(outgroup));
	}

	private class ArrayIterator implements Iterator<TLongBipartition> {
		int i = 0;
		public ArrayIterator() { }
		@Override
		public boolean hasNext() {
			return i < bipart.length;
		}
		@Override
		public TLongBipartition next() {
			return bipart[i++];
		}
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}
