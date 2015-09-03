package org.neo4j.server.rest.repr;

import jade.tree.deprecated.JadeNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import opentree.constants.RelProperty;
import opentree.constants.RelType;
import org.opentree.utils.GeneralUtils;
import org.opentree.graphdb.GraphDatabaseAgent;
import org.neo4j.graphdb.Node;
import opentree.constants.SourceProperty;
import org.neo4j.graphdb.Relationship;

public class ArgusonRepresentationConverter extends MappingRepresentation {

	public ArgusonRepresentationConverter(RepresentationType type) {
		super(type);
	}

	ArgusonRepresentationConverter(String type) {
		super(type);
	}
	
	/**
	 * Return an Representation object capable of serializing arguson description of `inNode`
	 * 
	 * This implementation does not return branch lengths.
	 * 
	 * @param inNode
	 * @return arguson description of inNode
	 */
	public static ArgusonRepresentationConverter getArgusonRepresentationForJadeNode(
			final JadeNode inNode) {
		return new ArgusonRepresentationConverter(RepresentationType.MAP.toString()) {

			@Override
			protected void serialize(final MappingSerializer serializer) {
				/*
				 * EXAMPLE CODE FROM TNRSRESULTSREPRESENTATION
				 * 
				HashMap<String, Object> tnrsResultsMap = new HashMap<String, Object>();

				tnrsResultsMap
						.put("governing_code", results.getGoverningCode());
				tnrsResultsMap.put("unambiguous_names",
						results.getNamesWithDirectMatches());
				tnrsResultsMap.put("unmatched_names",
						results.getUnmatchedNames());
				tnrsResultsMap.put("matched_names", results.getMatchedNames());
				tnrsResultsMap.put("context", results.getContextName());

				for (Map.Entry<String, Object> pair : tnrsResultsMap.entrySet()) {
					String key = pair.getKey();
					Object value = pair.getValue();

					if (value instanceof String) {
						serializer.putString(key, (String) value);

					} else if (value instanceof Set) {
						serializer.putList(key, OpentreeRepresentationConverter
								.getListRepresentation((Set) value));
					}
				}

				serializer.putList("results", OpentreeRepresentationConverter
						.getListRepresentation(results));
						
				*
				* END EXAMPLE CODE
				*/
							
				if (inNode.getName() != null) {
					serializer.putString("name", inNode.getName());
				} else {
					serializer.putString("name", "");
				}

				if (inNode.getObject("nodeid") != null) {
					serializer.putNumber("nodeid", (Long) inNode.getObject("nodeid"));
				}
				
				ArrayList<Representation> children = new ArrayList<Representation>();
				for (int i = 0; i < inNode.getChildCount(); i++) {
					children.add(ArgusonRepresentationConverter.getArgusonRepresentationForJadeNode(inNode.getChild(i)));
				}
				
				if (children.size() > 0) {
					serializer.putList("children", OTRepresentationConverter.getListRepresentation(children));
				}
				
				if (inNode.getObject("nodedepth") != null) {
					serializer.putNumber("maxnodedepth", (Integer) inNode.getObject("nodedepth"));
				}
				
				if (inNode.getObject("tip_descendants") != null) {
					serializer.putNumber("nTipDescendants", (Integer) inNode.getObject("tip_descendants"));
				}
				
				if (inNode.isInternal()) {
					serializer.putNumber("nleaves", inNode.getTips().size());
				} else {
					serializer.putNumber("nleaves", 0);
				}
				
				String [] optProperties = {"uniqName", "taxSource", "taxSourceId", "taxRank", "ottId"};
				for (String optPropertyName : optProperties) {
					if (inNode.getObject(optPropertyName) != null) {
						serializer.putString(optPropertyName, (String) inNode.getObject(optPropertyName));
					}
				}
				
				// add in metadata for pathToRoot (requested by jimallman)
				List<Node> pathToRoot = (List<Node>) inNode.getObject("pathToRoot");
				if (pathToRoot != null) {
					LinkedList<Representation> pathToRootRepresentation = new LinkedList<Representation>();

					for (Node m : pathToRoot) {
						pathToRootRepresentation.add(ArgusonRepresentationConverter.getNodeRepresentationWithMetadata(m));
					}
					serializer.putList("pathToRoot", OTRepresentationConverter.getListRepresentation(pathToRootRepresentation));
				}
				
				if (inNode.getObject("hasChildren") != null) {
					serializer.putBoolean("hasChildren", (Boolean) inNode.getObject("hasChildren"));
				}
				
				String[] dnl = (String[]) inNode.getObject("descendantNameList");
				if (dnl != null) {
					LinkedList<String> dnlList = new LinkedList<String>();
					for (int i = 0; i < dnl.length; i++) {
						dnlList.add(dnl[i]);
					}
					serializer.putList("descendantNameList", OTRepresentationConverter.getListRepresentation(dnlList));
				}

				// report tree IDs supporting each clade as supportedBy list of strings
				String[] sup = (String[]) inNode.getObject("supporting_sources");
				if (sup != null) {
					LinkedList<String> supList = new LinkedList<String>();
					for (int i = 0; i < sup.length; i++) {
						supList.add(sup[i]);
					}
					serializer.putList("supportedBy", OTRepresentationConverter.getListRepresentation(supList));
				}
								
				// report metadata for the sources mentioned in supporting_sources. the sourceMetaList property
				//	should be set for the root
				Object n2m = inNode.getObject("sourceMetaList");
				if (n2m != null) {
					serializer.putMapping("sourceToMetaMap", getSourceMetadataRepresentation(inNode));
				}
			}
		};
	}

