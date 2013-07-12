package org.neo4j.server.rest.repr;

import jade.tree.JadeNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.Node;

import opentree.JSONExporter;
import opentree.synthesis.SourceProperty;

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
							
//				public String getJSON(boolean bl) {
//				StringBuffer ret = new StringBuffer("{");

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
//					if (i == 0) {
//						ret.append("\n, \"children\": [\n");
//					}
//					ret.append(this.getChild(i).getJSON(bl));
//					if (i == this.getChildCount() - 1) {
//						ret.append("]\n");
//					} else {
//						ret.append(",\n");
//					}

					children.add(ArgusonRepresentationConverter.getArgusonRepresentationForJadeNode(inNode.getChild(i)));
					
				}
				
				if (children.size() > 0) {
					serializer.putList("children", OpenTreeMachineRepresentationConverter.getListRepresentation(children));
				}
				
				// if you want branch lengths
//					serializer.putNumber("size", inNode.getBL());

				// what is this for? can't serialize already serialized json
//				if (inNode.getObject("jsonprint") != null) {
//					ret.append(this.getObject("jsonprint"));
//				}

				if (inNode.getObject("nodedepth") != null) {
					serializer.putNumber("maxnodedepth", (Integer) inNode.getObject("nodedepth"));
				}
				
				if (inNode.isInternal()) {
					serializer.putNumber("nleaves", inNode.getTips().size());
				} else {
					serializer.putNumber("nleaves", 0);
				}
				
				String [] optProperties = {"uniqName", "taxSource", "taxSourceId", "taxRank", "ottolId"};
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
//					ret.append(", ");
//					JSONExporter.escapePropertyColon(ret, "pathToRoot");
//					JSONExporter.writeListOfNodesAsJSONSummary(ret, pathToRoot);
					serializer.putList("pathToRoot", OpenTreeMachineRepresentationConverter.getListRepresentation(pathToRootRepresentation));
				}
				
				if (inNode.getObject("hasChildren") != null) {
					serializer.putBoolean("hasChildren", (Boolean) inNode.getObject("hasChildren"));
//					ret.append(", ");
//					JSONExporter.escapePropertyColon(ret, "hasChildren");
//					JSONExporter.writeBooleanAsJSON(ret, (Boolean) hc);
				}
				
				String[] dnl = (String[]) inNode.getObject("descendantNameList");
				if (dnl != null) {
//					ret.append(", \"descendantNameList\": ");
//					JSONExporter.writeStringArrayAsJSON(ret, v dnl);
					LinkedList<String> dnlList = new LinkedList<String>();
					for (int i = 0; i < dnl.length; i++) {
						dnlList.add(dnl[i]);
					}
					serializer.putList("descendantNameList", OpenTreeMachineRepresentationConverter.getListRepresentation(dnlList));
				}

				// report tree IDs supporting each clade as supportedBy list of strings
				String[] sup = (String[]) inNode.getObject("supporting_sources");
				if (sup != null) {
//					ret.append(", \"supportedBy\": ");
//					JSONExporter.writeStringArrayAsJSON(ret, (String []) sup);
					LinkedList<String> supList = new LinkedList<String>();
					for (int i = 0; i < sup.length; i++) {
						supList.add(sup[i]);
					}
					serializer.putList("supportedBy", OpenTreeMachineRepresentationConverter.getListRepresentation(supList));

				}
								
				// report metadata for the sources mentioned in supporting_sources. the sourceMetaList property
				//	should be set for the root
				Object n2m = inNode.getObject("sourceMetaList");
				if (n2m != null) {
//					ret.append(", ");
//					JSONExporter.writeSourceToMetaMapForArgus(ret, n2m);
					serializer.putMapping("sourceToMetaMap", getSourceMetadataRepresentation(inNode));
				}
//				ret.append("}");
//				return ret.toString();
				/*
			}
			 */
				
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
		nodeInfoMap.put("nodeid", (Long) n.getId());
		if (n.hasProperty("name")) {
			nodeInfoMap.put("name", (String) n.getProperty("name"));
		}
		
		return GeneralizedMappingRepresentation.getMapRepresentation(nodeInfoMap);
	}
	
	
	public static MappingRepresentation getSourceMetadataRepresentation(JadeNode inNode) {
		
//		buffer.append("\"sourceToMetaMap\": {");
		HashMap<String, Node> sourceNameToMetadataNodeMap = (HashMap<String, Node>) inNode.getObject("sourceMetaList");
//		boolean first = true;

		HashMap<String, Object> sourceMetadataMap = new HashMap<String, Object>();
		
		for (String sourceName : sourceNameToMetadataNodeMap.keySet()) {
//			if (first) {
//				first = false;
//			} else {
//				buffer.append(",");
//			}
//			String source = n2mEl.getKey();
			Node metadataNode = sourceNameToMetadataNodeMap.get(sourceName);
			if (sourceName == null || sourceName.length() == 0) {
				sourceName = "unnamedSource";
			}

			if (metadataNode == null) {
				sourceMetadataMap.put(sourceName, null);

			} else {
				HashMap<String, Object> studyMetadata = new HashMap<String, Object>();
				
//				LinkedList<String> propertyNames = new LinkedList<String>();
				
				for (SourceProperty p : SourceProperty.values()) {
					if (metadataNode.hasProperty(p.propertyName)) {
						studyMetadata.put(p.propertyName, p.type.cast(metadataNode.getProperty(p.propertyName)));
					}
				}
				HashMap<String, Map<String, Object>> studyMetadataContainer = new HashMap<String, Map<String, Object>>();
				studyMetadataContainer.put("study", studyMetadata);
				sourceMetadataMap.put(sourceName, studyMetadataContainer);

//				buffer.append("{\"study\": {");
//				wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:studyPublication", wrotePrev) || wrotePrev;
//				wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:curatorName", wrotePrev) || wrotePrev;
//				wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:dataDeposit", wrotePrev) || wrotePrev;
//				wrotePrev = writeStringPropertyIfFound(buffer, metadataNode, "ot:studyId", wrotePrev) || wrotePrev;
//				wrotePrev = writeIntegerPropertyIfFound(buffer, metadataNode, "ot:studyYear", wrotePrev) || wrotePrev;
//				buffer.append("}}");
			}
		}

		return GeneralizedMappingRepresentation.getMapRepresentation(sourceMetadataMap);
//		buffer.append("}");
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
