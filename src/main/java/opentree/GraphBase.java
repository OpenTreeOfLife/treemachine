package opentree;

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
	
	
	protected static enum RelTypes implements RelationshipType{
		MRCACHILDOF, //standard rel for graph db, from node to parent
		TAXCHILDOF, //standard rel for tax db, from node to parent
		SYNONYMOF,
		STREECHILDOF, //standard rel for input tree, from node to parent
		SYNTHCHILDOF, // standard rel for stored synthesis tree
		METADATAFOR,
		//To make tree order not matter, going back to just one type of STREEREL
		//STREEEXACTCHILDOF, //these refer to branches from the input tree that have NO ADDITIONAL 
						   // inclusive children (all taxa subtending are present in the tree)
		//STREEINCLUCHILDOF,//these refer to branches from the input tree that have ADDITIONAL 
		   				  // inclusive children (NOT all taxa subtending are present in the tree)
		//ISCALLED @deprecated once the taxonomy graph was moved out// is called ,from node in graph of life to node in tax graph 
	}
	
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

}
