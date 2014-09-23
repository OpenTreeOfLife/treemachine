package opentree.constants;

/**
 * Graph properties are stored in node 0. These are basic pieces of information used to identify the graph itself. For more information see:
 * 
 * https://github.com/OpenTreeOfLife/treemachine/wiki/Vocabulary
 * 
 * @author cody hinchliff
 *
 */
public enum GraphProperty {
	
	GRAPH_ROOT_NODE_NAME ("graphRootNodeName", String.class, "The value of the `name` property of the root node of the graph."),
	GRAPH_ROOT_NODE_ID ("graphRootNodeId", String.class, "The neo4j node id of the root node of the graph. Used to gain access to that node."),
	GRAPH_ROOT_NODE_TAXONOMY ("graphRootNodeTaxonomy", String.class, "The version of the taxonomy used to initialize the graph.");
	

	public String propertyName;
	public final Class<?> type;
	public final String description;
    
    GraphProperty(String propertyName, Class<?> T, String description) {
        this.propertyName = propertyName;
        this.type = T;
        this.description = description;
    }
}
