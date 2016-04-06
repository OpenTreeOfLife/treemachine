package opentree.constants;

/**
 * Node properties as defined within the graph itself. These are stored in graph nodes.
 * 
 * @author cody
 *
 */
public enum NodeProperty {

    // NEW: either the OTTID (from taxonomy) or mrca statement (e.g. mrcaott86522ott850264)
    OT_NODE_ID ("ot_node_id", String.class, "The OpenTree node identifier."),
    
    // ===== Taxonomy nodes
    NAME ("name", String.class, "The taxonomic name of this node. Generally used only for taxonomy nodes..."),
    NAME_UNIQUE ("unique_name", String.class, "A unique identifier made using taxonomic information. Used only for taxonomy nodes."),
    TAX_UID ("tax_uid", String.class, "The OTT uid of the node. Used only for taxonomy nodes."),
    TAX_RANK ("tax_rank", String.class, "The taxonomic rank of this node. Used for taxonomy nodes."),
    TAX_SOURCE ("tax_source", String.class, "Contains identifying information for this node in the taxonomy(ies) that define it. A string of the format \"<sourcename>:<taxid>, etc.\" where <taxid> is the id of this taxon for the indicated source. Used for taxonomy nodes."); 
    
    public String propertyName;
    public final Class<?> type;
    public final String description;
    
    NodeProperty(String propertyName, Class<?> T, String description) {
        this.propertyName = propertyName;
        this.type = T;
        this.description = description;
    }
}
