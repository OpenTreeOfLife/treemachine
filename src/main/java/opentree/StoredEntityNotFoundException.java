package opentree;
import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintStream;
import org.apache.commons.lang3.StringUtils;

/**
 * Base class for exceptions raised when and entity that was expected to be found in the db is not found.
 * Calling functions 
 */
public class StoredEntityNotFoundException extends Exception {
    private String singularEntity;
    private String pluralEntity;
    private ArrayList<String> missingNames;

    // single name constructor
    public StoredEntityNotFoundException(String nameOfTaxon, String singularEntityName, String pluralEntityName){
        this.singularEntity = singularEntityName;
        this.pluralEntity = pluralEntityName;
        this.missingNames = new ArrayList<String>();
        this.missingNames.add(nameOfTaxon);
    }

    // list of names constructor
    public StoredEntityNotFoundException(List<String> namesOfTaxa, String singularEntityName, String pluralEntityName){
        this.singularEntity = singularEntityName;
        this.pluralEntity = pluralEntityName;
        this.missingNames = new ArrayList<String>();
        this.missingNames.addAll(namesOfTaxa);
    }
    
    private String getNames(){
        // make a string of all names, use commas after first name
        return StringUtils.join(this.missingNames, ", ");
    }

    public String getQuotedName(){
        return "\'" + this.getNames() + "\'";
    }
    
    public String toString(){
        return this.singularEntity + " \"" + this.getNames() + "\" is not recognized.";
    }

    public void reportFailedAction(PrintStream out, String failedAction) {
        String qn = this.getQuotedName();
        String pre;
        String noun = (missingNames.size() == 1 ? this.singularEntity : this.pluralEntity);
        pre = failedAction + " failed; " + noun + " not recognized: ";
        out.println(pre + qn);
    }
}
