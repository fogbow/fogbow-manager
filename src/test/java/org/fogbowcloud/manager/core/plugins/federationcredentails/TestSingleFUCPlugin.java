package org.fogbowcloud.manager.core.plugins.federationcredentails;

import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.occi.request.Request;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSingleFUCPlugin {

	private SingleFUCPlugin singleFUCPlugin;
	private final String CREDENTIAL_ONE = "credOne";
	private final String CREDENTIAL_TWO = "credTwo";
	private String PROVIDER_ONE = "providerOne";
	private String PROVIDER_TWO = "providerTwo";
	private String VALUE_ONE_FOGBOW = "valueOneFogbow";
	private String VALUE_TWO_FOGBOW = "valueTwoFogbow";	
	private String VALUE_ONE = "valueOne";
	private String VALUE_TWO = "valueTwo";
	private String VALUE_THREE = "valueThree";
	private String VALUE_FOUR = "valueFour";
	private Properties properties;
	
	@Before
	public void setUp() {
		this.properties = new Properties();
		properties.put(FUCPluginHelper.FUC_PREFIX + FUCPluginHelper.FOGBOW_DEFAULTS + 
				FUCPluginHelper.UNDERLINE + CREDENTIAL_ONE, VALUE_ONE_FOGBOW);
		properties.put(FUCPluginHelper.FUC_PREFIX + FUCPluginHelper.FOGBOW_DEFAULTS + 
				FUCPluginHelper.UNDERLINE + CREDENTIAL_TWO, VALUE_TWO_FOGBOW);		
		properties.put(FUCPluginHelper.FUC_PREFIX + PROVIDER_ONE + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_ONE);
		properties.put(FUCPluginHelper.FUC_PREFIX + PROVIDER_ONE + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_TWO);
		properties.put(FUCPluginHelper.FUC_PREFIX + PROVIDER_TWO + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_ONE, VALUE_THREE);
		properties.put(FUCPluginHelper.FUC_PREFIX + PROVIDER_TWO + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_FOUR);
		properties.put(FUCPluginHelper.FUC_PREFIX + "wrong" + FUCPluginHelper.UNDERLINE
				+ CREDENTIAL_TWO, VALUE_FOUR);
		properties.put(FUCPluginHelper.FUC_PREFIX + "wr" + FUCPluginHelper.UNDERLINE + "ong" + FUCPluginHelper.UNDERLINE
				+ "trash " + FUCPluginHelper.UNDERLINE +  CREDENTIAL_TWO, VALUE_FOUR);		
		properties.put(FUCPluginHelper.FUC_PREFIX + "without-underline", VALUE_FOUR);				
		this.singleFUCPlugin = new SingleFUCPlugin(properties);		
	}
	
	@Test
	public void testGetAllFedUsersCredentials() {
		Map<String, Map<String, String>> allFedUserCredentials = this.singleFUCPlugin
				.getAllFedUsersCredentials();
		Assert.assertEquals(VALUE_ONE_FOGBOW, allFedUserCredentials.get(FUCPluginHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, allFedUserCredentials.get(FUCPluginHelper.FOGBOW_DEFAULTS)
				.get(CREDENTIAL_TWO));			
		Assert.assertEquals(1, allFedUserCredentials.size());	
	}
	
	@Test
	public void testGetFedUserCredentials() {
		Request request = new Request(null, null, null, null, false, null);
		request.setProvidingMemberId(PROVIDER_ONE);
		Map<String, String> fedUserCredentials = this.singleFUCPlugin.getFedUserCredentials(request);
		Assert.assertEquals(VALUE_ONE_FOGBOW, fedUserCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, fedUserCredentials.get(CREDENTIAL_TWO));		
	}
	
	@Test
	public void testGetFedUserCredentialsNotFountWithDefaultValue() {
		Request request = new Request(null, null, null, null, false, null);
		request.setProvidingMemberId("notfound");
		Map<String, String> fedUserCredentials = this.singleFUCPlugin.getFedUserCredentials(request);
		Assert.assertEquals(VALUE_ONE_FOGBOW, fedUserCredentials.get(CREDENTIAL_ONE));
		Assert.assertEquals(VALUE_TWO_FOGBOW, fedUserCredentials.get(CREDENTIAL_TWO));
	}	
	
	@Test
	public void testGetAllFedUsersCredentialsNotFoundWithoutDefaultValue() {
		singleFUCPlugin = new SingleFUCPlugin(new Properties()); 
		Map<String, Map<String, String>> allFedUserCredentials = singleFUCPlugin.getAllFedUsersCredentials();
		Assert.assertTrue(allFedUserCredentials.isEmpty());		
	}	
	
	@Test
	public void testGetFedUserCredentialsNotFoundWithoutDefaultValue() {
		singleFUCPlugin = new SingleFUCPlugin(new Properties()); 
		Request request = new Request(null, null, null, null, false, null);
		request.setProvidingMemberId(PROVIDER_ONE);
		Map<String, String> fedUserCredentials = this.singleFUCPlugin.getFedUserCredentials(request);
		Assert.assertTrue(fedUserCredentials.isEmpty());
	}	
	
}