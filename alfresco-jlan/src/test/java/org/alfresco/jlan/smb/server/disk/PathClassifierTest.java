package org.alfresco.jlan.smb.server.disk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.alfresco.jlan.server.filesys.FileName;
import org.apache.commons.io.FilenameUtils;
import org.junit.Test;

/**
 * The paths are all Windows-type backslash!
 * 
 * @author marko
 *
 */
public class PathClassifierTest {
	static final char DOS_SEPARATOR = '\\';
	@Test
	public void test() {
		String path = "\\bar\\foo.txt";
		String[] pathStr = FileName.splitPath(path, DOS_SEPARATOR);

		System.out.println(Arrays.toString(pathStr));

		assertEquals(2, pathStr.length);
	}

	@Test
	public void test2() {
		String path = "\\foo\\bar\\test.txt";
		String[] pathStr = FileName.splitPath(path, DOS_SEPARATOR);

		System.out.println(Arrays.toString(pathStr));

		assertEquals(2, pathStr.length);
	}

	@Test
	public void test3() {
		String path = "\\test.txt";
		String[] pathStr = FileName.splitPath(path, DOS_SEPARATOR);

		System.out.println(Arrays.toString(pathStr));

		assertEquals(2, pathStr.length);
	}

	@Test
	public void isPathFileTest() throws Exception {

		assertTrue(PathClassifier.isFilePath("\\foo\\bar\\test.txt"));
		assertTrue(PathClassifier.isFilePath("\\test.txt"));
		assertTrue(PathClassifier.isFilePath("test.txt"));

		assertFalse(PathClassifier.isFilePath("\\foo\\bar\\test"));
		assertFalse(PathClassifier.isFilePath("\\foo"));
		assertFalse(PathClassifier.isFilePath("\\"));

	}

	@Test
	public void getDirPathTest() throws Exception {
		assertEquals("foo", PathClassifier.getDirPath("\\foo\\bar.txt"));
		assertEquals("\\foo", PathClassifier.getDirPath("\\foo"));
		assertEquals("\\foo", PathClassifier.getDirPath("\\foo\\"));
	}

	@Test
	public void testClassifyPath() throws Exception {
		String path = "\\foo\\bar\\test.txt";

		assertTrue(PathClassifier.isFilePath(path));
		assertEquals("foo\\bar", PathClassifier.getDirPath(path));
		assertEquals("test.txt", PathClassifier.getFileName(path));

		boolean isExt = FilenameUtils.isExtension("test.txt", Arrays.asList("txt", "doc", "docx", "pdf"));
		assertTrue(isExt);
	}

	@Test
	public void test2ClassifyPath() throws Exception {
		String path = "\\foo\\bar";

		assertFalse(PathClassifier.isFilePath(path));
		assertEquals("\\foo\\bar", PathClassifier.getDirPath(path));
		assertEquals(null, PathClassifier.getFileName(path));

	}

}
