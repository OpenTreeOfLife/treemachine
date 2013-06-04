package opentree;

import jade.tree.JadeNode;
import jade.tree.TreeReader;
import jade.tree.JadeTree;
import jade.tree.NexsonReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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

import opentree.TaxonNotFoundException;
import opentree.TreeNotFoundException;
import opentree.StoredEntityNotFoundException;
import opentree.testing.TreeUtils;

public class MainRunner {
	//static Logger _LOG = Logger.getLogger(MainRunner.class);

	/// @returns 0 for success, 1 for poorly formed command
	public int taxonomyLoadParser(String [] args) {
		if (args.length < 3) {
			System.out.println("arguments should be: filename synfilename graphdbfolder");
			return 1;
		}
		String filename = args[1];
		String synfilename = args[2];
		String graphname = args[3] ;
		if (args[0].compareTo("inittax") != 0) {
			System.err.println("ERROR: not a known command");
			return 1;
		}
		GraphInitializer tl = new GraphInitializer(graphname);
		System.out.println("initializing taxonomy from " + filename + " with synonyms in " + synfilename+" to " + graphname);
		try {
			tl.addInitialTaxonomyTableIntoGraph(filename, synfilename);
		} finally {
			tl.shutdownDB();
		}
		return 0;
	}
	
