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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.BlockingConnectionPool;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class BlockingConnectionPoolTest {

	private static final String DELIMITER = System.getProperty("line.separator");
	private static final int LOOPS = 10;

	private AtomicInteger running = new AtomicInteger(0);

	private final List<String> errors = new ArrayList<String>();



	public static void main(String[] args) throws Exception {
		IServer server = new org.xsocket.connection.Server(new EchoHandler());
		ConnectionUtils.start(server);

		new BlockingConnectionPoolTest().callPooled("localhost", server.getLocalPort(), 40000000);
	}


	@Test
	public void testSimple() throws Exception {

		BlockingConnectionPool pool = new BlockingConnectionPool();
		MyServer server = new MyServer(50);
		new Thread(server).start();

		ConnectionUtils.registerMBean(pool);

		IBlockingConnection con = null;
		for (int i = 0;  i < 50; i++) {
			try {
				// retrieve a connection (if no connection is in pool, a new one will be created)
				con = pool.getBlockingConnection("localhost", server.getLocalPort());
				con.write("Hello" + DELIMITER);
				Assert.assertEquals("OK", con.readStringByDelimiter(DELIMITER));

				con.close();
			} catch (IOException e) {
				if (con != null) {
					try {
						 // if the connection is invalid -> destroy it (it will not return into the pool)
						pool.destroy(con);
					 } catch (Exception ignore) { }
				}
			}
		}


		pool.close();
		server.close();
	}


	@Test
	public void testSimplePerformanceCompare() throws Exception {
	    
		IServer server = new org.xsocket.connection.Server(new EchoHandler());
		server.setFlushmode(FlushMode.ASYNC);
		ConnectionUtils.start(server);

		// warm up
		callPooled("localhost", server.getLocalPort(), 500);
		callUnPooled("localhost", server.getLocalPort(), 500);

		long elapsedPooled = callPooled("localhost", server.getLocalPort(), 5000);
		long elapsedUnpooled = callUnPooled("localhost", server.getLocalPort(), 5000);


		System.out.println("\r\npooled " + DataConverter.toFormatedDuration(elapsedPooled) +  " " +
				           " unpooled " + DataConverter.toFormatedDuration(elapsedUnpooled));

		server.close();

		Assert.assertTrue(elapsedPooled < elapsedUnpooled);
	}


	private long callPooled(String hostname, int port, int loops) throws IOException {
		long elapsed = 0;

		BlockingConnectionPool pool = new BlockingConnectionPool();

		for (int j = 0; j < loops; j++) {
		    long start = System.nanoTime();
		    IBlockingConnection con = pool.getBlockingConnection(hostname, port);
		    con.setFlushmode(FlushMode.ASYNC);
    
		    con.write("Hello\r\n");
		    elapsed += System.nanoTime() - start;
    			
		    Assert.assertEquals("Hello", con.readStringByDelimiter("\r\n"));
		    con.close();
        }
		
		pool.close();

		return (elapsed / 1000000);
	}


	private long callUnPooled(String hostname, int port, int loops) throws IOException {
		long elapsed = 0;

		for (int j = 0; j < loops; j++) {
		    long start = System.nanoTime();
		    IBlockingConnection con = new BlockingConnection(hostname, port);
		    con.setFlushmode(FlushMode.ASYNC);
    
		    con.write("Hello\r\n");
		    elapsed += System.nanoTime() - start;
    			
		    Assert.assertEquals("Hello", con.readStringByDelimiter("\r\n"));
		    con.close();
		}


		return (elapsed / 1000000);
	}



  


	@Test
	public void testUnlimitedPool() throws Exception {
		
		BlockingConnectionPool pool = new BlockingConnectionPool();
		ConnectionUtils.registerMBean(pool);

		IDataHandler dh = new IDataHandler() {
			
			public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {

				int sleeptime = connection.readInt();
				QAUtil.sleep(sleeptime);
				connection.write("OK\r\n");
				return true;
			}
		};
		
		IServer server = new Server(dh);
		server.start();

		IBlockingConnection con1 = pool.getBlockingConnection("localhost", server.getLocalPort());
		con1.write(2000);
		
		IBlockingConnection con2 = pool.getBlockingConnection("localhost", server.getLocalPort());
		con2.write(2000);
		
		IBlockingConnection con3 = pool.getBlockingConnection("localhost", server.getLocalPort());
		con3.write(2000);

		QAUtil.sleep(500);
		Assert.assertEquals(3, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());

		IBlockingConnection con4 = pool.getBlockingConnection("localhost", server.getLocalPort());
		con4.write(1000);
		
		IBlockingConnection con5 = pool.getBlockingConnection("localhost", server.getLocalPort());
		con5.write(1000);

		QAUtil.sleep(500);
		Assert.assertEquals(5, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());
		
		

		Assert.assertEquals("OK", con1.readStringByDelimiter("\r\n"));
		Assert.assertEquals("OK", con2.readStringByDelimiter("\r\n"));
		con1.close();
		con2.close();
		
		QAUtil.sleep(1000);		
		Assert.assertEquals(3, pool.getNumActive());
		Assert.assertEquals(2, pool.getNumIdle());
		
		Assert.assertEquals("OK", con3.readStringByDelimiter("\r\n"));
		Assert.assertEquals("OK", con4.readStringByDelimiter("\r\n"));
		Assert.assertEquals("OK", con5.readStringByDelimiter("\r\n"));
		con3.close();
		con4.close();
		con5.close();
		
		QAUtil.sleep(1000);		
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(5, pool.getNumIdle());

		
		
		pool.close();
		server.close();
	}





	@Test
	public void testLimitedPool() throws Exception {
		int maxActive = 2;

		BlockingConnectionPool pool = new BlockingConnectionPool();
		pool.setMaxActive(maxActive);

		IDataHandler dh = new IDataHandler() {
			
			public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {

				int sleeptime = connection.readInt();
				QAUtil.sleep(sleeptime);
				connection.write("OK\r\n");
				return true;
			}
		};
		
		IServer server = new Server(dh);
		server.start();

		IBlockingConnection con1 = pool.getBlockingConnection("localhost", server.getLocalPort());
		con1.write(2000);
		
		IBlockingConnection con2 = pool.getBlockingConnection("localhost", server.getLocalPort());
		con2.write(2000);
		
		try {
			pool.getBlockingConnection("localhost", server.getLocalPort());
			Assert.fail("MaxConnectionsExceededException expected");
		} catch (MaxConnectionsExceededException expected) {	}
		
		
		pool.close();
		server.close();	
	}
	

	@Test
	public void testLimitedPool2() throws Exception {

		Server server1 = new Server(new EchoHandler());
		server1.start();

		Server server2 = new Server(new EchoHandler());
		server2.start();

		BlockingConnectionPool pool = new BlockingConnectionPool();
		pool.setMaxActive(2);
		ConnectionUtils.registerMBean(pool);

		IBlockingConnection bc1a = pool.getBlockingConnection("localhost", server1.getLocalPort());
		Assert.assertEquals(1, pool.getNumActive());

		IBlockingConnection bc1b = pool.getBlockingConnection("localhost", server1.getLocalPort());
		Assert.assertEquals(2, pool.getNumActive());


		bc1a.close();
		
		QAUtil.sleep(300);
		
		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumIdle());

		IBlockingConnection bc2a = pool.getBlockingConnection("localhost", server2.getLocalPort());
		Assert.assertEquals(2, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumIdle());


		try {
			IBlockingConnection bc2b = pool.getBlockingConnection("localhost", server2.getLocalPort());
			Assert.fail("MaxConnectionsExceededException expected");
		} catch (MaxConnectionsExceededException expected) { }

		pool.close();
		server1.close();
		server2.close();
	}



	@Test
	public void testCloseAndDestroyConnection() throws Exception {
		BlockingConnectionPool pool = new BlockingConnectionPool();

		MyServer server = new MyServer(50);
		new Thread(server).start();


		IBlockingConnection connection = pool.getBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);

		Assert.assertTrue(pool.getNumActive() == 1);
		Assert.assertTrue(pool.getNumIdle() == 0);

		connection.close();
		QAUtil.sleep(500);

		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumIdle());

		connection = pool.getBlockingConnection("localhost", server.getLocalPort());
		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());

		pool.destroy(connection);
		QAUtil.sleep(300);

		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());


		pool.close();
		server.close();
	}


	@Test
	public void testIdleTimeout() throws Exception {
		errors.clear();
		BlockingConnectionPool pool = new BlockingConnectionPool();

		MyServer server = new MyServer(50);
		new Thread(server).start();

		IBlockingConnection con = pool.getBlockingConnection("localhost", server.getLocalPort());
		con.setIdleTimeoutMillis(1 * 1000);

		Assert.assertTrue(con.isOpen());

		QAUtil.sleep(1500);
		Assert.assertFalse(con.isOpen());
		Assert.assertTrue(pool.getNumActive() == 0);


		pool.close();
		server.close();
	}



	@Test
	public void testConnectionTimeout() throws Exception {
		errors.clear();
		final BlockingConnectionPool pool = new BlockingConnectionPool();

		MyServer server = new MyServer(50);
		new Thread(server).start();

		IBlockingConnection con = pool.getBlockingConnection("localhost", server.getLocalPort());
		con.setConnectionTimeoutMillis(1 * 1000);

		Assert.assertTrue(con.isOpen());

		QAUtil.sleep(1500);
		Assert.assertFalse(con.isOpen());
		Assert.assertTrue(pool.getNumActive() == 0);


		pool.close();
		server.close();
	}



	private void startWorkers(final String host, final int port, final BlockingConnectionPool pool, int count) {
		for (int i = 0; i < count; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					running.incrementAndGet();

					IBlockingConnection con = null;

					for (int i = 0; i < LOOPS; i++) {
						try {
							con = pool.getBlockingConnection(host, port);
							con.setAutoflush(false);

							con.write("test1" + DELIMITER);
							con.write("test2" + DELIMITER);
							con.flush();

							Assert.assertEquals("OK", con.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE));
							Assert.assertEquals("OK", con.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE));
						} catch (Exception ignore) {


						} finally {
							if (con != null) {
								try {
									con.close();

								} catch (Exception ignore) { }
							}
						}
					}

					running.decrementAndGet();
				}
			};
			t.start();
		}
	}


	private static final class EchoHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}
	}



	private static class MyServer implements Runnable {
		private ExecutorService executorService = Executors.newCachedThreadPool();
		private volatile boolean isRunning = true;

		private ServerSocket sso = null;
		private long pause = 0;

		MyServer(long pause) throws IOException {
			this.pause = pause;
			sso = new ServerSocket(0);
		}

		public void run()  {
			while (isRunning) {
				try {
					Socket s = sso.accept();
					executorService.execute(new Worker(s, pause));
				} catch (Exception e) {
					if (isRunning) {
						e.printStackTrace();
					}
				}
			}
		}

		public InetAddress getLocalAddress() {
			return sso.getInetAddress();
		}

		public int getLocalPort() {
			return sso.getLocalPort();
		}

		public void close() throws IOException {
			isRunning = false;
			sso.close();
		}
	}

	private static class Worker implements Runnable {
		private volatile boolean isRunning = true;

		private LineNumberReader in = null;
		private PrintWriter out = null;
		private Socket s = null;
		private long pause = 0;

	    Worker(Socket s, long pause) throws IOException {
	      this.s = s;
	      this.pause = pause;
	      in = new LineNumberReader(new InputStreamReader(s.getInputStream()));
	      out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
	    }

	    public void run() {
	    	while (isRunning) {
	    		try {
	    			String request = in.readLine();
	    			if (request != null) {
	    				try {
		    				Thread.sleep(pause);
		    			} catch (InterruptedException ignore) { }

		    			out.write("OK" + DELIMITER);
		    			out.flush();
		    			//System.out.print(".");

		    			//LOG.info("Server sending..");

	    			} else {
	    				isRunning = false;
	    			}
	    		} catch (Exception e ) {
	    			e.printStackTrace();
	    		}
	    	}
	    	try {
	    		in.close();
	    		out.close();
	    		s.close();
	    	} catch (Exception e) {
	    		e.printStackTrace();
	    	}
	    }
	}
}
