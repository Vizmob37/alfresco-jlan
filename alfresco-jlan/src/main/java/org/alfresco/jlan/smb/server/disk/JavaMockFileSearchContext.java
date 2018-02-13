package org.alfresco.jlan.smb.server.disk;

import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.SearchContext;

public class JavaMockFileSearchContext extends SearchContext {

	public JavaMockFileSearchContext() {
		super();
	}

	@Override
	public int getResumeId() {
		return 0;
	}

	@Override
	public boolean hasMoreFiles() {
		return false;
	}

	@Override
	public boolean nextFileInfo(FileInfo info) {
		return false;
	}

	@Override
	public String nextFileName() {
		return null;
	}

	@Override
	public boolean restartAt(int resumeId) {
		return false;
	}

	@Override
	public boolean restartAt(FileInfo info) {
		return false;
	}

}
