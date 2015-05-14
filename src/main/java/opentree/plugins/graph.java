package opentree.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

//import jade.tree.deprecated.JadeTree;
import opentree.GraphExplorer;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.constants.GeneralConstants;

import java.net.URL;
import java.net.URLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.TaxonNotFoundException;
//import org.opentree.exceptions.TreeNotFoundException;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OTRepresentationConverter;
import org.opentree.utils.GeneralUtils;
import org.opentree.graphdb.GraphDatabaseAgent;

// Graph of Life Services 
public class graph extends ServerPlugin {

	
	@Description("Returns summary information about the entire graph database, including identifiers for the "
			+ "taxonomy and source trees used to build it.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation about(@Source GraphDatabaseService graphDb) throws TaxonNotFoundException,
			MultipleHitsException {

		GraphDatabaseAgent gdb = new GraphDatabaseAgent(graphDb);
		GraphExplorer ge = new GraphExplorer(gdb);
		HashMap<String, Object> graphInfo = new HashMap<String, Object>();

		Node root = ge.getGraphRootNode();

		graphInfo.put("graph_root_node_id", root.getId());
		graphInfo.put("graph_root_ott_id", Long.valueOf((String) root.getProperty(NodeProperty.TAX_UID.propertyName)));
		graphInfo.put("graph_root_name", String.valueOf(root.getProperty(NodeProperty.NAME.propertyName)));
		graphInfo.put("graph_taxonomy_version", ge.getTaxonomyVersion());
		graphInfo.put("graph_num_tips", ((long[]) root.getProperty(NodeProperty.MRCA.propertyName)).length);

		// *** do we want to return the _list_ of studies?
		graphInfo.put("graph_num_source_trees", ge.getSourceList().size());

		ge.shutdownDB();

		return OTRepresentationConverter.convert(graphInfo);
	}
	
	// Possibility of replacing tip label ottids with names?!?
	@Description("Returns a processed source tree (corresponding to a tree in some [study](#studies)) used "
			+ "as input for the synthetic tree. Although the result of this service is a tree corresponding directly to a "
			+ "tree in a study, the representation of the tree in the graph may differ slightly from its "
			+ "canonical representation in the study, due to changes made during tree import: 1) includes "
			+ "only the curator-designated ingroup clade, and 2) both unmapped and duplicate tips are pruned "
			+ "from the tree. The tree is returned in newick format, with terminal nodes labelled with ott ids.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation source_tree(
			@Source GraphDatabaseService graphDb,
			@Description("The study identifier. Will typically include a prefix (\"pg_\" or \"ot_\")") @Parameter(
					name = "study_id", optional = false) String studyID,
			@Description("The tree identifier for a given study.") @Parameter(
					name = "tree_id", optional = false) String treeID,
			@Description("The name of the return format. The only currently supported format is newick.") @Parameter(
					name = "format", optional = true) String format) {

		HashMap<String, Object> responseMap = new HashMap<String, Object>();
		String source = studyID + "_" + treeID;
		
		String tree = getSourceTree(source);

		if (tree == null) {
			responseMap.put("error", "Invalid source id '" + source + "' provided.");
			return OTRepresentationConverter.convert(responseMap);
		} else {
			responseMap.put("newick", tree);
		}
		
		return OTRepresentationConverter.convert(responseMap);
		
	}
	
	
	@Description("Returns summary information about a node in the graph. The node of interest may be specified "
			+ "using *either* a node id, or an ott id, **but not both**. If the specified node or ott id is not in "
			+ "the graph, an error will be returned.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation node_info(
			@Source GraphDatabaseService graphDb,
			@Description("The node id of the node of interest. This argument may not be combined with `ott_id`.") @Parameter(
					name = "node_id", optional = true) Long queryNodeId,
			@Description("The ott id of the node of interest. This argument may not be combined with `node_id`.") @Parameter(
					name = "ott_id", optional = true) Long queryOttId,
			@Description("Include the ancestral lineage of the node in the draft tree. If this argument is `true`, then "
					+ "a list of all the ancestors of this node in the draft tree, down to the root of the tree itself, "
					+ "will be included in the results. Higher list indices correspond to more incluive (i.e. deeper) "
					+ "ancestors, with the immediate parent of the specified node occupying position 0 in the list.") 
			@Parameter(name = "include_lineage", optional = true) Boolean includeLineage) {

		HashMap<String, Object> nodeIfo = new HashMap<String, Object>();

		Long ottId = null;
		String name = "";
		String rank = "";
		String taxSource = "";
		Long nodeId = null;
		boolean inGraph = false;
		boolean inSynthTree = false;
		Integer numSynthChildren = 0;
		Integer numMRCA = 0;
		LinkedList<HashMap<String, Object>> synthSources = new LinkedList<HashMap<String, Object>>();
		LinkedList<HashMap<String, Object>> treeSources = new LinkedList<HashMap<String, Object>>();

		if (queryNodeId == null && queryOttId == null) {
			nodeIfo.put("error", "Must provide a \"node_id\" or \"ott_id\" argument.");
			return OTRepresentationConverter.convert(nodeIfo);
		} else if (queryNodeId != null && queryOttId != null) {
			nodeIfo.put("error", "Provide only one \"node_id\" or \"ott_id\" argument.");
			return OTRepresentationConverter.convert(nodeIfo);
		}

		GraphExplorer ge = new GraphExplorer(graphDb);

		if (queryOttId != null) {
			Node n = null;
			try {
				n = ge.findGraphTaxNodeByUID(String.valueOf(queryOttId));
			} catch (TaxonNotFoundException e) {
			}
			if (n != null) {
				nodeId = n.getId();
			} else {
				nodeIfo.put("error", "Could not find any graph nodes corresponding to the ott id provided.");
				return OTRepresentationConverter.convert(nodeIfo);
			}

		} else if (queryNodeId != null) {
			Node n = null;
			try {
				n = graphDb.getNodeById(queryNodeId);
			} catch (NotFoundException e) {

			} catch (NullPointerException e) {

			}
			if (n != null) {
				nodeId = queryNodeId;
			} else {
				nodeIfo.put("error", "Could not find any graph nodes corresponding to the node id provided.");
				return OTRepresentationConverter.convert(nodeIfo);
			}
		}

		if (nodeId != null) {
			Node n = graphDb.getNodeById(nodeId);
			if (n.hasProperty(NodeProperty.NAME.propertyName)) {
				name = String.valueOf(n.getProperty(NodeProperty.NAME.propertyName));
				rank = String.valueOf(n.getProperty(NodeProperty.TAX_RANK.propertyName));
				taxSource = String.valueOf(n.getProperty(NodeProperty.TAX_SOURCE.propertyName));
				ottId = Long.valueOf((String) n.getProperty(NodeProperty.TAX_UID.propertyName));
			}
			inGraph = true;
			numMRCA = ((long[]) n.getProperty(NodeProperty.MRCA.propertyName)).length;
			if (ge.nodeIsInSyntheticTree(n)) {
				inSynthTree = true;
				numSynthChildren = ge.getSynthesisDescendantTips(n).size(); // may be faster to just use stored MRCA
				// get all the unique sources supporting this node
				ArrayList<String> sSources = ge.getSynthesisSupportingSources(n);
				ArrayList<String> tSources = ge.getSupportingTreeSources(n);
				
				for (String sStudy : sSources) {
					HashMap<String, Object> indStudy = GeneralUtils.reformatSourceID(sStudy);
					synthSources.add(indStudy);
				}
				
				for (String tStudy : tSources) {
					HashMap<String, Object> indStudy = GeneralUtils.reformatSourceID(tStudy);
					treeSources.add(indStudy);
				}
			}
		}
		
		// problem: can't pass null objects.
		nodeIfo.put("name", name);
		nodeIfo.put("rank", rank);
		nodeIfo.put("tax_source", taxSource);
		nodeIfo.put("node_id", nodeId);
		// a hack, since OTRepresentationConverter apparently cannot use null values
		if (ottId != null) {
			nodeIfo.put("ott_id", ottId);
		} else {
			nodeIfo.put("ott_id", "null");
		}
		nodeIfo.put("in_graph", inGraph);
		nodeIfo.put("in_synth_tree", inSynthTree);
		nodeIfo.put("num_synth_children", numSynthChildren);
		nodeIfo.put("num_tips", numMRCA);
		nodeIfo.put("synth_sources", synthSources);
		nodeIfo.put("tree_sources", treeSources);
		
		if (includeLineage != null && includeLineage == true) {
			LinkedList<HashMap<String, Object>> lineage = new LinkedList<HashMap<String, Object>>();
			if (inSynthTree) {
				Node n = graphDb.getNodeById(nodeId);
				List<Long> nodeList = getDraftTreePathToRoot(n);

				for (Long node : nodeList) {
					HashMap<String, Object> info = new HashMap<String, Object>();
					addNodeInfo(graphDb.getNodeById(node), info);
					lineage.add(info);
				}
			}
			nodeIfo.put("draft_tree_lineage", lineage);
		}
		// report treeID
		Node meta = ge.getMostRecentSynthesisMetaNode();
		nodeIfo.put("tree_id", meta.getProperty("name"));

		ge.shutdownDB();

		return OTRepresentationConverter.convert(nodeIfo);
	}
	
	
	// fetch the processed input source tree newick from files.opentree.org
	public String getSourceTree(String source) {
		String tree = null;
		String urlbase = "http://files.opentreeoflife.org/preprocessed/v3.0/trees/"
				+ source + ".tre";
		System.out.println("Looking up study: " + urlbase);

		try {
			URL phurl = new URL(urlbase);
			URLConnection conn = (URLConnection) phurl.openConnection();
			conn.connect();
			try (BufferedReader un = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
				tree = un.readLine();
			}
			return tree;
		} catch (Exception e) {
		}
		return tree;
	}
	
	
	public List<Long> getDraftTreePathToRoot(Node startNode) {

		ArrayList<Long> path = new ArrayList<Long>();
		String synthTreeName = (String) GeneralConstants.DRAFT_TREE_NAME.value;

		Node curParent = startNode;
		boolean atRoot = false;
		while (!atRoot) {

			Iterable<Relationship> parentRels = curParent.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING);
			atRoot = true; // assume we have hit the root until proven otherwise
			for (Relationship m : parentRels) {

				if (String.valueOf(m.getProperty("name")).equals(synthTreeName)) {
					atRoot = false;
					curParent = m.getEndNode();
					path.add(curParent.getId());
					break;
				}
			}
		}
		return path;
	}
	
	
	// add information from a node
	private void addNodeInfo(Node n, HashMap<String, Object> results) {

		String name = "";
		String uniqueName = "";
		String rank = "";
		Long ottId = null;
		if (n.hasProperty(NodeProperty.NAME.propertyName)) {
			name = String.valueOf(n.getProperty(NodeProperty.NAME.propertyName));
			uniqueName = String.valueOf(n.getProperty(NodeProperty.NAME_UNIQUE.propertyName));
			rank = String.valueOf(n.getProperty(NodeProperty.TAX_RANK.propertyName));
			ottId = Long.valueOf((String) n.getProperty(NodeProperty.TAX_UID.propertyName));
		}

		results.put("node_id", n.getId());
		results.put("name", name);
		results.put("unique_name", uniqueName);
		results.put("rank", rank);
		if (ottId != null) {
			results.put("ott_id", ottId);
		} else {
			results.put("ott_id", "null");
		}
	}

}