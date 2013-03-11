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
		for (JadeTree tree : readNexson(filename)) {
			System.out.println("Curator: " + tree.getObject("ot:curatorName"));
			System.out.println("Reference: " + tree.getObject("ot:studyPublicationReference"));
			System.out.println(tree.getRoot().getNewick(false));

			int i = 0;
			for (JadeNode node: tree.iterateExternalNodes()) {
				Object o = node.getObject("ot:ottolid");
				System.out.println(node.getName() + " / " + o + " " + o.getClass());
				if (++i > 10) break;
			}
		}
	}

	/* Read Nexson study from a file, given file name */

	public static List<JadeTree> readNexson(String filename) throws java.io.IOException {
		Reader r = new BufferedReader(new FileReader(filename));
		List<JadeTree> result = readNexson(r);
		r.close();
		return result;
	}

	/* Read Nexson study from a Reader */

	public static List<JadeTree> readNexson(Reader r) throws java.io.IOException {
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

		// The XML root element
		JSONObject root = (JSONObject)all.get("nexml");

		// All of the <meta> elements
		List<Object> metaList = getMetaList(root);
			
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

		List<JadeTree> result = new ArrayList<JadeTree>();

		// Process each tree in turn, yielding a JadeTree
		for (Object tree : treeList) {
			JSONObject tree2 = (JSONObject)tree;
			// tree2 = {"node": [...], "edge": [...]}
			result.add(importTree(otuMap, (JSONArray)tree2.get("node"), (JSONArray)tree2.get("edge"), metaList));
		}

		return result;
	}

	/* Process a single tree (subroutine of above) */

	private static JadeTree importTree(Map<String,JSONObject> otuMap,
									   JSONArray nodeList,
									   JSONArray edgeList,
									   List<Object> metaList) {
		System.out.println(nodeList.size() + " nodes, " + edgeList.size() + " edges");
		Map<String, JadeNode> nodeMap = new HashMap<String, JadeNode>();

		// arbitraryNode is for finding the root later on, see below
		JadeNode arbitraryNode = null;

		// For each node as specified in the Nexson file, create a JadeNode, and squirrel it away
		for (Object node : nodeList) {
			// {"@otu": "otu221", "@id": "node692"}
			JSONObject j = (JSONObject)node;
			// System.out.println(j);

			JadeNode jn = new JadeNode();
			nodeMap.put((String)j.get("@id"), jn);
			arbitraryNode = jn;

			// Some nodes have associated OTUs, others don't
			String otuId = (String)j.get("@otu");
			if (otuId != null) {
				JSONObject otu = otuMap.get(otuId);
				String label = (String)otu.get("@label");
				jn.setName(label);

				// Get taxon id (usually present) and maybe other metadata (rarely present)
				List<Object> metaList2 = getMetaList(otu);
				if (metaList2 != null)
					for (Object meta : metaList2) {
						JSONObject m = (JSONObject)meta;
						String propname = (String)m.get("@property");
						Object value = m.get("$");
						if (propname.equals("ot:ottolid")) {
							// Kludge! For important special case
							if (value instanceof String)
								value = Long.parseLong((String)value);
							else if (value instanceof Long)
								;
							else if (value instanceof Integer)
								value = new Long((long)(((Integer)value).intValue()));
							else
								throw new RuntimeException("Invalid ottolid value: " + value);
						}
						jn.assocObject(propname, value);
					}
			}
		}

		// For each specified each, hook up the two corresponding JadeNodes
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
		JadeNode root = null;
		for (JadeNode jn = arbitraryNode; jn != null; jn = jn.getParent()) {
			root = jn;
		}

		JadeTree tree = new JadeTree(root);

		// Copy tree-level metadata into the JadeTree
		// See https://github.com/nexml/nexml/wiki/NeXML-Manual#wiki-Metadata_annotations_and_NeXML
		if (metaList != null) {
			for (Object meta : metaList) {
				JSONObject j = (JSONObject)meta;
				// {"@property": "ot:curatorName", "@xsi:type": "nex:LiteralMeta", "$": "Rick Ree"},
				String propname = (String)j.get("@property");
				if (propname != null) {
					// String propkind = (String)j.get("@xsi:type");  = nex:LiteralMeta
					Object value = j.get("$");
					if (value == null) throw new RuntimeException("missing value for " + propname);
					tree.assocObject(propname, value);
				} else if ((propname = (String)j.get("@rel")) != null) {
					System.out.println("propname = " + propname);
					// String propkind = (String)j.get("@xsi:type");  = nex:ResourceMeta
					Object value = j.get("@href");
					if (value == null) {
						throw new RuntimeException("missing value for " + propname);
					}
					tree.assocObject(propname, value);
				} else {
					throw new RuntimeException("missing property name" + j);
				}
			}
		}
		return tree;
	}

	private static List<Object> getMetaList(JSONObject obj) {
		Object meta = obj.get("meta");
		if (meta == null) {
			return null;
		}
		if (meta instanceof JSONObject) {
			List l = new ArrayList(1);
			l.add(meta);
			return l;
		} else {
			return (JSONArray) meta;
		}
	}
}
