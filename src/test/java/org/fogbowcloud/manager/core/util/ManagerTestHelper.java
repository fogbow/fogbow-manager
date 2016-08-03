package org.fogbowcloud.manager.core.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.CurrentThreadExecutorService;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.fogbowcloud.manager.core.plugins.AccountingPlugin;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.FederationMemberPickerPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.StoragePlugin;
import org.fogbowcloud.manager.core.plugins.capacitycontroller.satisfactiondriven.SatisfactionDrivenCapacityControllerPlugin;
import org.fogbowcloud.manager.core.plugins.identity.openstack.KeystoneIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.memberauthorization.DefaultMemberAuthorizationPlugin;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.ErrorType;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.Order;
import org.fogbowcloud.manager.xmpp.AsyncPacketSender;
import org.fogbowcloud.manager.xmpp.ManagerXmppComponent;
import org.jamppa.client.XMPPClient;
import org.jamppa.component.PacketCallback;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class ManagerTestHelper extends DefaultDataTestHelper {

	public static final String MANAGER_TEST_JID = "manager.test.com";
	public static final String VALUE_FLAVOR_LARGE = "{cpu=4,mem=8}";
	public  static final String VALUE_FLAVOR_MEDIUM = "{cpu=2,mem=4}";
	public static final String VALUE_FLAVOR_SMALL = "{cpu=1,mem=1}";
	public static final int MAX_WHOISALIVE_MANAGER_COUNT = 100;
	private ManagerXmppComponent managerXmppComponent;
	private ComputePlugin computePlugin;
	private StoragePlugin storagePlugin;
	private IdentityPlugin identityPlugin;
	private MapperPlugin mapperPlugin;
	private IdentityPlugin federationIdentityPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private BenchmarkingPlugin benchmarkingPlugin;
	private AccountingPlugin computeAccountingPlugin;
	private AccountingPlugin storageAccountingPlugin;
	private FederationMemberPickerPlugin memberPickerPlugin;
	private Token defaultFederationToken;
	private Map<String, Map<String, String>> defaultFederationAllUsersCrendetials;
	private Map<String, String> defaultFederationUserCrendetials = new HashMap<String, String>();
	private FakeXMPPServer fakeServer = new FakeXMPPServer();
	private ScheduledExecutorService executorService;
	
	private final String CPU_IDLE = "1";
	private final String CPU_INUSE = "2";
	private final String MEM_IDLE = "1000";
	private final String MEM_INUSE = "2000";
	private final String INSTANCES_IDLE = "5";
	private final String INSTANCES_INUSE = "10";

	public ManagerTestHelper() {
		Map<String, String> tokenAttributes = new HashMap<String, String>();
		tokenAttributes.put(KeystoneIdentityPlugin.TENANT_ID, "tenantId_r4fci3qhbcy3b");
		
		this.defaultFederationToken = new Token(FED_ACCESS_TOKEN_ID, FED_USER_NAME, new Date(),
				new HashMap<String, String>());
		
		this.defaultFederationAllUsersCrendetials = new HashMap<String, Map<String,String>>();
		this.defaultFederationAllUsersCrendetials.put("one", defaultFederationUserCrendetials);
	}

	public ResourcesInfo getResources() throws CertificateException, IOException {
		ResourcesInfo resources = new ResourcesInfo("abc", CPU_IDLE, CPU_INUSE, MEM_IDLE, 
				MEM_INUSE, INSTANCES_IDLE, INSTANCES_INUSE);
		return resources;
	}

	public IQ createWhoIsAliveResponse(ArrayList<FederationMember> aliveIds, IQ iq)
			throws CertificateException, IOException {
		IQ resultIQ = IQ.createResultIQ(iq);
		Element queryElement = resultIQ.getElement().addElement("query", WHOISALIVE_NAMESPACE);
		for (FederationMember rendezvousItem : aliveIds) {
			Element itemEl = queryElement.addElement("item");
			itemEl.addAttribute("id", rendezvousItem.getId());
		}
		return resultIQ;
	}

	public XMPPClient createXMPPClient() throws XMPPException {

		XMPPClient xmppClient = Mockito.spy(new XMPPClient(CLIENT_ADRESS, CLIENT_PASS, SERVER_HOST,
				SERVER_CLIENT_PORT));
		fakeServer.connect(xmppClient);
		xmppClient.process(false);

		return xmppClient;
	}

	public AsyncPacketSender createPacketSender() throws XMPPException {
		final XMPPClient xmppClient = createXMPPClient();
		AsyncPacketSender sender = new AsyncPacketSender() {
			@Override
			public Packet syncSendPacket(Packet packet) {
				PacketFilter responseFilter = new PacketIDFilter(packet.getID());
				PacketCollector response = xmppClient.getConnection().createPacketCollector(
						responseFilter);
				xmppClient.send(packet);
				Packet result = response.nextResult(50000);
				response.cancel();
				return result;
			}

			@Override
			public void sendPacket(Packet packet) {
				xmppClient.send(packet);
			}

			@Override
			public void addPacketCallback(final Packet request,
					final PacketCallback packetCallback) {
				xmppClient.getConnection().addPacketListener(new PacketListener() {
					
					@Override
					public void processPacket(Packet packet) {
						packetCallback.handle(packet);
					}
				}, new PacketFilter() {
					
					@Override
					public boolean accept(Packet reply) {
						return request.getID().equals(reply.getID());
					}
				});
			}
		};
		return sender;
	}

	public ComputePlugin getComputePlugin() {
		return computePlugin;
	}

	public IdentityPlugin getIdentityPlugin() {
		return identityPlugin;
	}
	
	public StoragePlugin getStoragePlugin() {
		return storagePlugin;
	}
	
	public IdentityPlugin getFederationIdentityPlugin() {
		return federationIdentityPlugin;
	}
	
	public AuthorizationPlugin getAuthorizationPlugin() {
		return authorizationPlugin;
	}
	
	public AccountingPlugin getAccountingPlugin() {
		return computeAccountingPlugin;
	}
	
	public MapperPlugin getMapperPlugin() {
		return mapperPlugin;
	}

	public ManagerXmppComponent initializeXMPPManagerComponent(boolean init) throws Exception {

		Properties properties = new Properties();
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_NAME_KEY, "fogbow");
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_PASS_KEY, "fogbow");
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_TENANT_NAME_KEY, "fogbow");
		properties.put(ConfigurationConstants.XMPP_JID_KEY, MANAGER_TEST_JID);
		properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		properties.put("max_whoisalive_manager_count", MAX_WHOISALIVE_MANAGER_COUNT);
		
		ManagerController managerFacade = Mockito.spy(new ManagerController(properties));
		return initializeXMPPManagerComponent(init, managerFacade);
	}
	
	public ManagerXmppComponent initializeXMPPManagerComponentFacadeMocki(boolean init, ManagerController managerFacade) throws Exception {

		Properties properties = new Properties();
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_NAME_KEY, "fogbow");
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_PASS_KEY, "fogbow");
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_TENANT_NAME_KEY, "fogbow");
		properties.put(ConfigurationConstants.XMPP_JID_KEY, MANAGER_TEST_JID);
		properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		properties.put("max_whoisalive_manager_count", MAX_WHOISALIVE_MANAGER_COUNT);
		
		return initializeXMPPManagerComponent(init, managerFacade);
	}
	
	@SuppressWarnings("unchecked")
	public ManagerXmppComponent initializeXMPPManagerComponent(boolean init, ManagerController managerFacade) throws Exception {
		
		this.storagePlugin = Mockito.mock(StoragePlugin.class);
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		this.benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		this.computeAccountingPlugin = Mockito.mock(AccountingPlugin.class);
		this.mapperPlugin = Mockito.mock(MapperPlugin.class);
		this.authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		
		Mockito.when(computePlugin.getInstances(Mockito.any(Token.class))).thenReturn(
				new ArrayList<Instance>());
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				getResources());
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(
				this.defaultFederationAllUsersCrendetials);
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(
				defaultFederationToken);		
		
		// mocking benchmark executor
		ExecutorService benchmarkExecutor = new CurrentThreadExecutorService();
				
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setComputeAccountingPlugin(computeAccountingPlugin);
		managerFacade.setLocalIdentityPlugin(identityPlugin);
		managerFacade.setBenchmarkExecutor(benchmarkExecutor);
		managerFacade.setBenchmarkingPlugin(benchmarkingPlugin);
		managerFacade.setFederationIdentityPlugin(federationIdentityPlugin);
		managerFacade.setAuthorizationPlugin(authorizationPlugin);
		managerFacade.setValidator(new DefaultMemberAuthorizationPlugin(null));
		managerFacade.setLocalCredentailsPlugin(mapperPlugin);
		managerFacade.setStoragePlugin(storagePlugin);
		managerFacade.setCapacityControllerPlugin(new SatisfactionDrivenCapacityControllerPlugin());
				
		managerXmppComponent = Mockito.spy(new ManagerXmppComponent(LOCAL_MANAGER_COMPONENT_URL,
				MANAGER_COMPONENT_PASS, SERVER_HOST, SERVER_COMPONENT_PORT, managerFacade));
				
		managerXmppComponent.setDescription("Manager Component");
		managerXmppComponent.setName("Manager");
		managerXmppComponent.setRendezvousAddress(CLIENT_ADRESS + SMACK_ENDING);
		fakeServer.connect(managerXmppComponent);
		managerXmppComponent.process();
		if (init) {
			managerXmppComponent.init();
		}
		managerFacade.setPacketSender(managerXmppComponent);
		return managerXmppComponent;
	}

	@SuppressWarnings("unchecked")
	public ManagerXmppComponent initializeLocalXMPPManagerComponent() throws Exception {

		this.computePlugin = Mockito.mock(ComputePlugin.class);
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.mapperPlugin = Mockito.mock(MapperPlugin.class);

		Properties properties = new Properties();
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_NAME_KEY, "fogbow");
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_PASS_KEY, "fogbow");
		properties.put(ConfigurationConstants.XMPP_JID_KEY, MANAGER_TEST_JID);

		Mockito.when(computePlugin.getInstances(Mockito.any(Token.class))).thenReturn(
				new ArrayList<Instance>());
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				getResources());
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(this.defaultFederationAllUsersCrendetials);
		Mockito.when(identityPlugin.createToken(Mockito.anyMap())).thenReturn(defaultFederationToken);

		ManagerController managerFacade = new ManagerController(properties);
		managerFacade.setComputePlugin(computePlugin);
		managerFacade.setLocalIdentityPlugin(identityPlugin);
		managerFacade.setFederationIdentityPlugin(identityPlugin);
		managerFacade.setLocalCredentailsPlugin(mapperPlugin);
		managerFacade.setValidator(new DefaultMemberAuthorizationPlugin(null));

		managerXmppComponent = Mockito.spy(new ManagerXmppComponent(LOCAL_MANAGER_COMPONENT_URL,
				MANAGER_COMPONENT_PASS, SERVER_HOST, SERVER_COMPONENT_PORT, managerFacade));
		
		managerXmppComponent.setDescription("Manager Component");
		managerXmppComponent.setName("Manager");
		managerXmppComponent.setRendezvousAddress(CLIENT_ADRESS + SMACK_ENDING);
		fakeServer.connect(managerXmppComponent);
		managerXmppComponent.process();
		return managerXmppComponent;
	}

	public IQ CreateImAliveResponse(IQ iq) {
		IQ response = IQ.createResultIQ(iq);
		return response;
	}

	public void shutdown() throws ComponentException {
		fakeServer.disconnect(managerXmppComponent.getJID().toBareJID());
	}

	@SuppressWarnings("unchecked")
	public List<FederationMember> getItemsFromIQ(Packet response) throws CertificateException,
			IOException {
		Element queryElement = response.getElement().element("query");
		Iterator<Element> itemIterator = queryElement.elementIterator("item");
		ArrayList<FederationMember> aliveItems = new ArrayList<FederationMember>();

		while (itemIterator.hasNext()) {
			Element itemEl = itemIterator.next();
			Attribute id = itemEl.attribute("id");
			Element statusEl = itemEl.element("status");
			String cpuIdle = statusEl.element("cpu-idle").getText();
			String cpuInUse = statusEl.element("cpu-inuse").getText();
			String memIdle = statusEl.element("mem-idle").getText();
			String memInUse = statusEl.element("mem-inuse").getText();
			String instancesIdle = statusEl.element("instances-idle").getText();
			String instancesInUse = statusEl.element("instances-inuse").getText();

			ResourcesInfo resources = new ResourcesInfo(id.getValue(), cpuIdle, cpuInUse, memIdle,
					memInUse, instancesIdle, instancesInUse);
			FederationMember item = new FederationMember(resources);
			aliveItems.add(item);
		}
		return aliveItems;
	}

	public Properties getProperties() throws IOException {
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(CONFIG_PATH);
		properties.load(input);
		return properties;
	}

	public Properties getProperties(String path) throws IOException {
		Properties properties = new Properties();
		FileInputStream input = new FileInputStream(path);
		properties.load(input);
		return properties;
	}

	public ManagerController createDefaultManagerController() {
		return createDefaultManagerController(null);
	}

	@SuppressWarnings("unchecked")
	public ManagerController createDefaultManagerController(Map<String, String> extraProperties) {
		Properties properties = new Properties();
		properties.put(ConfigurationConstants.XMPP_JID_KEY,
				DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_NAME_KEY,
				DefaultDataTestHelper.FED_USER_NAME);
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_PASS_KEY,
				DefaultDataTestHelper.FED_USER_PASS);
		properties.put(KeystoneIdentityPlugin.FEDERATION_USER_TENANT_NAME_KEY,
				DefaultDataTestHelper.TENANT_NAME);
		properties.put(ConfigurationConstants.SCHEDULER_PERIOD_KEY,
				DefaultDataTestHelper.SCHEDULER_PERIOD.toString());
		properties.put(ConfigurationConstants.INSTANCE_MONITORING_PERIOD_KEY,
				Long.toString(DefaultDataTestHelper.LONG_TIME));
		properties.put(ConfigurationConstants.TOKEN_HOST_PRIVATE_ADDRESS_KEY,
				DefaultDataTestHelper.SERVER_HOST);
		properties.put(ConfigurationConstants.TOKEN_HOST_HTTP_PORT_KEY,
				String.valueOf(DefaultDataTestHelper.TOKEN_SERVER_HTTP_PORT));
		properties.put(ConfigurationConstants.PREFIX_FLAVORS + "small", VALUE_FLAVOR_SMALL);
		properties.put(ConfigurationConstants.PREFIX_FLAVORS + "medium", VALUE_FLAVOR_MEDIUM);
		properties.put(ConfigurationConstants.PREFIX_FLAVORS + "large", VALUE_FLAVOR_LARGE);
		properties.put(ConfigurationConstants.SERVED_ORDER_MONITORING_PERIOD_KEY,
				String.valueOf(DefaultDataTestHelper.SERVED_ORDER_MONITORING_PERIOD));
		properties.put(ConfigurationConstants.GREEN_SITTER_JID, DefaultDataTestHelper.GREEN_SITTER_JID);
		properties.put(ConfigurationConstants.ADMIN_USERS, DefaultDataTestHelper.FED_USER_NAME + ";" + "admin_user");
		
		if (extraProperties != null) {
			for (Entry<String, String> entry : extraProperties.entrySet()) {
				properties.put(entry.getKey(), entry.getValue());
			}
		}
		
		this.executorService = Mockito.mock(ScheduledExecutorService.class);
		ManagerController managerController = new ManagerController(properties,
				executorService);

		// mocking compute
		computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.when(
				computePlugin.requestInstance(Mockito.any(Token.class), Mockito.any(List.class),
						Mockito.any(Map.class), Mockito.anyString())).thenThrow(
				new OCCIException(ErrorType.QUOTA_EXCEEDED,
						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES));
		Mockito.when(computePlugin.getResourcesInfo(Mockito.any(Token.class))).thenReturn(
				new ResourcesInfo(LOCAL_MANAGER_COMPONENT_URL, "", "", "", "", "", ""));
		Mockito.when(computePlugin.getInstances(Mockito.any(Token.class))).thenReturn(
				new ArrayList<Instance>());
		
		// mocking identity
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		federationIdentityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(federationIdentityPlugin.getToken(FED_ACCESS_TOKEN_ID)).thenReturn(
				defaultFederationToken);
		Mockito.when(identityPlugin.getToken(FED_ACCESS_TOKEN_ID)).thenReturn(
				defaultFederationToken);

		// mocking FUC
		this.mapperPlugin = Mockito.mock(MapperPlugin.class);
		Mockito.when(mapperPlugin.getAllLocalCredentials()).thenReturn(
				this.defaultFederationAllUsersCrendetials);
		Mockito.when(
				mapperPlugin.getLocalCredentials(Mockito.any(Order.class)))
				.thenReturn(this.defaultFederationUserCrendetials);
		Mockito.when(identityPlugin.createToken(this.defaultFederationUserCrendetials)).thenReturn(defaultFederationToken);
		
		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		
		computeAccountingPlugin = Mockito.mock(AccountingPlugin.class);
		storageAccountingPlugin = Mockito.mock(AccountingPlugin.class);
		
		memberPickerPlugin = Mockito.mock(FederationMemberPickerPlugin.class);
		Mockito.when(memberPickerPlugin.pick(Mockito.any(List.class))).thenReturn(
				new FederationMember(new ResourcesInfo(
						DefaultDataTestHelper.REMOTE_MANAGER_COMPONENT_URL, "", "", "", "", "")));
		
		// mocking benchmark executor
		ExecutorService benchmarkExecutor = new CurrentThreadExecutorService();
				
		managerController.setAuthorizationPlugin(authorizationPlugin);
		managerController.setLocalIdentityPlugin(identityPlugin);
		managerController.setFederationIdentityPlugin(federationIdentityPlugin);
		managerController.setLocalCredentailsPlugin(mapperPlugin);
		managerController.setComputePlugin(computePlugin);
		managerController.setBenchmarkingPlugin(benchmarkingPlugin);
		managerController.setComputeAccountingPlugin(computeAccountingPlugin);
		managerController.setStorageAccountingPlugin(storageAccountingPlugin);
		managerController.setValidator(new DefaultMemberAuthorizationPlugin(null));
		managerController.setMemberPickerPlugin(memberPickerPlugin);
		managerController.setBenchmarkExecutor(benchmarkExecutor);
		
		return managerController;
	}

	public void useSameThreadExecutor() {
		Mockito.when(executorService.scheduleWithFixedDelay(
				Mockito.any(Runnable.class), Mockito.anyLong(), 
				Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenAnswer(new Answer<Future<?>>() {
			@Override
			public Future<?> answer(InvocationOnMock invocation)
					throws Throwable {
				Runnable runnable = (Runnable) invocation.getArguments()[0];
				runnable.run();
				return null;
			}
		});
	}

	public Token getDefaultFederationToken() {
		return defaultFederationToken;
	}
	
	public MapperPlugin getLocalCredentialsPlugin(){
		return mapperPlugin;
	}

}
