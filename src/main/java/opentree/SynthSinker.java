package opentree;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import opentree.constants.RelType;
import opentree.synthesis.GraphGenerator;
import opentree.synthesis.TopologicalOrder;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.opentree.bitarray.ImmutableCompactLongSet;
import org.opentree.bitarray.MutableCompactLongSet;
import org.opentree.graphdb.GraphDatabaseAgent;

public class SynthSinker {
	GraphDatabaseAgent gdb;
	Node startNode;
	HashMap<Long,MutableCompactLongSet> nodemap = new HashMap<Long,MutableCompactLongSet>();
	
	public SynthSinker(Long startId,GraphDatabaseAgent gdb){
		this.gdb = gdb;
		this.startNode = gdb.getNodeById(startId);
	}
	
	/*
	 * this conducts a postorder traversal through the tree by way of the synth child of rels
	 * and then checks each node and if the taxa that are supposed to be present aren't subtending
	 * then all below are sunk one more 
	 */
	public void sinkSynth(){
		CustomTopologicalOrder order = new CustomTopologicalOrder(gdb, RelType.SYNTHCHILDOF);
		System.out.println("nodes in topological order: \n");
		for (Node n : order) {
			if(n.hasRelationship(Direction.INCOMING, RelType.SYNTHCHILDOF)==false){
				nodemap.put(n.getId(), new MutableCompactLongSet());
				nodemap.get(n.getId()).add(n.getId());
			}else{
				MutableCompactLongSet mls = new MutableCompactLongSet((long[])n.getProperty("mrca"));
				MutableCompactLongSet tests = new MutableCompactLongSet();
				for(Relationship rel: n.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)){
					tests.addAll(nodemap.get(rel.getStartNode().getId()));
				}
				nodemap.put(n.getId(), tests);
				if(tests.containsAll(mls)==false){
					System.out.println("fixing "+n);
					Node par = n.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).getEndNode();	
					Transaction tx = gdb.beginTx();
					HashSet<Node> fixones = new HashSet<Node>();
					for(Relationship rel: n.getRelationships(Direction.INCOMING, RelType.SYNTHCHILDOF)){
						fixones.add(rel.getStartNode());
					}
					for(Node f: fixones){
						f.getSingleRelationship(RelType.SYNTHCHILDOF, Direction.OUTGOING).delete();
						Relationship rel = f.createRelationshipTo(par, RelType.SYNTHCHILDOF);
					}
					tx.success();
					tx.finish();
				}
			}
			System.out.println(n);
		}
	}
	
	public class CustomTopologicalOrder implements Iterable<Node> {

		private Set<Node> unmarked = new HashSet<Node>();
		private Set<Node> temporaryMarked = new HashSet<Node>();
		private List<Node> nodes = new LinkedList<Node>();
		
		private final RelationshipType[] relTypes;
		
		public CustomTopologicalOrder(GraphDatabaseAgent G, RelationshipType... relTypes) {

			this.relTypes = relTypes;
			
			for (Node n : G.getAllNodes()) { 
				if(n.hasRelationship(RelType.SYNTHCHILDOF))
					unmarked.add(n); 
			}

			while (! unmarked.isEmpty()) {
				visit(unmarked.iterator().next());
			}
		}
		
		private void visit(Node n) {
			if (temporaryMarked.contains(n)) {
				throw new IllegalArgumentException("The graph contains a directed cycle that includes the node: " + n);
			}

			if (unmarked.contains(n)) {
				temporaryMarked.add(n);
				for (Relationship m : n.getRelationships(Direction.INCOMING, relTypes)) {
					visit(m.getStartNode());
				}
				
				unmarked.remove(n);
				temporaryMarked.remove(n);
				nodes.add(n);
			}
		}
		
		@Override
		public Iterator<Node> iterator() {
			return nodes.iterator();
		}
		
	}
	
}
