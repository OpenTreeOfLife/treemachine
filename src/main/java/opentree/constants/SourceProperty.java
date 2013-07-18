package opentree.constants;

/**
 * Source properties as defined by the ottol controlled vocabulary. These are used whenever source properties are accessed, e.g. synthesis
 * (SourcePropertyFilterCriterion.class and SourcePropertySelectCriterion.class), export (ArgusRepresentationConverter.class).
 * 
 * @author cody hinchliff
 *
 */
public enum SourceProperty {

    CURATOR_NAME("ot:curatorName", String.class), // name of curator who uploaded the study
    DATA_DEPOSIT("ot:dataDeposit", String.class), // a URI (or other identifier) for the published data
    PUBLICATION_REFERENCE ("ot:studyPublicationReference", String.class), // the citation string
    STUDY_ID ("ot:studyId", String.class), // the phylografter study id
    STUDY_PUBLICATION("ot:studyPublication", String.class), // a URI (or other identifier) for the published study itself

    // TODO: it seems like this should be an integer, but currently it is being stored as a long so that is what we are using here. return this to int when/if this gets changed
    YEAR ("ot:studyYear", Long.class); // the year the study was created
    
    public final String propertyName;
    public final Class<?> type;
    
    SourceProperty(String propertyName, Class<?> T) {
        this.propertyName = propertyName;
        this.type = T;
    }
}
