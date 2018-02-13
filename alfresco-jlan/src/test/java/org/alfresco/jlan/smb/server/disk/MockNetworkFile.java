package org.alfresco.jlan.smb.server.disk;

import java.io.IOException;

import org.alfresco.jlan.server.filesys.NetworkFile;

public class MockNetworkFile extends NetworkFile {

	public MockNetworkFile(String name) {
		super(name);
	}

	@Override
	public void openFile(boolean createFlag) throws IOException {
	}

	@Override
	public int readFile(byte[] buf, int len, int pos, long fileOff) throws IOException {
		return 0;
	}

	@Override
	public void writeFile(byte[] buf, int len, int pos, long fileOff) throws IOException {
	}

	@Override
	public long seekFile(long pos, int typ) throws IOException {
		return 0;
	}

	@Override
	public void flushFile() throws IOException {

	}

	@Override
	public void truncateFile(long siz) throws IOException {

	}

	@Override
	public void closeFile() throws IOException {

	}

}
