package opentree;

import gnu.trove.set.hash.TLongHashSet;
import jade.tree.JadeNode;
import jade.tree.TreeReader;
import jade.tree.JadeTree;
import jade.tree.NexsonReader;
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;


//import org.apache.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
//import org.apache.log4j.PropertyConfigurator;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.kernel.EmbeddedGraphDatabase;
//import org.neo4j.graphdb.index.IndexHits;

import opentree.exceptions.DataFormatException;
import opentree.exceptions.MultipleHitsException;
import opentree.exceptions.OttIdNotFoundException;
import opentree.exceptions.StoredEntityNotFoundException;
import opentree.exceptions.TaxonNotFoundException;
import opentree.exceptions.TreeIngestException;
import opentree.exceptions.TreeNotFoundException;
import opentree.GeneralUtils;
import opentree.testing.TreeUtils;
import jade.MessageLogger;
import jade.JSONMessageLogger;

public class MainRunner {
	//static Logger _LOG = Logger.getLogger(MainRunner.class);

	/// @returns 0 for success, 1 for poorly formed command
	public int taxonomyLoadParser(String [] args) throws TaxonNotFoundException {
		if (args.length < 3) {
			System.out.println("arguments should be: filename (optional:synfilename) graphdbfolder");
			return 1;
		}
		String filename = args[1];
		String graphname = "";
		String synfilename = "";
		if (args.length == 4) {
			synfilename = args[2];
			graphname = args[3];
			System.out.println("initializing taxonomy from " + filename + " with synonyms in " + synfilename+" to " + graphname);
		} else if(args.length == 3) {
			graphname = args[2];
			System.out.println("initializing taxonomy from " + filename + " to " + graphname);

		} else {
			System.out.println("you have the wrong number of arguments. should be : filename (optional:synonym) graphdbfolder");
			return 1;
		}
		GraphInitializer tl = new GraphInitializer(graphname);
		try {
			tl.addInitialTaxonomyTableIntoGraph(filename, synfilename);
		} finally {
			tl.shutdownDB();
		}
		return 0;
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
// TODO: newicks must be treated differently from nexsons. Former will contain '_', latter will not.
	public int graphImporterParser(String [] args) throws Exception {
		
		if (args.length != 6) {
			System.out.println("arguments should be: filename <taxacompletelyoverlap[T|F]> focalgroup sourcename graphdbfolder");
			return 1;
		}
		
		boolean readingNewick = false;
		if (args[0].compareTo("addnewick") == 0) {
			readingNewick = true;
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
				if (readingNewick) { // newick. replace '_' with ' ' in leaf labels to match with existing db
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
					//			System.out.println("Current name: " + tt.getName());
								String newName = tt.getName().replaceAll("_", " ");
								tt.setName(newName);
					//			System.out.println("\tName changed to: " + tt.getName());
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
				System.out.println("constructing a json for: "+ name);
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
			for(int i=0;i<tsl.length;i++){preferredSources.add(tsl[i]);}
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
		if (soverlap.toLowerCase().equals("f")){
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
		} catch (IOException ioe) {}
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
			  String pid = st.nextToken().trim();
			  String name = st.nextToken().trim();
			  String rank = st.nextToken().trim();
			  String srce = st.nextToken().trim();
			  String uniqname = st.nextToken().trim();
			 "tax.temp" is updated below. Note use of " " vs. original "\t" for easier reading
		 */	
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter("tax.temp"));
			ArrayList<String> namesal = new ArrayList<String>();
			namesal.addAll(names);
			for (int i = 0; i < namesal.size(); i++) {
				//outFile.write((i+2) + "\t|\t1\t|\t" + namesal.get(i) + "\t|\t\n");
				outFile.write((i+2) + "|1|" + namesal.get(i) + "| | | | | | | |\n");
				//             tid     pid    name       rank+src+srce_id+srce_pid+uniqname (all empty)
			}
			//outFile.write("1\t|\t0\t|\tlife\t|\t\n");
			outFile.write("1| |" + rootnodename + "| | | | | | | |\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		GraphInitializer gin = new GraphInitializer(graphname);
		// make a temp file to be loaded into the tax loader, a hack for now
		gin.addInitialTaxonomyTableIntoGraph("tax.temp", "");
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
	
	public int sourceTreeExplorer(String [] args) throws TreeNotFoundException {
		String sourcename = null;
		String treeID = null;
		String graphname = null;
		String rootNodeID = null;
		int maxDepth = -1;
		if (args[0].equalsIgnoreCase("sourceexplorer")) {
			if (args.length == 3) {
				sourcename = args[1];
				graphname = args[2];
			} else if (args.length == 4) {
				if (args[1].compareTo("id") != 0) {
					System.out.println("arguments should be:\n <sourcename> <graphdbfolder>\nor\n id <sourcename> <graphdbfolder>\n");
					return 1;
				}
				treeID = args[2];
				graphname = args[3];
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
			} else if (args.length == 6) {
				if (args[1].compareTo("id") != 0) {
					System.out.println("arguments should be:\n <sourcename> <rootNodeID> <maxDepth> <graphdbfolder>\nor\n id <sourcename> <graphdbfolder>\n");
					return 1;
				}
				treeID = args[2];
				rootNodeID = args[3];
				maxDepth = Integer.parseInt(args[4]);
				graphname = args[5];
			} else {
				System.out.println("arguments should be: <sourcename> <rootNodeID> <maxDepth> <graphdbfolder>");
				return 1;
			}
		}
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			JadeTree tree;
			if (rootNodeID == null) {
				if (treeID == null) {
					tree = ge.reconstructSource(sourcename, maxDepth);
				} else {
					tree = ge.reconstructSourceByTreeID(treeID, maxDepth);
				}
			} else {
				long rootNodeIDParsed = Long.parseLong(rootNodeID);
				if (treeID == null) {
					tree = ge.reconstructSource(sourcename, rootNodeIDParsed, maxDepth);
				} else {
					tree = ge.reconstructSourceByTreeID(treeID, rootNodeIDParsed, maxDepth);
				}
			}
			final String newick = tree.getRoot().getNewick(tree.getHasBranchLengths());
			System.out.println(newick + ";");
		} finally {
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
	public int graphExporter(String [] args) throws TaxonNotFoundException {
		String usageString = "arguments should be name outfile usetaxonomy[T|F] graphdbfolder";
		if (args.length > 5) {
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
			while ((ts = br.readLine())!=null) {
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
		System.err.println("Unrecognized command argument \"" + args[0] + "\"");
		return 2;
	}
	
	/*
	 * these are treeutils that need the database
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
				System.out.println(jt.get(i).getRoot().getNewick(false));
			}
			messageLogger.close();
			ge.shutdownDB();
		} else if (arg.equals("checktax")) {
			GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
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
			for (int i = 0; i < tsl.length; i++){preferredSources.add(tsl[i]);}
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
				throw new opentree.exceptions.OttIdNotFoundException(ottId);
			}
			try {
				success = ge.synthesizeAndStoreDraftTreeBranches(firstNode, preferredSources,test);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (OttIdNotFoundException oex) {
			oex.printStackTrace();
		} finally {
			ge.shutdownDB();
		}
		return (success ? 0 : -1);
	}

	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftTreeForOttId(String [] args) throws OttIdNotFoundException, MultipleHitsException, TaxonNotFoundException {

		// open the graph
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);
		String ottId = args[1];

		// get the start node
		String startNodeIdStr = String.valueOf(ge.findGraphTaxNodeByUID(ottId).getId());
		args[1] = startNodeIdStr;
		ge.shutdownDB();
		// do the synth
		return extractDraftTreeForNodeId(args);
	}
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftTreeForNodeId(String [] args) throws OttIdNotFoundException, MultipleHitsException, TaxonNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be rootottId outFileName graphdbfolder");
			return 1;
		}
		Long startNodeId = Long.valueOf(args[1]);
		String outFileName = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);
		
		// find the start node
//        Node firstNode = ge.findGraphTaxNodeByUID(ottId);
		Node firstNode = ge.graphDb.getNodeById(startNodeId);
//        if (firstNode == null) {
//            throw new opentree.exceptions.OttIdNotFoundException(ottId);
//        }
		
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

	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftSubTreeForOttIDs(String [] args) throws OttIdNotFoundException, MultipleHitsException, TaxonNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be tipOTTid1,tipOTTid2,... outFileName graphdbfolder");
			return 1;
		}

		System.out.println(args[1]);
		String[] OTTids = args[1].trim().split("\\,");
		String outFileName = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);

		ArrayList<Node> tipNodes = new ArrayList<Node>();
		for (String OTTid : OTTids) {
			System.out.println(OTTid);
			Node tip = ge.findGraphTaxNodeByUID(OTTid);
			if (tip != null) {
				System.out.println("id = " + tip.getId());
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
	public int extractDraftTreeForOttidJSON(String [] args) throws OttIdNotFoundException, MultipleHitsException, TaxonNotFoundException{
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
            	
//            	System.out.println(bipart.getNewick(reportBranchLength).concat(";\n"));
            	
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
		Index<Node> sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
		IndexHits<Node > hits = sourceMetaIndex.get("source", sourcename);
		if (hits.size() > 0) {
			return false;
		}
		return newTree;
	}
	
	public static int loadPhylografterStudy(GraphDatabaseAgent graphDb, 
											BufferedReader nexsonContentBR, String treeid,
											MessageLogger messageLogger,
											boolean onlyTestTheInput) 
											throws Exception {
		List<JadeTree> rawTreeList = null;
		ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
		rawTreeList = NexsonReader.readNexson(nexsonContentBR, true, messageLogger);
		if (treeid != null) {
			System.out.println("loading a specific tree: "+treeid);
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
			boolean good = PhylografterConnector.fixNamesFromTrees(jt, graphDb, true, messageLogger);
			System.out.println("done fixing name");
			if (good == false) {
				System.err.println("failed to get the names from server fixNamesFromTrees 3");
				return -1;
			}
		} catch (IOException ioe){
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
					System.out.println("skipping tree: "+treeJId);
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
			if (doubname == true){
				messageLogger.indentMessageStr(1, "null or duplicate names. Skipping tree", "tree id", treeJId);
			} else {
				gi.setTree(j);
				String sourcename = "";
				if (j.getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
					sourcename = (String)j.getObject("ot:studyId");
				}
				if (treeJId != null) { // use treeid (if present) as sourcename
					sourcename += "_" + treeJId;
				}
				if (onlyTestTheInput) {
					messageLogger.indentMessageStr(1, "Checking if tree could be added to graph", "tree id", treeJId);
				} else {
					messageLogger.indentMessageStr(1, "Adding tree to graph", "tree id", treeJId);
				}
				gi.addSetTreeToGraphWIdsSet(sourcename, false, onlyTestTheInput, messageLogger);
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
		if (args.length != 4 && args.length != 5) {
			graphDb.shutdownDb();
			System.out.println("the argument has to be graphdb filename treeid (test)");
			System.out.println("\tif you have test at the end, it will not be entered into the database, but everything will be performed");
			return 1;
		}
		if (args.length == 4) {
			if (!args[3].matches("\\d+")) { // check for missing treeid
				graphDb.shutdownDb();
				System.out.println("Missing treeid argument");
				System.out.println("the argument has to be graphdb filename treeid (test)");
				System.out.println("\tif you have test at the end, it will not be entered into the database, but everything will be performed");
				return 1;
			}
		}
		if (args.length == 5) {
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
			if (loadPhylografterStudy(graphDb, br, treeid, messageLogger, test) != 0) {
				rc = -1;
			}
		} catch (IOException e) {
			rc = -1;
		} catch (java.lang.NullPointerException e){
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
	
	/**
	 *  arguments are 
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
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[2]);
		if (args.length != 3) {
			graphDb.shutdownDb();
			return 1;
		}
		String node = args[1];
		System.out.println("Getting information about node " + node);
		Long nodel = Long.valueOf(node);
		Node tn=graphDb.getNodeById(nodel);
		System.out.println("properties\n================\n");
		for (String ts:tn.getPropertyKeys()){
			if (ts.equals("mrca") || ts.equals("outmrca") || ts.equals("nested_mrca")) {
				System.out.print(ts+"\t");
				long [] m = (long[])tn.getProperty(ts);
				if (m.length < 100000) {
					for (int i=0;i<m.length;i++) {
						System.out.print(m[i]+" ");
					}
					System.out.print("\n");
				}
				System.out.println(m.length);
				TLongHashSet ths = new TLongHashSet(m);
				System.out.println(ths.size());
			} else if (ts.equals("name")) {
				System.out.println(ts+"\t"+(String)tn.getProperty(ts));
			} else {
				System.out.println(ts+"\t"+(String)tn.getProperty(ts));
			}
		}
		graphDb.shutdownDb();
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
		} catch (IOException ioe) {}
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
		
		System.out.println("sucessfully wrote " + treeCounter + " newick trees to file '" + outFilename + "'");		
		return 0;
	}
	
	public static void printShortHelp() {
		System.out.println("======================Treemachine======================");
		System.out.println("usage: java -jar locationOftreemachine.jar command options");
		System.out.println("For a more comprehensive help message, type java -jar locationOftreemachine.jar help");
		System.out.println("");
		System.out.println("Here are some common commands with descriptions.");
		System.out.println("INPUT SET OF TREES (bootstrap, posterior probability)");
		System.out.println("  \033[1mjusttrees\033[0m <filename> <taxacompletelyoverlap[T|F]> <rootnodename> <graphdbfolder>");
		System.out.println("");
		System.out.println("INPUT TAXONOMY AND TREES");
		System.out.println("  Initializes the graph with a tax list in the format");
		System.out.println("    \033[1minittax\033[0m <filename> (optional:synonymfilename) <graphdbfolder>");
		System.out.println("  Add a newick tree to the graph");
		System.out.println("    \033[1maddnewick\033[0m <filename> <taxacompletelyoverlap[T|F]> <focalgroup> <sourcename> <graphdbfolder>");
		System.out.println("  Export a source tree from that graph with taxonomy mapped");
		System.out.println("    \033[1msourceexplorer\033[0m <sourcename> <graphdbfolder>");
		System.out.println("");
		System.out.println("WORK WITH GRAPH");
		System.out.println("  Export the graph in graphml format with statistics embedded");
		System.out.println("    \033[1mgraphml\033[0m <name> <outfile> <usetaxonomy[T|F]>  <graphdbfolder>");
		System.out.println("  Synthesize the graph");
		System.out.println("    \033[1mfulltree\033[0m <name> <graphdbfolder> <usetaxonomy[T|F]> <usebranchandbound[T|F]> sinklostchildren[T|F]");
		System.out.println("    \033[1mfulltree_sources\033[0m <name> <preferred sources csv> <graphdbfolder> usetaxonomy[T|F] sinklostchildren[T|F]");
		System.out.println("\n");
	}
	
	public static void printHelp() {
		System.out.println("==========================");
		System.out.println("usage: treemachine command options");
		System.out.println("");
		System.out.println("commands");
		System.out.println("---initialize---");
		System.out.println("\tinittax <filename> <synonymfilename> <graphdbfolder> (initializes the tax graph with a tax list)\n");

		System.out.println("---graph input---");
		System.out.println("\taddnewick <filename>  <taxacompletelyoverlap[T|F]> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph of life)");
		System.out.println("\taddnexson <filename>  <taxacompletelyoverlap[T|F]> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph)");
		System.out.println("\tpgloadind <graphdbfolder> filepath treeid [test] (add trees from the nexson file \"filepath\" into the db. If fourth arg is found the tree is just tested, not added).\n");
		System.out.println("\tmapcompat <graphdbfolder> treeid (maps the compatible nodes)");
		
		System.out.println("---graph output---");
		System.out.println("\tjsgol <name> <graphdbfolder> (constructs a json file from a particular node)");
		System.out.println("\tfulltree <name> <graphdbfolder> <usetaxonomy[T|F]> <usebranchandbound[T|F]> sinklostchildren[T|F] (constructs a newick file from a particular node)");
		System.out.println("\tfulltree_sources <name> <preferred sources csv> <graphdbfolder> usetaxonomy[T|F] sinklostchildren[T|F] (constructs a newick file from a particular node, break ties preferring sources)");
		System.out.println("\tfulltreelist <filename list of taxa> <preferred sources csv> <graphdbfolder> (constructs a newick file for a group of species)");
		System.out.println("\tmrpdump <name> <outfile> <graphdbfolder> (dumps the mrp matrix for a subgraph without the taxonomy branches)");
		System.out.println("\tgraphml <name> <outfile> <usetaxonomy[T|F]>  <graphdbfolder> (constructs a graphml file of the region starting from the name)");
		System.out.println("\tcsvdump <name> <outfile> <graphdbfolder> (dumps the graph in format node,parent,nodename,parentname,source,brlen\n");

		System.out.println("---graph exploration---");
		System.out.println("(This is for testing the graph with a set of trees from a file)");
		System.out.println("\tjusttrees <filename> <taxacompletelyoverlap[T|F]> <rootnodename> <graphdbfolder> (loads the trees into a graph)");
		System.out.println("\tsourceexplorer <sourcename> <graphdbfolder> (explores the different source files)");
		System.out.println("\tsourcepruner <sourcename> <nodeid> <maxDepth> <graphdbfolder> (explores the different source files)");
		System.out.println("\tlistsources <graphdbfolder> (lists the names of the sources loaded in the graph)");
		System.out.println("\tbiparts <graphdbfolder> (looks at bipartition information for a graph)");
		System.out.println("\tmapsupport <file> <outfile> <graphdbfolder> (maps bipartition information from graph to tree)");
		System.out.println("\tgetlicanames <nodeid> <graphdbfolder> (print the list of names that are associated with a lica if there are any names)\n");

		System.out.println("---tree functions---");
		System.out.println("(This is temporary and for doing some functions on trees (output or potential input))");
		System.out.println("\tcounttips <filename> (count the number of nodes and leaves in a newick)");
		System.out.println("\tdiversity <filename> (for each node it will print the immediate descendents and their diversity)");
		System.out.println("\tlabeltips <filename.tre> <filename>");
		System.out.println("\tlabeltax <filename.tre> <graphdbfolder>");
		System.out.println("\tchecktax <filename.tre> <graphdbfolder>");
		System.out.println("\tnexson2newick <filename.nexson> [filename.newick]\n");
		
		System.out.println("---synthesis functions---");
		System.out.println("\tsynthesizedrafttreelist_ottid <rootNodeOttId> <list> <graphdbfolder> (perform default synthesis from the root node using source-preferenc tie breaking and store the synthesized rels with a list (csv))");
		System.out.println("\tsynthesizedrafttreelist_nodeid <rootNodeId> <list> <graphdbfolder> (perform default synthesis from the root node using source-preferenc tie breaking and store the synthesized rels with a list (csv))");
		System.out.println("\textractdrafttree_ottid <rootNodeOttId> <outfilename> <graphdbfolder> extracts the default synthesized tree (if any) stored below the root node");
		System.out.println("\textractdrafttree_nodeid <rootNodeId> <outfilename> <graphdbfolder> extracts the default synthesized tree (if any) stored below the root node");
		System.out.println("\textractdraftsubtreefornodes <tipOttId1>,<tipOttId2>,... <outfilename> <graphdbfolder> extracts the default synthesized tree (if any) stored below the root node\n");
				
		System.out.println("---temporary functions---");
		System.out.println("\taddtaxonomymetadatanodetoindex <metadatanodeid> <graphdbfolder> add the metadata node attched to 'life' to the sourceMetaNodes index for the 'taxonomy' source\n");

		System.out.println("---testing---");
		System.out.println("\tmakeprunedbipartstestfiles <randomseed> <ntaxa> <path> (export newick files containing (1) a randomized tree and (2) topologies for each of its bipartitions, pruned to a minimal subset of taxa)\n");
		
		System.out.println("---server functions---");
		System.out.println("\tgetupdatedlist\n");
		
		System.out.println("---general functions---");
		System.out.println("\thelp (print this help)\n");
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
		if (command.compareTo("help") == 0) {
			printHelp();
			System.exit(0);
		}
		System.err.println("things will happen here");
		int cmdReturnCode = 0;
		try {
			MainRunner mr = new MainRunner();
			if (args.length < 2) {
				System.err.println("ERROR: not the right arguments");
				printHelp();
				System.exit(cmdReturnCode);
			} else if (command.compareTo("inittax") == 0) {
				cmdReturnCode = mr.taxonomyLoadParser(args);
			} else if (command.compareTo("addnewick") == 0
					|| command.compareTo("addnexson") == 0) {
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
					|| command.equalsIgnoreCase("sourcepruner")) {
				cmdReturnCode = mr.sourceTreeExplorer(args);
			} else if (command.compareTo("listsources") == 0
					|| command.compareTo("getsourcetreeids") == 0) {
				cmdReturnCode = mr.listSources(args);
			} else if (command.compareTo("graphml") == 0) {
				cmdReturnCode = mr.graphExporter(args);
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
					|| command.compareTo("labeltips") == 0) {
				cmdReturnCode = mr.treeUtils(args);
			} else if (command.compareTo("labeltax") == 0
					|| command.compareTo("checktax") == 0) {
				cmdReturnCode = mr.treeUtilsDB(args);
			} else if (command.compareTo("synthesizedrafttree") == 0) {
				cmdReturnCode = mr.synthesizeDraftTree(args);
			} else if (command.compareTo("synthesizedrafttreelist_ottid") == 0) {
				cmdReturnCode = mr.synthesizeDraftTreeWithListForTaxUID(args);
			} else if (command.compareTo("synthesizedrafttreelist_nodeid") == 0) {
				cmdReturnCode = mr.synthesizeDraftTreeWithListForNodeId(args);
			} else if (command.compareTo("extractdrafttree_ottid") == 0) {
				cmdReturnCode = mr.extractDraftTreeForOttId(args);
			} else if (command.compareTo("extractdrafttree_ottid_JSON") == 0) {
				cmdReturnCode = mr.extractDraftTreeForOttidJSON(args);
			} else if (command.compareTo("extractdrafttree_nodeid") == 0) {
				cmdReturnCode = mr.extractDraftTreeForNodeId(args);
			} else if (command.compareTo("extractdraftsubtreefornodes") == 0) {
				cmdReturnCode = mr.extractDraftSubTreeForOttIDs(args);
			// not sure where this should live
			} else if (command.compareTo("nexson2newick") == 0) {
				cmdReturnCode = mr.nexson2newick(args);
			} else if(command.equals("nodeinfo")){
				cmdReturnCode = mr.nodeInfo(args);
			// testing functions
			} else if (command.compareTo("makeprunedbipartstestfiles") == 0) {
				cmdReturnCode = mr.makePrunedBipartsTestFiles(args);
			} else if (command.compareTo("pgloadind") == 0) {
				cmdReturnCode = mr.pg_loading_ind_studies(args);
			} else if (command.compareTo("pgdelind") == 0) {
				cmdReturnCode = mr.pg_delete_ind_study(args);
			} else if (command.compareTo("mapcompat") == 0) {
				cmdReturnCode = mr.mapcompat(args);
			} else {
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
