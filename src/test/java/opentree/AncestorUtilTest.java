package opentree;

/* Uses some template code from
	 https://github.com/neo4j/community/blob/1.7.2/embedded-examples/src/test/java/org/neo4j/examples/Neo4jBasicTest.java
*/
// junit functions
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

//
import java.util.List;
import java.util.LinkedList;

// Core neo4j components for a test db
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.ImpermanentGraphDatabase;
import org.neo4j.test.TestGraphDatabaseFactory;

// neo4 packages needed by this test
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipExpander;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.Traversal;

// the opentree needed by the test
import opentree.GraphBase.RelTypes;

// the opentree package to be tested
import opentree.AncestorUtil;

public class AncestorUtilTest {

	protected GraphDatabaseService graphDb;
	protected Node human;
	protected Node chimp;
	protected Node gorilla;
	protected Node orang;
	protected Node bogus;
	protected Node hc_anc;
	protected Node hcg_anc;
	protected Node hcgo_anc;

	/**
	 * Create temporary database for each unit test.
	 */
	// START SNIPPET: beforeTest
	@Before
	public void prepareTestDatabase() {
		graphDb = new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder().newGraphDatabase();

		// START SNIPPET: unitTest
        Transaction tx = graphDb.beginTx();

        Node n = null;
        try {
            human = graphDb.createNode();
            human.setProperty("name", "human");
            chimp = graphDb.createNode();
            chimp.setProperty("name", "chimp");
            gorilla = graphDb.createNode();
            gorilla.setProperty("name", "gorilla");
            orang = graphDb.createNode();
            orang.setProperty("name", "orang");
			hc_anc = graphDb.createNode();
            hc_anc.setProperty("name", "hc_anc");
            human.createRelationshipTo(hc_anc, RelTypes.MRCACHILDOF);
            chimp.createRelationshipTo(hc_anc, RelTypes.MRCACHILDOF);
			hcg_anc = graphDb.createNode();
            hcg_anc.setProperty("name", "hcg_anc");
            hc_anc.createRelationshipTo(hcg_anc, RelTypes.MRCACHILDOF);
            gorilla.createRelationshipTo(hcg_anc, RelTypes.MRCACHILDOF);
			hcgo_anc = graphDb.createNode();
            hcgo_anc.setProperty("name", "hcgo_anc");
            hcg_anc.createRelationshipTo(hcgo_anc, RelTypes.MRCACHILDOF);
            orang.createRelationshipTo(hcgo_anc, RelTypes.MRCACHILDOF);
            bogus = graphDb.createNode();
            bogus.setProperty("name", "bogus");
            tx.success();
        }
        catch ( Exception e ) {
            tx.failure();
        }
        finally {
            tx.finish();
        }		
	}
	// END SNIPPET: beforeTest

	/**
	 * Shutdown the database.
	 */
	// START SNIPPET: afterTest
	@After
	public void destroyTestDatabase() {
		graphDb.shutdown();
		System.err.println("In destroyTestDatabase");
	}
	// END SNIPPET: afterTest

	@Test
	public void testAnc() throws Exception {
		LinkedList<Node> leaf_list = new LinkedList<Node>();
		leaf_list.add(human);
		leaf_list.add(chimp);
		RelationshipExpander expander = Traversal.expanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING);
		Node anc = AncestorUtil.lowestCommonAncestor( leaf_list, expander);
		assertNotNull(anc);
		assertEquals(anc.getProperty("name"), "hc_anc");
		
		leaf_list.clear();
		leaf_list.add(human);
		leaf_list.add(gorilla);
		anc = AncestorUtil.lowestCommonAncestor( leaf_list, expander);
		assertNotNull(anc);
		assertEquals(anc.getProperty("name"), "hcg_anc");

		leaf_list.clear();
		leaf_list.add(chimp);
		leaf_list.add(gorilla);
		anc = AncestorUtil.lowestCommonAncestor( leaf_list, expander);
		assertNotNull(anc);
		assertEquals(anc.getProperty("name"), "hcg_anc");

		leaf_list.clear();
		leaf_list.add(gorilla);
		leaf_list.add(chimp);
		leaf_list.add(human);
		anc = AncestorUtil.lowestCommonAncestor( leaf_list, expander);
		assertNotNull(anc);
		assertEquals(anc.getProperty("name"), "hcg_anc");

		leaf_list.clear();
		leaf_list.add(gorilla);
		leaf_list.add(orang);
		anc = AncestorUtil.lowestCommonAncestor( leaf_list, expander);
		assertNotNull(anc);
		assertEquals(anc.getProperty("name"), "hcgo_anc");
	}

	@Test
	public void testNoAnc() throws Exception {
		LinkedList<Node> leaf_list = new LinkedList<Node>();
		leaf_list.add(human);
		leaf_list.add(bogus);
		RelationshipExpander expander = Traversal.expanderForTypes(RelTypes.MRCACHILDOF, Direction.OUTGOING);
		Node anc = AncestorUtil.lowestCommonAncestor( leaf_list, expander);
		assertNull(anc);
	}
}
