package opentree;

import jade.tree.*;

import java.lang.StringBuffer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
//import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;

import opentree.TaxonNotFoundException;
import opentree.TreeIngestException;
//import opentree.GraphBase.RelTypes;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;
//import org.apache.log4j.Logger;

import scala.actors.threadpool.Arrays;

/**
 * GraphImporter is intended to control the initial creation 
 * and addition of trees to the tree graph.
 */


public class GraphImporter extends GraphBase{
	//static Logger _LOG = Logger.getLogger(GraphImporter.class);

	private int transaction_iter = 100000;
	private int cur_tran_iter = 0;
	private JadeTree jt;
	private String treestring; // original newick string for the jt
	private ArrayList<Node> updatedNodes;
	private HashSet<Node> updatedSuperLICAs;
	private Transaction	tx;
	
	/* TODO: Need to add metadata (if present) from jadetree coming from nexson.
	   See vocabulary: https://docs.google.com/spreadsheet/ccc?key=0ArYBkaCSQjOwdDE0MXlKMktvRFR1dllrTXQxQTY5V0E#gid=0
	   Fields at present:
		ot:studyPublicationReference - string: ot:studyPublicationReference "long string"
		ot:studyPublication - URI: ot:studyPublication <http://dx.doi.org/...>
		ot:curatorName - string: ot:curatorName "Jane Doe"
		ot:dataDeposit - string: ot:dataDeposit <http://purl.org/phylo/treebase/phylows/study/TB2:S1925>
		ot:studyId - string: ot:studyId "123"
		ot:ottolid - integer: ot:ottolid 783941
	*/
	
	public GraphImporter(String graphname) {
		graphDb = new GraphDatabaseAgent(graphname);
		this.initializeIndices();
	}

	public GraphImporter(EmbeddedGraphDatabase graphn) {
		graphDb = new GraphDatabaseAgent(graphn);
		this.initializeIndices();
	}
	
	public GraphImporter(GraphDatabaseAgent graphn) {
		graphDb = graphn;
		this.initializeIndices();
	}
	
	/**
	 * Helper function called by constructors so that we can update the list of indices in one place.
	 */
	private void initializeIndices() {
		graphNodeIndex = graphDb.getNodeIndex( "graphNamedNodes" ); // name is the key
		graphTaxUIDNodeIndex = graphDb.getNodeIndex( "graphTaxUIDNodes" ); // tax_uid is the key
		synTaxUIDNodeIndex = graphDb.getNodeIndex("synTaxUIDNodes");
		synNodeIndex = graphDb.getNodeIndex("graphNamedNodesSyns");
		sourceRelIndex = graphDb.getRelIndex("sourceRels");
		sourceRootIndex = graphDb.getNodeIndex("sourceRootNodes");
		sourceMetaIndex = graphDb.getNodeIndex("sourceMetaNodes");
	}

