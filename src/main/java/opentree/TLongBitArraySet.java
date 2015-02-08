package opentree;

import gnu.trove.list.array.TLongArrayList;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * A container class combining the features of TLongArrayList and BitSet.
 * DOES NOT ALLOW DUPLICATES, AND PROVIDES ITERATOR IN SORTED ORDER.
 * @author cody hinchliff
 */
public class TLongBitArraySet implements Iterable<Long> {

	private static boolean testing = false; // set to true when performing unit tests 
	
	TLongArrayList tl; 
	BitSet bs;
	
	// ==== constructors
	
	public TLongBitArraySet(Iterable<Long> longArr) {
		tl = new TLongArrayList();
		bs = new BitSet();
		this.addAll(longArr);
	}

	public TLongBitArraySet(int[] intArr) {
		tl = new TLongArrayList();
		bs = new BitSet();
		this.addAll(intArr);
	}
	
	public TLongBitArraySet(long[] longArr) {
		tl = new TLongArrayList();
		bs = new BitSet();
		this.addAll(longArr);
	}

	public TLongBitArraySet(TLongArrayList tLongArr) {
		tl = new TLongArrayList();
		bs = new BitSet();
		this.addAll(tLongArr);
	}
	
	public TLongBitArraySet(BitSet bs) {
		tl = new TLongArrayList();
		this.bs = bs;
		this.addAll(bs);
	}
	
	public TLongBitArraySet() {
		tl = new TLongArrayList();
		bs = new BitSet();
	}

	// ==== basic functions
	
	public BitSet getBitSet() {
		return bs;
	}
	
	public int size() {
		if (testing) {testInternalState();}
		return tl.size();
	}

	public int cardinality() {
		return bs.cardinality();
	}
	
	public Long get(int i) {
		if (testing) {testInternalState();}
		return tl.get(i);
	}

	public Long getQuick(int i) {
		if (testing) {testInternalState();}
		return tl.getQuick(i);
	}
	
	public long[] toArray() {
		if (testing) {testInternalState();}
		return tl.toArray();
	}
	
	public void sort() {
		if (testing) {testInternalState();}
		tl.sort();
	}
	
	public boolean contains(Long l) {
		if (testing) {testInternalState();}
		return bs.get(l.intValue());
	}

	public boolean contains(int i) {
		if (testing) {testInternalState();}
		return bs.get(i);
	}
	
	// ==== any operation that changes the underlying TLongArray instance must trigger an update to the BitSet

	// == addition methods
	
	/**
	 * Adds the value to the array and the bitset. For adding more than one value, the use of addAll() methods is preferred,
	 * as they are more efficient than using add() on multiple individual values.	 * @param l
	 */
	public void add(Long l) {
		int i = l.intValue();
		add(i);
	}

	/**
	 * Adds the value to the array and the bitset. For adding more than one value, the use of addAll() methods is preferred,
	 * as they are more efficient than using add() on multiple individual values.
	 * @param l
	 */
	public void add(int i) {
		if (bs.get(i) == false) {
			tl.add(i);
			bs.set(i, true);
		}
	}

	/**
	 * Add all the values to the array and the bitset.
	 * @param toAdd
	 */
	public void addAll(int[] toAdd) {
		for (int i : toAdd) {
			add(i);
//			if (! tl.contains(i)) {
//				tl.add(i);
//			}
		}
//		updateBitSet();
	}
	
	/**
	 * Add all the values to the array and the bitset.
	 * @param toAdd
	 */
	public void addAll(long[] toAdd) {
		for (long l : toAdd) {
			add(l);
/*			if (! tl.contains(l)) {
				tl.add(l);
			} */
		}
//		updateBitSet();
	}
	
	/**
	 * Add all the values to the array and the bitset.
	 * @param toAdd
	 */
	public void addAll(Iterable<Long> toAdd) {
		for (Long l : toAdd) {
			add(l);
/*			if (! tl.contains(l)) {
				tl.add(l);
			} */
		}
//		updateBitSet();
	}
	
	/**
	 * Add all the values to the array and the bitset.
	 * @param toAdd
	 */
	public void addAll(BitSet toAdd) {
		for (int i = toAdd.nextSetBit(0); i >= 0; i = toAdd.nextSetBit(i+1)) {
			if (! tl.contains(i)) {
				tl.add(i);
				bs.set(i, true);
			}
		}
//		updateBitSet();
	}

	/**
	 * Add all the values to the array and the bitset.
	 * @param toAdd
	 */
	public void addAll(TLongArrayList toAdd) {
		for (int i = 0; i < toAdd.size(); i++) {
			long l = toAdd.get(i);
			add(l);
/*			if (! tl.contains(l)) {
				tl.addAll(toAdd);
			} */
		}
//		updateBitSet();
	}

