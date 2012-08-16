package opentree;
import java.lang.Exception;

public class TaxonNotFoundException extends Exception {
 
    private String taxon_name;
 
    public TaxonNotFoundException(String name_of_taxon){
        this.taxon_name = name_of_taxon;
    }
 
    public String getName(){
        return this.taxon_name;
    }

    public String getQuotedName(){
        return "\'" + this.taxon_name + "\'";
    }
    
    public String toString(){
        return "Taxon \"" + this.taxon_name + "\" is not recognized.";
    }
}
