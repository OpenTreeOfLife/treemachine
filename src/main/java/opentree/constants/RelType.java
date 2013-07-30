package opentree.constants;

import org.neo4j.graphdb.RelationshipType;

public enum RelType implements RelationshipType {
	MRCACHILDOF, //standard rel for graph db, from node to parent
	TAXCHILDOF, //standard rel for tax db, from node to parent
	SYNONYMOF,
	STREECHILDOF, //standard rel for input tree, from node to parent
	STREEEXEMPLAROF, // rel connecting shallow exemplar taxa (mapped directly to input tips) to the deep taxa they exemplify, which are what are used in synthesis
	SYNTHCHILDOF, // standard rel for stored synthesis tree
	SYNTHMETADATAFOR, // relationship for synthesis meta data node. points to the start node of the synthesis
	METADATAFOR;
}
