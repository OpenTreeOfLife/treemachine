package opentree.synthesis.mwis;

public class Logarithm {
	public static double logb(double a, double b) {
		return Math.log(a) / Math.log(b);
	}

	public static double log2(double a) {
		return logb(a, 2);
	}

	public static void main(String[] args) {
		int n = 8;
		System.out.println(log2(n)+1);
		System.out.println(Math.ceil(log2(n)+1));
		System.out.println(Integer.toBinaryString(n));
	}
}