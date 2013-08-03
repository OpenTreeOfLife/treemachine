// to perform unit testing, first comment out the `package` line, then call the java compiler/vm from the command line:
// javac -cp ~/.m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar TLongBitArray.java 
// java -cp ~/.m2/repository/net/sf/trove4j/trove4j/3.0.3/trove4j-3.0.3.jar:. TLongBitArray

package opentree; // comment out for unit testing

import gnu.trove.list.array.TLongArrayList;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * A container class combining the features of TLongArrayList and BitSet.
 * @author cody hinchliff
 */
public class TLongBitArray implements Iterable<Long> {

	TLongArrayList tl; 
	BitSet bs;
	
	// ==== constructors
	
	public TLongBitArray(Iterable<Long> longArr) {
		tl = new TLongArrayList();
		this.addAll(longArr);
//		for (Long l : longArr) {
//			tl.add(l);
//		}
//		updateBitSet();
	}

	public TLongBitArray(int[] intArr) {
		tl = new TLongArrayList();
		this.addAll(intArr);
//		for (int i : intArr) {
//			tl.add(i);
//		}
//		updateBitSet();
	}
	
	public TLongBitArray(long[] longArr) {
		tl = new TLongArrayList();
		this.addAll(longArr);
//		updateBitSet();
	}

	public TLongBitArray(TLongArrayList tLongArr) {
		tl = new TLongArrayList();
		this.addAll(tLongArr);
//		updateBitSet();
	}
	
	public TLongBitArray(BitSet bs) {
		tl = new TLongArrayList();
		this.addAll(bs);
//		this.bs = (BitSet) bs.clone();
	}
	
	public TLongBitArray() {
		tl = new TLongArrayList();
		updateBitSet();
	}

	// ==== basic functions
	
	public BitSet getBitSet() {
		return bs;
	}
	
	public int size() {
		return tl.size();
	}
	
	public Long get(int i) {
		assert bs.get(Long.valueOf(tl.get(i)).intValue()) == true;
		return tl.get(i);
	}
	
	public long[] toArray() {
		return tl.toArray();
	}
	
	public void sort() {
		tl.sort();
	}
	
	public boolean contains(Long l) {
		assert tl.contains(l) == bs.get(l.intValue());
		return tl.contains(l);
	}

	public boolean contains(int i) {
		assert tl.contains(i) == bs.get(i);
		return tl.contains(i);
	}
	
	// ==== any operation that changes the underlying TLongArray instance must trigger an update to the BitSet

	// == addition methods
	
	/**
	 * Adds the value to the array. A subsequent call to updateBitSet() must be made manually. This is intended *only* to
	 * provide access to the add() method without rebuilding the BitSet every time, but use with caution! If possible,
	 * use addAll() to avoid the need to manually update the BitSet.
	 * @param l
	 */
	public void addAndDoNotUpdateBitSet(Long l) {
		tl.add(l);
		bs.set(l.intValue(), true);
	}

	/**
	 * Adds the value to the array. A subsequent call to updateBitSet() must be made manually. This is intended *only* to
	 * provide access to the add() method without rebuilding the BitSet every time, but use with caution! If possible,
	 * use addAll() to avoid the need to manually update the BitSet.
	 * @param l
	 */
	public void addAndDoNotUpdateBitSet(int i) {
		tl.add((long) i);
		bs.set(i, true);
	}

