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
import org.xsocket.Execution;
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
public final class TimeoutTest {

	private static boolean connectionTimeoutOccured = false;
	private static boolean idleTimeoutOccured = false;

	

	@Test 
	public void testIdleTimeout() throws Exception {
		
		IServer server = new Server(new TimeoutTestServerHandler());
		ConnectionUtils.start(server);

		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		long idleTimeout = 1 * 1000;
		System.out.println("set idle timeout " + idleTimeout + " sec");
		server.setIdleTimeoutMillis(idleTimeout);
		Assert.assertTrue(server.getIdleTimeoutMillis() == idleTimeout);

		server.setConnectionTimeoutMillis(15 * 1000);
		Assert.assertTrue(server.getConnectionTimeoutMillis() == (15 * 1000));
		

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write((int) 4);

		int sleepTime = 2000;
		QAUtil.sleep(sleepTime);
		
		con.close();

		Assert.assertTrue("idle timeout should have been occured (idle timeout=" + idleTimeout + ", elapsed=" + sleepTime, idleTimeoutOccured);
		Assert.assertFalse("connection timeout should have been occured", connectionTimeoutOccured);
		
		server.close();
	}

	
	@Test 
	public void testRepeatedIdleTimeout() throws Exception {
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);

		
		RepeatedTimeoutTestServerHandler hdl = new RepeatedTimeoutTestServerHandler();
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);

		System.out.println("set idle timeout 1 sec");
		con.setIdleTimeoutMillis(1 * 1000);
		
		QAUtil.sleep(3000);
		Assert.assertEquals(1, hdl.countIdleTimeoutOccured);
		
		System.out.println("set idle timeout 1 sec");
		con.setIdleTimeoutMillis(1 * 1000);

		QAUtil.sleep(200);
		Assert.assertEquals(1, hdl.countIdleTimeoutOccured);
		
		
		QAUtil.sleep(3000);
		Assert.assertEquals(2, hdl.countIdleTimeoutOccured);
	}
	
	
	
	@Test 
	public void testRepeatedConnectionTimeout() throws Exception {
		IServer server = new Server(new EchoHandler());
		server.start();

		
		RepeatedTimeoutTestServerHandler hdl = new RepeatedTimeoutTestServerHandler();
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);

		System.out.println("set connection timeout 1 sec");
		con.setConnectionTimeoutMillis(1 * 1000);
		
		QAUtil.sleep(3000);
		Assert.assertEquals(1, hdl.countConnectionTimeoutOccured);
		
		System.out.println("set connecetion timeout 1 sec");
		con.setConnectionTimeoutMillis(1 * 1000);

		QAUtil.sleep(200);
		Assert.assertEquals(1, hdl.countConnectionTimeoutOccured);
		
		
		QAUtil.sleep(3000);
		Assert.assertEquals(2, hdl.countConnectionTimeoutOccured);
	}

	
	



	@Test 
	public void testConnectionTimeout() throws Exception {
		IServer server = new Server(new TimeoutTestServerHandler());
		server.start();
 
		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		server.setIdleTimeoutMillis(15 * 1000);
		server.setConnectionTimeoutMillis(1000);

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write((int) 4);

		QAUtil.sleep(3000);

		con.close();

		Assert.assertFalse(idleTimeoutOccured);
		server.close();
	}

	
	@Test 
	public void testIdleTimeoutWithoutSending() throws Exception {
		IServer server = new Server(new TimeoutTestServerHandler());
		ConnectionUtils.start(server);

		connectionTimeoutOccured = false;
		idleTimeoutOccured = false;

		int idleTimeout = 1;
		System.out.println("set idle timeout " + idleTimeout + " sec");
		server.setIdleTimeoutMillis(idleTimeout * 1000);
		server.setConnectionTimeoutMillis(20 * 1000);

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

		int sleepTime = 2000;
		QAUtil.sleep(sleepTime);
		
		con.close();

		Assert.assertTrue("idle timeout should have been occured (idle timeout=" + idleTimeout + ", elapsed=" + sleepTime, idleTimeoutOccured);
		Assert.assertFalse(connectionTimeoutOccured);
		server.close();
	}

	
	
	@Test 
	public void testIdleTimeoutDefault() throws Exception {
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);

		int idleTimeout = 1;
		System.out.println("set idle timeout " + idleTimeout + " sec");
		server.setIdleTimeoutMillis(1 * 1000);
		
		ClientHandler clientHdl = new ClientHandler();
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), clientHdl);
		con.write("rt");
		
		QAUtil.sleep(2000);
		
		Assert.assertTrue(clientHdl.isDisconnected);
		Assert.assertFalse(con.isOpen());
		server.close();
	}


	
	
	@Test 
	public void testIdleTimeoutDefault2() throws Exception {
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);

		System.out.println("set connection timeout 1 sec");
		server.setConnectionTimeoutMillis(1 * 1000);
		
		ClientHandler clientHdl = new ClientHandler();
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), clientHdl);
		con.write("rt");
		
		QAUtil.sleep(2000);

		Assert.assertTrue(clientHdl.isDisconnected);
		Assert.assertFalse(con.isOpen());
		server.close();
	}

	
	

	private static final class TimeoutTestServerHandler implements IIdleTimeoutHandler, IConnectionTimeoutHandler {


		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("con time out");
			connectionTimeoutOccured = true;
			connection.close();
			return true;
		}


		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("idle time out");
			idleTimeoutOccured = true;
			connection.close();
			return true;
		}
	}
	
	
	

	@Execution(Execution.NONTHREADED)
	private static final class RepeatedTimeoutTestServerHandler implements IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		private int countConnectionTimeoutOccured = 0;
		private int countIdleTimeoutOccured = 0;
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("con time out");
			
			countConnectionTimeoutOccured++;
			return true;
		}


		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("idle time out");

			countIdleTimeoutOccured++;
			return true;
		}
	}
	

	private static final class ClientHandler implements IDisconnectHandler {
		
		boolean isDisconnected = false;
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			
			connection.close();
			return true;
		}
	}
	
	
	private static final class EchoHandler implements IDataHandler {
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}
	}
}
