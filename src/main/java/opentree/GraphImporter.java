package opentree;

import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.TreeReader;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphalgo.WeightedPath;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.RelationshipExpander;
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
		processMRCAS();
	}
	
	private void processMRCAS(){
		for (int i=0;i<jt.getInternalNodeCount();i++){
			JadeNode tnode = jt.getInternalNode(i);
			ArrayList<JadeNode> nds = tnode.getTips();
//			ArrayList<String> nds_names = new ArrayList<String>();
			ArrayList<Node> hit_nodes = new ArrayList<Node>();
			for (int j=0;j<nds.size();j++){
//				nds_names.add(nds.get(j).getName());
				IndexHits<Node> hits = nodeIndex.get("name", nds.get(j).getName().replace("_"," "));
				hit_nodes.add( hits.getSingle());
				hits.close();
			}
			if (hit_nodes.size() > 8){
				expander = Traversal.expanderForTypes(RelTypes.CHILDOF, Direction.OUTGOING);
				PathFinder<Path> pathfinder = GraphAlgoFactory.shortestPath(expander, jt.getInternalNodeCount()+jt.getInternalNodeCount()+1);
				Iterable<Path> paths = pathfinder.findAllPaths(hit_nodes.get(0),graphDb.getNodeById(1));
				for(Path path: paths){
					for (Node node: path.nodes())
						System.out.println(node.getProperty("name"));
					System.out.println("----");
				}
			}
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("Something!");
		
	}

}
