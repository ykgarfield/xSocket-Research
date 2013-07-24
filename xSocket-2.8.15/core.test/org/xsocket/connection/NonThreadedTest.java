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
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class NonThreadedTest {

	private static final String DELIMITER = "\r";

	
	@Test 
	public void testSingleThreaded() throws Exception {

		Handler serverHandler = new Handler(); 
		IServer server = new Server(serverHandler);
		server.setFlushmode(FlushMode.ASYNC);
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		connection.write("test" + DELIMITER);
		
		String response = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals("test", response);
		Assert.assertTrue(serverHandler.threadName.startsWith("xDispatcher"));
		
		connection.close();
		server.close();
	}

	
	@Test
	public void testSimplePerformanceCompare() throws Exception {
		System.out.println("testSimplePerformanceCompare");
		
		int workers = 1;
		
		IServer threadedServer = new org.xsocket.connection.Server(new MultithreadedHandler());
		threadedServer.setFlushmode(FlushMode.ASYNC);
		ConnectionUtils.start(threadedServer);

		IServer nonThreadedServer = new org.xsocket.connection.Server(new Handler());
		nonThreadedServer.setFlushmode(FlushMode.ASYNC);
		ConnectionUtils.start(nonThreadedServer);

		
		IServer mixedThreadedServer = new org.xsocket.connection.Server(new MixedthreadedHandler());
		mixedThreadedServer.setFlushmode(FlushMode.ASYNC);
		ConnectionUtils.start(mixedThreadedServer);

		
		List<IBlockingConnection> threadedClients = new ArrayList<IBlockingConnection>();
		List<IBlockingConnection> nonthreadedClients = new ArrayList<IBlockingConnection>();
		List<IBlockingConnection> mixedthreadedClients = new ArrayList<IBlockingConnection>();
		
		
		for (int i = 0; i < workers; i++) {
			threadedClients.add(new BlockingConnection("localhost", threadedServer.getLocalPort()));
			nonthreadedClients.add(new BlockingConnection("localhost", nonThreadedServer.getLocalPort()));
			mixedthreadedClients.add(new BlockingConnection("localhost", mixedThreadedServer.getLocalPort()));
		}
		
		
		
		System.out.println("warm up non threaded clients");
		call(nonthreadedClients, 10000);
		
		System.out.println("warm up threaded clients");
		call(threadedClients, 10000);
		
		System.out.println("warm up mixed threaded clients");
		call(mixedthreadedClients,  10000);

		
		System.out.println("run test non threaded");
		long elapsedNonThreaded = call(nonthreadedClients, 40000);
		
		System.out.println("run test threaded");
		long elapsedThreaded = call(threadedClients, 40000);
		
		System.out.println("run test mixed threaded");
		long elapsedMixedThreaded = call(mixedthreadedClients, 40000);
		

		
		int percent = (int) (((double) elapsedNonThreaded * 100) / elapsedThreaded);
		int percentMixed = (int) (((double) elapsedMixedThreaded * 100) / elapsedThreaded);
		
		System.out.println("\r\nthreaded " + DataConverter.toFormatedDuration(elapsedThreaded) +  " " +
				           "\r\nnonthreaded " + DataConverter.toFormatedDuration(elapsedNonThreaded) + " (" + percent + "%)" +
				           "\r\nmixedthreaded " + DataConverter.toFormatedDuration(elapsedMixedThreaded) + " (" + percentMixed + "%)");
		
		threadedServer.close();
		nonThreadedServer.close();
		
		if (elapsedNonThreaded > elapsedThreaded) {
			String msg = "error non-threaded run long than threaded";
			System.out.println(msg);
			Assert.fail(msg);
		}
		
		if (elapsedMixedThreaded > elapsedThreaded) {
			String msg = "error mixed-threaded run long than threaded";
			System.out.println(msg);
			Assert.fail(msg);
		}
	}
	
	
	private long call(List<IBlockingConnection> cons, final int loops) throws IOException {

		final AtomicInteger running = new AtomicInteger(); 
		final AtomicLong elapsed = new AtomicLong(); 
		
		for (final IBlockingConnection con : cons) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running.incrementAndGet();
					try {
						long t = call(con, loops);
						elapsed.addAndGet(t);
					} catch(Exception e) {
						e.printStackTrace();
					}
					running.decrementAndGet();
				}
			};
			
			t.start();
		}
		
		do {
			QAUtil.sleep(100);
		} while (running.get() > 0);
		
		
		return elapsed.get();
	}
	
	

	private long call(IBlockingConnection con, int loops) throws IOException {

		long elapsed = 0;
		
		for (int i = 0; i < loops; i++) {
			long start = System.nanoTime();
			con.write("Hello" + DELIMITER);
			String response = con.readStringByDelimiter(DELIMITER);
			elapsed += System.nanoTime() - start;

			Assert.assertEquals("Hello", response);
		}
		
		return (elapsed / 1000000);
	}
	
	


	@Execution(Execution.NONTHREADED)
	private static final class Handler implements IConnectHandler, IDataHandler {
		
		private String threadName = null;

		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return true;
		}

		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			try {
				connection.write(connection.readStringByDelimiter(DELIMITER) + DELIMITER);
			} catch (SocketTimeoutException se) {
				se.printStackTrace();
				throw se;
			}
			
			return true;
		}
	}
	
	
	@Execution(Execution.MULTITHREADED)
	private static final class MultithreadedHandler implements IConnectHandler, IDataHandler {
		
		private String threadName = null;

		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return true;
		}

		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			try {
				connection.write(connection.readStringByDelimiter(DELIMITER) + DELIMITER);
			} catch (SocketTimeoutException se) {
				se.printStackTrace();
				throw se;
			}

			return true;
		}
	}
	
	
	@Execution(Execution.NONTHREADED)
	private static final class MixedthreadedHandler implements IConnectHandler, IDataHandler {
		
		private String threadName = null;
		
		@Execution(Execution.MULTITHREADED)
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			try {
				connection.write(connection.readStringByDelimiter(DELIMITER) + DELIMITER);
			} catch (SocketTimeoutException se) {
				se.printStackTrace();
				throw se;
			}
			return true;
		}
	}
}
