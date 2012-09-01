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
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Traverser;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;
import org.apache.log4j.Logger;


@SuppressWarnings("deprecation")
public class TaxonomyExplorer extends TaxonomyBase{
	static Logger _LOG = Logger.getLogger(TaxonomyExplorer.class);
	
	public TaxonomyExplorer(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		taxNodeIndex = graphDb.index().forNodes( "taxNamedNodes" );
		graphNodeIndex = graphDb.index().forNodes("graphNamedNodes");
	}
	
	
	/**
	 * Writes a dot file for the taxonomy graph that is rooted at `clade_name`
	 * 
	 * @param clade_name - the name of the internal node in taxNodeIndex that will be
	 * 		the root of the subtree that is written
	 * @param out_filepath - the filepath to create
	 * @todo support other graph file formats
	 */
	public void exportGraphForClade(String clade_name, String out_filepath){
		Node firstNode = findTaxNodeByName(clade_name);
		if (firstNode == null){
			_LOG.error("name \"" + clade_name + "\" not found");
			return;
		}
		//TraversalDescription CHILDOF_TRAVERSAL = Traversal.description().relationships(RelTypes.TAXCHILDOF,Direction.INCOMING );
		_LOG.info("Constructing graph file for " + firstNode.getProperty("name"));
		PrintWriter out_file;
		try {
			out_file = new PrintWriter(new FileWriter(out_filepath));
			out_file.write("strict digraph  {\n\trankdir = RL ;\n");
			HashMap<String, String> src2style = new HashMap<String, String>();
			HashMap<Node, String> nd2dot_name = new HashMap<Node, String>();
			int count = 0;
			for (Node nd : firstNode.traverse(Traverser.Order.BREADTH_FIRST, 
											  StopEvaluator.END_OF_GRAPH,
											  ReturnableEvaluator.ALL,
											  RelTypes.TAXCHILDOF,
											  Direction.INCOMING)) {
				for(Relationship rel : nd.getRelationships(RelTypes.TAXCHILDOF,Direction.INCOMING)) {
					count += 1;
					Node rel_start = rel.getStartNode();
					String rel_start_name = ((String) rel_start.getProperty("name"));
					String rel_start_dot_name = nd2dot_name.get(rel_start);
					if (rel_start_dot_name == null){
						rel_start_dot_name = "n" + (1 + nd2dot_name.size());
						nd2dot_name.put(rel_start, rel_start_dot_name);
						out_file.write("\t" + rel_start_dot_name + " [label=\"" + rel_start_name + "\"] ;\n");
					}
					Node rel_end = rel.getEndNode();
					String rel_end_name = ((String) rel_end.getProperty("name"));
					String rel_end_dot_name = nd2dot_name.get(rel_end);
					if (rel_end_dot_name == null){
						rel_end_dot_name = "n" + (1 + nd2dot_name.size());
						nd2dot_name.put(rel_end, rel_end_dot_name);
						out_file.write("\t" + rel_end_dot_name + " [label=\"" + rel_end_name + "\"] ;\n");
					}
					String rel_source = ((String) rel.getProperty("source"));
					String edge_style = src2style.get(rel_source);
					if (edge_style == null) {
						edge_style = "color=black"; // @TMP
						src2style.put(rel_source, edge_style);
					}
					out_file.write("\t" + rel_start_dot_name + " -> " + rel_end_dot_name + " [" + edge_style + "] ;\n");
					if (count % 100000 == 0)
						_LOG.info(count + " edges written");
				}
			}
			out_file.write("}\n");
			out_file.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This essentially uses every relationship and constructs a newick tree (hardcoded to taxtree.tre file)
	 * 
	 * It would be trivial to only include certain relationship sources
	 * @ name the name of the internal node that will be the root of the subtree 
	 * that is written
	 */
	public void buildTaxonomyTree(String name){
		Node firstNode = findTaxNodeByName(name);
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
		Node firstNode = findTaxNodeByName(name);
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
		Node firstNode = findTaxNodeByName(name);
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
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 100);
		Node focalnode = findTaxNodeByName(focalgroup);
		String ts = "";
		try{
			BufferedReader br = new BufferedReader(new FileReader(treefilename));
			ts = br.readLine();
			br.close();
		}catch(IOException ioe){
			System.out.println("problem reading tree");
		}
		TreeReader tr = new TreeReader();
		JadeTree jt = tr.readTree(ts);
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
