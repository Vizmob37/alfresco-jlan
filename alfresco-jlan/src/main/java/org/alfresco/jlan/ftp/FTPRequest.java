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

package org.alfresco.jlan.ftp;

/**
 * FTP Request Class
 * 
 * <p>Contains the details of an FTP request
 *
 * @author gkspencer
 */
public class FTPRequest {

	//	FTP command id
	
	private int m_cmd;
	
	//	Command argument
	
	private String m_arg;

	/**
	 * Default constructor
	 */
	public FTPRequest() {
		m_cmd = FTPCommand.InvalidCmd;
	}
	
	/**
	 * Class constructor
	 * 
	 * @param cmd int
	 * @param arg String
	 */
	public FTPRequest(int cmd, String arg) {
		m_cmd = cmd;
		m_arg = arg;
	}
		
	/**
	 * Class constructor
	 * 
	 * @param cmdLine String
	 */
	public FTPRequest(String cmdLine) {

		//	Parse the FTP command record
		
		parseCommandLine(cmdLine);
	}

	/**
	 * Return the command index
	 * 
	 * @return int
	 */
	public final int isCommand() {
		return m_cmd;
	}

	/**
	 * Check if the request has an argument
	 * 
	 * @return boolean		
	 */
	public final boolean hasArgument() {
		return m_arg != null ? true : false;
	}
	
	/**
	 * Return the request argument
	 * 
	 * @return String
	 */
	public final String getArgument() {
		return m_arg;
	}
	
	/**
	 * Set the command line for the request
	 * 
	 * @param cmdLine String
	 * @return int
	 */
	public final int setCommandLine(String cmdLine) {
		
		//	Reset the current values
		
		m_cmd = FTPCommand.InvalidCmd;
		m_arg = null;
		
		//	Parse the new command line
		
		parseCommandLine(cmdLine);
		return isCommand();
	}
	
	/**
	 * Parse a command string
	 * 
	 * @param cmdLine String
	 */
	protected final void parseCommandLine(String cmdLine) {
		
		//	Check if the command has an argument
		
		int pos = cmdLine.indexOf(' ');
		String cmd = null;
		
		if ( pos != -1) {
			cmd = cmdLine.substring(0, pos);
			m_arg = cmdLine.substring(pos + 1);
		}
		else
			cmd = cmdLine;
			
		//	Validate the FTP command
		
		m_cmd = FTPCommand.getCommandId(cmd);
	}
  
  /**
   * Update the command argument
   * 
   * @param arg String
   */
  protected final void updateArgument(String arg) {
    m_arg = arg;
  }
  
  /**
   * Return the FTP request as a string
   * 
   * @return String
   */
  public String toString() {
    StringBuffer str = new StringBuffer();
    
    str.append("[");
    str.append( FTPCommand.getCommandName( m_cmd));
    str.append(":");
    str.append( m_arg);
    str.append("]");
    
    return str.toString();
  }
}
