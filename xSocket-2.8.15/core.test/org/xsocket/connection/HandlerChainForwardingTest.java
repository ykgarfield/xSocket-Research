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
import java.util.concurrent.atomic.AtomicBoolean;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;




/**
*
* @author grro@xsocket.org
*/
public final class HandlerChainForwardingTest {


	@Test 
	public void testData() throws Exception {

	    HandlerChain chain = new HandlerChain();
	    
	    DataHandler dh1 = new DataHandler(false);
	    chain.addLast(dh1);
	    
	    DataHandler dh2 = new DataHandler(false);
	    chain.addLast(dh2);
	    
        DataHandler dh3 = new DataHandler(false);
	    chain.addLast(dh3);
	    
	    
		IServer server = new Server(chain);
		server.start();
		
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort()); 
		con.write("test\r\n");
		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		
		QAUtil.sleep(1000);
		
		Assert.assertTrue(dh1.isOnDataCalled());
		Assert.assertTrue(dh2.isOnDataCalled());
		Assert.assertTrue(dh3.isOnDataCalled());
		
		
		con.close();
		server.close();
	}


	
    @Test 
    public void testData2() throws Exception {

        HandlerChain chain = new HandlerChain();
        
        DataHandler dh1 = new DataHandler(false);
        chain.addLast(dh1);
        
        DataHandler dh2 = new DataHandler(true);
        chain.addLast(dh2);
        
        DataHandler dh3 = new DataHandler(false);
        chain.addLast(dh3);
        
        
        IServer server = new Server(chain);
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort()); 
        con.write("test\r\n");
        Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
        
        QAUtil.sleep(1000);

        
        Assert.assertTrue(dh1.isOnDataCalled());
        Assert.assertTrue(dh2.isOnDataCalled());
        Assert.assertFalse(dh3.isOnDataCalled());
        
        
        con.close();
        server.close();
    }
	
    
    @Test 
    public void testConnect() throws Exception {

        HandlerChain chain = new HandlerChain();
        
        ConnectHandler ch1 = new ConnectHandler(false);
        chain.addLast(ch1);
        
        ConnectHandler ch2 = new ConnectHandler(false);
        chain.addLast(ch2);
        
        ConnectHandler ch3 = new ConnectHandler(false);
        chain.addLast(ch3);

        
        IServer server = new Server(chain);
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort()); 

        QAUtil.sleep(1000);
        
        Assert.assertTrue(ch1.isOnConnectCalled());
        Assert.assertTrue(ch2.isOnConnectCalled());
        Assert.assertTrue(ch3.isOnConnectCalled());
        
        
        con.close();
        server.close();
    }


    @Test 
    public void testConnect2() throws Exception {

        HandlerChain chain = new HandlerChain();
        
        ConnectHandler ch1 = new ConnectHandler(false);
        chain.addLast(ch1);
        
        ConnectHandler ch2 = new ConnectHandler(true);
        chain.addLast(ch2);
        
        ConnectHandler ch3 = new ConnectHandler(false);
        chain.addLast(ch3);

        
        IServer server = new Server(chain);
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort()); 

        QAUtil.sleep(1000);
        
        Assert.assertTrue(ch1.isOnConnectCalled());
        Assert.assertTrue(ch2.isOnConnectCalled());
        Assert.assertFalse(ch3.isOnConnectCalled());
        
        
        con.close();
        server.close();
    }
    
    
    @Test 
    public void testConnectionTimeout() throws Exception {
     
        HandlerChain chain = new HandlerChain();
        
        ConnectionTimeoutHandler ch1 = new ConnectionTimeoutHandler(false);
        chain.addLast(ch1);
        
        ConnectionTimeoutHandler ch2 = new ConnectionTimeoutHandler(false);
        chain.addLast(ch2);
        
        ConnectionTimeoutHandler ch3 = new ConnectionTimeoutHandler(false);
        chain.addLast(ch3);

        
        IServer server = new Server(chain);
        server.setConnectionTimeoutMillis(100);
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

        QAUtil.sleep(1000);
        
        Assert.assertTrue(ch1.isOnConnectionTimeoutCalled());
        Assert.assertTrue(ch2.isOnConnectionTimeoutCalled());
        Assert.assertTrue(ch3.isOnConnectionTimeoutCalled());
        
        
        con.close();
        server.close();
    }

    
    @Test 
    public void testConnectionTimeout2() throws Exception {

        HandlerChain chain = new HandlerChain();
        
        ConnectionTimeoutHandler ch1 = new ConnectionTimeoutHandler(false);
        chain.addLast(ch1);
        
        ConnectionTimeoutHandler ch2 = new ConnectionTimeoutHandler(true);
        chain.addLast(ch2);
        
        ConnectionTimeoutHandler ch3 = new ConnectionTimeoutHandler(false);
        chain.addLast(ch3);

        
        IServer server = new Server(chain);
        server.setConnectionTimeoutMillis(100);
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

        QAUtil.sleep(1000);
        
        Assert.assertTrue(ch1.isOnConnectionTimeoutCalled());
        Assert.assertTrue(ch2.isOnConnectionTimeoutCalled());
        Assert.assertFalse(ch3.isOnConnectionTimeoutCalled());
        
        
        con.close();
        server.close();
    }
    
    
    
    @Test 
    public void testIdleTimeout() throws Exception {

        HandlerChain chain = new HandlerChain();
        
        IdleTimeoutHandler ih1 = new IdleTimeoutHandler(false);
        chain.addLast(ih1);
        
        IdleTimeoutHandler ih2 = new IdleTimeoutHandler(false);
        chain.addLast(ih2);
        
        IdleTimeoutHandler ih3 = new IdleTimeoutHandler(false);
        chain.addLast(ih3);

        
        IServer server = new Server(chain);
        server.setIdleTimeoutMillis(100);
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

        QAUtil.sleep(1000);
        
        Assert.assertTrue(ih1.isOnIdleTimeoutCalled());
        Assert.assertTrue(ih2.isOnIdleTimeoutCalled());
        Assert.assertTrue(ih3.isOnIdleTimeoutCalled());
        
        
        con.close();
        server.close();
    }

     
    
    @Test 
    public void testIdleTimeout2() throws Exception {
      
        HandlerChain chain = new HandlerChain();
        
        IdleTimeoutHandler ih1 = new IdleTimeoutHandler(false);
        chain.addLast(ih1);
        
        IdleTimeoutHandler ih2 = new IdleTimeoutHandler(true);
        chain.addLast(ih2);
        
        IdleTimeoutHandler ih3 = new IdleTimeoutHandler(false);
        chain.addLast(ih3);

        
        IServer server = new Server(chain);
        server.setIdleTimeoutMillis(100);
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

        QAUtil.sleep(1000);
        
        Assert.assertTrue(ih1.isOnIdleTimeoutCalled());
        Assert.assertTrue(ih2.isOnIdleTimeoutCalled());
        Assert.assertFalse(ih3.isOnIdleTimeoutCalled());
        
        
        con.close();
        server.close();
    }    
    
	private static final class DataHandler implements IDataHandler {
	    
	    private final boolean isReturningTrue;
	    private final AtomicBoolean onDataCalled = new AtomicBoolean(false);
	    
	    
	    public DataHandler(boolean isReturningTrue) {
	        this.isReturningTrue = isReturningTrue;
        }
	    
	    
	    public boolean onData(INonBlockingConnection connection) throws IOException {
	        onDataCalled.set(true);
	        
	        int available = connection.available();
	        if (available > 0) {
	            connection.write(connection.readByteBufferByLength(available));
	        }
	        
	        return isReturningTrue;
	    }
	    
	    public boolean isOnDataCalled() {
	        return onDataCalled.get();
	    }
	}
	
	
	
	private static final class ConnectHandler implements IConnectHandler {
	        
	    private final boolean isReturningTrue;
	    private final AtomicBoolean onConnectCalled = new AtomicBoolean(false);
	        
	        
	    public ConnectHandler(boolean isReturningTrue) {
	        this.isReturningTrue = isReturningTrue;
	    }
	        

	    public boolean onConnect(INonBlockingConnection connection) throws IOException {
	        onConnectCalled.set(true);
	            
	        return isReturningTrue;
	    }
	        
	    public boolean isOnConnectCalled() {
	        return onConnectCalled.get();
	    }
	}
	
	
	
    private static final class ConnectionTimeoutHandler implements IConnectionTimeoutHandler {
        
        private final boolean isReturningTrue;
        private final AtomicBoolean onConnectionTimeoutCalled = new AtomicBoolean(false);
            
            
        public ConnectionTimeoutHandler(boolean isReturningTrue) {
            this.isReturningTrue = isReturningTrue;
        }
            

        public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
            onConnectionTimeoutCalled.set(true);
            return isReturningTrue;
        }
            
        public boolean isOnConnectionTimeoutCalled() {
            return onConnectionTimeoutCalled.get();
        }
    }	
    
    private static final class IdleTimeoutHandler implements IIdleTimeoutHandler {
        
        private final boolean isReturningTrue;
        private final AtomicBoolean onIdleTimeoutCalled = new AtomicBoolean(false);
            
            
        public IdleTimeoutHandler(boolean isReturningTrue) {
            this.isReturningTrue = isReturningTrue;
        }
            
        
        public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
            onIdleTimeoutCalled.set(true);
            return isReturningTrue;
        }
            
        public boolean isOnIdleTimeoutCalled() {
            return onIdleTimeoutCalled.get();
        }
    }       
}
