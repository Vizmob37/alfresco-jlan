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

package org.alfresco.jlan.server.core;

/**
 * <p>Available shared resource types.
 *
 * @author gkspencer
 */
public class ShareType {

  // Disk share resource type.

  public static final int DISK = 0;

  // Printer share resource type.

  public static final int PRINTER = 1;

  // Named pipe/IPC share resource type.

  public static final int NAMEDPIPE = 2;

  //	Remote administration named pipe, IPC$

  public static final int ADMINPIPE = 3;

  //	Unknown share type

  public static final int UNKNOWN = -1;
  
  /**
   * Return the share type as a share information type.
   *
   * @return int
   * @param typ int
   */
  public final static int asShareInfoType(int typ) {

    //  Convert the share type value to a valid share information structure share type
    //  value.

    int shrTyp = 0;

    switch (typ) {
      case DISK :
        shrTyp = 0;
        break;
      case PRINTER :
        shrTyp = 1;
        break;
      case NAMEDPIPE :
      case ADMINPIPE :
        shrTyp = 3;
        break;
    }
    return shrTyp;
  }
  
  /**
   * Return the SMB service name as a shared device type.
   *
   * @return int
   * @param srvName java.lang.String
   */
  public final static int ServiceAsType(String srvName) {

    //  Check the service name

    if (srvName.compareTo("A:") == 0)
      return DISK;
    else if (srvName.compareTo("LPT1:") == 0)
      return PRINTER;
    else if (srvName.compareTo("IPC") == 0)
      return NAMEDPIPE;

    //  Unknown service name string

    return UNKNOWN;
  }
  
  /**
   * Return the share type as a service string.
   *
   * @return java.lang.String
   * @param typ int
   */
  public final static String TypeAsService(int typ) {

    if (typ == DISK)
      return "A:";
    else if (typ == PRINTER)
      return "LPT1:";
    else if (typ == NAMEDPIPE || typ == ADMINPIPE)
      return "IPC";
    return "";
  }
  
  /**
   * Return the share type as a string.
   *
   * @return java.lang.String
   * @param typ int
   */
  public final static String TypeAsString(int typ) {

    if (typ == DISK)
      return "DISK";
    else if (typ == PRINTER)
      return "PRINT";
    else if (typ == NAMEDPIPE)
      return "PIPE";
    else if (typ == ADMINPIPE)
      return "IPC$";
    return "<Unknown>";
  }
}
