package opentree.constants;

/**
 * Node properties as defined within the graph itself. These are stored in graph nodes. Different types
 * of nodes may have different properties. For more information see:
 * 
 * https://github.com/OpenTreeOfLife/treemachine/wiki/Vocabulary
 * 
 * @author cody hinchliff
 *
 */
public enum NodeProperty {

	// ===== All nodes
	MRCA ("mrca", long[].class, "An array containing the node ids of all descendant nodes of this node. Used in graph algorithms."),
	NESTED_MRCA ("nested_mrca", long[].class, ""),
	
	// ===== Taxonomy nodes
	NAME ("name", String.class, "The taxonomic name of this node. Generally used only for taxonomy nodes..."),
	NAME_UNIQUE ("uniqname", String.class, "A unique identifier made using taxonomic information. Used only for taxonomy nodes."),
	TAX_UID ("tax_uid", String.class, "The OTT uid of the node. Used only for taxonomy nodes."),
	TAX_PARENT_UID ("tax_parent_uid", String.class, "The UID of the taxonomic parent of this node. Used for taxonomy nodes."),
	TAX_RANK ("tax_rank", String.class, "The taxonomic rank of this node. Used for taxonomy nodes."),
	TAX_SOURCE ("tax_source", String.class, "Contains identifying information for this node in the taxonomy(ies) that define it. A string of the format \"<sourcename>:<taxid>, etc.\" where <taxid> is the id of this taxon for the indicated source. Used for taxonomy nodes."),
	
	// ===== Synonym nodes
	NAMETYPE ("nametype", String.class, "The type of synonym. Used for synonym nodes"),
	SOURCE ("source", String.class, "The taxonomic source of this synonym."),

	// ===== Synthetic tree nodes
	DESCENDANT_TIPS_IN_DRAFT_TREE ("descendant_tips_in_draft_tree", Long.class, "The number of descendant tips in the draft tree below this node");
	
	public String propertyName;
	public final Class<?> type;
	public final String description;
    
    NodeProperty(String propertyName, Class<?> T, String description) {
        this.propertyName = propertyName;
        this.type = T;
        this.description = description;
    }
}
