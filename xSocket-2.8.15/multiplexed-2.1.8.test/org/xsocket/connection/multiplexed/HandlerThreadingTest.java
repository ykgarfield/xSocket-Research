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



import org.junit.Assert;
import org.junit.Test;

import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.multiplexed.IBlockingPipeline;
import org.xsocket.connection.multiplexed.IMultiplexedConnection;
import org.xsocket.connection.multiplexed.INonBlockingPipeline;
import org.xsocket.connection.multiplexed.IPipelineConnectHandler;
import org.xsocket.connection.multiplexed.IPipelineDataHandler;
import org.xsocket.connection.multiplexed.IPipelineDisconnectHandler;
import org.xsocket.connection.multiplexed.MultiplexedConnection;
import org.xsocket.connection.multiplexed.MultiplexedProtocolAdapter;




/**
*
* @author grro@xsocket.org
*/
public final class HandlerThreadingTest {
	

	
	@Test 
	public void testMultithreaded() throws Exception {
		//QAUtil.setLogLevel("org.xsocket.stream.multiplexed", Level.FINE);
		
		Handler hdl = new Handler();
		IServer server = new Server(new MultiplexedProtocolAdapter(hdl));
		ConnectionUtils.start(server);
		
		
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		String pipelineId = connection.createPipeline();
		QAUtil.sleep(200);
		
		Assert.assertTrue(hdl.connectedCalled);
		Assert.assertFalse(hdl.threadName.startsWith("xDispatcher"));
			
		IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);
			
		pipeline.write("Hello echo" + EchoHandler.DELIMITER);
		
		QAUtil.sleep(100);
		Assert.assertTrue(hdl.dataCalled);
		Assert.assertFalse(hdl.threadName.startsWith("xDispatcher"));
			
		pipeline.close();

		QAUtil.sleep(100);
		Assert.assertTrue(hdl.disconnectedCalled);
		Assert.assertFalse(hdl.threadName.startsWith("xDispatcher"));
		
		connection.close();
		server.close();
	}

	
	
	@Test 
	public void testNonhreaded() throws Exception {
		//QAUtil.setLogLevel("org.xsocket.stream.multiplexed", Level.FINE);
		
		NonThreadedHandler hdl = new NonThreadedHandler();
		IServer server = new Server(new MultiplexedProtocolAdapter(hdl));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		String pipelineId = connection.createPipeline();
		QAUtil.sleep(200);
		
		Assert.assertTrue(hdl.connectedCalled);
		Assert.assertTrue(hdl.threadName.startsWith("xDispatcher"));
			
		IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);
			
		pipeline.write("Hello echo" + EchoHandler.DELIMITER);
		
		QAUtil.sleep(100);
		Assert.assertTrue(hdl.dataCalled);
		Assert.assertTrue(hdl.threadName.startsWith("xDispatcher"));
			
		pipeline.close();

		QAUtil.sleep(100);
		Assert.assertTrue(hdl.disconnectedCalled);
		Assert.assertTrue(hdl.threadName.startsWith("xDispatcher"));
		
		connection.close();
		server.close();
	}
	

	
	private static final class Handler implements IPipelineConnectHandler, IPipelineDataHandler, IPipelineDisconnectHandler {
		
		private String threadName = null;
		private boolean connectedCalled = false;
		private boolean dataCalled = false;
		private boolean disconnectedCalled = false;

		public boolean onConnect(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			connectedCalled = true;
			return true;
		}

		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			dataCalled = true;
			return true;
		}

		public boolean onDisconnect(INonBlockingPipeline pipeline) throws IOException {
			threadName = Thread.currentThread().getName();			
			disconnectedCalled = true;
			return true;
		}
	}
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class NonThreadedHandler implements IPipelineConnectHandler, IPipelineDataHandler, IPipelineDisconnectHandler {
		
		private String threadName = null;
		private boolean connectedCalled = false;
		private boolean dataCalled = false;
		private boolean disconnectedCalled = false;

		public boolean onConnect(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			connectedCalled = true;
			return true;
		}

		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			threadName = Thread.currentThread().getName();
			dataCalled = true;
			return true;
		}

		public boolean onDisconnect(INonBlockingPipeline pipeline) throws IOException {
			threadName = Thread.currentThread().getName();			
			disconnectedCalled = true;
			return true;
		}
	}
}