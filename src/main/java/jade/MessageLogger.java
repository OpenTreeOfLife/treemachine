package jade;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import org.apache.commons.lang3.StringUtils;

/**
 * A simple class for emitting output in the form of:
 *	1. indentation level (to denote hierarchical structure of messages)
 *	2. label a free-from string expressing to a user the type of message
 *	3. one or more pairs of key-value pairs
 *
 * In this base classs the messages are simply emitted using println in
 *	the form: label + sep + key1 + sep + val1 + sep + key2 + sep val2
 *
 * If the JSONMessageLogger is used, the same set of calls will result in
 *	the writing of a simple JSON representation of nested object which 
 *	preserve the indentation via their nesting.
 *
 * This logging system is used in functions called by the pgloadind command to 
 *	to produce a set of easily parseable messages about a phylografter study
 */
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

	public void messageStr(String label, String s, String s2) {
		this.indentMessageStr(0, label, s, s2);
	}

	public void messageLong(String label, String s, Long i) {
		this.indentMessageLong(0, label, s, i);
	}

	public void messageInt(String label, String s, int i) {
		this.indentMessageInt(0, label, s, i);
	}

	public void indentMessage(int indentLevel, String label) {
		this._write_prefix();
		this._indent(indentLevel);
		this._message(label);
	}

	public void indentMessageStr(int indentLevel, String label, String s, String s2) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStr(label, s, s2);
	}

	public void indentMessageInt(int indentLevel, String label, String s, int i) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageInt(label, s, i);
	}

	public void indentMessageLong(int indentLevel, String label, String s, Long i) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageLong(label, s, i);
	}

	public void indentMessageLongStr(int indentLevel, String label, String s, Long i, String s2, String s3) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageLongStr(label, s, i, s2, s3);
	}
	
	public void indentMessageIntStr(int indentLevel, String label, String s, int i, String s2, String s3) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageIntStr(label, s, i, s2, s3);
	}
	
	public void indentMessageStrStr(int indentLevel, String label, String s, String s2, String s3, String s4) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageStrStr(label, s, s2, s3, s4);
	}
	public void indentMessageLongStrStr(int indentLevel, String label, String s, Long i, String s2, String s3, String s4, String s5) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageLongStrStr(label, s, i, s2, s3, s4, s5);
	}

	public void indentMessageLongStrStrStr(int indentLevel, String label, String s, Long i, String s2, String s3, String s4, String s5, String s6, String s7) {
		this._write_prefix();
		this._indent(indentLevel);
		this._messageLongStrStrStr(label, s, i, s2, s3, s4, s5, s6, s7);
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

	protected void _messageStr(String label, String s, String s2) {
		this.outStream.println(label + this.sep + s + this.sep + '"' + s2 + '"');
	}
	protected void _messageLong(String label, String s, Long i) {
		this.outStream.println(label + this.sep + s + this.sep + i);
	}

	protected void _messageInt(String label, String s, int i) {
		this.outStream.println(label + this.sep + s + this.sep + i);
	}

	protected void _messageStrStr(String label, String s, String s2, String s3, String s4) {
		this.outStream.println(label + this.sep + s + this.sep + '"' + s2 + '"' + this.sep + s3 + this.sep + '"' + s4 + '"');
	}
	protected void _messageLongStr(String label, String s, Long i, String s3, String s4) {
		this.outStream.println(label + this.sep + s + this.sep + i + this.sep + s3 + this.sep + '"' + s4 + '"');
	}

	protected void _messageIntStr(String label, String s, int i, String s3, String s4) {
		this.outStream.println(label + this.sep + s + this.sep + i + this.sep + s3 + this.sep + '"' + s4 + '"');
	}
	protected void _messageLongStrStr(String label, String s, Long i, String s3, String s4, String s5, String s6) {
		this.outStream.println(label + this.sep + s + this.sep + i + this.sep + s3 + this.sep + '"' + s4 + '"' + this.sep + s5 + this.sep + '"' + s6 + '"');
	}
	protected void _messageLongStrStrStr(String label, String s, Long i, String s3, String s4, String s5, String s6, String s7, String s8) {
		this.outStream.println(label + this.sep + s + this.sep + i + this.sep + s3 + this.sep + '"' + s4 + '"' + this.sep + s5 + this.sep + '"' + s6 + '"' + s7 + this.sep + '"' + s8 + '"');
	}

}
