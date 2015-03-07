package org.opentree.tag.treeimport;

import jade.tree.Tree;
import jade.tree.TreeNode;
import jade.tree.JadeNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import opentree.constants.NodeProperty;
import opentree.constants.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.Traversal;
import org.opentree.bitarray.ImmutableCompactLongSet;
import org.opentree.bitarray.LongSet;
import org.opentree.bitarray.MutableCompactLongSet;
import org.opentree.graphdb.GraphDatabaseAgent;

public class SubsetTreesUtility {
	GraphDatabaseAgent gdb;
	Map<Object, Collection<Object>> explodedTipsHash;
	
	public SubsetTreesUtility(GraphDatabaseAgent gdb){
		this.gdb = gdb;
	}
	
	public void subsetMultiple(List<Tree> jt, String [] taxId, Map<Tree, String> sourceForTrees){
		System.out.println("started subsetting process");
		HashSet<Tree> alreadyContained = new HashSet<Tree> ();
		HashMap<Tree,HashMap<String,HashSet<String>>> prunedOriginalLabels = new HashMap<Tree,HashMap<String,HashSet<String>>>();
		HashMap<String,HashSet<Tree>> containedTrees = new HashMap<String,HashSet<Tree>>();
		HashMap<String,HashSet<Tree>> prunedTrees = new HashMap<String,HashSet<Tree>>();
		HashMap<String,HashMap<Tree,TreeNode>> prunedNodes = new HashMap<String,HashMap<Tree,TreeNode>>();
		HashSet<TreeNode> alreadyPruned = new HashSet<TreeNode>();
		explodedTipsHash = TipExploder.explodeTipsReturnHash(jt, gdb); 
		System.out.println("getting subset mrca");
		Index<Node> ottIdIndex = gdb.getNodeIndex("graphTaxUIDNodes", "type", "exact", "to_lower_case", "true");
		//determine the subsets and pruned
		for(String tid: taxId){
			HashSet<Tree> contained = new HashSet<Tree>();
			HashSet<Tree> toPruneTree = new HashSet<Tree>();
			HashMap<Tree,TreeNode> toPruneNode = new HashMap<Tree,TreeNode>();
			Node hit = null;
			MutableCompactLongSet subset_mrcas = new MutableCompactLongSet();//((long[]) hit.getProperty(NodeProperty.MRCA.propertyName));
			try {
				hit = ottIdIndex.get(NodeProperty.TAX_UID.propertyName, tid).getSingle();
				long [] tl = (long[]) hit.getProperty(NodeProperty.MRCA.propertyName);
				for(int i=0;i<tl.length;i++){
					Long l = Long.valueOf((String)gdb.getNodeById(tl[i]).getProperty(NodeProperty.TAX_UID.propertyName));
					subset_mrcas.add(l);
				}
			} catch (NoSuchElementException ex) {
				System.out.println("WARNING: more than one match was found for id " + tid + ". this tip will not be exploded.");
			}
			for(Tree tr : jt){
				if(alreadyContained.contains(tr))
					continue;
				TreeNode pruned = null;
				LongSet rootcls = getFullMRCAForNode(tr.getRoot());
				if(subset_mrcas.containsAll(rootcls)== false && subset_mrcas.containsAny(rootcls) ){
					boolean external = false;
					TreeNode nonmono = null;

					//checks internal nodes
					for(TreeNode in: tr.internalNodes(jade.tree.NodeOrder.POSTORDER)){
						if(in == tr.getRoot())
							continue;
						LongSet ts = getFullMRCAForNode(in);
						MutableCompactLongSet other = new MutableCompactLongSet(rootcls);
						other.removeAll(ts);
						if(subset_mrcas.containsAll(ts) == true && subset_mrcas.containsAny(other) == false){
							pruned = in;
							//it will just be replaced as it goes down
						}if(subset_mrcas.containsAll(ts) == false && subset_mrcas.containsAny(other) == false){
							if(nonmono == null){
								//if nonmono, set the first one, this is the mrca and will be used if not 
								//    external and no internal node
								nonmono = in;
							}
						}
					}
					//if null, it could be external node (not a subset) or if non monophyletic, we want the mrca (including the nonmonophyly
					if(pruned == null){
						for(TreeNode ex: tr.externalNodes()){
							LongSet ts = getFullMRCAForNode(ex);
							MutableCompactLongSet other = new MutableCompactLongSet(rootcls);
							other.removeAll(ts);
							if(subset_mrcas.containsAll(ts) == true && subset_mrcas.containsAny(other) == false){
								external = true;
								break;
								//it will just be replaced as it goes down
							}
						}
					}
					
					//if we prune, then we need to go ahead and make the pruned tree for the next analysis
					if(external == false){
						if(pruned == null){
							pruned = nonmono;
							System.out.println("NONMONO:"+pruned);
							System.exit(0);
						}
						if(alreadyPruned.contains(pruned)==false){
							toPruneTree.add(tr);
							toPruneNode.put(tr,pruned);
							if(prunedOriginalLabels.containsKey(tr) == false)
								prunedOriginalLabels.put(tr, new HashMap<String,HashSet<String>>());
							HashSet<String> originalLabels = new HashSet<String>();
							for (TreeNode tnd: pruned.getDescendantLeaves()){
								originalLabels.add((String)tnd.getLabel());
							}
							prunedOriginalLabels.get(tr).put(tid, originalLabels);
							alreadyPruned.add(pruned);
						}
					}else{
						//System.out.println("not included within:"+tr);
					}
				}else if(subset_mrcas.containsAny(rootcls) ==false){
					//System.out.println("not included within:"+tr);
				}else if(subset_mrcas.containsAll(rootcls)== true){
					//System.out.println("contained within:"+tr);
					contained.add(tr);
				}
			}
			//
			containedTrees.put(tid, new HashSet<Tree>());
			for (Tree tr: contained){
				alreadyContained.add(tr);
				containedTrees.get(tid).add(tr);
			}
			prunedTrees.put(tid, new HashSet<Tree>());
			for(Tree tr: toPruneTree){
				prunedTrees.get(tid).add(tr);
			}
			prunedNodes.put(tid, new HashMap<Tree,TreeNode>());
			for(Tree tr: toPruneNode.keySet()){
				prunedNodes.get(tid).put(tr,toPruneNode.get(tr));
			}
		}
		for(String tid: taxId){
			System.out.println("set:"+tid);
			for (Tree tr: containedTrees.get(tid)){
				if(sourceForTrees != null)
					System.out.println("\t"+sourceForTrees.get(tr)+" "+tr+";");
				else
					System.out.println("\t"+tr+";");
			}for (Tree tr: prunedTrees.get(tid)){
				TreeNode pruned = prunedNodes.get(tid).get(tr);
				
				if(prunedOriginalLabels.containsKey(tr) == false){
					if(sourceForTrees != null)
						System.out.println("\tpruned:"+sourceForTrees.get(tr)+" "+pruned+"subset="+tid+";");
					else
						System.out.println("\tpruned:"+pruned+"subset="+tid+";");
				}else{
					HashSet<String> subsets_to_include = new HashSet<String>();
					for(TreeNode tnn: pruned.getDescendantLeaves()){
						if (tnn.getLabel().toString().contains("__subset=")){
							subsets_to_include.add(tnn.getLabel().toString().split("__subset=")[1]);
						}
					}
					if(sourceForTrees != null)
						System.out.print("\tpruned:"+sourceForTrees.get(tr)+" "+pruned+"subset="+tid+";");
					else
						System.out.print("\tpruned:"+pruned+"subset="+tid+";");
					for(String tid2: subsets_to_include){
						if(prunedOriginalLabels.get(tr).containsKey(tid2))
							System.out.print(" subset_"+tid2+"="+prunedOriginalLabels.get(tr).get(tid2));
					}
					System.out.print("\n");
				}
				TreeNode par = pruned.getParent();
				par.removeChild(pruned);
				TreeNode nn = new JadeNode();
				((JadeNode)nn).setName(tid+"__subset="+tid);
				par.addChild(nn);
				//System.out.println("subsetting:"+tr);
			}
		}
		System.out.println("final set:");
		for(Tree tr: jt){
			if(alreadyContained.contains(tr)==false){
				if(prunedOriginalLabels.containsKey(tr)){
					HashSet<String> subsets_to_include = new HashSet<String>();
					for(TreeNode tnn: tr.getRoot().getDescendantLeaves()){
						if (tnn.getLabel().toString().contains("__subset=")){
							subsets_to_include.add(tnn.getLabel().toString().split("__subset=")[1]);
						}
					}
					if(sourceForTrees != null)
						System.out.print("\t"+sourceForTrees.get(tr)+" "+tr+";");
					else
						System.out.print("\t"+tr+";");
					for(String tid: subsets_to_include){
						if(prunedOriginalLabels.get(tr).containsKey(tid))
							System.out.print(" subset_"+tid+"="+prunedOriginalLabels.get(tr).get(tid));
					}
					System.out.print("\n");
				}else{
					if(sourceForTrees != null)
						System.out.println("\t"+sourceForTrees.get(tr)+" "+tr+";");
					else
						System.out.println("\t"+tr+";");
				}
			}
		}
	}
	
