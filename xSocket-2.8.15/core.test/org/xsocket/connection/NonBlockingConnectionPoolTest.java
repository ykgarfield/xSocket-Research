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
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnectionPool;




/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnectionPoolTest {

	private static final String DELIMITER = System.getProperty("line.separator");
	
	private static final String QUIT = "QUIT";


	@Test
	public void testLiveSimple() throws Exception {

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		INonBlockingConnection con = pool.getNonBlockingConnection("www.gmx.de", 80);

		Assert.assertTrue(pool.getNumActive() == 1);

		con.write("GET / HTTP/1.1\r\n" + 
		          "Host: www.gmx.com\r\n" +
                  "User-Agent: me\r\n\r\n");
		QAUtil.sleep(1000);

		String responseCode = con.readStringByDelimiter("\r\n");
		Assert.assertTrue(responseCode.contains("HTTP"));

		con.close();
		
		QAUtil.sleep(1000);

		Assert.assertEquals(0, pool.getNumActive());
	}


	

	@Test
	public void testSimple() throws Exception {
		IDataHandler dh = new IDataHandler() {
			public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
				String msg = connection.readStringByDelimiter("\r\n");
				
				connection.write(msg + "\r\n");
				if (msg.equals("close")) {
					connection.close();
				}
				return true;
			}
		};
		
		IServer server = new Server(dh);
		server.start();

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());

		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumCreated());

		con.write("test\r\n");
		QAUtil.sleep(1000);

		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		con.close();
		
		QAUtil.sleep(1000);

		Assert.assertEquals(0, pool.getNumActive());
		
		
		con = pool.getNonBlockingConnection("localhost", server.getLocalPort());

		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumCreated());

		con.write("test\r\n");
		QAUtil.sleep(1000);

		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		con.close();
		
		QAUtil.sleep(1000);

		Assert.assertEquals(0, pool.getNumActive());

		pool.close();
	}


    @Test
    public void testInvalidAddress() throws Exception {

        NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
        
        try {
            pool.getNonBlockingConnection("154.34.2.123", 565, 1000);
            Assert.fail("SocketTimeoutException expected");
        } catch (SocketTimeoutException expected) { }

        pool.close();
    }	
	

	@Test
	public void testSimpleServerSideclose() throws Exception {
		
		IDataHandler dh = new IDataHandler() {
			public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
				String msg = connection.readStringByDelimiter("\r\n");
				
				connection.write(msg + "\r\n");
				if (msg.equals("close")) {
					connection.close();
				}
				return true;
			}
		};
		
		IServer server = new Server(dh);
		server.start();

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());

		Assert.assertTrue(pool.getNumActive() == 1);

		con.write("close\r\n");
		QAUtil.sleep(2000);

		Assert.assertEquals("close", con.readStringByDelimiter("\r\n"));
		Assert.assertEquals(0, pool.getNumActive());
	}




	@Test
	public void testReuse() throws Exception {
		
		IServer server1 = new Server(new EchoLineHandler());
		server1.start();
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		
		for (int i = 0; i < 2; i++) {
			INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
			con.write("test\r\n");
			
			QAUtil.sleep(1000);
			
			Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
			
			Assert.assertEquals(1, pool.getNumActive());
			Assert.assertEquals(0, pool.getNumIdle());

			con.close();
			
			QAUtil.sleep(1000);
			
			Assert.assertEquals(0, pool.getNumActive());
			Assert.assertEquals(1, pool.getNumIdle());
		}
		
		pool.close();
		server1.close();
	}



 


    @Test
    public void testMaxActive() throws Exception {
        
        final IServer server1 = new Server(new EchoLineHandler());
        server1.start();
        
        final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
        pool.setMaxActive(2);
        
        INonBlockingConnection con1 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        INonBlockingConnection con2 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        
        
        QAUtil.sleep(1000);


        try {
            INonBlockingConnection con3 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
            Assert.fail("MaxConnectionsExceededException expected");
        } catch (MaxConnectionsExceededException expected) { }

        pool.close();
        server1.close();
    }
    

  
    
    
    @Test
    public void testLiveMaxActivePerServer() throws Exception {
        
        final IServer server1 = new Server(new EchoLineHandler());
        server1.start();
        
        final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
        pool.setMaxActivePerServer(2);
        
        INonBlockingConnection con0 = pool.getNonBlockingConnection("www.1and1.com", 80);
        con0.close();  // will be returned into idle pool
        
        INonBlockingConnection con1 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        INonBlockingConnection con2 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        
        try {
            INonBlockingConnection con3 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
            Assert.fail("MaxConnectionsExceededException expected");
        } catch (MaxConnectionsExceededException expected) { }

        pool.close();
        server1.close();
    }



    @Test
    public void testAcquireTimeNull() throws Exception {
        
        final IServer server1 = new Server(new EchoLineHandler());
        server1.start();
        
        final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
        pool.setMaxActivePerServer(2);
        
        final INonBlockingConnection con1 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        INonBlockingConnection con2 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        
        new Thread() {
            
            public void run() {
                QAUtil.sleep(500);
                try {
                    con1.close();
                } catch (IOException ignore) { }
            };
        }.start();
        
        try {
            INonBlockingConnection con3 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
            Assert.fail("MaxConnectionsExceededException expected");
        } catch (MaxConnectionsExceededException expected) { }

        pool.close();
        server1.close();
    }

    
    @Test
    public void testAcquireTimeOneSecond() throws Exception {
        
        final IServer server1 = new Server(new EchoLineHandler());
        server1.start();
        
        final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
        pool.setMaxActivePerServer(2);
        pool.setAcquireTimeoutMillis(5000);
        
        final INonBlockingConnection con1 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        INonBlockingConnection con2 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        
        new Thread() {
            
            public void run() {
                QAUtil.sleep(500);
                try {
                    con1.close();
                } catch (IOException ignore) { }
            };
        }.start();
        
        INonBlockingConnection con3 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());

        pool.close();
        server1.close();
    }    


    @Test
    public void testAcquireTimeOneSecondFailed() throws Exception {
        
        final IServer server1 = new Server(new EchoLineHandler());
        server1.start();
        
        final NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
        pool.setMaxActivePerServer(2);
        pool.setAcquireTimeoutMillis(1000);
        
        final INonBlockingConnection con1 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        INonBlockingConnection con2 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
        
   
        try {
            INonBlockingConnection con3 = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
            Assert.fail("MaxConnectionsExceededException expected");
        } catch (MaxConnectionsExceededException expected) { }

        pool.close();
        server1.close();
    }    
    
    
	@Test
	public void testCloseClientSide() throws Exception {
		
		IServer server1 = new Server(new EchoLineHandler());
		server1.start();
		
		IServer server2 = new Server(new EchoLineHandler());
		server2.start();
		
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		
		for (int i = 0; i < 2; i++) {
			INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server1.getLocalPort());
			con.write("test\r\n");
			
			INonBlockingConnection con2 = pool.getNonBlockingConnection("localhost", server2.getLocalPort());
			con2.write("test\r\n");

			QAUtil.sleep(1000);
			
			Assert.assertEquals(2, pool.getNumActive());
			Assert.assertEquals(0, pool.getNumIdle());
			Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
			Assert.assertEquals("test", con2.readStringByDelimiter("\r\n"));

			con.close();
			con2.close();
			
			QAUtil.sleep(1000);
			
			Assert.assertEquals(0, pool.getNumActive());
			Assert.assertEquals(2, pool.getNumIdle());
		}
		
		pool.close();
		
		server1.close();
		server2.close();
	}


	

	@Test
	public void testCloseClientSideAndRead() throws Exception {
			
		IServer server = new Server(new EchoLineHandler());
		server.start();
		
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
	
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.write("test\r\n");
		QAUtil.sleep(1000);
		con.close();
		
		QAUtil.sleep(1000);
			
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());

		
		try {
			con.readStringByDelimiter("\r\n");
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		server.close();
	}

	

	@Test
	public void testCloseServerSideAndRead() throws Exception {
		
		IServer server = new Server(new EchoLineHandler());
		server.start();
		
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
	
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.write("test\r\n");
		QAUtil.sleep(1000);
		server.close();
		
		QAUtil.sleep(1000);
			
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());

		
		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));

		try {
			con.readStringByDelimiter("\r\n");
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

	}

	
	



	@Test
	public void testIdleTimeout() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.setIdleTimeoutMillis(1 * 1000);

		Assert.assertTrue(con.isOpen());
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());
		
		QAUtil.sleep(1500);
			
		Assert.assertFalse(con.isOpen());
		Assert.assertEquals(1, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());
		Assert.assertEquals(1, pool.getNumDestroyed());

		
		pool.close();
		server.close();
	}
	
	

	@Test
	public void testPoolClose() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		INonBlockingConnection con2 = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		INonBlockingConnection con3 = pool.getNonBlockingConnection("localhost", server.getLocalPort());

		con.write("test\r\n");
		con2.write("test\r\n");
		con3.write("test\r\n");

		QAUtil.sleep(1000);
		
		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		Assert.assertEquals("test", con2.readStringByDelimiter("\r\n"));
		Assert.assertEquals("test", con3.readStringByDelimiter("\r\n"));
		
		con.close();

		QAUtil.sleep(1000);
		
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(2, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumIdle());
		Assert.assertEquals(0, pool.getNumDestroyed());
		
		pool.close();
		QAUtil.sleep(1000);

		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(0, pool.getNumIdle());
		Assert.assertEquals(1, pool.getNumDestroyed());

		
		server.close();
	}
	
	

	@Test
	public void testPoolDestroy() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		INonBlockingConnection con2 = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		INonBlockingConnection con3 = pool.getNonBlockingConnection("localhost", server.getLocalPort());

		con.write("test\r\n");
		con2.write("test\r\n");
		con3.write("test\r\n");

		QAUtil.sleep(1000);
		
		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		Assert.assertEquals("test", con2.readStringByDelimiter("\r\n"));
		Assert.assertEquals("test", con3.readStringByDelimiter("\r\n"));
		
		con.close();

		QAUtil.sleep(1000);
		
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(2, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumIdle());
		Assert.assertEquals(0, pool.getNumDestroyed());
		
		pool.destroy();
		QAUtil.sleep(1000);

		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(0, pool.getNumIdle());
		Assert.assertEquals(3, pool.getNumDestroyed());

		
		server.close();
	}
	

	@Test
	public void testPoolIdleTimeout() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool(null, 200);
		pool.setPooledMaxIdleTimeMillis(1 * 1000);

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.write("test\r\n");
		
		QAUtil.sleep(1000);
		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		con.close();

		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumIdle());
		
		QAUtil.sleep(2000);
			
		Assert.assertFalse(con.isOpen());
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(1, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());
		
		pool.close();
		server.close();
	}
	

	
	@Test
	public void testPoolLifetimeTimeout() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool(null, 200);
		pool.setPooledMaxLifeTimeMillis(2 * 1000);

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.write("test\r\n");
		
		QAUtil.sleep(1000);
		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));

		
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());
		
		con.close();
		QAUtil.sleep(3000);
			
		Assert.assertFalse(con.isOpen());
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(1, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumDestroyed());
		Assert.assertEquals(0, pool.getNumIdle());

		
		pool.close();
		server.close();
	}
	


	@Test
	public void testConnectionTimeout() throws Exception {
		IServer server = new Server(new EchoHandler());
		server.start();
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.setConnectionTimeoutMillis(1 * 1000);

		Assert.assertTrue(con.isOpen());
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumConnectionTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());
		
		QAUtil.sleep(2000);
			
		Assert.assertFalse(con.isOpen());
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(1, pool.getNumConnectionTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(1, pool.getNumDestroyed());
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());

		
		pool.close();
		server.close();
	}


	
	@Test
	public void testServerSideClose() throws Exception {
	    System.out.println("testServerSideClose");
	    
	    System.out.println("start server");
		Server server = new Server(new EchoHandler());
		server.start();
		
	    System.out.println("create pool");
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

	    System.out.println("get a connection");
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());

		Assert.assertTrue(con.isOpen());
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumConnectionTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());
		
		System.out.println("close server");
		server.close();
		
		QAUtil.sleep(2000);
		
		System.out.println("read byte");
		try {
		    con.readByte();
		    Assert.fail("ClosedChannelException expected");
		} catch (Exception expected) { };
		
		
		System.out.println("PASSED expected exception occured");
		QAUtil.sleep(2000);
		
		
		System.out.println(pool);
		
		Assert.assertEquals("0 idle timeout expected", 0, pool.getNumIdleTimeout());
		Assert.assertEquals("1 destroyed expected", 1, pool.getNumDestroyed());
		Assert.assertEquals("0 connection timeout expected", 0, pool.getNumConnectionTimeout());
		Assert.assertEquals("0 pool idle timeout expected", 0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals("0 pool life timeout expected", 0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals("0 num active expected", 0, pool.getNumActive());
		Assert.assertEquals("0 idle expected", 0, pool.getNumIdle());
        Assert.assertEquals("0 open connections expected", 0, server.getOpenConnections().size());
		
		
        System.out.println("closing");
		pool.close();
	}

	
	@Test
	public void testDestroy() throws Exception {
		IServer server = new Server(new EchoHandler());
		server.start();
		
		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());

		con.write("test\r\n");
		
		QAUtil.sleep(1000);
		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));

		Assert.assertTrue(con.isOpen());
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumConnectionTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());
		
		NonBlockingConnectionPool.destroy(con);
		
		QAUtil.sleep(1000);
			
		Assert.assertEquals(0, pool.getNumIdleTimeout());
		Assert.assertEquals(1, pool.getNumDestroyed());
		Assert.assertEquals(0, pool.getNumConnectionTimeout());
		Assert.assertEquals(0, pool.getNumPoolIdleTimeout());
		Assert.assertEquals(0, pool.getNumPoolLifetimeTimeout());
		Assert.assertEquals(0, pool.getNumActive());
		Assert.assertEquals(0, pool.getNumIdle());

		
		pool.close();
	}
	






	@Test
	public void testNonHandler() throws Exception {

		IServer server = new org.xsocket.connection.Server(0, new EchoHandler());
		server.start();

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort());
		con.write("test\r\n");

		QAUtil.sleep(1000);

		con.close();
		pool.close();
		server.close();
	}





	@Test
	public void testNonThreadedHandler() throws Exception {

		IServer server = new org.xsocket.connection.Server(0, new EchoHandler());
		server.start();

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		NonThreadedClientHandler hdl = new NonThreadedClientHandler();
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort(), hdl);
		con.write("test\r\n");

		QAUtil.sleep(2000);

		Assert.assertTrue(hdl.getThreadname().startsWith("xDispatcherClientGlb#"));

		con.close();
		pool.close();
		server.close();
	}



	@Test
	public void testThreadedHandler() throws Exception {

		IServer server = new org.xsocket.connection.Server(0, new EchoHandler());
		server.start();

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

		ClientHandler hdl = new ClientHandler();
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort(), hdl);
		con.write("test\r\n");

		QAUtil.sleep(2000);

		Assert.assertTrue(hdl.getThreadname().startsWith("xNbcPo"));

		con.close();
		pool.close();
		server.close();
	}
	
	
	

    @Test
    public void testConnectTimeout() throws Exception {
        
        NonBlockingConnectionPool pool = new NonBlockingConnectionPool();

        try {
            pool.getNonBlockingConnection("localhost", 55332, 500);
            Assert.fail("Exception expected");
        } catch (Exception expected) {  }


        pool.close();
    }

	


	@Execution(Execution.MULTITHREADED)
	private static final class ClientHandler implements IDataHandler {

		private String threadName = null;

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();

			connection.readByteBufferByLength(connection.available());
			return true;
		}
		
		
		String getThreadname() {
			return threadName;
		}
	}


	@Execution(Execution.NONTHREADED)
	private static final class NonThreadedClientHandler implements IDataHandler {

		private String threadName = null;

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();

			connection.readByteBufferByLength(connection.available());
			return true;
		}
		

		String getThreadname() {
			return threadName;
		}
	}


	private static final class EchoHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}
	}

	
	
	private static final class EchoLineHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
			connection.write(connection.readStringByDelimiter("\r\n") + "\r\n");
			return true;
		}
		
	}
	

	private static class MyServer implements Runnable {
		private ExecutorService executorService = Executors.newCachedThreadPool();
		private volatile boolean isRunning = true;

		private ServerSocket sso = null;

		MyServer() throws IOException {
			sso = new ServerSocket(0);
		}

		public void run()  {
			while (isRunning) {
				try {
					Socket s = sso.accept();
					executorService.execute(new Worker(s));
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

	    Worker(Socket s) throws IOException {
	      this.s = s;
	      in = new LineNumberReader(new InputStreamReader(s.getInputStream()));
	      out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));
	    }

	    public void run() {
	    	while (isRunning) {
	    		try {
	    			String request = in.readLine();
	    			if (request != null) {
	    				if (request.equals(QUIT)) {
	    					isRunning = false;
	    				} else {
			    			out.write("OK" + DELIMITER);
			    			out.flush();
			    			//LOG.info("Server sending..");
	    				}
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
