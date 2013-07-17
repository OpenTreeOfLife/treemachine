package opentree.exceptions;

import java.util.List;

/**
 *  Thrown when we cannot find an indicated ottolid.
 */
public class OttolIdNotFoundException extends StoredEntityNotFoundException {

	private static final long serialVersionUID = 1L;

	// single id constructor
    public OttolIdNotFoundException(String ottolId) {
        super(ottolId, "ottolid", "ottolids");
    }

    // list of ids constructor
    public OttolIdNotFoundException(List<String> ottolIds){
        super(ottolIds, "ottolid", "ottolids");
    }

	
}
