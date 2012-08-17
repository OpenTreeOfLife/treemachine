package opentree;
import java.lang.Exception;

/**
 * Thrown when more specific errors classes do not apply, and a tree 
 *  could not be ingested into the GoL.
 */
public class TreeIngestException extends Exception {
 
    private String msg;
 
    public TreeIngestException(String error_msg){
        this.msg = error_msg;
    }
 
    public String getName(){
        return this.msg;
    }

    
    public String toString(){
        return "TreeIngestException: " + this.msg;
    }
}
