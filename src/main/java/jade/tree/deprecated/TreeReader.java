/**
 * 
 */
package jade.tree.deprecated;

import jade.tree.deprecated.JadeNode;
import jade.tree.deprecated.JadeTree;

/**
 * @author smitty
 * 
 */
public class TreeReader {
	/*
	 * constructor
	 */
	public TreeReader() {
	}

	public JadeTree readTree(String treeString) {
		JadeTree tree = new JadeTree();
		String pb = treeString;

		if (pb.charAt(pb.length() - 1) != ';') {
			System.out.println("Tree is invalid: missing concluding semicolon. Exiting.");
			System.exit(1);
		}

		int x = 0;
		char nextChar = pb.charAt(x);
		boolean start = true;
		boolean keepGoing = true;
		JadeNode currNode = new JadeNode();
		while (keepGoing == true) {
			if (nextChar == '(') {
				if (start == true) {
					JadeNode root = new JadeNode();
					tree.setRoot(root);
					currNode = root;
					start = false;
				} else {
					JadeNode newNode = new JadeNode(currNode);
					currNode.addChild(newNode);
					currNode = newNode;
				}
			} else if (nextChar == ',') {
				currNode = currNode.getParent();
			} else if (nextChar == ')') {
				currNode = currNode.getParent();
				x++;
				nextChar = pb.charAt(x);
				String nam = "";
				boolean goingName = true;
				if (nextChar == ',' || nextChar == ')' || nextChar == ':' || nextChar == ';' || nextChar == '[') {
					goingName = false;
				}
				while (goingName == true) {
					nam = nam + nextChar;
					x++;
					nextChar = pb.charAt(x);
					if (nextChar == ',' || nextChar == ')' || nextChar == ':' || nextChar == ';' || nextChar == '[') {
						goingName = false;
						break;
					}
				}// work on edge
				currNode.setName(nam);
				// currNode.getEdge(currNode.getParent()).setLength(Double.parseDouble(edgeL));
				// System.out.println(nam);
				// pb.unread(nextChar);
				x--;
				pb.charAt(x);
			} else if (nextChar == ';') {
				keepGoing = false;
			} else if (nextChar == ':') {
				x++;
				nextChar = pb.charAt(x);
				String edgeL = "";
				boolean goingName = true;
				while (goingName == true) {
					edgeL = edgeL + nextChar;
					x++;
					nextChar = pb.charAt(x);
					if (nextChar == ',' || nextChar == ')' || nextChar == ':' || nextChar == ';' || nextChar == '[') {
						goingName = false;
						break;
					}
				}// work on edge
				currNode.setBL(Double.parseDouble(edgeL));
				// currNode.getEdge(currNode.getParent()).setLength(Double.parseDouble(edgeL));
				// System.out.println(Double.parseDouble(edgeL));
				// pb.unread(nextChar);
				x--;
				pb.charAt(x);
			} else if (nextChar == '[') { // note
				x++;
				nextChar = pb.charAt(x);
				String note = "";
				boolean goingNote = true;
				while (goingNote == true) {
					note = note + nextChar;
					x++;
					nextChar = pb.charAt(x);
					if (nextChar == ']') {
						goingNote = false;
						break;
					}
				}// work on note
					// currNode.setBL(Double.parseDouble(edgeL));
					// x--;
				pb.charAt(x);
			} else if (nextChar == ' ') {

			} else { // external named node
				JadeNode newNode = new JadeNode(currNode);
				currNode.addChild(newNode);
				currNode = newNode;
				String nodeName = "";
				boolean goingName = true;
				while (goingName == true) {
					nodeName = nodeName + nextChar;
					x++;
					nextChar = pb.charAt(x);
					if (nextChar == ',' || nextChar == ')' || nextChar == ':' || nextChar == '[') {
						goingName = false;
						break;
					}
				}
				newNode.setName(nodeName);
				// System.out.println(nodeName);
				// pb.unread(nextChar);
				x--;
				pb.charAt(x);
			}
			if (x < pb.length() - 1) {// added
				x++;
			}
			//
			nextChar = pb.charAt(x);
			// System.out.println(nextChar);
		}
		tree.processRoot();
		return tree;
	}
}
