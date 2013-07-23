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
	LICAS ("licas", long[].class, "?"),
	INCLUSIVE_RELIDS ("inclusive_relids", long[].class, "?"),
	EXCLUSIVE_MRCA ("exclusive_mrca", long[].class, "?"),
	ROOT_EXCLUSIVE_MRCA ("root_exclusive_mrca", long[].class, "?"),

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
