package opentree;
import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintStream;

public class DataFormatException extends Exception {

	private String message;
	private String filename;
	private int lineNumber;

	// single name constructor
	public DataFormatException(String msg){
		this.message = msg;
		this.filename = null;
		this.lineNumber = -1;
	}

	public void setFilePath(String filename) {
		this.filename = filename;
	}
	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}
	public String toString(){
		String msg = "Data Format Error: " + this.message;
		if (msg != null) {
			msg += "\nFile \"" + this.filename + "\"";
		}
		if (lineNumber >= 0) {
			msg += "\nOn line " + this.lineNumber;
		}
		return msg;
	}

	public void reportFailedAction(PrintStream out, String failedAction) {
		String em = failedAction + " failed. " + this.toString();
		out.println(em);
	}
}
