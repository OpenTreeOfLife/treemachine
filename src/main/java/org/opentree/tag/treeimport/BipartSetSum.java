package org.opentree.tag.treeimport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

//import org.opentree.bitarray.TLongBitArraySet;


import org.opentree.bitarray.CompactLongSet;

import static java.util.stream.Collectors.*;

public class BipartSetSum implements Iterable<TLongBipartition> {
	
	private Set<TLongBipartition> bipart;

	public BipartSetSum(Collection<TLongBipartition> all) {
		bipart = sum(all, all); // sum all against all
	}
	
	public BipartSetSum(TLongBipartition[] original) {
		Collection<TLongBipartition> all = new HashSet<TLongBipartition>();
		for (int i = 0; i < original.length; i++) { all.add(original[i]); }

		// sum all against all
		bipart = sum(all, all);
	}
	
	/**
	 * The bipart sum can be performed more efficiently if some biparts are known to originate from
	 * the same tree, because no two biparts within a single tree may be completely overlapping. In
	 * this constructor, biparts from within a single tree can be supplied in groups corresponding
	 * to collections within a list. No biparts from within the same collection will be compared.
	 * 
	 * TODO for some reason this seems to be very slow, though it isn't clear why.
	 * 
	 * @param trees
	 */
	public BipartSetSum(List<Collection<TLongBipartition>> bipartsByTree) {
		Set<TLongBipartition> biparts = new HashSet<TLongBipartition>();
		for (int i = 0; i < bipartsByTree.size(); i++) {
			biparts.addAll(bipartsByTree.get(i)); // record the originals from group i
			for (int j = i+1; j < bipartsByTree.size(); j++) {
				biparts.addAll(sum(bipartsByTree.get(i), bipartsByTree.get(j))); // record the sums against group j
			}
		}
		bipart = biparts; //.toArray(new TLongBipartition[0]);
	}

	private static Set<TLongBipartition> combineWithAll(TLongBipartition b, Collection<TLongBipartition> others) {
		Set<TLongBipartition> x = (others.parallelStream()
				.map(a -> a.sum(b)).collect(toSet())).stream() // sum this bipart against all others
				.filter(r -> r != null).collect(toSet());      // and filter null entries from the results
		return x;
	}
	
	private Set<TLongBipartition> sum(Collection<TLongBipartition> A, Collection<TLongBipartition> B) {
		return A.parallelStream()
				.map(a -> combineWithAll(a, B)) // sum all biparts against all others, (returns a stream of sets)
				// and collect the contents of all the resulting sets into a single set
				.collect(() -> new HashSet<TLongBipartition>(), (x, y) -> x.addAll(y), (x, y) -> x.addAll(y));
	}

	@Override
	public Iterator<TLongBipartition> iterator() {
		return bipart.iterator();
	}
	
	public TLongBipartition[] toArray() {
		return bipart.toArray(new TLongBipartition[0]);
	}

	public Set<TLongBipartition> biparts() {
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
		testSimpleGroup();
//		testManyRandomAllByAll();
//		testManyRandomGroups();
	}
	
