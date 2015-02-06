package opentree.synthesis;

import java.util.HashMap;
import java.util.HashSet;

public class WeightedUndirectedGraph {
		
	class Node {
		
		private long id;
		private int weight;
		private HashSet<Long> edges = new HashSet<Long>();
		
		public Node(long id, int weight) {
			this.id = id;
			this.weight = weight;
		}
		
		public void attachTo(long other) {
			Node o = WeightedUndirectedGraph.this.getNode(other);
			assert o != null;
			edges.add(other);
			o.edges.add(id);
		}
		
		public int weight() {
			return weight;
		}
		
		public long id() {
			return id;
		}
		
		public Node[] adjacentNodes() {
			Node[] N = new Node[edges.size()];
			int i = 0;
			for (Long id : edges) {
				N[i++] = WeightedUndirectedGraph.this.getNode(id);
			}
			return N;
		}
	}
	
	private HashMap<Long, Node> V;
	
	public WeightedUndirectedGraph() {
		V = new HashMap<Long, Node>();
	}
	
	public void addNode(long id, int weight) {
		Node n = new Node(id, weight);
		V.put(n.id(), n);
	}
	
	public Node getNode(long id) {
		return V.get(id);
	}
	
	@Override
	public String toString() {
		StringBuffer s = new StringBuffer();
		for (Node v : V.values()) {
			s.append(v.id);
			for (long w : v.edges) {
				s.append("\t" + w);
			}
			s.append("\n");
		}
		return s.toString();
	}
	
	public static void main(String[] args) {
		WeightedUndirectedGraph G = new WeightedUndirectedGraph();
		G.addNode(1, 1);
		G.addNode(2, 2);
		G.addNode(3, 3);
		G.addNode(4, 4);
		G.addNode(5, 5);
		G.addNode(6, 4);
		G.addNode(7, 3);
		G.addNode(8, 2);
		G.addNode(9, 1);
		G.getNode(1).attachTo(2);
		G.getNode(2).attachTo(3);
		G.getNode(3).attachTo(4);
		G.getNode(4).attachTo(5);
		G.getNode(5).attachTo(6);
		G.getNode(6).attachTo(7);
		G.getNode(7).attachTo(8);
		G.getNode(8).attachTo(9);
		G.getNode(9).attachTo(1);
		System.out.println(G);
	}
}