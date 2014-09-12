package opentree.plugins;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import jade.tree.JadeTree;
import jade.JSONMessageLogger;
import opentree.GraphBase;
import opentree.GraphDatabaseAgent;
import opentree.GraphExplorer;
import opentree.MainRunner;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;
import opentree.constants.GeneralConstants;
import opentree.exceptions.MultipleHitsException;
import opentree.exceptions.OttIdNotFoundException;
import opentree.exceptions.TaxonNotFoundException;
import opentree.exceptions.TreeIngestException;
import opentree.exceptions.TreeNotFoundException;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;
import org.neo4j.server.rest.repr.ArgusonRepresentationConverter;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.OTRepresentationConverter;
import org.opentree.properties.OTVocabularyPredicate;

// Graph of Life Services 
public class graph extends ServerPlugin {
	
	
	@Description("Returns summary information about the graph database.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation about (
			@Source GraphDatabaseService graphDb) throws TaxonNotFoundException, MultipleHitsException {
		
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
	
	
	@Description("Returns a newick-fromatted source tree. The returned JSON will have two fields: newick and tree_id.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation source_tree (
			@Source GraphDatabaseService graphDb,
			@Description("The identifier for the source tree to return. Takes format: \"studyid_treeid_GITSHA\"")
			@Parameter(name = "tree_id", optional = false) String treeID,
			@Description("The name of the return format (default is newick)")
			@Parameter(name = "format", optional = true) String format) throws TreeNotFoundException {
		
		// get the tree
		GraphExplorer ge = new GraphExplorer(graphDb);
		JadeTree tree = null;
		try {
			tree = ge.reconstructSource(treeID, -1); // -1 here means no limit on depth
		} finally {
			ge.shutdownDB();
		}
		
		// return results
		HashMap<String, Object> responseMap = new HashMap<String, Object>();
		responseMap.put("newick", tree.getRoot().getNewick(tree.getHasBranchLengths()));
		responseMap.put("tree_id", treeID);
		return OTRepresentationConverter.convert(responseMap);
	}
	
	
	@Description("Returns summary information about a node in the graph.")
	@PluginTarget(GraphDatabaseService.class)
	public Representation node_info (
			@Source GraphDatabaseService graphDb,
			@Description("The node id of the node of interest")
			@Parameter(name = "node_id", optional = true) Long queryNodeId,
			@Description("The ott id of the node of interest")
			@Parameter(name = "ott_id", optional = true) Long queryOttId) {
		
		HashMap<String, Object> nodeIfo = new HashMap<String, Object>();
		
		Long ottId = null;
		String name = "";
		String rank = "";
		String taxSource = "";
		Long nodeId = null;
		//long nodeId = -1;
		boolean inGraph = false;
		boolean inSynthTree = false;
		Integer numSynthChildren = 0;
		Integer numMRCA = 0;
		ArrayList<String> treeSources = new ArrayList<String>();
		ArrayList<String> sources = new ArrayList<String>();
		
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
			} catch (TaxonNotFoundException e) {}
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
				sources = ge.getSynthesisSupportingSources(n);
				treeSources = ge.getSupportingTreeSources(n);
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
		nodeIfo.put("synth_sources", sources);
		nodeIfo.put("tree_sources", treeSources);
		
		ge.shutdownDB();
		
		return OTRepresentationConverter.convert(nodeIfo);
	}

}

