package org.fogbowcloud.manager.occi.federatednetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.federatednetwork.FederatedNetwork;
import org.fogbowcloud.manager.core.federatednetwork.FederatedNetworksController;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.BenchmarkingPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.plugins.MapperPlugin;
import org.fogbowcloud.manager.occi.OCCIConstants;
import org.fogbowcloud.manager.occi.TestDataStorageHelper;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.fogbowcloud.manager.occi.model.Token;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;


public class TestFederatedNetworkResource {

	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private MapperPlugin mapperPlugin;
	private AuthorizationPlugin authorizationPlugin;
	private BenchmarkingPlugin benchmarkingPlugin;
	private FederatedNetworksController federatedNetworksController;

	private OCCITestHelper orderHelper;

	private ManagerController facade;

	@Before
	public void setUp() throws Exception {
		this.orderHelper = new OCCITestHelper();

		computePlugin = Mockito.mock(ComputePlugin.class);
		identityPlugin = Mockito.mock(IdentityPlugin.class);
		mapperPlugin = Mockito.mock(MapperPlugin.class);
		authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		benchmarkingPlugin = Mockito.mock(BenchmarkingPlugin.class);
		federatedNetworksController = Mockito.mock(FederatedNetworksController.class);

		facade = this.orderHelper.initializeComponentExecutorSameThread(computePlugin,
				identityPlugin, authorizationPlugin, benchmarkingPlugin, mapperPlugin,
				federatedNetworksController);
	}

	@After
	public void tearDown() throws Exception {
		TestDataStorageHelper.clearManagerDataStore(
				this.facade.getManagerDataStoreController().getManagerDatabase());
		this.orderHelper.stopComponent();
	}

	@Test
	public void testGet() throws ClientProtocolException, IOException {
		String FNId = "fake-id";
		String cidr = "10.0.0.0/24";
		String label = "fake-label";
		String[] members = new String[] { "member01", "member02" };

		FederatedNetwork fn = new FederatedNetwork(FNId, cidr, label, new HashSet<FederationMember>(
				Arrays.asList(new FederationMember(members[0]), new FederationMember(members[1]))));
		Mockito.doReturn(fn).when(this.federatedNetworksController)
				.getFederatedNetwork(Mockito.any(Token.User.class), Mockito.anyString());
		
		Token token = new Token("fake-acess-id", Mockito.mock(Token.User.class), new Date(), new HashMap<String, String>());
		Mockito.doReturn(token).when(this.identityPlugin).getToken(Mockito.anyString());
		
		Mockito.doReturn(true).when(this.authorizationPlugin).isAuthorized(Mockito.any(Token.class));

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/" + FNId);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(responseString.contains("/federatedNetwork/" + FNId + "/"));
		Assert.assertTrue(responseString.contains(FNId));
		Assert.assertTrue(responseString.contains(cidr));
		Assert.assertTrue(responseString.contains(label));
		for (String member : members) {
			Assert.assertTrue(responseString.contains(member));
		}
		client.close();
	}

	@Test
	public void testGetNull() throws ClientProtocolException, IOException {
		String FNId = "fake-id";

		Mockito.doReturn(null).when(this.federatedNetworksController)
				.getFederatedNetwork(Mockito.any(Token.User.class), Mockito.anyString());
		
		Token token = new Token("fake-acess-id", Mockito.mock(Token.User.class), new Date(), new HashMap<String, String>());
		Mockito.doReturn(token).when(this.identityPlugin).getToken(Mockito.anyString());
		
		Mockito.doReturn(true).when(this.authorizationPlugin).isAuthorized(Mockito.any(Token.class));

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/" + FNId);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(FederatedNetworkResource.NO_NETWORKS_MESSAGE, responseString);
		client.close();
	}

