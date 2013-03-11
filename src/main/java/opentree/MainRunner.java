package opentree;

import jade.tree.TreeReader;
import jade.tree.JadeTree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;

//import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.neo4j.graphdb.Node;
//import org.neo4j.graphdb.index.IndexHits;

public class MainRunner {
	public void taxonomyLoadParser(String [] args) {
		if (args.length < 3) {
			System.out.println("arguments should be: filename graphdbfolder");
			return;
		}
		String filename = args[1];
		String graphname = args[2] ;
		GraphImporter tl = new GraphImporter(graphname);
		if (args[0].compareTo("inittax") == 0) {
			System.out.println("initializing taxonomy from " + filename + " to " + graphname);
			tl.addInitialTaxonomyTableIntoGraph(filename);
		} else {
			System.err.println("ERROR: not a known command");
			tl.shutdownDB();
			printHelp();
			System.exit(1);
		}
		tl.shutdownDB();
	}
	
	public void graphReloadTrees(String [] args) {
		GraphImporter gi = null;
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return;
		}
		String graphname = args[1];
		gi = new GraphImporter(graphname);
		gi.deleteAllTreesAndReprocess();
		gi.shutdownDB();
	}
	
	public void graphDeleteTrees(String [] args) {
		GraphImporter gi = null;
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return;
		}
		String graphname = args[1];
		gi = new GraphImporter(graphname);
		gi.deleteAllTrees();
		gi.shutdownDB();
	}
	
	public void graphImporterParser(String [] args) {
		GraphImporter gi = null;
		if (args[0].compareTo("addtree") == 0) {
			if (args.length != 5) {
				System.out.println("arguments should be: filename focalgroup sourcename graphdbfolder");
				return;
			}
			String filename = args[1];
			String focalgroup = args[2];
			String sourcename = args[3];
			String graphname = args[4];
			gi = new GraphImporter(graphname);
			System.out.println("adding tree(s) to the graph: " + filename);
			String ts = "";
			ArrayList<JadeTree> jt = new ArrayList<JadeTree>();
			TreeReader tr = new TreeReader();
			try {
				BufferedReader br = new BufferedReader(new FileReader(filename));
				while ((ts = br.readLine())!=null) {
					if (ts.length() > 1) {
						jt.add(tr.readTree(ts));
					}
				}
				br.close();
			} catch (IOException ioe) {}
			System.out.println("trees read");
			//Go through the trees again and add and update as necessary
			for (int i = 0; i < jt.size(); i++) {
				System.out.println("adding a tree to the graph: " + i);
				gi.setTree(jt.get(i));
				try {
					if (jt.size() == 1) {
						gi.addProcessedTreeToGraph(focalgroup, sourcename);
						gi.updateAfterTreeIngest(false);//TODO: this still needs work
					} else {
						gi.addProcessedTreeToGraph(focalgroup, sourcename + "_" + String.valueOf(i));
					    gi.deleteTreeBySource(sourcename + "_" + String.valueOf(i));	
					}
				    //gi.updateAfterTreeIngest(false);
				} catch (TaxonNotFoundException tnfx) {
	    			System.err.println("Tree could not be read because the taxon " + tnfx.getQuotedName() + " was not recognized");
	    			System.exit(1);
				} catch (TreeIngestException tix) {
	    			System.err.println("Tree could not be imported.\n" + tix.toString());
	    			System.exit(1);
				}
			}
			if (jt.size() > 1) {
				for (int i = 0; i < jt.size(); i++) {
					System.out.println("adding a tree to the graph: " + i);
					gi.setTree(jt.get(i));
					try {
						gi.addProcessedTreeToGraph(focalgroup, sourcename + "_" + String.valueOf(i));
					} catch (TaxonNotFoundException tnfx) {
		    			System.err.println("Tree could not be read because the taxon " + tnfx.getQuotedName() + " was not recognized");
		    			System.exit(1);
					} catch (TreeIngestException tix) {
		    			System.err.println("Tree could not be imported.\n" + tix.toString());
		    			System.exit(1);
					}
				}
			}
		} else {
			System.err.println("ERROR: not a known command");
			printHelp();
			System.exit(1);
		}
		gi.shutdownDB();
	}
	
	public void graphExplorerParser(String [] args) {
		GraphExplorer gi = null;

		if (args[0].compareTo("jsgol") == 0) {
			if (args.length != 3) {
				System.out.println("arguments should be: name graphdbfolder");
				return;
			}
			String name = args[1];
			String graphname = args[2];
			GraphExporter ge = new GraphExporter(graphname);
			System.out.println("constructing a json for: "+ name);
			ge.writeJSONWithAltParentsToFile(name);
			
		} else if (args[0].compareTo("fulltree") == 0) {
		    String usageString = "arguments should be: name graphdbfolder usetaxonomy[T|F] usebranchandbound[T|F]";
			if (args.length != 5) {
				System.out.println(usageString);
				return;
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
	            return;
	        }

	        boolean useBranchAndBound= false;
            if (_useBranchAndBound.equals("T")) {
                useBranchAndBound = true;
            } else if (!(_useBranchAndBound.equals("F"))) {
                System.out.println(usageString);
                return;
            }

	        
			gi = new GraphExplorer();
			gi.setEmbeddedDB(graphname);
			gi.constructNewickSourceTieBreaker(name, useTaxonomy, useBranchAndBound);
			
		} else if (args[0].compareTo("fulltree_sources") == 0) {
			if (args.length != 4) {
				System.out.println("arguments should be: name preferredsource graphdbfolder");
				return;
			}
			String name = args[1];
			String sourcename = args[2];
			String [] sources = sourcename.split(",");
			System.out.println("Sources (in order) that will be used to break conflicts");
			for (int i = 0; i < sources.length; i++) {
				System.out.println(sources[i]);
			}
			String graphname = args[3];
			gi = new GraphExplorer();
			gi.setEmbeddedDB(graphname);
			gi.constructNewickSourceTieBreaker(name, sources);
		} else {
			System.err.println("ERROR: not a known command");
//			gi.shutdownDB(); // not used.
			printHelp();
			System.exit(1);
		}
		gi.shutdownDB();
	}
	
	public void justTreeAnalysis(String [] args) {
		if (args.length > 3) {
			System.out.println("arguments should be: filename graphdbfolder");
			return;
		}
		String filename = args[1];
		String graphname = args[2];
		GraphImporter gi = new GraphImporter(graphname);
		//Run through all the trees and get the union of the taxa for a raw taxonomy graph
		//read the tree from a file
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
		HashSet<String> names = new HashSet<String>();
		for (int i = 0; i < jt.size(); i++) {
			for (int j = 0; j < jt.get(i).getExternalNodeCount(); j++) {
				names.add(jt.get(i).getExternalNode(j).getName());
			}
		}
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter("tax.temp"));
			ArrayList<String> namesal = new ArrayList<String>(); namesal.addAll(names);
			for (int i = 0; i < namesal.size(); i++) {
				outFile.write((i+2) + "\t|\t1\t|\t" + namesal.get(i) + "\t|\t\n");
			}
			outFile.write("1\t|\t0\t|\tlife\t|\t\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//make a temp file to be loaded into the tax loader, a hack for now
		gi.addInitialTaxonomyTableIntoGraph("tax.temp");
		//Use the taxonomy as the first tree in the composite tree
		
		System.out.println("started graph importer");
		//Go through the trees again and add and update as necessary
		for (int i = 0; i < jt.size(); i++) {
			System.out.println("adding a tree to the graph: "+ i);
			gi.setTree(jt.get(i));
			try {
			    gi.addProcessedTreeToGraph("life", "treeinfile_" + String.valueOf(i));
			    gi.deleteTreeBySource("treeinfile_" + String.valueOf(i));
			    //gi.updateAfterTreeIngest(false);
			} catch (TaxonNotFoundException tnfx) {
    			System.err.println("Tree could not be read because the taxon " + tnfx.getQuotedName() + " was not recognized");
    			System.exit(1);
			} catch (TreeIngestException tix) {
    			System.err.println("Tree could not be imported.\n" + tix.toString());
    			System.exit(1);
			}
		}
		//adding them again after all the nodes are there
		for (int i = 0; i < jt.size(); i++) {
			System.out.println("adding a tree to the graph: "+ i);
			gi.setTree(jt.get(i));
			try {
			    gi.addProcessedTreeToGraph("life","treeinfile_" + String.valueOf(i));
			    //gi.updateAfterTreeIngest(false);
			} catch (TaxonNotFoundException tnfx) {
    			System.err.println("Tree could not be read because the taxon " + tnfx.getQuotedName() + " was not recognized");
    			System.exit(1);
			} catch (TreeIngestException tix) {
    			System.err.println("Tree could not be imported.\n" + tix.toString());
    			System.exit(1);
			}
		}
		
//	    gi.updateAfterTreeIngest(true);
		gi.shutdownDB();
	}
	
	public void graphListPruner(String [] args) {
		if (args.length != 4) {
			System.out.println("arguments should be: name preferredsource graphdbfolder");
			return;
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
		GraphExplorer gi = new GraphExplorer();
		gi.setEmbeddedDB(graphname);
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
		gi.shutdownDB();
	}
	
	public void sourceTreeExplorer(String [] args) {
		if (args.length > 3) {
			System.out.println("arguments should be: sourcename graphdbfolder");
			return;
		}
		String sourcename = args[1];
		String graphname = args[2];
		GraphExplorer ge = new GraphExplorer();
		ge.setEmbeddedDB(graphname);
		ge.reconstructSource(sourcename);
		ge.shutdownDB();
	}

	public void listSources(String [] args) {
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return;
		}
		String graphname = args[1];
		GraphExplorer ge = new GraphExplorer();
		ge.setEmbeddedDB(graphname);
		System.out.print(ge.getSourceList());
		ge.shutdownDB();
	}
	
	public void graphExplorerBiparts(String [] args) {
		if (args.length != 2) {
			System.out.println("arguments should be: graphdbfolder");
			return;
		}
		String graphname = args[1];
		GraphExplorer ge = new GraphExplorer();
		ge.setEmbeddedDB(graphname);
		ge.getBipartSupport("life"); // need to change this from hardcoded
		ge.shutdownDB();
	}
	
	public void graphExplorerMapSupport(String [] args) {
		if (args.length != 4) {
			System.out.println("arguments should be infile outfile graphdbfolder");
			return;
		}
		String infile = args[1];
		String outfile = args[2];
		String graphname = args[3];
		GraphExplorer ge = new GraphExplorer();
		ge.setEmbeddedDB(graphname);
		try {
			ge.getMapTreeSupport(infile, outfile);
		} catch (TaxonNotFoundException tnfx) {
			System.err.println("Tree could not be read because the taxon " + tnfx.getQuotedName() + " was not recognized");
			System.exit(1);
		}
		ge.shutdownDB();
	}
	
	public void graphExporter(String [] args) {
	    String usageString = "arguments should be name outfile usetaxonomy[T|F] graphdbfolder";
		if (args.length > 5) {
			System.out.println(usageString);
			return;
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
		    return;
		}
		
		GraphExporter ge = new GraphExporter(graphname);
		ge.writeGraphML(taxon, outfile, useTaxonomy);
		ge.shutdownDB();
	}
	
	public void mrpDumpParser(String [] args) {
		if (args.length > 4) {
			System.out.println("arguments should be name outfile graphdbfolder");
			return;
		}
		String taxon = args[1];
		String outfile = args[2];
		String graphname = args[3];
		GraphExporter ge = new GraphExporter(graphname);
		ge.mrpDump(taxon, outfile);
		ge.shutdownDB();
	}
	
	public void csvDumpParser(String [] args) {
		if (args.length != 4) {
			System.out.println("arguments should be name outfile graphdbfolder");
			return;
		}
		String taxon = args[1];
		String outfile = args[2];
		String graphname = args[3];
		GraphExporter ge = new GraphExporter(graphname);
		ge.dumpCSV(taxon, outfile,true);
		ge.shutdownDB();
	}
	
	public void getlicanames(String [] args) {
		if (args.length != 3) {
			System.out.println("arguments should be nodeid graphdbfolder");
			return;
		}
		String nodeid = args[1];
		String graphname = args[2];
		GraphExplorer ge = new GraphExplorer();
		ge.setEmbeddedDB(graphname);
		ge.printLicaNames(nodeid);
		ge.shutdownDB();
	}
	
	public void treeUtils(String [] args) {
		if (args.length < 2) {
			System.out.println("arguments need to at least be a treefilename");
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
		} else if (args[0].equals("diversity")) {
			for (int i = 0; i < jt.get(0).getInternalNodeCount(); i++) {
				if (jt.get(0).getInternalNode(i).getName().length() > 0) {
					System.out.print(jt.get(0).getInternalNode(i).getName() + " :" + jt.get(0).getInternalNode(i).getTipCount());
					for (int j = 0; j < jt.get(0).getInternalNode(i).getChildCount(); j++) {
						System.out.print(" || " + jt.get(0).getInternalNode(i).getChild(j).getName() + " :" + jt.get(0).getInternalNode(i).getChild(j).getTipCount());
					}
					System.out.print("\n");
				}
			}
		} else if (args[0].equals("labeltips")) {
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
		}
	}
	
	public static void printHelp() {
		System.out.println("==========================");
		System.out.println("usage: treemachine command options");
		System.out.println("");
		System.out.println("commands");
		System.out.println("---initialize---");
		System.out.println("\tinittax <filename> <graphdbfolder> (initializes the tax graph with a tax list)");
		System.out.println("\n");
		System.out.println("---graph input---");
		System.out.println("\taddtree <filename> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph of life)");
		System.out.println("\treprocess <graphdbfolder> (delete the sources and reprocess)");
		System.out.println("\tdeletetrees <graphdbfolder> (delete all the sources)");
		System.out.println("\n");
		System.out.println("---graph output---");
		System.out.println("\tjsgol <name> <graphdbfolder> (constructs a json file from a particular node)");
		System.out.println("\tfulltree <name> <graphdbfolder> <usetaxonomy[T|F]> <usebranchandbound[T|F]> (constructs a newick file from a particular node)");
		System.out.println("\tfulltree_sources <name> <preferred sources csv> <graphdbfolder> (constructs a newick file from a particular node, break tie preferring sources)");
		System.out.println("\tfulltreelist <filename list of taxa> <preferred sources csv> <graphdbfolder> (constructs a newick file for a group of species)");
		System.out.println("\tmrpdump <name> <outfile> <graphdbfolder> (dumps the mrp matrix for a subgraph without the taxonomy branches)");
		System.out.println("\tgraphml <name> <outfile> <graphdbfolder> <usetaxonomy[T|F]> (constructs a graphml file of the region starting from the name)");
		System.out.println("\tcsvdump <name> <outfile> <graphdbfolder> (dumps the graph in format node,parent,nodename,parentname,source,brlen");
		System.out.println("\n");
		System.out.println("---graph exploration---");
		System.out.println("(This is for testing the graph with a set of trees from a file)");
		System.out.println("\tjusttrees <filename> <graphdbfolder> (loads the trees into a graph)");
		System.out.println("\tsourceexplorer <sourcename> <graphdbfolder> (explores the different source files)");
		System.out.println("\tlistsources <graphdbfolder> (lists the names of the sources loaded in the graph)");
		System.out.println("\tbiparts <graphdbfolder> (looks at bipartition information for a graph)");
		System.out.println("\tmapsupport <file> <outfile> <graphdbfolder> (maps bipartition information from graph to tree)");
		System.out.println("\tgetlicanames <nodeid> <graphdbfolder> (print the list of names that are associated with a lica if there are any names)");
		System.out.println("\n");
		System.out.println("---tree functions---");
		System.out.println("(This is temporary and for doing some functions on trees output by the fulltree)");
		System.out.println("\tcounttips <filename> (count the number of nodes and leaves in a newick)");
		System.out.println("\tdiversity <filename> (for each node it will print the immediate descendents and their diversity)");
		System.out.println("\tlabeltips <filename.tre> <filename>");
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		PropertyConfigurator.configure(System.getProperties());
		System.out.println("treemachine version alpha.alpha.prealpha");
		if (args.length < 2) {
			printHelp();
			System.exit(1);
		} else if (args[0] == "help") {
			printHelp();
			System.exit(0);
		} else {
			System.out.println("things will happen here");
			MainRunner mr = new MainRunner();
			if (args.length < 2) {
				System.err.println("ERROR: not the right arguments");
				printHelp();
			}
			if (args[0].compareTo("inittax") == 0) {
				mr.taxonomyLoadParser(args);
			} else if (args[0].compareTo("addtree") == 0) {
				mr.graphImporterParser(args);
			} else if (args[0].compareTo("jsgol") == 0 || args[0].compareTo("fulltree") == 0 || args[0].compareTo("fulltree_sources") == 0) {
				mr.graphExplorerParser(args);
			} else if (args[0].compareTo("mrpdump") == 0) {
				mr.mrpDumpParser(args);
			} else if (args[0].compareTo("fulltreelist") == 0) {
				mr.graphListPruner(args);
			} else if (args[0].compareTo("justtrees") == 0) {
				mr.justTreeAnalysis(args);
			} else if (args[0].compareTo("sourceexplorer") == 0) {
				mr.sourceTreeExplorer(args);
			} else if (args[0].compareTo("listsources") == 0) {
				mr.listSources(args);
			} else if (args[0].compareTo("graphml") == 0) {
				mr.graphExporter(args);
			} else if (args[0].compareTo("biparts") == 0) {
				mr.graphExplorerBiparts(args);
			} else if (args[0].compareTo("mapsupport") == 0) {
				mr.graphExplorerMapSupport(args);
			} else if (args[0].compareTo("reprocess") == 0) {
				mr.graphReloadTrees(args);
			} else if (args[0].compareTo("deletetrees") == 0) {
				mr.graphDeleteTrees(args);
			} else if (args[0].compareTo("csvdump") == 0) {
				mr.csvDumpParser(args);
			} else if (args[0].compareTo("getlicanames") == 0) {
				mr.getlicanames(args);
			} else if (args[0].compareTo("counttips") == 0 || args[0].compareTo("diversity") == 0
					|| args[0].compareTo("labeltips") == 0) {
				mr.treeUtils(args);
			} else {
				System.err.println("Unrecognized command \"" + args[0] + "\"");
				printHelp();
				System.exit(1);
			}
		}
	}

}
