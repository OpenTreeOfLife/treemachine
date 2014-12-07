package opentree.addanalyses;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TLongHashSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.IndexHits;
import org.opentree.exceptions.AmbiguousTaxonException;
import org.opentree.exceptions.TaxonNotFoundException;

import opentree.GraphBase;
import opentree.GraphDatabaseAgent;
import opentree.LicaUtil;
import opentree.PhylografterConnector;
import opentree.constants.RelType;
import jade.MessageLogger;
import jade.tree.JadeNode;
import jade.tree.JadeTree;
import jade.tree.NexsonReader;

public class TreeComparator extends GraphBase{
	private boolean taxtree = false;
	private String nexsonfile = "";
	private String treeid = "";
	private JadeTree treetocompare = null;
	private GraphDatabaseAgent graphdb = null;
	
	private MessageLogger logger;
	
	private ArrayList<JadeNode> inputJadeTreeLeaves; // just the leaves of the input tree
	// TODO making a Set<Long> or sorted ArrayList<Long> for the ids would make the look ups faster. See comment in testIsMRCA
	private TLongArrayList graphNodeIdsForInputLeaves; // the graph node ids for the nodes matched to the input tree leaves
	private HashMap<JadeNode,Long> inputJadeTreeLeafToMatchedGraphNodeIdMap; // maps each jade node to the id of the graph node it's mapped to
	private TLongArrayList graphDescendantNodeIdsForInputLeaves; // all the ids of the graph nodes descended from the leaves of the input tree
	private HashMap<JadeNode,ArrayList<Long>> jadeNodeToDescendantGraphNodeIdsMap; // maps each jade node to the node ids in the mrca property of its matched graph node
	
	
	public TreeComparator(boolean tax, String nexsonfile, String treeid, GraphDatabaseAgent graphdb){
		super(graphdb);
		this.taxtree = tax;
		this.nexsonfile = nexsonfile;
		this.treeid = treeid;
		this.graphdb = graphdb;
	}
	
