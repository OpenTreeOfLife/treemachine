package opentree;

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

import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;

public class PhylografterConnector {
	
	/**
	 * This will get the list of studies that have been updated in phylografter
	 * 		since a particular date and to another date
	 * 
	 * The resulting list of ids can then be fetched using the fetchTreesFromStudy
	 * 
	 * @param datefrom should be like 2010-01-01
	 * @param dateto should be like 2013-03-19
	 * @return list of study ids
	 */
	public static ArrayList<Long> getUpdateStudyList(String datefrom, String dateto){
		String urlbase = "http://www.reelab.net/phylografter/study/modified_list.json/url?from="+datefrom+"T00:00:00&to="+dateto+"T10:00:00";
		System.out.println("Grabbing list of updated studies from: " + urlbase);
//		urlbase = "http://www.reelab.net/phylografter/study/modified_list.json/";
//		System.out.println("Looking up: " + urlbase);
		try {
			URL phurl = new URL(urlbase);
			URLConnection conn = phurl.openConnection();
			conn.connect();
			BufferedReader un = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inl;
			JSONObject all = (JSONObject)JSONValue.parse(un);
			JSONArray root = (JSONArray)all.get("studies");
			ArrayList<Long> stids = new ArrayList<Long>();
			for (Object id : root) {
				Long j = (Long)id;
				stids.add(j);
			}
			return stids;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e){
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Given a studyid, this will extract the list of JadeTrees contained within
	 * 		Nexson as sent by phylografter
	 * 
	 * Not all of the ottolids will be set so these trees should be run through fixNamesFromTrees
	 * 		after processing here
	 * 
	 * @param studyid that is being requested
	 * @return a List<JadeTree> of the trees processed
	 */
	public static List<JadeTree> fetchTreesFromStudy(Long studyid){
		String urlbase = "http://www.reelab.net/phylografter/study/export_NexSON.json/"+String.valueOf(studyid);
		System.out.println("Looking up study: " + urlbase);
		
		try {
			URL phurl = new URL(urlbase);
			HttpURLConnection conn = (HttpURLConnection)phurl.openConnection();
			conn.connect();
			BufferedReader un = new BufferedReader(new InputStreamReader(conn.getInputStream()));
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
	 * There are three possibilities
	 * 	1. the ottol id is present
	 * 	2. there is no ottol id but the TNRS gets one with high probability
	 *  3. there is no ottol id and the TNRS is no help
	 * 
	 * For 3 (the problematic one), we will add an entry in an index and add a name
	 * 		to the taxonomy. This is a special case when adding things from phylografter
	 * 
	 * @param trees that have been processed from fetchTreesFromStudy
	 */
	public static void fixNamesFromTrees(List<JadeTree> trees){
		//TODO: should probably change these to real json sending but for now
		//		we are testing
		String urlbasecontext = "http://opentree-dev.bio.ku.edu:7476/db/data/ext/TNRS/graphdb/getContextForNames";
		String urlbasefetch = "http://opentree-dev.bio.ku.edu:7476/db/data/ext/TNRS/graphdb/doTNRSForNames";
		System.out.println("conducting TNRS on trees");
		for(int i=0;i<trees.size();i++){
			StringBuffer sb = new StringBuffer();
			sb.append("{\"queryString\":\"");
			sb.append(trees.get(i).getExternalNode(0).getName());
			for(int j=1;j<trees.get(i).getExternalNodeCount();j++){
				sb.append(","+trees.get(i).getExternalNode(j).getName());
			}
			sb.append("\"}");
			String urlParameters = sb.toString();
//			System.out.println(urlParameters);
			try {
				URL phurl = new URL(urlbasecontext);
				System.out.println(urlbasecontext);
				URLConnection conn = (URLConnection) phurl.openConnection();
//				conn.setDoOutput(true);
				conn.setDoInput(true);
//				conn.setInstanceFollowRedirects(false); 
//				conn.setRequestMethod("GET");
//				conn.setRequestProperty("Content-Type", "Application/json"); 
//				conn.setRequestProperty("charset", "utf-8");
//				conn.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
//				conn.setUseCaches (false);
				conn.connect();	
//				DataOutputStream wr = new DataOutputStream(conn.getOutputStream ());
/*
				OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
				writer.write(urlParameters);
				writer.flush();*/
				BufferedReader un = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				JSONObject all = (JSONObject)JSONValue.parse(un);
				System.out.println(all);
				un.close();
//				writer.close();
				//conn.disconnect();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}	
		}
	}
}
