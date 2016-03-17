package org.neo4j.server.rest.repr;

import jade.tree.deprecated.JadeNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import opentree.constants.RelType;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Direction;
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
    public static ArgusonRepresentationConverter getArgusonRepresentationForJadeNode (
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
                
                String treeID = (String) inNode.getObject("treeID");
                
                if (inNode.getName() != null) {
                    serializer.putString("name", inNode.getName());
                } else {
                    serializer.putString("name", "");
                }
                if (inNode.getObject("node_id") != null) {
                    serializer.putString("node_id", (String) inNode.getObject("node_id"));
                }
                ArrayList<Representation> children = new ArrayList<Representation>();
                for (int i = 0; i < inNode.getChildCount(); i++) {
                    children.add(ArgusonRepresentationConverter.getArgusonRepresentationForJadeNode(inNode.getChild(i)));
                }
                if (children.size() > 0) {
                    serializer.putList("children", OTRepresentationConverter.getListRepresentation(children));
                }
                if (inNode.getObject("nodedepth") != null) {
                    serializer.putNumber("max_node_depth", (Integer) inNode.getObject("nodedepth"));
                }
                if (inNode.getObject("tip_descendants") != null) {
                    serializer.putNumber("n_tip_descendants", (Integer) inNode.getObject("tip_descendants"));
                }
                if (inNode.isInternal()) {
                    serializer.putNumber("n_leaves", inNode.getTips().size());
                } else {
                    serializer.putNumber("n_leaves", 0);
                }
                
                if (inNode.getObject("ott_id") != null) {
                    serializer.putNumber("ott_id", (Long) inNode.getObject("ott_id"));
                }
                
                String [] optProperties = {"unique_name", "tax_rank", "node_id"};
                for (String optPropertyName : optProperties) {
                    if (inNode.getObject(optPropertyName) != null) {
                        serializer.putString(optPropertyName, (String) inNode.getObject(optPropertyName));
                    }
                }
                if (inNode.getObject("tax_sources") != null) {
                    HashMap<String, Object> tsMap = (HashMap<String, Object>) inNode.getObject("tax_sources");
                    serializer.putMapping("tax_sources", GeneralizedMappingRepresentation.getMapRepresentation(tsMap));
                }
                
                // add in metadata for pathToRoot (requested by jimallman)
                List<Node> pathToRoot = (List<Node>) inNode.getObject("path_to_root");
                if (pathToRoot != null) {
                    LinkedList<Representation> pathToRootRepresentation = new LinkedList<Representation>();

                    for (Node m : pathToRoot) {
                        pathToRootRepresentation.add(ArgusonRepresentationConverter.getNodeRepresentationWithMetadata(m, treeID));
                    }
                    serializer.putList("path_to_root", OTRepresentationConverter.getListRepresentation(pathToRootRepresentation));
                }
                
                if (inNode.getObject("has_children") != null) {
                    serializer.putBoolean("has_children", (Boolean) inNode.getObject("has_children"));
                }
                
                String[] dnl = (String[]) inNode.getObject("descendantNameList");
                if (dnl != null) {
                    LinkedList<String> dnlList = new LinkedList<String>();
                    for (int i = 0; i < dnl.length; i++) {
                        dnlList.add(dnl[i]);
                    }
                    serializer.putList("descendant_name_list", OTRepresentationConverter.getListRepresentation(dnlList));
                }
                
                if (inNode.getObject("annotations") != null) {
                    HashMap<String, Object> ann = (HashMap<String, Object>) inNode.getObject("annotations");
                    for (String indProp : ann.keySet()) {
                        HashMap<String, Object> prop = (HashMap<String, Object>) ann.get(indProp);
                        serializer.putMapping(indProp, GeneralizedMappingRepresentation.getMapRepresentation(prop));
                    }
                }
                
                // source id map
                if (inNode.getObject("sourceMap") != null) {
                    HashMap<String, Object> sMap = (HashMap<String, Object>) inNode.getObject("sourceMap");
                    serializer.putMapping("sourceToMetaMap", GeneralizedMappingRepresentation.getMapRepresentation(sMap));
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
    public static Representation getNodeRepresentationWithMetadata(final Node nd, String treeID) {
        
        HashMap<String, Object> nodeInfoMap = new HashMap<String, Object>();
        //nodeInfoMap.put("nodeid", nd.getId());
        if (nd.hasProperty("name")) {
            nodeInfoMap.put("name", nd.getProperty("name"));
        }
        if (nd.hasProperty("unique_name")) {
            nodeInfoMap.put("unique_name", nd.getProperty("unique_name"));
        }
        if (nd.hasProperty("tax_source")) {
            String tSrc = (String) nd.getProperty("tax_source");
            HashMap<String, String> res = stringToMap(tSrc);
            nodeInfoMap.put("tax_sources", res);
        }
        if (nd.hasProperty("tax_rank")) {
            nodeInfoMap.put("tax_rank", nd.getProperty("tax_rank"));
        }
        if (nd.hasProperty("tax_uid")) {
            nodeInfoMap.put("ott_id", Long.valueOf((String) nd.getProperty("tax_uid")));
        }
        if (nd.hasProperty("ot_node_id")) {
            nodeInfoMap.put("node_id", nd.getProperty("ot_node_id"));
        }
        
        HashMap<String, Object> md = getSynthMetadata(nd, treeID);
        for (String indProp : md.keySet()) {
            HashMap<String, Object> prop = (HashMap<String, Object>) md.get(indProp);
            nodeInfoMap.put(indProp, prop);
        }
        
        /*
        // this does not exist
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
        */
        return GeneralizedMappingRepresentation.getMapRepresentation(nodeInfoMap);
    }
    
    public static HashMap<String, Object> getSynthMetadata (Node curNode, String treeID) {
        HashMap<String, Object> results = new HashMap<>();
        if (curNode.hasRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
            for (Relationship rel : curNode.getRelationships(RelType.SYNTHCHILDOF, Direction.OUTGOING)) {
                if (String.valueOf(rel.getProperty("name")).equals(treeID)) {
                    // loop over properties
                    for (String key : rel.getPropertyKeys()) {
                        if (!"name".equals(key) && !"tip_descendants".equals(key)) {
                            HashMap<String, String> mapProp = stringToMap((String) rel.getProperty(key));
                            results.put(key, mapProp);
                        }
                    }
                }
            }
        }
        return results;
    }
    
    public static HashMap<String, String> stringToMap (String source) {
        
        HashMap<String, String> res = new HashMap<>();
        /// format will be: git_sha:c6ce2f9067e9c74ca7b1f770623bde9b6de8bd1f,tree_id:tree1,study_id:ot_157
        String [] props = source.split(",");
        for (String s : props) {
            String[] terp = s.split(":");
            if (terp.length == 2) {
                res.put(terp[0], terp[1]);
            }
        }
        return res;
    }
    
    // deprecated
    /*
    // just use what is in tree_of_life/about
    // actually, probably don't need this. just construct map earlier
    public static MappingRepresentation getsourceToMetaMap (JadeNode inNode) {
        
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
    */
    
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