	/// @returns 0 for success, 1 for poorly formed command
	public int graphReloadTrees(String [] args) {
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
	
	/*
	 Peek at tree flavour, report back, reset reader for subsequent processing
	 @param r a tree file reader
	 @return treeFormat a string indicating recognized tree format
	*/
	public String divineTreeFormat(Reader r) throws java.io.IOException, DataFormatException {
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
	
	/**
	 * @returns 0 for success, 1 for error, 2 for error with a request that the generic help be displayed
	 */
	public int graphImporterParser(String [] args) 
					throws TaxonNotFoundException, DataFormatException, TreeIngestException {
		boolean readingNewick = false;
		boolean readingNexson = false;
		if (args[0].compareTo("addnewick") == 0) {
			readingNewick = true;
		} else if (args[0].compareTo("addnexson") == 0) {
			readingNexson = true;
		} else {
			System.out.println("Unrecognized command \"" + args[0] + "\" ");
			return 2;
		}
		String filename;
		String idfilename = "";
		String focalgroup;
		String sourcename;
		String graphname;
		if (readingNewick) {
			if (args.length != 6) {
				System.out.println("arguments should be: filename idfilename focalgroup sourcename graphdbfolder");
				return 1;
			}
			filename = args[1];
			idfilename = args[2];
			focalgroup = args[3];
			sourcename = args[4];
			graphname = args[5];
		} else {
			if (args.length != 5) {
				System.out.println("arguments should be: filename focalgroup sourcename graphdbfolder");
				return 1;
			}
			filename = args[1];
			focalgroup = args[2];
			sourcename = args[3];
			graphname = args[4];
		}
		int treeCounter = 0;
		GraphImporter gi = new GraphImporter(graphname);
		try {
			if (gi.hasSoureTreeName(sourcename)) {
				String emsg = "Tree with the name \"" + sourcename + "\" already exists in this db.";
				throw new TreeIngestException(emsg);
			}
			System.out.println("adding tree(s) to the graph from file: " + filename);
			ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
			try {
				if (readingNewick) { // newick
					System.out.println("Reading newick file...");
					TreeReader tr = new TreeReader();
					BufferedReader ibr = new BufferedReader(new FileReader(idfilename));
					String treeID;
					String ts = "";
					BufferedReader br = new BufferedReader(new FileReader(filename));
					int treeNum = 0;
					while ((ts = br.readLine()) != null) {
						if (ts.length() > 1) {
							++treeNum;
							treeID = "";
							while (treeID.length() == 0) {
								if (null  == (treeID = ibr.readLine())) {
									String emsg = "Newick treefile \"" + filename + "\" has (at least) " + treeNum + " line(s), but the file of IDs \"" + idfilename + "\" does not have that many ids. Expecting one ID per line.";
									throw new TreeIngestException(emsg);
								}
							}
							JadeTree newestTree = tr.readTree(ts);
							newestTree.assocObject("id", treeID);
							jt.add(newestTree);
							treeCounter++;
						}
					}
					br.close();
				} else { // nexson
					System.out.println("Reading nexson file...");
					for (JadeTree tree : NexsonReader.readNexson(filename)) {
						jt.add(tree);
						treeCounter++;
					}
				}
			} catch (IOException ioe) {}
			System.out.println(treeCounter + " trees read.");
			
			// Go through the trees again and add and update as necessary
			for (int i = 0; i < jt.size(); i++) {
				System.out.println("adding a tree to the graph: " + i);
				gi.setTree(jt.get(i));
				if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
					sourcename = (String)jt.get(i).getObject("ot:studyId");
				}
				if (jt.size() == 1) {
					gi.addSetTreeToGraph(focalgroup, sourcename);
				} else {
					gi.addSetTreeToGraph(focalgroup, sourcename + "_" + String.valueOf(i));
					gi.deleteTreeBySource(sourcename + "_" + String.valueOf(i));	
				}
				// gi.updateAfterTreeIngest(false);
				
			}
			if (jt.size() > 1) {
				for (int i = 0; i < jt.size(); i++) {
					if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
						sourcename = (String)jt.get(i).getObject("ot:studyId");
					}
					System.out.println("adding a tree to the graph: " + i);
					gi.setTree(jt.get(i));
					gi.addSetTreeToGraph(focalgroup, sourcename + "_" + String.valueOf(i)); //@QUERY treeID has been added, so I'm not sure we want to munge the sourcename
				}
			}
		} finally {
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
	public int graphArgusJSON(String [] args)
			throws TreeNotFoundException {
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
	 */
	public int graphExplorerParser(String [] args)
			throws TaxonNotFoundException {
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
	        Node firstNode = gi.findGraphNodeByName(name);
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
	            outFile.write(synthTree.getRoot().getNewick(reportBranchLength));
	            outFile.write(";\n");
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
			
			Node firstNode = gi.findGraphNodeByName(name);
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
	            outFile.write(synthTree.getRoot().getNewick(true));
	            outFile.write(";\n");
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
	public int justTreeAnalysis(String [] args)
				throws DataFormatException, TaxonNotFoundException, TreeIngestException{
		if (args.length > 3) {
			System.out.println("arguments should be: filename graphdbfolder");
			return 1;
		}
		String filename = args[1];
		String graphname = args[2];
		int treeCounter = 0;
		// Run through all the trees and get the union of the taxa for a raw taxonomy graph
		// read the tree from a file
		String ts = "";
		ArrayList<JadeTree> jt = new ArrayList<JadeTree>();

		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			if (divineTreeFormat(br).compareTo("newick") == 0) { // newick
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
				for (JadeTree tree : NexsonReader.readNexson(filename)) {
					jt.add(tree);
					treeCounter++;
				}
			}
			br.close();
		} catch (IOException ioe) {}
		System.out.println(treeCounter + " trees read.");

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
			  String srce_id = st.nextToken().trim();
			  String srce_pid = st.nextToken().trim();
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
				outFile.write((i+2)+"|1|"+namesal.get(i)+"| | | | | | |\n");
				//             tid  pid    name       rank+src+srce_id+srce_pid+uniqname (all empty)
			}
			//outFile.write("1\t|\t0\t|\tlife\t|\t\n");
			outFile.write("1|0|life| | | | | | |\n");
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
		// Go through the trees again and add and update as necessary
		for (int i = 0; i < jt.size(); i++) {
			String sourcename = "treeinfile";
			if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
				sourcename = (String)jt.get(i).getObject("ot:studyId");
			}
			sourcename += "_" + String.valueOf(i);

			System.out.println("adding tree '" + sourcename + "' to the graph");
			gi.setTree(jt.get(i));
			gi.addSetTreeToGraph("life", sourcename);
			gi.deleteTreeBySource(sourcename);
		}			
		// adding them again after all the nodes are there
		for (int i = 0; i < jt.size(); i++) {
			String sourcename = "treeinfile";
			if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
				sourcename = (String)jt.get(i).getObject("ot:studyId");
			}
			sourcename += "_" + String.valueOf(i);

			System.out.println("adding tree '" + sourcename + "' to the graph");
			gi.setTree(jt.get(i));
			gi.addSetTreeToGraph("life", sourcename);
		}
		gi.shutdownDB();
		return 0;
	}
	
