package opentree;


/***
 * 
 * THIS IS FROM NEO4J FUTURE RELEASE AND SHOULD BE REPLACED ONCE IN THE MAIN BRANCH
 */

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipExpander;

/**
 * @author Pablo Pareja Tobes
 */
public class AncestorUtil {

    /**
     * 
     * @param nodeSet Set of nodes for which the LCA will be found.
     * @param relationshipType Relationship type used to look for the LCA
     * @param relationshipDirection Direction of the relationships used (seen from the descendant node)
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
                lookForCommonAncestor(firstAncestors, currentNode, expander);                
            }
            
            if(!firstAncestors.isEmpty()){
                lowerCommonAncestor = firstAncestors.get(0);
            }
            
        }

        return lowerCommonAncestor;
    }

    private static LinkedList<Node> getAncestorsPlusSelf(Node node,
            RelationshipExpander expander) {
        
        LinkedList<Node> ancestors = new LinkedList<Node>();

        ancestors.add(node);
        Iterator<Relationship> relIterator = expander.expand(node).iterator();

        while (relIterator.hasNext()) {
            Relationship rel = relIterator.next();
            node = rel.getOtherNode(node);       

            ancestors.add(node);

            relIterator = expander.expand(node).iterator();

        }

        return ancestors;

    }

    private static void lookForCommonAncestor(LinkedList<Node> commonAncestors,
            Node currentNode,
            RelationshipExpander expander) {

        while (currentNode != null) {
            for (int i = 0; i < commonAncestors.size(); i++) {
                Node node = commonAncestors.get(i);
                if (node.getId() == currentNode.getId()) {
                    for (int j = 0; j < i; j++) {
                        commonAncestors.pollFirst();
                    }
                    return;
                }
            }

            Iterator<Relationship> relIt = expander.expand(currentNode).iterator();

            if (relIt.hasNext()) {
                
                Relationship rel = relIt.next();
                
                currentNode = rel.getOtherNode(currentNode); 
                
            }else{
                currentNode = null;
            }
        }

    }
}
