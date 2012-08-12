package jade.tree;

import java.util.*;

public class JadeTree {
	/*
	 * private
	 */
	private JadeNode root;

	private ArrayList<JadeNode> nodes; // @todo this does not appear to be used (and it is just the union of internalNodes and externalNodes, but it does provide a preorder traversal)

	private ArrayList<JadeNode> internalNodes; // stored in preorder

	private ArrayList<JadeNode> externalNodes;

	private ArrayList<TreeObject> assoc; //@todo as with JavaNode.assoc, this could be changed to a HashMap or TreeMap, if needed

	private int internalNodeCount;  // @todo could be removed by relying on internalNodes.size()

	private int externalNodeCount; // @todo could be removed by relying on externalNodes.size()

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

	/**
	 * Initializes data members based on current root.
	 */
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

	/**
	 * Adds `tn` to the this.externalNodes, but does not tell the node its number
	 *	@todo Is this deprecated? it is not usesd within opentree-treemachine code
	 */
	public void addExternalNode(JadeNode tn) {
		externalNodes.add(tn);
		externalNodeCount = externalNodes.size();
		nodes.add(tn);
	}

	/**
	 * Adds `tn` to the this.internalNodeCount, but does not tell the node its number
	 *	@todo Is this deprecated? it is not usesd within opentree-machine code
	 */
	public void addInternalNode(JadeNode tn) {
		internalNodes.add(tn);
		internalNodeCount = internalNodes.size();
		//to nodes
		nodes.add(tn);
	}

	/**
	 * Adds `tn` to the this.externalNodes and uses setNumber to tell the node its index
	 * @todo it is unclear why we can't just make num a local variable that it eaqual to the initial externalNodes.size();
	 */
	public void addExternalNode(JadeNode tn, int num) {
		externalNodes.add(tn);
		externalNodeCount = externalNodes.size();
		//to nodes
		nodes.add(tn);
		tn.setNumber(num);
	}

	/**
	 * Adds `tn` to the this.internalNodes and uses setNumber to tell the node its index
	 * @todo it is unclear why we can't just make num a local variable that it eaqual to the initial internalNodes.size();
	 */
	public void addInternalNode(JadeNode tn, int num) {
		internalNodes.add(tn);
		internalNodeCount = internalNodes.size();
		nodes.add(tn);
		tn.setNumber(num);
	}

	/**
	 * @return a leaf with the index `num` from the externalNodes or throw IndexOutOfBoundsException.
	 */
	public JadeNode getExternalNode(int num) throws IndexOutOfBoundsException {
		return externalNodes.get(num);
	}

	/**
	 * @return a leaf with name `name` or null
	 * O(N) lookup.
	 */
	public JadeNode getExternalNode(String name) {
		Iterator go = externalNodes.iterator();
		while (go.hasNext()) {
			JadeNode ne = (JadeNode) go.next();
			if (ne.getName().compareTo(name) == 0) {
				return ne;
			}
		}
		return null;
	}

	/**
	 * @return an internal node with the index `num` from the internalNodes or throw IndexOutOfBoundsException.
	 * Calling this with arguments in the order 0 -> internalNodeCount will be a preorder traversal
	 */
	public JadeNode getInternalNode(int num) throws IndexOutOfBoundsException {
		return internalNodes.get(num);
	}

	/**
	 * @return an internal node with name `name` or null
	 * O(N) lookup.
	 */
	public JadeNode getInternalNode(String name) {
		Iterator go = internalNodes.iterator();
		while (go.hasNext()) {
			JadeNode ne = (JadeNode) go.next();
			if (ne.getName().compareTo(name) == 0) {
				return ne;
			}
		}
		return null;
	}
	
	/**
	 * @return size of externalNodes.
	 * NOTE: this disagrees with externalNodesCount during tree construction methods!
	 */
	public int getExternalNodeCount(){return externalNodes.size();}
	/**
	 * @return size of internalNodes.
	 * NOTE: this disagrees with internalNodesCount during tree construction methods!
	 */
	public int getInternalNodeCount(){return internalNodes.size();}
	
	public JadeNode getRoot() {return root;}

	public void setRoot(JadeNode root) {this.root = root;}

	/**
	 * Adds a mapping of key->obj for this tree. Unlike the JavaNode version,
	 *	this method does NOT guard against multiple keys being added. Note
	 *	that only the last object associated with a key will be accessible via
	 *	getObject
	 * @note this in an example of an O(N) routine that would be constant time
	 * 		or O(log(N)) if we change the assoc datatype.
	 * @param key
	 * @param obj Object to be storted
	 */
	public void assocObject(String key, Object obj) {
		TreeObject no = new TreeObject(key, obj);
		assoc.add(no);
	}

	/**
	 * Returns the object associated with the last call of assocObject with this key
	 * @note this in an example of an O(N) routine that would be constant time
	 * 		or O(log(N)) if we change the assoc datatype.
	 * @param key
	 */
	public Object getObject(String key) {
		Object a = null;
		for (int i = 0; i < assoc.size(); i++) {
			if (assoc.get(i).getName().compareTo(key) == 0) {
				a = assoc.get(i);
			}
		}
		return a;
	}

