package opentree.exceptions;

import java.util.List;

/**
 *  Thrown when we cannot find an indicated ottId.
 */
public class OttIdNotFoundException extends StoredEntityNotFoundException {

	private static final long serialVersionUID = 1L;

	// single id constructor
    public OttIdNotFoundException(String ottId) {
        super(ottId, "ottId", "ottIds");
    }

    // list of ids constructor
    public OttIdNotFoundException(List<String> ottIds){
        super(ottIds, "ottId", "ottIds");
    }
}