	/**
	 * Add all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void addAll(int[] toAdd) {
		for (int i : toAdd) {
			tl.add(i);
		}
		updateBitSet();
	}
	
	/**
	 * Add all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void addAll(long[] toAdd) {
		for (long l : toAdd) {
			tl.add(l);
		}
		updateBitSet();
	}
	
	/**
	 * Add all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void addAll(Iterable<Long> toAdd) {
		for (Long l : toAdd) {
			tl.add(l);
		}
		updateBitSet();
	}
	
	/**
	 * Add all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void addAll(BitSet toAdd) {
		for (int i = toAdd.nextSetBit(0); i >= 0; i = toAdd.nextSetBit(i+1)) {
			tl.add(i);
		}
		updateBitSet();
	}

	/**
	 * Add all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void addAll(TLongArrayList toAdd) {
		tl.addAll(toAdd);
		updateBitSet();
	}

	/**
	 * Add all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void addAll(TLongBitArray toAdd) {
		this.addAll((Iterable<Long>) toAdd); // use the iterator method
		updateBitSet();
	}
	
	// == removal methods
	
	// THIS IS INCORRECT BEHAVIOR. REVIEW REMOVE METHODS FOR TLONGARRAYLIST AND UPDATE ACCORDINGLY.
	
	/**
	 * Remove the value from the array. If there are multiple equivalent values, only one will be removed.
	 * Not as efficient as removeAll() because of the need to validate the internal state on every removal.
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
	 * Remove the value from the array. If there are multiple equivalent values, only one will be removed.
	 * Not as efficient as removeAll() because of the need to validate the internal state on every removal.
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
	 * Remove all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void removeAll(int[] toRemove) {
		for (int i : toRemove) {
			tl.remove(i);
		}
		updateBitSet();
	}
	
	/**
	 * Remove all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void removeAll(long[] toRemove) {
		for (long l : toRemove) {
			tl.remove(l);
		}
		updateBitSet();
	}
	
	/**
	 * Remove all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void removeAll(Iterable<Long> toRemove) {
		for (Long l : toRemove) {
			tl.remove(l);
		}
		updateBitSet();
	}

	/**
	 * Remove all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void removeAll(BitSet toRemove) {
		for (int i = toRemove.nextSetBit(0); i >= 0; i = toRemove.nextSetBit(i+1)) {
			tl.remove(i);
		}
		updateBitSet();
	}
	
	/**
	 * Remove all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void removeAll(TLongArrayList toRemove) {
		tl.removeAll(toRemove);
		updateBitSet();
	}

	/**
	 * Remove all the values to the array. Will update the underlying bitset.
	 * @param toAdd
	 */
	public void removeAll(TLongBitArray toRemove) {
		this.removeAll((Iterable<Long>) toRemove); // use the iterator method
		updateBitSet();
	}
	
	/**
	 * Rebuild the internal bitset, used for bitwise operations. This could probably be optimized but it is
	 * still pretty fast even to rebuild the whole thing.
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
//		testInternalState();
	}
	
	// ==== boolean / bitwise operations

	/**
	 * Perfoms a binary andNot on the internal BitSet against the passed BitSet and returns a new TLongBitArray containing the result.
	 * Does not modify the internal or the passed BitSet.
	 * @param compBS
	 * @return
	 */
	public TLongBitArray andNot(BitSet compBS) {
//		testInternalState();
		BitSet result = (BitSet) this.bs.clone();
		result.andNot(compBS);
		return new TLongBitArray(result);
	}
	
	/**
	 * Perfoms a binary andNot on the internal BitSet against the passed TLongBitArray and returns a new TLongBitArray containing the result.
	 * Does not modify the internal or the passed BitSet.
	 * @param compArr
	 * @return
	 */
	public TLongBitArray andNot(TLongBitArray compArr) {
//		testInternalState();
		return new TLongBitArray(this.andNot(compArr.bs));
	}
	
	/**
	 * Returns true if and only if this TLongBitArray contains any values from the passed TLongBitArray.
	 * @param compArr
	 * @return
	 */
	public boolean containsAny(TLongBitArray compArr) {
//		testInternalState();
		return this.containsAny(compArr.getBitSet());
	}

	/**
	 * Returns true if and only if this TLongBitArray contains any values set to true in the passed BitSet.
	 * @param compArr
	 * @return
	 */
	public boolean containsAny(BitSet compBS) {
//		testInternalState();
		
//		if (compBS.isEmpty()) {
//			throw new NoSuchElementException("attempt to pass an empty list to contains any");
//		}
		
		return bs.intersects(compBS);
	}

	/**
	 * Returns true if and only if this TLongBitArray contains all the from the passed TLongBitArray.
	 * @param compArr
	 * @return
	 */
	public boolean containsAll(TLongBitArray compArr) {
//		testInternalState();
		return this.containsAll(compArr.bs);
	}

	/**
	 * Returns true if and only if this TLongBitArray contains all the values set to true in the passed BitSet.
	 * @param compArr
	 * @return
	 */
	public boolean containsAll(BitSet compBS) {
//		testInternalState();
		
//		if (compBS.isEmpty()) {
//			throw new NoSuchElementException("attempt to pass an empty list to contains all");
//		}
		
		BitSet intersection = (BitSet) compBS.clone();
		intersection.and(bs);
		if (intersection.cardinality() == compBS.cardinality()) { 
			return true;
		} else {
			return false;
		}

		// alternative method. not sure which is faster
//		for (int i = compBS.nextSetBit(0); i >= 0; i = compBS.nextSetBit(i+1)) {
//			if (bs.get(i) == false) {
//				return false; // if we find a bit from compBS not in our BS
//		    }
//		}
//		return true; // we found all the bits from compBS
	}
	
