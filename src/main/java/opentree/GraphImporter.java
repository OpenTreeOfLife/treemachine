package opentree;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
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
//import opentree.RelTypes;

import org.apache.commons.lang3.ArrayUtils;
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
	//THIS IS FOR PERFORMANCE
	private TLongArrayList root_ndids;
	boolean assumecomplete = false;//this will trigger getalllica if true (getbipart otherwise)
	
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
	 * Ingest the current JadeTree (in the jt data member) to the GoL.
	 *
	 * This will assume that the JadeNodes all have a property set as ot:ottolid
	 * 		that will be the preset ottol id identifier that will be found by index.
	 * 		ALL THE NAMES HAVE TO BE SET FOR THIS FUNCTION
	 *
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @param test don't add to the database
	 */
	public void addSetTreeToGraphWIdsSet(String sourcename,boolean taxacompletelyoverlap, boolean test) throws TaxonNotFoundException,TreeIngestException {
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();
		assumecomplete = taxacompletelyoverlap;
		ArrayList<JadeNode> nds = jt.getRoot().getTips();

		/* TODO making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
		  would make the look ups faster. See comment in testIsMRCA */
		TLongArrayList ndids = new TLongArrayList(); 
		// We'll map each Jade node to the internal ID of its taxonomic node.
		HashMap<JadeNode,Long> hashnodeids = new HashMap<JadeNode,Long>();
		// same as above but added for nested nodes, so more comprehensive and 
		//		used just for searching. the others are used for storage
		//HashSet<Long> ndidssearch = new HashSet<Long>();
		TLongArrayList ndidssearch = new TLongArrayList();
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
			if (numh == 0) {
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
		//jt.getRoot().assocObject("ndids", ndids);
		jt.getRoot().assocObject("hashnodeids", hashnodeids);
		ndidssearch.sort();
		jt.getRoot().assocObject("ndidssearch", ndidssearch);
		jt.getRoot().assocObject("hashnodeidssearch", hashnodeidssearch);
		ndids.sort();
		root_ndids = ndids;
		try {
			tx = graphDb.beginTx();
			if(test == false)
				postOrderAddProcessedTreeToGraph(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"));
			else
				postOrderAddProcessedTreeToGraphNoAdd(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"));
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
	public void addSetTreeToGraph(String focalgroup, String sourcename,boolean taxacompletelyoverlap) throws TaxonNotFoundException, TreeIngestException {
		boolean test = false;
		Node focalnode = findTaxNodeByName(focalgroup);
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();
		assumecomplete = taxacompletelyoverlap;
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 1000);
		ArrayList<JadeNode> nds = jt.getRoot().getTips();

		// TODO: could take this out and make it a separate procedure
		/* TODO making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
		  would make the look ups faster. See comment in testIsMRCA */
		TLongArrayList ndids = new TLongArrayList(); 
		// We'll map each Jade node to the internal ID of its taxonomic node.
		HashMap<JadeNode,Long> hashnodeids = new HashMap<JadeNode,Long>();
		// same as above but added for nested nodes, so more comprehensive and 
		//		used just for searching. the others are used for storage
		//HashSet<Long> ndidssearch = new HashSet<Long>();
		TLongArrayList ndidssearch = new TLongArrayList();
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
					if (tpath != null) {
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
		//jt.getRoot().assocObject("ndids", ndids);
		jt.getRoot().assocObject("hashnodeids", hashnodeids);
		ndidssearch.sort();
		jt.getRoot().assocObject("ndidssearch", ndidssearch);
		jt.getRoot().assocObject("hashnodeidssearch", hashnodeidssearch);
		ndids.sort();
		root_ndids = ndids;
		try {
			tx = graphDb.beginTx();
			if(test == false)
				postOrderAddProcessedTreeToGraph(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"));
			else
				postOrderAddProcessedTreeToGraphNoAdd(jt.getRoot(), jt.getRoot(), sourcename, (String)jt.getObject("id"));
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
	 * @param test don't add the tree to the database, just run through as though you would add it
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
//			System.out.println(inode.getNewick(false));
			ArrayList<JadeNode> nds = inode.getTips();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			ArrayList<Node> hit_nodes_search = new ArrayList<Node> ();
			TLongArrayList hit_nodes_small_search = new TLongArrayList ();
			// store the hits for each of the nodes in the tips
			for (int j = 0; j < nds.size(); j++) {
				hit_nodes.add(graphDb.getNodeById(roothash.get(nds.get(j))));
				ArrayList<Long> tlist = roothashsearch.get(nds.get(j));
				hit_nodes_small_search.addAll(tlist);
				for (int k = 0; k < tlist.size(); k++) {
					hit_nodes_search.add(graphDb.getNodeById(tlist.get(k)));
				}
			}
			hit_nodes_small_search.sort();
			// get all the childids even if they aren't in the tree, this is the postorder part
			TLongHashSet childndidsh = new TLongHashSet();
			for (int i = 0; i < inode.getChildCount(); i++) {
				Node [] dbnodesob = (Node [])inode.getChild(i).getObject("dbnodes"); 
				for (int k = 0; k < dbnodesob.length; k++) {
					childndidsh.addAll((long[])dbnodesob[k].getProperty("mrca"));
				}
			}
			TLongArrayList childndids = new TLongArrayList(childndidsh);
			childndids.sort();			
			//			_LOG.trace("finished names");
			TLongArrayList rootids = new TLongArrayList((TLongArrayList) root.getObject("ndidssearch"));
			TLongArrayList outndids = new TLongArrayList();
			//add all the children of the mapped nodes to the outgroup as well
			for (int i = 0; i < root_ndids.size(); i++) {
				if(childndids.contains(root_ndids.getQuick(i))==false)
					outndids.addAll((long[])graphDb.getNodeById(root_ndids.get(i)).getProperty("mrca"));
			}
			childndids.sort();
			outndids.removeAll(childndids);
			outndids.sort();

			HashSet<Node> ancestors = null;
			/*
			 * we can use a simpler calculation if we can assume that the 'trees that come in 
			 * are complete in their taxa
			 */
			if(assumecomplete == true){
				ancestors = LicaUtil.getAllLICAt4j(hit_nodes_search, childndids, outndids);
			}else{
				ancestors = LicaUtil.getBipart4j(hit_nodes,hit_nodes_search, hit_nodes_small_search,childndids, outndids,graphDb);
			}
						
			//			_LOG.trace("ancestor "+ancestor);
			// _LOG.trace(ancestor.getProperty("name"));
			if (ancestors.size() > 0) {
				inode.assocObject("dbnodes", ancestors.toArray(new Node[ancestors.size()]));
				long[] ret = new long[hit_nodes.size()];
				for (int i = 0; i < hit_nodes.size(); i++) {
					ret[i] = hit_nodes.get(i).getId();
				}
				Arrays.sort(ret);
				inode.assocObject("exclusive_mrca", ret);
				inode.assocObject("root_exclusive_mrca", root_ndids.toArray());
			} else {
				//				_LOG.trace("need to make a new node");
				// make a node
				// get the super lica, or what would be the licas if we didn't have the other taxa in the tree
				// this is used to connect the new nodes to their licas for easier traversals
				//HashSet<Node> superlica = LicaUtil.getSuperLICA(hit_nodes_search, childndids);
				HashSet<Node> superlica = LicaUtil.getSuperLICAt4j(hit_nodes,hit_nodes_search, hit_nodes_small_search, childndids);
				//System.out.println("\t\tsuperlica: "+superlica);
				// steps
				// 1. create a node
				// 2. store the mrcas
				// 3. assoc with the node
				Node dbnode = graphDb.createNode();
				//System.out.println("\t\tnewnode: "+dbnode);
				Node [] nar = {dbnode};
				inode.assocObject("dbnodes",nar);
				dbnode.setProperty("mrca", childndids.toArray());
				//System.out.println("\t\tmrca: "+childndids);
				//set outmrcas
				dbnode.setProperty("outmrca",outndids.toArray());
				//System.out.println("\t\toutmrca: "+outndids);
				//set exclusive relationships
				long[] rete = new long[hit_nodes.size()];
				for (int j = 0; j < hit_nodes.size(); j++) {
					rete[j] = hit_nodes.get(j).getId();
				}
				Arrays.sort(rete);
				inode.assocObject("exclusive_mrca",rete);
				root_ndids.sort();
				inode.assocObject("root_exclusive_mrca",root_ndids.toArray());
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
	 * Finish ingest a tree into the GoL. This is called after the names in the tree
	 *	have been mapped to IDs for the nodes in the Taxonomy graph. The mappings are stored
	 *	as an object associated with the root node, as are the list of node ID's. 
	 *
	 * This will update the class member updatedNodes so they can be used for updating 
	 * existing relationships.
	 *
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @param test don't add the tree to the database, just run through as though you would add it
	 * @todo note that if a TreeIngestException the database will not have been reverted
	 *		back to its original state. At minimum at least some relationships
	 *		will have been created. It is also possible that some nodes will have
	 *		been created. We should probably add code to assure that we won't get
	 *		a TreeIngestException, or rollback the db modifications.
	 *		
	 */
	@SuppressWarnings("unchecked")
	private void postOrderAddProcessedTreeToGraphNoAdd(JadeNode inode, JadeNode root, String sourcename, String treeID) throws TreeIngestException {
		// postorder traversal via recursion
		for (int i = 0; i < inode.getChildCount(); i++) {
			postOrderAddProcessedTreeToGraphNoAdd(inode.getChild(i), root, sourcename, treeID);
		}
		//		_LOG.trace("children: "+inode.getChildCount());
		// roothash are the actual ids with the nested names -- used for storing
		// roothashsearch are the ids with nested exploded -- used for searching
		HashMap<JadeNode, Long> roothash = ((HashMap<JadeNode, Long>)root.getObject("hashnodeids"));
		HashMap<JadeNode, ArrayList<Long>> roothashsearch = ((HashMap<JadeNode, ArrayList<Long>>)root.getObject("hashnodeidssearch"));

		if (inode.getChildCount() > 0) {
			System.out.println(inode.getNewick(false));
			ArrayList<JadeNode> nds = inode.getTips();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			ArrayList<Node> hit_nodes_search = new ArrayList<Node> ();
			TLongArrayList hit_nodes_small_search = new TLongArrayList ();
			// store the hits for each of the nodes in the tips
			for (int j = 0; j < nds.size(); j++) {
				hit_nodes.add(graphDb.getNodeById(roothash.get(nds.get(j))));
				ArrayList<Long> tlist = roothashsearch.get(nds.get(j));
				hit_nodes_small_search.addAll(tlist);
				for (int k = 0; k < tlist.size(); k++) {
					hit_nodes_search.add(graphDb.getNodeById(tlist.get(k)));
				}
			}
			hit_nodes_small_search.sort();
			// because we don't associate nodes from the database to this, we have to search based on just the short names			
			TLongArrayList childndids = new TLongArrayList(hit_nodes_small_search);
			TLongArrayList outndids = new TLongArrayList();
			//add all the children of the mapped nodes to the outgroup as well
			for (int i = 0; i < root_ndids.size(); i++) {
				if (childndids.contains(root_ndids.getQuick(i)) == false) {
					outndids.addAll((long[])graphDb.getNodeById(root_ndids.get(i)).getProperty("mrca"));
				}
			}
			childndids.sort();
			outndids.removeAll(childndids);
			outndids.sort();

			HashSet<Node> ancestors = null;
			/*
			 * we can use a simpler calculation if we can assume that the 'trees that come in 
			 * are complete in their taxa
			 */
			if (assumecomplete == true) {
				ancestors = LicaUtil.getAllLICAt4j(hit_nodes_search, childndids, outndids);
			} else {
				ancestors = LicaUtil.getBipart4j(hit_nodes,hit_nodes_search, hit_nodes_small_search,childndids, outndids,graphDb);
			}
			for (Node tnd : ancestors) {
				System.out.println("\tmatched nodes: "+tnd);
				if (tnd.hasProperty("name")) {
					System.out.println("\t\t" + tnd.getProperty("name"));
				}
			}
		}
	}
	
	
	/**
	 * This should be called from within postOrderaddProcessedTreeToGraph
	 * to create relationships between nodes that have already been identified
	 * 
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
				   STUDY-wide fields used at present:
					ot:studyPublicationReference - string: ot:studyPublicationReference "long string"
					ot:studyPublication - URI: ot:studyPublication <http://dx.doi.org/...>
					ot:curatorName - string: ot:curatorName "Jane Doe"
					ot:dataDeposit - string: ot:dataDeposit <http://purl.org/phylo/treebase/phylows/study/TB2:S1925>
					ot:studyId - string / integer ot:studyId "123"
					ot:ottolid - integer: ot:ottolid 783941
				   TREE-wide fields used at present:
					ot:branchLengthMode - string: ot:branchLengthMode "ot:substitutionCount"
					ot:inGroupClade - string: ot:inGroupClade node208482
				*/
				
				// Note: setProperty throws IllegalArgumentException - if value is of an unsupported type (including null)
				
				Node metadatanode = null;
				metadatanode = graphDb.createNode();
				
		// first (ugly) go at this. find more concise way to do this.
		// if property does not exist, do we want 1) nothing, or 2) an empty property? answer: the former.
				if (jt.getObject("ot:studyPublicationReference") != null) {
					System.out.println("Adding property 'ot:studyPublicationReference' for tree " + sourcename + ": " + jt.getObject("ot:studyPublicationReference"));
					metadatanode.setProperty("ot:studyPublicationReference", jt.getObject("ot:studyPublicationReference"));
				}
				if (jt.getObject("ot:studyPublication") != null) {
					System.out.println("Adding property 'ot:studyPublication' for tree " + sourcename + ": " + jt.getObject("ot:studyPublication"));
					metadatanode.setProperty("ot:studyPublication", jt.getObject("ot:studyPublication"));
				}
				if (jt.getObject("ot:curatorName") != null) {
					System.out.println("Adding property 'ot:curatorName' for tree " + sourcename + ": " + jt.getObject("ot:curatorName"));
					metadatanode.setProperty("ot:curatorName", jt.getObject("ot:curatorName"));
				}
				if (jt.getObject("ot:dataDeposit") != null) {
					System.out.println("Adding property 'ot:dataDeposit' for tree " + sourcename + ": " + jt.getObject("ot:dataDeposit"));
					metadatanode.setProperty("ot:dataDeposit", jt.getObject("ot:dataDeposit"));
				}
				if (jt.getObject("ot:studyId") != null) {
					System.out.println("Adding property 'ot:studyId' for tree " + sourcename + ": " + jt.getObject("ot:studyId"));
					metadatanode.setProperty("ot:studyId", jt.getObject("ot:studyId"));
				}
				if (jt.getObject("ot:studyYear") != null) {
					System.out.println("Adding property 'ot:studyYear' for tree " + sourcename + ": " + jt.getObject("ot:studyYear"));
					metadatanode.setProperty("ot:studyYear", jt.getObject("ot:studyYear"));
				}
				if (jt.getObject("ot:inGroupClade") != null) {
					System.out.println("Adding property 'ot:inGroupClade' for tree " + sourcename + ": " + jt.getObject("ot:inGroupClade"));
					metadatanode.setProperty("ot:inGroupClade", jt.getObject("ot:inGroupClade"));
				}
				
		// should studyID replace sourcename?
				metadatanode.setProperty("source", sourcename);
				//metadatanode.setProperty("author", "no one"); // seems deprecated now
				metadatanode.setProperty("newick", treestring); // this could be giant. do we want to do this?
				if (treeID != null) {
					metadatanode.setProperty("treeID", treeID);
				}
				sourceMetaIndex.add(metadatanode, "source", sourcename);
				//add the source taxa ids
				long[]ret2 = root_ndids.toArray();
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
	
	public void deleteAllTrees() {
		IndexHits<Node> hits  = sourceMetaIndex.query("source", "*");
		System.out.println(hits.size());
		for (Node itrel : hits) {
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
		for (Node itrel : hits) {
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
				addSetTreeToGraph("life",source,false);
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
			for (Node itrel : shits) {
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
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Something!");
	}
}
