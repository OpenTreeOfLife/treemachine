package opentree.constants;

public enum NodeProperty {

	GRAPH_ROOT_NODE_NAME ("graphRootNodeName"),
	GRAPH_ROOT_NODE_ID ("graphRootNodeId"),
	NAME ("name"),
	TAX_UID ("tax_uid");
	
	public String propertyName;
	
	NodeProperty(String propertyName) {
		this.propertyName = propertyName;
	}
}
