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
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import java.util.ArrayList;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class TransferAvailableByDelimiterTest {

	private static final String DELIMITER = "\r\n\r\n\r";
	private static final String OK = "OK";


	@Test 
	public void testSimple() throws Exception {
		
		System.out.println("test simple");
		
		DataSink dataSink = new DataSink();
		IServer server = new Server(new Handler(dataSink));
		server.start();	
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		byte[] request1 = QAUtil.generateByteArray(60);
		connection.write(request1);
		connection.flush();
		QAUtil.sleep(2000);
		
		Assert.assertTrue("available data " + dataSink.getSize() + " not lager than 50", dataSink.getSize() > 50);
	
		byte[] request2 = QAUtil.generateByteArray(20);
		connection.write(request2);
		connection.flush();
		QAUtil.sleep(2000);
		
		Assert.assertTrue("available data " + dataSink.getSize() + " not lager than 70", dataSink.getSize() > 70);
		
		
		connection.write(DELIMITER);
		connection.flush();
		
		QAUtil.sleep(2000);
		
		String okResponse = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertTrue("ok response expected", okResponse.equals(OK));

		Assert.assertTrue("available data " + dataSink.getSize() + " not equals 80", dataSink.getSize() == 80);
		
		
		connection.close();
		server.close();
	}
	
	
	
	

	@Test 
	public void testFile() throws Exception {
		System.out.println("test file");
		
		File file = QAUtil.createTempfile();
		
		RandomAccessFile raf = new RandomAccessFile(file, "rw");
		FileChannel fc = raf.getChannel();
		
		IServer server = new Server(new Handler(fc));
		server.start();	
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		byte[] request = QAUtil.generateByteArray(60000);
		connection.write(request);
		connection.flush();

		connection.write(DELIMITER);
		connection.flush();
		connection.close();
		
		QAUtil.sleep(1000);
		
		Assert.assertTrue(fc.size() == 60000);

		file.delete();
		fc.close();
		raf.close();
		file.delete();
		server.close();
	}

	

	private static final class DataSink implements WritableByteChannel {
		
		private final ArrayList<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
		
		private boolean isOpen = true;
		
		public void close() throws IOException {
			isOpen = false;
		}
		
		public boolean isOpen() {
			return isOpen;
		}
		
		public int write(ByteBuffer buffer) throws IOException {
			int size = buffer.remaining();
			buffers.add(buffer.duplicate());
			
			buffer.position(buffer.limit());
			return size;
		}
		
		int getSize() {
			int size = 0;
			
			ArrayList<ByteBuffer> buffersCopy = (ArrayList<ByteBuffer>) buffers.clone();
			for (ByteBuffer buffer : buffersCopy) {
				size += buffer.remaining();
			}
			
			return size;
		}
		
		ByteBuffer[] getReceiveBuffers() {
			return buffers.toArray(new ByteBuffer[buffers.size()]);
		}
	}


	private static final class Handler implements IDataHandler {
		
		private WritableByteChannel dataSink = null;
		
		public Handler(WritableByteChannel dataSink) {
			this.dataSink = dataSink;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			int available = connection.available();
			
			int i = connection.indexOf(DELIMITER);
			if (i < 0) {
				connection.transferTo(dataSink, available);
				
			} else {
				connection.transferTo(dataSink, i);
				connection.readStringByLength(DELIMITER.length()); // remove delimiter
				
				connection.write(OK);
				connection.write(DELIMITER);
			}
			
			return true;
		}
	}
}
