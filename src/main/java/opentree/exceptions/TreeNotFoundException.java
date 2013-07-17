package opentree.exceptions;
import java.lang.Exception;
import java.util.ArrayList;
import java.util.List;
import java.io.PrintStream;


public class TreeNotFoundException extends StoredEntityNotFoundException {

    // single name constructor
    public TreeNotFoundException(String nameOfTree) {
        super(nameOfTree, "tree", "trees");
    }

    // list of names constructor
    public TreeNotFoundException(List<String> namesOfTrees){
        super(namesOfTrees, "tree", "trees");
    }
}
