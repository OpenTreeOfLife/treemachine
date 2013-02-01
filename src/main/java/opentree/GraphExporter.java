package opentree;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import opentree.GraphBase.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

public class GraphExporter extends GraphBase{

	public GraphExporter(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		graphNodeIndex = graphDb.index().forNodes( "graphNamedNodes" );
		sourceRelIndex = graphDb.index().forRelationships("sourceRels");
		sourceRootIndex = graphDb.index().forNodes("sourceRootNodes");
	}
	
	public GraphExporter(EmbeddedGraphDatabase graphn){
		graphDb = graphn;
		graphNodeIndex = graphDb.index().forNodes( "graphNamedNodes" );
		sourceRelIndex = graphDb.index().forRelationships("sourceRels");
		sourceRootIndex = graphDb.index().forNodes("sourceRootNodes");
	}

	public void writeGraphML(String taxname, String outfile){
		Node firstNode = findTaxNodeByName(taxname);
		String tofile = getGraphML(firstNode);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(outfile));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String getGraphML(Node startnode){
		String retstring = "<graphml>\n";
		retstring += "<key id=\"d0\" for=\"node\" attr.name=\"taxon\" attr.type=\"string\">\n";
	    retstring += "<default></default>\n";
	    retstring += "</key>\n";
	    retstring += "<key id=\"d1\" for=\"edge\" attr.name=\"sourcename\" attr.type=\"string\">\n";
	    retstring += "<default></default>\n";
	    retstring += "</key>\n";
	    retstring += "<graph id=\"G\" edgedefault=\"directed\">\n";
		HashSet<Node> nodes = new HashSet<Node>();
		for(Node tnode:  Traversal.description().relationships(RelTypes.STREECHILDOF, Direction.INCOMING)
				.traverse(startnode).nodes()){
			nodes.add(tnode);
		}
		System.out.println("nodes traversed: "+nodes.size());
		//could do this in one loop but it is just cleaner to read like this for now
		Iterator<Node> itn = nodes.iterator();
		while(itn.hasNext()){
			Node nxt = itn.next();
			retstring += "<node id=\"n"+nxt.getId()+"\">\n";
			if(nxt.hasProperty("name"))
				retstring += "<data key=\"d0\">"+((String)nxt.getProperty("name")).replace("&", "_")+"</data>\n";
			else
				retstring += "<data key=\"d0\">"+nxt.getId()+"</data>\n";
			retstring += "</node>\n";
		}
		System.out.println("nodes written");
		itn = nodes.iterator();
		while(itn.hasNext()){
			Node nxt = itn.next();
			for(Relationship rel: nxt.getRelationships(RelTypes.STREECHILDOF, Direction.INCOMING)){
				if(nodes.contains(rel.getStartNode()) && nodes.contains(rel.getEndNode()) ){
//						&& 
//						((String)rel.getProperty("source")).compareTo("taxonomy") != 0){
					retstring += "<edge source=\"n"+rel.getStartNode().getId()+"\" target=\"n"+rel.getEndNode().getId()+"\">\n"; 
					retstring += "<data key=\"d1\">"+((String)rel.getProperty("source")).replace("&", "_")+"</data>\n";
					retstring += "</edge>\n";
				}
			}
		}
		System.out.println("edges written");
		retstring += "</graph>\n</graphml>\n";
		return retstring;
	}
	
	public void mrpDump(String taxname, String outfile){
		Node firstNode = findTaxNodeByName(taxname);
		String tofile = getMRPDump(firstNode);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(outfile));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This will return the mrp matrix for a node assuming that you only want to look at the tips
	 * So it will ignore internal taxonomic names
	 * @param startnode
	 * @return string of the mrp matrix
	 */
	private String getMRPDump(Node startnode){
		HashSet<Long> tids = new HashSet<Long>();
		HashSet<Long> nodeids = new HashSet<Long>();
		HashMap<Long,HashSet<Long>> mrpmap = new HashMap<Long,HashSet<Long>>(); //key is the id for the taxon and the hashset is the list of nodes to which the taxon is a member
		long [] dbnodei = (long []) startnode.getProperty("mrca");
		for(long temp:dbnodei){tids.add(temp);mrpmap.put(temp, new HashSet<Long>());}
		TraversalDescription STREECHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.STREECHILDOF,Direction.INCOMING );
		for(Node tnd:STREECHILDOF_TRAVERSAL.traverse(startnode).nodes()){
			long [] dbnodet = (long []) tnd.getProperty("mrca");
			if (dbnodet.length == 1)
				continue;
			for(long temp:dbnodet){
				mrpmap.get(temp).add(tnd.getId());
			}
			nodeids.add(tnd.getId());
		}
		String retstring = String.valueOf(tids.size())+" "+String.valueOf(nodeids.size())+"\n";
		for(Long nd: tids){
			retstring += (String)graphDb.getNodeById(nd).getProperty("name");
			retstring += "\t";
			for(Long nnid: nodeids){
				if (mrpmap.get(nd).contains(nnid)){
					retstring += "1";
				}else{
					retstring += "0";
				}
			}
			retstring += "\n";
		}
		return retstring;
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		

	}

}
