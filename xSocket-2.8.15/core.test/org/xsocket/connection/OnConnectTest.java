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

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;


/**
*
* @author grro@xsocket.org
*/
public final class OnConnectTest {

	private static final String DELIMITER = "x"; 
	private static final String GREETING = "helo";
	
	


	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		String greeting = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals(greeting, GREETING);
		
		String request = "reert";
		connection.write(request + DELIMITER);
		
		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		Assert.assertEquals(request, response);
		
		
		connection.close();
		server.close();
	}


	
	private static class ServerHandler implements IDataHandler, IConnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			
			connection.write(GREETING + DELIMITER);
			connection.flush();
			return true;
		}

		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.write(connection.readByteBufferByDelimiter(DELIMITER, Integer.MAX_VALUE));
			connection.write(DELIMITER);
			connection.flush();
			return true;
		}
	}
}
