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
import java.util.Random;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.IDataSink;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
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
public final class SimultaneousReadWriteTest {

	private static final byte[] RECORD = QAUtil.generateByteArray(4000);
	private static final int LOOPS = 100;

	@Test 
	public void testNonblocking() throws Exception {

		/*
		 * onConnect event handling method starts the writer thread (this is true for the client handler as well as for the server handler)
		 * onData event handling method receives the records and count it 
		 */
		
		Handler serverHandler = new Handler("s"); 
		IServer server = new Server(serverHandler);
		ConnectionUtils.start(server);

		Handler clientHandler = new Handler("c"); 
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), clientHandler);
		connection.setAutoflush(false);
		
		do {
			QAUtil.sleep(100);
		} while ((serverHandler.received < LOOPS) & (clientHandler.received < LOOPS));
		
		
		Assert.assertFalse(clientHandler.errorOccured);
		Assert.assertFalse(clientHandler.writer.exceptionOccured);
		Assert.assertFalse(serverHandler.errorOccured);
		
		connection.close();
		server.close();
	}


	@Test 
	public void testBlocking() throws Exception {
		
		Handler serverHandler = new Handler("s"); 
		IServer server = new Server(serverHandler);
		ConnectionUtils.start(server);

		int received = 0;
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		WriteProcessor writeProcessor = new WriteProcessor(connection);
		new Thread(writeProcessor).start();
		
		do {
			byte[] response = connection.readBytesByLength(RECORD.length);
			Assert.assertTrue(QAUtil.isEquals(response, RECORD));
			System.out.print("c");
			received++;
		} while (received < LOOPS);
		
		do {
			QAUtil.sleep(100);
		} while ((serverHandler.received < LOOPS));
		
		
		Assert.assertFalse(serverHandler.errorOccured);
		
		connection.close();
		server.close();
	}

	
	private static class Handler implements IDataHandler, IConnectHandler {
		private WriteProcessor writer = null;
		
		private String progressMsg = null;
		private boolean errorOccured = false;
		private int received = 0;
		
		Handler(String progressMsg) {
			this.progressMsg = progressMsg;
		}
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(true);
			
			writer = new WriteProcessor(connection);
			Thread t = new Thread(writer);
			t.start();
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			byte[] request = connection.readBytesByLength(RECORD.length);
			if (!QAUtil.isEquals(request, RECORD)) {
				errorOccured = true;
			}
			System.out.print(progressMsg);
			received++;
			return true;
		}

		
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
	
	
	
	private static final class WriteProcessor implements Runnable {
		
		private Random random = new Random();
		private boolean exceptionOccured = false;
		private int sent = 0;
		private IDataSink connection = null;
		
		WriteProcessor(IDataSink connection) {
			this.connection = connection;
		}
		
		public void run() {
			for (int i = 0; i < LOOPS; i++) {
				try {
					connection.write(RECORD);
					sent++;
					
					randomWait();
					
				} catch (Exception e) {
					exceptionOccured = true;
				}
			}
		}
		
		private void randomWait() {
			int i = Math.abs(random.nextInt());
			i = i % 15;
			
			try {
				Thread.sleep(i);
			} catch (InterruptedException ignore) { }
		}
	}	
}
