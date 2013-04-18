package opentree;
import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintStream;
import opentree.StoredEntityNotFoundException;

public class TaxonNotFoundException extends StoredEntityNotFoundException {

    // single name constructor
    public TaxonNotFoundException(String nameOfTaxon) {
        super(nameOfTaxon, "taxon", "taxa");
    }

    // list of names constructor
    public TaxonNotFoundException(List<String> namesOfTaxa){
        super(namesOfTaxa, "taxon", "taxa");
    }
}
