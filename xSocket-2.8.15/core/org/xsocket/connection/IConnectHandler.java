/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket.connection;

import java.io.IOException;
import java.nio.BufferUnderflowException;

import org.xsocket.MaxReadSizeExceededException;

/**
 * 处理新的连接.		</br>
 * 
 * Handles new incoming connections. <br><br>
 * 
 * E.g. 
 * <pre>
 * public final class BlackIpHandler implements IConnectHandler {
 *   ...
 *   
 *   public boolean onConnect(INonBlockingConnection connection) throws IOException {
 *      connection.write("220 my smtp server" + LINE_DELIMITER);
 *      return true;
 *   }
 * } 
 * <pre>
 * 
 * @author grro@xsocket.org
 */
public interface IConnectHandler extends IHandler {
 
	
	/**
	 * handles a new incoming connection
	 * 
	 * @return true for positive result of handling, false for negative result of handling
     * @throws IOException if some other I/O error occurs. Throwing this exception causes that the underlying connection will be closed.
	 * @throws BufferUnderflowException if more incoming data is required to process (e.g. delimiter hasn't yet received -> readByDelimiter methods or size of the available, received data is smaller than the required size -> readByLength). The BufferUnderflowException will be swallowed by the framework
 	 * @throws MaxReadSizeExceededException if the max read size has been reached (e.g. by calling method {@link INonBlockingConnection#readStringByDelimiter(String, int)}).
 	 *                                      Throwing this exception causes that the underlying connection will be closed.
     * @throws RuntimeException if an runtime exception occurs. Throwing this exception causes that the underlying connection will be closed.                                      
	 */
	boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException;
}
