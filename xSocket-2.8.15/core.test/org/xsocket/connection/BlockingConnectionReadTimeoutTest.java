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
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;


import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
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
public final class BlockingConnectionReadTimeoutTest {


	@Test
	public void testReadIntegerNoData() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setReadTimeoutMillis(1000);
		
		try {
			con.readInt();
			Assert.fail("SocketTimeoutException expected");
		} catch (SocketTimeoutException expected) {  }
		
		con.close();
		server.close();
	}


	@Test
	public void testReadStringByDelimiterNoData() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setReadTimeoutMillis(1000);
		
		try {
			con.readStringByDelimiter("\r\n");
			Assert.fail("SocketTimeoutException expected");
		} catch (SocketTimeoutException expected) {  }
		
		con.close();
		server.close();
	}
	
	@Ignore
	@Test
	public void testReadStringByDelimiterConnectionClosed() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setReadTimeoutMillis(1000);
		
		server.close();

		QAUtil.sleep(2000);
		
		try {
			con.readStringByDelimiter("\r\n");
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }
		
		con.close();
	}
	
	

	

	@Test
	public void testReadStringByDelimiter() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setReadTimeoutMillis(1000);
		
		con.write("test1234");
		
		try {
			con.readStringByDelimiter("\r\n");
			Assert.fail("SocketTimeoutException expected");
		} catch (SocketTimeoutException expected) {  }
		
		con.close();
		server.close();
	}
	
	

	@Test
	public void testReadStringByLength() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setReadTimeoutMillis(1000);
		
		con.write("test");
		
		try {
			con.readStringByLength(5);
			Assert.fail("SocketTimeoutException expected");
		} catch (SocketTimeoutException expected) {  }
		
		con.close();
		server.close();
	}
	
	@Test
	public void testReadInteger() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setReadTimeoutMillis(1000);
		
		con.write((byte) 45);
		
		try {
			con.readInt();
			Assert.fail("SocketTimeoutException expected");
		} catch (SocketTimeoutException expected) {  }
		
		con.close();
		server.close();
	}
	
	

	@Test
	public void testReadNoData() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setReadTimeoutMillis(1000);
		
		try {
			ByteBuffer buffer = ByteBuffer.allocate(10);
			con.read(buffer);
			Assert.fail("SocketTimeoutException expected");
		} catch (SocketTimeoutException expected) {  }
		
		con.close();
		server.close();
	}
	
	
	@Ignore
	@Test
	public void testReadConnectionClosed() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setReadTimeoutMillis(1000);
		
		server.close();
		QAUtil.sleep(2000);
		
		ByteBuffer buffer = ByteBuffer.allocate(10);
		int i = con.read(buffer);
		if (i != -1) {
			System.out.println("size -1 expected not " + i);
			Assert.fail("size -1 expected not " + i);
		}
				
		con.close();
	}
	
	
	
	private static final class EchoHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
	
			int available = connection.available();
			if (available > 0 ){
				connection.write(connection.readByteBufferByLength(available));		
			}
			
			return true;
		}
	}
	
}
