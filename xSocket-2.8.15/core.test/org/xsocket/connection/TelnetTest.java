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
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionScoped;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class TelnetTest  {


	@Test 
	public void testSimple() throws Exception {
		
		IServer server = new Server(new ServerHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

		con.write("A test\r\n");
		String response = con.readStringByDelimiter("\r\n");
		Assert.assertEquals("OK", response);
		
		con.write("other\r\n");
		response = con.readStringByDelimiter("\r\n");
		Assert.assertEquals("other failed", response);
		
		System.out.println("finished");
		con.close();
		server.close();
	}
	
	
	
	private static final class ServerHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			String cmd = connection.readStringByDelimiter("\r\n");
			if (cmd.startsWith("A")) {
				connection.write("OK\r\n");
			} else {
				connection.write(cmd + " failed\r\n");
			}
			return true;
		}
	}
}
