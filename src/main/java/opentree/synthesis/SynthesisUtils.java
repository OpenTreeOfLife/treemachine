package opentree.synthesis;

import jade.tree.JadeNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import opentree.TaxonNotFoundException;
import opentree.GraphBase;
import opentree.RelTypes;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.kernel.Traversal;

//EXPERIMENTAL

/*
 * There is nothing particularly special about this file. It is just currently a test
 * class for experimenting with some synthesis queries. I fully expect that it will be
 * deleted at some point.
 */

public class SynthesisUtils extends GraphBase{
	
	public SynthesisUtils(){}
	
	public void get_edges_for_taxaset(GraphDatabaseService graphDb, HashSet<String> TaxaList) throws TaxonNotFoundException{
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 1000);
		Node focalnode = graphDb.getNodeById(1);//OBVIOUSLY NEED TO SET THIS
		HashSet<Long> ndids = new HashSet<Long>();
		HashSet<Node> nodes = new HashSet<Node>();
		Iterator<String> taxiterator = TaxaList.iterator();
		while (taxiterator.hasNext()){
			String name = taxiterator.next();
			Node hitnode = null;
			String processedname = name.replace("_", " "); //@todo processing syntactic rules like '_' -> ' ' should be done on input parsing. 
			IndexHits<Node> hits = graphNodeIndex.get("name", processedname);
			int numh = hits.size();
			if (numh == 1){
				hitnode = hits.getSingle();
			}else if (numh > 1){
				System.out.println(processedname + " gets " + numh +" hits");
				int shortest = 1000;//this is shortest to the focal, could reverse this
				Node shortn = null;
				for(Node tnode : hits){
					Path tpath = pf.findSinglePath(tnode, focalnode);
					if (tpath!= null){
						if (shortn == null)
							shortn = tnode;
						if(tpath.length()<shortest){
							shortest = tpath.length();
							shortn = tnode;
						}
					}
				}
				assert shortn != null; // @todo this could happen if there are multiple hits outside the focalgroup, and none inside the focalgroup.  We should develop an AmbiguousTaxonException class
				hitnode = shortn;
			}
			hits.close();
			if (hitnode == null) {
				assert numh == 0;
				throw new TaxonNotFoundException(processedname);
			}
			ndids.add(hitnode.getId());
			nodes.add(hitnode);
		}
		
	}

}
