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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
public final class LimitedReadTest {
	
	private static final int PORT = 9651;

	

	@Test 
	public void testManuellSuspendAndResume() throws Exception {
	
		ServerHandler hdl = new ServerHandler();
		Server server = new Server(hdl);
		server.start();
		
		
		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.write(QAUtil.generateByteArray(850));
		System.out.println("850 bytes written");
		
		QAUtil.sleep(1000);
		Assert.assertTrue(hdl.getMaxAvailable() < 900);
		
		connection.write(QAUtil.generateByteArray(850));
		System.out.println("850 bytes written");

		QAUtil.sleep(1000);
		Assert.assertTrue(hdl.getMaxAvailable() < 900);

		QAUtil.sleep(3000);
		Assert.assertTrue(hdl.getMaxAvailable() < 900);
		
		connection.close();
		server.close();
		
	}


	@Test 
	public void testMaxReadBufferSize() throws Exception {
		
		ClassicServer server = new ClassicServer();
		Thread t = new Thread(server);
		t.setDaemon(true);
		t.start();

		QAUtil.sleep(200);
		
		NonBlockingConnection nbc = new NonBlockingConnection("localhost", PORT, new ClientHandler());
		nbc.setMaxReadBufferThreshold(4096);
		
		System.out.println("socket rcvbuf: " + nbc.getOption(IConnection.SO_RCVBUF));
		
		QAUtil.sleep(2000);	
		server.stop();
	}

	
	private static final class ClientHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int available = connection.available();
		//	System.out.println("onData available = " + available);
			byte[] data = connection.readBytesByLength(available);
			 
			return true;
		}
	}
	
	
	private static final class ClassicServer implements Runnable {

		private boolean isOpen = true;
		
		public void run() {
			try {
				ServerSocket ssck = new ServerSocket(PORT);
				Socket sck = ssck.accept();
				
				QAUtil.sleep(300);
				
				OutputStream out = new BufferedOutputStream(sck.getOutputStream());
				for (int i = 0; i < 100000000; i++) {
					if (!isOpen) {
						break;
					}
					
					String s = "Message " + i + "\r\n";
					
					// 1.write out a string
					out.write(s.getBytes());
	
					// 2.write out bytes length
					ByteBuffer buffer = ByteBuffer.allocate(4);
					buffer.putInt(i);
					buffer.rewind();
					out.write(buffer.getInt());
					
					// 3.write out bytes
					byte[] data = new byte[i];
					out.write(data);
					out.flush();
					
					//try {
					//	Thread.sleep(1000);
					//} catch (InterruptedException ignore) { }
				}
				
				sck.close();
				ssck.close();
				
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		
		
		void stop() throws IOException {
			isOpen = false;
		}
	}
			 
	private static final class ServerHandler implements IConnectHandler, IDisconnectHandler, IDataHandler {
		
		private int maxAvailable = 0;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.setAttachment(new Processor(connection));
			
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			int available = connection.available();
			
			if (available > maxAvailable) {
				maxAvailable = available;
			}
			
			// suspend if to many bytes
			if (available > 800) {
				connection.suspendReceiving();
				System.out.println("read suspended");
			}

			Processor processor = (Processor) connection.getAttachment();
			processor.process(connection.readByteBufferByLength(available));
			
			return true;
		}

		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			Processor processor = (Processor) connection.getAttachment();
			processor.destroy();
			return true;
		}
		

		int getMaxAvailable() {
			return maxAvailable;
		}

	}	
	
	
	private static final class Processor {
		
		private final List<ByteBuffer> bufs = new ArrayList<ByteBuffer>();
		private volatile boolean isOpen = true;

		private int processed = 0;
		
		
		
		public Processor(final INonBlockingConnection connection) {

			
			Thread worker = new Thread() {
				public void run() {

					while (isOpen) {

						ByteBuffer buffer = null;
						synchronized (bufs) {
							if (!bufs.isEmpty()) {
								buffer = bufs.remove(0);
							} else {
								continue;
							}
						}

						// simulate processing
						try {
							Thread.sleep(buffer.remaining());
							processed += buffer.remaining();
						} catch (InterruptedException ignore) { }
						System.out.print(".");
						
						int size = 0;
						synchronized (bufs) {
							for (ByteBuffer buf : bufs) {
								size += buf.remaining();
							}	
						}
						
						if (size < 1024) {
							try {
								connection.resumeReceiving();
								System.out.println("read resumed");
							} catch (IOException ioe) {
								ioe.toString();
							}
						}
					}
				}
			};
		
			worker.setDaemon(true);
			worker.start();			
		}
		
		
		void destroy() {
			System.out.println("closing processor (processed=" + processed + ")");
			isOpen = false;
		}
		
		void process(ByteBuffer[] buffers) {
			synchronized (bufs) {
				bufs.addAll(Arrays.asList(buffers));
			}
		}
		
		
	}
}
