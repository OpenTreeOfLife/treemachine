package opentree.synthesis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Collection;

import static org.opentree.utils.GeneralUtils.print;

public class CartesianProduct<T> implements Iterable<Set<T>> {

	List<List<T>> sets;
	
	CartesianProduct (Collection<Set<T>> sets) { 
		this.sets = new ArrayList<List<T>>();
		for (Collection<T> t : sets) {
			this.sets.add(new ArrayList<T>(t));
		}
		java.lang.System.out.println();
	}

	@Override
	public Iterator<Set<T>> iterator() {
		return new CPIterator(false); // no missing elements
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

	public Iterable<Set<T>> withMissingElements() {
		return new Iterable<Set<T>> () {
			@Override
			public Iterator<Set<T>> iterator() {
				return new CPIterator(true); // make sets with missing elements
			}
		};
	}
	
	private class CPIterator implements Iterator<Set<T>> {
		
		int[] index;
		boolean withMissing;
		boolean hasNext = true;

		public CPIterator(boolean withMissingElements) {
			index = new int[sets.size()];
			withMissing = withMissingElements;
			if (sets.size() < 1) {
				hasNext = false;
			}
		}
		
		@Override
		public boolean hasNext() {
			return hasNext;
		}

		@Override
		public Set<T> next() {
			Set<T> sample = new HashSet<T>();
			for(int i = 0; i < index.length; i++) {
				if (! (withMissing && index[i] >= sets.get(i).size())) {
					sample.add(sets.get(i).get(index[i]));
				}
			}
			// TODO: remember the position we need to updating the next index so we don't
			// have to search for it every time (would save a lot of time for large numbers
			// of sets). just have the increment function return the column where the next
			// increment operation should occur.
			increment(0);
			return sample;
		}
		
		private void increment(int column) {
			
			if (index[column] > maxPosition(column)) {
				rollOver(column);
				
			} else if (column == sets.size() - 1) {
				if (index[column] < maxPosition(column)) {
					index[column]++;
				} else {
					rollOver(column);
				}

			} else {
				increment(column + 1);
			}
		}
		
		private int maxPosition(int column) {
			return withMissing ? sets.get(column).size() : sets.get(column).size() - 1;
		}
		
		private void rollOver(int column) {
			if (column > 0) {
				// rollover the previous column if it is finished
				if (index[column-1] >= maxPosition(column-1)) {
					rollOver(column-1);
				} else {
					// reset all deeper columns
					for (int c = column; c < index.length; c++) {
						index[c] = 0;
					}
					index[column-1]++;
				}
			} else { // attempt to roll over first column means we're done
				hasNext = false;
			}
		}
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
		for (Set<Integer> s : (withMissingElements ? samples.withMissingElements() : samples)) {
			print(s);
		}
	}
	
	public static void main(String[] args) {
		simpleTest(new Integer[][] {{0,1,2}, {3,4,5}, {6,7,8}, {9,10,11}}, true);
		simpleTest(new Double[][] {{0.1,0.2}, {0.3,0.4}, {0.5,0.6}, {0.7,0.8}}, true);
	}
}