	/**
	 * Add all the values to the array and the bitset.
	 * @param toAdd
	 */
	public void addAll(TLongBitArraySet toAdd) {
		this.addAll((Iterable<Long>) toAdd); // use the iterator method
	}
	
	// == removal methods
	
	/**
	 * Remove the value from the array. If this is the last instance of this value in the array, it will alsoe be removed from
	 * the bitset. When removing multiple values, the use of removeAll() is preferred, as it only requires a single update to
	 * the bitset.
	 * @param l
	 */
	public void remove(Long l) {
		tl.remove(l);
		for (Long val : this) {
			if (l == val) {
				return;
			}
		} // if we removed the last copy of this value, then remove it from the bitset
		bs.set(l.intValue(), false);
	}
	
	/**
	 * Remove the value from the array. If this is the last instance of this value in the array, it will alsoe be removed from
	 * the bitset. When removing multiple values, the use of removeAll() is preferred, as it only requires a single update to
	 * the bitset.
	 * @param i
	 */
	public void remove(int i) {
		tl.remove((long) i);
		for (Long val : this) {
			if (i == val) {
				return;
			}
		} // if we removed the last copy of this value, then remove it from the bitset
		bs.set(i, false);
	}

	/**
	 * Remove all the values to the array and the bitset.
	 * @param toRemove
	 */
	public void removeAll(int[] toRemove) {
		for (int i : toRemove) {
//			tl.remove(i);
			while(tl.remove(i)==true) {
				continue;
			}
		}
		updateBitSet();
	}
	
	/**
	 * Remove all the values to the array and the bitset.
	 * @param toRemove
	 */
	public void removeAll(long[] toRemove) {
		for (long l : toRemove) {
			while(tl.remove(l)==true) {
				continue;
			}
		}
		updateBitSet();
	}
	
	/**
	 * Remove all the values to the array and the bitset.
	 * @param toRemove
	 */
	public void removeAll(Iterable<Long> toRemove) {
		for (Long l : toRemove) {
			while(tl.remove(l)==true) {
				continue;
			}
		}
		updateBitSet();
	}

	/**
	 * Remove all the values to the array and the bitset.
	 * @param toRemove
	 */
	public void removeAll(BitSet toRemove) {
		for (int i = toRemove.nextSetBit(0); i >= 0; i = toRemove.nextSetBit(i+1)) {
//			tl.remove(i);
			while(tl.remove(i)==true) {
				continue;
			}
		}
		updateBitSet();
	}
	
	/**
	 * Remove all the values to the array and the bitset.
	 * @param toRemove
	 */
	public void removeAll(TLongArrayList toRemove) {
		while(tl.removeAll(toRemove) == true) {
			continue;
		}
		updateBitSet();
	}

	/**
	 * Remove all the values to the array and the bitset.
	 * @param toRemove
	 */
	public void removeAll(TLongBitArraySet toRemove) {
		this.removeAll((Iterable<Long>) toRemove); // use the iterator method
		updateBitSet();
	}
	
	/**
	 * Rebuild the bitset based on the array. Currently just creates a new bitset and fills it from the array.
	 * This could be optimized further but it is fairly fast.
	 */
	public void updateBitSet() {
		if (tl.size() > 0) {
			bs = new BitSet((int) tl.max());
			for (int i = 0; i < tl.size(); i++) {
				bs.set((int) tl.getQuick(i));
			}
		} else {
			bs = new BitSet();
		}
		if (testing) {testInternalState();}
	}
	
	// ==== boolean / bitwise operations

	/**
	 * Perfoms a binary andNot on the internal BitSet against the passed BitSet and returns a new TLongBitArray containing the result.
	 * Does not modify the internal or the passed BitSet.
	 * @param compBS
	 * @return
	 */
	public TLongBitArraySet andNot(BitSet compBS) {
		if (testing) {testInternalState();}
		BitSet result = (BitSet) this.bs.clone();
		result.andNot(compBS);
		return new TLongBitArraySet(result);
	}
	
	/**
	 * Perfoms a binary andNot on the internal BitSet against the passed TLongBitArray and returns a new TLongBitArray containing the result.
	 * Does not modify the internal or the passed BitSet.
	 * @param compArr
	 * @return
	 */
	public TLongBitArraySet andNot(TLongBitArraySet compArr) {
		if (testing) {testInternalState();}
		return new TLongBitArraySet(this.andNot(compArr.bs));
	}
	
	/**
	 * Returns true if and only if this TLongBitArray contains any values from the passed TLongBitArray.
	 * @param compArr
	 * @return
	 */
	public boolean containsAny(TLongBitArraySet compArr) {
		if (testing) {testInternalState();}
		return this.containsAny(compArr.getBitSet());
	}

	/**
	 * Returns true if and only if this TLongBitArray contains any values set to true in the passed BitSet.
	 * @param compArr
	 * @return
	 */
	public boolean containsAny(BitSet compBS) {
		if (testing) {testInternalState();}
		return bs.intersects(compBS);
	}

