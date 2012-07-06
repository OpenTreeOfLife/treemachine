package opentree.plugins;

import org.neo4j.graphalgo.GraphAlgoFactory;
import org.neo4j.graphalgo.PathFinder;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.Expander;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.kernel.Traversal;
import org.neo4j.server.plugins.Description;
import org.neo4j.server.plugins.Parameter;
import org.neo4j.server.plugins.PluginTarget;
import org.neo4j.server.plugins.ServerPlugin;
import org.neo4j.server.plugins.Source;

public class GetNodeFromName extends ServerPlugin{
    @Description( "Find the nodes with the name given." )
    @PluginTarget( Node.class )
    public Iterable<Path> shortestPath(
            @Source Node source,
            @Description( "The node to find the shortest path to." )
                @Parameter( name = "target" ) Node target,
            @Description( "The relationship types to follow when searching for the shortest path(s). " +
             "Order is insignificant, if omitted all types are followed." )
                @Parameter( name = "types", optional = true ) String[] types,
            @Description( "The maximum path length to search for, default value (if omitted) is 4." )
                @Parameter( name = "depth", optional = true ) Integer depth ){
        Expander expander;
        if ( types == null ){
            expander = Traversal.expanderForAllTypes();
        }
        else{
            expander = Traversal.emptyExpander();
            for ( int i = 0; i < types.length; i++ ){
                expander = expander.add( DynamicRelationshipType.withName( types[i] ) );
            }
        }
        PathFinder<Path> shortestPath = GraphAlgoFactory.shortestPath(
                expander, depth == null ? 4 : depth.intValue() );
        return shortestPath.findAllPaths( source, target );
    }
}
