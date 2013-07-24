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
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


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
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnectionClientTest {

	private static final String DELIMITER = "\n";
	
	private List<String> errors = new ArrayList<String>();
	private AtomicInteger running = new AtomicInteger(0); 




	@Test
	public void testSimple() throws Exception {
		IServer testServer = new Server(new ServerHandler());
		ConnectionUtils.start(testServer);

		SendTask sendTask = new SendTask("localhost", testServer.getLocalPort(), "testi@xsocket.de");
		do {
			sendTask.perform();

			try {
				Thread.sleep(10);
			} catch (InterruptedException ignore) { };

		} while (!sendTask.isDone());

		testServer.close();
	}
	
	
	@Test
	public void testBulk() throws Exception {
		
		final IServer server = new Server(new Responder());
		ConnectionUtils.start(server);

		
		for (int i = 0; i < 3; i++) {
			Thread t = new Thread() {
				
				public void run() {
					running.incrementAndGet();
					try {
						IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
						
						for (int i = 1; i < 9000; i++) {
						
							byte[] request = QAUtil.generateByteArray(i);
							con.write(request);
							
							byte[] response = con.readBytesByLength(i);
							
							if (!QAUtil.isEquals(request, request)) {
								errors.add("request " + new String(request) + " is not equals to response " + new String(response));
							}
						}
						
						con.close();
						
					} catch (Exception e) {
						errors.add(e.toString());
					}
					
					running.decrementAndGet();
				}
			};
			
			t.start();
		}

		do {
			QAUtil.sleep(100);
		} while (running.get() > 0);

		server.close();
	}
	
	

	@Test
	public void testOnConnectedCalled() throws Exception {
		IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server);

		ClientHandler hdl = new ClientHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);
	
		QAUtil.sleep(1000);
		Assert.assertTrue(hdl.countOnConnectCalled == 1);

		connection.close();
		
		QAUtil.sleep(1000);
		Assert.assertTrue(hdl.countOnConnectCalled == 1);
		Assert.assertTrue(hdl.countOnDisconnectCalled == 1);

		server.close();

		QAUtil.sleep(1000);
		Assert.assertTrue(hdl.countOnConnectCalled == 1);
		Assert.assertTrue(hdl.countOnDisconnectCalled == 1);
	}



	@Test
	public void testDelayedWrite() throws Exception {
		IServer testServer = new Server(new ServerHandler());
		ConnectionUtils.start(testServer);

		INonBlockingConnection connection = new NonBlockingConnection("localhost", testServer.getLocalPort());
		connection.setAutoflush(true);
		connection.setFlushmode(FlushMode.ASYNC);

		receive(connection, DELIMITER);



		// call without delay
		byte[] data = QAUtil.generateByteArray(1000);

		long start = System.currentTimeMillis();
		connection.write(data);
		connection.write(DELIMITER);

		byte[] response = receive(connection, DELIMITER);
		long elapsed = System.currentTimeMillis() - start;

		Assert.assertTrue(isEquals(data, response));
		Assert.assertTrue(inTimeRange(elapsed, 0, 0, 1500));



		// call with 2500/sec delay
		data = QAUtil.generateByteArray(5000);
		connection.setWriteTransferRate(1500);

		start = System.currentTimeMillis();
		connection.write(data);
		connection.write(DELIMITER);

		response = receive(connection, DELIMITER);
		elapsed = System.currentTimeMillis() - start;

		Assert.assertTrue(isEquals(data, response));
		Assert.assertTrue(inTimeRange(elapsed, 3333, 2000, 6000));  // 5000 byte / 1500 bytesPerSec -> 3,3 sec


		connection.close();
		testServer.close();
	}


	private boolean isEquals(byte[] array1, byte[] array2) {
		if (array1.length != array2.length) {
			return false;
		}

		for (int i = 0; i < array1.length; i++) {
			if (array1[i] !=array2[i]) {
				return false;
			}
		}

		return true;
	}

	private byte[] receive(INonBlockingConnection connection, String delimiter) throws ClosedChannelException, IOException {
		byte[] response = null;
		do {
			try {
				response = connection.readBytesByDelimiter(delimiter);
			} catch (BufferUnderflowException bue) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ignore) { }
			}
		} while (response == null);

		return response;
	}


	private boolean inTimeRange(long time, long expected, long min, long max) {
		System.out.println("elapsed=" + time + " (expected=" + expected + ", min=" + min + ", max=" + max + ")");
		return ((time >= min) && (time <= max));
	}



	private static final class SendTask {
		private static final int PRE_CONNECT = 0;
		private static final int WAIT_FOR_GREETING = 1;
		private static final int WAIT_FOR_SENDER = 2;
		private static final int WAIT_FOR_RECEIVER = 3;
		private static final int WAIT_FOR_DATA = 4;
		private static final int WAIT_FOR_MAIL = 5;
		private static final int WAIT_FOR_QUIT = 6;
		private static final int DONE = 9;

		private int state = PRE_CONNECT;

		private String address = null;
		private int port = 0;
		private String sender = null;

		private INonBlockingConnection connection = null;

		public SendTask(String address, int port, String sender) {
			this.address = address;
			this.port = port;
			this.sender = sender;
		}


		public void perform() {

			try {
				switch (state) {
					case PRE_CONNECT:
						connection = new NonBlockingConnection(address, port);
						connection.setAutoflush(false);
						state = WAIT_FOR_GREETING;
					break;

					case WAIT_FOR_GREETING:
						String greeting = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
						if (!greeting.equals("hello")) {
							throw new IOException("Wrong greeting " + greeting);
						}

						connection.write("HELO response" + DELIMITER);
						connection.flush();
						state = WAIT_FOR_SENDER;
					break;

					case WAIT_FOR_SENDER:
						String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
						if (!response.equals("HELO response")) {
							throw new IOException("Wrong response " + response);
						}

						connection.write("QUIT" + DELIMITER);
						connection.flush();
						connection.close();
						state = DONE;
					break;

					case DONE:

					break;


				default:
					break;
				}


			} catch (BufferUnderflowException ignore) {
				// not enough data available -> wait for data

			} catch (Exception e) {
				if (connection != null) {
					try {
						connection.close();
					} catch (Exception ignore) { }
				}
			}
		}

		public boolean isDone() {
			return (state == DONE);
		}
	}



	private static final class ServerHandler implements IDataHandler, IConnectHandler {


		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.write("hello" + DELIMITER);

			connection.flush();
			return true;
		}


		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);

			connection.flush();
			return true;
		}
	}



	private static final class ClientHandler implements IConnectHandler, IDataHandler, IDisconnectHandler {

		String response = null;
		int countOnConnectCalled = 0;
		int countOnDisconnectCalled = 0;

		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			countOnConnectCalled++;
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			countOnDisconnectCalled++;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (response == null) {
				response = connection.readStringByDelimiter("\r\n\r\n");
			}
			return true;
		}
	}
	
	
	private static final class Responder implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}
	}
}
