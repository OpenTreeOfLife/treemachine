/*
 * Read in a Nexml/JSON study file, returning a list of JadeTrees.

 * The file is assumed to be in the form produced by the
 * JSON generator in Phylografter; in particular, XML namespace
 * declarations are not processed, so particular namespace prefix
 * bindings, and a default namespace of "http://www.nexml.org/2009",
 * are assumed.
 *
 * @about is ignored, etc.
 * Ill-formed nexson files will lead to random runtime exceptions, such as bad 
 * casts and null pointer exceptions.  Should be cleaned up eventually, but not
 * high priority.
 */

package jade.tree;

import jade.tree.JadeNode;
import jade.tree.JadeTree;

import jade.MessageLogger;

import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

import java.io.Reader;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class NexsonReader {

	/* main method for testing - just dumps out Newick */
	public static void main(String argv[]) throws Exception {
		String filename = "study15.json";
		if (argv.length == 1) {
			filename = argv[0];
		}
		MessageLogger msgLogger = new MessageLogger("nexsonReader:");
		int treeIndex = 0;
		for (JadeTree tree : readNexson(filename, true, msgLogger)) {
			msgLogger.messageStrInt("tree", "index", treeIndex);
			msgLogger.indentMessageStrStr(1, "annotation", "Curator", (String)tree.getObject("ot:curatorName"));
			msgLogger.indentMessageStrStr(1, "annotation", "Reference", (String)tree.getObject("ot:studyPublicationReference"));
			msgLogger.indentMessageStrStr(1, "representation", "newick", tree.getRoot().getNewick(false));
			int i = 0;
			for (JadeNode node : tree.iterateExternalNodes()) {
				Object o = node.getObject("ot:ottolid");
				msgLogger.indentMessageStrStr(2, "node", "name", node.getName());
				msgLogger.indentMessageStrStr(2, "node", "OTT ID", o.toString());
				msgLogger.indentMessageStrStr(2, "node", "ID class", o.getClass().toString());
				if (++i > 10) {
					break;
				}
			}
			++treeIndex;
		}
	}

	/* Read Nexson study from a file, given file name */
	public static List<JadeTree> readNexson(String filename, Boolean verbose, MessageLogger msgLogger) throws java.io.IOException {
		Reader r = new BufferedReader(new FileReader(filename));
		List<JadeTree> result = readNexson(r, verbose, msgLogger);
		r.close();
		return result;
	}

	/* Read Nexson study from a Reader */
	// TODO: tree(s) may be deprecated. Need to check this. May result in no trees to return.
	public static List<JadeTree> readNexson(Reader r, Boolean verbose, MessageLogger msgLogger) throws java.io.IOException {
		JSONObject all = (JSONObject)JSONValue.parse(r);

		/*
		  The format of the file, roughly speaking (some noise omitted):
		  {"nexml": {
		  "@xmlns": ...,
		  "@nexmljson": "http:\/\/www.somewhere.org",
		  "meta": [...],
		  "otus": { "otu": [...] },
		  "trees": { "tree": [...] }}}

		  See http://www.nexml.org/manual for NexML documentation.
		*/
		
		// the desired result: a list of valid trees
		List<JadeTree> result = new ArrayList<JadeTree>();
		
		// The XML root element
		JSONObject root = (JSONObject)all.get("nexml");

		// All of the <meta> elements from root (i.e. study-wide). Trees may have their own meta elements (e.g. inGroupClade)
		List<Object> studyMetaList = getMetaList(root);
		//System.out.println("studyMetaList = " + studyMetaList);
		
		// check if study is flagged as deprecated. if so, skip.
		if (studyMetaList != null && checkDeprecated(studyMetaList)) {
			System.out.println("Study tagged as deprecated. Ignore.");
			return result;
		}
		
		// All of the <otu> elements
		JSONObject otus = (JSONObject)root.get("otus");
		JSONArray otuList = (JSONArray)otus.get("otu");
		System.out.println(otuList.size() + " OTUs");

		// Make an index by id of the OTUs. We'll need to be able to find them when we built the trees.
		Map<String,JSONObject> otuMap = new HashMap<String,JSONObject>();
		for (Object otu : otuList) {
			JSONObject j = (JSONObject)otu;
			// j = {"@label": "Platanus", "@id": "otu192"}   maybe other data too
			otuMap.put((String)j.get("@id"), j);
		}

		// Get the trees. Each one has nodes and edges
		JSONObject trees = (JSONObject)root.get("trees");
		JSONArray treeList = (JSONArray)trees.get("tree");

		// Process each tree in turn, yielding a JadeTree
		for (Object tree : treeList) {
			JSONObject tree2 = (JSONObject)tree;
			String treeID = (String)tree2.get("@id");
			
			System.out.println("Processing tree '" + treeID + "'...");
			
		// trees can have their own specific metadata e.g. [{"@property":"ot:branchLengthMode","@xsi:type":"nex:LiteralMeta","$":"ot:substitutionCount"},{"@property":"ot:inGroupClade","$":"node208482","xsi:type":"nex:LiteralMeta"}]
			List<Object> treeMetaList = getMetaList(tree2);
			//System.out.println("treeMetaList = " + treeMetaList);
			// check if tree is deprecated. will be a tree-specific tag (ot:tag). if so, abort.
			if (treeMetaList != null && checkDeprecated(treeMetaList)) {
				System.out.println("Tree '" + treeID + "' tagged as deprecated. Ignore.");
			} else {
				if (treeID.startsWith("tree")) { // phylografter tree ids are #'s, but in the Nexson export, they'll have the word tree prepended
					treeID = treeID.substring(4); // chop off 0-3 to chop off "tree"
				}
				// tree2 = {"node": [...], "edge": [...]}
				result.add(importTree(otuMap, (JSONArray)tree2.get("node"), (JSONArray)tree2.get("edge"), studyMetaList, treeMetaList, treeID, verbose));
			}
		}
		return result;
	}

	/* Process a single tree (subroutine of above) */
	private static JadeTree importTree(Map<String,JSONObject> otuMap,
									   JSONArray nodeList,
									   JSONArray edgeList,
									   List<Object> studyMetaList,
									   List<Object> treeMetaList,
									   String treeID,
									   Boolean verbose) {
		System.out.println(nodeList.size() + " nodes, " + edgeList.size() + " edges");
		Map<String, JadeNode> nodeMap = new HashMap<String, JadeNode>();
		
		JadeNode root = null;
		
		// check if an ingroup is defined. if so, discard outgroup(s).
		String ingroup = null;
		if (treeMetaList != null) {
			for (Object meta : treeMetaList) {
				JSONObject j = (JSONObject)meta;
				if (((String)j.get("@property")).compareTo("ot:inGroupClade") == 0) {
					if ((j.get("$")) != null) {
						ingroup = (String)j.get("$");
						System.out.println("Found ingroup node: " + ingroup);
					} else {
						throw new RuntimeException("missing property value for name: " + j);
					}
				}
			}
		}
		
		// arbitraryNode is for finding the root later on (if not specified), see below
		JadeNode arbitraryNode = null;

		// For each node as specified in the Nexson file, create a JadeNode, and squirrel it away
		for (Object node : nodeList) {
			// {"@otu": "otu221", "@id": "node692"}
			JSONObject j = (JSONObject)node;
			// System.out.println(j);

			JadeNode jn = new JadeNode();
			String id = (String)j.get("@id");
			nodeMap.put(id, jn);
			arbitraryNode = jn;
			
			// Set the root node
			if (ingroup != null && id.compareTo(ingroup) == 0) {
				System.out.println("Setting ingroup root node.");
				root = jn;
			}

			// Some nodes have associated OTUs, others don't
			String otuId = (String)j.get("@otu");
			if (otuId != null) {
				JSONObject otu = otuMap.get(otuId);
				String label = (String)otu.get("@label");
				jn.setName(label);

				// Get taxon id (usually present) and maybe other metadata (rarely present)
				List<Object> metaList2 = getMetaList(otu);
				if (metaList2 != null) {
					for (Object meta : metaList2) {
						JSONObject m = (JSONObject)meta;
						String propname = (String)m.get("@property");
						Object value = m.get("$");
						if (propname.equals("ot:ottolid")) {
							// Kludge! For important special case
							if (value instanceof String) {
								value = Long.parseLong((String)value);
							} else if (value instanceof Long) {
								; // what is this about?
							} else if (value instanceof Integer) {
								value = new Long((long)(((Integer)value).intValue()));
							} else if (value == null) {
								System.out.println("Warning: dealing with null ot:ottolid here.");
							} else {
								System.out.println("Error with: " + m);
								throw new RuntimeException("Invalid ottolid value: " + value);
							}
						} else {
							System.out.println("Warning: dealing with property: " + propname + ". Don't know what to do...");
						}
						jn.assocObject(propname, value);
					}
				}
			}
		}

		// For each specified edge, hook up the two corresponding JadeNodes
		for (Object edge : edgeList) {
			JSONObject j = (JSONObject)edge;
			// {"@source": "node830", "@target": "node834", "@length": 0.000241603, "@id": "edge834"}
			// source is parent, target is child
			JadeNode source = nodeMap.get(j.get("@source"));  // why isn't this a type error?
			JadeNode target = nodeMap.get(j.get("@target"));
			Double length = (Double)j.get("@length");
			if (length != null) {
				target.setBL(length);
			}
			source.addChild(target);
		}
		
		// Find the root (the node without a parent) so we can return it.
		// If the input file is malicious this might loop forever.
		if (root == null) {
			for (JadeNode jn = arbitraryNode; jn != null; jn = jn.getParent()) {
				root = jn;
			}
		} else { // a pruned tree. GraphImporter looks for root as node with no parents.
			root.setParent(null);
		}
		
		JadeTree tree = new JadeTree(root);
		
		int nc = tree.getExternalNodeCount();
		if (nc != otuMap.size()) {
			System.out.println("Ingested tree has " + nc + " external nodes.");
		}
		
		// Copy STUDY-level metadata into the JadeTree
		// See https://github.com/nexml/nexml/wiki/NeXML-Manual#wiki-Metadata_annotations_and_NeXML
		if (studyMetaList != null) {
			associateMetadata(tree, studyMetaList, verbose);
		}
		// Copy TREE-level metadata into the JadeTree
		if (treeMetaList != null) {
			associateMetadata(tree, treeMetaList, verbose);
		}
		tree.assocObject("id", treeID);
		return tree;
	}
	
	// check through metadata information for ot:tag del*
	// works for both study-wide and tree-specific metadata
	private static Boolean checkDeprecated (List<Object> metaData) {
		Boolean deprecated = false;
		for (Object meta : metaData) {
			JSONObject j = (JSONObject)meta;
			if (((String)j.get("@property")).compareTo("ot:tag") == 0) {
				if ((j.get("$")) != null) {
					String currentTag = (String)j.get("$");
					if (currentTag.startsWith("del")); {
						return true;
					}
				} else {
					throw new RuntimeException("missing property value for name: " + j);
				}
			}
		}
		return deprecated;
	}
	
	private static void associateMetadata (JadeTree tree, List<Object> metaData, Boolean verbose) {
		for (Object meta : metaData) {
			JSONObject j = (JSONObject)meta;
			// {"@property": "ot:curatorName", "@xsi:type": "nex:LiteralMeta", "$": "Rick Ree"},
			String propname = (String)j.get("@property");
			if (propname != null) {
				// String propkind = (String)j.get("@xsi:type");  = nex:LiteralMeta
				// looking for either "$" or "@href" (former is more frequent)
				if ((j.get("$")) != null) {
					Object value = j.get("$");
					if (value == null) {
						throw new RuntimeException("missing value for " + propname);
					}
					tree.assocObject(propname, value);
					if (verbose) {
						System.out.println("Added propname (" + propname + "): " + value);
					}
				} else if ((j.get("@href")) != null) {
					Object value = j.get("@href");
					if (value == null) {
						throw new RuntimeException("missing value for " + propname);
					}
					tree.assocObject(propname, value);
					if (verbose) {
						System.out.println("Added propname (" + propname + "): " + value);
					}
				} else {
					throw new RuntimeException("missing property value for name: " + j);
				}
			} else {
				throw new RuntimeException("missing property name: " + j);
			}
		}
	}
	
	private static List<Object> getMetaList(JSONObject obj) {
		//System.out.println("looking up meta for: " + obj);
		Object meta = obj.get("meta");
		if (meta == null) {
			//System.out.println("meta == NULL");
			return null;
		}
		//System.out.println("meta != NULL: " + meta);
		if (meta instanceof JSONObject) {
			List l = new ArrayList(1);
			l.add(meta);
			return l;
		} else {
			return (JSONArray) meta;
		}
	}
}
