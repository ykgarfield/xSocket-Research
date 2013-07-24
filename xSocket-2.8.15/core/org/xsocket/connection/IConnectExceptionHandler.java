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

/**
 * 处理连接异常.只支持客户端	</br>
 * 
 * Handles connect exception. This handler is only supported on the client-side  <br><br>
 * 
 * E.g. 
 * <pre>
 * Handler implements IConnectHandler, IConnectExceptionHandler, IDataHandler {
 * 
 *    public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
 *       ...
 *    }
 *
 *    public boolean onConnectException(INonBlockingConnection connection, IOException ioe) throws IOException {
 *       ...
 *    }
 *        
 *    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
 *       ...
 *    }
 *    
 * }
 * 
 *  
 *  Handler hdl = new Handler();
 *  INonBlockingConnection con = new NonBlockingConnection(InetAddress.getByName(host), port, hdl, false, 500);
 *  ...
 *  <pre>
 * 
 * 
 * @author grro@xsocket.org
 */
public interface IConnectExceptionHandler extends IHandler {
 

    /**
     * handle a connect exception
     * 
     * 
     * @param connection  the connection
     * @param ioe  the exception
     * @return true for positive result of handling, false for negative result of handling 
     * @throws IOException if some other I/O error occurs. Throwing this exception causes that the underlying connection will be closed.
     */
	boolean onConnectException(INonBlockingConnection connection, IOException ioe) throws IOException;
}
