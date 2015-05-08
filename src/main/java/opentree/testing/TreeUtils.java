package opentree.testing;

import java.util.HashSet;
import java.util.LinkedList;

import jade.tree.deprecated.JadeNode;
import jade.tree.deprecated.JadeNode.NodeOrder;

public final class TreeUtils {	
	
	public static JadeNode makeRandomTree(Iterable<String> leafNames, int seed) {
		
		LinkedList<JadeNode> nodesToBeAdded = new LinkedList<JadeNode>();
		
		for (String name : leafNames) {
			JadeNode newLeaf = new JadeNode();
			newLeaf.setName(name);
			nodesToBeAdded.add(newLeaf);
		}
		
		JadeNode root = null;
		while (true) {
			int i1 = (int) Math.floor(Math.random() * nodesToBeAdded.size());
			int i2 = i1;
			while (i2 == i1) {
				i2 = (int) Math.floor(Math.random() * nodesToBeAdded.size());
			}

			JadeNode child1 = nodesToBeAdded.get(i1);
			JadeNode child2 = nodesToBeAdded.get(i2);

			JadeNode newNode = new JadeNode();
			newNode.addChild(child1);
			newNode.addChild(child2);
			
			nodesToBeAdded.remove(child1);
			nodesToBeAdded.remove(child2);
			
			if (nodesToBeAdded.size() == 0) {
				root = newNode;
				break;
			} else {
				nodesToBeAdded.add(newNode);
			}
		}
		
		return root;
	}
	
	public static Iterable<JadeNode> extractBipartitions(JadeNode inTreeRoot) {

		LinkedList<JadeNode> bipartTrees = new LinkedList<JadeNode>();
		
		HashSet<HashSet<JadeNode>> rootChildNodeSets = new HashSet<HashSet<JadeNode>>();
		
		for (JadeNode curNode : inTreeRoot.getDescendants(NodeOrder.POSTORDER)) {
			if (curNode.isInternal() && !curNode.isTheRoot()) {

//				System.out.println(curNode.toString());
				
				JadeNode ingroup = new JadeNode();
				JadeNode outgroup = new JadeNode();

				// make a hashset of the ingroup nodes
				HashSet<JadeNode> inTips = new HashSet<JadeNode>();
				for (JadeNode inTip : curNode.getTips()) {

//					System.out.println("Adding " + inTip.getName() + " to ingroup set");
					ingroup.addChild(inTip);
					inTips.add(inTip);
				}
					
				// sort the tree's tips into ingroup/outgroup
				HashSet<JadeNode> outTips = new HashSet<JadeNode>();
				for (JadeNode tip : inTreeRoot.getTips()) {
					if (!inTips.contains(tip)) {
//						System.out.println("Adding " + tip.getName() + " to outgroup set");
						outgroup.addChild(tip);
						outTips.add(tip);
					}
				}

				if (curNode.getParent().equals(inTreeRoot)) {
					if (rootChildNodeSets.contains(inTips)) {
						continue;
					} else {
						rootChildNodeSets.add(inTips);
						rootChildNodeSets.add(outTips);
					}
				}
				
				JadeNode root = new JadeNode();
				root.addChild(ingroup);
				root.addChild(outgroup);

//				System.out.println("tree: " + root.getNewick(false));

				bipartTrees.add(root);
			}
		}
		
//		System.out.println("created " + bipartTrees.size() + " bipartition trees");
		return bipartTrees;
	}
	
}
