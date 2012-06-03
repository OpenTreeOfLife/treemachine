package jade.tree;

public class TreeUtils {
	/*
	 * setting the height of nodes
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
