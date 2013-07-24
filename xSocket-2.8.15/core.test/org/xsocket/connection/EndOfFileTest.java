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

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class EndOfFileTest  {

	

	@Test 
	public void testClientSideNonClosed() throws Exception {
		IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server); 

		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
		
		int length = 3;
		con.write(length);
		QAUtil.sleep(1000);
		
		for (int i = 0; i < length; i++) {
			Assert.assertTrue(con.available() > 0);
			String txt = con.readStringByDelimiter("\r\n");
		}
		
		Assert.assertEquals(0, con.available());

		con.close();
		server.close();
	}


	@Test 
	public void testClientSideClosed() throws Exception {
		IServer server = new Server(new ClosingServerHandler());
		ConnectionUtils.start(server); 

		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
		
		int length = 3;
		con.write(length);
		QAUtil.sleep(1000);
		
		for (int i = 0; i < length; i++) {
			System.out.println(con.available());
			String txt = con.readStringByDelimiter("\r\n");
		}
		
		Assert.assertEquals(-1, con.available());

		con.close();
		server.close();
	}

	


	private static final class ServerHandler implements IDataHandler {
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			int length = connection.readInt();
			
			for (int i = 0; i < length; i++) {
				connection.write("test\r\n");
			}
			return true;
		}
	}
	
	private static final class ClosingServerHandler implements IDataHandler {
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			int length = connection.readInt();
			
			for (int i = 0; i < length; i++) {
				connection.write("test\r\n");
			}
			
			connection.close();
			return true;
		}
	}
}
