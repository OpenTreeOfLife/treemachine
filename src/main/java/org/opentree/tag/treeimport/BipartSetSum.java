package org.opentree.tag.treeimport;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.opentree.bitarray.TLongBitArraySet;

import static java.util.stream.Collectors.*;

public class BipartSetSum implements Iterable<TLongBipartition> {
	
	private TLongBipartition[] bipart;

	public BipartSetSum(Collection<TLongBipartition> original) {
		sum(original);
	}
	
	public BipartSetSum(TLongBipartition[] original) {

		Collection<TLongBipartition> b = new HashSet<TLongBipartition>();
		for (int i = 0; i < original.length; i++) {
			b.add(original[i]);
		}
		
		sum(b);
		
		/*
		// keep track of biparts compatible with others.
		// currently this is unused
		boolean[] compatible = new boolean[original.length];

		// combine every pair of compatible biparts
		Set<TLongBipartition> sumResults = new HashSet<TLongBipartition>();
		for (int i = 0; i < original.length; i++) {
			for (int j = i+1; j < original.length; j++) {
				TLongBipartition b = original[i].sum(original[j]);
				if (b != null) {
					sumResults.add(b);
					compatible[i] = true;
					compatible[j] = true;
				}
			}
		}

		// record the originals in the summed set. if we chose to exclude
		// originals that have been combined with others, it would happen here.
		for (int i = 0; i < original.length; i++) {
			sumResults.add(original[i]);
		}
		
		bipart = sumResults.toArray(new TLongBipartition[0]); */
	}

	private static Set<TLongBipartition> combineWithAll(TLongBipartition b, Collection<TLongBipartition> others) {
		Set<TLongBipartition> x =
				(others.parallelStream().map(a -> a.sum(b)).collect(toSet())) // sum this bipart against all others
					.stream().filter(r -> r != null).collect(toSet()); // and filter null entries from the results
		return x;
	}
	
	private void sum(Collection<TLongBipartition> original) {
		// sum all biparts against all others, and collect all the results into one set
		bipart = (TLongBipartition[]) original.parallelStream().map(b -> combineWithAll(b, original))
				.collect(() -> new HashSet(), (a, b) -> a.addAll(b), (a, b) -> a.addAll(b))
				.toArray(new TLongBipartition[0]);
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
		testNoOverlap();
		// need test for duplicates from equivalent overlapping
		testManyRandom();
	}
	
	private static void testManyRandom() {

		int maxId = 1000000;
		int count = 1000;
		int size = 100;
		
		Set<TLongBipartition> input = new HashSet<TLongBipartition>();
		
		for (int i = 0; i < count; i++) {
			TLongBitArraySet ingroup = new TLongBitArraySet ();
			TLongBitArraySet outgroup = new TLongBitArraySet ();
			while(ingroup.size() + outgroup.size() < size) {
				int id = (int) Math.round(Math.random() * maxId);
				if (! (ingroup.contains(id) || outgroup.contains(id))) {
					if (Math.random() > 0.5) { outgroup.add(id); } else { ingroup.add(id); };
				}
			}
			input.add(new TLongBipartition(new TLongBitArraySet(ingroup), new TLongBitArraySet(outgroup)));
		}
		System.out.println("start: " + Clock.systemUTC().instant());
		new BipartSetSum(input);
		System.out.println("stop: " + Clock.systemUTC().instant());
	}

	private static void testNoOverlap() {
		long[][] in = new long[][]   {{1,2},  {4,5},  {7,8}};
		long[][] out = new long[][]  {{3},    {6},    {9}};
		long[][] inE = new long[][]  {};
		long[][] outE = new long[][] {};
		test("simple no overlap: sum should only contain original biparts", in, out, inE, outE);
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
		long[][] inE = new long[][]  {};
		long[][] outE = new long[][] {};
		test("five symmetrical non overlap: sum should only contain original biparts", in, out, inE, outE);
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
		test("duplication - looking for more than one instance of any bipart", in, out, inE, outE);
	}

	private static void test(String name, long[][] in, long[][] out, long[][] inE, long[][] outE) {
		
		System.out.println("testing: " + name);
		
		List<TLongBipartition> inSet = makeBipartList(in, out);
		System.out.println("input:");
		for (TLongBipartition b : inSet) {
			System.out.println(b);
		}
		
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

		System.out.println("output:");
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
			biparts.add(new TLongBipartition(ins[i],outs[i]));
		}
		return biparts;
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
