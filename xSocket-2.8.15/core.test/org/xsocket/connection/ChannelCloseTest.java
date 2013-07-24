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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class ChannelCloseTest {

	
	@Test 
	public void testSimple() throws Exception {
		Handler hdl = new Handler();
		IServer server = new Server(hdl);
		server.start();
		
		IBlockingConnection clCon = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(2000);
		
		INonBlockingConnection servCon = hdl.getConnection();
		
		clCon.write("test\r");
		QAUtil.sleep(1000);
		
		Assert.assertTrue(servCon.isOpen());
		Assert.assertTrue(servCon.available() > 0);
		Assert.assertEquals("test", servCon.readStringByDelimiter("\r"));
		Assert.assertTrue(servCon.available() == 0);
		
		
		clCon.write("test\r");
		clCon.close();
		QAUtil.sleep(1000);
		
		Assert.assertTrue(servCon.isOpen());
		Assert.assertTrue(servCon.available() > 0);
		Assert.assertEquals("test", servCon.readStringByDelimiter("\r"));
		QAUtil.sleep(1000);

		Assert.assertTrue(servCon.available() == -1);
		Assert.assertFalse(servCon.isOpen()); 
		
		server.close();
	}


	@Test 
	public void testSimple2() throws Exception {
		Handler hdl = new Handler();
		IServer server = new Server(hdl);
		server.start();
		
		IBlockingConnection clCon = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(2000);
		
		INonBlockingConnection servCon = hdl.getConnection();
		
		clCon.write("test\r");
		QAUtil.sleep(1000);
		
		Assert.assertTrue(servCon.isOpen());
		Assert.assertTrue(servCon.available() > 0);
		Assert.assertEquals("test", servCon.readStringByDelimiter("\r"));
		Assert.assertTrue(servCon.available() == 0);
		
		
		clCon.write("test");
		clCon.close();
		QAUtil.sleep(1000);
		
		Assert.assertTrue(servCon.isOpen());
		Assert.assertTrue(servCon.available() > 0);
		
		try {
			servCon.readStringByDelimiter("\r");
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) {  }


		
		server.close();
	}
	

	
	@Test 
	public void testSimple3() throws Exception {
		Handler hdl = new Handler();
		IServer server = new Server(hdl);
		server.start();
		
		INonBlockingConnection clCon = new NonBlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(2000);
		
		INonBlockingConnection servCon = hdl.getConnection();
		
		clCon.write("test\r");
		QAUtil.sleep(1000);
		Assert.assertEquals("test", servCon.readStringByDelimiter("\r"));

		
		servCon.write("Hello client\r");
		QAUtil.sleep(1000);
		Assert.assertTrue(clCon.available() > 0);
		
		clCon.close();
		
		Assert.assertTrue(clCon.available() == -1);
		
		try {
			clCon.readStringByDelimiter("\r");
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		
		try {
			clCon.write("test\r");
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }
		
		server.close();
	}

	
	
	
	@Test 
	public void testSimple4() throws Exception {
		Handler hdl = new Handler();
		IServer server = new Server(hdl);
		server.start();
		
		INonBlockingConnection clCon = new NonBlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(2000);
		
		INonBlockingConnection servCon = hdl.getConnection();
		
		clCon.write("test\r");
		QAUtil.sleep(1000);
		Assert.assertEquals("test", servCon.readStringByDelimiter("\r"));

		
		servCon.write("Hello client\r");
		QAUtil.sleep(1000);
		Assert.assertTrue(clCon.available() > 0);
		
		servCon.close();
		QAUtil.sleep(1000);
		
		Assert.assertTrue(clCon.available() > 0);
		Assert.assertTrue(clCon.isOpen());
		
		clCon.readByteBufferByLength(clCon.available());
		Assert.assertTrue(clCon.available() == -1);
		Assert.assertFalse(clCon.isOpen());
		
		clCon.close();
	}
	
	
	
	@Test 
	public void testEcho() throws Exception {
		EchoHandler hdl = new EchoHandler();
		IServer server = new Server(hdl);
		server.start();
		
		IBlockingConnection clCon = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(2000);
		
		INonBlockingConnection srvCo = hdl.getConnection();
		Assert.assertTrue(srvCo.isOpen());
		
		String txt = "Test";
		
		clCon.write(txt + "\r");
		String res = clCon.readStringByDelimiter("\r");
		
		Assert.assertEquals(txt, res);
		
		clCon.close();
		QAUtil.sleep(1000);
		
		Assert.assertFalse(srvCo.isOpen());
		
		server.close();
	}
	
	
	
	private static final class Handler implements IConnectHandler {
		
		private final AtomicReference<INonBlockingConnection> conRef = new AtomicReference<INonBlockingConnection>();
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			this.conRef.set(connection);
			return true;
		}
		
		INonBlockingConnection getConnection() {
			return conRef.get();
		}	
	}

	
	private static final class EchoHandler implements IConnectHandler, IDataHandler {
		
		private INonBlockingConnection connection = null;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			this.connection = connection;
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}
		
		
		INonBlockingConnection getConnection() {
			return connection;
		}
	}
	
	
}
