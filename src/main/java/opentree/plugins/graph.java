package opentree.plugins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import jade.tree.JadeTree;
import opentree.GraphDatabaseAgent;
import opentree.GraphExplorer;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.constants.GeneralConstants;

import org.opentree.exceptions.MultipleHitsException;
import org.opentree.exceptions.TaxonNotFoundException;
import org.opentree.exceptions.TreeNotFoundException;
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

// Graph of Life Services 
public class graph extends ServerPlugin {

	
	@Description("Returns summary information about the entire graph database, including identifiers for the "
			+ "taxonomy and source trees used to build it.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation about(@Source GraphDatabaseService graphDb) throws TaxonNotFoundException,
			MultipleHitsException {
		
		System.out.println(GeneralUtils.getTimestamp() + "  Running 'graph/about' service.");
		
		HashMap<String, Object> responseMap = new HashMap<String, Object>();
		
		GraphDatabaseAgent gdb = new GraphDatabaseAgent(graphDb);
		GraphExplorer ge = new GraphExplorer(gdb);
		ge.setQuiet(); // turn off logging

		Node root = ge.getGraphRootNode();

		responseMap.put("graph_root_node_id", root.getId());
		responseMap.put("graph_root_ott_id", Long.valueOf((String) root.getProperty(NodeProperty.TAX_UID.propertyName)));
		responseMap.put("graph_root_name", String.valueOf(root.getProperty(NodeProperty.NAME.propertyName)));
		responseMap.put("graph_taxonomy_version", ge.getTaxonomyVersion());
		responseMap.put("graph_num_tips", ((long[]) root.getProperty(NodeProperty.MRCA.propertyName)).length);

		// *** do we want to return the _list_ of studies?
		responseMap.put("graph_num_source_trees", ge.getSourceList().size());

		ge.shutdownDB();
		logSuccess();
		return OTRepresentationConverter.convert(responseMap);
	}
	
	
	// TODO: change input arguments
	@Description("Returns a source tree (corresponding to a tree in some [study](#studies)) as it exists "
			+ "within the graph. Although the result of this service is a tree corresponding directly to a "
			+ "tree in a study, the representation of the tree in the graph may differ slightly from its "
			+ "canonical representation in the study, due to changes made during tree import (for example, "
			+ "pruning tips from the tree that cannot be mapped to taxa in the graph). In addition, both "
			+ "internal and terminal nodes are labelled ott ids. The tree is returned in newick format.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation source_tree(
			@Source GraphDatabaseService graphDb,
			@Description("The study identifier. Will typically include a prefix (\"pg_\" or \"ot_\")")
			@Parameter(name = "study_id", optional = false) String studyID,
			@Description("The tree identifier for a given study.")
			@Parameter(name = "tree_id", optional = false) String treeID,
			@Description("The git SHA identifying a particular source version.")
			@Parameter(name = "git_sha", optional = false) String gitSHA,
			@Description("The name of the return format. The only currently supported format is newick.")
			@Parameter(name = "format", optional = true) String format) throws TreeNotFoundException {
		
		System.out.println(GeneralUtils.getTimestamp() + "  Running 'graph/source_tree' service.");
		
		HashMap<String, Object> args = new HashMap<String, Object>();
		args.put("study_id", studyID);
		args.put("tree_id", treeID);
		args.put("git_sha", gitSHA);
		args.put("format", format);
		logArguments(args);
		
		HashMap<String, Object> responseMap = new HashMap<String, Object>();
		
		String source = studyID + "_" + treeID + "_" + gitSHA;
		
		// get the tree
		GraphExplorer ge = new GraphExplorer(graphDb);
		JadeTree tree = null;
		try {
			tree = ge.reconstructSource(source, -1); // -1 here means no limit on depth
		} catch (TreeNotFoundException e) {

		} finally {
			ge.shutdownDB();
		}

		if (tree == null) {
			responseMap.put("error", "Invalid source id provided.");
			logError(responseMap);
			return OTRepresentationConverter.convert(responseMap);
		}

		responseMap.put("newick", tree.getRoot().getNewick(tree.getHasBranchLengths()) + ";");
		logSuccess();
		return OTRepresentationConverter.convert(responseMap);
	}
	
	
	@Description("Returns summary information about a node in the graph. The node of interest may be specified "
			+ "using *either* a node id, or an ott id, **but not both**. If the specified node or ott id is not in "
			+ "the graph, an error will be returned.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation node_info(
			@Source GraphDatabaseService graphDb,
			@Description("The node id of the node of interest. This argument may not be combined with `ott_id`.")
			@Parameter(name = "node_id", optional = true) Long nodeId,
			@Description("The ott id of the node of interest. This argument may not be combined with `node_id`.")
			@Parameter(name = "ott_id", optional = true) Long ottId,
			@Description("Include the ancestral lineage of the node in the draft tree. If this argument is `true`, then "
					+ "a list of all the ancestors of this node in the draft tree, down to the root of the tree itself, "
					+ "will be included in the results. Higher list indices correspond to more incluive (i.e. deeper) "
					+ "ancestors, with the immediate parent of the specified node occupying position 0 in the list.") 
			@Parameter(name = "include_lineage", optional = true) Boolean includeLineage) {

		System.out.println(GeneralUtils.getTimestamp() + "  Running 'graph/node_info' service.");
		
		HashMap<String, Object> args = new HashMap<String, Object>();
		args.put("node_id", nodeId);
		args.put("ott_id", ottId);
		args.put("include_lineage", includeLineage);
		logArguments(args);
		
		HashMap<String, Object> responseMap = new HashMap<String, Object>();
		
		Long graphOttId = null;
		String name = "";
		String rank = "";
		String taxSource = "";
		Long graphNodeId = null;
		boolean inSynthTree = false;
		Integer numSynthChildren = 0;
		Integer numMRCA = 0;
		LinkedList<HashMap<String, Object>> synthSources = new LinkedList<HashMap<String, Object>>();
		LinkedList<HashMap<String, Object>> treeSources = new LinkedList<HashMap<String, Object>>();
		
		if (nodeId == null && ottId == null) {
			responseMap.put("error", "Must provide a \"node_id\" or \"ott_id\" argument.");
			logError(responseMap);
			return OTRepresentationConverter.convert(responseMap);
		} else if (nodeId != null && ottId != null) {
			responseMap.put("error", "Provide only one \"node_id\" or \"ott_id\" argument.");
			logError(responseMap);
			return OTRepresentationConverter.convert(responseMap);
		}
		
		GraphExplorer ge = new GraphExplorer(graphDb);
		
		if (ottId != null) {
			Node n = null;
			try {
				n = ge.findGraphTaxNodeByUID(String.valueOf(ottId));
			} catch (TaxonNotFoundException e) {
			}
			if (n != null) {
				graphNodeId = n.getId();
			} else {
				responseMap.put("error", "Could not find any graph nodes corresponding to the ott id provided.");
				logError(responseMap);
				return OTRepresentationConverter.convert(responseMap);
			}
			
		} else if (nodeId != null) {
			Node n = null;
			try {
				n = graphDb.getNodeById(nodeId);
			} catch (NotFoundException e) {

			} catch (NullPointerException e) {

			}
			if (n != null) {
				graphNodeId = nodeId;
			} else {
				responseMap.put("error", "Could not find any graph nodes corresponding to the node id provided.");
				logError(responseMap);
				return OTRepresentationConverter.convert(responseMap);
			}
		}
		
		if (graphNodeId != null) {
			Node n = graphDb.getNodeById(graphNodeId);
			if (n.hasProperty(NodeProperty.NAME.propertyName)) {
				name = String.valueOf(n.getProperty(NodeProperty.NAME.propertyName));
				rank = String.valueOf(n.getProperty(NodeProperty.TAX_RANK.propertyName));
				taxSource = String.valueOf(n.getProperty(NodeProperty.TAX_SOURCE.propertyName));
				graphOttId = Long.valueOf((String) n.getProperty(NodeProperty.TAX_UID.propertyName));
			}
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
		responseMap.put("name", name);
		responseMap.put("rank", rank);
		responseMap.put("tax_source", taxSource);
		responseMap.put("node_id", graphNodeId);
		// a hack, since OTRepresentationConverter apparently cannot use null values
		if (graphOttId != null) {
			responseMap.put("ott_id", graphOttId);
		} else {
			responseMap.put("ott_id", "null");
		}
		responseMap.put("in_synth_tree", inSynthTree);
		responseMap.put("num_synth_tips", numSynthChildren);
		responseMap.put("num_tips", numMRCA);
		responseMap.put("synth_sources", synthSources);
		responseMap.put("tree_sources", treeSources);
		
		if (includeLineage != null && includeLineage == true) {
			LinkedList<HashMap<String, Object>> lineage = new LinkedList<HashMap<String, Object>>();
			if (inSynthTree) {
				Node n = graphDb.getNodeById(graphNodeId);
				List<Long> nodeList = getDraftTreePathToRoot(n);

				for (Long node : nodeList) {
					HashMap<String, Object> info = new HashMap<String, Object>();
					addNodeInfo(graphDb.getNodeById(node), info);
					lineage.add(info);
				}
			}
			responseMap.put("draft_tree_lineage", lineage);
		}
		
		ge.shutdownDB();
		logSuccess();
		return OTRepresentationConverter.convert(responseMap);
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
	
	
	// Send error message to console
	public void logError (HashMap<String, Object> responseMap) {
		System.out.println("\tError: " + responseMap.get("error"));
		System.out.println(GeneralUtils.getTimestamp() + "  Exiting service on error.");
	}
	
	
	public void logSuccess () {
		System.out.println(GeneralUtils.getTimestamp() + "  Exiting service on success.");
	}
	
	// Send passed arguments to console
	public void logArguments (HashMap<String, Object> args) {
		for (Entry<String, Object> entry: args.entrySet()) {
			String key = entry.getKey();
			Object val = entry.getValue();
			if (val != null) {
				System.out.println("\tArgument '" + key + "' = " + val);
			}
		}
	}

}
