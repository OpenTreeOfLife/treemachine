package opentree;
import java.util.*;

import org.neo4j.graphdb.Node;

/** 
 * This class has only static methods. The main reasons for its existence are to:
 * 	1. Consolidate serialization code so that it can be easily re-used. This will make
 *		it easier to refactor exporting of JSON (e.g. into GraphExplorer rather than via a JadeTree)
 *	2. reduce inter-package dependencies to one file (JadeNode has to know about this 
 *		file but not neo4j Nodes, Relationships...),
 */
public class JSONExporter {
	public static void writeStringArrayAsJSON(StringBuffer buffer, String [] sArr) {
		buffer.append("[");
		boolean first = true;
		for (String s : sArr) {
			if (first) {
				first = false;
			} else {
				buffer.append(",");
			}
			buffer.append("\"");
			buffer.append(s);
			buffer.append("\"");
		}
		buffer.append("]");
	}
	///@TEMP: placeholder for real JSON escape string. Or are we going to escape of JSON on ingest into the db?
	public static void escapeString(StringBuffer buffer, String s) {
		buffer.append("\"");
		buffer.append(s);
		buffer.append("\"");
	}

	// write s as a quoted name appropriate for a JS property name and adds a colon and space for assignment to that property
	///@TEMP: placeholder for real JSON property name escaping. Are there restrictions here???
	public static void escapePropertyColon(StringBuffer buffer, String s) {
		buffer.append("\"");
		buffer.append(s);
		buffer.append("\": ");
	}

	// write s as a quoted name appropriate for a JS property name and adds a colon and space for assignment to that property
	///@TEMP: placeholder for real JSON property name escaping. Are there restrictions here???
	public static void writeBooleanAsJSON(StringBuffer buffer, Boolean b) {
		if (b) {
			buffer.append("true");
		} else {
			buffer.append("false");
		}
	}

	// if the Node has a property with name properyName, then this name will be appended to the JSON string buffer as a property
	//	name. The colon will be added so that the rvalue can simply be written
	// returns true if a property: was written.
	public static boolean writePropertyNameColonIfFound(StringBuffer buffer, Node nd, String propertyName, boolean prependComma) {
		if (nd.hasProperty(propertyName)) {
			if (prependComma) {
				buffer.append(", ");
			}
			escapePropertyColon(buffer, propertyName);
			return true;
		}
		return false;
	}

	// returns true if a property/value pair was written.
	public static boolean writeStringPropertyIfFound(StringBuffer buffer, Node nd, String propertyName, boolean prependComma) {
		if (writePropertyNameColonIfFound(buffer, nd, propertyName, prependComma)){
			escapeString(buffer, (String)nd.getProperty(propertyName));
			return true;
		}
		return false;
	}
	// returns true if a property/value pair was written.
	public static boolean writeIntegerPropertyIfFound(StringBuffer buffer, Node nd, String propertyName, boolean prependComma) {
		if (writePropertyNameColonIfFound(buffer, nd, propertyName, prependComma)){
			buffer.append((Integer)nd.getProperty(propertyName));
			return true;
		}
		return false;
	}

	public static void writeSourceToMetaMapForArgus(StringBuffer buffer, Object n2m){
		buffer.append("\"sourceToMetaMap\": {");
		HashMap<String, Node> name2metanode = (HashMap<String, Node>) n2m;
		boolean first = true;
		for (Map.Entry<String, Node> n2mEl : name2metanode.entrySet()) { // if this iter is slow, we might need to use a TreeMap...
			if (first) {
				first = false;
			} else {
				buffer.append(",");
			}
			String source = n2mEl.getKey();
			Node metadataNode = n2mEl.getValue();
			if (source == null || source.length() == 0) {
				escapePropertyColon(buffer, "unnamedSource");
			} else {
				escapePropertyColon(buffer, source);
			}
			if (metadataNode == null) {
				buffer.append("{}");
			} else {
				boolean wrotePrev = false;
				buffer.append("{\"study\": {");
					wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:studyPublicationReference", wrotePrev) || wrotePrev;
					wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:studyPublication", wrotePrev) || wrotePrev;
					wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:curatorName", wrotePrev) || wrotePrev;
					wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:dataDeposit", wrotePrev) || wrotePrev;
					wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:studyId", wrotePrev) || wrotePrev;
					wrotePrev = writeIntegerPropertyIfFound(buffer, metadataNode, "ot:studyYear", wrotePrev) || wrotePrev;
				buffer.append("}}");
			}
		}
		buffer.append("}");
	}
}