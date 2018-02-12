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

package org.alfresco.jlan.smb.server.notify;

import org.alfresco.jlan.server.filesys.NotifyChange;

/**
 * Notify Change Event Class
 * 
 * <p>Contains the details of a change notification event
 *
 * @author gkspencer
 */
public class NotifyChangeEvent {

	//	Notification event action and filter type
	
	private int m_action;
	private int m_filter;
	
	//	Notification file/directory name
	
	private String m_fileName;

	//	Path is a directory
	
	private boolean m_dir;
	
	//	Original file name for file/directory rename
	
	private String m_oldName;
		
	/**
	 * Class constructor
	 * 
	 * @param filter int
	 * @param action int
	 * @param fname String
	 * @param dir boolean
	 */
	public NotifyChangeEvent(int filter, int action, String fname, boolean dir) {
		m_filter   = filter;
		m_action   = action;
		m_fileName = fname;
		m_dir      = dir;
		
		//	Normalize the path
		
		if ( m_fileName.indexOf('/') != -1)
		  m_fileName = m_fileName.replace('/', '\\');
	}

	/**
	 * Class constructor
	 * 
	 * @param filter int
	 * @param action int
	 * @param fname String
	 * @param oldname String
	 * @param dir boolean
	 */
	public NotifyChangeEvent(int filter, int action, String fname, String oldname, boolean dir) {
		m_filter   = filter;
		m_action   = action;
		m_fileName = fname;
		m_oldName  = oldname;
		m_dir      = dir;
		
		//	Normalize the path
		
		if ( m_fileName.indexOf('/') != -1)
		  m_fileName = m_fileName.replace('/', '\\');
		
		if ( m_oldName.indexOf('/') != -1)
		  m_oldName.replace('/', '\\');
	}

	/**
	 * Return the event filter type
	 * 
	 * @return int
	 */	
	public final int getFilter() {
		return m_filter;
	}
	
	/**
	 * Return the action
	 * 
	 * @return int
	 */
	public final int getAction() {
		return m_action;
	}
	
	/**
	 * Return the file/directory name
	 * 
	 * @return String
	 */
	public final String getFileName() {
		return m_fileName;
	}

	/**
	 * Return the file/directory name only by stripping any leading path
	 * 
	 * @return String
	 */
	public final String getShortFileName() {
		
		//	Find the last '\' in the path string
		
		int pos = m_fileName.lastIndexOf("\\");
		if ( pos != -1)
			return m_fileName.substring(pos+1);
		return m_fileName;
	}

	/**
	 * Return the old file/directory name, for rename events
	 * 
	 * @return String
	 */	
	public final String getOldFileName() {
		return m_oldName;
	}

	/**
	 * Return the old file/directory name only by stripping any leading path
	 * 
	 * @return String
	 */
	public final String getShortOldFileName() {
	
		//	Check if the old path string is valid
		
		if ( m_oldName == null)
			return null;
				
		//	Find the last '\' in the path string
		
		int pos = m_oldName.lastIndexOf("\\");
		if ( pos != -1)
			return m_oldName.substring(pos+1);
		return m_oldName;
	}

	/**
	 * Check if the old file/directory name is valid
	 * 
	 * @return boolean
	 */	
	public final boolean hasOldFileName() {
		return m_oldName != null ? true : false;
	}
	
	/**
	 * Check if the path refers to a directory
	 *
	 * @return boolean
	 */
	public final boolean isDirectory() {
		return m_dir;
	}
	
	/**
	 * Return the notify change event as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();
		
		str.append("[");
		str.append(NotifyChange.getFilterAsString(getFilter()));
		str.append("-");
		str.append(NotifyChange.getActionAsString(getAction()));
		str.append(":");
		str.append(getFileName());
		
		if ( isDirectory())
			str.append(",DIR");
			
		if ( hasOldFileName()) {
			str.append(",Old=");
			str.append(getOldFileName());
		}

		str.append("]");
		
		return str.toString();
	}
}