	@Test
	public void testGetWithoutFNIdVerbose() throws ClientProtocolException, IOException {
		String[] FNIds = new String[] { "fake-id1", "fake-id2" };
		String[] CIDRs = new String[] { "10.0.0.1/23", "10.0.0.0/0" };
		String[] labels = new String[] { "fake-label1", "fake-label2" };
		String[] members = new String[] { "member01", "member02", "member03", "member04" };
		FederatedNetwork fn1 = new FederatedNetwork(FNIds[0], CIDRs[0], labels[0],
				new HashSet<FederationMember>(Arrays.asList(new FederationMember(members[0]),
						new FederationMember(members[1]))));

		FederatedNetwork fn2 = new FederatedNetwork(FNIds[1], CIDRs[1], labels[1],
				new HashSet<FederationMember>(Arrays.asList(new FederationMember(members[2]),
						new FederationMember(members[3]))));

		Collection<FederatedNetwork> fnList = new ArrayList<FederatedNetwork>();
		fnList.add(fn1);
		fnList.add(fn2);

		Mockito.doReturn(fnList).when(this.federatedNetworksController)
				.getUserNetworks(Mockito.any(Token.User.class));
		
		Token token = new Token("fake-acess-id", Mockito.mock(Token.User.class), new Date(), new HashMap<String, String>());
		Mockito.doReturn(token).when(this.identityPlugin).getToken(Mockito.anyString());
		
		Mockito.doReturn(true).when(this.authorizationPlugin).isAuthorized(Mockito.any(Token.class));

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "?verbose=true");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		for (String FNId : FNIds) {
			Assert.assertTrue(responseString.contains("/federatedNetwork/" + FNId + "/"));
			Assert.assertTrue(responseString.contains(FNId));
		}
		for (String CIDR : CIDRs) {
			Assert.assertTrue(responseString.contains(CIDR));
		}
		for (String label : labels) {
			Assert.assertTrue(responseString.contains(label));
		}
		for (String member : members) {
			Assert.assertTrue(responseString.contains(member));
		}
		client.close();
	}
	
	@Test
	public void testGetWithoutFNIdNoVerbose() throws ClientProtocolException, IOException {
		String[] FNIds = new String[] { "fake-id1", "fake-id2" };
		String[] CIDRs = new String[] { "10.0.0.1/23", "10.0.0.0/0" };
		String[] labels = new String[] { "fake-label1", "fake-label2" };
		String[] members = new String[] { "member01", "member02", "member03", "member04" };
		FederatedNetwork fn1 = new FederatedNetwork(FNIds[0], CIDRs[0], labels[0],
				new HashSet<FederationMember>(Arrays.asList(new FederationMember(members[0]),
						new FederationMember(members[1]))));

		FederatedNetwork fn2 = new FederatedNetwork(FNIds[1], CIDRs[1], labels[1],
				new HashSet<FederationMember>(Arrays.asList(new FederationMember(members[2]),
						new FederationMember(members[3]))));

		Collection<FederatedNetwork> fnList = new ArrayList<FederatedNetwork>();
		fnList.add(fn1);
		fnList.add(fn2);

		Mockito.doReturn(fnList).when(this.federatedNetworksController)
				.getUserNetworks(Mockito.any(Token.User.class));
		
		Token token = new Token("fake-acess-id", Mockito.mock(Token.User.class), new Date(), new HashMap<String, String>());
		Mockito.doReturn(token).when(this.identityPlugin).getToken(Mockito.anyString());
		
		Mockito.doReturn(true).when(this.authorizationPlugin).isAuthorized(Mockito.any(Token.class));

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		for (String FNId : FNIds) {
			Assert.assertTrue(responseString.contains("/federatedNetwork/" + FNId + "/"));
			Assert.assertTrue(responseString.contains(FNId));
		}
		client.close();
	}

	@Test
	public void testGetWithoutFNIdEmpty() throws ClientProtocolException, IOException {
		Collection<FederatedNetwork> fnList = new ArrayList<FederatedNetwork>();

		Mockito.doReturn(fnList).when(this.federatedNetworksController)
				.getUserNetworks(Mockito.any(Token.User.class));
		
		Token token = new Token("fake-acess-id", Mockito.mock(Token.User.class), new Date(), new HashMap<String, String>());
		Mockito.doReturn(token).when(this.identityPlugin).getToken(Mockito.anyString());
		
		Mockito.doReturn(true).when(this.authorizationPlugin).isAuthorized(Mockito.any(Token.class));

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(FederatedNetworkResource.NO_NETWORKS_MESSAGE, responseString);
		client.close();
	}

	@Test
	public void testGetWithoutFNIdNull() throws ClientProtocolException, IOException {
		Mockito.doReturn(null).when(this.federatedNetworksController)
				.getUserNetworks(Mockito.any(Token.User.class));
		
		Token token = new Token("fake-acess-id", Mockito.mock(Token.User.class), new Date(), new HashMap<String, String>());
		Mockito.doReturn(token).when(this.identityPlugin).getToken(Mockito.anyString());
		
		Mockito.doReturn(true).when(this.authorizationPlugin).isAuthorized(Mockito.any(Token.class));

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertEquals(FederatedNetworkResource.NO_NETWORKS_MESSAGE, responseString);
		client.close();
	}

	@Test
	public void testGetWithoutAuthentication() throws ClientProtocolException, IOException {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		client.close();
	}
	
	@Test
	public void testGetUnauthorized() throws ClientProtocolException, IOException {
		String FNId = "fake-id";
		String cidr = "10.0.0.0/24";
		String label = "fake-label";
		String[] members = new String[] { "member01", "member02" };

		FederatedNetwork fn = new FederatedNetwork(FNId, cidr, label, new HashSet<FederationMember>(
				Arrays.asList(new FederationMember(members[0]), new FederationMember(members[1]))));
		Mockito.doReturn(fn).when(this.federatedNetworksController)
				.getFederatedNetwork(Mockito.any(Token.User.class), Mockito.anyString());
		
		Token token = new Token("fake-acess-id", Mockito.mock(Token.User.class), new Date(), new HashMap<String, String>());
		Mockito.doReturn(token).when(this.identityPlugin).getToken(Mockito.anyString());
		
		Mockito.doReturn(false).when(this.authorizationPlugin).isAuthorized(Mockito.any(Token.class));

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/" + FNId);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		client.close();
	}

	//TODO: Mock AuthorizationPlugin
	@Ignore
	@Test
	public void testPut() throws ClientProtocolException, IOException {
		String FNId = "fake-id";

		HttpPut post = new HttpPut(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/" + FNId);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		String membersList[] = new String[] { "lsd.manager.something", "alemanha.naf.something" };
		for (String member : membersList) {
			post.addHeader(OCCIConstants.FEDERATED_NETWORK_MEMBERS, member);
		}

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);
		String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
		
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
		Assert.assertTrue(responseString.contains(FNId));
		for (String member : membersList) {
			Assert.assertTrue(responseString.contains(member));
		}

		client.close();
	}

	@Test
	public void testPutWithoutFNId() throws ClientProtocolException, IOException {
		HttpPut post = new HttpPut(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/");
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		String membersList[] = new String[] { "lsd.manager.something", "alemanha.naf.something" };
		for (String member : membersList) {
			post.addHeader(OCCIConstants.FEDERATED_NETWORK_MEMBERS, member);
		}

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
		client.close();
	}

	@Test
	public void testPutWithoutMember() throws ClientProtocolException, IOException {
		String FNId = "fake-id";

		HttpPut post = new HttpPut(OCCITestHelper.URI_FOGBOW_FEDERATED_NETWORK + "/" + FNId);
		post.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		post.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);

		CloseableHttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(post);

		Assert.assertEquals(HttpStatus.SC_NOT_ACCEPTABLE, response.getStatusLine().getStatusCode());
		client.close();
	}
}
