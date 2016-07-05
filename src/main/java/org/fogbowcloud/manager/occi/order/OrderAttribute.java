package org.fogbowcloud.manager.occi.order;

import java.util.ArrayList;
import java.util.List;

public enum OrderAttribute {
	
	INSTANCE_COUNT("org.fogbowcloud.request.instance-count"), 
	TYPE("org.fogbowcloud.request.type"),
	VALID_UNTIL("org.fogbowcloud.request.valid-until"), 
	VALID_FROM("org.fogbowcloud.request.valid-from"),
	STATE("org.fogbowcloud.request.state"),
	INSTANCE_ID("org.fogbowcloud.request.instance-id"),
	DATA_PUBLIC_KEY("org.fogbowcloud.credentials.publickey.data"),
	USER_DATA_ATT("org.fogbowcloud.request.user-data"),
	EXTRA_USER_DATA_ATT("org.fogbowcloud.request.extra-user-data"),
	EXTRA_USER_DATA_CONTENT_TYPE_ATT("org.fogbowcloud.request.extra-user-data-content-type"),
	REQUIREMENTS("org.fogbowcloud.request.requirements"),
	BATCH_ID("org.fogbowcloud.request.batch-id"),
	REQUESTING_MEMBER("org.fogbowcloud.request.requesting-member"),
	PROVIDING_MEMBER("org.fogbowcloud.request.providing-member"),
	RESOURCE_KIND("org.fogbowcloud.order.resource-kind"),
	STORAGE_SIZE("org.fogbowcloud.order.storage-size"),
	NETWORK_ID("org.fogbowcloud.order.network-id");
	
	private String value;
	
	private OrderAttribute(String value) {
		this.value = value;
	}
	
	public String getValue() {
		return this.value;
	}
	
	public static List<String> getValues() {	
		List<String> values = new ArrayList<String>();		
		OrderAttribute[] elements = values();
		for (OrderAttribute attribute : elements) {
			values.add(attribute.getValue());
		}
		return values;
	}
}