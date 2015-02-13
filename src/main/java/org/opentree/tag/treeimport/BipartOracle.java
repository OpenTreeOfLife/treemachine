package org.opentree.tag.treeimport;

import jade.tree.JadeBipartition;
import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.NodeOrder;
import jade.tree.TreeParseException;
import jade.tree.TreePrinter;
import jade.tree.TreeReader;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.opentree.bitarray.TLongBitArraySet;

/*
 * TODO: create interfaces for Tree and TreeNode, and implement them on JadeTree/JadeNode,
 * NexsonTree/NexsonNode. Then, adjust import procedures to be ambivalent to source of the
 * tree.
 */

public class BipartOracle {

	Map<String, Integer> idForName = new HashMap<String, Integer>();
	Map<Integer, String> nameForId = new HashMap<Integer, String>();
	
	// indices in these correspond nodes in input trees
	JadeNode[] treeNode;
	int[][] childrenOf;
	TLongBipartition[] original;
	
	// indices/ints in these correspond to summed bipartitions
	TLongBipartition[] bipart;
	ArrayList<Integer>[] nestedWithin;
	HashSet<ArrayList<Integer>> paths;

	int internalNodeCounter = 0;

	@SuppressWarnings("unchecked")
	public BipartOracle(List<JadeTree> trees) {

		gatherTreeData(trees); // populate 'treeNode', 'childrenOf', and 'original' arrays
		
		// get pairwise sums of all tree biparts
		bipart = new BipartSetSum(original).toArray();

		// find compatible child mappings among the summed biparts:
		nestedWithin = new ArrayList[bipart.length];
		for (int i = 0; i < bipart.length; i++) {
			nestedWithin[i] = new ArrayList<Integer>();
			for (int j = 0; j < bipart.length; j++) {
				if (i != j && bipart[j].isNestedPartitionOf(bipart[i])) {
					nestedWithin[i].add(j);
				}
			}
		}
		
		// map all tree nodes to all summed biparts
		int[][] treeNodesForBipart = new int[bipart.length][];
		for (int i = 0; i < bipart.length; i++) {
			ArrayList<Integer> accepted = new ArrayList<Integer>();
			for (int j = 0; j < treeNode.length; j++) {
				if (bipart[i].isCompatibleWith(original[j])) {
					accepted.add(i);
				}
			}
			// store the compatible bipart ids
			treeNodesForBipart[i] = new int[accepted.size()];
			for (int j = 0; j < accepted.size(); j++) {
				treeNodesForBipart[i][j] = accepted.get(j).intValue();
			}
		}
		
		// now walk the bipart nestings and build the potential paths.
		paths = new HashSet<ArrayList<Integer>>();
		for (int i = 0; i < bipart.length; i++) {
			makePathsRecursive(i, new TLongBipartition(new TLongBitArraySet(), new TLongBitArraySet()), new ArrayList<Integer>());
		}
		
		
		// for each tree node A in tree T, start at each bipart which could be mapped to A,
		// and generate paths to all the bipartitions mapped to children of A in T, updating
		// the outgroups of bipartitions

	}

	private TLongBipartition makePathsRecursive(int parentId, TLongBipartition nested, ArrayList<Integer> path) {

		TLongBipartition parent = bipart[parentId];
		
		// three cases where we stop traversing this path:
		if (hasNoNestedBiparts(parentId)) { return null;

		// these two cases result from updates to the child performed earlier on this path
		} else if (nested.ingroup().containsAny(parent.outgroup())) { return null; // conflict
		} else if (nested.ingroup().containsAll(parent.ingroup())) { return null; // no information

		} else { // update the child and keep going down the path

			nested.ingroup().addAll(parent.ingroup()); // ingroup

			if (nested.ingroup().containsAny(nested.outgroup())) {
				// if the child's updated ingroup overlaps with its outgroup,
				// just replace the child's outgroup with the parent's.
				// not clear why this works. is it a shortcut? does the child's outgroup
				// really not matter? if so, why do we even check?
				nested.outgroup().clear();
			}

			nested.outgroup().addAll(parent.outgroup());
		}
		
		path.add(parentId);
		
		for (int nextParentId : nestedWithin[parentId]) {
			if (path.contains(nextParentId)) { continue; }
			TLongBipartition result = makePathsRecursive(nextParentId, nested, path);
			if (result != null) { // did not encounter a dead end path
				
				// why?
				nested.outgroup().addAll(result.ingroup());
			}
		}
		
		System.out.println("Will create node " + nested + " based on path " + path);
		assert ! nested.ingroup().containsAny(nested.outgroup());

		paths.add(path);
		return nested;
	}
	
	private boolean hasNoNestedBiparts(int parent) {
		return nestedWithin[parent].size() == 0;
	}
	
	private void gatherTreeData(List<JadeTree> trees) {
		int treeNodeCount = 0;
		for (JadeTree t : trees) { 
			treeNodeCount += t.internalNodeCount();
		}
		
		// mint ids for all unique tip names
		int tipCounter = 0;
		for (JadeTree t : trees) {
			for (JadeNode l : t.externalNodes()) {
				String n = l.getName();
				if (! idForName.containsKey(n)) {
					idForName.put(n, tipCounter);
					nameForId.put(tipCounter++, n);
				}
			}
		}
			
		// gather the tree nodes, tree structure, and tip labels.
		// for nexson, we would want ott ids instead of tip labels.
		treeNode = new JadeNode[treeNodeCount];
		childrenOf = new int[treeNodeCount][];
		original = new TLongBipartition[treeNodeCount];
		internalNodeCounter = -1;
		for (JadeTree t : trees) {
			recurRecordTree(t.getRoot(), ++internalNodeCounter, t);
		}
	}

	private void recurRecordTree(JadeNode p, int nodeId, JadeTree tree) {

		treeNode[nodeId] = p;

		// collect the internal children and send the tips for processing
		ArrayList<JadeNode> internalChildren = new ArrayList<JadeNode>();
		for (JadeNode child : p.getChildren()) {
			if (child.isInternal()) { internalChildren.add(child); }
		}
		
		// process the internal children
		childrenOf[nodeId] = new int[internalChildren.size()];
		int k = 0;
		for (JadeNode child : internalChildren) {
			childrenOf[nodeId][k++] = ++internalNodeCounter;
			recurRecordTree(child, internalNodeCounter, tree);
		}
		
		// record the bipartition
		TLongBitArraySet ingroup = new TLongBitArraySet();
		TLongBitArraySet outgroup = new TLongBitArraySet();
		JadeBipartition b = tree.getBipartition(p);
		for (JadeNode n : b.ingroup())  { ingroup.add(idForName.get(n.getName()));  }
		for (JadeNode n : b.outgroup()) { outgroup.add(idForName.get(n.getName())); }
		original[nodeId] = new TLongBipartition(ingroup, outgroup);
	}

	public static void main(String[] args) throws TreeParseException {

		ArrayList<JadeTree> t = new ArrayList<JadeTree>();
		t.add(TreeReader.readTree("((A,B),C);"));
		t.add(TreeReader.readTree("((A,C),D);"));
		t.add(TreeReader.readTree("((A,D),B);"));

		BipartOracle bi = new BipartOracle(t);

		System.out.println("ids: " + bi.nameForId);
		for (TLongBipartition s : bi.bipart) {
			System.out.println(s.toString(bi.nameForId));
		}
	}
}
