package opentree.synthesis;

/**
 * Source properties as defined by the ottol controlled vocabulary. These are required by the SourcePropertyFilterCriterion and
 * SourcePropertySelectCriterion to filter and select relationships, respectively.
 * 
 * @author cody hinchliff
 *
 */
public enum SourceProperty {

    YEAR ("ot:studyYear", Integer.class), // the year the study was created
    STUDYID ("ot:studyId", String.class); // the phylografter study id

    public final String propertyName;
    public final Class<?> type;
    
    SourceProperty(String propertyName, Class<?> T) {
        this.propertyName = propertyName;
        this.type = T;
    }
}
