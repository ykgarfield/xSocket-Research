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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class ReplaceHandlerTest {
	
	
	private final List<String> errors = new ArrayList<String>();
	private int running = 0;
	
	
	@Test 
	public void testServerSide() throws Exception {
		
		errors.clear();
		
		final Server server = new Server(new MultiplexedProtocolAdapter(new ServerHandlerA()));
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		
		for (int j = 0; j < 10; j++) {
			Thread t = new Thread() {
				
				@Override
				public void run() {
					running++;

					try {
						MultiplexedConnection con = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));

						String pipelineId = con.createPipeline();
						IBlockingPipeline pipeline = con.getBlockingPipeline(pipelineId);

						for (int i = 0; i < 100; i++) {
							pipeline.write("test\r\n");
							Assert.assertEquals("Atest", pipeline.readStringByDelimiter("\r\n"));
							
							pipeline.write("OneMoreTest\r\n");
							Assert.assertEquals("AOneMoreTest", pipeline.readStringByDelimiter("\r\n"));
					
							pipeline.write("And");
							QAUtil.sleep(100);
							pipeline.write("AnotherOne\r\n");
							Assert.assertEquals("AAndAnotherOne", pipeline.readStringByDelimiter("\r\n"));
					
							pipeline.write("switch\r\ntest\r\n");
							Assert.assertEquals("Btest", pipeline.readStringByDelimiter("\r\n"));
							
							pipeline.write("AnothrTest\r\n");
							Assert.assertEquals("BAnothrTest", pipeline.readStringByDelimiter("\r\n"));

							pipeline.write("switch\r\ntest\r\n");
							Assert.assertEquals("Atest", pipeline.readStringByDelimiter("\r\n"));
							System.out.print(".");
						}
						
						con.close();

						
					} catch (Exception e) {
						errors.add(e.toString());
					}
					
					running--;
				}
			};
			
			t.start();
		}
		
		
		do {
			QAUtil.sleep(200);
		} while (running > 0);
		
		Assert.assertTrue(errors.isEmpty());
		server.close();
	}
	

	

	
	@Test 
	public void testClientSideNonBlockingNonthreaded() throws Exception {
		
		IServer server = new Server(new MultiplexedProtocolAdapter(new ServerHandler()));
		server.start();

		
		
		NonThreadedClientHandler hdl = new NonThreadedClientHandler();
		MultiplexedConnection con = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()), hdl);

		String pipelineId = con.createPipeline();
		INonBlockingPipeline pipeline = con.getNonBlockingPipeline(pipelineId);
		
		pipeline.write("test\r\n");
		QAUtil.sleep(200);
		Assert.assertEquals("test", pipeline.readStringByDelimiter("\r\n"));
		Assert.assertTrue(hdl.getThreadname().startsWith("xDispatcher"));
		
		pipeline.write("OneMoreTest\r\n23");
		QAUtil.sleep(200);
		Assert.assertEquals("OneMoreTest", pipeline.readStringByDelimiter("\r\n"));
		Assert.assertTrue(hdl.getThreadname().startsWith("xDispatcher"));

		ClientHandler hdl2 = new ClientHandler();
		pipeline.setHandler(hdl2);

		pipeline.write("test2\r\n");
		QAUtil.sleep(200);
		Assert.assertEquals("23test2", pipeline.readStringByDelimiter("\r\n"));
		Assert.assertTrue(hdl2.getThreadname().startsWith("xNbcPool"));
		
		pipeline.write("OneMoreTest2\r\n");
		QAUtil.sleep(200);
		Assert.assertEquals("OneMoreTest2", pipeline.readStringByDelimiter("\r\n"));
		Assert.assertTrue(hdl2.getThreadname().startsWith("xNbcPool"));

		
		con.close();
		server.close();
	}

	
	
	@Test 
	public void testBoth() throws Exception {

		IServer server = new Server(new MultiplexedProtocolAdapter(new ProtocolHandler()));
		server.start();
	
		MultiplexedConnection con = new MultiplexedConnection(new NonBlockingConnection("localhost", server.getLocalPort()));

		String pipelineId = con.createPipeline();
		IBlockingPipeline pipeline = con.getBlockingPipeline(pipelineId);
		pipeline.setAutoflush(false);
		
		int length = 200;
		byte[] data = QAUtil.generateByteArray(length);
	
		pipeline.write((byte) 'A');           // record type
		pipeline.write("1.0.1\r\n");           // version
		pipeline.write("MBwGA1UEChM...\r\n");  // signature
		pipeline.write(length);                // data length
		pipeline.flush();
	
		pipeline.write(data);                  // data
		pipeline.flush();
		
		String status = pipeline.readStringByDelimiter("\r\n");
		Assert.assertEquals("ACCEPTED", status);
		
		pipeline.close();
		server.close();
		
	}

	
	private static final class ProtocolHandler implements IPipelineDataHandler {
	
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

		      // reset to read position (if former reads failed). Will be ignored 
		      // if no read mark is set
			  pipeline.resetToReadMark();

		      // mark the read position
			  pipeline.markReadPosition();

		      // try to read the header data (BufferUnderflowException can
		      // been thrown by any read method)
		      byte recordType = pipeline.readByte();
		      String version = pipeline.readStringByDelimiter("\r\n");
		      String signature = pipeline.readStringByDelimiter("\r\n");
		      int dataLength = pipeline.readInt();

		      // got the complete header (BufferUnderflowException hasn't
		      // been thrown) -> remove read mark
		      pipeline.removeReadMark();


		      // replace the handler
		      pipeline.setHandler(new ContentHandler(dataLength, signature));

		      return true;
		}
	}
	
	

	private static final class ContentHandler implements IDataHandler {

		private int remaining = 0;

		public ContentHandler(int dataLength, String signature) {
			remaining = dataLength;
			//...
		}

		public boolean onData(INonBlockingConnection nbc) throws IOException {

		      int available = nbc.available();

		      int lengthToRead = remaining;
		      if (available < remaining) {
		         lengthToRead = available;
		      }

		      ByteBuffer[] buffers = nbc.readByteBufferByLength(lengthToRead);
		      remaining -= lengthToRead;

		      // processing the data
		      // ...

		      if (remaining == 0) {
		         nbc.setAttachment(null);
		         nbc.write("ACCEPTED\r\n");
		      }

		      return true;
		}
	}  

	
	
	
	private static final class ClientHandler implements IPipelineDataHandler {
		
		private String threadname = null;
		
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			this.threadname = Thread.currentThread().getName();
			return true;
		}
		
		String getThreadname() {
			return threadname;
		}
	}
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class NonThreadedClientHandler implements IPipelineDataHandler {
		
		private String threadname = null;
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			this.threadname = Thread.currentThread().getName();
			return true;
		}
		
		String getThreadname() {
			return threadname;
		}
	}

	
	private static final class ServerHandlerA implements IPipelineDataHandler {
		

		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			String cmd = pipeline.readStringByDelimiter("\r\n");
			if (cmd.equals("switch")) {
				pipeline.setHandler(new ServerHandlerB());
				
			} else {
				pipeline.write("A" + cmd + "\r\n");
			}
			
			return true;
		}
	}
	
	
	private static final class ServerHandlerB implements IPipelineDataHandler {

		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			String cmd = pipeline.readStringByDelimiter("\r\n");
			if (cmd.equals("switch")) {
				pipeline.setHandler(new ServerHandlerA());
				
			} else {
				pipeline.write("B" + cmd + "\r\n");
			}
			
			return true;
		}
	}	
	
	
	private static final class ServerHandler implements IPipelineDataHandler {
		
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			pipeline.write(pipeline.readByteBufferByLength(pipeline.available()));
			return true;
		}
	}
}
	
