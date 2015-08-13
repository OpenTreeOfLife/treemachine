package opentree;

import gnu.trove.list.array.TLongArrayList;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import org.apache.commons.lang3.ArrayUtils;

import opentree.constants.NodeProperty;
import opentree.constants.RelProperty;
import opentree.constants.RelType;
import opentree.constants.SourceProperty;

import org.opentree.exceptions.TaxonNotFoundException;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;
import org.opentree.graphdb.GraphDatabaseAgent;

public class GraphInitializer extends GraphBase {

	//static Logger _LOG = Logger.getLogger(GraphImporter.class);

	private int transactionFrequency = 100000;
	private int cur_tran_iter = 0;
//	private JadeTree jt;
//	private String treestring; // original newick string for the jt
//	private ArrayList<Node> updatedNodes;
//	private HashSet<Node> updatedSuperLICAs;
	private Transaction	tx;
	//THIS IS FOR PERFORMANCE
//	private TLongArrayList root_ndids;
	boolean assumecomplete = false; // this will trigger getalllica if true (getbipart otherwise)
	
	// containers used during import
	private HashMap<String,ArrayList<ArrayList<String>>> synonymHash;
	private HashMap<String, Node> taxUIDToNodeMap;
	private HashMap<String, String> childNodeIDToParentNodeIDMap;
	private boolean synFileExists;
	private String [] dubiousFlags; // filter out taxa that contain any of these filters
	
	// dubious flags; change with taxonomy version //
	// used in 'version 3' and previous synthetic trees
	String[] ott28_exclude_flags = {"major_rank_conflict", "major_rank_conflict_direct", "major_rank_conflict_inherited", 
		"environmental", "unclassified_inherited", "unclassified_direct", "viral", "nootu", "barren", "not_otu", 
		"incertae_sedis", "incertae_sedis_direct", "incertae_sedis_inherited", "extinct_inherited", "extinct_direct", 
		"hidden", "unclassified"};
	// not sure if these are the correct flags. need documentation
	String[] ott29_exclude_flags = {"major_rank_conflict", "major_rank_conflict_inherited", "environmental",
		"unclassified_inherited", "unclassified", "viral", "barren", "not_otu", "incertae_sedis",
		"incertae_sedis_inherited", "extinct_inherited", "extinct", "hidden", "unplaced", "unplaced_inherited",
		"was_container", "inconsistent", "inconsistent"};
	
	public GraphInitializer(String graphname) {
		super(graphname);
	}

	public GraphInitializer(EmbeddedGraphDatabase eg) {
		super(eg);
	}
	
	public GraphInitializer(GraphDatabaseAgent gdb) {
		super(gdb);
	}
	
	public GraphInitializer(GraphDatabaseService gs) {
		super(gs);
	}

	private void initContainersForTaxLoading() {
		synonymHash = new HashMap<String,ArrayList<ArrayList<String>>>();
		taxUIDToNodeMap = new HashMap<String, Node>();
		childNodeIDToParentNodeIDMap = new HashMap<String, String>();
		synFileExists = false;
		//dubiousFlags = ott28_exclude_flags; // change this depending on taxonomy version
		dubiousFlags = ott29_exclude_flags; // change this depending on taxonomy version
	}
	
