package org.fogbowcloud.manager.occi.order;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.occi.model.Category;
import org.fogbowcloud.manager.occi.model.Token;

public class Order {

	public static String SEPARATOR_GLOBAL_ID = "@";
	
	private String id;
	private Token federationToken;
	private String instanceId;
	private String providingMemberId;
	private final String requestingMemberId;
	private long fulfilledTime = 0;
	private final boolean isLocal;
	private OrderState state;
	private List<Category> categories;
	private Map<String, String> xOCCIAtt;	
	private String resourceKind;
	private long syncronousTime;
	private boolean syncronousStatus;
	
	private DateUtils dateUtils = new DateUtils();
		
	public Order(String id, Token federationToken, String instanceId, String providingMemberId,
			String requestingMemberId, long fulfilledTime, boolean isLocal, OrderState state,
			List<Category> categories, Map<String, String> xOCCIAtt) {
		this.id = id;
		this.federationToken = federationToken;
		this.instanceId = instanceId;
		this.providingMemberId = providingMemberId;
		this.requestingMemberId = requestingMemberId;
		this.fulfilledTime = fulfilledTime;
		this.isLocal = isLocal;
		this.state = state;
		this.categories = categories;
		this.xOCCIAtt = xOCCIAtt;
		if (this.xOCCIAtt == null) {
			this.resourceKind = null;
		} else {
			this.resourceKind = this.xOCCIAtt.get(OrderAttribute.RESOURCE_KIND.getValue());
		}		
	}

	public Order(String id, Token federationToken, 
			List<Category> categories, Map<String, String> xOCCIAtt, boolean isLocal, String requestingMemberId) {
		this(id, federationToken, categories, xOCCIAtt, isLocal, requestingMemberId, new DateUtils());
	}
	
	public Order(String id, Token federationToken, 
			List<Category> categories, Map<String, String> xOCCIAtt, boolean isLocal, String requestingMemberId, DateUtils dateUtils) {
		this.id = id;
		this.federationToken = federationToken;
		this.categories = categories;
		this.xOCCIAtt = xOCCIAtt;
		this.isLocal = isLocal;
		this.requestingMemberId = requestingMemberId;
		this.dateUtils = dateUtils;
		setState(OrderState.OPEN);		
		if (this.xOCCIAtt == null) {
			this.resourceKind = null;
		} else {
			this.resourceKind = this.xOCCIAtt.get(OrderAttribute.RESOURCE_KIND.getValue());
		}
	}
	
	public Order(Order order) {
		this(order.getId(), order.getFederationToken(), order.getCategories(), 
				order.getxOCCIAtt(), order.isLocal, order.getRequestingMemberId());
	}

	public List<Category> getCategories() {
		if (categories == null) {
			return null;
		}
		return new ArrayList<Category>(categories);
	}

	public void addCategory(Category category) {
		if (categories == null) {
			categories = new LinkedList<Category>();
		}
		if (!categories.contains(category)) {
			categories.add(category);
		}
	}

	public boolean isSyncronousStatus() {
		return syncronousStatus;
	}
	
	public void setSyncronousStatus(boolean syncronousStatus) {
		this.syncronousStatus = syncronousStatus;
	}
	
	public long getSyncronousTime() {
		return syncronousTime;
	}
	
	public void setSyncronousTime(long syncronousTime) {
		this.syncronousTime = syncronousTime;
	}
	
	public String getRequirements() {
		return xOCCIAtt.get(OrderAttribute.REQUIREMENTS.getValue());
	}
	
	public String getBatchId() {
		return xOCCIAtt.get(OrderAttribute.BATCH_ID.getValue());
	}
	
	public String getInstanceId() {
		return instanceId;
	}

	public String getGlobalInstanceId() {
		if (instanceId != null) {
			return instanceId + SEPARATOR_GLOBAL_ID + providingMemberId;
		}
		return instanceId;
	}

	public void setInstanceId(String instanceId) {
		this.instanceId = instanceId;
	}
	
	public void setDateUtils(DateUtils dateUtils) {
		this.dateUtils = dateUtils;
	}
	
	public boolean isLocal(){
		return isLocal;
	}

	public OrderState getState() {
		return state;
	}

