package org.fogbowcloud.manager.core.ssh;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.fogbowcloud.manager.occi.core.Category;
import org.fogbowcloud.manager.occi.request.Request;
import org.fogbowcloud.manager.occi.request.RequestConstants;

public class DefaultSSHTunnel implements SSHTunnel {

	public static final String USER_DATA_ATT = "org.fogbowcloud.request.user-data";
	public static final String SSH_ADDRESS_ATT = "org.fogbowcloud.request.ssh-address";
	private Set<Integer> takenPorts = new HashSet<Integer>();
	
	public void create(Properties properties, Request request) throws FileNotFoundException, IOException {
		
		request.addCategory(new Category(RequestConstants.USER_DATA_TERM, 
				RequestConstants.SCHEME, RequestConstants.MIXIN_CLASS));
		
		String sshTunnelCmd = IOUtils.toString(new FileInputStream("bin/fogbow-inject-tunnel"));
		String sshHost = properties.getProperty("ssh_tunnel_host");
		
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_USER#", properties.getProperty("ssh_tunnel_user"));
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_HOST#", sshHost);
		String[] portRange = properties.getProperty("ssh_tunnel_port_range").split(":");
		
		Integer portFloor = Integer.parseInt(portRange[0]);
		Integer portCeiling = Integer.parseInt(portRange[1]);
		
		Integer sshPort = null;
		
		for (Integer i = portFloor; i <= portCeiling; i++) {
			if (!takenPorts.contains(i) && available(i)) {
				sshPort = i;
				takenPorts.add(i);
				break;
			}
		}
		if (sshPort == null) {
			throw new IllegalStateException("No SSH port available for reverse tunnelling");
		}
		
		sshTunnelCmd = sshTunnelCmd.replace("#REMOTE_PORT#", sshPort.toString());
		request.putAttValue(USER_DATA_ATT, Base64.encodeBase64URLSafeString(
				sshTunnelCmd.getBytes(Charsets.UTF_8)));
		request.putAttValue(SSH_ADDRESS_ATT, sshHost + ":" + sshPort);
	}
	
	public void release(Request request) {
		String sshAddress = request.getAttValue(SSH_ADDRESS_ATT);
		if (sshAddress == null) {
			return;
		}
		String[] sshAddressSplit = sshAddress.split(":");
		int sshPort = Integer.parseInt(sshAddressSplit[1]);
		takenPorts.remove(sshPort);
	}
	
	private static boolean available(int port) {
		ServerSocket ss = null;
		DatagramSocket ds = null;
		try {
			ss = new ServerSocket(port);
			ss.setReuseAddress(true);
			ds = new DatagramSocket(port);
			ds.setReuseAddress(true);
			return true;
		} catch (IOException e) {
		} finally {
			if (ds != null) {
				ds.close();
			}
			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {
					/* should not be thrown */
				}
			}
		}
		return false;
	}
}
