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
import org.xsocket.connection.IWriteCompletionHandler;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;
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
public final class WriteCompletionTest {
	
	
	
	@Test 
	public void testSync() throws Exception {
	    
		IServer server = new Server(new MultiplexedProtocolAdapter(new PipelineHandler()));
		server.start();
		
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		String pipelineId = connection.createPipeline();
		IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);

		
	
		
		pipeline.write("line one\r\n".getBytes());
	    pipeline.write("line two\r\n".getBytes());


		String txt = pipeline.readStringByLength(20);
		
		Assert.assertEquals("line one\r\nline two\r\n", txt);
		
		
		pipeline.close();
		
		connection.close();
		server.close();
	}
	
	

    @Test 
    public void testAsyncMutlithreaded() throws Exception {
        
        IServer server = new Server(new MultiplexedProtocolAdapter(new PipelineHandler()));
        server.start();
        
        
        IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
        String pipelineId = connection.createPipeline();
        final IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);

        pipeline.setFlushmode(FlushMode.ASYNC);
    
        
        WriteCompletionHandler completionHandler = new WriteCompletionHandler(pipeline);
        pipeline.write("line one\r\n".getBytes(), completionHandler);
        
        QAUtil.sleep(500);
        Assert.assertTrue(completionHandler.getThreadname().startsWith("xNbcPool"));
        
        pipeline.write("line two\r\n".getBytes());


        
        
        String txt = pipeline.readStringByLength(20);
        
        Assert.assertEquals("line one\r\nline two\r\n", txt);
        
        
        pipeline.close();
        
        connection.close();
        server.close();
    }

    
    
    @Test 
    public void testAsyncNonthreaded() throws Exception {
        
        IServer server = new Server(new MultiplexedProtocolAdapter(new PipelineHandler()));
        server.start();
        
        
        IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
        String pipelineId = connection.createPipeline();
        final IBlockingPipeline pipeline = connection.getBlockingPipeline(pipelineId);

        pipeline.setFlushmode(FlushMode.ASYNC);
    
        
        NonThreadedWriteCompletionHandler completionHandler = new NonThreadedWriteCompletionHandler(pipeline);
        pipeline.write("line one\r\n".getBytes(), completionHandler);
        
        QAUtil.sleep(500);
        Assert.assertTrue(completionHandler.getThreadname().startsWith("xDispatcher"));
        
        pipeline.write("line two\r\n".getBytes());


        
        
        String txt = pipeline.readStringByLength(20);
        
        Assert.assertEquals("line one\r\nline two\r\n", txt);
        
        
        pipeline.close();
        
        connection.close();
        server.close();
    }
    
    
    private static class WriteCompletionHandler implements IWriteCompletionHandler {
        
        private final IBlockingPipeline pipeline;
        private String threadname = null;
        
        
        public WriteCompletionHandler(IBlockingPipeline pipeline) {
            this.pipeline = pipeline;
        }
        
        
        public void onWritten(int written) throws IOException {
            threadname = Thread.currentThread().getName();
            pipeline.write("line two\r\n".getBytes());
        }
        
        public void onException(IOException ioe) {
            threadname = Thread.currentThread().getName();
        }
        
     
        String getThreadname() {
            return threadname;
        }        
    }
    
    
    @Execution(Execution.NONTHREADED)
    private static class NonThreadedWriteCompletionHandler implements IWriteCompletionHandler {
        
        private final IBlockingPipeline pipeline;
        private String threadname = null;
        
        
        public NonThreadedWriteCompletionHandler(IBlockingPipeline pipeline) {
            this.pipeline = pipeline;
        }
        
        
        public void onWritten(int written) throws IOException {
            threadname = Thread.currentThread().getName();
            pipeline.write("line two\r\n".getBytes());
        }
        
        public void onException(IOException ioe) {
            threadname = Thread.currentThread().getName();
        }
        
     
        String getThreadname() {
            return threadname;
        }        
    }
	
	private static final class PipelineHandler implements IPipelineDataHandler {
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		    pipeline.write(pipeline.readByteBufferByLength(pipeline.available()));
			return true;
		}
	}	
}