package opentree;


import gnu.trove.set.hash.TLongHashSet;
import jade.tree.deprecated.JadeNode;
import jade.tree.deprecated.JadeTree;

import org.opentree.utils.GeneralUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.LinkedList;
//import java.util.Iterator;
import java.util.Stack;

import opentree.constants.NodeProperty;
import opentree.constants.RelProperty;
//import opentree.RelTypes;
import opentree.constants.RelType;

import org.opentree.exceptions.TaxonNotFoundException;
import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

/**
 * GraphExporter is meant to export the graph in a variety of 
 * formats
 * GraphExporter major functions<br/>
 * =============================<br/>
 * mrpdump<br/>
 * write graph ml<br/>
 * write json<br/>
 */
public class GraphExporter extends GraphBase {

	public static final String[] GRAPHVIZ_COLORS = {"aliceblue","antiquewhite","antiquewhite1","antiquewhite2","antiquewhite3","antiquewhite4","aquamarine","aquamarine1","aquamarine2","aquamarine3","aquamarine4","azure","azure1","azure2","azure3","azure4","beige","bisque","bisque1","bisque2","bisque3","bisque4","black","blanchedalmond","blue","blue1","blue2","blue3","blue4","blueviolet","brown","brown1","brown2","brown3","brown4","burlywood","burlywood1","burlywood2","burlywood3","burlywood4","cadetblue","cadetblue1","cadetblue2","cadetblue3","cadetblue4","chartreuse","chartreuse1","chartreuse2","chartreuse3","chartreuse4","chocolate","chocolate1","chocolate2","chocolate3","chocolate4","coral","coral1","coral2","coral3","coral4","cornflowerblue","cornsilk","cornsilk1","cornsilk2","cornsilk3","cornsilk4","crimson","cyan","cyan1","cyan2","cyan3","cyan4","darkgoldenrod","darkgoldenrod1","darkgoldenrod2","darkgoldenrod3","darkgoldenrod4","darkgreen","darkkhaki","darkolivegreen","darkolivegreen1","darkolivegreen2","darkolivegreen3","darkolivegreen4","darkorange","darkorange1","darkorange2","darkorange3","darkorange4","darkorchid","darkorchid1","darkorchid2","darkorchid3","darkorchid4","darksalmon","darkseagreen","darkseagreen1","darkseagreen2","darkseagreen3","darkseagreen4","darkslateblue","darkslategray","darkslategray1","darkslategray2","darkslategray3","darkslategray4","darkslategrey","darkturquoise","darkviolet","deeppink","deeppink1","deeppink2","deeppink3","deeppink4","deepskyblue","deepskyblue1","deepskyblue2","deepskyblue3","deepskyblue4","dimgray","dimgrey","dodgerblue","dodgerblue1","dodgerblue2","dodgerblue3","dodgerblue4","firebrick","firebrick1","firebrick2","firebrick3","firebrick4","floralwhite","forestgreen","gainsboro","ghostwhite","gold","gold1","gold2","gold3","gold4","goldenrod","goldenrod1","goldenrod2","goldenrod3","goldenrod4","hotpink","hotpink1","hotpink2","hotpink3","hotpink4","indianred","indianred1","indianred2","indianred3","indianred4","indigo","invis","ivory","ivory1","ivory2","ivory3","ivory4","khaki","khaki1","khaki2","khaki3","khaki4","lavender","lavenderblush","lavenderblush1","lavenderblush2","lavenderblush3","lavenderblush4","lawngreen","lemonchiffon","lemonchiffon1","lemonchiffon2","lemonchiffon3","lemonchiffon4","lightblue","lightblue1","lightblue2","lightblue3","lightblue4","lightcoral","lightcyan","lightcyan1","lightcyan2","lightcyan3","lightcyan4","lightgoldenrod","lightgoldenrod1","lightgoldenrod2","lightgoldenrod3","lightgoldenrod4","lightgoldenrodyellow","lightgray","lightgrey","lightpink","lightpink1","lightpink2","lightpink3","lightpink4","lightsalmon","lightsalmon1","lightsalmon2","lightsalmon3","lightsalmon4","lightseagreen","lightskyblue","lightskyblue1","lightskyblue2","lightskyblue3","lightskyblue4","lightslateblue","lightslategray","lightslategrey","lightsteelblue","lightsteelblue1","lightsteelblue2","lightsteelblue3","lightsteelblue4","lightyellow","lightyellow1","lightyellow2","lightyellow3","lightyellow4","limegreen","linen","magenta","magenta1","magenta2","magenta3","magenta4","maroon","maroon1","maroon2","maroon3","maroon4","mediumaquamarine","mediumblue","mediumorchid","mediumorchid1","mediumorchid2","mediumorchid3","mediumorchid4","mediumpurple","mediumpurple1","mediumpurple2","mediumpurple3","mediumpurple4","mediumseagreen","mediumslateblue","mediumspringgreen","mediumturquoise","mediumvioletred","midnightblue","mintcream","mistyrose","mistyrose1","mistyrose2","mistyrose3","mistyrose4","moccasin","navajowhite","navajowhite1","navajowhite2","navajowhite3","navajowhite4","navy","navyblue","none","oldlace","olivedrab","olivedrab1","olivedrab2","olivedrab3","olivedrab4","orange","orange1","orange2","orange3","orange4","orangered","orangered1","orangered2","orangered3","orangered4","orchid","orchid1","orchid2","orchid3","orchid4","palegoldenrod","palegreen","palegreen1","palegreen2","palegreen3","palegreen4","paleturquoise","paleturquoise1","paleturquoise2","paleturquoise3","paleturquoise4","palevioletred","palevioletred1","palevioletred2","palevioletred3","palevioletred4","papayawhip","peachpuff","peachpuff1","peachpuff2","peachpuff3","peachpuff4","peru","pink","pink1","pink2","pink3","pink4","plum","plum1","plum2","plum3","plum4","powderblue","purple","purple1","purple2","purple3","purple4","red","red1","red2","red3","red4","rosybrown","rosybrown1","rosybrown2","rosybrown3","rosybrown4","royalblue","royalblue1","royalblue2","royalblue3","royalblue4","saddlebrown","salmon","salmon1","salmon2","salmon3","salmon4","sandybrown","seagreen","seagreen1","seagreen2","seagreen3","seagreen4","seashell","seashell1","seashell2","seashell3","seashell4","sienna","sienna1","sienna2","sienna3","sienna4","skyblue","skyblue1","skyblue2","skyblue3","skyblue4","slateblue","slateblue1","slateblue2","slateblue3","slateblue4","slategray","slategray1","slategray2","slategray3","slategray4","slategrey","snow","snow1","snow2","snow3","snow4","springgreen","springgreen1","springgreen2","springgreen3","springgreen4","steelblue","steelblue1","steelblue2","steelblue3","steelblue4","tan","tan1","tan2","tan3","tan4","thistle","thistle1","thistle2","thistle3","thistle4","tomato","tomato1","tomato2","tomato3","tomato4","transparent","turquoise","turquoise1","turquoise2","turquoise3","turquoise4","violet","violetred","violetred1","violetred2","violetred3","violetred4","wheat","wheat1","wheat2","wheat3","wheat4","white","whitesmoke","yellow","yellow1","yellow2","yellow3","yellow4","yellowgreen"};
	
