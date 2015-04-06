package org.opentree.tag.treeimport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

//import org.opentree.bitarray.TLongBitArraySet;


import org.opentree.bitarray.MutableCompactLongSet;

import static java.util.stream.Collectors.*;

public class BipartSetSum implements Iterable<LongBipartition> {
	
	private Set<LongBipartition> bipart;
	
	//crashes with parallel with 
	/*
	 * Exception in thread "main" java.lang.NullPointerException
        at java.util.HashMap$TreeNode.putTreeVal(HashMap.java:1970)
        at java.util.HashMap.putVal(HashMap.java:637)
        at java.util.HashMap.put(HashMap.java:611)
        at java.util.HashSet.add(HashSet.java:219)

	 */
	private static boolean USE_PARALLEL = false;

	public BipartSetSum(Collection<LongBipartition> all) {
		// first remove duplicates
		Collection<LongBipartition> filtered = new HashSet<LongBipartition>();
		for (LongBipartition b : all) { filtered.add(b); }

		bipart = sum(filtered, filtered); // sum all against all
		bipart.removeAll(filtered); // remove duplicates of the original biparts
	}
	
	public BipartSetSum(LongBipartition[] original) {
		// first remove duplicates
		Collection<LongBipartition> filtered = new HashSet<LongBipartition>();
		for (int i = 0; i < original.length; i++) { filtered.add(original[i]); }

		bipart = sum(filtered, filtered); // sum all against all
		bipart.removeAll(filtered); // remove duplicates of the original biparts
	}
	
	/**
	 * The bipart sum can be performed more efficiently if some biparts are known to originate from
	 * the same tree, because no two biparts within a single tree may be completely overlapping. In
	 * this constructor, biparts from within a single tree can be supplied in groups corresponding
	 * to collections within a list. No biparts from within the same collection will be compared.
	 * 
	 * @param trees
	 */
	public BipartSetSum(List<Collection<LongBipartition>> bipartsByTree) {

		// first filter the incoming set so no two groups contain any identical biparts -- a linear time operation
		System.out.print("removing duplicate bipartitions across/within groups...");
		long z = new Date().getTime();
		Set<LongBipartition> observedOriginals = new HashSet<LongBipartition>();
		List<Collection<LongBipartition>> filteredGroups = new ArrayList<Collection<LongBipartition>>();
		List<Collection<LongBipartition>> filteredRoots = new ArrayList<Collection<LongBipartition>>();
		int n = 0;
		int d = 0;
		for (int i = 0; i < bipartsByTree.size(); i++) {
			Collection<LongBipartition> filteredRoot = new ArrayList<LongBipartition>();
			Collection<LongBipartition> filteredCurTree = new ArrayList<LongBipartition>();
			for (LongBipartition b : bipartsByTree.get(i)) {
				if(b.outgroup().size()==0)
					filteredRoot.add(b);
				if (! observedOriginals.contains(b)) {
					filteredCurTree.add(b);
					observedOriginals.add(b);
					n++;
				} else {
					d++;
				}
			}
			filteredGroups.add(filteredCurTree);
			filteredRoots.add(filteredRoot);
		}
		observedOriginals = null; // free resource for garbage collector
		System.out.println(" done. found " + d + " duplicate biparts. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");

		System.out.print("now summing " + n + " unique biparts across " + filteredGroups.size() + " groups...");
		z = new Date().getTime();
		Set<LongBipartition> biparts = new HashSet<LongBipartition>();
		//for (int i = 0; i < filteredGroups.size(); i++) {
		for(int i=0; i<filteredRoots.size();i++){
//			biparts.addAll(filteredGroups.get(i)); // record the originals from group i
			for (int j = i+1; j < filteredGroups.size(); j++) {
				biparts.addAll(sum(filteredGroups.get(i), filteredGroups.get(j))); // record the sums against group j
			}
		}
		System.out.println(" done. elapsed time: " + (new Date().getTime() - z) / (float) 1000 + " seconds");

		bipart = biparts; //.toArray(new LongBipartition[0]);
		for (Collection<LongBipartition> b : bipartsByTree) {
			bipart.removeAll(b); // remove duplicates of the original biparts
		}
	}

