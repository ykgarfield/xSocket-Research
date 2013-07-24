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
import java.net.URLEncoder;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
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
public final class ConcurrentReadWriteTest {
	
	private static final DataGenerator DATA_GENERATOR = new DataGenerator();
	
	private static List<String> errors;
	
	
	@Before
	public void setUp() {
	    
		errors = new ArrayList<String>();
	}
	

	public static void main(String... args) throws Exception {
		
		new ConcurrentReadWriteTest().run(1000000000, 4000, true);		
	}
	
	

	@Test 
	public void testMediumPackets() throws Exception {		
		System.out.println("test medium packets");
		run(10000, 4000, false);
		
		System.gc();
	}

	@Test 
	public void testVeryLargePacketsAsync() throws Exception {
		System.out.println("test very large packets async");
		run(1, 4000000, false);
		
		System.gc();
	} 

	
	@Test 
	public void testLargePacketsAsync() throws Exception {
		System.out.println("test large packets async");
		run(100, 200000, false);
		
		System.gc();
	} 
	

	


	@Test 
	public void testLargePacketsSync() throws Exception {
		System.out.println("test large packets sync");
		run(100, 200000, true);
		
		System.gc();
	}

	

	@Test 
	public void testVeryLargePacketsSync() throws Exception {
		System.out.println("test very large packets sync");
		run(1, 4000000, false);
		
		System.gc();
	}
	
	
	private void run(int countPackets, int packetSize, boolean syncMode) throws Exception {

		AtomicInteger countReceived = new AtomicInteger();
		
		Handler handler = new Handler(countPackets, packetSize, syncMode);
		IServer server = new Server(0, handler);
		ConnectionUtils.start(server);
		
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
		if (!syncMode) {
			con.setFlushmode(FlushMode.ASYNC);
		}
		
		Reader reader = new Reader(con, countReceived, packetSize);
		Thread t = new Thread(reader);
		t.start();
		
		write(countPackets, packetSize, con, syncMode);
		
		while (errors.isEmpty() && ((countReceived.get() < countPackets) || (handler.countReceived.get() < countPackets))) {
			QAUtil.sleep(100);
		}
		
		System.out.println("");
		
		reader.terminate();
		
		QAUtil.sleep(350);
		
		con.close();
		server.close();
		
		if (!errors.isEmpty()) {
			for (String error : errors) {
				System.out.println(error);
			}
			
			Assert.fail("errors");
		}
	}
	
	
	
	
	
	private static final void write(int countPackets, int packetSize, INonBlockingConnection con, boolean syncMode) throws IOException {
		
		
		int counter = 0;
		ByteBuffer[] data = DATA_GENERATOR.generate(packetSize);
		
		for (int i = 0; i < countPackets; i++) {
			if (!syncMode) {
				data = DATA_GENERATOR.generate(packetSize);
			}
			
			
			if (!syncMode) {
				while (con.getPendingWriteDataSize() > 400000) {
					QAUtil.sleep(50);
				}
			}
			
			con.write(data);
			
			if (syncMode) {
				for (ByteBuffer buffer : data) { 
					buffer.rewind();
				}
			} 
				
			//System.out.print(">");
			
			counter++;
			if (counter > 5000) {
				counter = 0;
				System.gc();
				System.out.print(".");
			}
		}
	}
	

	private final class Reader implements Runnable {
		private volatile boolean isRunning = true;
		
		private INonBlockingConnection con = null;
		private AtomicInteger countReceived = null;
		
		private int packetSize = 0;
		
		public Reader(INonBlockingConnection con, AtomicInteger countReceived, int packetSize) {
			this.con = con;
			this.countReceived = countReceived;
			this.packetSize = packetSize;
		}
		
		
		public void run() {
			while (isRunning) {
				try { 
					while (con.available() >= packetSize) {
						con.readBytesByLength(packetSize);
						countReceived.incrementAndGet();
					}
				}  catch (Exception e) {
					errors.add(DataConverter.toString(e));
					return;
				}
				
				QAUtil.sleep(200);
			}
		}
		
		
		void terminate() {
			isRunning = false;
		}
	}
	


	private static final class Handler implements IConnectHandler, IDataHandler {
		
		private AtomicInteger countReceived = new AtomicInteger();
		
		private int countPackets = 0;
		private int packetSize = 0;
		private boolean syncMode = false;
		

		public Handler(int countPackets, int packetSize, boolean syncMode) {
			this.countPackets = countPackets;
			this.packetSize = packetSize;
			this.syncMode = syncMode;
		}
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			if (!syncMode) {
				connection.setFlushmode(FlushMode.ASYNC);
			}
			
			Writer writer = new Writer(connection, countPackets, packetSize);
			Thread t = new Thread(writer);
			t.start();
			
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			connection.readBytesByLength(packetSize);
			countReceived.incrementAndGet();
			return true;
		}
	}
	
	
	private static final class Writer implements Runnable {
		private INonBlockingConnection con = null;
		
		private int countPackets = 0;
		private int packetSize = 0;
		
		
		public Writer(INonBlockingConnection con, int countPackets, int packetSize) {
			this.con = con;
			this.countPackets = countPackets;
			this.packetSize = packetSize;
		}
		
		
		public void run() {
			
			try {
				write(countPackets, packetSize, con, false);
			}  catch (Exception e) {
				errors.add(DataConverter.toString(e));
				return;
			}
		}		
	}
	
	
	
	private static final class DataGenerator {
		
		ByteBuffer[] generate(int length) {
			return new ByteBuffer [] { QAUtil.generateByteBuffer(length) };
		}
	}
}
