package opentree.synthesis;

import opentree.constants.SourceProperty;

public class SourcePropertyValue implements Comparable<SourcePropertyValue> {
	
	private String s;
	private Long l;
	private Double d;
	private SourceProperty property;

	public SourcePropertyValue(SourceProperty property, Object value) {
		this.property = property;
		storeValue(value);
	}

	/**
	 * store the value
	 * @param value
	 */
	private void storeValue(Object value) {

		if (property.type == String.class) {
			this.s = String.valueOf(value);

		} else if (property.type == Long.class || property.type == Integer.class) {
			this.l = (Long) value;

		} else if (property.type == Double.class) {
			this.d = (Double) value;

		} else {
			throw new java.lang.IllegalArgumentException("property type " + property.type + " is unrecognized");
		}
	}
	
	/**
	 * Return the type of the stored value.
	 * @return type
	 */
	public Class<?> type() {
		return property.type;
	}

	/**
	 * Return the stored value as a generic object.
	 * @return value
	 */
	public Object value() {

		if (property.type == String.class) {
			return s;

		} else if (property.type == Long.class || property.type == Integer.class) {
			return l;

		} else if (property.type == Double.class) {
			return d;

		} else {
			// for objects without stored values
			return null;
		}
	}
	
	/**
	 * Compare the stored value to another SourcePropertyValue.
	 * 
	 * @return 0 if equal; -1 if stored value is less; 1 if stored value is greater
	 */
	@Override
	public int compareTo(SourcePropertyValue comparable) {

		if (this.property.type == String.class) {
			if (comparable.type() != String.class) {
				throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
			} else {
				return s.compareTo((String) comparable.value());
			}
		
		} else if (this.property.type == Double.class) {
			if (comparable.type() == String.class) {
				throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
			} else {
				return d.compareTo((Double) comparable.value());
			}
		
		} else if (this.property.type == Long.class || this.property.type == Integer.class) {
			if (comparable.type() == String.class) {
				throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
			} else {
				return l.compareTo((Long) comparable.value());
			}

		} else {
			throw new java.lang.UnsupportedOperationException(illegalComparisonMessage(comparable.type()));
		}
	}
	
	/**
	 * Generate an error message describing the attempted illegal comparison.
	 * 
	 * @param incomparableClass
	 * @return message
	 */
	private String illegalComparisonMessage(Class<?> incomparableClass) {
		return "cannot compare " + incomparableClass.toString() + " to " + this.property.type.toString();
	}
}
