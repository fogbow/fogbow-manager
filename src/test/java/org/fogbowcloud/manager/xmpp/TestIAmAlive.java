package org.fogbowcloud.manager.xmpp;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerTestHelper;
import org.fogbowcloud.manager.core.model.FederationMember;
import org.fogbowcloud.manager.core.util.DefaultDataTestHelper;
import org.jamppa.client.XMPPClient;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class TestIAmAlive {

	private ManagerTestHelper managerTestHelper;
	private ManagerXmppComponent managerXmppComponent;

	@Before
	public void setUp() throws ComponentException {
		managerTestHelper = new ManagerTestHelper();
	}

	@Test
	public void testIAmAlive() throws Exception {
		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(false);
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();

		final BlockingQueue<Packet> blockingQueue = new LinkedBlockingQueue<Packet>(1);

		final String value = "10";
		final PacketListener callback = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ iAmAlive = (IQ) packet;
				iAmAlive.getElement().element("query")
						.addElement(ManagerPacketHelper.I_AM_ALIVE_PERIOD).setText(value);
				try {
					blockingQueue.put(packet);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				IQ createResultIQ = IQ.createResultIQ(iAmAlive);
				createResultIQ.getElement().addElement("query")
						.addElement(ManagerPacketHelper.I_AM_ALIVE_PERIOD)
						.setText(value);
				xmppClient.send(createResultIQ);
			}
		};

		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				if (packet.getFrom() == null) {
					return false;
				}
				return packet.getFrom().toBareJID()
						.equals(DefaultDataTestHelper.LOCAL_MANAGER_COMPONENT_URL);
			}
		}, callback);
		
		try {
			managerXmppComponent.iAmAlive();
		} catch (Exception e) {
			e.printStackTrace();
		}

		Packet packet = blockingQueue.poll(5, TimeUnit.SECONDS);
		Assert.assertEquals(value, packet.getElement().element("query")
				.element(ManagerPacketHelper.I_AM_ALIVE_PERIOD).getText());
		xmppClient.disconnect();
	}

	@Test
	public void testCallIAmAlive() throws Exception {
		final XMPPClient xmppClient = managerTestHelper.createXMPPClient();
		final Semaphore semaphore = new Semaphore(0);

		final PacketListener callbackIAmAlive = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ iAmAlive = (IQ) packet;
				semaphore.release();
				xmppClient.send(IQ.createResultIQ(iAmAlive));
			}
		};

		final PacketListener callbackWhoIsAlive = new PacketListener() {
			public void processPacket(Packet packet) {
				IQ whoIsAlive = (IQ) packet;
				List<FederationMember> aliveIds = new ArrayList<FederationMember>();
				aliveIds.add(new FederationMember(managerTestHelper
                        .getResources()));
				IQ iq = managerTestHelper.createWhoIsAliveResponse(
                        (ArrayList<FederationMember>) aliveIds, whoIsAlive);
				try {
					xmppClient.syncSend(iq);
				} catch (XMPPException e) {
					// No problem if exception is thrown
				}

			}
		};

		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				Element element = packet.getElement().element("query");
				if (element == null) {
					return false;
				}
				return element.getNamespaceURI().equals(
						DefaultDataTestHelper.IAMALIVE_NAMESPACE);
			}
		}, callbackIAmAlive);

		xmppClient.on(new PacketFilter() {
			@Override
			public boolean accept(Packet packet) {
				Element element = packet.getElement().element("query");
				if (element == null) {
					return false;
				}
				return element.getNamespaceURI().equals(
						DefaultDataTestHelper.WHOISALIVE_NAMESPACE);
			}
		}, callbackWhoIsAlive);

		managerXmppComponent = managerTestHelper
				.initializeXMPPManagerComponent(true);
		Assert.assertTrue(semaphore.tryAcquire(10000, TimeUnit.MILLISECONDS));
		xmppClient.disconnect();
	}

	@After
	public void tearDown() throws ComponentException {
		managerTestHelper.shutdown();
	}
}
