package opentree.synthesis.filtering;

import java.util.HashSet;

import opentree.synthesis.SourcePropertyValue;

/**
 * This class is used to contain arbitrary values for comparison against SourcePropertyValue objects. It is used
 * by the SourcePropertySingleValueTest class.
 * 
 * @author cody hinchliff
 *
 */
public class TestValue implements Comparable<SourcePropertyValue> {
	
	String s;
	Long l;
	Double d;
	HashSet<String> h;
	Class<?> type;
	
	// string constructor
	public TestValue(String s) {
		this.s = s;
		this.type = String.class;
	}

	// integer constructor
	public TestValue(int i) {
		this.l = Long.valueOf(i);
		this.type = Long.class;
	}

	// long constructor
	public TestValue(Long l) {
		this.l = Long.valueOf(l);
		this.type = Long.class;
	}

	// float constructor
	public TestValue(Float f) {
		this.d = Double.valueOf(f);
		this.type = Double.class;
	}

	// double constructor
	public TestValue(Double d) {
		this.d = d;
		this.type = Double.class;
	}

	public Object getValue() {
		if (type == Long.class)
			return this.l;

		else if (type == String.class)
			return this.s;

		else if (type == Double.class)
			return this.d;
		else if (type == HashSet.class){
			return this.h;
		}else
			return null;
	}

	public int compareTo(SourcePropertyValue comparable) {

		if (this.type == String.class) {
			if (comparable.type() != String.class) {
				throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
			} else {
				boolean b = s.equals(comparable.value()); // returns 1 if they are equal
				int val = b? 1 : 0;
				return val;
			}
		
		} else if (this.type == Double.class) {
			if (comparable.type() == String.class) {
				throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
			} else {
				return d.compareTo((Double) comparable.value());
			}
		
		} else if (this.type == Long.class) {
			if (comparable.type() == String.class) {
				throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
			} else {
				return l.compareTo((Long) comparable.value());
			}
			
		} else {
			throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
		}
	}
	
	private String illegalComparisonMessage(Class<?> incomparableClass) {
		return "cannot compare " + incomparableClass.toString() + " to " + this.type.toString();
	}
}
