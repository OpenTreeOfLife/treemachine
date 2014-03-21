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

Compare to the list in TreemachineSecurityRule.java.  In general if a
method is defined here it should be listed there, although that's not
strictly necessary.

*/

package opentree.plugins;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.server.rest.repr.Representation;

import java.nio.charset.Charset;

@Path( "/GoLS" )

public class UnmanagedGoLS {

    private final GraphDatabaseService database;
    private final GoLS gols;
    private final Harness harness;

    public UnmanagedGoLS( @Context GraphDatabaseService database )
    {
        this.database = database;
        gols = new GoLS();
        harness = new Harness();
    }

    @GET
    @Produces( MediaType.APPLICATION_JSON )
    @Path( "/getDraftTreeID" )
    public Response getDraftTreeID( @QueryParam( "startingTaxonOTTId" ) final String startingTaxonOTTId )
    {
        return harness.run(new Representer() {
                public Representation run() throws Exception {
                    return gols.getDraftTreeID(database, startingTaxonOTTId);
                }
            });
    }

    @GET
    @Produces( MediaType.APPLICATION_JSON )
    @Path( "/getSyntheticTree" )
    public Response getSyntheticTree( @QueryParam( "treeID" ) final String treeID,
                                      @QueryParam( "format" ) final String format,
                                      @QueryParam( "subtreeNodeID" ) final String subtreeNodeID,
                                      @QueryParam( "maxDepth" ) final Integer maxDepth)
    {
        return harness.run(new Representer() {
                public Representation run() throws Exception {
                    return gols.getSyntheticTree(database, treeID, format, subtreeNodeID, maxDepth);
                }
            });
    }

}
