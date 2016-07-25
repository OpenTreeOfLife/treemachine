package opentree.plugins;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import org.neo4j.server.rest.repr.BadInputException;

public class BadIdsException extends BadInputException {

    List<Long> ottIds;
    List<String> nodeIds;
    HashMap<String, Object> json;

    public BadIdsException(List<Long> ottIds, List<String> nodeIds, HashMap<String, Object> json) {
        super("Unrecognized node or taxon id (you should never see this string)");
        this.ottIds = ottIds;
        this.nodeIds = nodeIds;
        this.json = json;
    }

    public String getMessage() {
        return multipleBadNodeIDsError(ottIds, nodeIds);
    }

    private String multipleBadNodeIDsError (List<Long> ottIdsNotInTree, List<String> nodesIDsNotInTree) {
        String ret = "";
        if (!ottIdsNotInTree.isEmpty()) {
            ret = "The following OTT ids were not found: [";
            for (int i = 0; i < ottIdsNotInTree.size(); i++) {
                ret += ottIdsNotInTree.get(i);
                if (i != ottIdsNotInTree.size() - 1) {
                    ret += ", ";
                }
            }
            ret += "]. ";
        }
        if (!nodesIDsNotInTree.isEmpty()) {
            ret += "The following node ids were not found: [";
            for (int i = 0; i < nodesIDsNotInTree.size(); i++) {
                ret += nodesIDsNotInTree.get(i);
                if (i != nodesIDsNotInTree.size() - 1) {
                    ret += ", ";
                }
            }
            ret += "]. ";
        }
        return ret;
    }

}