	public void processNexson(){
		File file = new File(nexsonfile);
		System.err.println("file " + file);
		try {
			BufferedReader  br = new BufferedReader(new FileReader(file));
			List<JadeTree> rawTreeList = null;
			MessageLogger messageLogger = new MessageLogger("treecomparator", " ");
			rawTreeList = NexsonReader.readNexson(br, true, messageLogger);
			int count = 0;
			for (JadeTree j : rawTreeList) {
				if (j == null) {
					messageLogger.indentMessage(1, "Skipping null tree...");
				} else {
					String treeJId = (String)j.getObject("id");
					if (treeid != null) {
						if (treeJId.equals(treeid) == false) {
							System.out.println("skipping tree: " + treeJId);
							continue;
						}
					}
					System.err.println("\ttree " + count + ": " + j.getExternalNodeCount());
					count += 1;
					treetocompare = j;
					break;
				}
			}
			br.close();
			if(treetocompare == null){
				System.err.println("there is a problem with the id");
				return;
			}
			try {
				ArrayList<JadeTree> tt = new ArrayList<JadeTree>();tt.add(treetocompare);
				boolean good = PhylografterConnector.fixNamesFromTreesNOTNRS(tt, graphdb, true, messageLogger);
				System.out.println("done fixing name");
				if (good == false) {
					System.err.println("failed to get the names from server fixNamesFromTrees 3");
					return;
				}
			} catch (IOException ioe) {
				System.err.println("excpeption to get the names from server fixNamesFromTrees 4");
				throw ioe;
			}
			boolean doubname = false;
			String treeJId = (String)treetocompare.getObject("id");
			if (treeid != null) {
				if (treeJId.equals(treeid) == false) {
					System.out.println("skipping tree: " + treeJId);
					return;
				}
			}
			HashSet<Long> ottols = new HashSet<Long>();
			messageLogger.indentMessageStr(1, "Checking for uniqueness of OTT IDs", "tree id", treeJId);
			for (int m = 0; m < treetocompare.getExternalNodeCount(); m++) {
				//System.out.println(j.getExternalNode(m).getName() + " " + j.getExternalNode(m).getObject("ot:ottId"));
				if (treetocompare.getExternalNode(m).getObject("ot:ottId") == null) {//use doubname as also 
					messageLogger.indentMessageStr(2, "null OTT ID for node", "name", treetocompare.getExternalNode(m).getName());
					doubname = true;
					break;
				}
				Long ottID = (Long)treetocompare.getExternalNode(m).getObject("ot:ottId");
				if (ottols.contains(ottID) == true) {
					messageLogger.indentMessageLongStr(2, "duplicate OTT ID for node", "OTT ID", ottID, "name", treetocompare.getExternalNode(m).getName());
					doubname = true;
					break;
				} else {
					ottols.add((Long)treetocompare.getExternalNode(m).getObject("ot:ottId"));
				}
			}
			//check for any duplicate ottol:id
			if (doubname == true) {
				messageLogger.indentMessageStr(1, "null or duplicate names. Skipping tree", "tree id", treeJId);
			} else {
				//gi.setTree(j);
				messageLogger.indentMessageStr(1, "tree ready to compare", "tree id", treeJId);
				//gi.addSetTreeToGraphWIdsSet(sourcename, false, onlyTestTheInput, messageLogger);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			graphdb.shutdownDb();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void compareTree() throws TaxonNotFoundException{
		inputJadeTreeLeaves = treetocompare.getRoot().getTips();
		graphNodeIdsForInputLeaves = new TLongArrayList();  // was ndids
		inputJadeTreeLeafToMatchedGraphNodeIdMap = new HashMap<JadeNode,Long>(); // was hashnodeids
		graphDescendantNodeIdsForInputLeaves = new TLongArrayList(); // was ndidssearch
		jadeNodeToDescendantGraphNodeIdsMap = new HashMap<JadeNode,ArrayList<Long>>(); // hashnodeidssearch
		for (JadeNode curLeaf : inputJadeTreeLeaves) {
			
			Long ottId = (Long) curLeaf.getObject("ot:ottId");

			// use the ottId to match to a graph node, or throw an exception if we can't
			Node matchedGraphNode = null;
			IndexHits<Node> hits = null;
			try {
				hits = graphTaxUIDNodeIndex.get("tax_uid", ottId);
				int numh = hits.size();
				if (numh < 1) {
					throw new TaxonNotFoundException(String.valueOf(ottId));
				} else if (numh > 1) {
					throw new AmbiguousTaxonException("tax_uid=" + ottId);
				} else {
					matchedGraphNode = hits.getSingle();
				}
			} finally {
				hits.close();
			}
			inputJadeTreeLeafToMatchedGraphNodeIdMap.put(curLeaf, matchedGraphNode.getId());
		}
		gatherInfoForLicaSearches();
		//map to deeper node
		remapToDeeperTaxon();
		compareTreeLICAs(treetocompare.getRoot());
	}
	
	private void gatherInfoForLicaSearches() {
		for (Entry<JadeNode, Long> match : inputJadeTreeLeafToMatchedGraphNodeIdMap.entrySet()) {
			
			JadeNode curLeaf = match.getKey();
			Node matchedGraphNode = graphdb.getNodeById(match.getValue());
			
			// add information on node mappings and descendants to instance variables for easy access during import
			// these are used for the lica calculations, etc.
			long [] mrcaArray = (long[])matchedGraphNode.getProperty("mrca");
			ArrayList<Long> descendantIdsForCurMatchedGraphNode = new ArrayList<Long>(); 
			for (int k = 0; k < mrcaArray.length; k++) {
				graphDescendantNodeIdsForInputLeaves.add(mrcaArray[k]);
				descendantIdsForCurMatchedGraphNode.add(mrcaArray[k]);
			}
			jadeNodeToDescendantGraphNodeIdsMap.put(curLeaf, descendantIdsForCurMatchedGraphNode);
			graphNodeIdsForInputLeaves.add(matchedGraphNode.getId());
		}
		graphDescendantNodeIdsForInputLeaves.sort();
		graphNodeIdsForInputLeaves.sort();		
	}
	
	public void compareTreeLICAs(JadeNode curJadeNode){
		// postorder traversal via recursion
		for (int i = 0; i < curJadeNode.getChildCount(); i++) {
			compareTreeLICAs(curJadeNode.getChild(i));
		}

		// testing
		ArrayList<String> namesList = new ArrayList<String>();
		for (JadeNode child : curJadeNode.getDescendantLeaves()) {
			namesList.add(child.getName());
		}
		//System.out.println("working on node: " + Arrays.toString(namesList.toArray()));
		if (curJadeNode.isTheRoot()) {
			System.out.println("this is the ROOT");
		}

		if (curJadeNode.getChildCount() == 0) { // this is a tip, not much to do here
			
			// there is only one lica match: the node to which we mapped this input node
			HashSet<Node> licaMatches = new HashSet<Node>();
			licaMatches.add(graphdb.getNodeById(inputJadeTreeLeafToMatchedGraphNodeIdMap.get(curJadeNode)));
			curJadeNode.assocObject("dbnodes", licaMatches);
			curJadeNode.assocObject("graph_nodes_mapped_to_descendant_leaves", new LinkedList<Node>(licaMatches));

			TLongArrayList mrcaDescendantIds = new TLongArrayList((long[]) graphdb.getNodeById(inputJadeTreeLeafToMatchedGraphNodeIdMap.get(curJadeNode)).getProperty("mrca"));
//			System.out.println("Mapping tip [" + curJadeNode.getName() + "] to " + Arrays.toString(licaMatches.toArray()));						
			// add all the ids for the mrca descendants of the mapped node to the 'exclusive_mrca' field
			curJadeNode.assocObject("exclusive_mrca", mrcaDescendantIds.toArray());
			
		} else { // this is an internal node
			LinkedList<String> childNames = new LinkedList<String>();
			for (JadeNode child : curJadeNode.getDescendantLeaves()) {
				childNames.add(child.getName());
			}

			// testing
			System.out.println("looking for descendants of input node : " + Arrays.toString(childNames.toArray()));

			// NOTE: the following several variables contain similar information in different combinations and formats.
			// They are used to optimize the LICA searches by attempting to perform less exhaustive tests whenever possible.
			// For ease of interpretation, the variable names are constructed using the names of other related variables.
			// An underscore in the name indicates that what follows is a direct reference to another variable (with that name).
			
			// the nodes mapped to the descendant tips of the current node in the input tree
			LinkedList<Node> graphNodesMappedToDescendantLeavesOfThisJadeNode = new LinkedList<Node>();
			// the graph nodes corresponding to all the mrca descendants of the nodes mapped to all the tips descended from the current node in the input tree
			LinkedList<Node> graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode = new LinkedList<Node>();
			// the node ids of the nodes in graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode
			TLongArrayList nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode = new TLongArrayList();


			// begin alternative block 2
 			// gather lica mapping information independently for each node as we see it. this is slightly slower than recording this
			// info recursively in the jadenode objects as we traverse, but it uses *much* less memory
			TLongHashSet licaDescendantIdsForCurrentJadeNode_Hash = new TLongHashSet(); // use a hashset to avoid duplicate entries

			for (JadeNode curLeaf : curJadeNode.getDescendantLeaves()) {

				// remember the ids for the the graph nodes mapped to each jade tree leaf descended from the current node
				graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphdb.getNodeById(inputJadeTreeLeafToMatchedGraphNodeIdMap.get(curLeaf)));

				// get the mrca descendants for each leaf descended from this jade node (same info as the mrca fields from the graph nodes we matched to these jade tree leaves)
				ArrayList<Long> descendantGraphNodeIdsForCurLeaf = jadeNodeToDescendantGraphNodeIdsMap.get(curLeaf);

				// remember the node ids for all the mrca descendants of this jade node's descendant leaves (just from the input tree)
				nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.addAll(descendantGraphNodeIdsForCurLeaf);

				// also remember all the nodes themselves for for mrca descendant ids that we encounter
				for (Long descId : descendantGraphNodeIdsForCurLeaf) {
					graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.add(graphdb.getNodeById(descId));
				}
				
				HashSet<Node> childNodeLicaMappings = (HashSet<Node>) curLeaf.getObject("dbnodes"); // the graph nodes for the mrca mappings for this child node

				for (Node licaNode : childNodeLicaMappings) {
					licaDescendantIdsForCurrentJadeNode_Hash.addAll((long[]) licaNode.getProperty("mrca"));
				}
				
			}
			// end alternative block 2  */

			curJadeNode.assocObject("graph_nodes_mapped_to_descendant_leaves", graphNodesMappedToDescendantLeavesOfThisJadeNode);
			curJadeNode.assocObject("exclusive_mrca", nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.toArray());
		
			// testing
//			System.out.println("mrca descendants for input node \'" + curJadeNode.getName() + "\' include: " + Arrays.toString(nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.toArray()));
			
			nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.sort();
			curJadeNode.assocObject("exclusive_mrca", nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode.toArray()); // moved up from below

			// convert hashset to arraylist so we can use it in the lica calculations
			TLongArrayList licaDescendantIdsForCurrentJadeNode = new TLongArrayList(licaDescendantIdsForCurrentJadeNode_Hash);
			licaDescendantIdsForCurrentJadeNode.sort();

			// ================ this section needs review
			
			// get the outgroup ids, which is the set of mrca descendent ids for all the graph nodes mapped to jade nodes in the
			// input tree that are *not* descended from the current jade node
			TLongArrayList licaOutgroupDescendantIdsForCurrentJadeNode = new TLongArrayList();
			for (int i = 0; i < graphNodeIdsForInputLeaves.size(); i++) {
				if(licaDescendantIdsForCurrentJadeNode_Hash.contains(graphNodeIdsForInputLeaves.getQuick(i))==false) {
					licaOutgroupDescendantIdsForCurrentJadeNode.addAll((long[])graphdb.getNodeById(graphNodeIdsForInputLeaves.get(i)).getProperty("mrca"));
				}
			}
			
			// things that are in the ingroup of any child of this node are in the ingroup of this node (even if they're in the outgroup of another child)
			while(licaOutgroupDescendantIdsForCurrentJadeNode.removeAll(licaDescendantIdsForCurrentJadeNode)==true)
				continue;
			licaOutgroupDescendantIdsForCurrentJadeNode.sort();

			// =================
			
			LinkedList<String> names = new LinkedList<String>();
			for (JadeNode d : curJadeNode.getDescendantLeaves()) {
				names.add(d.getName());
			}
			//System.out.println("Looking for LICA Nodes of " + Arrays.toString(names.toArray()));
			
			// find all the compatible lica mappings for this jade node to existing graph nodes
			HashSet<Node> licaMatches = null;
			 // when taxon sets don't completely overlap, the lica calculator needs more info
			
			licaMatches = LicaUtil.getBipart4jChooseRelationshipType(curJadeNode,graphNodesMappedToDescendantLeavesOfThisJadeNode,
						graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						licaDescendantIdsForCurrentJadeNode,
						licaOutgroupDescendantIdsForCurrentJadeNode, graphdb,RelType.SYNTHCHILDOF);
			
			if (licaMatches.size() > 0) { // if we found any compatible lica mappings to nodes already in the graph
				// remember all the lica mappings
				//curJadeNode.assocObject("dbnodes", licaMatches);
				for(Node d: licaMatches){
					System.out.println("matchRel: "+d.getSingleRelationship(RelType.SYNTHCHILDOF,Direction.OUTGOING).getId());					
				}
			} else { // if there were no compatible lica mappings found for this jade node, then we need to make a new one
				//newLicaNode.setProperty("mrca", licaDescendantIdsForCurrentJadeNode.toArray());
				//newLicaNode.setProperty("outmrca", licaOutgroupDescendantIdsForCurrentJadeNode.toArray());
				// === step 3. assoc the jade node with the new graph node

				// first get the super licas, which is what would be the licas if we didn't have the other taxa in the tree
				// this will be used to connect the new nodes to their licas for easier traversals
				System.out.println("no match");
				/*
				HashSet<Node> superlicas = LicaUtil.getSuperLICAt4jChooseRelationshipType(graphNodesMappedToDescendantLeavesOfThisJadeNode,
						graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						nodeIdsFor_graphNodesDescendedFrom_graphNodesMappedToDescendantLeavesOfThisJadeNode,
						licaDescendantIdsForCurrentJadeNode, RelType.SYNTHCHILDOF);
				Iterator<Node> itrsl = superlicas.iterator();
				while (itrsl.hasNext()) {
					Node itrnext = itrsl.next();
					//newLicaNode.createRelationshipTo(itrnext, RelType.MRCACHILDOF);
					//updatedSuperLICAs.add(itrnext);
				}
				*/
			}
		}
	}
	
	private void remapToDeeperTaxon(){
		HashMap<JadeNode, Long> shallowTaxonMappings = new HashMap<JadeNode, Long>(inputJadeTreeLeafToMatchedGraphNodeIdMap);
		System.out.println("attempting to remap tips to deepest exemplified taxa");
		for (JadeNode curLeaf : shallowTaxonMappings.keySet()) {
			Long originalMatchedNodeId = shallowTaxonMappings.get(curLeaf);
			Node originalMatchedNode = graphdb.getNodeById(originalMatchedNodeId);
//			System.out.println("attempting to remap tip " + curLeaf.getName() + " (was mapped to " + getIdString(originalMatchedNode) +")");
			// get the outgroup set for this node, which is *all* the mrca descendendants of all the nodes mapped to all the input tips except this one.
			// we do this here because we get the mrca properties from the original taxon mappings.
			TLongArrayList outgroupIds = new TLongArrayList();
			for (Long tid : shallowTaxonMappings.values()) {
				if (tid.equals(originalMatchedNodeId) == false) {
					outgroupIds.addAll((long[]) graphdb.getNodeById(tid).getProperty("mrca"));
				}
			}
			Node newMatch = getDeepestExemplifiedTaxon(graphdb.getNodeById(originalMatchedNodeId), outgroupIds);
			if (originalMatchedNodeId.equals(newMatch.getId())) {
				//System.out.println("\t" + curLeaf.getName() + " was not remapped");
			} else { // we remapped the leaf to a deeper taxon
				//System.out.println("\t" + curLeaf.getName() + " was remapped");
				inputJadeTreeLeafToMatchedGraphNodeIdMap.put(curLeaf, newMatch.getId());
			}
		}
	}
	
	private Node getDeepestExemplifiedTaxon(Node inNode, TLongArrayList outgroupIds) {
		
		BitSet exemplarOutgroupIdsBS = new BitSet((int) outgroupIds.max());
		for (int i = 0; i < outgroupIds.size(); i++) {
			exemplarOutgroupIdsBS.set((int) outgroupIds.getQuick(i));
		}

		Node taxChild = inNode;
		Node taxParent = taxChild.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
		boolean stillLookingForDeepest = true;
		while (stillLookingForDeepest) {
			
			// get all the mrca descendants of the parent
			TLongArrayList parentIngroupIds = new TLongArrayList((long[]) taxParent.getProperty("mrca"));
			BitSet parentIngroupIdsBS = new BitSet((int) parentIngroupIds.max());
			for (int i = 0; i < parentIngroupIds.size(); i++) {
				parentIngroupIdsBS.set((int) parentIngroupIds.getQuick(i));
			}
			
			// if this node's parent taxon node contains anything from the outgroup, then this node is the deepest exemplified taxon
			if (parentIngroupIdsBS.intersects(exemplarOutgroupIdsBS)) {
				stillLookingForDeepest = false;

			} else { // move up to the next node
				taxChild = taxParent;
				try {
					taxParent = taxChild.getSingleRelationship(RelType.TAXCHILDOF, Direction.OUTGOING).getEndNode();
				} catch (NullPointerException ex) {
					//throw new IllegalStateException("hit the root of the graph while trying to remap " + getIdString(inNode) + ". this should not have happened.");
				}
			}
		}

		return taxChild;
	}
	
}
