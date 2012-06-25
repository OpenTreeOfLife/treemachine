package opentree;


import org.neo4j.graphdb.*;
import org.neo4j.graphdb.Traverser.Order;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.*;
import jade.tree.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hello world!
 *
 */
public class TaxonomyLoader extends TaxonomyBase{
	int transaction_iter = 100000;
	int LARGE = 100000000;
	final TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
	        .relationships( RelTypes.TAXCHILDOF,Direction.OUTGOING );
	
	public TaxonomyLoader(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		nodeIndex = graphDb.index().forNodes( "nodes" );
	}
	
	private Integer sum(ArrayList<Integer> si){
		Integer ret = 0;
		for(int i=0;i<si.size();i++)
			ret += si.get(i);
		return ret;
	}
	
	public void addInitialTaxonomyTableIntoGraph(String filename){
    	String str = "";
    	String shortfilename = filename.split("/")[filename.split("/").length-1];
    	int count = 0;
    	HashMap<String, Node> dbnodes = new HashMap<String, Node>();
    	HashMap<String, String> parents = new HashMap<String, String>();
    	Transaction tx;
		ArrayList<String> templines = new ArrayList<String>();
		try{
			//create the root node
			tx = graphDb.beginTx();
			try{
				Node node = graphDb.createNode();
				node.setProperty("name", "root");
				nodeIndex.add( node, "name", "root" );
				dbnodes.put("0", node);
				tx.success();
			}finally{
				tx.finish();
			}
			BufferedReader br = new BufferedReader(new FileReader(filename));
			while((str = br.readLine())!=null){
				count += 1;
				templines.add(str);
				if (count % transaction_iter == 0){
					System.out.print(count);
					System.out.print("\n");
					tx = graphDb.beginTx();
					try{
						for(int i=0;i<templines.size();i++){
							String[] spls = templines.get(i).split(",");
							if (spls[1].length() > 0){
								Node tnode = graphDb.createNode();
								tnode.setProperty("name", spls[2]);
								nodeIndex.add( tnode, "name", spls[2] );
								parents.put(spls[0], spls[1]);
								dbnodes.put(spls[0], tnode);
							}
						}
						tx.success();
					}finally{
						tx.finish();
					}
					templines.clear();
				}
			}
			br.close();
			tx = graphDb.beginTx();
			try{
				for(int i=0;i<templines.size();i++){
					String[] spls = templines.get(i).split(",");
					count += 1;
					if (spls[1].length() > 0){
						Node tnode = graphDb.createNode();
						tnode.setProperty("name", spls[2]);
						nodeIndex.add( tnode, "name", spls[2] );
						parents.put(spls[0], spls[1]);
						dbnodes.put(spls[0], tnode);
					}
				}
				tx.success();
			}finally{
				tx.finish();
			}
			templines.clear();
			//add the relationships
			ArrayList<String> temppar = new ArrayList<String>();
			count = 0;
			for(String key: dbnodes.keySet()){
				count += 1;
				temppar.add(key);
				if (count % transaction_iter == 0){
					System.out.println(count);
					tx = graphDb.beginTx();
					try{
						for (int i=0;i<temppar.size();i++){
							try {
								Relationship rel = dbnodes.get(temppar.get(i)).createRelationshipTo(dbnodes.get(parents.get(temppar.get(i))), RelTypes.TAXCHILDOF);
								rel.setProperty("source", shortfilename);
								rel.setProperty("childid",temppar.get(i));
								rel.setProperty("parentid",parents.get(temppar.get(i)));
							}catch(java.lang.IllegalArgumentException io){
//								System.out.println(temppar.get(i));
								continue;
							}
						}
						tx.success();
					}finally{
						tx.finish();
					}
					temppar.clear();
				}
			}
			tx = graphDb.beginTx();
			try{
				for (int i=0;i<temppar.size();i++){
					try {
						Relationship rel = dbnodes.get(temppar.get(i)).createRelationshipTo(dbnodes.get(parents.get(temppar.get(i))), RelTypes.TAXCHILDOF);
						rel.setProperty("source", shortfilename);
						rel.setProperty("childid",temppar.get(i));
						rel.setProperty("parentid",parents.get(temppar.get(i)));
					}catch(java.lang.IllegalArgumentException io){
//						System.out.println(temppar.get(i));
						continue;
					}
				}
				tx.success();
			}finally{
				tx.finish();
			}
		}catch(IOException ioe){}
	}
	
