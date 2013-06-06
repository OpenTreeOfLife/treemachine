package opentree.plugins;

import jade.tree.JadeTree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import opentree.GraphDatabaseAgent;
import opentree.GraphImporter;
import opentree.PhylografterConnector;
import opentree.TaxonNotFoundException;
import opentree.TreeIngestException;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

public class PhylografterUpdater extends ServerPlugin{

	@Description ("Update the graph studies that should be added from phylografter")
	@PluginTarget (GraphDatabaseService.class)
	public void updateGraphFromPhylografter(@Source GraphDatabaseService graphDbs){
//			,
//			@Description("Last date to check")
//			@Parameter( date = "date", optional= true ) String date){
		GraphDatabaseAgent graphDb = new GraphDatabaseAgent(graphDbs);
		ArrayList<Long> list = PhylografterConnector.getUpdateStudyList("2010-01-01","2013-03-22");
		int rc = 0;
		for (Long k: list){
//			if ((k == 60) || (k == 105) || (k == 106) || (k == 107) || (k == 115) || (k == 116)) { // some bad studies
//				System.out.println("Skipping study " + k);
//				continue;
//			}
			if (k > 20)
				break;
			if (k<8)
				continue;
			try{
				List<JadeTree> jt = PhylografterConnector.fetchTreesFromStudy(k);
				for (JadeTree j : jt) {
					System.out.println(k + ": " + j.getExternalNodeCount());
				}
				try {
					PhylografterConnector.fixNamesFromTrees(jt,graphDb,false);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		        for(JadeTree j: jt){
		        	GraphImporter gi = new GraphImporter(graphDb);
		        	boolean doubname = false;
		        	HashSet<Long> ottols = new HashSet<Long>();
		        	for(int m=0;m<j.getExternalNodeCount();m++){
		        		if(j.getExternalNode(m).getObject("ot:ottolid")==null){//use doubname as also 
		        			doubname = true;
		        			break;
		        		}
		        		if (ottols.contains((Long)j.getExternalNode(m).getObject("ot:ottolid"))==true){
		        			doubname = true;
		        			break;
		        		}else{
		        			ottols.add((Long)j.getExternalNode(m).getObject("ot:ottolid"));
		        		}
		        	}
		        	//check for any duplicate ottol:id
					if(doubname == true){
						System.out.println("there are duplicate names");
					}else{
						System.out.println("this is being added");
						gi.setTree(j);
						String sourcename = "";
						if (j.getObject("ot:studyId") != null) { // use studyid (if present) as sourcename
							sourcename = (String)j.getObject("ot:studyId");
						}
						gi.addSetTreeToGraphWIdsSet(sourcename,false);
					}
		        }
			} catch(java.lang.NullPointerException e){
				System.out.println("failed to get study "+k);
				rc = 1;
				continue;
			} catch (TaxonNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TreeIngestException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		
	}
}
