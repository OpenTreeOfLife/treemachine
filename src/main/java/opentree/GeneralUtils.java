package opentree;

import java.io.Reader;
import java.util.*;

import opentree.exceptions.DataFormatException;

public class GeneralUtils {

    // all common non-alphanumeric chars except "_" and "-", for use when cleaning strings
    public static final String offendingChars = "[\\Q\"_~`:;/[]{}|<>,.!@#$%^&*()?+=`\\\\\\E\\s]+";

    public static int sum_ints(List<Integer> list){
		if (list == null || list.size() < 1) {
			return 0;
		}

		int sum = 0;
		for (Integer i: list) {
			sum = sum + i;
		}
		return sum;
	}

	/**
	 * Replaces non-alphanumeric characters (excluding "_" and "-") in `dirtyName` with "_" and returns the cleaned name.
	 * 
	 * @param dirtyName
	 * @return cleaned name
	 */
	public static String cleanName(String dirtyName) {
	    String cleanName = dirtyName.replaceAll(offendingChars, "_");
	    return cleanName;
	}
	
	/*
	 Peek at tree flavour, report back, reset reader for subsequent processing
	 @param r a tree file reader
	 @return treeFormat a string indicating recognized tree format
	*/
	public static String divineTreeFormat(Reader r) throws java.io.IOException, DataFormatException {
		String treeFormat = "";
		r.mark(1);
		char c = (char)r.read();
		r.reset();
		if (c == '(') {
			treeFormat = "newick";
		} else if (c == '{') {
			treeFormat = "nexson";
		} else if (c == '#') {
			throw new DataFormatException("Appears to be a nexus tree file, which is not currently supported.");
		} else {
			throw new DataFormatException("We don't know what format this tree is in.");
		}
		return treeFormat;
	}

}
