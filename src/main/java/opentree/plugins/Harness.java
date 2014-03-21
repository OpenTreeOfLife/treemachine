
package opentree.plugins;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import org.neo4j.server.rest.repr.RepresentationFormatRepository;
import org.neo4j.server.rest.repr.OutputFormat;
import org.neo4j.server.rest.repr.BadInputException;
import org.neo4j.server.plugins.PluginLookupException;
import org.neo4j.server.plugins.BadPluginInvocationException;
//import org.neo4j.cypher.SyntaxException;  -- weird errors having to do with scala
import org.neo4j.server.plugins.PluginInvocationFailureException;


public class Harness {

    RepresentationFormatRepository repository = new RepresentationFormatRepository(null);

    public Response run(Representer thunk) {
        // repository.outputFormat(acceptable, baseUri, requestHeaders)
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

}
