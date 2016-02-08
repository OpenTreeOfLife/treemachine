package opentree.constants;

/**
 * Node properties as defined within the graph itself. These are stored in graph nodes. Different types
 * of nodes may have different properties. For more information see:
 * 
 * https://github.com/OpenTreeOfLife/treemachine/wiki/Vocabulary
 * 
 * @author cody
 *
 */
public enum NodeProperty {

    // ===== All nodes
    MRCA ("mrca", long[].class, "An array containing the node ids of all descendant nodes of this node. Used in graph algorithms."),
    // TODO: get rid of the following
    OUTMRCA ("outmrca", long[].class, "An array containing the node ids of all nodes known *not* to descend from this node. Used in graph algorithms."),
    NESTED_MRCA ("nested_mrca", long[].class, "??"),
    EXCLUSIVE_MRCA ("exclusive_mrca", long[].class, "An array containing the node ids of all *terminal* graph nodes that descend from all the graph nodes that descend from the *source tree* node that was mapped to the child node of this rel. Basically, the ingroup of the corresponding source tree node, but inclusive of all the terminal graph nodes in the case that the source tree node was mapped to a deeper taxon."),
    EXCLUSIVE_OUTMRCA ("exclusive_outmrca", long[].class, "Analogous to EXCLUSIVE_MRCA but for the outgroup instead."),
    
    
    
    
    // NEW: either the OTTID (from taxonomy) or mrca statement (e.g. mrcaott86522ott850264)
    OT_NODE_ID ("ot_node_id", String.class, "The OpenTree node identifier."),
    
    
    
    
    // TODO: get rid of this
    SYNTHESIZED ("synthesized", boolean.class, "Whether or not synthesis has been finished for this node."),
    
    // ===== Taxonomy nodes
    NAME ("name", String.class, "The taxonomic name of this node. Generally used only for taxonomy nodes..."),
    NAME_UNIQUE ("uniqname", String.class, "A unique identifier made using taxonomic information. Used only for taxonomy nodes."),
    TAX_UID ("tax_uid", String.class, "The OTT uid of the node. Used only for taxonomy nodes."),
    // don't need this
    TAX_PARENT_UID ("tax_parent_uid", String.class, "The UID of the taxonomic parent of this node. Used for taxonomy nodes."),
    TAX_RANK ("tax_rank", String.class, "The taxonomic rank of this node. Used for taxonomy nodes."),
    TAX_SOURCE ("tax_source", String.class, "Contains identifying information for this node in the taxonomy(ies) that define it. A string of the format \"<sourcename>:<taxid>, etc.\" where <taxid> is the id of this taxon for the indicated source. Used for taxonomy nodes."),
    
    // ===== Synonym nodes
    // TODO: get rid of the following
    NAMETYPE ("nametype", String.class, "The type of synonym. Used for synonym nodes"),
    SOURCE ("source", String.class, "The taxonomic source of this synonym.");
    
    public String propertyName;
    public final Class<?> type;
    public final String description;
    
    NodeProperty(String propertyName, Class<?> T, String description) {
        this.propertyName = propertyName;
        this.type = T;
        this.description = description;
    }
}
