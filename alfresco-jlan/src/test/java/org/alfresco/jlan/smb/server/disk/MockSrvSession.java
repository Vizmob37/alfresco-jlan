package org.alfresco.jlan.smb.server.disk;

import java.net.InetAddress;

import org.alfresco.jlan.server.NetworkServer;
import org.alfresco.jlan.server.SrvSession;

public class MockSrvSession extends SrvSession {

	public MockSrvSession(int sessId, NetworkServer srv, String proto, String remoteName) {
		super(sessId, srv, proto, remoteName);
	}

	@Override
	public InetAddress getRemoteAddress() {
		return InetAddress.getLoopbackAddress();
	}

	@Override
	public boolean useCaseSensitiveSearch() {
		return false;
	}

}
