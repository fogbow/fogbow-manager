package org.fogbowcloud.manager.occi.plugins.voms;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fogbowcloud.manager.core.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.voms.VomsIdentityPlugin;
import org.fogbowcloud.manager.core.plugins.voms.VomsIdentityPlugin.GenerateProxyCertificate;
import org.fogbowcloud.manager.occi.core.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import eu.emi.security.authn.x509.helpers.proxy.ProxyCertificateImpl;
import eu.emi.security.authn.x509.impl.PEMCredential;
import eu.emi.security.authn.x509.proxy.ProxyCertificate;

public class TestIdentityVoms {

	private static final int TWELVE_HOURS = 1000 * 60 * 60 * 12;
	private final String VOMS_PASSWORD = "pass";
	private final String VOMS_SERVER = "atlas";

	private GenerateProxyCertificate generateProxyCertificate;
	private VomsIdentityPlugin vomsIdentityPlugin;
	private Properties properties;

	@Before
	public void setUp() {
		properties = new Properties();
		properties.put(ConfigurationConstants.VOMS_PATH_TRUST,
				"src/test/resources/voms/trust-anchors");
		properties.put(ConfigurationConstants.VOMS_PATH_VOMSES, "src/test/resources/voms/vomses");
		properties.put(ConfigurationConstants.VOMS_PATH_VOMSDIR, "src/test/resources/voms/vomsdir");
		properties.put(ConfigurationConstants.FEDERATION_USER_PASS_VOMS, VOMS_PASSWORD);
		properties.put(ConfigurationConstants.FEDERATION_USER_SERVER_VOMS, VOMS_SERVER);

		vomsIdentityPlugin = new VomsIdentityPlugin(properties);
		generateProxyCertificate = Mockito.mock(GenerateProxyCertificate.class);
		vomsIdentityPlugin.setGenerateProxyCertificate(generateProxyCertificate);
	}

	@SuppressWarnings({ "unchecked", "deprecation" })
	@Test
	public void testReIssueToken() throws Exception {
		PEMCredential holder = Utils.getTestUserCredential();
		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		X509Certificate[] extendedChain = proxy.getCertificateChain();
		PrivateKey proxyPrivateKey = proxy.getPrivateKey();
		ProxyCertificateImpl proxyCertificate = new ProxyCertificateImpl(extendedChain,
				proxyPrivateKey);

		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(Token.Constants.VOMS_PASSWORD.getValue(), VOMS_PASSWORD);
		credentials.put(Token.Constants.VOMS_SERVER.getValue(), VOMS_SERVER);

		Mockito.when(generateProxyCertificate.generate(Mockito.anyMap()))
				.thenReturn(proxyCertificate);

		Token myToken = new Token("", "", new Date(), credentials);
		Token token = vomsIdentityPlugin.reIssueToken(myToken);

		Assert.assertEquals(vomsIdentityPlugin.generateAcessId(proxy.getCertificateChain()),
				token.getAccessId());
		Assert.assertEquals("CN=test0, O=IGI, C=IT", token.getUser());
		Date dateExpiration = new Date(System.currentTimeMillis() + TWELVE_HOURS);
		Assert.assertEquals(dateExpiration.getDay(), token.getExpirationDate().getDay());
		Assert.assertEquals(dateExpiration.getHours(), token.getExpirationDate().getHours());
		Assert.assertEquals(dateExpiration.getMinutes(), token.getExpirationDate().getMinutes());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testCreateToken() throws Exception {
		PEMCredential holder = Utils.getTestUserCredential();
		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);
		X509Certificate[] extendedChain = proxy.getCertificateChain();
		PrivateKey proxyPrivateKey = proxy.getPrivateKey();
		ProxyCertificateImpl proxyCertificate = new ProxyCertificateImpl(extendedChain,
				proxyPrivateKey);

		Map<String, String> credentials = new HashMap<String, String>();
		credentials.put(Token.Constants.VOMS_PASSWORD.getValue(), VOMS_PASSWORD);
		credentials.put(Token.Constants.VOMS_SERVER.getValue(), VOMS_SERVER);

		Mockito.when(generateProxyCertificate.generate(credentials)).thenReturn(
				proxyCertificate);

		Token token = vomsIdentityPlugin.createToken(credentials);

		Assert.assertEquals(vomsIdentityPlugin.generateAcessId(proxy.getCertificateChain()),
				token.getAccessId());
		Assert.assertEquals("CN=test0, O=IGI, C=IT", token.getUser());
		Date dateExpiration = new Date(System.currentTimeMillis() + TWELVE_HOURS);
		Assert.assertEquals(dateExpiration.getDay(), token.getExpirationDate().getDay());
		Assert.assertEquals(dateExpiration.getHours(), token.getExpirationDate().getHours());
		Assert.assertEquals(dateExpiration.getMinutes(), token.getExpirationDate().getMinutes());
	}

	@SuppressWarnings("deprecation")
	@Test
	public void testGetToken() throws Exception {
		PEMCredential holder = Utils.getTestUserCredential();

		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);

		String accessId = vomsIdentityPlugin.generateAcessId(proxy
				.getCertificateChain());
		Token token = vomsIdentityPlugin.getToken(accessId);

		Assert.assertEquals("CN=test0, O=IGI, C=IT", token.getUser());
		Assert.assertEquals(accessId, token.getAccessId());
		Date dateExpiration = new Date(System.currentTimeMillis() + TWELVE_HOURS);
		Assert.assertEquals(dateExpiration.getDay(), token.getExpirationDate().getDay());
		Assert.assertEquals(dateExpiration.getHours(), token.getExpirationDate().getHours());
		Assert.assertEquals(dateExpiration.getMinutes(), token.getExpirationDate().getMinutes());
	}

	@Test
	public void testValidCetificate() throws Exception {
		PEMCredential holder = Utils.getTestUserCredential();

		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);

		Assert.assertTrue(vomsIdentityPlugin.isValid(vomsIdentityPlugin.generateAcessId(proxy
				.getCertificateChain())));
	}

	@Test
	public void testExpiredCertificate() throws Exception {
		PEMCredential holder = Utils.getExpiredCredential();

		ProxyCertificate proxy = Utils.getVOMSAA().createVOMSProxy(holder, Fixture.defaultVOFqans);

		Assert.assertFalse(vomsIdentityPlugin.isValid(vomsIdentityPlugin.generateAcessId(proxy
				.getCertificateChain())));
	}
}
