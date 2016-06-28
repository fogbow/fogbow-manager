package org.fogbowcloud.manager.core.plugins.network.openstack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.plugins.compute.openstack.OpenStackConfigurationConstants;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.instance.Instance;
import org.fogbowcloud.manager.occi.model.OCCIException;
import org.fogbowcloud.manager.occi.model.ResponseConstants;
import org.fogbowcloud.manager.occi.model.Token;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestOpenStackV2NetworkPlugin {

	private static final String DEFAULT_GATEWAY_INFO = "000000-gateway_info";
	private static final String DEFAULT_TENANT_ID = "tenantId";
	private static final String DEFAULT_NETWORK_URL = "http://localhost:0000";
	
	private OpenStackV2NetworkPlugin openStackV2NetworkPlugin;
	private Token defaultToken;
	private HttpClient client;
	
	@Before
	public void setUp() {
		Properties properties = new Properties();
		properties.put(OpenStackV2NetworkPlugin.KEY_EXTERNAL_GATEWAY_INFO, DEFAULT_GATEWAY_INFO);
		properties.put(OpenStackConfigurationConstants.NETWORK_NOVAV2_URL_KEY, DEFAULT_NETWORK_URL);
		this.openStackV2NetworkPlugin = new OpenStackV2NetworkPlugin(properties);
	
		this.client = Mockito.mock(HttpClient.class);
		this.openStackV2NetworkPlugin.setClient(this.client);
		
		Map<String, String> attributes = new HashMap<String, String>();
		attributes.put(OpenStackV2NetworkPlugin.TENANT_ID, DEFAULT_TENANT_ID);
		this.defaultToken = new Token("accessId", "user", new Date(), attributes);
	}
	
	@After
	public void validate() {
	    Mockito.validateMockitoUsage();
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstanceWithoutTenantId() {
		Token token = new Token("accessId", "user", new Date(), new HashMap<String, String>());
		openStackV2NetworkPlugin.requestInstance(token, null, null);
	}
	
	@Test
	public void testGenerateJsonEntityToCreateRouter() throws JSONException {
		JSONObject generateJsonEntityToCreateRouter = openStackV2NetworkPlugin
				.generateJsonEntityToCreateRouter();

		JSONObject routerJsonObject = generateJsonEntityToCreateRouter
				.optJSONObject(OpenStackV2NetworkPlugin.KEY_JSON_ROUTER);
		Assert.assertEquals(DEFAULT_GATEWAY_INFO, routerJsonObject.optJSONObject(
				OpenStackV2NetworkPlugin.KEY_EXTERNAL_GATEWAY_INFO)
				.optString(OpenStackV2NetworkPlugin.KEY_NETWORK_ID));
		Assert.assertTrue(routerJsonObject.optString(OpenStackV2NetworkPlugin.KEY_NAME)
				.contains(OpenStackV2NetworkPlugin.DEFAULT_ROUTER_NAME));		
	}
	
	@Test
	public void testGenerateJsonEntityToCreateNetwork() throws JSONException {
		JSONObject generateJsonEntityToCreateNetwork = openStackV2NetworkPlugin
				.generateJsonEntityToCreateNetwork(DEFAULT_TENANT_ID);
		
		JSONObject networkJsonObject = generateJsonEntityToCreateNetwork
				.optJSONObject(OpenStackV2NetworkPlugin.KEY_JSON_NETWORK);
		Assert.assertEquals(DEFAULT_TENANT_ID, networkJsonObject.optString(OpenStackV2NetworkPlugin.KEY_TENANT_ID));
		Assert.assertTrue(networkJsonObject.optString(OpenStackV2NetworkPlugin.KEY_NAME)
				.contains(OpenStackV2NetworkPlugin.DEFAULT_NETWORK_NAME));
	}
	
	@Test
	public void testSetDnsList() {
		Properties properties = new Properties();
		String dnsOne = "one";
		String dnsTwo = "Two";
		properties.put(OpenStackV2NetworkPlugin.KEY_DNS_NAMESERVERS, dnsOne + "," + dnsTwo);
		openStackV2NetworkPlugin = new OpenStackV2NetworkPlugin(properties);
		Assert.assertEquals(2, openStackV2NetworkPlugin.getDnsList().length);
		Assert.assertEquals(dnsOne, openStackV2NetworkPlugin.getDnsList()[0]);
		Assert.assertEquals(dnsTwo, openStackV2NetworkPlugin.getDnsList()[1]);
	}
	
	@Test
	public void testGenerateJsonEntityToCreateSubnet() throws JSONException {
		Properties properties = new Properties();
		String dnsOne = "one";
		String dnsTwo = "Two";
		properties.put(OpenStackV2NetworkPlugin.KEY_DNS_NAMESERVERS, dnsOne + "," + dnsTwo);
		openStackV2NetworkPlugin = new OpenStackV2NetworkPlugin(properties);
		
		String networkId = "networkId";
		String address = "10.10.10.10/24";
		String gateway = "10.10.10.11";
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ADDRESS, address);
		xOCCIAtt.put(OCCIConstants.NETWORK_GATEWAY, gateway);
		xOCCIAtt.put(OCCIConstants.NETWORK_ALLOCATION,
				OCCIConstants.NetworkAllocation.DYNAMIC.getValue());
		JSONObject generateJsonEntityToCreateSubnet = openStackV2NetworkPlugin
				.generateJsonEntityToCreateSubnet(networkId, DEFAULT_TENANT_ID, xOCCIAtt);		
		
		JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
				.optJSONObject(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET);
		Assert.assertEquals(DEFAULT_TENANT_ID, subnetJsonObject
				.optString(OpenStackV2NetworkPlugin.KEY_TENANT_ID));
		Assert.assertTrue(subnetJsonObject.optString(OpenStackV2NetworkPlugin.KEY_NAME)
				.contains(OpenStackV2NetworkPlugin.DEFAULT_SUBNET_NAME));
		Assert.assertEquals(networkId, subnetJsonObject
				.optString(OpenStackV2NetworkPlugin.KEY_NETWORK_ID));
		Assert.assertEquals(address,
				subnetJsonObject.optString(OpenStackV2NetworkPlugin.KEY_CIRD));
		Assert.assertEquals(gateway,
				subnetJsonObject.optString(OpenStackV2NetworkPlugin.KEY_GATEWAY_IP));		
		Assert.assertEquals(true, subnetJsonObject
				.optBoolean(OpenStackV2NetworkPlugin.KEY_ENABLE_DHCP));
		Assert.assertEquals(OpenStackV2NetworkPlugin.DEFAULT_IP_VERSION, subnetJsonObject
						.optString(OpenStackV2NetworkPlugin.KEY_IP_VERSION));
		Assert.assertEquals(dnsOne, subnetJsonObject
				.optJSONArray(OpenStackV2NetworkPlugin.KEY_DNS_NAMESERVERS).get(0));
		Assert.assertEquals(dnsTwo, subnetJsonObject
				.optJSONArray(OpenStackV2NetworkPlugin.KEY_DNS_NAMESERVERS).get(1));		
	}
	
	@Test
	public void testGenerateJsonEntityToCreateSubnetDefaultAddress() throws JSONException {
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		JSONObject generateJsonEntityToCreateSubnet = openStackV2NetworkPlugin
				.generateJsonEntityToCreateSubnet("networkId", DEFAULT_TENANT_ID, xOCCIAtt);
		
		JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
				.optJSONObject(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET);
		Assert.assertEquals(OpenStackV2NetworkPlugin.DEFAULT_NETWORK_ADDRESS,
				subnetJsonObject.optString(OpenStackV2NetworkPlugin.KEY_CIRD));
	}
	
	@Test
	public void testGenerateJsonEntityToCreateSubnetDefaultDns() throws JSONException {
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		JSONObject generateJsonEntityToCreateSubnet = openStackV2NetworkPlugin
				.generateJsonEntityToCreateSubnet("networkId", DEFAULT_TENANT_ID, xOCCIAtt);
		
		JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
				.optJSONObject(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET);
		Assert.assertEquals(OpenStackV2NetworkPlugin.DEFAULT_DNS_NAME_SERVERS[0], subnetJsonObject
				.optJSONArray(OpenStackV2NetworkPlugin.KEY_DNS_NAMESERVERS).get(0));
		Assert.assertEquals(OpenStackV2NetworkPlugin.DEFAULT_DNS_NAME_SERVERS[1], subnetJsonObject
				.optJSONArray(OpenStackV2NetworkPlugin.KEY_DNS_NAMESERVERS).get(1));	
	}	
	
	@Test
	public void testGenerateJsonEntityToCreateSubnetStaticAllocation() throws JSONException {
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		xOCCIAtt.put(OCCIConstants.NETWORK_ALLOCATION,
				OCCIConstants.NetworkAllocation.STATIC.getValue());
		JSONObject generateJsonEntityToCreateSubnet = openStackV2NetworkPlugin
				.generateJsonEntityToCreateSubnet("networkId", DEFAULT_TENANT_ID, xOCCIAtt);
		
		JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
				.optJSONObject(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET);
		Assert.assertEquals(false, subnetJsonObject
				.optBoolean(OpenStackV2NetworkPlugin.KEY_ENABLE_DHCP));
	}		

	@Test
	public void testGenerateJsonEntityToCreateSubnetWithoutGateway() throws JSONException {
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		JSONObject generateJsonEntityToCreateSubnet = openStackV2NetworkPlugin
				.generateJsonEntityToCreateSubnet("networkId", DEFAULT_TENANT_ID, xOCCIAtt);

		JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
				.optJSONObject(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET);
		Assert.assertTrue(subnetJsonObject.optString(OpenStackV2NetworkPlugin.KEY_GATEWAY_IP).isEmpty());		
	}			
	
	@Test
	public void testGenerateJsonEntitySubnetId() throws JSONException {
		String subnetId = "subnet";
		JSONObject generateJsonEntitySubnetId = openStackV2NetworkPlugin
				.generateJsonEntitySubnetId(subnetId);

		Assert.assertEquals(subnetId, 
				generateJsonEntitySubnetId.optString(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET_ID));			
	}
	
	@Test
	public void testGetRouterIdFromJson() throws JSONException {
		String routerId = "routerId00";
		JSONObject routerContentJsonObject = new JSONObject();
		routerContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_ID, routerId);
		
		JSONObject routerJsonObject = new JSONObject();
		routerJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_ROUTER, routerContentJsonObject);
		Assert.assertEquals(routerId, openStackV2NetworkPlugin.getRouterIdFromJson(routerJsonObject.toString()));
	}
	
	@Test
	public void testGetNetworkIdFromJson() throws JSONException {
		String networkId = "networkId00";
		JSONObject networkContentJsonObject = new JSONObject();
		networkContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_ID, networkId);
		
		JSONObject networkJsonObject = new JSONObject();
		networkJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_NETWORK, networkContentJsonObject);
		Assert.assertEquals(networkId, openStackV2NetworkPlugin.getNetworkIdFromJson(networkJsonObject.toString()));
	}
	
	@Test
	public void testGetSubnetIdFromJson() throws JSONException {
		String subnetId = "subnetId00";
		JSONObject subnetContentJsonObject = new JSONObject();
		subnetContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_ID, subnetId);
		
		JSONObject subnetJsonObject = new JSONObject();
		subnetJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET, subnetContentJsonObject);
		Assert.assertEquals(subnetId, openStackV2NetworkPlugin.getSubnetIdFromJson(subnetJsonObject.toString()));
	}		
	
	@Test
	public void testGetInstanceFromJson() throws ClientProtocolException, IOException, JSONException {
		// Genating network response string
		JSONObject networkContentJsonObject = new JSONObject();
		String networkId = "networkId00";
		String networkName = "netName";
		String subnetId = "subnetId00";
		String vlan = "vlan00";
		networkContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_ID, networkId);
		networkContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_PROVIDER_SEGMENTATION_ID, vlan);
		networkContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_STATUS, 
				OpenStackV2NetworkPlugin.STATUS_OPENSTACK_ACTIVE);
		networkContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_NAME, networkName);
		JSONArray subnetJsonArray = new JSONArray(Arrays.asList(new String[] {subnetId}));
		networkContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_SUBNETS, subnetJsonArray);				
		JSONObject networkJsonObject = new JSONObject();
		networkJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_NETWORK, networkContentJsonObject);

		// Genating subnet response string
		JSONObject subnetContentJsonObject = new JSONObject();
		String gatewayIp = "10.10.10.10";
		String cird = "10.10.10.0/24";
		subnetContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_GATEWAY_IP, gatewayIp);
		subnetContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_ENABLE_DHCP, true);
		subnetContentJsonObject.put(OpenStackV2NetworkPlugin.KEY_CIRD, cird);		
		
		JSONObject subnetJsonObject = new JSONObject();
		subnetJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET, subnetContentJsonObject);
		
		HttpResponse httpResponseGetNetwork = createHttpResponse(
				networkJsonObject.toString(), HttpStatus.SC_OK);
		HttpResponse httpResponseGetSubnet = createHttpResponse(
				subnetJsonObject.toString(), HttpStatus.SC_OK);
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class)))
				.thenReturn(httpResponseGetNetwork, httpResponseGetSubnet);
		
		Instance instance = openStackV2NetworkPlugin.getInstance(defaultToken, "instanceId00");
		
		Assert.assertEquals(networkId, instance.getId());
		Assert.assertEquals(networkName, instance.getAttributes().get(OCCIConstants.TITLE));
		Assert.assertEquals(vlan, instance.getAttributes().get(OCCIConstants.NETWORK_VLAN));
		Assert.assertEquals(OCCIConstants.NetworkState.ACTIVE.getValue(),
				instance.getAttributes().get(OCCIConstants.NETWORK_STATE));
		Assert.assertEquals(gatewayIp, instance.getAttributes().get(OCCIConstants.NETWORK_GATEWAY));
		Assert.assertEquals(cird, instance.getAttributes().get(OCCIConstants.NETWORK_ADDRESS));
		Assert.assertEquals(OCCIConstants.NetworkAllocation.DYNAMIC.getValue(),
				instance.getAttributes().get(OCCIConstants.NETWORK_ALLOCATION));	
	}
	
	@Test(expected=OCCIException.class)
	public void testRemoveWithoutTenantId() {
		Token token = new Token("accessId", "user", new Date(), new HashMap<String, String>());
		openStackV2NetworkPlugin.removeInstance(token, "instanceId");
	}
	
	@Test
	public void testRemoveInstance() throws IOException, JSONException {
		JSONObject portOneJsonObject = new JSONObject();
		String networkId = "networkId";
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_NETWORK_ID, networkId);
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_DEVICE_OWNER, "owner");
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_DEVICE_ID, "routerId");			
		JSONArray subnetsjsonArray = new JSONArray();
		JSONObject subnetObject = new JSONObject();
		subnetObject.put(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET_ID, "subnetId");
		subnetsjsonArray.put(0, subnetObject);
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_FIXES_IPS, subnetsjsonArray);
		
		JSONArray portsArrayJsonObject = new JSONArray();
		portsArrayJsonObject.put(0, portOneJsonObject);
		
		JSONObject portsJsonObject = new JSONObject();
		portsJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_PORTS, portsArrayJsonObject);
		
		HttpResponse httpResponseGetPorts = createHttpResponse(
				portsJsonObject.toString(), HttpStatus.SC_OK);
		HttpResponse httpResponsePutRemoveInterface = createHttpResponse("", HttpStatus.SC_OK);	
		HttpResponse httpResponseDeleteRouter = createHttpResponse("", HttpStatus.SC_OK);	
		HttpResponse httpResponseDeleteNetwork = createHttpResponse("", HttpStatus.SC_OK);	
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class)))
				.thenReturn(httpResponseGetPorts, httpResponsePutRemoveInterface, 
				httpResponseDeleteRouter, httpResponseDeleteNetwork);
				
		openStackV2NetworkPlugin.removeInstance(defaultToken, networkId);
		
		Mockito.verify(client, Mockito.times(4)).execute(Mockito.any(HttpUriRequest.class));
	}	
	
	@Test
	public void testRemoveInstanceNullpointException() throws JSONException, IOException {
		JSONObject portOneJsonObject = new JSONObject();
		String networkId = "networkId";
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_NETWORK_ID, networkId);
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_DEVICE_OWNER, "owner");
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_DEVICE_ID, "routerId");			
		
		JSONArray portsArrayJsonObject = new JSONArray();
		portsArrayJsonObject.put(0, portOneJsonObject);
		
		JSONObject portsJsonObject = new JSONObject();
		portsJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_PORTS, portsArrayJsonObject);
		
		HttpResponse httpResponseGetPorts = createHttpResponse(
				portsJsonObject.toString(), HttpStatus.SC_OK);		
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class)))
				.thenReturn(httpResponseGetPorts);
		
		try {
			openStackV2NetworkPlugin.removeInstance(defaultToken, networkId);
			Assert.fail();
		} catch (OCCIException e) {
			Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, e.getStatus().getCode());
			Assert.assertEquals(ResponseConstants.IRREGULAR_SYNTAX, e.getStatus().getDescription());
		} catch (Exception e) {
			Assert.fail();
		}
		
		Mockito.verify(client, Mockito.times(1)).execute(Mockito.any(HttpUriRequest.class));				
	}
	
	@Test
	public void testRemoveInstanceRemoveRouterBadRequestException() throws JSONException, IOException {
		JSONObject portOneJsonObject = new JSONObject();
		String networkId = "networkId";
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_NETWORK_ID, networkId);
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_DEVICE_OWNER, "owner");
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_DEVICE_ID, "routerId");			
		JSONArray subnetsjsonArray = new JSONArray();
		JSONObject subnetObject = new JSONObject();
		subnetObject.put(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET_ID, "subnetId");
		subnetsjsonArray.put(0, subnetObject);
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_FIXES_IPS, subnetsjsonArray);
		
		JSONArray portsArrayJsonObject = new JSONArray();
		portsArrayJsonObject.put(0, portOneJsonObject);
		
		JSONObject portsJsonObject = new JSONObject();
		portsJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_PORTS, portsArrayJsonObject);
		
		HttpResponse httpResponseGetPorts = createHttpResponse(
				portsJsonObject.toString(), HttpStatus.SC_OK);	
		HttpResponse httpResponseDeleteRouter = createHttpResponse("", HttpStatus.SC_BAD_REQUEST);	
		HttpResponse httpResponseDeleteNetwork = createHttpResponse("", HttpStatus.SC_OK);	
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class)))
				.thenReturn(httpResponseGetPorts, httpResponseDeleteRouter,
				httpResponseDeleteNetwork);
		
		try {
			openStackV2NetworkPlugin.removeInstance(defaultToken, networkId);
			Assert.fail();
		} catch (OCCIException e) {
			Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, e.getStatus().getCode());
		} catch (Exception e) {
			Assert.fail();
		}
		
		Mockito.verify(client, Mockito.times(2)).execute(Mockito.any(HttpUriRequest.class));				
	}	
	
	@Test
	public void testRemoveInstanceRemoveNetworkBadRequestException() throws JSONException, IOException {
		JSONObject portOneJsonObject = new JSONObject();
		String networkId = "networkId";
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_NETWORK_ID, networkId);
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_DEVICE_OWNER, "owner");
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_DEVICE_ID, "routerId");		
		JSONArray subnetsjsonArray = new JSONArray();
		JSONObject subnetObject = new JSONObject();
		subnetObject.put(OpenStackV2NetworkPlugin.KEY_JSON_SUBNET_ID, "subnetId");
		subnetsjsonArray.put(0, subnetObject);
		portOneJsonObject.put(OpenStackV2NetworkPlugin.KEY_FIXES_IPS, subnetsjsonArray);
		
		JSONArray portsArrayJsonObject = new JSONArray();
		portsArrayJsonObject.put(0, portOneJsonObject);
		
		JSONObject portsJsonObject = new JSONObject();
		portsJsonObject.put(OpenStackV2NetworkPlugin.KEY_JSON_PORTS, portsArrayJsonObject);
		
		HttpResponse httpResponseGetPorts = createHttpResponse(
				portsJsonObject.toString(), HttpStatus.SC_OK);	
		HttpResponse httpResponseDeleteRouter = createHttpResponse("", HttpStatus.SC_OK);	
		HttpResponse httpResponseDeleteNetwork = createHttpResponse("", HttpStatus.SC_BAD_REQUEST);	
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class)))
				.thenReturn(httpResponseGetPorts, httpResponseDeleteRouter,
				httpResponseDeleteNetwork);
		
		try {
			openStackV2NetworkPlugin.removeInstance(defaultToken, networkId);
			Assert.fail();
		} catch (OCCIException e) {
			Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, e.getStatus().getCode());
		} catch (Exception e) {
			Assert.fail();
		}
		
		Mockito.verify(client, Mockito.times(3)).execute(Mockito.any(HttpUriRequest.class));				
	}	
	
	@Test
	public void testRequestInstance() throws IOException {
		HttpResponse httpResponsePostRouter = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePostNetwork = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePostSubnet = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePutInterface = createHttpResponse("", HttpStatus.SC_OK);
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				httpResponsePostRouter, httpResponsePostNetwork, httpResponsePostSubnet, 
				httpResponsePutInterface);
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		openStackV2NetworkPlugin.requestInstance(defaultToken, null, xOCCIAtt);
		
		Mockito.verify(client, Mockito.times(4)).execute(Mockito.any(HttpUriRequest.class));		
	}
	
	@Test(expected=OCCIException.class)
	public void testRequestInstancePostRouterError() throws IOException {
		HttpResponse httpResponsePostRouter = createHttpResponse("", HttpStatus.SC_BAD_REQUEST);
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				httpResponsePostRouter);
		
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		openStackV2NetworkPlugin.requestInstance(defaultToken, null, xOCCIAtt);
		
		Mockito.verify(client, Mockito.times(1)).execute(Mockito.any(HttpUriRequest.class));	
	}
	
	@Test
	public void testRequestInstancePostNetworkError() throws IOException {
		HttpResponse httpResponsePostRouter = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePostNetwork = createHttpResponse("", HttpStatus.SC_BAD_REQUEST);
		HttpResponse httpResponseRemoveRouter = createHttpResponse("", HttpStatus.SC_OK);
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				httpResponsePostRouter, httpResponsePostNetwork, httpResponseRemoveRouter);
				
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		try {
			openStackV2NetworkPlugin.requestInstance(defaultToken, null, xOCCIAtt);
			Assert.fail();
		} catch (Exception e) {}
		
		Mockito.verify(client, Mockito.times(3)).execute(Mockito.any(HttpUriRequest.class));
	}	
	
	@Test(expected=OCCIException.class)
	public void testRequestInstancePostSubnetError() throws IOException {
		HttpResponse httpResponsePostRouter = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePostNetwork = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePostSubnet = createHttpResponse("", HttpStatus.SC_BAD_REQUEST);
		HttpResponse httpResponseRemoveRouter = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponseRemoveNetwork = createHttpResponse("", HttpStatus.SC_OK);
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				httpResponsePostRouter, httpResponsePostNetwork, httpResponsePostSubnet, 
				httpResponseRemoveRouter, httpResponseRemoveNetwork);
		
		
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		openStackV2NetworkPlugin.requestInstance(defaultToken, null, xOCCIAtt);
		
		Mockito.verify(client, Mockito.times(5)).execute(Mockito.any(HttpUriRequest.class));		
	}	
	
	@Test
	public void testRequestInstancePutInterfaceError() throws IOException {
		HttpResponse httpResponsePostRouter = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePostNetwork = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePostSubnet = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponsePutInterface = createHttpResponse("", HttpStatus.SC_BAD_REQUEST);
		HttpResponse httpResponseRemoveRouter = createHttpResponse("", HttpStatus.SC_OK);
		HttpResponse httpResponseRemoveNetwork = createHttpResponse("", HttpStatus.SC_OK);		
		Mockito.when(client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(
				httpResponsePostRouter, httpResponsePostNetwork, httpResponsePostSubnet, 
				httpResponsePutInterface, httpResponseRemoveRouter, httpResponseRemoveNetwork);
				
		Map<String, String> xOCCIAtt = new HashMap<String, String>();
		try {
			openStackV2NetworkPlugin.requestInstance(defaultToken, null, xOCCIAtt);		
			Assert.fail();
		} catch (Exception e) {}
		
		Mockito.verify(client, Mockito.times(6)).execute(Mockito.any(HttpUriRequest.class));	
	}	

	private HttpResponse createHttpResponse(String content, int httpStatus) throws IOException {
		HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
		HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
		InputStream inputStrem = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));;
		Mockito.when(httpEntity.getContent()).thenReturn(inputStrem);
		Mockito.when(httpResponse.getEntity()).thenReturn(httpEntity);
		StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("", 0, 0), httpStatus, "");
		Mockito.when(httpResponse.getStatusLine()).thenReturn(statusLine);
		return httpResponse;
	}
	

}
