package opentree.synthesis;

//EXPERIMENTAL

/*
 * There is nothing particularly special about this file. It is just currently a test
 * class for experimenting with some synthesis queries. I fully expect that it will be
 * deleted at some point. (from sas?)
 * 
 * response: I have commented out the class in light of the above comment, as it was causing
 * compile errors due to some refactoring. It can be reinstated if it is still being used. - ceh 2013 07 17
 */

public class SynthesisUtils /* extends GraphBase */  {
	
/*	public SynthesisUtils(){}
	
	public void get_edges_for_taxaset(GraphDatabaseService graphDb, HashSet<String> TaxaList) throws TaxonNotFoundException{
		PathFinder <Path> pf = GraphAlgoFactory.shortestPath(Traversal.pathExpanderForTypes(RelTypes.TAXCHILDOF, Direction.OUTGOING), 1000);
		Node focalnode = graphDb.getNodeById(1);//OBVIOUSLY NEED TO SET THIS
		HashSet<Long> ndids = new HashSet<Long>();
		HashSet<Node> nodes = new HashSet<Node>();
		Iterator<String> taxiterator = TaxaList.iterator();
		while (taxiterator.hasNext()){
			String name = taxiterator.next();
			Node hitnode = null;
			String processedname = name.replace("_", " "); //@todo processing syntactic rules like '_' -> ' ' should be done on input parsing. 
			IndexHits<Node> hits = graphNodeIndex.get("name", processedname);
			int numh = hits.size();
			if (numh == 1){
				hitnode = hits.getSingle();
			}else if (numh > 1){
				System.out.println(processedname + " gets " + numh +" hits");
				int shortest = 1000;//this is shortest to the focal, could reverse this
				Node shortn = null;
				for(Node tnode : hits){
					Path tpath = pf.findSinglePath(tnode, focalnode);
					if (tpath!= null){
						if (shortn == null)
							shortn = tnode;
						if(tpath.length()<shortest){
							shortest = tpath.length();
							shortn = tnode;
						}
					}
				}
				assert shortn != null; // @todo this could happen if there are multiple hits outside the focalgroup, and none inside the focalgroup.  We should develop an AmbiguousTaxonException class
				hitnode = shortn;
			}
			hits.close();
			if (hitnode == null) {
				assert numh == 0;
				throw new TaxonNotFoundException(processedname);
			}
			ndids.add(hitnode.getId());
			nodes.add(hitnode);
		}
		
	} */

}
