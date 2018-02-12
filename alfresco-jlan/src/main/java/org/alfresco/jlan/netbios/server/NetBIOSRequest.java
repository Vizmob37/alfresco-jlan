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

package org.alfresco.jlan.netbios.server;

import org.alfresco.jlan.netbios.NetBIOSName;

/**
 * NetBIOS Request Class
 * 
 * <p>Contains the details of NetBIOS server request, such as an add name request.
 *
 * @author gkspencer
 */
class NetBIOSRequest {

	//	Request types
	
	public final static int AddName			= 0;
	public final static int DeleteName	= 1;
	public final static int RefreshName	= 2;
	
	//	Default retry count and interval
	
	public final static int DefaultRetries		= 5;
	public final static long DefaultInterval	= 2000;	//	ms

	//	Requets type strings
	
	private final static String[] _typeNames = {"AddName", "DelName", "RefreshName"};
		
	//	Request type
	
	private int m_type;
	
	//	NetBIOS name details
	
	private NetBIOSName m_nbName;
	
	//	Retry count and interval
	
	private int m_retry;
	private long m_retryIntvl;
	
	//	Response status
	
	private boolean m_error;
	
	//	Transaction id for this request
	
	private int m_tranId;
	
	/**
	 * Class constructor
	 * 
	 * @param typ int
	 * @param nbName NetBIOSName
	 * @param tranId int
	 */
	public NetBIOSRequest(int typ, NetBIOSName nbName, int tranId) {
		m_type   = typ;
		m_nbName = nbName;
		m_tranId = tranId;
		
		m_retry      = DefaultRetries;
		m_retryIntvl = DefaultInterval;

		m_error = false;
	}
	
	/**
	 * Class constructor
	 * 
	 * @param typ int
	 * @param nbName NetBIOSName
	 * @param tranId int
	 * @param retry int
	 */
	public NetBIOSRequest(int typ, NetBIOSName nbName, int tranId, int retry) {
		m_type   = typ;
		m_nbName = nbName;
		m_tranId = tranId;
		
		m_retry      = retry;
		m_retryIntvl = DefaultInterval;

		m_error = false;
	}
	
	/**
	 * Return the request type
	 * 
	 * @return int
	 */
	public final int isType() {
		return m_type;
	}

	/**
	 * Return the type as a string
	 * 
	 * @return String
	 */
	public final String getTypeAsString() {
		if ( m_type < 0 || m_type >= _typeNames.length)
			return "";
		return _typeNames[m_type];
	}
	
	/**
	 * Return the NetBIOS name details
	 * 
	 * @return NetBIOSName
	 */
	public final NetBIOSName getNetBIOSName() {
		return m_nbName;
	}

	/**
	 * Return the retry count
	 * 
	 * @return int
	 */
	public final int getRetryCount() {
		return m_retry;
	}
	
	/**
	 * Return the retry interval
	 * 
	 * @return long
	 */
	public final long getRetryInterval() {
		return m_retryIntvl;
	}

	/**
	 * Return the transaction id
	 * 
	 * @return int
	 */
	public final int getTransactionId() {
		return m_tranId;
	}

	/**
	 * Check if the request has an error status
	 * 
	 * @return boolean
	 */
	public final boolean hasErrorStatus() {
		return m_error;
	}
			
	/**
	 * Decrement the retry count
	 * 
	 * @return int
	 */
	protected final int decrementRetryCount() {
		return m_retry--;
	}
	
	/**
	 * Set the error status
	 * 
	 * @param sts boolean
	 */
	protected final void setErrorStatus(boolean sts) {
		m_error = sts;
	}

	/**
	 * Set the request retry count
	 * 
	 * @param retry int
	 */
	public final void setRetryCount(int retry) {
		m_retry = retry;	
	}
	
	/**
	 * Set the retry interval, in milliseconds
	 * 
	 * @param interval long
	 */
	public final void setRetryInterval(long interval) {
		m_retryIntvl = interval;
	}

	/**
	 * Return the request as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();
		
		str.append("[");
		str.append(getTypeAsString());
		str.append(":");
		str.append(getNetBIOSName());
		str.append(",");
		str.append(getRetryCount());
		str.append(",");
		str.append(getRetryInterval());
		str.append(",");
		str.append(getTransactionId());
		str.append("]");
		
		return str.toString();
	}
}
