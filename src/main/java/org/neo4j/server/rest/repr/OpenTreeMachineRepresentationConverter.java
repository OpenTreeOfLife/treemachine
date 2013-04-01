package org.neo4j.server.rest.repr;

import java.util.Iterator;
import java.util.Map;

import org.neo4j.helpers.collection.FirstItemIterable;
import org.neo4j.helpers.collection.IterableWrapper;
import org.neo4j.helpers.collection.IteratorWrapper;

import org.neo4j.server.rest.repr.ListRepresentation;
import org.neo4j.server.rest.repr.MappingRepresentation;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.RepresentationType;
import org.neo4j.server.rest.repr.ValueRepresentation;

public class OpenTreeMachineRepresentationConverter {

    /**
     * Return a serialization of the passed in data, which is created by first converting `data` to a Representation object (which can then be serialized).
     * An attempts is made to determine the best approach to serializing `data` by examining the type of `data` and then calling a specialized converter method
     * intended to deal with that type of data. This works for primitives, simple container types that implement Iterable, and objects with explicit conversion
     * methods defined, but will fail on complex datatypes (e.g. classes with instance variables) that do not have explicitly defined conversion methods. New
     * conversion methods can be defined easily; see conversion methods for TNRSResults and ContextResult for examples.
     * 
     * @note taken from OpentreeRepresentationConverter.java in the same package in taxomachine.
     *
     * @param data
     * @return serialized
     */
    public static Representation convert(final Object data)
    {

        // determine the approach to conversion by the type of the data to be converted
        // start with specific object classes that might be observed, and move to more general
        // types of containers if the data doesn't match a specific type
        
        if (data instanceof Iterable) {
            return getListRepresentation( (Iterable) data);
        } else if (data instanceof Iterator) {
            Iterator iterator = (Iterator) data;
            return getIteratorRepresentation(iterator);
        
        } else if (data instanceof Map) {
            return getMapRepresentation( (Map) data );

        // deprecated code, probably won't ever be used here...
        // } else if ( data instanceof Table ) { return new GremlinTableRepresentation( (Table) data );
            
        } else {
            return getSingleRepresentation(data);

        }

    }
    
    ///////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  public conversion methods for specific data types below here
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Return a serialization of a general map type
     * @param data
     * @return
     */
    public static MappingRepresentation getMapRepresentation(Map data) {
        return GeneralizedMappingRepresentation.getMapRepresentation(data);
    }

    /**
     * Return a serialization of a general Iterable type
     * @param data
     * @return
     */
    public static ListRepresentation getListRepresentation(Iterable data)
    {
        final FirstItemIterable<Representation> results = convertValuesToRepresentations(data);
        return new ListRepresentation(getType(results), results);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    //
    //  internal conversion methods below here
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////
    
    static Representation getIteratorRepresentation(Iterator data)
    {
        final FirstItemIterable<Representation> results = new FirstItemIterable<Representation>(
                new IteratorWrapper<Representation, Object>(data) {
                    @Override
                    protected Representation underlyingObjectToObject(Object value) {
                        if (value instanceof Iterable)
                        {
                            FirstItemIterable<Representation> nested = convertValuesToRepresentations((Iterable) value);
                            return new ListRepresentation(getType(nested), nested);
                        } else {
                            return getSingleRepresentation(value);
                        }
                    }
                }
                );
        return new ListRepresentation(getType(results), results);
    }

    static FirstItemIterable<Representation> convertValuesToRepresentations(Iterable data)
    {
        /*
         * if ( data instanceof Table ) { return new FirstItemIterable<Representation>(Collections.<Representation>singleton(new GremlinTableRepresentation(
         * (Table) data ))); }
         */
        return new FirstItemIterable<Representation>(
                new IterableWrapper<Representation, Object>(data) {

                    @Override
                    protected Representation underlyingObjectToObject(Object value) {

                        if (value instanceof Iterable) {
                            final FirstItemIterable<Representation> nested = convertValuesToRepresentations((Iterable) value);
                            return new ListRepresentation(getType(nested), nested);
                        
                        } else {
                            return getSingleRepresentation(value);
                        }
                    }
                });
    }

    /**
     * Infer the type of the objects contained by the Iterable `representationIter`, or if `representationIter` has no elements in its iterator,
     * then return the type of `representationIter` itself. Used by other converter methods to sniff the datatypes of elements in containers so that the appropriate
     * converter methods can be called.
     * @param representationIter
     * @return type of elements in 
     */
    static RepresentationType getType(FirstItemIterable<Representation> representationIter)
    {
        Representation representation = representationIter.getFirst();
        if (representation == null)
            return RepresentationType.STRING;
        return representation.getRepresentationType();
    }

    
    /**
     * Return a Representation object that represents `data`. Representation objects are required by the RepresentationConverter serialization methods, so all objects to
     * be serialized (including primitives) must be converted to a Representation; this method provides that functionality.
     * @param data
     * @return Representation object for data
     */
    static Representation getSingleRepresentation(Object data)
    {
        if (data == null) {
            return ValueRepresentation.string("null");        

        } else if (data instanceof Double || data instanceof Float) {
            return ValueRepresentation.number(((Number) data).doubleValue());
        
        } else if (data instanceof Long) {
            return ValueRepresentation.number(((Long) data).longValue());

        } else if (data instanceof Integer) {
            return ValueRepresentation.number(((Integer) data).intValue());
        
        } else {
            return ValueRepresentation.string(data.toString());
        }
    }
}