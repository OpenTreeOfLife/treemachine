package opentree;
import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintStream;

public class TaxonNotFoundException extends Exception {

    private ArrayList<String> taxonNames;

    // single name constructor
    public TaxonNotFoundException(String nameOfTaxon){
        taxonNames = new ArrayList<String>();
        taxonNames.add(nameOfTaxon);
    }

    // list of names constructor
    public TaxonNotFoundException(List<String> namesOfTaxa){
        taxonNames = new ArrayList<String>();
        taxonNames.addAll(namesOfTaxa);
    }
    
    private String getNames(){
        // make a string of all names, use commas after first name
        String names = taxonNames.get(0);
        for (int i = 1; i < taxonNames.size(); i++) {
            names = names.concat(", ").concat(taxonNames.get(i));
        }
        return names;
    }

    public String getQuotedName(){
        return "\'" + getNames() + "\'";
    }
    
    public String toString(){
        return "Taxa \"" + getNames() + "\" is not recognized.";
    }

    public void reportFailedAction(PrintStream out, String failedAction) {
        String qn = this.getQuotedName();
        String pre;
        if (taxonNames.size() == 1) {
            pre = failedAction + " failed due to unrecognized taxon: ";
        } else {
            pre = failedAction + " failed due to unrecognized taxa: ";
        }
        out.println(pre + qn);
    }
}
