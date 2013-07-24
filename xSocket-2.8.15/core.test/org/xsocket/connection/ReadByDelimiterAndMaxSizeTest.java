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
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class ReadByDelimiterAndMaxSizeTest {

	private static final String DELIMITER = "x"; 
	private static final int MAX_READ_SIZE = 300;
	

	@Test 
	public void testBlocking1() throws Exception {
		IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.write("server, send me the data!");
		
		try {
			connection.readBytesByDelimiter(DELIMITER, MAX_READ_SIZE);
			Assert.fail("maxlimit exception expected");
		} catch (MaxReadSizeExceededException expected) { }
		
		connection.close();
		server.close();
	}


	@Test 
	public void testBlocking2() throws Exception {
		IServer server = new Server(new ServerHandler2());
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.write("server, send me the data!");
		
		try {
			connection.readStringByDelimiter(DELIMITER, MAX_READ_SIZE);
			Assert.fail("maxlimit exception expected");
		} catch (MaxReadSizeExceededException expected) { }
		
		connection.close();
		server.close();
	}
	
	
	@Test 
	public void testNonBlockingClientSide() throws Exception {
		IServer server = new Server(new ServerHandler2());
		ConnectionUtils.start(server);

		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.write("server, send me the data!");
		
		QAUtil.sleep(400);
		
		try {
			connection.readByteBufferByDelimiter(DELIMITER, MAX_READ_SIZE);
			Assert.fail("maxlimit exception expected");
		} catch (MaxReadSizeExceededException expected) { }
		
		connection.close();
		server.close();
	}

	
	@Test 
	public void testNonBlockingServerSide() throws Exception {
		ServerHandler3 hdl = new ServerHandler3();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		connection.write(QAUtil.generateByteArray(MAX_READ_SIZE + 10));
		connection.write(DELIMITER);
		connection.flush();
		
		QAUtil.sleep(250);
		Assert.assertTrue(hdl.bytes == null);
		
		connection.close();
		server.close();
	}
	
	
	
	
	private static class ServerHandler implements IDataHandler {
		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			
			connection.write(QAUtil.generateByteArray(MAX_READ_SIZE + 10));
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
	}
	
	
	private static class ServerHandler2 implements IDataHandler {
		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.write(QAUtil.generateByteArray(MAX_READ_SIZE + 10));
						
			return true;
		}
	}
	
	private static class ServerHandler3 implements IDataHandler {	
		private byte[] bytes = null;
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			try {
				bytes = connection.readBytesByDelimiter(DELIMITER, MAX_READ_SIZE);
			} catch (MaxReadSizeExceededException expected) { }
						
			return true;
		}
	}
	
}
