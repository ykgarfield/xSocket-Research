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
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.connection.IConnection.FlushMode;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;


public class SSLNonThreadedTest {
	
	private final List<String> errors = new ArrayList<String>();
	
	private AtomicInteger running = new AtomicInteger(0);
	
	
	
	public static void main(String[] args) throws Exception {
		new SSLNonThreadedTest().perfomTest(Integer.parseInt(args[0]));
	}

	
	@Test
	public void testBulk() throws Exception {
		perfomTest(1000);
	}

	
	public void perfomTest(int loops) throws Exception {

		
	   // System.setProperty("org.xsocket.connection.server.ssl.sslengine.enabledCipherSuites", "SSL_RSA_WITH_RC4_128_SHA, SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA, SSL_RSA_WITH_3DES_EDE_CBC_SHA");
 		

		
		errors.clear();
		
		Server server = new Server(0, new Repeater(), SSLTestContextFactory.getSSLContext(), true);
		server.setFlushmode(FlushMode.ASYNC);
		server.setAutoflush(false);
		server.start();
		
		ConnectionUtils.registerMBean(server);
		
		
		new Thread(new Node ("register", "1234", server.getLocalPort(), loops)).start();
		QAUtil.sleep(200);
		
		new Thread(new Node ("connect", "1234",  server.getLocalPort(), loops)).start();
		
		do {
			QAUtil.sleep(100);
		} while (running.get() > 0);
	}
	
	
	
	
	private final class Repeater implements IDataHandler {
	
		private final HashSet<Link> links = new HashSet<Link>();
		private final Map<String, INonBlockingConnection> unassignedConnections = Collections.synchronizedMap(new HashMap<String, INonBlockingConnection>());

		
		@Execution(Execution.MULTITHREADED)
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			///////////////
			// "transactional" read
			connection.resetToReadMark();  // will be ignored, if no read mark is set
			connection.markReadPosition();
			
			String type = connection.readStringByDelimiter("\n", "UTF-8");
			String id = connection.readStringByDelimiter("\n", "UTF-8");
			
			connection.removeReadMark();
			//
			////////////////////////

			
			// ... got the meta data, detach this handler to avoid incorrect reading of more (content) data for the given connection
			connection.setHandler(null);
			
			
			// handle the command
			if(type.startsWith("register")) {
				handleRegister(id, connection);
				
			} else if(type.startsWith("connect")) {
				handleConnect(id, connection);
			}
			
			return true;
		}
		
		
		private void handleRegister(String id, INonBlockingConnection connection) {
			System.out.println("register node: " + id);
			unassignedConnections.put(id, connection);
		}
		

		private void handleConnect(String id, INonBlockingConnection connection) throws IOException {
			INonBlockingConnection peer = null;

			// waiting for a peer
			long maxTime = System.currentTimeMillis() + 30000L;
			do {
				
				peer = unassignedConnections.get(id);
				System.out.println("connect node: " + id);
				if (peer != null) {
					new Link(this, connection, peer);
					return;
					
				} else {
					try {
						Thread.sleep(400);
					} catch (InterruptedException ignore) { }
				}
			} while (System.currentTimeMillis() < maxTime);
			
			System.out.println("timeout 30000 millis exceeded");
			errors.add("timeout 30000 millis exceeded");
			connection.close();
		}
		
		
		/**
		 * jmx-support (will be detected by introspection)
		 * 
		 */
		@SuppressWarnings("unchecked")
		List<String> getLinkInfo() {
			List<String> result = new ArrayList<String>();
			
			HashSet<Link> linksCopy = null;
			synchronized (links) {
				linksCopy = (HashSet<Link>) links.clone();
			}
			for (Link link : linksCopy) {
				result.add(link.toString());
			}
			
			return result;
		}
		
		
		private void registerLink(Link link) {
			synchronized (links) {
				links.add(link);	
			}
		}
		
		private void unregisterLink(Link link) {
			synchronized (links) {
				links.remove(link);
			}
		}
	}
	
	
	
	
	@Execution(Execution.NONTHREADED)
	private final class Link implements IDataHandler, IDisconnectHandler {
		
		private Repeater repeater = null;
		private String linkInfo;
	
		Link(Repeater repeater, INonBlockingConnection connection, INonBlockingConnection peer) throws IOException {
			this.repeater = repeater;
			repeater.registerLink(this);
			
			connection.setAttachment(peer);
			peer.setAttachment(connection);
			
			connection.setHandler(this);
			peer.setHandler(this);
			
			linkInfo = connection.getRemoteAddress().toString() + ":" + connection.getRemotePort() 
			           + " <-> " + peer.getRemoteAddress().toString() + ":" + peer.getRemotePort();
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
			
			INonBlockingConnection peer = (INonBlockingConnection) connection.getAttachment();
			if(peer != null) {
				peer.write(data);
				peer.flush();
				//System.out.print(">");
			}
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			repeater.unregisterLink(this);
			
			INonBlockingConnection peer = (INonBlockingConnection) connection.getAttachment();
			if (peer != null) {
				peer.setAttachment(null);
				peer.close();
			}
			
			return true;
		}
		
		
		@Override
		public String toString() {
			return linkInfo;
		}
	}
	
	
	private final class Node implements Runnable, IDataHandler {
		
		private String cmd;
		private String id;
		private int serverPort;
		private int loops = 0;
		
		private int bytesReceived = 0;
		
		public Node(String cmd, String id, int serverPort, int loops) { 
			this.cmd = cmd;
			this.id = id; 
			this.serverPort = serverPort;
			this.loops = loops;
		}
		
		public void run() {
			running.incrementAndGet();
			
			System.out.println("starting node");
			
			try {
				INonBlockingConnection c = new NonBlockingConnection(InetAddress.getByName("localhost"), serverPort, this, SSLTestContextFactory.getSSLContext(), true);
				c.setFlushmode(FlushMode.ASYNC);
			
				c.write(cmd + "\nID:" + id + "\n");
				
				byte[] bytes = QAUtil.generateRandomByteArray(1024);
				
				for (int i = 0; i < loops; i++) {
					c.write(bytes); 
					c.flush();
					Thread.sleep(5);
				}
			} catch(Exception e) {
				errors.add("ClientNode: error occured " + e.toString());
				e.printStackTrace();
			}
			
			System.out.println("finishing node (" + bytesReceived + " bytes received)");
			running.decrementAndGet();
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
			
			for (ByteBuffer buf : data) {
				bytesReceived += buf.remaining();
			}
			
			//System.out.print("<");
			return true;
		}
	}
	
}
