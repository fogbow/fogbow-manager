package org.fogbowcloud.manager.occi.request;

public enum RequestType {
	
	ONE_TIME("one-time"), PERSISTENT("persistent");
	
	private String value;
	
	RequestType(String value) {
		this.value = value;
	}
	
	public String getValue() {
		return value;
	}
}