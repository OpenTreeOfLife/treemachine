package opentree.exceptions;
import java.lang.Exception;
import java.io.PrintStream;

public class DataFormatException extends Exception {

	private static final long serialVersionUID = 1L;
	private String message;

	// single name constructor
	public DataFormatException(String msg){
		this.message = msg;
	}

	@Override
	public String toString(){
		return "Format not recognized: " + this.message;
	}

	public void reportFailedAction(PrintStream out, String failedAction) {
		String em = failedAction + " failed. " + this.toString();
		out.println(em);
	}
}
