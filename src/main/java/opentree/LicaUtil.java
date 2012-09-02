package opentree;

import java.util.HashSet;
import java.util.List;

import opentree.GraphBase.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.Traversal;

/**
 * 
 * @author Stephen Smith
 *
 * The idea of this class is to find not a single common ancestor (as AncestorUtil)
 * but instead to find all the non-nested Least Inclusive Common Ancestors. 
 * 
 * Note: In order for nested names to be found, nodeset, inIdSet, and fullIdSet 
 * should include not the nested names (Lonicera), but all the current children 
 * of that higher taxon name. 
 */

public class LicaUtil {

    /**
     * This should find all the least inclusive common ancestors (LICA). The idea
     * is to walk all the paths from one tip to and query at each mrca list as to 
     * whether it has at least the tip values necessary and none of the ones not
     * necessary. 
     * 
     * @param nodeSet list of all the nodes for which we are looking for the LICA
     * @param inIdSet list of the ingroup ids
     * @param fullIdSet list of all the node ids for the tree of interest
     * @return an ArrayList<Node> of all the nodes that are feasible LICA
     */
    
    public static HashSet<Node> getAllLICA(List<Node> nodeSet, HashSet<Long> inIdSet, HashSet<Long> fullIdSet){
    	HashSet<Node> retaln = new HashSet<Node>();
    	Node firstNode = nodeSet.get(0);//should be the node with the fewest outgoing relationships
    	int fewestnumrel = 10000000;
    	for (int i=0;i<nodeSet.size();i++){
    		int num = 0;
    		//only way to get number of relationships
    		for(Relationship rel: nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)){num++;}
    		if(num < fewestnumrel){
    			fewestnumrel = num;
    			firstNode = nodeSet.get(i);
    		}
    	}
    	
    	//remove everything but that which is in the outgroup
    	fullIdSet.removeAll(inIdSet);
    	
    	Node innode = firstNode;
    	for ( Path pa : Traversal.description()
	        .depthFirst()
	        .relationships( RelTypes.MRCACHILDOF, Direction.OUTGOING )
	        .traverse( innode ) ){
    		boolean going = true;
    		for (Node tnode: pa.nodes()){
    			long [] dbnodei = (long []) tnode.getProperty("mrca");
    			HashSet<Long> Ldbnodei =new HashSet<Long>();
    			for(long temp:dbnodei){Ldbnodei.add(temp);}
    			//should look to apache commons primitives for a better solution to this
    			int beforesize = Ldbnodei.size();
				Ldbnodei.removeAll(fullIdSet);
				if(Ldbnodei.size()==beforesize){
					//this gets all, but we want to only include the exact if one exists
					boolean containsall = Ldbnodei.containsAll(inIdSet);
					if(containsall && inIdSet.size()==Ldbnodei.size()){
						retaln.clear();
						retaln.add(tnode);
						going = false;
					}else if(containsall){
    					retaln.add(tnode);
    					going = false;
    				}
    			}else{
					going = false;
				}
    			if(going == false){
    				break;
    			}
    		}
    	}
    	return retaln;
    }
    
    /**
     * This should find all the least inclusive common ancestors (LICA) ignoring 
     * the sampling of the outgroup or other sampling in the source tree. The idea
     * is to walk all the paths from one tip to and query at each mrca list as to 
     * whether it has at least the tip values necessary and none of the ones not
     * necessary. 
     * 
     * @param nodeSet list of all the nodes for which we are looking for the LICA
     * @param inIdSet list of the ingroup ids
     * @return
     */
    public static HashSet<Node> getSuperLICA(List<Node> nodeSet, HashSet<Long> inIdSet){
    	HashSet<Node> retaln = new HashSet<Node>();
    	Node firstNode = nodeSet.get(0);//should be the node with the fewest outgoing relationships
    	int fewestnumrel = 10000000;
    	for (int i=0;i<nodeSet.size();i++){
    		int num = 0;
    		//only way to get number of relationships
    		for(Relationship rel: nodeSet.get(i).getRelationships(Direction.OUTGOING, RelTypes.MRCACHILDOF)){num++;}
    		if(num < fewestnumrel){
    			fewestnumrel = num;
    			firstNode = nodeSet.get(i);
    		}
    	}
    	
    	Node innode = firstNode;
    	for ( Path pa : Traversal.description()
	        .depthFirst()
	        .relationships( RelTypes.MRCACHILDOF, Direction.OUTGOING )
	        .traverse( innode ) ){
    		boolean going = true;
    		for (Node tnode: pa.nodes()){
    			long [] dbnodei = (long []) tnode.getProperty("mrca");
    			HashSet<Long> Ldbnodei =new HashSet<Long>();
    			for(long temp:dbnodei){Ldbnodei.add(temp);}
    			//should look to apache commons primitives for a better solution to this
    			//this gets all, but we want to only include the exact if one exists
				boolean containsall = Ldbnodei.containsAll(inIdSet);
				if(containsall && inIdSet.size()==Ldbnodei.size()){
					retaln.clear();
					retaln.add(tnode);
					going = false;
				}else if(containsall){
					retaln.add(tnode);
					going = false;
				}
    			if(going == false){
    				break;
    			}
    		}
    	}
    	return retaln;
    }
}
