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
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.Resource;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class HandlerChainTest {


	@Test 
	public void testLifeCycle() throws Exception {

		HandlerChain root = new HandlerChain();
		
		Handler h1 = new Handler();
		h1.setResultOnData(true);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.start();
		
		Assert.assertEquals(1, h1.getCountOnInitCalled());
		Assert.assertEquals(1, h2.getCountOnInitCalled());
		Assert.assertNotNull(h1.getServer());
		Assert.assertNotNull(h2.getServer());
		
		server.close();
		
		Assert.assertEquals(1, h1.getCountOnDestroyCalled());
		Assert.assertEquals(1, h2.getCountOnDestroyCalled());
	}

	
	@Test 
	public void testConnectHandler() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnConnectCalled());
		Assert.assertTrue(h2.getOnConnectThreadname().startsWith("xWorkerPool"));	
		
		con.close();
		server.close();
	}


	

	@Test 
	public void testConnectHandlerNonThreaded() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		NonThreadedHandler h2 = new NonThreadedHandler();
		h2.setResultOnData(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnConnectCalled());
		Assert.assertTrue(h2.getOnConnectThreadname().startsWith("xDispatcher"));	
		
		con.close();
		server.close();
	}

	

	
	@Test 
	public void testDataHandler() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		
		con.write("test");
		Assert.assertEquals("test", con.readStringByLength(4));
		
		Assert.assertEquals(1, h1.getCountOnDataCalled());
		Assert.assertTrue(h1.getOnDataThreadname().startsWith("xWorkerPool"));	
		
		Assert.assertEquals(1, h2.getCountOnDataCalled());
		Assert.assertTrue(h2.getOnDataThreadname().startsWith("xWorkerPool"));
		
		con.close();
		server.close();
	}
	

		
	@Test 
	public void testDataHandlerNonThreaded() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		NonThreadedHandler h2 = new NonThreadedHandler();
		h2.setResultOnData(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		
		con.write("test");
		Assert.assertEquals("test", con.readStringByLength(4));
		
		Assert.assertEquals(1, h1.getCountOnDataCalled());
		Assert.assertTrue(h1.getOnDataThreadname().startsWith("xDispatcher"));	
		
		Assert.assertEquals(1, h2.getCountOnDataCalled());
		Assert.assertTrue(h2.getOnDataThreadname().startsWith("xDispatcher"));
		
		con.close();
		server.close();
	}
	

	
	@Test 
	public void testDataHandlerInterruptChain() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(true);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

		con.write("test");
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h1.getCountOnDataCalled());
		Assert.assertEquals(0, h2.getCountOnDataCalled());
		
		con.close();
		server.close();
	}
	

	@Test 
	public void testDisconnectHandler() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnConnectCalled());
		Assert.assertTrue(h2.getOnConnectThreadname().startsWith("xWorkerPool"));	
		
		
		server.close();
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnDisconnectCalled());
		Assert.assertTrue(h2.getOnDisconnectThreadname().startsWith("xWorkerPool"));	
		
		con.close();
		server.close();
	}


	
	@Test 
	public void testDisconnectHandlerNonthreaded() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		NonThreadedHandler h2 = new NonThreadedHandler();
		h2.setResultOnData(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnConnectCalled());
		Assert.assertTrue(h2.getOnConnectThreadname().startsWith("xDispatcher"));	
		
		server.close();
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnDisconnectCalled());
		Assert.assertTrue("thread " + h2.getOnDisconnectThreadname() + " does not start with xDispatcher", h2.getOnDisconnectThreadname().startsWith("xDispatcher"));
		
		con.close();
		server.close();
	}

	
	
	@Test 
	public void testIdleTimeoutHandler() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		h2.setResultOnIdleTimeout(true);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.setIdleTimeoutMillis(300);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnIdleTimeoutCalled());
		Assert.assertTrue(h2.getOnIdleTimeoutThreadname().startsWith("xWorkerPool"));
		Assert.assertEquals(0, h2.getCountOnDisconnectCalled());
		
		con.close();
		server.close();
	}
	
	@Test 
	public void testIdleTimeoutHandlerNonThreaded() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		NonThreadedHandler h2 = new NonThreadedHandler();
		h2.setResultOnData(false);
		h2.setResultOnIdleTimeout(true);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.setIdleTimeoutMillis(300);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnIdleTimeoutCalled());
		Assert.assertTrue(h2.getOnIdleTimeoutThreadname().equals("xIoTimer"));
		Assert.assertEquals(0, h2.getCountOnDisconnectCalled());
		
		con.close();
		server.close();
	}


	@Test 
	public void testIdleTimeoutHandlerNotHandledResult() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		h2.setResultOnIdleTimeout(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.setIdleTimeoutMillis(300);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(3000);
		
		Assert.assertEquals("idle timeout should be called", 1, h2.getCountOnIdleTimeoutCalled());
		Assert.assertTrue("thread is " + h2.getOnIdleTimeoutThreadname(), h2.getOnIdleTimeoutThreadname().startsWith("xWorkerPool"));
		Assert.assertEquals("disconnect should be called", 1, h2.getCountOnDisconnectCalled());
		
		con.close();
		server.close();
	}

	

	@Test 
	public void testConnectionTimeoutHandler() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		h2.setResultOnConnectionTimeout(true);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.setConnectionTimeoutMillis(300);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(2000);
		
		Assert.assertEquals(1, h2.getCountOnConnectionTimeoutCalled());
		Assert.assertTrue(h2.getOnConnectionTimeoutThreadname().startsWith("xWorkerPool"));
		Assert.assertEquals(0, h2.getCountOnDisconnectCalled());
		
		con.close();
		server.close();
	}

	
	@Test 
	public void testConnectionTimeoutHandlerNonThreaded() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		NonThreadedHandler h2 = new NonThreadedHandler();
		h2.setResultOnData(false);
		h2.setResultOnConnectionTimeout(true);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.setConnectionTimeoutMillis(300);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(2000);
		
		Assert.assertEquals(1, h2.getCountOnConnectionTimeoutCalled());
		Assert.assertTrue(h2.getOnConnectionTimeoutThreadname().startsWith("xIoTimer"));
		Assert.assertEquals(0, h2.getCountOnDisconnectCalled());
		
		con.close();
		server.close();
	}


	
	@Test 
	public void testConnectionTimeoutHandlerNotHandledResult() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);
		
		Handler h2 = new Handler();
		h2.setResultOnData(false);
		h2.setResultOnConnectionTimeout(false);
		root.addLast(h2);
		
		IServer server = new Server(root);
		server.setFlushmode(FlushMode.ASYNC);
		server.setConnectionTimeoutMillis(300);
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		Assert.assertEquals(1, h2.getCountOnConnectionTimeoutCalled());
		Assert.assertTrue(h2.getOnConnectionTimeoutThreadname().startsWith("xWorkerPool"));
		Assert.assertEquals(1, h2.getCountOnDisconnectCalled());
		
		con.close();
		server.close();
	}

	
	
	@Test 
	public void testNestedChain() throws Exception {

		HandlerChain root = new HandlerChain();
		
		FilterHandler h1 = new FilterHandler();
		h1.setResultOnData(false);
		root.addLast(h1);

		HandlerChain subChain = new HandlerChain();
		Handler h2 = new Handler();
		subChain.addLast(h2);

		root.addLast(subChain);

        IServer server = new Server(root);
        server.start();
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
        
        con.write("test");
        Assert.assertEquals("test", con.readStringByLength(4));
        
        Assert.assertEquals(1, h1.getCountOnDataCalled());
        Assert.assertTrue(h1.getOnDataThreadname().startsWith("xWorkerPool"));  
        
        Assert.assertEquals(1, h2.getCountOnDataCalled());
        Assert.assertTrue(h2.getOnDataThreadname().startsWith("xWorkerPool"));
        
        con.close();
        server.close();
	}


    @Test 
    public void testNestedChainModified() throws Exception {

        HandlerChain root = new HandlerChain();
        
        FilterHandler h1 = new FilterHandler();
        h1.setResultOnData(false);
        root.addLast(h1);

        HandlerChain subChain = new HandlerChain();
        FilterHandler h2 = new FilterHandler();
        h2.setResultOnData(false);
        subChain.addLast(h2);

        NonThreadedHandler h3 = new NonThreadedHandler();
        subChain.addLast(h3);
        
        root.addLast(subChain);

        IServer server = new Server(root);
        server.start();
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
        
        con.write("test");
        Assert.assertEquals("test", con.readStringByLength(4));
        
        Assert.assertEquals(1, h1.getCountOnDataCalled());
        Assert.assertTrue(h1.getOnDataThreadname().startsWith("xDispatcher"));  
        
        Assert.assertEquals(1, h2.getCountOnDataCalled());
        Assert.assertTrue(h2.getOnDataThreadname().startsWith("xDispatcher"));
        

        subChain.addLast(new Handler());
        
        
        con.write("test");
        Assert.assertEquals("test", con.readStringByLength(4));
        
        Assert.assertEquals(2, h1.getCountOnDataCalled());
        Assert.assertTrue(h1.getOnDataThreadname().startsWith("xWorkerPool"));  
        
        Assert.assertEquals(2, h2.getCountOnDataCalled());
        Assert.assertTrue(h2.getOnDataThreadname().startsWith("xWorkerPool"));
        
        
        con.close();
        server.close();
    }

	
	
	
	private static final class Handler implements IConnectHandler, IDataHandler, IDisconnectHandler, IConnectionTimeoutHandler, IIdleTimeoutHandler, ILifeCycle {

		@Resource
		private IServer server = null;
		
		private int countOnInitCalled = 0;
		private int countOnDestroyCalled = 0;
		
		private int countOnConnectCalled = 0;
		private String onConnectThreadname = null;
		
		private boolean resultOnData = true;
		private int countOnDataCalled = 0;
		private String onDataThreadname = null;

		private int countOnDisconnectCalled = 0;
		private String onDisconnectThreadname = null;

		private boolean resultOnIdleTimeout = true;
		private int countOnIdleTimeoutCalled = 0;
		private String onIdleTimeoutThreadname = null;

		private boolean resultOnConnectionTimeout = true;
		private int countOnConnectionTimeoutCalled = 0;
		private String onConnectionTimeoutThreadname = null;

		
		public void onInit() {
			countOnInitCalled++;
		}
		
		public void onDestroy() throws IOException {
			countOnDestroyCalled++;
		}
		
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			countOnConnectCalled++;
			onConnectThreadname = Thread.currentThread().getName();
			return true;
		}

		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			countOnDataCalled++;
			onDataThreadname = Thread.currentThread().getName();
			connection.write((connection.readByteBufferByLength(connection.available())));
			return resultOnData;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			countOnDisconnectCalled++;
			onDisconnectThreadname = Thread.currentThread().getName();
			return true;
		}
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			countOnIdleTimeoutCalled++;
			onIdleTimeoutThreadname = Thread.currentThread().getName();
			
			return resultOnIdleTimeout;
		}

		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			countOnConnectionTimeoutCalled++;
			onConnectionTimeoutThreadname = Thread.currentThread().getName();
			
			return resultOnConnectionTimeout;
		}

		
		void setResultOnData(boolean resultOnData) {
			this.resultOnData = resultOnData;
		}
		
		void setResultOnIdleTimeout(boolean resultOnIdleTimeout) {
			this.resultOnIdleTimeout = resultOnIdleTimeout;
		}
		
		void setResultOnConnectionTimeout(boolean resultOnConnectionTimeout) {
			this.resultOnConnectionTimeout = resultOnConnectionTimeout;
		}
		
		int getCountOnDataCalled() {
			return countOnDataCalled;
		}
				
		int getCountOnConnectCalled() {
			return countOnConnectCalled;
		}
		
		String getOnConnectThreadname() {
			return onConnectThreadname;
		}
		
		String getOnDataThreadname() {
			return onDataThreadname;
		}
		
		int getCountOnDisconnectCalled() {
			return countOnDisconnectCalled;
		}
		
		String getOnDisconnectThreadname() {
			return onDisconnectThreadname;
		}
		
		public int getCountOnInitCalled() {
			return countOnInitCalled;
		}
		
		public int getCountOnDestroyCalled() {
			return countOnDestroyCalled;
		}

		public int getCountOnIdleTimeoutCalled() {
			return countOnIdleTimeoutCalled;
		}
		
		String getOnIdleTimeoutThreadname() {
			return onIdleTimeoutThreadname;
		}
		
		public int getCountOnConnectionTimeoutCalled() {
			return countOnConnectionTimeoutCalled;
		}
		
		String getOnConnectionTimeoutThreadname() {
			return onConnectionTimeoutThreadname;
		}
		
		public IServer getServer() {
			return server;
		}
	}
	
	
	@Execution(Execution.NONTHREADED)
	private static final class NonThreadedHandler implements IDataHandler, IConnectHandler, IDisconnectHandler, IConnectionTimeoutHandler, IIdleTimeoutHandler  {
		
		private boolean resultOnData = true;
		private int countOnDataCalled = 0;
		private String onDataThreadname = null;
		
		private int countOnConnectCalled = 0;
		private String onConnectThreadname = null;
		
		private int countOnDisconnectCalled = 0;
		private String onDisconnectThreadname = null;

		private boolean resultOnIdleTimeout = true;
		private int countOnIdleTimeoutCalled = 0;
		private String onIdleTimeoutThreadname = null;

		private boolean resultOnConnectionTimeout = true;
		private int countOnConnectionTimeoutCalled = 0;
		private String onConnectionTimeoutThreadname = null;

		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			countOnConnectCalled++;
			onConnectThreadname = Thread.currentThread().getName();
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			countOnDataCalled++;
			onDataThreadname = Thread.currentThread().getName();
			connection.setFlushmode(FlushMode.ASYNC);

			connection.write((connection.readByteBufferByLength(connection.available())));
			return resultOnData;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			countOnDisconnectCalled++;
			onDisconnectThreadname = Thread.currentThread().getName();
			return true;
		}
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			countOnIdleTimeoutCalled++;
			onIdleTimeoutThreadname = Thread.currentThread().getName();
			
			return resultOnIdleTimeout;
		}
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			countOnConnectionTimeoutCalled++;
			onConnectionTimeoutThreadname = Thread.currentThread().getName();
			
			return resultOnConnectionTimeout;
		}
		
		void setResultOnData(boolean resultOnData) {
			this.resultOnData = resultOnData;
		}
		
		void setResultOnIdleTimeout(boolean resultOnIdleTimeout) {
			this.resultOnIdleTimeout = resultOnIdleTimeout;
		}
		
		void setResultOnConnectionTimeout(boolean resultOnConnectionTimeout) {
			this.resultOnConnectionTimeout = resultOnConnectionTimeout;
		}
		
		int getCountOnConnectCalled() {
			return countOnConnectCalled;
		}
		
		String getOnConnectThreadname() {
			return onConnectThreadname;
		}

		
		int getCountOnDataCalled() {
			return countOnDataCalled;
		}
		
		String getOnDataThreadname() {
			return onDataThreadname;
		}
		
		int getCountOnDisconnectCalled() {
			return countOnDisconnectCalled;
		}
		
		String getOnDisconnectThreadname() {
			return onDisconnectThreadname;
		}
		
		public int getCountOnIdleTimeoutCalled() {
			return countOnIdleTimeoutCalled;
		}
		
		String getOnIdleTimeoutThreadname() {
			return onIdleTimeoutThreadname;
		}
		
		public int getCountOnConnectionTimeoutCalled() {
			return countOnConnectionTimeoutCalled;
		}
		
		String getOnConnectionTimeoutThreadname() {
			return onConnectionTimeoutThreadname;
		}
	}

	
	private static final class FilterHandler implements IDataHandler {
		
		private boolean resultOnData = true;
		private int countOnDataCalled = 0;
		private String onDataThreadname = null;
		
		
		@Execution(Execution.NONTHREADED)
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			countOnDataCalled++;
			onDataThreadname = Thread.currentThread().getName();
			
			return resultOnData;
		}
		
		void setResultOnData(boolean resultOnData) {
			this.resultOnData = resultOnData;
		}
		
		int getCountOnDataCalled() {
			return countOnDataCalled;
		}
		
		String getOnDataThreadname() {
			return onDataThreadname;
		}
	}
}
