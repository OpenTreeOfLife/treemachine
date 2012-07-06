package opentree.plugins;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;


import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.Expander;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

import jade.tree.*;

public class GetJsons extends ServerPlugin {
	protected static enum RelTypes implements RelationshipType{
	    MRCACHILDOF, //standard rel for graph db, from node to parent
	    TAXCHILDOF, //standard rel for tax db, from node to parent
	    STREECHILDOF, //standard rel for input tree, from node to parent  
	    ISCALLED // is called ,from node in graph of life to node in tax graph 
	}
	
	@Description( "Find the nodes with the name given." )
	@PluginTarget( Node.class )
    public String getConflictJson(@Source Node source){
		String sourcename = "ATOL_III_ML_CP"; 
//		sourcename = "dipsacales_matK";
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING), 100);
		Node firstNode = source;
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
		return "["+tree.getRoot().getJSON(false)+"]";
	}
}
