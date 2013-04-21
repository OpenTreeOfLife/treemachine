package opentree;
import java.lang.UnsupportedOperationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Stack;
import opentree.GraphBase.RelTypes;
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

    /// @note: if the tree maps through multiple LICAs, the current behavior will be to return all of these nodes
    public static FilterByPropertyRelIterator getSourceTreeRelIterator(Node startNode, int maxDepthArg, String sourceName) {
        return new FilterByPropertyRelIterator(startNode, maxDepthArg, RelTypes.STREECHILDOF, Direction.INCOMING, "source", sourceName);
    }
    /**
     * FilterByPropertyRelIterator(startNode, 4, STREECHILDOF, INCOMING, "source", "bogus") would create an iterator for the relationships
     *  in a tree with the source namd "bogus" which are within 4 edges of the root
     */
    public FilterByPropertyRelIterator(Node startNode, int maxDepthArg, RelationshipType relTypeArg, Direction dirArg, String propName, String propVal) {
        this.maxDepth = maxDepthArg;
        this.relType = relTypeArg;
        this.direction = dirArg;
        this.propertyName = propName;
        this.propertyValue = propVal;
        this.lastReturned = null;
        this.nextToReturn = null;
        this.start = startNode;
        this.toReturnStack = new Stack<LinkedList<Relationship> >();
        if (maxDepth != 0) {
            this.nextToReturn = pushIteratorsForNode(startNode);
        }
        this.lastDepth = 0;
    }

    private Relationship pushIteratorsForNode(Node parNode) {
        LinkedList<Relationship> llr = new LinkedList<Relationship>();
        for (Relationship rel : parNode.getRelationships(this.relType, this.direction)) {
            if (rel.hasProperty(this.propertyName)) {
                String s = (String) rel.getProperty(this.propertyName);
                if (s.equals(this.propertyValue)) {
                    llr.add(rel);
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
