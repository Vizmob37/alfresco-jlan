package org.alfresco.jlan.smb.server.disk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.alfresco.jlan.server.filesys.AccessMode;
import org.alfresco.jlan.server.filesys.FileAction;
import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.NetworkFile;
import org.alfresco.jlan.server.filesys.PathNotFoundException;
import org.alfresco.jlan.server.filesys.TreeConnection;
import org.alfresco.jlan.server.filesys.pseudo.MemoryNetworkFile;
import org.junit.Before;
import org.junit.Test;

public class JavaMockFileDiskDriverTest {

	private static final String UTF_8 = "UTF-8";
	private static final String FOO_TXT = "foo.txt";
	private JavaMockFileDiskDriver driver;
	private MockSrvSession sess;
	private TreeConnection tc = null;
	private NetworkFile nf;
	private FileOpenParams fop;

	@Before
	public void initJavaMockFileDiskDriver() throws UnsupportedEncodingException {
		driver = new JavaMockFileDiskDriver();
		sess = new MockSrvSession(0, null, null, null);
		nf = new MockNetworkFile(FOO_TXT);
		FileInfo finfo = JavaMockFileDiskDriver.buildFileInformation(FOO_TXT);
		nf = new MemoryNetworkFile(FOO_TXT, FOO_TXT.getBytes(UTF_8), finfo);
		fop = new FileOpenParams(FOO_TXT, FileAction.NTOpen, AccessMode.ReadWrite, FileAttribute.Normal, 0);
	}

	@Test
	public void testBuildFileInformation() {
		FileInfo fi = driver.buildFileInformation(FOO_TXT);
		assertNotNull(fi);
		System.out.println(fi);
	}

	@Test
	public void testGetFileInformation() throws IOException {
		FileInfo info = driver.getFileInformation(sess, tc, FOO_TXT);
		assertEquals(FOO_TXT, info.getFileName());
	}

	@Test
	public void testCreateFile() throws IOException {
		driver.createFile(sess, tc, fop);
	}

	@Test
	public void testMapPathString() throws FileNotFoundException, PathNotFoundException {
		String result = driver.mapPath("foo/bar", "test");
		assertEquals("foo/bar/test", result);
	}

	@Test
	public void testOpenFile() throws IOException {
		driver.openFile(sess, tc, fop);
	}

	@Test
	public void testReadFile() throws IOException {
		byte[] buf = new byte[10];
		int read = driver.readFile(sess, tc, nf, buf, 0, 10, 0);
		byte[] expected = FOO_TXT.getBytes(UTF_8);

		assertEquals(expected.length, read);
	}

	@Test
	public void testCloseFile() throws IOException {
		driver.closeFile(sess, tc, nf);
	}

	@Test
	public void testCreateDirectory() throws IOException {
		driver.createDirectory(sess, tc, null);
	}

	@Test
	public void testDeleteDirectory() throws IOException {
		driver.deleteDirectory(sess, tc, "foo");
	}

	@Test
	public void testDeleteFile() throws IOException {
		driver.deleteFile(sess, tc, FOO_TXT);
	}

	@Test
	public void testFileExists() {
		int exists = driver.fileExists(sess, tc, FOO_TXT);
		assertTrue(exists != 0);
	}

	@Test
	public void testFlushFile() throws IOException {
		driver.flushFile(sess, tc, nf);
	}

	@Test
	public void testIsReadOnly() throws IOException {
		assertFalse(driver.isReadOnly(sess, null));
	}

	@Test
	public void testRenameFile() throws IOException {
		driver.renameFile(sess, tc, "foo.old", "foo.new");
	}

	@Test
	public void testSeekFile() throws IOException {
		driver.seekFile(sess, tc, nf, 0, 1);
	}

	@Test
	public void testSetFileInformation() throws IOException {
		driver.setFileInformation(sess, tc, FOO_TXT, null);
	}

	@Test
	public void testStartSearch() throws FileNotFoundException {
		driver.startSearch(sess, tc, "foo/bar", 0);
	}

	@Test
	public void testTruncateFile() throws IOException {
		driver.truncateFile(sess, tc, nf, 0);
	}

	@Test
	public void testWriteFile() throws IOException {
		driver.writeFile(sess, tc, nf, new byte[0], 0, 0, 0);
	}

}
