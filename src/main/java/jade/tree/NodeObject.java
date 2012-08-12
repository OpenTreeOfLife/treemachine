package jade.tree;

/**
 * Like TreeObject this class is just a (String, Object) pair. 
 * @todo Could use the same class for this as TreeObject (or could refactor the assoc elements of JadeNode and JadeTree)
 */
public class NodeObject {
	private String name;
	private Object obj;
	
	public NodeObject(String name, Object obj){
		this.name = name;
		this.obj = obj;
	}
	
	public Object getObject(){return this.obj;}
	public String getName(){return this.name;}
	public void setObject(Object obj){this.obj = obj;}
	public void setName(String name){this.name = name;}
}
