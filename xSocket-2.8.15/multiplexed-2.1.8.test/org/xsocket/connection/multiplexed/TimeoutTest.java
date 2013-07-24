// $Id: DisconnectTest.java 1432 2007-07-03 17:15:47Z grro $
/*
 *  Copyright (c) xsocket.org, 2006. All rights reserved.
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
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.multiplexed.IBlockingPipeline;
import org.xsocket.connection.multiplexed.IMultiplexedConnection;
import org.xsocket.connection.multiplexed.INonBlockingPipeline;
import org.xsocket.connection.multiplexed.IPipelineConnectHandler;
import org.xsocket.connection.multiplexed.IPipelineDataHandler;
import org.xsocket.connection.multiplexed.MultiplexedConnection;
import org.xsocket.connection.multiplexed.MultiplexedProtocolAdapter;




/**
*
* @author grro@xsocket.org
*/
public final class TimeoutTest {
	

	
	@Test 
	public void testIdleTimeoutBlockingConnection() throws Exception {
		
		IServer server = new Server(new MultiplexedProtocolAdapter(new EchoHandler()));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		String pipelineId = connection.createPipeline();
		IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);
		pipeline.setIdleTimeoutMillis(1 * 1000);

		QAUtil.sleep(1500);
		Assert.assertFalse(pipeline.isOpen());
		
		connection.close();
		server.close();
	}
	
	
	@Test 
	public void testConnectionTimeoutBlockingConnection() throws Exception {
		
		IServer server = new Server(new MultiplexedProtocolAdapter(new EchoHandler()));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		String pipelineId = connection.createPipeline();
		IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);
		pipeline.setConnectionTimeoutMillis(1 * 1000);

		QAUtil.sleep(1500);
		Assert.assertFalse(pipeline.isOpen());
		
		connection.close();
		server.close();
	}
	
	
	
	
	@Test 
	public void testIdleTimeoutNonBlockingConnection() throws Exception {
		
		Handler hdl = new Handler(true);
		IServer server = new Server(new MultiplexedProtocolAdapter(hdl));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		String pipelineId = connection.createPipeline();
		IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);

		QAUtil.sleep(1500);
		Assert.assertTrue(hdl.idleTimeoutCalled);
		Assert.assertFalse(hdl.connectionTimeoutCalled);
		Assert.assertFalse(pipeline.isOpen());
		
		connection.close();
		server.close();
	}
	
	
	@Test 
	public void testConnectionTimeoutNonBlockingConnection() throws Exception {
		
		Handler hdl = new Handler(false);
		IServer server = new Server(new MultiplexedProtocolAdapter(hdl));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		String pipelineId = connection.createPipeline();
		IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);

		QAUtil.sleep(1500);
		Assert.assertTrue(hdl.connectionTimeoutCalled);
		Assert.assertFalse(hdl.idleTimeoutCalled);
		Assert.assertTrue(pipeline.isOpen());
		
		connection.close();
		server.close();
	}
	
	
	
	
	

	@Test 
	public void testRepeatedIdleTimeout() throws Exception {
		
		
		IServer server = new Server(new MultiplexedProtocolAdapter(new PlainHandler()));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		String pipelineId = connection.createPipeline();
		INonBlockingConnection pipeline = connection.getNonBlockingPipeline(pipelineId);
		
		RepeatedTimeoutTestServerHandler phdl = new RepeatedTimeoutTestServerHandler();
		pipeline.setHandler(phdl);

		System.out.println("set idle timeout 1 sec");
		pipeline.setIdleTimeoutMillis(1 * 1000);
		
		QAUtil.sleep(1500);
		Assert.assertEquals(1, phdl.countIdleTimeoutOccured);
		
		System.out.println("set idle timeout 1 sec");
		pipeline.setIdleTimeoutMillis(1 * 1000);

		QAUtil.sleep(300);
		Assert.assertEquals(1, phdl.countIdleTimeoutOccured);
		
		
		QAUtil.sleep(1500);
		Assert.assertEquals(2, phdl.countIdleTimeoutOccured);
		
		
		connection.close();
		server.close();
	}
	
	
	

	@Test 
	public void testRepeatedConnectionTimeout() throws Exception {
		
		
		Handler hdl = new Handler(false);
		IServer server = new Server(new MultiplexedProtocolAdapter(hdl));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		String pipelineId = connection.createPipeline();
		INonBlockingConnection pipeline = connection.getNonBlockingPipeline(pipelineId);
		
		RepeatedTimeoutTestServerHandler phdl = new RepeatedTimeoutTestServerHandler();
		pipeline.setHandler(phdl);

		System.out.println("set connection timeout 1 sec");
		pipeline.setConnectionTimeoutMillis(1 * 1000);
		
		QAUtil.sleep(1500);
		Assert.assertEquals(1, phdl.countConnectionTimeoutOccured);
		
		System.out.println("set connection timeout 1 sec");
		pipeline.setConnectionTimeoutMillis(1 * 1000);

		QAUtil.sleep(300);
		Assert.assertEquals(1, phdl.countConnectionTimeoutOccured);
		
		
		QAUtil.sleep(1500);
		Assert.assertEquals(2, phdl.countConnectionTimeoutOccured);
		
		
		connection.close();
		server.close();
	}
	
	
	
	
	
	
	
	
	
	private static final class Handler implements IPipelineConnectHandler, IPipelineDataHandler, IPipelineIdleTimeoutHandler, IPipelineConnectionTimeoutHandler {
		
		private boolean checkIdleTimeout = true;
		
		private boolean dataCalled = false;
		private boolean idleTimeoutCalled = false;
		private boolean connectionTimeoutCalled = false;
		
		
		public Handler(boolean checkIdleTimeout) {
			this.checkIdleTimeout = checkIdleTimeout;
		}

		
		public boolean onConnect(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (checkIdleTimeout) {
				pipeline.setIdleTimeoutMillis(1 * 1000);
			} else {
				pipeline.setConnectionTimeoutMillis(1 * 1000);
			}
			return true;
		}
		
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			dataCalled = true;
			return true;
		}

		
		public boolean onConnectionTimeout(INonBlockingPipeline pipeline) throws IOException {
			connectionTimeoutCalled = true;
			return true;
		}

		public boolean onIdleTimeout(INonBlockingPipeline pipeline) throws IOException {
			idleTimeoutCalled = true;
			return false;		
		}
	}
	
	
	private static final class PlainHandler implements IPipelineDataHandler {
			
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return true;
		}
	}
	
	
	@Execution(Execution.NONTHREADED)
	private static final class RepeatedTimeoutTestServerHandler implements IIdleTimeoutHandler, IConnectionTimeoutHandler {
		
		private int countConnectionTimeoutOccured = 0;
		private int countIdleTimeoutOccured = 0;
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("con time out");
			
			countConnectionTimeoutOccured++;
			return true;
		}


		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			System.out.println("idle time out");

			countIdleTimeoutOccured++;
			return true;
		}
	}
	
}