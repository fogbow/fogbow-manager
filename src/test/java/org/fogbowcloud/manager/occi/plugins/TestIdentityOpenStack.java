package org.fogbowcloud.manager.occi.plugins;

import java.util.Properties;

import org.fogbowcloud.manager.core.plugins.openstack.OpenStackIdentityPlugin;
import org.fogbowcloud.manager.occi.util.PluginHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.restlet.resource.ResourceException;

public class TestIdentityOpenStack {

	private final String KEYSTONE_URL = "http://localhost:" + PluginHelper.PORT_ENDPOINT;
	private OpenStackIdentityPlugin identityOpenStack;
	private PluginHelper pluginHelper;

	@Before
	public void setUp() throws Exception {
		Properties properties = new Properties();
		properties.put("identity_openstack_url", KEYSTONE_URL);
		this.identityOpenStack = new OpenStackIdentityPlugin(properties);
		this.pluginHelper = new PluginHelper();
		this.pluginHelper.initializeKeystoneComponent();
	}

	@After
	public void tearDown() throws Exception {
		this.pluginHelper.disconnectComponent();
	}

	@Test
	public void testValidToken() {
		Assert.assertEquals(PluginHelper.USERNAME_FOGBOW,
				this.identityOpenStack.getUser(PluginHelper.AUTH_TOKEN));
	}

	@Test(expected = ResourceException.class)
	public void testInvalidToken() {
		identityOpenStack.getUser("Invalid Token");
	}

	@Test
	public void testGetNameUserFromToken() {
		Assert.assertEquals(PluginHelper.USERNAME_FOGBOW,
				this.identityOpenStack.getUser(PluginHelper.AUTH_TOKEN));
	}

	@Test(expected = ResourceException.class)
	public void testGetNameUserFromTokenInvalid() {
		this.identityOpenStack.getUser("invalid_token");
	}

	@Test
	public void testGetToken() {
		String token = this.identityOpenStack.getToken(PluginHelper.USERNAME_FOGBOW,
				PluginHelper.PASSWORD_FOGBOW);
		Assert.assertEquals(PluginHelper.AUTH_TOKEN, token);
	}

	@Test(expected = ResourceException.class)
	public void testGetTokenWrongUsername() {
		this.identityOpenStack.getToken("wrong", PluginHelper.USERNAME_FOGBOW);
	}

	@Test(expected = ResourceException.class)
	public void testGetTokenWrongPassword() {
		this.identityOpenStack.getToken(PluginHelper.PASSWORD_FOGBOW, "wrong");
	}
}