package opentree;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;
import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.NexsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import javax.ws.rs.core.MediaType;

import opentree.constants.RelType;
import jade.MessageLogger;

import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

// for gzipped nexsons
import java.util.zip.*;

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
	public static ArrayList<Long> getUpdateStudyList(String datefrom, String dateto) {
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
			//		String inl;	// not used
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
	 * Not all of the ottIds will be set so these trees should be run through
	 * fixNamesFromTrees after processing here
	 * 
	 * @param studyid
	 *            that is being requested
	 * @return a List<JadeTree> of the trees processed
	 */
	public static List<JadeTree> fetchTreesFromStudy(Long studyid, MessageLogger messageLogger) {
		String urlbase = "http://www.reelab.net/phylografter/study/export_NexSON.json/"
				+ String.valueOf(studyid);
		System.out.println("Looking up study: " + urlbase);

		try {
			URL phurl = new URL(urlbase);
			HttpURLConnection conn = (HttpURLConnection) phurl.openConnection();
			conn.connect();
			BufferedReader un = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));
			List<JadeTree> trees = NexsonReader.readNexson(un, true, messageLogger);
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
	 * Not all of the ottIds will be set so these trees should be run through
	 * fixNamesFromTrees after processing here
	 * 
	 * @param studyid
	 *            that is being requested
	 * @return a List<JadeTree> of the trees processed
	 */
	public static List<JadeTree> fetchGzippedTreesFromStudy(Long studyid, MessageLogger messageLogger) {
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
			List<JadeTree> trees = NexsonReader.readNexson(un, true, messageLogger);
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
	public static boolean fixNamesFromTrees(List<JadeTree> trees, GraphDatabaseAgent graphDb, boolean prune, MessageLogger logger) throws IOException{
		String urlbasecontext = "http://ec2-54-203-194-13.us-west-2.compute.amazonaws.com/taxomachine/ext/TNRS/graphdb/getContextForNames";
		String urlbasefetch = "http://ec2-54-203-194-13.us-west-2.compute.amazonaws.com/taxomachine/ext/TNRS/graphdb/contextQueryForNames";
		logger.message("conducting TNRS on trees");
		for (int i = 0; i < trees.size(); i++) {
			JadeTree currTree = trees.get(i);
			logger.indentMessageStr(1, "name fixing on tree", "tree id", (String)currTree.getObject("id"));
			// get the names that don't have ids
			// if the number is 0 then break
			ArrayList<JadeNode> searchnds = new ArrayList<JadeNode>();
			HashMap<String,JadeNode> namenodemap = new HashMap<String,JadeNode>();
			ArrayList<JadeNode> matchednodes = new ArrayList<JadeNode>();
			for (int j = 0; j < currTree.getExternalNodeCount(); j++) {
				JadeNode ndJ = currTree.getExternalNode(j);
				if (ndJ.getObject("ot:ottId") == null) {
					logger.indentMessageStrStr(2, "OTT ID missing", "name", ndJ.getName(), "nexsonid", (String)ndJ.getObject("nexsonid"));
					searchnds.add(ndJ);
					namenodemap.put(ndJ.getName(), ndJ);
				}
			}
			if (searchnds.size() == 0) {
				logger.indentMessage(1, "all nodes have ottIds");
			} else {
				// build the parameter string for the context query
				ArrayList<String> namelist = new ArrayList<String>();
				for (int j = 0; j < currTree.getExternalNodeCount(); j++) {
					namelist.add(currTree.getExternalNode(j).getName());
				}
				HashMap <String,Object> namemap = new HashMap <String,Object>();
				namemap.put("names",namelist);
				String contextQueryParameters = new JSONObject(namemap).toJSONString();
				//System.out.println(contextQueryParameters);

				// set up the connection to the TNRS context query
				ClientConfig cc = new DefaultClientConfig();
				Client c = Client.create(cc);
				WebResource contextQuery = c.resource(urlbasecontext);

				// query for the context
				String contextResponseJSONStr = null;
				try {
					contextResponseJSONStr = contextQuery.accept(MediaType.APPLICATION_JSON_TYPE).type(MediaType.APPLICATION_JSON_TYPE).post(String.class, contextQueryParameters);
				} catch(Exception e) {
					System.out.println("PROBLEM CONNECTING TO SERVER");
				}
				if (contextResponseJSONStr != null) {
					JSONObject contextResponse = (JSONObject) JSONValue.parse(contextResponseJSONStr);
					String cn = (String)contextResponse.get("context_name");
					//System.out.println(contextResponse);
					ArrayList<String> namelist2 = new ArrayList<String>();
					for (int j = 0; j < searchnds.size(); j++) {
						if (searchnds.get(j).getObject("ot:ottId") == null) {
							namelist2.add(searchnds.get(j).getName());
						}
					}
					HashMap <String,Object> namemap2 = new HashMap <String,Object>();
					namemap2.put("names", namelist2);
					namemap2.put("contextName", cn);
					contextQueryParameters = new JSONObject(namemap2).toJSONString();

					// set up the connection to the TNRS context query
					cc = new DefaultClientConfig();
					c = Client.create(cc);
					contextQuery = c.resource(urlbasefetch);

					// query for the context
					String tnrsResponseJSONStr = null;
					try {
						tnrsResponseJSONStr = contextQuery.accept(MediaType.APPLICATION_JSON_TYPE)
								.type(MediaType.APPLICATION_JSON_TYPE)
								.post(String.class, contextQueryParameters);
					} catch (Exception x) {
						logger.indentMessageStr(1, "Error in call to tnrs", "params", contextQueryParameters);
					}
					if (tnrsResponseJSONStr != null) {
						contextResponse = (JSONObject) JSONValue.parse(tnrsResponseJSONStr);
						//System.out.println(contextResponse);
						JSONArray unm = (JSONArray) contextResponse.get("unmatched_name_ids");
						logger.indentMessageInt(1, "TNRS unmatched", "total number", unm.size());
						JSONArray res = (JSONArray) contextResponse.get("results");
						// if the match is with score 1, then we keep


						// TODO: casting below is not all necessary
						for (Object id: res) {
							JSONArray tres = (JSONArray)((JSONObject)id).get("matches");
							String origname = (String)((JSONObject)id).get("id");
							for (Object tid: tres) {
								Double score = (Double)((JSONObject)tid).get("score");
								boolean permat = (Boolean)((JSONObject)tid).get("is_perfect_match");
								String ottId = String.valueOf(((JSONObject)tid).get("matched_ott_id"));
								String matchedName = (String)((JSONObject)tid).get("matched_name");
								if (score >= 1) {
									Long tnrsottId = Long.valueOf(ottId);
									System.out.println(tnrsottId+ " "+ namenodemap.get(origname));
									JadeNode fixedNode = namenodemap.get(origname);	
									logger.indentMessageLongStrStrStr(2, "TNRS resolved ottId", "OTT ID", tnrsottId, "name", matchedName, "searched on", origname, "nexsonid", (String)fixedNode.getObject("nexsonid"));
									fixedNode.assocObject("ot:ottId", tnrsottId);
									matchednodes.add(namenodemap.get(origname));
									namenodemap.remove(origname);
									break;
								}
							}
						}
					}
					Index<Node> graphNodeIndex = graphDb.getNodeIndex( "graphNamedNodes" ); // name is the key

					// check to make sure that they aren't already in the new index
					ArrayList<String> removenames = new ArrayList<String>();
					for (String name: namenodemap.keySet()) {
						IndexHits <Node> hits = graphNodeIndex.get("name", name);
						if (hits.size() == 0) {
						} else if (hits.size() == 1) {
							String uidString = (String) hits.getSingle().getProperty("tax_uid");
							//				System.out.println("\tResult is: " + uidString);
							Long lid = Long.valueOf(uidString);
							logger.indentMessageLong(2, "Name previously ingested into graphNamedNodes", name, lid);
							namenodemap.get(name).assocObject("ot:ottId", Long.valueOf(lid));
							removenames.add(name);
						} else if (hits .size() > 1) {
							logger.indentMessageInt(2, "Name not unique in graphNamedNodes", name, hits.size());
						}
						hits.close();
					}
					// remove the ones that are added
					for (String name: removenames) {
						namenodemap.remove(name);
					}

					if (namenodemap.size() > 0) {
						if (prune) {
							for (String name: namenodemap.keySet()) {
								JadeNode jnode = namenodemap.get(name);
								logger.indentMessageStrStr(2, "pruning unmapped", "name", name, "nexsonid", (String)jnode.getObject("nexsonid"));
								try {
									currTree.pruneExternalNode(jnode);
								} catch (Exception x) {
									logger.indentMessageStr(3, "Error pruning leaf", "name", name);
									return false;
								}
							}
						} else {
							return false;
						}
					}
				}
			}
			//now checking for duplicate names or names to point to parents or dubious names
			if (prune) {
				//for each tip in the tree, see if there are duplicates
				TLongHashSet tipottols = new TLongHashSet();
				HashSet<JadeNode> pru = new HashSet<JadeNode> ();
				for (int j = 0; j < currTree.getExternalNodeCount(); j++) {
					JadeNode currNd = currTree.getExternalNode(j);
					Long tid = (Long)currNd.getObject("ot:ottId");
					if (tid == null) {
						logger.indentMessage(2, "Null OTT ID in tree");
						logger.indentMessageStrStr(3, "null", "name", currNd.getName(), "nexsonid", (String)currNd.getObject("nexsonid"));
						pru.add(currNd);
					} else if (tipottols.contains(tid)) {
						//prune
						logger.indentMessage(2, "OTT ID reused in tree");
						logger.indentMessageStrStr(3, "duplicate", "name", currNd.getName(), "nexsonid", (String)currNd.getObject("nexsonid"));
						logger.indentMessageLong(3, "duplicate", "OTT ID", tid);
						pru.add(currNd);
					} else {
						IndexHits<Node> hits = graphDb.getNodeIndex("graphTaxUIDNodes").get("tax_uid", String.valueOf(tid));
						if (hits.size() == 0) {
							logger.indentMessage(2, "OTT ID not in database (probably dubious)");
							logger.indentMessageStrStr(3, "dubious", "name", currNd.getName(), "nexsonid", (String)currNd.getObject("nexsonid"));
							logger.indentMessageLong(3, "dubious", "OTT ID", tid);
							pru.add(currNd);
						} else {
							tipottols.add(tid);
						}
						hits.close();
					}
				}
				//for each tip see if there are tips that map to parents of other tips
				//do this by seeing if there is any overlap between the mrcas from different 
				for (int j = 0; j < currTree.getExternalNodeCount(); j++){
					JadeNode currNdJ = currTree.getExternalNode(j);
					if(pru.contains(currNdJ)) {
						continue;
					}
					Long tid = (Long)currNdJ.getObject("ot:ottId");
					IndexHits<Node> hits = graphDb.getNodeIndex("graphTaxUIDNodes").get("tax_uid", String.valueOf(tid));
					Node firstNode = hits.getSingle();
					hits.close();
					TLongArrayList t1 = new TLongArrayList((long [])firstNode.getProperty("mrca"));
					for (int k = 0; k < currTree.getExternalNodeCount(); k++) {
						JadeNode currNdK = currTree.getExternalNode(k);
						if (pru.contains(currNdK) || k == j) {
							continue;
						}
						Long tid2 = (Long)currNdK.getObject("ot:ottId");
						IndexHits<Node> hits2 = graphDb.getNodeIndex("graphTaxUIDNodes").get("tax_uid", String.valueOf(tid2));
						Node secondNode = hits2.getSingle();
						hits2.close();
						if (secondNode == null) {
							logger.indentMessageStr(2, "null node in graphTaxUIDNodes", "tax_uid", String.valueOf(tid2));
							pru.add(currNdK);
						} else {
							TLongArrayList t2 = new TLongArrayList((long [])secondNode.getProperty("mrca"));
							if (LicaUtil.containsAnyt4jUnsorted(t1, t2)){
								logger.indentMessage(2, "overlapping tips");
								if(t2.size() < t1.size()){
									pru.add(currNdJ);
									logger.indentMessageStrStr(3, "overlapping retained", "name", currNdK.getName(), "nexsonid", (String)currNdK.getObject("nexsonid"));
									logger.indentMessageStrStr(3, "overlapping pruned", "name", currNdJ.getName(), "nexsonid", (String)currNdJ.getObject("nexsonid"));
									break;
								}else{
									pru.add(currNdK);
									logger.indentMessageStrStr(3, "overlapping retained", "name", currNdJ.getName(), "nexsonid", (String)currNdJ.getObject("nexsonid"));
									logger.indentMessageStrStr(3, "overlapping pruned", "name", currNdK.getName(), "nexsonid", (String)currNdK.getObject("nexsonid"));
								}
							}
						}
					}
				}
				for (JadeNode tn: pru) {
					logger.indentMessageStrStr(2, "pruning dups and overlapping", "name", tn.getName(), "nexsonid", (String)tn.getObject("nexsonid"));
					try {
						currTree.pruneExternalNode(tn);
					} catch (Exception x) {
						logger.indentMessageStr(3, "Error pruning leaf", "name", tn.getName());
						return false;
					}
				}
				currTree.processRoot();
				if (prune == true) {
					logger.indentMessageStr(1, "postpruning newick", "tree", currTree.getRoot().getNewick(false));
				}
			}
			//final mapping of the taxonomy
			logger.indentMessage(1, "taxon mapping summary");
			for (int k = 0; k < currTree.getExternalNodeCount(); k++) {
				JadeNode ndK = currTree.getExternalNode(k);
				Long tid = (Long)ndK.getObject("ot:ottId");
				IndexHits<Node> hits = graphDb.getNodeIndex("graphTaxUIDNodes").get("tax_uid", String.valueOf(tid));
				Node firstNode = hits.getSingle();
				hits.close();
				Node cnode = firstNode;
				if (cnode == null) {
					logger.indentMessageLongStrStr(2, "Error ottId indexed to a null node!", "OTT ID", tid, "original name", ndK.getName(), "nexsonid", (String)ndK.getObject("nexsonid"));
				} else {
					String cnodeName = (String) cnode.getProperty("name");
					StringBuffer sb = new StringBuffer();
					sb.append(cnodeName == null ? "{null name}" : cnodeName);
					while (cnode.hasRelationship(Direction.OUTGOING, RelType.TAXCHILDOF)) {
						cnode = cnode.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
						cnodeName = (String) cnode.getProperty("name");
						sb.append("->");
						sb.append(cnodeName == null ? "{null name}" : cnodeName);
					}
					logger.indentMessageLongStrStr(2, "taxon mapping", "OTT ID", tid, "taxonomy", sb.toString(), "nexsonid", (String)ndK.getObject("nexsonid"));
				}
			}
		}
		return true;
	}
}
