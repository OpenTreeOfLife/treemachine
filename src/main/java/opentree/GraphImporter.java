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

public class GraphImporter extends GraphBase{
	
	private JadeTree jt;
	private RelationshipExpander expander;
	
	public GraphImporter(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		nodeIndex = graphDb.index().forNodes( "nodes" );
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
		System.exit(0);
	}
	
	/*
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
	
	private void postOrderInitializeNode(JadeNode inode,Transaction tx){
		for(int i=0;i<inode.getChildCount();i++){
			postOrderInitializeNode(inode.getChild(i),tx);
		}
		//initialize the nodes
		//is a tip record the name, otherwise , don't record the name [could do a label]
		Node dbnode = graphDb.createNode();
		if (inode.getName().length()>0){
			dbnode.setProperty("name", inode.getName());
			nodeIndex.add( dbnode, "name", inode.getName() );
		}
		inode.assocObject("dbnode", dbnode);
		for(int i=0;i<inode.getChildCount();i++){
			Relationship rel = ((Node)inode.getChild(i).getObject("dbnode")).createRelationshipTo(((Node)(inode.getObject("dbnode"))), RelTypes.MRCACHILDOF);
		}
	}
	
	public void processMRCAS(){
		for (int i=0;i<jt.getInternalNodeCount();i++){
			JadeNode tnode = jt.getInternalNode(i);
			ArrayList<JadeNode> nds = tnode.getTips();
//			ArrayList<String> nds_names = new ArrayList<String>();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			for (int j=0;j<nds.size();j++){
//				nds_names.add(nds.get(j).getName());
				IndexHits<Node> hits = nodeIndex.get("name", nds.get(j).getName().replace("_"," "));
				hit_nodes.add( hits.getSingle());
				Node tnode1 = hits.getSingle();
/*				expander = Traversal.expanderForTypes(RelTypes.CHILDOF, Direction.OUTGOING);
				PathFinder<Path> pathfinder = GraphAlgoFactory.shortestPath(expander, jt.getInternalNodeCount()+jt.getInternalNodeCount()+1);
				Path path = pathfinder.findSinglePath(tnode1,graphDb.getNodeById(1));
				//for(Path path: paths){
					for (Node node: path.nodes())
						System.out.println(node.getProperty("name"));
					System.out.println("----");
				//}
*/
				hits.close();
			}
			expander = Traversal.expanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING);
			Node ancestor = AncestorUtil.lowestCommonAncestor( hit_nodes, expander);
			System.out.println(ancestor.getProperty("name"));
//			GraphAlgoFactory.
			
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Something!");
		
	}

}
