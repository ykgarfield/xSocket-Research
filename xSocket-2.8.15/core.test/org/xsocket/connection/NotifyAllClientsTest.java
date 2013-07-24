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
import java.util.ArrayList;
import java.util.List;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;



/**
*
* @author grro@xsocket.org
*/
public final class NotifyAllClientsTest {


	@Test 
	public void testSimple() throws Exception {
		Server server = new Server(new ServerHandler());
		server.start();
		
		List<IBlockingConnection> connections = new ArrayList<IBlockingConnection>();
		for (int i = 0; i < 15; i++) {
			IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
			
			for (int j = 0; j < 10; j++) {
				con.write("test\r\n");
				Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
			}
		}
		
		
		for (INonBlockingConnection serverCon : server.getOpenConnections()) {
			synchronized (serverCon) {
				serverCon.write("notify\r\n");				
			}
		}
		
		
		for (IBlockingConnection connection : connections) {
			Assert.assertEquals("notify", connection.readStringByDelimiter("\r\n"));
			connection.close();
		}
		
		server.close();
	}

	
	
	private static final class ServerHandler implements IDataHandler {	
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			
			synchronized (connection) {
				connection.write(connection.readStringByDelimiter("\r\n") + "\r\n");
			}
			
			return true;
		}
	}
}
