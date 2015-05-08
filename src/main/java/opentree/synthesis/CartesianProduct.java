package opentree.synthesis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Collection;

import static org.opentree.utils.GeneralUtils.print;

public class CartesianProduct<T> implements Iterable<Set<T>> {

	boolean withMissingElements = false;
	List<List<T>> sets;
	
	CartesianProduct (Collection<Set<T>> sets) { 
		this.sets = new ArrayList<List<T>>();
		for (Collection<T> t : sets) {
			this.sets.add(new ArrayList<T>(t));
		}
	}

	public CartesianProduct<T> withMissingElements() {
		withMissingElements = true;
		return this;
	}

	public CartesianProduct<T> withoutMissingElements() {
		withMissingElements = false;
		return this;
	}
	
	@Override
	public Iterator<Set<T>> iterator() {
		return new PrunableCPSupersetIterator<T>(sets, withMissingElements);
	}
	
	public int numberOfSets() {
		int n = 1;
		for (List<T> t : sets) {
			n *= t.size();
		}
		return n;
	}

	public int numberOfSetsWithMissingElements() {
		int n = 1;
		for (List<T> t : sets) {
			n *= t.size() + 1;
		}
		return n;
	}

	public PrunableCPSupersetIterator<T> prunableIterator() {
		return new PrunableCPSupersetIterator<T>(sets, withMissingElements);
	}

	/* work in progress... may not be necessary
	public static class PrunableCPSubsetIterator<R> implements Iterator<Set<R>> {

		boolean withMissingElements;

		List<List<R>> sets;
		LinkedList<Sample> approved = new LinkedList<Sample>();
		List<Sample> proposed = new ArrayList<Sample>();

		/** simple container class *
		private class Sample {
			int[] index;
			int lastPosition;
			public Sample(int[] index, int nextPosition) {
				this.index = index;
				this.lastPosition = nextPosition;
			}
		}
		
		public PrunableCPSubsetIterator(List<List<R>> sets, boolean withMissingElements) {
			this.sets = sets;
			this.withMissingElements = withMissingElements;
			
			int[] start = new int[sets.size()];
//			for (int i = 0; i < start.length; i++) { start[i] = startVal(i); }
			approved.add(new Sample(start, -1));
		}
		
		@Override
		public boolean hasNext() {
			// approve all the proposed samples derived from the last combination if they have not been pruned
			if (proposed != null) { approved.addAll(proposed); }
			proposed = new ArrayList<Sample>();
			return approved.size() > 0;
		}


	} */
	
	public static class PrunableCPSupersetIterator<R> implements Iterator<Set<R>> {
		
		boolean withMissingElements;

		List<List<R>> sets;
		LinkedList<Sample> approved = new LinkedList<Sample>();
		List<Sample> proposed = new ArrayList<Sample>();

		/** simple container class */
		private class Sample {
			int[] index;
			int lastPosition;
			public Sample(int[] index, int nextPosition) {
				this.index = index;
				this.lastPosition = nextPosition;
			}
		}
				
		public PrunableCPSupersetIterator(List<List<R>> sets, boolean withMissingElements) {
			this.sets = sets;
			this.withMissingElements = withMissingElements;
			
			int[] start = new int[sets.size()];
			for (int i = 0; i < start.length; i++) { start[i] = startVal(i); }
			approved.add(new Sample(start, -1));
		}
		
		private int startVal(int i) {
			return withMissingElements ? -1 : 0;
		}
		
		@Override
		public boolean hasNext() {
			// approve all the proposed samples derived from the last combination if they have not been pruned
			if (proposed != null) { approved.addAll(proposed); }
			proposed = new ArrayList<Sample>();
			return approved.size() > 0;
		}

		@Override
		public Set<R> next() {
			
			// get the next sample from the approved queue
			Sample s = approved.pop();
			
			// collect the set that corresponds to this sample
			Set<R> t = new HashSet<R>();
			for (int i = 0; i < s.index.length; i++) {
				if (s.index[i] >= 0) {
					t.add(sets.get(i).get(s.index[i]));
				}
			}
			
			// add the samples that dervive from this one to the proposed queue
			addSamplesLeastInclusiveFirst(s);
			
			return t;
		}
		
