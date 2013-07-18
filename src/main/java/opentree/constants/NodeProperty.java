package opentree.constants;

/**
 * Node properties as defined within the graph itself. These are used whenever node properties are accessed
 * within the graph.
 * 
 * @author cody hinchliff
 *
 */
public enum NodeProperty {

	GRAPH_ROOT_NODE_NAME ("graphRootNodeName", String.class),
	GRAPH_ROOT_NODE_ID ("graphRootNodeId", String.class),
	NAME ("name", String.class),
	TAX_UID ("tax_uid", String.class);
	
	public String propertyName;
	public final Class<?> type;
    
    NodeProperty(String propertyName, Class<?> T) {
        this.propertyName = propertyName;
        this.type = T;
    }
}