	private static void testManyRandomAllByAll() {

		int maxId = 1000000;
		int count = 1000;
		int size = 100; // 1000 x 1000 takes a long time. should check into pure bitset implementation
		
		Set<TLongBipartition> input = new HashSet<TLongBipartition>();
		
		for (int i = 0; i < count; i++) {
			CompactLongSet ingroup = new CompactLongSet ();
			CompactLongSet outgroup = new CompactLongSet ();
			while(ingroup.size() + outgroup.size() < size) {
				int id = (int) Math.round(Math.random() * maxId);
				if (! (ingroup.contains(id) || outgroup.contains(id))) {
					if (Math.random() > 0.5) { outgroup.add(id); } else { ingroup.add(id); };
				}
			}
			input.add(new TLongBipartition(new CompactLongSet(ingroup), new CompactLongSet(outgroup)));
		}
		System.out.println("attempting " + count + " random bipartitions of size " + size + " (ingroup + outgroup)");
		long z = new Date().getTime();
		new BipartSetSum(input);
		System.out.println("elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
	}

	private static void testManyRandomGroups() {

		int maxId = 1000000;	// maximum id. this affects the size of the underlying bitset(s)
		int n = 100;			// number of groups (e.g. trees, though for this test they are random so not trees)
		int count = 10;			// number of bipartitions per group
		int size = 500;			// size of bipartitions (if these were trees then the count and size would be the same)

		//  on macbook pro 2.5Ghz i7 x 8 cores, running in Eclipse debugger
		//
		//                         FastLongSet
		//                             |||
		//           TLongBitArraySet  |||
		//                       |||   |||
		//    n  count   size	 |||   |||
		//   60	    10     10    3.0   5.4
		//   70     10     10    3.9   7.1
		//   80     10     10    5.9   9.4
		//   90     10     10    8.2  11.8
		//  100     10     10   11.9  14.2
		//  100     20     10   22.0  50.4
		//  100     30     10   52.0
		//  100     40     10   92.0
		//  100     50     10  146.0
		//  100     10     20    7.6
		//  100     10     30    8.1 
		//  100     10     40    8.2
		//  100     10     50    8.6
		//  100     10    100         13.1
		//  100     10    200         20.8
		//  100     10    400         49.4
		//  100     10    500    ***  94.7
		//  100     10   1000  	 ***   ***
		//
		//  *** = out of memory, -Xmx16G
		
		List<Collection<TLongBipartition>> input = new ArrayList<Collection<TLongBipartition>>();
		
		for (int h = 0; h < n; h++) {
			HashSet<TLongBipartition> group = new HashSet<TLongBipartition>();
			for (int i = 0; i < count; i++) {
				CompactLongSet ingroup = new CompactLongSet ();
				CompactLongSet outgroup = new CompactLongSet ();
				while(ingroup.size() + outgroup.size() < size) {
					int id = (int) Math.round(Math.random() * maxId);
					if (! (ingroup.contains(id) || outgroup.contains(id))) {
						if (Math.random() > 0.5) { outgroup.add(id); } else { ingroup.add(id); };
					}
				}
				group.add(new TLongBipartition(new CompactLongSet(ingroup), new CompactLongSet(outgroup)));
			}
			input.add(group);
		}
		
		System.out.println("attempting " + n + " groups of "  + count + " random bipartitions (" + n*count + " total) of size " + size + " (ingroup + outgroup)");
		long z = new Date().getTime();
		new BipartSetSum(input);
		System.out.println("elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");
	}

	private static void testSimpleGroup() {
		long[][][] in = new long[][][]   {{{1,2  }, {1,2,3}, {1,2,3,4}}, {{1,9     }, {1,9,10}, {1,9,10,11}}};
		long[][][] out = new long[][][]  {{{3,4,5}, {4,5  }, {5      }}, {{10,11,12}, {11,12 }, {12       }}};
		testGroup("simple no overlap: sum should only contain original biparts", in, out);
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

	private static void testGroup(String name, long[][][] in, long[][][] out) {
		
		List<Collection<TLongBipartition>> inSetGrouped = makeBipartGroupList(in, out);
		Collection<TLongBipartition> inSet = new HashSet<TLongBipartition>();
		for (Collection<TLongBipartition> group : inSetGrouped) {
			inSet.addAll(group);
		}

		System.out.println("testing grouped against brute. answers should be the same. " + name);
		System.out.println("input:");
		int i = 0;
		for (Collection<TLongBipartition> set : inSetGrouped) {
			System.out.println("set: " + i++);
			for (TLongBipartition b : set) {
				System.out.println(b);
			}
		}

		Set<TLongBipartition> groupResult = new HashSet<TLongBipartition>();
		(new BipartSetSum(inSetGrouped)).forEach(a -> groupResult.add(a));
		
		Set<TLongBipartition> result = new HashSet<TLongBipartition>();
		(new BipartSetSum(inSet)).forEach(a -> result.add(a));
		
		System.out.println("group result:");
		for (TLongBipartition b: groupResult) {
			System.out.println(b);
		}		

		System.out.println("normal result:");
		for (TLongBipartition b: result) {
			System.out.println(b);
		}		

		for (TLongBipartition b: groupResult) {
			if (! result.contains(b)) {
				System.out.println("bipart: " + b + " in grouo result but missing from normal result");
				throw new AssertionError();
			}
		}

		for (TLongBipartition b: result) {
			if (! groupResult.contains(b)) {
				System.out.println("bipart: " + b + " in normal result but missing from group result");
				throw new AssertionError();
			}
		}
	}
	
	private static void test(String name, long[][] in, long[][] out, long[][] inE, long[][] outE) {

		Collection<TLongBipartition> inSet = new HashSet<TLongBipartition>(makeBipartList(inE, outE));
		Collection<TLongBipartition> expected = new HashSet<TLongBipartition>(makeBipartList(inE, outE));

		System.out.println("testing: " + name);
		System.out.println("input:");
		for (TLongBipartition b : inSet) {
			System.out.println(b);
			expected.add(b);
		}
		
		test(inSet, expected);
	}
	
	public static void test(Collection inSet, Collection<TLongBipartition> expected)  {

		BipartSetSum b = new BipartSetSum(inSet);
		
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
	
	private static List<Collection<TLongBipartition>> makeBipartGroupList(long[][][] ins, long[][][] outs) {
		ArrayList<Collection<TLongBipartition>> biparts = new ArrayList<Collection<TLongBipartition>>();
		assert ins.length == outs.length;
		for (int k = 0; k < ins.length; k++) {
			Collection<TLongBipartition> b = new ArrayList<TLongBipartition>();
			for (int i = 0; i < ins[k].length; i++) {
				b.add(new TLongBipartition(ins[k][i],outs[k][i]));
			}
			biparts.add(b);
		}
		return biparts;
	}

	/*
	private class ArrayIterator implements Iterator<TLongBipartition> {
		int i = 0;
		public ArrayIterator() { }
		@Override
		public boolean hasNext() {
			return i < bipart.size();
		}
		@Override
		public TLongBipartition next() {
			return bipart[i++];
		}
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	} */
}
