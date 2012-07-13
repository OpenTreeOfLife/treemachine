package opentree;

import jade.tree.JadeNode;
import jade.tree.JadeTree;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.text.html.HTMLDocument.Iterator;

import opentree.GraphBase.RelTypes;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

public class GraphExplorer extends GraphBase{
	private SpeciesEvaluator se;
	private ChildNumberEvaluator cne;
	
	public GraphExplorer (){
		cne = new ChildNumberEvaluator();
		cne.setChildThreshold(100);
		se = new SpeciesEvaluator();
	}
	
	public void setEmbeddedDB(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname ) ;
		graphNodeIndex = graphDb.index().forNodes("graphNamedNodes");
		taxNodeIndex = graphDb.index().forNodes("taxNamedNodes");
	}
	
	/*
	 * given a taxonomic name, construct a json object of the graph surrounding that name
	 */
	public void constructJSONGraph(String name){
		IndexHits<Node> hits = graphNodeIndex.get("name",name);
		Node firstNode = hits.getSingle();
		hits.close();
		if(firstNode == null){
			System.out.println("name not found");
			return;
		}
		//System.out.println(firstNode.getProperty("name"));
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.MRCACHILDOF,Direction.INCOMING );
		HashMap<Node,Integer> nodenumbers = new HashMap<Node,Integer>();
		HashMap<Integer,Node> numbernodes = new HashMap<Integer,Node>();
		int count = 0;
		for(Node friendnode: CHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			nodenumbers.put(friendnode, count);
			numbernodes.put(count,friendnode);
			count += 1;
		}
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter("graph_data.js"));
			outFile.write("{\"nodes\":[");
			for(int i=0; i<count;i++){
				Node tnode = numbernodes.get(i);
				if(tnode.hasRelationship(RelTypes.ISCALLED))
					outFile.write("{\"name\":\""+(tnode.getRelationships(RelTypes.ISCALLED)).iterator().next().getEndNode().getProperty("name")+"");
				else
					outFile.write("{\"name\":\"");
				//outFile.write("{\"name\":\""+tnode.getProperty("name")+"");
				outFile.write("\",\"group\":"+nodenumbers.get(tnode)+"");
				if(i+1<count)
					outFile.write("},");
				else
					outFile.write("}");
			}
			outFile.write("],\"links\":[");
			String outs = "";
			for(Node tnode: nodenumbers.keySet()){
				Iterable<Relationship> it = tnode.getRelationships(RelTypes.MRCACHILDOF,Direction.OUTGOING);
				for(Relationship trel : it){
					if(nodenumbers.get(trel.getStartNode())!= null && nodenumbers.get(trel.getEndNode())!=null){
						outs+="{\"source\":"+nodenumbers.get(trel.getStartNode())+"";
						outs+=",\"target\":"+nodenumbers.get(trel.getEndNode())+"";
						outs+=",\"value\":"+1+"";
						outs+="},";
					}
				}
			}
			outs = outs.substring(0, outs.length()-1);
			outFile.write(outs);
			outFile.write("]");
			outFile.write("}\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * given a taxonomic name, construct a newick string, breaking ties based on a source name
	 * this is just one example of one type of synthesis
	 */
	public void constructNewickSourceTieBreaker(String taxname, String sourcename){
		IndexHits<Node> hits = graphNodeIndex.get("name",taxname);
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING), 100);
		Node firstNode = hits.getSingle();
		hits.close();
		if(firstNode == null){
			System.out.println("name not found");
			return;
		}
		JadeNode root = new JadeNode();
		System.out.println(firstNode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
		TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.MRCACHILDOF,Direction.INCOMING );
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node,JadeNode> nodejademap = new HashMap<Node,JadeNode>();
		HashMap<JadeNode,Node> jadeparentmap = new HashMap<JadeNode,Node>();
		visited.add(firstNode);
		nodejademap.put(firstNode, root);
		for(Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			//if it is a tip, move back, 
			if(friendnode.hasRelationship(Direction.INCOMING, RelTypes.MRCACHILDOF))
				continue;
			else{
				Node curnode = friendnode;
				while(curnode.hasRelationship(Direction.OUTGOING, RelTypes.MRCACHILDOF)){
					//if it is visited continue
					if (visited.contains(curnode)){
						break;
					}else{
						JadeNode newnode = new JadeNode();
						if(curnode.hasRelationship(Direction.OUTGOING, RelTypes.ISCALLED)){
							newnode.setName((String)curnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
							newnode.setName(newnode.getName().replace("(", "_").replace(")","_").replace(" ", "_").replace(":", "_"));
						}
						Relationship keep = null;
						for(Relationship rel: curnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)){
							if(keep == null)
								keep = rel;
							if (((String)rel.getProperty("source")).compareTo(sourcename) == 0){
								keep = rel;
								break;
							}
							if(pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())){
								keep = rel;
							}
						}
						if(keep.hasProperty("branch_length")){
							newnode.setBL((Double)keep.getProperty("branch_length"));
						}
						nodejademap.put(curnode, newnode);
						visited.add(curnode);
						keepers.add(keep);
						if(pf.findSinglePath(keep.getEndNode(), firstNode) != null){
							curnode = keep.getEndNode();
							jadeparentmap.put(newnode, curnode);
						}else
							break;
					}
				}
			}
		}
		for(JadeNode jn:jadeparentmap.keySet()){
			nodejademap.get(jadeparentmap.get(jn)).addChild(jn);
		}
		JadeTree tree = new JadeTree(root);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(taxname+".tre"));
			outFile.write(tree.getRoot().getNewick(true));
			outFile.write(";\n");
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * this constructs a json with tie breaking and puts the alt parents
	 * in the assocOBjects for printing
	 * 
	 * need to be guided by some source in order to walk a particular tree
	 * works like , "altparents": [{"name": "Adoxaceae",nodeid:"nodeid"}, {"name":"Caprifoliaceae",nodeid:"nodeid"}]
	 */
	
	public void writeJSONWithAltParentsToFile(String taxname){
//		IndexHits<Node> hits = graphNodeIndex.get("name",taxname);
		IndexHits<Node> hits = taxNodeIndex.get("name",taxname);
		Node firstNode = hits.getSingle();
		hits.close();
		if(firstNode == null){
			System.out.println("name not found");
			return;
		}
//		String tofile = constructJSONAltParents(firstNode);
		ArrayList<Long>alt = new ArrayList<Long>();
		String tofile = constructJSONAltRels(firstNode,null,alt);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(taxname+".json"));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Used for creating a JSON string with a dominant tree, but with alternative 
	 * parents noted.
	 * For now the dominant source is hardcoded for testing. This needs to be an option
	 * once we can list and choose sources
	 */
	public String constructJSONAltParents(Node firstNode){
		String sourcename = "ATOL_III_ML_CP"; 
//		sourcename = "dipsacales_matK";
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING), 100);
		JadeNode root = new JadeNode();
		System.out.println(firstNode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
		root.setName((String)firstNode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
		TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.MRCACHILDOF,Direction.INCOMING );
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node,JadeNode> nodejademap = new HashMap<Node,JadeNode>();
		HashMap<JadeNode,Node> jadeparentmap = new HashMap<JadeNode,Node>();
		visited.add(firstNode);
		nodejademap.put(firstNode, root);
		root.assocObject("nodeid", firstNode.getId());
		for(Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			//if it is a tip, move back, 
			if(friendnode.hasRelationship(Direction.INCOMING, RelTypes.MRCACHILDOF))
				continue;
			else{
				Node curnode = friendnode;
				while(curnode.hasRelationship(Direction.OUTGOING, RelTypes.MRCACHILDOF)){
					//if it is visited continue
					if (visited.contains(curnode)){
						break;
					}else{
						JadeNode newnode = new JadeNode();
						if(curnode.hasRelationship(Direction.OUTGOING, RelTypes.ISCALLED)){
							newnode.setName((String)curnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
							newnode.setName(newnode.getName().replace("(", "_").replace(")","_").replace(" ", "_").replace(":", "_"));
						}
						Relationship keep = null;
						for(Relationship rel: curnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)){
							if(keep == null)
								keep = rel;
							if (((String)rel.getProperty("source")).compareTo(sourcename) == 0){
								keep = rel;
								break;
							}
							if(pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())){
								keep = rel;
							}
						}
						newnode.assocObject("nodeid", curnode.getId());
						ArrayList<Node> conflictnodes = new ArrayList<Node>();
						for(Relationship rel:curnode.getRelationships(Direction.OUTGOING, RelTypes.STREECHILDOF)){
							if(rel.getEndNode().getId() != keep.getEndNode().getId() && conflictnodes.contains(rel.getEndNode())==false){
								//check for nested conflicts
	//							if(pf.findSinglePath(keep.getEndNode(), rel.getEndNode())==null)
									conflictnodes.add(rel.getEndNode());
							}
						}
						newnode.assocObject("conflictnodes", conflictnodes);
						nodejademap.put(curnode, newnode);
						visited.add(curnode);
						keepers.add(keep);
						if(pf.findSinglePath(keep.getEndNode(), firstNode) != null){
							curnode = keep.getEndNode();
							jadeparentmap.put(newnode, curnode);
						}else
							break;
					}
				}
			}
		}
		for(JadeNode jn:jadeparentmap.keySet()){
			if(jn.getObject("conflictnodes")!=null){
				String confstr = "";
				@SuppressWarnings("unchecked")
				ArrayList<Node> cn = (ArrayList<Node>)jn.getObject("conflictnodes");
				if(cn.size()>0){
					confstr += ", \"altparents\": [";
					for(int i=0;i<cn.size();i++){
						String namestr = "";
						if(cn.get(i).hasRelationship(RelTypes.ISCALLED))
							namestr = (String) cn.get(i).getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name");
						confstr += "{\"name\": \""+namestr+"\",\"nodeid\":\""+cn.get(i).getId()+"\"}";
						if(i+1 != cn.size())
							confstr += ",";
					}
					confstr += "]\n";
					jn.assocObject("jsonprint", confstr);
				}
			}
			nodejademap.get(jadeparentmap.get(jn)).addChild(jn);
		}
		JadeTree tree = new JadeTree(root);
		root.assocObject("nodedepth", root.getNodeMaxDepth());
		String ret = "[\n";
		ret += tree.getRoot().getJSON(false);
		ret += "]\n";
		return ret;
	}
	
	/*
	 * This is similar to the JSON alt parents but differs in that it takes
	 * a dominant source string, and the alternative relationships in an
	 * array.
	 * 
	 * Also this presents a max depth and doesn't show species unless the firstnode 
	 * is the direct parent of a species
	 * 
	 * Limits the depth to 5
	 * 
	 * Goes back one parent
	 * 
	 * Should work with taxonomy or with the graph and determines this based on relationships
	 * around the node
	 */
	public String constructJSONAltRels(Node firstNode, String domsource, ArrayList<Long> altrels){
		cne.setStartNode(firstNode);
		cne.setChildThreshold(200);
		se.setStartNode(firstNode);
		int maxdepth = 3;
		boolean taxonomy = true;
		RelationshipType defaultchildtype = RelTypes.TAXCHILDOF;
		RelationshipType defaultsourcetype = RelTypes.TAXCHILDOF;
		String sourcename = "ncbi";
		if(firstNode.hasRelationship(RelTypes.MRCACHILDOF)){
			taxonomy = false;
			defaultchildtype = RelTypes.MRCACHILDOF;
			defaultsourcetype = RelTypes.STREECHILDOF;
			sourcename = "ATOL_III_ML_CP";
		}
		if(domsource != null)
			sourcename = domsource;

		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(defaultchildtype, Direction.OUTGOING), 100);
		JadeNode root = new JadeNode();
		if(taxonomy == false)
			root.setName((String)firstNode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
		else
			root.setName((String)firstNode.getProperty("name"));
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( defaultchildtype,Direction.INCOMING );
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node,JadeNode> nodejademap = new HashMap<Node,JadeNode>();
		HashMap<JadeNode,Node> jadeparentmap = new HashMap<JadeNode,Node>();
		nodejademap.put(firstNode, root);
		root.assocObject("nodeid", firstNode.getId());
		//These are the altrels that actually made it in the tree
		ArrayList<Long> returnrels = new ArrayList<Long>();
		for(Node friendnode : CHILDOF_TRAVERSAL.depthFirst().evaluator(Evaluators.toDepth(maxdepth)).evaluator(cne).evaluator(se).traverse(firstNode).nodes()){
//			System.out.println("visiting: "+friendnode.getProperty("name"));
			if (friendnode == firstNode)
				continue;
			Relationship keep = null;
			Relationship spreferred = null;
			Relationship preferred = null;
			
			for(Relationship rel: friendnode.getRelationships(Direction.OUTGOING, defaultsourcetype)){
				if(preferred == null)
					preferred = rel;
				if(altrels.contains(rel.getId())){
					keep = rel;
					returnrels.add(rel.getId());
					break;
				}else{
					if (((String)rel.getProperty("source")).compareTo(sourcename) == 0){
						spreferred = rel;
						break;
					}
					/*just for last ditch efforts
					 * if(pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())){
						preferred = rel;
					}*/
				}
			}
			if(keep == null){
				keep = spreferred;//prefer the source rel after an alt
				if(keep == null){
					continue;//if the node is not part of the main source just continue without making it
//					keep = preferred;//fall back on anything
				}
			}
			JadeNode newnode = new JadeNode();
			if(taxonomy == false){
				if(friendnode.hasRelationship(Direction.OUTGOING, RelTypes.ISCALLED)){
					newnode.setName((String)friendnode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name"));
					newnode.setName(newnode.getName().replace("(", "_").replace(")","_").replace(" ", "_").replace(":", "_"));
				}
			}else{
				newnode.setName(((String)friendnode.getProperty("name")).replace("(", "_").replace(")","_").replace(" ", "_").replace(":", "_"));
			}

			newnode.assocObject("nodeid", friendnode.getId());
			
			ArrayList<Relationship> conflictrels = new ArrayList<Relationship>();
			for(Relationship rel:friendnode.getRelationships(Direction.OUTGOING, defaultsourcetype)){
				if(rel.getEndNode().getId() != keep.getEndNode().getId() && conflictrels.contains(rel)==false){
					//check for nested conflicts
					//							if(pf.findSinglePath(keep.getEndNode(), rel.getEndNode())==null)
					conflictrels.add(rel);
				}
			}
			newnode.assocObject("conflictrels",conflictrels);
			nodejademap.put(friendnode, newnode);
			keepers.add(keep);
			visited.add(friendnode);
			if(firstNode != friendnode && pf.findSinglePath(keep.getStartNode(), firstNode) != null){
				jadeparentmap.put(newnode, keep.getEndNode());
			}
		}
		//build tree and work with conflicts
		System.out.println("root "+root.getChildCount());
		for(JadeNode jn:jadeparentmap.keySet()){
			if(jn.getObject("conflictrels")!=null){
				String confstr = "";
				@SuppressWarnings("unchecked")
				ArrayList<Relationship> cr = (ArrayList<Relationship>)jn.getObject("conflictrels");
				if(cr.size()>0){
					confstr += ", \"altrels\": [";
					for(int i=0;i<cr.size();i++){
						String namestr = "";
						if(taxonomy == false){
							if(cr.get(i).getEndNode().hasRelationship(RelTypes.ISCALLED))
								namestr = (String) cr.get(i).getEndNode().getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name");
						}else{
							namestr = (String)cr.get(i).getEndNode().getProperty("name");
						}
						confstr += "{\"parentname\": \""+namestr+"\",\"parentid\":\""+cr.get(i).getEndNode().getId()+"\",\"altrelid\":\""+cr.get(i).getId()+"\",\"source\":\""+cr.get(i).getProperty("source")+"\"}";
						if(i+1 != cr.size())
							confstr += ",";
					}
					confstr += "]\n";
					jn.assocObject("jsonprint", confstr);
				}
			}
			try{
//				System.out.println(jn.getName()+" "+nodejademap.get(jadeparentmap.get(jn)).getName());
				nodejademap.get(jadeparentmap.get(jn)).addChild(jn);
			}catch(java.lang.NullPointerException npe){
				continue;
			}
		}
		System.out.println("root "+root.getChildCount());
		
		//get the parent so we can move back one node
		Node parFirstNode = null;
		for(Relationship rels : firstNode.getRelationships(Direction.OUTGOING, defaultsourcetype)){
			if(((String)rels.getProperty("source")).compareTo(sourcename) == 0){
				parFirstNode = rels.getEndNode();
				break;
			}
		}
		JadeNode beforeroot = new JadeNode();
		if(parFirstNode != null){
			String namestr = "";
			if(taxonomy == false){
				if(parFirstNode.hasRelationship(RelTypes.ISCALLED))
					namestr = (String) parFirstNode.getSingleRelationship(RelTypes.ISCALLED, Direction.OUTGOING).getEndNode().getProperty("name");
			}else{
				namestr = (String)parFirstNode.getProperty("name");
			}
			beforeroot.assocObject("nodeid", parFirstNode.getId());
			beforeroot.setName(namestr);
			beforeroot.addChild(root);
		}else{
			beforeroot = root;
		}
		beforeroot.assocObject("nodedepth", beforeroot.getNodeMaxDepth());
		
		//construct the final string
		JadeTree tree = new JadeTree(beforeroot);
		String ret = "[\n";
		ret += tree.getRoot().getJSON(false);
		ret += ",{\"domsource\":\""+sourcename+"\"}]\n";
		return ret;
	}
	
}
