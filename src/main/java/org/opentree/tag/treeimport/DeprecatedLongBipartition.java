package org.opentree.tag.treeimport;

import java.util.Map;

import org.opentree.bitarray.MutableCompactLongSet;

/**
 * Has been superseded by ImmutableLongBipartition. Left temporarily for reference.
 * @author cody
 *
 */
@Deprecated
public class DeprecatedLongBipartition {

	private MutableCompactLongSet ingroup;
	private MutableCompactLongSet outgroup;

	public DeprecatedLongBipartition(long[] ingroup, long[] outgroup) {
		this.ingroup = new MutableCompactLongSet(ingroup);
		this.outgroup = new MutableCompactLongSet(outgroup);
	}

	public DeprecatedLongBipartition(MutableCompactLongSet ingroup, MutableCompactLongSet outgroup) {
		this.ingroup = new MutableCompactLongSet(ingroup);
		this.outgroup = new MutableCompactLongSet(outgroup);
	}

	public DeprecatedLongBipartition(DeprecatedLongBipartition original) {
		ingroup = new MutableCompactLongSet(original.ingroup);
		outgroup = new MutableCompactLongSet(original.outgroup);
	}

	public MutableCompactLongSet ingroup() {
		return ingroup;
	}

	public MutableCompactLongSet outgroup() {
		return outgroup;
	}

	/**
	 * If there is no conflict between this bipartition and the passed bipartition, and if this bipartition has at least one element
	 * in common with the passed bipartition (in either the ingroup or outgroup), then return a bipartition whose ingroup is the union
	 * of the ingroups of this bipartition and the passed bipartition, and whose outgroup is the union of the outgroups of this
	 * bipartition and the passed bipartition. If there is conflict or if the bipartitions do not overlap at all, then return null.
	 * 
	 * @param that
	 * @return
	 */
	public DeprecatedLongBipartition sum(DeprecatedLongBipartition that) {

		if (!this.isCompatibleWith(that) || !this.overlapsWith(that)) {
			return null;
		}

		if (this.equals(that)) {
			return new DeprecatedLongBipartition(ingroup, outgroup);
		}

		MutableCompactLongSet sumIn = new MutableCompactLongSet();
		sumIn.addAll(this.ingroup);
		sumIn.addAll(that.ingroup);

		MutableCompactLongSet sumOut = new MutableCompactLongSet();
		sumOut.addAll(this.outgroup);
		sumOut.addAll(that.outgroup);

		return new DeprecatedLongBipartition(sumIn, sumOut);
	}
	
	/**
	 * This differs from sum in that it doesn't not return a bipart if it is equal
	 * and it requires overlap with the ingroups not ingroups or outgroups
	 * 
	 * @param that
	 * @return
	 */
	public DeprecatedLongBipartition strictSum(DeprecatedLongBipartition that) {

		if (!this.isCompatibleWith(that))
			return null;
		
		if(!this.ingroup.containsAny(that.ingroup))
			return null;

		if (this.equals(that))
			return null;
		
		MutableCompactLongSet sumIn = new MutableCompactLongSet();
		sumIn.addAll(this.ingroup);
		sumIn.addAll(that.ingroup);

		MutableCompactLongSet sumOut = new MutableCompactLongSet();
		sumOut.addAll(this.outgroup);
		sumOut.addAll(that.outgroup);

		return new DeprecatedLongBipartition(sumIn, sumOut);
	}

	/**
	 * Returns true if there is no conflict between these bipartitions. More formally, let A be the bipartition on which
	 * the method is called and B the bipartition passed to the method. Then, we return true if and only if (1) the
	 * intersection of A's ingroup and B's outgroup is null, and (2) the intersection of B's ingroup and A's outgroup is
	 * null.<br/><br/>
	 * 
	 * This is a symmetrical comparison. That is, A is compatible with B if and only if B is compatible with A.
	 * 
	 * @param that
	 * @return
	 */
	public boolean isCompatibleWith(DeprecatedLongBipartition that) {
		return ! (this.ingroup.containsAny(that.outgroup) || this.outgroup.containsAny(that.ingroup));
	}

	/**
	 * Returns true if the ingroups is completely contained within the ingroup and the outgroup is contained completely 
	 * within the outgroup (a potential lica). 
	 */
	public boolean containsAll(DeprecatedLongBipartition that){
		if (this.ingroup.containsAny(that.outgroup) || this.outgroup.containsAny(that.ingroup))
			return false;
		if (this.ingroup.containsAll(that.ingroup) && this.outgroup.containsAll(that.outgroup))
			return true;
		return false;
	}
	
