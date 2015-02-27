package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.model.DateUtils;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.model.ServedRequest;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.ImageStoragePlugin;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.core.ErrorType;
import org.fogbowcloud.manager.occi.core.OCCIException;
import org.fogbowcloud.manager.occi.core.Resource;
import org.fogbowcloud.manager.occi.core.ResourceRepository;
import org.fogbowcloud.manager.occi.core.ResponseConstants;
import org.fogbowcloud.manager.occi.core.Token;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestAttribute;
import org.fogbowcloud.manager.occi.request.RequestConstants;
import org.fogbowcloud.manager.occi.request.RequestRepository;
import org.fogbowcloud.manager.occi.request.RequestState;
import org.fogbowcloud.manager.occi.request.RequestType;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.fogbowcloud.manager.xmpp.ManagerPacketHelper;
import org.restlet.Response;

public class ManagerController {
	
	private static final String PROP_MAX_WHOISALIVE_MANAGER_COUNT = "max_whoisalive_manager_count";
	private static final Logger LOGGER = Logger.getLogger(ManagerController.class);
	public static final long DEFAULT_SCHEDULER_PERIOD = 30000; // 30 seconds
	private static final long DEFAULT_TOKEN_UPDATE_PERIOD = 300000; // 5 minutes
	private static final long DEFAULT_INSTANCE_MONITORING_PERIOD = 120000; // 2 minutes
	private static final long DEFAULT_SERVED_REQUEST_MONITORING_PERIOD = 120000; // 2 minutes
	private static final long DEFAULT_GARBAGE_COLLECTOR_PERIOD = 240000; // 4 minutes
																			
	private final ManagerTimer requestSchedulerTimer;
	private final ManagerTimer tokenUpdaterTimer;
	private final ManagerTimer instanceMonitoringTimer;
	private final ManagerTimer servedRequestMonitoringTimer;
	private final ManagerTimer garbageCollectorTimer;

	private Token federationUserToken;
	private final List<FederationMember> members = Collections.synchronizedList(new LinkedList<FederationMember>());
	private RequestRepository requests = new RequestRepository();
	private FederationMemberPicker memberPicker = new RoundRobinMemberPicker();

	private ImageStoragePlugin imageStoragePlugin;
	private AuthorizationPlugin authorizationPlugin;
	private ComputePlugin computePlugin;
	private IdentityPlugin localIdentityPlugin;
	private IdentityPlugin federationIdentityPlugin;
	private Properties properties;
	private AsyncPacketSender packetSender;
	private FederationMemberValidator validator = new DefaultMemberValidator();
	private Map<String, ServedRequest> instancesForRemoteMembers = new HashMap<String, ServedRequest>();
	private Map<String, ForwardedRequest> asynchronousRequests = new HashMap<String, ForwardedRequest>();

	private DateUtils dateUtils = new DateUtils();
	public ManagerController(Properties properties) {
		this(properties, null);
	}

	public ManagerController(Properties properties, ScheduledExecutorService executor) {
		if (properties == null) {
			throw new IllegalArgumentException();
		}
		this.properties = properties;
		if (executor == null) {
			this.requestSchedulerTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.tokenUpdaterTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.instanceMonitoringTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.servedRequestMonitoringTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
			this.garbageCollectorTimer = new ManagerTimer(Executors.newScheduledThreadPool(1));
		} else {
			this.requestSchedulerTimer = new ManagerTimer(executor);
			this.tokenUpdaterTimer = new ManagerTimer(executor);
			this.instanceMonitoringTimer = new ManagerTimer(executor);
			this.servedRequestMonitoringTimer = new ManagerTimer(executor);
			this.garbageCollectorTimer = new ManagerTimer(executor);
		}
	}

	public void setAuthorizationPlugin(AuthorizationPlugin authorizationPlugin) {
		this.authorizationPlugin = authorizationPlugin;
	}

	public void setImageStoragePlugin(ImageStoragePlugin imageStoragePlugin) {
		this.imageStoragePlugin = imageStoragePlugin;
	}
	
	public void setComputePlugin(ComputePlugin computePlugin) {
		this.computePlugin = computePlugin;
		// garbage collector may starting only after set compute plugin
		if (!garbageCollectorTimer.isScheduled()) {
			triggerGarbageCollector();
		}
	}

	public void setLocalIdentityPlugin(IdentityPlugin identityPlugin) {
		this.localIdentityPlugin = identityPlugin;
	}

	public void setFederationIdentityPlugin(IdentityPlugin federationIdentityPlugin) {
		this.federationIdentityPlugin = federationIdentityPlugin;
	}
	
