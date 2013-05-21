package opentree;


import java.util.HashSet;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;

/**
 * 
 * @author Stephen Smith
 *
 *
 * Node properties
 * 		mrca - all the subtending children ids
 * 		nested_mrcas - all the subtending children ids that would be nested (higher taxa)
 * 
 * Relationship properties
 * 		STREECHILDOF
 * 			exclusive_mrca - the mrcas (with nested ids not the full ids when applicable, 
 * 										so these need to be expanded for searches) for this inclusive relationship
 * 			root_exclusive_mrca - the root exclusive mrcas for the tree, necessary for future lica searching
 * 			lica - node ids for all the potential licas (could be just one or more if there are ambiguities)
 * 			inclusive_relids - the list of other relationship ids involved in this set
 * 			source - name of the source tree
 * 			branch_length - branch length
 * 
 * 		SYNTHCHILDOF
 */

public abstract class GraphBase {
	GraphDatabaseAgent graphDb;
	
	protected static Index<Node> graphNodeIndex;
	protected static Index<Node> synNodeIndex;
	protected static Index<Relationship> sourceRelIndex;
	protected static Index<Node> sourceRootIndex;
	protected static Index<Node> sourceMetaIndex;
	protected static Index<Node> graphTaxUIDNodeIndex; //tax_uid is the key, the uid from the taxonomy points to this node
	protected static Index<Node> synTaxUIDNodeIndex; //tax_uid is the key, this points to the synonymn node, 
													//to get the tax that this points to you need to travel synonymof
	protected static Index<Node> graphTaxNewNodes;

	public static final String DRAFTTREENAME = "otol.draft.0";
	
	public void shutdownDB(){
		graphDb.shutdownDb();
	}
	
	/**
	 * @return Checks graphNodeIndex for `name` and returns null (if the name is not found) or 
	 *  the node using IndexHits<Node>.getSingle()
	 * helper function primarily written to avoid forgetting to call hits.close();
	 */
    public Node findGraphNodeByName(final String name) {
        IndexHits<Node> hits = this.graphNodeIndex.get("name", name);
		Node firstNode = hits.getSingle();
		hits.close();
		return firstNode;
	}
    
	/**
	 * @return Checks graphTaxUIDNodes for `name` and returns null (if the name is not found) or 
	 *  the node using IndexHits<Node>.getSingle()
	 * helper function primarily written to avoid forgetting to call hits.close();
	 */
    public Node findGraphTaxNodeByUID(final String taxUID) {
        IndexHits<Node> hits = this.graphTaxUIDNodeIndex.get("tax_uid", taxUID);
		Node firstNode = hits.getSingle();
		hits.close();
		return firstNode;
	}
    
	/**
	 * @return Checks taxNodeIndex for `name` and returns null (if the name is not found) or 
	 *  the node using IndexHits<Node>.getSingle()
	 * helper function primarily written to avoid forgetting to call hits.close();
	 */
    public Node findTaxNodeByName(final String name) throws TaxonNotFoundException {
        IndexHits<Node> hits = this.graphNodeIndex.get("name", name);
		Node firstNode = hits.getSingle();
		hits.close();
		if (firstNode == null) {
			throw new TaxonNotFoundException(name);
		}
		return firstNode;
	}

	public HashSet<Node> idArrayToNodeSet(long [] nodeIDArr) {
		HashSet<Node> s = new HashSet<Node>();
		for (int i = 0; i < nodeIDArr.length; i++) {
			Node n = graphDb.getNodeById(nodeIDArr[i]);
			s.add(n);
		}
		return s;
	}
}
