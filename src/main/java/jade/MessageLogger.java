package jade;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import org.apache.commons.lang3.StringUtils;

public class MessageLogger {
	//static Logger _LOG = Logger.getLogger(MainRunner.class);
	String msgPrefix;
	PrintStream outStream;
	String sep;
	public MessageLogger(String pref) {
		this.msgPrefix = pref;
		this.outStream = System.out;
		this.sep = "\t|\t";
	}
	public MessageLogger(String pref, String separator) {
		this.msgPrefix = pref;
		this.outStream = System.out;
		this.sep = separator;
	}

	public void setPrintStream(PrintStream ps) {
		this.outStream = ps;
	}

	
	public void message(String label) {
		this.indentMessage(0, label);
	}

	public void messageStrStr(String label, String s, String s2) {
		this.indentMessageStrStr(0, label, s, s2);
	}

	public void messageStrLong(String label, String s, Long i) {
		this.indentMessageStrLong(0, label, s, i);
	}

	public void messageStrInt(String label, String s, int i) {
		this.indentMessageStrInt(0, label, s, i);
	}

	public void indentMessage(int indentLevel, String label) {
		this._write_prefix();
		this._indent(indentLevel);
		this._message(label);
	}

	public void indentMessageStrStr(int indentLevel, String label, String s, String s2) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStrStr(label, s, s2);
	}

	public void indentMessageStrInt(int indentLevel, String label, String s, int i) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStrInt(label, s, i);
	}

	public void indentMessageStrLong(int indentLevel, String label, String s, Long i) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStrLong(label, s, i);
	}

	public void indentMessageStrLongStrStr(int indentLevel, String label, String s, Long i, String s2, String s3) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStrLongStrStr(label, s, i, s2, s3);
	}
	
	public void indentMessageStrIntStrStr(int indentLevel, String label, String s, int i, String s2, String s3) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStrIntStrStr(label, s, i, s2, s3);
	}
	
	public void indentMessageStrStrStrStr(int indentLevel, String label, String s, String s2, String s3, String s4) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStrStrStrStr(label, s, s2, s3, s4);
	}
	public void indentMessageStrLongStrStrStrStr(int indentLevel, String label, String s, Long i, String s2, String s3, String s4, String s5) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStrLongStrStr(label, s, i, s2, s3, s4, s5);
	}
	
	// no-op for streaming to std out. close objects for JSON
	public void close() {
	}

	// prepend prefix (or no-op in JSON)
	protected void _write_prefix() {
		this.outStream.print(this.msgPrefix + this.sep);
	}

	// indent with spaces (or nest if JSON)
	protected void _indent(int indentLevel) {
		for (int x = 0; x < indentLevel; ++x) {
			this.outStream.print("  ");
		}
	}

	protected void _message(String label) {
		this.outStream.println(label);
	}

	protected void _messageStrStr(String label, String s, String s2) {
		this.outStream.println(label + this.sep + s + this.sep + '"' + s2 + '"');
	}
	protected void _messageStrLong(String label, String s, Long i) {
		this.outStream.println(label + this.sep + s + this.sep + i);
	}

	protected void _messageStrInt(String label, String s, int i) {
		this.outStream.println(label + this.sep + s + this.sep + i);
	}

	protected void _messageStrStrStrStr(String label, String s, String s2, String s3, String s4) {
		this.outStream.println(label + this.sep + s + this.sep + '"' + s2 + '"' + this.sep + s3 + this.sep + '"' + s4 + '"');
	}
	protected void _messageStrLongStrStr(String label, String s, Long i, String s3, String s4) {
		this.outStream.println(label + this.sep + s + this.sep + i + this.sep + s3 + this.sep + '"' + s4 + '"');
	}

	protected void _messageStrIntStrStr(String label, String s, int i, String s3, String s4) {
		this.outStream.println(label + this.sep + s + this.sep + i + this.sep + s3 + this.sep + '"' + s4 + '"');
	}
	protected void _messageStrLongStrStr(String label, String s, Long i, String s3, String s4, String s5, String s6) {
		this.outStream.println(label + this.sep + s + this.sep + i + this.sep + s3 + this.sep + '"' + s4 + '"' + this.sep + s5 + this.sep + '"' + s6 + '"');
	}

}