	private ArrayList<Integer> stepsToMatch(ArrayList<String> keylist, ArrayList<String> list1){
		int count1 = 0;
		ArrayList<Integer> ret = new ArrayList<Integer>();
		for (int i=0;i < keylist.size(); i++){
			count1 += 1;
			int count2 = 0;
			for (int j=0; j< list1.size();j++){
				count2 += 1;
			    if (list1.get(j).compareTo(keylist.get(i))==0){
			    	ret.add(count1);ret.add(count2);
			    	return ret;
			    }
			}
		}
		ret.add(LARGE);ret.add(LARGE);
		return ret;
	}
	
	public void addAdditionalTaxonomyTableIntoGraph(String filename){
		String str = "";
		String shortfilename = filename.split("/")[filename.split("/").length-1];
    	int count = 0;
    	HashMap<String, String> ndnames = new HashMap<String, String>();
    	HashMap<String, String> parents = new HashMap<String, String>();
    	Transaction tx;
    	ArrayList<String> addnodes = new ArrayList<String>();
		//first, need to get what nodes are new
		try{
			BufferedReader br = new BufferedReader(new FileReader(filename));
			while((str = br.readLine())!=null){
				count += 1;
				String[] spls = str.split(",");
				parents.put(spls[0], spls[1]);
				String strname = spls[2];
				ndnames.put(spls[0], strname);
				IndexHits<Node> ih = nodeIndex.get("name", strname);
				try{
					if(ih.size()==0){
						addnodes.add(strname);
					}
				}finally{
					ih.close();
				}
				if (count % transaction_iter == 0){
					System.out.print(count);
					System.out.print(" ");
					System.out.print(addnodes.size());
					System.out.print("\n");
					tx = graphDb.beginTx();
					try{
						for(int i=0;i<addnodes.size();i++){
							Node tnode = graphDb.createNode();
							tnode.setProperty("name", addnodes.get(i));
							nodeIndex.add( tnode, "name", addnodes.get(i) );
						}
						addnodes.clear();
						tx.success();
					}finally{
						tx.finish();
					}
				}
			}
			br.close();
		}catch(IOException ioe){}
		tx = graphDb.beginTx();
		try{
			for(int i=0;i<addnodes.size();i++){
				Node tnode = graphDb.createNode();
				tnode.setProperty("name", addnodes.get(i));
				nodeIndex.add( tnode, "name", addnodes.get(i) );
			}
			addnodes.clear();
			tx.success();
		}finally{
			tx.finish();
		}
		System.out.println("second pass through file for relationships");
		//GET NODE
		ArrayList<Node> rel_nd = new ArrayList<Node>();
		ArrayList<Node> rel_pnd = new ArrayList<Node>();
		ArrayList<String> rel_cid = new ArrayList<String>();
		ArrayList<String> rel_pid = new ArrayList<String>();
		try{
			count = 0;
			BufferedReader br = new BufferedReader(new FileReader(filename));
			boolean verbose = false;
			while((str = br.readLine())!=null){
				count += 1;
				String[] spls = str.split(",");
				String strname = spls[2];
				String strparentname = "";
				if(spls[1].compareTo("0") != 0)
					strparentname = ndnames.get(spls[1]);
				if (spls[1].compareTo("0") == 0)
					continue;
				if (verbose)
					System.out.println(str);
				ArrayList<String> path1 = new ArrayList<String>();
				boolean going = true;
				boolean badpath = false;
				String cur = parents.get(spls[0]);
				if(verbose)
					System.out.println("parent:"+cur);
				while(going == true){
					if(cur == null){
						going = false;
						badpath = true;
						System.out.println("-bad path start:"+spls[0]);
					}else if (cur.compareTo("0")==0){
						going = false;
					}else{
						if (verbose)
							System.out.println("-parent:"+cur);
						path1.add(ndnames.get(cur));
						cur = parents.get(cur);
					}
				}
				if(badpath == true)
					continue;
				Node matchnode = null;
				HashMap<Node,ArrayList<Integer>> itemcounts = new HashMap<Node,ArrayList<Integer>>();
				int bestcount = LARGE+LARGE;
				Node bestitem = null;
				ArrayList<String> bestpath = null;
				ArrayList<Node> bestpathitems= null;
				IndexHits<Node> hits = nodeIndex.get("name", strname);
				ArrayList<String> path2 = null;
				ArrayList<Node> path2items = null;
				boolean first = true;
				try{
					for(Node node: hits){
						path2 = new ArrayList<String> ();
						path2items = new ArrayList<Node> ();
						for(Node currentNode : CHILDOF_TRAVERSAL.traverse(node).nodes()){
							if(verbose)
								System.out.println("+"+((String) currentNode.getProperty("name")));
							if(((String) currentNode.getProperty("name")).compareTo(strname) != 0){
								path2.add((String)currentNode.getProperty("name"));
								path2items.add(currentNode);
								if (verbose)
									System.out.println((String)currentNode.getProperty("name"));
							}
						}
						itemcounts.put(node, stepsToMatch(path1,path2));
						if(verbose)
							System.out.println(sum(itemcounts.get(node)));
						if(sum(itemcounts.get(node)) < bestcount || first == true){
							first = false;//sets these all to not null and at least the first one
							bestcount = sum(itemcounts.get(node));
							bestitem = node;
							bestpath = new ArrayList<String>(path2);
							bestpathitems = new ArrayList<Node>(path2items);
						}
						path2.clear();
						path2items.clear();
						if(verbose)
							System.out.println(bestcount);
						if(verbose)
							System.out.println("after:"+bestpath.get(1));
					}
				}finally{
					hits.close();
				}

				itemcounts.clear();
				matchnode = bestitem;
				if(spls[1].compareTo("0") != 0){
					Node matchnodeparent = null;
					for(int i=0;i<bestpath.size();i++){
						if(bestpath.get(i).compareTo(strparentname) == 0){
							matchnodeparent = bestpathitems.get(i);
//							break;
						}
						if(verbose)
							System.out.println("="+bestpath.get(i)+" "+strparentname);
					}
					if(matchnodeparent == null){
						IndexHits<Node> hits2 = nodeIndex.get("name", strparentname);
						try{
							for(Node node2 : hits2){
								matchnodeparent = node2;
							}
						}finally{
							hits2.close();
						}
					}
					rel_nd.add(matchnode);
					rel_pnd.add(matchnodeparent);
					rel_cid.add(spls[0]);
					rel_pid.add(spls[1]);
				}

				if(count % transaction_iter == 0){
					System.out.println(count);
					tx = graphDb.beginTx();
					try{
						for(int i=0;i<rel_nd.size();i++){
							Relationship rel = rel_nd.get(i).createRelationshipTo(rel_pnd.get(i), RelTypes.TAXCHILDOF);
							rel.setProperty("source", shortfilename);
							rel.setProperty("childid", rel_cid.get(i));
							rel.setProperty("parentid", rel_pid.get(i));
						}
						rel_nd.clear();
						rel_pnd.clear();
						rel_cid.clear();
						rel_pid.clear();
						tx.success();
					}finally{
						tx.finish();
					}
				}
				path1.clear();
			}
			tx = graphDb.beginTx();
			try{
				for(int i=0;i<rel_nd.size();i++){
					Relationship rel = rel_nd.get(i).createRelationshipTo(rel_pnd.get(i), RelTypes.TAXCHILDOF);
					rel.setProperty("source", shortfilename);
					rel.setProperty("childid", rel_cid.get(i));
					rel.setProperty("parentid", rel_pid.get(i));
				}
				rel_nd.clear();
				rel_pnd.clear();
				rel_cid.clear();
				rel_pid.clear();
				tx.success();
			}finally{
				tx.finish();
			}
			br.close();
		}catch(IOException ioe){}
	}
	
	public void runittest(String filename,String filename2){
		System.out.println("adding initial taxonomy");
		addInitialTaxonomyTableIntoGraph(filename);
		System.out.println("adding additional taxonomies");
		addAdditionalTaxonomyTableIntoGraph(filename2);
		shutdownDB();
	}
	
    public static void main( String[] args ){
        System.out.println( "unit testing taxonomy loader" );
        String DB_PATH ="/home/smitty/Dropbox/projects/AVATOL/graphtests/neo4j-community-1.8.M02/data/graph.db";
        TaxonomyLoader a = new TaxonomyLoader(DB_PATH);
        //String filename = "/media/data/Dropbox/projects/AVATOL/graphtests/taxonomies/union4.txt";
        String filename =  "/home/smitty/Dropbox/projects/AVATOL/graphtests/taxonomies/col_acc.txt";
        String filename2 = "/home/smitty/Dropbox/projects/AVATOL/graphtests/taxonomies/ncbi_no_env_samples.txt";
        a.runittest(filename,filename2);
        System.exit(0);
    }
}
