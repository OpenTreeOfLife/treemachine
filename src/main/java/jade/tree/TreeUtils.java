package jade.tree;

public class TreeUtils {
	/**
	 * Sets the DistanceFromTip for each node in the tree to be the max distance
	 *	to a tip that is in the subtree descended from that node.
	 *
	 * @todo: this could be more efficiently done as postorder traversal, rather than a preorder
	 */
	public static void setDistanceToTips(JadeNode root) {
		setDistanceToTips(root, getGreatestDistance(root));
	}

	private static double getGreatestDistance(JadeNode inNode) {
		double distance = 0.0;
		if (inNode.isInternal()) {
			if (inNode.isTheRoot()) {
				distance = inNode.getBL();
			}
			double posmax = 0.0;
			for (int i = 0; i < inNode.getChildCount(); i++) {
				double posmax2 = getGreatestDistance(inNode.getChild(i));
				if (posmax2 > posmax)
					posmax = posmax2;
			}
			distance += posmax;
			return distance;
		} else
			return inNode.getBL();
	}

	/**
	 * Sets the DistanceFromTip for each node in the tree to be the max distance
	 *	to a tip that is in the subtree descended from that node.
	 *
	 * @todo: this could be more efficiently done as postorder traversal, rather than a preorder
	 */
	private static void setDistanceToTips(JadeNode inNode, double newHeight) {
		if (inNode.isTheRoot() == false) {
			newHeight -= inNode.getBL();
			inNode.setDistanceToTip(newHeight);
		} else {
			inNode.setDistanceToTip(newHeight);
		}
		for (int i = 0; i < inNode.getChildCount(); i++) {
			setDistanceToTips(inNode.getChild(i), newHeight);
		}
	}
	/**
	 * Sets the DistanceFromTip for each tip in the tree to be the distance
	 *	from that tip to the root of the tree
	 */
	public static void setDistanceFromTip(JadeTree tree) {
		for (int i = 0; i < tree.getExternalNodeCount(); i++) {
			JadeNode cur = tree.getExternalNode(i);
			double curh = 0.0;
			while (cur != null) {
				curh += cur.getBL();
				cur = cur.getParent();
			}
			tree.getExternalNode(i).setDistanceFromTip(curh);
		}
	}

	/**
	 * Sets the DistanceFromTip for each node in the tree to be the max distance
	 *	to a tip that is in the subtree descended from that node.
	 *
	 * @todo: this only works if the starting values of the internal node's
	 *		distanceToTip members are <= the correct value (initializing them to zero would be safer).
	 */
	public static void setDistanceToTip(JadeTree tree){
		for (int i = 0; i < tree.getExternalNodeCount(); i++) {
			double curh = 0.0;
			JadeNode cur = tree.getExternalNode(i);
			cur.setDistanceToTip(curh);
			while (cur != null) {
				curh += cur.getBL();
				if(cur.getDistanceToTip()<curh)
					cur.setDistanceToTip(curh);
				cur = cur.getParent();
			}

		}
	}
	
}
