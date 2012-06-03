package jade.tree;

import java.util.*;

public class JadeTree {
	/*
	 * private 
	 */
	private JadeNode root;

	private ArrayList<JadeNode> nodes;

	private ArrayList<JadeNode> internalNodes;

	private ArrayList<JadeNode> externalNodes;

	private ArrayList<TreeObject> assoc;

	private int internalNodeCount;

	private int externalNodeCount;

	/*
	 * constructors
	 */
	public JadeTree() {
		root = null;
		assoc = new ArrayList<TreeObject>();
		processRoot();
	}

	public JadeTree(JadeNode root) {
		this.root = root;
		assoc = new ArrayList<TreeObject>();
		processRoot();
	}

	public void processRoot() {
		nodes = new ArrayList<JadeNode>();
		internalNodes = new ArrayList<JadeNode>();
		externalNodes = new ArrayList<JadeNode>();
		internalNodeCount = 0;
		externalNodeCount = 0;
		if (root == null)
			return;
		postOrderProcessRoot(root);
	}

	public void addExternalNode(JadeNode tn) {
		externalNodes.add(tn);
		externalNodeCount = externalNodes.size();
		nodes.add(tn);
	}

	public void addInternalNode(JadeNode tn) {
		internalNodes.add(tn);
		internalNodeCount = internalNodes.size();
		//to nodes
		nodes.add(tn);
	}

	public void addExternalNode(JadeNode tn, int num) {
		externalNodes.add(tn);
		externalNodeCount = externalNodes.size();
		//to nodes
		nodes.add(tn);
		tn.setNumber(num);
	}

	public void addInternalNode(JadeNode tn, int num) {
		internalNodes.add(tn);
		internalNodeCount = internalNodes.size();
		nodes.add(tn);
		tn.setNumber(num);
	}

	public JadeNode getExternalNode(int num) {
		return externalNodes.get(num);
	}

	public JadeNode getExternalNode(String name) {
		JadeNode retNode = null;
		Iterator go = externalNodes.iterator();
		while (go.hasNext()) {
			JadeNode ne = (JadeNode) go.next();
			if (ne.getName().compareTo(name) == 0) {
				retNode = ne;
				break;
			}
		}
		return retNode;
	}

	public JadeNode getInternalNode(int num) {
		return internalNodes.get(num);
	}

	public JadeNode getInternalNode(String name) {
		JadeNode retNode = null;
		Iterator go = internalNodes.iterator();
		while (go.hasNext()) {
			JadeNode ne = (JadeNode) go.next();
			if (ne.getName().compareTo(name) == 0) {
				retNode = ne;
				break;
			}
		}
		return retNode;
	}
	
	public int getExternalNodeCount(){return externalNodes.size();}
	public int getInternalNodeCount(){return internalNodes.size();}
	
	public JadeNode getRoot() {
		return root;
	}

	public void setRoot(JadeNode root) {
		this.root = root;
	}

	public void assocObject(String name, Object obj) {
		TreeObject no = new TreeObject(name, obj);
		assoc.add(no);
	}

	public Object getObject(String name) {
		Object a = null;
		for (int i = 0; i < assoc.size(); i++) {
			if (assoc.get(i).getName().compareTo(name) == 0) {
				a = assoc.get(i);
			}
		}
		return a;
	}

	//need to check
	public void unRoot(JadeNode inRoot){
		processRoot();
		if (this.getRoot().getChildCount() < 3) {
			tritomyRoot(inRoot);
		}
		processRoot();
	}
	
	/*
	 * just need to verify that the rerooting treats the branch lengths correctly
	 */
	public void reRoot(JadeNode inRoot) {
		processRoot();
		if (this.getRoot().getChildCount() < 3) {
			tritomyRoot(inRoot);
		}
		//System.out.println(inRoot.getBL());
		if (inRoot == this.getRoot()) {
			System.err.println("you asked to root at the current root");
		} else {
			JadeNode tempParent = inRoot.getParent();
			JadeNode newRoot = new JadeNode(tempParent);
			newRoot.addChild(inRoot);
			inRoot.setParent(newRoot);
			tempParent.removeChild(inRoot);
			tempParent.addChild(newRoot);
			newRoot.setParent(tempParent);
			newRoot.setBL(inRoot.getBL() / 2);
			inRoot.setBL(inRoot.getBL() / 2);
			ProcessReRoot(newRoot);
			setRoot(newRoot);
			processRoot();
		}
	}

	public void tritomyRoot(JadeNode toberoot) {
		JadeNode curroot = this.getRoot();
		if (toberoot == null) {
			if (curroot.getChild(0).isInternal()) {
				JadeNode currootCH = curroot.getChild(0);
				double nbl = currootCH.getBL();
				curroot.getChild(1).setBL(curroot.getChild(1).getBL() + nbl);
				curroot.removeChild(currootCH);
				for (int i = 0; i < currootCH.getChildCount(); i++) {
					curroot.addChild(currootCH.getChild(i));
					//currootCH.getChild(i).setParent(curroot);
				}
			} else {
				JadeNode currootCH = curroot.getChild(1);
				double nbl = currootCH.getBL();
				curroot.getChild(0).setBL(curroot.getChild(0).getBL() + nbl);
				curroot.removeChild(currootCH);
				for (int i = 0; i < currootCH.getChildCount(); i++) {
					curroot.addChild(currootCH.getChild(i));
					//currootCH.getChild(i).setParent(curroot);
				}
			}
		} else {
			if (curroot.getChild(1) == toberoot) {
				JadeNode currootCH = curroot.getChild(0);
				double nbl = currootCH.getBL();
				curroot.getChild(1).setBL(curroot.getChild(1).getBL() + nbl);
				curroot.removeChild(currootCH);
				for (int i = 0; i < currootCH.getChildCount(); i++) {
					curroot.addChild(currootCH.getChild(i));
					//currootCH.getChild(i).setParent(curroot);
				}
			} else {
				JadeNode currootCH = curroot.getChild(1);
				double nbl = currootCH.getBL();
				curroot.getChild(0).setBL(curroot.getChild(0).getBL() + nbl);
				curroot.removeChild(currootCH);
				for (int i = 0; i < currootCH.getChildCount(); i++) {
					curroot.addChild(currootCH.getChild(i));
					//currootCH.getChild(i).setParent(curroot);
				}
			}
		}
	}

