package org.alfresco.jlan.smb.server.disk;

import static org.junit.Assert.*;

import org.alfresco.jlan.server.filesys.FileInfo;
import org.junit.Before;
import org.junit.Test;

public class JavaMockFileDiskDriverTest {

	private JavaMockFileDiskDriver driver;

	@Before
	public void initJavaMockFileDiskDriver() {
		driver = new JavaMockFileDiskDriver();
	}

	@Test
	public void testBuildFileInformation() {
		FileInfo fi = driver.buildFileInformation("foo", "bar");
		assertNotNull(fi);
		System.out.println(fi);
	}

	@Test
	public void testCloseFile() {
		driver.closeFile(sess, tree, file);
		fail("Not yet implemented");
	}

	@Test
	public void testCreateDirectory() {
		fail("Not yet implemented");
	}

	@Test
	public void testCreateFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testDeleteDirectory() {
		fail("Not yet implemented");
	}

	@Test
	public void testDeleteFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testFileExists() {
		fail("Not yet implemented");
	}

	@Test
	public void testFlushFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetFileInformation() {
		fail("Not yet implemented");
	}

	@Test
	public void testIsReadOnly() {
		fail("Not yet implemented");
	}

	@Test
	public void testMapPathString() {
		fail("Not yet implemented");
	}

	@Test
	public void testMapPathStringString() {
		fail("Not yet implemented");
	}

	@Test
	public void testOpenFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testReadFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testRenameFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testSeekFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testSetFileInformation() {
		fail("Not yet implemented");
	}

	@Test
	public void testStartSearch() {
		fail("Not yet implemented");
	}

	@Test
	public void testTruncateFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testWriteFile() {
		fail("Not yet implemented");
	}

	@Test
	public void testCreateContext() {
		fail("Not yet implemented");
	}

	@Test
	public void testTreeOpened() {
		fail("Not yet implemented");
	}

	@Test
	public void testTreeClosed() {
		fail("Not yet implemented");
	}

	@Test
	public void testGetGlobalCreateDateTime() {
		fail("Not yet implemented");
	}

}
