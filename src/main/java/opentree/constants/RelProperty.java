package opentree.constants;

/**
 * Relationship properties as defined within the graph itself. These are stored in relationships. Different
 * types of relationships may have different properties. For more information see:
 * 
 * https://github.com/OpenTreeOfLife/treemachine/wiki/Vocabulary
 * 
 * @author cody hinchliff
 *
 */
public enum RelProperty {

	// TAXCHILDOF relationships
	CHILD_TAX_UID ("childid", String.class, "The tax_uid of the child of this relationship. Used for taxnonomic rels."),
	PARENT_TAX_UID ("parentid", String.class, "The tax_uid of the parent of this relationship. Used for taxonomic rels."),
	
	// STREECHILDOF relationships
	SOURCE ("source", String.class, "The name of the source supporting this relationship"),
	LICAS ("licas", long[].class, "A reference to alternate LICA mappings that are associated with this LICA mapping. There can be at least 1 and as many as there are ambiguous mappings."),
	INCLUSIVE_RELIDS ("inclusive_relids", long[].class, "The list of STREECHILDOF relationships that are involved with this mapping."),
	EXCLUSIVE_MRCA ("exclusive_mrca", long[].class, "The list of mrca descendants that are exclusive to this node."),
	ROOT_EXCLUSIVE_MRCA ("root_exclusive_mrca", long[].class, "The list of the taxa (with the list being the original mapping to the Long ids of the nodes in the graph). This is currently the same as the metadata node original_taxa_map and can be deleted once the references are corrected in the code"),

	// SYNTHCHILDOF relationships
	NAME ("name", String.class, "The name used to identify this synthetic tree"),
	SUPPORTING_SOURCES ("supporting_sources", String[].class, "The phylografter ids of the source trees supporting this synthetic rel. Format is <studyid>_<treeid>");
	
	public String propertyName;
	public final Class<?> type;
	public final String description;
    
    RelProperty(String propertyName, Class<?> T, String description) {
        this.propertyName = propertyName;
        this.type = T;
        this.description = description;
    }	
}
