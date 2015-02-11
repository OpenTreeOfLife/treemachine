package org.neo4j.server.rest.repr;

import jade.tree.deprecated.JadeNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.Node;
import org.neo4j.helpers.collection.FirstItemIterable;
import org.neo4j.helpers.collection.IterableWrapper;
import org.neo4j.server.rest.repr.GeneralizedMappingRepresentation;

import opentree.constants.SourceProperty;
import scala.actors.threadpool.Arrays;

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
				
				List<Node> pathToRoot = (List<Node>) inNode.getObject("pathToRoot");
				if (pathToRoot != null) {
					LinkedList<Representation> pathToRootRepresentation = new LinkedList<Representation>();

					for (Node m : pathToRoot) {
						pathToRootRepresentation.add(ArgusonRepresentationConverter.getNodeRepresentationSimple(m));
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
	public static Representation getNodeRepresentationSimple(
			final Node n) {
		
		HashMap<String, Object> nodeInfoMap = new HashMap<String, Object>();
		nodeInfoMap.put("nodeid", n.getId());
		if (n.hasProperty("name")) {
			nodeInfoMap.put("name", n.getProperty("name"));
		}
		
		return GeneralizedMappingRepresentation.getMapRepresentation(nodeInfoMap);
	}
	
	
	public static MappingRepresentation getSourceMetadataRepresentation(JadeNode inNode) {
		
		HashMap<String, Node> sourceNameToMetadataNodeMap = (HashMap<String, Node>) inNode.getObject("sourceMetaList");
		HashMap<String, Object> sourceMetadataMap = new HashMap<String, Object>();
		
		for (String sourceName : sourceNameToMetadataNodeMap.keySet()) {
			Node metadataNode = sourceNameToMetadataNodeMap.get(sourceName);
			if (sourceName == null || sourceName.length() == 0) {
				sourceName = "unnamedSource";
			}

			if (metadataNode == null) {
				sourceMetadataMap.put(sourceName, null);

			} else {
				HashMap<String, Object> studyMetadata = new HashMap<String, Object>();
								
				for (SourceProperty p : SourceProperty.values()) {
					if (metadataNode.hasProperty(p.propertyName)) {
                        if (!p.propertyName.equals("newick"))
                            studyMetadata.put(p.propertyName, p.type.cast(metadataNode.getProperty(p.propertyName)));
					}
				}
				HashMap<String, Map<String, Object>> studyMetadataContainer = new HashMap<String, Map<String, Object>>();
				studyMetadataContainer.put("study", studyMetadata);
				sourceMetadataMap.put(sourceName, studyMetadataContainer);

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
