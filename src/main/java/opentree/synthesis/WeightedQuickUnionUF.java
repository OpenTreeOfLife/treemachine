package opentree.synthesis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.opentree.bitarray.ImmutableCompactLongSet;
import org.opentree.bitarray.LongSet;
import org.opentree.bitarray.MutableCompactLongSet;

//import StdIn;
//import StdOut;
//import WeightedQuickUnionUF;

/****************************************************************************
 *  Compilation:  javac WeightedQuickUnionUF.java
 *  Execution:  java WeightedQuickUnionUF < input.txt
 *  Dependencies: StdIn.java StdOut.java
 *
 *  Weighted quick-union (without path compression).
 *
 ****************************************************************************/

public class WeightedQuickUnionUF implements Iterable<Integer> {
    private int[] id;    // id[i] = parent of i
    private int[] sz;    // sz[i] = number of objects in subtree rooted at i
//    private int count;   // number of components

    private Map<Integer, MutableCompactLongSet> sets;
    
    // Create an empty union find data structure with N isolated sets.
    public WeightedQuickUnionUF(int N) {
//        count = N;
        id = new int[N];
        sz = new int[N];
    	sets = new HashMap<Integer, MutableCompactLongSet>(N);
        for (int i = 0; i < N; i++) {
            id[i] = i;
            sz[i] = 1;
            MutableCompactLongSet s = new MutableCompactLongSet();
        	s.add(i);
        	sets.put(i, s);
        }
    }

    // Return the number of disjoint sets.
    public int count() {
//        return count;
    	return sets.size();
    }

    // Return component identifier for component containing p
    public int find(int p) {
        while (p != id[p])
            p = id[p];
        return p;
    }

   // Are objects p and q in the same set?
    public boolean connected(int p, int q) {
        return find(p) == find(q);
    }
  
    // Replace sets containing p and q with their union.
    public int union(int p, int q) {
        int i = find(p);
        int j = find(q);
        if (i == j) return find(i);
       
        // make smaller root point to larger one
        int root;
        if   (sz[i] < sz[j]) {
        	id[i] = j;
        	sz[j] += sz[i];
        	sets.get(j).addAll(sets.get(i));
        	sets.remove(i);
        	root = j;
        } else {
        	id[j] = i;
        	sz[i] += sz[j];
        	sets.get(i).addAll(sets.get(j));
        	sets.remove(j);
        	root = i;
        }
        return root;
//        count--;
    }
    
    public LongSet getSet(int rootId) {
    	return new ImmutableCompactLongSet(sets.get(rootId));
    }

	@Override
	public Iterator<Integer> iterator() {
		return sets.keySet().iterator();
	}

    public static void main(String[] args) {
    	WeightedQuickUnionUF u = new WeightedQuickUnionUF(10);
    	u.union(1, 2);
    	u.union(3, 4);
    	u.union(5, 6);
    	u.union(7, 8);
    	u.union(9, 0);
    	for (int i : u) { System.out.println(u.getSet(i)); }
    	u.union(1, 3);
    	u.union(5, 7);
    	for (int i : u) { System.out.println(u.getSet(i)); }
    	u.union(5, 9);
    	for (int i : u) { System.out.println(u.getSet(i)); }
    	u.union(1, 0);
    	for (int i : u) { System.out.println(u.getSet(i)); }
    }

}

