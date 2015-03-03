package org.opentree.tag.treeimport;

import jade.tree.Tree;
import jade.tree.TreeNode;
import jade.tree.JadeNode;

import java.util.Collection;
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
import org.opentree.bitarray.CompactLongSet;
import org.opentree.graphdb.GraphDatabaseAgent;

public class SubsetTreesUtility {
	GraphDatabaseAgent gdb;
	Map<Object, Collection<Object>> explodedTipsHash;
	
	public SubsetTreesUtility(){}
	
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
	public void subsetSingle(List<Tree> jt, String taxId,GraphDatabaseAgent gdb){
		System.out.println("started subsetting process");
		explodedTipsHash = TipExploder.explodeTipsReturnHash(jt, gdb); 
		System.out.println("getting subset mrca");
		Index<Node> ottIdIndex = gdb.getNodeIndex("graphTaxUIDNodes", "type", "exact", "to_lower_case", "true");
		Node hit = null;
		CompactLongSet subset_mrcas = new CompactLongSet();//((long[]) hit.getProperty(NodeProperty.MRCA.propertyName));
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
			CompactLongSet rootcls = getFullMRCAForNode(tr.getRoot());
			if(subset_mrcas.containsAll(rootcls)== false && subset_mrcas.containsAny(rootcls) ){
				boolean external = false;
				TreeNode nonmono = null;

				//checks internal nodes
				for(TreeNode in: tr.internalNodes(jade.tree.NodeOrder.POSTORDER)){
					if(in == tr.getRoot())
						continue;
					CompactLongSet ts = getFullMRCAForNode(in);
					CompactLongSet other = new CompactLongSet(rootcls);
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
						CompactLongSet ts = getFullMRCAForNode(ex);
						CompactLongSet other = new CompactLongSet(rootcls);
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
	
	private CompactLongSet getFullMRCAForNode(TreeNode nd){
		CompactLongSet ret = new CompactLongSet();
		for(TreeNode tip: nd.getDescendantLeaves()){
			for(Object ob:(HashSet<Object>)explodedTipsHash.get(tip)){
				ret.add(Long.valueOf((String)ob));
			}
		}
		return ret;
	}
}
