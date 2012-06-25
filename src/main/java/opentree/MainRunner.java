package opentree;

public class MainRunner {
	public void taxonomyLoadParser(String [] args){
		String filename = args[1];
		String graphname = args[2] ;
		TaxonomyLoader tl = new TaxonomyLoader(graphname);
		if (args[0].compareTo("inittax") == 0){
			System.out.println("initializing taxonomy from "+filename+" to "+graphname);
			tl.addInitialTaxonomyTableIntoGraph(filename);
		}else if(args[0].compareTo("addtax") == 0){
			System.out.println("adding taxonomy from "+filename+" to "+graphname);
			tl.addAdditionalTaxonomyTableIntoGraph(filename);
		}else{
			System.err.println("ERROR: not a known command");
			tl.shutdownDB();
			printHelp();
		}
		tl.shutdownDB();
	}
	
	public void taxonomyQueryParser(String [] args){
		String query = args[1];
		String graphname = args[2];
		TaxonomyExplorer te =  new TaxonomyExplorer(graphname);
		if(args[0].compareTo("comptaxtree") == 0){
			System.out.println("constructing a comprehensive tax tree of "+query);
			te.buildTaxonomyTree(query);
		}else if(args[0].compareTo("findcycles")==0){
			System.out.println("finding taxonomic cycles for " + query);
			te.findTaxonomyCycles(query);
		}else if(args[0].compareTo("jsgraph")==0){
			System.out.println("constructing json graph data for " + query);
			te.constructJSONGraph(query);
		}else{
			System.err.println("ERROR: not a known command");
			te.shutdownDB();
			printHelp();
		}
		te.shutdownDB();
	}
	
	public void graphImporterParser(String [] args){
		String filename = args[1];
		String graphname = args[2];
		GraphImporter gi = new GraphImporter(graphname);
		if(args[0].compareTo("addtree") == 0){
			System.out.println("adding a tree to the graph: "+ filename);
			gi.preProcessTree(filename);
			gi.processMRCAS();
		}else if(args[0].compareTo("inittree")==0){
			System.out.println("initializing the database with a tree" + filename);
			gi.preProcessTree(filename);
			gi.initializeGraphDB();
		}else{
			System.err.println("ERROR: not a known command");
			gi.shutdownDB();
			printHelp();
		}
		gi.shutdownDB();
	}
	
	public static void printHelp(){
		System.out.println("==========================");
		System.out.println("usage: treemachine command options");
		System.out.println("");
		System.out.println("commands");
		System.out.println("---taxonomy---");
		System.out.println("\tinittax <filename> <graphdbfolder>");
		System.out.println("\taddtax <filename> <graphdbfolder>");
		System.out.println("---query---");
		System.out.println("\tcomptaxtree <name> <graphdbfolder>");
		System.out.println("\tfindcycles <name> <graphdbfolder>");
		System.out.println("\tjsgraph <name> <graphdbfolder>");
		System.out.println("---process---");
		System.out.println("\tinittree <filename> <graphdbfolder>");
		System.out.println("\taddtree <filename> <graphdbfolder>");
		System.exit(0);
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("treemachine version alpha.alpha.prealpha");
		if(args.length < 3){
			printHelp();
		}else if(args[0] == "help"){
			printHelp();
		}else{
			System.out.println("things will happen here");
			MainRunner mr = new MainRunner();
			if(args.length != 3){
				System.err.println("ERROR: not the right arguments");
				printHelp();
			}
			if(args[0].compareTo("inittax")==0 || args[0].compareTo("addtax")==0){
				mr.taxonomyLoadParser(args);
			}else if(args[0].compareTo("comptaxtree") == 0 || args[0].compareTo("findcycles")==0 || args[0].compareTo("jsgraph") == 0){
				mr.taxonomyQueryParser(args);
			}else if(args[0].compareTo("inittree") == 0 || args[0].compareTo("addtree")==0){
				mr.graphImporterParser(args);
			}
		}
	}

}
