/*
 *  Copyright (c) xsocket.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection.multiplexed;




import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.util.concurrent.atomic.AtomicBoolean;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.multiplexed.IMultiplexedConnection;
import org.xsocket.connection.multiplexed.MultiplexedConnection;
import org.xsocket.connection.multiplexed.MultiplexedProtocolAdapter;




/**
*
* @author grro@xsocket.org
*/
public final class CloseTest {
	

	
	@Test 
	public void testIdleTimeoutBlockingConnection() throws Exception {
		
		IServer server = new Server(new MultiplexedProtocolAdapter(new EchoHandler()));
		server.start();
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));
		
		IBlockingPipeline p1 = connection.getBlockingPipeline(connection.createPipeline());
		IBlockingPipeline p2 = connection.getBlockingPipeline(connection.createPipeline());
		IBlockingPipeline p3 = connection.getBlockingPipeline(connection.createPipeline());

		Assert.assertEquals(3, connection.listOpenPipelines().length);
		connection.close();
		
		QAUtil.sleep(100);
		
		Assert.assertEquals(0, connection.listOpenPipelines().length);
		Assert.assertFalse(p1.isOpen());
		Assert.assertFalse(p2.isOpen());
		Assert.assertFalse(p3.isOpen());
		
		
		server.close();
	}
	
	
	
	@Test 
    public void testOnDisconnectClient() throws Exception {
        
        IServer server = new Server(new MultiplexedProtocolAdapter(new EchoHandler()));
        server.start();
        
        IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));
        
        INonBlockingPipeline p1 = connection.getNonBlockingPipeline(connection.createPipeline());
        PipelineHandler ph = new PipelineHandler();
        p1.setHandler(ph);
        
        QAUtil.sleep(500);
        Assert.assertTrue(ph.isConnectCalled());
        
        connection.close();
        
        QAUtil.sleep(500);
        Assert.assertTrue(ph.isDisconnectCalled());
        
        
        server.close();
    }

	
    @Test 
    public void testOnDisconnectServer() throws Exception {
        
        IServer server = new Server(new MultiplexedProtocolAdapter(new EchoHandler()));
        server.start();
        
        IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));
        
        INonBlockingPipeline p1 = connection.getNonBlockingPipeline(connection.createPipeline());
        PipelineHandler ph = new PipelineHandler();
        p1.setHandler(ph);
        
        QAUtil.sleep(500);
        Assert.assertTrue(ph.isConnectCalled());
        
        server.close();
        
        QAUtil.sleep(2000);
        Assert.assertTrue(ph.isDisconnectCalled());
        
        
        server.close();
    }
	

    @Test 
    public void testOnDisconnectClient2() throws Exception {
        
        PipelineHandler ph = new PipelineHandler();
        IServer server = new Server(new MultiplexedProtocolAdapter(ph));
        server.start();
        
        IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));
        
        IBlockingPipeline p1 = connection.getBlockingPipeline(connection.createPipeline());
       
        
        QAUtil.sleep(1000);
        Assert.assertTrue(ph.isConnectCalled());
        
        connection.close();
        
        QAUtil.sleep(2000);
        Assert.assertTrue(ph.isDisconnectCalled());
        
        
        server.close();
    }
    	
    
    
    @Test 
    public void testOnDisconnectServer2() throws Exception {
        
        PipelineHandler ph = new PipelineHandler();
        IServer server = new Server(new MultiplexedProtocolAdapter(ph));
        server.start();
        
        IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));
        
        IBlockingPipeline p1 = connection.getBlockingPipeline(connection.createPipeline());
       
        
        QAUtil.sleep(1000);
        Assert.assertTrue(ph.isConnectCalled());
        
        server.close();
        
        QAUtil.sleep(2000);
        Assert.assertTrue(ph.isDisconnectCalled());
        
        
        server.close();
    }
            
	
	private static final class PipelineHandler implements IPipelineConnectHandler, IPipelineDataHandler, IPipelineDisconnectHandler {
	    
	    private final AtomicBoolean isDisconnectCalled = new AtomicBoolean(false);
	    private final AtomicBoolean isConnectCalled = new AtomicBoolean(false);
	    
	    
	    public boolean onConnect(INonBlockingPipeline pipeline) throws IOException {
	        isConnectCalled.set(true);
	        return true;
	    }
	    
	    public boolean onData(INonBlockingPipeline pipeline) throws IOException {
	        return true;
	    }
	    
	    public boolean onDisconnect(INonBlockingPipeline pipeline) throws IOException {
	        isDisconnectCalled.set(true);
	        return true;
	    }
	    
        boolean isConnectCalled() {
            return isConnectCalled.get();
        }
	    
	    boolean isDisconnectCalled() {
	        return isDisconnectCalled.get();
	    }	    
	}
}