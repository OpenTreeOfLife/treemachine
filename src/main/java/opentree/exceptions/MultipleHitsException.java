package opentree.exceptions;

/**
 * This class thrown when we find multiple matches to a search where we were expecting only one,
 * indicating problem with the underlying data.
 */
public class MultipleHitsException extends java.lang.IllegalStateException {

    private static final long serialVersionUID = 1L;

    String error;

    public MultipleHitsException() {
        super();
        error = "";
    }
    
    public MultipleHitsException(Object searchTerm) {
        super();
        error = "The search for '" + String.valueOf(searchTerm) + "' produced multiple hits, but only one one hit was expected.";
    }
    
    @Override
    public String toString() {
    	return error;
    }
}
