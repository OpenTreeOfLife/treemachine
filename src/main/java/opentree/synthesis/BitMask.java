package opentree.synthesis;

public interface BitMask extends Iterable<Integer> {
	public void open(int position);
	public void close(int position);
	public boolean isSet(int position);
	public int size();
	public int maxSize();
}
