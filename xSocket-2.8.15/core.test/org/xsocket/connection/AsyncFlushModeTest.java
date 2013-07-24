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


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
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
public final class AsyncFlushModeTest {

	private static final String DELIMITER = "x"; 
	

	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new ServerHandler());
		server.start();

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		
		String request = "dsfdsdsffds";
		connection.write(request + DELIMITER);
		
		String response = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals(request, response);
		
		connection.close();
		server.close();
	}
	

	@Test 
	public void testDefaultAsync() throws Exception {
		ServerHandler2 hdl = new ServerHandler2();
		IServer server = new Server(hdl);
		server.setFlushmode(FlushMode.ASYNC);
		
		server.start();

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		
		connection.write("werewr" + DELIMITER);
		
		QAUtil.sleep(1000);

		Assert.assertTrue(hdl.getFlushMode() == FlushMode.ASYNC);
		
		connection.close();
		server.close();
	}
	


	@Test 
	public void testNonBlockingClientSide() throws Exception {
		IServer server = new Server(new ServerHandler());
		server.start();

		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setFlushmode(FlushMode.ASYNC);
		
		String request = "dsfdsdsffds";
		connection.write(request + DELIMITER);
		
		QAUtil.sleep(1000);
		
		String response = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals(request, response);
		
		connection.close();
		server.close();
	}


	
	


	@Test 
	public void testReuseByteBuffer() throws Exception {
		IServer server = new Server(new ServeeHandler3());
		server.start();

		
		File file = QAUtil.createTestfile_400k();
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		ReadableByteChannel fc = raf.getChannel(); 
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		
		ByteBuffer copyBuffer = ByteBuffer.allocate(4096);

		int read = 0;
        while (read >= 0) {
        	// read channel
            read = fc.read(copyBuffer);
            copyBuffer.flip();
            
            if (read > 0) {
	            // write channel
	            connection.write(copyBuffer);
	            if (copyBuffer.hasRemaining()) {
	                copyBuffer.compact();
	            } else {
	                copyBuffer.clear();
	            }
            }
        }
		
		ByteBuffer[] buffer = connection.readByteBufferByLength((int) file.length());
		Assert.assertTrue(QAUtil.isEquals(file, buffer));

		raf.close();
		file.delete();
		connection.close();
		server.close();
	}

	
	

	
	
	
	
	
	private static class ServerHandler implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setFlushmode(FlushMode.ASYNC);
			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			ByteBuffer[] data = connection.readByteBufferByDelimiter(DELIMITER);
			connection.write(data);
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
	}
	
	
	private static class ServerHandler2 implements IDataHandler {
		
		private FlushMode flushMode = null;
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			flushMode = connection.getFlushmode();
			return true;
		}
		
		public FlushMode getFlushMode() {
			return flushMode;
		}
	}
	
	
	private static final class ServeeHandler3 implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
			
			ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
			connection.write(data);
			
			return true;
		}
		
		
	}
}
