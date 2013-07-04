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

	// no-op for streaming to std out. close objects for JSON
	public void close() {
	}

	// prepend prefix (or no-op in JSON)
	private void _write_prefix() {
		this.outStream.print(this.msgPrefix + this.sep);
	}

	// indent with spaces (or nest if JSON)
	private void _indent(int indentLevel) {
		for (int x = 0; x < indentLevel; ++x) {
			this.outStream.print("  ");
		}
	}

	private void _message(String label) {
		this.outStream.println(label);
	}

	private void _messageStrStr(String label, String s, String s2) {
		this.outStream.println(label + this.sep + s + this.sep + s2);
	}
	private void _messageStrLong(String label, String s, Long i) {
		this.outStream.println(label + this.sep + s + this.sep + i);
	}

	public void _messageStrInt(String label, String s, int i) {
		this.outStream.println(label + this.sep + s + this.sep + i);
	}


}