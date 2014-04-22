package org.fogbowcloud.manager.xmpp;

import java.util.Timer;
import java.util.TimerTask;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.ManagerFacade;
import org.fogbowcloud.manager.core.model.ResourcesInfo;
import org.jamppa.component.XMPPComponent;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.IQ.Type;

public class ManagerXmppComponent extends XMPPComponent {

	public static final String WHOISALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/whoisalive";
	public static final String IAMALIVE_NAMESPACE = "http://fogbowcloud.org/rendezvous/iamalive";
	private static long PERIOD = 100;
	private ManagerFacade managerFacade;
	private final Timer timer = new Timer();
	private String rendezvousAddress;
	
	public ManagerXmppComponent(String jid, String password, String server,
			int port, ManagerFacade managerFacade) {
		super(jid, password, server, port);
		this.managerFacade = managerFacade;
	}

	@Override
	public void connect() throws ComponentException {
		super.connect();
	}

	public void init() {
		callIamAlive();
	}
	
	public void iAmAlive() {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAddress);
		iq.setFrom(getJID());
		Element statusEl = iq.getElement()
				.addElement("query", IAMALIVE_NAMESPACE).addElement("status");
		ResourcesInfo resourcesInfo = managerFacade.getResourcesInfo();
		statusEl.addElement("cpu-idle").setText(resourcesInfo.getCpuIdle());
		statusEl.addElement("cpu-inuse").setText(resourcesInfo.getCpuInUse());
		statusEl.addElement("mem-idle").setText(resourcesInfo.getMemIdle());
		statusEl.addElement("mem-inuse").setText(resourcesInfo.getMemInUse());
		this.syncSendPacket(iq);
	}

	public void whoIsalive() {
		IQ iq = new IQ(Type.get);
		iq.setTo(rendezvousAddress);
		iq.setFrom(getJID());
		iq.getElement().addElement("query", WHOISALIVE_NAMESPACE);
		IQ response = (IQ) this.syncSendPacket(iq);
		managerFacade.getItemsFromIQ(response);
	}

	private void callIamAlive() {
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				iAmAlive();
				whoIsalive();
			}
		}, 0, PERIOD);
	}

	public void setRendezvousAddress(String address) {
		rendezvousAddress = address;
	}

	public ManagerFacade getManagerFacade() {
		return managerFacade;
	}

}
