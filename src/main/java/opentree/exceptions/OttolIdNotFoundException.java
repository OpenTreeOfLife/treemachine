package opentree.exceptions;

import java.util.List;


public class OttolIdNotFoundException extends StoredEntityNotFoundException {

    // single id constructor
    public OttolIdNotFoundException(String ottolId) {
        super(ottolId, "ottolid", "ottolids");
    }

    // list of ids constructor
    public OttolIdNotFoundException(List<String> ottolIds){
        super(ottolIds, "ottolid", "ottolids");
    }

	
}