	/**
	 * Returns true if and only if this TLongBitArray contains all the from the passed TLongBitArray.
	 * @param compArr
	 * @return
	 */
	public boolean containsAll(TLongBitArraySet compArr) {
		if (testing) {testInternalState();}
		return this.containsAll(compArr.bs);
	}

	/**
	 * Returns true if and only if this TLongBitArray contains all the values set to true in the passed BitSet.
	 * @param compArr
	 * @return
	 */
	public boolean containsAll(BitSet compBS) {
		if (testing) {testInternalState();}
		BitSet intersection = (BitSet) compBS.clone();
		intersection.and(bs);
		if (intersection.cardinality() == compBS.cardinality()) { 
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Returns a TLongBitArray containing the values that are both in this TLongBitArray and the passed TLongBitArray.
	 * @param compArr
	 * @return
	 */
	public TLongBitArraySet getIntersection(TLongBitArraySet compArr) {
		if (testing) {testInternalState();}
		BitSet intersection = (BitSet) bs.clone();
		intersection.and(compArr.bs);
		return new TLongBitArraySet(intersection);
	}

	/**
	 * Returns a TLongBitArray containing the values that are both in this TLongBitArray and are set to true in the passed BitSet.
	 * @param compArr
	 * @return
	 */
	public TLongBitArraySet getIntersection(BitSet compBS) {
		if (testing) {testInternalState();}
		BitSet intersection = (BitSet) bs.clone();
		intersection.and(compBS);
		return new TLongBitArraySet(intersection);
	}
	
	@Override
	public String toString() {
		StringBuffer s = new StringBuffer();
		s.append("["+tl.get(0));
		for (int i = 1; i < tl.size(); i++) {
			s.append(",");
			s.append(tl.get(i));
		}
		s.append("]");
		return s.toString();
	}
	
	// ==== internal methods
	
	/**
	 * Returns an iterator over the values from this TLongBitArray.
	 */
	@Override
	public Iterator<Long> iterator() {
		return new TLongBitSetIterator(tl);
	}
	
	private class TLongBitSetIterator implements Iterator<Long> {

		int i;
		long[] arr;

		public TLongBitSetIterator(TLongArrayList tl) {
			i = 0;
			this.arr = tl.toArray();
			Arrays.sort(arr);
		}
		
		@Override
		public boolean hasNext() {
			if (i < arr.length) {
				return true;
			} else {
				return false;
			}
		}

		@Override
		public Long next() {
			return arr[i++];
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("");
		}
	}
	
	/**
	 * Validates that the bitset and long array are congruent. If not then there is an error somewhere. Used during unit tests.
	 */
	private void testInternalState() {
	
		int[] bsVals = new int[bs.cardinality()];
		int j = 0;
		for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
			bsVals[j++] = i;
		}
		tl.sort();
		for (int k : bsVals) {
			if (tl.contains(k) == false) {
				System.out.println("Sanity check failed.");
				System.out.println("BitSet: " + Arrays.toString(bsVals));
				System.out.println("TLongArrayList: " + Arrays.toString(tl.toArray()));
				throw new java.lang.AssertionError("The internal BitSet contains " + k + " but the Long array does not");
			}
		}
		for (int k = 0; k < tl.size(); k++) {
			if (bs.get(Long.valueOf(tl.get(k)).intValue()) != true) {
				System.out.println("Sanity check failed.");
				System.out.println("BitSet: " + Arrays.toString(bsVals));
				System.out.println("TLongArrayList: " + Arrays.toString(tl.toArray()));
				throw new java.lang.AssertionError("The internal Long array contains " + tl.getQuick(k) + " but the BitSet does not");
			}
		}
	}
	
	/**
	 * Thorough testing for construction, adding elements, removing elements, and doing bitwise/binary operations.
	 * @param args (unused)
	 */
	public static void main(String[] args) {

		testing = true;
		
		int numTestCycles = 0;
		if (args.length == 1) {
			numTestCycles = Integer.valueOf(args[0]);
		} else {
			throw new java.lang.IllegalArgumentException("you must indicate the number of test cycles to perform");
		}

		// run the tests
		Random r = new Random();
		boolean allTestsPassed = false;
		for (int i = 0; i < numTestCycles; i++) {
			System.out.println("\nTest cycle " + i);
			allTestsPassed  = runUnitTests(r.nextInt(Integer.MAX_VALUE));
		}
		
		if (allTestsPassed) {
			System.out.println("\nAll tests passed\n");
		} else {
			System.out.println("\nTests failed\n");
		}
	}
	
	private static boolean runUnitTests(int randSeed) {
		int maxVal = 100;
		Random r = new Random(randSeed);

		// create a random test arrays of ints
		int n1 = r.nextInt(20);
		int[] arr1 = new int[n1];
		for (int i = 0; i < arr1.length; i++) {
			arr1[i] = r.nextInt(maxVal);
		}
		
		System.out.println("\nThe first array is: " + Arrays.toString(arr1));
		
		// test setting and getting
		System.out.println("Testing adding and getting");
		TLongBitArraySet test1 = new TLongBitArraySet();
		for (int k : arr1) {
			test1.add(k);
		}
		test1.updateBitSet();
		test1.testInternalState();
		TLongBitArraySet test2 = new TLongBitArraySet();
		for (int k : arr1) {
			if (test1.contains(k) == false) {
				throw new AssertionError("Adding and getting failed. BitArray 1 should have contained " + k + " but it did not");
			} else {
				test2.add(k);
			}
		}
		test1 = new TLongBitArraySet(arr1);
		for (Long l : test2) {
			if (test1.contains(l) == false) {
				throw new AssertionError("Adding and getting failed. BitArray 1 should have contained " + l + " but it did not");
			}
		}
		System.out.println("Adding and getting passed\n");
		
		// instantiating a BitArray from a BitSet
		System.out.println("Testing BitArray creation from BitSet");
		BitSet testBS1 = new BitSet();
		for (int i : arr1) {
			testBS1.set(i, true);
		}
		int[] bs1Vals = new int[testBS1.cardinality()];
		int j = 0;
		for (int i = testBS1.nextSetBit(0); i >= 0; i = testBS1.nextSetBit(i+1)) {
		     bs1Vals[j++] = i;
		}
		System.out.println("the BitSet contains " + testBS1.cardinality() + " values: " + Arrays.toString(bs1Vals));
		test1 = new TLongBitArraySet(testBS1);
		System.out.println("The BitArray constructed from the BitSet contains: " + Arrays.toString(test1.toArray()));
		j = 0;
		for (Long l : test1) {
			if (bs1Vals[j++] != l.intValue()) {
				throw new java.lang.AssertionError("BitArray creation from BitSet failed. Position " + j + " should have been " + arr1[j] + " but it was " + test1.get(j));
			}
		}
		test1.testInternalState();
		System.out.println("BitArray construction from BitSet passed\n");

		// instantiating a BitArray from a TLongArrayList
		System.out.println("Testing BitArray construction from TLongArrayList");
		TLongArrayList testTL = new TLongArrayList();
		for (int i : arr1) {
			testTL.add(i);
		}
		System.out.println("the TLongArrayList contains " + testTL.size() + " values: " + Arrays.toString(testTL.toArray()));
		test1 = new TLongBitArraySet(testTL);
		System.out.println("The BitArray constructed from the TLongArrayList contains: " + Arrays.toString(test1.toArray()));
		Arrays.sort(arr1); // has to be on because testInternalState calls sort the bitarray
		HashSet<Integer> uniqueInts = new HashSet<Integer>();
		for (int k = 0; k < arr1.length; k++) {
			uniqueInts.add(arr1[k]);
			if (! test1.contains(arr1[k])) {
				throw new java.lang.AssertionError("BitArray creation from TLongArrayList failed");
			}
		}
		if (! (test1.size() == uniqueInts.size())) {
			throw new java.lang.AssertionError("BitArray creation from long array failed");
		}
		test1.testInternalState();
		System.out.println("BitArray construction from TLongArrayList passed\n");

		// test instantiating a BitArray from another BitArray
		System.out.println("Testing BitArray construction from another BitArray");
		test2 = new TLongBitArraySet(arr1);
		System.out.println("The starting BitArray contains " + test2.size() + " values: " + Arrays.toString(test2.toArray()));
		test1 = new TLongBitArraySet(test2);
		System.out.println("The BitArray constructed from the starting BitArray contains: " + Arrays.toString(test1.toArray()));
		uniqueInts = new HashSet<Integer>();
		for (int k = 0; k < arr1.length; k++) {
			uniqueInts.add(arr1[k]);
			if (! test1.contains(arr1[k])) {
				throw new java.lang.AssertionError("BitArray creation from BitArray failed");
			}
		}
		if (! (test1.size() == uniqueInts.size())) {
			throw new java.lang.AssertionError("BitArray creation from long array failed");
		}
		test1.testInternalState();
		System.out.println("BitArray construction from BitArray passed\n");

		// instantiating a BitArray from a long array
		System.out.println("Testing BitArray construction from long array primitive");
		long[] testArrLong = new long[r.nextInt(20)];
		for (int k = 0; k < testArrLong.length; k++) {
			testArrLong[k] = r.nextInt(maxVal);
		}
		System.out.println("The long array contains " + testArrLong.length + " values: " + Arrays.toString(testArrLong));
		test1 = new TLongBitArraySet(testArrLong);
		System.out.println("The BitArray constructed from the TLongArrayList contains: " + Arrays.toString(test1.toArray()));
		Arrays.sort(testArrLong); // has to be on because testInternalState calls sort the bitarray
		HashSet<Long> uniqueLongs = new HashSet<Long>();
		for (int k = 0; k < testArrLong.length; k++) {
			uniqueLongs.add(testArrLong[k]);
			if (! test1.contains(testArrLong[k])) {
				throw new java.lang.AssertionError("BitArray creation from long array failed");
			}
		}
		if (! (test1.size() == uniqueLongs.size())) {
			throw new java.lang.AssertionError("BitArray creation from long array failed");
		}
		test1.testInternalState();
		System.out.println("BitArray construction from long array primitive passed\n");
		
		// instantiating a BitArray from a long array
		System.out.println("Testing BitArray construction from int array primitive");
		testArrLong = new long[r.nextInt(20)];
		for (int k = 0; k < testArrLong.length; k++) {
			testArrLong[k] = r.nextInt(maxVal);
		}
		System.out.println("The int array contains " + testArrLong.length + " values: " + Arrays.toString(testArrLong));
		test1 = new TLongBitArraySet(testArrLong);
		System.out.println("The BitArray constructed from the TLongArrayList contains: " + Arrays.toString(test1.toArray()));
		Arrays.sort(testArrLong); // has to be on because testInternalState calls sort the bitarray
		uniqueLongs = new HashSet<Long>();
		for (int k = 0; k < testArrLong.length; k++) {
			uniqueLongs.add(testArrLong[k]);
			if (! test1.contains(testArrLong[k])) {
				throw new java.lang.AssertionError("BitArray creation from int array failed");
			}
		}
		if (! (test1.size() == uniqueLongs.size())) {
			throw new java.lang.AssertionError("BitArray creation from long array failed");
		}
		test1.testInternalState();
		System.out.println("BitArray construction from int array primitive passed\n");
		
		// testing BitSet updating
		System.out.println("Testing removal from the BitArray");
		System.out.println("BitArray contains: " + Arrays.toString(test1.toArray()));
		System.out.println("Removing values: " + Arrays.toString(testArrLong));
		test1.removeAll(testArrLong);
		test1.testInternalState();
		for (int k = 0; k < testArrLong.length; k++) {
			if (test1.contains(testArrLong[k])) {
				throw new java.lang.AssertionError("BitArray removal failed, still contains " + testArrLong[k]);
			}
		}
		if (test1.size() > 0) {
			System.out.println("Array should be empty, but it still contains: " + Arrays.toString(test1.toArray()));
			throw new java.lang.AssertionError("BitArray removal failed");
		}
		if (test1.bs.cardinality() > 0) {
			throw new AssertionError("BitArray is empty, but its BitSet still contains " + test1.bs.cardinality() + " values");
		} else {
			System.out.println("BitArray is empty");
		}
		System.out.println("BitArray removal passed\n");
		
		// creating arrays to test intersection
		maxVal = 10;
		n1 = r.nextInt(20);
		arr1 = new int[n1];
		for (int i = 0; i < arr1.length; i++) {
			arr1[i] = r.nextInt(maxVal);
		}
		int n2 = r.nextInt(20);
		int[] arr2 = new int[n2];
		for (int i = 0; i < arr2.length; i++) {
			arr2[i] = r.nextInt(maxVal);
		}

		// replace testBS1
		testBS1 = new BitSet();
		for (int i : arr1) {
			testBS1.set(i, true);
		}
		
		// making a new BitSet with arr2 values
		BitSet testBS2 = new BitSet();
		for (int i : arr2) {
			testBS2.set(i, true);
		}
		
		// testing intersection
		System.out.println("Testing intersection with BitSet. Finding intersection of:");
		System.out.println(Arrays.toString(arr1));
		System.out.println(Arrays.toString(arr2));
		testBS1.and(testBS2);
		int[] bsVals = new int[testBS1.cardinality()];
		j = 0;
		for (int k = testBS1.nextSetBit(0); k >= 0; k = testBS1.nextSetBit(k+1)) {
			bsVals[j++] = k;
		}
		System.out.println("Intersection should be: " + Arrays.toString(bsVals));
		
		test1 = new TLongBitArraySet(arr1);
		System.out.println("Intersecting BitArray: " + Arrays.toString(test1.toArray()));
		System.out.println("with BitSet containing: " + Arrays.toString(arr2));

		// making a new BitSet with arr2 values
		testBS2 = new BitSet();
		for (int i : arr2) {
			testBS2.set(i, true);
		}
		TLongBitArraySet intersection = test1.getIntersection(testBS2);

		System.out.println("Intersection is: " + Arrays.toString(intersection.toArray()));
		for (Long l : intersection) {
			if (testBS1.get(l.intValue()) != true) {
				throw new java.lang.AssertionError("Intersection failed: value " + l + " is present but should not be.");
			}
		}
		for (int k = testBS1.nextSetBit(0); k >= 0; k = testBS1.nextSetBit(k+1)) {
			if (intersection.contains(k) != true) {
				throw new java.lang.AssertionError("Intersection failed: value " + k + " should be present but is not.");
			}
		}
		System.out.println("Intersection with BitSet passed\n");

		System.out.println("Testing intersection with BitArray:");
		test2 = new TLongBitArraySet(arr2);
		intersection = test1.getIntersection(test2);
		System.out.println("Intersection is: " + Arrays.toString(intersection.toArray()));
		for (Long l : intersection) {
			if (testBS1.get(l.intValue()) != true) {
				throw new java.lang.AssertionError("Intersection failed: value " + l + " is present but should not be.");
			}
		}
		for (int k = testBS1.nextSetBit(0); k >= 0; k = testBS1.nextSetBit(k+1)) {
			if (intersection.contains(k) != true) {
				throw new java.lang.AssertionError("Intersection failed: value " + k + " should be present but is not.");
			}
		}
		System.out.println("Intersection with BitArray passed\n");

		// replace testBS1
		testBS1 = new BitSet();
		for (int i : arr1) {
			testBS1.set(i, true);
		}
		
		System.out.println("Testing containsAll");
		System.out.println("BitArray 1 contains: " + Arrays.toString(test1.toArray()));
		System.out.println("BitArray 2 contains: " + Arrays.toString(test2.toArray()));
		
		boolean containsAll1;
		boolean bsContainsAll1;
		int cardinalityBeforeAnd;
		if (test2.size() > 0) {
			containsAll1 = test1.containsAll(test2);
			System.out.println("BitArray 1 contains all of BitArray 2? " + containsAll1);
			cardinalityBeforeAnd = testBS2.cardinality();
			testBS2.and(testBS1);
			bsContainsAll1 = cardinalityBeforeAnd == testBS2.cardinality();
			System.out.println("test BitSet 1 contains all of test BitSet 2? " + bsContainsAll1);
			if (bsContainsAll1 != containsAll1) {
				throw new AssertionError("Contains all failed.");
			}
			System.out.println("Passed contains all test 1");
		} else {
			try {
				containsAll1 = test1.containsAll(test2);
			} catch (NoSuchElementException ex) {
				System.out.println(ex.getMessage() + " (NoSuchElementException thrown correctly)");
			}
		}
			
		// replace testBS2
		testBS2 = new BitSet();
		for (int i : arr2) {
			testBS2.set(i, true);
		}
		
		boolean containsAll2;
		boolean bsContainsAll2;
		if (test1.size() > 0) {
			containsAll2 = test2.containsAll(test1);
			System.out.println("BitArray 2 contains all of BitArray 1? " + test2.containsAll(test1));
			cardinalityBeforeAnd = testBS1.cardinality();
			testBS1.and(testBS2);
			bsContainsAll2 = cardinalityBeforeAnd == testBS1.cardinality();
			System.out.println("test BitSet 1 contains all of test BitSet 2? " + bsContainsAll2);
			if (bsContainsAll2 != containsAll2) {
				throw new AssertionError("Contains all failed.");
			}		
			System.out.println("Passed contains all test 2");
		} else {
			try {
				containsAll1 = test2.containsAll(test1);
			} catch (NoSuchElementException ex) {
				System.out.println(ex.getMessage() + " (NoSuchElementException thrown correctly)");
			}
		}
		
		System.out.println("Passed contains all\n");
		
		// creating arrays to test containsAny
		maxVal = 10;
		n1 = r.nextInt(7);
		arr1 = new int[n1];
		for (int i = 0; i < arr1.length; i++) {
			arr1[i] = r.nextInt(maxVal);
		}
		n2 = r.nextInt(7);
		arr2 = new int[n2];
		for (int i = 0; i < arr2.length; i++) {
			arr2[i] = r.nextInt(maxVal);
		}
		
		// replace testBS1
		testBS1 = new BitSet();
		for (int i : arr1) {
			testBS1.set(i, true);
		}
		
		// making a new BitSet with arr2 values
		testBS2 = new BitSet();
		for (int i : arr2) {
			testBS2.set(i, true);
		}

		System.out.println("Testing contains any");
		test1 = new TLongBitArraySet(arr1);
		test2 = new TLongBitArraySet(arr2);
		System.out.println("BitArray 1 contains: " + Arrays.toString(test1.toArray()));
		System.out.println("BitArray 2 contains: " + Arrays.toString(test2.toArray()));

		boolean containsAny1 = false;
		boolean bsContainsAny1 = false;
		if (test2.size() > 0) {
			containsAny1 = test1.containsAny(test2);
			System.out.println("BitArray 1 contains any of BitArray 2? " + containsAny1);
			if (containsAny1) {
				System.out.println("BitArray 1 and BitArray 2 both contain: " + Arrays.toString(test1.getIntersection(test2).toArray()));
			} else {
				System.out.println("No overlap");
			}
			bsContainsAny1 = testBS1.intersects(testBS2);
			System.out.println("test BitSet 1 contains any of test BitSet 2? " + testBS1.intersects(testBS2));
			if (bsContainsAny1 != containsAny1) {
				throw new AssertionError("Contains any failed.");
			}		
			System.out.println("Passed contains any test 1");

		} else {
			try {
				containsAny1 = test1.containsAny(test2);
			} catch (NoSuchElementException ex) {
				System.out.println(ex.getMessage() + " (NoSuchElementException thrown correctly)");
			}
		}
		
		boolean containsAny2 = false;
		boolean bsContainsAny2 = false;
		if (test1.size() > 0) {
			containsAny2 = test2.containsAny(test1);
			System.out.println("BitArray 2 contains any of BitArray 1? " + containsAny2);
			if (containsAny1) {
				System.out.println("BitArray 1 and BitArray 2 both contain: " + Arrays.toString(test1.getIntersection(test2).toArray()));
			} else {
				System.out.println("No overlap");
			}
			bsContainsAny2 = testBS2.intersects(testBS1);
			System.out.println("test BitSet 2 contains any of test BitSet 1? " + testBS2.intersects(testBS1));
			if (bsContainsAny2 != containsAny2) {
				throw new AssertionError("Contains any failed.");
			}		
			System.out.println("Passed contains any test 2");

		} else {
			try {
				containsAny2 = test2.containsAny(test1);
			} catch (NoSuchElementException ex) {
				System.out.println(ex.getMessage() + " (NoSuchElementException thrown correctly)");
			}
			containsAny2 = containsAny1;
			bsContainsAny2 = containsAny1;
		}

		if ((containsAny1 == containsAny2 == bsContainsAny1 == bsContainsAny2) == false) {
			throw new AssertionError("Contains any failed. If either BitArray contained any of the other, then all the contains any tests should have been true.");
		} else {
			System.out.println("Passed contains any\n");
		}

		maxVal = 20;
		int maxOps = 10;

		/*
		System.out.println("Testing add/remove with duplicate values");
		
		int nCycles = r.nextInt(maxOps);
		int nAdds;
		int nRemoves;
		int testVal;

		test1 = new TLongBitSet();
		System.out.println("BitArray contains " + Arrays.toString(test1.toArray()));
		HashMap<Integer, Integer> expectedCounts = new HashMap<Integer, Integer>();
		for (int k = 0; k < nCycles; k++) {
			nAdds = r.nextInt(maxOps);
			nRemoves = r.nextInt(maxOps);
			testVal = r.nextInt(maxVal);
			System.out.println("Will add the value " + testVal + " to  BitArray " + nAdds + " times, then attempt to remove it " + nRemoves + " times");
			for (int l = 0; l < nAdds; l++) {
				test1.add(testVal);
			}
			for (int l = 0; l < nRemoves; l++) {
				test1.remove(testVal);
			}
			System.out.println("BitArray contains " + Arrays.toString(test1.toArray()));
			int expectedChange = nAdds - nRemoves;
			if (expectedCounts.containsKey(testVal)) {
				expectedChange += expectedCounts.get(testVal);
			}
			expectedCounts.put(testVal, expectedChange > 0 ? expectedChange : 0);
			int obsCount = 0;
			for (Long l : test1) {
				if (l == testVal) {
					obsCount++;
				}
			}
			if (obsCount != expectedCounts.get(testVal)) {
				throw new AssertionError("BitArray should contain exactly " + expectedCounts.get(testVal) + " copies of " +  testVal + " but instead it contains " + obsCount);
			}
			test1.testInternalState();
		}
		System.out.println("Passed duplicate values add/remove\n"); */
		
		System.out.println("Testing sequential add and remove");
		maxVal = 50;
		maxOps = 100;
		int nOps1 = r.nextInt(maxOps);
		int nOps2 = maxOps - nOps1;
		test1 = new TLongBitArraySet();
		test2 = new TLongBitArraySet();
		
		int nAddOps = 0;
		int nRemoveOps = 0;
		int nSkippedOps = 0;
		System.out.println("Will attempt to perform " + nOps1 + " operations on BitArray 1");
		for (int i = 0; i < nOps1; i++) {
			int nextInt = r.nextInt(maxVal);
			if (r.nextBoolean()) {
				test1.add(nextInt);
				nAddOps++;
			} else {
				if (test1.contains(nextInt)) {
					test1.remove(nextInt);
					nRemoveOps++;
				} else {
					nSkippedOps++;
				}
			}
		}
		test1.updateBitSet();
		System.out.println("Performed " + nAddOps + " add operations, " + nRemoveOps + " remove operations, and skipped " + nSkippedOps + " operations on BitArray 1");

		nAddOps = 0;
		nRemoveOps = 0;
		nSkippedOps = 0;
		System.out.println("Will attempt to perform " + nOps2 + " operations on BitArray 2");
		for (int i = 0; i < nOps2; i++) {
			int nextInt = r.nextInt(maxVal);
			if (r.nextBoolean()) {
				test2.add(nextInt);
				nAddOps++;
			} else {
				if (test2.contains(nextInt)) {
					test2.remove(nextInt);
					nRemoveOps++;
				} else {
					nSkippedOps++;
				}
			}
		}
		test1.updateBitSet();
		System.out.println("Performed " + nAddOps + " add operations, " + nRemoveOps + " remove operations, and skipped " + nSkippedOps + " operations on BitArray 2\n");

		System.out.println("BitArray 1: " + Arrays.toString(test1.toArray()));
		System.out.println("BitArray 2: " + Arrays.toString(test2.toArray()) + "\n");

		intersection = test1.getIntersection(test2);
		System.out.println("BitArray 1 and BitArray 2 have " + intersection.size() + " elements in common: " + Arrays.toString(intersection.toArray()));

		// these should always be true
		boolean arr1ContainsAllIntersectionEXPECT = true;
		boolean arr2ContainsAllIntersectionEXPECT = true;
		boolean arr1ContainsAllArr2EXPECT = intersection.size() == test2.cardinality() ? true : false;
		boolean arr2ContainsAllArr1EXPECT = intersection.size() == test1.cardinality() ? true : false;

		// these depend on the situation
		boolean intersectionContainsAnyArr1EXPECT;
		boolean intersectionContainsAnyArr2EXPECT;
		boolean arr1ContainsAnyArr2EXPECT;
		boolean arr2ContainsAnyArr1EXPECT;

		if (intersection.size() > 0) {
			
			intersectionContainsAnyArr1EXPECT = true;
			intersectionContainsAnyArr2EXPECT = true;
			arr1ContainsAnyArr2EXPECT = true;
			arr2ContainsAnyArr1EXPECT = true;
			
		} else { // the intersection was null

			intersectionContainsAnyArr1EXPECT = false;
			intersectionContainsAnyArr2EXPECT = false;
			arr1ContainsAnyArr2EXPECT = false;
			arr2ContainsAnyArr1EXPECT = false;
			
		}

		boolean arr1ContainsAllIntersection = test1.containsAll(intersection);
		System.out.println("Does BitArray 1 contain all the shared values? " + arr1ContainsAllIntersection);
		if (arr1ContainsAllIntersection != arr1ContainsAllIntersectionEXPECT) {
			throw new AssertionError("Contains all failed");
		}

		boolean arr2ContainsAllIntersection = test2.containsAll(intersection);
		System.out.println("Does BitArray 2 contain all the shared values? " + arr2ContainsAllIntersection);
		if (arr2ContainsAllIntersection != arr2ContainsAllIntersectionEXPECT) {
			throw new AssertionError("Contains all failed");
		}
		
		boolean intersectionContainsAnyArr1 = intersection.containsAny(test1);
		System.out.println("Does the intersection contain any of BitArray 1? " + intersectionContainsAnyArr1);
		if (intersectionContainsAnyArr1 != intersectionContainsAnyArr1EXPECT) {
			throw new AssertionError("Contains any failed");
		}
		
		boolean intersectionContainsAnyArr2 = intersection.containsAny(test2);
		System.out.println("Does the intersection contain any of BitArray 2? " + intersectionContainsAnyArr2);
		if (intersectionContainsAnyArr2 != intersectionContainsAnyArr2EXPECT) {
			throw new AssertionError("Contains any failed");
		}
		
		boolean arr1ContainsAnyArr2 = test1.containsAny(test2);
		System.out.println("Does BitArray 1 contain any of BitArray 2? " + arr1ContainsAnyArr2);
		if (arr1ContainsAnyArr2 != arr1ContainsAnyArr2EXPECT) {
			throw new AssertionError("Contains any failed");
		}

		boolean arr2ContainsAnyArr1 = test2.containsAny(test1);
		System.out.println("Does BitArray 2 contain any of BitArray 1? " + arr2ContainsAnyArr1);
		if (arr2ContainsAnyArr1 != arr2ContainsAnyArr1EXPECT) {
			throw new AssertionError("Contains any failed");
		}
		
		boolean arr1ContainsAllArr2 = test1.containsAll(test2);
		System.out.println("Does BitArray 1 contain ALL of BitArray 2? " + arr1ContainsAllArr2);
		if (arr1ContainsAllArr2 != arr1ContainsAllArr2EXPECT) {
			throw new AssertionError("Contains all failed");
		}

		boolean arr2ContainsAllArr1 = test2.containsAll(test1);
		System.out.println("Does BitArray 2 contain ALL of BitArray 1? " + arr2ContainsAllArr1);
		if (arr2ContainsAllArr1 != arr2ContainsAllArr1EXPECT) {
			throw new AssertionError("Contains all failed");
		}

		return true;
	}
}
