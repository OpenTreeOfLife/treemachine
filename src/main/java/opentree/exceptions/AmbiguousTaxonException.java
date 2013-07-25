package opentree.exceptions;

/**
 * This class is just a wrapper for MultipleHitsException, but is reserved for the case when we find multiple
 * hits for a taxon search where we were expecting only one (e.g. a search for a tax_uid or taxon name).
 */
public class AmbiguousTaxonException extends MultipleHitsException {

	private static final long serialVersionUID = 1L;

    public AmbiguousTaxonException() {
        super();
    }
    
    public AmbiguousTaxonException(Object searchTerm) {
    	super(searchTerm);
    }
}