	/**
	 * @todo need to check
	 * @todo we should probably have a boolean flag to indicate whether or not the tree should be treated as rooted
	 */
	public void unRoot(JadeNode inRoot){
		processRoot();
		if (this.getRoot().getChildCount() < 3) {
			tritomyRoot(inRoot);
		}
		processRoot();
	}
	
	/**
	 * Reroots the tree by:
	 *		1. adding a new Node object halfway between `inRoot` and it parent, and
	 *		2. rooting the tree at this new node.
	 * @todo just need to verify that the rerooting treats the branch lengths correctly
	 * @todo should probably be renamed to "addRootBelow(JadeNode inRootChild)"
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

	/**
	 * Converts a root with outdegree 2 to a root of outdegree 3 by deleting a child
	 *	of the root. It guards against removing toberoot.
	 *
	 * just need to verify that the rerooting treats the branch lengths correctly
	 * @param toberoot the node that will be the next root of the tree (NOTE: this
	 *		does not make this node the new root, it is just passed in to avoid
	 *		deleting the node that was intended to be the root of the tree).
	 * Assumes that the current root has outdegree 2 (and that it is not a cherry).
	 * @todo the name of the removed node is lost (other code associated with rerooting the tree moves internal node names (see exchangeInfo)
	 */
	private void tritomyRoot(JadeNode toberoot) {
		JadeNode curroot = this.getRoot();
		assert curroot != null;
		assert curroot.getChildCount() == 2;
		
	 	// @todo code duplication could be lessened by using a pair of ints: toBeDeletedIndex, toGetExtraBLIndex set to (0,1) or (1,0)
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
				assert currootCH.isInternal();
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
				assert currootCH.isInternal();
				double nbl = currootCH.getBL();
				curroot.getChild(1).setBL(curroot.getChild(1).getBL() + nbl);
				curroot.removeChild(currootCH);
				for (int i = 0; i < currootCH.getChildCount(); i++) {
					curroot.addChild(currootCH.getChild(i));
					//currootCH.getChild(i).setParent(curroot);
				}
			} else {
				JadeNode currootCH = curroot.getChild(1);
				assert currootCH.isInternal();
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

	/**
	 * @return the node in the tree that is the most recent common ancestor of all of the leaves specified
	 * @param innodes an array of leaf node names
	 * @todo this could be optimized (by not calling getMRCATraverse repeatedly)
	 */
	public JadeNode getMRCA(String [] innodes){
		if(innodes.length == 1)
    		return this.getExternalNode(innodes[0]);
		ArrayList <String> outgroup = new ArrayList<String>();
		for(int i = 0;i < innodes.length; i++){
			outgroup.add(innodes[i]);
		}
		JadeNode cur1 = this.getExternalNode(outgroup.get(0));
		outgroup.remove(0);
		JadeNode cur2 = null;
		JadeNode tempmrca = null;
		while(outgroup.size() > 0){
			cur2 = this.getExternalNode(outgroup.get(0));
			outgroup.remove(0);
			tempmrca = getMRCATraverse(cur1, cur2);
			cur1 = tempmrca;
		}
		return cur1;
    }
	
	/**
	 * @return the node in the tree that is the most recent common ancestor of all of the leaves specified
	 * @param innodes an array of leaf node names
	 * @todo this could be optimized (by not calling getMRCATraverse repeatedly)
	 */
	public JadeNode getMRCA(ArrayList<String> innodes){
    	if(innodes.size() == 1)
    		return this.getExternalNode(innodes.get(0));
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
		return cur1;
    }
	
	/**
	 * Changes the direction of the arc connecting node to it's parent
	 * @todo uses recursion.
	 */
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

	/**
	 * Swaps name and branch length of `node1` and `node2`
	 * @todo swapping internal node names implicitly treats a node name as
	 *		a name attached to the edge under a node.
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

	/**
	 * Adds node and its descendants to the appropriate list (externalNodes or internalNodes)
	 */
	private void postOrderProcessRoot(JadeNode node) {
		if (node == null)
			return;
		if (node.getChildCount() > 0) {
			for (int i = 0; i < node.getChildCount(); i++) {
				postOrderProcessRoot(node.getChild(i)); //@todo recursion could be a problem for big trees...
			}
		}
		if (node.isExternal()) {
			addExternalNode(node, externalNodeCount);
		} else {
			addInternalNode(node, internalNodeCount);
		}
	}

	/**
	 * prunes `node` from the tree, if `node` is external
	 * @todo doesn't yet take care if node.parent == root or is a polytomy
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
		 */
		double bl = 0;
		JadeNode parent = node.getParent();
		JadeNode other = null;
		for(int i = 0; i < parent.getChildCount(); i++) {
			if (parent.getChild(i) != node){
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
	 * @returns the MRCA of two nodes in a tree. Returns null if the two nodes
	 *		do not have a common ancestor
	 *
	 */
	private static JadeNode getMRCATraverse(JadeNode curn1, JadeNode curn2) {
		JadeNode mrca = null;
		//get path to root for first node
		ArrayList<JadeNode> path1 = new ArrayList<JadeNode>();
		JadeNode parent = curn1;
		while (parent != null) {
			path1.add(parent);
			parent = parent.getParent();
		}
		//find first match between this node and the first one
		parent = curn2;
		while (parent != null) {
			if (path1.contains(parent)) {
				return parent;
			}
			parent = parent.getParent();
		}
		return null;
	}
}
