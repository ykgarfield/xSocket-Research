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

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ServerSocketFactory;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class SSLxSocketClientTest {

	private static final String DELIMITER = System.getProperty("line.separator");



	public static void main(String[] args) throws Exception {
		
		System.out.println("test non blocking");
		new SSLxSocketClientTest().testNonBlocking();
		new SSLxSocketClientTest().testDelayedNonBlocking();
		new SSLxSocketClientTest().testBlocking();
	}
	
	

	@Test
	public void testBlocking() throws Exception {
	    System.out.println("testBlocking");
	    
		Server server = new Server(0);
		server.start();

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort(), SSLTestContextFactory.getSSLContext(), true);

		connection.write("test"+ DELIMITER);

		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		connection.close();

		Assert.assertEquals("test", response);

		server.shutdown();
	}



	@Test
	public void testNonBlocking() throws Exception {
	    System.out.println("testNonBlocking");
			
		Server server = new Server(0);
		server.start();

		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), SSLTestContextFactory.getSSLContext(), true);
		connection.setAutoflush(true);

		System.out.println("[" + Thread.currentThread().getId() + "] writing test");
		connection.write("test" + DELIMITER);

		String response = receive(connection, DELIMITER);
		connection.close();

		Assert.assertEquals("test", response);

		server.shutdown();
	}


	@Test
	public void testDelayedNonBlocking() throws Exception {
	    System.out.println("testDelayedNonBlocking");
	    
		Server server = new Server(0);
		server.start();

		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), SSLTestContextFactory.getSSLContext(), true);
		connection.setAutoflush(true);
		connection.setFlushmode(FlushMode.ASYNC);

		connection.write("test" + DELIMITER);

		String response = receive(connection, DELIMITER);
		Assert.assertEquals("test", response);

		long start = System.currentTimeMillis();
		connection.setWriteTransferRate(7);
		connection.write("testi" + DELIMITER);
		response = receive(connection, DELIMITER);
		QAUtil.assertTimeout(System.currentTimeMillis() - start, 1000, 500, 2000);   // 7 Bytes / 7 bytesSec -> 1 sec

		Assert.assertEquals("testi", response);

		connection.close();
		server.shutdown();
	}


	private String receive(INonBlockingConnection connection, String delimiter) throws IOException {
		String response = null;
		do {
			try {
				response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			} catch (BufferUnderflowException bue) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) { }
			}
		} while (response == null);

		return response;
	}


	private static final class Server {

		private ServerSocket ssocket = null;
		private volatile boolean isRunning = true;

		private Thread server = null;

		private List<Worker> activeWorkers = new ArrayList<Worker>();

        private Server(int port) throws Exception {
    		ServerSocketFactory socketFactory = SSLTestContextFactory.getSSLContext().getServerSocketFactory();
            ssocket = socketFactory.createServerSocket(port);
        }


        int getLocalPort() {
        	return ssocket.getLocalPort();
        }

        void start() {
        	server = new Thread("server") {
        		@Override
        		public void run() {
        			try {
	        			while (isRunning) {
	        				Socket socket = ssocket.accept();
	        				if (socket != null) {
	        					Worker worker = new Worker(socket);
	        					worker.start();
	        					activeWorkers.add(worker);
	        				}
	        			}
        			} catch (Exception ignore) { }
        		}
        	};

        	server.start();
        }

        void shutdown() {
        	isRunning = false;

        	for (Worker worker : activeWorkers) {
				worker.shutdown();
			}

        	try {
        		ssocket.close();
        	} catch (Exception ignore) { }
        }
	}


	private static final class Worker extends Thread {

		private Socket socket = null;
		private LineNumberReader in = null;
        private PrintWriter out = null;

        private volatile boolean isRunning = true;


		Worker(Socket socket) throws IOException {
			super("worker");
			setDaemon(true);

			this.socket = socket;
	        in = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
	        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
		}

		@Override
		public void run() {

			while(isRunning) {
				try {
					String request = in.readLine();
					if (request != null) {
						out.println(request);
						out.flush();
					}
				} catch (IOException ioException) { }
			} while (isRunning);
			
			try {
			    socket.close();
			} catch (Exception ignore) { }
		}


		   void shutdown()  {
	        	isRunning = false;
        		try {
    				in.close();
    			} catch (Exception ignore) { }
		   }
	}
}
