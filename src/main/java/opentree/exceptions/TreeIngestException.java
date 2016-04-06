package opentree.exceptions;

import java.lang.Exception;
import java.io.PrintStream;

/**
 * Thrown when more specific errors classes do not apply, and a tree 
 *  could not be ingested into the GoL.
 */
public class TreeIngestException extends Exception {
    
    private static final long serialVersionUID = 1L;
    private String msg;
    
    public TreeIngestException(String error_msg){
        this.msg = error_msg;
    }
    
    public String getName(){
        return this.msg;
    }
    
    @Override
    public String toString(){
        return "TreeIngestException: " + this.msg;
    }
    
    public void reportFailedAction(PrintStream out, String failedAction) {
        String m = failedAction + " failed due to " + this.toString();
        out.println(m);
    }
    
}
