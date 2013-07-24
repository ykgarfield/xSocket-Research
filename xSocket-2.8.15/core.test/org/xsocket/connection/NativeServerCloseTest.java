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
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class NativeServerCloseTest {


	private static final String DELIMITER = "\r\n";

	private static final String QUIT_COMMAND = "QUIT";
	private static final String OK_RESPONSE = "OK";
	private static final String CLOSED_RESPONSE = "CLOSED";


	private AtomicInteger running = new AtomicInteger(0);
	private List<String> errors = new ArrayList<String>();

	
	public static void main(String[] args) throws Exception {
		
		System.setProperty("org.xsocket.connection.sendFlushTimeoutMillis", "2000");
		
		for (int i = 0; i < 1000000; i++) {
			new NativeServerCloseTest().testBulk();
		}
	}
	
	
	@Before
	public void setup() {
		running.set(0);
		errors.clear();
	}


	@Test
	public void testSimple() throws Exception {
		System.out.println("testSimple");
		
		ClassicServer server = new ClassicServer();
		new Thread(server).start();
		QAUtil.sleep(1000);

		call("localhost", server.getLocalPort());

		server.close();
	}

	

	

	@Test
	public void testBulk() throws Exception {
		
		System.out.println("testBulk");

		final ClassicServer server = new ClassicServer();
		new Thread(server).start();
		QAUtil.sleep(1000);

		

		call("localhost", server.getLocalPort());
		
		for (int i = 0; i < 3; i++) {
			Thread t = new Thread(new Runnable() {
				public void run() {
					running.incrementAndGet();
					for (int j = 0; j < 1000; j++) {
						try {
							call("localhost", server.getLocalPort());
						} catch (IOException ioe) {
							ioe.printStackTrace();
							errors.add(ioe.toString());
						}
					}
					running.decrementAndGet();
				}
			});

			t.start();
		}

		do {
			try {
				Thread.sleep(200);
			} catch (InterruptedException igonre) {	}
		} while (running.get() > 0);

		
		for (String error : errors) {
			System.out.println(error);
		}

		Assert.assertTrue(errors.isEmpty());

		server.close();
	}

	private void call(String srvAddress, int srvPort) throws IOException {
	
		IBlockingConnection connection = new BlockingConnection(srvAddress, srvPort);
		connection.setAutoflush(true);
		connection.setReadTimeoutMillis(30 * 1000);

		connection.write("hello" + DELIMITER);
		Assert.assertTrue(connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE).equals(OK_RESPONSE));

		connection.write("some command" + DELIMITER);
		Assert.assertTrue(connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE).equals(OK_RESPONSE));


		connection.write(QUIT_COMMAND + DELIMITER);
		try {
			Assert.assertTrue(connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE).equals(CLOSED_RESPONSE));
		} catch (SocketTimeoutException stoe) {
			stoe.printStackTrace();
			throw stoe;
		}

		try {
			connection.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			// ioe should have been thrown, because the connection is closed by the server
		}
	}



	private static class ClassicServer implements Runnable {
		
		private volatile boolean isRunning = true;
		private final ExecutorService executor = Executors.newCachedThreadPool();
		private final ServerSocket ss;
	
		private final int port;
		
		public ClassicServer() throws IOException {
			ss = new ServerSocket(0);
			port = ss.getLocalPort();
		}

		int getLocalPort() {
			return port;
		}
		
		public void run() {
			
			try {
				while (isRunning) {
					Socket s = ss.accept();
					executor.execute(new Worker(s));
				}
			} catch (IOException ignore) { }
		}
		
		
		public void close() throws IOException {
			isRunning = false;
			executor.shutdown();
			ss.close();
		}
	}
	
	
	private static final class Worker implements Runnable {
		
		private final Socket s;
		private final OutputStream os;
		private final LineNumberReader lnr;
		
		public Worker(Socket s) throws IOException {
			this.s = s;
			os = s.getOutputStream();
			lnr = new LineNumberReader(new InputStreamReader(s.getInputStream()));
		}
		
		public void run() {
			
			try {
				
				while (true) {
					String request = lnr.readLine();
	
					if (request.equals(QUIT_COMMAND)) {
						os.write(new String(CLOSED_RESPONSE + DELIMITER).getBytes());
						os.close();
						lnr.close();
						s.close();
						return;
		
					} else {
						os.write(new String(OK_RESPONSE + DELIMITER).getBytes());
					}
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
			
		}
	}
	
	

}
