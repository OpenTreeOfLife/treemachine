package opentree.exceptions;
import java.util.List;

/**
 * Thrown when we cannot find an indicated taxon.
 */
public class TaxonNotFoundException extends StoredEntityNotFoundException {

	private static final long serialVersionUID = 1L;

	// single name constructor
    public TaxonNotFoundException(String nameOfTaxon) {
        super(nameOfTaxon, "taxon", "taxa");
    }

    // list of names constructor
    public TaxonNotFoundException(List<String> namesOfTaxa){
        super(namesOfTaxa, "taxon", "taxa");
    }
}
