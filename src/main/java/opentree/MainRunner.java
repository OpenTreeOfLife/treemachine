package opentree;

import gnu.trove.set.hash.TLongHashSet;
import jade.deprecated.JSONMessageLogger;
import jade.deprecated.MessageLogger;
import jade.tree.deprecated.JadeNode;
import jade.tree.deprecated.JadeTree;
import jade.tree.deprecated.NexsonReader;
import jade.tree.deprecated.TreeReader;
import jade.tree.deprecated.JadeNode.NodeOrder;

import org.opentree.exceptions.DataFormatException;
import org.opentree.utils.GeneralUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.StringTokenizer;

import org.opentree.exceptions.MultipleHitsException;

//import org.apache.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.graphdb.Direction;
//import org.apache.log4j.PropertyConfigurator;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.kernel.EmbeddedGraphDatabase;
//import org.neo4j.graphdb.index.IndexHits;

import org.neo4j.kernel.Traversal;

import opentree.addanalyses.TreeComparator;
import opentree.constants.NodeProperty;
import opentree.constants.RelType;

import org.opentree.exceptions.StoredEntityNotFoundException;
import org.opentree.exceptions.TaxonNotFoundException;

import opentree.exceptions.TreeIngestException;

import org.opentree.exceptions.TreeNotFoundException;

import opentree.synthesis.DraftTreePathExpander;
import opentree.testing.TreeUtils;
import org.opentree.graphdb.GraphDatabaseAgent;

public class MainRunner {
	//static Logger _LOG = Logger.getLogger(MainRunner.class);
	
