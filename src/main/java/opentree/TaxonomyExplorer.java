package opentree;

import opentree.TaxonomyBase.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Traverser;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.Traversal;

public class TaxonomyExplorer extends TaxonomyBase{
	
	public TaxonomyExplorer(String graphname){
		graphDb = new EmbeddedGraphDatabase( graphname );
		nodeIndex = graphDb.index().forNodes( "nodes" );
	}
	
	public void querySomeData(String name){
		IndexHits<Node> hits = nodeIndex.get("name", name);
		Node firstNode = hits.getSingle();
		hits.close();
		if (firstNode == null){
			System.out.println("name not found");
			return;
		}
		TraversalDescription CHILDOF_TRAVERSAL = Traversal.description()
		        .relationships( RelTypes.CHILDOF,Direction.INCOMING );
		System.out.println(firstNode.getProperty("name"));
		int count =0;
		for(Node friendNd : CHILDOF_TRAVERSAL.traverse(firstNode).nodes()){
			count += 1;
//			if(friendNd.hasProperty("name"))
//				System.out.println(friendNd.getProperty("name"));
			if (count % 100000 == 0)
				System.out.println(count);
		}
	}
	
	
	
	public void runittest(){
		querySomeData("Lonicera");
		shutdownDB();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("unit testing taxonomy explorer");
	    String DB_PATH ="/home/smitty/Dropbox/projects/AVATOL/graphtests/neo4j-community-1.8.M02/data/graph.db";
	    TaxonomyExplorer a = new TaxonomyExplorer(DB_PATH);
	    a.runittest();
	}

}
