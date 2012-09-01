package opentree;


/***
 * 
 * THIS IS FROM NEO4J FUTURE RELEASE AND SHOULD BE REPLACED ONCE IN THE MAIN BRANCH
 */

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import opentree.GraphBase.RelTypes;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.kernel.Traversal;

/**
 * @author Pablo Pareja Tobes
 */
public class AncestorUtil {

    /**
     * Return the lowest common ancestor of the nodes in `nodeSet` or null (if they do not share a common ancestor).
     *
     * @todo This could be made more efficient, if necessary (by walking back from each node until coalescence; a bit of a bookkeeping nightmare, though).
     *
     * @param nodeSet Set of nodes for which the LCA will be found.
     * @param expander RelationshipExpander which determines which relationships 
     *      will be traversed when looking for the least common ancestor
     * @return The LCA node if there's one, null otherwise.
     */
    public static Node lowestCommonAncestor(List<Node> nodeSet,
            RelationshipExpander expander) {

        Node lowerCommonAncestor = null;

        if (nodeSet.size() > 1) {

            Node firstNode = nodeSet.get(0);
            LinkedList<Node> firstAncestors = getAncestorsPlusSelf(firstNode, expander);

            for (int i = 1; i < nodeSet.size() && !firstAncestors.isEmpty(); i++) {
                Node currentNode = nodeSet.get(i);
                if(!lookForCommonAncestor(firstAncestors, currentNode, expander)) {
                    return null; // If we do not find a common ancestor, we should return null
                }
            }
            
            if(!firstAncestors.isEmpty()){
                lowerCommonAncestor = firstAncestors.get(0);
            }
            
        }

        return lowerCommonAncestor;
    }

    /**
     * Returns a list of nodes starting at `node` and including every node 
     *  in the "first" path created using the RelationshipExpander to the root.
     *
     * For instance if `expander` was created using Traversal.expanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING)
     *  then this function will return `node` and all of its ancestors.
     *
     * @param node starting Node
     * @param expander RelationshipExpander wraps a relationship that should not contain any cycles
     * @return LinkedList of nodes whichi starts with `node` and includes
     *      each first node returned by the iterator created by calling  
     *      expander.expand(node).iterator();
     */
    private static LinkedList<Node> getAncestorsPlusSelf(Node node,
            RelationshipExpander expander) {
        
        LinkedList<Node> ancestors = new LinkedList<Node>();

        ancestors.add(node);
        Iterator<Relationship> relIterator = expander.expand(node).iterator();

        while (relIterator.hasNext()) {
            Relationship rel = relIterator.next();
            node = rel.getOtherNode(node);       
            System.out.println(node.getId());
            ancestors.add(node);

            relIterator = expander.expand(node).iterator();

        }

        return ancestors;

    }

    /**
     * Shortens the commonAncestors list, such that it starts with the first
     * element of commonAncestors which is an ancestor of currentNode.
     * If none of the 
     * @param commonAncestors LinkedList of Nodes that should represent a node -> root path
     * @param expander RelationshipExpander wraps a relationship that should not contain 
            any cycles (e.g. the node->root path via a Traversal.expanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING) )
     * @return true if a common ancestor is found, or false indicating that `commonAncestors` 
     *      did not have a node in the path that started with `currentNode'
     */
    private static boolean lookForCommonAncestor(LinkedList<Node> commonAncestors,
            Node currentNode,
            RelationshipExpander expander) {

        while (currentNode != null) {
            // If we find the current node in commonAncestors, we removed the preceding elements from commonAncestors and return true
            for (int i = 0; i < commonAncestors.size(); i++) {
                Node node = commonAncestors.get(i);
                if (node.getId() == currentNode.getId()) {
                    if (i > 0) {
                        commonAncestors.subList(0, i).clear();
                    }
                    return true;
                }
            }

            // iterator to the next "ancestor" 
            Iterator<Relationship> relIt = expander.expand(currentNode).iterator();
            if (relIt.hasNext()) {
                Relationship rel = relIt.next();
                currentNode = rel.getOtherNode(currentNode); 
            }else{
                currentNode = null;
            }
        }
    return false;
    }
    
    /**
     * TODO: deal with the nested_mrcas (higher taxon names)
     * This should find all the least inclusive common ancestors (LICA). The idea
     * is to walk all the paths from one tip to and query at each mrca list as to 
     * whether it has at least the tip values necessary and none of the ones not
     * necessary. 
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
     * TODO: deal with the nested_mrcas (higher taxon names)
     * This should find all the least inclusive common ancestors (LICA) ignoring 
     * the sampling of the outgroup or other sampling in the source tree. The idea
     * is to walk all the paths from one tip to and query at each mrca list as to 
     * whether it has at least the tip values necessary and none of the ones not
     * necessary. 
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
