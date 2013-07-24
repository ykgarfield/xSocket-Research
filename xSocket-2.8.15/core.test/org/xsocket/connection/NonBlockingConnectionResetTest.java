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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
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
public final class NonBlockingConnectionResetTest  {
	

	public static void main(String[] args) throws Exception {
		for (int i = 0; i < 1000; i++) {
			new NonBlockingConnectionResetTest().testIdleTimeout();
		}
	}
	
	
	@Test 
	public void testIdleTimeout() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		
		Handler hdl = new Handler();
		NonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);
		
		con.setIdleTimeoutMillis(2 * 1000);
		
		con.write("test" + EchoHandler.DELIMITER);
		QAUtil.sleep(1000);
		
		Assert.assertEquals("test", hdl.getData());
		Assert.assertFalse(hdl.isConnectionTimeoutOccurred());
		Assert.assertFalse(hdl.isIdleTimeoutOccurred());
		hdl.setData(null);
		
		con.reset();
		
		con.setHandler(hdl);
		
		con.write("test2" + EchoHandler.DELIMITER);
		QAUtil.sleep(1000);
		
		Assert.assertEquals("test2", hdl.getData());
		Assert.assertFalse(hdl.isConnectionTimeoutOccurred());
		Assert.assertFalse(hdl.isIdleTimeoutOccurred());
		hdl.setData(null);
		
		
		QAUtil.sleep(3000);

		Assert.assertFalse(hdl.isConnectionTimeoutOccurred());
		Assert.assertTrue(hdl.isIdleTimeoutOccurred());
		QAUtil.assertTimeout(hdl.elapsed, 2000, 1800, 5000);
		
		
		con.close();
		server.close();
	}
	
	
	@Test 
	public void testConnectionTimeout() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		
		Handler hdl = new Handler();
		NonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);
		
		con.setConnectionTimeoutMillis(4 * 1000);
		
		con.write("test" + EchoHandler.DELIMITER);
		QAUtil.sleep(1000);
		
		Assert.assertEquals("test", hdl.getData());
		Assert.assertFalse(hdl.isConnectionTimeoutOccurred());
		Assert.assertFalse(hdl.isIdleTimeoutOccurred());
		hdl.setData(null);
		
		
		con.reset();
		
		con.setHandler(hdl);

		
		con.write("test2" + EchoHandler.DELIMITER);
		QAUtil.sleep(1000);
		
		Assert.assertEquals("test2", hdl.getData());
		Assert.assertFalse(hdl.isConnectionTimeoutOccurred());
		Assert.assertFalse(hdl.isIdleTimeoutOccurred());
		hdl.setData(null);
		
		
		QAUtil.sleep(5000);

		Assert.assertTrue(hdl.isConnectionTimeoutOccurred());
		Assert.assertFalse(hdl.isIdleTimeoutOccurred());
		QAUtil.assertTimeout(hdl.elapsed, 2000, 1800, 5000);
		
		
		con.close();
		server.close();
	}
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class Handler implements IConnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		private AtomicReference<String> data = new AtomicReference<String>(null);
		
		private AtomicBoolean connectionTimeoutOccurred = new AtomicBoolean(false);
		private AtomicBoolean idleTimeoutOccurred = new AtomicBoolean(false);
		
		private long elapsed = 0;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			elapsed = System.currentTimeMillis();
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			data.set(connection.readStringByDelimiter(EchoHandler.DELIMITER));
			return true;
		}
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			elapsed = System.currentTimeMillis() - elapsed;
			connectionTimeoutOccurred.set(true);
			return true;
		}
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			elapsed = System.currentTimeMillis() - elapsed;
			idleTimeoutOccurred.set(true);
			return true;
		}
		
		String getData() {
			return data.get();
		}
		
		void setData(String data) {
			this.data.set(data);
		}
		
		boolean isConnectionTimeoutOccurred() {
			return connectionTimeoutOccurred.get();
		}
		
		boolean isIdleTimeoutOccurred() {
			return idleTimeoutOccurred.get();
		}
		
	}
}
