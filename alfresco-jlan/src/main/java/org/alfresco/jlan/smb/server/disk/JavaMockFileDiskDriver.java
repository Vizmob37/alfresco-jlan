/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.alfresco.jlan.smb.server.disk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.core.DeviceContext;
import org.alfresco.jlan.server.core.DeviceContextException;
import org.alfresco.jlan.server.filesys.DiskDeviceContext;
import org.alfresco.jlan.server.filesys.DiskInterface;
import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileName;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.FileStatus;
import org.alfresco.jlan.server.filesys.FileSystem;
import org.alfresco.jlan.server.filesys.NetworkFile;
import org.alfresco.jlan.server.filesys.PathNotFoundException;
import org.alfresco.jlan.server.filesys.SearchContext;
import org.alfresco.jlan.server.filesys.TreeConnection;
import org.alfresco.jlan.server.filesys.pseudo.MemoryNetworkFile;
import org.springframework.extensions.config.ConfigElement;

/**
 * Mock Disk interface implementation that uses the java.io.File class.
 * 
 * The behavior is as follows:<br>
 * 
 * <b>Directory operations</b> are generally not supported.
 * <ul>
 * <li>Create: not supported (does nothing)
 * <li>Delete: not supported (does nothing)
 * <li>List: return empty list
 * <li>Search: return empty list
 * </ul>
 * <b>Files</b>
 * <ul>
 * Create: does nothing, but reports 'success' (Like /dev/null)
 * <li>Open: creates a temporary in-memory file of given type and contents of
 * the file base name.
 * <li>Close: does nothing/frees resources allocated in Open()
 * <li>Delete: does nothing
 * <li>Get File Info: first creates temporary in-memory file (identical to
 * 'Open') and then returns data based on that operation
 * </ul>
 * 
 *
 * @author marko
 */

public class JavaMockFileDiskDriver implements DiskInterface {
	private static final char DOS_SEPARATOR = '\\';

	Logger logger = Logger.getLogger(JavaMockFileDiskDriver.class.getName());

	// SMB date used as the creation date/time for all files
	protected static long _globalCreateDate = System.currentTimeMillis();

	public JavaMockFileDiskDriver() {
		super();
		System.out.println("Mock init.");
		logger.finest("Driver loaded.");
	}

	/**
	 * Close the specified file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file details
	 * @exception IOException
	 */
	public void closeFile(SrvSession sess, TreeConnection tree, NetworkFile file) throws java.io.IOException {
		// file.closeFile();
		logger.log(Level.FINEST, "Close file ''{0}''", file.getFullName());
	}

	/**
	 * Create a new directory
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param params
	 *            Directory parameters
	 * @exception IOException
	 */
	public void createDirectory(SrvSession sess, TreeConnection tree, FileOpenParams params) throws java.io.IOException {

		logger.log(Level.FINE, "Create directory ''{0}''", params != null ? params.getPath() : "<unknown>");
	}

	/**
	 * Create a new file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param params
	 *            File open parameters
	 * @return NetworkFile
	 * @exception IOException
	 */
	public NetworkFile createFile(SrvSession sess, TreeConnection tree, FileOpenParams params) throws java.io.IOException {

		// Actual file creation not performed!
		logger.log(Level.FINE, "Create file ''{0}''", params.getPath());

		File file = new File(params.getPath());
		JavaNetworkFile netFile = new JavaNetworkFile(file, params.getPath());
		netFile.setGrantedAccess(NetworkFile.READWRITE);
		netFile.setFullName(params.getPath());

		return netFile;
	}