	/**
	 * Returns a TLongBitArray containing the values that are both in this TLongBitArray and the passed TLongBitArray.
	 * @param compArr
	 * @return
	 */
	public TLongBitArray getIntersection(TLongBitArray compArr) {
//		testInternalState();
/*		TLongBitArray intersect = new TLongBitArray();
		for (int k = 0; k < compArr.size(); k++) {
			if (tl.contains(compArr.get(k))) {
				intersect.addAndDoNotUpdateBitSet(compArr.get(k));
			}
		} */
		
		BitSet intersection = (BitSet) bs.clone();
		intersection.and(compArr.bs);
		return new TLongBitArray(intersection);
	}

	/**
	 * Returns a TLongBitArray containing the values that are both in this TLongBitArray and are set to true in the passed BitSet.
	 * @param compArr
	 * @return
	 */
	public TLongBitArray getIntersection(BitSet compBS) {
//		testInternalState();
/*		TLongBitArray intersect = new TLongBitArray();
		for (int k = 0; k < tl.size(); k++) {
			if (compBS.get((int) tl.get(k)) == true) { // casting to an int, this could be bad
				intersect.addAndDoNotUpdateBitSet(tl.get(k));
			}
		} */

		// testing
/*		int[] compValues = new int[compBS.length()];
		int j = 0;
		for (int k = compBS.nextSetBit(0); k >= 0;  k = compBS.nextSetBit(k+1)) {
			compValues[j++] = k;
		}
		System.out.println("Performing intersection.\nThe passed in BitSet contained: " + Arrays.toString(compValues));

		int[] intValues = new int[bs.length()];
		j = 0;
		for (int k = bs.nextSetBit(0); k >= 0;  k = bs.nextSetBit(k+1)) {
			intValues[j++] = k;
		}
		System.out.println("The internal BitSet contained: " + Arrays.toString(intValues)); */

		
		BitSet intersection = (BitSet) bs.clone();

		
/*		int[] tempValues = new int[intersection.length()];
		j = 0;
		for (int k = intersection.nextSetBit(0); k >= 0;  k = intersection.nextSetBit(k+1)) {
			tempValues[j++] = k;
		}
		System.out.println("The temporary BitSet (cloned from internal) contained: " + Arrays.toString(tempValues)); */

		
		intersection.and(compBS);

		
/*		int[] intersectedValues = new int[intersection.length()];
		j = 0;
		for (int k = intersection.nextSetBit(0); k >= 0;  k = intersection.nextSetBit(k+1)) {
			intersectedValues[j++] = k;
		}
		System.out.println("The intersection BitSet contained: " + Arrays.toString(intersectedValues)); */

		return new TLongBitArray(intersection);
	}
	
	// ==== internal methods
	
	/**
	 * Returns an iterator over the values from this TLongBitArray.
	 */
	@Override
	public Iterator<Long> iterator() {
		return new TLongBitArrayIterator(tl);
	}
	
	private class TLongBitArrayIterator implements Iterator<Long> {

		int i;
		TLongArrayList tl;

		public TLongBitArrayIterator(TLongArrayList tl) {
			i = 0;
			this.tl = tl;
		}
		
		@Override
		public boolean hasNext() {
			if (i < tl.size()) {
				return true;
			} else {
				return false;
			}
		}

		@Override
		public Long next() {
			return tl.get(i++);
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("the reverse method is not supported for TLongBitArrayIterator");
		}
	}
	
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

		int maxVal = 100;
		Random r = new Random();

		// create a random test arrays of ints
		int n1 = r.nextInt(20);
		int[] arr1 = new int[n1];
		for (int i = 0; i < arr1.length; i++) {
			arr1[i] = r.nextInt(maxVal);
		}
		
		System.out.println("\nThe first array is: " + Arrays.toString(arr1));
		
		// test setting and getting
		System.out.println("Testing adding and getting");
		TLongBitArray test1 = new TLongBitArray();
		for (int k : arr1) {
			test1.addAndDoNotUpdateBitSet(k);
		}
		test1.updateBitSet();
		test1.testInternalState();
//		int j = 0;
		TLongBitArray test2 = new TLongBitArray();
		for (int k : arr1) {
			if (test1.contains(k) == false) {
				throw new AssertionError("Adding and getting failed. BitArray 1 should have contained " + k + " but it did not");
			} else {
				test2.addAndDoNotUpdateBitSet(k);
			}
		}
		test1 = new TLongBitArray(arr1);
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
		test1 = new TLongBitArray(testBS1);
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
		test1 = new TLongBitArray(testTL);
		System.out.println("The BitArray constructed from the TLongArrayList contains: " + Arrays.toString(test1.toArray()));
		Arrays.sort(arr1);
		for (int k = 0; k < arr1.length; k++) {
			if (test1.get(k) != arr1[k]) {
				throw new java.lang.AssertionError("BitArray creation from TLongArrayList failed");
			}
		}
		test1.testInternalState();
		System.out.println("BitArray construction from TLongArrayList passed\n");

