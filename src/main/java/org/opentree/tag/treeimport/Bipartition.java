package org.opentree.tag.treeimport;

import org.opentree.bitarray.TLongBitArraySet;

public class Bipartition {

	private final TLongBitArraySet ingroup;
	private final TLongBitArraySet outgroup;
	
	public Bipartition(long[] ingroup, long[] outgroup) {
		this.ingroup = new TLongBitArraySet(ingroup);
		this.outgroup = new TLongBitArraySet(outgroup);
	}

	public Bipartition(TLongBitArraySet ingroup, TLongBitArraySet outgroup) {
		this.ingroup = new TLongBitArraySet(ingroup);
		this.outgroup = new TLongBitArraySet(outgroup);
	}

	public Bipartition sum(Bipartition that) {
		if (this.equals(that)) {
			return new Bipartition(ingroup, outgroup);
		}
		
		if (this.ingroup.containsAny(that.outgroup) ||
		    this.outgroup.containsAny(that.ingroup)) {
			return null;
		
		} else {
			TLongBitArraySet sumIn = new TLongBitArraySet();
			sumIn.addAll(this.ingroup);
			sumIn.addAll(that.ingroup);
			
			TLongBitArraySet sumOut = new TLongBitArraySet();
			sumOut.addAll(this.outgroup);
			sumOut.addAll(that.outgroup);
			
			return new Bipartition(sumIn, sumOut);
		}
	}
	
	@Override
	public boolean equals(Object that) {
		boolean result = false;
		if (that instanceof Bipartition) {
			Bipartition b = (Bipartition) that;
			result = ingroup.size() == b.ingroup.size() && outgroup.size() == b.outgroup.size() &&
					 ingroup.containsAll(b.ingroup) && outgroup.containsAll(b.outgroup);
		}
		return result;
	}
	
	@Override
	public int hashCode() {
		// have not tested this hash function for performance. be wary.
		long h = 0;
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
}
