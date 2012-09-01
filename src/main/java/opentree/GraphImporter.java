package opentree;

import jade.tree.*;

import java.lang.StringBuffer;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

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
//import org.apache.log4j.Logger;

import scala.actors.threadpool.Arrays;

public class GraphImporter extends GraphBase{
	//static Logger _LOG = Logger.getLogger(GraphImporter.class);

	private int transaction_iter = 100000;
	private int cur_tran_iter = 0;
	private JadeTree jt;
	private RelationshipExpander expander;
	private ArrayList<Node> updatedNodes;
	private HashSet<Node> updatedSuperLICAs;
	private Transaction	tx;
	
	public GraphImporter(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		graphNodeIndex = graphDb.index().forNodes( "graphNamedNodes" );
		taxNodeIndex = graphDb.index().forNodes( "taxNamedNodes" );
		sourceRelIndex = graphDb.index().forRelationships("sourceRels");
		sourceRootIndex = graphDb.index().forNodes("sourceRootNodes");
	}
	
	public GraphImporter(EmbeddedGraphDatabase graphn){
		graphDb = graphn;
		graphNodeIndex = graphDb.index().forNodes( "graphNamedNodes" );
		taxNodeIndex = graphDb.index().forNodes( "taxNamedNodes" );
		sourceRelIndex = graphDb.index().forRelationships("sourceRels");
		sourceRootIndex = graphDb.index().forNodes("sourceRootNodes");
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
	 * Sets the jt member by a JadeTree already read and processed.
	 *
	 * @param JadeTree object
	 */
	public void setTree(JadeTree tree){
		jt = tree;
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
//			_LOG.debug("startnode name = " + (String)startnode.getProperty("name"));
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
						long [] ntmrcas = {};
						newnode.setProperty("nested_mrca", ntmrcas);
					}
					if(startnode != friendnode){//not the root
						// getSingleRelationship works here because we know that we are adding the first tree to the graph...
						Node graphparent = taxparent.getSingleRelationship(RelTypes.ISCALLED, Direction.INCOMING).getStartNode();
						newnode.createRelationshipTo(graphparent, RelTypes.MRCACHILDOF);
						Relationship trel2 = newnode.createRelationshipTo(graphparent, RelTypes.STREEEXACTCHILDOF);
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
		//could make this a hashset if dups become a problem
		ArrayList<Long> mrcas = new ArrayList<Long> ();
		ArrayList<Long> nested_mrcas = new ArrayList<Long>();
		if(dbnode.hasProperty("mrca")==false){
			System.out.println(dbnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
			for(Relationship rel: dbnode.getRelationships(Direction.INCOMING,RelTypes.MRCACHILDOF)){
				Node tnode = rel.getStartNode();
				long[] tmrcas = (long[])tnode.getProperty("mrca");
				for(int j=0;j<tmrcas.length;j++){
					mrcas.add(tmrcas[j]);
				}
				long[] nestedtmrcas = (long[])tnode.getProperty("nested_mrca");
				for(int j=0;j<nestedtmrcas.length;j++){
					nested_mrcas.add(nestedtmrcas[j]);
				}
			}
			//should these be added to the nested ones?
			//higher taxa?
			//mrcas.add(dbnode.getId());
			long[] ret = new long[mrcas.size()];
			for (int i=0; i < ret.length; i++){
				ret[i] = mrcas.get(i).longValue();
			}
			dbnode.setProperty("mrca", ret);
			
			nested_mrcas.add(dbnode.getId());
			long[] ret2 = new long[nested_mrcas.size()];
			for (int i=0; i < ret2.length; i++){
				ret2[i] = nested_mrcas.get(i).longValue();
			}
			dbnode.setProperty("nested_mrca", ret2);
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
	public void addProcessedTreeToGraph(String focalgroup, String sourcename) throws TaxonNotFoundException, TreeIngestException {
		Node focalnode = findTaxNodeByName(focalgroup);
		updatedNodes = new ArrayList<Node>();
		updatedSuperLICAs = new HashSet<Node>();
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 1000);
		ArrayList<JadeNode> nds = jt.getRoot().getTips();
		//We'll Create a list of the internal IDs for each taxonomic node that matches
		//		the name of leaf in the tree to be ingested.

		/*@todo making the ndids a Set<Long>, sorted ArrayList<Long> or HashSet<Long>
		  would make the look ups faster. See comment in testIsMRCA */
		HashSet<Long> ndids = new HashSet<Long>(); 
		//We'll map each Jade node to the internal ID of its taxonomic node.
		HashMap<JadeNode,Long> hashnodeids = new HashMap<JadeNode,Long>();
		//same as above but added for nested nodes, so more comprehensive and 
		//		used just for searching. the others are used for storage
		HashSet<Long> ndidssearch = new HashSet<Long>();
		HashMap<JadeNode,ArrayList<Long>> hashnodeidssearch = new HashMap<JadeNode,ArrayList<Long>>();
		// this loop fills ndids and hashnodeids or throws an Exception (for 
		//	    errors in matching leaves to the taxonomy). No other side effects.
		//TODO: when receiving trees in the future the ids should already be set so we don't have to 
		//      do this kind of fuzzy matching
		for (int j=0;j<nds.size();j++){
			//find all the tip taxa and with doubles pick the taxon closest to the focal group
			Node hitnode = null;
			String processedname = nds.get(j).getName();//.replace("_", " "); //@todo processing syntactic rules like '_' -> ' ' should be done on input parsing. 
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
			//added for nested nodes 
			long [] mrcas = (long[])hitnode.getProperty("mrca");
			ArrayList<Long> tset = new ArrayList<Long>(); 
			for(int k=0;k<mrcas.length;k++){
				ndidssearch.add(mrcas[k]);
				tset.add((Long)mrcas[k]);
			}

			hashnodeidssearch.put(nds.get(j),tset);
			ndids.add(hitnode.getId());
			hashnodeids.put(nds.get(j), hitnode.getId());
		}
		// Store the list of taxonomic IDs and the map of JadeNode to ID in the root.
		jt.getRoot().assocObject("ndids", ndids);
		jt.getRoot().assocObject("hashnodeids",hashnodeids);
		jt.getRoot().assocObject("ndidssearch",ndidssearch);
		jt.getRoot().assocObject("hashnodeidssearch",hashnodeidssearch);
		tx = graphDb.beginTx();
		try{
			postOrderaddProcessedTreeToGraph(jt.getRoot(),jt.getRoot(),sourcename, focalnode);
		}finally{
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
	@SuppressWarnings("unchecked")
	private void postOrderaddProcessedTreeToGraph(JadeNode inode, JadeNode root, String sourcename, Node focalnode) throws TreeIngestException {
		// postorder traversal via recursion
		for(int i = 0; i < inode.getChildCount(); i++){
			postOrderaddProcessedTreeToGraph(inode.getChild(i), root, sourcename, focalnode);
		}
		//		_LOG.trace("children: "+inode.getChildCount());
		//roothash are the actual ids with the nested names -- used for storing
		//roothashsearch are the ids with nested exploded -- used for searching
		HashMap<JadeNode,Long> roothash = ((HashMap<JadeNode,Long>)root.getObject("hashnodeids"));
		HashMap<JadeNode, ArrayList<Long>> roothashsearch = ((HashMap<JadeNode,ArrayList<Long>>)root.getObject("hashnodeidssearch"));

		if(inode.getChildCount() > 0){
			ArrayList<JadeNode> nds = inode.getTips();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			ArrayList<Node> hit_nodes_search = new ArrayList<Node> ();
			//store the hits for each of the nodes in the tips
			for (int j=0;j<nds.size();j++){
				hit_nodes.add(graphDb.getNodeById(roothash.get(nds.get(j))));
				ArrayList<Long> tlist = roothashsearch.get(nds.get(j));
				for(int k=0;k<tlist.size();k++){
					hit_nodes_search.add(graphDb.getNodeById(tlist.get(k)));
				}
			}
			//get all the childids even if they aren't in the tree, this is the postorder part
			HashSet<Long> childndids =new HashSet<Long> (); 
			for(int i =0;i<inode.getChildCount();i++){
				Node [] dbnodesob = (Node [])inode.getChild(i).getObject("dbnodes"); 
				for(int k=0;k<dbnodesob.length;k++){
					long [] mrcas = ((long[])dbnodesob[k].getProperty("mrca"));
					for(int j=0;j<mrcas.length;j++){
						if(childndids.contains(mrcas[j]) == false)
							childndids.add(mrcas[j]);
					}
				}
			}
			//			_LOG.trace("finished names");
			HashSet<Long> rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndids"));
			HashSet<Node> ancestors = AncestorUtil.getAllLICA(hit_nodes_search, childndids, rootids);
			//			_LOG.trace("ancestor "+ancestor);
			//_LOG.trace(ancestor.getProperty("name"));
			if(ancestors.size()>0){
				inode.assocObject("dbnodes",ancestors.toArray(new Node[ancestors.size()]));
				boolean exact = false;
				if(ancestors.size() == 1){
					int retlength = ((long[])ancestors.iterator().next().getProperty("mrca")).length;
					if(retlength == hit_nodes.size()){
						exact = true;
					}
				}if(exact == true){
					inode.assocObject("streetype", "exact");//node is exact and only has taxa in the stree)
				}else{
					inode.assocObject("streetype", "inclu"); // node is inclusive (includes taxa not in the stree)
					long[] ret = new long[hit_nodes.size()];
					for(int i=0;i<hit_nodes.size();i++){
						ret[i] = hit_nodes.get(i).getId();
					}
					rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndids"));
					long[] ret2 = new long[rootids.size()];
					Iterator<Long> chl2 = rootids.iterator();
					int i=0;
					while(chl2.hasNext()){
						ret2[i] = chl2.next().longValue();
						i++;
					}
					inode.assocObject("streetype", "inclu"); // node is inclusive (includes taxa not in the stree)
					inode.assocObject("exclusive_mrca",ret);
					inode.assocObject("root_exclusive_mrca",ret2);
				}
			}else{
				//				_LOG.trace("need to make a new node");
				//make a node
				//get the super lica, or what would be the licas if we didn't have the other taxa in the tree
				//this is used to connect the new nodes to their licas for easier traversals
				HashSet<Node> superlica = AncestorUtil.getSuperLICA(hit_nodes_search, childndids);
				//steps
				//1. create a node
				//2. store the mrcas
				//3. assoc with the node
				Node dbnode = graphDb.createNode();
				//					inode.assocObject("dbnode",dbnode);
				Node [] nar = {dbnode};
				inode.assocObject("dbnodes",nar);
				long[] ret = new long[childndids.size()];
				Iterator<Long> chl = childndids.iterator();
				int i=0;
				while(chl.hasNext()){
					ret[i] = chl.next().longValue();
					i++;
				}
				dbnode.setProperty("mrca", ret);
				//determine if the relationships that are made should be STREEEXACT or STREEINCLU
				if(ret.length == hit_nodes.size()){
					inode.assocObject("streetype", "exact");//node is exact and only has taxa in the stree)
				}else{
					inode.assocObject("streetype", "inclu"); // node is inclusive (includes taxa not in the stree)
					long[] rete = new long[hit_nodes.size()];
					for(int j=0;j<hit_nodes.size();j++){
						rete[j] = hit_nodes.get(j).getId();
					}
					inode.assocObject("exclusive_mrca",rete);
					rootids = new HashSet<Long>((HashSet<Long>) root.getObject("ndids"));
					long[] ret2 = new long[rootids.size()];
					Iterator<Long> chl2 = rootids.iterator();
					i=0;
					while(chl2.hasNext()){
						ret2[i] = chl2.next().longValue();
						i++;
					}
					inode.assocObject("root_exclusive_mrca",ret2);
				}
				//					for(int i=0;i<inode.getChildCount();i++){
				//						Relationship rel = ((Node)inode.getChild(i).getObject("dbnode")).createRelationshipTo(((Node)(inode.getObject("dbnode"))), RelTypes.MRCACHILDOF);
				//					}
				Iterator<Node> itrsl = superlica.iterator();
				while(itrsl.hasNext()){
					Node itrnext = itrsl.next();
					dbnode.createRelationshipTo(itrnext, RelTypes.MRCACHILDOF);
					updatedSuperLICAs.add(itrnext);
				}
				tx.success();
				//add new nodes so they can be used for updating after tree ingest
				updatedNodes.add(dbnode);
			}
			addProcessedNodeRelationships(inode, sourcename);
		}else{
//			inode.assocObject("dbnode",graphDb.getNodeById(roothash.get(inode)));
			Node [] nar = {graphDb.getNodeById(roothash.get(inode))};
			inode.assocObject("dbnodes",nar);
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
	private void addProcessedNodeRelationships(JadeNode inode, String sourcename) throws TreeIngestException{
		// At this point the inode is guaranteed to be associated with a dbnode
		// add the actual branches for the source
		//			Node currGoLNode = (Node)(inode.getObject("dbnode"));
		Node [] allGoLNodes = (Node [])(inode.getObject("dbnodes"));
		//for use if this node will be an incluchildof and we want to store the relationships for faster retrieval
		ArrayList<Relationship> inclusiverelationships = new ArrayList<Relationship>();
		boolean inclusive = false;
		for(int k=0;k<allGoLNodes.length;k++){
			Node currGoLNode = allGoLNodes[k];
			//add the root index for the source trail
			if (inode.isTheRoot()){
				//TODO: this will need to be updated when trees are updated
				System.out.println("placing root in index");
				sourceRootIndex.add(currGoLNode, "rootnode", sourcename);
			}
			//				System.out.println("working on relationships for "+currGoLNode.getId());

			for(int i=0;i<inode.getChildCount();i++){
				JadeNode childJadeNode = inode.getChild(i);
				//					Node childGoLNode = (Node)childJadeNode.getObject("dbnode");
				Node [] allChildGoLNodes = (Node [])(childJadeNode.getObject("dbnodes"));
				for(int m=0;m<allChildGoLNodes.length;m++){
					Node childGoLNode = allChildGoLNodes[m];
					Relationship rel;
					if(((String)inode.getObject("streetype")).compareTo("exact")==0){//exact only taxa included in the stree
						rel = childGoLNode.createRelationshipTo(currGoLNode, RelTypes.STREEEXACTCHILDOF);
						sourceRelIndex.add(rel, "source", sourcename);
					}else{//inclu taxa included not sampled in stree
						inclusive = true;
						rel = childGoLNode.createRelationshipTo(currGoLNode, RelTypes.STREEINCLUCHILDOF);
						sourceRelIndex.add(rel, "source", sourcename);
						rel.setProperty("exclusive_mrca",(long [])inode.getObject("exclusive_mrca"));
						rel.setProperty("root_exclusive_mrca",(long []) inode.getObject("root_exclusive_mrca"));
						long [] licaids = new long[allGoLNodes.length];
						for(int n=0;n<licaids.length;n++){
							licaids[n] = allGoLNodes[n].getId();
						}
						rel.setProperty("licas",licaids);
						inclusiverelationships.add(rel);
					}
					// check to make sure the parent and child nodes are distinct entities...
					if(rel.getStartNode().getId() == rel.getEndNode().getId()){
						StringBuffer errbuff = new StringBuffer();
						errbuff.append("A node and its child map to the same GoL node.\nTips:\n");
						for(int j=0;j<inode.getTips().size();j++){
							errbuff.append(inode.getTips().get(j).getName() + "\n");
							errbuff.append("\n");
						}
						if(currGoLNode.hasRelationship(opentree.TaxonomyBase.RelTypes.ISCALLED,Direction.OUTGOING)){
							errbuff.append(" ancestor taxonomic name: " + currGoLNode.getSingleRelationship(opentree.TaxonomyBase.RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
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
			}
		}
		//for inclusivechildof nodes, set the property of each relationshipid
		if(inclusive==true){
			long [] relids = new long[inclusiverelationships.size()];
			for(int n=0;n<inclusiverelationships.size();n++){
				relids[n] = inclusiverelationships.get(n).getId();
			}
			for(int n=0;n<inclusiverelationships.size();n++){
				inclusiverelationships.get(n).setProperty("inclusive_relids", relids);
			}
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
	private boolean testIsMRCA(Node dbnode,JadeNode root, JadeNode inode){
		boolean ret = true;
		long [] dbnodei = (long []) dbnode.getProperty("mrca");
		@SuppressWarnings("unchecked")
		ArrayList<Long> rootids = new ArrayList<Long>((HashSet<Long>) root.getObject("ndids"));
		@SuppressWarnings("unchecked")
		ArrayList<Long> inodeids =(ArrayList<Long>) inode.getObject("ndids");
//		System.out.println(rootids.size());
		rootids.removeAll(inodeids);
//		System.out.println(rootids.size());
		for(int i=0;i<dbnodei.length;i++){
			if (rootids.contains(dbnodei[i])){ //@todo this is the Order(N) lookup that could be log(N) or constant time if ndids was not an ArrayList<Long>
				ret = false;
				break;
			}
		}
//		System.out.println("testisMRCA "+ret);
		return ret;
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
	 */
/*
 * This might be better done if we indexed the new updated node
 */
	public void updateAfterTreeIngest(){
		ArrayList<Relationship> krels = new ArrayList<Relationship>();
		Iterator<Node> itr = updatedSuperLICAs.iterator();
		while(itr.hasNext()){
			Node tnext = itr.next();
			for(Relationship rel: tnext.getRelationships(Direction.INCOMING, RelTypes.STREEINCLUCHILDOF)){
				boolean add = true;
				long [] incl = (long [])rel.getProperty("inclusive_relids");
				for(int i=0;i<incl.length;i++){
					if(krels.contains(graphDb.getRelationshipById(incl[i]))){
						add = false;
						break;
					}
				}
				if(add ==true){
					krels.add(rel);
				}
				
			}
		}
		//these are relationships that will be deleted at the end but can't 
		//yet because of null pointers here. They arise often because they are
		//parents of some of these other nodes that get fixed
		HashSet<Relationship> tobedeleted = new HashSet<Relationship>();
		//going through the potential problem nodes and updating
		for(int i=0;i<krels.size();i++){
			if(tobedeleted.contains(krels.get(i))){
				continue;
			}
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			HashSet<Long> childndids = new HashSet<Long>();
			HashSet<Long> rootids = new HashSet<Long>();
			long [] exmrcas = (long [])krels.get(i).getProperty("exclusive_mrca");
			long [] rtexmrcas = (long [])krels.get(i).getProperty("root_exclusive_mrca");
			for(int j=0;j<exmrcas.length;j++){
				childndids.add(exmrcas[j]);
				hit_nodes.add(graphDb.getNodeById(exmrcas[j]));
			}
			for(int j=0;j<rtexmrcas.length;j++){
				rootids.add(rtexmrcas[j]);
			}
			HashSet<Node> ancestors = AncestorUtil.getAllLICA(hit_nodes, childndids, rootids);
			long [] licas = (long[])krels.get(i).getProperty("licas");
			long [] relids = (long [])krels.get(i).getProperty("inclusive_relids");
			String source = (String)krels.get(i).getProperty("source");
			HashSet<Node> startNodes = new HashSet<Node>();
			for(int j=0;j<relids.length;j++){
				startNodes.add(graphDb.getRelationshipById(relids[j]).getStartNode());
			}
			//these are the nodes that are parents of the current ancestors
			//if the children change, these need to be updated as well
			HashSet<Node> parentnodes = new HashSet<Node>();
			HashMap<Node,HashSet<Relationship>> parent_rel_map = new HashMap<Node, HashSet<Relationship>>();
			//TODO: update the rootnode index if this changes
			//this should hold, and for now, just remake the relationships
			if(licas.length != ancestors.size()){
				for(int j=0;j<relids.length;j++){
					System.out.println("deleting rel: "+relids[j]);
					Transaction	tx = graphDb.beginTx();
					try{
						Relationship trel = graphDb.getRelationshipById(relids[j]);
						sourceRelIndex.remove(trel, "source", source);
						System.out.println(trel.getStartNode()+" "+trel.getEndNode());
						for(Relationship ptrel: trel.getEndNode().getRelationships(Direction.OUTGOING)){
							if(ptrel.hasProperty("source")){
								if(((String)ptrel.getProperty("source")).compareTo(source)==0){
									System.out.println(ptrel.getStartNode()+"-"+ptrel.getEndNode());
									parentnodes.add(ptrel.getEndNode());
									if(parent_rel_map.containsKey(ptrel.getEndNode())==false){
										parent_rel_map.put(ptrel.getEndNode(), new HashSet<Relationship>());
									}
									parent_rel_map.get(ptrel.getEndNode()).add(ptrel);
									tobedeleted.add(ptrel);
								}
							}
						}
						trel.delete();
						tx.success();
					}finally{
						tx.finish();
					}
				}
				//streeexact
				Transaction	tx = graphDb.beginTx();
				try{
					if(ancestors.size() == 1 && 
							exmrcas.length == ((long[])ancestors.iterator().next().getProperty("mrca")).length){
						Iterator<Node>snit = startNodes.iterator();
						Node anc = ancestors.iterator().next();
						while(snit.hasNext()){
							Relationship rel = snit.next().createRelationshipTo(anc, RelTypes.STREEEXACTCHILDOF);
							sourceRelIndex.add(rel, "source", source);
							System.out.println("adding rel: "+rel.getId());
							rel.setProperty("source", source);
						}
					}else{//streeincl
						Iterator<Node> ancit = ancestors.iterator();
						long [] anlicas = new long[ancestors.size()];
						int j = 0;
						while(ancit.hasNext()){
							anlicas[j] = ancit.next().getId();
							j++;
						}
						ancit = ancestors.iterator();
						ArrayList<Relationship> finrels = new ArrayList<Relationship> ();
						while(ancit.hasNext()){
							Node ancitn = ancit.next();
							Iterator<Node>snit = startNodes.iterator();
							while(snit.hasNext()){
								Node snitn = snit.next(); 
								Relationship rel = snitn.createRelationshipTo(ancitn, RelTypes.STREEINCLUCHILDOF);
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
						for(j=0;j<finrels.size();j++){
							relidsfin[j] = finrels.get(j).getId();
						}
						for(j=0;j<finrels.size();j++){
							finrels.get(j).setProperty("inclusive_relids",relidsfin);
						}
					}
					//connect the new ancestors to the original parents
					Iterator<Node> ancit = ancestors.iterator();
					Iterator<Node> pnodes = parentnodes.iterator();
					while(pnodes.hasNext()){
						Node pnode = pnodes.next();
						HashSet<Relationship> oldones = parent_rel_map.get(pnode);
						Relationship toldone = oldones.iterator().next();
						long [] tlicas = (long [])toldone.getProperty("licas");
						long [] texmrcas = (long [])toldone.getProperty("exclusive_mrca");
						long [] trtexmrcas = (long [])toldone.getProperty("root_exclusive_mrca");
						ArrayList<Relationship> new_rels = new ArrayList<Relationship>();
						long [] oldrelids = (long [])toldone.getProperty("inclusive_relids");
						ArrayList<Long> newrelids = new ArrayList<Long>();
						for(int j=0;j<oldrelids.length;j++){newrelids.add(oldrelids[j]);}
						Iterator<Relationship> oldit = oldones.iterator();
						while(oldit.hasNext()){newrelids.remove(oldit.next().getId());}
						while(ancit.hasNext()){
							Node anc = ancit.next();
							//first make sure that there is a MRCACHILDOF for these nodes
							boolean present = false;
							for(Relationship rels: pnode.getRelationships(Direction.INCOMING, RelTypes.MRCACHILDOF)){
								if(rels.getStartNode()==anc){
									present = true;
									break;
								}
							}
							if (present == false){
								anc.createRelationshipTo(pnode, RelTypes.MRCACHILDOF);
							}
							Relationship newrel = anc.createRelationshipTo(pnode, RelTypes.STREEINCLUCHILDOF);
							sourceRelIndex.add(newrel, "source", source);
							newrel.setProperty("source", source);
							newrel.setProperty("licas", tlicas);
							newrel.setProperty("exclusive_mrca",texmrcas);
							newrel.setProperty("root_exclusive_mrca",trtexmrcas);
							new_rels.add(newrel);
							newrelids.add(newrel.getId());
						}
						long [] finalnewrelids = new long[newrelids.size()];
						for(int j=0;j<finalnewrelids.length;j++){
							finalnewrelids[j] = newrelids.get(j);
						}
						for(Relationship rels: pnode.getRelationships(Direction.INCOMING, RelTypes.STREEINCLUCHILDOF)){
							if(((String)rels.getProperty("source")).compareTo(source)==0){
								rels.setProperty("inclusive_relids", finalnewrelids);
							}
						}
					}
					tx.success();
				}finally{
					tx.finish();
				}
			}
		}
		//cleaning up parent relationships that have to be deleted
		Transaction	tx = graphDb.beginTx();
		try{
			Iterator<Relationship> itrel = tobedeleted.iterator();
			while (itrel.hasNext())
				itrel.next().delete();
			tx.success();
		}finally{
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
