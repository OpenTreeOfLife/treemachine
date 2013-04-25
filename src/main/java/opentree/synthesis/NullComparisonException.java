package opentree.synthesis;

public class NullComparisonException extends ClassCastException {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    String error;

    public NullComparisonException() {
        super();
        error = "";
    }

    public NullComparisonException(String err) {
        super(err);
        error = err;
    }

    public String getError() {
        return error;
    }}
