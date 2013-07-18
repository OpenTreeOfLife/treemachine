package opentree.constants;

public enum RelProperty {

	SOURCE ("source", String.class);
	
	public String propertyName;
	public final Class<?> type;
    
    RelProperty(String propertyName, Class<?> T) {
        this.propertyName = propertyName;
        this.type = T;
    }	
}
