package opentree.constants;

/**
 * Source properties as defined by the ottol controlled vocabulary. These are stored in metadata nodes; this enum is used
 * whenever those are accessed, e.g. synthesis (SourcePropertyFilterCriterion.class and SourcePropertySelectCriterion.class),
 * export (ArgusRepresentationConverter.class). For more information see:
 * 
 * https://github.com/OpenTreeOfLife/treemachine/wiki/Vocabulary
 * 
 * @author cody hinchliff
 *
 */
public enum SourceProperty {
    
    NAME ("name", String.class, "Synthetic tree id"),
    
    // ===== All metadata nodes
    SOURCE ("source", String.class, "The name of this source. Used for all sources."),

    // ===== Study metadata nodes
    NEWICK_STRING("newick", String.class, "The newick string that was originally imported for the file. Used for study sources."),
    CURATOR_NAME("ot:curatorName", String.class, "The name of curator who uploaded the study. Used for study sources."),
    DATA_DEPOSIT("ot:dataDeposit", String.class, "A URI (or other identifier) for the published data. Used for study sources."),
    PUBLICATION_REFERENCE ("ot:studyPublicationReference", String.class, "The citation string. Used for study sources."),
    STUDY_ID ("ot:studyId", String.class, "The phylografter study id. Used for study sources."),
    STUDY_PUBLICATION("ot:studyPublication", String.class, "A URI (or other identifier) for the published study itself. Used for study sources."),
    YEAR ("ot:studyYear", Long.class, "The year the study was created. Used for study sources.");  // TODO: currently stored as a long, but should be int

    // Questionable/undetermined:
//    TAXON_MAP("original_taxa_map", ????, "The list of the taxa (with the list being the original mapping to the Long ids of the nodes in the graph)"),
    
    public final String propertyName;
    public final Class<?> type;
    public final String description;
    
    SourceProperty(String propertyName, Class<?> T, String description) {
        this.propertyName = propertyName;
        this.type = T;
        this.description = description;
    }
}
