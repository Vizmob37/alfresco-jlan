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

package org.alfresco.jlan.server.filesys.db.oracle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import org.alfresco.jlan.debug.Debug;
import org.alfresco.jlan.server.config.InvalidConfigurationException;
import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileExistsException;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileName;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.FileStatus;
import org.alfresco.jlan.server.filesys.FileType;
import org.alfresco.jlan.server.filesys.cache.FileState;
import org.alfresco.jlan.server.filesys.db.DBDataDetails;
import org.alfresco.jlan.server.filesys.db.DBDataDetailsList;
import org.alfresco.jlan.server.filesys.db.DBDataInterface;
import org.alfresco.jlan.server.filesys.db.DBDeviceContext;
import org.alfresco.jlan.server.filesys.db.DBException;
import org.alfresco.jlan.server.filesys.db.DBFileInfo;
import org.alfresco.jlan.server.filesys.db.DBInterface;
import org.alfresco.jlan.server.filesys.db.DBObjectIdInterface;
import org.alfresco.jlan.server.filesys.db.DBQueueInterface;
import org.alfresco.jlan.server.filesys.db.DBSearchContext;
import org.alfresco.jlan.server.filesys.db.JdbcDBInterface;
import org.alfresco.jlan.server.filesys.db.ObjectIdFileLoader;
import org.alfresco.jlan.server.filesys.db.RetentionDetails;
import org.alfresco.jlan.server.filesys.loader.CachedFileInfo;
import org.alfresco.jlan.server.filesys.loader.FileRequest;
import org.alfresco.jlan.server.filesys.loader.FileRequestQueue;
import org.alfresco.jlan.server.filesys.loader.FileSegment;
import org.alfresco.jlan.server.filesys.loader.FileSegmentInfo;
import org.alfresco.jlan.server.filesys.loader.MultipleFileRequest;
import org.alfresco.jlan.server.filesys.loader.SingleFileRequest;
import org.alfresco.jlan.smb.server.ntfs.StreamInfo;
import org.alfresco.jlan.smb.server.ntfs.StreamInfoList;
import org.alfresco.jlan.util.MemorySize;
import org.alfresco.jlan.util.StringList;
import org.alfresco.jlan.util.WildCard;
import org.alfresco.jlan.util.db.DBConnectionPool;
import org.springframework.extensions.config.ConfigElement;

/**
 * Oracle Base Database Interface Class
 * 
 * <p>Oracle specific implementation of the database interface used by the database filesystem driver (DBDiskDriver).
 *
 * @author gkspencer
 */
public class OracleDBInterface extends JdbcDBInterface implements DBQueueInterface, DBDataInterface, DBObjectIdInterface {

  //  Constants
  //
  //  Default database table names
  
  public final static String DefaultDBPrefix    = "JLAN";
  
  public final static String OraFileSysTable       = "FileSys";
  public final static String OraStreamsTable       = "Streams";
  public final static String OraRetentionTable     = "Retain";
  public final static String OraQueueTable         = "Queue";
  public final static String OraTransactQueueTable = "TransQueue";
  public final static String OraDataTable          = "Data";
  public final static String OraJarDataTable       = "JarData";
  public final static String OraObjectIdTable      = "ObjectIds";
  public final static String OraSymLinkTable       = "SymLinks";
  
	//	Memory buffer maximum size
	
	public final static long MaxMemoryBuffer	= MemorySize.MEGABYTE / 2;	//	1/2Mb
  
  //  Lock file name, used to check if server shutdown was clean or not
  
  public final static String LockFileName = "OracleLoader.lock";
  
	//	Database connection and prepared statement used to write file requests to the queue tables
	
	private Connection m_dbConn;
	private PreparedStatement m_reqStmt;
	private PreparedStatement m_tranStmt;

  //  Database table prefix
  
  private String m_dbTablePrefix = DefaultDBPrefix;
  
  /**
   * Default constructor
   */
  public OracleDBInterface() {
    super();
  }
  
  /**
   * Return the database interface name
   * 
   * @return String
   */
  public String getDBInterfaceName() {
    return "Oracle";
  }

  /**
   * Get the supported database features mask
   * 
   * @return int
   */
  protected int getSupportedFeatures() {
    
    //	Determine the available database interface features
    
    return FeatureNTFS + FeatureRetention + FeatureSymLinks + FeatureQueue + FeatureData + FeatureJarData + FeatureObjectId;
  }
  
  /**
   * Return the database table prefix
   * 
   * @return String
   */
  public final String getTablePrefix() {
    return m_dbTablePrefix;
  }
  
  /**
   * Initialize the database interface
   * 
   * @param dbCtx DBDeviceContext
   * @param params ConfigElement
   * @exception InvalidConfigurationException
   */
  public void initializeDatabase(DBDeviceContext dbCtx, ConfigElement params)
  	throws InvalidConfigurationException {

    //	Set the JDBC driver class, must be set before the connection pool is created
		
	  setDriverName("oracle.jdbc.driver.OracleDriver");
    
    //  Save the device context
    
    m_dbCtx = dbCtx;
    
    //  Parse the standard JDBC database interface parameters
    
    ConfigElement nameVal = null;
    
    nameVal = params.getChild("DSN");
    if ( nameVal != null)
      m_dsn = nameVal.getValue();
      
    nameVal = params.getChild("Username");
    if ( nameVal != null)
      m_userName = nameVal.getValue();
      
    nameVal = params.getChild("Password");
    if ( nameVal != null)
      m_password = nameVal.getValue();
    
    nameVal = params.getChild( "TablePrefix");
    if ( nameVal != null) {
      m_dbTablePrefix = nameVal.getValue();
      
      if ( m_dbTablePrefix == null || m_dbTablePrefix.length() == 0)
        throw new InvalidConfigurationException( "Invalid database table prefix, empty string");
      else if ( m_dbTablePrefix.length() > 15)
        throw new InvalidConfigurationException( "Invalid database table prefix, too long");
      
      //  Validate the database prefix characters, only allow A-Z, a-z, 0-9 and underscore
      
      for ( int i = 0; i < m_dbTablePrefix.length(); i++) {
        char curChar = m_dbTablePrefix.charAt( i);
        if ( curChar != '_' && Character.isDigit( curChar) == false && Character.isLetter( curChar) == false)
          throw new InvalidConfigurationException( "Invalid character in database table prefix, '" + curChar + "'");
      }
    }
    
    nameVal = params.getChild("FileSystemTable");
    if ( nameVal != null)
      m_structTable = nameVal.getValue();
    else
      m_structTable = getTablePrefix() + OraFileSysTable;

    nameVal = params.getChild("StreamsTable");
    if ( nameVal != null)
      m_streamTable = nameVal.getValue();
    else
      m_streamTable = getTablePrefix() + OraStreamsTable;
      
    nameVal = params.getChild("RetentionTable");
    if ( nameVal != null)
      m_retentionTable = nameVal.getValue();
    else
      m_retentionTable = getTablePrefix() + OraRetentionTable;

    nameVal = params.getChild("QueueTable");
    if ( nameVal != null)
      m_queueTable = nameVal.getValue();
    else
      m_queueTable = getTablePrefix() + OraQueueTable;

    nameVal = params.getChild("TransactQueueTable");
    if ( nameVal != null)
      m_transactTable = nameVal.getValue();
    else
      m_transactTable = getTablePrefix() + OraTransactQueueTable;
    
    nameVal = params.getChild("DataTable");
    if ( nameVal != null)
      m_dataTable = nameVal.getValue();
    else
      m_dataTable = getTablePrefix() + OraDataTable;
    
    nameVal = params.getChild("JarDataTable");
    if ( nameVal != null)
      m_jarDataTable = nameVal.getValue();
    else
      m_jarDataTable = getTablePrefix() + OraJarDataTable;

    nameVal = params.getChild("ObjectIdTable");
    if ( nameVal != null)
      m_objectIdTable = nameVal.getValue();
    else
      m_objectIdTable = getTablePrefix() + OraObjectIdTable;
    
    nameVal = params.getChild( "SymLinksTable");
    if ( nameVal != null)
      m_symLinkTable = nameVal.getValue();
    else
      m_symLinkTable = getTablePrefix() + OraSymLinkTable;
    
    //  Check if the database connection pool initial and maximum size has been specified

    nameVal = params.getChild("ConnectionPool");
        
    if ( nameVal != null) {
      try {
        
        //  Check for a single value or split initial/maximum values
        
        String numVal = nameVal.getValue();
        int pos = numVal.indexOf(':');
        
        if ( pos == -1) {
          
          //  Use the same number of read and write worker threads

          m_dbMaxConns = Integer.parseInt(numVal);
        }
        else {
          
          //  Split the string value into read and write values, and convert to integers
          
          String val = numVal.substring(0,pos);
          m_dbInitConns = Integer.parseInt(val);
          
          val = numVal.substring(pos + 1);
          m_dbMaxConns = Integer.parseInt(val);
        }
        
        //  Range check the initial/maximum connection pool sizes
        
        if ( m_dbInitConns < DBConnectionPool.MinimumConnections ||
             m_dbInitConns > m_dbMaxConns)
          throw new InvalidConfigurationException("Database interface invalid initial connections value");
          
        if ( m_dbMaxConns > DBConnectionPool.MaximumConnections ||
             m_dbMaxConns < m_dbInitConns)
          throw new InvalidConfigurationException("Database interface invalid maximum connections value");
      }
      catch (NumberFormatException ex) {
        throw new InvalidConfigurationException("Database interface invalid ConnectionPool value, " + ex.toString());
      }
    }

    //  Check if the database online check interval has been specified
    
    nameVal = params.getChild("OnlineCheckInterval");
    if ( nameVal != null) {
      try {
        
        // Parse the online check interval value
        
        m_onlineCheckInterval = Integer.parseInt( nameVal.getValue());
        if ( m_onlineCheckInterval < 1 || m_onlineCheckInterval > 30)
          throw new InvalidConfigurationException( "Database online check interval out of valid range (1-30");
      }
      catch ( NumberFormatException ex) {
        throw new InvalidConfigurationException("Database online check interval value invalid, " + nameVal.getValue());
      }
    }
    
    //  Check if debug output is enabled
    
    if ( params.getChild("Debug") != null)
      m_debug = true;
    
    //  Check if SQL debug output is enabled
    
    if ( params.getChild("SQLDebug") != null)
      m_sqlDebug = true;
    
    //  Copy the retention period from the context, value will be -1 if not enabled
    
    m_retentionPeriod = dbCtx.getRetentionPeriod();

    //	Create the database connection pool
		
		try {
		  createConnectionPool();
		}
		catch (Exception ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println("[Oracle] Error creating connection pool, " + ex.toString());
		  
		  //	Rethrow the exception
		  
		  throw new InvalidConfigurationException("Failed to create connection pool, " + ex.getMessage()); 
		}

    //	Check if the file system table exists

		Connection conn = null;
		
    try {

      //	Open a connection to the database

      conn = getConnection();
      
      DatabaseMetaData dbMeta = conn.getMetaData();
      ResultSet rs = dbMeta.getTables(null, null, null, null);

      boolean foundStruct  = false;
      boolean foundStream  = false;
      boolean foundRetain  = false;
      boolean foundQueue   = false;
      boolean foundTrans   = false;
      boolean foundData    = false;
      boolean foundJarData = false;
      boolean foundObjId   = false;
      boolean foundSymLink = false;

      while (rs.next()) {
      	
      	//	Get the table name
      	
        String tblName = rs.getString("TABLE_NAME");
        
        //	Check if we found the filesystem structure or streams table
        
        if (tblName.equalsIgnoreCase(getFileSysTableName()))
          foundStruct = true;
        else if ( hasStreamsTableName() && tblName.equalsIgnoreCase(getStreamsTableName()))
        	foundStream = true;
        else if ( hasRetentionTableName() && tblName.equalsIgnoreCase(getRetentionTableName()))
          foundRetain = true;
        else if ( hasDataTableName() && tblName.equalsIgnoreCase(getDataTableName()))
          foundData = true;
        else if ( hasJarDataTableName() && tblName.equalsIgnoreCase(getJarDataTableName()))
          foundJarData = true;
        else if ( hasQueueTableName() && tblName.equalsIgnoreCase(getQueueTableName()))
          foundQueue = true;
        else if ( hasTransactionTableName() && tblName.equalsIgnoreCase(getTransactionTableName()))
          foundTrans = true;
        else if ( hasObjectIdTableName() && tblName.equalsIgnoreCase(getObjectIdTableName()))
          foundObjId = true;
        else if ( hasSymLinksTableName() && tblName.equalsIgnoreCase( getSymLinksTableName()))
          foundSymLink = true;
      }

      //	Check if the file system structure table should be created

      if (foundStruct == false) {

        //	Create the file system structure table

        Statement stmt = conn.createStatement();

        stmt.execute("CREATE SEQUENCE " + getTablePrefix() + "FileId INCREMENT BY 1 START WITH 1 NOMAXVALUE NOCYCLE");
        
        stmt.execute("CREATE TABLE " + getFileSysTableName()
            + " (FileId NUMBER PRIMARY KEY, DirId NUMBER, FileName VARCHAR2(255) NOT NULL, FileSize NUMBER,"
            + "CreateDate TIMESTAMP, ModifyDate TIMESTAMP, AccessDate TIMESTAMP, ChangeDate TIMESTAMP, ReadOnlyFile NUMBER(1),"
            + "ArchivedFile NUMBER(1), DirectoryFile NUMBER(1), SystemFile NUMBER(1), HiddenFile NUMBER(1), IsSymLink NUMBER(1),"
            + " OwnerUid NUMBER(6), OwnerGid NUMBER(6), FileMode NUMBER(6), IsDeleted NUMBER(1) DEFAULT 0)");
            
        //	Create various indexes

        stmt.execute("CREATE INDEX " + getTablePrefix() + "IFileDirId ON " + getFileSysTableName() + "(FileName, DirId)");
        stmt.execute("CREATE INDEX " + getTablePrefix() + "IDirId ON " + getFileSysTableName() + "(DirId)");
        stmt.execute("CREATE INDEX " + getTablePrefix() + "IDir ON " + getFileSysTableName() + "(DirId,DirectoryFile)");
        stmt.execute("CREATE INDEX " + getTablePrefix() + "IFileDirIdDir ON " + getFileSysTableName() + "(FileName,DirId,DirectoryFile)");
        
        stmt.close();
        
        //	DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getFileSysTableName());
      }

			//	Check if the file streams table should be created
			
			if ( isNTFSEnabled() && foundStream == false && getStreamsTableName() != null) {

				//	Create the file streams table

				Statement stmt = conn.createStatement();

				stmt.execute("CREATE SEQUENCE " + getTablePrefix() + "StreamId INCREMENT BY 1 START WITH 1 NOMAXVALUE NOCYCLE");
				
				stmt.execute("CREATE TABLE " + getStreamsTableName()
						+ " (StreamId NUMBER, FileId NUMBER PRIMARY KEY, StreamName VARCHAR2(255) NOT NULL, StreamSize NUMBER,"
						+ "CreateDate TIMESTAMP, ModifyDate TIMESTAMP, AccessDate TIMESTAMP)");
            
				stmt.close();
        
        //	DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getStreamsTableName());
			}
			
			//	Check if the retention table should be created
			
			if ( isRetentionEnabled() && foundRetain == false && getRetentionTableName() != null) {			
				
				//	Create the retention period data table
				
				Statement stmt = conn.createStatement();

				stmt.execute("CREATE TABLE " + getRetentionTableName() + " (FileId NUMBER NOT NULL PRIMARY KEY,"
				    + "StartDate TIMESTAMP, EndDate TIMESTAMP, PurgeFlag CHAR(1))");
				stmt.close();
        
        //	DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getRetentionTableName());
			}
			
			//	Check if the file loader queue table should be created
			
			if ( isQueueEnabled() && foundQueue == false && getQueueTableName() != null) {
			  
				//	Create the request queue data table
				
				Statement stmt = conn.createStatement();

				stmt.execute("CREATE SEQUENCE " + getTablePrefix() + "SeqNo INCREMENT BY 1 START WITH 1 NOMAXVALUE NOCYCLE");
				
				stmt.execute("CREATE TABLE " + getQueueTableName() + " (FileId NUMBER NOT NULL, StreamId INTEGER NOT NULL, ReqType NUMBER(2)," +
				    "SeqNo NUMBER PRIMARY KEY, TempFile VARCHAR2(512), VirtualPath VARCHAR2(512), QueuedAt TIMESTAMP, Attribs VARCHAR(512))");

				stmt.execute("CREATE INDEX " + getTablePrefix() + "IFileId ON " + getQueueTableName() + "(FileId)");
				stmt.execute("CREATE INDEX " + getTablePrefix() + "IFileIdType ON " + getQueueTableName() + "(FileId, ReqType)");

				stmt.close();
        
        //	DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getQueueTableName());
			}

			//	Check if the file loader transaction queue table should be created
			
			if ( isQueueEnabled() && foundTrans == false && getTransactionTableName() != null) {

			  //	Create the transaction request queue data table
				
				Statement stmt = conn.createStatement();

				stmt.execute("CREATE TABLE " + getTransactionTableName() + " (FileId NUMBER NOT NULL, StreamId NUMBER NOT NULL," +
				    "TranId NUMBER NOT NULL, ReqType NUMBER(2), TempFile VARCHAR2(512), VirtualPath VARCHAR2(512), QueuedAt TIMESTAMP," +
				    "Attribs VARCHAR(512), PRIMARY KEY (FileId,StreamId,TranId))");

				stmt.close();
        
        //	DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getTransactionTableName());
			}
			
			//	Check if the file data table should be created
			
			if ( isDataEnabled() && foundData == false && hasDataTableName()) {
			  
				//	Create the file data table
			
		    Statement stmt = conn.createStatement();
		
		    stmt.execute("CREATE TABLE " + getDataTableName() + " (FileId NUMBER NOT NULL, StreamId NUMBER NOT NULL, FragNo NUMBER, FragLen NUMBER, Data BLOB, JarFile CHAR(1), JarId NUMBER)");
		    
				stmt.execute("CREATE INDEX " + getTablePrefix() + "IDataFidStid ON " + getDataTableName() + " (FileId,StreamId)");
		    stmt.execute("CREATE INDEX " + getTablePrefix() + "IDataFileId ON " + getDataTableName() + " (FileId)");
		    stmt.execute("CREATE INDEX " + getTablePrefix() + "IDataFidFragNo ON " + getDataTableName() + " (FileId,FragNo)");

		    stmt.close();
        
        //	DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getDataTableName());
			}
			
			//	Check if the Jar file data table should be created
			
			if ( isJarDataEnabled() && foundJarData == false && hasJarDataTableName()) {
			  
			  //	Create the Jar file data table
			  
				Statement stmt = conn.createStatement();
				
				stmt.execute("CREATE SEQUENCE " + getTablePrefix() + "JarId INCREMENT BY 1 START WITH 1 NOMAXVALUE NOCYCLE");
				
				stmt.execute("CREATE TABLE " + getJarDataTableName() + " (JarId NUMBER PRIMARY KEY, Data BLOB)");

				stmt.close();
        
        //	DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getJarDataTableName());
			}

			//	Check if the file id/object id mapping table should be created
			
			if ( isObjectIdEnabled() && foundObjId == false && hasObjectIdTableName()) {
			  
			  //	Create the file id/object id mapping table
			  
				Statement stmt = conn.createStatement();
				
				stmt.execute("CREATE TABLE " + getObjectIdTableName() + " (FileId NUMBER NOT NULL, StreamId NUMBER NOT NULL, ObjectId VARCHAR2(128), PRIMARY KEY(FileId,StreamId))");

				stmt.close();
        
        //	DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getObjectIdTableName());
      }

      //  Check if the symbolic links table should be created
      
      if ( isSymbolicLinksEnabled() && foundSymLink == false && hasSymLinksTableName()) {
        
        //  Create the symbolic links table
        
        Statement stmt = conn.createStatement();
        
        stmt.execute("CREATE TABLE " + getSymLinksTableName() + " (FileId NUMBER NOT NULL PRIMARY KEY, SymLink VARCHAR(2048))");

        stmt.close();
        
        //  DEBUG
        
        if ( Debug.EnableInfo && hasDebug())
          Debug.println("[Oracle] Created table " + getSymLinksTableName());
      }
    }
    catch (Exception ex) {
      Debug.println("Error: " + ex.toString());
    }
    finally {

      //	Release the database connection
      
      if ( conn != null)
        releaseConnection(conn);
    }
  }
  
