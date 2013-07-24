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
import java.util.HashMap;
import java.util.Map;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class SocketOptionsTest {

	private static final String DELIMITER = "\r\n\r\n\r";
	
	private String error = null;

	
	@Test 
	public void testOption() throws Exception {
		IServer server = new Server(new TestHandler());
		ConnectionUtils.start(server);	
		
		BlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		
		boolean noDelay = (Boolean) connection.getOption(IConnection.TCP_NODELAY);
		
		
		connection.setOption(IConnection.TCP_NODELAY, !noDelay);
		Assert.assertEquals(!noDelay, (Boolean) connection.getOption(IConnection.TCP_NODELAY));

		
		connection.write("Hello" + DELIMITER);
		connection.flush();

		connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.close();
		server.close();
		
		Assert.assertNull(error, error);
	}


	@Test 
	public void testBlockingConnectionOption() throws Exception {
		IServer server = new Server(new TestHandler());
		ConnectionUtils.start(server);	
		
		
		Map<String, Object> opts = new HashMap<String, Object>();
		opts.put(IConnection.SO_LINGER, 1000);
		opts.put(IConnection.SO_SNDBUF, 8899);
		opts.put(IConnection.SO_RCVBUF, 9988);
		opts.put(IConnection.TCP_NODELAY, true);
		opts.put(IConnection.SO_KEEPALIVE, true);
		
		BlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort(), opts);
		connection.setAutoflush(false);
		
		Assert.assertTrue(connection.getOption(IConnection.SO_LINGER).equals(1000));
		Assert.assertTrue(connection.getOption(IConnection.SO_SNDBUF).equals(8899));
		Assert.assertTrue(connection.getOption(IConnection.SO_RCVBUF).equals(9988));
		Assert.assertTrue(connection.getOption(IConnection.TCP_NODELAY).equals(true));
		Assert.assertTrue(connection.getOption(IConnection.SO_KEEPALIVE).equals(true));
		
		connection.write("Hello" + DELIMITER);
		connection.flush();

		connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		
		connection.close();
		server.close();
		
		Assert.assertNull(error, error);
	}


	



	

	private static class TestHandler implements IDataHandler, IConnectHandler {
		
	
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			byte[] buffer = connection.readBytesByDelimiter(DELIMITER, Integer.MAX_VALUE);

			connection.write(buffer);
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
	}
}
