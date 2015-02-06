package opentree.synthesis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import opentree.TLongBitSet;
import scala.actors.threadpool.Arrays;

public class BruteWeightedIS implements Iterable<Long> {
	
	private Long[] ids;
	private TLongBitSet[] descendants;

	private List<Long> bestSet;
	private double bestScore;
	
	public BruteWeightedIS(Long[] ids, TLongBitSet[] descendants) {
		this.ids = ids;
		this.descendants = descendants;
		System.out.println("brute initialized with: " + Arrays.toString(ids) + " and " + Arrays.toString(descendants));
		
		findBestSet();
	}
	
	@Override
	public Iterator<Long> iterator() {
		return bestSet.iterator();
	}
	
	/**
	 * Initialize recursive sampling with empty bitmask, starting from first position.
	 */
	private void findBestSet() {
		sample(new BitMask(ids.length), -1);
	}
	
	/**
	 * Recursive procedure takes incoming bitmask, opens each additional position, and
	 * checks if each updated bitmask still represents a valid set (no internal conflict).
	 * If it does, the candidate is scored and passed to the next recursive step.
	 */
	private void sample(BitMask incoming, int lastPos) {
		for (int i = 1; i + lastPos < incoming.size(); i++) {
			int nextPos = lastPos + i;
			BitMask candidate = new BitMask(incoming);
			candidate.open(nextPos);
			
			if (validate(candidate)) {
				scoreSizeOnly(candidate); // switch this to a smarter scoring mechanism
				sample(candidate, nextPos);
			}
		}
	}

	private boolean validate(BitMask candidate) {
		TLongBitSet S = new TLongBitSet();

		for (int j : candidate) {
			if (S.containsAny(descendants[j])) {
				return false; // internal conflict
			} else {
				S.addAll(descendants[j]);
			}
		}
		return true;
	}
	
	/** 
	 * Trivial scoring based only on total set size. Adjust this to address conflict with trees.
	 */
	private void scoreSizeOnly(BitMask candidate) {
		
		double s = 0;
		ArrayList<Long> R = new ArrayList<Long>();
		for (int j : candidate) {
			s += descendants[j].size();
			R.add(ids[j]);
		}
		
		if (s > bestScore) {
			bestScore = s;
			bestSet = R;
		}
	}
	
	public static void main(String[] args) {
		
		Long[] ids = new Long[] {1L, 2L, 3L, 4L};
		
		TLongBitSet a = new TLongBitSet();
		a.addAll(new int[] {1,3,5});
		
		TLongBitSet b = new TLongBitSet();
		b.addAll(new int[] {3,8});
		
		TLongBitSet c = new TLongBitSet();
		c.addAll(new int[] {4,9,10});
		
		TLongBitSet d = new TLongBitSet();
		d.addAll(new int[] {11});
/*		
		TLongBitArray e = new TLongBitArray();
		e.addAll(new int[] {1,2,3,4,5});
		
		TLongBitArray f = new TLongBitArray();
		f.addAll(new int[] {1,2,3,4,5});
		
		TLongBitArray g = new TLongBitArray();
		g.addAll(new int[] {1,2,3,4,5});
		
		TLongBitArray h = new TLongBitArray();
		h.addAll(new int[] {1,2,3,4,5});
		
		TLongBitArray i = new TLongBitArray();
		i.addAll(new int[] {1,2,3,4,5});
		
		TLongBitArray j = new TLongBitArray();
		j.addAll(new int[] {1,2,3,4,5}); */
		
		TLongBitSet[] descendants = new TLongBitSet[] {a, b, c, d};
		
		BruteWeightedIS B = new BruteWeightedIS(ids, descendants);

		for (Long relId : B) {
			System.out.println(relId);
		}
	}
}