		private void addSamplesLeastInclusiveFirst(Sample s) {
			for (int column = s.lastPosition + 1; column < s.index.length; column++) {
				for (int j = 0; j <= maxPosition(column); j++) {
					int[] index = Arrays.copyOf(s.index, s.index.length);
					index[column] = j;
					proposed.add(new Sample(index, column));
				}
			}
		}
		
		private int maxPosition(int column) {
			return sets.get(column).size() - 1;
		}

		/**
		 * exclude all the the potential sets that could have been generated using the last set returned by the next() method
		 * as a starting point.
		 */
		public void prune() {
			proposed = new ArrayList<Sample>();
		}

/*		private Sample increment(int[] index, int curColumn) {
			
			Sample s;
			if (index[curColumn] > maxPosition(curColumn)) {
				s = rollOver(index, curColumn);
				
			} else if (curColumn == sets.size() - 1) {
				if (index[curColumn] < maxPosition(curColumn)) {
					index[curColumn]++;
					s = new Sample(index, curColumn);
				} else {
					s = rollOver(index, curColumn);
				}

			} else {
				s = increment(index, curColumn + 1);
			}
			
			return s;
		} */
		
/*		private Sample rollOver(int[] index, int column) {
			Sample s;
			if (column > 0) {
				// rollover the previous column if it is finished
				if (index[column-1] >= maxPosition(column-1)) {
					s = rollOver(index, column-1);
				} else {
					// reset all deeper columns
					for (int c = column; c < index.length; c++) {
						index[c] = 0;
					}
					index[--column]++;
					s = new Sample(index, column);
				}
			} else { // attempt to roll over first column means we're done
				s = null;
			}
			return s;
		} */
	}
	
	private static Set<Set<Object>> makeSets(Object[][] input) {

		Set<Set<Object>> t = new HashSet<Set<Object>>();
		for (int i = 0; i < input.length; i++) {
			Set<Object> x = new HashSet<Object>();
			for (int j = 0; j < input[i].length; j++) {
				x.add(input[i][j]);
			}
			t.add(x);
		}
		return t;
	}
	
	private static void simpleTest(Object[][] i, boolean withMissingElements) {
		Set<Set<Integer>> t = (Set<Set<Integer>>)(Set<?>) makeSets((Object[][]) i);
		CartesianProduct<Integer> samples = new CartesianProduct<Integer>(t);
		if (withMissingElements) {
			samples = samples.withMissingElements();
		} else {
			samples = samples.withoutMissingElements();
		}
		for (Set<Integer> s : samples) {
			print(s);
		}
	}

	private static void pruneIfSumExceedsTest(Object[][] i, boolean withMissingElements, double maxVal) {
		Set<Set<Integer>> t = (Set<Set<Integer>>)(Set<?>) makeSets((Object[][]) i);
		CartesianProduct<Integer> samples = new CartesianProduct<Integer>(t).withMissingElements();
		PrunableCPSupersetIterator<Integer> combinations = samples.prunableIterator();
		while (combinations.hasNext()) {

			Set<Integer> s = combinations.next();
			if (sum(s) > maxVal) {
				combinations.prune();
			} else {
				print(s);
			}
		}
	}
	
	private static <T extends Number> double sum(Set<T> incoming) {
		Double sum = 0.0;
		for (Number n : incoming) {
			sum += n.doubleValue();
		}
		return sum;
	}

	public static void main(String[] args) {
		simpleTest(new Integer[][] {{0,1}, {3,4}, {6,7}}, true);
		simpleTest(new Double[][] {{0.1,0.2}, {0.3,0.4}, {0.5,0.6}, {0.7,0.8}}, true);
		simpleTest(new Integer[][] {{0,1}, {10,11,12}, {20,21,22,23}}, true);
		pruneIfSumExceedsTest(new Integer[][] {{0,1}, {10,11,12}, {20,21,22,23}}, true, 5);
		pruneIfSumExceedsTest(new Integer[][] {{0,1}, {10,11,12}, {20,21,22,23}}, true, 15);
		pruneIfSumExceedsTest(new Integer[][] {{0,1}, {10,11,12}, {20,21,22,23}}, true, 25);
		pruneIfSumExceedsTest(new Integer[][] {{0,1}, {10,11,12}, {20,21,22,23}}, true, 35);
		pruneIfSumExceedsTest(new Integer[][] {{0,1}, {3,4}, {6,7}}, true, 10);
	}
}