	public boolean hasSoureTreeName(String sourcename) {
		IndexHits<Node> hits = sourceRootIndex.get("rootnode", sourcename);
		return (hits != null && hits.size() > 0);
	}
	/**
	 * Sets the jt member by reading a JadeTree from filename.
	 *
	 * This currently reads a tree from a file but this will need to be changed to 
	 * another form later
	 * @param filename name of file with a newick tree representation
	 */
	public void preProcessTree(String filename, String treeID) {
		// read the tree from a file
		String ts = "";
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			ts = br.readLine();
			treestring = ts;
			br.close();
		} catch(IOException ioe) {}
		TreeReader tr = new TreeReader();
		jt = tr.readTree(ts);
		jt.assocObject("id", treeID);
		System.out.println("tree read");
		// System.exit(0);
	}
	
	/**
	 * Sets the jt member by a JadeTree already read and processed.
	 *
	 * @param JadeTree object
	 */
	public void setTree(JadeTree tree) {
		jt = tree;
		treestring = jt.getRoot().getNewick(true) + ";";
		System.out.println("tree set");
	}
	
	public void setTree(JadeTree tree, String ts) {
		jt = tree;
		treestring = ts;
		System.out.println("tree set");
	}
	
	/**
	 * Helper function that returns adjacent the node connected by the first
	 *		relationship with the source property equal to `src` 
	 * @param nd the focal node (serves as the source for all potential relationships
	 * @param relType the type of Relationship to check
	 * @param dir the direction of the relationship's connection to `nd`
	 * @param src the string that must match the `source` property
	 * @return adjacent node from the first relationship satisfying the criteria or null
	 * @todo could be moved to a more generic class (this has nothing to do with GraphImporter).
	 */
	static public Node getAdjNodeFromFirstRelationshipBySource(Node nd, RelationshipType relType, Direction dir,  String src) {
		for (Relationship rel: nd.getRelationships(relType, dir)) {
			if (((String)rel.getProperty("source")).equals(src)) {
				if (dir == Direction.OUTGOING) {
					return rel.getEndNode();
				} else {
					return rel.getStartNode();
				}
			}
		}
		return null;
	}
	
	/**
	 * Reads a taxonomy file with rows formatted as:
	 *	taxon_id,parent_id,Name with spaces allowed\n
	 * 
	 * The source name is going to be OTTOL
	 * 
	 * Creates the nodes and TAXCHILDOF relationship for a taxonomy tree
	 * Node objects will get a "name", "mrca", and "nested_mrca" properties
	 * 
	 * They will also get tax_uid, tax_parent_uid, tax_rank, tax_source, tax_sourceid, tax_sourcepid, uniqname
	 * 
	 * 
	 * TAXCHILDOF relationships will get "source" of "ottol", "childid", and "parentid" properties
	 * with the addition of the new information in the ottol dumps the nodes will also get properties
	 * 
	 * STREECHILDOF relationships will get "source" properties as "taxonomy"
	 * Nodes are indexed in graphNamedNodes with their name as the value for a "name" key
	 * 
	 * This will load the taxonomy, adding 
	 * 
	 * @param filename file path to the taxonomy file
	 * @param synonymfile file that has the synonyms as dumped by ottol dump
	 */
	public void addInitialTaxonomyTableIntoGraph(String filename, String synonymfile) {
		String str = "";
		int count = 0;
		HashMap<String,ArrayList<ArrayList<String>>> synonymhash = null;
		boolean synFileExists = false;
		if (synonymfile.length() > 0){
			synFileExists = true;
		}
		//preprocess the synonym file
		//key is the id from the taxonomy, the array has the synonym and the type of synonym
		if (synFileExists) {
			synonymhash = new HashMap<String,ArrayList<ArrayList<String>>>();
			try {
				BufferedReader sbr = new BufferedReader(new FileReader(synonymfile));
				while ((str = sbr.readLine()) != null) {
					StringTokenizer st = new StringTokenizer(str,"\t|\t");
					String uid = st.nextToken();
					//this is the id that points to the right node
					String parentuid = st.nextToken();
					String name = st.nextToken();
					String type = st.nextToken();
					String source = st.nextToken();
					ArrayList<String> tar = new ArrayList<String>();
					tar.add(uid);tar.add(name);tar.add(type);tar.add(source);
					if (synonymhash.get(parentuid) == null) {
						ArrayList<ArrayList<String> > ttar = new ArrayList<ArrayList<String> >();
						synonymhash.put(parentuid, ttar);
					}
					synonymhash.get(parentuid).add(tar);
				}
				sbr.close();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
			System.out.println("synonyms: " + synonymhash.size());
		}
		//finished processing synonym file


		HashMap<String, Node> dbnodes = new HashMap<String, Node>();
		HashMap<String, String> parents = new HashMap<String, String>();
		Transaction tx;
		ArrayList<String> templines = new ArrayList<String>();
		try {
			//create the root node
			//tx = graphDb.beginTx();
			/*try {
				Node node = graphDb.createNode();
				node.setProperty("name", "root");
				graphNodeIndex.add( node, "name", "root" );
				dbnodes.put("0", node);
				tx.success();
			}finally{
				tx.finish();
			}*/
			BufferedReader br = new BufferedReader(new FileReader(filename));
			while ((str = br.readLine())!=null) {
				count += 1;
				templines.add(str);
				if (count % transaction_iter == 0) {
					System.out.print(count);
					System.out.print("\n");
					tx = graphDb.beginTx();
					try {
						for (int i = 0; i < templines.size(); i++) {
							StringTokenizer st = new StringTokenizer(templines.get(i),"|");
							String tid = st.nextToken().trim();
							String pid = st.nextToken().trim();
							String name = st.nextToken().trim();
							String rank = st.nextToken().trim();
							String srce = st.nextToken().trim();
							String srce_id = st.nextToken().trim();
							String srce_pid = st.nextToken().trim();
							String uniqname = st.nextToken().trim();
							
							Node tnode = graphDb.createNode();
							tnode.setProperty("name", name);
							tnode.setProperty("tax_uid",tid);
							tnode.setProperty("tax_parent_uid",pid);
							tnode.setProperty("tax_rank",rank);
							tnode.setProperty("tax_source",srce);
							tnode.setProperty("tax_sourceid",srce_id);
							tnode.setProperty("tax_sourcepid",srce_pid);
							tnode.setProperty("uniqname",uniqname);
							graphNodeIndex.add( tnode, "name", name );
							if(tid.length()>0){
								graphTaxUIDNodeIndex.add(tnode, "tax_uid", tid);
							}
							if (pid.length() > 0) {
								parents.put(tid, pid);
							}
							dbnodes.put(tid, tnode);
							// synonym processing
							if (synFileExists) {
								if (synonymhash.get(tid) != null) {
									ArrayList<ArrayList<String>> syns = synonymhash.get(tid);
									for (int j=0; j < syns.size(); j++) {
										String tax_uid = syns.get(j).get(0);
										String synName = syns.get(j).get(1);
										String synNameType = syns.get(j).get(2);
										String sourcename = syns.get(j).get(3);
										Node synode = graphDb.createNode();
										synode.setProperty("name",synName);
										synode.setProperty("tax_uid", tax_uid);
										if(tax_uid.length()>0){
											synTaxUIDNodeIndex.add(tnode, "tax_uid", tid);
										}
										synode.setProperty("nametype",synNameType);
										synode.setProperty("source",sourcename);
										synode.createRelationshipTo(tnode, RelTypes.SYNONYMOF);
										synNodeIndex.add(tnode, "name", synName);
									}
								}
							}
						}
						tx.success();
					} finally {
						tx.finish();
					}
					templines.clear();
				}
			}
			br.close();
			tx = graphDb.beginTx();
			try {
				for (int i = 0; i < templines.size(); i++) {
					StringTokenizer st = new StringTokenizer(templines.get(i),"|");
					String tid = st.nextToken().trim();
					String pid = st.nextToken().trim();
					String name = st.nextToken().trim();
					String rank = st.nextToken().trim();
					String srce = st.nextToken().trim();
					String srce_id = st.nextToken().trim();
					String srce_pid = st.nextToken().trim();
					String uniqname = st.nextToken().trim();

					Node tnode = graphDb.createNode();
					tnode.setProperty("name", name);
					tnode.setProperty("tax_uid",tid);
					tnode.setProperty("tax_parent_uid",pid);
					tnode.setProperty("tax_rank",rank);
					tnode.setProperty("tax_source",srce);
					tnode.setProperty("tax_sourceid",srce_id);
					tnode.setProperty("tax_sourcepid",srce_pid);
					tnode.setProperty("uniqname",uniqname);
					graphNodeIndex.add( tnode, "name", name );
					if(tid.length()>0){
						graphTaxUIDNodeIndex.add(tnode, "tax_uid", tid);
					}
					if (pid.length() > 0) {
						parents.put(tid, pid);
					}
					dbnodes.put(tid, tnode);
					// synonym processing
					if (synFileExists) {
						if (synonymhash.get(tid) != null) {
							ArrayList<ArrayList<String>> syns = synonymhash.get(tid);
							for (int j=0; j < syns.size(); j++) {
								String tax_uid = syns.get(j).get(0);
								String synName = syns.get(j).get(1);
								String synNameType = syns.get(j).get(2);
								String sourcename = syns.get(j).get(3);
								Node synode = graphDb.createNode();
								synode.setProperty("name",synName);
								synode.setProperty("tax_uid", tax_uid);
								if(tax_uid.length()>0){
									synTaxUIDNodeIndex.add(tnode, "tax_uid", tid);
								}
								synode.setProperty("nametype",synNameType);
								synode.setProperty("source",sourcename);
								synode.createRelationshipTo(tnode, RelTypes.SYNONYMOF);
								synNodeIndex.add(tnode, "name", synName);
							}
						}
					}
				}
				tx.success();
			} finally {
				tx.finish();
			}
			templines.clear();
			//add the relationships
			ArrayList<String> temppar = new ArrayList<String>();
			count = 0;
			for (String key: dbnodes.keySet()) {
				count += 1;
				temppar.add(key);
				if (count % transaction_iter == 0) {
					System.out.println(count);
					tx = graphDb.beginTx();
					try {
						for (int i = 0; i < temppar.size(); i++) {
							try {
								Relationship rel = dbnodes.get(temppar.get(i)).createRelationshipTo(dbnodes.get(parents.get(temppar.get(i))), RelTypes.TAXCHILDOF);
								rel.setProperty("childid",temppar.get(i));
								rel.setProperty("parentid",parents.get(temppar.get(i)));
								rel.setProperty("source","ottol");
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
			tx = graphDb.beginTx();
			try {
				for (int i = 0; i < temppar.size(); i++) {
					try {
						Relationship rel = dbnodes.get(temppar.get(i)).createRelationshipTo(dbnodes.get(parents.get(temppar.get(i))), RelTypes.TAXCHILDOF);
						rel.setProperty("childid",temppar.get(i));
						rel.setProperty("parentid",parents.get(temppar.get(i)));
						rel.setProperty("source","ottol");
					} catch(java.lang.IllegalArgumentException io) {
//						System.out.println(temppar.get(i));
						continue;
					}
				}
				tx.success();
			} finally {
				tx.finish();
			}
		} catch(IOException ioe) {}
		initMrcaAndStreeRelsTax();
	}
	
	/**
	 * assumes the structure of the graphdb where the taxonomy is stored alongside the graph of life
	 * and therefore the graph is initialized, in this case, with the taxonomy relationships
	 * 
	 * for a more general implementation, could just go through and work on the preferred nodes
	 * 
	 * STREE
	 */
	private void initMrcaAndStreeRelsTax() {
		tx = graphDb.beginTx();
		//start from the node called root
		Node startnode = (graphNodeIndex.get("name", "life")).next();
		try {
			//root should be the taxonomy startnode
//			_LOG.debug("startnode name = " + (String)startnode.getProperty("name"));
			TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
					.relationships( RelTypes.TAXCHILDOF,Direction.INCOMING );
			for (Node friendnode: CHILDOF_TRAVERSAL.traverse(startnode).nodes()) {
				Node taxparent = getAdjNodeFromFirstRelationshipBySource(friendnode, RelTypes.TAXCHILDOF, Direction.OUTGOING, "ottol");
				if (taxparent != null) {
					Node firstchild = getAdjNodeFromFirstRelationshipBySource(friendnode, RelTypes.TAXCHILDOF, Direction.INCOMING, "ottol");
					if (firstchild == null) {//leaf
						long [] tmrcas = {friendnode.getId()};
						friendnode.setProperty("mrca", tmrcas);
						long [] ntmrcas = {};
						friendnode.setProperty("nested_mrca", ntmrcas);
					}
					if (startnode != friendnode) {//not the root
						friendnode.createRelationshipTo(taxparent, RelTypes.MRCACHILDOF);
						Relationship trel2 = friendnode.createRelationshipTo(taxparent, RelTypes.STREECHILDOF);
						trel2.setProperty("source", "taxonomy");
						sourceRelIndex.add(trel2, "source", "taxonomy");
					}
					cur_tran_iter += 1;
					if (cur_tran_iter % transaction_iter == 0) {
						tx.success();
						tx.finish();
						tx = graphDb.beginTx();
						System.out.println("cur transaction: "+cur_tran_iter);
					}
				} else {
					System.out.println(friendnode+"\t"+friendnode.getProperty("name"));
				}
			}
			tx.success();
		} finally {
			tx.finish();
		}
		//start the mrcas
		System.out.println("calculating mrcas");
		try {
			tx = graphDb.beginTx();
			postorderAddMRCAsTax(startnode);
			tx.success();
		} finally {
			tx.finish();
		}
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
		for (Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelTypes.MRCACHILDOF)) {
			Node tnode = rel.getStartNode();
			postorderAddMRCAsTax(tnode);
		}
		//could make this a hashset if dups become a problem
		ArrayList<Long> mrcas = new ArrayList<Long> ();
		ArrayList<Long> nested_mrcas = new ArrayList<Long>();
		if (dbnode.hasProperty("mrca") == false) {
			for (Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelTypes.MRCACHILDOF)) {
				Node tnode = rel.getStartNode();
				long[] tmrcas = (long[])tnode.getProperty("mrca");
				for (int j = 0; j < tmrcas.length; j++) {
					mrcas.add(tmrcas[j]);
				}
				long[] nestedtmrcas = (long[])tnode.getProperty("nested_mrca");
				for (int j = 0; j < nestedtmrcas.length; j++) {
					nested_mrcas.add(nestedtmrcas[j]);
				}
			}
			long[] ret = new long[mrcas.size()];
			for (int i = 0; i < ret.length; i++) {
				ret[i] = mrcas.get(i).longValue();
			}
			Arrays.sort(ret);
			dbnode.setProperty("mrca", ret);
			
			nested_mrcas.add(dbnode.getId());
			long[] ret2 = new long[nested_mrcas.size()];
			for (int i = 0; i < ret2.length; i++) {
				ret2[i] = nested_mrcas.get(i).longValue();
			}
			Arrays.sort(ret2);
			dbnode.setProperty("nested_mrca", ret2);
		}
	}

	/**
	 * Ingest the current JadeTree (in the jt data member) to the GoL.
	 *
	 * This will assume that the JadeNodes all have a property set as ot:ottolid
	 * 		that will be the preset ottol id identifier that will be found by index.
	 * 		ALL THE NAMES HAVE TO BE SET FOR THIS FUNCTION
	 *
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 */
	public void addSetTreeToGraphWIdsSet(String sourcename) throws TaxonNotFoundException,TreeIngestException {
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 1000);
		ArrayList<JadeNode> nds = jt.getRoot().getTips();

		/* TODO making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
		  would make the look ups faster. See comment in testIsMRCA */
		HashSet<Long> ndids = new HashSet<Long>(); 
		// We'll map each Jade node to the internal ID of its taxonomic node.
		HashMap<JadeNode,Long> hashnodeids = new HashMap<JadeNode,Long>();
		// same as above but added for nested nodes, so more comprehensive and 
		//		used just for searching. the others are used for storage
		HashSet<Long> ndidssearch = new HashSet<Long>();
		HashMap<JadeNode,ArrayList<Long>> hashnodeidssearch = new HashMap<JadeNode,ArrayList<Long>>();
		// this loop fills ndids and hashnodeids or throws an Exception (for 
		//		errors in matching leaves to the taxonomy). No other side effects.
		// TODO: this could be modified to account for internal node name mapping
		for (int j = 0; j < nds.size(); j++) {
			//use the internal node id to get the nodes mapped 
			Node hitnode = null;
			Long ottolid = (Long)nds.get(j).getObject("ot:ottolid");
			IndexHits<Node> hits = graphTaxUIDNodeIndex.get("tax_uid", ottolid);
			int numh = hits.size();
			if(numh == 0){
				throw new TaxonNotFoundException(String.valueOf(ottolid));
			}
			assert numh == 1;
			hitnode = hits.getSingle();
			hits.close();
			// added for nested nodes 
			long [] mrcas = (long[])hitnode.getProperty("mrca");
			ArrayList<Long> tset = new ArrayList<Long>(); 
			for (int k = 0; k < mrcas.length; k++) {
				ndidssearch.add(mrcas[k]);
				tset.add((Long)mrcas[k]);
			}
			hashnodeidssearch.put(nds.get(j), tset);
			ndids.add(hitnode.getId());
			hashnodeids.put(nds.get(j), hitnode.getId());
		}
		// Store the list of taxonomic IDs and the map of JadeNode to ID in the root.
		jt.getRoot().assocObject("ndids", ndids);
		jt.getRoot().assocObject("hashnodeids", hashnodeids);
		jt.getRoot().assocObject("ndidssearch", ndidssearch);
		jt.getRoot().assocObject("hashnodeidssearch", hashnodeidssearch);
		try {
			tx = graphDb.beginTx();
			postOrderAddProcessedTreeToGraph(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"));
			tx.success();
		} finally {
			tx.finish();
		}
	}
	
	/**
	 * Ingest the current JadeTree (in the jt data member) to the GoL.
	 *
	 * this should be done as a preorder traversal
	 *
	 * @param focalgroup a taxonomic name of the ancestor of the leaves in the tree
	 *		this is only used in disambiguating taxa when there are multiple hits 
	 *		for a leaf's taxonomic name
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @todo we probably want a node in the graph representing the tree with an 
	 *		ISROOTOF edge from its root to the tree. We could attach annotations
	 *		about the tree to this node. We have the index of the root node, but
	 *		need to having and isroot would also be helpful. Unless we are indexing
	 *		this we could just randomly choose one of the edges that is connected
	 *		to the root node that is in the index
	 */
	public void addSetTreeToGraph(String focalgroup, String sourcename) throws TaxonNotFoundException, TreeIngestException {
		Node focalnode = findTaxNodeByName(focalgroup);
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 1000);
		ArrayList<JadeNode> nds = jt.getRoot().getTips();

		// TODO: could take this out and make it a separate procedure
		/* TODO making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
		  would make the look ups faster. See comment in testIsMRCA */
		HashSet<Long> ndids = new HashSet<Long>(); 
		// We'll map each Jade node to the internal ID of its taxonomic node.
		HashMap<JadeNode,Long> hashnodeids = new HashMap<JadeNode,Long>();
		// same as above but added for nested nodes, so more comprehensive and 
		//		used just for searching. the others are used for storage
		HashSet<Long> ndidssearch = new HashSet<Long>();
		HashMap<JadeNode,ArrayList<Long>> hashnodeidssearch = new HashMap<JadeNode,ArrayList<Long>>();
		// this loop fills ndids and hashnodeids or throws an Exception (for 
		//		errors in matching leaves to the taxonomy). No other side effects.
		// TODO: this could be modified to account for internal node name mapping
		for (int j = 0; j < nds.size(); j++) {
			// find all the tip taxa and with doubles pick the taxon closest to the focal group
			Node hitnode = null;
			String processedname = nds.get(j).getName(); //.replace("_", " ");
			// TODO processing syntactic rules like '_' -> ' ' should be done on input parsing. 
			IndexHits<Node> hits = graphNodeIndex.get("name", processedname);
			int numh = hits.size();
			if (numh == 1) {
				hitnode = hits.getSingle();
			} else if (numh > 1) {
				System.out.println(processedname + " gets " + numh +" hits");
				int shortest = 1000; // this is shortest to the focal, could reverse this
				Node shortn = null;
				for (Node tnode : hits) {
					Path tpath = pf.findSinglePath(tnode, focalnode);
					if (tpath!= null) {
						if (shortn == null) {
							shortn = tnode;
						}
						if (tpath.length()<shortest) {
							shortest = tpath.length();
							shortn = tnode;
						}
//						System.out.println(shortest + " " + tpath.length());
					} else {
						System.out.println("one taxon is not within "+ focalgroup);
					}
				}
				assert shortn != null; // TODO this could happen if there are multiple hits outside the focalgroup, and none inside the focalgroup.  We should develop an AmbiguousTaxonException class
				hitnode = shortn;
			}
			hits.close();
			if (hitnode == null) {
				assert numh == 0;
				throw new TaxonNotFoundException(processedname);
			}
			// added for nested nodes 
			long [] mrcas = (long[])hitnode.getProperty("mrca");
			ArrayList<Long> tset = new ArrayList<Long>(); 
			for (int k = 0; k < mrcas.length; k++) {
				ndidssearch.add(mrcas[k]);
				tset.add((Long)mrcas[k]);
			}

			hashnodeidssearch.put(nds.get(j), tset);
			ndids.add(hitnode.getId());
			hashnodeids.put(nds.get(j), hitnode.getId());
		}
		// Store the list of taxonomic IDs and the map of JadeNode to ID in the root.
		jt.getRoot().assocObject("ndids", ndids);
		jt.getRoot().assocObject("hashnodeids", hashnodeids);
		jt.getRoot().assocObject("ndidssearch", ndidssearch);
		jt.getRoot().assocObject("hashnodeidssearch", hashnodeidssearch);
		try {
			tx = graphDb.beginTx();
			postOrderAddProcessedTreeToGraph(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"));
			tx.success();
		} finally {
			tx.finish();
		}
	}
	
	
	/**
	 * Finish ingest a tree into the GoL. This is called after the names in the tree
	 *	have been mapped to IDs for the nodes in the Taxonomy graph. The mappings are stored
	 *	as an object associated with the root node, as are the list of node ID's. 
	 *
	 * This will update the class member updatedNodes so they can be used for updating 
	 * existing relationships.
	 *
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @todo note that if a TreeIngestException the database will not have been reverted
	 *		back to its original state. At minimum at least some relationships
	 *		will have been created. It is also possible that some nodes will have
	 *		been created. We should probably add code to assure that we won't get
	 *		a TreeIngestException, or rollback the db modifications.
	 *		
	 */
	@SuppressWarnings("unchecked")
	private void postOrderAddProcessedTreeToGraph(JadeNode inode, JadeNode root, String sourcename, String treeID) throws TreeIngestException {
		// postorder traversal via recursion
		for (int i = 0; i < inode.getChildCount(); i++) {
			postOrderAddProcessedTreeToGraph(inode.getChild(i), root, sourcename, treeID);
		}
		//		_LOG.trace("children: "+inode.getChildCount());
		// roothash are the actual ids with the nested names -- used for storing
		// roothashsearch are the ids with nested exploded -- used for searching
		HashMap<JadeNode, Long> roothash = ((HashMap<JadeNode, Long>)root.getObject("hashnodeids"));
		HashMap<JadeNode, ArrayList<Long>> roothashsearch = ((HashMap<JadeNode, ArrayList<Long>>)root.getObject("hashnodeidssearch"));

		if (inode.getChildCount() > 0) {
			ArrayList<JadeNode> nds = inode.getTips();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			ArrayList<Node> hit_nodes_search = new ArrayList<Node> ();
			// store the hits for each of the nodes in the tips
			for (int j = 0; j < nds.size(); j++) {
				hit_nodes.add(graphDb.getNodeById(roothash.get(nds.get(j))));
				ArrayList<Long> tlist = roothashsearch.get(nds.get(j));
				for (int k = 0; k < tlist.size(); k++) {
					hit_nodes_search.add(graphDb.getNodeById(tlist.get(k)));
				}
			}
			// get all the childids even if they aren't in the tree, this is the postorder part
			HashSet<Long> childndids = new HashSet<Long> (); 
			
			for (int i = 0; i < inode.getChildCount(); i++) {
				Node [] dbnodesob = (Node [])inode.getChild(i).getObject("dbnodes"); 
				for (int k = 0; k < dbnodesob.length; k++) {
					long [] mrcas = ((long[])dbnodesob[k].getProperty("mrca"));
					for (int j = 0; j < mrcas.length; j++) {
						if (childndids.contains(mrcas[j]) == false) {
							childndids.add(mrcas[j]);
						}
					}
				}
			}
			//			_LOG.trace("finished names");
			HashSet<Long> rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndidssearch"));
			HashSet<Node> ancestors = LicaUtil.getAllLICA(hit_nodes_search, childndids, rootids);
			
			//			_LOG.trace("ancestor "+ancestor);
			// _LOG.trace(ancestor.getProperty("name"));
			if (ancestors.size() > 0) {
				inode.assocObject("dbnodes", ancestors.toArray(new Node[ancestors.size()]));
				long[] ret = new long[hit_nodes.size()];
				for (int i = 0; i < hit_nodes.size(); i++) {
					ret[i] = hit_nodes.get(i).getId();
				}
				rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndids"));
				long[] ret2 = new long[rootids.size()];
				Iterator<Long> chl2 = rootids.iterator();
				int i = 0;
				while (chl2.hasNext()) {
					ret2[i] = chl2.next().longValue();
					i++;
				}
				inode.assocObject("exclusive_mrca", ret);
				inode.assocObject("root_exclusive_mrca", ret2);
			} else {
				//				_LOG.trace("need to make a new node");
				// make a node
				// get the super lica, or what would be the licas if we didn't have the other taxa in the tree
				// this is used to connect the new nodes to their licas for easier traversals
				HashSet<Node> superlica = LicaUtil.getSuperLICA(hit_nodes_search, childndids);
				// steps
				// 1. create a node
				// 2. store the mrcas
				// 3. assoc with the node
				Node dbnode = graphDb.createNode();
				//					inode.assocObject("dbnode",dbnode);
				Node [] nar = {dbnode};
				inode.assocObject("dbnodes",nar);
				long[] ret = new long[childndids.size()];
				Iterator<Long> chl = childndids.iterator();
				int i = 0;
				while (chl.hasNext()) {
					ret[i] = chl.next().longValue();
					i++;
				}
				Arrays.sort(ret);
				dbnode.setProperty("mrca", ret);
				long[] rete = new long[hit_nodes.size()];
				for (int j = 0; j < hit_nodes.size(); j++) {
					rete[j] = hit_nodes.get(j).getId();
				}
				Arrays.sort(rete);
				inode.assocObject("exclusive_mrca",rete);
				rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndids"));
				long[] ret2 = new long[rootids.size()];
				Iterator<Long> chl2 = rootids.iterator();
				i = 0;
				while (chl2.hasNext()) {
					ret2[i] = chl2.next().longValue();
					i++;
				}
				Arrays.sort(ret2);
				inode.assocObject("root_exclusive_mrca",ret2);
				//	for (int i=0;i<inode.getChildCount();i++) {
				//		Relationship rel = ((Node)inode.getChild(i).getObject("dbnode")).createRelationshipTo(((Node)(inode.getObject("dbnode"))), RelTypes.MRCACHILDOF);
				//	}
				Iterator<Node> itrsl = superlica.iterator();
				while (itrsl.hasNext()) {
					Node itrnext = itrsl.next();
					dbnode.createRelationshipTo(itrnext, RelTypes.MRCACHILDOF);
					updatedSuperLICAs.add(itrnext);
				}
				tx.success();
				// add new nodes so they can be used for updating after tree ingest
				updatedNodes.add(dbnode);
			}
			addProcessedNodeRelationships(inode, sourcename, treeID);
		} else {
//			inode.assocObject("dbnode", graphDb.getNodeById(roothash.get(inode)));
			Node [] nar = {graphDb.getNodeById(roothash.get(inode))};
			inode.assocObject("dbnodes", nar);
		}
	}
	
	/**
	 * This should be called from within postOrderaddProcessedTreeToGraph
	 * to create relationships between nodes that have already been identified
	 * 
	 * Nothing postorder happens in this method, it is just to emphasize that
	 * it is called from within another method that is postorder
	 * 
	 * @param inode current focal node from postorderaddprocessedtreetograph 
	 * @param source source name for the tree
	 */
	private void addProcessedNodeRelationships(JadeNode inode, String sourcename, String treeID) throws TreeIngestException{
		// At this point the inode is guaranteed to be associated with a dbnode
		// add the actual branches for the source
		// Node currGoLNode = (Node)(inode.getObject("dbnode"));
		Node [] allGoLNodes = (Node [])(inode.getObject("dbnodes"));
		// for use if this node will be an incluchildof and we want to store the relationships for faster retrieval
		ArrayList<Relationship> inclusiverelationships = new ArrayList<Relationship>();
		for (int k = 0; k < allGoLNodes.length; k++) {
			Node currGoLNode = allGoLNodes[k];
			// add the root index for the source trail
			if (inode.isTheRoot()) {
				// TODO: this will need to be updated when trees are updated
				System.out.println("placing root in index");
				sourceRootIndex.add(currGoLNode, "rootnode", sourcename);
				if (treeID != null)
					sourceRootIndex.add(currGoLNode, "rootnodeForID", treeID);

				
				
				/* TODO: Need to add metadata (if present) from jadetree coming from nexson.
				   Fields at present:
					ot:studyPublicationReference - string: ot:studyPublicationReference "long string"
					ot:studyPublication - URI: ot:studyPublication <http://dx.doi.org/...>
					ot:curatorName - string: ot:curatorName "Jane Doe"
					ot:dataDeposit - string: ot:dataDeposit <http://purl.org/phylo/treebase/phylows/study/TB2:S1925>
					ot:studyId - string / integer ot:studyId "123"
					ot:ottolid - integer: ot:ottolid 783941
				*/
				
				// Note: setProperty throws IllegalArgumentException - if value is of an unsupported type (including null)
				
				Node metadatanode = null;
				metadatanode = graphDb.createNode();
				
		// first (ugly) go at this. do we want new (shorter) names (keys)?
		// if property does not exist, do we want 1) nothing, or 2) an empty property?
				if (jt.getObject("ot:studyPublicationReference") != null) {
					System.out.println("Adding property 'ot:studyPublicationReference' for tree " + sourcename + ": " + jt.getObject("ot:studyPublicationReference"));
					metadatanode.setProperty("ot:studyPublicationReference", jt.getObject("ot:studyPublicationReference"));
				} else {
					System.out.println("Property 'ot:studyPublicationReference' does not exist for tree " + sourcename);
				}
				if (jt.getObject("ot:studyPublication") != null) {
					System.out.println("Adding property 'ot:studyPublication' for tree " + sourcename + ": " + jt.getObject("ot:studyPublication"));
					metadatanode.setProperty("ot:studyPublication", jt.getObject("ot:studyPublication"));
				} else {
					System.out.println("Property 'ot:studyPublication' does not exist for tree " + sourcename);
				}
				if (jt.getObject("ot:curatorName") != null) {
					System.out.println("Adding property 'ot:curatorName' for tree " + sourcename + ": " + jt.getObject("ot:curatorName"));
					metadatanode.setProperty("ot:curatorName", jt.getObject("ot:curatorName"));
				} else {
					System.out.println("Property 'ot:curatorName' does not exist for tree " + sourcename);
				}
				if (jt.getObject("ot:dataDeposit") != null) {
					System.out.println("Adding property 'ot:dataDeposit' for tree " + sourcename + ": " + jt.getObject("ot:dataDeposit"));
					metadatanode.setProperty("ot:dataDeposit", jt.getObject("ot:dataDeposit"));
				} else {
					System.out.println("Property 'ot:dataDeposit' does not exist for tree " + sourcename);
				}
				if (jt.getObject("ot:studyId") != null) {
					System.out.println("Adding property 'ot:studyId' for tree " + sourcename + ": " + jt.getObject("ot:studyId"));
					metadatanode.setProperty("ot:studyId", jt.getObject("ot:studyId"));
				} else {
					System.out.println("Property 'ot:studyId' does not exist for tree " + sourcename);
				}
				
		// should studyID replace sourcename?
				metadatanode.setProperty("source", sourcename);
				//metadatanode.setProperty("author", "no one"); // seems deprecated now
				metadatanode.setProperty("newick", treestring); // this could be giant. do we want to do this?
				if (treeID != null)
					metadatanode.setProperty("treeID", treeID);
				sourceMetaIndex.add(metadatanode, "source", sourcename);
				//add the source taxa ids
				HashSet<Long> rootids = new HashSet<Long>((HashSet<Long>) jt.getRoot().getObject("ndids"));
				long[] ret2 = new long[rootids.size()];
				Iterator<Long> chl2 = rootids.iterator();
				int i = 0;
				while (chl2.hasNext()) {
					ret2[i] = chl2.next().longValue();
					i++;
				}
				Arrays.sort(ret2);
				metadatanode.setProperty("original_taxa_map",ret2);
				//end add source taxa ids
				// TODO: doesn't account for multiple root nodes
				metadatanode.createRelationshipTo(currGoLNode, RelTypes.METADATAFOR);
			}
			for (int i = 0; i < inode.getChildCount(); i++) {
				JadeNode childJadeNode = inode.getChild(i);
//				Node childGoLNode = (Node)childJadeNode.getObject("dbnode");
				Node [] allChildGoLNodes = (Node [])(childJadeNode.getObject("dbnodes"));
				for (int m = 0; m < allChildGoLNodes.length; m++) {
					Node childGoLNode = allChildGoLNodes[m];
					Relationship rel = childGoLNode.createRelationshipTo(currGoLNode, RelTypes.STREECHILDOF);
					sourceRelIndex.add(rel, "source", sourcename);
					rel.setProperty("exclusive_mrca", (long [])inode.getObject("exclusive_mrca"));
					rel.setProperty("root_exclusive_mrca", (long []) inode.getObject("root_exclusive_mrca"));
					long [] licaids = new long[allGoLNodes.length];
					for (int n = 0; n < licaids.length; n++) {
						licaids[n] = allGoLNodes[n].getId();
					}
					rel.setProperty("licas", licaids);
					inclusiverelationships.add(rel);

					// check to make sure the parent and child nodes are distinct entities...
					if (rel.getStartNode().getId() == rel.getEndNode().getId()) {
						StringBuffer errbuff = new StringBuffer();
						errbuff.append("A node and its child map to the same GoL node.\nTips:\n");
						for (int j = 0; j < inode.getTips().size(); j++) {
							errbuff.append(inode.getTips().get(j).getName() + "\n");
							errbuff.append("\n");
						}
						if (currGoLNode.hasProperty("name")) {
							errbuff.append(" ancestor taxonomic name: " + currGoLNode.getProperty("name"));
						}
						errbuff.append("\nThe tree has been partially imported into the db.\n");
						throw new TreeIngestException(errbuff.toString());
					}
					// METADATA ENTRY
					rel.setProperty("source", sourcename);
					// TODO this if will cause us to drop 0 length branches. We probably need a "has branch length" flag in JadeNode...
					if (childJadeNode.getBL() > 0.0) {
						rel.setProperty("branch_length", childJadeNode.getBL());
					}
					boolean mrca_rel = false;
					for (Relationship trel: childGoLNode.getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)) {
						if (trel.getOtherNode(childGoLNode).getId() == currGoLNode.getId()) {
							mrca_rel = true;
							break;
						}
					}
					if (mrca_rel == false) {
						Relationship rel2 = childGoLNode.createRelationshipTo(currGoLNode, RelTypes.MRCACHILDOF);
						// I'm not sure how this assert could ever trip, given that we create a 
						// childGoLNode -> currGoLNode relationship above and raise an exception
						// if the endpoints have the same ID.
						assert rel2.getStartNode().getId() != rel2.getEndNode().getId();
					}
				}
			}
		}
		long [] relids = new long[inclusiverelationships.size()];
		for (int n = 0; n < inclusiverelationships.size(); n++) {
			relids[n] = inclusiverelationships.get(n).getId();
		}
		for (int n = 0; n < inclusiverelationships.size(); n++) {
			inclusiverelationships.get(n).setProperty("inclusive_relids", relids);
		}

	}
	
	/**
	 * Takes a node in the GoL (dbnode) that is a parent of all of the nodes 
	 *	in the subtree rooted at `inode` This will return true if `dbnode` is
	 *  not an ancestor of any of the other leaves in the tree (which is rooted
	 *	at `root`).
	 * The idea here is to determine if `dbnode` would be a MRCA of the leaves
	 *		in `inode` if the GoL were pruned down to only contain the leaf set
	 *		of `root`
	 *
	 * @return true if the GoL Node `dbnode` qualifies as the MRCA node for
	 *		the leaves that descend from `inode`
	 * @param dbnode must be a common ancestor of all of the leaves that descend
	 *		from `inode`
	 */
	@SuppressWarnings("unused")
	private boolean testIsMRCA(Node dbnode,JadeNode root, JadeNode inode) {
		boolean ret = true;
		long [] dbnodei = (long []) dbnode.getProperty("mrca");
		@SuppressWarnings("unchecked")
		ArrayList<Long> rootids = new ArrayList<Long>((HashSet<Long>) root.getObject("ndids"));
		@SuppressWarnings("unchecked")
		ArrayList<Long> inodeids =(ArrayList<Long>) inode.getObject("ndids");
//		System.out.println(rootids.size());
		rootids.removeAll(inodeids);
//		System.out.println(rootids.size());
		for (int i = 0; i < dbnodei.length; i++) {
			if (rootids.contains(dbnodei[i])) { //@todo this is the Order(N) lookup that could be log(N) or constant time if ndids was not an ArrayList<Long>
				ret = false;
				break;
			}
		}
//		System.out.println("testisMRCA "+ret);
		return ret;
	}
	
	public void deleteAllTrees() {
		IndexHits<Node> hits  = sourceMetaIndex.query("source", "*");
		System.out.println(hits.size());
		for (Node itrel: hits) {
			String source = (String)itrel.getProperty("source");
			deleteTreeBySource(source);
		}
	}
	
	/**
	 * TODO: update this for the new method
	 */
	public void deleteAllTreesAndReprocess() {
		IndexHits<Node> hits  = sourceMetaIndex.query("source", "*");
		System.out.println(hits.size());
		for (Node itrel: hits) {
			String source = (String)itrel.getProperty("source");
			String trees = (String)itrel.getProperty("newick");
			String treeID = (String)itrel.getProperty("treeID");
			deleteTreeBySource(source);
			TreeReader tr = new TreeReader();
			jt = tr.readTree(trees);
			jt.assocObject("id", treeID);
			System.out.println("tree read");
			setTree(jt,trees);
			try {
				addSetTreeToGraph("life",source);
			} catch (TaxonNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TreeIngestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public void deleteTreeBySource(String source) {
		System.out.println("deleting tree: " + source);
		IndexHits <Relationship> hits = sourceRelIndex.get("source", source);
		Transaction	tx = graphDb.beginTx();
		try {
//			Iterator<Relationship> itrel = tobedeleted.iterator();
			for (Relationship itrel : hits) {
				itrel.delete();
				sourceRelIndex.remove(itrel, "source", source);
			}
			tx.success();
		} finally {
			tx.finish();
		}
		hits.close();
		IndexHits <Node> shits = sourceRootIndex.get("rootnode", source);
		tx = graphDb.beginTx();
		try {
			for (Node itrel: shits) {
				sourceRootIndex.remove(itrel, "rootnode", source);
			}
			tx.success();
		} finally {
			tx.finish();
		}
		shits.close();
		shits = sourceMetaIndex.get("source", source);
		tx = graphDb.beginTx();
		try {
			for (Node itrel : shits) {
				sourceMetaIndex.remove(itrel, "source", source);
				itrel.getRelationships(RelTypes.METADATAFOR).iterator().next().delete();
				itrel.delete();
			}
			tx.success();
		} finally {
			tx.finish();
		}
	}
	
	/**
	 * Looks at the class member updatedNodes to determine if there are any relationships
	 * that need to be updated. 
	 * 
	 * Right now goes like this 
	 * 1) look at the updated super licas to see if there are relationships incoming that are inclusive (not exact)
	 * 2) check the mrcas with the ones from the updated nodes to see if any of the updated ones are better or equally good
	 * 3) if so, delete (if better) the old ones and add with connections to the new node(s)
	 * 
	 * This needs to be more comprehensive so that it checks the other relationships for inclusive ones
	 */
/*
 * This might be better done if we indexed the new updated node
 */
	public void updateAfterTreeIngest(boolean comprehensive) {
		ArrayList<Relationship> krels = new ArrayList<Relationship>();
		//seems like this doesn't find all the nodes
		if (comprehensive == false) {
			for (Node tnext: updatedSuperLICAs) {
//				System.out.println(tnext);
				for (Relationship rel: tnext.getRelationships(Direction.INCOMING, RelTypes.STREECHILDOF)) {
//					System.out.print(rel+"\n");
					if (((String)rel.getProperty("source")).compareTo("taxonomy") == 0) {
						continue;
					}
					boolean add = true;
					long [] incl = (long [])rel.getProperty("inclusive_relids");
//					for (int i = 0; i < incl.length; i++) {
//						System.out.print(incl[i]+" ");
//					}
//					System.out.print("\n");

					for (int i = 0; i < incl.length; i++) {
//						System.out.print(incl[i] + " ");
						if (krels.contains(graphDb.getRelationshipById(incl[i]))) {
							add = false;
							break;
						}
					}
//					System.out.print("\n");
					if (add == true) {
						krels.add(rel);
					}
				}
			}
//			System.out.println("krels: "+krels);
			//here is the comprehensive searcher
		} else if (comprehensive == true ) {
			TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
					.relationships( RelTypes.MRCACHILDOF,Direction.INCOMING );
			Node startnode = (graphNodeIndex.get("name", "life")).next();
			for (Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(startnode).nodes()) {
				for (Relationship rel: friendnode.getRelationships(Direction.INCOMING, RelTypes.STREECHILDOF)) {
//					System.out.print(rel+"\n");
					if (((String)rel.getProperty("source")).compareTo("taxonomy") == 0) {
						continue;
					}
					boolean add = true;
					long [] incl = (long [])rel.getProperty("inclusive_relids");
//					for (int i = 0; i < incl.length; i++) {
//						System.out.print(incl[i] + " ");
//					}
//					System.out.print("\n");

					for (int i = 0; i < incl.length; i++) {
//						System.out.print(incl[i] + " ");
						if (krels.contains(graphDb.getRelationshipById(incl[i]))) {
							add = false;
							break;
						}
					}
//					System.out.print("\n");

					if (add == true) {
						krels.add(rel);
					}
				}
			}
		}
		//these are relationships that will be deleted at the end but can't 
		//yet because of null pointers here. They arise often because they are
		//parents of some of these other nodes that get fixed
		HashSet<Relationship> tobedeleted = new HashSet<Relationship>();
		//going through the potential problem nodes and updating
		for (int i = 0; i < krels.size(); i++) {
			if (tobedeleted.contains(krels.get(i))) {
				continue;
			}
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			//HashSet<Long> childndids = new HashSet<Long>();
			HashSet<Long> rootidsearch = new HashSet<Long>();
			//same as above but added for nested nodes, so more comprehensive and 
			//		used just for searching. the others are used for storage
			HashSet<Long> ndidssearch = new HashSet<Long>();
			long [] exmrcas = (long [])krels.get(i).getProperty("exclusive_mrca");
			long [] rtexmrcas = (long [])krels.get(i).getProperty("root_exclusive_mrca");
			for (int j = 0; j < exmrcas.length; j++) {
				Node tnode = graphDb.getNodeById(exmrcas[j]);
				hit_nodes.add(tnode);
				//added for nested nodes
				long [] mrcas = (long[])tnode.getProperty("mrca");
				for (int k = 0; k < mrcas.length; k++) {
					ndidssearch.add(mrcas[k]);
				}
				//childndids.add(exmrcas[j]);
			}
			for (int j = 0; j < rtexmrcas.length; j++) {
				//added for nested nodes
				Node tnode = graphDb.getNodeById(rtexmrcas[j]);
				long [] mrcas = (long[])tnode.getProperty("mrca");
				for (int k = 0; k < mrcas.length; k++) {
					rootidsearch.add(mrcas[k]);
				}
				//rootidsearch.add(rtexmrcas[j]);
			}
			HashSet<Node> ancestors = LicaUtil.getAllLICA(hit_nodes, ndidssearch, rootidsearch);
			long [] licas = (long[])krels.get(i).getProperty("licas");
			long [] relids = (long [])krels.get(i).getProperty("inclusive_relids");
			String source = (String)krels.get(i).getProperty("source");
			HashSet<Node> startNodes = new HashSet<Node>();
//			System.out.print("relids (" + krels.get(i) + "):");
			for (int j = 0; j < relids.length; j++) {
//				System.out.print(" " + relids[j]);
				startNodes.add(graphDb.getRelationshipById(relids[j]).getStartNode());
			}
//			System.out.print("\n");
//			System.out.println(startNodes);
			//these are the nodes that are parents of the current ancestors
			//if the children change, these need to be updated as well
			HashSet<Node> parentnodes = new HashSet<Node>();
			HashMap<Node,HashSet<Relationship>> parent_rel_map = new HashMap<Node, HashSet<Relationship>>();
			//TODO: update the rootnode index if this changes
			//this should hold, and for now, just remake the relationships
			if (licas.length != ancestors.size()) {
				//because the original lica length is different than the ancestors length
				//we need to connect the nodes
				for (int j = 0; j < relids.length; j++) {
					System.out.println("deleting rel: " + relids[j]);
					Transaction	tx = graphDb.beginTx();
					try {
						Relationship trel = graphDb.getRelationshipById(relids[j]);
						sourceRelIndex.remove(trel, "source", source);
						System.out.println(trel.getStartNode() + " " + trel.getEndNode());
						//if there aren't relationships then we need to change the root node
						for (Relationship ptrel: trel.getEndNode().getRelationships(Direction.OUTGOING)) {
							if (ptrel.hasProperty("source")) {
								if (((String)ptrel.getProperty("source")).compareTo(source) == 0) {
									System.out.println(ptrel + " "+ptrel.getStartNode() + "-" + ptrel.getEndNode());
									parentnodes.add(ptrel.getEndNode());
									if (parent_rel_map.containsKey(ptrel.getEndNode()) == false) {
										parent_rel_map.put(ptrel.getEndNode(), new HashSet<Relationship>());
									}
									parent_rel_map.get(ptrel.getEndNode()).add(ptrel);
									tobedeleted.add(ptrel);
								}
							}
						}
						tobedeleted.add(trel);
						//trel.delete();
						tx.success();
					} finally {
						tx.finish();
					}
				}
				Transaction	tx = graphDb.beginTx();
				try {
					//now for each of the new licas
					long [] anlicas = new long[ancestors.size()];
					int j = 0;
					for (Node tancn: ancestors) {
						anlicas[j] = tancn.getId();
						j++;
					}
					ArrayList<Relationship> finrels = new ArrayList<Relationship> ();
					for (Node ancitn: ancestors) {
						for (Node snitn: startNodes) {
							Relationship rel = snitn.createRelationshipTo(ancitn, RelTypes.STREECHILDOF);
							sourceRelIndex.add(rel, "source", source);
							System.out.println("adding rel: "+rel.getId());
							rel.setProperty("source", source);
							rel.setProperty("licas", anlicas);
							rel.setProperty("exclusive_mrca", exmrcas);
							rel.setProperty("root_exclusive_mrca", rtexmrcas);
							finrels.add(rel);
						}
					}
					long [] relidsfin = new long[finrels.size()];
					for (j = 0; j < finrels.size(); j++) {
						relidsfin[j] = finrels.get(j).getId();
					}
					for (j = 0; j < finrels.size(); j++) {
						finrels.get(j).setProperty("inclusive_relids",relidsfin);
					}

					//connect the new ancestors to the original parents
//					Iterator<Node> pnodes = parentnodes.iterator();
//					ancit = ancestors.iterator();
					for (Node pnode: parentnodes) {
//					while (pnodes.hasNext()) {
//						Node pnode = pnodes.next();
						HashSet<Relationship> oldones = parent_rel_map.get(pnode);
						//this is going to need to be in some sort of array
						System.out.println("oldones: "+oldones);
						Relationship toldone = oldones.iterator().next();
						long [] tlicas = (long [])toldone.getProperty("licas");
						long [] texmrcas = (long [])toldone.getProperty("exclusive_mrca");
						long [] trtexmrcas = (long [])toldone.getProperty("root_exclusive_mrca");
						ArrayList<Relationship> new_rels = new ArrayList<Relationship>();
						long [] oldrelids = (long [])toldone.getProperty("inclusive_relids");
						ArrayList<Long> newrelids = new ArrayList<Long>();
						for (j = 0; j < oldrelids.length; j++) {System.out.println("adding original rel: " + oldrelids[j]);newrelids.add(oldrelids[j]);}
						/*for (Relationship toldrel: oldones) {
							long oldid = toldrel.getId();
							System.out.println("deleting the old bad rel: "+oldid);
							newrelids.remove(oldid);
						}*/
						//change to just eliminating all the to be deleted ones
						for (Relationship toldrel: tobedeleted) {
							long oldid = toldrel.getId();
							System.out.println("deleting the old bad rel: "+oldid);
							newrelids.remove(oldid);	
						}
						for (Node anc: ancestors) {
//						while (ancit.hasNext()) {
//							Node anc = ancit.next();
							//first make sure that there is a MRCACHILDOF for these nodes
							boolean present = false;
							for (Relationship rels: pnode.getRelationships(Direction.INCOMING, RelTypes.MRCACHILDOF)) {
								if (rels.getStartNode() == anc) {
									present = true;
									break;
								}
							}
							if (present == false) {
								anc.createRelationshipTo(pnode, RelTypes.MRCACHILDOF);
							}
							Relationship newrel = anc.createRelationshipTo(pnode, RelTypes.STREECHILDOF);
							sourceRelIndex.add(newrel, "source", source);
							newrel.setProperty("source", source);
							newrel.setProperty("licas", tlicas);
							newrel.setProperty("exclusive_mrca",texmrcas);
							newrel.setProperty("root_exclusive_mrca",trtexmrcas);
							new_rels.add(newrel);
							newrelids.add(newrel.getId());
						}
						long [] finalnewrelids = new long[newrelids.size()];
						System.out.print("newrelids: ");
						for (j = 0; j < finalnewrelids.length; j++) {
							System.out.print(" "+newrelids.get(j));
							finalnewrelids[j] = newrelids.get(j);
						}
						System.out.print("\n");
						//this is where the new rel id is not getting fixed
						for (Relationship rels: pnode.getRelationships(Direction.INCOMING, RelTypes.STREECHILDOF)) {
							if (((String)rels.getProperty("source")).compareTo(source) == 0) {
								System.out.println("setting new inclusive relids: " + rels);
								rels.setProperty("inclusive_relids", finalnewrelids);
							}
						}
					}
					tx.success();
				} finally {
					tx.finish();
				}
			}
		}
		//cleaning up parent relationships that have to be deleted
		Transaction	tx = graphDb.beginTx();
		try {
//			Iterator<Relationship> itrel = tobedeleted.iterator();
			for (Relationship itrel: tobedeleted) {
				System.out.println("actually deleting: "+itrel);
//			while (itrel.hasNext()) {
				try {
					itrel.delete();
//					itrel.next().delete();
				} catch(org.neo4j.graphdb.NotFoundException f) {
					System.out.println("seems " + f.getMessage());
				}
			}
			tx.success();
		} finally {
			tx.finish();
		}
	}
	
	/*
	 * can't seem to find the full error in the updateAfterTreeIngest procedure so 
	 * just trying a full remap of any trees that need it instead of the localized remap
	 */
	private void updateAfterTreeIngestRemapTree() {
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Something!");
	}
}