	private static Set<LongBipartition> combineWithAll(LongBipartition b, Collection<LongBipartition> others) {

		Set<LongBipartition> x;

		if (USE_PARALLEL) {
			x = (others.parallelStream()
					.map(a -> a.sum(b)).collect(toSet())).stream() // sum this bipart against all others
					.filter(r -> r != null).collect(toSet());      // and filter null entries from the results
		} else { // sequential
			x = new HashSet<LongBipartition>();
			for (LongBipartition a : others) {
				LongBipartition s = a.sum(b);
				if (s != null) { x.add(s); }
			}
		}
		return x;
	}
	
	private Set<LongBipartition> sum(Collection<LongBipartition> A, Collection<LongBipartition> B) {
		
		Set<LongBipartition> z;

		if (USE_PARALLEL) {
			z = A.parallelStream()
					.map(a -> combineWithAll(a, B)) // sum all biparts against all others, (returns a stream of sets)
					// and collect the contents of all the resulting sets into a single set
					.collect(() -> new HashSet<LongBipartition>(), (x, y) -> x.addAll(y), (x, y) -> x.addAll(y));
		} else {
			z = new HashSet<LongBipartition>();
			for (LongBipartition a : A) {
				z.addAll(combineWithAll(a, B));
			}
		}
		return z;
	}

	@Override
	public Iterator<LongBipartition> iterator() {
		return bipart.iterator();
	}
	
	public LongBipartition[] toArray() {
		return bipart.toArray(new LongBipartition[0]);
	}

	public Set<LongBipartition> biparts() {
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
		testManyRandomGroups();
	}
		
	private static void testManyRandomAllByAll() {

		int maxId = 1000000;
		int count = 1000;
		int size = 100; // 1000 x 1000 takes a long time. should check into pure bitset implementation
		
		Set<LongBipartition> input = new HashSet<LongBipartition>();
		
		for (int i = 0; i < count; i++) {
			MutableCompactLongSet ingroup = new MutableCompactLongSet ();
			MutableCompactLongSet outgroup = new MutableCompactLongSet ();
			while(ingroup.size() + outgroup.size() < size) {
				int id = (int) Math.round(Math.random() * maxId);
				if (! (ingroup.contains(id) || outgroup.contains(id))) {
					if (Math.random() > 0.5) { outgroup.add(id); } else { ingroup.add(id); };
				}
			}
			input.add(new ImmutableLongBipartition(ingroup, outgroup));
		}
		System.out.println("attempting " + count + " random bipartitions of size " + size + " (ingroup + outgroup)");
		new BipartSetSum(input);
	}

	private static void testManyRandomGroups() {

		int maxId = 10;	// maximum id. this affects the size of the underlying bitset(s)
		int n = 1000;			// number of groups (e.g. trees, though for this test they are random so not trees)
		int count = 10;			// number of bipartitions per group
		int size = 10;			// size of bipartitions (if these were trees then the count and size would be the same)

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
		
		List<Collection<LongBipartition>> input = new ArrayList<Collection<LongBipartition>>();
		
		System.out.println("attempting to generate " + n + " groups of "  + count + " random bipartitions (" + n*count + " total) of size " + size + " (ingroup + outgroup)");
		for (int h = 0; h < n; h++) {
			HashSet<LongBipartition> group = new HashSet<LongBipartition>();
			for (int i = 0; i < count; i++) {
				MutableCompactLongSet ingroup = new MutableCompactLongSet ();
				MutableCompactLongSet outgroup = new MutableCompactLongSet ();
				while(ingroup.size() + outgroup.size() < size) {
					int id = (int) Math.round(Math.random() * maxId);
					if (! (ingroup.contains(id) || outgroup.contains(id))) {
						if (Math.random() > 0.5) { outgroup.add(id); } else { ingroup.add(id); };
					}
				}
				group.add(new ImmutableLongBipartition(new MutableCompactLongSet(ingroup), new MutableCompactLongSet(outgroup)));
			}
			input.add(group);
		}
		
		System.out.println("processing biparts for sum");
		new BipartSetSum(input);
	}

