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
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;

 



/**
*
* @author grro@xsocket.org
*/
public final class AsyncConnectTest {


	
	
	@Test
	public void testAsyncConnect() throws Exception {

	    IServer server = new Server(new ServerHandler());
	    server.start();


	    ClientHandler clientHdl = new ClientHandler();
	    INonBlockingConnection clientCon = new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort(), clientHdl, false, 5000);

	    QAUtil.sleep(1000);
	    Assert.assertEquals(1, clientHdl.getOnConnectCalled());
	    Assert.assertEquals(0, clientHdl.getOnConnectException());
	    
	    clientCon.write("test\r\n");
	    QAUtil.sleep(1000);
	        
	    Assert.assertEquals("test", clientHdl.getRecord());
	        
	    clientCon.close();
	    server.close();
	}


    @Test
    public void testAsyncConnectTimeoutSync() throws Exception {

        ClientHandler clientHdl = new ClientHandler();
        
        try {
            new NonBlockingConnection(InetAddress.getByName("localhost"), 40222, clientHdl, true, 500);
            Assert.fail("IOException expected");
        } catch (IOException expected) { }

        QAUtil.sleep(1000);
        Assert.assertEquals(0, clientHdl.getOnConnectCalled());
        Assert.assertEquals(1, clientHdl.getOnConnectException());
        Assert.assertTrue(clientHdl.getOnConnectExceptionThread().startsWith("xNbcPool"));
    }

    
	 
	@Test
	public void testAsyncConnectTimeout() throws Exception {

	    ClientHandler clientHdl = new ClientHandler();
	    INonBlockingConnection clientCon = new NonBlockingConnection(InetAddress.getByName("localhost"), 40222, clientHdl, false, 500);

	    QAUtil.sleep(1000);
	    Assert.assertEquals(0, clientHdl.getOnConnectCalled());
	    Assert.assertEquals(1, clientHdl.getOnConnectException());
	    Assert.assertTrue(clientHdl.getOnConnectExceptionThread().startsWith("xNbcPool"));
	        
	    clientCon.close();
	}


    @Test
    public void testAsyncConnectTimeoutNonthreaded() throws Exception {

        NonthreadedClientHandler clientHdl = new NonthreadedClientHandler();
        INonBlockingConnection clientCon = new NonBlockingConnection(InetAddress.getByName("localhost"), 40222, clientHdl, false, 500);

        QAUtil.sleep(2000);
        Assert.assertEquals(0, clientHdl.getOnConnectCalled());
        Assert.assertEquals(1, clientHdl.getOnConnectException());
        Assert.assertTrue(clientHdl.getOnConnectExceptionThread().startsWith("xConnector"));
            
        clientCon.close();
    }
	
	
	

	
	
	
	private static final class ClientHandler implements IConnectHandler, IDataHandler, IConnectExceptionHandler {
	    
	    private int onConnectCalled = 0;
	    private int onConnectException = 0;
	    private String onConnectExceptionThread;
	    private String record;
	    

	    public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
	        onConnectCalled++;
	        return true;
	    }
	    
	    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
	        record = connection.readStringByDelimiter("\r\n");
	        return true;
	    }
	    
	    
	    
	    public boolean onConnectException(INonBlockingConnection connection, IOException ioe) throws IOException {
	        onConnectExceptionThread = Thread.currentThread().getName();
	        onConnectException++;
	        return true;
	    }
	    
	    String getRecord() {
	        return record; 
	    }
	    
	    
	    int getOnConnectCalled() {
	        return onConnectCalled;
	    }
	    
	    String getOnConnectExceptionThread() {
	        return onConnectExceptionThread;
	    }
	    
	    int getOnConnectException() {
	        return onConnectException;
	    }
	}
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class NonthreadedClientHandler implements IConnectHandler, IDataHandler, IConnectExceptionHandler {
        
        private int onConnectCalled = 0;
        private int onConnectException = 0;
        private String onConnectExceptionThread;
        private String record;
        

        public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
            onConnectCalled++;
            return true;
        }
        
        public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
            record = connection.readStringByDelimiter("\r\n");
            return true;
        }
        
        
        
        public boolean onConnectException(INonBlockingConnection connection, IOException ioe) throws IOException {
            onConnectExceptionThread = Thread.currentThread().getName();
            onConnectException++;
            return true;
        }
        
        String getRecord() {
            return record; 
        }
        
        
        int getOnConnectCalled() {
            return onConnectCalled;
        }
        
        String getOnConnectExceptionThread() {
            return onConnectExceptionThread;
        }
        
        int getOnConnectException() {
            return onConnectException;
        }
    }

	private static final class ServerHandler implements IConnectHandler, IDataHandler {
	    
	    
	    public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

	        return true;
	    }

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
		    connection.write(connection.readStringByDelimiter("\r\n") + "\r\n");
		    return true;
		}
	}
}