	private void triggerGarbageCollector() {
		String garbageCollectorPeriodStr = properties
				.getProperty(ConfigurationConstants.GARBAGE_COLLECTOR_PERIOD_KEY);
		final long garbageCollectorPeriod = garbageCollectorPeriodStr == null ? DEFAULT_GARBAGE_COLLECTOR_PERIOD
				: Long.valueOf(garbageCollectorPeriodStr);
		
		garbageCollectorTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {	
				garbageCollector();
			}
		}, 0, garbageCollectorPeriod);	
	}
	
	protected void garbageCollector() {
		if (computePlugin != null) {
			Token federationUserToken = getFederationUserToken();
			List<Instance> federationInstances = computePlugin.getInstances(federationUserToken);
			LOGGER.debug("Federation instances=" + federationInstances);
			for (Instance instance : federationInstances) {
				if (!isInstanceBeenUsed(generateGlobalId(instance.getId(), null))
						&& !instancesForRemoteMembers.containsKey(instance.getId())) {
					// this is an orphan instance
					LOGGER.debug("Removing the orphan instance " + instance.getId());
					this.computePlugin.removeInstance(federationUserToken, instance.getId());
				}
			}
		}
	}

	public void updateMembers(List<FederationMember> members) {
		LOGGER.debug("Updating members: " + members);
		if (members == null) {
			throw new IllegalArgumentException();
		}
		FederationMember myself = new FederationMember(getResourcesInfo());
		synchronized (this.members) {
			this.members.clear();
			for (FederationMember member : members) {
				if (member.getResourcesInfo().getId().equals(
						properties.getProperty(ConfigurationConstants.XMPP_JID_KEY))) {
					this.members.add(myself);
				} else {
					this.members.add(member);
				}
			}
		}
	}

	public List<FederationMember> getMembers() {
		List<FederationMember> membersCopy = null;
		synchronized (this.members) {
			membersCopy = new LinkedList<FederationMember>(members);
		}
		boolean containsThis = false;
		for (FederationMember member : membersCopy) {
			if (member.getResourcesInfo().getId().equals(
					properties.getProperty(ConfigurationConstants.XMPP_JID_KEY))) {
				containsThis = true;
				break;
			}
		}
		if (!containsThis) {
			membersCopy.add(new FederationMember(getResourcesInfo()));
		}
		return membersCopy;
	}

	public ResourcesInfo getResourcesInfo() {
		Token token = getFederationUserToken();
		ResourcesInfo resourcesInfo = computePlugin.getResourcesInfo(token);
		resourcesInfo.setId(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));
		return resourcesInfo;
	}

	public String getUser(String accessId) {
		Token token = getTokenFromFederationIdP(accessId);
		if (token == null) {
			return null;
		}
		return token.getUser();
	}

	public List<Request> getRequestsFromUser(String federationAccessToken) {
		String user = getUser(federationAccessToken);
		return requests.getByUser(user);
	}

	public void removeAllRequests(String accessId) {
		String user = getUser(accessId);
		LOGGER.debug("Removing all requests of user: " + user);
		requests.removeByUser(user);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
	}

	public void removeRequest(String accessId, String requestId) {
		LOGGER.debug("Removing requestId: " + requestId);
		checkRequestId(accessId, requestId);
		requests.remove(requestId);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
	}

	private void checkRequestId(String accessId, String requestId) {
		String user = getUser(accessId);
		if (requests.get(user, requestId) == null) {
			LOGGER.debug("User " + user + " does not have requesId " + requestId);
			throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
		}
	}

	public List<Instance> getInstances(String accessId) {
		LOGGER.debug("Getting instances of token " + accessId);
		List<Instance> instances = new ArrayList<Instance>();
		for (Request request : requests.getByUser(getUser(accessId))) {
			String instanceId = request.getInstanceId();
			LOGGER.debug("InstanceId " + instanceId);
			if (instanceId == null) {
				continue;
			}
			try {			
				instances.add(generateInstanceWithGlobalId(request.getInstanceId(), request.getMemberId()));
			} catch (Exception e) {
				LOGGER.warn("Exception thown while getting instance " + instanceId + ".", e);
			}
		}
		return instances;
	}
	
	public Instance generateInstanceWithGlobalId(String instanceId, String memberId) {
		return new Instance(generateGlobalId(instanceId, memberId));
	}

	private String generateGlobalId(String instanceId, String memberId) {
		if (memberId == null) {
			memberId = this.properties.get(ConfigurationConstants.XMPP_JID_KEY).toString();
		}
		return instanceId + Request.SEPARATOR_GLOBAL_ID + memberId;
	}

	public Instance getInstance(String accessId, String instanceId) {
		Request request = getRequestForInstance(accessId, instanceId);
		return getInstance(request);
	}

	private Instance getInstance(Request request) {
		Instance instance = null;
		if (isLocal(request)) {
			LOGGER.debug(request.getInstanceId()
					+ " is local, getting its information in the local cloud.");
			
			if (request.isFulfilledByFederationUser()) {
				instance = this.computePlugin.getInstance(getFederationUserToken(),
						request.getInstanceId());
			} else {
				instance = this.computePlugin.getInstance(request.getLocalToken(),
						request.getInstanceId());
			}

			String sshPublicAdd = getSSHPublicAddress(request.getId());
			if (sshPublicAdd != null) {
				instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, sshPublicAdd);
			}
			Category osCategory = getImageCategory(request.getCategories());
			if (osCategory != null) {
				instance.addResource(
						ResourceRepository.createImageResource(osCategory.getTerm()));
			}

		} else {
			LOGGER.debug(request.getInstanceId() + " is remote, going out to "
					+ request.getMemberId() + " to get its information.");
			instance = getRemoteInstance(request);
		}
		return instance;
	}

	private static Category getImageCategory(List<Category> categories) {
		if (categories == null) {
			return null;
		}
		Category osCategory = null;
		for (Category category : categories) {
			if (category.getScheme().equals(RequestConstants.TEMPLATE_OS_SCHEME)) {
				osCategory = category;
				break;
			}
		}
		return osCategory;
	}

	private String getSSHPublicAddress(String tokenId) {
		
		if (tokenId == null || tokenId.isEmpty()){
			return null;
		}
		
		try {
			String hostAddr = properties.getProperty(ConfigurationConstants.SSH_PRIVATE_HOST_KEY);
			String httpHostPort = properties.getProperty(ConfigurationConstants.SSH_HOST_HTTP_PORT_KEY);
			LOGGER.debug("private host: " + hostAddr);
			LOGGER.debug("private host HTTP port: " + httpHostPort);
			LOGGER.debug("tokenId: " + tokenId);
			LOGGER.debug("token address: http://" + hostAddr + ":" + httpHostPort + "/token/"
					+ tokenId);
			HttpGet httpGet = new HttpGet("http://" + hostAddr + ":" + httpHostPort + "/token/"
					+ tokenId);
			HttpClient client = new DefaultHttpClient();
			HttpResponse response = client.execute(httpGet);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				String sshPort = EntityUtils.toString(response.getEntity());
				String sshPublicHostIP = properties
						.getProperty(ConfigurationConstants.SSH_PUBLIC_HOST_KEY);
				return sshPublicHostIP + ":" + sshPort;
			}
		} catch (Throwable e) {
			LOGGER.warn("", e);
		}
		return null;
	}

	private Instance getRemoteInstance(Request request) {
		return ManagerPacketHelper.getRemoteInstance(request, packetSender);
	}

	public void removeInstances(String accessId) {
		String user = getUser(accessId);
		LOGGER.debug("Removing instances of user: " + user);
		for (Request request : requests.getByUser(user)) {
			String instanceId = request.getInstanceId();
			if (instanceId == null) {
				continue;
			}
	        removeInstance(accessId, normalizeInstanceId(instanceId), request);
		}
	}
	
	public String normalizeInstanceId(String instanceId) {
		if (instanceId.contains(Request.SEPARATOR_GLOBAL_ID)) {
			String[] partsInstanceId = instanceId.split(Request.SEPARATOR_GLOBAL_ID);
			instanceId = partsInstanceId[0];
		}
		return instanceId;
	}

	public void removeInstance(String federationToken, String instanceId) {
		Request request = getRequestForInstance(federationToken, instanceId);
		instanceId = normalizeInstanceId(instanceId);
		removeInstance(federationToken, instanceId, request);
	}

	private void removeInstance(String federationToken, String instanceId, Request request) {
		if (isLocal(request)) {
			if (federationToken.equals(request.getFederationToken().getAccessId())) {
				if (request.isFulfilledByFederationUser()) {
					this.computePlugin.removeInstance(getFederationUserToken(), instanceId);
				} else {
					this.computePlugin.removeInstance(request.getLocalToken(), instanceId);
				}
			} else {
				// If this is a token of the fogbow user 
				this.computePlugin.removeInstance(getTokenFromLocalIdP(federationToken), instanceId);
			}
		} else {
			removeRemoteInstance(request);
		}
		instanceRemoved(request);
	}

	private void instanceRemoved(Request request) {
		request.setInstanceId(null);
		request.setMemberId(null);
		request.setFulfilledByFederationUser(false);		

		if (request.getState().equals(RequestState.DELETED)) {
			requests.exclude(request.getId());
		} else if (isPersistent(request)) {
			LOGGER.debug("Request: " + request + ", setting state to " + RequestState.OPEN);
			request.setState(RequestState.OPEN);
			if (!requestSchedulerTimer.isScheduled()) {
				triggerRequestScheduler();
			}
		} else {
			LOGGER.debug("Request: " + request + ", setting state to " + RequestState.CLOSED);
			request.setState(RequestState.CLOSED);
		}
	}

	private boolean isPersistent(Request request) {
		return request.getAttValue(RequestAttribute.TYPE.getValue()) != null
				&& request.getAttValue(RequestAttribute.TYPE.getValue()).equals(
						RequestType.PERSISTENT.getValue());
	}

	private void removeRemoteInstance(Request request) {
		ManagerPacketHelper.deleteRemoteInstace(request, packetSender);
	}
	
	public Request getRequestForInstance(String federationToken, String instanceId) {
		String user = getUser(federationToken);
		LOGGER.debug("Getting instance " + instanceId + " of user " + user);
		List<Request> userRequests = requests.getAll();
		
		for (Request request : userRequests) {
			if (instanceId.equals(request.getInstanceId() + Request.SEPARATOR_GLOBAL_ID
					+ request.getMemberId())) {
				if (!request.getFederationToken().getUser().equals(user)) {
					throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
				}
				return request;
			}
		}
		throw new OCCIException(ErrorType.NOT_FOUND, ResponseConstants.NOT_FOUND);
	}

	private boolean isLocal(Request request) {
		if (request.getMemberId() != null
				&& request.getMemberId().equals(properties.get(ConfigurationConstants.XMPP_JID_KEY))) {
			return true;
		}
		return false;
	}

	public Request getRequest(String accessId, String requestId) {
		LOGGER.debug("Getting requestId " + requestId);
		checkRequestId(accessId, requestId);
		return requests.get(requestId);
	}

	public FederationMember getFederationMember(String memberId) {
		for (FederationMember member : members) {
			if (member.getResourcesInfo().getId().equals(memberId)) {
				return member;
			}
		}
		if (memberId.equals(properties.get(ConfigurationConstants.XMPP_JID_KEY))) {
			return new FederationMember(getResourcesInfo());
		}
		return null;
	}

	public String createInstanceWithFederationUser(String memberId, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		FederationMember member = null;
		try {
			member = getFederationMember(memberId);
		} catch (Exception e) {
		}

		if (!validator.canDonateTo(member)) {
			return null;
		}
		LOGGER.info("Submiting request with categories: " + categories + " and xOCCIAtt: "
				+ xOCCIAtt + " for remote member: " + memberId);
		String instanceToken = String.valueOf(UUID.randomUUID());
		try {
			String command = UserdataUtils.createBase64Command(instanceToken, 
					properties.getProperty(ConfigurationConstants.SSH_PRIVATE_HOST_KEY),
					properties.getProperty(ConfigurationConstants.SSH_HOST_PORT_KEY),
					properties.getProperty(ConfigurationConstants.SSH_HOST_HTTP_PORT_KEY));
			xOCCIAtt.put(RequestAttribute.USER_DATA_ATT.getValue(), command);
			categories.add(new Category(RequestConstants.USER_DATA_TERM,
					RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS));
		} catch (Exception e) {
			LOGGER.warn("Exception while creating ssh tunnel.", e);
			return null;
		}
		
		Token federationUserToken = getFederationUserToken();
		String localImageId = getLocalImageId(categories, federationUserToken);
		
		List<Category> categoriesWithoutImage = new LinkedList<Category>();
		for (Category category : categories) {
			if (category.getScheme().equals(
					RequestConstants.TEMPLATE_OS_SCHEME)) {
				continue;
			}
			categoriesWithoutImage.add(category);
		}
		
		try {
			String instanceId = computePlugin.requestInstance(federationUserToken, categoriesWithoutImage,
					xOCCIAtt, localImageId);
			
			if (!properties.getProperty("xmpp_jid").equals(memberId)) {
				instancesForRemoteMembers.put(instanceId, new ServedRequest(instanceToken,
						memberId, categories, xOCCIAtt));
				if (!servedRequestMonitoringTimer.isScheduled()) {
					triggerServedRequestMonitoring();
				}
			}						
			return instanceId;
		} catch (OCCIException e) {
			if (e.getStatus().getCode() == HttpStatus.SC_BAD_REQUEST) {
				return null;
			}
			throw e;
		}
	}

	private void triggerServedRequestMonitoring() {
		String servedRequestMonitoringPeriodStr = properties
				.getProperty(ConfigurationConstants.SERVED_REQUEST_MONITORING_PERIOD_KEY);
		final long servedRequestMonitoringPeriod = servedRequestMonitoringPeriodStr == null ? DEFAULT_SERVED_REQUEST_MONITORING_PERIOD
				: Long.valueOf(servedRequestMonitoringPeriodStr);

		servedRequestMonitoringTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {	
				monitorServedRequests();
			}
		}, 0, servedRequestMonitoringPeriod);		
	}

	private String getLocalImageId(List<Category> categories,
			Token federationUserToken) {
		if (imageStoragePlugin == null) {
			return null;
		}
		Category osCategory = getImageCategory(categories);
		String localImageId = null;
		if (osCategory != null) {
			String globalImageId = osCategory.getTerm();
			localImageId = imageStoragePlugin.getLocalId(federationUserToken, globalImageId);
		}
		LOGGER.debug("The " + osCategory.getTerm() + " is related to this localImageId=" + localImageId);
		return localImageId;
	}

	protected Token getFederationUserToken() {
		if (federationUserToken != null
				&& localIdentityPlugin.isValid(federationUserToken.getAccessId())) {
			return federationUserToken;
		}

		federationUserToken = localIdentityPlugin.createFederationUserToken();
		return federationUserToken;
	}

	public Instance getInstanceForRemoteMember(String instanceId) {
		LOGGER.info("Getting instance " + instanceId + " for remote member.");
		try {
			Instance instance = computePlugin.getInstance(getFederationUserToken(), instanceId);
			ServedRequest servedRequest = instancesForRemoteMembers.get(instanceId);
			if (servedRequest != null) {
				String sshPublicAddress = getSSHPublicAddress(servedRequest.getInstanceToken());			
				if (sshPublicAddress != null) {
					instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, sshPublicAddress);
				}
				Category osCategory = getImageCategory(servedRequest.getCategories());
				if (osCategory != null) {
					instance.addResource(
							ResourceRepository.createImageResource(osCategory.getTerm()));
				}
			}
			return instance;
		} catch (OCCIException e) {
			LOGGER.warn("Exception while getting instance " + instanceId + " for remote member.", e);
			if (e.getStatus().getCode() == HttpStatus.SC_NOT_FOUND) {
				return null;
			}
			throw e;
		}
	}

	public void removeInstanceForRemoteMember(String instanceId) {
		LOGGER.info("Removing instance " + instanceId + " for remote member.");
		computePlugin.removeInstance(getFederationUserToken(), instanceId);
		instancesForRemoteMembers.remove(instanceId);
		
		if (instancesForRemoteMembers.isEmpty()) {
			LOGGER.info("There are no served requests. Canceling served request monitoring.");
			servedRequestMonitoringTimer.cancel();
		}
	}

	public Token getTokenFromFederationIdP(String accessId) {
		Token token = federationIdentityPlugin.getToken(accessId);
		if (!authorizationPlugin.isAuthorized(token)) {
			throw new OCCIException(ErrorType.UNAUTHORIZED, ResponseConstants.UNAUTHORIZED_USER);
		}
		return token;
	}

	public List<Request> createRequests(String federationAccessTokenStr, 
			String localAccessTokenStr, List<Category> categories,
			Map<String, String> xOCCIAtt) {
		Token federationToken = getTokenFromFederationIdP(federationAccessTokenStr);
		Token localToken;
		try {
			localToken = getTokenFromLocalIdP(localAccessTokenStr);			
		} catch (Throwable e) {
			LOGGER.warn("Local Access Token \"" + localAccessTokenStr + "\" is not valid.", e);
			LOGGER.debug("Making local access token equals to federation access token.");
			localToken = federationToken;
		}
		LOGGER.debug("Federation User Token: " + federationToken);
		LOGGER.debug("Local User Token: " + localToken);

		Integer instanceCount = Integer.valueOf(xOCCIAtt.get(RequestAttribute.INSTANCE_COUNT
				.getValue()));
		LOGGER.info("Request " + instanceCount + " instances");

		List<Request> currentRequests = new ArrayList<Request>();
		for (int i = 0; i < instanceCount; i++) {
			String requestId = String.valueOf(UUID.randomUUID());
			Request request = new Request(requestId, federationToken, localToken,
					new LinkedList<Category>(categories), new HashMap<String, String>(xOCCIAtt));
			LOGGER.info("Created request: " + request);
			currentRequests.add(request);
			requests.addRequest(federationToken.getUser(), request);
		}
		if (!requestSchedulerTimer.isScheduled()) {
			triggerRequestScheduler();			
		}
		if (!tokenUpdaterTimer.isScheduled()) {
			triggerTokenUpdater();
		}

		return currentRequests;
	}

	private Token getTokenFromLocalIdP(String localAccessTokenStr) {
		return localIdentityPlugin.getToken(localAccessTokenStr);
	}

	protected void triggerInstancesMonitor() {
		String instanceMonitoringPeriodStr = properties
				.getProperty(ConfigurationConstants.INSTANCE_MONITORING_PERIOD_KEY);
		final long instanceMonitoringPeriod = instanceMonitoringPeriodStr == null ? DEFAULT_INSTANCE_MONITORING_PERIOD
				: Long.valueOf(instanceMonitoringPeriodStr);

		instanceMonitoringTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				monitorInstances();
			}
		}, 0, instanceMonitoringPeriod);
	}

	protected void monitorInstances() {
		boolean turnOffTimer = true;
		LOGGER.info("Monitoring instances.");

		for (Request request : requests.getAll()) {
			if (request.getState().in(RequestState.FULFILLED, RequestState.DELETED)) {
				turnOffTimer = false;
				try {
					LOGGER.debug("Monitoring instance of request: " + request);
					getInstance(request);
				} catch (Throwable e) {
					LOGGER.debug("Error while getInstance of " + request.getInstanceId(), e);
					instanceRemoved(requests.get(request.getId()));
				}
			}
		}

		if (turnOffTimer) {
			LOGGER.info("There are no requests.");
			instanceMonitoringTimer.cancel();
		}
	}

	private void triggerTokenUpdater() {
		String tokenUpdatePeriodStr = properties
				.getProperty(ConfigurationConstants.TOKEN_UPDATE_PERIOD_KEY);
		final long tokenUpdatePeriod = tokenUpdatePeriodStr == null ? DEFAULT_TOKEN_UPDATE_PERIOD
				: Long.valueOf(tokenUpdatePeriodStr);

		tokenUpdaterTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkAndUpdateRequestToken(tokenUpdatePeriod);
			}
		}, 0, tokenUpdatePeriod);
	}

	protected void checkAndUpdateRequestToken(long tokenUpdatePeriod) {
		List<Request> allRequests = requests.getAll();
		boolean turnOffTimer = true;

		LOGGER.info("Checking and updating request token.");

		for (Request request : allRequests) {
			try {
				if (request.getState().notIn(RequestState.CLOSED, RequestState.FAILED)) {
					// TODO Close requests that have an expired federation token
					turnOffTimer = false;
					long validInterval = request.getLocalToken().getExpirationDate().getTime()
							- dateUtils.currentTimeMillis();
					LOGGER.debug("Valid interval of requestId " + request.getId() + " is "
							+ validInterval);
					if (validInterval < 2 * tokenUpdatePeriod) {
						Token newToken = localIdentityPlugin.reIssueToken(request.getLocalToken());
						LOGGER.info("Setting new token " + newToken + " on request "
								+ request.getId());
						requests.get(request.getId()).setLocalToken(newToken);
					}
				}
			} catch (Exception e) {
				LOGGER.error("Exception while checking token.", e);
			}
		}

		if (turnOffTimer) {
			LOGGER.info("There are no requests.");
			tokenUpdaterTimer.cancel();
		}
	}

	private void createAsynchronousRemoteInstance(final Request request) {
		FederationMember member = memberPicker.pick(this);
		if (member == null) {
			return;
		}
		final String memberAddress = member.getResourcesInfo().getId();
		request.setMemberId(memberAddress);

		LOGGER.info("Submiting request " + request + " to member " + memberAddress);
		
		asynchronousRequests.put(request.getId(),
				new ForwardedRequest(request, dateUtils.currentTimeMillis()));
		ManagerPacketHelper.asynchronousRemoteRequest(request, memberAddress,
				packetSender, new AsynchronousRequestCallback() {
					
					@Override
					public void success(String instanceId) {
						LOGGER.debug("The request " + request + " forwarded to " + memberAddress
								+ " gets instance " + instanceId);
						if (asynchronousRequests.remove(request.getId()) == null) {
							return;
						}
						if (instanceId == null) {
							return;
						}
						
						request.setState(RequestState.FULFILLED);
						request.setInstanceId(instanceId);
						if (!instanceMonitoringTimer.isScheduled()) {
							triggerInstancesMonitor();
						}
					}
					
					@Override
					public void error(Throwable t) {
						LOGGER.debug("The request " + request + " forwarded to " + memberAddress
								+ " gets error ", t);
						asynchronousRequests.remove(request.getId());
					}
				});
			
	}
	
	protected boolean isRequestForwardedtoRemoteMember(String requestId) {
		return asynchronousRequests.containsKey(requestId);
	}
	
	private boolean createLocalInstance(Request request) {
		request.setMemberId(this.properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));
		String instanceId = null;
		LOGGER.info("Submiting local request " + request);		
		
		try {			
			try {
				String command = UserdataUtils.createBase64Command(request.getId(),
						properties.getProperty(ConfigurationConstants.SSH_PRIVATE_HOST_KEY),
						properties.getProperty(ConfigurationConstants.SSH_HOST_PORT_KEY),
						properties.getProperty(ConfigurationConstants.SSH_HOST_HTTP_PORT_KEY));
				request.putAttValue(RequestAttribute.USER_DATA_ATT.getValue(), command);
				request.addCategory(new Category(RequestConstants.USER_DATA_TERM,
						RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS));
			} catch (Exception e) {
				LOGGER.warn("Exception while creating userdata.", e);
				request.setState(RequestState.FAILED);
				return false;
			}	
			
			String localImageId = getLocalImageId(request.getCategories(), 
					request.getLocalToken());
			List<Category> categories = new LinkedList<Category>();
			for (Category category : request.getCategories()) {
				if (category.getScheme().equals(
						RequestConstants.TEMPLATE_OS_SCHEME)) {
					continue;
				}
				categories.add(category);
			}
			
			instanceId = computePlugin.requestInstance(request.getLocalToken(),
					categories, request.getxOCCIAtt(), localImageId);
		} catch (OCCIException e) {
			int statusCode = e.getStatus().getCode();
			if (statusCode == HttpStatus.SC_INSUFFICIENT_SPACE_ON_RESOURCE) {
				LOGGER.warn("Request failed locally for quota exceeded.", e);
				return false;
			} else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
				LOGGER.warn("Request failed locally for user unauthorized.", e);
				return false;
			} else if (statusCode == HttpStatus.SC_BAD_REQUEST) {
				LOGGER.warn("Request failed locally for image not found.", e);
				return false;
			} else {
				// TODO Think this through...
				request.setState(RequestState.FAILED);
				LOGGER.warn("Request failed locally for an unknown reason.", e);
				return true;				
			}
		}

		request.setInstanceId(instanceId);
		request.setState(RequestState.FULFILLED);
		LOGGER.debug("Fulfilled Request: " + request);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
		return true;
	}

	private void triggerRequestScheduler() {
		String schedulerPeriodStr = properties
				.getProperty(ConfigurationConstants.SCHEDULER_PERIOD_KEY);
		long schedulerPeriod = schedulerPeriodStr == null ? DEFAULT_SCHEDULER_PERIOD : Long
				.valueOf(schedulerPeriodStr);
		requestSchedulerTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				checkAndSubmitOpenRequests();
			}
		}, 0, schedulerPeriod);
	}

	protected void checkAndSubmitOpenRequests() {
		boolean allFulfilled = true;
		LOGGER.debug("Checking and submiting requests.");

		// removing requests that reach timeout
		removeRequestsThatReachTimeout();
		
		List<Request> openRequests = requests.get(RequestState.OPEN);
		for (Request request : openRequests) {
			if (isRequestForwardedtoRemoteMember(request.getId())) {
				LOGGER.debug("The request " + request.getId()
						+ " was forwarded to remote member and is not fulfilled yet.");
				continue;
			}
			LOGGER.debug(request.getId() + " considering for scheduling.");
			Map<String, String> xOCCIAtt = request.getxOCCIAtt();
			if (request.isIntoValidPeriod()) {
				for (String keyAttributes : RequestAttribute.getValues()) {
					xOCCIAtt.remove(keyAttributes);
				}
				boolean isFulfilled = createLocalInstance(request)
						|| createLocalInstanceWithFederationUser(request);
				if (!isFulfilled) {
					createAsynchronousRemoteInstance(request);
				}
				allFulfilled &= isFulfilled;
				
			} else if (request.isExpired()) {
				request.setState(RequestState.CLOSED);
			} else {
				allFulfilled = false;
			}
		}
		if (allFulfilled) {
			LOGGER.info("All requests fulfilled.");
		}
	}

	protected void monitorServedRequests() {
		LOGGER.info("Monitoring served requests.");
		LOGGER.debug("Current served requests=" + instancesForRemoteMembers);
				
		Set<String> instanceIds = instancesForRemoteMembers.keySet();
		for (String instanceId : instanceIds) {
			ServedRequest servedRequest = instancesForRemoteMembers.get(instanceId);
			System.out.println("instanceId= " + instanceId);
			System.out.println("servedRequest= " + servedRequest);
			if (!isInstanceBeenUsedByRemoteMember(instanceId, servedRequest)){
				LOGGER.debug("The instance " + instanceId + " is not been used anymore by "
						+ servedRequest.getMemberId() + " and will be removed.");
				removeInstanceForRemoteMember(instanceId);
			}
		}
	}

	private boolean isInstanceBeenUsedByRemoteMember(String instanceId, ServedRequest servedRequest) {
		try{
			ManagerPacketHelper.checkIfInstanceIsBeenUsedByRemoteMember(
					generateGlobalId(instanceId, null), servedRequest.getMemberId(), packetSender);
			return true;
		} catch (OCCIException e) {
			return false;
		}
	}

	protected void removeRequestsThatReachTimeout() {
		Collection<ForwardedRequest> forwardedRequests = asynchronousRequests.values();
		for (ForwardedRequest forwardedRequest : forwardedRequests) {
			if (timoutReached(forwardedRequest.getTimeStamp())){
				LOGGER.debug("The forwarded request " + forwardedRequest.getRequest().getId()
						+ " reached timeout and is been removed from asynchronousRequests list.");
				asynchronousRequests.remove(forwardedRequest.getRequest().getId());
			}
		}
	}

	private boolean timoutReached(long timeStamp) {
		long nowMilli = dateUtils.currentTimeMillis();
		Date now = new Date(nowMilli);
		
		Calendar c = Calendar.getInstance();
		c.setTime(new Date(timeStamp)); 
		c.add(Calendar.MILLISECOND, (int) DEFAULT_SCHEDULER_PERIOD); 
		return now.after(c.getTime());
	}

	private boolean createLocalInstanceWithFederationUser(Request request) {
		request.setMemberId(properties.getProperty(ConfigurationConstants.XMPP_JID_KEY));

		LOGGER.info("Submiting request " + request + " with federation user locally.");

		String remoteInstanceId = null;
		try {
			remoteInstanceId = createInstanceWithFederationUser(properties.getProperty("xmpp_jid"),
					request.getCategories(), request.getxOCCIAtt());
		} catch (Exception e) {
			LOGGER.info("Could not create instance with federation user locally." + e);
		}

		if (remoteInstanceId == null) {
			return false;
		}

		request.setState(RequestState.FULFILLED);
		request.setInstanceId(remoteInstanceId);
		request.setFulfilledByFederationUser(true);
		if (!instanceMonitoringTimer.isScheduled()) {
			triggerInstancesMonitor();
		}
		return true;
	}

	public void setPacketSender(AsyncPacketSender packetSender) {
		this.packetSender = packetSender;
	}

	public void setRequests(RequestRepository requests) {
		this.requests = requests;
	}

	public Token getToken(Map<String, String> attributesToken) {
		return localIdentityPlugin.createToken(attributesToken);
	}

	public Properties getProperties() {
		return properties;
	}

	public void setDateUtils(DateUtils dateUtils) {
		this.dateUtils = dateUtils;
	}

	public FederationMemberValidator getValidator() {
		return validator;
	}

	public void setValidator(FederationMemberValidator validator) {
		this.validator = validator;
	}

	protected Map<String, ServedRequest> getInstancesForRemoteMember() {
		return instancesForRemoteMembers;
	}
	
	public List<Resource> getAllResouces(String accessId) {
		Token userToken = getTokenFromFederationIdP(accessId);
		LOGGER.debug("User Token: " + userToken);
		return ResourceRepository.getInstance().getAll();
	}

	public void bypass(org.restlet.Request request, Response response) {
		LOGGER.debug("Bypassing request: " + request);
		computePlugin.bypass(request, response);
	}

	public String getAuthenticationURI() {
		return localIdentityPlugin.getAuthenticationURI();
	}
	
	public Integer getMaxWhoIsAliveManagerCount() {
		String max = properties.getProperty(PROP_MAX_WHOISALIVE_MANAGER_COUNT);
		if (max == null) {
			return (Integer) null;
		}
		return Integer.parseInt(max);
	}

	public List<Instance> getInstancesFullInfo(String authToken) {		
		List<Request> requestsFromUser = getRequestsFromUser(authToken);
		List<Instance> allFullInstances = new ArrayList<Instance>();
		LOGGER.debug("Getting all instances and your information.");
		for (Request request : requestsFromUser) {
			Instance instance = null;
			if (isLocal(request)) {
				LOGGER.debug(request.getInstanceId()
						+ " is local, getting its information in the local cloud.");
				if (request.isFulfilledByFederationUser()) {
					instance = this.computePlugin.getInstance(getFederationUserToken(),
							request.getInstanceId());
				} else {
					instance = this.computePlugin.getInstance(request.getLocalToken(),
							request.getInstanceId());
				}

				String sshPublicAdd = getSSHPublicAddress(request.getId());
				if (sshPublicAdd != null) {
					instance.addAttribute(Instance.SSH_PUBLIC_ADDRESS_ATT, sshPublicAdd);
				}
				Category osCategory = getImageCategory(request.getCategories());
				if (osCategory != null) {
					instance.addResource(
							ResourceRepository.createImageResource(osCategory.getTerm()));
				}
			} else {
				LOGGER.debug(request.getInstanceId() + " is remote, going out to "
						+ request.getMemberId() + " to get its information.");
				instance = getRemoteInstance(request);
			}
			allFullInstances.add(instance);
		}
		return allFullInstances;
	}

	public boolean isInstanceBeenUsed(String instanceId) {
		LOGGER.debug("Checking if instance " + instanceId + " is been used yet.");
		for (Request request : requests.getAll()) {
			if (request.getState().in(RequestState.FULFILLED, RequestState.DELETED)) {
				String reqInstanceId = generateGlobalId(request.getInstanceId(), request.getMemberId());
				if (reqInstanceId != null && reqInstanceId.equals(instanceId)) {
					return true;
				}
			}
		}
		return false;
	}
}

class ForwardedRequest {
	
	private Request request;
	private long timeStamp;
	
	public ForwardedRequest(Request request, long timeStamp) {
		this.request = request;
		this.timeStamp = timeStamp;
	}
	
	public Request getRequest() {
		return request;
	}
	
	public long getTimeStamp() {
		return timeStamp;
	}
}