	/// @returns 0 for success, 1 for poorly formed command
	public int graphListPruner(String [] args) {
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
				Node t = gi.findGraphNodeByName(tn);
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
	public int graphExplorerMapSupport(String [] args) throws TaxonNotFoundException {
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
	public int csvDumpParser(String [] args) {
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
	public int treeUtilsDB(String [] args){
		if (args.length != 3){
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
			while ((ts = br.readLine())!=null) {
				if (ts.length() > 1)
					jt.add(tr.readTree(ts));
			}
			br.close();
		} catch (IOException ioe) {}
		System.out.println("trees read");
		//
		boolean success = false;
		String graphdbn = args[2];
		
		String arg = args[0];
		if (arg.equals("labeltax")){
			GraphExplorer ge = new GraphExplorer(graphdbn);	
			for (int i=0;i<jt.size();i++){
				ge.labelInternalNodesTax(jt.get(i));
				System.out.println(jt.get(i).getRoot().getNewick(false));
			}
			ge.shutdownDB();
		}else if(arg.equals("checktax")){
			GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
			try{
				boolean good = PhylografterConnector.fixNamesFromTrees(jt,graphDb,false);
				if (good == false){
					System.out.println("failed to get the names from server fixNamesFromTrees");
					graphDb.shutdownDb();
					return 0;
				}
			}catch(IOException ioe){
				ioe.printStackTrace();
				System.out.println("failed to get the names from server fixNamesFromTrees");
				graphDb.shutdownDb();
				return 0;
			}
			graphDb.shutdownDb();
		}
		return (success ? 0 : -1);
	}
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int synthesizeDraftTreeWithList(String [] args) throws OttolIdNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be rootOTToLid listofsources(CSV) graphdbfolder");
			return 1;
		}
		String ottolId = args[1];
		String slist = args[2];
		String graphname = args[3];
		boolean success = false;
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			// build the list of preferred sources, this should probably be done externally
			LinkedList<String> preferredSources = new LinkedList<String>();
			String [] tsl = slist.split(",");
			for(int i=0;i<tsl.length;i++){preferredSources.add(tsl[i]);}
			preferredSources.add("taxonomy");

			// find the start node
			Node firstNode = ge.findGraphTaxNodeByUID(ottolId);
			if (firstNode == null) {
				throw new opentree.OttolIdNotFoundException(ottolId);
			}

			success = ge.synthesizeAndStoreDraftTreeBranches(firstNode, preferredSources);
		} catch (OttolIdNotFoundException oex) {
			oex.printStackTrace();
		} finally {
			ge.shutdownDB();
		}

		return (success ? 0 : -1);
	}
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int synthesizeDraftTree(String [] args) throws OttolIdNotFoundException {
		if (args.length != 3) {
			System.out.println("arguments should be rootOTToLid graphdbfolder");
			return 1;
		}
		String ottolId = args[1];
		String graphname = args[2];
		boolean success = false;
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			// build the list of preferred sources, this should probably be done externally
			LinkedList<String> preferredSources = new LinkedList<String>();
			preferredSources.add("15");
			preferredSources.add("taxonomy");
			
			// find the start node
			Node firstNode = ge.findGraphTaxNodeByUID(ottolId);
			if (firstNode == null) {
				throw new opentree.OttolIdNotFoundException(ottolId);
			}
			success = ge.synthesizeAndStoreDraftTreeBranches(firstNode, preferredSources);
		} catch (OttolIdNotFoundException oex) {
			oex.printStackTrace();
		} finally {
			ge.shutdownDB();
		}
		return (success ? 0 : -1);
	}

	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftTree(String [] args) throws OttolIdNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be rootOTToLid outFileName graphdbfolder");
			return 1;
		}
		String ottolId = args[1];
		String outFileName = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);
		
		// find the start node
        Node firstNode = ge.findGraphTaxNodeByUID(ottolId);
        if (firstNode == null) {
            throw new opentree.OttolIdNotFoundException(ottolId);
        }
		
		JadeTree synthTree = null;
		synthTree = ge.extractDraftTree(firstNode, GraphBase.DRAFTTREENAME);

		if (synthTree == null) {
			return -1;
		}
		
		PrintWriter outFile = null;
		try {
			outFile = new PrintWriter(new FileWriter(outFileName));
			outFile.write(synthTree.getRoot().getNewick(false));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			outFile.close();
			ge.shutdownDB();
		}
		
		return 0;
	}

	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int extractDraftSubTreeForOttIDs(String [] args) throws OttolIdNotFoundException {
		if (args.length != 4) {
			System.out.println("arguments should be tipOTTid1,tipOTTid2,... outFileName graphdbfolder");
			return 1;
		}

		String[] OTTids = args[1].trim().split(",");
		String outFileName = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer(graphname);

		ArrayList<Node> tipNodes = new ArrayList<Node>();
		for (String OTTid : OTTids) {
			tipNodes.add(ge.findGraphTaxNodeByUID(OTTid));
		}
		
		System.out.println(ge.extractDraftSubtreeForTipNodes(tipNodes).toString());

		return 0;
    }
	
	/// @returns 0 for success, 1 for poorly formed command, -1 for failure
	public int addTaxonomyMetadataNodeToIndex(String [] args) {
		if (args.length != 3) {
			System.out.println("arguments should be metadatanodeid graphdbfolder");
			return 1;
		}

		String metadataNodeIdStr = args[1];
		String graphname = args[2];
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(new EmbeddedGraphDatabase(graphname));
		Index<Node> metadataNodeIndex = graphDb.getNodeIndex("sourceMetaNodes");
		
		Node metadataNode = graphDb.getNodeById(Long.valueOf(metadataNodeIdStr));
		
		Transaction tx = graphDb.beginTx();
		
		try {
			metadataNodeIndex.add(metadataNode, "source", "taxonomy");
			metadataNode.setProperty("source", "taxonomy");
			tx.success();
		} catch (Exception ex) {
			tx.failure();
			ex.printStackTrace();
		} finally {
			tx.finish();
			graphDb.shutdownDb();
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
            outFile.write(randomTree.getNewick(reportBranchLength));
            outFile.write(";\n");
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
	
	/*
	 * Use this to load trees from nexson into the graph from a directory
	 * not from the server
	 */
	public int pg_loading(String [] args){
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
		if (args.length != 3) {
			graphDb.shutdownDb();
			return 1;
		}
		String directory = args[2];
		System.out.println("loading files from "+directory+" into "+args[1]);
		File file = new File(directory);
		File [] files = file.listFiles();
		for(int i =0;i<files.length;i++){
			System.out.println("files "+ files[i]);
			BufferedReader br= null;
			List<JadeTree> jt = null;
				try{
					br = new BufferedReader(new FileReader(files[i]));
					jt = NexsonReader.readNexson(br);
					for (JadeTree j : jt) {
						System.out.println(files[i] + ": " + j.getExternalNodeCount());
					}
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}catch (java.lang.NullPointerException e){
					e.printStackTrace();
				}
				try{
					PhylografterConnector.fixNamesFromTrees(jt,graphDb,false);
				}catch(IOException ioe){
					ioe.printStackTrace();
					System.out.println("failed to get the names from server fixNamesFromTrees");
					continue;
				}
				try{
					for(JadeTree j: jt){
						GraphImporter gi = new GraphImporter(graphDb);
						boolean doubname = false;
						HashSet<Long> ottols = new HashSet<Long>();
						for(int m=0;m<j.getExternalNodeCount();m++){
							System.out.println(j.getExternalNode(m).getName()+" "+j.getExternalNode(m).getObject("ot:ottolid"));
							if(j.getExternalNode(m).getObject("ot:ottolid") == null){//use doubname as also 
								doubname = true;
								break;
							}
							if (ottols.contains((Long)j.getExternalNode(m).getObject("ot:ottolid")) == true){
								doubname = true;
								break;
							} else {
								ottols.add((Long)j.getExternalNode(m).getObject("ot:ottolid"));
							}
						}
						//check for any duplicate ottol:id
						if (doubname == true){
							System.out.println("there are duplicate names");
						} else {
							System.out.println("this is being added");
							gi.setTree(j);
							String sourcename = "";
							if (j.getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
								sourcename = (String)j.getObject("ot:studyId");
							}if (j.getObject("id") != null) { // use treeid (if present) as sourcename
								sourcename += "_"+(String)j.getObject("id");
							}							
							//test to see if the tree is already in there
							Index<Node> sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
							IndexHits<Node > hits = sourceMetaIndex.get("source", sourcename);
							if (hits.size() > 0){
								System.out.println("source "+sourcename+" already added");
							}else{
								gi.addSetTreeToGraphWIdsSet(sourcename);
							}
						}
					}
				} catch(java.lang.NullPointerException e){
					System.out.println("failed to get study "+files[i].getName());
					continue;
				} catch (TaxonNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (TreeIngestException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				try {
					br.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
		}
		graphDb.shutdownDb();
		return 0;
	}
	
	/*
	 * Use this to load trees from nexson into the graph from a file
	 * not from the server
	 */
	public int pg_loading_ind_studies(String [] args){
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
		if (args.length != 3) {
			graphDb.shutdownDb();
			return 1;
		}
		String filen = args[2];
		File file = new File(filen);
		System.out.println("file "+ file);
		BufferedReader br= null;
		List<JadeTree> jt = null;
		try{
			br = new BufferedReader(new FileReader(file));
			jt = NexsonReader.readNexson(br);
			for (JadeTree j : jt) {
				System.out.println(file + ": " + j.getExternalNodeCount());
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			graphDb.shutdownDb();
			return 0;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			graphDb.shutdownDb();
			return 0;
		}catch (java.lang.NullPointerException e){
			e.printStackTrace();
			graphDb.shutdownDb();
			return 0;
		}
		try{
			boolean good = PhylografterConnector.fixNamesFromTrees(jt,graphDb,false);
			if (good == false){
				System.out.println("failed to get the names from server fixNamesFromTrees");
				graphDb.shutdownDb();
				return 0;
			}
		}catch(IOException ioe){
			ioe.printStackTrace();
			System.out.println("failed to get the names from server fixNamesFromTrees");
			graphDb.shutdownDb();
			return 0;
		}
		try{
			for(JadeTree j: jt){
				GraphImporter gi = new GraphImporter(graphDb);
				boolean doubname = false;
				HashSet<Long> ottols = new HashSet<Long>();
				for(int m=0;m<j.getExternalNodeCount();m++){
					System.out.println(j.getExternalNode(m).getName()+" "+j.getExternalNode(m).getObject("ot:ottolid"));
					if(j.getExternalNode(m).getObject("ot:ottolid") == null){//use doubname as also 
						doubname = true;
						break;
					}
					if (ottols.contains((Long)j.getExternalNode(m).getObject("ot:ottolid")) == true){
						doubname = true;
						break;
					} else {
						ottols.add((Long)j.getExternalNode(m).getObject("ot:ottolid"));
					}
				}
				//check for any duplicate ottol:id
				if (doubname == true){
					System.out.println("there are duplicate names");
				} else {
					System.out.println("this is being added");
					gi.setTree(j);
					String sourcename = "";
					if (j.getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
						sourcename = (String)j.getObject("ot:studyId");
					}if (j.getObject("id") != null) { // use treeid (if present) as sourcename
						sourcename += "_"+(String)j.getObject("id");
					}							
					//test to see if the tree is already in there
					Index<Node> sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
					IndexHits<Node > hits = sourceMetaIndex.get("source", sourcename);
					if (hits.size() > 0){
						System.out.println("source "+sourcename+" already added");
					}else{
						gi.addSetTreeToGraphWIdsSet(sourcename);
					}
				}
			}
		} catch(java.lang.NullPointerException e){
			System.out.println("failed to get study "+file.getName());
			graphDb.shutdownDb();
			return 0;
		} catch (TaxonNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			graphDb.shutdownDb();
			return 0;
		} catch (TreeIngestException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			graphDb.shutdownDb();
			return 0;
		}
		try {
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			graphDb.shutdownDb();
			return 0;
		}
		graphDb.shutdownDb();
		return 0;
	}
	
	public int pgtesting(String [] args){
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
		if (args.length != 2) {
			return 1;
		}
		ArrayList<Long> list = PhylografterConnector.getUpdateStudyList("2010-01-01","2013-04-22");
		int rc = 0;
		
		long start = System.currentTimeMillis();
		
		for (Long k: list) {
			if (k > 20) {
				break;
			}
			try {
				//List<JadeTree> jt = PhylografterConnector.fetchTreesFromStudy(k);
				List<JadeTree> jt = PhylografterConnector.fetchGzippedTreesFromStudy(k);
				for (JadeTree j : jt) {
					System.out.println(k + ": " + j.getExternalNodeCount());
				}
				try {
					PhylografterConnector.fixNamesFromTrees(jt,graphDb,false);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		        for(JadeTree j: jt){
		        	GraphImporter gi = new GraphImporter(graphDb);
		        	boolean doubname = false;
		        	HashSet<Long> ottols = new HashSet<Long>();
		        	for(int m=0;m<j.getExternalNodeCount();m++){
		        		if(j.getExternalNode(m).getObject("ot:ottolid") == null){//use doubname as also 
		        			doubname = true;
		        			break;
		        		}
		        		if (ottols.contains((Long)j.getExternalNode(m).getObject("ot:ottolid")) == true){
		        			doubname = true;
		        			break;
		        		} else {
		        			ottols.add((Long)j.getExternalNode(m).getObject("ot:ottolid"));
		        		}
		        	}
		        	//check for any duplicate ottol:id
					if (doubname == true){
						System.out.println("there are duplicate names");
					} else {
						System.out.println("this is being added");
						gi.setTree(j);
						String sourcename = "";
						if (j.getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
							sourcename = (String)j.getObject("ot:studyId");
						}if (j.getObject("id") != null) { // use treeid (if present) as sourcename
							sourcename += "_"+(String)j.getObject("id");
						}
						//test to see if the tree is already in there
						Index<Node> sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
						IndexHits<Node > hits = sourceMetaIndex.get("source", sourcename);
						if (hits.size() > 0){
							System.out.println("source "+sourcename+" already added");
						}else{
							gi.addSetTreeToGraphWIdsSet(sourcename);
						}
					}
		        }
			} catch(java.lang.NullPointerException e){
				System.out.println("failed to get study "+k);
				rc = 1;
				continue;
			} catch (TaxonNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TreeIngestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		long elapsedTimeMillis = System.currentTimeMillis() - start;
		float elapsedTimeSec = elapsedTimeMillis/1000F;
		System.out.println("elapsed time: " + elapsedTimeSec);
		
		graphDb.shutdownDb();
		return rc;
	}
	
	public static void printHelp() {
		System.out.println("==========================");
		System.out.println("usage: treemachine command options");
		System.out.println("");
		System.out.println("commands");
		System.out.println("---initialize---");
		System.out.println("\tinittax <filename> <synonymfilename> <graphdbfolder> (initializes the tax graph with a tax list)\n");

		System.out.println("---graph input---");
		System.out.println("\taddnewick <filename> <filewithtreeids> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph of life)");
		System.out.println("\taddnexson <filename> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph of life)");
		System.out.println("\treprocess <graphdbfolder> (delete the sources and reprocess)");
		System.out.println("\tdeletetrees <graphdbfolder> (delete all the sources)\n");

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
		System.out.println("\tjusttrees <filename> <graphdbfolder> (loads the trees into a graph)");
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
		System.out.println("\tlabeltips <filename.tre> <filename>\n");
		System.out.println("\tlabeltax <filename.tre> <graphdbfolder>\n");
		System.out.println("\tchecktax <filename.tre> <graphdbfolder>\n");
		
		System.out.println("---synthesis functions---");
		System.out.println("\tsynthesizedrafttree <rootNodeId> <graphdbfolder> (perform default synthesis from the root node using source-preference tie breaking and store the synthesized rels)");
		System.out.println("\tsynthesizedrafttreelist <rootNodeId> <list> <graphdbfolder> (perform default synthesis from the root node using source-preferenc tie breaking and store the synthesized rels with a list (csv))");
		System.out.println("\textractdrafttree <rootNodeId> <outfilename> <graphdbfolder> extracts the default synthesized tree (if any) stored below the root node\n");
		
				
		System.out.println("---temporary functions---");
		System.out.println("\taddtaxonomymetadatanodetoindex <metadatanodeid> <graphdbfolder> add the metadata node attched to 'life' to the sourceMetaNodes index for the 'taxonomy' source\n");

		System.out.println("---testing---");
		System.out.println("\tmakeprunedbipartstestfiles <randomseed> <ntaxa> <path> (export newick files containing (1) a randomized tree and (2) topologies for each of its bipartitions, pruned to a minimal subset of taxa)\n");
		
		System.out.println("---server functions---");
		System.out.println("\tgetupdatedlist\n");
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//PropertyConfigurator.configure(System.getProperties());
		//System.err.println("treemachine version alpha.alpha.prealpha");
		if (args.length < 1) {
			printHelp();
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
			}
			if (command.compareTo("inittax") == 0) {
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
			}else if (command.compareTo("synthesizedrafttreelist") == 0) {
				cmdReturnCode = mr.synthesizeDraftTreeWithList(args);
			} else if (command.compareTo("extractdrafttree") == 0) {
				cmdReturnCode = mr.extractDraftTree(args);
				
			
			// temporary
			} else if (command.compareTo("addtaxonomymetadatanodetoindex") == 0) {
				cmdReturnCode = mr.addTaxonomyMetadataNodeToIndex(args);

			// testing functions
			} else if (command.compareTo("makeprunedbipartstestfiles") == 0) {
				cmdReturnCode = mr.makePrunedBipartsTestFiles(args);
			
			} else if (command.compareTo("pgload") == 0) {
				cmdReturnCode = mr.pg_loading(args);
			} else if (command.compareTo("pgloadind") == 0) {
				cmdReturnCode = mr.pg_loading_ind_studies(args);
			}else if (command.compareTo("getupdatedlist") == 0) {
				cmdReturnCode = mr.pgtesting(args);
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
