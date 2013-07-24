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
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;



/**
*
* @author grro@xsocket.org
*/
public final class NativeClientCloseTest {


	private static final String DELIMITER = "\r\n";

	private static final String QUIT_COMMAND = "QUIT";
	private static final String OK_RESPONSE = "OK";
	private static final String CLOSED_RESPONSE = "CLOSED";


	private AtomicInteger running = new AtomicInteger(0);
	private List<String> errors = new ArrayList<String>();

	
	public static void main(String[] args) throws Exception {
		
		System.setProperty("org.xsocket.connection.sendFlushTimeoutMillis", "2000");
		
		for (int i = 0; i < 1000000; i++) {
			new NativeClientCloseTest().testBulk();
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
		
		IServer server = new Server(new ServerHandler());
		server.setIdleTimeoutMillis(2 * 60 * 1000);
		server.start();

		call("localhost", server.getLocalPort());

		server.close();
	}

	
	@Test
	public void testBulk() throws Exception {
		
		System.out.println("testBulk");

		final IServer server = new Server(new ServerHandler());
		server.setIdleTimeoutMillis(2 * 60 * 1000);
		server.start();

		call("localhost", server.getLocalPort());
		
		for (int i = 0; i < 3; i++) {
			Thread t = new Thread(new Runnable() {
				public void run() {
					running.incrementAndGet();
					for (int j = 0; j < 100; j++) {
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
		
		Socket s = new Socket(srvAddress, srvPort);
		LineNumberReader lnr = new LineNumberReader(new InputStreamReader(s.getInputStream()));
		OutputStream os = s.getOutputStream();
		
		
		os.write(new String("Hello" + DELIMITER).getBytes());
		os.flush();
		Assert.assertEquals(OK_RESPONSE, lnr.readLine());
		
		
		os.write(new String("some command" + DELIMITER).getBytes());
		Assert.assertEquals(OK_RESPONSE, lnr.readLine());
		
		os.write(new String(QUIT_COMMAND + DELIMITER).getBytes());
		Assert.assertEquals(CLOSED_RESPONSE, lnr.readLine());
		
		lnr.close();
		os.close();
		s.close();
	}



	private static class ServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException {
			String request = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);

			try {
				if (request.equals(QUIT_COMMAND)) {
					connection.write(CLOSED_RESPONSE + DELIMITER);
					connection.close();
	
				} else {
					connection.write(OK_RESPONSE + DELIMITER);
				}
			} catch (SocketTimeoutException ste) {
				System.out.println("write timeout reached");
				ste.printStackTrace();
			}
			return true;
		}
	}
}
