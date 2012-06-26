package opentree;

import jade.tree.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import opentree.TaxonomyBase.RelTypes;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

import scala.actors.threadpool.Arrays;

public class GraphImporter extends GraphBase{
	
	private JadeTree jt;
	private RelationshipExpander expander;
	
	public GraphImporter(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		graphNodeIndex = graphDb.index().forNodes( "graphNamedNodes" );
		taxNodeIndex = graphDb.index().forNodes( "taxNamedNodes" );
		sourceRelIndex = graphDb.index().forRelationships("sourceRels");
	}
	
	/*
	 * This currently reads a tree from a file but this will need to be changed to 
	 * another form later
	 */
	public void preProcessTree(String filename){
		//read the tree from a file
		TreeReader tr = new TreeReader();
		String ts = "";
		try{
			BufferedReader br = new BufferedReader(new FileReader(filename));
			ts = br.readLine();
			br.close();
		}catch(IOException ioe){}
		tr.setTree(ts);
		jt = tr.readTree();
		System.out.println("tree read");
		//System.exit(0);
	}
	
	/*
	 * @deprecated
	 * assumes that the tree has been read by the preProcessTree
	 */
	public void initializeGraphDB(){
		assert jt != null;
		Transaction	tx = graphDb.beginTx();
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
	 * assumes the structure of the graphdb where the taxonomy is stored alongside the graph of life
	 * and therefore the graph is initialized, in this case, with the ncbi relationships
	 */
	public void initializeGraphDBfromNCBI(){
		Transaction tx = graphDb.beginTx();
		//start from the node called root
		try{
			//root should be the ncbi startnode
			Node startnode = (taxNodeIndex.get("name", "Magnoliophyta")).next();
			postOrderInitializeNodefromNCBI(startnode,tx);
			tx.success();
		}finally{
			tx.finish();
		}
	}
	
	private void postOrderInitializeNodefromNCBI(Node inode, Transaction tx){
		int ncount= 0;
		for(Relationship rel: inode.getRelationships(Direction.INCOMING, RelTypes.TAXCHILDOF)){
			if (((String)rel.getProperty("source")).compareTo("ncbi_no_env_samples.txt") == 0){
				ncount += 1;
				postOrderInitializeNodefromNCBI(rel.getStartNode(),tx);
			}
		}
		//if leaf, just make the new graph node and the called relationship
		Node newnode = graphDb.createNode();
		newnode.createRelationshipTo(inode, RelTypes.ISCALLED);
		graphNodeIndex.add(newnode, "name", inode.getProperty("name"));
		if(ncount == 0){//leaf
			long [] tmrcas = {newnode.getId()};
			newnode.setProperty("mrca", tmrcas);
		}else{//internal
			for (Relationship rel: inode.getRelationships(Direction.INCOMING, RelTypes.TAXCHILDOF)){
				if (((String)rel.getProperty("source")).compareTo("ncbi_no_env_samples.txt") == 0){
					//get the node related to is called
					Node tnode = rel.getStartNode().getRelationships(Direction.INCOMING,RelTypes.ISCALLED).iterator().next().getStartNode();
					Relationship trel = tnode.createRelationshipTo(newnode, RelTypes.MRCACHILDOF);
					Relationship trel2 = tnode.createRelationshipTo(newnode, RelTypes.STREECHILDOF);
					trel2.setProperty("source", "ncbi");
					sourceRelIndex.add(trel2, "source", "ncbi");
				}
			}
			newNodeAddMRCAArray(newnode);
		}
		if (ncount > 1000)
			System.out.println(inode.getProperty("name")+" "+ncount);
	}
	
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
	
	/*
	 * this should be done as a preorder traversal
	 */
	public void addProcessedTreeToGraph(){
		ArrayList<JadeNode> nds = jt.getRoot().getTips();
		//store at the root all the nd ids for the matches
		ArrayList<Long> ndids = new ArrayList<Long>();
		for (int j=0;j<nds.size();j++){
			IndexHits<Node> hits = graphNodeIndex.get("name", nds.get(j).getName().replace("_"," "));
			ndids.add(hits.getSingle().getId());
			hits.close();
		}
		jt.getRoot().assocObject("ndids", ndids);
		postOrderaddProcessedTreeToGraph(jt.getRoot(),jt.getRoot());
		//generate the relationships between the stored nodes
		
		//store the mrcas for the new nodes that don't have mrcas
	}
	
	private void postOrderaddProcessedTreeToGraph(JadeNode inode,JadeNode root){
		for(int i=0;i<inode.getChildCount();i++){
			postOrderaddProcessedTreeToGraph(inode.getChild(i),root);
		}
		if(inode.getChildCount()>0){
			ArrayList<JadeNode> nds = inode.getTips();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			//store the hits for each of the nodes in the tips
			ArrayList<Long> ndids = new ArrayList<Long>();
			for (int j=0;j<nds.size();j++){
				IndexHits<Node> hits = graphNodeIndex.get("name", nds.get(j).getName().replace("_"," "));
				hit_nodes.add( hits.getSingle());
				hits.close();
				ndids.add(hit_nodes.get(j).getId());
				System.out.print(nds.get(j).getName()+" ");
			}
			System.out.println();
			inode.assocObject("ndids", ndids);
			expander = Traversal.expanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING);
			Node ancestor = AncestorUtil.lowestCommonAncestor( hit_nodes, expander);
			//System.out.println(ancestor.getProperty("name"));
			if(testIsMRCA(ancestor,root,inode)){
				inode.assocObject("dbnode", ancestor);
			}else{
				System.out.println("need to make a new node");
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
					for(int i=0;i<inode.getChildCount();i++){
						Relationship rel = ((Node)inode.getChild(i).getObject("dbnode")).createRelationshipTo(((Node)(inode.getObject("dbnode"))), RelTypes.MRCACHILDOF);
					}
					tx.success();
				}finally{
					tx.finish();
				}
			}
			//add the actual branches for the source
			Transaction	tx = graphDb.beginTx();
			try{
				for(int i=0;i<inode.getChildCount();i++){
					Relationship rel = ((Node)inode.getChild(i).getObject("dbnode")).createRelationshipTo(((Node)(inode.getObject("dbnode"))), RelTypes.STREECHILDOF);
					rel.setProperty("source", "TESTING");
				}
				tx.success();
			}finally{
				tx.finish();
			}
		}else{
			IndexHits<Node> hits = graphNodeIndex.get("name", inode.getName().replace("_"," "));
			inode.assocObject("dbnode",hits.getSingle());
			hits.close();
		}
	}
	
	private boolean testIsMRCA(Node dbnode,JadeNode root, JadeNode inode){
		boolean ret = true;
		long [] dbnodei = (long []) dbnode.getProperty("mrca");
		@SuppressWarnings("unchecked")
		ArrayList<Long> rootids = new ArrayList<Long>((ArrayList<Long>) root.getObject("ndids"));
		@SuppressWarnings("unchecked")
		ArrayList<Long> inodeids =(ArrayList<Long>) inode.getObject("ndids");
		System.out.println(rootids.size());
		rootids.removeAll(inodeids);
		System.out.println(rootids.size());
		for(int i=0;i<dbnodei.length;i++){
			if (rootids.contains(dbnodei[i]))
				ret = false;
		}
		return ret;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Something!");
		
	}

}
