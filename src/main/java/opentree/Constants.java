package opentree;

public enum Constants {

	DRAFT_TREE_NAME (String.class, "otol.draft.22"),
	GRAPH_ROOT_NODE_NAME (String.class, "life");
	
    public final Class<?> type;
    public final Object value;

	Constants(Class<?> type, Object value) {
		this.type = type;
		this.value = value;
	}
}
