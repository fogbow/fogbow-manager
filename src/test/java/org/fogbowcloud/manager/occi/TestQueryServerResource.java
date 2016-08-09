package org.fogbowcloud.manager.occi;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.codec.Charsets;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.fogbowcloud.manager.core.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.ComputePlugin;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.fogbowcloud.manager.occi.model.HeaderUtils;
import org.fogbowcloud.manager.occi.model.OCCIHeaders;
import org.fogbowcloud.manager.occi.model.Resource;
import org.fogbowcloud.manager.occi.model.ResourceRepository;
import org.fogbowcloud.manager.occi.model.Token;
import org.fogbowcloud.manager.occi.order.OrderConstants;
import org.fogbowcloud.manager.occi.util.OCCITestHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;

public class TestQueryServerResource {

	private OCCITestHelper helper;
	private ComputePlugin computePlugin;
	private IdentityPlugin identityPlugin;
	private AuthorizationPlugin authorizationPlugin;

	@SuppressWarnings("deprecation")
	@Before
	public void setup() throws Exception {
		this.computePlugin = Mockito.mock(ComputePlugin.class);
		Mockito.doNothing().when(computePlugin)
				.bypass(Mockito.any(Request.class), Mockito.any(Response.class));
		
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		Mockito.when(identityPlugin.getToken(OCCITestHelper.ACCESS_TOKEN))
				.thenReturn(
						new Token("id", OCCITestHelper.USER_MOCK, new Date(),
								new HashMap<String, String>()));
		Mockito.when(identityPlugin.getAuthenticationURI()).thenReturn("Keystone uri='http://localhost:5000/'");
		
		this.authorizationPlugin = Mockito.mock(AuthorizationPlugin.class);
		Mockito.when(authorizationPlugin.isAuthorized(Mockito.any(Token.class))).thenReturn(true);
		
		this.helper = new OCCITestHelper();
		this.helper.initializeComponent(computePlugin, identityPlugin, authorizationPlugin);
	}

	@After
	public void tearDown() throws Exception {
		this.helper.stopComponent();
	}

	@Test
	public void testGetQueryDifferentContentType() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, "text/plain");
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetQueryWithoutAccessToken() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
	}

	@Test
	public void testGetQueryWithOutAccept() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetQueryValidAccept() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.ACCEPT, MediaType.TEXT_PLAIN.toString());
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetQueryInvalidAccept() throws Exception {
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.ACCEPT, OCCIHeaders.OCCI_ACCEPT);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetQuery() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		for (Resource resource : ResourceRepository.getInstance().getAll()) {
			Assert.assertTrue(responseStr.contains(resource.toHeader()));
		}

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetQueryWithTypeTwo() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY_TYPE_TWO);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);

		String responseStr = EntityUtils.toString(response.getEntity(),
				String.valueOf(Charsets.UTF_8));

		for (Resource resource : ResourceRepository.getInstance().getAll()) {
			Assert.assertTrue(responseStr.contains(resource.toHeader()));
		}

		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	

	@Test
	public void testHeadQueryWithoutAccessToken() throws Exception {

		HttpHead head = new HttpHead(OCCITestHelper.URI_FOGBOW_QUERY);
		head.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(head);
		
		Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());
		Assert.assertEquals("Keystone uri='http://localhost:5000/'",
				response.getFirstHeader(HeaderUtils.WWW_AUTHENTICATE).getValue());
	}
	
	@Test
	public void testGetQueryFiltrated() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.CATEGORY, OCCITestHelper.FOGBOW_SMALL_IMAGE + "; " + 
				"scheme=\"http://schemas.fogbowcloud.org/template/resource#\"; class=\"mixin\";");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}

	@Test
	public void testGetQueryFiltratedTwoCategories() throws Exception {
		String categorySmall = OCCITestHelper.FOGBOW_SMALL_IMAGE + "; " + 
				"scheme=\"http://schemas.fogbowcloud.org/template/resource#\"; class=\"mixin\";";
		String categoryFogbowOrder = OrderConstants.TERM + "; " + 
				"scheme=\"" + OrderConstants.SCHEME + "\"; class=\"kind\";";

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.CATEGORY, categorySmall);
		get.addHeader(OCCIHeaders.CATEGORY, categoryFogbowOrder);		
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		
		String responseStr = EntityUtils.toString(response.getEntity());			

		Assert.assertTrue(responseStr.contains(categorySmall));
		Assert.assertTrue(responseStr.contains(categoryFogbowOrder));
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetQueryFiltratedRelatedToCategory() throws Exception {

		String termCategory = "resource_tpl";
		String schemeCategory = "http://schemas.ogf.org/occi/infrastructure#";
		
		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.CATEGORY, "Category: " + termCategory + "; " + 
				" scheme=\"" + schemeCategory  + "\"; class=\"mixin\";");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		
		String responseStr = EntityUtils.toString(response.getEntity());
		
		String relReference = schemeCategory + termCategory;
		List<Resource> allResources = ResourceRepository.getInstance().getAll();
		for (Resource resource : allResources) {
			if (resource.getRel().equals(relReference)) {
				Assert.assertTrue(responseStr.contains(resource.toHeader()));
			}
		}
		
		Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
	}	
	
	@Test
	public void testGetQueryFiltratedWrongCategory() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.CATEGORY, "wrong category");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}
	
	@Test
	public void testGetQueryFiltratedWrongCategoryWithoutSemicolon() throws Exception {

		HttpGet get = new HttpGet(OCCITestHelper.URI_FOGBOW_QUERY);
		get.addHeader(OCCIHeaders.CONTENT_TYPE, OCCIHeaders.OCCI_CONTENT_TYPE);
		get.addHeader(OCCIHeaders.X_AUTH_TOKEN, OCCITestHelper.ACCESS_TOKEN);
		get.addHeader(OCCIHeaders.CATEGORY, "fogbow_small " + 
				"scheme=\"http://schemas.fogbowcloud.org/template/resource#\" class=\"mixin\"");
		HttpClient client = HttpClients.createMinimal();
		HttpResponse response = client.execute(get);
		
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
	}		
}
