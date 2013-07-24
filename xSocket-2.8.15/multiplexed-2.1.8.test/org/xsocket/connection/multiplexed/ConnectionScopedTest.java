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
import java.nio.BufferUnderflowException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectionScoped;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.multiplexed.IBlockingPipeline;
import org.xsocket.connection.multiplexed.IMultiplexedConnection;
import org.xsocket.connection.multiplexed.INonBlockingPipeline;
import org.xsocket.connection.multiplexed.IPipelineDataHandler;
import org.xsocket.connection.multiplexed.MultiplexedConnection;
import org.xsocket.connection.multiplexed.MultiplexedProtocolAdapter;




/**
*
* @author grro@xsocket.org
*/
public final class ConnectionScopedTest {
	

	
	@Test 
	public void testConnectionScoped() throws Exception {
		
		IServer server = new Server(new MultiplexedProtocolAdapter(new ConnectionScopedPipelineDataHandler()));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));
		
		for (int i = 0; i < 5; i++) {
			IBlockingPipeline pipeline = connection.getBlockingPipeline(connection.createPipeline());
			for (int j = 0; j < 3; j++) {
				pipeline.write(4);
				int counter = pipeline.readInt();
				
				Assert.assertEquals(j, counter);
			}
			pipeline.close();
		}
		
		connection.close();
		server.close();
	}
	
	@Test 
	public void testInstanceScoped() throws Exception {
		
		IServer server = new Server(new MultiplexedProtocolAdapter(new InstanceScopedPipelineDataHandler()));
		ConnectionUtils.start(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));
		
		for (int i = 0; i < 5; i++) {
			IBlockingPipeline pipeline = connection.getBlockingPipeline(connection.createPipeline());
			for (int j = 0; j < 3; j++) {
				pipeline.write(4);
				int counter = pipeline.readInt();
				
				Assert.assertEquals(j + (3 * i), counter);
			}
			pipeline.close();
		}
		
		connection.close();
		server.close();
	}
	
	
	private static final class ConnectionScopedPipelineDataHandler implements IPipelineDataHandler, IConnectionScoped {
		
		private int counter = 0;
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			pipeline.readInt();
			pipeline.write(counter);
			counter++;
			return true;
		}

		
		@Override
		public Object clone() throws CloneNotSupportedException {
			ConnectionScopedPipelineDataHandler copy = (ConnectionScopedPipelineDataHandler) super.clone();
			return copy;
		}
	}

	
	private static final class InstanceScopedPipelineDataHandler implements IPipelineDataHandler {
		
		private int counter = 0;
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			pipeline.readInt();
			pipeline.write(counter);
			counter++;
			return true;
		}
	}

}