package opentree.exceptions;

import java.util.List;

/**
 *  Thrown when we cannot find an indicated ottId.
 */
public class ottIdNotFoundException extends StoredEntityNotFoundException {

	private static final long serialVersionUID = 1L;

	// single id constructor
    public ottIdNotFoundException(String ottId) {
        super(ottId, "ottId", "ottIds");
    }

    // list of ids constructor
    public ottIdNotFoundException(List<String> ottIds){
        super(ottIds, "ottId", "ottIds");
    }

	
}
