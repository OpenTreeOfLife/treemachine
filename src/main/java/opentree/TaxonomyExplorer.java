package opentree;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.TreeReader;
import opentree.TaxonomyBase.RelTypes;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Traverser;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;
import org.neo4j.kernel.Uniqueness;

public class TaxonomyExplorer extends TaxonomyBase{
	
	public TaxonomyExplorer(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		taxNodeIndex = graphDb.index().forNodes( "taxNamedNodes" );
		graphNodeIndex = graphDb.index().forNodes("graphNamedNodes");
	}
	
	
	/*
	 * This essentially uses every relationship and constructs a newick tree (hardcoded to taxtree.tre file)
	 * 
	 * It would be trivial to only include certain relationship sources
	 */
	public void buildTaxonomyTree(String name){
		IndexHits<Node> hits = taxNodeIndex.get("name", name);
		Node firstNode = hits.getSingle();
		hits.close();
		if (firstNode == null){
			System.out.println("name not found");
			return;
		}
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.TAXCHILDOF,Direction.INCOMING );
		System.out.println(firstNode.getProperty("name"));
		JadeNode root = new JadeNode();
		root.setName(((String) firstNode.getProperty("name")).replace(" ", "_"));
		HashMap<Node,JadeNode> nodes = new HashMap<Node,JadeNode>();
		nodes.put(firstNode, root);
		int count =0;
		for(Relationship friendrel : CHILDOF_TRAVERSAL.traverse(firstNode).relationships()){
			count += 1;
			if (nodes.containsKey(friendrel.getStartNode())==false){
				JadeNode node = new JadeNode();
				node.setName(((String) friendrel.getStartNode().getProperty("name")).replace(" ", "_").replace(",", "_").replace(")", "_").replace("(", "_").replace(":", "_"));
				nodes.put(friendrel.getStartNode(), node);
			}
			if(nodes.containsKey(friendrel.getEndNode())==false){
				JadeNode node = new JadeNode();
				node.setName(((String)friendrel.getEndNode().getProperty("name")).replace(" ", "_").replace(",", "_").replace(")", "_").replace("(", "_").replace(":", "_"));
				nodes.put(friendrel.getEndNode(),node);
			}
			nodes.get(friendrel.getEndNode()).addChild(nodes.get(friendrel.getStartNode()));
			if (count % 100000 == 0)
				System.out.println(count);
		}
		JadeTree tree = new JadeTree(root);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter("taxtree.tre"));
			outFile.write(tree.getRoot().getNewick(false));
			outFile.write(";\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Given a Taxonomic name (as name), this will attempt to find cycles which should be conflicting taxonomies
	 */
	public void findTaxonomyCycles(String name){
		IndexHits<Node> hits = taxNodeIndex.get("name", name);
		Node firstNode = hits.getSingle();
		hits.close();
		if (firstNode == null){
			System.out.println("name not found");
			return;
		}
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.TAXCHILDOF,Direction.INCOMING );
		System.out.println(firstNode.getProperty("name"));
		ArrayList<Node> conflictingnodes = new ArrayList<Node>();
		for(Node friendnode : CHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			int count = 0;
			boolean conflict = false;
			String endNode = "";
			for(Relationship rel : friendnode.getRelationships(Direction.OUTGOING)){
				if (endNode == "")
					endNode = (String) rel.getEndNode().getProperty("name");
				if ((String)rel.getEndNode().getProperty("name") != endNode){
					conflict = true;
				}
				count += 1;
			}
			if (count > 1 && conflict){
				conflictingnodes.add(friendnode);
			}
		}
	}
	
	/*
	 * given a taxonomic name, construct a json object of the graph surrounding that name
	 */
	public void constructJSONGraph(String name){
		IndexHits<Node> hits = taxNodeIndex.get("name",name);
		Node firstNode = hits.getSingle();
		hits.close();
		if(firstNode == null){
			System.out.println("name not found");
			return;
		}
		System.out.println(firstNode.getProperty("name"));
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.TAXCHILDOF,Direction.INCOMING );
		HashMap<Node,Integer> nodenumbers = new HashMap<Node,Integer>();
		HashMap<Integer,Node> numbernodes = new HashMap<Integer,Node>();
		int count = 0;
		for(Node friendnode: CHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			if (friendnode.hasRelationship(Direction.INCOMING)){
				nodenumbers.put(friendnode, count);
				numbernodes.put(count,friendnode);
				count += 1;
			}
		}
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter("graph_data.js"));
			outFile.write("{\"nodes\":[");
			for(int i=0; i<count;i++){
				Node tnode = numbernodes.get(i);
				outFile.write("{\"name\":\""+tnode.getProperty("name")+"");
				outFile.write("\",\"group\":"+nodenumbers.get(tnode)+"");
				outFile.write("},");
			}
			outFile.write("],\"links\":[");
			for(Node tnode: nodenumbers.keySet()){
				for(Relationship trel : tnode.getRelationships(Direction.OUTGOING)){
					outFile.write("{\"source\":"+nodenumbers.get(trel.getStartNode())+"");
					outFile.write(",\"target\":"+nodenumbers.get(trel.getEndNode())+"");
					outFile.write(",\"value\":"+1+"");
					outFile.write("},");
				}
			}
			outFile.write("]");
			outFile.write("}\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void runittest(){
		buildTaxonomyTree("Lonicera");
		shutdownDB();
	}
	
	public void checkNamesInTree(String treefilename,String focalgroup){
		IndexHits<Node> hits2 = taxNodeIndex.get("name", focalgroup);
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 100);
		Node focalnode = hits2.getSingle();
		hits2.close();
		TreeReader tr = new TreeReader();
		String ts = "";
		try{
			BufferedReader br = new BufferedReader(new FileReader(treefilename));
			ts = br.readLine();
			br.close();
		}catch(IOException ioe){
			System.out.println("problem reading tree");
		}
		tr.setTree(ts);
		JadeTree jt = tr.readTree();
		System.out.println("tree read");
		for(int i=0;i<jt.getExternalNodeCount();i++){
			IndexHits<Node> hits = taxNodeIndex.get("name", jt.getExternalNode(i).getName().replace("_"," "));
			int numh = hits.size();
			if (numh == 0)
				System.out.println(jt.getExternalNode(i).getName()+" gets NO hits");
			if (numh > 1){
				System.out.println(jt.getExternalNode(i).getName()+" gets "+numh+" hits");
				for(Node tnode : hits){
					Path tpath = pf.findSinglePath(tnode, focalnode);
				}
			}
			hits.close();
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("unit testing taxonomy explorer");
	    String DB_PATH ="/home/smitty/Dropbox/projects/AVATOL/graphtests/neo4j-community-1.8.M02/data/graph.db";
	    TaxonomyExplorer a = new TaxonomyExplorer(DB_PATH);
	    a.runittest();
	}

}
