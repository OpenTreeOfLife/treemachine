package opentree.constants;

import org.neo4j.graphdb.RelationshipType;

public enum RelType implements RelationshipType {
	MRCACHILDOF, //standard rel for graph db, from node to parent
	TAXCHILDOF, //standard rel for tax db, from node to parent
	SYNONYMOF,
	STREECHILDOF, //standard rel for input tree, from node to parent
	STREEEXEMPLAROF, // rel connecting the deep taxa that input leaves are mapped to when with the shallow taxa to which the tip name corresponds
	SYNTHCHILDOF, // standard rel for stored synthesis tree
	METADATAFOR;
	//To make tree order not matter, going back to just one type of STREEREL
	//STREEEXACTCHILDOF, //these refer to branches from the input tree that have NO ADDITIONAL 
					   // inclusive children (all taxa subtending are present in the tree)
	//STREEINCLUCHILDOF,//these refer to branches from the input tree that have ADDITIONAL 
	   				  // inclusive children (NOT all taxa subtending are present in the tree)
	//ISCALLED @deprecated once the taxonomy graph was moved out// is called ,from node in graph of life to node in tax graph 
}