	/**
	 * Delete a directory
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param dir
	 *            Path of directory to delete
	 * @exception IOException
	 */
	public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir) throws java.io.IOException {
		logger.log(Level.FINE, "Delete directory ''{0}''", dir);
	}

	/**
	 * Delete a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param name
	 *            Name of file to delete
	 * @exception IOException
	 */
	public void deleteFile(SrvSession sess, TreeConnection tree, String name) throws java.io.IOException {
		logger.log(Level.FINE, "Delete file ''{0}''", name);
	}

	/**
	 * Check if the specified file exists, and it is a file.
	 *
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param name
	 *            File name
	 * @return int
	 */
	public int fileExists(SrvSession sess, TreeConnection tree, String name) {
		logger.log(Level.FINE, "Requested existence of file ''{0}''", name);

		return FileStatus.FileExists;
	}

	/**
	 * Flush buffered data for the specified file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file
	 * @exception IOException
	 */
	public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file) throws java.io.IOException {
		// Do nothing
		logger.log(Level.FINEST, "Flush file ''{0}''", file.getFullName());
	}

	/**
	 * Return file information about the specified file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param name
	 *            File name
	 * @return SMBFileInfo
	 * @exception IOException
	 */
	public FileInfo getFileInformation(SrvSession sess, TreeConnection tree, String name) throws java.io.IOException {

		logger.log(Level.FINE, "Get file information of ''{0}''", name);
		FileInfo info = buildFileInformation(name);

		return info;
	}

	/**
	 * Determine if the disk device is read-only.
	 *
	 * @param sess
	 *            Session details
	 * @param ctx
	 *            Device context
	 * @return true if the device is read-only, else false
	 * @exception IOException
	 *                If an error occurs.
	 */
	public boolean isReadOnly(SrvSession sess, DeviceContext ctx) throws java.io.IOException {

		// All mock files are writable.
		return false;
	}

	/**
	 * Open a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param params
	 *            File open parameters
	 * @return NetworkFile
	 * @exception IOException
	 */
	public NetworkFile openFile(SrvSession sess, TreeConnection tree, FileOpenParams params) throws java.io.IOException {

		// Requested resource is considered always a file, never a directory.		
		logger.log(Level.FINEST, "Open file ''{0}''", params.getPath());

		//File file = new File(params.getPath());
		//NetworkFile netFile = new JavaNetworkFile(file, params.getPath());
		
		String fileName = params.getPath();
		NetworkFile netFile = new MemoryNetworkFile(fileName, fileName.getBytes("UTF-8"),buildFileInformation(fileName));
		netFile.setFullName(params.getPath());

		return netFile;
	}

	/**
	 * Read a block of data from a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file
	 * @param buf
	 *            Buffer to return data to
	 * @param bufPos
	 *            Starting position in the return buffer
	 * @param siz
	 *            Maximum size of data to return
	 * @param filePos
	 *            File offset to read data
	 * @return Number of bytes read
	 * @exception IOException
	 */
	public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufPos, int siz, long filePos) throws java.io.IOException {

		// int rdlen = file.readFile(buf, siz, bufPos, filePos);

		// Use file name as it's contents!
		byte[] contents = file.getName().getBytes("UTF-8");

		if (filePos >= contents.length)
			return 0;

		int rdlen = Math.min(siz, contents.length);
		System.arraycopy(contents, (int) filePos, buf, bufPos, rdlen);

		return rdlen;
	}

	/**
	 * Rename a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param oldName
	 *            Existing file name
	 * @param newName
	 *            New file name
	 * @exception IOException
	 */
	public void renameFile(SrvSession sess, TreeConnection tree, String oldName, String newName) throws java.io.IOException {
		// Do nothing
		logger.log(Level.FINE, "Rename file ''{0}'' to ''{0}''", new Object[] { oldName, newName });
	}

	/**
	 * Seek to the specified point within a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file
	 * @param pos
	 *            New file position
	 * @param typ
	 *            Seek type
	 * @return New file position
	 * @exception IOException
	 */
	public long seekFile(SrvSession sess, TreeConnection tree, NetworkFile file, long pos, int typ) throws java.io.IOException {

		// Check that the network file is our type
		// return file.seekFile(pos, typ);
		logger.log(Level.FINEST, "Perform file seek of ''{0}'' to {1}", new Object[] { file.getFullName(), pos });

		return pos;
	}

	/**
	 * Set file information
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param name
	 *            File name
	 * @param info
	 *            File information to be set
	 * @exception IOException
	 */
	public void setFileInformation(SrvSession sess, TreeConnection tree, String name, FileInfo info) throws java.io.IOException {
		logger.log(Level.FINEST, "Set file information of ''{0}''", info != null ? info.getFileName() : "<unknown>");
	}

	/**
	 * Start a file search
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param searchPath
	 *            Search path, may include wildcards
	 * @param attrib
	 *            Search attributes
	 * @return SearchContext
	 * @exception FileNotFoundException
	 */
	public SearchContext startSearch(SrvSession sess, TreeConnection tree, String searchPath, int attrib) throws java.io.FileNotFoundException {

		return new JavaMockFileSearchContext();
	}

	/**
	 * Truncate a file to the specified size
	 * 
	 * @param sess
	 *            Server session
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file details
	 * @param siz
	 *            New file length
	 * @exception java.io.IOException
	 *                The exception description.
	 */
	public void truncateFile(SrvSession sess, TreeConnection tree, NetworkFile file, long siz) throws IOException {
		logger.log(Level.FINEST, "Truncate file ''{0}''", file.getFullName());
	}

	/**
	 * Write a block of data to a file
	 * 
	 * @param sess
	 *            Session details
	 * @param tree
	 *            Tree connection
	 * @param file
	 *            Network file
	 * @param buf
	 *            Data to be written
	 * @param bufoff
	 *            Offset of data within the buffer
	 * @param siz
	 *            Number of bytes to be written
	 * @param fileoff
	 *            Offset within the file to start writing the data
	 */
	public int writeFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufoff, int siz, long fileoff) throws java.io.IOException {

		// Do nothing
		logger.log(Level.FINEST, "Write to file ''{0}'' {1} bytes", new Object[] { file.getFullName(), siz });

		return siz;
	}

	/**
	 * Parse and validate the parameter string and create a device context for
	 * this share
	 * 
	 * @param shareName
	 *            String
	 * @param args
	 *            ConfigElement
	 * @return DeviceContext
	 * @exception DeviceContextException
	 */
	public DeviceContext createContext(String shareName, ConfigElement args) throws DeviceContextException {
		DiskDeviceContext ctx = null;

		logger.log(Level.FINEST, "Create context on ''{0}''", shareName);
		ConfigElement path = args.getChild("LocalPath");

		logger.log(Level.FINEST, "Path is ''{0}''", path);
		if (path != null) {
			File rootDir = new File(path.getValue());

			ctx = new DiskDeviceContext(rootDir.getAbsolutePath());
			ctx.setFilesystemAttributes(FileSystem.CasePreservedNames + FileSystem.UnicodeOnDisk);

			return ctx;
		}

		// Required parameters not specified

		throw new DeviceContextException("LocalPath parameter not specified");
	}

	/**
	 * Connection opened to this disk device
	 * 
	 * @param sess
	 *            Server session
	 * @param tree
	 *            Tree connection
	 */
	public void treeOpened(SrvSession sess, TreeConnection tree) {
	}

	/**
	 * Connection closed to this device
	 * 
	 * @param sess
	 *            Server session
	 * @param tree
	 *            Tree connection
	 */
	public void treeClosed(SrvSession sess, TreeConnection tree) {
	}

	/**
	 * Return the global file creation date/time
	 * 
	 * @return long
	 */
	public final static long getGlobalCreateDateTime() {
		return _globalCreateDate;
	}

	/**
	 * Build the file information for the specified file/directory, if it
	 * exists.
	 *
	 * @param path
	 *            String
	 * @param relPath
	 *            String
	 * @return FileInfo
	 */
	FileInfo buildFileInformation(String path) {
		long fdate = System.currentTimeMillis();
		FileInfo finfo;

		logger.log(Level.FINE, "Building file information of ''{0}''", path);

		String[] pathStr = FileName.splitPath(path, DOS_SEPARATOR);

		if (pathStr[1] != null) {
			String fileName = pathStr[1];
			String contents = fileName;

			long flen = contents.length();
			long alloc = (flen + 512L) & 0xFFFFFFFFFFFFFE00L;
			int fattr = 0;

			finfo = new FileInfo(fileName, flen, fattr);
			finfo.setAllocationSize(alloc);
			finfo.setFileId(fileName.hashCode());
		} else {
			finfo = new FileInfo(path, 0, FileAttribute.Directory);
			finfo.setFileId(path.hashCode());
		}

		finfo.setModifyDateTime(fdate);
		finfo.setCreationDateTime(fdate);
		finfo.setChangeDateTime(fdate);

		logger.log(Level.FINEST, "FileInfo: {0}", finfo.toString());

		return finfo;
	}

	/**
	 * Map the input path to a real path, this may require changing the case of
	 * various parts of the path. The base path is not checked, it is assumed to
	 * exist.
	 *
	 * @param base
	 *            java.lang.String
	 * @param path
	 *            java.lang.String
	 * @return java.lang.String
	 * @exception java.io.FileNotFoundException
	 *                The path could not be mapped to a real path.
	 * @exception PathNotFoundException
	 *                Part of the path is not valid
	 */
	protected final String mapPath(String base, String path) throws java.io.FileNotFoundException, PathNotFoundException {
		return new File(base, path).getPath();
	}

}
