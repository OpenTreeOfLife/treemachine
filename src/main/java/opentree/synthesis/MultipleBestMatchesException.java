package opentree.synthesis;

public class MultipleBestMatchesException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    String error;

    public MultipleBestMatchesException() {
        super();
        error = "";
    }

    public MultipleBestMatchesException(String err) {
        super(err);
        error = err;
    }

    public String getError() {
        return error;
    }
}