	private static void testSimpleGroup() {
		long[][][] in = new long[][][]   {{{1,2  }, {1,2,3}, {1,2,3,4}}, {{1,9     }, {1,9,10}, {1,9,10,11}}};
		long[][][] out = new long[][][]  {{{3,4,5}, {4,5  }, {5      }}, {{10,11,12}, {11,12 }, {12       }}};
		testGroup("simple no overlap with groups: sum should be empty", in, out);
	}

	
	private static void testNoOverlap() {
		long[][] in = new long[][]   {{1,2},  {4,5},  {7,8}};
		long[][] out = new long[][]  {{3},    {6},    {9}};
		long[][] inE = new long[][]  {};
		long[][] outE = new long[][] {};
		test("simple no overlap: sum should be empty", in, out, inE, outE);
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
		test("five symmetrical non overlap: sum should be empty", in, out, inE, outE);
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
		
		List<Collection<LongBipartition>> inSetGrouped = makeBipartGroupList(in, out);
		Collection<LongBipartition> inSet = new HashSet<LongBipartition>();
		for (Collection<LongBipartition> group : inSetGrouped) {
			inSet.addAll(group);
		}

		System.out.println("testing grouped against brute. answers should be the same. " + name);
		System.out.println("input:");
		int i = 0;
		for (Collection<LongBipartition> set : inSetGrouped) {
			System.out.println("set: " + i++);
			for (LongBipartition b : set) {
				System.out.println(b);
			}
		}

		Set<LongBipartition> groupResult = new HashSet<LongBipartition>();
		(new BipartSetSum(inSetGrouped)).forEach(a -> groupResult.add(a));
		
		Set<LongBipartition> result = new HashSet<LongBipartition>();
		(new BipartSetSum(inSet)).forEach(a -> result.add(a));
		
		System.out.println("group result:");
		for (LongBipartition b: groupResult) {
			System.out.println(b);
		}		

		System.out.println("normal result:");
		for (LongBipartition b: result) {
			System.out.println(b);
		}		

		for (LongBipartition b: groupResult) {
			if (! result.contains(b)) {
				System.out.println("bipart: " + b + " in group result but missing from normal result");
				throw new AssertionError();
			}
		}

		for (LongBipartition b: result) {
			if (! groupResult.contains(b)) {
				System.out.println("bipart: " + b + " in normal result but missing from group result");
				throw new AssertionError();
			}
		}
	}
	
	private static void test(String name, long[][] in, long[][] out, long[][] inE, long[][] outE) {

		Collection<LongBipartition> inSet = new HashSet<LongBipartition>(makeBipartList(in, out));
		Collection<LongBipartition> expected = new HashSet<LongBipartition>(makeBipartList(inE, outE));

		System.out.println("testing: " + name);
		System.out.println("input:");
		for (LongBipartition b : inSet) {
			System.out.println(b);
//			expected.add(b);
		}
 		
/*		test(inSet, expected);
	}
	
	public static void test(Collection inSet, Collection<LongBipartition> expected)  { */

		BipartSetSum b = new BipartSetSum(inSet);
		
		System.out.println("output:");
		for (LongBipartition bi : b) {
			System.out.println(bi);
		}
		
		Set<LongBipartition> observed = new HashSet<LongBipartition>();
		for (LongBipartition bi : b) {
			observed.add(bi);
		}
		
		for (LongBipartition o : observed) {
			boolean found = false;
			for (LongBipartition e : expected) {
				if (o.equals(e)) { found = true; break; }
			}
			if (! found) {
				System.out.println(o + " not in expected set.");
				throw new AssertionError();
			}
		}

		for (LongBipartition e : expected) {
			boolean found = false;
			for (LongBipartition o : observed) {
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
	}

	/*
	private static void printBipartSum(BipartSetSum b) {
		for (LongBipartition bi : b) {
			System.out.println(bi);
		}
		System.out.println();
	} */
	
	private static List<LongBipartition> makeBipartList(long[][] ins, long[][] outs) {
		ArrayList<LongBipartition> biparts = new ArrayList<LongBipartition>();
		assert ins.length == outs.length;
		for (int i = 0; i < ins.length; i++) {
			biparts.add(new ImmutableLongBipartition(ins[i],outs[i]));
		}
		return biparts;
	}
	
	private static List<Collection<LongBipartition>> makeBipartGroupList(long[][][] ins, long[][][] outs) {
		ArrayList<Collection<LongBipartition>> biparts = new ArrayList<Collection<LongBipartition>>();
		assert ins.length == outs.length;
		for (int k = 0; k < ins.length; k++) {
			Collection<LongBipartition> b = new ArrayList<LongBipartition>();
			for (int i = 0; i < ins[k].length; i++) {
				b.add(new ImmutableLongBipartition(ins[k][i], outs[k][i]));
			}
			biparts.add(b);
		}
		return biparts;
	}

	/*
	private class ArrayIterator implements Iterator<LongBipartition> {
		int i = 0;
		public ArrayIterator() { }
		@Override
		public boolean hasNext() {
			return i < bipart.size();
		}
		@Override
		public LongBipartition next() {
			return bipart[i++];
		}
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	} */
}