	/**
	 * Reads a taxonomy file with rows formatted as:
	 *  uid, parent_uid, name, rank, sourceinfo, uniqname, flags
	 * 
	 * The source name is going to be TAXONOMY
	 * 
	 * Creates the nodes and TAXCHILDOF relationship for a taxonomy tree
	 * Node objects will get a "name", "mrca", and "nested_mrca" properties
	 * 
	 * They will also get tax_uid, tax_parent_uid, tax_rank, tax_source, tax_sourceid, tax_sourcepid, uniqname
	 * 
	 * TAXCHILDOF relationships will get "source" of "ottol", "childid", and "parentid" properties
	 * with the addition of the new information in the ott dumps the nodes will also get properties
	 * 
	 * STREECHILDOF relationships will get "source" properties as "taxonomy"
	 * Nodes are indexed in graphNamedNodes with their name as the value for a "name" key
	 * 
	 * This will load the taxonomy, adding 
	 * 
	 * @param filename file path to the taxonomy file
	 * @param synonymfile file that has the synonyms as dumped by ott dump
	 * @throws TaxonNotFoundException 
	 */
	public void addInitialTaxonomyTableIntoGraph(String filename, String synonymfile, String taxonomyversion) throws TaxonNotFoundException {
		
		initContainersForTaxLoading();
		String str = "";
		int count = 0;

		if (synonymfile.length() > 0) {
			synFileExists = true;
		}
		// preprocess the synonym file
		// key is the id from the taxonomy, the array has the synonym and the type of synonym
		if (synFileExists) {
			synonymHash = new HashMap<String,ArrayList<ArrayList<String>>>();
			try {
				BufferedReader sbr = new BufferedReader(new FileReader(synonymfile));
				while ((str = sbr.readLine()) != null) {
					if (!str.trim().equals("")) {
						StringTokenizer st = new StringTokenizer(str,"\t|\t");
						String name = st.nextToken();
						// this is the id that points to the right node
						String parentuid = st.nextToken();
						String uid = parentuid;
						String type = "OTT synonym";
						String source = "OTT";
						ArrayList<String> tar = new ArrayList<String>();
						tar.add(uid); tar.add(name); tar.add(type); tar.add(source);
						if (synonymHash.get(parentuid) == null) {
							ArrayList<ArrayList<String> > ttar = new ArrayList<ArrayList<String> >();
							synonymHash.put(parentuid, ttar);
						}
						synonymHash.get(parentuid).add(tar);
					}
				}
				sbr.close();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
			System.out.println("synonyms: " + synonymHash.size());
		}
		// finished processing synonym file

		Transaction tx;
		ArrayList<String> templines = new ArrayList<String>();
		try {
			
			// for each line in input file
			BufferedReader br = new BufferedReader(new FileReader(filename));
			while ((str = br.readLine()) != null) {
				if (!str.trim().equals("")) {
					// check the first line to see if it the file has a header that we should skip
					if (count == 0) {
						if (str.startsWith("uid")) { // file contains a header. skip line
							System.out.println("Skipping taxonomy header line: " + str);
							continue;
						}
					}
					// collect sets of lines until we reach the transaction frequency
					count += 1;
					templines.add(str);
					
					// process lines in sets of N = transactionFrequency
					if (count % transactionFrequency == 0) {
						System.out.print("cur transaction: " + count);
						System.out.print("\n");
						tx = graphDb.beginTx();
						try {
							for (int i = 0; i < templines.size(); i++) {
								processTaxInputLine(templines.get(i), taxonomyversion); // replaced repeated code with function call
							}
							tx.success();
						} finally {
							tx.finish();
						}
						templines.clear();
					}
				}
			}
			br.close();

			// we hit the end of the file, so do a final transaction to process any remaining lines
			tx = graphDb.beginTx();
			try {
				for (int i = 0; i < templines.size(); i++) {
					processTaxInputLine(templines.get(i), taxonomyversion); // replaced repeated code with function call
				}
				tx.success();
			} finally {
				tx.finish();
			}
			templines.clear();
			
			// add the relationships
			ArrayList<String> temppar = new ArrayList<String>();
			count = 0;
			
			// for every taxon node that was added
			for (String key: taxUIDToNodeMap.keySet()) {
				
				// collect nodes until we reach the transaction frequency
				count += 1;
				temppar.add(key);
				
				// process nodes in sets of N = transactionFrequency
				if (count % transactionFrequency == 0) {
					System.out.println(count);
					tx = graphDb.beginTx();
					try {
						for (int i = 0; i < temppar.size(); i++) {
							try {
								addParentRelationshipForTaxUID(temppar.get(i)); // replaced repeated code with function call
							} catch(java.lang.IllegalArgumentException io) {
//								System.out.println(temppar.get(i));
								continue;
							}
						}
						tx.success();
					} finally {
						tx.finish();
					}
					temppar.clear();
				}
			}
			
			// we hit the end of the nodes list, so process all remaining nodes
			tx = graphDb.beginTx();
			try {
				for (int i = 0; i < temppar.size(); i++) {
					try {
						addParentRelationshipForTaxUID(temppar.get(i)); // replaced repeated code with function call
					} catch(java.lang.IllegalArgumentException io) {
//						System.out.println(temppar.get(i));
						continue;
					}
				}
				tx.success();
			} finally {
				tx.finish();
			}
			
		} catch(IOException ioe) { ioe.printStackTrace(); }

		// taxonomy structure is done. now add the MRCA_CHILDOF and STREE_CHILDOF relationships
		initMrcaAndStreeRelsTax();
	}
	
	private void addParentRelationshipForTaxUID(String taxUID) {
		Relationship rel = taxUIDToNodeMap.get(taxUID).createRelationshipTo(taxUIDToNodeMap.get(childNodeIDToParentNodeIDMap.get(taxUID)), RelType.TAXCHILDOF);
		rel.setProperty(RelProperty.CHILD_TAX_UID.propertyName, taxUID);
		rel.setProperty(RelProperty.PARENT_TAX_UID.propertyName, childNodeIDToParentNodeIDMap.get(taxUID));
		rel.setProperty(RelProperty.SOURCE.propertyName, "ottol");
	}
	
	/**
	 * Called during taxonomy loading to process each line of input from the taxonomy file
	 * @param line
	 */
	private void processTaxInputLine(String line, String taxonomyversion) {
		
		StringTokenizer st = new StringTokenizer(line, "|");
		String tid = null;
		String pid = null;
		String name = null;
		String rank = null;
		String srce = null;
		String uniqname = null;
		String flag = null; 
		
		try {
			tid = st.nextToken().trim();
			pid = st.nextToken().trim();
			name = st.nextToken().trim();
			rank = st.nextToken().trim();
			srce = st.nextToken().trim();
			uniqname = st.nextToken().trim();
			flag = st.nextToken().trim(); // for dubious
		} catch (NoSuchElementException ex) {
			throw new NoSuchElementException("the taxonomy file appears to be missing some fields.");
		}

		/**
		 * This is meant to ignore certain bad taxa based on a flagging system used in smasher
		 * The set of 'dubious' flags are stored in the array dubiousFlags, which itself is set
		 * in initContainersForTaxLoading(). This should make swapping flag sets easier.
		 */
		// the flag can be comma delimited
		StringTokenizer stfl = new StringTokenizer(flag, ",");
		int flagIndex = -1;
		while (stfl.hasMoreTokens()) {
			String tflag = stfl.nextToken();
			flagIndex = ArrayUtils.indexOf(dubiousFlags, tflag);
			if (flagIndex > -1) {
				System.out.println("skipping " + dubiousFlags[flagIndex] + " " + name);	
				return;
			}
			/*
			//if flag == D
			if (tflag.equals("major_rank_conflict")) {
				System.out.println("skipping major_rank_conflict "+name);	
				return;
			}
			if (tflag.equals("major_rank_conflict_direct")) {
				System.out.println("skipping major_rank_confict_direct "+name);	
				return;
			}
			if (tflag.equals("major_rank_conflict_inherited")) {
				System.out.println("skipping major_rank_conflict_inherited "+name);	
				return;
			}
			if (tflag.equals("environmental")) {
				System.out.println("skipping environmental "+name);	
				return;
			}
			if (tflag.equals("unclassified_inherited")) {
				System.out.println("skipping unclassified_inherited "+name);	
				return;
			}
			if (tflag.equals("unclassified_direct")) {
				System.out.println("skipping unclassified_direct "+name);	
				return;
			}
			if (tflag.equals("viral")) {
				System.out.println("skipping viral "+name);	
				return;
			}
			if (tflag.equals("nootu")) {
				System.out.println("skipping nootu "+name);	
				return;
			}
			if (tflag.equals("barren")) {
				System.out.println("skipping barren "+name);	
				return;
			}
			if (tflag.equals("not_otu")) {
				System.out.println("skipping not_otu "+name);	
				return;
			}
			if (tflag.equals("incertae_sedis")) {
				System.out.println("skipping incertae_sedis "+name);	
				return;
			}
			if (tflag.equals("incertae_sedis_direct")) {
				System.out.println("skipping incertae_sedis_direct "+name);	
				return;
			}
			if (tflag.equals("incertae_sedis_inherited")) {
				System.out.println("skipping incertae_sedis_inherited "+name);	
				return;
			}
			if (tflag.equals("extinct_inherited")) {
				System.out.println("skipping extinct_inherited "+name);	
				return;
			}
			if (tflag.equals("extinct_direct")) {
				System.out.println("skipping extinct_direct "+name);	
				return;
			}
			if (tflag.equals("hidden")) {
				System.out.println("skipping hidden "+name);	
				return;
			}
			if (tflag.equals("unclassified")) {
				System.out.println("skipping unclassified "+name);	
				return;
			}
	// was "tattered" turned off from filtering on purpose?!?
			if (tflag.equals("tattered")) {
				System.out.println("skipping tattered "+name);	
			//	return;
			}
            if (tflag.equals("tattered_inherited")) {
                System.out.println("skipping tattered_inherited "+name);
             //  return;
            }
            */
		}
		
		Node tnode = graphDb.createNode();
		tnode.setProperty(NodeProperty.NAME.propertyName, name);
		tnode.setProperty(NodeProperty.TAX_UID.propertyName, tid);
		tnode.setProperty(NodeProperty.TAX_PARENT_UID.propertyName, pid);
		tnode.setProperty(NodeProperty.TAX_RANK.propertyName, rank);
		tnode.setProperty(NodeProperty.TAX_SOURCE.propertyName, srce);
		tnode.setProperty(NodeProperty.NAME_UNIQUE.propertyName, uniqname.equals("") ? name : uniqname);
		
		// add index entries
		graphNodeIndex.add(tnode, NodeProperty.NAME.propertyName, name);
		graphTaxUIDNodeIndex.add(tnode, NodeProperty.TAX_UID.propertyName, tid);
		
		if (pid.length() > 0) {
			childNodeIDToParentNodeIDMap.put(tid, pid);
		
		} else { // root node
			
			// set taxonomy version here
			System.out.println("found root node: " + tnode.getProperty("name"));
			setGraphRootNode(tnode, taxonomyversion);
			
			// set the source metadata
			Node mdnode = graphDb.createNode();
			mdnode.createRelationshipTo(tnode, RelType.METADATAFOR);
			mdnode.setProperty(SourceProperty.SOURCE.propertyName, "taxonomy");
			sourceMetaIndex.add(mdnode, SourceProperty.SOURCE.propertyName, "taxonomy");
			System.err.println("Node " + mdnode.getId() + " holds METADATAFOR Node" + tnode.getId());								
		}
		
		// remember that we added this node
		taxUIDToNodeMap.put(tid, tnode);

		// synonym processing
		if (synFileExists) {
			if (synonymHash.get(tid) != null) {
				ArrayList<ArrayList<String>> syns = synonymHash.get(tid);
				for (int j = 0; j < syns.size(); j++) {
					String tax_uid = syns.get(j).get(0);
					String synName = syns.get(j).get(1);
					String synNameType = syns.get(j).get(2);
					String sourcename = syns.get(j).get(3);
					Node synode = graphDb.createNode();
					synode.setProperty(NodeProperty.NAME.propertyName,synName);
					synode.setProperty(NodeProperty.TAX_UID.propertyName, tax_uid);
					if (tax_uid.length() > 0) {
						synTaxUIDNodeIndex.add(tnode, NodeProperty.TAX_UID.propertyName, tid);
					}
					synode.setProperty(NodeProperty.NAMETYPE.propertyName, synNameType);
					synode.setProperty(NodeProperty.SOURCE.propertyName, sourcename);
					synode.createRelationshipTo(tnode, RelType.SYNONYMOF);
					synNodeIndex.add(tnode, NodeProperty.NAME.propertyName, synName);
				}
			}
		}		
	}
	
	/**
	 * Adds the MRCA_CHILDOF and STREE_CHILDOF relationships to an existing taxonomy.
	 * 
	 * Assumes the structure of the graphdb where the taxonomy is stored alongside the graph of life
	 * and therefore the graph is initialized, in this case, with the taxonomy relationships
	 * 
	 * for a more general implementation, could just go through and work on the preferred nodes
	 * 
	 * @throws TaxonNotFoundException 
	 */
	private void initMrcaAndStreeRelsTax() throws TaxonNotFoundException {
		Node startnode = getGraphRootNode();

		tx = graphDb.beginTx();
		try {
			// root should be the taxonomy startnode
//			_LOG.debug("startnode name = " + (String)startnode.getProperty("name"));
			TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
					.relationships(RelType.TAXCHILDOF,Direction.INCOMING);
			for (Node friendnode: CHILDOF_TRAVERSAL.traverse(startnode).nodes()) {
				Node taxparent = getAdjNodeFromFirstRelationshipBySource(friendnode, RelType.TAXCHILDOF, Direction.OUTGOING, "ottol");
				if (taxparent != null) {
					Node firstchild = getAdjNodeFromFirstRelationshipBySource(friendnode, RelType.TAXCHILDOF, Direction.INCOMING, "ottol");
					if (firstchild == null) {//leaf
						long [] tmrcas = {friendnode.getId()};
						friendnode.setProperty(NodeProperty.MRCA.propertyName, tmrcas);
						long [] ntmrcas = {};
						friendnode.setProperty(NodeProperty.NESTED_MRCA.propertyName, ntmrcas);
					}
					if (startnode != friendnode) {//not the root
						friendnode.createRelationshipTo(taxparent, RelType.MRCACHILDOF);
					}
					cur_tran_iter += 1;
					if (cur_tran_iter % transactionFrequency == 0) {
						tx.success();
						tx.finish();
						tx = graphDb.beginTx();
						System.out.println("cur transaction: " + cur_tran_iter);
					}
				} else {
					System.out.println(friendnode+"\t"+friendnode.getProperty(NodeProperty.NAME.propertyName));
				}
			}
			tx.success();
		} finally {
			tx.finish();
		}
		// start the mrcas
		System.out.println("calculating mrcas");
		try {
			tx = graphDb.beginTx();
			postorderAddMRCAsTax(startnode);
			tx.success();
		} finally {
			tx.finish();
		}
		//NOTE: outmrcas don't exist for taxchild of nodes because they are assumed to be the whole thing
	}
	
	/**
	 * for initial taxonomy to tree processing.  adds a mrca->long[]  property
	 *	to the node and its children (where the elements of the array are the ids
	 *	of graph of life nodes). The property is is the union of the mrca properties
	 *	for the subtree. So the leaves of the tree must already have their mrca property
	 *	filled in!
	 *
	 * @param dbnode should be a node in the graph-of-life (has incoming MRCACHILDOF relationship)
	 */
	private void postorderAddMRCAsTax(Node dbnode) {
		//traversal incoming and record all the names
		for (Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelType.MRCACHILDOF)) {
			Node tnode = rel.getStartNode();
			postorderAddMRCAsTax(tnode);
		}
		//could make this a hashset if dups become a problem
		TLongArrayList mrcas = new TLongArrayList();
		TLongArrayList nested_mrcas = new TLongArrayList();
		if (dbnode.hasProperty(NodeProperty.MRCA.propertyName) == false) {
			for (Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelType.MRCACHILDOF)) {
				Node tnode = rel.getStartNode();
				mrcas.addAll((long[]) tnode.getProperty(NodeProperty.MRCA.propertyName));
				nested_mrcas.addAll((long[]) tnode.getProperty(NodeProperty.NESTED_MRCA.propertyName));
			}
			mrcas.sort();
			dbnode.setProperty(NodeProperty.MRCA.propertyName, mrcas.toArray());
			nested_mrcas.sort();
			dbnode.setProperty(NodeProperty.NESTED_MRCA.propertyName, nested_mrcas.toArray());
		}
	}
}