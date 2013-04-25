package opentree.synthesis;

/**
 * This class is used to contain arbitrary values for comparison against SourcePropertyValue objects. It is used
 * by classes implementing the FilterCriterion interface.
 * 
 * @author cody hinchliff
 *
 */
public class TestValue implements Comparable<SourcePropertyValue> {
	
	String s;
	Long l;
	Double d;
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
			return (Object) this.l;

		else if (type == String.class)
			return (Object) this.s;

		else if (type == Double.class)
			return (Object) this.d;
		
		else
			return null;
	}

	@Override
	public int compareTo(SourcePropertyValue comparable) {

		if (this.type == String.class) {
			if (comparable.type() != String.class) {
				throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
			} else {
				return s.compareTo((String) comparable.value());
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
