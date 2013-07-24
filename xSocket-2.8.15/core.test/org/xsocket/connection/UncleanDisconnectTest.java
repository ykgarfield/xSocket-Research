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
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;



import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class UncleanDisconnectTest {
	
	
	@Test 
	public void testBlockingClientServerCloseWrite() throws Exception {
		
		TestHandler hdl = new TestHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		connection.write("test");
		QAUtil.sleep(500);

		server.close();
		QAUtil.sleep(1000);
		
		Assert.assertTrue(connection.isOpen());
		
		try {
			connection.write("rt");
			String msg = "testBlockingClientServerClose: an ClosedConnectionException should have been thrown";
			System.out.println(msg);
			Assert.fail(msg);
		} catch (ClosedChannelException expected) { }
		
		Assert.assertFalse(connection.isOpen());	
	}

	
	@Test 
	public void testBlockingClientServerCloseRead() throws Exception {
		
		TestHandler hdl = new TestHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		connection.write("test");
		QAUtil.sleep(500);
		
		server.close();
		QAUtil.sleep(500);
		
		connection.readStringByLength(2);
		Assert.assertTrue(connection.isOpen());
		
		try {
			connection.readStringByLength(3);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }		
		
		Assert.assertFalse(connection.isOpen());
	}

	

	@Test 
	public void testNonBlockingClientServerCloseRead() throws Exception {
		TestHandler hdl = new TestHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);
		
		ClientHandler cHdl = new ClientHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), cHdl);
		connection.setAutoflush(true);
		
		connection.write("test");
		QAUtil.sleep(500);

		server.close();		
		QAUtil.sleep(500);
		
		if (!cHdl.isDisconnected) {
			String msg = "testNonBlockingClientServerClose: hdl is not disconnected";
			System.out.println(msg);
			Assert.fail(msg);
		}
		
		try {
			connection.write("rt");
			String msg = "testNonBlockingClientServerClose: an ClosedConnectionException should have been thrown";
			System.out.println(msg);
			Assert.fail(msg);
		} catch (ClosedChannelException expected) { }		
	}
	
	
	@Test 
	public void testNonBlockingClientServerCloseWrite() throws Exception {
		TestHandler hdl = new TestHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);
		
		ClientHandler cHdl = new ClientHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), cHdl);
		connection.setAutoflush(true);
		
		connection.write("test");
		QAUtil.sleep(500);

		server.close();		
		QAUtil.sleep(500);
		
		String s = connection.readStringByLength(2);
		Assert.assertTrue(connection.isOpen());
		
		
		if (!cHdl.isDisconnected) {
			String msg = "testNonBlockingClientServerClose: hdl is not disconnected";
			System.out.println(msg);
			Assert.fail(msg);
		}
		
		try {
			connection.readStringByLength(3);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }		
		
		Assert.assertFalse(connection.isOpen());
	}
	

	@Ignore
	@Test 
	public void testClientCloseWrite() throws Exception {
		TestHandler hdl = new TestHandler();
		IServer server = new Server(hdl);
		server.start();
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);

		connection.write("test");
		connection.close();

		QAUtil.sleep(4000);
		
		if (!hdl.isDisconnected()) {
			String msg = "error client close didn't detected";
			System.out.println(msg);
			Assert.fail(msg);
		}
		
		
		server.close();
	}
	
	
	@Ignore
	@Test 
	public void testClientCloseRead() throws Exception {
		TestHandler hdl = new TestHandler();
		IServer server = new Server(hdl);
		server.start();
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);

		connection.write("test");
		connection.close();

		QAUtil.sleep(4000);
		
		if (!hdl.isDisconnected()) {
			String msg = "error client close didn't detected";
			System.out.println(msg);
			Assert.fail(msg);
		}
		
		Assert.assertFalse(connection.isOpen());
		
		server.close();
	}
	
	
	private static final class ClientHandler implements IDisconnectHandler {
		private boolean isDisconnected = false;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			return true;
		}
	}
	
	
	

	private class TestHandler implements IDataHandler, IDisconnectHandler {
		
		private AtomicBoolean isDisconnected = new AtomicBoolean(false);
		private String error = null;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
			connection.write(data);
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected.set(true);
			
			try {
				connection.write("shouldn't work");
				error = "ClosedConnectionException should have been thrown";
			} catch (ClosedChannelException expected) { }
			return true;
		}
		
		
		boolean isDisconnected() {
			return isDisconnected.get();
		}
	}
}
