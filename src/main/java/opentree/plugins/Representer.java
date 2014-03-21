
// This interface is used in the coercion of POST methods (managed) to
// GET methods (unmanaged).  

// A Representer is basically a thunk that yields a Representation.
// Typically a Representer will be an instance of an anonymous inner
// class (i.e. lambda expression) that calls one of the POST methods.


package opentree.plugins;

import org.neo4j.server.rest.repr.Representation;

public interface Representer {
    public Representation run() throws Exception;
}
