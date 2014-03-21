
// In neo4j-server.properties, put the following:
// org.neo4j.server.rest.security_rules=opentree.plugins.TreemachineSecurityRule

package opentree.plugins;

import java.util.regex.Pattern;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.servlet.http.HttpServletRequest;
import org.neo4j.server.rest.security.SecurityRule;
import org.neo4j.server.rest.security.SecurityFilter;

public class TreemachineSecurityRule implements SecurityRule {

    public static final String REALM = "WallyWorld"; // as per RFC2617 :-)
 
    // List from webapp/private/config.example in opentree repository
    static Pattern ok = Pattern.compile("/db/data/ext/(" +
                                        "GoLS/graphdb/getDraftTreeID|" +
                                        "GoLS/graphdb/getSyntheticTree|" +
                                        "GoLS/graphdb/getSourceTree|" +
                                        "GoLS/graphdb/getDraftTreeForottId|" +
                                        "GoLS/graphdb/getDraftTreeForNodeID|" +
                                        "GoLS/graphdb/getNodeIDForottId|" +
                                        "GoLS/graphdb/getSourceTreeIDs|" +
                                        "GoLS/graphdb/getSynthesisSourceList|" +
                                        "GetJsons/node)"
                                        );

    @Override
    public boolean isAuthorized(HttpServletRequest request)
    {
        String uri = request.getRequestURI();
        if (ok.matcher(uri).matches()) {
            System.out.format("%s passed because pattern match\n", uri);
            return true;
        } else {
            System.out.format("%s failed pattern match\n", uri);
        }
        if (request.getRemoteAddr().equals("127.0.0.1")) {
            System.out.format("%s passed because localhost\n", uri);
            return true;
        }
        try {
            if (request.getRemoteAddr().equals(InetAddress.getLocalHost().getHostAddress()))
                return true;
        } catch (UnknownHostException e) {
            ;
        }
        return false;
    }
 
    @Override
    public String forUriPath()
    {
        return "/*";
    }
 
    @Override
    public String wwwAuthenticateHeader()
    {
        return SecurityFilter.basicAuthenticationResponse(REALM);
    }
}