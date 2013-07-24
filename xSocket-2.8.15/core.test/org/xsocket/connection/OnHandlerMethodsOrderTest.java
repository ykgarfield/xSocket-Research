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
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;



import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xsocket.ILifeCycle;
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
public final class OnHandlerMethodsOrderTest {
	
	private enum State { CONNECTED, DISCONNECTED };
	
	private static final String DELIMITER = "\r\n";
	private static final int CLIENTS = 10;
	private static final int LOOPS = 30;
	
	private final List<String> clientErrors = new ArrayList<String>();
	private final AtomicInteger running = new AtomicInteger(0);

	
	public static void main(String[] args) throws Exception {
		
		for (int i = 0; i < 1000; i++) {
			new OnHandlerMethodsOrderTest().testIdleTimeout();
		}
		
	}
	
	
	@Before
	public void setup() {
		clientErrors.clear();
		running.set(0);
	}
	
	
	
    @Test 
    public void testSimpleNative() throws Exception {

        MyHandler hdl = new MyHandler();
        IServer server = new Server(hdl);
        server.setWorkerpool(Executors.newCachedThreadPool(new MyThreadFactory()));
        server.start();
        
        
        Socket s = new Socket("localhost", server.getLocalPort());
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(s.getInputStream()));
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
        
        pw.write("test\r\n");
        pw.flush();
        
        String resp = lnr.readLine();
        Assert.assertEquals("test", resp);
        
        Assert.assertEquals(0, hdl.getCountOnIdleTimeoutCalled());
        Assert.assertEquals(0, hdl.getCountOnConnectionTimeoutCalled());
        Assert.assertEquals(0, hdl.getCountOnDisconnectCalled());
        Assert.assertEquals(1, hdl.getCountOnConnectCalled());
        Assert.assertTrue(hdl.getCountOnDataCalled() > 0);

