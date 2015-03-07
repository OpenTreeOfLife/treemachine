package org.opentree.tag.treeimport;

import java.util.Map;

import org.opentree.bitarray.LongSet;
import org.opentree.bitarray.MutableCompactLongSet;

public abstract interface LongBipartition {

	public LongSet ingroup();
	public LongSet outgroup();
	public LongBipartition sum(LongBipartition that);
	public LongBipartition strictSum(LongBipartition that);
	public boolean isCompatibleWith(LongBipartition that);
	public boolean containsAll(LongBipartition that);
	public boolean isNestedPartitionOf(LongBipartition A);
	public boolean overlapsWith(LongBipartition that);
	public boolean hasIdenticalTaxonSetAs(LongBipartition that);
	public String toString(Map<Long, Object> names);

}