	/** Returns a representation object capable of serializing a brief summary of a Node. Currently the fields written are: nodeid, name.
	 * @param nd
	 * @returns node representation
	 */
	public static Representation getNodeRepresentationSimple(final Node n) {
		
		HashMap<String, Object> nodeInfoMap = new HashMap<String, Object>();
		nodeInfoMap.put("nodeid", n.getId());
		if (n.hasProperty("name")) {
			nodeInfoMap.put("name", n.getProperty("name"));
		}
		
		return GeneralizedMappingRepresentation.getMapRepresentation(nodeInfoMap);
	}
	
	// same as above, but with more info returned
	public static Representation getNodeRepresentationWithMetadata(final Node nd) {
		
		HashMap<String, Object> nodeInfoMap = new HashMap<String, Object>();
		nodeInfoMap.put("nodeid", nd.getId());
		if (nd.hasProperty("name")) {
			nodeInfoMap.put("name", nd.getProperty("name"));
		}
		if (nd.hasProperty("uniqname")) {
			nodeInfoMap.put("uniqname", nd.getProperty("uniqname"));
		}
		if (nd.hasProperty("tax_source")) {
			nodeInfoMap.put("taxSource", nd.getProperty("tax_source"));
		}
		if (nd.hasProperty("tax_sourceid")) {
			nodeInfoMap.put("taxSourceId", nd.getProperty("tax_sourceid"));
		}
		if (nd.hasProperty("tax_rank")) {
			nodeInfoMap.put("taxRank", nd.getProperty("tax_rank"));
		}
		if (nd.hasProperty("tax_uid")) {
			nodeInfoMap.put("ottId", nd.getProperty("tax_uid"));
		}
		
		LinkedList<String> sourceList = new LinkedList<String>();
		if (nd.hasRelationship(RelType.SYNTHCHILDOF)) {
			for (Relationship rel : nd.getRelationships(RelType.SYNTHCHILDOF)) {
				if (rel.hasProperty("supporting_sources")) {
					String[] sources = (String[]) rel.getProperty(RelProperty.SUPPORTING_SOURCES.propertyName);
					for (String s : sources) {
						if (!sourceList.contains(s)) {
							sourceList.add(s);
						}
					}
				}
			}
		}
		if (sourceList.size() != 0) {
			nodeInfoMap.put("supporting_sources", sourceList);
		}
		
		return GeneralizedMappingRepresentation.getMapRepresentation(nodeInfoMap);
	}
	
	public static MappingRepresentation getSourceMetadataRepresentation(JadeNode inNode) {
		
		HashMap<String, Node> sourceNameToMetadataNodeMap = (HashMap<String, Node>) inNode.getObject("sourceMetaList");
		HashMap<String, Object> sourceMetadataMap = new HashMap<String, Object>();
		
		for (String sourceName : sourceNameToMetadataNodeMap.keySet()) {
			
			HashMap<String, Object> studyMetadata = new HashMap<String, Object>();
			
			Node metadataNode = sourceNameToMetadataNodeMap.get(sourceName);
			if (sourceName == null || sourceName.length() == 0) {
				sourceName = "unnamedSource";
			}
			if (metadataNode == null) {
				sourceMetadataMap.put(sourceName, null);
			} else {
				for (SourceProperty p : SourceProperty.values()) {
					if (metadataNode.hasProperty(p.propertyName)) {
						if (!p.propertyName.equals("newick")) {
							if (p.propertyName.equals("source")) {
								String sStudy = String.valueOf(metadataNode.getProperty(p.propertyName));
								if (sStudy.compareTo("taxonomy") == 0) {
									// get taxonomy version. stored at node 0
									GraphDatabaseAgent gda = new GraphDatabaseAgent(metadataNode.getGraphDatabase());
									String taxVersion = String.valueOf(gda.getGraphProperty("graphRootNodeTaxonomy"));
									gda.shutdownDb();
									studyMetadata.put("version", taxVersion);
								} else {
									HashMap<String, Object> indStudy = GeneralUtils.reformatSourceID(sStudy);
									studyMetadata.putAll(indStudy);
								}
							} else { // allow the possibility of future metadata
								studyMetadata.put(p.propertyName, p.type.cast(metadataNode.getProperty(p.propertyName)));
							}
						}
					}
				}
				sourceMetadataMap.put(sourceName, studyMetadata);
			}
		}

		return GeneralizedMappingRepresentation.getMapRepresentation(sourceMetadataMap);
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	// general serialization methods below here, mostly just copied from Neo4j
	// RepresentationConverter classes
	//
	// ///////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	String serialize(RepresentationFormat format, URI baseUri,
			ExtensionInjector extensions) {
		MappingWriter writer = format.serializeMapping(type);
		Serializer.injectExtensions(writer, this, baseUri, extensions);
		serialize(new MappingSerializer(writer, baseUri, extensions));
		writer.done();
		return format.complete(writer);
	}

	@Override
	void addTo(ListSerializer serializer) {
		serializer.addMapping(this);
	}

	@Override
	void putTo(MappingSerializer serializer, String key) {
		serializer.putMapping(key, this);
	}

	@Override
	protected void serialize(MappingSerializer serializer) {
		throw new java.lang.UnsupportedOperationException("unimplemented method");
	}		
}
