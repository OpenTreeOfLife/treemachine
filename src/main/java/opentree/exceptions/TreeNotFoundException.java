package opentree.exceptions;
import java.util.List;


public class TreeNotFoundException extends StoredEntityNotFoundException {

	private static final long serialVersionUID = 1L;

	// single name constructor
    public TreeNotFoundException(String nameOfTree) {
        super(nameOfTree, "tree", "trees");
    }

    // list of names constructor
    public TreeNotFoundException(List<String> namesOfTrees){
        super(namesOfTrees, "tree", "trees");
    }
}
