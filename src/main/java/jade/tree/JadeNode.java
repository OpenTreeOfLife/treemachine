package jade.tree;

import java.util.*;

public class JadeNode {
	/*
	 * common associations
	 */
	private double BL;//branch lengths
	private double distance_to_tip;
	private double distance_from_tip;
	private int number;
	private String name;
	private JadeNode parent;
	private ArrayList<JadeNode> children;
	private ArrayList<NodeObject> assoc;
	
	/*
	 * constructors
	 */
	public JadeNode(){
		BL = 0.0;
		distance_to_tip = 0.0;
		distance_from_tip = 0.0;
		number = 0;
		name = "";
		parent = null;
		children = new ArrayList<JadeNode> ();
		assoc = new ArrayList<NodeObject>();
	}
	
	public JadeNode(JadeNode parent){
		BL = 0.0;
		distance_to_tip = 0.0;
		distance_from_tip = 0.0;
		number = 0;
		name = "";
		this.parent = parent;
		children = new ArrayList<JadeNode> ();
		assoc = new ArrayList<NodeObject>();
	}
	
	public JadeNode(double BL, int number, String name, JadeNode parent){
		this.BL = BL;
		distance_to_tip = 0.0;
		distance_from_tip = 0.0;
		this.number = number;
		this.name = name;
		this.parent = parent;
		children = new ArrayList<JadeNode> ();
		assoc = new ArrayList<NodeObject>();
	}

	/*
	 * public methods
	 */
	
	public JadeNode [] getChildrenArr(){return (JadeNode[])children.toArray();}
	
	public ArrayList<JadeNode> getChildren(){return children;}
	
	public boolean isExternal(){
		if(children.size()<1)
			return true;
		else
			return false;
	}
	
	public boolean isInternal(){
		if(children.size()<1)
			return false;
		else
			return true;
	}
	
	public boolean isTheRoot(){
		if(parent == null)
			return true;
		else
			return false;
	}
	
	public boolean hasParent(){
		if(parent == null)
			return false;
		else
			return true;
	}
	
	public void setParent(JadeNode p){this.parent = p;}
	
	public int getNumber(){return number;}
	
	public void setNumber(int n){number = n;}
	
	public double getBL(){return BL;}
	
	public void setBL(double b){BL=b;}
	
	public boolean hasChild(JadeNode test){
		return children.contains((JadeNode)test);
	}
	
	public boolean addChild(JadeNode c){
		if(hasChild(c)==false){
			children.add(c);
			c.setParent(this);
			return true;
		}else{
			return false;
		}
	}
	
	public boolean removeChild(JadeNode c){
		if(hasChild(c)==true){
			children.remove(c);
			return true;
		}else{
			return false;
		}
	}

	public JadeNode getChild(int c){return children.get(c);}
	
	public void setName(String s){name = s;}
	
	public String getName(){
		//if(name != ""){
			return name;
		//}else{
		//	return this.getNewick(false);
		//}
	}
	
	public String getNewick(boolean bl){
		String ret = "";
		for(int i=0;i<this.getChildCount();i++){
			if(i==0)
				ret = ret+"(";
			ret = ret+this.getChild(i).getNewick(bl);
			if(bl==true)
				ret = ret +":"+this.getChild(i).getBL();
			if(i == this.getChildCount()-1)
				ret =ret +")";
			else
				ret = ret+",";
		}
		if(name!=null)
			ret = ret + name;
		return ret;
	}
	
	public String getJSON(boolean bl){
		String ret = "{";
		if (name !=null)
			ret += " \"name\": \""+this.getName()+"\"";
		else
			ret += " \"name\": \"\"";
		if(this.getObject("nodeid")!=null)
			ret += "\n, \"nodeid\": \""+this.getObject("nodeid")+"\"";
		for(int i=0;i<this.getChildCount();i++){
			if(i==0)
				ret += "\n, \"children\": [\n";
			ret += this.getChild(i).getJSON(bl);
			if(i == this.getChildCount()-1)
				ret += "]\n";
			else
				ret += ",\n";
		}
		if (bl == true)
			ret += ", \"size\": "+this.getBL();
		if((this.getObject("jsonprint"))!=null)
			ret += this.getObject("jsonprint");
		if(this.getObject("nodedepth")!=null)
			ret += ", \"maxnodedepth\": "+this.getObject("nodedepth");
		if(this.isInternal())
			ret += ", \"nleaves\": "+this.getTips().size();
		else
			ret += ", \"nleaves\": 0";
		ret += "}";
		return ret;
	}
	
	public ArrayList<JadeNode> getTips(){
		ArrayList<JadeNode> children = new ArrayList<JadeNode>();
		Stack<JadeNode> nodes = new Stack<JadeNode>();
		nodes.push(this);
		while(nodes.isEmpty()==false){
			JadeNode jt = nodes.pop();
			for (int i=0;i<jt.getChildCount();i++){
				nodes.push(jt.getChild(i));
			}
			if (jt.isExternal()==true)
				children.add(jt);
		}
		return children;
	}
	
	public int getNodeMaxDepth(){
		int maxnodedepth=0;
		ArrayList<JadeNode> tips = this.getTips();
		for(int i = 0;i<tips.size();i++){
			JadeNode curnode = tips.get(i);
			int tnodedepth = 0;
			while(curnode != this){
				tnodedepth += 1;
				curnode = curnode.getParent();
			}
			if (tnodedepth > maxnodedepth)
				maxnodedepth = tnodedepth;
		}
		return maxnodedepth;
	}
	
	public JadeNode getParent(){return parent;}
	
	public int getChildCount(){return children.size();}
	
	public double getDistanceFromTip(){return distance_from_tip;}
	
	public void setDistanceFromTip(double inh){distance_from_tip = inh;}
	
	public double getDistanceToTip(){return distance_to_tip;}
	
	public void setDistanceToTip(double inh){distance_to_tip = inh;}
	
	public void assocObject(String name, Object obj){
		boolean test = false;
		for(int i=0;i<assoc.size();i++){
			if(assoc.get(i).getName().compareTo(name)==0){
				test = true;
				assoc.get(i).setObject(obj);
			}
		}
		if(test == false){
			NodeObject no = new NodeObject(name, obj);
			assoc.add(no);
		}
	}
	
	public Object getObject(String name){
		Object a = null;
		for(int i=0;i<assoc.size();i++){
			if(assoc.get(i).getName().compareTo(name)==0){
				a = assoc.get(i).getObject();
			}
		}
		return a;
	}
}
