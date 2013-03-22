package opentree;

import jade.tree.JadeTree;
import jade.tree.NexsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
	 * 
	 * @param datefrom should be like 2010-01-01
	 * @param dateto should be like 2013-03-19
	 * @return
	 */
	public static ArrayList<Long> getUpdateStudyList(String datefrom, String dateto){
		String urlbase = "http://www.reelab.net/phylografter/study/modified_list.json/url?from="+datefrom+"T00:00:00&to="+dateto+"T10:00:00";
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
		}catch (IOException e){
			e.printStackTrace();
		}
		return null;
	}
	
	public static List<JadeTree> fetchTreesFromStudy(Long studyid){
		String urlbase = "http://www.reelab.net/phylografter/study/export_NexSON.json/"+String.valueOf(studyid);
		try {
			URL phurl = new URL(urlbase);
			URLConnection conn = phurl.openConnection();
			conn.connect();
			BufferedReader un = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			List<JadeTree> trees = NexsonReader.readNexson(un);
			un.close();
			return trees;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}catch (IOException e){
			e.printStackTrace();
		}
		return null;
	}
}