		// test instantiating a BitArray from another BitArray
		System.out.println("Testing BitArray construction from another BitArray");
		test2 = new TLongBitArray(arr1);
		System.out.println("The starting BitArray contains " + test2.size() + " values: " + Arrays.toString(test2.toArray()));
		test1 = new TLongBitArray(test2);
		System.out.println("The BitArray constructed from the starting BitArray contains: " + Arrays.toString(test1.toArray()));
		for (int k = 0; k < arr1.length; k++) {
			if (test1.get(k) != arr1[k]) {
				throw new java.lang.AssertionError("BitArray creation from BitArray failed");
			}
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
		test1 = new TLongBitArray(testArrLong);
		System.out.println("The BitArray constructed from the TLongArrayList contains: " + Arrays.toString(test1.toArray()));
		Arrays.sort(testArrLong);
		for (int k = 0; k < testArrLong.length; k++) {
			if (test1.get(k) != testArrLong[k]) {
				throw new java.lang.AssertionError("BitArray creation from long array failed");
			}
		}
		test1.testInternalState();
		System.out.println("BitArray construction from long array primitive passed\n");
		
		// instantiating a BitArray from a long array
		System.out.println("Testing BitArray construction from int array primitive");
		long[] testArrInt = new long[r.nextInt(20)];
		for (int k = 0; k < testArrInt.length; k++) {
			testArrInt[k] = r.nextInt(maxVal);
		}
		System.out.println("The int array contains " + testArrInt.length + " values: " + Arrays.toString(testArrInt));
		test1 = new TLongBitArray(testArrInt);
		System.out.println("The BitArray constructed from the TLongArrayList contains: " + Arrays.toString(test1.toArray()));
		Arrays.sort(testArrInt);
		for (int k = 0; k < testArrInt.length; k++) {
			if (test1.get(k) != testArrInt[k]) {
				throw new java.lang.AssertionError("BitArray creation from int array failed");
			}
		}
		test1.testInternalState();
		System.out.println("BitArray construction from int array primitive passed\n");
		
		// testing BitSet updating
		System.out.println("Testing removal from the BitArray");
		System.out.println("BitArray contains: " + Arrays.toString(test1.toArray()));
		System.out.println("Removing values: " + Arrays.toString(testArrInt));
		test1.removeAll(testArrInt);
		test1.testInternalState();
		for (int k = 0; k < testArrInt.length; k++) {
			if (test1.contains(testArrInt[k])) {
				throw new java.lang.AssertionError("BitArray removal failed, still contains " + testArrInt[k]);
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
		
		test1 = new TLongBitArray(arr1);
		System.out.println("Intersecting BitArray: " + Arrays.toString(test1.toArray()));
		System.out.println("with BitSet containing: " + Arrays.toString(arr2));
		// making a new BitSet with arr2 values
		testBS2 = new BitSet();
		for (int i : arr2) {
			testBS2.set(i, true);
		}
		TLongBitArray intersection = test1.getIntersection(testBS2);

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
		test2 = new TLongBitArray(arr2);
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
		test1 = new TLongBitArray(arr1);
		test2 = new TLongBitArray(arr2);
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

		System.out.println("Testing add/remove with duplicate values");
		maxVal = 20;
		int maxOps = 10;
		int nCycles = r.nextInt(maxOps);
		int nAdds;
		int nRemoves;
		int testVal;

		test1 = new TLongBitArray();
		System.out.println("BitArray contains " + Arrays.toString(test1.toArray()));
		HashMap<Integer, Integer> expectedCounts = new HashMap<Integer, Integer>();
		for (int k = 0; k < nCycles; k++) {
			nAdds = r.nextInt(maxOps);
			nRemoves = r.nextInt(maxOps);
			testVal = r.nextInt(maxVal);
			System.out.println("Will add the value " + testVal + " to  BitArray " + nAdds + " times, then attempt to remove it " + nRemoves + " times");
			for (int l = 0; l < nAdds; l++) {
				test1.addAndDoNotUpdateBitSet(testVal);
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
		System.out.println("Passed duplicate values add/remove\n");
		
		System.out.println("Testing sequential add and remove");
		maxVal = 50;
		maxOps = 100;
		int nOps1 = r.nextInt(maxOps);
		int nOps2 = maxOps - nOps1;
		test1 = new TLongBitArray();
		test2 = new TLongBitArray();
		
		int nAddOps = 0;
		int nRemoveOps = 0;
		int nSkippedOps = 0;
		System.out.println("Will attempt to perform " + nOps1 + " operations on BitArray 1");
		for (int i = 0; i < nOps1; i++) {
			int nextInt = r.nextInt(maxVal);
			if (r.nextBoolean()) {
				test1.addAndDoNotUpdateBitSet(nextInt);
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
				test2.addAndDoNotUpdateBitSet(nextInt);
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

		boolean intersectionContainsAnyArr1EXPECT;
		boolean intersectionContainsAnyArr2EXPECT;
		boolean arr1ContainsAnyArr2EXPECT;
		boolean arr2ContainsAnyArr1EXPECT;
		boolean arr1ContainsAllArr2EXPECT;
		boolean arr2ContainsAllArr1EXPECT;

		if (intersection.size() > 0) {
			
			boolean arr1ContainsAllIntersection = test1.containsAll(intersection);
			System.out.println("Does BitArray 1 contain all the shared values? " + arr1ContainsAllIntersection);
			if (arr1ContainsAllIntersection != true) {
				throw new AssertionError("Contains all failed");
			}
			
			boolean arr2ContainsAllIntersection = test2.containsAll(intersection);
			System.out.println("Does BitArray 2 contain all the shared values? " + arr2ContainsAllIntersection);
			if (arr2ContainsAllIntersection != true) {
				throw new AssertionError("Contains all failed");
			}
			
			intersectionContainsAnyArr1EXPECT = true;
			intersectionContainsAnyArr2EXPECT = true;
			arr1ContainsAnyArr2EXPECT = true;
			arr2ContainsAnyArr1EXPECT = true;
			arr1ContainsAllArr2EXPECT = intersection.size() == test2.size() ? true : false;
			arr2ContainsAllArr1EXPECT = intersection.size() == test1.size() ? true : false;
			
		} else { // the intersection was null

			System.out.println("Does BitArray 1 contain all the shared values?");
			try {
				boolean arr1ContainsAllIntersection = test1.containsAll(intersection);
			} catch (NoSuchElementException ex) {
				System.out.println(ex.getMessage() + " (NoSuchElementException thrown correctly)");
			}

			System.out.println("Does BitArray 2 contain all the shared values?");
			try {
				boolean arr1ContainsAllIntersection = test1.containsAll(intersection);
			} catch (NoSuchElementException ex) {
				System.out.println(ex.getMessage() + " (NoSuchElementException thrown correctly)");
			}
			
			intersectionContainsAnyArr1EXPECT = false;
			intersectionContainsAnyArr2EXPECT = false;
			arr1ContainsAnyArr2EXPECT = false;
			arr2ContainsAnyArr1EXPECT = false;
			arr1ContainsAllArr2EXPECT = false;
			arr2ContainsAllArr1EXPECT = false;
			
		}
		
		if (test2.size() > 0) {
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

			boolean arr1ContainsAllArr2 = test1.containsAll(test2);
			System.out.println("Does BitArray 1 contain ALL of BitArray 2? " + arr1ContainsAllArr2);
			if (arr1ContainsAllArr2 != arr1ContainsAllArr2EXPECT) {
				throw new AssertionError("Contains all failed");
			}
		}
		
		if (test1.size() > 0) {
			boolean intersectionContainsAnyArr1 = intersection.containsAny(test1);
			System.out.println("Does the intersection contain any of BitArray 1? " + intersectionContainsAnyArr1);
			if (intersectionContainsAnyArr1 != intersectionContainsAnyArr1EXPECT) {
				throw new AssertionError("Contains any failed");
			}
			
			boolean arr2ContainsAnyArr1 = test2.containsAny(test1);
			System.out.println("Does BitArray 2 contain any of BitArray 1? " + arr2ContainsAnyArr1);
			if (arr2ContainsAnyArr1 != arr2ContainsAnyArr1EXPECT) {
				throw new AssertionError("Contains any failed");
			}

			boolean arr2ContainsAllArr1 = test2.containsAll(test1);
			System.out.println("Does BitArray 2 contain ALL of BitArray 1? " + arr2ContainsAllArr1);
			if (arr2ContainsAllArr1 != arr2ContainsAllArr1EXPECT) {
				throw new AssertionError("Contains all failed");
			}
		}

		System.out.println("\nAll tests passed\n");
	}
}
