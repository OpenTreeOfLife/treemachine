package opentree;

import jade.tree.*;

import java.lang.StringBuffer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import opentree.TaxonomyBase.RelTypes;
import opentree.TaxonNotFoundException;
import opentree.TreeIngestException;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;
import org.apache.log4j.Logger;

import scala.actors.threadpool.Arrays;

public class GraphImporter extends GraphBase{
	static Logger _LOG = Logger.getLogger(GraphImporter.class);

	private int transaction_iter = 100000;
	private int cur_tran_iter = 0;
	private JadeTree jt;
	Transaction tx;
	private RelationshipExpander expander;
	
	public GraphImporter(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		graphNodeIndex = graphDb.index().forNodes( "graphNamedNodes" );
		taxNodeIndex = graphDb.index().forNodes( "taxNamedNodes" );
		sourceRelIndex = graphDb.index().forRelationships("sourceRels");
	}
	
	/**
	 * Sets the jt member by reading a JadeTree from filename.
	 *
	 * This currently reads a tree from a file but this will need to be changed to 
	 * another form later
	 * @param filename name of file with a newick tree representation
	 */
	public void preProcessTree(String filename){
		//read the tree from a file
		String ts = "";
		try{
			BufferedReader br = new BufferedReader(new FileReader(filename));
			ts = br.readLine();
			br.close();
		}catch(IOException ioe){}
		TreeReader tr = new TreeReader();
		jt = tr.readTree(ts);
		System.out.println("tree read");
		//System.exit(0);
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
				}
				else {
					return rel.getStartNode();
				}
			}
		}
		return null;
	}
	
	/**
	 * assumes the structure of the graphdb where the taxonomy is stored alongside the graph of life
	 * and therefore the graph is initialized, in this case, with the ncbi relationships
	 * 
	 * for a more general implementation, could just go through and work on the preferred nodes
	 */
	public void initializeGraphDBfromNCBI(){
		tx = graphDb.beginTx();
		//start from the node called root
		Node graphstart = null;
		try{
			//root should be the ncbi startnode
			Node startnode = (taxNodeIndex.get("name", "root")).next();
			_LOG.debug("startnode name = " + (String)startnode.getProperty("name"));
			graphstart = graphDb.createNode();
			graphstart.createRelationshipTo(startnode, RelTypes.ISCALLED); //@MTH: Do we need to add a source property to this relationship
			TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
			        .relationships( RelTypes.TAXCHILDOF,Direction.INCOMING );
			for(Node friendnode: CHILDOF_TRAVERSAL.traverse(startnode).nodes()){
				Node taxparent = getAdjNodeFromFirstRelationshipBySource(friendnode, RelTypes.TAXCHILDOF, Direction.OUTGOING, "ncbi");
				if (taxparent != null){
					Node firstchild = getAdjNodeFromFirstRelationshipBySource(friendnode, RelTypes.TAXCHILDOF, Direction.INCOMING, "ncbi");
					Node newnode = graphDb.createNode();
					newnode.createRelationshipTo(friendnode, RelTypes.ISCALLED); //@MTH: Do we need no add a "source" property to this relationship
					graphNodeIndex.add(newnode, "name", friendnode.getProperty("name"));
					if(firstchild == null){//leaf
						long [] tmrcas = {newnode.getId()};
						newnode.setProperty("mrca", tmrcas);			
					}
					if(startnode != friendnode){//not the root
						// getSingleRelationship works here because we know that we are adding the first tree to the graph...
						Node graphparent = taxparent.getSingleRelationship(RelTypes.ISCALLED, Direction.INCOMING).getStartNode();
						Relationship trel = newnode.createRelationshipTo(graphparent, RelTypes.MRCACHILDOF);
						Relationship trel2 = newnode.createRelationshipTo(graphparent, RelTypes.STREECHILDOF);
						trel2.setProperty("source", "ncbi");
						sourceRelIndex.add(trel2, "source", "ncbi");
					}
					cur_tran_iter += 1;
					if(cur_tran_iter % transaction_iter == 0){
						tx.success();
						tx.finish();
						tx = graphDb.beginTx();
						System.out.println("cur transaction: "+cur_tran_iter);
					}
				}
			}
			tx.success();
		}finally{
			tx.finish();
		}
		//start the mrcas
		System.out.println("calculating mrcas");
		try{
			tx = graphDb.beginTx();
			postordernewNodeAddMRCAArray(graphstart);
			tx.success();
		}finally{
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
	private void postordernewNodeAddMRCAArray(Node dbnode){
		//traversal incoming and record all the names
		for(Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelTypes.MRCACHILDOF)){
			Node tnode = rel.getStartNode();
			postordernewNodeAddMRCAArray(tnode);
		}
		ArrayList<Long> mrcas = new ArrayList<Long> ();
		if(dbnode.hasProperty("mrca")==false){
			System.out.println(dbnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
			for(Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelTypes.MRCACHILDOF)){
				Node tnode = rel.getStartNode();
				long[] tmrcas = (long[])tnode.getProperty("mrca");
				for(int j=0;j<tmrcas.length;j++){
					//can take this out as long as this is really the first addition and this is a tree structure going in
//					if (mrcas.contains(tmrcas[j])==false){
						mrcas.add(tmrcas[j]);
//					}
				}
			}
			mrcas.add(dbnode.getId());
			long[] ret = new long[mrcas.size()];
			for (int i=0; i < ret.length; i++){
				ret[i] = mrcas.get(i).longValue();
			}
			dbnode.setProperty("mrca", ret);
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
	 *		about the tree to this node
	 */
	public void addProcessedTreeToGraph(String focalgroup, String sourcename) throws TaxonNotFoundException, TreeIngestException {
		Node focalnode = findTaxNodeByName(focalgroup);
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 1000);
		ArrayList<JadeNode> nds = jt.getRoot().getTips();
		//We'll Create a list of the internal IDs for each taxonomic node that matches
		//		the name of leaf in the tree to be ingested.

		/*@todo making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
		  would make the look ups faster. See comment in testIsMRCA */
		ArrayList<Long> ndids = new ArrayList<Long>(); 
		//We'll map each Jade node to the internal ID of its taxonomic node.
		HashMap<JadeNode,Long> hashnodeids = new HashMap<JadeNode,Long>();
		// this loop fills ndids and hashnodeids or throws an Exception (for 
		//	errors in matching leaves to the taxonomy). No other side effects.
		for (int j=0;j<nds.size();j++){
			//find all the tip taxa and with doubles pick the taxon closest to the focal group
			Node hitnode = null;
			String processedname = nds.get(j).getName().replace("_", " "); //@todo processing syntactic rules like '_' -> ' ' should be done on input parsing. 
			IndexHits<Node> hits = taxNodeIndex.get("name", processedname);
			int numh = hits.size();
			if (numh == 1){
				hitnode = (hits.getSingle().getSingleRelationship(RelTypes.ISCALLED, Direction.INCOMING).getStartNode());
			}else if (numh > 1){
				System.out.println(processedname + " gets " + numh +" hits");
				int shortest = 1000;//this is shortest to the focal, could reverse this
				Node shortn = null;
				for(Node tnode : hits){
					Path tpath = pf.findSinglePath(tnode, focalnode);
					if (tpath!= null){
						if (shortn == null)
							shortn = tnode;
						if(tpath.length()<shortest){
							shortest = tpath.length();
							shortn = tnode;
						}
//						System.out.println(shortest+" "+tpath.length());
					}else{
						System.out.println("one taxon is not within "+ focalgroup);
					}
				}
				assert shortn != null; // @todo this could happen if there are multiple hits outside the focalgroup, and none inside the focalgroup.  We should develop an AmbiguousTaxonException class
				hitnode = shortn.getSingleRelationship(RelTypes.ISCALLED, Direction.INCOMING).getStartNode();
			}
			hits.close();
			if (hitnode == null) {
				assert numh == 0;
				throw new TaxonNotFoundException(processedname);
			}
			ndids.add(hitnode.getId());
			hashnodeids.put(nds.get(j), hitnode.getId());
		}
		// Store the list of taxonomic IDs and the map of JadeNode to ID in the root.
		jt.getRoot().assocObject("ndids", ndids);
		jt.getRoot().assocObject("hashnodeids",hashnodeids);
		postOrderaddProcessedTreeToGraph(jt.getRoot(),jt.getRoot(),sourcename);
	}
	
	
	/**
	 * Finish ingest a tree into the GoL. This is called after the names in the tree
	 *	have been mapped to IDs for the nodes in the Taxonomy graph. The mappings are stored
	 *	as an object associated with the root node, as are the list of node ID's
	 *
	 * @todo: recursive
	 *
	 * this should be done as a preorder traversal
	 *
	 * @param focalgroup a taxonomic name of the ancestor of the leaves in the tree
	 *		this is only used in disambiguating taxa when there are multiple hits 
	 *		for a leaf's taxonomic name
	 * @param sourcename the name to be registered as the "source" property for
	 *		every edge in this tree.
	 * @todo note that if a TreeIngestException the database will not have been reverted
	 *		back to its original state. At minimum at least some relationships
	 *		will have been created. It is also possible that some nodes will have
	 *		been created. We should probably add code to assure that we won't get
	 *		a TreeIngestException, or rollback the db modifications.
	 *		
	 */
	private void postOrderaddProcessedTreeToGraph(JadeNode inode, JadeNode root, String sourcename) throws TreeIngestException {
		// postorder traversal via recursion
		for(int i = 0; i < inode.getChildCount(); i++){
			postOrderaddProcessedTreeToGraph(inode.getChild(i), root, sourcename);
		}
//		_LOG.trace("children: "+inode.getChildCount());
		@SuppressWarnings("unchecked")
		HashMap<JadeNode,Long> roothash = ((HashMap<JadeNode,Long>)root.getObject("hashnodeids"));
		
		if(inode.getChildCount() > 0){
			ArrayList<JadeNode> nds = inode.getTips();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			//store the hits for each of the nodes in the tips
			ArrayList<Long> ndids = new ArrayList<Long>();
			for (int j=0;j<nds.size();j++){
//				IndexHits<Node> hits = graphNodeIndex.get("name", nds.get(j).getName().replace("_"," "));
//				hit_nodes.add( hits.getSingle());
//				hits.close();
				hit_nodes.add(graphDb.getNodeById(roothash.get(nds.get(j))));
				ndids.add(roothash.get(nds.get(j)));
//				System.out.print(nds.get(j).getName()+" ");
			}
//			_LOG.trace("finished names");
			inode.assocObject("ndids", ndids);
			expander = Traversal.expanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING);
			Node ancestor = AncestorUtil.lowestCommonAncestor( hit_nodes, expander);
//			_LOG.trace("ancestor "+ancestor);
			//_LOG.trace(ancestor.getProperty("name"));
			if(testIsMRCA(ancestor, root, inode)){
				// get here if ancestor does not contain any leaves from this tree other than the leaves under inode.
				//	in this case, we can treat this node in the GoL as the MRCA
				inode.assocObject("dbnode", ancestor);
			}else{
//				_LOG.trace("need to make a new node");
				//make a node
				//steps
				//1. create a node
				//2. store the mrcas
				//3. assoc with the node
				Transaction	tx = graphDb.beginTx();
				try{
					Node dbnode = graphDb.createNode();
					inode.assocObject("dbnode",dbnode);
					ArrayList<Long> tal =new ArrayList<Long> (); 
					for(int i =0;i<inode.getChildCount();i++){
						long [] mrcas = ((long[])((Node)inode.getChild(i).getObject("dbnode")).getProperty("mrca"));
						for(int j=0;j<mrcas.length;j++){
							if(tal.contains(mrcas[j]) == false)
								tal.add(mrcas[j]);
						}
					}
					long[] ret = new long[tal.size()];
				    for (int i=0; i < ret.length; i++){
				        ret[i] = tal.get(i).longValue();
				    }
					dbnode.setProperty("mrca", ret);
//					for(int i=0;i<inode.getChildCount();i++){
//						Relationship rel = ((Node)inode.getChild(i).getObject("dbnode")).createRelationshipTo(((Node)(inode.getObject("dbnode"))), RelTypes.MRCACHILDOF);
//					}
					tx.success();
				}finally{
					tx.finish();
				}
			}
			
			// At this point the inode is guaranteed to be associated with a dbnode
			// add the actual branches for the source
			Transaction	tx = graphDb.beginTx();
			try{
				Node currGoLNode = (Node)(inode.getObject("dbnode"));
				for(int i=0;i<inode.getChildCount();i++){
					JadeNode childJadeNode = inode.getChild(i);
					Node childGoLNode = (Node)childJadeNode.getObject("dbnode");
					Relationship rel = childGoLNode.createRelationshipTo(currGoLNode, RelTypes.STREECHILDOF);
					// check to make sure the parent and child nodes are distinct entities...
					if(rel.getStartNode().getId() == rel.getEndNode().getId()){
						StringBuffer errbuff = new StringBuffer();
						errbuff.append("A node and its child map to the same GoL node.\nTips:\n");
						for(int j=0;j<inode.getTips().size();j++){
							errbuff.append(inode.getTips().get(j).getName() + "\n");
							errbuff.append("\n");
						}
						if(ancestor.hasRelationship(opentree.TaxonomyBase.RelTypes.ISCALLED,Direction.OUTGOING)){
							errbuff.append(" ancestor taxonomic name: " + ancestor.getSingleRelationship(opentree.TaxonomyBase.RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
						}
						errbuff.append("\nThe tree has been partially imported into the db.\n");
						throw new TreeIngestException(errbuff.toString());
					}
					//METADATA ENTRY
					rel.setProperty("source", sourcename);
					if(childJadeNode.getBL() > 0.0){ //@todo this if will cause us to drop 0 length branches. We probably need a "has branch length" flag in JadeNode...
						rel.setProperty("branch_length", childJadeNode.getBL());
					}
					boolean mrca_rel = false;
					for(Relationship trel: childGoLNode.getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)){
						if (trel.getOtherNode(childGoLNode).getId() == currGoLNode.getId()){
							mrca_rel = true;
							break;
						}
					}
					if(mrca_rel == false){
						Relationship rel2 = childGoLNode.createRelationshipTo(currGoLNode, RelTypes.MRCACHILDOF);
						// I'm not sure how this assert could ever trip, given that we create a 
						//	childGoLNode -> currGoLNode relationship above and raise an exception
						//	if the endpoints have the same ID.
						assert rel2.getStartNode().getId() != rel2.getEndNode().getId();
					}
				}
				tx.success();
			}finally{
				tx.finish();
			}
		}else{
			inode.assocObject("dbnode",graphDb.getNodeById(roothash.get(inode)));
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
	private boolean testIsMRCA(Node dbnode,JadeNode root, JadeNode inode){
		boolean ret = true;
		long [] dbnodei = (long []) dbnode.getProperty("mrca");
		@SuppressWarnings("unchecked")
		ArrayList<Long> rootids = new ArrayList<Long>((ArrayList<Long>) root.getObject("ndids"));
		@SuppressWarnings("unchecked")
		ArrayList<Long> inodeids =(ArrayList<Long>) inode.getObject("ndids");
//		System.out.println(rootids.size());
		rootids.removeAll(inodeids);
//		System.out.println(rootids.size());
		for(int i=0;i<dbnodei.length;i++){
			if (rootids.contains(dbnodei[i])) //@todo this is the Order(N) lookup that could be log(N) or constant time if ndids was not an ArrayList<Long>
				ret = false;
		}
//		System.out.println("testisMRCA "+ret);
		return ret;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Something!");
		
	}
	
	/*
	 * @deprecated
	 * assumes that the tree has been read by the preProcessTree
	 */
	public void initializeGraphDB(){
		assert jt != null;
		tx = graphDb.beginTx();
		try{
			postOrderInitializeNode(jt.getRoot(),tx);
			tx.success();
		}finally{
			tx.finish();
		}
	}
	
	/*
	 * @deprecated
	 * initializes a graph from a tree
	 */
	private void postOrderInitializeNode(JadeNode inode,Transaction tx){
		for(int i=0;i<inode.getChildCount();i++){
			postOrderInitializeNode(inode.getChild(i),tx);
		}
		//initialize the nodes
		//is a tip record the name, otherwise , don't record the name [could do a label]
		Node dbnode = graphDb.createNode();
		if (inode.getName().length()>0){
			dbnode.setProperty("name", inode.getName());
			graphNodeIndex.add( dbnode, "name", inode.getName() );
		}
		inode.assocObject("dbnode", dbnode);
		if(inode.getChildCount()>0){
			for(int i=0;i<inode.getChildCount();i++){
				Relationship rel = ((Node)inode.getChild(i).getObject("dbnode")).createRelationshipTo(((Node)(inode.getObject("dbnode"))), RelTypes.MRCACHILDOF);
			}
			newNodeAddMRCAArray(dbnode);
		}else{
			ArrayList<Long> mrcas = new ArrayList<Long>();
			mrcas.add(dbnode.getId());
			long[] ret = new long[mrcas.size()];
		    for (int i=0; i < ret.length; i++)
		    {
		        ret[i] = mrcas.get(i).longValue();
		    }
			dbnode.setProperty("mrca",ret);
		}
	}
	
	/*
	 * @deprecated 
	 */
	private void newNodeAddMRCAArray(Node dbnode){
		//traversal incoming and record all the names
		ArrayList<Long> mrcas = new ArrayList<Long> ();
		for(Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelTypes.MRCACHILDOF)){
			Node tnode = rel.getStartNode();
			if(tnode.hasProperty("mrca")){//need to add what to do if it doesn't have it which just means walking
				long[] tmrcas = (long[])tnode.getProperty("mrca");
				for(int j=0;j<tmrcas.length;j++){
					if (mrcas.contains(tmrcas[j])==false){
						mrcas.add(tmrcas[j]);
					}
				}
			}
		}
		long[] ret = new long[mrcas.size()];
	    for (int i=0; i < ret.length; i++){
	        ret[i] = mrcas.get(i).longValue();
	    }
		dbnode.setProperty("mrca", ret);
	}

}
