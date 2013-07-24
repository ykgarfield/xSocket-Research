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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
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
public final class NonBlockingConnectionClientHandlerTest {

	private static final String DELIMITER = "\r\n";


	@Test
	public void testSimple() throws Exception {
		IServer testServer = new Server(new ServerHandler());
		ConnectionUtils.start(testServer);

		IDataHandler clientHandler = new IDataHandler() {
			public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
				String response = connection.readStringByDelimiter("\r\n");
				return true;
			}
		};

		INonBlockingConnection connection = new NonBlockingConnection("localhost", testServer.getLocalPort(), clientHandler);
		connection.write("test" + DELIMITER);

		QAUtil.sleep(100);



		testServer.close();
	}




	@Test
	public void testConnectAndDisconnect() throws Exception {
		System.out.println("testConnectAndDisconnect");
		IServer testServer = new Server(new ServerHandler());
		ConnectionUtils.start(testServer);

		ClientDataHandler clientDataHandler = new ClientDataHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", testServer.getLocalPort(), clientDataHandler);
		connection.setAutoflush(true);
		QAUtil.sleep(200);

		Assert.assertTrue("connect event should have bee occured", clientDataHandler.countConnectedNotification == 1);
		Assert.assertTrue("disconnect event shouldn't have bee occured", clientDataHandler.countDisconnectedNotification == 0);


		do {
			QAUtil.sleep(100);
		} while(clientDataHandler.msg == null);
		clientDataHandler.msg = null;

		connection.write("helo echo" + DELIMITER);
		do {
			QAUtil.sleep(200);
		} while(clientDataHandler.msg == null);
		Assert.assertTrue(clientDataHandler.msg.equals("helo echo"));
		clientDataHandler.msg = null;


		connection.close();
		QAUtil.sleep(200);

		Assert.assertTrue(clientDataHandler.countDisconnectedNotification == 1);
		Assert.assertTrue(clientDataHandler.countConnectedNotification == 1);


		testServer.close();
	}


	@Test
	public void testServerInitiatedClose() throws Exception {
		System.out.println("testServerInitiatedCloset");
		IServer testServer = new Server(new ServerHandler());
		testServer.start();

		ClientDataHandler clientDataHandler = new ClientDataHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", testServer.getLocalPort(), clientDataHandler);
		connection.setAutoflush(true);
		QAUtil.sleep(2000);

		Assert.assertTrue("connect event should have bee occured", clientDataHandler.countConnectedNotification == 1);
		Assert.assertTrue("disconnect event shouldn't have bee occured", clientDataHandler.countDisconnectedNotification == 0);

		connection.write("CLOSE" + DELIMITER);
		QAUtil.sleep(2000);


		Assert.assertTrue(clientDataHandler.countDisconnectedNotification == 1);
		Assert.assertTrue(clientDataHandler.countConnectedNotification == 1);

		connection.close();
		QAUtil.sleep(2000);
		Assert.assertTrue(clientDataHandler.countDisconnectedNotification == 1);
		Assert.assertTrue(clientDataHandler.countConnectedNotification == 1);

		testServer.close();
	}



	@Test
	public void testIdleTimeout() throws Exception {
		System.out.println("testIdleTimeout");
		IServer testServer = new Server(new ServerHandler());
		ConnectionUtils.start(testServer);

		ClientDataHandler clientDataHandler = new ClientDataHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", testServer.getLocalPort(), clientDataHandler);
		connection.setAutoflush(true);
		connection.setIdleTimeoutMillis(1 * 1000);
		QAUtil.sleep(3000);


		Assert.assertEquals(1, clientDataHandler.countIdleTimeoutNotification);
		Assert.assertEquals(0, clientDataHandler.countConnectionTimeoutNotification);
		Assert.assertEquals(1, clientDataHandler.countDisconnectedNotification);
		Assert.assertTrue(!connection.isOpen());

		testServer.close();
	}


	@Test
	public void testConnectionTimeout() throws Exception {
		System.out.println("testConnectionTimeout");
		IServer testServer = new Server(new ServerHandler());
		ConnectionUtils.start(testServer);

		ClientDataHandler clientDataHandler = new ClientDataHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", testServer.getLocalPort(), clientDataHandler);
		connection.setAutoflush(true);
		connection.setConnectionTimeoutMillis(1 * 1000);
		QAUtil.sleep(2000);

		Assert.assertEquals(0, clientDataHandler.countIdleTimeoutNotification);
		Assert.assertEquals(1, clientDataHandler.countConnectionTimeoutNotification);
		Assert.assertEquals(1, clientDataHandler.countDisconnectedNotification);
		Assert.assertTrue(!connection.isOpen());


		testServer.close();
	}



	@Test
	public void testCustomWorkerPool() throws Exception {
		System.out.println("testCustomWorkerPool");
		IServer testServer = new Server(new ServerHandler());
		ConnectionUtils.start(testServer);

		ClientDataHandler clientDataHandler = new ClientDataHandler();
		WorkerPool workerPool = new WorkerPool();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", testServer.getLocalPort(), clientDataHandler, workerPool);
		connection.setAutoflush(true);
		connection.write("test");

		QAUtil.sleep(200);
		Assert.assertTrue(workerPool.executed > 0);

		connection.close();
		testServer.close();
	}


	private static final class WorkerPool implements Executor {

		private int executed = 0;

		public void execute(Runnable command) {
			executed++;
			Thread t = new Thread(command);
			t.start();

		}

		public <T> List<Future<T>> invokeAll(Collection<Callable<T>> tasks) throws InterruptedException {
			return null;
		}

		public int getActiveCount() {
			return 0;
		}

		public int getMaximumPoolSize() {
			return 0;
		}

		public int getMinimumPoolSize() {
			return 0;
		}

		public int getPoolSize() {
			return 0;
		}

		public int getLoad() {
			return 0;
		}

		public boolean isOpen() {
			return true;
		}

		public void close() {
		}
	}



	private static final class ClientDataHandler implements IDataHandler, IConnectHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {

		private int countConnectedNotification = 0;
		private int countDisconnectedNotification = 0;
		private int countIdleTimeoutNotification = 0;
		private int countConnectionTimeoutNotification = 0;
		private String msg = null;

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			msg = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			return true;
		}

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			countConnectedNotification++;
			return true;
		}

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			countDisconnectedNotification++;
			return true;
		}

		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			countConnectionTimeoutNotification++;
			return false;
		}

		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			countIdleTimeoutNotification++;
			return false;
		}
	}


	private static final class ServerHandler implements IDataHandler, IConnectHandler {


		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write("hello" + DELIMITER);
			return true;
		}


		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);

			if (word.equals("CLOSE")) {
				connection.close();
			} else {
				connection.write(word + DELIMITER);
			}

			return true;
		}
	}
}
