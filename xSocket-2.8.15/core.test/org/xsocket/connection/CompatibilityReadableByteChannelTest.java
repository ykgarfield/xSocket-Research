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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;




/**
*
* @author grro@xsocket.org
*/
public final class CompatibilityReadableByteChannelTest {
	
    
    public static void main(String[] args) throws Exception {
        for (int i = 0; i < 10000; i++) {
            System.out.print(".");
            new CompatibilityReadableByteChannelTest().testBlockingReadEndOfStream();
        }
    }

	@Test 
	public void testNonBlockingReadEndOfStream() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		server.start();
		
		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write(QAUtil.generateByteArray(100));
		QAUtil.sleep(1000);
		
		// in buffer contains 100 bytes
		ByteBuffer buffer = ByteBuffer.allocate(60);
		int read = clientCon.read(buffer);
		Assert.assertEquals(60, read);
		
		// in buffer contains 40 bytes
		buffer = ByteBuffer.allocate(60);
		read = clientCon.read(buffer);
		Assert.assertEquals(40, read);
		
		// in buffer contains 0 bytes
		buffer = ByteBuffer.allocate(60);
		read = clientCon.read(buffer);
		Assert.assertEquals(0, read);
	
		serverCon.write(5);
		server.close();
		QAUtil.sleep(1000);

		
		// in buffer contains 4 bytes
		buffer = ByteBuffer.allocate(60);
		read = clientCon.read(buffer);
		Assert.assertEquals(4, read);
		
		Assert.assertFalse(clientCon.isOpen());
		
