package opentree;

import jade.tree.TreeReader;
import jade.tree.JadeTree;
import jade.tree.NexsonReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


//import org.apache.log4j.Logger;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.PropertyConfigurator;
import org.neo4j.graphdb.Node;
import org.neo4j.kernel.EmbeddedGraphDatabase;
//import org.neo4j.graphdb.index.IndexHits;

import opentree.TaxonNotFoundException;
import opentree.TreeNotFoundException;
import opentree.StoredEntityNotFoundException;

public class MainRunner {
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
		GraphImporter tl = new GraphImporter(graphname);
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
		if (args[0].compareTo("addtree") != 0) {
			return 2;
		}
		if (args.length != 5) {
			System.out.println("arguments should be: filename focalgroup sourcename graphdbfolder");
			return 1;
		}
		String filename = args[1];
		String focalgroup = args[2];
		String sourcename = args[3];
		String graphname = args[4];
		int treeCounter = 0;
		GraphImporter gi = new GraphImporter(graphname);
		try {
			if (gi.hasSoureTreeName(sourcename)) {
				String emsg = "Tree with the name \"" + sourcename + "\" already exists in this db.";
				throw new TreeIngestException(emsg);
			}
			System.out.println("adding tree(s) to the graph from file: " + filename);
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
			
			// Go through the trees again and add and update as necessary
			for (int i = 0; i < jt.size(); i++) {
				System.out.println("adding a tree to the graph: " + i);
				gi.setTree(jt.get(i));
				if (jt.get(i).getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
					sourcename = (String)jt.get(i).getObject("ot:studyId");
				}
				if (jt.size() == 1) {
					gi.addSetTreeToGraph(focalgroup, sourcename);
					gi.updateAfterTreeIngest(false); // TODO: this still needs work
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
					gi.addSetTreeToGraph(focalgroup, sourcename + "_" + String.valueOf(i));
				}
			}
		} finally {
			gi.shutdownDB();
		}
		return 0;
	}
	
	/// @returns 0 for success, 1 for poorly formed command
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
			String usageString = "arguments should be: name graphdbfolder usetaxonomy[T|F] usebranchandbound[T|F]";
			if (args.length != 5) {
				System.out.println(usageString);
				return 1;
			}

			String name = args[1];
			String graphname = args[2];
			String _useTaxonomy = args[3];
			String _useBranchAndBound = args[4];
			
			boolean useTaxonomy = false;
			if (_useTaxonomy.equals("T")) {
				useTaxonomy = true;
			} else if (!(_useTaxonomy.equals("F"))) {
				System.out.println(usageString);
				return 1;
			}

			boolean useBranchAndBound= false;
			if (_useBranchAndBound.equals("T")) {
				useBranchAndBound = true;
			} else if (!(_useBranchAndBound.equals("F"))) {
				System.out.println(usageString);
				return 1;
			}

			gi = new GraphExplorer(graphname);
			try {
				gi.constructNewickTieBreakerDEFAULT(name, useTaxonomy, useBranchAndBound);
			} finally {
				gi.shutdownDB();
			}
			return 0;
		} else if (args[0].compareTo("fulltree_sources") == 0) {
			String usageString = "arguments should be: name preferredsource graphdbfolder usetaxonomy[T|F] usebranchandbound[T|F]";
			if (args.length != 6) {
				System.out.println(usageString);
				return 1;
			}
			
			String name = args[1];
			String sourcenames = args[2];
			String graphname = args[3];
			String _useTaxonomy = args[4];
			String _useBranchAndBound = args[5];
			
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
			
			boolean useBranchAndBound= false;
			if (_useBranchAndBound.equals("T")) {
				useBranchAndBound = true;
			} else if (!(_useBranchAndBound.equals("F"))) {
				System.out.println(usageString);
				return 1;
			}
			
			gi = new GraphExplorer(graphname);
			try {
				gi.constructNewickTieBreakerSOURCE(name, sources, useTaxonomy, useBranchAndBound);
			} finally {
				gi.shutdownDB();
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
		GraphImporter gi = new GraphImporter(graphname);
		try {
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
			
			// make a temp file to be loaded into the tax loader, a hack for now
			gi.addInitialTaxonomyTableIntoGraph("tax.temp", "");
			// Use the taxonomy as the first tree in the composite tree
			
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
				//gi.updateAfterTreeIngest(false);
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
				// gi.updateAfterTreeIngest(false);
			}
			//	gi.updateAfterTreeIngest(true);
		} finally {
			gi.shutdownDB();
		}
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
		if (args.length != 3) {
			System.out.println("arguments should be: sourcename graphdbfolder");
			return 1;
		}
		String sourcename = args[1];
		String graphname = args[2];
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			JadeTree tree = ge.reconstructSource(sourcename);
			final String newick = tree.getRoot().getNewick(tree.getHasBranchLengths());
			System.out.println(newick + ";");
		} finally {
			ge.shutdownDB();
		}
		return 0;
	}

	/// @returns 0 for success, 1 for poorly formed command
	public int listSources(String [] args) {
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return 1;
		}
		String graphname = args[1];
		GraphExplorer ge = new GraphExplorer(graphname);
		try {
			System.out.println(StringUtils.join(ge.getSourceList(), "\n"));
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
	
	public int pgtesting(String [] args){
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(args[1]);
		if (args.length != 2)
			return 1;
		ArrayList<Long> list = PhylografterConnector.getUpdateStudyList("2010-01-01","2013-03-22");
		int rc = 0;
		for (Long k: list){
//			if ((k == 60) || (k == 105) || (k == 106) || (k == 107) || (k == 115) || (k == 116)) { // some bad studies
//				System.out.println("Skipping study " + k);
//				continue;
//			}
			if (k > 20)
				break;
			try{
				List<JadeTree> jt = PhylografterConnector.fetchTreesFromStudy(k);
				for (JadeTree j : jt) {
					System.out.println(k + ": " + j.getExternalNodeCount());
				}
				PhylografterConnector.fixNamesFromTrees(k,jt,graphDb);
			} catch(java.lang.NullPointerException e){
				System.out.println("failed to get study "+k);
				rc = 1;
				continue;
			}
		}
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
		System.out.println("\taddtree <filename> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph of life)");
		System.out.println("\treprocess <graphdbfolder> (delete the sources and reprocess)");
		System.out.println("\tdeletetrees <graphdbfolder> (delete all the sources)\n");

		System.out.println("---graph output---");
		System.out.println("\tjsgol <name> <graphdbfolder> (constructs a json file from a particular node)");
		System.out.println("\tfulltree <name> <graphdbfolder> <usetaxonomy[T|F]> <usebranchandbound[T|F]> (constructs a newick file from a particular node)");
		System.out.println("\tfulltree_sources <name> <preferred sources csv> <graphdbfolder> <usetaxonomy[T|F]> <usebranchandbound[T|F]> (constructs a newick file from a particular node, break ties preferring sources)");
		System.out.println("\tfulltreelist <filename list of taxa> <preferred sources csv> <graphdbfolder> (constructs a newick file for a group of species)");
		System.out.println("\tmrpdump <name> <outfile> <graphdbfolder> (dumps the mrp matrix for a subgraph without the taxonomy branches)");
		System.out.println("\tgraphml <name> <outfile> <graphdbfolder> <usetaxonomy[T|F]> (constructs a graphml file of the region starting from the name)");
		System.out.println("\tcsvdump <name> <outfile> <graphdbfolder> (dumps the graph in format node,parent,nodename,parentname,source,brlen\n");

		System.out.println("---graph exploration---");
		System.out.println("(This is for testing the graph with a set of trees from a file)");
		System.out.println("\tjusttrees <filename> <graphdbfolder> (loads the trees into a graph)");
		System.out.println("\tsourceexplorer <sourcename> <graphdbfolder> (explores the different source files)");
		System.out.println("\tlistsources <graphdbfolder> (lists the names of the sources loaded in the graph)");
		System.out.println("\tbiparts <graphdbfolder> (looks at bipartition information for a graph)");
		System.out.println("\tmapsupport <file> <outfile> <graphdbfolder> (maps bipartition information from graph to tree)");
		System.out.println("\tgetlicanames <nodeid> <graphdbfolder> (print the list of names that are associated with a lica if there are any names)\n");

		System.out.println("---tree functions---");
		System.out.println("(This is temporary and for doing some functions on trees output by the fulltree)");
		System.out.println("\tcounttips <filename> (count the number of nodes and leaves in a newick)");
		System.out.println("\tdiversity <filename> (for each node it will print the immediate descendents and their diversity)");
		System.out.println("\tlabeltips <filename.tre> <filename>");
		
		System.out.println("---server functions---");
		System.out.println("\tgetupdatedlist\n");
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		PropertyConfigurator.configure(System.getProperties());
		System.out.println("treemachine version alpha.alpha.prealpha");
		if (args.length < 1) {
			printHelp();
			System.exit(1);
		}
		String command = args[0];
		if (command.compareTo("help") == 0) {
			printHelp();
			System.exit(0);
		}
		System.out.println("things will happen here");
		int cmdReturnCode = 0;
		try {
			MainRunner mr = new MainRunner();
			if (args.length < 2) {
				System.err.println("ERROR: not the right arguments");
				printHelp();
			}
			if (command.compareTo("inittax") == 0) {
				cmdReturnCode = mr.taxonomyLoadParser(args);
			} else if (command.compareTo("addtree") == 0) {
				cmdReturnCode = mr.graphImporterParser(args);
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
			} else if (command.compareTo("sourceexplorer") == 0) {
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
			} else if (command.compareTo("getupdatedlist") == 0) {
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
