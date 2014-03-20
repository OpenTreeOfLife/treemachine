/*
The web2py app references these methods:

/ext/GoLS/graphdb/getDraftTreeID
/ext/GoLS/graphdb/getSyntheticTree     - this is the workhorse I think
/ext/GoLS/graphdb/getSourceTree          - rarely if ever used?
/ext/GoLS/graphdb/getDraftTreeForottId   - no max depth, no arguson
/ext/GoLS/graphdb/getDraftTreeForNodeID  - similarly
/ext/GoLS/graphdb/getNodeIDForottId      - should be cached
/ext/GoLS/graphdb/getSourceTreeIDs       (not in web2py list, yet)
/ext/GoLS/graphdb/getSynthesisSourceList (not in web2py list, yet)
/ext/GetJsons/node

*/

package opentree.plugins;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.rest.repr.Representation;
//import org.neo4j.server.rest.repr.formats.StreamingJsonFormat;
import org.neo4j.server.rest.repr.ExceptionRepresentation;
import org.neo4j.server.rest.repr.OutputFormat;
import org.neo4j.server.rest.repr.RepresentationFormatRepository;

import org.neo4j.server.plugins.BadPluginInvocationException;
import org.neo4j.server.plugins.PluginLookupException;
//import org.neo4j.cypher.SyntaxException;  -- weird errors having to do with scala
import org.neo4j.server.plugins.PluginInvocationFailureException;
import org.neo4j.server.rest.repr.BadInputException;

import java.nio.charset.Charset;

@Path( "/v0" )

public class Unmanaged {

    // This is what all the tests do.  Hope it works
    RepresentationFormatRepository repository = new RepresentationFormatRepository(null);

    private final GraphDatabaseService database;

    private final GoLS gols;

    public Unmanaged( @Context GraphDatabaseService database )
    {
        this.database = database;
        gols = new GoLS();
    }

    /*
    @GET
    @Produces( MediaType.TEXT_PLAIN )
    @Path( "/{nodeId}" )
    public Response hello( @PathParam( "nodeId" ) long nodeId, @QueryParam( "foo" ) String foo )
    {
        return Response.status( Status.OK ).entity(
                ("Hello World, nodeId=" + nodeId + " foo=" + foo).getBytes( Charset.forName("UTF-8") ) ).build();
    }
    */

    interface Thunk {
        public Representation run() throws Exception;
    }

    public Response harness(Thunk thunk) {
        // repository.outputFormat(acceptable, baseUri, requestHeaders)
        // Copied from RepresentationFormatRepositoryTest.java
        OutputFormat output = repository.outputFormat( java.util.Arrays.asList( MediaType.APPLICATION_JSON_TYPE ), null, null );

        // Compare ExtensionService.invokeGraphDatabaseExtension
        try {
            return output.ok(thunk.run());
        }
        catch ( BadInputException e ) {
            return output.badRequest( e );
        }
        catch ( PluginLookupException e ) {
            return output.notFound( e );
        }
        catch ( BadPluginInvocationException e ) {
            return output.badRequest( e.getCause() );
        }
        /*catch ( SyntaxException e ) {
            return output.badRequest( e.getCause() );
            }*/
        catch ( PluginInvocationFailureException e ) {
            return output.serverError( e.getCause() );
        }
        catch (Exception e) {
            return output.serverError( e );
        }
    }

    @GET
    @Produces( MediaType.APPLICATION_JSON )
    @Path( "/getDraftTreeID" )
    public Response getDraftTreeID( @QueryParam( "startingTaxonOTTId" ) final String startingTaxonOTTId )
    {
        return harness(new Thunk() {
                public Representation run() throws Exception {
                    return gols.getDraftTreeID(database, startingTaxonOTTId);
                }
            });
    }

}