		buffer = ByteBuffer.allocate(60);
		read = clientCon.read(buffer);
		Assert.assertEquals(-1, read);
		Assert.assertEquals(-1, clientCon.available());
		Assert.assertFalse(clientCon.isOpen());
		
		
		clientCon.close();
		server.close();
	}
	
	
	
	@Test 
	public void testBlockingReadEndOfStream() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		final IServer server = new Server(srvHdl);
		server.start();
		
		ReadableByteChannel clientCon = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		final INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write(QAUtil.generateByteArray(100));
		QAUtil.sleep(1000);
		
		// in buffer contains 100 bytes
		ByteBuffer buffer = ByteBuffer.allocate(60);
		int read = clientCon.read(buffer);
		Assert.assertEquals(60, read);
		
		// in buffer contains 40 bytes
		buffer = ByteBuffer.allocate(60);
		read = clientCon.read(buffer);
		Assert.assertEquals(40, read);

		Thread t = new Thread() {
			@Override
			public void run() {
				QAUtil.sleep(500);
				try {
					serverCon.write(6);
					server.close();

				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
		};
		t.start();
		
		// in buffer contains 0 bytes (after 500 millis 4 bytes)
		buffer = ByteBuffer.allocate(60);
		read = clientCon.read(buffer);
		Assert.assertEquals(4, read);
		
		Assert.assertTrue(clientCon.isOpen());
		
		buffer = ByteBuffer.allocate(60);
		read = clientCon.read(buffer);
		Assert.assertEquals(-1, read);
		Assert.assertFalse(clientCon.isOpen());
		
		
		clientCon.close();
		server.close();
	}
	
	
	@Test 
	public void testNonBlockingReadClientChannelClosed() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		server.start();
		
		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write(QAUtil.generateByteArray(100));
		QAUtil.sleep(1000);
		
		// in buffer contains 100 bytes
		ByteBuffer buffer = ByteBuffer.allocate(60);
		int read = clientCon.read(buffer);
		Assert.assertEquals(60, read);

		clientCon.close();
		QAUtil.sleep(1000);
		
		buffer = ByteBuffer.allocate(60);
		Assert.assertEquals(-1, clientCon.available());
		
		server.close();
	}
	
	
	

	@Test 
	public void testReadLine() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		server.start();
		
		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write("test\r\n");
		serverCon.close();
		QAUtil.sleep(1000);
		
		InputStream is = Channels.newInputStream(new BlockingConnection(clientCon));
		BufferedReader bufferedInput = new BufferedReader(new InputStreamReader(is));
		
		Assert.assertEquals("test", bufferedInput.readLine());
		Assert.assertNull(bufferedInput.readLine());
		
		
	}
	
	

    @Test 
    public void testReadLine2() throws Exception {
        
        ServerHandler srvHdl = new ServerHandler();
        IServer server = new Server(srvHdl);
        server.start();
        
        IBlockingConnection clientCon = new BlockingConnection("localhost", server.getLocalPort());
        QAUtil.sleep(1000);
        
        INonBlockingConnection serverCon = srvHdl.getConection();
        
        File file = QAUtil.createTestfile_400k();
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        FileChannel fc = raf.getChannel();
        serverCon.transferFrom(fc);
        fc.close();
        raf.close();
        serverCon.close();
        QAUtil.sleep(1000);
        
        
        InputStream is = Channels.newInputStream(clientCon);
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
        
        StringBuilder sb = new StringBuilder();
        String line = null;
        do {
            line = lnr.readLine();
            if (line != null) {
                sb.append(line + "\r\n");
            }
        } while (line != null);
        
 
        
        InputStream is2 = new FileInputStream(file);
        LineNumberReader lnr2 = new LineNumberReader(new InputStreamReader(is2));
        
        StringBuilder sb2 = new StringBuilder();
        String line2 = null;
        do {
            line2 = lnr2.readLine();
            if (line2 != null) {
                sb2.append(line2 + "\r\n");
            }
        } while (line2 != null);
        
        Assert.assertEquals(sb2.toString(), sb.toString());
        
        
        file.delete();
        clientCon.close();
        server.close();        
    }
    
    
	
	
	
	@Test 
	public void testBlockingReadClientChannelClosed() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		server.start();
		
		ReadableByteChannel clientCon = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write(QAUtil.generateByteArray(100));
		QAUtil.sleep(200);
		
		// in buffer contains 100 bytes
		ByteBuffer buffer = ByteBuffer.allocate(60);
		int read = clientCon.read(buffer);
		Assert.assertEquals(60, read);

		
		clientCon.close();
		
		buffer = ByteBuffer.allocate(60);
		try {
			read = clientCon.read(buffer);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }  
		
		server.close();
	}
	
	

	@Test 
	public void testBlockingRead() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		server.start();
		
		IBlockingConnection clientCon = new BlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write(QAUtil.generateByteArray(4));
		QAUtil.sleep(200);
	
		clientCon.readInt();
		Assert.assertTrue(clientCon.isOpen());
		
		serverCon.write(QAUtil.generateByteArray(7));
		serverCon.close();
		QAUtil.sleep(200);
		
		clientCon.readInt();
		Assert.assertTrue(clientCon.isOpen());

		try {
			clientCon.readInt();
			Assert.fail("ClosedChannelException excepted");
		} catch (ClosedChannelException excepted) { }
		
		Assert.assertFalse(clientCon.isOpen());
		
		server.close();
	}
	
	
	@Test 
	public void testNonBlockingRead() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		server.start();
		
		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(2000);
		
		INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write(QAUtil.generateByteArray(4));
		QAUtil.sleep(500);
	
		Assert.assertEquals(4, clientCon.available());
		clientCon.readInt();
		Assert.assertEquals(0, clientCon.available());
		Assert.assertTrue(clientCon.isOpen());
		
		serverCon.write(QAUtil.generateByteArray(7));
		serverCon.close();
		QAUtil.sleep(500);
		
		Assert.assertEquals(7, clientCon.available());
		clientCon.readInt();
		Assert.assertEquals(3, clientCon.available());
		Assert.assertTrue(clientCon.isOpen());

		try {
			clientCon.readInt();
			Assert.fail("ClosedChannelException excepted");
		} catch (ClosedChannelException excepted) { }
		
		QAUtil.sleep(500);
		Assert.assertFalse(clientCon.isOpen());
		Assert.assertEquals(-1, clientCon.available());
		
		server.close();
	}
	
	@Test 
	public void testNonBlockingAvailableRead() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		server.start();
		
		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());
		QAUtil.sleep(1000);
		
		INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write(QAUtil.generateByteArray(4));
		serverCon.close();
		QAUtil.sleep(1000);
	
		Assert.assertEquals(4, clientCon.available());
		clientCon.readInt();
		
		int available = clientCon.available();
		Assert.assertEquals(-1, available);
		
		Assert.assertFalse(clientCon.isOpen());
		
		try {
			clientCon.readStringByLength(available);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }
		
		Assert.assertFalse(clientCon.isOpen());
			
		server.close();
	}
	
	
	@Test 
	public void testNonBlockingHandlerRead() throws Exception {
		
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		server.start();
		
		ClientHandler cltHdl = new ClientHandler();
		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort(), cltHdl);
		QAUtil.sleep(1000);
		
		
		INonBlockingConnection serverCon = srvHdl.getConection();
		serverCon.write(QAUtil.generateByteArray(4));
		QAUtil.sleep(1000);

		Assert.assertEquals(4, cltHdl.getAvailable());
		
		server.close();

		QAUtil.sleep(1000);

		Assert.assertEquals(-1, cltHdl.getAvailable());
	}
	
	
	private static final class ClientHandler implements IDataHandler {
		
		private int available = 0;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			available = connection.available();
			if (available > 0) {
				connection.readBytesByLength(available);
			}
			
			return true;
		}
		
		int getAvailable() {
			return available;
		}
	}
	
	
	private static final class ServerHandler implements IConnectHandler {
		
		private AtomicReference<INonBlockingConnection> connectionRef = new AtomicReference<INonBlockingConnection>();

		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			this.connectionRef.set(connection);
			return true;
		}

		INonBlockingConnection getConection() {
			return connectionRef.get();
		}
	}
}
