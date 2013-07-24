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
 * 处理空闲超时.超时时间由服务器定义.
 * server.setIdleTimeoutMillis()		</br>
 * 
 * Handles idle timeout. The timeouts will be defined by the server. To modify the timeouts
 * the proper server methods has to be called. E.g.<br>
 * <pre>
 *    ...
 *    IServer server = new Server(new MyHandler());
 *    server.setIdleTimeoutMillis(60 * 1000);
 *    ConnectionUtils.start(server);
 *    ...
 *    
 *    
 *    class MyHandler implements IIdleTimeoutHandler {
 *        
 *        public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
 *           ...
 *           connection.close();
 *           return true;  // true -> event has been handled (by returning false xSocket will close the connection)
 *        }
 *    }
 * </pre>
 * 
 * @author grro@xsocket.org
 */
public interface IIdleTimeoutHandler extends IHandler {

	/**
	 * handles the idle timeout.
	 * 
	 * @param connection the underlying connection
	 * @return true if the timeout event has been handled (in case of false the connection will be closed by the server)
	 * @throws IOException if an error occurs. Throwing this exception causes that the underlying connection will be closed.
	 */
	boolean onIdleTimeout(INonBlockingConnection connection) throws IOException;
}