	// @returns 0 for success, 1 for poorly formed command, -1 for failure to complete well-formed command
	public int taxonomyLoadParser(String [] args) throws TaxonNotFoundException {
		if (args.length < 4) {
			System.out.println("arguments should be: filename (optional:synfilename) taxonomyversion graphdbfolder");
			return 1;
		}
		
		String filename = args[1];
		String graphname = "";
		String synfilename = "";
		String taxonomyversion = "";
		if (args.length == 5) {
			synfilename = args[2];
			taxonomyversion = args[3];
			graphname = args[4];
			System.out.println("initializing taxonomy from " + filename + " with synonyms in " + synfilename + " to "
					+ graphname + " for taxonomy '" + taxonomyversion + "'.");
		} else if (args.length == 4) {
			taxonomyversion = args[2];
			graphname = args[3];
			System.out.println("initializing taxonomy from " + filename + " to " + graphname + " for taxonomy '" + taxonomyversion + "'.");
		} else {
			System.out.println("you have the wrong number of arguments. should be : filename (optional:synonym) taxonomyversion graphdbfolder");
			return 1;
		}
		
		// check if graph already exists. abort if it does to prevent overwriting.
		File f = new File(graphname);
		if (f.exists()) {
			System.err.println("Directory '" + graphname + "' already exists. Exiting...");
			return -1;
		}
		
		GraphInitializer tl = new GraphInitializer(graphname);
		try {
			tl.addInitialTaxonomyTableIntoGraph(filename, synfilename, taxonomyversion);
		} finally {
			tl.shutdownDB();
		}
		return 0;
	}
	
	
	/*
	 *  Initialize graph directly from OTT distribution files.
	 *  Depends on the existence of the following files:
	 *  1. taxonomy.tsv
	 *  2. synonyms.tsv
	 *  3. version.txt
	 *  Will create a DB called 'ott_v[ottVersion].db' e.g. 'ott_v2.8draft5.db'
	*/
	// @returns 0 for success, 1 for poorly formed command, -1 for failure to complete well-formed command
	public int loadOTT(String [] args) throws FileNotFoundException, TaxonNotFoundException {
		if (args.length != 2 && args.length != 3) {
			System.out.println("arguments should be: ott_directory [graph_name (defaults to 'ott_v[ottVersion].db'])");
			return 1;
		}
		
		String ottDir = args[1];
		
		if (args[1].endsWith("/") || args[1].endsWith("\\")) {
			ottDir = args[1].substring(0, args[1].length() - 1);
		}
		
		if (!new File(ottDir).exists()) {
			System.out.println("Directory '" + ottDir + "' not found. Exiting...");
			return -1;
		}
		
		String ottVersion = "";
		String taxFile = ottDir + File.separator + "taxonomy.tsv";
		String synFile = ottDir + File.separator + "synonyms.tsv";
		String versionFile = ottDir + File.separator + "version.txt";
		String graphName = "";
		
		// grab taxonomy version
		try {
			BufferedReader br = new BufferedReader(new FileReader(versionFile));
			ottVersion = br.readLine();
			br.close();
		} catch (FileNotFoundException e) {
			System.err.println("Could not open the file '" + versionFile + "'. Exiting...");
			return -1;
		} catch (IOException ioe) {
			
		}
		
		if (!ottVersion.isEmpty()) {
			ottVersion = "ott_v" + ottVersion;
			if (args.length == 3) {
				graphName = args[2];
			} else {
				graphName = ottVersion + ".db";
			}
		}
		
		// check if graph already exists. abort if it does to prevent overwriting.
		if (new File(graphName).exists()) {
			System.err.println("Graph database '" + graphName + "' already exists. Exiting...");
			return -1;
		}
		if (!new File(taxFile).exists()) {
			System.err.println("Could not open the file '" + taxFile + "'. Exiting...");
			return -1;
		}
		if (!new File(synFile).exists()) {
			System.err.println("Could not open the file '" + synFile + "'. Exiting...");
			return -1;
		}

		System.out.println("Initializing ott taxonomy '" + ottVersion + "' from '" + taxFile + "' with synonyms in '"
				+ synFile + "' to graphDB '" + graphName + "'.");
		
		GraphInitializer tl = new GraphInitializer(graphName);
		try {
			tl.addInitialTaxonomyTableIntoGraph(taxFile, synFile, ottVersion);
		} finally {
			tl.shutdownDB();
		}
		
		return 0;
	}
	
	
	/*
	 * Get the MRCA of a set of nodes. MRCA is calculated from the treeSource, which may be 'taxonomy' or 'synth' (the current
	    synthetic tree). come from either the 1) taxonomy, or 2) synthetic tree (which could be quite different)in the taxonomy.
		Taxa are specified by comma-delimited ottIds. Returns the following information about the MRCA: 1) name, 2) ottId,
		3) rank, and 4) nodeId. Also returns the nodeIDs of the query taxa, and the query target (treeSource)
	 */
	// @returns 0 for success, 1 for poorly formed command
	public int getMRCA(String [] args) throws MultipleHitsException, TaxonNotFoundException {
		
		if (args.length != 4) {
			System.out.println("arguments should be: graphdb treesource('taxonomy' or 'synth') ottIds");
			return 1;
		}
		String graphDb = args[1];
		String treeSource = args[2];
		String slist = args[3];
		boolean taxonomyOnly = true;
		
		if (!treeSource.equalsIgnoreCase("synth") && !treeSource.equalsIgnoreCase("taxonomy")) {
			System.out.println("treesource must be either 'taxonomy' or 'synth'");
			return 1;
		}
		if (treeSource.equalsIgnoreCase("synth")) {
			taxonomyOnly = false;
			System.out.println("Searching for MRCA of taxa against the current synthetic tree.");
		} else {
			System.out.println("Searching for MRCA of taxa against the taxonomy only.");
		}
		
		String [] ottIds = slist.split(",");
		ArrayList<Node> tips = new ArrayList<Node>();
		ArrayList<String> unmatched = new ArrayList<String>();
		GraphExplorer ge = new GraphExplorer(graphDb);
		
		for (String ottId : ottIds) {
			Node n = null;
			try {
				n = ge.findGraphTaxNodeByUID(ottId);
			} catch (TaxonNotFoundException e) {}
			if (n != null) {
				System.out.println("Matched query taxon '" + ottId + "'.");
				if (taxonomyOnly) {
					tips.add(n);
				} else { // need to check if taxon is in the synthetic tree. 
					if (ge.nodeIsInSyntheticTree(n)) {
						tips.add(n);
					} else { // if not in synth (i.e. not monophyletic), grab descendant tips, which *should* be in synth tree
						
						ArrayList<Node> taxTips = new ArrayList<Node>();
						taxTips = ge.getTaxonomyDescendantTips(n);
						System.out.print("Query taxon '" + n.getProperty(NodeProperty.NAME.propertyName) + "' is not monophyletic in the synth tree.");
						System.out.println(" Adding it's " + taxTips.size() + " taxonomic tip descendants for MRCA calculation.");
						tips.addAll(taxTips);	
					}
				}
			} else {
				System.out.println("Failed to match query taxon '" + ottId + "'.");
				unmatched.add(ottId);
			}
		}

		if (tips.size() < 1) {
			throw new IllegalArgumentException("Could not find any graph nodes corresponding to the ottIds provided.");
		} else {
			
			/*
			Node foo = null;
			
			int numIters = 100;
			long startTime = -1;
			long endTime = -1;
			long duration = -1;
			
			
			// These tests don't mean anything anymore, as the old function calls the new one.
			
			startTime = System.nanoTime();
			for (int i = 0; i < numIters; i++) {
				foo = ge.getDraftTreeMRCAForNodes(tips, true);
			}
			endTime = System.nanoTime();
			duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
			
			System.out.println("Old method: name = " + foo.getProperty(NodeProperty.NAME.propertyName));
			System.out.println("Duration for old method: name = " + duration);
			
			startTime = System.nanoTime();
			for (int i = 0; i < numIters; i++) {
				foo = ge.getTaxonomyMRCA(tips);
			}
			endTime = System.nanoTime();
			duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
			
			System.out.println("New method: name = " + foo.getProperty(NodeProperty.NAME.propertyName));
			System.out.println("Duration for new method: name = " + duration);
			
			// Do again...
			
			startTime = System.nanoTime();
			for (int i = 0; i < numIters; i++) {
				foo = ge.getTaxonomyMRCA(tips);
			}
			endTime = System.nanoTime();
			duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
			
			System.out.println("New method: name = " + foo.getProperty(NodeProperty.NAME.propertyName));
			System.out.println("Duration for new method: name = " + duration);
			
			startTime = System.nanoTime();
			for (int i = 0; i < numIters; i++) {
				foo = ge.getDraftTreeMRCAForNodes(tips, true);
			}
			endTime = System.nanoTime();
			duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
			
			System.out.println("Old method: name = " + foo.getProperty(NodeProperty.NAME.propertyName));
			System.out.println("Duration for old method: name = " + duration);
			
			*/
			
			Node mrca = ge.getDraftTreeMRCAForNodes(tips, taxonomyOnly);
			
			// now attempt to find the most recent taxonomic ancestor
			Node mrta = mrca;
			
			if (!unmatched.isEmpty()) {
				System.out.println("Could not map the following ottID:");
				for (String i : unmatched) {
					System.out.println("\t" + i);
				}
			}
			
			if (!taxonomyOnly) {
				while (!mrta.hasProperty(NodeProperty.TAX_UID.propertyName)) {
					mrta = mrta.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
				}
				System.out.println("nearest_taxon_mrca_name: " + mrta.getProperty(NodeProperty.NAME.propertyName));
				System.out.println("nearest_taxon_mrca_rank: " + mrta.getProperty(NodeProperty.TAX_RANK.propertyName));
				System.out.println("nearest_taxon_mrca_ottId: " + mrta.getProperty(NodeProperty.TAX_UID.propertyName));
				System.out.println("nearest_taxon_mrca_nodeId: " + mrta.getId());
				System.out.println("mrca_nodeId: " + mrca.getId());
			} else {
				System.out.println("mrca_name: " + mrta.getProperty(NodeProperty.NAME.propertyName));
				System.out.println("mrca_rank: " + mrta.getProperty(NodeProperty.TAX_RANK.propertyName));
				System.out.println("mrca_ottId: " + mrta.getProperty(NodeProperty.TAX_UID.propertyName));
				System.out.println("mrca_nodeId: " + mrta.getId());
			}
		}
		return 0;
	}
	
	
	/*
	 * Get information about a node. Is it:
	 * 1. In the graph?
	 * 2. In the synthetic tree? If so:
	 * 3. What sources support it?
	 * 4. How many child tips does it have?
	 * 5. Others?!?
	*/
	// @returns 0 for success, 1 for poorly formed command
	public int getNodeStatus(String [] args) {
		
		if (args.length != 3) {
			System.out.println("arguments should be: ottId graphdb");
			return 1;
		}
		String ottId = args[1];
		String graphDb = args[2];
		String name = "";
		String rank = "";
		String taxSource = "";
		Long nodeId = null;
		boolean inGraph = false;
		boolean inSynthTree = false;
		Integer numSynthChildren = 0;
		Integer numMRCA = 0;
		ArrayList<String> sources = null;
		ArrayList<String> treeSources = null;
		
		GraphExplorer ge = new GraphExplorer(graphDb);
		
		Node n = null;
		try {
			n = ge.findGraphTaxNodeByUID(ottId);
		} catch (TaxonNotFoundException e) {}
		if (n != null) {
			nodeId = n.getId();
			if (n.hasProperty(NodeProperty.NAME.propertyName)) {
				name = String.valueOf(n.getProperty(NodeProperty.NAME.propertyName));
				rank = String.valueOf(n.getProperty(NodeProperty.TAX_RANK.propertyName));
				taxSource = String.valueOf(n.getProperty(NodeProperty.TAX_SOURCE.propertyName));
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
		
		System.out.println("Query ottId: " + ottId);
		if (name != null) {
			System.out.println("Name: " + name);
			System.out.println("Rank: " + rank);
			System.out.println("Taxonomy source: " + taxSource);
		}
		System.out.println("NodeId: " + nodeId);
		System.out.println("Is_in_graph: " + inGraph);
		System.out.println("Is_in_synth_tree: " + inSynthTree);
		System.out.println("Num_synth_tips: " + numSynthChildren);
		System.out.println("MRCA_length: " + numMRCA);
		
		if (sources != null) {
			System.out.println("Node is supported by " + sources.size() + " synthesis source tree(s):");
			for (String s : sources) {
				System.out.println("\t" + s);
			}
		} else {
			System.out.println("No synthesis supporting sources found.");
		}
		if (treeSources != null) {
			System.out.println("Node is supported by " + treeSources.size() + " source tree(s):");
			for (String s : treeSources) {
				System.out.println("\t" + s);
			}
		} else {
			System.out.println("No supporting tree sources found.");
		}
		return 0;
	}
	
	
	private static void getMRPmatrix(JadeTree tree, GraphDatabaseAgent graphDb) {
		
		HashMap<JadeNode,StringBuffer> taxastart = new HashMap<JadeNode,StringBuffer>();
		for (int i = 0; i < tree.getExternalNodeCount(); i++) {
			taxastart.put(tree.getExternalNode(i), new StringBuffer(""));
		}
		for (int i = 0; i < tree.getInternalNodeCount(); i++) {
			ArrayList<JadeNode> nds = new ArrayList<JadeNode>();
			for (JadeNode n: tree.getInternalNode(i).getDescendantLeaves()) {
				nds.add(n);
			}
			for (int j = 0; j < tree.getExternalNodeCount(); j++) {
				if (nds.contains(tree.getExternalNode(j))) {
					taxastart.get(tree.getExternalNode(j)).append("1");
				} else {
					taxastart.get(tree.getExternalNode(j)).append("0");
				}
			}
		}
		HashMap<String,StringBuffer> taxafinal = new HashMap<String,StringBuffer>();
		HashMap<JadeNode,String> orignames = new HashMap<JadeNode,String>();
		for (JadeNode jd: taxastart.keySet()) {
			Long taxUID = (Long)jd.getObject("ot:ottId");
			IndexHits<Node> hts = graphDb.getNodeIndex("graphTaxUIDNodes","type", "exact", "to_lower_case", "true").get(NodeProperty.TAX_UID.propertyName, taxUID);
			Node startnode = null;
			try {
				startnode = hts.getSingle();
			} catch (NoSuchElementException ex) {
				throw new MultipleHitsException(taxUID);
			} finally {
				hts.close();
			}
			long[] dbnodei = (long[]) startnode.getProperty("mrca");
			if (dbnodei.length > 0) {
				for (long temp : dbnodei) {
					taxafinal.put(String.valueOf(((Node)graphDb.getNodeById(temp)).getProperty("tax_uid")), taxastart.get(jd));
				}
			} else {
				taxafinal.put(String.valueOf(taxUID), taxastart.get(jd));
			}
			orignames.put(jd, jd.getName());
			jd.setName(String.valueOf(taxUID));
		}
		System.out.println("TREEUID");
		System.out.println(tree.getRoot().getNewick(false) + ";");
		for (int i = 0; i < tree.getExternalNodeCount(); i++) {
			tree.getExternalNode(i).setName(orignames.get(tree.getExternalNode(i)));
		}
		System.out.println("MRP");
		System.out.println(tree.getExternalNodeCount() + "\t" + tree.getInternalNodeCount());
		for (String st: taxafinal.keySet()) {
			System.out.print(st);
			System.out.print("\t");
			System.out.print(taxafinal.get(st));
			System.out.print("\n");
		}
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int graphReloadTrees(String [] args) throws Exception {
		GraphImporter gi = null;
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return 1;
		}
		String graphname = args[1];
		gi = new GraphImporter(graphname);
		try {
			gi.deleteAllTreesAndReprocess();
		} finally {
			gi.shutdownDB();
		}
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int graphDeleteTrees(String [] args) {
		GraphImporter gi = null;
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return 1;
		}
		String graphname = args[1];
		gi = new GraphImporter(graphname);
		try {
			gi.deleteAllTrees();
		} finally {
			gi.shutdownDB();
		}
		return 0;
	}
	
	
	/**
	 * @throws Exception 
	 * @returns 0 for success, 1 for error, 2 for error with a request that the generic help be displayed
	*/
	public int graphImporterParser(String [] args) throws Exception {
		
		if (args.length != 6) {
			System.out.println("arguments should be: filename <taxacompletelyoverlap[T|F]> focalgroup sourcename graphdbfolder");
			return 1;
		}
		
		boolean readingNewick = false;
		boolean readingNewickTNRS = false;
		if (args[0].compareTo("addnewick") == 0) {
			readingNewick = true;
		} else if (args[0].compareTo("addnewickTNRS") == 0) {
			readingNewick = true;
			readingNewickTNRS = true;
		} 
		
		String filename = args[1];
		String soverlap = args[2];
		String focalgroup = args[3];
		String sourcename = args[4];
		String graphname = args[5];
		
		boolean overlap = true;
		if (soverlap.toLowerCase().equals("f")) {
			overlap = false;
		}

		int treeCounter = 0;
		GraphImporter gi = new GraphImporter(graphname);
		MessageLogger messageLogger = new MessageLogger(args[0] + ":");
		try {
			if (gi.hasSourceTreeName(sourcename)) {
				String emsg = "Tree with the name \"" + sourcename + "\" already exists in this db.";
				throw new TreeIngestException(emsg);
			}
			System.out.println("adding tree(s) to the graph from file: " + filename);
			ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
			try {
				if (readingNewickTNRS) { // newick. replace '_' with ' ' in leaf labels to match with existing db
					System.out.println("Reading newick file...");
					TreeReader tr = new TreeReader();
					String ts = "";
					BufferedReader br = new BufferedReader(new FileReader(filename));
					int treeNum = 0;
					while ((ts = br.readLine()) != null) {
						if (ts.length() > 1) {
							++treeNum;
							JadeTree newestTree = tr.readTree(ts);
							
					// change names to match format from nexson (i.e. spaces instead of under_scores)
							Iterable<JadeNode> terp = newestTree.iterateExternalNodes();
							for (JadeNode tt : terp) {
								String newName = tt.getName().replaceAll("_", " ");
								tt.setName(newName);
							}
							
							newestTree.assocObject("id", String.valueOf(treeNum));
							jt.add(newestTree);
							treeCounter++;
						}
					}
					br.close();				
				} else if (readingNewick == true && readingNewickTNRS == false) {
					System.out.println("Reading newick file...");
					TreeReader tr = new TreeReader();
					String ts = "";
					BufferedReader br = new BufferedReader(new FileReader(filename));
					int treeNum = 0;
					while ((ts = br.readLine()) != null) {
						if (ts.length() > 1) {
							++treeNum;
							JadeTree newestTree = tr.readTree(ts);
							
					// change names to match format from nexson (i.e. spaces instead of under_scores)
							Iterable<JadeNode> terp = newestTree.iterateExternalNodes();
							for (JadeNode tt : terp) {
								String newName = tt.getName().replaceAll("_", " ");
								tt.setName(newName);
							}
							
							newestTree.assocObject("id", String.valueOf(treeNum));
							jt.add(newestTree);
							treeCounter++;
						}
					}
					br.close();
				} else { // nexson
					System.out.println("Reading nexson file...");
					for (JadeTree tree : NexsonReader.readNexson(filename, true, messageLogger)) {
						if (tree == null) {
							messageLogger.indentMessage(1, "Skipping null tree.");
						} else {
							jt.add(tree);
							treeCounter++;
						}
					}
				}
			} catch (IOException ioe) {}
			
			System.out.println(treeCounter + " trees read.");
			
			// Do TNRS on trees
			if (readingNewickTNRS == true) {
				System.out.println("Conducting TNRS on input leaf names...");
				GraphDatabaseAgent graphDb = new GraphDatabaseAgent("fool"); // does it matter what the name is?!?
				try {
					boolean good = PhylografterConnector.fixNamesFromTrees(jt, graphDb, true, messageLogger);
					if (good == false) {
						System.out.println("failed to get the names from server fixNamesFromTrees 1");
						messageLogger.close();
						graphDb.shutdownDb();
						return 0;
					}
				} catch(IOException ioe) {
					ioe.printStackTrace();
					System.out.println("failed to get the names from server fixNamesFromTrees 2");
					messageLogger.close();
					graphDb.shutdownDb();
					return 0;
				}
				messageLogger.close();
				graphDb.shutdownDb();
			}
			
			// Go through the trees again and add and update as necessary
			for (int i = 0; i < jt.size(); i++) {
				System.out.println("adding a tree to the graph: " + i);
				gi.setTree(jt.get(i));
				if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
					sourcename = (String)jt.get(i).getObject("ot:studyId");
				}
				if (jt.size() == 1) {
					gi.addSetTreeToGraph(focalgroup, sourcename, overlap, messageLogger);
				} else {
					gi.addSetTreeToGraph(focalgroup, sourcename + "_" + String.valueOf(i), false, messageLogger);
					gi.deleteTreeBySource(sourcename + "_" + String.valueOf(i));	
				}				
			}
			if (jt.size() > 1) {
				for (int i = 0; i < jt.size(); i++) {
					if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
						sourcename = (String)jt.get(i).getObject("ot:studyId");
					}
					System.out.println("adding a tree to the graph: " + i);
					gi.setTree(jt.get(i));
					String tmpName = sourcename + "_" + String.valueOf(i);
					gi.addSetTreeToGraph(focalgroup, tmpName, overlap, messageLogger); //@QUERY treeID has been added, so I'm not sure we want to munge the sourcename
				}
			}
		} finally {
			messageLogger.close();
			gi.shutdownDB();
		}
		return 0;
	}
	
	
	/**
	 * 
	 * @param args
	 * @return 0 for success, 1 for poorly formed command, -1 for failure to complete well-formed command
	 * @throws TaxonNotFoundException
	 */
	
	
	public int graphArgusJSON(String [] args) throws TreeNotFoundException, TaxonNotFoundException {
		GraphExplorer ge = null;
		if (args[0].compareTo("argusjson") == 0) {
			if (args.length != 6) {
				System.err.println("Found " + args.length + " arguments. The arguments should be: synth/source name nodeID maxDepth graphdbfolder");
				return 1;
			}
			String treeType = args[1];
			boolean useSyntheticTree = false;
			if (treeType.equalsIgnoreCase("synth") || treeType.equalsIgnoreCase("synth")) {
				useSyntheticTree = true;
			}
			if (!useSyntheticTree) {
				System.err.println("For now, only the synth tree exploration is supported (expecting the first arg to be \"synth\")");
				return 1;
			}
			String treeID = args[2];
			String nodeID = args[3];
			long subtreeID = 0;
			if (nodeID.length() == 0) {
				nodeID = null;
			} else {
				try {
					subtreeID = Long.parseLong(nodeID, 10);
				} catch (NumberFormatException x) {
					System.err.println("Expecting numeric nodeID got " + nodeID);
					return 1;
				}
			}

			String maxDepthStr = args[4];
			int maxDepth = 5;
			if (maxDepthStr.length() > 0) {
				try {
					maxDepth = Integer.parseInt(maxDepthStr, 10);
				} catch (NumberFormatException x) {
					System.err.println("Expecting number for maxDepth got " + maxDepthStr);
					return 1;
				}
			}
			String graphname = args[5];
			ge = new GraphExplorer(graphname);
			try {
				JadeTree tree;
				if (nodeID == null) {
					tree = ge.reconstructSyntheticTree(treeID, maxDepth);
				} else {
					tree = ge.reconstructSyntheticTree(treeID, subtreeID, maxDepth);
				}
				StringBuffer retB = new StringBuffer("[");
				retB.append(tree.getRoot().getJSON(false));
				retB.append("]");
				System.out.println(retB.toString());
			} finally {
				ge.shutdownDB();
			}
			return 0;
		} else {
			System.err.println("ERROR: not a known command");
			return 2;
		}
	}
	/**
	 * 
	 * @param args
	 * @return 0 for success, 1 for poorly formed command, -1 for failure to complete well-formed command
	 * @throws TaxonNotFoundException
	 * @throws MultipleHitsException 
	 */
	
	
	public int graphExplorerParser(String [] args) throws TaxonNotFoundException, MultipleHitsException {
		GraphExplorer gi = null;
		GraphExporter ge = null;
		if (args[0].compareTo("jsgol") == 0) {
			if (args.length != 3) {
				System.out.println("arguments should be: name graphdbfolder");
				return 1;
			}
			String name = args[1];
			String graphname = args[2];
			ge = new GraphExporter(graphname);
			try {
				System.out.println("constructing a json for: " + name);
				ge.writeJSONWithAltParentsToFile(name);
			} finally {
				ge.shutdownDB();
			}
			return 0;
			
		} else if (args[0].compareTo("fulltree") == 0) {
			String usageString = "arguments should be: name graphdbfolder usetaxonomy[T|F] usebranchandbound[T|F] sinklostchildren[T|F]";
			if (args.length != 6) {
				System.out.println(usageString);
				return 1;
			}

			String name = args[1];
			String graphname = args[2];
			String _useTaxonomy = args[3];
			String _useBranchAndBound = args[4];
			String _sinkLostChildren = args[5];

			boolean useTaxonomy = false;
			if (_useTaxonomy.equals("T")) {
				useTaxonomy = true;
			} else if (!(_useTaxonomy.equals("F"))) {
				System.out.println(usageString);
				return 1;
			}

			boolean useBranchAndBound = false;
			if (_useBranchAndBound.equals("T")) {
				useBranchAndBound = true;
			} else if (!(_useBranchAndBound.equals("F"))) {
				System.out.println(usageString);
				return 1;
			}

			boolean sinkLostChildren = false;
			if (_sinkLostChildren.equals("T")) {
				sinkLostChildren = true;
			} else if (!(_sinkLostChildren.equals("F"))) {
				System.out.println(usageString);
				return 1;
			}

			gi = new GraphExplorer(graphname);
			gi.setSinkLostChildren(sinkLostChildren);
			
			// find the start node
			Node firstNode = gi.findTaxNodeByName(name);
			Long nodeId = null;
			if (firstNode == null) {
				System.out.println("name not found");
				return -1;
			} else {
				nodeId = firstNode.getId();
			}
			JadeTree synthTree = gi.graphSynthesis(firstNode, useTaxonomy, useBranchAndBound,"synthtree");
			gi.shutdownDB();

			// write newick tree to a file
			PrintWriter outFile;
			try {
				outFile = new PrintWriter(new FileWriter(name + ".tre"));
				boolean reportBranchLength = false;
				outFile.write(synthTree.getRoot().getNewick(reportBranchLength) + ";\n");
				outFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			return 0;

		} else if (args[0].compareTo("fulltree_sources") == 0) {
			String usageString = "arguments should be: name preferredsource graphdbfolder usetaxonomy[T|F] sinklostchildren[T|F]";
			if (args.length != 6) {
				System.out.println(usageString);
				return 1;
			}
			
			String name = args[1];
			String sourcenames = args[2];
			String graphname = args[3];
			String _useTaxonomy = args[4];
			String _sinkLostChildren = args[5];
			
			String [] sources = sourcenames.split(",");
			System.out.println("Sources (in order) that will be used to break conflicts");
			for (int i = 0; i < sources.length; i++) {
				System.out.println(sources[i]);
			}

			boolean useTaxonomy = false;
			if (_useTaxonomy.equals("T")) {
				useTaxonomy = true;
			} else if (!(_useTaxonomy.equals("F"))) {
				System.out.println(usageString);
				return 1;
			}

			boolean sinkLostChildren = false;
			if (_sinkLostChildren.equals("T")) {
				sinkLostChildren = true;
			} else if (!(_sinkLostChildren.equals("F"))) {
				System.out.println(usageString);
				return 1;
			}

			gi = new GraphExplorer(graphname);
			gi.setSinkLostChildren(sinkLostChildren);
			
			Node firstNode = gi.findTaxNodeByName(name);
			if (firstNode == null) {
				System.out.println("name not found");
				return -1;
			}
		
			LinkedList<String> preferredSources = new LinkedList<String>();
			String [] tsl = sourcenames.split(",");
			for (int i = 0; i < tsl.length; i++) {preferredSources.add(tsl[i]);}
			JadeTree synthTree = gi.sourceSynthesis(firstNode, preferredSources, useTaxonomy);
			gi.shutdownDB();

			PrintWriter outFile;
			try {
				outFile = new PrintWriter(new FileWriter(name + ".tre"));
				outFile.write(synthTree.getRoot().getNewick(true) + ";\n");
				outFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			return 0;

		} else {
			System.err.println("ERROR: not a known command");
			return 2;
		}
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int justTreeAnalysis(String [] args) throws Exception {
		if (args.length != 5) {
			System.out.println("arguments should be: filename (taxacompletelyoverlap)T|F rootnodename graphdbfolder");
			return 1;
		}
		String filename = args[1];
		String soverlap = args[2];
		boolean overlap = true;
		if (soverlap.toLowerCase().equals("f")) {
			overlap = false;
		}
		String rootnodename = args[3];
		String graphname = args[4];
		int treeCounter = 0;
		// Run through all the trees and get the union of the taxa for a raw taxonomy graph
		// read the tree from a file
		String ts = "";
		ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
		MessageLogger messageLogger = new MessageLogger("justTreeAnalysis:");
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			if (GeneralUtils.divineTreeFormat(br).compareTo("newick") == 0) { // newick
				System.out.println("Reading newick file...");
				TreeReader tr = new TreeReader();
				while ((ts = br.readLine()) != null) {
					if (ts.length() > 1) {
						jt.add(tr.readTree(ts));
						treeCounter++;
					}
				}
			} else { // nexson
				System.out.println("Reading nexson file...");
				for (JadeTree tree : NexsonReader.readNexson(filename, true, messageLogger)) {
					if (tree == null) {
						messageLogger.indentMessage(1, "Skipping null tree.");
					} else {
						jt.add(tree);
						treeCounter++;
					}
				}
			}
			br.close();
		} catch (FileNotFoundException e) {
			//e.printStackTrace();
			System.err.println("Could not open the file \"" + args[1] + "\" Exiting...");
			return -1;
		} catch (IOException ioe) {
			
		}
		
		if (treeCounter == 0) {
			System.err.println("No valid trees found in file \"" + args[1] + "\" Exiting...");
			return -1;
		}
		System.out.println(treeCounter + " trees read.");
		// Should abort here if no valid trees read
		

		HashSet<String> names = new HashSet<String>();
		for (int i = 0; i < jt.size(); i++) {
			for (int j = 0; j < jt.get(i).getExternalNodeCount(); j++) {
				names.add(jt.get(i).getExternalNode(j).getName());
			}
		}
		/*
			 The number of expected properties in "tax.temp" has changed:
			  String tid = st.nextToken().trim();
			  String parent_uid = st.nextToken().trim();
			  String name = st.nextToken().trim();
			  String rank = st.nextToken().trim();
			  String sourceinfo = st.nextToken().trim();
			  String uniqname = st.nextToken().trim();
			  String flags = st.nextToken().trim();
			  uid	|	parent_uid	|	name	|	rank	|	sourceinfo	|	uniqname	|	flags	|	
			  No header is printed to the temporary taxonomy
		 */	
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter("tax.temp"));
			ArrayList<String> namesal = new ArrayList<String>();
			namesal.addAll(names);
			for (int i = 0; i < namesal.size(); i++) {
				outFile.write((i+2) + "\t|\t1\t|\t" + namesal.get(i) + "\t|\t\t|\t\t|\t\t|\t\t|\t\n");
			}
			outFile.write("1" + "\t|\t\t|\t" + rootnodename + "\t|\t\t|\t\t|\t\t|\t\t|\t\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		GraphInitializer gin = new GraphInitializer(graphname);
		// make a temp file to be loaded into the tax loader, a hack for now
		// "justtrees" is the name of the taxonomy
		gin.addInitialTaxonomyTableIntoGraph("tax.temp", "", "justtrees");
		// Use the taxonomy as the first tree in the composite tree
		gin.shutdownDB();
		
		GraphImporter gi = new GraphImporter(graphname);
		System.out.println("started graph importer");
		/*
		 * if the taxa are not completely overlapping, we add, add again, then delete the first ones
		 * this is the first add
		 */
		if (overlap == false) {
			// Go through the trees again and add and update as necessary
			for (int i = 0; i < jt.size(); i++) {
				String sourcename = "treeinfile";
				if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
					sourcename = (String)jt.get(i).getObject("ot:studyId");
				}
				sourcename += "t_" + String.valueOf(i);
	
				System.out.println("adding tree '" + sourcename + "' to the graph");
				jt.get(i).assocObject("id", String.valueOf(i));
				gi.setTree(jt.get(i));
				gi.addSetTreeToGraph(rootnodename, sourcename, overlap, messageLogger);
				//gi.deleteTreeBySource(sourcename);
			}
		}
		
		/*
		 * If the taxa overlap completely, we only add once, this time
		 * If the taxa don't overlap, this is the second time
		 */
		for (int i = 0; i < jt.size(); i++) {
			String sourcename = "treeinfile";
			if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
				sourcename = (String)jt.get(i).getObject("ot:studyId");
			}
			sourcename += "_" + String.valueOf(i);

			System.out.println("adding tree '" + sourcename + "' to the graph");
			jt.get(i).assocObject("id", String.valueOf(i));
			gi.setTree(jt.get(i));
			gi.addSetTreeToGraph(rootnodename, sourcename, overlap, messageLogger);
		}
		/*
		 * If the taxa don't overlap, we delete the second set of trees
		 */
		if (overlap == false) {
			for (int i = 0; i < jt.size(); i++) {
				String sourcename = "treeinfile";
				if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
					sourcename = (String)jt.get(i).getObject("ot:studyId");
				}
				sourcename += "t_" + String.valueOf(i);
				gi.deleteTreeBySource(sourcename);
			}
		}
		gi.shutdownDB();
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int graphListPruner(String [] args) throws TaxonNotFoundException, MultipleHitsException {
		if (args.length != 4) {
			System.out.println("arguments should be: name preferredsource graphdbfolder");
			return 1;
		}

		String filename = args[1];
		HashSet<String> speciesnames = new HashSet<String>();
		String ts = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			while ((ts = br.readLine()) != null) {
				if (ts.length() > 1)
					speciesnames.add(ts);
			}
			br.close();
		} catch (IOException ioe) {}
		System.out.println("read " + speciesnames.size() + " taxa from " + filename);
		System.out.println("(these will have to match exactly, so doing that check now)");
		String graphname = args[3];
		GraphExplorer gi = new GraphExplorer(graphname);
		try {
			HashSet<Long> fnodes = new HashSet<Long>();
			for (String tn: speciesnames) {
				Node t = gi.findTaxNodeByName(tn);
				if (t != null) {
					fnodes.add(t.getId());
				} else {
					System.out.println(tn + " not found");
				}
			}
			String sourcename = args[2];
			String [] sources = sourcename.split(",");
			System.out.println("Sources (in order) that will be used to break conflicts");
			for (int i = 0; i < sources.length; i++) {
				System.out.println(sources[i]);
			}
			gi.constructNewickTaxaListTieBreaker(fnodes,sources);
			//gi.constructNewickSourceTieBreaker(name, sources);
		} finally {
			gi.shutdownDB();
		}
		return 0;
	}
	
	
	// @returns 0 for success, 1 for poorly formed command
	public int sourceTreeExplorer(String [] args) throws TreeNotFoundException {
		String sourcename = null;
		String graphname = null;
		String rootNodeID = null;
		int maxDepth = -1;
		if (args[0].equalsIgnoreCase("sourceexplorer") || args[0].equalsIgnoreCase("sourceexplorer_inf_mono")) {
			if (args.length == 3) {
				sourcename = args[1];
				graphname = args[2];
			} else {
				System.out.println("arguments should be: <sourcename> <graphdbfolder>");
				return 1;
			}
		} else {
			if (args.length == 5) {
				sourcename = args[1];
				rootNodeID = args[2];
				maxDepth = Integer.parseInt(args[3]);
				graphname = args[4];
			} else {
				System.out.println("arguments should be: <sourcename> <rootNodeID> <maxDepth> <graphdbfolder>");
				return 1;
			}
		}
		
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			JadeTree tree = null;
			if (rootNodeID == null) {
				if (args[0].equalsIgnoreCase("sourceexplorer")) {
					tree = ge.reconstructSource(sourcename, maxDepth);
				} else if (args[0].equalsIgnoreCase("sourceexplorer_inf_mono")) {
					ge.getInformationAndMonophylyTreeVsTaxonomy(sourcename);
				}
			} else {
				long rootNodeIDParsed = Long.parseLong(rootNodeID);
				tree = ge.reconstructSource(sourcename, rootNodeIDParsed, maxDepth);
			}
			if (args[0].equalsIgnoreCase("sourceexplorer_inf_mono") == false) {
				String newick = tree.getRoot().getNewick(tree.getHasBranchLengths()) + ";";
				System.out.println(newick);
//				System.out.println("Tree has ELs: " + tree.getHasBranchLengths());
			}
//			else {
//				System.out.println("I am somehow here...\n");
//			}
		} catch (NoSuchElementException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
		}
		finally {
			ge.shutdownDB();
		}
		return 0;
	}

	
	/// @returns 0 for success, 1 for poorly formed command
	public int listSources(String [] args) {
		boolean listIDs = false;
		String graphname;
		if (args.length == 2) {
			graphname = args[1];
		} else if (args.length == 3) {
			if (args[1].compareTo("id") != 0) {
				System.out.println("arguments should be:\n <graphdbfolder>\nor\n id <graphdbfolder>\n");
				return 1;
			}
			graphname = args[2];
			listIDs = true;
		} else {
			System.out.println("arguments should be: graphdbfolder");
			return 1;
		}
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			ArrayList<String> result;
			if (listIDs) {
				result = ge.getTreeIDList();
			} else {
				result = ge.getSourceList();
			}
			System.out.println(StringUtils.join(result, "\n"));
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	
	
	// Report which taxonomy (e.g. version of OTT) was used to initialize the graph
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure to complete well-formed command
	public int getTaxonomyVersion(String [] args) {
		String graphname;
		
		if (args.length != 2) {
			System.out.println("arguments should be: <graphdbfolder>");
			return 1;
		}
		graphname = args[1];
		
		if (!new File(graphname).exists()) {
			System.err.println("Graph database '" + graphname + "' not found. Exiting...");
			return -1;
		}
		
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			String taxVersion = "";
			taxVersion = ge.getTaxonomyVersion();
			System.out.println("The graph was initialized with the taxonomy '" + taxVersion + "'.");
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	

	public int getTaxonomyTreeExport(String [] args) {
		if (args.length != 4) {
			System.out.println("arguments should be: uid outfile graphdbfolder");
			return 1;
		}
		String startuid = args[1];
		String outfile = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);
		JadeNode root =  null;
		List<String> taxonList = new ArrayList<String>();;
		try {
			Node tnode = ge.findGraphTaxNodeByUID(startuid);
			root = new JadeNode();
			root.setName((String)tnode.getProperty(NodeProperty.TAX_UID.propertyName));
			System.out.println(root.getName());
			HashMap<Node,JadeNode> nodemp = new HashMap<Node,JadeNode>();
			nodemp.put(tnode, root);
			for (Node m : Traversal.description().relationships(RelType.TAXCHILDOF, Direction.INCOMING).traverse(tnode).nodes()) {
				if (m == tnode) {
					continue;
				}
				Node p = m.getSingleRelationship(RelType.TAXCHILDOF,Direction.OUTGOING).getEndNode();
				JadeNode pa = nodemp.get(p);
				JadeNode tn = new JadeNode(pa);
				pa.addChild(tn);
				tn.setParent(pa);
				taxonList.add((String)m.getProperty(NodeProperty.TAX_UID.propertyName));
				tn.setName((String)m.getProperty(NodeProperty.TAX_UID.propertyName));
				nodemp.put(m, tn);
			}
		} catch (MultipleHitsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TaxonNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			ge.shutdownDB();
		}
		JadeTree tt = new JadeTree(root);
		
		// list of taxa
		try {
			FileWriter fw = new FileWriter(outfile + "_taxonList");
			for (String i : taxonList) {
				fw.write(i+"\n");
			}
			fw.write(startuid+"\n");
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			FileWriter fw = new FileWriter(outfile);
			fw.write(tt.getRoot().getNewick(false) + ";");
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int graphExplorerBiparts(String [] args) {
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return 1;
		}
		String graphname = args[1];
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			ge.getBipartSupport("life"); // need to change this from hardcoded
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int graphExplorerMapSupport(String [] args) throws TaxonNotFoundException, MultipleHitsException {
		if (args.length != 4) {
			System.out.println("arguments should be infile outfile graphdbfolder");
			return 1;
		}
		String infile = args[1];
		String outfile = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			ge.getMapTreeSupport(infile, outfile);
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	
	/// @returns 0 for success, 1 for poorly formed command
	public int dotGraphExporter(String [] args) throws TaxonNotFoundException {
		String usageString = "arguments should be name outfile usetaxonomy[T|F] graphdbfolder";
		if (args.length != 5) {
			System.out.println(usageString);
			return 1;
		}
		String taxon = args[1];
		String outfile = args[2];
		String _useTaxonomy = args[3];
		String graphname = args[4];
		
		boolean useTaxonomy = false;
		if (_useTaxonomy.equals("T")) {
			useTaxonomy = true;
		} else if (!(_useTaxonomy.equals("F"))) {
			System.out.println(usageString);
			return 1;
		}
		
		GraphExporter ge = new GraphExporter(graphname);
		try {
			ge.writeGraphDot(taxon, outfile, useTaxonomy);
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}

	/// @returns 0 for success, 1 for poorly formed command
	public int graphExporter(String [] args) throws TaxonNotFoundException {
		String usageString = "arguments should be name outfile usetaxonomy[T|F] graphdbfolder";
		if (args.length != 5) {
			System.out.println(usageString);
			return 1;
		}
		String taxon = args[1];
		String outfile = args[2];
		String _useTaxonomy = args[3];
		String graphname = args[4];
		
		boolean useTaxonomy = false;
		if (_useTaxonomy.equals("T")) {
			useTaxonomy = true;
		} else if (!(_useTaxonomy.equals("F"))) {
			System.out.println(usageString);
			return 1;
		}
		
		GraphExporter ge = new GraphExporter(graphname);
		try {
			ge.writeGraphML(taxon, outfile, useTaxonomy);
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	
	
	public int graphExporter_ottolid(String [] args) throws TaxonNotFoundException {
		String usageString = "arguments should be ottolid outfile usetaxonomy[T|F] graphdbfolder [depth]";
		if (args.length != 5 && args.length != 6) {
			System.out.println(usageString);
			return 1;
		}
		String ottolid = args[1];
		String outfile = args[2];
		String _useTaxonomy = args[3];
		String graphname = args[4];
		int depth = 0;
		if (args.length == 6) {
			depth = Integer.valueOf(args[5]);
		}
		
		boolean useTaxonomy = false;
		if (_useTaxonomy.equals("T")) {
			useTaxonomy = true;
		} else if (!(_useTaxonomy.equals("F"))) {
			System.out.println(usageString);
			return 1;
		}
		
		GraphExporter ge = new GraphExporter(graphname);
		try {
			ge.writeGraphMLDepth_ottolid(ottolid, outfile, useTaxonomy, depth);
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	
	
	public int mrpDumpParser(String [] args) throws TaxonNotFoundException {
		if (args.length > 4) {
			System.out.println("arguments should be name outfile graphdbfolder");
			return 1;
		}
		String taxon = args[1];
		String outfile = args[2];
		String graphname = args[3];
		GraphExporter ge = new GraphExporter(graphname);
		try {
			ge.mrpDump(taxon, outfile);
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int csvDumpParser(String [] args) throws TaxonNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be name outfile graphdbfolder");
			return 1;
		}
		String taxon = args[1];
		String outfile = args[2];
		String graphname = args[3];
		GraphExporter ge = new GraphExporter(graphname);
		try {
			ge.dumpCSV(taxon, outfile,true);
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int getLicaNames(String [] args) {
		if (args.length != 3) {
			System.out.println("arguments should be nodeid graphdbfolder");
			return 1;
		}
		String nodeid = args[1];
		String graphname = args[2];
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			ge.printLicaNames(nodeid);
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}
	
	
	public int treeUtils(String [] args) {
		if (args.length < 2) {
			System.out.println("arguments need to at least be a treefilename");
			return 1;
		}
		String filename = args[1];
		String ts = "";
		ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
		TreeReader tr = new TreeReader();
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			while ((ts = br.readLine()) != null) {
				if (ts.length() > 1)
					jt.add(tr.readTree(ts));
			}
			br.close();
		} catch (IOException ioe) {}
		System.out.println("trees read");
		if (args[0].equals("counttips")) {
			System.out.println("int: " + jt.get(0).getInternalNodeCount() + " ext:" + jt.get(0).getExternalNodeCount());
			return 0;
		}
		if (args[0].equals("diversity")) {
			for (int i = 0; i < jt.get(0).getInternalNodeCount(); i++) {
				if (jt.get(0).getInternalNode(i).getName().length() > 0) {
					System.out.print(jt.get(0).getInternalNode(i).getName() + " :" + jt.get(0).getInternalNode(i).getTipCount());
					for (int j = 0; j < jt.get(0).getInternalNode(i).getChildCount(); j++) {
						System.out.print(" || " + jt.get(0).getInternalNode(i).getChild(j).getName() + " :" + jt.get(0).getInternalNode(i).getChild(j).getTipCount());
					}
					System.out.print("\n");
				}
			}
			return 0;
		}
		if (args[0].equals("labeltips")) {
			String filename2 = args[2];
			ts = "";
			int count = 0;
			HashSet<String> names = new HashSet<String>();
			try {
				BufferedReader br = new BufferedReader(new FileReader(filename2));
				while ((ts = br.readLine()) != null) {
					String [] tss = ts.split("\t");
					if (tss.length == 4) {
						if (tss[3].contains("ncbi")) {
							names.add(tss[2]);
							count += 1 ;
						}
					}
				}
				br.close();
			} catch (IOException ioe) {}
			System.out.println(count);
			System.out.println("names read");
			count = 0;
			for (int i = 0; i < jt.get(0).getExternalNodeCount(); i++) {
				if (names.contains(jt.get(0).getExternalNode(i).getName())) {
					jt.get(0).getExternalNode(i).setBL(1.0);
				}
			}
			for (int i = 0; i < jt.get(0).getInternalNodeCount(); i++) {
				if (names.contains(jt.get(0).getInternalNode(i).getName())) {
					jt.get(0).getInternalNode(i).setBL(1.0);
				}
			}
			System.out.println(count);
			System.out.println(jt.get(0).getRoot().getNewick(true) + ";");
			return 0;
		}
		// relabel tree from ottIds to names e.g. 654722 -> Crax_alector_654722
		if (args[0].equals("labeltipsottol")) {
			GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[2]);
			for (int i = 0; i < jt.get(0).getExternalNodeCount(); i++) {
				JadeNode jd = jt.get(0).getExternalNode(i);
				Long taxUID = Long.valueOf(jd.getName().replaceAll("\\s+",""));
				IndexHits<Node> hts = graphDb.getNodeIndex("graphTaxUIDNodes","type", "exact", "to_lower_case", "true").get(NodeProperty.TAX_UID.propertyName, taxUID);
				Node startnode = null;
				try {
					startnode = hts.getSingle();
				} catch (NoSuchElementException ex) {
					throw new MultipleHitsException(taxUID);
				} finally {
					hts.close();
				}
				jd.setName((String)startnode.getProperty("name") + "_ott" + String.valueOf(taxUID));
			}
			for (int i = 0; i < jt.get(0).getInternalNodeCount(); i++) {
				JadeNode jd = jt.get(0).getInternalNode(i);
				try {
					Long taxUID = Long.valueOf(jd.getName().replaceAll("\\s+",""));
					IndexHits<Node> hts = graphDb.getNodeIndex("graphTaxUIDNodes","type", "exact", "to_lower_case", "true").get(NodeProperty.TAX_UID.propertyName, taxUID);
					Node startnode = null;
					try {
						startnode = hts.getSingle();
					} catch (NoSuchElementException ex) {
						throw new MultipleHitsException(taxUID);
					} finally {
						hts.close();
					}
					jd.setName((String)startnode.getProperty("name") + "_ott" + String.valueOf(taxUID));
				} catch(Exception ex2) {
					continue;
				}
			}
			System.out.println(jt.get(0).getRoot().getNewick(false) + ";");
			return 0;
		}
		if (args[0].equals("convertfigtree") || args[0].equals("convertfigtree2")) {
			//the idea here is to generate another newick that will have internal labels the width of the branch
			//polytomies will have one node and it will have this width
			HashMap<String,String> source_mp = new HashMap<String,String>();
			HashMap<String,Double> id_counts = new HashMap<String,Double>();
			if (args.length == 4 && args[0].equals("convertfigtree")) {
				try {
					BufferedReader brt = new BufferedReader(new FileReader(args[3]));
					while ((ts = brt.readLine()) != null) {
						StringTokenizer st = new StringTokenizer(ts,"\t|\t");
						String tid = st.nextToken();
						String par = st.nextToken();
						st.nextToken();
						st.nextToken();
						String source = st.nextToken();
						source_mp.put(tid, source);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				getNCBICountsFigtreeNewick(jt.get(0).getRoot(),source_mp);
			}
			if (args.length == 4 && args[0].equals("convertfigtree2")) {
				try {
					BufferedReader brt = new BufferedReader(new FileReader(args[3]));
					while ((ts = brt.readLine()) != null) {
						String tid = ts.trim();
						if (id_counts.containsKey(tid) == false) {
							id_counts.put(tid,0.);
						}
						id_counts.put(tid,id_counts.get(tid)+1);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				getOTTCountsFigtreeNewick(jt.get(0).getRoot(),id_counts);
			}

			try {
				ArrayList<JadeNode> destroy = new ArrayList<JadeNode>();
				for (int i = 0; i < jt.get(0).getInternalNodeCount(); i++) {
					JadeNode t = jt.get(0).getInternalNode(i);
					if (args.length != 4) {
						t.assocObject("tipcount", Math.log10(t.getTipCount()));
					}
					for (int j = 0; j < t.getChildCount(); j++) {
						if (t.getChild(j).getTipCount() < 500) {// ||t.getChild(j).getChildCount() < 2) {//  || t.getChild(j).getName().split("_").length > 2) {
							destroy.add(t.getChild(j));
						}
					}
				}
				for (int i = 0; i < destroy.size(); i++) {
					if (destroy.get(i).getParent().getName().length() < 2) {
						destroy.get(i).getParent().setName(destroy.get(i).getName());
					}
					destroy.get(i).getParent().removeChild(destroy.get(i));
				}
				destroy.clear();
				//now we take out the polytomies
				for (int i = 0; i < jt.get(0).getInternalNodeCount(); i++) {
					JadeNode t = jt.get(0).getInternalNode(i);
					if (t.getChildCount() > 2) {
						for (int j = 0; j < t.getChildCount(); j++) {
							if (t.getChild(j).getChildCount() < 1) {//sometimes I do 2
								destroy.add(t.getChild(j));
							}
						}
					}
				}
				for (int i = 0; i < destroy.size(); i++) {
					if (destroy.get(i).getParent().getName().length() < 2) {
						destroy.get(i).getParent().setName(destroy.get(i).getName());
					}
					//Double v = (Double)destroy.get(i).getParent().getObject("value");
					//Double tv = (Double)destroy.get(i).getObject("value");
					destroy.get(i).getParent().removeChild(destroy.get(i));
				}
				FileWriter fw = new FileWriter(args[2]);
				fw.write("#nexus\nbegin trees;\ntree a = ");
				fw.write(getSynthMinorFigtreeNewick(jt.get(0).getRoot()) + ";");
				fw.write("\nend;\n");
				fw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return 0;
		}
		System.err.println("Unrecognized command argument \"" + args[0] + "\"");
		return 2;
	}
	
	
	/**
	 * this will just fill out the ncbi counts
	 * @param innode
	 * @param id_childs
	 * @param source_mp
	 */
	private double getNCBICountsFigtreeNewick(JadeNode innode,HashMap<String,String> source_mp) {
		double childcount = 0;
		for (int i = 0; i < innode.getChildCount(); i++) {
			childcount += getNCBICountsFigtreeNewick(innode.getChild(i),source_mp);
		}
		double count = 0;
		if (innode.isExternal()) {
			String nm = innode.getName().split("_")[innode.getName().split("_").length-1].replace("ott", "");
			if (source_mp.containsKey(nm)) {
				if (source_mp.get(nm).contains("ncbi")) {
					count = 1;
				}
			}
		}
		count += childcount;
		//innode.assocObject("tipcount", Math.log10(count));
		innode.assocObject("tipcount",count/innode.getTipCount());
		return count;
	}

	
	/**
	 * this will just fill out the ott counts
	 * @param innode
	 * @param id_childs
	 */
	private double getOTTCountsFigtreeNewick(JadeNode innode,HashMap<String,Double> id_counts) {
		double childcount = 0;
		for (int i = 0; i < innode.getChildCount(); i++) {
			childcount += getOTTCountsFigtreeNewick(innode.getChild(i),id_counts);
		}
		double count = 0;
		String nm = innode.getName().split("_")[innode.getName().split("_").length-1].replace("ott", "");
		if (id_counts.containsKey(nm)) {
			count = id_counts.get(nm);
		}
		count += childcount;
		//innode.assocObject("tipcount", Math.log10(count));
		if (count > 0) {
			innode.assocObject("tipcount",1.);
		}
		if (count == 0) {
			innode.assocObject("tipcount",0.);
		}
		return count;
	}
	
	
	/**
	 * similar to the getNewick but doesn't go to the tips and adds a &label for thickness of the branches
	 * @param bl
	 * @return
	 */
	private String getSynthMinorFigtreeNewick(JadeNode innode) {
		StringBuffer ret = new StringBuffer("");
		for (int i = 0; i < innode.getChildCount(); i++) {
			if (i == 0) {
				ret.append("(");
			}
			ret.append(getSynthMinorFigtreeNewick(innode.getChild(i)));
			
			
			if (i == innode.getChildCount() - 1) {
				ret.append(")");
			} else {
				ret.append(",");
			}
		}
		if (innode.getName() != null) {
			String [] spls = innode.getName().split("_");
			String tname = spls[0];
			for (int i = 1; i < spls.length; i++) {
				tname += "_" +spls[i];
			}
//			ret.append(GeneralUtils.cleanName(tname));
			ret.append(GeneralUtils.scrubName(tname));
		}
		double value = (Double)innode.getObject("tipcount");
		ret.append("[&tipcount=".concat(String.valueOf(value)).concat("]"));
		return ret.toString();
	}
	
	
	/*
	 * these are treeutils that need the database
	 * 
	 * checktaxhier - takes a tree with ottid
	 */
	public int treeUtilsDB(String [] args) {
		if (args.length != 3) {
			System.out.println("arguments should be treefile graphdbfolder");
			return 1;
		}
		//read trees
		String filename = args[1];
		String ts = "";
		ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
		TreeReader tr = new TreeReader();
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			while ((ts = br.readLine()) != null) {
				if (ts.length() > 1) {
					jt.add(tr.readTree(ts));
				}
			}
			br.close();
		} catch (IOException ioe) {}
		System.out.println("trees read");
		//
		boolean success = false;
		String graphdbn = args[2];
		
		String arg = args[0];
		MessageLogger messageLogger = new MessageLogger("treeUtilsDB", " ");
		if (arg.equals("labeltax")) {
			GraphExplorer ge = new GraphExplorer(graphdbn);	
			for (int i = 0; i < jt.size(); i++) {
				ge.labelInternalNodesTax(jt.get(i), messageLogger);
				System.out.println(jt.get(i).getRoot().getNewick(false) + ";");
			}
			messageLogger.close();
			ge.shutdownDB();
		} else if (arg.equals("checktax")) {
			GraphDatabaseAgent graphDb = new GraphDatabaseAgent(graphdbn);
			try {
				boolean good = PhylografterConnector.fixNamesFromTrees(jt, graphDb, false, messageLogger);
				if (good == false) {
					System.out.println("failed to get the names from server fixNamesFromTrees 1");
					messageLogger.close();
					graphDb.shutdownDb();
					return 0;
				}
			} catch(IOException ioe) {
				ioe.printStackTrace();
				System.out.println("failed to get the names from server fixNamesFromTrees 2");
				messageLogger.close();
				graphDb.shutdownDb();
				return 0;
			}
			messageLogger.close();
			graphDb.shutdownDb();
		} else if (arg.equals("checktaxhier")) {
			GraphDatabaseAgent graphDb = new GraphDatabaseAgent(graphdbn);
			HashSet<Node> nodes = new HashSet<Node>();
			for (int i = 0; i < jt.get(0).getExternalNodeCount(); i++) {
				Long taxUID = Long.valueOf(jt.get(0).getExternalNode(i).getName());
				IndexHits<Node> hts = graphDb.getNodeIndex("graphTaxUIDNodes","type", "exact", "to_lower_case", "true").get(NodeProperty.TAX_UID.propertyName, taxUID);
				Node startnode = null;
				try {
					startnode = hts.getSingle();
				} catch (NoSuchElementException ex) {
					throw new MultipleHitsException(taxUID);
				} finally {
					hts.close();
				}
				nodes.add(startnode);
			}
			HashSet<String> toprune= new HashSet<String>();
			for (Node nd: nodes) {
				Node tnd = nd; 
				while (tnd.hasRelationship(RelType.TAXCHILDOF, Direction.OUTGOING)) {
					tnd = tnd.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
					if (nodes.contains(tnd)) {
						toprune.add((String) tnd.getProperty(NodeProperty.TAX_UID.propertyName));
					}
				}
			}
			for (String s: toprune) {
				JadeNode c = jt.get(0).getExternalNode(s);
				JadeNode p = c.getParent();
				p.removeChild(c);
			}
			System.out.println(jt.get(0).getRoot().getNewick(false) + ";");
			graphDb.shutdownDb();
		}
		return (success ? 0 : -1);
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int synthesizeDraftTreeWithListForTaxUID(String [] args) throws Exception {

		// open the graph
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);
		String ottId = args[1];

		// get the start node
		String startNodeIdStr = String.valueOf(ge.findGraphTaxNodeByUID(ottId).getId());
		args[1] = startNodeIdStr;
		ge.shutdownDB();
		// do the synth
		return synthesizeDraftTreeWithListForNodeId(args);
	}
	

	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int synthesizeDraftTreeWithListForNodeId(String [] args) throws Exception {
		
		boolean test = false; 
		if (args.length != 4 && args.length != 5) {
			System.out.println("arguments should be rootottId listofsources(CSV) graphdbfolder (test)");
			return 1;
		}
		if (args.length == 5) {
			System.out.println("test is set, so the synthesis will not be stored");
			test = true;
		}
		Long startNodeId = Long.valueOf(args[1]);
		String slist = args[2];
		String graphname = args[3];
		boolean success = false;
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			// build the list of preferred sources, this should probably be done externally
			LinkedList<String> preferredSources = new LinkedList<String>();
			String [] tsl = slist.split(",");
			for (int i = 0; i < tsl.length; i++) {preferredSources.add(tsl[i]);}
			System.out.println(preferredSources);
			//preferredSources.add("taxonomy");, need to add taxonomy 

			// find the start node
			Node firstNode = ge.graphDb.getNodeById(startNodeId);

			try {
				success = ge.synthesizeAndStoreDraftTreeBranches(firstNode, preferredSources, test);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("done with synthesis");
		} finally {
			ge.shutdownDB();
		}
		return (success ? 0 : -1);
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int synthesizeDraftTree(String [] args) throws Exception {
		boolean test = false;
		if (args.length != 3) {
			System.out.println("arguments should be rootottId graphdbfolder");
			return 1;
		}
		String ottId = args[1];
		String graphname = args[2];
		boolean success = false;
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			// build the list of preferred sources, this should probably be done externally
			LinkedList<String> preferredSources = new LinkedList<String>();
			preferredSources.add("15");
			preferredSources.add("taxonomy");
			
			// find the start node
			Node firstNode = ge.findGraphTaxNodeByUID(ottId);
			if (firstNode == null) {
				throw new TaxonNotFoundException(ottId);
			}
			try {
				success = ge.synthesizeAndStoreDraftTreeBranches(firstNode, preferredSources,test);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (TaxonNotFoundException oex) {
			oex.printStackTrace();
		} finally {
			ge.shutdownDB();
		}
		return (success ? 0 : -1);
	}
	

	public int getSynthesisInfo(String [] args) {
		boolean success = true;
		if (args.length != 2 && args.length != 3) {
			System.out.println("arguments should be graphdbfolder or ottolID graphdbfolder");
			return 1;
		}
		String graphname = null;
		Node startnode = null;

		GraphExplorer ge; 
		if (args.length == 2) {
			graphname = args[1];
			ge = new GraphExplorer(graphname);
		} else {
			graphname = args[2];
			ge = new GraphExplorer(graphname);
			try {
				startnode = ge.findGraphTaxNodeByUID(args[1]);
			} catch (MultipleHitsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TaxonNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println(ge.getNumberSynthesisTips(startnode));
		return (success ? 0 : -1);
	}
	
	
	// gets graph nodeid from ottid
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftTreeForOttId(String [] args) throws MultipleHitsException, TaxonNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be rootNodeOttId outfilename graphdbfolder");
			return 1;
		}
		// open the graph
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);
		String ottId = args[1];

		// get the start node
		String startNodeIdStr = String.valueOf(ge.findGraphTaxNodeByUID(ottId).getId());
		args[1] = startNodeIdStr;
		ge.shutdownDB();
		
		return extractDraftTreeForNodeId(args);
	}
	
	
	public int extractDraftTreeForOttIdRelIDMap(String [] args) throws MultipleHitsException, TaxonNotFoundException {
		if (args.length != 5) {
			System.out.println("arguments should be rootNodeOttId relidinfile outfilename graphdbfolder");
			return 1;
		}
		// open the graph
		String graphname = args[4];
		GraphExplorer ge = new GraphExplorer(graphname);
		String ottId = args[1];

		// get the start node
		String startNodeIdStr = String.valueOf(ge.findGraphTaxNodeByUID(ottId).getId());
		args[1] = startNodeIdStr;
		
		//read the relationships into a hashset of longs to be mapped
		HashMap<Long,Integer> rellist = new HashMap<Long,Integer>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(args[2]));
			String str;
			while ((str = br.readLine()) != null) {
				if (str.trim().length() > 0){
					Long lv = Long.valueOf(str.trim());
					if (rellist.containsKey(lv)){
						rellist.put(lv, rellist.get(lv)+1);
					}else{
						rellist.put(lv, 1);
					}
				}
			}
			br.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//extract draft tree for node ids
		Long startNodeId = Long.valueOf(args[1]);
		String outFileName = args[3];
		
		// find the start node
		Node firstNode = ge.graphDb.getNodeById(startNodeId);
//		if (firstNode == null) {
//			throw new opentree.exceptions.OttIdNotFoundException(ottId);
//		}
		
		JadeTree synthTree = null;
		//TODO: need to add the bit about reading the file
		synthTree = ge.extractDraftTreeMap(firstNode, GraphBase.DRAFTTREENAME,rellist);

		if (synthTree == null) {
			return -1;
		}
		
		PrintWriter outFile = null;
		try {
			outFile = new PrintWriter(new FileWriter(outFileName));
			//outFile.write(synthTree.getRoot().getNewick(false) + ";\n");
			outFile.write(figtreetop);
			outFile.write(getCustomNewick(synthTree.getRoot(),"relmap",false)+";\nend;\n");
			outFile.write(figtreetail);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outFile.close();
			ge.shutdownDB();
		}
		return 0;
	}
	
	private String getCustomNewick(JadeNode innode, String objectName, boolean bl) {
		StringBuffer ret = new StringBuffer("");
		for (int i = 0; i < innode.getChildCount(); i++) {
			if (i == 0) {
				ret.append("(");
			}
			ret.append(getCustomNewick(innode.getChild(i),objectName,bl));
			if (bl) {
			    double branchLength = innode.getChild(i).getBL();
			    if (branchLength == 0)
			        branchLength = 0.0000001;

			    ret.append(":".concat(String.valueOf(branchLength)));
			}
			if (i == innode.getChildCount()-1) {
				ret.append(")");
			} else {
				ret.append(",");
			}
		}
		if (innode.getName() != null) {
//			ret.append(GeneralUtils.cleanName(this.name));
			//This isn't working so I am just putting this in
            //ret.append(GeneralUtils.scrubName(this.name));
            ret.append(innode.getName().replaceAll(innode.offendingChars,"_"));
		}
		if(innode.getObject(objectName) != null){
			ret.append("[&"+objectName+"="+String.valueOf((Integer)innode.getObject(objectName))+".0]");
		}
		return ret.toString();
	}
	public static final String figtreetop = "#NEXUS\nbegin trees;\n\ttree support = [&R] ";
	public static final String figtreetail = "begin figtree;\nset appearance.backgroundColorAttribute=\"Default\";\nset appearance.backgroundColour=#-1;\nset appearance.branchColorAttribute=\"relmap\";\nset appearance.branchLineWidth=1.0;\nset appearance.branchMinLineWidth=0.0;\nset appearance.branchWidthAttribute=\"Fixed\";\nset appearance.foregroundColour=#-16777216;\nset appearance.selectionColour=#-2144520576;\nset branchLabels.colorAttribute=\"User selection\";\nset branchLabels.displayAttribute=\"label\";\nset branchLabels.fontName=\"Arial\";\nset branchLabels.fontSize=8;\nset branchLabels.fontStyle=0;\nset branchLabels.isShown=true;\nset branchLabels.significantDigits=4;\nset layout.expansion=0;\nset layout.layoutType=\"RECTILINEAR\";\nset layout.zoom=0;\nset legend.attribute=\"label\";\nset legend.fontSize=10.0;\nset legend.isShown=false;\nset legend.significantDigits=4;\nset nodeBars.barWidth=4.0;\nset nodeBars.displayAttribute=null;\nset nodeBars.isShown=false;\nset nodeLabels.colorAttribute=\"User selection\";\nset nodeLabels.displayAttribute=\"Node ages\";\nset nodeLabels.fontName=\"sansserif\";\nset nodeLabels.fontSize=9;\nset nodeLabels.fontStyle=0;\nset nodeLabels.isShown=false;\nset nodeLabels.significantDigits=4;\nset nodeShape.colourAttribute=\"User selection\";\nset nodeShape.isShown=false;\nset nodeShape.minSize=0.0;\nset nodeShape.scaleType=Area;\nset nodeShape.shapeType=Circle;\nset nodeShape.size=25.0;\nset nodeShape.sizeAttribute=\"label\";\nset polarLayout.alignTipLabels=false;\nset polarLayout.angularRange=0;\nset polarLayout.rootAngle=0;\nset polarLayout.rootLength=100;\nset polarLayout.showRoot=true;\nset radialLayout.spread=0.0;\nset rectilinearLayout.alignTipLabels=false;\nset rectilinearLayout.curvature=0;\nset rectilinearLayout.rootLength=100;\nset scale.offsetAge=0.0;\nset scale.rootAge=1.0;\nset scale.scaleFactor=1.0;\nset scale.scaleRoot=false;\nset scaleAxis.automaticScale=true;\nset scaleAxis.fontSize=8.0;\nset scaleAxis.isShown=false;\nset scaleAxis.lineWidth=1.0;\nset scaleAxis.majorTicks=1.0;\nset scaleAxis.origin=0.0;\nset scaleAxis.reverseAxis=false;\nset scaleAxis.showGrid=true;\nset scaleBar.automaticScale=true;\nset scaleBar.fontSize=10.0;\nset scaleBar.isShown=false;\nset scaleBar.lineWidth=1.0;\nset scaleBar.scaleRange=2.0;\nset tipLabels.colorAttribute=\"User selection\";\nset tipLabels.displayAttribute=\"Names\";\nset tipLabels.fontName=\"Arial\";\nset tipLabels.fontSize=9.;\nset tipLabels.fontStyle=0;\nset tipLabels.isShown=false;\nset tipLabels.significantDigits=4;\nset trees.order=false;\nset trees.orderType=\"decreasing\";\nset trees.rooting=false;\nset trees.rootingType=\"User Selection\";\nset trees.transform=false;\nset trees.transformType=\"cladogram\";\nend;\n";
	
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftTreeForNodeId(String [] args) throws MultipleHitsException, TaxonNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be rootottId outFileName graphdbfolder");
			return 1;
		}
		Long startNodeId = Long.valueOf(args[1]);
		String outFileName = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);
		
		// find the start node
		Node firstNode = ge.graphDb.getNodeById(startNodeId);
//		if (firstNode == null) {
//			throw new opentree.exceptions.OttIdNotFoundException(ottId);
//		}
		
		JadeTree synthTree = null;
		synthTree = ge.extractDraftTree(firstNode, GraphBase.DRAFTTREENAME);

		if (synthTree == null) {
			return -1;
		}
		
		PrintWriter outFile = null;
		try {
			outFile = new PrintWriter(new FileWriter(outFileName));
			outFile.write(synthTree.getRoot().getNewick(false) + ";\n");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outFile.close();
			ge.shutdownDB();
		}
		
		return 0;
	}
	
	
	public int extractDraftSubTreeForNodeIDs(String [] args) throws MultipleHitsException {
		if (args.length != 4) {
			System.out.println("arguments should be nodeId1,nodeId2,... outFileName graphdbfolder");
			return 1;
		}

//		System.out.println(args[1]);
		String[] nodeIds = args[1].trim().split("\\,");
		String outFileName = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);

		ArrayList<Node> tipNodes = new ArrayList<Node>();
		for (String nodeId : nodeIds) {
//			System.out.println(nodeId);
			Node tip = ge.graphDb.getNodeById(Long.valueOf(nodeId));
			if (tip != null) {
//				System.out.println("id = " + tip.getId());
				tipNodes.add(tip);
			}
		}
		
		JadeNode synthTreeRootNode = ge.extractDraftSubtreeForTipNodes(tipNodes);

		PrintWriter outFile = null;
		try {
			outFile = new PrintWriter(new FileWriter(outFileName));
			outFile.write(synthTreeRootNode.getNewick(true) + ";\n");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outFile.close();
			ge.shutdownDB();
		}
		
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftSubTreeForOttIDs(String [] args) throws MultipleHitsException, TaxonNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be tipOTTid1,tipOTTid2,... outFileName graphdbfolder");
			return 1;
		}

//		System.out.println(args[1]);
		String[] OTTids = args[1].trim().split("\\,");
		String outFileName = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);

		ArrayList<Node> tipNodes = new ArrayList<Node>();
		for (String OTTid : OTTids) {
//			System.out.println(OTTid);
			Node tip = ge.findGraphTaxNodeByUID(OTTid);
			if (tip != null) {
//				System.out.println("id = " + tip.getId());
				tipNodes.add(tip);
			}
		}
		
		JadeNode synthTreeRootNode = ge.extractDraftSubtreeForTipNodes(tipNodes);

		PrintWriter outFile = null;
		try {
			outFile = new PrintWriter(new FileWriter(outFileName));
			outFile.write(synthTreeRootNode.getNewick(true) + ";\n");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outFile.close();
			ge.shutdownDB();
		}
		
		return 0;
	}
	
	
	public int extractTaxonomySubTreeForOttIDs(String [] args) throws MultipleHitsException, TaxonNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be tipOTTid1,tipOTTid2,... outFileName graphdbfolder");
			return 1;
		}

//		System.out.println(args[1]);
		String[] OTTids = args[1].trim().split("\\,");
		String outFileName = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);

		ArrayList<Node> tipNodes = new ArrayList<Node>();
		for (String OTTid : OTTids) {
//			System.out.println(OTTid);
			Node tip = ge.findGraphTaxNodeByUID(OTTid);
			if (tip != null) {
//				System.out.println("id = " + tip.getId());
				tipNodes.add(tip);
			}
		}
		
		JadeNode synthTreeRootNode = ge.extractTaxonomySubtreeForTipNodes(tipNodes);

		PrintWriter outFile = null;
		try {
			outFile = new PrintWriter(new FileWriter(outFileName));
			outFile.write(synthTreeRootNode.getNewick(false) + ";\n");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outFile.close();
			ge.shutdownDB();
		}
		
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftTreeForOttidJSON(String [] args) throws MultipleHitsException, TaxonNotFoundException{
		// open the graph
		String graphname = args[3];
		String outFileName = args[2];
		String ottId = args[1];
		GraphExplorer ge = new GraphExplorer(graphname);
		Node firstNode = ge.findGraphTaxNodeByUID(ottId);
		JadeTree synthTree = null;
		synthTree = ge.extractDraftTree(firstNode, GraphBase.DRAFTTREENAME);
		if (synthTree == null) {
			return -1;
		}

		PrintWriter outFile = null;
		try {
			outFile = new PrintWriter(new FileWriter(outFileName));
			outFile.write(synthTree.getRoot().getJSON(false));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outFile.close();
			ge.shutdownDB();
		}
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command
	public int deleteDraftTree(String [] args) {
		GraphImporter gi = null;
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return 1;
		}
		String graphname = args[1];
		gi = new GraphImporter(graphname);
		try {
			gi.deleteSynthesisTree();
		} finally {
			gi.shutdownDB();
		}
		return 0;
	}
	
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int makePrunedBipartsTestFiles(String [] args) {
		if (args.length != 4) {
			System.out.println("arguments should be randomseed ntips graphdbfolder");
			return 1;
		}

		int seed = Integer.valueOf(args[1]);
		int nTaxa = Integer.valueOf(args[2]);
		String exportPath = args[3];

		ArrayList<String> names = new ArrayList<String>();
		
		System.out.println("enumerating tips");		
		for (int i = 0; i < nTaxa; i++) {
			System.out.println(i);
			names.add(String.valueOf(i));
		}

		System.out.println("randomizing a fully resolved tree with " + nTaxa + " tips");
		JadeNode randomTree = TreeUtils.makeRandomTree(names, seed);

		PrintWriter outFile;

		String completeTreePath = (exportPath + "/" + "random.tre").replaceAll("/+", "/");
		System.out.println("Writing the random tree to " + completeTreePath);
		try {
			outFile = new PrintWriter(new FileWriter(completeTreePath));
			boolean reportBranchLength = false;
			outFile.write(randomTree.getNewick(reportBranchLength) + ";\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
			return 1;
		}

		System.out.println("Finding all bipartitions ");
		Iterable<JadeNode> randomTreeBipartTrees = TreeUtils.extractBipartitions(randomTree);

		String bipartTreesPath = (exportPath + "/" + "random.biparts.pruned.tre").replaceAll("/+", "/");
		System.out.println("Writing bipartition trees to " + bipartTreesPath);
		try {
			outFile = new PrintWriter(new FileWriter(bipartTreesPath));
			boolean reportBranchLength = false;
			
			for (JadeNode bipart : randomTreeBipartTrees) {
				// here is where the pruning happens
//				System.out.println(bipart.getNewick(reportBranchLength).concat(";\n"));
				outFile.write(bipart.getNewick(reportBranchLength).concat(";\n"));
			}
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
			return 1;
		}
		return 0;
	}
	
	
	// check if tree is already in the graph
	// @returns true is new to graph, false if already present
	public static boolean checkTreeNewToGraph (String sourcename, GraphDatabaseAgent graphDb) {
		boolean newTree = true;
		// test to see if the tree is already in graph
		Index<Node> sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes","type", "exact", "to_lower_case", "true");
		IndexHits<Node > hits = sourceMetaIndex.get("source", sourcename);
		if (hits.size() > 0) {
			return false;
		}
		return newTree;
	}
	
	
	public static int loadPhylografterStudy(GraphDatabaseAgent graphDb, 
											BufferedReader nexsonContentBR, String treeid, String SHA,
											MessageLogger messageLogger,
											boolean onlyTestTheInput) 
											throws Exception {
		List<JadeTree> rawTreeList = null;
		ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
		rawTreeList = NexsonReader.readNexson(nexsonContentBR, true, messageLogger);
		if (treeid != null) {
			System.out.println("loading a specific tree: " + treeid);
		}
		int count = 0;
		for (JadeTree j : rawTreeList) {
			if (j == null) {
				messageLogger.indentMessage(1, "Skipping null tree...");
			} else {
				// kick out early if tree is already in graph
				String sourcename = "";
				if (j.getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
					sourcename = (String)j.getObject("ot:studyId");
				}
				String treeJId = (String)j.getObject("id");
				if (treeJId != null) { // use treeid (if present) as sourcename
					sourcename += "_" + treeJId;
				}
				if (treeid != null) {
					if (treeJId.equals(treeid) == false) {
						System.out.println("skipping tree: " + treeJId);
						continue;
					}
				}
				if (!checkTreeNewToGraph(sourcename, graphDb)) {
					messageLogger.indentMessageStrStr(1, "Source tree already added:", "tree id", treeJId, "source", sourcename);
				} else {
					System.err.println("\ttree " + count + ": " + j.getExternalNodeCount());
					count += 1;
					jt.add(j);
				}
			}
		}
		
		if (jt.size() == 0) {
			nexsonContentBR.close();
			System.out.println("\nNo new trees to add to graph.\n");
			return 0;
		}
		
		try {
			boolean good = PhylografterConnector.fixNamesFromTreesNOTNRS(jt, graphDb, true, messageLogger);
			System.out.println("done fixing name");
			if (good == false) {
				System.err.println("failed to get the names from server fixNamesFromTrees 3");
				return -1;
			}
		} catch (IOException ioe) {
			System.err.println("excpeption to get the names from server fixNamesFromTrees 4");
			throw ioe;
		}
		messageLogger.message("Finished with attempts to fix names");
		int treeIndex = 0;
		for (JadeTree j : jt) {
			GraphImporter gi = new GraphImporter(graphDb);
			boolean doubname = false;
			String treeJId = (String)j.getObject("id");
			if (treeid != null) {
				if (treeJId.equals(treeid) == false) {
					System.out.println("skipping tree: " + treeJId);
					continue;
				}
			}
			HashSet<Long> ottols = new HashSet<Long>();
			messageLogger.indentMessageStr(1, "Checking for uniqueness of OTT IDs", "tree id", treeJId);
			for (int m = 0; m < j.getExternalNodeCount(); m++) {
				//System.out.println(j.getExternalNode(m).getName() + " " + j.getExternalNode(m).getObject("ot:ottId"));
				if (j.getExternalNode(m).getObject("ot:ottId") == null) {//use doubname as also 
					messageLogger.indentMessageStr(2, "null OTT ID for node", "name", j.getExternalNode(m).getName());
					doubname = true;
					break;
				}
				Long ottID = (Long)j.getExternalNode(m).getObject("ot:ottId");
				if (ottols.contains(ottID) == true) {
					messageLogger.indentMessageLongStr(2, "duplicate OTT ID for node", "OTT ID", ottID, "name", j.getExternalNode(m).getName());
					doubname = true;
					break;
				} else {
					ottols.add((Long)j.getExternalNode(m).getObject("ot:ottId"));
				}
			}
			//check for any duplicate ottol:id
			if (doubname == true) {
				messageLogger.indentMessageStr(1, "null or duplicate names. Skipping tree", "tree id", treeJId);
			} else {
				gi.setTree(j);
				String sourcename = "";
				if (j.getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
					sourcename = (String)j.getObject("ot:studyId");
				}
				if (treeJId != null) { // use treeid (if present) as sourcename
					sourcename += "_" + treeJId+"_"+SHA;
				}
				if (onlyTestTheInput) {
					messageLogger.indentMessageStr(1, "Checking if tree could be added to graph", "tree id", treeJId);
					getMRPmatrix(j,graphDb);
				} else {
					messageLogger.indentMessageStr(1, "Adding tree to graph", "tree id", treeJId);
					gi.addSetTreeToGraphWIdsSet(sourcename, false, onlyTestTheInput, messageLogger);
				}
			}
			++treeIndex;
		}
		nexsonContentBR.close();
		return 0;
	}

	
	/*
	 * Use this to load trees from nexson into the graph from a file
	 * not from the server
	 * 
	 * changed to loading individual trees
	 * 
	 * arguments are 
	 * database filename treeid (test)
	 */
	public int pg_loading_ind_studies(String [] args) throws Exception {
		boolean test = false;
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
		PrintStream jsonOutputPrintStream = null;
		if (args.length != 5 && args.length != 6) {
			graphDb.shutdownDb();
			System.out.println("the argument has to be graphdb filename treeid SHA (test)");
			System.out.println("\tif you have test at the end, it will not be entered into the database, but everything will be performed");
			return 1;
		}
		if (args.length == 5) {
			if (!args[3].matches("\\d+")) { // check for missing treeid
				graphDb.shutdownDb();
				System.out.println("Missing treeid argument");
				System.out.println("the argument has to be graphdb filename treeid SHA (test)");
				System.out.println("\tif you have test at the end, it will not be entered into the database, but everything will be performed");
				return 1;
			}
		}
		if (args.length == 6) {
			System.err.println("not entering into the database, just testing");
			test = true;
			if (args[3].endsWith(".json")) {
				try {
					jsonOutputPrintStream = new PrintStream(new FileOutputStream(args[3]));
				} catch (Exception x) {
					System.err.println("Could not open the file \"" + args[3] + "\" Exiting...");
					return -1;
				}
			}
		}
		String filen = args[2];
		File file = new File(filen);
		System.err.println("file " + file);
		String treeid = args[3];
		String SHA = args[4];// git commit SHA
		treeid.concat(SHA);
		BufferedReader br= null;
		MessageLogger messageLogger;
		if (jsonOutputPrintStream == null) {
			messageLogger = new MessageLogger("pgloadind", " ");
		} else {
			messageLogger = new JSONMessageLogger("pgloadind");
			messageLogger.setPrintStream(jsonOutputPrintStream);
		}
		try {
			br = new BufferedReader(new FileReader(file));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			graphDb.shutdownDb();
			return -1;
		}
		int rc = 0;
		try {
			//set treeid == null if you want to load all of them
			if (loadPhylografterStudy(graphDb, br, treeid, SHA, messageLogger, test) != 0) {
				rc = -1;
			}
		} catch (IOException e) {
			rc = -1;
		} catch (java.lang.NullPointerException e) {
			rc = -1;
		} catch (TaxonNotFoundException e) {
			rc = -1;
		} catch (TreeIngestException e) {
			rc = -1;
		}
		messageLogger.close();
		try {
			br.close();
		} catch (IOException e) {
			rc = -1;
		}
		graphDb.shutdownDb();
		return rc;
	}
	
	
	public int pg_loading_ind_studies_newick(String [] args) throws Exception {
		boolean test = false;
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
		PrintStream jsonOutputPrintStream = null;
		if (args.length != 3 && args.length != 4) {
			graphDb.shutdownDb();
			System.out.println("the argument has to be graphdb filename (test)");
			System.out.println("\tif you have test at the end, it will not be entered into the database, but everything will be performed");
			return 1;
		}
		if (args.length == 4) {
			System.err.println("not entering into the database, just testing");
			test = true;
		}
		String filen = args[2];
		File file = new File(filen);
		System.err.println("file " + file);
		BufferedReader br= null;
		MessageLogger messageLogger = new MessageLogger("pgloadindnewick", " ");
		String treeString = "";
		try {
			br = new BufferedReader(new FileReader(file));
			treeString  = br.readLine();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			graphDb.shutdownDb();
			return -1;
		}
		int rc = 0;
		try {
			TreeReader tr = new TreeReader();
			JadeTree tree = tr.readTree(treeString);
			//set treeid == null if you want to load all of them
			if (loadNewickStudy(graphDb, tree, filen,messageLogger, test) != 0) {
				rc = -1;
			}
		} catch (IOException e) {
			rc = -1;
		} catch (java.lang.NullPointerException e) {
			rc = -1;
		} catch (TaxonNotFoundException e) {
			rc = -1;
		} catch (TreeIngestException e) {
			rc = -1;
		}
		messageLogger.close();
		try {
			br.close();
		} catch (IOException e) {
			rc = -1;
		}
		graphDb.shutdownDb();
		return rc;
	}
	
	
	public static int loadNewickStudy(GraphDatabaseAgent graphDb, 
			JadeTree tree, String treeid,
			MessageLogger messageLogger,
			boolean onlyTestTheInput) 
					throws Exception {
		int count = 0;
		String sourcename = null;
		if (tree == null) {
			messageLogger.indentMessage(1, "Skipping null tree...");
			return -1;
		} else {
			// kick out early if tree is already in graph
			sourcename = treeid;
			if (!checkTreeNewToGraph(sourcename, graphDb)) {
				messageLogger.indentMessageStrStr(1, "Source tree already added:", "", "", "source", sourcename);
			} else {
				System.err.println("\ttree " + count + ": " + tree.getExternalNodeCount());
				count += 1;
			}
		}

		GraphImporter gi = new GraphImporter(graphDb);
		boolean doubname = false;
		String treeJId = sourcename;
		tree.assocObject("id", sourcename);
		HashSet<Long> ottols = new HashSet<Long>();
		messageLogger.indentMessageStr(1, "Checking for uniqueness of OTT IDs", "tree id", treeJId);
		for (int m = 0; m < tree.getExternalNodeCount(); m++) {
			Long ottID = Long.parseLong(tree.getExternalNode(m).getName());
			tree.getExternalNode(m).assocObject("ot:ottId", ottID);
			if (ottols.contains(ottID) == true) {
				messageLogger.indentMessageLongStr(2, "duplicate OTT ID for node", "OTT ID", ottID, "name", tree.getExternalNode(m).getName());
				doubname = true;
				break;
			} else {
				ottols.add((Long)tree.getExternalNode(m).getObject("ot:ottId"));
			}
		}
			//check for any duplicate ottol:id
		if (doubname == true) {
			messageLogger.indentMessageStr(1, "null or duplicate names. Skipping tree", "tree id", treeJId);
		} else {
			gi.setTree(tree);
			if (onlyTestTheInput) {
				messageLogger.indentMessageStr(1, "Checking if tree could be added to graph", "tree id", treeJId);
				getMRPmatrix(tree,graphDb);
			} else {
				messageLogger.indentMessageStr(1, "Adding tree to graph", "tree id", treeJId);
				gi.addSetTreeToGraphWIdsSet(sourcename, false, onlyTestTheInput, messageLogger);
			}
		}
		return 0;
	}
	
	
	/**
	 * arguments are:
	 * database sourcename (test)
	 * @param args
	 * @return
	 * @throws Exception
	 */
	public int pg_delete_ind_study(String [] args) throws Exception {
//		boolean test = false;	// not used
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
//		PrintStream jsonOutputPrintStream = null;	// not used
		if (args.length != 3 && args.length != 4) {
			graphDb.shutdownDb();
			System.out.println("the argument has to be graphdb sourcename (test)");
			System.out.println("\tif you have test at the end, it will not be deleted from the database, but everything will be performed");
			return 1;
		}
		if (args.length == 4) {
			System.err.println("not entering into the database, just testing");
//			test = true;	// not used
		}
		String sourcename = args[2];
		GraphImporter gi = new GraphImporter(graphDb);
		gi.deleteTreeBySource(sourcename);
		gi.shutdownDB();
		return 0;
	}
	
	
	/**
	 * maps the compatible relationships
	 * @param args
	 * @return
	 * @throws Exception
	 */
	public int mapcompat(String [] args) throws Exception {
		boolean test = false;
		String graphname = args[1];
		if (args.length != 3 && args.length != 4) {
			System.out.println("the argument has to be graphdb sourcename (test)");
			System.out.println("\tif you have test at the end, database won't be changed, but everything will be performed");
			return 1;
		}
		if (args.length == 4) {
			System.err.println("not entering into the database, just testing");
			test = true;
		}
		String treeid = args[2];
		GraphExplorer ge = new GraphExplorer(graphname);
		ge.mapcompat(treeid,test);
		ge.shutdownDB();
		return 0;
	}
	
	
	public int nodeInfo(String [] args) {
		if (args.length != 3) {
			System.out.println("arguments should be: node graphdb");
			return 1;
		}
		String node = args[1];
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[2]);
		System.out.println("Getting information about node " + node);
		Long nodel = Long.valueOf(node);
		Node tn = graphDb.getNodeById(nodel);
		System.out.println("properties\n================\n");
		for (String ts:tn.getPropertyKeys()) {
			if (ts.equals("mrca") || ts.equals("outmrca") || ts.equals("nested_mrca")) {
				System.out.print(ts + "\t");
				long [] m = (long[])tn.getProperty(ts);
				if (m.length < 100000) {
					for (int i = 0; i < m.length; i++) {
						System.out.print(m[i] + " ");
					}
					System.out.print("\n");
				}
				System.out.println(m.length);
				TLongHashSet ths = new TLongHashSet(m);
				System.out.println(ths.size());
			} else if (ts.equals("name")) {
				System.out.println(ts + "\t" + (String)tn.getProperty(ts));
			} else {
				System.out.println(ts + "\t" + (String)tn.getProperty(ts));
			}
		}
		graphDb.shutdownDb();
		return 0;
	}
	
	
	/**
	 * Constructs a newick tree file from a passed in taxonomy file
	 * arguments are:
	 * taxonomy_filename output_tree_filename
	 * @param args
	 * @return
	 * @throws Exception
	 */
	// @returns 0 for success, 1 for poorly formed command
	public int convertTaxonomy(String []args) {
		if (args.length != 3) {
			System.out.println("arguments should be: taxonomyfile treefile");
			return 1;
		}
		JadeTree tree = null;
		JadeNode root = null;
		String cellular = "93302"; // default. for full tree.
		Boolean cellularHit = false;
		String taxonomyRoot = "";
		String taxonomyfile = args[1];
		try {
			BufferedReader br = new BufferedReader(new FileReader(taxonomyfile));
			String str;
			int count = 0;
			HashMap<String,JadeNode> id_node_map = new HashMap<String,JadeNode>();
			HashMap<String,ArrayList<String>> id_childs = new HashMap<String,ArrayList<String>>();
			while ((str = br.readLine()) != null) {
				// check the first line to see if it the file has a header that we should skip
				if (count == 0) {
					if (str.startsWith("uid")) { // file contains a header. skip line
						System.out.println("Skipping taxonomy header line: " + str);
						continue;
					}
				}
				if (!str.trim().equals("")) {
					// collect sets of lines until we reach the transaction frequency
					count += 1;
					StringTokenizer st = new StringTokenizer(str,"|");
					String tid = null;
					String pid = null;
					String name = null;
					String rank = null;
					String srce = null;
					String uniqname = null;
					String flag = null; 
					tid = st.nextToken().trim();
					pid = st.nextToken().trim();
					name = st.nextToken().trim();
					rank = st.nextToken().trim();
					srce = st.nextToken().trim();
					uniqname = st.nextToken().trim();
					flag = st.nextToken().trim(); //for dubious
					if (pid.trim().equals("")) {
						taxonomyRoot = tid;
					}
					if (tid == cellular) {
						cellularHit = true;
					}
					if (id_childs.containsKey(pid) == false) {
						id_childs.put(pid, new ArrayList<String>());
					}
					id_childs.get(pid).add(tid);
					JadeNode tnode = new JadeNode();
//					tnode.setName(GeneralUtils.cleanName(name).concat("_ott").concat(tid));
					tnode.setName(GeneralUtils.scrubName(name).concat("_ott").concat(tid));
					tnode.assocObject("id", tid);
					id_node_map.put(tid, tnode);
				}
			}
			count = 0;
			// construct tree
			Stack <JadeNode> nodes = new Stack<JadeNode>();
			if (cellularHit) {
				taxonomyRoot = cellular;
			}
			System.out.println("Setting root to: " + taxonomyRoot);
			root = id_node_map.get(taxonomyRoot);
			
			nodes.add(root);
			while (nodes.empty() == false) {
				JadeNode tnode = nodes.pop();
				count += 1;
				ArrayList<String> childs = id_childs.get((String)tnode.getObject("id"));
				for (int i = 0; i < childs.size(); i++) {
					JadeNode ttnode = id_node_map.get(childs.get(i));
					tnode.addChild(ttnode);
					if (id_childs.containsKey(childs.get(i))) {
						nodes.add(ttnode);
					}
				}
				if (count%10000 == 0) {
					System.out.println(count);
				}
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		tree = new JadeTree(root);
		tree.processRoot();
		String outfile = args[2];
		FileWriter fw;
		try {
			fw = new FileWriter(outfile);
			fw.write(tree.getRoot().getNewick(false) + ";");
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
	
	
	/*
	 * Read in nexson-formatted tree, export as newick
	 */
	/// @returns 0 for success, 1 for poorly formed command
	public int nexson2newick(String [] args) throws DataFormatException, TaxonNotFoundException, TreeIngestException {
		if (args.length > 3 | args.length < 2) {
			System.out.println("arguments should be: filename.nexson [outname.newick]");
			return 1;
		}
		
		String filename = args[1];
		String outFilename = "";
		
		if (args.length == 3) {
			outFilename = args[2];
		} else {
			outFilename = filename + ".tre";
		}
		
		int treeCounter = 0;
		ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
		MessageLogger messageLogger = new MessageLogger("nexson2newick:");
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			if (GeneralUtils.divineTreeFormat(br).compareTo("nexson") != 0) {
				System.out.println("tree does not appear to be in nexson format");
				return 1;
			} else { // nexson
				System.out.println("Reading nexson file...");
				for (JadeTree tree : NexsonReader.readNexson(filename, false, messageLogger)) {
					if (tree == null) {
						messageLogger.indentMessage(1, "Skipping null tree.");
					} else {
						jt.add(tree);
						treeCounter++;
					}
				}
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		System.out.println(treeCounter + " trees read.");
		
		PrintWriter outFile = null;
		try {
			outFile = new PrintWriter(new FileWriter(outFilename));
			
			for (int i = 0; i < jt.size(); i++) {
				outFile.write(jt.get(i).getRoot().getNewick(false) + ";\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outFile.close();
		}
		
		System.out.println("Sucessfully wrote " + treeCounter + " newick trees to file '" + outFilename + "'");		
		return 0;
	}
	
	//Runs tree comparison analyses
	public int treeCompare(String [] args){
		//1 = graphdb, 2 = nexson, 3 = treeid
		if (args.length != 4){
			return 1;
		}
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
		TreeComparator tc = new TreeComparator(false, args[2], args[3], graphDb);
		tc.processNexson();
		try {
			tc.compareTree();
		} catch (TaxonNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		graphDb.shutdownDb();
		return 0;
	}
	
	//Runs tree comparison analyses on taxonomy tree
	public int taxCompare(String [] args){
			//1 = graphdb, 2 = nexson, 3 = treeid
			if (args.length != 4){
				return 1;
			}
			GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
			TreeComparator tc = new TreeComparator(true,args[2],args[3],graphDb);
			tc.processNexson();
			try {
				tc.compareTree();
			} catch (TaxonNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			graphDb.shutdownDb();
			return 0;
		}
	
	public static void printShortHelp() {
		System.out.println("======================Treemachine======================");
		System.out.println("usage: java -jar locationOftreemachine.jar command options");
		System.out.println("For a more comprehensive help message, type java -jar locationOftreemachine.jar help");
		System.out.println("");
		System.out.println("Here are some common commands with descriptions.");
		System.out.println("INPUT SET OF TREES (bootstrap, posterior probability)");
		//System.out.println("	\033[1mjusttrees\033[0m <filename> <taxacompletelyoverlap[T|F]> <rootnodename> <graphdbfolder>");
		System.out.println("	\033[1mloadtrees\033[0m <filename> <graphdbfolder> <taxonomyloadedalread[T|F]>");
		System.out.println("");
		System.out.println("INPUT TAXONOMY AND TREES");
		System.out.println("  Initializes the graph with a tax list in the format");
		System.out.println("	\033[1minittax\033[0m <filename> (optional:synonymfilename) <taxonomyversion> <graphdbfolder>");
		System.out.println("  Add a newick tree to the graph");
		System.out.println("	\033[1maddnewick\033[0m <filename> <taxacompletelyoverlap[T|F]> <focalgroup> <sourcename> <graphdbfolder>");
		System.out.println("  Export a source tree from that graph with taxonomy mapped");
		System.out.println("	\033[1msourceexplorer\033[0m <sourcename> <graphdbfolder>");
		System.out.println("");
		System.out.println("WORK WITH GRAPH");
		System.out.println("	\033[1mexporttodot\033[0m <name> <outfile> <usetaxonomy[T|F]>  <graphdbfolder>");
		System.out.println("  Export the graph in graphml format with statistics embedded");
		System.out.println("	\033[1mgraphml\033[0m <name> <outfile> <usetaxonomy[T|F]>  <graphdbfolder>");
		System.out.println("  Synthesize the graph");
		System.out.println("	\033[1mfulltree\033[0m <name> <graphdbfolder> <usetaxonomy[T|F]> <usebranchandbound[T|F]> sinklostchildren[T|F]");
		System.out.println("	\033[1mfulltree_sources\033[0m <name> <preferred sources csv> <graphdbfolder> usetaxonomy[T|F] sinklostchildren[T|F]");
		System.out.println("\n");
	}
	
	
	public static void printHelp() {
		System.out.println("==========================");
		System.out.println("usage: treemachine command options");
		System.out.println("");
		System.out.println("commands");
		System.out.println("---initialize---");
		System.out.println("  inittax <filename> (<synonymfilename>) <taxonomyversion> <graphdbfolder> (initialize the tax graph with a tax list)");
		System.out.println("  loadott <ott_distribution_directory> (initialize the tax graph from the opentree-taxonomy (ott))\n");

		System.out.println("---graph input---");
		System.out.println("  addnewick <filename> <taxacompletelyoverlap[T|F]> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph)");
		System.out.println("  addnewickTNRS <filename> <taxacompletelyoverlap[T|F]> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph with TNRS)");
		System.out.println("  addnexson <filename> <taxacompletelyoverlap[T|F]> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph)");
		System.out.println("  pgloadind <graphdbfolder> <filepath> <treeid> [test] (add trees from the nexson file \"filepath\" into the db. If fourth arg is found the tree is just tested, not added).\n");
		System.out.println("  mapcompat <graphdbfolder> <treeid> (maps the compatible nodes)");
		
		System.out.println("---graph output---");
		System.out.println("  jsgol <name> <graphdbfolder> (construct a json file from a particular node)");
		System.out.println("  fulltree <name> <graphdbfolder> <usetaxonomy[T|F]> <usebranchandbound[T|F]> sinklostchildren[T|F] (construct a newick file from a particular node)");
		System.out.println("  fulltree_sources <name> <preferred sources csv> <graphdbfolder> usetaxonomy[T|F] sinklostchildren[T|F] (construct a newick file from a particular node, break ties preferring sources)");
		System.out.println("  fulltreelist <filename list of taxa> <preferred sources csv> <graphdbfolder> (construct a newick tree for a group of species)");
		System.out.println("  mrpdump <name> <outfile> <graphdbfolder> (dump the mrp matrix for a subgraph without the taxonomy branches)");
		System.out.println("  exporttodot <name> <outfile> <usetaxonomy[T|F]> <graphdbfolder> (construct a dot file of the region starting from the name)");
		System.out.println("  graphml <name> <outfile> <usetaxonomy[T|F]> <graphdbfolder> (construct a graphml file of the region starting from the name)");
		System.out.println("  csvdump <name> <outfile> <graphdbfolder> (dump the graph in format node,parent,nodename,parentname,source,brlen)");
		System.out.println("  taxtree <name> <outfile> <graphdbfolder> (dump the taxonomy as a tree with UIDs as the tips)\n");
		
		System.out.println("---graph exploration---");
		System.out.println("(This is for testing the graph with a set of trees from a file)");
		System.out.println("  justtrees <filename> <taxacompletelyoverlap[T|F]> <rootnodename> <graphdbfolder> (load trees into a new graph)");
		System.out.println("  sourceexplorer <sourcename> <graphdbfolder> (explore the different source files)");
		System.out.println("  sourcepruner <sourcename> <nodeid> <maxDepth> <graphdbfolder> (explore the different source files)");
		System.out.println("  listsources <graphdbfolder> (list the names of the sources loaded in the graph)");
		System.out.println("  gettaxonomy <graphdbfolder> (return the name of the taxonomy used to initialize the graph)");
		System.out.println("  biparts <graphdbfolder> (look at bipartition information for a graph)");
		System.out.println("  mapsupport <file> <outfile> <graphdbfolder> (map bipartition information from graph to tree)");
		System.out.println("  getlicanames <nodeid> <graphdbfolder> (print the list of names that are associated with a lica if there are any names)");
		System.out.println("  nodestatus <ottId> <graphdbfolder> (give summary info about node, including num. descendants, supporting trees, etc.)");
		System.out.println("  gettaxonomy <graphdbfolder> (report which taxonomy (e.g. version of OTT) was used to initialize the graph)\n");

		System.out.println("---tree functions---");
		System.out.println("(This is temporary and for doing some functions on trees (output or potential input))");
		System.out.println("  counttips <filename> (count the number of nodes and leaves in a newick)");
		System.out.println("  diversity <filename> (for each node it will print the immediate descendents and their diversity)");
		System.out.println("  labeltips <filename.tre> <filename>");
		System.out.println("  labeltax <filename.tre> <graphdbfolder>");
		System.out.println("  checktax <filename.tre> <graphdbfolder>");
		System.out.println("  nexson2newick <filename.nexson> (<filename.newick>) (construct newick tree file from a nexson file)");
		System.out.println("  convertfigtree <filename.tre> <outfile.tre>");
		System.out.println("  nexson2mrp <filename.nexson>");
		System.out.println("  converttaxonomy <taxonomy_filename> <outfile.tre> (construct a newick tree from a TSV taxonomy file)\n");
		
		System.out.println("---synthesis functions---");
		System.out.println("  synthesizedrafttreelist_ottid <rootNodeOttId> <list> <graphdbfolder> (perform default synthesis from the root node using source-preferenc tie breaking and store the synthesized rels with a list (csv))");
		System.out.println("  synthesizedrafttreelist_nodeid <rootNodeId> <list> <graphdbfolder> (perform default synthesis from the root node using source-preferenc tie breaking and store the synthesized rels with a list (csv))");
		System.out.println("  extractdrafttree_ottid <rootNodeOttId> <outfilename> <graphdbfolder> (extract the default synthesized tree (if any) stored below the root node)");
		System.out.println("  extractdrafttree_nodeid <rootNodeId> <outfilename> <graphdbfolder> (extract the default synthesized tree (if any) stored below the root node)");
		System.out.println("  extractdraftsubtreeforottids <tipOttId1>,<tipOttId2>,... <outfilename> <graphdbfolder> (extract the default synthesized tree (if any) stored below the root node)");
		System.out.println("  extracttaxonomysubtreeforottids <tipOttId1>,<tipOttId2>,... <outfilename> <graphdbfolder> (extract the default synthesized tree (if any) stored below the root node)");
		System.out.println("  extractdraftsubtreefornodeids <tipNodeId1>,<tipNodeId2>,... <outfilename> <graphdbfolder> (extract the default synthesized tree (if any) stored below the root node)");
		System.out.println("  synthesisinfo (ottolID) <graphdbfolder> (summarize current synthetic tree)");
		System.out.println("  deleteDraftTree <graphdbfolder> (deletes the synthesized tree (if any) from the graph)\n");
				
		System.out.println("---temporary functions---");
		System.out.println("  addtaxonomymetadatanodetoindex <metadatanodeid> <graphdbfolder> (add the metadata node attached to 'life' to the sourceMetaNodes index for the 'taxonomy' source)\n");

		System.out.println("---testing---");
		System.out.println("  makeprunedbipartstestfiles <randomseed> <ntaxa> <path> (export newick files containing (1) a randomized tree and (2) topologies for each of its bipartitions, pruned to a minimal subset of taxa)\n");
		
		System.out.println("---server functions---");
		System.out.println("  getupdatedlist\n");
		
		System.out.println("---general functions---");
		System.out.println("  help (print this help)\n");
	}
	
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		//PropertyConfigurator.configure(System.getProperties());
		//System.err.println("treemachine version alpha.alpha.prealpha");
		if (args.length < 1) {
			printShortHelp();
			System.exit(1);
		}
		String command = args[0];
		if (command.compareTo("help") == 0 || args[0].equals("-h") || args[0].equals("--help")) {
			printHelp();
			System.exit(0);
		}
		System.err.println("things will happen here");
		int cmdReturnCode = 0;
		try {
			MainRunner mr = new MainRunner();
	//		if (args.length < 2) {
	//			System.err.println("ERROR: not the right arguments");
	//			printHelp();
	//			System.exit(cmdReturnCode);
	//		} else
			if (command.compareTo("inittax") == 0) {
				cmdReturnCode = mr.taxonomyLoadParser(args);
			} else if (command.compareTo("addnewick") == 0
					|| command.compareTo("addnewickTNRS") == 0
							||command.compareTo("addnexson") == 0) {
				cmdReturnCode = mr.graphImporterParser(args);
			} else if (command.compareTo("argusjson") == 0) {
				cmdReturnCode = mr.graphArgusJSON(args);
			} else if (command.compareTo("jsgol") == 0
					|| command.compareTo("fulltree") == 0
					|| command.compareTo("fulltree_sources") == 0) {
				cmdReturnCode = mr.graphExplorerParser(args);
			} else if (command.compareTo("mrpdump") == 0) {
				cmdReturnCode = mr.mrpDumpParser(args);
			} else if (command.compareTo("fulltreelist") == 0) {
				cmdReturnCode = mr.graphListPruner(args);
			} else if (command.compareTo("justtrees") == 0) {
				cmdReturnCode = mr.justTreeAnalysis(args);
			} else if (command.compareTo("sourceexplorer") == 0
					|| command.equalsIgnoreCase("sourcepruner")
					|| command.equalsIgnoreCase("sourceexplorer_inf_mono")) {
				cmdReturnCode = mr.sourceTreeExplorer(args);
			} else if (command.compareTo("listsources") == 0
					|| command.compareTo("getsourcetreeids") == 0) {
				cmdReturnCode = mr.listSources(args);
			} else if (command.compareTo("exporttodot") == 0) {
				cmdReturnCode = mr.dotGraphExporter(args);
			} else if (command.compareTo("graphml") == 0) {
				cmdReturnCode = mr.graphExporter(args);
			} else if (command.compareTo("graphml_ottolid") == 0) {
				cmdReturnCode = mr.graphExporter_ottolid(args);
			} else if (command.compareTo("biparts") == 0) {
				cmdReturnCode = mr.graphExplorerBiparts(args);
			} else if (command.compareTo("mapsupport") == 0) {
				cmdReturnCode = mr.graphExplorerMapSupport(args);
			} else if (command.compareTo("reprocess") == 0) {
				cmdReturnCode = mr.graphReloadTrees(args);
			} else if (command.compareTo("deletetrees") == 0) {
				cmdReturnCode = mr.graphDeleteTrees(args);
			} else if (command.compareTo("csvdump") == 0) {
				cmdReturnCode = mr.csvDumpParser(args);
			} else if (command.compareTo("getlicanames") == 0) {
				cmdReturnCode = mr.getLicaNames(args);
			} else if (command.compareTo("counttips") == 0
				   || command.compareTo("diversity") == 0
				   || command.compareTo("labeltips") == 0 
				   || command.compareTo("labeltipsottol") == 0
				   || command.compareTo("convertfigtree") == 0
				   || command.compareTo("convertfigtree2") == 0) {
				cmdReturnCode = mr.treeUtils(args);
			} else if (command.compareTo("converttaxonomy") == 0) {
				cmdReturnCode = mr.convertTaxonomy(args);
			} else if (command.compareTo("labeltax") == 0
					|| command.compareTo("checktax") == 0 
					|| command.compareTo("checktaxhier") == 0) {
				cmdReturnCode = mr.treeUtilsDB(args);
			} else if (command.compareTo("synthesizedrafttree") == 0) {
				cmdReturnCode = mr.synthesizeDraftTree(args);
			} else if (command.compareTo("synthesizedrafttreelist_ottid") == 0) {
				cmdReturnCode = mr.synthesizeDraftTreeWithListForTaxUID(args);
			} else if (command.compareTo("synthesizedrafttreelist_nodeid") == 0) {
				cmdReturnCode = mr.synthesizeDraftTreeWithListForNodeId(args);
			} else if (command.compareTo("synthesisinfo") == 0) {
				cmdReturnCode = mr.getSynthesisInfo(args);
			} else if (command.compareTo("extractdrafttree_ottid") == 0) {
				cmdReturnCode = mr.extractDraftTreeForOttId(args);
			} else if (command.compareTo("extractdrafttree_ottid_relidmap") == 0) {
				cmdReturnCode = mr.extractDraftTreeForOttIdRelIDMap(args);
			}else if (command.compareTo("deleteDraftTree") == 0) {
				cmdReturnCode = mr.deleteDraftTree(args);
			} else if (command.compareTo("extractdrafttree_ottid_JSON") == 0) {
				cmdReturnCode = mr.extractDraftTreeForOttidJSON(args);
			} else if (command.compareTo("extractdrafttree_nodeid") == 0) {
				cmdReturnCode = mr.extractDraftTreeForNodeId(args);
			} else if (command.compareTo("extractdraftsubtreeforottids") == 0) {
				cmdReturnCode = mr.extractDraftSubTreeForOttIDs(args);
			} else if (command.compareTo("extracttaxonomysubtreeforottids") == 0) {
				cmdReturnCode = mr.extractTaxonomySubTreeForOttIDs(args);
			} else if (command.compareTo("extractdraftsubtreefornodeids") == 0) {
				cmdReturnCode = mr.extractDraftSubTreeForNodeIDs(args);
			// not sure where this should live
			} else if (command.compareTo("nexson2newick") == 0) {
				cmdReturnCode = mr.nexson2newick(args);
			} else if (command.equals("nodeinfo")) {
				cmdReturnCode = mr.nodeInfo(args);
			// testing functions
			} else if (command.compareTo("makeprunedbipartstestfiles") == 0) {
				cmdReturnCode = mr.makePrunedBipartsTestFiles(args);
			} else if (command.compareTo("pgloadind") == 0) {
				cmdReturnCode = mr.pg_loading_ind_studies(args);
			} else if (command.compareTo("pgloadindnew") == 0) {
				cmdReturnCode = mr.pg_loading_ind_studies_newick(args);
			} else if (command.compareTo("pgdelind") == 0) {
				cmdReturnCode = mr.pg_delete_ind_study(args);
			} else if (command.compareTo("mapcompat") == 0) {
				cmdReturnCode = mr.mapcompat(args);
			} else if (command.compareTo("gettaxonomy") == 0) {
				cmdReturnCode = mr.getTaxonomyVersion(args);
			}  else if (command.compareTo("taxtree") == 0) {
				cmdReturnCode = mr.getTaxonomyTreeExport(args);
			} else if (command.compareTo("getmrca") == 0) {
				cmdReturnCode = mr.getMRCA(args);
			} else if (command.compareTo("loadott") == 0) {
				cmdReturnCode = mr.loadOTT(args);
			} else if (command.compareTo("nodestatus") == 0) {
				cmdReturnCode = mr.getNodeStatus(args);
			} else if (command.compareTo("treecomp") == 0){
				cmdReturnCode = mr.treeCompare(args);
			} else if (command.compareTo("taxcomp") == 0){
				cmdReturnCode = mr.taxCompare(args);
			}else {
				System.err.println("Unrecognized command \"" + command + "\"");
				cmdReturnCode = 2;
			}
		} catch (StoredEntityNotFoundException tnfx) {
			String action = "Command \"" + command + "\"";
			tnfx.reportFailedAction(System.err, action);
		} catch (TreeIngestException tix) {
			String action = "Command \"" + command + "\"";
			tix.reportFailedAction(System.err, action);
		} catch (DataFormatException dfx) {
			String action = "Command \"" + command + "\"";
			dfx.reportFailedAction(System.err, action);
		}
		if (cmdReturnCode == 2) {
			printHelp();
		}
		System.exit(cmdReturnCode);
	}
}
