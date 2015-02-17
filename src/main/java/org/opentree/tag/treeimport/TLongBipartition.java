package org.opentree.tag.treeimport;

import java.util.Map;

import org.opentree.bitarray.FastLongSet;
import org.opentree.bitarray.TLongBitArraySet;

public class TLongBipartition {

	// TODO: just use bitset instead of TLongBitArraySet to reduce memory footprint
	
//	private TLongBitArraySet ingroup;
//	private TLongBitArraySet outgroup;
	private FastLongSet ingroup;
	private FastLongSet outgroup;

	public TLongBipartition(long[] ingroup, long[] outgroup) {
		this.ingroup = new FastLongSet(ingroup);
		this.outgroup = new FastLongSet(outgroup);
	}

	public TLongBipartition(FastLongSet ingroup, FastLongSet outgroup) {
		this.ingroup = new FastLongSet(ingroup);
		this.outgroup = new FastLongSet(outgroup);
	}

	public TLongBipartition(TLongBipartition nested) {
		ingroup = new FastLongSet(nested.ingroup);
		outgroup = new FastLongSet(nested.outgroup);
	}

	public TLongBitArraySet ingroup() {
		return ingroup;
	}

	public TLongBitArraySet outgroup() {
		return outgroup;
	}

	public TLongBipartition sum(TLongBipartition that) {

		if (!this.isCompatibleWith(that) || !this.overlapsWith(that)) {
			return null;
		}

		if (this.equals(that)) {
			return new TLongBipartition(ingroup, outgroup);
		}

		TLongBitArraySet sumIn = new TLongBitArraySet();
		sumIn.addAll(this.ingroup);
		sumIn.addAll(that.ingroup);

		TLongBitArraySet sumOut = new TLongBitArraySet();
		sumOut.addAll(this.outgroup);
		sumOut.addAll(that.outgroup);

		return new TLongBipartition(sumIn, sumOut);
	}

	/**
	 * Returns true if there is no conflict between these bipartitions. More formally, let A be the bipartition on which
	 * the method is called and B the bipartition passed to the method. Then, we return true if and only if (1) the
	 * intersection of A's ingroup and B's outgroup is null, and (2) the intersection of B's ingroup and A's outgroup is
	 * null.
	 * 
	 * @param that
	 * @return
	 */
	public boolean isCompatibleWith(TLongBipartition that) {
		return !(this.ingroup.containsAny(that.outgroup) || this.outgroup.containsAny(that.ingroup));
	}

	/**
	 * Returns true if the ingroups is completely contained within the ingroup and the outgroup is contained completely 
	 * within the outgroup (a potential lica). 
	 */
	public boolean containsAll(TLongBipartition that){
		if (this.ingroup.containsAny(that.outgroup) || this.outgroup.containsAny(that.ingroup))
			return false;
		if (this.ingroup.containsAll(that.ingroup) && this.outgroup.containsAll(that.outgroup))
			return true;
		return false;
	}
	
	
	/**
	 * Returns true if the called bipartition contains information that could be used to further resolve relationships
	 * among the ingroup of the bipartition passed to the method. This is an asymmetrical relationship--no bipartition
	 * may be a resolving child of a second bipartition which is in turn a resolving child of the first.<br/>
	 * <br/>
	 * Let B be the potentially nested bipartition and A be the bipartition it is proposed to be nested within. Then, B
	 * is nested within A if and only if B's ingroup does not contain anything in the outgroup of A (if it did then B
	 * could not be a child of A), *and* A's outgroup contains at least something in the ingroup of B. This second
	 * condition implies that the relationships among A's ingroup can be further resolved by the inclusion of B as a
	 * child of A.
	 * 
	 * @param A
	 *            the potentially more inclusive bipartition
	 * @return	true if this bipartition is nested within A
	 */
	public boolean isNestedPartitionOf(TLongBipartition B) {
		if (B.outgroup.containsAny(this.ingroup)) {
			return false; // a cannot be nested within b
		} 
		return B.ingroup.containsAny(this.outgroup); // a can be nested within b
	}
	
	public boolean overlapsWith(TLongBipartition that) {
		return this.ingroup.containsAny(that.ingroup) || this.ingroup.containsAny(that.outgroup) ||
			   this.outgroup.containsAny(that.ingroup) || this.outgroup.containsAny(that.outgroup);
	}

	@Override
	public boolean equals(Object that) {
		boolean result = false;
		if (that != null && that instanceof TLongBipartition) {
			TLongBipartition b = (TLongBipartition) that;
			result = ingroup.size() == b.ingroup.size() && outgroup.size() == b.outgroup.size()
					&& ingroup.containsAll(b.ingroup) && outgroup.containsAll(b.outgroup);
		}
		return result;
	}

	@Override
	public int hashCode() {
		// have not tested this hash function for performance. be wary.
		long h = 1;
		for (long p : ingroup) { h = (h * (59 + p)) + p; }
		for (long p : outgroup) { h = (h * (73 + p)) + p; }
		return (int) h;
	}

	@Override
	public String toString() {
		StringBuffer s = new StringBuffer();
		s.append("{");
		boolean first = true;
		for (long l : ingroup) {
			if (first) {
				first = false;
			} else {
				s.append(", ");
			}
			s.append(l);
		}
		s.append("} | {");
		first = true;
		for (long l : outgroup) {
			if (first) {
				first = false;
			} else {
				s.append(", ");
			}
			s.append(l);
		}
		s.append("}");
		return s.toString();
	}
	
//	public String toString(Map<Long, String> names) {
	public String toString(Map<Long, Object> names) {
		StringBuffer s = new StringBuffer();
		s.append("{");
		boolean first = true;
		for (long l : ingroup) {
			if (first) {
				first = false;
			} else {
				s.append(", ");
			}
			s.append(names.get(l));
		}
		s.append("} | {");
		first = true;
		for (long l : outgroup) {
			if (first) {
				first = false;
			} else {
				s.append(", ");
			}
			s.append(names.get(l));
		}
		s.append("}");
		return s.toString();
	}

	public static void main(String[] args) {
		nestedTest(new long[][] {{1,2},{3}}, new long[][] {{1,4},{2}}, true);
		nestedTest(new long[][] {{0,2},{3}}, new long[][] {{0,1,2,3},{}}, false);
	}
	
	private static void nestedTest(long[][] parent, long[][] child, boolean valid) {
		TLongBipartition A = new TLongBipartition(parent[0], parent[1]);
		TLongBipartition B = new TLongBipartition(child[0], child[1]);
		assert B.isNestedPartitionOf(A) == valid;
	}
	
}