        validateResult(hdl);
        s.close();
        server.close();
    }

	

    @Test 
    public void testSimple() throws Exception {
 
        MyHandler hdl = new MyHandler();
        IServer server = new Server(hdl);
        server.setWorkerpool(Executors.newCachedThreadPool(new MyThreadFactory()));
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
        con.write("test\r\n");
        
        String resp = con.readStringByDelimiter("\r\n");
        Assert.assertEquals("test", resp);
        
        Assert.assertEquals(0, hdl.getCountOnIdleTimeoutCalled());
        Assert.assertEquals(0, hdl.getCountOnConnectionTimeoutCalled());
        Assert.assertEquals(0, hdl.getCountOnDisconnectCalled());
        Assert.assertEquals(1, hdl.getCountOnConnectCalled());
        Assert.assertTrue(hdl.getCountOnDataCalled() > 0);

        validateResult(hdl);
        con.close();
        server.close();
    }

    


    @Test 
    public void testIdleTimeout() throws Exception {
        System.out.println("testIdleTimeout()");

        MyHandler hdl = new MyHandler();
        IServer server = new Server(hdl);
        server.setWorkerpool(Executors.newCachedThreadPool(new MyThreadFactory()));
        server.setIdleTimeoutMillis(2000);
        server.start();
        
        IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
        connection.setAutoflush(false);
        
        QAUtil.sleep(1000);
        Assert.assertEquals(0, hdl.getCountOnIdleTimeoutCalled());
        Assert.assertEquals(0, hdl.getCountOnConnectionTimeoutCalled());
        Assert.assertEquals(0, hdl.getCountOnDisconnectCalled());
        Assert.assertEquals(1, hdl.getCountOnConnectCalled());
        
        String request = "test";
        connection.write(request);
        connection.write(DELIMITER);
        connection.flush();


        QAUtil.sleep(3000);
        Assert.assertEquals(1, hdl.getCountOnIdleTimeoutCalled());
        Assert.assertEquals(0, hdl.getCountOnConnectionTimeoutCalled());
        Assert.assertEquals(1, hdl.getCountOnDisconnectCalled());
        Assert.assertEquals(1, hdl.getCountOnConnectCalled());
     
        
        validateResult(hdl);
        connection.close();
        server.close();
    }
	
	
	@Test 
	public void testWithoutTimeout() throws Exception {
		System.out.println("testWithoutTimeout");
 		
		MyHandler hdl = new MyHandler();
		final IServer server = new Server(hdl);
		server.setWorkerpool(Executors.newCachedThreadPool(new MyThreadFactory()));
		server.start();

		
		
		for (int i = 0; i < CLIENTS; i++) {
			
			Thread client = new Thread() {
				
				@Override
				public void run() {
					running.incrementAndGet();
					
					try {
						IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
						connection.setAutoflush(false);
						
						for (int j = 0; j < LOOPS; j++) {
							String request = "test";
							connection.write(request);
							connection.write(DELIMITER);
							connection.flush();
							String result = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
							
							if (!result.equals(request)) {
								clientErrors.add("Error send " + request + " but got " + result);
							}
						}
						
						
						connection.close();
						
					} catch (Exception e) {
						e.printStackTrace();
						
					} finally {
						running.decrementAndGet();
					}
				}
			};
			client.start();
		}
	
		
		do {
			QAUtil.sleep(100);
		} while (running.get() > 0);
		
		server.close();
		
		validateResult(hdl);
	}

	


	@Test 
	public void testConnectionTimeout() throws Exception {
		System.out.println("testConnectionTimeout()");
		
		
		MyHandler hdl = new MyHandler();
		IServer server = new Server(hdl);
		server.setWorkerpool(Executors.newCachedThreadPool(new MyThreadFactory()));
		server.setIdleTimeoutMillis(5000);
		server.setConnectionTimeoutMillis(1000);
		server.start();

		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		String request = "test";
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();

		QAUtil.sleep(3000);
		Assert.assertEquals(0, hdl.getCountOnIdleTimeoutCalled());
        Assert.assertEquals(1, hdl.getCountOnConnectionTimeoutCalled());
        Assert.assertEquals(1, hdl.getCountOnConnectCalled());
        Assert.assertEquals(1, hdl.getCountOnDisconnectCalled());
        
		validateResult(hdl);
		
		server.close();
		
		validateResult(hdl);
	}


	
	
	private void validateResult(MyHandler hdl) {
		
		if (clientErrors.size() > 0) {
			System.out.println("client error occured");
			for (String error : clientErrors) {
				System.out.println(error);
			}
		}

		if (hdl.errors.size() > 0) {
			System.out.println("server error occured");
			for (String error : hdl.errors) {
				System.out.println(error);
			}
		}

		
		Assert.assertTrue("client error occured", clientErrors.size() == 0);
		Assert.assertTrue("server error occured", hdl.errors.size() == 0);
	}
	
	
	private static final class MyHandler implements IConnectHandler, IDataHandler, IDisconnectHandler, ILifeCycle, IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		private final List<String> errors = new ArrayList<String>();

		private boolean isInitialized = false;
		private boolean isDestroyed = false;
		
		
		private AtomicInteger countOnDataCalled = new AtomicInteger(0);
		private AtomicInteger countOnConnectCalled = new AtomicInteger(0);
		private AtomicInteger countOnDisconnectCalled = new AtomicInteger(0);
		private AtomicInteger countOnIdleTimeoutCalled = new AtomicInteger(0);
		private AtomicInteger countOnConnectionTimeoutCalled = new AtomicInteger(0);

		
		public void onInit() {
			if (Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onInit] shouldn't be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			
			if (Thread.currentThread().getName().startsWith("xDispatcher")) {
				errors.add("[onInit] shouldn't be executed by a disptacher thread not by " + Thread.currentThread().getName());
			}

			
			if (isInitialized) {
				errors.add("[onInit]  shouldn't be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onInit]  shouldn't be destroyed");				
			}
			
			isInitialized = true;
		}
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			countOnConnectCalled.incrementAndGet();
			
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onConnect] should be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			

			if (!isInitialized) {
				errors.add("[onConnect]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onConnect]  shouldn't be isDestroyed");				
			}
			
			State state = (State) connection.getAttachment();
			if (state != null) {
				errors.add("[onConnect] connection is in state " + state + ". should be not in a state");
			} else {
				connection.setAttachment(State.CONNECTED);
			}

			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
		    countOnDataCalled.incrementAndGet();
		    
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onData] should be executed by a worker thread not by " + Thread.currentThread().getName());
			}

			if (!isInitialized) {
				errors.add("[onData]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("[onData]  shouldn't be isDestroyed");				
			}
			
			State state = (State) connection.getAttachment();
			if (state == null) {
				errors.add("[onData] connection doesn't contains state attachment (state should be connected)");
			} else {
				if (state != State.CONNECTED) {
					errors.add("[on data] connection should be in state connected. current state is " + state);
				}
			}
			
			connection.write(connection.readByteBufferByDelimiter(DELIMITER));
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			countOnDisconnectCalled.incrementAndGet();
			
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onDisconnect] should be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			
			if (!isInitialized) {
				errors.add("[onDisconnect]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("onDisconnect]  shouldn't be isDestroyed");				
			}
			
			
			State state = (State) connection.getAttachment();
			if (state == null) {
				errors.add("[onDisconnect] connection doesn't contains state attachment (state should be connected)");
			} else {
				if (state != State.CONNECTED) {
					errors.add("[onDisconnect] connection  should be in state connected. not in " + state);
				} else {
					connection.setAttachment(State.DISCONNECTED);
				}
			}
			return true;
		}

		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			countOnConnectionTimeoutCalled.incrementAndGet();
			
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[ConnectionTimeout] should be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			
			if (!isInitialized) {
				errors.add("[ConnectionTimeout]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("ConnectionTimeout]  shouldn't be isDestroyed");				
			}

			
			State state = (State) connection.getAttachment();
			if (state == null) {
				errors.add("[onConnectionTimeout] connection doesn't contains state attachment (state should be connected)");
			} else {
				if (state != State.CONNECTED) {
					errors.add("[ConnectionTimeout] connection  should be in state connected. not in " + state);
				} 
			}
			return false;
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			countOnIdleTimeoutCalled.incrementAndGet();
			
			if (!Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[ConnectionTimeout] should be executed by a worker thread");
			}
			
			if (!isInitialized) {
				errors.add("[ConnectionTimeout]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("ConnectionTimeout]  shouldn't be isDestroyed");				
			}

			
			State state = (State) connection.getAttachment();
			if (state == null) {
				errors.add("[onIdleTimeout] connection doesn't contains state attachment (state should be connected)");
			} else {
				if (state != State.CONNECTED) {
					errors.add("[IdleTimeout] connection should be in state connected. not in " + state);
				}
			}
			
			return false;
		}
		
		
		public void onDestroy() {
			if (Thread.currentThread().getName().startsWith(MyThreadFactory.PREFIX)) {
				errors.add("[onInit] shouldn't be executed by a worker thread not by " + Thread.currentThread().getName());
			}
			if (Thread.currentThread().getName().startsWith("xDispatcher")) {
				errors.add("[onInit] shouldn't be executed by a disptacher thread");
			}
			
			
			if (!isInitialized) {
				errors.add("[onDestroy]  should be initialized");
			}
			
			if (isDestroyed) {
				errors.add("onDestroy]  shouldn't be isDestroyed");				
			}
			
			isInitialized = true;

			
		}
		
		List<String> getErrors() {
			return errors;
		}
		

		int getCountOnDataCalled() {
		    return countOnDataCalled.get();
		}
	      
		int getCountOnConnectCalled() {
		    return countOnConnectCalled.get();
		}
		
		int getCountOnDisconnectCalled() {
            return countOnDisconnectCalled.get();
        }
		
		int getCountOnConnectionTimeoutCalled() {
            return countOnConnectionTimeoutCalled.get();
        }
		
		int getCountOnIdleTimeoutCalled() {
	          return countOnIdleTimeoutCalled.get();
		}

	}
	
	
	private static class MyThreadFactory implements ThreadFactory {
		
		static final String PREFIX = "WORKER";
		private int num = 0;
		
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r);
			t.setName(PREFIX + (++num));
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}
}