	/**
	 * Returns true if the called bipartition contains information that could be used to further resolve relationships
	 * among the ingroup of the bipartition passed to the method. This is an asymmetrical relationship--no bipartition
	 * may be a nested partition of a second bipartition which is in turn a nested partition of the first.<br/>
	 * <br/>
	 * Let B be the potentially nested bipartition and A be the bipartition it is proposed to be nested within. Then, B
	 * is a nested partition of A if and only if B's ingroup does not contain any elements in the outgroup of A (if it did
	 * then B could not be a child of A), *and* B's outgroup contains at least one element in the ingroup of A. This second
	 * condition implies that the relationships among A's ingroup can be further resolved by the inclusion of B as a
	 * child of A.
	 * 
	 * @param A
	 *            the potentially more inclusive bipartition
	 * @return	true if this bipartition is nested within A
	 */
	public boolean isNestedPartitionOf(DeprecatedLongBipartition A) {
		if (A.outgroup.containsAny(this.ingroup)) { return false; } // this cannot be nested within A
		return this.outgroup.containsAny(A.ingroup); // whether this can be used to partition A's ingroup
	}
	
	public boolean overlapsWith(DeprecatedLongBipartition that) {
		return this.ingroup.containsAny(that.ingroup) || this.ingroup.containsAny(that.outgroup) ||
			   this.outgroup.containsAny(that.ingroup) || this.outgroup.containsAny(that.outgroup);
	}

	public boolean hasIdenticalTaxonSetAs(DeprecatedLongBipartition that) {
		boolean result = false;
		if (this.ingroup.size() + this.outgroup.size() == that.ingroup.size() + that.outgroup.size()) {
			
			MutableCompactLongSet a = new MutableCompactLongSet(this.ingroup);
			a.addAll(this.outgroup);
			
			MutableCompactLongSet b = new MutableCompactLongSet(that.ingroup);
			b.addAll(that.outgroup);

			result = b.equals(a);
		}
		return result;
	}
	
	@Override
	public boolean equals(Object that) {
		boolean result = false;
		if (that != null && that instanceof DeprecatedLongBipartition) {
			DeprecatedLongBipartition b = (DeprecatedLongBipartition) that;
			result = ingroup.size() == b.ingroup.size()
					&& outgroup.size() == b.outgroup.size()
					&& ingroup.equals(b.ingroup)
					&& outgroup.equals(b.outgroup);
		}
		return result;
	}

	@Override
	public int hashCode() {
		// have not tested this hash function for performance. be wary.
		// attempt to produce more even distribution of ints:
		return ((ingroup.hashCode() * 71) ^ (outgroup.hashCode()) * 29); 
	}
		
	@Override
	public String toString() {
		StringBuffer s = new StringBuffer();
		s.append(ingroup.toString());
		s.append(" | ");
		s.append(outgroup.toString());
		return s.toString();
	}
	
	public String toString(Map<Long, Object> names) {
		StringBuffer s = new StringBuffer();
		s.append(ingroup.toString(names));
		s.append(" | ");
		s.append(outgroup.toString(names));
		return s.toString();
	}

	public static void main(String[] args) {
		nestedTest(new long[][] {{1,2},{3}}, new long[][] {{1,4},{2}}, true);
		nestedTest(new long[][] {{0,2},{3}}, new long[][] {{0,1,2,3},{}}, false);
		equalsTest(new long[][] {{0,2},{3}}, new long[][] {{2,0},{3}}, true);
		equalsTest(new long[][] {{3},{0,2}}, new long[][] {{2,0},{3}}, false);
		equalsTest(new long[][] {{},{}}, new long[][] {{},{}}, true);
		equalsTest(new long[][] {{1,3},{2}}, new long[][] {{1,3},{3}}, false);
		equalsTest(new long[][] {{},{0}}, new long[][] {{},{0,0,0,0,0,0}}, true);
		equalsTest(new long[][] {{0},{}}, new long[][] {{},{0,0,0,0,0,0}}, false);
		equalsTest(new long[][] {{1,2},{0}}, new long[][] {{2,1},{0,0,0,0,0,0}}, true);
	}
	
	private static void nestedTest(long[][] parent, long[][] child, boolean valid) {
		DeprecatedLongBipartition A = new DeprecatedLongBipartition(parent[0], parent[1]);
		DeprecatedLongBipartition B = new DeprecatedLongBipartition(child[0], child[1]);
		assert B.isNestedPartitionOf(A) == valid;
	}
	
	private static void equalsTest(long[][] parent, long[][] child, boolean valid) {
		DeprecatedLongBipartition A = new DeprecatedLongBipartition(parent[0], parent[1]);
		DeprecatedLongBipartition B = new DeprecatedLongBipartition(child[0], child[1]);
		System.out.println(A + " == " + B + " ? " + (B.equals(A) && A.equals(B)));
		assert (B.equals(A) && A.equals(B)) == valid;
		System.out.println(A.hashCode() + " == " + B.hashCode() + " ? " + (B.hashCode() == A.hashCode()));
		assert B.hashCode() == A.hashCode() == valid;
		System.out.println();
	}
	
}