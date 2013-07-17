package opentree.exceptions;

/**
 * This class thrown when we find multiple matches to a search where we were expecting only one,
 * and when the presence of multiple matches indicates a problem with the data.
 */
public class MultipleHitsWhenOneExpectedException extends java.lang.IllegalStateException {

    private static final long serialVersionUID = 1L;

    String error;

    public MultipleHitsWhenOneExpectedException() {
        super();
        error = "";
    }
    
    public MultipleHitsWhenOneExpectedException(Object searchTerm) {
        super();
        error = "the search for the uid '" + String.valueOf(searchTerm) + "' produced more than one result, but there should only be one";
    }

/*    public MultipleHitsWhenOneExpectedException(String err) {
        super(err);
        error = err;
    } */

    public String getError() {
        return error;
    }
}
