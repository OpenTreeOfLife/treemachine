package opentree;

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
	
	void messageInt(String label, int i) {
		this.outStream.println(this.msgPrefix + this.sep + label + this.sep + i);
	}

	void messageStrLong(String label, String s, Long i) {
		this.outStream.println(this.msgPrefix + this.sep + label + this.sep + s + this.sep + i);
	}
}
