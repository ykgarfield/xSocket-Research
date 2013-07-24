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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;


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
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class MarkAndResetTest {
	
	private static final Logger LOG = Logger.getLogger(MarkAndResetTest.class.getName());
	

	private AtomicInteger running = new AtomicInteger(0);
	private int handled = 0;


	@Test 
	public void testSimple() throws Exception {
		System.out.println("test simple");
		IServer server = new Server(new Handler2());
		server.start();
		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		
		// wait, until data should be have received
		QAUtil.sleep(1000);
		
		String requestLine = connection.readStringByDelimiter("\r\n");
		connection.markReadPosition();
		
		connection.close();
		server.close();
		
	}

	
	
	@Test 
	public void testReadLengthField() throws Exception {
		System.out.println("test read length field");
		IServer server = new Server(new ReadServerHandler());
		ConnectionUtils.start(server);
	
		
		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		
		byte[] request = QAUtil.generateByteArray(20);
		
		bc.write((int) (request.length));
		
		QAUtil.sleep(500);
		
		bc.write(request);
		
		byte[] response = bc.readBytesByLength(request.length);
		Assert.assertTrue(QAUtil.isEquals(request, response));
	
		
		bc.close();
		server.close();
	}


	
	@Test 
	public void testReadSplittedLengthField() throws Exception {
		System.out.println("test read splitted length field");
		
		final IServer server = new Server(new ReadServerHandler());
		server.setWorkerpool(Executors.newFixedThreadPool(4));
		ConnectionUtils.start(server);
	
		int countThread = 1;
		final int countLoops = 100;

		for (int j = 0; j < countThread ; j++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					try {
						IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
	
						byte[] request = QAUtil.generateByteArray(20);
	
						for (int i = 0; i < countLoops; i++) {
					
							ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
							lengthBuffer.putInt(request.length);
							lengthBuffer.clear();
							
							// send length field byte by byte
							while (lengthBuffer.hasRemaining()) {
								bc.write(lengthBuffer.get());
							}
							
							bc.write(request);
							
							byte[] response = bc.readBytesByLength(request.length);
							Assert.assertTrue(QAUtil.isEquals(request, response));
							
							System.out.print(".");
							
							handled++;
						}
					
						
						bc.close();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						running.decrementAndGet();
					}
				}
			};
			
			running.incrementAndGet();
			t.start();
			
		}
		
	
		while(running.get() > 0) {
			QAUtil.sleep(200);
		}
		
		Assert.assertTrue(handled == (countLoops * countThread));
		
		server.close();
	}

	
	
	
	@Test 
	public void testReadMulti() throws Exception {
		System.out.println("test read multi");
		
		MultiServerHandler handler = new MultiServerHandler();
		final IServer server = new Server(handler);
		ConnectionUtils.start(server);
		
		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		
		bc.write((int) 45);
		QAUtil.sleep(500);
		
		bc.write((int) 77);
		QAUtil.sleep(500);

		bc.write((int) 99);
		QAUtil.sleep(500);

		bc.write((int) 43);
		
		Assert.assertTrue(handler.errorOccured == false);
		
		bc.close();
		server.close();
	}
	
	
	@Test 
	public void testReadMulti2() throws Exception {
		System.out.println("test read multi2");
		
		MultiServerHandler2 handler = new MultiServerHandler2();
		final IServer server = new Server(handler);
		server.start();
		
		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		
		bc.write((byte) 1);
		QAUtil.sleep(500);
		
		bc.write((int) 2);
		QAUtil.sleep(500);

		bc.write((int) 979);
		QAUtil.sleep(500);

		bc.write((int) 20);


		
		bc.write("12345678901234567890");

		String status = bc.readStringByDelimiter("\r\n");
		Assert.assertEquals("ACCEPTED", status);
		
		bc.close();
		server.close();
	}

	
	
	@Test 
	public void testMarkAndAutoflush() throws Exception {
		System.out.println("test mark and autoflush");
		
		MultiServerHandler handler = new MultiServerHandler();
		final IServer server = new Server(handler);
		ConnectionUtils.start(server);
		
		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		Assert.assertTrue(bc.isAutoflush());
		
		bc.write((int) 45);
		try {
			bc.markWritePosition();
			Assert.fail("an exception should haven been thrown, because write mark is only supported for autoflush off");
		} catch (RuntimeException expected) { }
		
		bc.close();
		server.close();
	}

	
	
	
	@Test 
	public void testWrite() throws Exception {		
		System.out.println("test write");
		WriteServerHandler handler = new WriteServerHandler();
		final IServer server = new Server(handler);
		ConnectionUtils.start(server);
		
		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		bc.setAutoflush(false);
		
		// magic head
		bc.write((int) 88);
		
		// mark written position
		bc.markWritePosition();
		
		
		// write garbage
		bc.write(45.56);
		bc.write((byte) 3);
		
		// jump back
		bc.resetToWriteMark();
		
		
		// "true" write
		int written = 0;
		
		// write "empty" length field
		bc.write((int) 0); 
		
		// write content 
		written += bc.write((byte) 5);
		written += bc.write(4354353L);
		written += bc.write(34);
		
		// update length filed
		bc.resetToWriteMark();
		bc.write((int) written); 
		
		// lets go
		bc.flush();
		
		
		// check response
		Assert.assertTrue(bc.readByte() == (byte) 5);
		Assert.assertTrue(bc.readLong() == 4354353L);
		Assert.assertTrue(bc.readInt() == 34);
		
		
		bc.close();
		server.close();
	}
	
	
	
	
	private static final class WriteServerHandler implements IConnectHandler, IDataHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.resetToReadMark();
			connection.markReadPosition();
			
			int i = connection.readInt();
			int length = connection.readInt();
			
			if (connection.available() >= length) {
				connection.removeReadMark();
				
				connection.write(connection.readByteBufferByLength(length));
			} 
			
			connection.flush();
			return true;
		}
	}
	
	
	private static final class MultiServerHandler implements IDataHandler {
		
		boolean errorOccured = false;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			connection.resetToReadMark();
			connection.markReadPosition();
			
			int i = connection.readInt();
			if (i != 45) {
				System.out.println("error on i (is " + i + " not 45)");
				errorOccured = true;
			}
			
			int i2 = connection.readInt();
			if (i2 != 77) {
				System.out.println("error on i2 (is " + i2 + " not 77)");
				errorOccured = true;
			}
			
			int i3 = connection.readInt();
			if (i3 != 99) {
				System.out.println("error on i3  (is " + i3 + " not 99)");
				errorOccured = true;
			}

			
			int i4 = connection.readInt();
			if (i4 != 43) {
				System.out.println("error on i4");
				errorOccured = true;
			}
		
			return false;
		}
	}
	
	
	private static final class MultiServerHandler2 implements IDataHandler {
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			
			byte type = -1;;
			int version = -1;
			int signature = -1;
			int dataLength = 0;
			
			
			
			////////////
			// "transaction" start
			//
			
			// mark read position
			connection.markReadPosition();
			
			try {
				type = connection.readByte();
				version = connection.readInt();
				signature = connection.readInt();
				dataLength = connection.readInt();

				connection.removeReadMark();
				
			} catch (BufferUnderflowException bue) {
				
				connection.resetToReadMark();
				return true;
			}
			
			//	
			// "transaction" end
			///////////////
			
			
			if (type == 1) {
				connection.setHandler(new ContentHandler(dataLength, signature));
				
			} else {
				//...
			}
			
			return true;
		}
	}
	
	
	private static final class ContentHandler implements IDataHandler {
		
		private int remaining = 0;

		public ContentHandler(int dataLength, int signature) {
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
	
	
	
	private static final class ReadServerHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			//connection.setFlushmode(FlushMode.ASYNC);
			
			connection.resetToReadMark();  // reset marked by previous reads with BufferUnderflowExceptions
			
			// read the length field
			connection.markReadPosition();
			int length = connection.readInt();  // BufferUnderflowException will not be handled 
				
			// if all data available
			if (connection.available() >= length) {
				connection.removeReadMark();
				ByteBuffer[] data = connection.readByteBufferByLength(length);
				connection.write(data);
			}

			
			return true;
		}
	}
	
	
	private static final class Handler2 implements IConnectHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
		
			connection.write("GET / HTTP/1.1\r\n");
			connection.write("Host: 192.168.1.24:15088\r\n");
			connection.write("User-Agent: Mozilla/5.0 (Windows; U; Windows NT 5.1; de; rv:1.8.1.4) Gecko/20070515 Firefox/2.0.0.4\r\n");
			connection.write("Accept: text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5\r\n");
			connection.write("Accept-Language: de-de,de;q=0.8,en-us;q=0.5,en;q=0.3\r\n");
			connection.write("Accept-Encoding: gzip,deflate\r\n");
			connection.write("Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7\r\n");
			connection.write("Keep-Alive: 300\r\n");
			connection.write("Connection: keep-alive\r\n");
			connection.write("\r\n\r\n");
			connection.flush();
			
			return true;
		}
	}
}
