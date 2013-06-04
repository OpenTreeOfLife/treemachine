package opentree;

import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.NexsonReader;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import opentree.RelTypes;

import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

import scala.actors.threadpool.Arrays;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

// for gzipped nexsons
import java.util.zip.*;
import java.io.FileInputStream;

public class PhylografterConnector {

	//this is used to prepend the new tax ids as they are created
	//this should be autoincremented and this is just the starting number
	//the current number can be retrieved by graphDb.getGraphProperty("newTaxUIDCurIter")
	private static Long newtaxidstart = (long) 1000000000;
	
	/**
	 * This will get the list of studies that have been updated in phylografter
	 * since a particular date and to another date
	 * 
	 * The resulting list of ids can then be fetched using the
	 * fetchTreesFromStudy
	 * 
	 * @param datefrom
	 *            should be like 2010-01-01
	 * @param dateto
	 *            should be like 2013-03-19
	 * @return list of study ids
	 */
	public static ArrayList<Long> getUpdateStudyList(String datefrom,
			String dateto) {
		String urlbase = "http://www.reelab.net/phylografter/study/modified_list.json/url?from="
				+ datefrom + "T00:00:00&to=" + dateto + "T10:00:00";
		System.out.println("Grabbing list of updated studies from: " + urlbase);
		// urlbase =
		// "http://www.reelab.net/phylografter/study/modified_list.json/";
		// System.out.println("Looking up: " + urlbase);
		try {
			URL phurl = new URL(urlbase);
			URLConnection conn = phurl.openConnection();
			conn.connect();
			BufferedReader un = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			String inl;
			JSONObject all = (JSONObject) JSONValue.parse(un);
			JSONArray root = (JSONArray) all.get("studies");
			ArrayList<Long> stids = new ArrayList<Long>();
			for (Object id : root) {
				Long j = (Long) id;
				stids.add(j);
			}
			return stids;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Given a studyid, this will extract the list of JadeTrees contained within
	 * Nexson as sent by phylografter
	 * 
	 * Not all of the ottolids will be set so these trees should be run through
	 * fixNamesFromTrees after processing here
	 * 
	 * @param studyid
	 *            that is being requested
	 * @return a List<JadeTree> of the trees processed
	 */
	public static List<JadeTree> fetchTreesFromStudy(Long studyid) {
		String urlbase = "http://www.reelab.net/phylografter/study/export_NexSON.json/"
				+ String.valueOf(studyid);
		System.out.println("Looking up study: " + urlbase);

		try {
			URL phurl = new URL(urlbase);
			HttpURLConnection conn = (HttpURLConnection) phurl.openConnection();
			conn.connect();
			BufferedReader un = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			List<JadeTree> trees = NexsonReader.readNexson(un);
			un.close();
			conn.disconnect();
			return trees;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Same as above, but takes in gzipped nexsons.
	 * 
	 * Given a studyid, this will extract the list of JadeTrees contained within
	 * Nexson as sent by phylografter
	 * 
	 * Not all of the ottolids will be set so these trees should be run through
	 * fixNamesFromTrees after processing here
	 * 
	 * @param studyid
	 *            that is being requested
	 * @return a List<JadeTree> of the trees processed
	 */
	public static List<JadeTree> fetchGzippedTreesFromStudy(Long studyid) {
		String urlbase = "http://www.reelab.net/phylografter/study/export_gzipNexSON.json/"
				+ String.valueOf(studyid);
		System.out.println("Looking up study: " + urlbase);

		try {
			URL phurl = new URL(urlbase);
			HttpURLConnection conn = (HttpURLConnection) phurl.openConnection();
			conn.connect();
			
			GZIPInputStream gzip = new GZIPInputStream(conn.getInputStream());
			
			BufferedReader un = new BufferedReader(new InputStreamReader(gzip));
			
//			BufferedReader un = new BufferedReader(new InputStreamReader(
//					conn.getInputStream()));
			List<JadeTree> trees = NexsonReader.readNexson(un);
			un.close();
			conn.disconnect();
			return trees;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * There are three possibilities 1. the ottol id is present 2. there is no
	 * ottol id but the TNRS gets one with high probability 3. there is no ottol
	 * id and the TNRS is no help
	 * 
	 * For 3 (the problematic one), we will add an entry in an index and add a
	 * name to the taxonomy. This is a special case when adding things from
	 * phylografter
	 * 
	 * The names that are added need to be searched if there were not matches in 
	 * the TNRS
	 * 
	 * this is similar to fixNamesFromTrees but it will not add the taxon if not found.
	 * it will try and prune
	 * @param studyid
	 * @param trees
	 * @param graphDb
	 * @throws IOException
	 */
	public static boolean fixNamesFromTrees(List<JadeTree> trees, GraphDatabaseAgent graphDb,boolean prune) throws IOException{
		// TODO: should probably change these to real json sending but for now
		// we are testing
		String urlbasecontext = "http://dev.opentreeoflife.org/taxomachine/ext/TNRS/graphdb/getContextForNames";
		String urlbasefetch = "http://dev.opentreeoflife.org/taxomachine/ext/TNRS/graphdb/doTNRSForNames";
		System.out.println("conducting TNRS on trees");
		for (int i = 0; i < trees.size(); i++) {
			//get the names that don't have ids
			//if the number is 0 then break
			ArrayList<JadeNode> searchnds = new ArrayList<JadeNode>();
			HashMap<String,JadeNode> namenodemap = new HashMap<String,JadeNode>();
			ArrayList<JadeNode> matchednodes = new ArrayList<JadeNode>();
			for (int j = 0; j < trees.get(i).getExternalNodeCount(); j++) {
				if(trees.get(i).getExternalNode(j).getObject("ot:ottolid")==null){
					System.out.println("looking for:"+trees.get(i).getExternalNode(j).getName());
					searchnds.add(trees.get(i).getExternalNode(j));
					namenodemap.put(trees.get(i).getExternalNode(j).getName(), trees.get(i).getExternalNode(j));
				}
			}
			if (searchnds.size() == 0){
				System.out.println("all nodes have ottolids");
				break;
			}
			
			StringBuffer sb = new StringBuffer();

			// build the parameter string for the context query
			sb.append("{\"queryString\":\"");
			sb.append(trees.get(i).getExternalNode(0).getName());
			for (int j = 1; j < trees.get(i).getExternalNodeCount(); j++) {
				sb.append("," + trees.get(i).getExternalNode(j).getName());
			}
			sb.append("\"}");
			String contextQueryParameters = sb.toString();
			// System.out.println(urlParameters);

	        // set up the connection to the TNRS context query
	        ClientConfig cc = new DefaultClientConfig();
	        Client c = Client.create(cc);
	        WebResource contextQuery = c.resource(urlbasecontext);

	        // query for the context
	        String contextResponseJSON = contextQuery.accept(MediaType.APPLICATION_JSON_TYPE).type(MediaType.APPLICATION_JSON_TYPE).post(String.class, contextQueryParameters);
	        JSONObject contextResponse = (JSONObject) JSONValue.parse(contextResponseJSON);
	        String cn = (String)contextResponse.get("context_name");
	       // Long cnid = Long.valueOf((String)contextResponse.get("content_rootnode_ottol_id"));
	      //  System.out.println(contextResponse);
	        //getting the names for each of the speices
	        sb = new StringBuffer();

			// build the parameter string for the context query
			sb.append("{\"queryString\":\"");
			sb.append(searchnds.get(0).getName());
			for (int j = 1; j < searchnds.size(); j++) {
				if(searchnds.get(j).getObject("ot:ottolid")==null){
					sb.append("," + searchnds.get(j).getName());
				}
			}
			sb.append("\",\"contextName\":\""+cn+"\"}");
			contextQueryParameters = sb.toString();

	        // set up the connection to the TNRS context query
	        cc = new DefaultClientConfig();
	        c = Client.create(cc);
	        contextQuery = c.resource(urlbasefetch);

	        // query for the context
	        contextResponseJSON = contextQuery.accept(MediaType.APPLICATION_JSON_TYPE).type(MediaType.APPLICATION_JSON_TYPE).post(String.class, contextQueryParameters);
	        contextResponse = (JSONObject) JSONValue.parse(contextResponseJSON);
	       // System.out.println(contextResponse);
	        JSONArray unm = (JSONArray) contextResponse.get("unmatched_names");
//	        System.out.println("total unmatched: "+unmcount);
	        JSONArray res = (JSONArray) contextResponse.get("results");
	        //if the match is with score 1, then we keep
	        for (Object id: res){
	        	JSONArray tres = (JSONArray)((JSONObject)id).get("matches");
	        	for(Object tid: tres){
	        		Double score = (Double)((JSONObject)tid).get("score");
	        		boolean permat = (Boolean)((JSONObject)tid).get("isPerfectMatch");
	        		String ottolid = (String)((JSONObject)tid).get("matchedOttolID");
	        		String searchString = (String)((JSONObject)tid).get("searchString");
//	        		System.out.println(score+" "+permat+" "+ottolid);
	        		if (score >= 1){
	        			namenodemap.get(searchString).assocObject("ot:ottolid", Long.valueOf(ottolid));
	        			matchednodes.add(namenodemap.get(searchString));
	        			namenodemap.remove(searchString);
	        			break;
	        		}
	        	}
	        }
	        Index<Node> graphNodeIndex = graphDb.getNodeIndex( "graphNamedNodes" ); // name is the key

	        //check to make sure that they aren't already in the new index
	        ArrayList<String> removenames = new ArrayList<String>();
	        for(String name: namenodemap.keySet()){
	        	IndexHits <Node> hits = graphNodeIndex.get("name", name);
	        	if (hits.size() == 1){
	        		Long lid = (Long) hits.getSingle().getProperty("tax_uid");
	        		namenodemap.get(name).assocObject("ot:ottolid", Long.valueOf(lid));
	        		removenames.add(name);
	        	}else{
	        		System.out.println(hits.size());
	        	}
	        	hits.close();
	        }
	        //remove the ones that are added
	        for(String name: removenames){
	        	namenodemap.remove(name);
	        }
	        //prune the ones that didn't map
	        /*
	         * The process is to use the id for the context that is obtained above
	         * 0. we assign a new temporary ottolid -- need to know how to autoincrement
	         * 1. for each name we add a taxon name in the taxonomy in treemachine
	         * 2. we add this to the taxonomy index
	         * 3. we add this to the index of new names to add
	         */
	        
	        if (namenodemap.size() > 0)
	        	if(prune){
	    	        for(String name: namenodemap.keySet()){
	    	        	System.out.println("need to prune"+name);
	    	        	JadeNode jnode = namenodemap.get(name);
	    	        	//get the nodes from the parent
	    	        	JadeNode jnp = jnode;
	    	        	
	    	        }
	    	        
	        	}else{
	        		return false;
	        	}
		}
		return true;
	}
	
}