  /**
   * Check if a file/folder exists
   * 
   * @param dirId int
   * @param fname String
   * @return FileStatus.NotExist, FileStatus.FileExists or FileStatus.DirectoryExists
   * @throws DBException
   */
  public int fileExists(int dirId, String fname)
  	throws DBException {

    //	Check if the file exists, and whether it is a file or folder
    
    int sts = FileStatus.NotExist;
    
		Connection conn = null;
		Statement stmt = null;

    try {
      
      //	Get a connection to the database, create a statement for the database lookup
      
      conn = getConnection();
      stmt = conn.createStatement();
      
      String sql = "SELECT FileName,DirectoryFile FROM " + getFileSysTableName() + " WHERE DirId = " + dirId +
			 						 " AND FileName = '" + checkNameForSpecialChars(fname) + "'";
      
      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] File exists SQL: " + sql);
      
      //	Search for the file/folder
      
      ResultSet rs = stmt.executeQuery(sql);

      //	Check if a file record exists
      
      if (rs.next()) {
        
        //	Check if the record is for a file or folder
        
  	    if ( rs.getBoolean("DirectoryFile") == true)
  	      sts = FileStatus.DirectoryExists;
  	    else
  	      sts = FileStatus.FileExists;
      }

      //	Close the result set
      
      rs.close();
    }
    catch (Exception ex) {
    }
    finally {

			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			    	
      //	Release the database connection
      
      if ( conn != null)
      	releaseConnection(conn);
    }
    
    //	Return the status
    