	/**
	 * this will output the trees with subsetting
	 * 
	 * trees are either contained within, not contained at all, partial
	 * 
	 * the first two are trivial. the last is trivial when monophyletic. 
	 * when it isn't monophyletic, this outputs the mrca (including the nonmonophyly)
	 * 
	 * @param jt
	 * @param taxId
	 * @param gdb
	 */
	public void subsetSingle(List<Tree> jt, String taxId){
		System.out.println("started subsetting process");
		explodedTipsHash = TipExploder.explodeTipsReturnHash(jt, gdb); 
		System.out.println("getting subset mrca");
		Index<Node> ottIdIndex = gdb.getNodeIndex("graphTaxUIDNodes", "type", "exact", "to_lower_case", "true");
		Node hit = null;
		MutableCompactLongSet subset_mrcas = new MutableCompactLongSet();//((long[]) hit.getProperty(NodeProperty.MRCA.propertyName));
		try {
			hit = ottIdIndex.get(NodeProperty.TAX_UID.propertyName, taxId).getSingle();
			long [] tl = (long[]) hit.getProperty(NodeProperty.MRCA.propertyName);
			for(int i=0;i<tl.length;i++){
				Long l = Long.valueOf((String)gdb.getNodeById(tl[i]).getProperty(NodeProperty.TAX_UID.propertyName));
				subset_mrcas.add(l);
			}
		} catch (NoSuchElementException ex) {
			System.out.println("WARNING: more than one match was found for id " + taxId + ". this tip will not be exploded.");
		}		
		for(Tree tr : jt){
			TreeNode pruned = null;
			LongSet rootcls = getFullMRCAForNode(tr.getRoot());
			if(subset_mrcas.containsAll(rootcls)== false && subset_mrcas.containsAny(rootcls) ){
				boolean external = false;
				TreeNode nonmono = null;

				//checks internal nodes
				for(TreeNode in: tr.internalNodes(jade.tree.NodeOrder.POSTORDER)){
					if(in == tr.getRoot())
						continue;
					LongSet ts = getFullMRCAForNode(in);
					MutableCompactLongSet other = new MutableCompactLongSet(rootcls);
					other.removeAll(ts);
					if(subset_mrcas.containsAll(ts) == true && subset_mrcas.containsAny(other) == false){
						pruned = in;
						//it will just be replaced as it goes down
					}if(subset_mrcas.containsAll(ts) == false && subset_mrcas.containsAny(other) == false){
						if(nonmono == null){
							//if nonmono, set the first one, this is the mrca and will be used if not 
							//    external and no internal node
							nonmono = in;
						}
					}
				}
				//if null, it could be external node (not a subset) or if non monophyletic, we want the mrca (including the nonmonophyly
				if(pruned == null){
					for(TreeNode ex: tr.externalNodes()){
						LongSet ts = getFullMRCAForNode(ex);
						MutableCompactLongSet other = new MutableCompactLongSet(rootcls);
						other.removeAll(ts);
						if(subset_mrcas.containsAll(ts) == true && subset_mrcas.containsAny(other) == false){
							external = true;
							break;
							//it will just be replaced as it goes down
						}
					}
				}
				
				//if we prune, then we need to go ahead and make the pruned tree for the next analysis
				if(external == false){
					if(pruned == null)
						pruned = nonmono;
					System.out.println("pruned:"+pruned+"subset=1");
					TreeNode par = pruned.getParent();
					par.removeChild(pruned);
					TreeNode nn = new JadeNode();
					((JadeNode)nn).setName(taxId+"__subset=1");
					par.addChild(nn);
					System.out.println("subsetting:"+tr);
				}else{
					System.out.println("not included within:"+tr);
				}
			}else if(subset_mrcas.containsAny(rootcls) ==false){
				System.out.println("not included within:"+tr);
			}else if(subset_mrcas.containsAll(rootcls)== true){
				System.out.println("contained within:"+tr);
				pruned = tr.getRoot();
			}
		}
	}
	
	private LongSet getFullMRCAForNode(TreeNode nd){
		MutableCompactLongSet ret = new MutableCompactLongSet();
		for(TreeNode tip: nd.getDescendantLeaves()){
			for(Object ob:(HashSet<Object>)explodedTipsHash.get(tip)){
				ret.add(Long.valueOf((String)ob));
			}
		}
		return new ImmutableCompactLongSet(ret);
	}
}
