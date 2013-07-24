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

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
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
public final class EchoExampleTest {
	
	
	@Test 
	public void testExample() throws Exception {
		
		IServer server = new Server(new MultiplexedProtocolAdapter(new EchoHandler()));
		server.start();
		
		
		// create a multiplexed connection
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		
		
		// create a pipeline to send data 
		String pipelineIdOne = connection.createPipeline();
		IBlockingPipeline pipelineOne = connection.getBlockingPipeline(pipelineIdOne);
			
		pipelineOne.write("test\r\n");
		Assert.assertTrue(pipelineOne.readStringByDelimiter("\r\n").startsWith("test (open pipelines: 1"));

		// create another pipeline to send data 
		String pipelineIdTwo = connection.createPipeline();
		IBlockingPipeline pipelineTwo = connection.getBlockingPipeline(pipelineIdTwo);

		pipelineOne.write("test\r\n");
		Assert.assertTrue(pipelineOne.readStringByDelimiter("\r\n").startsWith("test (open pipelines: 2"));

		pipelineTwo.write("test\r\n");
		Assert.assertTrue(pipelineTwo.readStringByDelimiter("\r\n").startsWith("test (open pipelines: 2"));

		
		
		// closing pipeline one
		pipelineOne.close();

		pipelineTwo.write("test\r\n");
		Assert.assertTrue(pipelineTwo.readStringByDelimiter("\r\n").startsWith("test (open pipelines: 1"));

		
		connection.close();
		server.close();
	}
	

	@Test 
    public void testExampleSSL() throws Exception {
        
        IServer server = new Server(0, new MultiplexedProtocolAdapter(new EchoHandler()), SSLTestContextFactory.getSSLContext(), true);
        server.start();
        
        
        // create a multiplexed connection
        IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort(), SSLTestContextFactory.getSSLContext(), true));
        
        
        // create a pipeline to send data 
        String pipelineIdOne = connection.createPipeline();
        IBlockingPipeline pipelineOne = connection.getBlockingPipeline(pipelineIdOne);
            
        pipelineOne.write("test\r\n");
        Assert.assertTrue(pipelineOne.readStringByDelimiter("\r\n").startsWith("test (open pipelines: 1"));

        // create another pipeline to send data 
        String pipelineIdTwo = connection.createPipeline();
        IBlockingPipeline pipelineTwo = connection.getBlockingPipeline(pipelineIdTwo);

        pipelineOne.write("test\r\n");
        Assert.assertTrue(pipelineOne.readStringByDelimiter("\r\n").startsWith("test (open pipelines: 2"));

        pipelineTwo.write("test\r\n");
        Assert.assertTrue(pipelineTwo.readStringByDelimiter("\r\n").startsWith("test (open pipelines: 2"));

        
        
        // closing pipeline one
        pipelineOne.close();

        pipelineTwo.write("test\r\n");
        Assert.assertTrue(pipelineTwo.readStringByDelimiter("\r\n").startsWith("test (open pipelines: 1"));

        
        connection.close();
        server.close();
    }

	

	
	
	private static final class EchoHandler implements IPipelineDataHandler {
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			String txt = pipeline.readStringByDelimiter("\r\n");
			pipeline.write(txt + " (open pipelines: " + pipeline.getMultiplexedConnection().listOpenPipelines().length + ", pipelineId=" + pipeline.getId() + ")\r\n");
			
			return true;
		}
	}	
}