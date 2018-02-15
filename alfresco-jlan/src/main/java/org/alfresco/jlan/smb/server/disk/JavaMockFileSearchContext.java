package org.alfresco.jlan.smb.server.disk;

import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.SearchContext;

public class JavaMockFileSearchContext extends SearchContext {

	int counter = 0;
	private FileInfo fileInfo;

	public JavaMockFileSearchContext(FileInfo fileInfo) {
		super();
		this.fileInfo = fileInfo;
	}

	@Override
	public int getResumeId() {
		return 0;
	}

	@Override
	public boolean hasMoreFiles() {
		return counter++ > 0 ? false : true;
	}

	@Override
	public boolean nextFileInfo(FileInfo info) {
		if (counter++ > 0) {
			return false;
		} else {
			info.copyFrom(fileInfo);
			return true;
		}
	}

	@Override
	public String nextFileName() {
		if (counter++ > 0) {
			return null;
		} else {
			return fileInfo.getFileName();
		}
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
