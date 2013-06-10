package opentree;

import gnu.trove.list.array.TLongArrayList;

import jade.tree.JadeTree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

public class GraphInitializer extends GraphBase{

	//static Logger _LOG = Logger.getLogger(GraphImporter.class);

	private int transaction_iter = 100000;
	private int cur_tran_iter = 0;
	private JadeTree jt;
	private String treestring; // original newick string for the jt
	private ArrayList<Node> updatedNodes;
	private HashSet<Node> updatedSuperLICAs;
	private Transaction	tx;
	//THIS IS FOR PERFORMANCE
	private TLongArrayList root_ndids;
	boolean assumecomplete = false;//this will trigger getalllica if true (getbipart otherwise)
	
	public GraphInitializer(String graphname) {
		graphDb = new GraphDatabaseAgent(graphname);
		this.initializeIndices();
	}

	public GraphInitializer(EmbeddedGraphDatabase graphn) {
		graphDb = new GraphDatabaseAgent(graphn);
		this.initializeIndices();
	}
	
	public GraphInitializer(GraphDatabaseAgent graphn) {
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
		if (synonymfile.length() > 0) {
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
					String name = st.nextToken();
					//this is the id that points to the right node
					String parentuid = st.nextToken();
					String uid = parentuid;
					String type = "OTT synonym";//st.nextToken();
					String source = "OTT";//st.nextToken();
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
							//String srce_id = st.nextToken().trim();
							//String srce_pid = st.nextToken().trim();
							String uniqname = st.nextToken().trim();
							
							Node tnode = graphDb.createNode();
							tnode.setProperty("name", name);
							tnode.setProperty("tax_uid",tid);
							tnode.setProperty("tax_parent_uid",pid);
							tnode.setProperty("tax_rank",rank);
							tnode.setProperty("tax_source",srce);
							//tnode.setProperty("tax_sourceid",srce_id);
							//tnode.setProperty("tax_sourcepid",srce_pid);
							tnode.setProperty("uniqname",uniqname);
							graphNodeIndex.add( tnode, "name", name );
							graphTaxUIDNodeIndex.add(tnode, "tax_uid", tid);
							if (pid.length() > 0) {
								parents.put(tid, pid);
							}else{//root node
								Node mdnode = graphDb.createNode();
								mdnode.createRelationshipTo(tnode, RelTypes.METADATAFOR);
								sourceMetaIndex.add(mdnode, "source", "taxonomy");
								System.err.println("Node " + mdnode.getId() + " holds METADATAFOR Node" + tnode.getId());
							}
							dbnodes.put(tid, tnode);
							// synonym processing
							if (synFileExists) {
								if (synonymhash.get(tid) != null) {
									ArrayList<ArrayList<String>> syns = synonymhash.get(tid);
									for (int j = 0; j < syns.size(); j++) {
										String tax_uid = syns.get(j).get(0);
										String synName = syns.get(j).get(1);
										String synNameType = syns.get(j).get(2);
										String sourcename = syns.get(j).get(3);
										Node synode = graphDb.createNode();
										synode.setProperty("name",synName);
										synode.setProperty("tax_uid", tax_uid);
										if (tax_uid.length() > 0) {
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
					//String srce_id = st.nextToken().trim();
					//String srce_pid = st.nextToken().trim();
					String uniqname = st.nextToken().trim();

					Node tnode = graphDb.createNode();
					tnode.setProperty("name", name);
					tnode.setProperty("tax_uid",tid);
					tnode.setProperty("tax_parent_uid",pid);
					tnode.setProperty("tax_rank",rank);
					tnode.setProperty("tax_source",srce);
					//tnode.setProperty("tax_sourceid",srce_id);
					//tnode.setProperty("tax_sourcepid",srce_pid);
					tnode.setProperty("uniqname",uniqname);
					graphNodeIndex.add( tnode, "name", name );
					graphTaxUIDNodeIndex.add(tnode, "tax_uid", tid);
					if (pid.length() > 0) {
						parents.put(tid, pid);
					}else{//root node
						Node mdnode = graphDb.createNode();
						mdnode.createRelationshipTo(tnode, RelTypes.METADATAFOR);
						sourceMetaIndex.add(mdnode, "source", "taxonomy");
						System.err.println("Node " + mdnode.getId() + " holds METADATAFOR Node" + tnode.getId());
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
								if (tax_uid.length()>0) {
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
		//NOTE: outmrcas don't exist for taxchild of nodes because they are assumed to be the whoel thing
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
		TLongArrayList mrcas = new TLongArrayList();
		TLongArrayList nested_mrcas = new TLongArrayList();
		if (dbnode.hasProperty("mrca") == false) {
			for (Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelTypes.MRCACHILDOF)) {
				Node tnode = rel.getStartNode();
				mrcas.addAll((long[])tnode.getProperty("mrca"));
				nested_mrcas.addAll((long[])tnode.getProperty("nested_mrca"));
			}
			mrcas.sort();
			dbnode.setProperty("mrca", mrcas.toArray());
			nested_mrcas.sort();
			dbnode.setProperty("nested_mrca", nested_mrcas.toArray());
		}
	}
	
}
