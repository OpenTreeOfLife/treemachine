package opentree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Stack;

import org.opentree.bitarray.ImmutableCompactLongSet;
import org.opentree.bitarray.LongSet;
import org.opentree.bitarray.MutableCompactLongSet;
import org.opentree.graphdb.GraphDatabaseAgent;
import org.opentree.tag.treeimport.ImmutableLongBipartition;
import org.opentree.tag.treeimport.LongBipartition;
import org.opentree.tag.treeimport.TipExploder;

import jade.tree.NodeOrder;
import jade.tree.Tree;
import jade.tree.TreeBipartition;
import jade.tree.TreeNode;

public class SynthCheck {
	Tree synth;
	ArrayList<Tree> testtree;
	GraphDatabaseAgent gdb;
	Map<Object, Collection<Object>> explodedTipsHash;
	HashMap<Object,ImmutableCompactLongSet> explodedCLS = new HashMap<Object,ImmutableCompactLongSet>();
	HashMap<Tree,ImmutableCompactLongSet> rootTaxa = new HashMap<Tree,ImmutableCompactLongSet>();
	HashMap<TreeNode,ImmutableCompactLongSet> ingroups = new HashMap<TreeNode,ImmutableCompactLongSet>();
	HashMap<TreeNode,ImmutableCompactLongSet> outgroups = new HashMap<TreeNode,ImmutableCompactLongSet>();
	
	
	public SynthCheck(Tree synth,ArrayList<Tree> testtrees, GraphDatabaseAgent gdb){
		this.synth = synth;
		this.testtree = testtrees;
		this.gdb = gdb;
	}
	
