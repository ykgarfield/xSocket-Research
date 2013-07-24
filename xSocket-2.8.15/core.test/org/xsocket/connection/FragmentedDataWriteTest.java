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
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;



import org.junit.Assert;
import org.junit.Test;


import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class FragmentedDataWriteTest {

		 
	@Test 
	public void testSmall() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);
		
		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		
		
		for (int i = 0; i < 10; i++) {
			String request = new String(QAUtil.generateByteArray(100));
			
			bc.write(request + "\r\n");
			String response = bc.readStringByDelimiter("\r\n");
			
			Assert.assertEquals(request, response);
		}
		
		server.close();
	}

	
	@Test 
	public void testLarge() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);
		
		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		
		for (int i = 0; i < 10; i++) {
			String request = new String(QAUtil.generateByteArray(54321));
			
			bc.write(request + "\r\n");
			String response = bc.readStringByDelimiter("\r\n");
			
			Assert.assertEquals(request, response);
		}
		
		server.close();
	}
	
	
	
	@Test 
	public void testLargeFragmented() throws Exception {
	
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);
		
		BlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		bc.setAutoflush(false);

		
		for (int i = 0; i < 30; i++) {
		    System.out.println(i);
		    String request0 = new String(QAUtil.generateByteArray(21));
			String request1 = new String(QAUtil.generateByteArray(321));
			String request2 = new String(QAUtil.generateByteArray(4321));
			String request3 = new String(QAUtil.generateByteArray(54321));
			
			bc.write(request0);
			bc.write(request1);
			bc.write(request2);
			bc.write(request3 + "\r\n");
			bc.flush();
			
			String response = null;
			try {
			    response = bc.readStringByDelimiter("\r\n");
			} catch (SocketTimeoutException ste) {
			    System.out.println("read timeout! available=" + bc.getDelegate().available());
			    throw ste;
			}
			
			Assert.assertEquals(request0 + request1 + request2 + request3, response);
		}
		
		server.close();
	}
	
	
	
	private static final class EchoHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}
	}
}
