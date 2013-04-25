package opentree;
import java.lang.UnsupportedOperationException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Stack;
import opentree.RelTypes;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
//import org.apache.log4j.Logger;

/**
 * Iterator which does a preorder traversal from a particular node of only relationships which have:
 *      1. a specified type, and 
 *      2. a specifiec property value
 */
public class FilterByPropertyRelIterator implements Iterator<Relationship> {
    //static Logger _LOG = Logger.getLogger(FilterByPropertyRelIterator.class);
    private Relationship lastReturned;
    private Relationship nextToReturn;
    int maxDepth;
    String sourceName;
    Node start;
    Direction direction;
    RelationshipType relType;
    String propertyName;
    String propertyValue;
    Stack<LinkedList<Relationship> > toReturnStack;
    int lastDepth;
    GraphBase graphDB; // longs -> Nodes inefficiency...only needed if we are going to instantiate the long[] into nodes. @TEMP, slow...
    HashSet<Node> leafSet; // longs -> Nodes inefficiency...

    /// @note: if the tree maps through multiple LICAs, the current behavior will be to return all of these nodes
    public static FilterByPropertyRelIterator getSourceTreeRelIterator(Node startNode, int maxDepthArg, GraphBase gb, Node metadataNode) {
        String sourceName = (String)metadataNode.getProperty("source");
        return new FilterByPropertyRelIterator(startNode, maxDepthArg, RelTypes.STREECHILDOF, Direction.INCOMING, "source", sourceName, gb, metadataNode);
    }
    /**
     * FilterByPropertyRelIterator(startNode, 4, STREECHILDOF, INCOMING, "source", "bogus") would create an iterator for the relationships
     *  in a tree with the source namd "bogus" which are within 4 edges of the root
     */
    public FilterByPropertyRelIterator(Node startNode,
                                       int maxDepthArg,
                                       RelationshipType relTypeArg,
                                       Direction dirArg,
                                       String propName,
                                       String propVal,
                                       GraphBase gb,
                                       Node metadataNode) {
        this.maxDepth = maxDepthArg;
        this.relType = relTypeArg;
        this.direction = dirArg;
        this.propertyName = propName;
        this.propertyValue = propVal;
        this.lastReturned = null;
        this.nextToReturn = null;
        this.start = startNode;
        // If each set of "tied" LICA rels was tagged with a ordinal property (relset=0, relset=1,...) then we could
        // avoid these big, slow set operations....
        long [] leafIDArr = (long[]) metadataNode.getProperty("original_taxa_map"); //@TEMP this is going to be slow on big trees...
        this.leafSet = gb.idArrayToNodeSet(leafIDArr);
        debugnodeset("treeleaves:", this.leafSet);
        long [] nodeMRCAArr = (long[]) startNode.getProperty("mrca"); //@TEMP this is going to be slow deep in the tree...
        HashSet<Node> rootNodesLeaves = gb.idArrayToNodeSet(nodeMRCAArr);
        debugnodeset("mrca:", rootNodesLeaves);
        this.graphDB = gb;
        this.leafSet.retainAll(rootNodesLeaves);
        debugnodeset("subtreeleaves:", this.leafSet);
        this.toReturnStack = new Stack<LinkedList<Relationship> >();
        // end longs -> Nodes inefficiency...
        
        if (maxDepth != 0) {
            this.nextToReturn = pushIteratorsForNode(startNode);
        }
        this.lastDepth = 0;
    }
    
    private void debugnodeset(String e, HashSet<Node> n) {
        System.err.print("debugnodeset " + e + ":  ");
        LinkedList<Long> ndIDList = new LinkedList<Long>();
        for (Node nd : n) {
            ndIDList.add(nd.getId());
        }
        Collections.sort(ndIDList);
        for (Long lid : ndIDList) {
            System.err.print(", " + lid);
        }
        System.err.println(".");
    }

    private Relationship pushIteratorsForNode(Node parNode) {
        HashSet<Node> incAtThisLevel = new HashSet<Node>();
        LinkedList<Relationship> llr = new LinkedList<Relationship>();
        String d = "level" + this.toReturnStack.size();

        for (Relationship rel : parNode.getRelationships(this.relType, this.direction)) {
            if (rel.hasProperty(this.propertyName)) {
                String s = (String) rel.getProperty(this.propertyName);
                if (s.equals(this.propertyValue)) {
                    // @TEMP more longs -> Nodes inefficiency...
                    Node furtherNode = (this.direction == Direction.INCOMING ? rel.getStartNode() : rel.getEndNode());
                    HashSet<Node> fnNodes;
                    if (furtherNode.hasProperty("mrca")) {
                        long [] nodeMRCAArr = (long[]) furtherNode.getProperty("mrca"); //@TEMP this is going to be slow deep in the tree...
                        fnNodes = this.graphDB.idArrayToNodeSet(nodeMRCAArr);
                        fnNodes.retainAll(this.leafSet);
                    } else {
                        fnNodes = new HashSet<Node>();
                        fnNodes.add(furtherNode);
                    }
                    debugnodeset(d + "fnNodes", fnNodes);
                    // end longs -> Nodes inefficiency...
                    final int numLeavesForThisTree = fnNodes.size();
                    fnNodes.removeAll(incAtThisLevel);
                    if (numLeavesForThisTree == fnNodes.size()) {
                        llr.add(rel);
                        incAtThisLevel.addAll(fnNodes);
                        debugnodeset(d + "incAtThisLevel", incAtThisLevel);
                    }
                }
            }
        }
        int s = llr.size();
        if (s > 0) {
            Relationship nr = llr.removeFirst();
            this.toReturnStack.push(llr);
            return nr;
        }
        return null;
    }

    private Relationship advance() {
        if (this.nextToReturn == null) {
            return null;
        }
        if (this.maxDepth < 0 || this.maxDepth > this.toReturnStack.size()) {
            Node furtherNode =(this.direction == Direction.INCOMING ? this.nextToReturn.getStartNode() : this.nextToReturn.getEndNode());
            this.nextToReturn = pushIteratorsForNode(furtherNode);
            if (this.nextToReturn != null) {
                return this.nextToReturn;
            }
        }
        // need to go to sib (or aunt, or great aunt...)
        while (this.toReturnStack.empty() == false) {
            LinkedList<Relationship> prevLevel = this.toReturnStack.peek();
            if (prevLevel.size() > 0) {
                this.nextToReturn = prevLevel.removeFirst();
                return this.nextToReturn;
            } else {
                this.toReturnStack.pop();
            }
        }
        this.nextToReturn = null;
        return null;
    }

    public int getLastElementDepth() {
        return this.lastDepth;
    }

    public boolean hasNext() {
        return (this.nextToReturn != null); 
    }

    public Relationship next() {
        Relationship r = this.nextToReturn;
        if (r == null) {
            throw new NoSuchElementException();
        }
        this.lastDepth = this.toReturnStack.size();
        this.nextToReturn = this.advance();
        return r;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

}