	public void testSynth(){
		explodedTipsHash = TipExploder.explodeTipsReturnHash(testtree, gdb); 
		for(Object e: explodedTipsHash.keySet()){
			MutableCompactLongSet mcls = new MutableCompactLongSet();
			for(Object o: explodedTipsHash.get(e)){
				mcls.add(Long.valueOf((String)o));
			}
			explodedCLS.put(e,new ImmutableCompactLongSet(mcls));
		}
		//getting the taxa stuff
		for(Tree t: testtree){
			for(TreeNode tn: t.internalNodes(NodeOrder.PREORDER)){
				if(tn == t.getRoot()){
					MutableCompactLongSet mcls = new MutableCompactLongSet();
					for(TreeNode e: tn.getDescendantLeaves()){
						mcls.addAll(explodedCLS.get(e));
					}
					rootTaxa.put(t, new ImmutableCompactLongSet(mcls));
				}else{
					TreeBipartition b = t.getBipartition(tn);
					MutableCompactLongSet mcls = new MutableCompactLongSet();
					for(TreeNode e: b.ingroup()){
						mcls.addAll(explodedCLS.get(e));
					}
					ingroups.put(tn, new ImmutableCompactLongSet(mcls));
					mcls = new MutableCompactLongSet();
					for(TreeNode e: b.outgroup()){
						mcls.addAll(explodedCLS.get(e));
					}
					outgroups.put(tn, new ImmutableCompactLongSet(mcls));
				}
			}
		}
		
		System.err.println("finished processing trees");
		//doing this in a stack
		MutableCompactLongSet fullset = new MutableCompactLongSet();
		for(Object key: explodedTipsHash.keySet()){
			for(Object tv: explodedTipsHash.get(key))
				fullset.add(Long.valueOf((String) tv));
		}
		TreeNode curnode = synth.getRoot();
		HashSet<TreeNode> testNodes = new HashSet<TreeNode>();
		MutableCompactLongSet foundset = new MutableCompactLongSet();
		for(TreeNode tn: synth.externalNodes()){
			if (fullset.contains(Long.valueOf((String)tn.getLabel()))){
				testNodes.add(tn);
				foundset.add(Long.valueOf((String)tn.getLabel()));
			}
		}
		System.err.println(testNodes.size());
		if(fullset.size() != testNodes.size()){
			fullset.removeAll(foundset);
			System.err.println("tip set from source trees not found: ("+(fullset.size()-foundset.size())+") "+fullset);
			gdb.shutdownDb();
			System.exit(0);
		}
		HashSet<TreeNode> done = new HashSet<TreeNode>();
		int count = 0;
		for(TreeNode tn: testNodes){
			boolean going = true;
			TreeNode par = tn;
			while(going){
				count += 1;
				if (count % 100 == 0)
					System.err.println(count);
				par = tn.getParent();
				if(done.contains(par))
					break;
				done.add(par);
				if(((String)par.getLabel()).isEmpty()==false)
					continue;
				TreeBipartition b = synth.getBipartition(par);
				MutableCompactLongSet ingroup = new MutableCompactLongSet();
				for (TreeNode n : b.ingroup())  { ingroup.add(Long.valueOf((String) n.getLabel()));  }
				if(ingroup.containsAll(fullset)){
					break;
				}else{
					
					MutableCompactLongSet outgroup = new MutableCompactLongSet();
					for (TreeNode n : b.outgroup()) { outgroup.add(Long.valueOf((String)n.getLabel())); }
					boolean test= false;
					for(Tree t: testtree){
						if(ingroup.containsAny(rootTaxa.get(t))){
							for(TreeNode tn2: t.internalNodes(NodeOrder.PREORDER)){
								if(tn2 == t.getRoot())
									continue;
								ImmutableCompactLongSet ing = ingroups.get(tn2);
								ImmutableCompactLongSet oug = outgroups.get(tn2);
								if(ingroup.containsAll(ing) && outgroup.containsAll(oug)){
									test = true;
									break;
								}
							}
						}else{
							continue;
						}
					}for(Object o: explodedCLS.keySet()){
						if(explodedCLS.get(o).size() == ingroup.size() && explodedCLS.get(o).containsAll(ingroup)){
							test=true;
							break;
						}
					}
					if(test==false){
						System.err.println("no match:"+par);
						//gdb.shutdownDb();
						//System.exit(0);
					}
				}
			}
		}
		System.err.println("synth tests passed");
		/*
		for(TreeNode tn: curnode.getChildren())
			stack.push(tn);
		int count = 0;
		while(stack.isEmpty()==false){
			curnode = stack.pop();
			MutableCompactLongSet ingroup = new MutableCompactLongSet();
			TreeBipartition b = synth.getBipartition(curnode);
			for (TreeNode n : b.ingroup())  { ingroup.add(Long.valueOf((String) n.getLabel()));  }
			if (fullset.containsAny(ingroup)){
				MutableCompactLongSet outgroup = new MutableCompactLongSet();
				for (TreeNode n : b.outgroup()) { outgroup.add(Long.valueOf((String)n.getLabel())); }
				for(TreeNode tn: curnode.getChildren())
					stack.push(tn);
			}
			if (count % 100 == 0)
				System.out.println(count);
			count += 1;
		}
		*/
	}
	
	private LongBipartition getGraphBipartForTreeNode(TreeNode p, Tree t) {
		MutableCompactLongSet ingroup = new MutableCompactLongSet();
		MutableCompactLongSet outgroup = new MutableCompactLongSet();
		TreeBipartition b = t.getBipartition(p);

			for (TreeNode n : b.ingroup())  {
				//for	(Object s: explodedTipsHash.get(n)) { ingroup.add(nodeIdForLabel.get(s)); }
				for	(Object s: explodedTipsHash.get(n)) { ingroup.add(Long.valueOf(s.toString())); }
			}
			for (TreeNode n : b.outgroup()) {
				//for	(Object s: explodedTipsHash.get(n)) { outgroup.add(nodeIdForLabel.get(s)); }
				for	(Object s: explodedTipsHash.get(n)) { outgroup.add(Long.valueOf(s.toString())); }
			}
		return new ImmutableLongBipartition(ingroup, outgroup); // made immutable
	}
	
}
