package opentree.constants;

/**
 * A general-purpose container for information that does not change but does not necessarily fit into other enums.
 * May need to be refactored at some point (into more specific enums) if it gets unwieldy. This enum uses a generic
 * structure to contain any type of information. The data objects themselves are stored in the `value` parameter,
 * and the type is indicated by the type stored in `type`.
 * 
 * @author cody
 *
 */
public enum GeneralConstants {
	
	// this should not be a constant, it should be set by the user when making the draft tree. it's just here for now as a convenience
	// agreed -- we may want to have several different draft trees in the db at a time. 
	DRAFT_TREE_NAME (String.class, "opentree4.0");

    public final Class<?> type;
    public final Object value;

	GeneralConstants(Class<?> type, Object value) {
		this.type = type;
		this.value = value;
	}
}
