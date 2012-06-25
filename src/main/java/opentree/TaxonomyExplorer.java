package opentree;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import jade.tree.JadeNode;
import jade.tree.JadeTree;
import opentree.TaxonomyBase.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
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
		nodeIndex = graphDb.index().forNodes( "nodes" );
	}
	
	
	/*
	 * This essentially uses every relationship and constructs a newick tree (hardcoded to taxtree.tre file)
	 * 
	 * It would be trivial to only include certain relationship sources
	 */
	public void buildTaxonomyTree(String name){
		IndexHits<Node> hits = nodeIndex.get("name", name);
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
		IndexHits<Node> hits = nodeIndex.get("name", name);
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
	
	
	public void runittest(){
		buildTaxonomyTree("Lonicera");
		shutdownDB();
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
