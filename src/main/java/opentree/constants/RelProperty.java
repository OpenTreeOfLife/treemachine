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
	
	// TODO should move all the descriptions into javadoc. They never need to be accessible to users, but are helpful for dev

	// TAXCHILDOF relationships
	/** The tax_uid of the child of this relationship. Used for taxnonomic rels. */
	CHILD_TAX_UID ("childid", String.class),
	
	/** The tax_uid of the parent of this relationship. Used for taxonomic rels. */
	PARENT_TAX_UID ("parentid", String.class),
	
	// STREECHILDOF and STREEEXEMPLAROF relationships
	/** The name of the source supporting this relationship */
	SOURCE ("source", String.class),

	// STREECHILDOF
	/** A reference to alternate LICA mappings that are associated with this LICA mapping. There can be at least 1 and as many as there are ambiguous mappings. */
	LICAS ("licas", long[].class),
	
	/** The list of STREECHILDOF relationships that are involved with this mapping. */
	INCLUSIVE_RELIDS ("inclusive_relids", long[].class),
	
	/** The ids of all graph nodes mapped to the child of this edge in the original source tree. */
	EXCLUSIVE_MRCA ("exclusive_mrca", long[].class),
	
	/** The ids of all graph nodes mapped to nodes in the original source tree that are *not* descended from this edge. */
	EXCLUSIVE_OUTMRCA ("exclusive_outmrca", long[].class),
	
	/** The list of the taxa (with the list being the original mapping to the Long ids of the nodes in the graph). This is currently the same as the metadata node original_taxa_map and can be deleted once the references are corrected in the code */
	ROOT_EXCLUSIVE_MRCA ("root_exclusive_mrca", long[].class),
	
	CHILD_IS_TIP ("child_is_tip", boolean.class),

	// SYNTHCHILDOF relationships
	/** The name used to identify this synthetic tree */
	NAME ("name", String.class),
	
	/** The phylografter ids of the source trees supporting this synthetic rel. Format is <studyid>_<treeid> */
	SUPPORTING_SOURCES ("supporting_sources", String[].class);
	
	public String propertyName;
	public final Class<?> type;
//	public final String description;
    
    RelProperty(String propertyName, Class<?> T/*, String description*/) {
        this.propertyName = propertyName;
        this.type = T;
//        this.description = description;
    }	
}