    return sts;
  }

  /**
   * Create a file record for a new file or folder
   * 
   * @param fname String
   * @param dirId int
   * @param params FileOpenParams
   * @param retain boolean
   * @return int
   * @exception DBException
   * @exception FileExistsException
   */
  public int createFileRecord(String fname, int dirId, FileOpenParams params, boolean retain)
  	throws DBException, FileExistsException {

    //	Create a new file record for a file/folder and return a unique file id

    Connection conn = null;
    PreparedStatement pstmt = null;
    Statement stmt = null;

    int fileId = -1;
    
    try {

			//	Get a database connection
			
      conn = getConnection();

      //	Check if the file already exists in the database

			stmt = conn.createStatement();
      String chkFileName = checkNameForSpecialChars(fname);
      
			String qsql = "SELECT FileName FROM " + getFileSysTableName() + " WHERE FileName = '" + chkFileName + "' AND DirId = " + dirId;
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Create file SQL: " + qsql);
			
			//	Check if the file/folder already exists
			
      ResultSet rs = stmt.executeQuery(qsql);
      if (rs.next())
        throw new FileExistsException();

      //	Get a statement

			Timestamp timeNow = new Timestamp(System.currentTimeMillis());
			
      pstmt = conn.prepareStatement("INSERT INTO " + getFileSysTableName()
      														 + "(FileName,CreateDate,ModifyDate,AccessDate,DirId,DirectoryFile,ReadOnlyFile,"
      														 + "ArchivedFile,SystemFile,HiddenFile,FileSize,OwnerGid,OwnerUid,FileMode,IsSymLink,FileId)"
      														 + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?," + getTablePrefix() + "FileId.NEXTVAL)");
      pstmt.setString(1,chkFileName);
      pstmt.setTimestamp(2, timeNow);
      pstmt.setTimestamp(3, timeNow);
      pstmt.setTimestamp(4, timeNow);
      pstmt.setInt(5,dirId);
      pstmt.setBoolean(6, params.isDirectory());
      pstmt.setBoolean(7, FileAttribute.hasAttribute(params.getAttributes(),FileAttribute.ReadOnly));
      pstmt.setBoolean(8, FileAttribute.hasAttribute(params.getAttributes(),FileAttribute.Archive));
      pstmt.setBoolean(9, FileAttribute.hasAttribute(params.getAttributes(),FileAttribute.System));
      pstmt.setBoolean(10, FileAttribute.hasAttribute(params.getAttributes(),FileAttribute.Hidden));
      pstmt.setInt(11, 0);
      
      pstmt.setInt(12, params.hasGid() ? params.getGid() : 0);
      pstmt.setInt(13, params.hasUid() ? params.getUid() : 0);
      pstmt.setInt(14, params.hasMode() ? params.getMode() : 0);

      pstmt.setInt(15, params.isSymbolicLink() ? 1 : 0);

      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Create file SQL: " + pstmt.toString());
      
      //	Create an entry for the new file

      if (pstmt.executeUpdate() > 0) {

				//	Get the last insert id
				
				ResultSet rs2 = stmt.executeQuery("SELECT " + getTablePrefix() + "FileId.CURRVAL FROM DUAL");
				
				if ( rs2.next())
					fileId = rs2.getInt(1); 

				//	Check if the returned file id is valid
				
				if ( fileId == -1)
					throw new DBException("Failed to get file id for " + fname);
				
				//	If retention is enabled then create a retention record for the new file/folder
				
				if ( retain == true && isRetentionEnabled()) {

				  //	Create a retention record for the new file/directory

					Timestamp startDate = new Timestamp(System.currentTimeMillis());
					Timestamp endDate   = new Timestamp(startDate.getTime() + getRetentionPeriod());
					
					String rSql = "INSERT INTO " + getRetentionTableName() + " (FileId,StartDate,EndDate) VALUES (" + fileId + ",'" + startDate.toString() + "','" + endDate.toString() + "')";
					
					//	DEBUG
					
					if ( Debug.EnableInfo && hasSQLDebug())
					  Debug.println("[Oracle] Add retention record SQL: " + rSql);
					
					//	Add the retention record for the file/folder
					
					stmt.executeUpdate(rSql);
				}
				
        //  Check if the new file is a symbolic link
        
        if ( params.isSymbolicLink()) {
          
          //  Create the symbolic link record
          
          String symSql = "INSERT INTO " + getSymLinksTableName() + " (FileId, SymLink) VALUES (" + fileId + ",'" + params.getSymbolicLinkName() + "')";

          //  DEBUG
          
          if ( Debug.EnableInfo && hasSQLDebug())
            Debug.println("[Oracle] Create symbolic link SQL: " + symSql);
          
          //  Add the symbolic link record
          
          stmt.executeUpdate( symSql);
        }
        
				//	DEBUG
				
				if ( Debug.EnableInfo && hasDebug())
				  Debug.println("[Oracle] Created file name=" + fname + ", dirId=" + dirId + ", fileId=" + fileId);
      }
    }
    catch (Exception ex) {
      
      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println("[Oracle] Create file record error " + ex.getMessage());
      
      //	Rethrow the exception
      
      throw new DBException(ex.toString());
    }
    finally {
    	
    	//	Close the prepared statement
    	
      if (pstmt != null) {
        try {
          pstmt.close();
        }
        catch (Exception ex) {
        }
      }

			//	Close the query statement
			
			if ( stmt != null) {
				try {
					stmt.close();      
				}
				catch (Exception ex) {
				}
			}

      //	Release the database connection

			if ( conn != null)      
      	releaseConnection(conn);
    }
    
    //	Return the allocated file id
    
    return fileId;
  }

  /**
   * Create a stream record for a new file stream
   *
   * @param sname String
   * @param fid int
   * @return int
   * @exception DBException
   */
	public int createStreamRecord(String sname, int fid)
  	throws DBException {

		//	Create a new file stream attached to the specified file

		Connection conn = null;
		PreparedStatement stmt = null;
		Statement stmt2 = null;

		int streamId = -1;
		
		try {

			//	Get a database connection
		
			conn = getConnection();

			//	Get a statement

			long timeNow = System.currentTimeMillis();
		
			stmt = conn.prepareStatement("INSERT INTO " + getStreamsTableName()
							+ "(FileId,StreamName,CreateDate,ModifyDate,AccessDate,StreamSize,StreamId) VALUES (?,?,?,?,?,?," + getTablePrefix() +"StreamId.NEXTVAL)");
			stmt.setInt(1, fid);
			stmt.setString(2, sname);
			stmt.setLong(3, timeNow);
			stmt.setLong(4, timeNow);
			stmt.setLong(5, timeNow);
			stmt.setInt(6, 0);

			//	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Create stream SQL: " + stmt.toString());

			//	Create an entry for the new stream

			if (stmt.executeUpdate() > 0) {

				//	Get the stream id for the newly created stream

				stmt2 = conn.createStatement();
				ResultSet rs2 = stmt2.executeQuery("SELECT " + getTablePrefix() + "StreamId.CURRVAL FROM DUAL");
				
				if ( rs2.next())
					streamId = rs2.getInt(1); 
				rs2.close();
			}
		}
		catch (Exception ex) {
		  
		  //	DEBUG
		  
			if ( Debug.EnableError && hasDebug())
				Debug.println("[Oracle] Create file stream error " + ex.getMessage());
			
			//	Rethrow the exception
			
			throw new DBException(ex.toString());
		}
		finally {
		  
		  //	Close the statements
		  
			if (stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
				
			if ( stmt2 != null) {
				try {
					stmt2.close();
				}
				catch (Exception ex) {
				}
			}
    
			//	Release the database connection

			if ( conn != null)      
				releaseConnection(conn);
		}
    
    //	Return the allocated stream id
		
    return streamId;
  }

  /**
   * Delete a file or folder record
   * 
   * @param dirId int
   * @param fid int
   * @param markOnly boolean
   * @exception DBException
   */
  public void deleteFileRecord(int dirId, int fid, boolean markOnly)
  	throws DBException {
    
    //	Delete a file record from the database, or mark the file record as deleted
    
    Connection conn = null;
    Statement stmt = null;

    try {

      //	Get a connection to the database

      conn = getConnection();

      //	Delete the file entry from the database

      stmt = conn.createStatement();
      String sql = null;
      
      if ( markOnly == true)
				sql = "UPDATE " + getFileSysTableName() + " SET IsDeleted = 1 WHERE FileId = " + fid;
      else
      	sql = "DELETE FROM " + getFileSysTableName() + " WHERE FileId = " + fid;

      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Delete file SQL: " + sql);
      
      //	Delete the file/folder, or mark as deleted
      
      stmt.executeUpdate(sql);
    }
    catch (SQLException ex) {
      
      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println("[Oracle] Delete file error " + ex.getMessage());
      
      //	Rethrow the exception
      
      throw new DBException(ex.toString());
    }
    finally {
    	
			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}

      //	Release the database connection
      
      if (conn != null)
      	releaseConnection(conn);
    }      
  }

  /**
   * Delete a file stream record
   *
   * @param fid int
   * @param stid int
   * @param markOnly boolean
   * @exception DBException
   */
  public void deleteStreamRecord(int fid, int stid, boolean markOnly)
  	throws DBException {

    //	Delete a file stream from the database, or mark the stream as deleted

		Connection conn = null;
		Statement stmt  = null;

		try {

			//	Get a database connection
	
			conn = getConnection();

			//	Get a statement

			stmt = conn.createStatement();
			String sql = "DELETE FROM " + getStreamsTableName() + " WHERE FileId = " + fid +
									 " AND StreamId = " + stid;

      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Delete stream SQL: " + sql);

      //	Delete the stream record
      
			stmt.executeUpdate(sql);
		}
		catch (Exception ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println("[Oracle] Delete stream error: " + ex.getMessage());
		  
		  //	Rethrow the exception
		  
		  throw new DBException(ex.toString());
		}
		finally {
		  
		  //	Close the statement
		  
			if (stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}			
  
			//	Release the database connection

			if ( conn != null)      
				releaseConnection(conn);
		}
  }

  /**
   * Set file information for a file or folder
   *
   * @param dirId int
   * @param fid int
   * @param finfo FileInfo 
   * @exception DBException
   */
  public void setFileInformation(int dirId, int fid, FileInfo finfo)
  	throws DBException {
    
    //	Set file information fields
    
    Connection conn = null;
    Statement stmt = null;
    
    try {
      
			//	Get a connection to the database
	
			conn = getConnection();
	
			//	Build the SQL statement to update the file information settings
			
			StringBuffer sql = new StringBuffer(256);
			sql.append("UPDATE ");
			sql.append(getFileSysTableName());
			sql.append(" SET ");
	
			//	Check if the file attributes have been updated
			
			if ( finfo.hasSetFlag(FileInfo.SetAttributes)) {
				
				//	Update the basic file attributes
	
				sql.append("ReadOnlyFile = ");
				sql.append(finfo.isReadOnly() ? "1" : "0");
				
				sql.append(", ArchivedFile =");
				sql.append(finfo.isArchived() ? "1" : "0");
				
				sql.append(", SystemFile = ");
				sql.append(finfo.isSystem() ? "1" : "0");
				
				sql.append(", HiddenFile = ");
				sql.append(finfo.isHidden() ? "1" : "0");
				sql.append(",");
			}
			
			//	Check if the file size should be set
			
			if ( finfo.hasSetFlag(FileInfo.SetFileSize)) {
			
			  //	Update the file size
			  
			  sql.append("FileSize = ");
			  sql.append(finfo.getSize());
			  sql.append(",");
			}
			
			//	Merge the group id, user id and mode into the in-memory file information
			
			if ( finfo.hasSetFlag(FileInfo.SetGid)) {
				
				//	Update the group id
				
				sql.append("Gid = ");
				sql.append(finfo.getGid());
				sql.append(",");
			}
				
			if ( finfo.hasSetFlag(FileInfo.SetUid)) {
				
				//	Update the user id
				
				sql.append("Uid = ");
				sql.append(finfo.getUid());
				sql.append(",");
			}
				
			if ( finfo.hasSetFlag(FileInfo.SetMode)) {
				
				//	Update the mode
				
				sql.append("FileMode = ");
				sql.append(finfo.getMode());
				sql.append(",");
			}
				
			//	Check if the access date/time has been set
			
			if ( finfo.hasSetFlag(FileInfo.SetAccessDate)) {
				
				//	Add the SQL to update the access date/time
				
				sql.append(" AccessDate = TIMESTAMP'");
				sql.append(new Timestamp(finfo.getAccessDateTime()));
				sql.append("',");
			}
			
			//	Check if the modify date/time has been set
			
			if ( finfo.hasSetFlag(FileInfo.SetModifyDate)) {
				
				//	Add the SQL to update the modify date/time
				
				sql.append(" ModifyDate = TIMESTAMP'");
				sql.append(new Timestamp(finfo.getModifyDateTime()));
				sql.append("',");
			}
			
			//	Check if the inode change date/time has been set

			if ( finfo.hasSetFlag(FileInfo.SetChangeDate)) {
							
				//	Add the SQL to update the change date/time
				
				sql.append(" ChangeDate = TIMESTAMP'");
				sql.append(new Timestamp(finfo.getChangeDateTime()));
				sql.append("'");
			}

			//	Trim any trailing comma
			
			if ( sql.charAt(sql.length() - 1) == ',')
			  sql.setLength(sql.length() - 1);
			
			//	Complete the SQL request string
			
			sql.append(" WHERE FileId = ");
			sql.append( fid);
			
      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Set file info SQL: " + sql.toString());
      
			//	Create the SQL statement
			
			stmt = conn.createStatement();
			stmt.executeUpdate(sql.toString());
	  }
	  catch (SQLException ex) {
	    
	    //	DEBUG
	    
	    if ( Debug.EnableError && hasDebug())
	      Debug.println("[Oracle] Set file information error " + ex.getMessage());
	    
	    //	Rethrow the exception
	    
	    throw new DBException(ex.toString());
	  }
	  finally {
	
			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			    	
	    //	Release the database connection
	
			if ( conn != null)      
	    	releaseConnection(conn);
	  }
  }

  /**
   * Set information for a file stream
   * 
   * @param dirId int
   * @param fid int
   * @param stid int
   * @param sinfo StreamInfo
   * @exception DBException
   */
  public void setStreamInformation(int dirId, int fid, int stid, StreamInfo sinfo)
  	throws DBException {
    
    //	Set file stream information fields
    
    Connection conn = null;
    Statement stmt = null;
    
    try {
      
			//	Get a connection to the database
	
			conn = getConnection();
	
			//	Build the SQL statement to update the file information settings
			
			StringBuffer sql = new StringBuffer(256);
			sql.append("UPDATE ");
			sql.append(getStreamsTableName());
			sql.append(" SET ");
	
			//	Check if the access date/time has been set
			
			if ( sinfo.hasSetFlag(StreamInfo.SetAccessDate)) {
				
				//	Add the SQL to update the access date/time
				
				sql.append(" AccessDate = TIMESTAMP'");
				sql.append(new Timestamp(sinfo.getAccessDateTime()));
				sql.append("',");
			}
			
			//	Check if the modify date/time has been set
			
			if ( sinfo.hasSetFlag(StreamInfo.SetModifyDate)) {
				
				//	Add the SQL to update the modify date/time
				
				sql.append(" ModifyDate = TIMESTAMP'");
				sql.append(new Timestamp(sinfo.getModifyDateTime()));
				sql.append("',");
			}
			
			//	Check if the stream size should be updated
			
			if ( sinfo.hasSetFlag(StreamInfo.SetStreamSize)) {
			  
			  //	Update the stream size
			  
			  sql.append(" StreamSize = ");
			  sql.append(sinfo.getSize());
			}
			
			//	Trim any trailing comma
			
			if ( sql.charAt(sql.length() - 1) == ',')
			  sql.setLength(sql.length() - 1);
			
			//	Complete the SQL request string
			
			sql.append(" WHERE FileId = ");
			sql.append( fid);
			sql.append(" AND StreamId = ");
			sql.append( stid);
			
      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Set stream info SQL: " + sql.toString());
      
			//	Create the SQL statement
			
			stmt = conn.createStatement();
			stmt.executeUpdate(sql.toString());
	  }
	  catch (SQLException ex) {
	    
	    //	DEBUG
	    
	    if ( Debug.EnableError && hasDebug())
	      Debug.println("[Oracle] Set stream information error " + ex.getMessage());
	    
	    //	Rethrow the exception
	    
	    throw new DBException(ex.toString());
	  }
	  finally {
	
			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			    	
	    //	Release the database connection
	
			if ( conn != null)      
	    	releaseConnection(conn);
	  }
  }

  /**
   * Get the id for a file/folder, or -1 if the file/folder does not exist.
   * 
   * @param dirId int
   * @param fname String
   * @param dirOnly boolean
   * @param caseLess boolean
   * @return int
   * @throws DBException
   */
  public int getFileId(int dirId, String fname, boolean dirOnly, boolean caseLess)
  	throws DBException {

    //	Get the file id for a file/folder
    
    int fileId = -1;
    
		Connection conn = null;
		Statement stmt = null;

    try {
      
      //	Get a connection to the database, create a statement for the database lookup
      
      conn = getConnection();
      stmt = conn.createStatement();
      
      //	Build the SQL for the file lookup

      StringBuffer sql = new StringBuffer(128);
      
      sql.append("SELECT FileId FROM ");
      sql.append(getFileSysTableName());
      sql.append(" WHERE DirId = ");
      sql.append(dirId);
      sql.append(" AND ");

      //	Check if the search is for a directory only
      
      if ( dirOnly == true) {
        
        //	Search for a directory record
        
        sql.append(" DirectoryFile = 1 AND ");
      }
      
      //	Check if the file name search should be caseless
      
      String chkFileName = checkNameForSpecialChars(fname);
      
      if ( caseLess == true) {
        
        //	Perform a caseless search
        
        sql.append(" UPPER(FileName) = '");
        sql.append(chkFileName.toUpperCase());
        sql.append("'");
      }
      else {
        
        //	Perform a case sensitive search
        
        sql.append(" FileName = '");
        sql.append(chkFileName);
        sql.append("'");
      }
      
      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Get file id SQL: " + sql.toString());
      
      //	Run the database search
      
      ResultSet rs = stmt.executeQuery(sql.toString());

      //	Check if a file record exists
      
      if (rs.next()) {
        
        //	Get the unique file id for the file or folder 

        fileId = rs.getInt("FileId");
      }

      //	Close the result set
      
      rs.close();
    }
    catch (Exception ex) {
      
      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println("[Oracle] Get file id error " + ex.getMessage());
      
      //	Rethrow the exception
      
      throw new DBException(ex.toString());
    }
    finally {

			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			    	
      //	Release the database connection
      
      if ( conn != null)
      	releaseConnection(conn);
    }
    
    //	Return the file id, or -1 if not found
    
    return fileId;
  }

  /**
   * Get information for a file or folder
   *
   * @param dirId int
   * @param fid int
   * @param infoLevel int
   * @return FileInfo
   * @exception DBException
   */
  public DBFileInfo getFileInformation(int dirId, int fid, int infoLevel)
  	throws DBException {

    //	Create a SQL select for the required file information

    StringBuffer sql = new StringBuffer(128);
    
    sql.append("SELECT ");

    //	Select fields according to the required information level
    
    switch ( infoLevel) {
    
    	//	File name only
    
    	case DBInterface.FileNameOnly:
    	  sql.append("FileName");
    	  break;
    	
    	//	File ids and name
    	
    	case DBInterface.FileIds:
    	  sql.append("FileName,FileId,DirId");
    	  break;
    	
    	//	All file information
    	
    	case DBInterface.FileAll:
    	  sql.append("*");
    	  break;
    	
    	//	Unknown information level
    	
    	default:
    	  throw new DBException("Invalid information level, " + infoLevel);
    }
    
    sql.append(" FROM ");
    sql.append(getFileSysTableName());
    sql.append(" WHERE FileId = ");
    sql.append(fid);

    //	DEBUG
    
    if ( Debug.EnableInfo && hasSQLDebug())
      Debug.println("[Oracle] Get file info SQL: " + sql.toString());
    
    //	Load the file record    

		Connection conn = null;
		Statement stmt = null;

    DBFileInfo finfo = null;
    
    try {

      //	Get a connection to the database

      conn = getConnection();
			stmt = conn.createStatement();
			
			//	Load the file record
			
			ResultSet rs = stmt.executeQuery(sql.toString());

      if (rs != null && rs.next()) {
        
        //	Create the file informaiton object
        
        finfo = new DBFileInfo();
        finfo.setFileId(fid);
        
        //	Load the file information
        
        switch ( infoLevel) {

        	//	File name only
        
        	case DBInterface.FileNameOnly:
        	  finfo.setFileName(rs.getString("FileName"));
        	  break;
        	
        	//	File ids and name
        	
        	case DBInterface.FileIds:
        	  finfo.setFileName(rs.getString("FileName"));
        		finfo.setDirectoryId(rs.getInt("DirId"));
        	  break;
        	
        	//	All file information
        	
        	case DBInterface.FileAll:
      	    finfo.setFileName(rs.getString("FileName"));
	    	    finfo.setSize(rs.getLong("FileSize"));
	    	    finfo.setAllocationSize(finfo.getSize());
	    	    finfo.setDirectoryId(rs.getInt("DirId"));
	    	    
	    	    //	Load the various file date/times
	    	    
	    	    Timestamp ftime = rs.getTimestamp("CreateDate");
    	    	finfo.setCreationDateTime( ftime != null ? ftime.getTime() : 0L);
    	    	
    	    	ftime = rs.getTimestamp("ModifyDate");
    	    	finfo.setModifyDateTime( ftime != null ? ftime.getTime() : 0L);
    	    	
    	    	ftime = rs.getTimestamp("AccessDate");
    				finfo.setAccessDateTime( ftime != null ? ftime.getTime() : 0L);
    				
    				ftime = rs.getTimestamp("ChangeDate");
    				finfo.setChangeDateTime( ftime != null ? ftime.getTime() : 0L);
	    				
	    	    //	Build the file attributes flags
	    	    
	    	    int attr = 0;
	    	    
	    	    if ( rs.getBoolean("ReadOnlyFile") == true)
	    	    	attr += FileAttribute.ReadOnly;
	    	    	
	    	    if ( rs.getBoolean("SystemFile") == true)
	    	    	attr += FileAttribute.System;
	    	    	
	    	    if ( rs.getBoolean("HiddenFile") == true)
	    	    	attr += FileAttribute.Hidden;
	    	    	
	    	    if ( rs.getBoolean("DirectoryFile") == true) {
              attr += FileAttribute.Directory;
              finfo.setFileType( FileType.Directory);
            }
            else
              finfo.setFileType( FileType.RegularFile);
	
	    			if ( rs.getBoolean("ArchivedFile") == true)
	    				attr += FileAttribute.Archive;
	    	    	
	    	    finfo.setFileAttributes(attr);
	    	    
	    	    //	Get the group/owner id
	    	    
	    	    finfo.setGid(rs.getInt("OwnerGid"));
	    	    finfo.setUid(rs.getInt("OwnerUid"));
	    	    
	    	    finfo.setMode(rs.getInt("FileMode"));
            
            //  Check if the file is a symbolic link
            
            if ( rs.getBoolean( "IsSymLink") == true)
              finfo.setFileType( FileType.SymbolicLink);
        	  break;
        }
      }
    }
    catch (Exception ex) {
      
      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println("[Oracle] Get file information error " + ex.getMessage());

      //	Rethrow the exception
      
      throw new DBException(ex.toString());
    }
    finally {

			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			    	
      //	Release the database connection
      
      if ( conn != null)
      	releaseConnection(conn);
    }
    
    //	Return the file information
    
    return finfo;
  }

  /**
   * Get information for a file stream
   *
   * @param fid int
   * @param stid int
   * @param infoLevel int
   * @return StreamInfo
   * @exception DBException
   */
  public StreamInfo getStreamInformation(int fid, int stid, int infoLevel)
  	throws DBException {

    //	Create a SQL select for the required stream information

    StringBuffer sql = new StringBuffer(128);
    
    sql.append("SELECT ");

    //	Select fields according to the required information level
    
    switch ( infoLevel) {
    
    	//	Stream name only.
    	//
    	//	Also used if ids are requested as we already have the ids
    
    	case DBInterface.StreamNameOnly:
    	case DBInterface.StreamIds:
    	  sql.append("StreamName");
    	  break;
    	
    	//	All file information
    	
    	case DBInterface.StreamAll:
    	  sql.append("*");
    	  break;
    	
    	//	Unknown information level
    	
    	default:
    	  throw new DBException("Invalid information level, " + infoLevel);
    }
    
    sql.append(" FROM ");
    sql.append(getStreamsTableName());
    sql.append(" WHERE FileId = ");
    sql.append(fid);
    sql.append(" AND StreamId = ");
    sql.append(stid);

    //	DEBUG
    
    if ( Debug.EnableInfo && hasSQLDebug())
      Debug.println("[Oracle] Get stream info SQL: " + sql.toString());
    
    //	Load the stream record    

		Connection conn = null;
		Statement stmt = null;

    StreamInfo sinfo = null;
    
    try {

      //	Get a connection to the database

      conn = getConnection();
			stmt = conn.createStatement();
			
			//	Load the stream record
			
			ResultSet rs = stmt.executeQuery(sql.toString());

      if (rs != null && rs.next()) {
        
        //	Create the stream informaiton object
        
        sinfo = new StreamInfo("", fid, stid);
        
        //	Load the file information
        
        switch ( infoLevel) {

        	//	Stream name only (or name and ids)
        
        	case DBInterface.StreamNameOnly:
        	case DBInterface.StreamIds:
        	  sinfo.setName(rs.getString("StreamName"));
        	  break;
        	
        	//	All stream information
        	
        	case DBInterface.FileAll:
        	  sinfo.setName(rs.getString("StreamName"));
	    	    sinfo.setSize(rs.getLong("StreamSize"));
	    	    
	    	    //	Load the various file date/times
	    	    
    	    	sinfo.setCreationDateTime( rs.getLong("CreateDate"));
    	    	sinfo.setModifyDateTime( rs.getLong("ModifyDate"));
    				sinfo.setAccessDateTime( rs.getLong("AccessDate"));
        	  break;
        }
      }
    }
    catch (Exception ex) {
      
      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println("[Oracle] Get stream information error " + ex.getMessage());

      //	Rethrow the exception
      
      throw new DBException(ex.toString());
    }
    finally {

			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			    	
      //	Release the database connection
      
      if ( conn != null)
      	releaseConnection(conn);
    }
    
    //	Return the stream information
    
    return sinfo;
  }

  /**
   * Return the list of streams for the specified file
   * 
   * @param fid int
   * @param infoLevel int
   * @return StreamInfoList
   * @exception DBException
   */
  public StreamInfoList getStreamsList(int fid, int infoLevel)
  	throws DBException {

    //	Create a SQL select for the required stream information

    StringBuffer sql = new StringBuffer(128);
    
    sql.append("SELECT ");

    //	Select fields according to the required information level
    
    switch ( infoLevel) {
    
    	//	Stream name only.
    
    	case DBInterface.StreamNameOnly:
    	  sql.append("StreamName");
    		break;
    		
    	//	Stream name and ids
    		
    	case DBInterface.StreamIds:
    	  sql.append("StreamName,FileId,StreamId");
    		break;
    	
    	//	All file information
    	
    	case DBInterface.StreamAll:
    	  sql.append("*");
    	  break;
    	
    	//	Unknown information level
    	
    	default:
    	  throw new DBException("Invalid information level, " + infoLevel);
    }
    
    sql.append(" FROM ");
    sql.append(getStreamsTableName());
    sql.append(" WHERE FileId = ");
    sql.append(fid);

    //	DEBUG
    
    if ( Debug.EnableInfo && hasSQLDebug())
      Debug.println("[Oracle] Get stream list SQL: " + sql.toString());
    
    //	Load the stream record    

		Connection conn = null;
		Statement stmt = null;

    StreamInfoList sList = null;
    
    try {

      //	Get a connection to the database

      conn = getConnection();
			stmt = conn.createStatement();
			
			//	Load the stream records
			
			ResultSet rs = stmt.executeQuery(sql.toString());
			sList = new StreamInfoList();

      while ( rs.next()) {
        
        //	Create the stream informaiton object
        
        StreamInfo sinfo = new StreamInfo("", fid, -1);
        
        //	Load the file information
        
        switch ( infoLevel) {

        	//	Stream name only
        
        	case DBInterface.StreamNameOnly:
        	  sinfo.setName(rs.getString("StreamName"));
        		break;
        		
        	//	Stream name and id
        		
        	case DBInterface.StreamIds:
        	  sinfo.setName(rs.getString("StreamName"));
        		sinfo.setStreamId(rs.getInt("StreamId"));
        	  break;
        	
        	//	All stream information
        	
        	case DBInterface.FileAll:
        	  sinfo.setName(rs.getString("StreamName"));
      			sinfo.setStreamId(rs.getInt("StreamId"));
	    	    sinfo.setSize(rs.getLong("StreamSize"));
	    	    
	    	    //	Load the various file date/times
	    	    
    	    	sinfo.setCreationDateTime( rs.getLong("CreateDate"));
    	    	sinfo.setModifyDateTime( rs.getLong("ModifyDate"));
    				sinfo.setAccessDateTime( rs.getLong("AccessDate"));
        	  break;
        }
        
        //	Add the stream information to the list
        
        sList.addStream(sinfo);
      }
    }
    catch (Exception ex) {
      
      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println("[Oracle] Get stream list error " + ex.getMessage());

      //	Rethrow the exception
      
      throw new DBException(ex.toString());
    }
    finally {

			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			    	
      //	Release the database connection
      
      if ( conn != null)
      	releaseConnection(conn);
    }
    
    //	Return the streams list
    
    return sList;
  }

  /**
   * Rename a file or folder, may also change the parent directory.
   *
   * @param dirId int
   * @param fid int
   * @param newName String
   * @param newDir int
   * @exception DBException
   * @exception FileNotFoundException
   */
  public void renameFileRecord(int dirId, int fid, String newName, int newDir)
  	throws DBException, FileNotFoundException {

    //	Rename a file/folder    

		Connection conn = null;
		Statement stmt = null;

    try {

      //	Get a connection to the database

      conn = getConnection();
			stmt = conn.createStatement();

			//	Update the file record
	
	    stmt = conn.createStatement();
	    String sql = "UPDATE " + getFileSysTableName() + " SET FileName = '" + checkNameForSpecialChars(newName) + "', DirId = " + newDir +
									 ", ChangeDate = TIMESTAMP'" + new Timestamp(System.currentTimeMillis()) + "' WHERE FileId = " + fid;
    
      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Rename SQL: " + sql.toString());
      
	    //	Rename the file/folder
	    
	    if ( stmt.executeUpdate(sql) == 0) {
	
	      //	Original file not found
	      
	    	throw new FileNotFoundException("" + fid);
	    }
	  }
	  catch (SQLException ex) {
	    
	    //	DEBUG
	    
	    if ( Debug.EnableError && hasDebug())
	      Debug.println("[Oracle] Rename file error " + ex.getMessage());
	    
	    //	Rethrow the exception
	    
	    throw new DBException(ex.toString());
	  }
	  finally {
	
			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
				    	
	  	//	Release the database connection
	
			if ( conn != null)    	
	    	releaseConnection(conn);
	  }
  }

  /**
   * Rename a file stream
   *
   * @param dirId int
   * @param fid int
   * @param stid int
   * @param newName String
   * @exception DBException
   */
  public void renameStreamRecord(int dirId, int fid, int stid, String newName)
  	throws DBException {
    // TODO Auto-generated method stub

  }

  /**
   * Return the retention period expiry date/time for the specified file, or zero if the file/folder
   * is not under retention.
   * 
   * @param dirId int
   * @param fid int
   * @return RetentionDetails
   * @exception DBException
   */
  public RetentionDetails getFileRetentionDetails(int dirId, int fid)
  	throws DBException {

    //	Check if retention is enabled
    
    if ( isRetentionEnabled() == false)
      return null;

    //	Get the retention record for the file/folder
    
		Connection conn = null;
		Statement stmt = null;

		RetentionDetails retDetails = null;
		
    try {

      //	Get a connection to the database

      conn = getConnection();
			stmt = conn.createStatement();

	    //	Get the retention record, if any

			retDetails = getRetentionExpiryDateTime(conn, stmt, fid);
    }
	  catch (SQLException ex) {
	    
	    //	DEBUG
	    
	    if ( Debug.EnableError && hasDebug())
	      Debug.println("[Oracle] Get retention error " + ex.getMessage());
	    
	    //	Rethrow the exception
	    
	    throw new DBException(ex.toString());
	  }
	  finally {
	
			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
				    	
	  	//	Release the database connection
	
			if ( conn != null)    	
	    	releaseConnection(conn);
	  }
	  
	  //	Return the retention expiry date/time
	  
	  return retDetails;
  }

  /**
   * Start a directory search
   *
   * @param dirId int
   * @param searchPath String
   * @param attrib int
   * @param infoLevel int
   * @param maxRecords int
   * @return DBSearchContext
   * @exception DBException
   */
  public DBSearchContext startSearch(int dirId, String searchPath, int attrib, int infoLevel, int maxRecords)
  	throws DBException {

    //	Search for files/folders in the specified folder

    StringBuffer sql = new StringBuffer(128);
    sql.append("SELECT * FROM ");
    sql.append(getFileSysTableName());
    
    sql.append(" WHERE DirId = ");
    sql.append(dirId);
    sql.append(" AND IsDeleted = 0");

		//	Split the search path
		
		String[] paths = FileName.splitPath(searchPath);
		
		//	Check if the file name contains wildcard characters

		WildCard wildCard = null;
		
		if ( WildCard.containsWildcards(searchPath)) {

			//	For the '*.*' and '*' wildcards the SELECT will already return all files/directories that are attached to the 
			//	parent directory. For 'name.*' and '*.ext' type wildcards we can use the LIKE clause to filter the required
			//	records, for more complex wildcards we will post-process the search using the WildCard class to match the
			//	file names.
			
			if (searchPath.endsWith("\\*.*") == false && searchPath.endsWith("\\*") == false) {
				
				//	Create a wildcard search pattern
				
				wildCard = new WildCard(paths[1], true);
				
				//	Check for a 'name.*' type wildcard
				
				if ( wildCard.isType() == WildCard.WILDCARD_EXT) {

					//	Add the wildcard file extension selection clause to the SELECT
					
					sql.append(" AND FileName LIKE('");
					sql.append(checkNameForSpecialChars(wildCard.getMatchPart()));
					sql.append("%')");
					
					//	Clear the wildcard object, we do not want it to filter the search results
					
					wildCard = null;
				}
				else if ( wildCard.isType() == WildCard.WILDCARD_NAME) {
					
					//	Add the wildcard file name selection clause to the SELECT
					
					sql.append(" AND FileName LIKE('%");
					sql.append(checkNameForSpecialChars(wildCard.getMatchPart()));
					sql.append("')");
					
					//	Clear the wildcard object, we do not want it to filter the search results
					
					wildCard = null;
				}
			}
		}
		else {
			
			//	Search for a specific file/directory		

			sql.append(" AND FileName = '");
			sql.append(checkNameForSpecialChars(paths[1]));
			sql.append("'");
		}
		
    //	Start the search    

    ResultSet rs = null;
		Connection conn = null;
		Statement stmt = null;
		
    try {
      
      //	Get a connection to the database
      
      conn = getConnection();
      stmt = conn.createStatement();
      
      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Start search SQL: " + sql.toString());
      
      //	Start the folder search
      
      rs = stmt.executeQuery(sql.toString());
    }
    catch (Exception ex) {
      
      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println("[Oracle] Start search error " + ex.getMessage());
      
      //	Rethrow the exception
      
      throw new DBException(ex.toString());
    }
    finally {

      //	Release the database connection

			if ( conn != null)      
      	releaseConnection(conn);
    }

    //	Create the search context, and return

    return new OracleSearchContext(rs, stmt, wildCard);
  }

  /**
   * Return the used file space, or -1 if not supported.
   * 
   * @return long
   */
  public long getUsedFileSpace() {

    //	Calculate the total used file space
		
		Connection conn = null;
		Statement stmt  = null;

		long usedSpace = -1L;

		try {

			//	Get a database connection and statement
			
			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT SUM(CAST(FileSize AS BIGINT)) FROM " + getFileSysTableName();
			
      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Get filespace SQL: " + sql);
      
			//	Calculate the currently used disk space
			
			ResultSet rs = stmt.executeQuery(sql);
			
			if ( rs.next())
				usedSpace = rs.getLong(1);
		}
		catch (SQLException ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println("[Oracle] Get used file space error " + ex.getMessage());
		}
		finally {
    	
			//	Close the prepared statement
    	
			if (stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}

			//	Release the database connection

			if ( conn != null)      
				releaseConnection(conn);
		}
		
		//	Return the used file space
		
		return usedSpace;
  }

  /**
   * Shutdown the database interface, release resources.
   * 
   * @param context DBDeviceContext
   */
  public void shutdownDatabase(DBDeviceContext context) {
    
    //	Call the base class
    
    super.shutdownDatabase(context);
  }
  
  /**
   * Get the retention expiry date/time for a file/folder
   * 
   * @param conn Connection
   * @param stmt Statement
   * @param fid int
   * @return RetentionDetails
   * @exception SQLException
   */
  private final RetentionDetails getRetentionExpiryDateTime(Connection conn, Statement stmt, int fid)
  	throws SQLException {

    //	Get the retention expiry date/time for the specified file/folder
    
    RetentionDetails retDetails = null;
    String sql = "SELECT StartDate,EndDate FROM " + getRetentionTableName() + " WHERE FileId = " + fid;

    //	DEBUG
    
    if ( Debug.EnableInfo && hasSQLDebug())
      Debug.println("[Oracle] Get retention expiry SQL: " + sql);
    
    //	Get the retention record, if any
  	
		ResultSet rs = stmt.executeQuery(sql);
		
		if ( rs.next()) {
			
			//	Get the retention expiry date

		  Timestamp startDate = rs.getTimestamp("StartDate");
			Timestamp endDate   = rs.getTimestamp("EndDate");
			
			retDetails = new RetentionDetails(fid, startDate != null ? startDate.getTime() : -1L, endDate.getTime());
		}

	  //	Return the retention expiry date/time
	  
	  return retDetails;
  }
  
  /**
   * Determine if the specified file/folder is still within an active retention period
   * 
   * @param conn Connection
   * @param stmt Statement
   * @param fid int
   * @return boolean
   * @exception SQLException
   */
  private final boolean fileHasActiveRetention(Connection conn, Statement stmt, int fid)
  	throws SQLException {

    //	Check if retention is enabled
    
    if ( isRetentionEnabled() == false)
      return false;
    
    //	Check if the file/folder is within the retention period
    
    RetentionDetails retDetails = getRetentionExpiryDateTime(conn, stmt, fid);
    if ( retDetails == null)
      return false;

    //	File/folder is within the retention period
    
    return retDetails.isWithinRetentionPeriod(System.currentTimeMillis());
  }
  
  /**
   * Queue a file request.
   *
   * @param req FileRequest
   * @exception DBException 
   */
  public void queueFileRequest(FileRequest req)
  	throws DBException {

    //	Make sure the associated file state stays in memory for a short time, if the queue is small
		//	the request may get processed soon.
		
		if ( req instanceof SingleFileRequest) {
			
			//	Get the request details
			
			SingleFileRequest fileReq = (SingleFileRequest) req;
			Statement stmt = null;

			try {
					
				//	Check if the file request queue database connection is valid
				
				if ( m_dbConn == null || m_dbConn.isClosed() || m_reqStmt == null || m_reqStmt.getConnection().isClosed())
					createQueueStatements();
						
				//	Check if the request is part of a transaction, or a standalone request
				
				if ( fileReq.isTransaction() == false) {
				
					//	Write the file request record
		
					int recCnt = 0;
					
					synchronized ( m_reqStmt) {
						
						//	Write the file request to the queue database
						
						m_reqStmt.clearParameters();
						
						m_reqStmt.setInt(1, fileReq.getFileId());
						m_reqStmt.setInt(2, fileReq.getStreamId());
						m_reqStmt.setInt(3, fileReq.isType());
						m_reqStmt.setString(4, fileReq.getTemporaryFile());
						m_reqStmt.setString(5, fileReq.getVirtualPath());
            m_reqStmt.setString(6, fileReq.getAttributesString());
						
						recCnt = m_reqStmt.executeUpdate();

						//	Retrieve the allocated sequence number

						if ( recCnt > 0) {
							
							//	Get the last insert id

						  stmt = m_dbConn.createStatement();
						  ResultSet rs2 = stmt.executeQuery("SELECT " + getTablePrefix() + "SeqNo.CURRVAL FROM DUAL");
					
							if ( rs2.next())
								fileReq.setSequenceNumber(rs2.getInt(1));
						}
					}
				}
				else {

					//	Check if the transaction prepared statement is valid, we may have lost the connection to the 
					//	database.
					
					if ( m_tranStmt == null || m_tranStmt.getConnection().isClosed())
						createQueueStatements();
						
					//	Write the transaction file request to the database
					
					synchronized ( m_tranStmt) {

						//	Write the request record to the	database
						
						m_tranStmt.clearParameters();
						
						m_tranStmt.setInt(1, fileReq.getFileId());
						m_tranStmt.setInt(2, fileReq.getStreamId());
						m_tranStmt.setInt(3, fileReq.isType());
						m_tranStmt.setInt(4, fileReq.getTransactionId());
						m_tranStmt.setString(5, fileReq.getTemporaryFile());
						m_tranStmt.setString(6, fileReq.getVirtualPath());
            m_tranStmt.setString(7, fileReq.getAttributesString());
						
						m_tranStmt.executeUpdate();
					}
				}
        
        // File request was queued successfully, check for any offline file requests
        
        if ( hasOfflineFileRequests())
          databaseOnlineStatus( true);
			}
			catch (SQLException ex) {
        
        // If the request is a save then add to a pending queue to retry when the database is back online
        
        if ( fileReq.isType() == FileRequest.SAVE || fileReq.isType() == FileRequest.TRANSSAVE)
          queueOfflineSaveRequest( fileReq);
			  
			  //	DEBUG
			  
			  if ( Debug.EnableError && hasDebug())
			    Debug.println(ex);
			  
			  //	Rethrow the exception
			  
			  throw new DBException ( ex.getMessage());
			}
			finally {
			  
			  //	Release the statement
			  
			  if ( stmt != null) {
			    try {
			      stmt.close();
			    }
			    catch (Exception ex) {
			    }
			  }
			}
		}
  }

  /**
   * Perform a queue cleanup deleting temporary cache files that do not have an associated save or transaction
   * request.
   * 
   * @param tempDir File
   * @param tempDirPrefix String
   * @param tempFilePrefix String
   * @param jarFilePrefix String
   * @return FileRequestQueue
   * @throws DBException
   */
  public FileRequestQueue performQueueCleanup(File tempDir, String tempDirPrefix, String tempFilePrefix, String jarFilePrefix)
    throws DBException {

    //  Get a connection to the database
    
    Connection conn = null;
    PreparedStatement pstmt = null;
    Statement stmt = null;
    
    FileRequestQueue reqQueue = new FileRequestQueue();
    
    try {

      //  Get a connection to the database
      
      conn = getConnection(DBConnectionPool.PermanentLease);

      //  Delete all load requests from the queue
      
      stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery("SELECT * FROM " + getQueueTableName() + " WHERE ReqType = " + FileRequest.LOAD);

      while ( rs.next()) {
        
        //  Get the path to the cache file
        
        String tempPath = rs.getString( "TempFile");
        
        //  Check if the cache file exists, the file load may have been in progress
        
        File tempFile = new File( tempPath);
        if ( tempFile.exists()) {
          
          //  Delete the cache file for the load request
          
          tempFile.delete();
          
          //  DEBUG
          
          if ( Debug.EnableInfo && hasDebug())
            Debug.println("[Oracle] Deleted load request file " + tempPath);
        }
      }
      
      //  Check if the lock file exists, if so then the server did not shutdown cleanly
      
      File lockFile = new File( tempDir, LockFileName);
      setLockFile( lockFile.getAbsolutePath());
      
      boolean cleanShutdown = lockFile.exists() == false;
      
      //  Create a crash recovery folder if the server did not shutdown clean
      
      File crashFolder = null;
      
      if ( cleanShutdown == false && hasCrashRecovery()) {
      
        // Create a unique crash recovery sub-folder in the temp area
        
        SimpleDateFormat dateFmt = new SimpleDateFormat( "yyyyMMMdd_HHmmss");
        crashFolder = new File( tempDir, "CrashRecovery_" + dateFmt.format(new Date( System.currentTimeMillis())));
        if ( crashFolder.mkdir() == true) {
          
          // DEBUG
          
          if (Debug.EnableDbg && hasDebug())
            Debug.println("[Oracle] Created crash recovery folder - " + crashFolder.getAbsolutePath());
        }
        else {
          
          // Use the top level temp area for the crash recovery files
          
          crashFolder = tempDir;
          
          // DEBUG
          
          if (Debug.EnableDbg && hasDebug())
            Debug.println("[Oracle] Failed to created crash recovery folder, using folder - " + crashFolder.getAbsolutePath());
        }
      }
      
      //  Delete the file load request records
      
      stmt.execute("DELETE FROM " + getQueueTableName() + " WHERE ReqType = " + FileRequest.LOAD);
      
      //  Create a statement to check if a temporary file is part of a save request
      
      pstmt = conn.prepareStatement("SELECT FileId,SeqNo FROM " + getQueueTableName() + " WHERE TempFile = ?");

      //  Scan all files/sub-directories within the temporary area looking for files that have been saved but not
      //  deleted due to a server shutdown or crash.
  
      File[] tempFiles = tempDir.listFiles();
      
      if ( tempFiles != null && tempFiles.length > 0) {
        
        //  Scan the file loader sub-directories for temporary files
        
        for ( int i = 0; i < tempFiles.length; i++) {
          
          //  Get the current file/sub-directory
          
          File curFile = tempFiles[i];
          
          if ( curFile.isDirectory() && curFile.getName().startsWith(tempDirPrefix)) {
            
            //  Check if the sub-directory has any loader temporary files
            
            File[] subFiles = curFile.listFiles();
            
            if ( subFiles != null && subFiles.length > 0) {
              
              //  Check each file to see if it has a pending save request in the file request database
              
              for ( int j = 0; j < subFiles.length; j++) {
                
                //  Get the current file from the list
                
                File ldrFile = subFiles[j];
                
                if ( ldrFile.isFile() && ldrFile.getName().startsWith(tempFilePrefix)) {
                  
                  try {
                    
                    //  Get the file details from the file system table
                    
                    pstmt.clearParameters();
                    pstmt.setString(1, ldrFile.getAbsolutePath());
                    
                    rs = pstmt.executeQuery();
                    
                    if ( rs.next()) {

                      //  File save request exists for temp file, nothing to do
                      
                    }
                    else {

                      //  Check if the modified date indicates the file may have been updated
                      
                      if ( ldrFile.lastModified() != 0L) {
                        
                        //  Get the file id from the cache file name
                        
                        String fname = ldrFile.getName();
                        int dotPos = fname.indexOf( '.');
                        String fidStr = fname.substring( tempFilePrefix.length(), dotPos);
                        
                        if ( fidStr.indexOf( '_') == -1) {
                          
                          //  Convert the file id
                          
                          int fid = -1;
                          
                          try {
                            fid = Integer.parseInt( fidStr);
                          }
                          catch (NumberFormatException ex) {
                          }
                          
                          //  Get the file details from the database
                          
                          if ( fid != -1) {
  
                            //  Get the file details for the temp file using the file id
                            
                            rs = stmt.executeQuery( "SELECT * FROM " + getFileSysTableName() + " WHERE FileId = " + fid);
                            
                            // If the previous server shutdown was clean then we may be able to queue the file save
                            
                            if ( cleanShutdown == true) {
                              
                              if ( rs.next()) {
  
                                //  Get the currently stored modifed date and file size for the associated file
                                
                                Timestamp modtime = rs.getTimestamp("ModifyDate");
                                long dbModDate  = modtime != null ? modtime.getTime() : 0L;
                                long dbFileSize = rs.getLong( "FileSize");
                                
                                //  Check if the temp file requires saving
                                
                                if ( ldrFile.length() != dbFileSize ||
                                     ldrFile.lastModified() > dbModDate) {
                                  
                                  //  Build the filesystem path to the file
                                  
                                  String filesysPath = buildPathForFileId( fid, stmt);
  
                                  if ( filesysPath != null) {
                                    
                                    // Create a file state for the file
                                    
                                    FileState fstate = m_dbCtx.getStateCache().findFileState( filesysPath, true);
                                    
                                    FileSegmentInfo fileSegInfo = (FileSegmentInfo) fstate.findAttribute(ObjectIdFileLoader.DBFileSegmentInfo);
                                    FileSegment fileSeg = null;
                                    
                                    if ( fileSegInfo == null) {
                                      
                                      //  Create a new file segment
                                      
                                      fileSegInfo = new FileSegmentInfo();
                                      fileSegInfo.setTemporaryFile(ldrFile.getAbsolutePath());
                                      
                                      fileSeg = new FileSegment(fileSegInfo, true);
                                      fileSeg.setStatus(FileSegmentInfo.SaveWait, true);
                                      
                                      //  Add the segment to the file state cache
                                      
                                      fstate.addAttribute(ObjectIdFileLoader.DBFileSegmentInfo, fileSegInfo);
  
                                      //  Add a file save request for the temp file to the recovery queue
                                      
                                      reqQueue.addRequest(new SingleFileRequest( FileRequest.SAVE, fid, 0, ldrFile.getAbsolutePath(), filesysPath, fstate));
                                      
                                      //  Update the file size and modified date/time in the filesystem database
                                      
                                      stmt.execute( "UPDATE " + getFileSysTableName() + " SET FileSize = " + ldrFile.length() + ", ModifyDate = TIMESTAMP'" + new Timestamp(ldrFile.lastModified()) + "' WHERE FileId = " + fid);
  
                                      //  DEBUG
                                      
                                      if ( Debug.EnableInfo && hasDebug())
                                        Debug.println( "[Oracle] Queued save request for " + ldrFile.getName() + ", path=" + filesysPath + ", fid=" + fid);
                                    }
                                  }
                                  else {
                                    
                                    //  Delete the temp file, cannot resolve the path
                                    
                                    ldrFile.delete();
                                    
                                    //  DEBUG
                                    
                                    if ( Debug.EnableInfo && hasDebug())
                                      Debug.println( "[Oracle] Cannot resolve filesystem path for FID " + fid + ", deleted file " + ldrFile.getName());
                                  }
                                }
                              }
                              else {
                                
                                //  Delete the temp file, file does not exist in the filesystem table
                                
                                ldrFile.delete();
    
                                //  DEBUG
                                
                                if ( Debug.EnableInfo && hasDebug())
                                  Debug.println( "[Oracle] No matching file record for FID " + fid + ", deleted file " + ldrFile.getName());
                              }
                            }
                            else {
                              
                              // File server did not shutdown cleanly so move any modified files to a holding area as they may be corrupt
                              
                              if ( rs.next() && hasCrashRecovery()) {
                                
                                // Get the filesystem file name
                                
                                String extName = rs.getString( "FileName");
                                
                                // Generate a file name to rename the cache file into a crash recovery folder
                                
                                File crashFile = new File ( crashFolder, "" + fid + "_" + extName);
                                
                                // Rename the cache file into the crash recovery folder
                                
                                if ( ldrFile.renameTo( crashFile)) {
                                  
                                  // DEBUG
                                  
                                  if ( Debug.EnableDbg && hasDebug())
                                    Debug.println("[Oracle] Crash recovery file - " + crashFile.getAbsolutePath());
                                }
                              }
                              else {
                                
                                // DEBUG

                                if ( Debug.EnableDbg && hasDebug())
                                  Debug.println( "[Oracle] Deleted incomplete cache file - " + ldrFile.getAbsolutePath());

                                // Delete the incomplete cache file
                                
                                ldrFile.delete();
                              }
                            }
                          }
                          else {
                            
                            //  Invalid file id format, delete the temp file
                            
                            ldrFile.delete();
  
                            //  DEBUG
                            
                            if ( Debug.EnableInfo && hasDebug())
                              Debug.println( "[Oracle] Bad file id format, deleted file, " + ldrFile.getName());
                          }
                        }
                        else  {
                          
                          //  Delete the temp file as it is for an NTFS stream
                          
                          ldrFile.delete();
                          
                          //  DEBUG
                          
                          if ( Debug.EnableInfo && hasDebug())
                            Debug.println( "[Oracle] Deleted NTFS stream temp file, " + ldrFile.getName());
                        }
                      }
                      else {
                        
                        //  Delete the temp file as it has not been modifed since it was loaded
                        
                        ldrFile.delete();
                        
                        //  DEBUG
                        
                        if ( Debug.EnableInfo && hasDebug())
                          Debug.println( "[Oracle] Deleted unmodified temp file, " + ldrFile.getName());
                      }
                    }
                  }
                  catch (SQLException ex) {
                    Debug.println(ex);
                  }
                }
                else {
                  
                  //  DEBUG
                  
                  if ( Debug.EnableInfo && hasDebug())
                    Debug.println("[Oracle] Deleted temporary file " + ldrFile.getName());
                  
                  //  Delete the temporary file
                  
                  ldrFile.delete();
                }
              }
            }
          }
        }
      }

      // Create the lock file, delete any existing lock file
      
      if ( lockFile.exists())
        lockFile.delete();
      
      try {
        lockFile.createNewFile();
      }
      catch (IOException ex) {
        
        // DEBUG
        
        if ( Debug.EnableDbg && hasDebug())
          Debug.println("[Oracle] Failed to create lock file - " + lockFile.getAbsolutePath());
      }
    }
    catch (SQLException ex) {
      
      //  DEBUG
      
      if  (Debug.EnableError && hasDebug())
        Debug.println(ex);
    }
    finally {

      //  Close the load request statment
      
      if (stmt != null) {
        try {
          stmt.close();
        }
        catch (Exception ex) {
        }
      }
      
      //  Close the prepared statement
      
      if ( pstmt != null) {
        try {
          pstmt.close();
        }
        catch (Exception ex) {
        }
      }
      
      //  Release the database connection
      
      if ( conn != null)
        releaseConnection(conn);
    }

    //  DEBUG
    
    if ( Debug.EnableInfo && hasDebug())
      Debug.println("[Oracle] Cleanup recovered " + reqQueue.numberOfRequests() + " file saves from previous run");
              
    //  Return the receovery file request queue
    
    return reqQueue;
  }
  
  /**
   * Check if the specified temporary file has a queued request.
   * 
   * @param tempFile String
   * @param lastFile boolean
   * @return boolean
   * @exception DBException
   */
  public boolean hasQueuedRequest(String tempFile, boolean lastFile)
  	throws DBException {

    Connection conn = null;
    Statement stmt = null;

    boolean queued = false;
    
    try {
      
			//	Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT FileId FROM " + getQueueTableName() + " WHERE TempFile = '" + tempFile + "'";
			
      //	DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Has queued req SQL: " + sql);
      
			//	Check if there is a queued request using the temporary file
			
			ResultSet rs = stmt.executeQuery(sql);
			if ( rs.next())
			  queued = true;
			else {
			  
			  //	Check if there is a transaction using the temporary file

			  sql = "SELECT FileId FROM " + getTransactionTableName() + " WHERE TempFile = '" + tempFile + "'";
			  
	      //	DEBUG
	      
	      if ( Debug.EnableInfo && hasSQLDebug())
	        Debug.println("[Oracle] Has queued req SQL: " + sql);
	      
	      //	Check the transaction table
	      
			  rs = stmt.executeQuery(sql);
			  if ( rs.next())
			    queued = true;
			}
		}
		catch (SQLException ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println(ex);
		  
		  //	Rethrow the exception
		  
		  throw new DBException(ex.getMessage());
		}
		finally {
	
			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (SQLException ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
		}
		
		//	Return the queued status
		
		return queued;
  }
  
  /**
   * Delete a file request from the pending queue.
   * 
   * @param fileReq FileRequest
   * @exception DBException
   */
  public void deleteFileRequest(FileRequest fileReq)
  	throws DBException {

    Connection conn = null;
    Statement stmt = null;
    
    try {
      
			//	Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();
	
			//	Delete the file request queue entry from the request table or multiple records from the
			//	transaction table
			
			if ( fileReq instanceof SingleFileRequest) {
				
				//	Get the single file request details
				
				SingleFileRequest singleReq = (SingleFileRequest) fileReq;
			
				//	Delete the request record
				
				stmt.executeUpdate("DELETE FROM " + getQueueTableName() + " WHERE SeqNo = " + singleReq.getSequenceNumber());
			}
			else {
				
				//	Delete the transaction records
				
				stmt.executeUpdate("DELETE FROM " + getTransactionTableName() + " WHERE TranId = " + fileReq.getTransactionId());
			}
		}
		catch (SQLException ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println(ex);
		  
		  //	Rethrow the exception
		  
		  throw new DBException(ex.getMessage());
		}
		finally {
	
			//	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (SQLException ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
		}						
  }
  
  /**
   * Load a block of file request from the database into the specified queue.
   *
   * @param fromSeqNo int
   * @param reqType int
   * @param reqQueue FileRequestQueue
   * @param recLimit int
   * @return int
   * @exception DBException 
   */
  public int loadFileRequests(int fromSeqNo, int reqType, FileRequestQueue reqQueue, int recLimit)
  	throws DBException {

    //	Load a block of file requests from the loader queue

    Connection conn = null;
    Statement stmt  = null;

    int recCnt = 0;
    
    try {
      
	    //	Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();
	
			//	Build the SQL to load the queue records
			
			String sql = "SELECT * FROM " + getQueueTableName() + " WHERE SeqNo > " + fromSeqNo + " AND ReqType = " + reqType + " AND rownum <= " + recLimit + " ORDER BY SeqNo";

			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Load file requests - " + sql);
			
			//	Get a block of file request records
			
			ResultSet rs = stmt.executeQuery(sql);
			
			while ( rs.next()) {
				
				//	Get the file request details
				
				int fid    = rs.getInt("FileId");
				int stid   = rs.getInt("StreamId");
				int reqTyp = rs.getInt("ReqType");
				int seqNo  = rs.getInt("SeqNo");
				String tempPath = rs.getString("TempFile");
				String virtPath = rs.getString("VirtualPath");
        String attribs = rs.getString("Attribs");
	
				//	Recreate the file request for the in-memory queue
				
				SingleFileRequest fileReq = new SingleFileRequest(reqTyp, fid, stid, tempPath, virtPath, seqNo, null);
        fileReq.setAttributes( attribs);
				
				//	Add the request to the callers queue
				
				reqQueue.addRequest(fileReq);
				
				//	Update the count of loaded requests
				
				recCnt++;
			}
    }
    catch (SQLException ex) {

      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println(ex);
      
      //	Rethrow the exception
      
      throw new DBException(ex.getMessage());
    }
    finally {

      //	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (SQLException ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
    }

    //	Return the count of file requests loaded
    
    return recCnt;
  }
  
  /**
   * Load a transaction request from the queue.
   *
   * @param tranReq MultiplFileRequest
   * @return MultipleFileRequest
   * @exception DBException
   */
  public MultipleFileRequest loadTransactionRequest(MultipleFileRequest tranReq)
  	throws DBException {

    //	Load a transaction request from the transaction loader queue

    Connection conn = null;
    Statement stmt  = null;

    try {

      //	Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT * FROM " + getTransactionTableName() + " WHERE TranId = " + tranReq.getTransactionId();
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Load trans request - " + sql);
			
			//	Get the block of file request records for the current transaction
			
			ResultSet rs = stmt.executeQuery(sql);
			
			while ( rs.next()) {
				
				//	Get the file request details
				
				int fid    = rs.getInt("FileId");
				int stid   = rs.getInt("StreamId");
				String tempPath = rs.getString("TempFile");
				String virtPath = rs.getString("VirtualPath");

				//	Create the cached file information and add to the request
				
				CachedFileInfo finfo = new CachedFileInfo(fid,stid,tempPath,virtPath);
				tranReq.addFileInfo(finfo);
			}					
    }
    catch (SQLException ex) {

      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println(ex);
      
      //	Rethrow the exception
      
      throw new DBException(ex.getMessage());
    }
    finally {

      //	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (SQLException ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
    }
    
    //	Return the updated file request
    
    return tranReq;
  }
  
	/**
	 * Create the prepared statements used by the file request queueing database
	 * 
	 * @exception SQLException
	 */
	protected final void createQueueStatements()
		throws SQLException {
	
		//	Check if the database connection is valid
		
		if ( m_dbConn != null) {
			
			//	Close the existing statements

		  if ( m_reqStmt != null)
		    m_reqStmt.close();

		  if ( m_tranStmt != null)
		    m_tranStmt.close();
		  
			//	Release the current database connection
			
			releaseConnection(m_dbConn);
			m_dbConn = null;
			
		}
		
		if ( m_dbConn == null)
			m_dbConn = getConnection(DBConnectionPool.PermanentLease);
			
		//	Create the prepared statements for accessing the file request queue database
		
	  m_reqStmt = m_dbConn.prepareStatement("INSERT INTO " + getQueueTableName() + " (FileId,StreamId,ReqType,TempFile,VirtualPath,Attribs,SeqNo) VALUES (?,?,?,?,?,?," + getTablePrefix() + "SeqNo.NEXTVAL)");
		
		//	Create the prepared statements for accessing the transaction request queue database

	  m_tranStmt = m_dbConn.prepareStatement("INSERT INTO " + getTransactionTableName() + " (FileId,StreamId,ReqType,TranId,TempFile,VirtualPath,Attribs) VALUES (?,?,?,?,?,?,?)");
	}

  /**
   * Return the file data details for the specified file or stream.
   * 
   * @param fileId int
   * @param streamId int
   * @return DBDataDetails
   * @throws DBException
   */
  public DBDataDetails getFileDataDetails(int fileId, int streamId)
  	throws DBException {
    
    //	Load the file details from the data table

    Connection conn = null;
    Statement stmt  = null;

    DBDataDetails  dbDetails = null;

    try {

      //	Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT * FROM " + getDataTableName() + " WHERE FileId = " + fileId +
									 " AND StreamId = " + streamId + " AND FragNo = 1";
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Get file data details SQL: " + sql);
			
			//	Load the file details
			
			ResultSet rs = stmt.executeQuery(sql);

			if ( rs.next()) {
				
				//	Create the file details

				dbDetails = new DBDataDetails(fileId, streamId);

				if ( rs.getBoolean("JarFile") == true)
				  dbDetails.setJarId(rs.getInt("JarId"));
			}					
    }
    catch (SQLException ex) {

      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println(ex);
      
      //	Rethrow the exception
      
      throw new DBException(ex.getMessage());
    }
    finally {

      //	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (SQLException ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
    }
    
    //	If the file details are not valid throw an exception
    
    if ( dbDetails == null)
      throw new DBException("Failed to load file details for " + fileId + ":" + streamId);
    
    //	Return the file data details
    
    return dbDetails;
  }
  
  /**
   * Return the maximum data fragment size supported
   *
   * @return long 
   */
  public long getMaximumFragmentSize() {
    return 20 * MemorySize.MEGABYTE;
  }
  
  /**
   * Load file data from the database into a temporary/local file
   * 
   * @param fileId int
   * @param streamId int
   * @param fileSeg FileSegment
   * @throws DBException
   * @throws IOException
   */
  public void loadFileData(int fileId, int streamId, FileSegment fileSeg)
		throws DBException, IOException {

    //	Open the temporary file
		
		FileOutputStream fileOut = new FileOutputStream(fileSeg.getTemporaryFile());

		//	Update the segment status
					
		fileSeg.setStatus(FileSegmentInfo.Loading);
		
	  //	DEBUG

		long startTime = 0L;
		
		if ( Debug.EnableInfo && hasDebug())
		  startTime = System.currentTimeMillis();

	  //	Load the file data fragments
  
  	Connection conn = null;
	  Statement stmt = null;

		try {
			
			//	Get a connection to the database, create a statement
			
			conn = getConnection();
			stmt = conn.createStatement();
			
			String sql = "SELECT * FROM " + getDataTableName() + " WHERE FileId = " + fileId +
									 " AND StreamId = " + streamId + " ORDER BY FragNo";
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Load file data SQL: " + sql);
			
			//	Find the data fragments for the file, check if the file is stored in a Jar
			
		  ResultSet rs = stmt.executeQuery(sql);

			//	Load the file data from the main file record(s)

			byte[] inbuf = null;
			int buflen = 0;
			int fragNo = -1;
			int fragSize = -1;
			
			long totLen = 0L;
			  
			while ( rs.next()) {
	
		    //	Access the file data
	
				Blob dataBlob = rs.getBlob("Data");
				fragNo   = rs.getInt("FragNo");
				fragSize = rs.getInt("FragLen");
	
				InputStream dataFrag = dataBlob.getBinaryStream();
	
				//	Allocate the read buffer, if not already allocated
				
				if ( inbuf == null) {
					buflen = (int) Math.min(dataBlob.length(), MaxMemoryBuffer);
					inbuf = new byte[buflen];
				}
				
				//	Read the data from the database record and write to the output file

				int rdLen = dataFrag.read(inbuf, 0, inbuf.length);
				
				while ( rdLen > 0) {
					
					//	Write a block of data to the temporary file segment
	
					fileOut.write(inbuf, 0, rdLen);
					totLen += rdLen;
					
					//	Read another block of data
					
					rdLen = dataFrag.read(inbuf, 0, inbuf.length);
				}

				//	Signal to waiting threads that data is available
				
				fileSeg.setReadableLength(totLen);
				fileSeg.signalDataAvailable();
				
				//	Renew the lease on the database connection
					
				getConnectionPool().renewLease(conn);
		  }

			//	Close the resultset
			
			rs.close();

			//	DEBUG
			
			if ( Debug.EnableInfo && hasDebug()) {
				long endTime = System.currentTimeMillis();
				Debug.println("[Oracle] Loaded fid=" + fileId + ", stream=" + streamId + ", frags=" + fragNo + ", time=" + (endTime-startTime) + "ms");
			}
		}
		catch ( SQLException ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println(ex);
		  
		  //	Rethrow the exception
		  
		  throw new DBException(ex.getMessage());
		}
		finally {

			//	Check if a statement was allocated
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
				
			//	Close the output file
			
			if ( fileOut != null) {
				try {
					fileOut.close();
				}
				catch (Exception ex) {
					Debug.println(ex);
				}
			}
		}

		//	Signal that the file data is available
		
		fileSeg.signalDataAvailable();
  }
  
  /**
   * Load Jar file data from the database into a temporary file
   *
   * @param jarId int
   * @param jarSeg FileSegment
   * @throws DBException
   * @throws IOException
   */
  public void loadJarData(int jarId, FileSegment jarSeg)
		throws DBException, IOException {
    
	  //	Load the Jar file data
    
  	Connection conn = null;
	  Statement stmt = null;

	  FileOutputStream outJar = null;
	  
 		try {
  			
			//	Get a connection to the database, create a statement
			
			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT * FROM " + getJarDataTableName() + " WHERE JarId = " + jarId;

			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Load Jar data SQL: " + sql);
			
			//	Create the temporary Jar file
			
			outJar = new FileOutputStream(jarSeg.getTemporaryFile());
			
			//	Get the Jar data record
			
			ResultSet rs = stmt.executeQuery(sql);
			
			if ( rs.next()) {
			
				//	Access the Jar file data
				
				Blob dataBlob = rs.getBlob("Data");
				InputStream dataFrag = dataBlob.getBinaryStream();
				
				//	DEBUG
				
				if ( Debug.EnableInfo && hasSQLDebug())
				  Debug.println("[Oracle] Load Jar BLOB, length=" + dataBlob.length());
				
				//	Allocate the read buffer
				
				byte[] inbuf = new byte[(int) Math.min(dataBlob.length(), MaxMemoryBuffer)]; 				
				
				//	Read the Jar data from the database record and write to the output file
				
				int rdLen = dataFrag.read(inbuf, 0, inbuf.length);
				long totLen = 0L;
			
				while ( rdLen > 0) {
				
					//	Write a block of data to the temporary file segment
					
					outJar.write(inbuf, 0, rdLen);
					totLen += rdLen;
					
					//	Read another block of data
					
					rdLen = dataFrag.read(inbuf, 0, inbuf.length);
				}
			}
		
			//	Close the output Jar file
			
			outJar.close();
		
			//	Set the Jar file segment status to indicate that the data has been loaded
			
			jarSeg.setStatus(FileSegmentInfo.Available, false);
 		}
 		catch ( SQLException ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println(ex);
		  
		  //	Rethrow the exception
		  
		  throw new DBException(ex.getMessage());
 		}
 		finally {

			//	Check if a statement was allocated
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (Exception ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
				
			//	Close the output file
			
			if ( outJar != null) {
				try {
					outJar.close();
				}
				catch (Exception ex) {
					Debug.println(ex);
				}
			}
 		}
  }
  
  /**
   * Save the file data from the temporary/local file to the database
   *
   * @param fileId int
   * @param streamId int
   * @param fileSeg FileSegment
   * @return int
   * @throws DBException
   * @throws IOException
   */
  public int saveFileData(int fileId, int streamId, FileSegment fileSeg)
		throws DBException, IOException {

		//	Use a memory buffer to copy the file data fragments
    
		byte[] memBuf = new byte[(int) getDataFragmentSize()];
	
		//	Get the temporary file size
		
		File tempFile = new File( fileSeg.getTemporaryFile());
		
	  //	Save the file data

	  Connection conn = null;
		Statement delStmt = null;
		PreparedStatement insStmt = null;
		PreparedStatement blobStmt = null;
		
		int fragNo = 1;
		boolean autoCommitState = false;

		FileInputStream inFile = null;

		try {

		  //	Open the temporary file
		  
		  inFile = new FileInputStream(tempFile);
		  
			//	Get a connection to the database. Auto commit must be disabled as we will use SELECT ... FOR UPDATE.
		
			conn = getConnection();
			
			autoCommitState = conn.getAutoCommit();
			conn.setAutoCommit(false);
			
			//	Create the delete statement to delete the existing data records
			
			delStmt = conn.createStatement();

			String sql = "DELETE FROM " + getDataTableName() + " WHERE FileId = " + fileId +
									 " AND StreamId = " + streamId;
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Save file data SQL: " + sql);
			
			//	Delete any existing file data records for this file
		
			int recCnt = delStmt.executeUpdate(sql);
			delStmt.close();
			delStmt = null;
		
			//	Add the file data to the database
		
			sql = "INSERT INTO " + getDataTableName() + " (FileId,StreamId,FragNo,FragLen,Data) VALUES (?,?,?,?,EMPTY_BLOB())";
			insStmt  = conn.prepareStatement(sql);
			blobStmt = conn.prepareStatement("SELECT Data FROM " + getDataTableName() + " WHERE FileId = ? AND StreamId = ? AND FragNo = ? FOR UPDATE");

			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Save file data SQL: " + sql);
		
			long saveSize = tempFile.length();
			long fragSize = 0;
		
			while ( saveSize > 0) {
			
				//	Copy a block of data to the memory buffer
				
				fragSize = inFile.read(memBuf);

				//	Store the current fragment details with an empty BLOB field
			
				insStmt.clearParameters();
				insStmt.setInt(1, fileId);
				insStmt.setInt(2, streamId);
			  insStmt.setInt(3, fragNo);
			  insStmt.setInt(4, (int) fragSize);

			  if ( insStmt.executeUpdate() < 1 && hasDebug())
					Debug.println("## Oracle Failed to update file data, fid=" + fileId + ", stream=" + streamId + ", fragNo=" + fragNo);

			  //	Update the BLOB field for the record just written
			  
			  blobStmt.clearParameters();
			  blobStmt.setInt(1, fileId);
			  blobStmt.setInt(2, streamId);
			  blobStmt.setInt(3, fragNo);
			  
			  ResultSet blobRs = blobStmt.executeQuery();
			  
			  if ( blobRs.next()) {

			    //	Get an output stream to write the data to the BLOB field
			    
			    Blob dataBlob = blobRs.getBlob(1);
			    OutputStream blobOut = ((oracle.sql.BLOB) dataBlob).getBinaryOutputStream();
			    
			    blobOut.write( memBuf, 0, (int) fragSize);
			    blobOut.close();
			  }
			  else if ( hasDebug())
			    Debug.println("## Oracle Failed to get BLOB record, fid=" + fileId + ", stream=" + streamId + ", fragNo=" + fragNo);

			  //	Commit the data
			  
			  conn.commit();
			  
			  //	Update the fragment index
			  
			  fragNo++;
			  
			  //	Update the remaining data size to be saved
		  
			  saveSize -= fragSize;
			  
			  //	Renew the lease on the database connection so that it does not expire
			  
			  getConnectionPool().renewLease(conn);
			}
		}
		catch (SQLException ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println(ex);
		  
		  //	Rethrow the exception
		  
		  throw new DBException(ex.getMessage());
		}
		finally {
		
			//	Close the delete statement
		
			if  (delStmt != null) {
				try {
					delStmt.close();
				}
				catch (Exception ex) {
				}
			}
		
			//	Close the insert statement
		
			if ( insStmt != null) {
				try {
					insStmt.close();
				}
				catch (Exception ex) {
				}
			}

			//	Close the BLOB update statement
			
			if ( blobStmt != null) {
				try {
					blobStmt.close();
				}
				catch (Exception ex) {
				}
			}
			
			//	Release the database connection, restore the auto commit state
		
			if ( conn != null) {
			  try {
			    conn.setAutoCommit(autoCommitState);
			  }
			  catch (Exception ex) {
			  }
				releaseConnection(conn);
			}
			
			//	Close the input file
			
			if ( inFile != null) {
			  try {
			    inFile.close();
			  }
			  catch (Exception ex) {
			  }
			}
		}
		
		//	Return the number of data fragments used to save the file data
		
    return fragNo;
  }
  
  /**
   * Save the file data from a Jar file to the database
   * 
   * @param jarPath String
   * @param fileList DBDataDetailsList
   * @return int
   * @throws DBException
   * @throws IOException
   */
  public int saveJarData( String jarPath, DBDataDetailsList fileList)
  	throws DBException, IOException {

    //	Write the Jar file to the blob field in the Jar data table
  	
		Connection conn = null;
	  Statement stmt = null;
	  String sql = null;

	  boolean autoCommitState = false;
	  int jarId = -1;

	  FileInputStream inJar = null;
	  
	  try {

		  //	Get a connection to the database. Auto commit must be disabled as we will use SELECT ... FOR UPDATE.
	
		  conn = getConnection();

		  autoCommitState = conn.getAutoCommit();
		  conn.setAutoCommit(false);
		  
			//	Open the Jar file

		  File jarFile = new File(jarPath);
			inJar = new FileInputStream(jarFile);
			
			//	Allocate a buffer to read the Jar data into
			
			long bufSize = jarFile.length();
			if ( bufSize > MaxMemoryBuffer)
			  bufSize = MaxMemoryBuffer;
			    
			byte[] buf = new byte[( int) bufSize];
											
		  //	Add the Jar file data to the database

			sql = "INSERT INTO " + getJarDataTableName() + " (JarId,Data) VALUES (" + getTablePrefix() + "JarId.NEXTVAL,EMPTY_BLOB())";
		  stmt = conn.createStatement();

		  //	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Save Jar data SQL: " + sql);

			//	Insert the Jar record
			
			if ( stmt.executeUpdate( sql) > 0) {
			  
				//	Get the unique jar id allocated to the new Jar record
	
				ResultSet rs = stmt.executeQuery("SELECT " + getTablePrefix() + "JarId.CURRVAL FROM DUAL");
						
				if ( rs.next())
					jarId = rs.getInt(1); 

				//	Select the Jar record just inserted and write the blob data
				
				sql = "SELECT Data FROM " + getJarDataTableName() + " WHERE JarId = " + jarId + " FOR UPDATE";
				rs = stmt.executeQuery( sql);
				
				if ( rs.next()) {
				
				  Blob dataBlob = rs.getBlob(1);
				  OutputStream blobOut = ((oracle.sql.BLOB) dataBlob).getBinaryOutputStream();
				  
				  //	Read data from the Jar file and write to the blob stream
				  
				  long totLen = 0L;
				  int rdsize = 0;
				  
				  while ( totLen < jarFile.length()) {
				    
				    //	Read a block of data from the Jar file
				    
				    rdsize = inJar.read(buf, 0, buf.length);
				    if ( rdsize > 0)
				      blobOut.write(buf, 0, rdsize);
				    
				    //	Update the total read length
				    
				    totLen += rdsize;
				  }
				  
				  //	Close the blob stream
				  
				  blobOut.close();
				  
				  //	Close the Jar file
				  
				  inJar.close();
				  inJar = null;
				  
				  //	DEBUG
					
					if ( Debug.EnableInfo && hasSQLDebug())
					  Debug.println("[Oracle] Wrote " + totLen + " bytes to BLOB for Jar");
					
				  //	Commit the blob data
				  
				  conn.commit();
				}

				//	Re-enable auto commit
				
				conn.setAutoCommit(true);
				
				//	Update the jar id record for each file in the Jar

				for ( int i = 0; i < fileList.numberOfFiles(); i++) {
									
					//	Get the current file details
	
				  DBDataDetails dbDetails = fileList.getFileAt(i);
			
					//	Add the file data record(s) to the database
				
					stmt.executeUpdate("DELETE FROM " + getDataTableName() + " WHERE FileId = " + dbDetails.getFileId() + " AND StreamId = " + dbDetails.getStreamId());
					
					if ( stmt.executeUpdate("INSERT INTO " + getDataTableName() + " (FileId,StreamId,FragNo,JarId,JarFile) VALUES (" + dbDetails.getFileId() + "," + dbDetails.getStreamId() +
														 ", 1," + jarId + ",1)") < 1) {
					  
					  //	DEBUG
					  
					  if ( Debug.EnableInfo && hasDebug())
					    Debug.println("[Oracle] Failed to insert data record for fid=" + dbDetails.getFileId() + ", jarId=" + jarId);
					}
				}
			}
			else if ( hasDebug())
			  Debug.println("## Oracle Failed to store Jar data");
		}
	  catch ( SQLException ex) {
		  
		  //	DEBUG
		  
		  if ( Debug.EnableError && hasDebug())
		    Debug.println(ex);
		  
		  //	Rethrow the exception
		  
		  throw new DBException(ex.getMessage());
	  }
		finally {
	
		  //	Close the statement
				
		  if ( stmt != null) {
			  try {
				  stmt.close();
			  }
			  catch (SQLException ex) {
			  }
		  }
				
		  //	Release the database connection

		  if ( conn != null) {
		    
		    //	Reset the auto commit state
		    
		    try {
		      conn.setAutoCommit(autoCommitState);
		    }
		    catch ( Exception ex) {
		    }
		    
		    //	Release the database connection
		    
			  releaseConnection(conn);
		  }
		  
		  //	Close the Jar file
		  
		  if ( inJar != null) {
		    try {
		      inJar.close();
		    }
		    catch (IOException ex) {
		    }
		  }
		}
		
		//	Return the allocated Jar id
		
		return jarId;
  }
  
  /**
   * Delete the file data for the specified file/stream
   * 
   * @param fileId int
   * @param streamId int
   * @throws DBException
   * @throws IOException
   */
  public void deleteFileData(int fileId, int streamId)
  	throws DBException, IOException {

    //	Delete the file data records for the file or stream
    
	  Connection conn = null;
		Statement delStmt = null;

		try {
		  
	  	//	Get a connection to the database
	
		  conn = getConnection();
		  
			//	Need to delete the existing data
			
			delStmt = conn.createStatement();
			String sql = null;
			
			//	Check if the main file stream is being deleted, if so then delete all stream data too
			
			if ( streamId == 0)
				sql = "DELETE FROM " + getDataTableName() + " WHERE FileId = " + fileId;
			else
				sql = "DELETE FROM " + getDataTableName() + " WHERE FileId = " + fileId + " AND StreamId = " + streamId;
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Delete file data SQL: " + sql);
			
			//	Delete the file data records
			
			int recCnt = delStmt.executeUpdate(sql);
	
			//	Debug
			
			if ( Debug.EnableInfo && hasDebug() && recCnt > 0)
				Debug.println("[Oracle] Deleted file data fid=" + fileId + ", stream=" + streamId + ", records=" + recCnt);
	  }
	  catch (SQLException ex) {
	  	
	  	//	DEBUG
	  	
	  	if ( Debug.EnableInfo && hasDebug())
	  		Debug.println(ex);
	  }
	  finally {

			//	Close the delete statement
			
			if  (delStmt != null) {
				try {
					delStmt.close();
				}
				catch (Exception ex) {
				}
			}
				
			//	Release the database connection
	
			if ( conn != null)			
				releaseConnection(conn);
	  }
  }
  
  /**
   * Delete the file data for the specified Jar file
   *
   * @param jarId int
   * @throws DBException
   * @throws IOException
   */
  public void deleteJarData(int jarId)
		throws DBException, IOException {

    //	Delete the data records for the Jar file data
    
	  Connection conn = null;
		Statement delStmt = null;

		try {
		  
	  	//	Get a connection to the database
	
		  conn = getConnection();
		  
			//	Need to delete the existing data
			
			delStmt = conn.createStatement();
			String sql = "DELETE FROM " + getJarDataTableName() + " WHERE JarId = " + jarId;

			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Delete Jar data SQL: " + sql);
			
			//	Delete the Jar data records
			
			int recCnt = delStmt.executeUpdate(sql);
	
			//	Debug
			
			if ( Debug.EnableInfo && hasDebug() && recCnt > 0)
				Debug.println("[Oracle] Deleted Jar data jarId=" + jarId + ", records=" + recCnt);
	  }
	  catch (SQLException ex) {
	  	
	  	//	DEBUG
	  	
	  	if ( Debug.EnableInfo && hasDebug())
	  		Debug.println(ex);
	  }
	  finally {

			//	Close the delete statement
			
			if  (delStmt != null) {
				try {
					delStmt.close();
				}
				catch (Exception ex) {
				}
			}
				
			//	Release the database connection
	
			if ( conn != null)			
				releaseConnection(conn);
	  }
  }
  
  //***** DBObjectIdInterface Methods *****
  
  /**
   * Create a file id to object id mapping
   *
   * @param fileId int
   * @param streamId int
   * @param objectId String
   * @exception DBException
   */
  public void saveObjectId(int fileId, int streamId, String objectId)
  	throws DBException {
    
    //	Create a new file id/object id mapping record

    Connection conn = null;
    Statement stmt  = null;

    try {

      //	Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();

			//	Delete any current mapping record for the object
			
			String sql = "DELETE FROM " + getObjectIdTableName() + " WHERE FileId = " + fileId + " AND StreamId = " + streamId;
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Save object id SQL: " + sql);

			//	Delete any current mapping record
			
			stmt.executeUpdate( sql);
			
			//	Insert the new mapping record
			
			sql = "INSERT INTO " + getObjectIdTableName() + " (FileId,StreamId,ObjectID) VALUES(" + fileId +
					  "," + streamId + ",'" + objectId + "')";
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Save object id SQL: " + sql);
			
			//	Create the mapping record
			
			if ( stmt.executeUpdate(sql) == 0)
			  throw new DBException("Failed to add object id record, fid=" + fileId + ", objId=" + objectId);
    }
    catch (SQLException ex) {

      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println(ex);
      
      //	Rethrow the exception
      
      throw new DBException(ex.getMessage());
    }
    finally {

      //	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (SQLException ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
    }
  }
  
  /**
   * Load the object id for the specified file id
   * 
   * @param fileId int
   * @param streamId int
   * @return String
   * @exception DBException
   */
  public String loadObjectId(int fileId, int streamId)
  	throws DBException {
    
    //	Load the object id for the specified file id

    Connection conn = null;
    Statement stmt  = null;

    String objectId = null;
    
    try {

      //	Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "SELECT ObjectId FROM " + getObjectIdTableName() + " WHERE FileId = " + fileId + " AND StreamId = " + streamId;
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Load object id SQL: " + sql);
			
			//	Load the mapping record

			ResultSet rs = stmt.executeQuery(sql);
			
			if ( rs.next())
			  objectId = rs.getString("ObjectId");
    }
    catch (SQLException ex) {

      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println(ex);
      
      //	Rethrow the exception
      
      throw new DBException(ex.getMessage());
    }
    finally {

      //	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (SQLException ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
    }
    
    //	Return the object id
    
    return objectId;
  }
  
  /**
   * Delete a file id/object id mapping
   *
   * @param fileId int
   * @param streamId int
   * @param objectId String
   * @exception DBException
   */
  public void deleteObjectId(int fileId, int streamId, String objectId)
  	throws DBException {
    
    //	Delete a file id/object id mapping record

    Connection conn = null;
    Statement stmt  = null;

    try {

      //	Get a connection to the database
			
			conn = getConnection();
			stmt = conn.createStatement();

			String sql = "DELETE FROM " + getObjectIdTableName() + " WHERE FileId = " + fileId + " AND StreamId = " + streamId;
			
			//	DEBUG
			
			if ( Debug.EnableInfo && hasSQLDebug())
			  Debug.println("[Oracle] Delete object id SQL: " + sql);
			
			//	Delete the mapping record

			stmt.executeUpdate(sql);
    }
    catch (SQLException ex) {

      //	DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println(ex);
      
      //	Rethrow the exception
      
      throw new DBException(ex.getMessage());
    }
    finally {

      //	Close the statement
			
			if ( stmt != null) {
				try {
					stmt.close();
				}
				catch (SQLException ex) {
				}
			}
			
			//	Release the database connection
			
			if ( conn != null)
				releaseConnection(conn);
    }
  }
  
  /**
   * Return the data for a symbolic link
   * 
   * @param dirId int 
   * @param fid int
   * @return String
   * @exception DBException
   */
  public String readSymbolicLink( int dirId, int fid)
    throws DBException {
    

    //  Read the symbolic link data

    Connection conn = null;
    Statement stmt  = null;

    String symLink = null;
    
    try {

      //  Get a connection to the database
      
      conn = getConnection();
      stmt = conn.createStatement();

      String sql = "SELECT SymLink FROM " + getSymLinksTableName() + " WHERE FileId = " + fid;
      
      //  DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Read symbolic link: " + sql);
      
      //  Load the mapping record

      ResultSet rs = stmt.executeQuery(sql);
      
      if ( rs.next())
        symLink = rs.getString("SymLink");
      else
        throw new DBException("Failed to load symbolic link data for " + fid);
    }
    catch (SQLException ex) {

      //  DEBUG
      
      if ( Debug.EnableError && hasDebug())
        Debug.println(ex);
      
      //  Rethrow the exception
      
      throw new DBException(ex.getMessage());
    }
    finally {

      //  Close the statement
      
      if ( stmt != null) {
        try {
          stmt.close();
        }
        catch (SQLException ex) {
        }
      }
      
      //  Release the database connection
      
      if ( conn != null)
        releaseConnection(conn);
    }
    
    // Return the symbolic link data
    
    return symLink;
  }
  
  /**
   * Delete a symbolic link record
   * 
   * @param dirId int 
   * @param fid int
   * @exception DBException
   */
  public void deleteSymbolicLinkRecord( int dirId, int fid)
    throws DBException {
    
    //  Delete the symbolic link record for a file
    
    Connection conn = null;
    Statement delStmt = null;

    try {
      
      //  Get a connection to the database
  
      conn = getConnection();
      delStmt = conn.createStatement();

      String sql = "DELETE FROM " + getSymLinksTableName() + " WHERE FileId = " + fid;
      
      //  DEBUG
      
      if ( Debug.EnableInfo && hasSQLDebug())
        Debug.println("[Oracle] Delete symbolic link SQL: " + sql);
      
      //  Delete the symbolic link record
      
      int recCnt = delStmt.executeUpdate(sql);
  
      //  Debug
      
      if ( Debug.EnableInfo && hasDebug() && recCnt > 0)
        Debug.println("[Oracle] Deleted symbolic link fid=" + fid);
    }
    catch (SQLException ex) {
      
      //  DEBUG
      
      if ( Debug.EnableInfo && hasDebug())
        Debug.println(ex);
    }
    finally {

      //  Close the delete statement
      
      if  (delStmt != null) {
        try {
          delStmt.close();
        }
        catch (Exception ex) {
        }
      }
        
      //  Release the database connection
  
      if ( conn != null)      
        releaseConnection(conn);
    }
  }
  
  /**
   * Check for special characters within a file/directory name and escape the characters to return 
   * a string which can be used in a SQL statement.
   * 
   * @param name String
   * @return String
   */
  protected String checkNameForSpecialChars(String name) {
    
    //  Check for a null string
    
    if ( name == null || name.length() == 0)
      return name;
      
    //  Check if the file/directory name contains special characters
    
    int idx = 0;
    boolean specChars = false;
    
    while ( idx < m_specialChars.length() && specChars == false) {
      
      //  Check if the name contains the current special character
      
      if ( name.indexOf( m_specialChars.charAt(idx++)) != -1)
        specChars = true;
    }
    
    //  Check if any special characters were found
    
    if ( specChars == false)
      return name;
      
    //  Escape any special characters within the file/directory name
    
    StringBuffer nameBuf = new StringBuffer(name.length() * 2);
    
    for ( int i = 0; i < name.length(); i++) {
      
      //  Get the current character and check if it needs to be escaped
      
      char curChar = name.charAt(i);
      
      //  Append the character to the escape string
      
      if ( curChar == '\'')
        nameBuf.append('\'');
      else if ( m_specialChars.indexOf(curChar) != -1)
        nameBuf.append("\\");
      nameBuf.append(curChar);
    }
    
    //  Return the escaped file/directory name
    
    return nameBuf.toString();
  }
  
  /**
   * Convert a file id to a share relative path
   *
   * @param fileid int
   * @param stmt Statement
   * @return String
   */
  private String buildPathForFileId(int fileid, Statement stmt) {

    //  Build an array of folder names working back from the files id
    
    StringList names = new StringList();
      
    try {

      //  Loop, walking backwards up the tree until we hit root
      
      int curFid = fileid;
      
      do {
            
        //  Search for the current file record in the database

        ResultSet rs = stmt.executeQuery("SELECT DirId,FileName FROM " + getFileSysTableName() + " WHERE FileId = " + curFid);

        if ( rs.next()) {
          
          //  Get the filename
          
          names.addString( rs.getString( "FileName"));
          
          //  The directory id becomes the next file id to search for
          
          curFid = rs.getInt( "DirId");
          
          //  Close the resultset
          
          rs.close();
        }
        else
          return null;
      
      } while ( curFid > 0);
    }
    catch ( SQLException ex) {
      
      //  DEBUG
      
      if ( hasDebug())
        Debug.println( ex);
      return null;
    }

    //  Build the path string

    StringBuffer pathStr = new StringBuffer (256);
    pathStr.append(FileName.DOS_SEPERATOR_STR);
    
    for ( int i = names.numberOfStrings() - 1; i >= 0; i--) {
      pathStr.append(names.getStringAt(i));
      pathStr.append(FileName.DOS_SEPERATOR_STR);
    }
    
    //  Remove the trailing slash from the path
    
    if ( pathStr.length() > 0)
      pathStr.setLength(pathStr.length() - 1);
    
    //  Return the path string
    
    return pathStr.toString();
  }
}
