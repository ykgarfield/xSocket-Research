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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.xsocket.QAUtil;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class ChannelWriteTest {

	
	@Test 
	public void testSuccess() throws Exception {
		// start the server 
		Server server = new Server(0);		
		int port = server.getLocalPort();
		server.start();
		
		QAUtil.sleep(500);

		
		// start the client
		INonBlockingConnection connection = new NonBlockingConnection("127.0.0.1", port);
		Assert.assertTrue(connection.isAutoflush() ==  true);
		Assert.assertTrue(connection.getFlushmode() ==  FlushMode.SYNC);
		Sender sender = new Sender(connection);
		new Thread(sender).start();

		QAUtil.sleep(1000);
		
		Assert.assertEquals(server.getReceivedBytes(), (Sender.COUNT_ARRAYS * Sender.SIZE_ARRAY));
		
		connection.close();
		server.close();
	}

	
	

	private final static class Sender implements Runnable {
		
		private static final int COUNT_ARRAYS = 100;
		private static final int SIZE_ARRAY = 4096;
		
		private WritableByteChannel outputChannel = null;
		
		Sender(WritableByteChannel outputChannel) {
			this.outputChannel = outputChannel;
		}
		
		public void run() {
			try {
				// prepare the source data and the transfer buffer 
				ByteBuffer[] sourceBuffers = QAUtil.generateDirectByteBufferArray(COUNT_ARRAYS, SIZE_ARRAY);
				ByteBuffer transferBuffer = ByteBuffer.allocateDirect(SIZE_ARRAY);
				
				// iterate by writing a single source ByteBuffer into the outputChannel
				for (ByteBuffer sourceBuffer : sourceBuffers) {
				
					// put the source buffer into the transfer buffer
					transferBuffer.put(sourceBuffer);
					transferBuffer.flip();
					
					// write transfer buffer into the outputChannel
					while (transferBuffer.hasRemaining()) {
						outputChannel.write(transferBuffer);
					}
					
					// clear the transfer buffer
					transferBuffer.clear();
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}
	
	
	
	private static final class Server {
		
		private ServerSocket ssocket = null;
		private volatile boolean isRunning = true;
		
		private Thread server = null;
	
		private List<Worker> workers = new ArrayList<Worker>();
		
        private Server(int port) throws IOException {
            ssocket = new ServerSocket(port);
        }   
    		
        
        int getLocalPort() {
        	return ssocket.getLocalPort();
        }
		
        void start() {
        	server = new Thread("server") {
        		@Override
        		public void run() {
        			try {
	        			while (isRunning) {
	        				Socket socket = ssocket.accept();
	        				if (socket != null) {
	        					Worker worker = new Worker(socket);
	        					worker.start();
	        					workers.add(worker);
	        				}
	        			}
        			} catch (Exception ignore) { }
        		}
        	};
        	
        	server.start();
        }
        
        int getReceivedBytes() {
        	int received = 0;
        	for (Worker worker : workers) {
				received += worker.receivedBytes;
			}
        	return received;
        }
        
        
        void close() {
        	isRunning = false;
        	
        	for (Worker worker : workers) {
				worker.close();
			}
        	
        	try {
        		ssocket.close();
        	} catch (Exception ignore) { }
        }
	}
	
	
	
	
	private static final class Worker extends Thread {
		
		private Socket socket = null;
		private InputStream in = null;
        private OutputStream out = null;
        
        private volatile boolean isRunning = true;
        
        private int receivedBytes = 0;

		Worker(Socket socket) throws IOException {
			super("worker");
			setDaemon(true);
			
			this.socket = socket;
	        in = socket.getInputStream();
	        out = socket.getOutputStream();
		}
		
		@Override
		public void run() {
			
			while(isRunning) {
				try {
					byte[] buf = new byte[4096];
					int c = in.read(buf);
					if (c == -1) {
						close();
					} else if (c > 0) {
						receivedBytes += c;
					}
				} catch (IOException ioException) { }
			} while (isRunning);
			
			try {
				in.close();
				out.close();
			} catch (Exception ignore) { }
		}		


		void close()  {
			isRunning = false;
	        	
			try {
				socket.close();
				in.close();
				out.close();
			} catch (Exception ignore) { }
		}
	}
	
}
