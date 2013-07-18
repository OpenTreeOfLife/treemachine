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

	SOURCE ("source", String.class, "The name of the source supporting this relationship"),
	CHILD_TAX_UID ("childid", String.class, "The tax_uid of the child of this relationship. Used for taxnonomic rels."),
	PARENT_TAX_UID ("parentid", String.class, "The tax_uid of the parent of this relationship. Used for taxonomic rels.");
	
	public String propertyName;
	public final Class<?> type;
	public final String description;
    
    RelProperty(String propertyName, Class<?> T, String description) {
        this.propertyName = propertyName;
        this.type = T;
        this.description = description;
    }	
}
