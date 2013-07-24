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
import java.io.OutputStream;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;



import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;




/**
*
* @author grro@xsocket.org
*/
public final class DisconnectTest {


	private AtomicInteger balance = new AtomicInteger(0);
	private int errors = 0;

	private AtomicInteger runningTreads = new AtomicInteger(0);
	
	
	@Before
	public void setUp() {
	    balance.set(0);
	    errors = 0;
	}
	
	
	@Test 
	public void testxSocket() throws Exception {
		TestHandler hdl = new TestHandler();
		final IServer server = new Server(hdl);
		server.start();


		for (int i = 0; i < 3; i++) {
			Thread t = new Thread() {
				public void run() {
					runningTreads.incrementAndGet();
					try {
						for (int j = 0; j < 2; j++) {
							IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
							con.write("test");
							con.close();
						}
					} catch (Exception e) {
						errors++;
					}
					
					runningTreads.decrementAndGet();
				};
			};
			t.start();
		}

		do {
			QAUtil.sleep(500);
		} while (runningTreads.get() > 0);

		Assert.assertTrue(" unbalanced connects disconnect ratio " + balance, balance.get() == 0);
		Assert.assertTrue("error occured", errors == 0);
		
		Assert.assertTrue("handler error occured", hdl.errors.isEmpty());
		
		server.close();
	}

	
	@Test 
	public void testNative() throws Exception {
		TestHandler hdl = new TestHandler();
		final IServer server = new Server(hdl);
		server.start();


		for (int i = 0; i < 3; i++) {
			Thread t = new Thread() {
				public void run() {
					runningTreads.incrementAndGet();
					try {
						for (int j = 0; j < 2; j++) {
							Socket socket = new Socket("localhost", server.getLocalPort());
							OutputStream os = socket.getOutputStream();
							os.write("test".getBytes());
							os.flush();
							os.close();
							socket.close();
						}
					} catch (Exception e) {
						errors++;
					}
					
					runningTreads.decrementAndGet();
				};
			};
			t.start();
		}

		do {
			QAUtil.sleep(500);
		} while (runningTreads.get() > 0);

		Assert.assertTrue(" unbalanced connects disconnect ratio " + balance, balance.get() == 0);
		Assert.assertTrue("error occured", errors == 0);
		
		Assert.assertTrue("handler error occured", hdl.errors.isEmpty());
		
		server.close();
	}




	private class TestHandler implements IDataHandler, IConnectHandler, IDisconnectHandler {

		private List<String> errors = new ArrayList<String>();
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);

			balance.incrementAndGet();
			return true;
		}

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			
			// shouldn't thrown an exception!
			try {
				connection.toString();
			} catch (NullPointerException npe) {
				errors.add("Nullpointerexception occured");
			}
		
			balance.decrementAndGet();
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.readByteBufferByLength(connection.available());
			return true;
		}
	}
}
