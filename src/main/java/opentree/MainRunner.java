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

public class MainRunner {
	public void taxonomyLoadParser(String [] args){
		if(args.length < 3){
			System.out.println("arguments should be: filename graphdbfolder");
			return;
		}
		String filename = args[1];
		String graphname = args[2] ;
		GraphImporter tl = new GraphImporter(graphname);
		if (args[0].compareTo("inittax") == 0){
			System.out.println("initializing taxonomy from "+filename+" to "+graphname);
			tl.addInitialTaxonomyTableIntoGraph(filename);
		}else{
			System.err.println("ERROR: not a known command");
			tl.shutdownDB();
			printHelp();
			System.exit(1);
		}
		tl.shutdownDB();
	}
	
	
	public void graphImporterParser(String [] args){
		GraphImporter gi = null;
		if(args[0].compareTo("addtree") == 0){
			if(args.length != 5){
				System.out.println("arguments should be: filename focalgroup sourcename graphdbfolder");
				return;
			}
			String filename = args[1];
			String focalgroup = args[2];
			String sourcename = args[3];
			String graphname = args[4];
			gi = new GraphImporter(graphname);
			System.out.println("adding a tree to the graph: "+ filename);
			gi.preProcessTree(filename);
			try {
			    gi.addProcessedTreeToGraph(focalgroup,sourcename);
			    gi.updateAfterTreeIngest();
			} catch (TaxonNotFoundException tnfx) {
    			System.err.println("Tree could not be read because the taxon " + tnfx.getQuotedName() + " was not recognized");
    			System.exit(1);
			} catch (TreeIngestException tix) {
    			System.err.println("Tree could not be imported.\n" + tix.toString());
    			System.exit(1);
			}
		}else{
			System.err.println("ERROR: not a known command");
			printHelp();
			System.exit(1);
		}
		gi.shutdownDB();
	}
	
	public void graphExplorerParser(String [] args){
		GraphExplorer gi = null;
		if(args[0].compareTo("jsgol") == 0){
			if(args.length != 3){
				System.out.println("arguments should be: name graphdbfolder");
				return;
			}
			String name = args[1];
			String graphname = args[2];
			gi = new GraphExplorer();
			gi.setEmbeddedDB(graphname);
			System.out.println("constructing a json for: "+ name);
//			gi.constructJSONGraph(name);
			gi.writeJSONWithAltParentsToFile(name);
		}else if (args[0].compareTo("fulltree") == 0){
			if(args.length != 4){
				System.out.println("arguments should be: name preferredsource graphdbfolder");
				return;
			}
			String name = args[1];
			String sourcename = args[2];
			String graphname = args[3];
			gi = new GraphExplorer();
			gi.setEmbeddedDB(graphname);
			System.out.println("constructing a full tree for: "+name+" with cycles resolved by "+sourcename);
			gi.constructNewickSourceTieBreaker(name, sourcename);
		}else{
			System.err.println("ERROR: not a known command");
			gi.shutdownDB();
			printHelp();
			System.exit(1);
		}
		gi.shutdownDB();
	}
	
	public void justTreeAnalysis(String [] args){
		if (args.length > 3){
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
		try{
			BufferedReader br = new BufferedReader(new FileReader(filename));
			while((ts = br.readLine())!=null){
				if(ts.length()>1)
					jt.add(tr.readTree(ts));
			}
			br.close();
		}catch(IOException ioe){}
		System.out.println("trees read");
		HashSet<String> names = new HashSet<String>();
		for(int i = 0;i<jt.size();i++){
			for(int j=0;j<jt.get(i).getExternalNodeCount();j++){
				names.add(jt.get(i).getExternalNode(j).getName());
			}
		}
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter("tax.temp"));
			ArrayList<String> namesal = new ArrayList<String>(); namesal.addAll(names);
			for(int i=0;i<namesal.size();i++){
				outFile.write((i+2)+",1,"+namesal.get(i)+"\n");
			}
			outFile.write("1,0,life\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//make a temp file to be loaded into the tax loader, a hack for now
		gi.addInitialTaxonomyTableIntoGraph("tax.temp");
		//Use the taxonomy as the first tree in the composite tree
		
		System.out.println("started graph importer");
		//Go through the trees again and add and update as necessary
		for(int i=0;i<jt.size();i++){
			System.out.println("adding a tree to the graph: "+ i);
			gi.setTree(jt.get(i));
			try {
			    gi.addProcessedTreeToGraph("life","treeinfile_"+String.valueOf(i));
			    gi.updateAfterTreeIngest();
			} catch (TaxonNotFoundException tnfx) {
    			System.err.println("Tree could not be read because the taxon " + tnfx.getQuotedName() + " was not recognized");
    			System.exit(1);
			} catch (TreeIngestException tix) {
    			System.err.println("Tree could not be imported.\n" + tix.toString());
    			System.exit(1);
			}
		}
		gi.shutdownDB();
	}
	
	public void sourceTreeExplorer(String [] args){
		if (args.length > 3){
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
	
	public static void printHelp(){
		System.out.println("==========================");
		System.out.println("usage: treemachine command options");
		System.out.println("");
		System.out.println("commands");
		System.out.println("---taxonomy---");
		System.out.println("\tinittax <filename> <graphdbfolder> (initializes the tax graph with a tax list)");
		System.out.println("\n");
		System.out.println("---graphoflife---");
		System.out.println("\taddtree <filename> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph of life)");
		System.out.println("\n");
		System.out.println("---testing graph---");
		System.out.println("(This is for testing the graph with a set of trees from a file)");
		System.out.println("\tjusttrees <filename> <graphdbfolder> (loads the trees into a graph)");
		System.out.println("\tsourceexplorer <sourcename> <graphdbfolder> (explores the different source files)");
		System.out.println("\tjsgol <name> <graphdbfolder> (constructs a json file from a particular node)");
		System.out.println("\tfulltree <name> <preferred sources> <graphdbfolder> (constructs a newick file from a particular node)");

	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		PropertyConfigurator.configure(System.getProperties());
		System.out.println("treemachine version alpha.alpha.prealpha");
		if(args.length < 2){
			printHelp();
			System.exit(1);
		}else if(args[0] == "help"){
			printHelp();
			System.exit(0);
		}else{
			System.out.println("things will happen here");
			MainRunner mr = new MainRunner();
			if(args.length < 2){
				System.err.println("ERROR: not the right arguments");
				printHelp();
			}
			if(args[0].compareTo("inittax")==0){
				mr.taxonomyLoadParser(args);
			}else if(args[0].compareTo("addtree")==0){
				mr.graphImporterParser(args);
			}else if(args[0].compareTo("jsgol") == 0 || args[0].compareTo("fulltree") == 0){
				mr.graphExplorerParser(args);
			}else if(args[0].compareTo("justtrees")==0){
				mr.justTreeAnalysis(args);
			}else if(args[0].compareTo("sourceexplorer")==0){
				mr.sourceTreeExplorer(args);
			}else {
				System.err.println("Unrecognized command \"" + args[0] + "\"");
				printHelp();
				System.exit(1);
			}
		}
	}

}
