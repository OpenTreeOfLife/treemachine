package org.neo4j.server.rest.repr;

import java.net.URI;
import java.util.List;
import java.util.Map;

public class GeneralizedMappingRepresentation extends MappingRepresentation {

    public GeneralizedMappingRepresentation(RepresentationType type) {
        super(type);
        // TODO Auto-generated constructor stub
    }

    public GeneralizedMappingRepresentation(String type) {
        super(type);
    }

    @Override
    String serialize(RepresentationFormat format, URI baseUri, ExtensionInjector extensions) {
        MappingWriter writer = format.serializeMapping(type);
        Serializer.injectExtensions(writer, this, baseUri, extensions);
        serialize(new MappingSerializer(writer, baseUri, extensions));
        writer.done();
        return format.complete(writer);
    }

    @Override
    void putTo(MappingSerializer serializer, String key) {
        serializer.putMapping(key, this);
    }

    @Override
    protected void serialize(MappingSerializer serializer) {
    }

    public static MappingRepresentation getMapRepresentation(final Map<String, Object> data) {

        return new MappingRepresentation(RepresentationType.MAP.toString()) {
            @Override
            protected void serialize(final MappingSerializer serializer) {

                for (Map.Entry<String, Object> pair : data.entrySet()) {
                    
                    // TODO: extend serializer so it can use things other than strings for map keys
                    // Object key = OpentreeRepresentationConverter.convert(pair.getKey());

                    String key = pair.getKey();
                    Object value = pair.getValue();
                    Object valueConverted = OpenTreeMachineRepresentationConverter.convert(value);

                    if (value instanceof Map) {
                        serializer.putMapping(key, (MappingRepresentation) valueConverted);

                    } else if (value instanceof List) {
                        serializer.putList(key, (ListRepresentation) valueConverted);

                    } else if (value instanceof Boolean) {
                        serializer.putBoolean(key, (Boolean) value);

                    } else if (value instanceof Float || value instanceof Double || value instanceof Long || value instanceof Integer) {
                        serializer.putNumber(key, (Number) value);

                    } else {
                        serializer.putString(key, (String) value);
                    }
                }
            }
        };
    }
}