	private SpeciesEvaluator se;
	private ChildNumberEvaluator cne;
	private TaxaListEvaluator tle;

	public GraphExporter(String graphname) {
		super(graphname);
		finishInitialization();
	}

	public GraphExporter(EmbeddedGraphDatabase embeddedGraph) {
		super(embeddedGraph);
		finishInitialization();
	}

	public GraphExporter(GraphDatabaseService gdb) {
		super(gdb);
		finishInitialization();
	}

	private void finishInitialization() {
		cne = new ChildNumberEvaluator();
		cne.setChildThreshold(100);
		se = new SpeciesEvaluator();
		tle = new TaxaListEvaluator();
	}

	public void writeGraphDot(String taxname, String outfile, boolean useTaxonomy) 
				throws TaxonNotFoundException {
		Node firstNode = findTaxNodeByName(taxname);
		String tofile = getDot(firstNode);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(outfile));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void writeGraphML(String taxname, String outfile, boolean useTaxonomy) 
				throws TaxonNotFoundException {
		Node firstNode = findTaxNodeByName(taxname);
		String tofile = getGraphML(firstNode,useTaxonomy,0);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(outfile));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void writeGraphMLDepth_ottolid(String ottolid, String outfile, boolean useTaxonomy, int depth) 
			throws TaxonNotFoundException {
	Node firstNode = findGraphTaxNodeByUID(ottolid);
	String tofile = getGraphMLSimple(firstNode,useTaxonomy,depth);
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
	 * creates a graphml for viewing in gephi Because gephi cannot view parallel lines, this will not output parallel edges. The properties that we have are
	 * node taxon edge sourcename (this is more complicated because of not outputting parallel edges) 
	 * 
	 * 
	 * @param startnode
	 * @return
	 */

	private String getGraphMLSimple(Node startnode,boolean taxonomy,int depth){
		StringBuffer retstring = new StringBuffer("<graphml>\n");
		retstring.append("<key id=\"d0\" for=\"node\" attr.name=\"taxon\" attr.type=\"string\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d1\" for=\"edge\" attr.name=\"sourcename\" attr.type=\"string\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<graph id=\"G\" edgedefault=\"directed\">\n");
		// get the list of nodes
		HashSet<Node> nodes = new HashSet<Node>();
		if(depth > 0){
		for (Node tnode : Traversal.description().evaluator(Evaluators.toDepth(depth)).relationships(RelType.STREECHILDOF, Direction.INCOMING)
				.traverse(startnode).nodes()) {
			nodes.add(tnode);
		}
		}else{
			for (Node tnode : Traversal.description().relationships(RelType.STREECHILDOF, Direction.INCOMING)
					.traverse(startnode).nodes()) {
				nodes.add(tnode);
			}
		}
		HashMap<Long, ArrayList<String>> sourcelists = new HashMap<Long, ArrayList<String>>();// number of sources for each node
		// do most of the calculations
		/*
		 * calculations here are effective parents and effective children
		 */
		HashSet<Node> removenodes = new HashSet<Node> ();//remove for taxonomy
		for (Node tnode : nodes) {
			HashMap<Long, Integer> parcount = new HashMap<Long, Integer>();
			ArrayList<String> slist = new ArrayList<String>();
			for (Relationship rel : tnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
				if (taxonomy == true || ((String) rel.getProperty("source")).compareTo("taxonomy") != 0) {
					if (parcount.containsKey(rel.getEndNode().getId()) == false) {
						parcount.put(rel.getEndNode().getId(), 0);
					}
					Integer tint = parcount.get(rel.getEndNode().getId()) + 1;
					parcount.put(rel.getEndNode().getId(), tint);
					slist.add((String) rel.getProperty("source"));
				}
			}
			if(slist.size()==0 && tnode!=startnode)
				removenodes.add(tnode);
			sourcelists.put(tnode.getId(), slist);
		}
		nodes.removeAll(removenodes);
		// calculate node support
		/*
		 * node support here is calculated as the number of outgoing edges divided by the total number of sources in the subtree obtained by getting the number
		 * of unique sources at each tip
		 */
		System.out.println("nodes traversed");
		//nothing calculated beyond, this is just for writing the file
		for(Node tnode: nodes){
			retstring.append("<node id=\"n"+tnode.getId()+"\">\n");
			if(tnode.hasProperty("name")){
				retstring.append("<data key=\"d0\">"+((String)tnode.getProperty("name")).replace("&", "_")+"</data>\n");
			}
			retstring.append("</node>\n");
		}
		for (Node tnode : nodes) {
			for(Relationship rel: tnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)){
				if (taxonomy == true || ((String) rel.getProperty("source")).compareTo("taxonomy") != 0) {
					if(nodes.contains(rel.getEndNode())){
						retstring.append("<edge source=\"n" + tnode.getId() + "\" target=\"n" + rel.getEndNode().getId() + "\">\n");
						//retstring.append("<data key=\"d3\">" + tedgesupport.get(tl) + "</data>\n");
						retstring.append("<data key=\"d1\">"+((String)rel.getProperty("source")).replace("&", "_")+"</data>\n");
						retstring.append("</edge>\n");
					}
				}
			}
		}
		System.out.println("nodes written");
		retstring.append("</graph>\n</graphml>\n");
		return retstring.toString();
	}

	
	private String getDot(Node startnode) {
		HashSet<Node> nodes = new HashSet<Node>();
		for (Node tnode : Traversal.description()
				.relationships(RelType.STREECHILDOF, Direction.INCOMING)
				.relationships(RelType.TAXCHILDOF, Direction.INCOMING)
				.traverse(startnode).nodes()) {
			nodes.add(tnode);
		}
		StringBuffer retstring = new StringBuffer("digraph G {\n");
		HashMap<Long, String> nd2Name = new HashMap<Long, String>();
		for (Node tnode: nodes) {
			String name;
			Long nid = tnode.getId();
			if(tnode.hasProperty("name")){
				name = "n" + nid + "_" + org.opentree.utils.GeneralUtils.scrubName(((String)tnode.getProperty("name")));
			} else {
				name = "n" + nid;
			}
			nd2Name.put(nid, name);
			retstring.append("  " + name + ";\n");
		}
		HashSet<Long> visitedRels = new HashSet<Long>();
		HashMap<String, Integer> relSource2ColInd = new HashMap<String, Integer>();
		TreeSet<String> relNames = new TreeSet<String>();
		for (Node tnode: nodes) {
			for (Relationship rel : tnode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
				String relSource = rel.getProperty("source").toString();
				if (!relSource.equals("taxonomy")) {
					relNames.add(relSource);
				}
			}
		}
//		relSource2ColInd.put("taxonomy", relNames.size()+1);
		int i = 0;
		for (String relSource : relNames) {
//			Integer colorOffset = new Integer(relSource2ColInd.size());
//			if (colorOffset > relColorArr.length - 1) {
//				colorOffset = new Integer(relColorArr.length - 1);
//			}
			assert ! relSource2ColInd.containsKey(relSource);
			relSource2ColInd.put(relSource, i++ % GRAPHVIZ_COLORS.length);
		}

		for (Node tnode: nodes) {
			for (Relationship rel : tnode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
				Long relid = rel.getId();
				if (!visitedRels.contains(relid)) {
					Long snid = rel.getStartNode().getId();
					Long enid = rel.getEndNode().getId();
					String sns = nd2Name.get(snid);
					String ens = nd2Name.get(enid);
					String relSource = rel.getProperty("source").toString();
					String relcolor;

					assert relSource2ColInd.containsKey(relSource);
					Integer colorOffset = relSource2ColInd.get(relSource);
					relcolor = GRAPHVIZ_COLORS[colorOffset];
					String rname = "r" + relid + " s" + relSource + " e" + rel.getProperty(RelProperty.SOURCE_EDGE_ID.propertyName);
					
					retstring.append("    " + sns + " -> " + ens + " [label=\"" + rname + "\" fontcolor=\"" + relcolor + "\" color=\"" + relcolor + "\"];\n");
					visitedRels.add(relid);
				}
			}
			for (Relationship rel : tnode.getRelationships(Direction.INCOMING, RelType.TAXCHILDOF)) {
				Long relid = rel.getId();
				if (!visitedRels.contains(relid)) {
					String rname = "r" + relid + " T";
					Long snid = rel.getStartNode().getId();
					Long enid = rel.getEndNode().getId();
					String sns = nd2Name.get(snid);
					String ens = nd2Name.get(enid);

					retstring.append("    " + sns + " -> " + ens + " [label=\"" + rname + "\" fontcolor=\"brown4\" color=\"brown4\" style=\"dashed\"];\n");
					visitedRels.add(relid);
				}
			}
		}
		for (Node tnode: nodes) {
			for (Relationship rel : tnode.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)) {
				Long relid = rel.getId();
				if (!visitedRels.contains(relid)) {
					String rname = "r" + relid;
					Long snid = rel.getStartNode().getId();
					Long enid = rel.getEndNode().getId();
					String sns = nd2Name.get(snid);
					String ens = nd2Name.get(enid);
					retstring.append("    " + sns + " -> " + ens + " [label=\"S\" style=\"bold\" color=\"black\"];\n");
					visitedRels.add(relid);
				}
			}
		}
		retstring.append("}\n");
		return retstring.toString();
	}
	
	/**
	 * creates a graphml for viewing in gephi Because gephi cannot view parallel lines, this will not output parallel edges. The properties that we have are
	 * node taxon edge sourcename (this is more complicated because of not outputting parallel edges) node support edge support node effective parents node
	 * average effective parents node delta average effective parents
	 * 
	 * measurement of effective parents is measured as Inverse Simpson index:$N=\frac{1}{\sum_{i=1}^{n}p_{i}^{2}}$
	 * 
	 * @param startnode
	 * @return
	 */
	private String getGraphML(Node startnode,boolean taxonomy,int depth){
		StringBuffer retstring = new StringBuffer("<graphml>\n");
		retstring.append("<key id=\"d0\" for=\"node\" attr.name=\"taxon\" attr.type=\"string\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d1\" for=\"edge\" attr.name=\"sourcename\" attr.type=\"string\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d2\" for=\"node\" attr.name=\"support\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d3\" for=\"edge\" attr.name=\"support\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d4\" for=\"node\" attr.name=\"effpar\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d5\" for=\"node\" attr.name=\"effch\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d6\" for=\"node\" attr.name=\"avgeffparsubtree\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<key id=\"d7\" for=\"node\" attr.name=\"avgdeltaparsubtree\" attr.type=\"double\">\n");
		retstring.append("<default></default>\n");
		retstring.append("</key>\n");
		retstring.append("<graph id=\"G\" edgedefault=\"directed\">\n");
		// get the list of nodes
		HashSet<Node> nodes = new HashSet<Node>();
		if(depth > 0){
		for (Node tnode : Traversal.description().evaluator(Evaluators.toDepth(depth)).relationships(RelType.STREECHILDOF, Direction.INCOMING)
				.traverse(startnode).nodes()) {
			nodes.add(tnode);
		}
		}else{
			for (Node tnode : Traversal.description().relationships(RelType.STREECHILDOF, Direction.INCOMING)
					.traverse(startnode).nodes()) {
				nodes.add(tnode);
			}
		}
		HashMap<Long, Double> nodesupport = new HashMap<Long, Double>();
		HashMap<Long, HashMap<Long, Double>> edgesupport = new HashMap<Long, HashMap<Long, Double>>();
		HashMap<Long, ArrayList<String>> sourcelists = new HashMap<Long, ArrayList<String>>();// number of sources for each node
		HashMap<Long, Double> effpar = new HashMap<Long, Double>();
		HashMap<Long, ArrayList<Double>> avgeffparnums = new HashMap<Long, ArrayList<Double>>();
		HashMap<Long, Double> effch = new HashMap<Long, Double>();
		HashMap<Long, HashMap<Long, Integer>> parcounts = new HashMap<Long, HashMap<Long, Integer>>();
		HashMap<Long, Double> avgdelta = new HashMap<Long, Double>();
		// do most of the calculations
		/*
		 * calculations here are effective parents and effective children
		 */
		for (Node tnode : nodes) {
			HashMap<Long, Integer> parcount = new HashMap<Long, Integer>();
			ArrayList<String> slist = new ArrayList<String>();
			int relcount = 0;
			for (Relationship rel : tnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
				if (taxonomy == true || ((String) rel.getProperty("source")).compareTo("taxonomy") != 0) {
					if (parcount.containsKey(rel.getEndNode().getId()) == false) {
						parcount.put(rel.getEndNode().getId(), 0);
					}
					Integer tint = parcount.get(rel.getEndNode().getId()) + 1;
					parcount.put(rel.getEndNode().getId(), tint);
					relcount += 1;
					slist.add((String) rel.getProperty("source"));
				}
			}
			sourcelists.put(tnode.getId(), slist);
			// calculate the inverse Simpson effective number of parents
			double efp = 0.0;
			for (Long tl : parcount.keySet()) {
				efp += (parcount.get(tl) / (double) relcount) * (parcount.get(tl) / (double) relcount);
			}
			efp = 1 / efp;
			effpar.put(tnode.getId(), efp);
			parcounts.put(tnode.getId(), parcount);

			// calculate the inverse Simpson effective number of children
			HashMap<Long, Integer> chcount = new HashMap<Long, Integer>();
			relcount = 0;
			for (Relationship rel : tnode.getRelationships(Direction.INCOMING, RelType.STREECHILDOF)) {
				if (taxonomy == true || ((String) rel.getProperty("source")).compareTo("taxonomy") != 0) {
					if (chcount.containsKey(rel.getStartNode().getId()) == false) {
						chcount.put(rel.getStartNode().getId(), 0);
					}
					Integer tint = chcount.get(rel.getStartNode().getId()) + 1;
					chcount.put(rel.getStartNode().getId(), tint);
					relcount += 1;
				}
			}
			double efc = 0.0;
			if (relcount > 0) {
				for (Long tl : chcount.keySet()) {
					efc += (chcount.get(tl) / (double) relcount) * (chcount.get(tl) / (double) relcount);
				}
				efc = 1 / efc;
			}
			effch.put(tnode.getId(), efc);
		}
		// calculate node support
		/*
		 * node support here is calculated as the number of outgoing edges divided by the total number of sources in the subtree obtained by getting the number
		 * of unique sources at each tip
		 */
		for (Node tnode : nodes) {
			
//			System.out.println(tnode.getProperty("name"));
			
			HashSet<String> sources = new HashSet<String>();
			long[] mrcas = (long[]) tnode.getProperty("mrca");
			for (int i = 0; i < mrcas.length; i++) {
				sources.addAll(sourcelists.get(mrcas[i]));
			}
			double supp = (double) sourcelists.get(tnode.getId()).size() / (double) sources.size();
			// give tips no support so they don't give weird looking
			if (tnode.hasRelationship(Direction.INCOMING, RelType.STREECHILDOF) == false) {
				nodesupport.put(tnode.getId(), 1.);
			} else {
				nodesupport.put(tnode.getId(), supp);
			}
			HashMap<Long, Integer> parcount = parcounts.get(tnode.getId());
			HashMap<Long, Double> edgesupp = new HashMap<Long, Double>();
			for (Long tl : parcount.keySet()) {
				double dedgesupp = (double) parcount.get(tl) / (double) sources.size();
				edgesupp.put(tl, dedgesupp);
			}
			edgesupport.put(tnode.getId(), edgesupp);
			// add effpar to parents
			Stack<Long> ndl = new Stack<Long>();
			for (Long tndl : parcounts.get(tnode.getId()).keySet()) {
				ndl.add(tndl);
			}
			HashSet<Long> done = new HashSet<Long>();
			while (ndl.isEmpty() == false) {
				Long curnodeid = ndl.pop();
				done.add(curnodeid);

//				System.out.println(parcounts.get(curnodeid));
				
				for (Long tndl : parcounts.get(curnodeid).keySet()) {
					if (done.contains(tndl) == false) {
						ndl.push(tndl);
					}
				}
				ArrayList<Double> tarl;
				if (avgeffparnums.containsKey(curnodeid) == false) {
					tarl = new ArrayList<Double>();
					tarl.add(0.0);
					tarl.add(0.0);
					avgeffparnums.put(curnodeid, tarl);
				}
				tarl = avgeffparnums.get(curnodeid);
				avgeffparnums.get(curnodeid).set(0, tarl.get(0) + (effpar.get(tnode.getId()) * nodesupport.get(tnode.getId())));
				avgeffparnums.get(curnodeid).set(1, tarl.get(1) + 1);
			}
		}
		// calculating the delta (startnode avgeffpar - endnode avgeffpar) for each relationship for a node
		// them calculate the weighted avg of the outgoing edge delta values and store as
		for (Node tnode : nodes) {
			Long curnodeid = tnode.getId();
			if (parcounts.containsKey(curnodeid) == false || avgeffparnums.containsKey(curnodeid) == false) {
				continue;
			}
			int totalweight = 0;
			double total = 0;
			for (Long tndl : parcounts.get(curnodeid).keySet()) {
				totalweight += parcounts.get(curnodeid).get(tndl);
				double st = (avgeffparnums.get(curnodeid).get(0) / avgeffparnums.get(curnodeid).get(1)) - 1;
				double ed = (avgeffparnums.get(tndl).get(0) / avgeffparnums.get(tndl).get(1)) - 1;
				total += ((st - ed) * parcounts.get(curnodeid).get(tndl));
			}
			total /= totalweight;
			avgdelta.put(curnodeid, total);
		}
		System.out.println("nodes traversed");
		Transaction tx = null;
		//nothing calculated beyond, this is just for writing the file
		for(Node tnode: nodes){
			try{
				tx = graphDb.beginTx();

				if(nodesupport.get(tnode.getId())==0){
					continue;
				}
				retstring.append("<node id=\"n"+tnode.getId()+"\">\n");
				if(tnode.hasProperty("name")){
					retstring.append("<data key=\"d0\">"+((String)tnode.getProperty("name")).replace("&", "_")+"</data>\n");
				}
				//not printing the ids as names
				//			else
				//				retstring.append("<data key=\"d0\">"+tnode.getId()+"</data>\n");
				//skip tips
				if(tnode.hasRelationship(Direction.INCOMING, RelType.STREECHILDOF)){
					retstring.append("<data key=\"d2\">"+nodesupport.get(tnode.getId())+"</data>\n");
					tnode.setProperty("nodesupport", nodesupport.get(tnode.getId()));
				}
				if(avgeffparnums.containsKey(tnode.getId())){
					double avgeffpar = avgeffparnums.get(tnode.getId()).get(0)/avgeffparnums.get(tnode.getId()).get(1);
					retstring.append("<data key=\"d6\">"+(avgeffpar)+"</data>\n");
					tnode.setProperty("avgeffpar", avgeffpar);
				}
				//else{
				//	retstring.append("<data key=\"d2\">1</data>\n");
				//}
				retstring.append("<data key=\"d4\">"+effpar.get(tnode.getId())+"</data>\n");
				tnode.setProperty("effpar", effpar.get(tnode.getId()));
				retstring.append("<data key=\"d5\">"+effch.get(tnode.getId())+"</data>\n");
				tnode.setProperty("effch", effch.get(tnode.getId()));
				if (avgdelta.containsKey(tnode.getId())==true){
					retstring.append("<data key=\"d7\">"+(avgdelta.get(tnode.getId())*nodesupport.get(tnode.getId()))+"</data>\n");
				}
				retstring.append("</node>\n");
				tx.success();
			}finally{
				tx.finish();
			}
		}
		for (Node tnode : nodes) {
			HashMap<Long, Double> tedgesupport = edgesupport.get(tnode.getId());
			for (Long tl : tedgesupport.keySet()) {
				retstring.append("<edge source=\"n" + tnode.getId() + "\" target=\"n" + tl + "\">\n");
				retstring.append("<data key=\"d3\">" + tedgesupport.get(tl) + "</data>\n");
				// retstring.append("<data key=\"d1\">"+((String)rel.getProperty("source")).replace("&", "_")+"</data>\n");
				retstring.append("</edge>\n");
			}
		}
		System.out.println("nodes written");
		retstring.append("</graph>\n</graphml>\n");
		return retstring.toString();
	}

	/**
	 * This will dump a csv for each of the relationships in the format nodeid,parentid,nodename,parentname,source,brlen
	 * @throws TaxonNotFoundException 
	 * 
	 */
	public void dumpCSV(String startnodes, String outfile, boolean taxonomy) throws TaxonNotFoundException {
		Node startnode = findTaxNodeByName(startnodes);
		if (startnode == null) {
			System.out.println("name not found");
			return;
		}
		try {
			PrintWriter outFile = new PrintWriter(new FileWriter(outfile));
			for (Node tnode : Traversal.description().relationships(RelType.MRCACHILDOF, Direction.INCOMING)
					.traverse(startnode).nodes()) {
				for (Relationship trel : tnode.getRelationships(RelType.STREECHILDOF)) {
					if (taxonomy == false) {
						if (((String) trel.getProperty("source")).equals("taxonomy"))
							continue;
					}
					outFile.write(trel.getStartNode().getId() + "," + trel.getEndNode().getId() + ",");
					if (trel.getStartNode().hasProperty("name")) {
						outFile.write(((String) trel.getStartNode().getProperty("name")).replace(",", "_"));
					}
					outFile.write(",");
					if (trel.getEndNode().hasProperty("name"))
						outFile.write(((String) trel.getEndNode().getProperty("name")).replace(",", "_"));
					outFile.write("," + trel.getProperty("source") + ",");
					if (trel.hasProperty("branch_length"))
						outFile.write(String.valueOf(trel.getProperty("branch_length")));
					outFile.write("\n");
				}
			}
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void mrpDump(String taxname, String outfile) throws TaxonNotFoundException  {
		Node firstNode = findTaxNodeByName(taxname);
		getMRPDump(firstNode,outfile);
	}

	/**
	 * This will return the mrp matrix for a node assuming that you only want to look at the tips So it will ignore internal taxonomic names
	 * 
	 * @param startnode
	 * @return string of the mrp matrix
	 */
	private void getMRPDump(Node startnode,String outfile) {
		TLongHashSet tids = new TLongHashSet();
		TLongHashSet nodeids = new TLongHashSet();
		HashMap<Long, TLongHashSet> mrpmap = new HashMap<Long, TLongHashSet>(); // key is the id for the taxon and the hashset is the list of nodes to which
																				  // the taxon is a member
		long[] dbnodei = (long[]) startnode.getProperty("mrca");
		for (long temp : dbnodei) {
			tids.add(temp);
			mrpmap.put(temp, new TLongHashSet());
		}
		TraversalDescription STREECHILDOF_TRAVERSAL = Traversal.description()
				.relationships(RelType.STREECHILDOF, Direction.INCOMING);
		for (Node tnd : STREECHILDOF_TRAVERSAL.traverse(startnode).nodes()) {
			long[] dbnodet = (long[]) tnd.getProperty("mrca");
			if (dbnodet.length == 1)
				continue;
			for (long temp : dbnodet) {
				mrpmap.get(temp).add(tnd.getId());
			}
			nodeids.add(tnd.getId());
		}
		try {
			FileWriter fw = new FileWriter(outfile);
			String retstring = String.valueOf(tids.size()) + " " + String.valueOf(nodeids.size()) + "\n";
			fw.write(retstring);
			for (Long nd : tids.toArray()) {
				StringBuffer retstring2 = new StringBuffer("");
				retstring2.append((String) graphDb.getNodeById(nd).getProperty(NodeProperty.TAX_UID.propertyName));
				retstring2.append("\t");
				for (Long nnid : nodeids.toArray()) {
					if (mrpmap.get(nd).contains(nnid)) {
						retstring2.append("1");
					} else {
						retstring2.append("0");
					}
				}
				retstring2.append("\n");
				fw.write(retstring2.toString());
			}
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * this constructs a json with tie breaking and puts the alt parents in the assocOBjects for printing
	 * 
	 * need to be guided by some source in order to walk a particular tree works like , "altparents": [{"name": "Adoxaceae",nodeid:"nodeid"},
	 * {"name":"Caprifoliaceae",nodeid:"nodeid"}]
	 */

	public void writeJSONWithAltParentsToFile(String taxname)
				throws TaxonNotFoundException {
		Node firstNode = findTaxNodeByName(taxname);
		if (firstNode == null) {
			System.out.println("name not found");
			return;
		}
		// String tofile = constructJSONAltParents(firstNode);
		ArrayList<Long> alt = new ArrayList<Long>();
		String tofile = constructJSONAltRels(firstNode, null, alt, 3);
		PrintWriter outFile;
		try {
			outFile = new PrintWriter(new FileWriter(taxname + ".json"));
			outFile.write(tofile);
			outFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Used for creating a JSON string with a dominant tree, but with alternative parents noted. For now the dominant source is hardcoded for testing. This
	 * needs to be an option once we can list and choose sources
	 */
	public String constructJSONAltParents(Node firstNode) {
		String sourcename = "ATOL_III_ML_CP";
		// sourcename = "dipsacales_matK";
		PathFinder<Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelType.MRCACHILDOF, Direction.OUTGOING), 100);
		JadeNode root = new JadeNode();
		root.setName((String) firstNode.getProperty("name"));
		TraversalDescription MRCACHILDOF_TRAVERSAL = Traversal.description()
				.relationships(RelType.MRCACHILDOF, Direction.INCOMING);
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node, JadeNode> nodejademap = new HashMap<Node, JadeNode>();
		HashMap<JadeNode, Node> jadeparentmap = new HashMap<JadeNode, Node>();
		visited.add(firstNode);
		nodejademap.put(firstNode, root);
		root.assocObject("nodeid", firstNode.getId());
		for (Node friendnode : MRCACHILDOF_TRAVERSAL.traverse(firstNode).nodes()) {
			// if it is a tip, move back,
			if (friendnode.hasRelationship(Direction.INCOMING, RelType.MRCACHILDOF)) {
				continue;
			} else {
				Node curnode = friendnode;
				while (curnode.hasRelationship(Direction.OUTGOING, RelType.MRCACHILDOF)) {
					// if it is visited continue
					if (visited.contains(curnode)) {
						break;
					} else {
						JadeNode newnode = new JadeNode();
						if (curnode.hasProperty("name")) {
							newnode.setName((String) curnode.getProperty("name"));
							newnode.setName(newnode.getName().replace("(", "_").replace(")", "_").replace(" ", "_").replace(":", "_"));
						}
						Relationship keep = null;
						for (Relationship rel : curnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
							if (keep == null) {
								keep = rel;
							}
							if (((String) rel.getProperty("source")).compareTo(sourcename) == 0) {
								keep = rel;
								break;
							}
							if (pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())) {
								keep = rel;
							}
						}
						newnode.assocObject("nodeid", curnode.getId());
						ArrayList<Node> conflictnodes = new ArrayList<Node>();
						for (Relationship rel : curnode.getRelationships(Direction.OUTGOING, RelType.STREECHILDOF)) {
							if (rel.getEndNode().getId() != keep.getEndNode().getId() && conflictnodes.contains(rel.getEndNode()) == false) {
								// check for nested conflicts
								// if(pf.findSinglePath(keep.getEndNode(), rel.getEndNode())==null)
								conflictnodes.add(rel.getEndNode());
							}
						}
						newnode.assocObject("conflictnodes", conflictnodes);
						nodejademap.put(curnode, newnode);
						visited.add(curnode);
						keepers.add(keep);
						if (pf.findSinglePath(keep.getEndNode(), firstNode) != null) {
							curnode = keep.getEndNode();
							jadeparentmap.put(newnode, curnode);
						} else {
							break;
						}
					}
				}
			}
		}
		for (JadeNode jn : jadeparentmap.keySet()) {
			if (jn.getObject("conflictnodes") != null) {
				String confstr = "";
				@SuppressWarnings("unchecked")
				ArrayList<Node> cn = (ArrayList<Node>) jn.getObject("conflictnodes");
				if (cn.size() > 0) {
					confstr += ", \"altparents\": [";
					for (int i = 0; i < cn.size(); i++) {
						String namestr = "";
						if (cn.get(i).hasProperty("name")) {
							namestr = (String) cn.get(i).getProperty("name");
						}
						confstr += "{\"name\": \"" + namestr + "\",\"nodeid\":\"" + cn.get(i).getId() + "\"}";
						if (i + 1 != cn.size()) {
							confstr += ",";
						}
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
	 * This is similar to the JSON alt parents but differs in that it takes a dominant source string, and the alternative relationships in an array.
	 * 
	 * Also this presents a max depth and doesn't show species unless the firstnode is the direct parent of a species
	 * 
	 * Limits the depth to 5
	 * 
	 * Goes back one parent
	 * 
	 * Should work with taxonomy or with the graph and determines this based on relationships around the node
	 */
	public String constructJSONAltRels(Node firstNode,
									   String domsource, 
									   ArrayList<Long> altrels,
									   int maxdepth) {
		cne.setStartNode(firstNode);
		cne.setChildThreshold(200);
		se.setStartNode(firstNode);
		boolean taxonomy = true;
		RelationshipType defaultchildtype = RelType.TAXCHILDOF;
		RelationshipType defaultsourcetype = RelType.TAXCHILDOF;
		String sourcename = "ncbi";
		if (firstNode.hasRelationship(RelType.MRCACHILDOF)) {
			taxonomy = false;
			defaultchildtype = RelType.MRCACHILDOF;
			defaultsourcetype = RelType.STREECHILDOF;
			sourcename = "ATOL_III_ML_CP";
		}
		if (domsource != null) {
			sourcename = domsource;
		}

		PathFinder<Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(defaultchildtype, Direction.OUTGOING), 100);
		JadeNode root = new JadeNode();
		root.setName((String) firstNode.getProperty("name"));
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
				.relationships(defaultchildtype, Direction.INCOMING);
		ArrayList<Node> visited = new ArrayList<Node>();
		ArrayList<Relationship> keepers = new ArrayList<Relationship>();
		HashMap<Node, JadeNode> nodejademap = new HashMap<Node, JadeNode>();
		HashMap<JadeNode, Node> jadeparentmap = new HashMap<JadeNode, Node>();
		//@diff visited.add(firstNode);
		nodejademap.put(firstNode, root);
		root.assocObject("nodeid", firstNode.getId());
		// These are the altrels that actually made it in the tree
		ArrayList<Long> returnrels = new ArrayList<Long>();
		for (Node friendnode : CHILDOF_TRAVERSAL.depthFirst().evaluator(Evaluators.toDepth(maxdepth)).evaluator(cne).evaluator(se).traverse(firstNode).nodes()) {
			// System.out.println("visiting: "+friendnode.getProperty("name"));
			if (friendnode == firstNode) {
				continue;
			}
			Relationship keep = null;
			Relationship spreferred = null;
			Relationship preferred = null;

			for (Relationship rel : friendnode.getRelationships(Direction.OUTGOING, defaultsourcetype)) {
				if (preferred == null) {
					preferred = rel;
				}
				if (altrels.contains(rel.getId())) {
					keep = rel;
					returnrels.add(rel.getId());
					break;
				} else {
					if (((String) rel.getProperty("source")).compareTo(sourcename) == 0) {
						spreferred = rel;
						break;
					}
					/*
					 * just for last ditch efforts if(pf.findSinglePath(rel.getEndNode(), firstNode) != null || visited.contains(rel.getEndNode())){ preferred =
					 * rel; }
					 */
				}
			}
			if (keep == null) {
				keep = spreferred;// prefer the source rel after an alt
				if (keep == null) {
					continue;// if the node is not part of the main source just continue without making it
					// keep = preferred;//fall back on anything
				}
			}
			JadeNode newnode = new JadeNode();
			if (taxonomy == false) {
				if (friendnode.hasProperty("name")) {
					newnode.setName((String) friendnode.getProperty("name"));
					newnode.setName(newnode.getName().replace("(", "_").replace(")", "_").replace(" ", "_").replace(":", "_"));
				}
			} else {
				newnode.setName(((String) friendnode.getProperty("name")).replace("(", "_").replace(")", "_").replace(" ", "_").replace(":", "_"));
			}

			newnode.assocObject("nodeid", friendnode.getId());

			ArrayList<Relationship> conflictrels = new ArrayList<Relationship>();
			for (Relationship rel : friendnode.getRelationships(Direction.OUTGOING, defaultsourcetype)) {
				if (rel.getEndNode().getId() != keep.getEndNode().getId() && conflictrels.contains(rel) == false) {
					// check for nested conflicts
					// if(pf.findSinglePath(keep.getEndNode(), rel.getEndNode())==null)
					conflictrels.add(rel);
				}
			}
			newnode.assocObject("conflictrels", conflictrels);
			nodejademap.put(friendnode, newnode);
			keepers.add(keep);
			visited.add(friendnode);
			if (firstNode != friendnode && pf.findSinglePath(keep.getStartNode(), firstNode) != null) {
				jadeparentmap.put(newnode, keep.getEndNode());
			}
		}
		// build tree and work with conflicts
		System.out.println("root " + root.getChildCount());
		for (JadeNode jn : jadeparentmap.keySet()) {
			if (jn.getObject("conflictrels") != null) {
				String confstr = "";
				@SuppressWarnings("unchecked")
				ArrayList<Relationship> cr = (ArrayList<Relationship>) jn.getObject("conflictrels");
				if (cr.size() > 0) {
					confstr += ", \"altrels\": [";
					for (int i = 0; i < cr.size(); i++) {
						String namestr = "";
						if (taxonomy == false) {
							if (cr.get(i).getEndNode().hasProperty("name")) {
								namestr = (String) cr.get(i).getEndNode().getProperty("name");
							}
						} else {
							namestr = (String) cr.get(i).getEndNode().getProperty("name");
						}
						confstr += "{\"parentname\": \"" + namestr + "\",\"parentid\":\"" + cr.get(i).getEndNode().getId() + "\",\"altrelid\":\""
								+ cr.get(i).getId() + "\",\"source\":\"" + cr.get(i).getProperty("source") + "\"}";
						if (i + 1 != cr.size()) {
							confstr += ",";
						}
					}
					confstr += "]\n";
					jn.assocObject("jsonprint", confstr);
				}
			}
			try {
				// System.out.println(jn.getName()+" "+nodejademap.get(jadeparentmap.get(jn)).getName());
				nodejademap.get(jadeparentmap.get(jn)).addChild(jn);
			} catch (java.lang.NullPointerException npe) {
				continue;
			}
		}
		System.out.println("root " + root.getChildCount());

		// get the parent so we can move back one node
		Node parFirstNode = null;
		for (Relationship rels : firstNode.getRelationships(Direction.OUTGOING, defaultsourcetype)) {
			if (((String) rels.getProperty("source")).compareTo(sourcename) == 0) {
				parFirstNode = rels.getEndNode();
				break;
			}
		}
		JadeNode beforeroot = new JadeNode();
		if (parFirstNode != null) {
			String namestr = "";
			if (taxonomy == false) {
				if (parFirstNode.hasProperty("name")) {
					namestr = (String) parFirstNode.getProperty("name");
				}
			} else {
				namestr = (String) parFirstNode.getProperty("name");
			}
			beforeroot.assocObject("nodeid", parFirstNode.getId());
			beforeroot.setName(namestr);
			beforeroot.addChild(root);
		} else {
			beforeroot = root;
		}
		beforeroot.assocObject("nodedepth", beforeroot.getNodeMaxDepth());

		// construct the final string
		JadeTree tree = new JadeTree(beforeroot);
		String ret = "[\n";
		ret += tree.getRoot().getJSON(false);
		ret += ",{\"domsource\":\"" + sourcename + "\"}]\n";
		return ret;
	}

	// ====================================== extracting Synthetic trees from the db ==========================================

	
	public JadeTree buildSyntheticTreeForWeb(Node startNode, String synthTreeName, int maxNodes, boolean storerelid){
        TraversalDescription CHILDOF_TRAVERSAL = Traversal.description().relationships(RelType.SYNTHCHILDOF, Direction.INCOMING);
        JadeNode root = new JadeNode();
        HashMap<Node,JadeNode> traveledNodes = new HashMap<Node,JadeNode>();
        int maxtips = maxNodes;
        HashSet<Node> includednodes = new HashSet<Node>();
        //used to make sure that we add all the children of the startnode
        HashSet<Node> parents = new HashSet<Node>();
        for (Node curGraphNode : CHILDOF_TRAVERSAL.breadthFirst().traverse(startNode).nodes()){//.evaluator(Evaluators.toDepth(3)).evaluator(cne).evaluator(se).traverse(startNode).nodes()) {
        	if(includednodes.size()>maxtips && parents.size() > 1){
        		break;
        	}
        	JadeNode curNode = null;
    		if (curGraphNode == startNode){
    			curNode = root;
    		}else{
    			curNode = new JadeNode();
    		}
    		traveledNodes.put(curGraphNode, curNode);
            if (curGraphNode.hasProperty("name")) {
//                curNode.setName(GeneralUtils.cleanName(String.valueOf(curGraphNode.getProperty("name"))));
                curNode.setName(GeneralUtils.scrubName(String.valueOf(curGraphNode.getProperty("name"))));
            }
            curNode.assocObject("nodeID", String.valueOf(curGraphNode.getId()));
            JadeNode parentJadeNode = null;
            Relationship incomingRel = null;
            if (curGraphNode.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF) && curGraphNode != startNode){
            	Node parentGraphNode = curGraphNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
            	parents.add(parentGraphNode);
            	if(includednodes.contains(parentGraphNode)){
            		includednodes.remove(parentGraphNode);
            	}
            	if(traveledNodes.containsKey(parentGraphNode)){
            		parentJadeNode = traveledNodes.get(parentGraphNode);
            		incomingRel = curGraphNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING);
            	}
            }
            includednodes.add(curGraphNode);
            // add the current node to the tree we're building
            if (curGraphNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).hasProperty("supporting_sources")){
            	curNode.assocObject("supporting_sources", (String [] ) curGraphNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getProperty("supporting_sources"));
            }
            
            if (parentJadeNode != null) {
            	parentJadeNode.addChild(curNode);
            	if(storerelid == true)
            		curNode.assocObject("relid", incomingRel.getId());
                if (incomingRel.hasProperty("branch_length")) {
                    curNode.setBL((Double) incomingRel.getProperty("branch_length"));
                }
            }
            
            // get the immediate synth children of the current node
            LinkedList<Relationship> synthChildRels = new LinkedList<Relationship>();
            int numchild = 0;
            for (Relationship synthChildRel : curGraphNode.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)) {
            	if (synthTreeName.equals(String.valueOf(synthChildRel.getProperty("name"))))	{
            		synthChildRels.add(synthChildRel);
            		numchild += 1;
            	}
            }
            if(numchild > 0){
            	//need to add a property of the jadenode if there are children, so if they aren't included, we can color it
            	curNode.assocObject("haschild", true);
            	curNode.assocObject("numchild", numchild);
            }
            
        }
        if (startNode.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF)){
        	Node curGraphNode = startNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();
        	JadeNode newroot = new JadeNode();
            if (curGraphNode.hasProperty("name")) {
//                newroot.setName(GeneralUtils.cleanName(String.valueOf(curGraphNode.getProperty("name"))));
                newroot.setName(GeneralUtils.scrubName(String.valueOf(curGraphNode.getProperty("name"))));
            }
            newroot.assocObject("nodeID", String.valueOf(curGraphNode.getId()));
            if(storerelid == true){
            	root.assocObject("relid", startNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getId());
            	if(curGraphNode.hasRelationship(Direction.OUTGOING, RelType.SYNTHCHILDOF)){
            		newroot.assocObject("relid", curGraphNode.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getId());
            	}
            }
        	newroot.addChild(root);
        	return new JadeTree(newroot);
        }
        //(add a bread crumb)
        return new JadeTree(root);
       // return new JadeTree(buildSyntheticTreeRecur(startNode, parentJadeNode, incomingRel, DRAFTTREENAME));
	}
	

	public JadeTree buildTaxonomyTreeForWeb(Node startNode, int maxNodes,boolean storerelid){
        TraversalDescription CHILDOF_TRAVERSAL = Traversal.description().relationships(RelType.TAXCHILDOF, Direction.INCOMING);
        JadeNode root = new JadeNode();
        HashMap<Node,JadeNode> traveledNodes = new HashMap<Node,JadeNode>();
        int maxtips = maxNodes;
        HashSet<Node> includednodes = new HashSet<Node>();
        HashSet<Node> parents = new HashSet<Node>();
        for (Node curGraphNode : CHILDOF_TRAVERSAL.breadthFirst().traverse(startNode).nodes()){
        	if(includednodes.size()>maxtips && parents.size() > 1){
        		break;
        	}
        	JadeNode curNode = null;
    		if (curGraphNode == startNode){
    			curNode = root;
    		}else{
    			curNode = new JadeNode();
    		}
    		traveledNodes.put(curGraphNode, curNode);
            if (curGraphNode.hasProperty("name")) {
//                curNode.setName(GeneralUtils.cleanName(String.valueOf(curGraphNode.getProperty("name"))));
                curNode.setName(GeneralUtils.scrubName(String.valueOf(curGraphNode.getProperty("name"))));
            }
            curNode.assocObject("nodeID", String.valueOf(curGraphNode.getId()));
            JadeNode parentJadeNode = null;
            Relationship incomingRel = null;
            if (curGraphNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF) && curGraphNode != startNode){
            	Node parentGraphNode = curGraphNode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
            	if(includednodes.contains(parentGraphNode)){
            		includednodes.remove(parentGraphNode);
            	}
            	parents.add(parentGraphNode);
            	if(traveledNodes.containsKey(parentGraphNode)){
            		parentJadeNode = traveledNodes.get(parentGraphNode);
            		incomingRel = curGraphNode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING);
            	}
            }
            includednodes.add(curGraphNode);
            // add the current node to the tree we're building

            HashSet<String> supportingsources = new HashSet<String> ();
            for(Relationship rels: curGraphNode.getRelationships(RelType.STREECHILDOF, Direction.OUTGOING)){
            	if(((String)rels.getProperty("source")).equals("taxonomy")==false){
            		supportingsources.add((String)rels.getProperty("source"));
            	}
            }
            if(supportingsources.size()>0){
            	String [] sendstring = new String[supportingsources.size()];
            	supportingsources.toArray(sendstring);
            	curNode.assocObject("supporting_sources", sendstring);
            }
            	
            if (parentJadeNode != null) {
            	parentJadeNode.addChild(curNode);
            	if(storerelid == true)
            		curNode.assocObject("relid", incomingRel.getId());
            }
            // get the immediate synth children of the current node
            LinkedList<Relationship> taxChildRels = new LinkedList<Relationship>();
            int numchild = 0;
            for (Relationship taxChildRel : curGraphNode.getRelationships(Direction.INCOMING, RelType.TAXCHILDOF)) {
            	taxChildRels.add(taxChildRel);
            	numchild += 1;
            }
            if(numchild > 0){
            	//need to add a property of the jadenode if there are children, so if they aren't included, we can color it
            	curNode.assocObject("haschild", true);
            	curNode.assocObject("numchild", numchild);
            }
            //adding information for gbif display
            if(curGraphNode.hasProperty("tax_source")){
            	String tsour = (String)curGraphNode.getProperty("tax_source");
            	if (tsour.contains("ncbi")==false){
            		curNode.assocObject("onlygbif", true);
            	}
            }
            
        }
        if (startNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)){
        	Node curGraphNode = startNode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
        	JadeNode newroot = new JadeNode();
            if (curGraphNode.hasProperty("name")) {
//                newroot.setName(GeneralUtils.cleanName(String.valueOf(curGraphNode.getProperty("name"))));
                newroot.setName(GeneralUtils.scrubName(String.valueOf(curGraphNode.getProperty("name"))));
            }
            newroot.assocObject("nodeID", String.valueOf(curGraphNode.getId()));
            if(storerelid == true){
            	root.assocObject("relid", startNode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getId());
            	if(curGraphNode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)){
            		newroot.assocObject("relid", curGraphNode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getId());
            	}
            }
        	newroot.addChild(root);
        	return new JadeTree(newroot);
        }
        //(add a bread crumb)
        return new JadeTree(root);
       // return new JadeTree(buildSyntheticTreeRecur(startNode, parentJadeNode, incomingRel, DRAFTTREENAME));
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
	}
}