	public void setState(OrderState state) {
		if (state.in(OrderState.FULFILLED)) {
			fulfilledTime = dateUtils.currentTimeMillis();
		} else if (state.in(OrderState.OPEN)) {
			fulfilledTime = 0;
		}
		this.state = state;
	}

	public String getId() {
		return id;
	}

	public String getAttValue(String attributeName) {
		if (xOCCIAtt == null) {
			return null;
		}
		return xOCCIAtt.get(attributeName);
	}

	public void putAttValue(String attributeName, String attributeValue) {
		if (xOCCIAtt == null) {
			xOCCIAtt = new HashMap<String, String>();
		}
		xOCCIAtt.put(attributeName, attributeValue);
	}

	public Token getFederationToken() {
		return this.federationToken;
	}

	public void setFederationToken(Token token) {
		this.federationToken = token;
	}
	
	public long getFulfilledTime() {
		return fulfilledTime;
	}

	public Map<String, String> getxOCCIAtt() {
		if (xOCCIAtt == null) {
			return new HashMap<String, String>();
		}
		return new HashMap<String, String>(xOCCIAtt);
	}
	
	public String getRequestingMemberId(){
		return requestingMemberId;
	}

	public String getProvidingMemberId() {
		return providingMemberId;
	}

	public void setProvidingMemberId(String providingMemberId) {
		this.providingMemberId = providingMemberId;
	}
	
	public void setxOCCIAtt(Map<String, String> xOCCIAtt) {
		this.xOCCIAtt = xOCCIAtt;
	}
	
	public String getResourceKind() {
		return resourceKind;
	}
	
	public void setResourceKind(String resourceKind) {
		this.resourceKind = resourceKind;
	}

	public String toString() {
		return "id: " + id + ", token: " + federationToken + ", instanceId: " + instanceId
				+ ", providingMemberId: " + providingMemberId + ", requestingMemberId: "
				+ requestingMemberId + ", state: " + state + ", isLocal " + isLocal
				+ ", categories: " + categories + ", xOCCIAtt: " + xOCCIAtt;
	}


	public boolean isIntoValidPeriod() {
		String startDateStr = xOCCIAtt.get(OrderAttribute.VALID_FROM.getValue());
		Date startDate = DateUtils.getDateFromISO8601Format(startDateStr);
		if (startDate == null) {
			if (startDateStr != null) {
				return false;
			}
			startDate = new Date();
		}
		long now = new DateUtils().currentTimeMillis();
		return startDate.getTime() <= now && !isExpired();
	}

	public boolean isExpired() {
		String expirationDateStr = xOCCIAtt.get(OrderAttribute.VALID_UNTIL.getValue());
		Date expirationDate = DateUtils.getDateFromISO8601Format(expirationDateStr);
		if (expirationDateStr == null) {
			return false;
		} else if (expirationDate == null) {
			return true;
		}

		long now = new DateUtils().currentTimeMillis();
		return expirationDate.getTime() < now;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Order other = (Order) obj;
		if (categories == null) {
			if (other.categories != null)
				return false;
		} else if (!categories.equals(other.categories))
			return false;
		if (federationToken == null) {
			if (other.federationToken != null)
				return false;
		} else if (!federationToken.equals(other.federationToken))
			return false;
		if (fulfilledTime != other.fulfilledTime)
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (instanceId == null) {
			if (other.instanceId != null)
				return false;
		} else if (!instanceId.equals(other.instanceId))
			return false;
		if (isLocal != other.isLocal)
			return false;
		if (providingMemberId == null) {
			if (other.providingMemberId != null)
				return false;
		} else if (!providingMemberId.equals(other.providingMemberId))
			return false;
		if (requestingMemberId == null) {
			if (other.requestingMemberId != null)
				return false;
		} else if (!requestingMemberId.equals(other.requestingMemberId))
			return false;
		if (state != other.state)
			return false;
		if (xOCCIAtt == null) {
			if (other.xOCCIAtt != null)
				return false;
		} else if (xOCCIAtt != null && !new HashSet(xOCCIAtt.values()).equals(new HashSet(other.xOCCIAtt.values())))
			return false;
		return true;
	}
	
}