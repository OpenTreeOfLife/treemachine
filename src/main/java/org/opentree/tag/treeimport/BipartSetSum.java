package org.opentree.tag.treeimport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.opentree.bitarray.TLongBitArraySet;

public class BipartSetSum implements Iterable<Bipartition> {
	
	private final List<Bipartition> originals;
	private boolean[] compatible;
	private Set<Bipartition> sum = new HashSet<Bipartition>();
	
	public BipartSetSum(List<Bipartition> biparts) {
		
		this.originals = biparts;
		
		// currently we don't use this but if it turns out we may be able to exclude
		// any original biparts that have been combined with others then we will
		this.compatible = new boolean[originals.size()];
		
		// for now we add all the original biparts to the set sum
		sum.addAll(originals);

		// and attempt to combine every pair
		for (int i = 0; i < originals.size(); i++) {
			for (int j = i+1; j < originals.size(); j++) {
				Bipartition b = originals.get(i).sum(originals.get(j));
				if (b != null && !sum.contains(b)) {
					sum.add(b);
					compatible[i] = true;
					compatible[j] = true;
				}
			}
		}
	}

	@Override
	public Iterator<Bipartition> iterator() {
		return sum.iterator();
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
		
		List<Bipartition> inSet = makeBipartList(in, out);
		HashSet<Bipartition> expected = new HashSet<Bipartition>(makeBipartList(inE, outE));
		for (Bipartition bi : inSet) {
			expected.add(bi);
		}
		
		BipartSetSum b = new BipartSetSum(inSet);
		Set<Bipartition> observed = new HashSet<Bipartition>();
		for (Bipartition bi : b) {
			observed.add(bi);
		}
		
		for (Bipartition o : observed) {
			boolean found = false;
			for (Bipartition e : expected) {
				if (o.equals(e)) { found = true; break; }
			}
			if (! found) {
				System.out.println(o + " not in expected set.");
				throw new AssertionError();
			}
		}

		for (Bipartition e : expected) {
			boolean found = false;
			for (Bipartition o : observed) {
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
		for (Bipartition bi : b) {
			System.out.println(bi);
		}
		System.out.println();
	}
	
	private static List<Bipartition> makeBipartList(long[][] ins, long[][] outs) {
		ArrayList<Bipartition> biparts = new ArrayList<Bipartition>();
		assert ins.length == outs.length;
		for (int i = 0; i < ins.length; i++) {
			biparts.add(makeBipart(ins[i],outs[i]));
		}
		return biparts;
	}
	
	private static Bipartition makeBipart(long[] ingroup, long[] outgroup) {
		return new Bipartition(new TLongBitArraySet(ingroup), new TLongBitArraySet(outgroup));
	}

}
