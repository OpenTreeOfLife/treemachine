package opentree;

public class MainRunner {
	public void taxonomyLoadParser(String [] args){
		if(args.length != 4){
			System.out.println("arguments should be: filename sourcename graphdbfolder");
			return;
		}
		String filename = args[1];
		String sourcename = args[2];
		String graphname = args[3] ;
		TaxonomyLoader tl = new TaxonomyLoader(graphname);
		if (args[0].compareTo("inittax") == 0){
			System.out.println("initializing taxonomy from "+filename+" to "+graphname);
			tl.addInitialTaxonomyTableIntoGraph(filename, sourcename);
		}else if(args[0].compareTo("addtax") == 0){
			System.out.println("adding taxonomy from "+filename+" to "+graphname);
			tl.addAdditionalTaxonomyTableIntoGraph(filename,sourcename);
		}else{
			System.err.println("ERROR: not a known command");
			tl.shutdownDB();
			printHelp();
		}
		tl.shutdownDB();
	}
	
	public void taxonomyQueryParser(String [] args){
		if(args[0].compareTo("checktree")!= 0 && args.length!=3){
			System.out.println("arguments should be: query graphdbfolder");
			return;
		}else if(args[0].compareTo("checktree")==0 && args.length!=4){
			System.out.println("arguments should be: treefile focalgroup graphdbfolder");
			return;
		}
		TaxonomyExplorer te = null;
		if(args[0].compareTo("comptaxtree") == 0){
			String query = args[1];
			String graphname = args[2];
			te =  new TaxonomyExplorer(graphname);
			System.out.println("constructing a comprehensive tax tree of "+query);
			te.buildTaxonomyTree(query);
		}else if(args[0].compareTo("findcycles")==0){
			String query = args[1];
			String graphname = args[2];
			te =  new TaxonomyExplorer(graphname);
			System.out.println("finding taxonomic cycles for " + query);
			te.findTaxonomyCycles(query);
		}else if(args[0].compareTo("jsgraph")==0){
			String query = args[1];
			String graphname = args[2];
			te =  new TaxonomyExplorer(graphname);
			System.out.println("constructing json graph data for " + query);
			te.constructJSONGraph(query);
		}else if(args[0].compareTo("checktree")==0){
			String query = args[1];
			String focalgroup = args[2];
			String graphname = args[3];
			te =  new TaxonomyExplorer(graphname);
			System.out.println("checking the names of " + query+ " against the taxonomy graph");
			te.checkNamesInTree(query,focalgroup);
		}else{
			System.err.println("ERROR: not a known command");
			te.shutdownDB();
			printHelp();
		}
		te.shutdownDB();
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
			gi.addProcessedTreeToGraph(focalgroup,sourcename);
		}else if(args[0].compareTo("inittree")==0){
			System.out.println("initializing the database with NCBI tax");
			String graphname = args[1];
			gi = new GraphImporter(graphname);
			gi.initializeGraphDBfromNCBI();
		}else{
			System.err.println("ERROR: not a known command");
			gi.shutdownDB();
			printHelp();
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
			gi = new GraphExplorer(graphname);
			System.out.println("constructing a json for: "+ name);
			gi.constructJSONGraph(name);
		}else if (args[0].compareTo("fulltree") == 0){
			if(args.length != 4){
				System.out.println("arguments should be: name preferredsource graphdbfolder");
				return;
			}
			String name = args[1];
			String sourcename = args[2];
			String graphname = args[3];
			gi = new GraphExplorer(graphname);
			System.out.println("constructing a full tree for: "+name+" with cycles resolved by "+sourcename);
			gi.constructNewickSourceTieBreaker(name, sourcename);
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
		System.out.println("\tinittax <filename> <sourcename> <graphdbfolder> (initializes the tax graph with a tax list)");
		System.out.println("\taddtax <filename> <sourcename> <graphdbfolder> (adds a tax list into the tax graph)");
		System.out.println("\tupdatetax <filename> <sourcename> <graphdbfolder> (updates a specific source taxonomy)");
		System.out.println("---taxquery---");
		System.out.println("\tcomptaxtree <name> <graphdbfolder> (construct a comprehensive tax newick)");
		System.out.println("\tfindcycles <name> <graphdbfolder> (find cycles in tax graph)");
		System.out.println("\tjsgraph <name> <graphdbfolder> (constructs a json file from tax graph)");
		System.out.println("\tchecktree <filename> <focalgroup> <graphdbfolder> (checks names in tree against tax graph)");
		System.out.println("---graphoflife---");
		System.out.println("\tinittree <graphdbfolder> (currently defaulting to NCBI branches)");
		System.out.println("\taddtree <filename> <focalgroup> <sourcename> <graphdbfolder> (add tree to graph of life)");
		System.out.println("\tjsgol <name> <graphdbfolder> (constructs a json file from a particular node)");
		System.out.println("\tfulltree <name> <preferred sources> <graphdbfolder> (constructs a newick file from a particular node)");
		System.exit(0);
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("treemachine version alpha.alpha.prealpha");
		if(args.length < 2){
			printHelp();
		}else if(args[0] == "help"){
			printHelp();
		}else{
			System.out.println("things will happen here");
			MainRunner mr = new MainRunner();
			if(args.length < 2){
				System.err.println("ERROR: not the right arguments");
				printHelp();
			}
			if(args[0].compareTo("inittax")==0 || args[0].compareTo("addtax")==0){
				mr.taxonomyLoadParser(args);
			}else if(args[0].compareTo("comptaxtree") == 0 || args[0].compareTo("findcycles")==0 || args[0].compareTo("jsgraph") == 0 || args[0].compareTo("checktree") == 0){
				mr.taxonomyQueryParser(args);
			}else if(args[0].compareTo("inittree") == 0 || args[0].compareTo("addtree")==0){
				mr.graphImporterParser(args);
			}else if(args[0].compareTo("jsgol") == 0 || args[0].compareTo("fulltree") == 0){
				mr.graphExplorerParser(args);
			}
		}
	}

}