	public JadeNode getMRCA(String [] innodes){
		JadeNode mrca = null;
    	if(innodes.length == 1)
    		return this.getExternalNode(innodes[0]);
    	else{
    		ArrayList <String> outgroup = new ArrayList<String>();
    		for(int i=0;i<innodes.length;i++){outgroup.add(innodes[i]);}
    		JadeNode cur1 = this.getExternalNode(outgroup.get(0));
    		outgroup.remove(0);
    		JadeNode cur2 = null;
    		JadeNode tempmrca = null;
    		while(outgroup.size()>0){
    			cur2 = this.getExternalNode(outgroup.get(0));
    			outgroup.remove(0);
    			tempmrca = getMRCATraverse(cur1,cur2);
    			cur1 = tempmrca;
    		}
    		mrca = cur1;
    	}
    	return mrca;
    }
	
	public JadeNode getMRCA(ArrayList<String> innodes){
		JadeNode mrca = null;
    	if(innodes.size() == 1)
    		return this.getExternalNode(innodes.get(0));
    	else{
    		ArrayList <String> outgroup = new ArrayList<String>();
    		for(int i=0;i<innodes.size();i++){outgroup.add(innodes.get(i));}
    		JadeNode cur1 = this.getExternalNode(outgroup.get(0));
    		outgroup.remove(0);
    		JadeNode cur2 = null;
    		JadeNode tempmrca = null;
    		while(outgroup.size()>0){
    			cur2 = this.getExternalNode(outgroup.get(0));
    			outgroup.remove(0);
    			tempmrca = getMRCATraverse(cur1,cur2);
    			cur1 = tempmrca;
    		}
    		mrca = cur1;
    	}
    	return mrca;
    }
	
	private void ProcessReRoot(JadeNode node) {
		if (node.isTheRoot() || node.isExternal()) {
			return;
		}
		if (node.getParent() != null) {
			ProcessReRoot(node.getParent());
		}
		// Exchange branch label, length et cetera
		exchangeInfo(node.getParent(), node);
		// Rearrange topology
		JadeNode parent = node.getParent();
		node.addChild(parent);
		parent.removeChild(node);
		parent.setParent(node);
	}

	/*
	 * swap info
	 */
	private void exchangeInfo(JadeNode node1, JadeNode node2) {
		String swaps;
		double swapd;
		swaps = node1.getName();
		node1.setName(node2.getName());
		node2.setName(swaps);

		swapd = node1.getBL();
		node1.setBL(node2.getBL());
		node2.setBL(swapd);
	}

	private void postOrderProcessRoot(JadeNode node) {
		if (node == null)
			return;
		if (node.getChildCount() > 0) {
			for (int i = 0; i < node.getChildCount(); i++) {
				postOrderProcessRoot(node.getChild(i));
			}
		}
		if (node.isExternal()) {
			addExternalNode(node, externalNodeCount);
		} else {
			addInternalNode(node, internalNodeCount);
		}
	}

	/*
	 * prune external node
	 */
	public void pruneExternalNode(JadeNode node){
		if(node.isInternal()){
			return;
		}
		/*
		 * how this works
		 * 
		 * get the parent = parent
		 * get the parent of the parent = mparent
		 * remove parent from mparent
		 * add !node from parent to mparent
		 * 
		 * doesn't yet take care if node.parent == root
		 * or polytomy
		 */
		double bl = 0;
		JadeNode parent = node.getParent();
		JadeNode other = null;
		for(int i=0;i<parent.getChildCount();i++){
			if(parent.getChild(i)!=node){
				other = parent.getChild(i);
			}
		}
		bl = other.getBL()+parent.getBL();
		JadeNode mparent = parent.getParent();
		if(mparent != null){
			mparent.addChild(other);
			other.setBL(bl);
			for(int i=0;i<mparent.getChildCount();i++){
				if(mparent.getChild(i)==parent){
					mparent.removeChild(parent);
					break;
				}
			}
		}
		this.processRoot();
	}
	
	/*
	 * get the MRCA of the array of strings
	 * 
	 */

	private JadeNode getMRCATraverse(JadeNode curn1, JadeNode curn2) {
		JadeNode mrca = null;
		//get path to root for first node
		ArrayList<JadeNode> path1 = new ArrayList<JadeNode>();
		JadeNode parent = curn1;
		path1.add(parent);
		while (parent != null) {
			path1.add(parent);
			if (parent.getParent() != null)
				parent = parent.getParent();
			else
				break;
		}
		//find first match between this node and the first one
		parent = curn2;
		boolean x = true;
		while (x == true) {
			for (int i = 0; i < path1.size(); i++) {
				if (parent == path1.get(i)) {
					mrca = parent;
					x = false;
					break;
				}
			}
			parent = parent.getParent();
		}
		return mrca;
	}
}
