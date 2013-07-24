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
import java.util.concurrent.Executor;


import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.Resource;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class ThreadedSSLProxy extends Server {

	
	public ThreadedSSLProxy(int listenPort, String forwardHost, int forwardPort, boolean prestartSSL) throws Exception {
		super(listenPort, new ClientToProxyHandler(InetAddress.getByName(forwardHost), forwardPort, prestartSSL), SSLTestContextFactory.getSSLContext(), prestartSSL);
	}
	
	
	public static void main(String... args) throws Exception {
		if (args.length != 3) {
			System.out.println("usage org.xsocket.stream.Proxy <listenport> <forwardhost> <forwardport> <prestartSSL>");
			System.exit(-1);
		}
		
		
		ThreadedSSLProxy proxy = new ThreadedSSLProxy(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]), Boolean.parseBoolean(args[3]));
		ConnectionUtils.registerMBean(proxy);
		
		proxy.run();
	}
	
		
	private static class ProxyHandler implements IDataHandler, IConnectHandler, IDisconnectHandler {
		
		
		private boolean prestartSSL = true;
		
		public ProxyHandler(boolean prestartSSL) {
			this.prestartSSL = prestartSSL;
		}
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (!prestartSSL) {
				connection.activateSecuredMode();
			}
			return true;
		}
		
	
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			INonBlockingConnection reverseConnection = (INonBlockingConnection) connection.getAttachment();
			if (reverseConnection != null) {
				connection.setAttachment(null);
				reverseConnection.close();
			}
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			INonBlockingConnection forwardConnection = (INonBlockingConnection) connection.getAttachment();

			ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
			forwardConnection.write(data);
			
			return true;
		}
	}
	
	
	private static final class ClientToProxyHandler extends ProxyHandler implements IConnectHandler {
		
		private InetAddress forwardHost = null;
		private int forwardPort = 0;
		
		private boolean prestartSSL = true;
		
		@Resource
		private IServer srv = null;
		
		public ClientToProxyHandler(InetAddress forwardHost, int forwardPort, boolean prestartSSL) {
			super(prestartSSL);
		
			this.prestartSSL = prestartSSL;
			this.forwardHost = forwardHost;
			this.forwardPort = forwardPort;
		}
		
		
		public boolean onConnect(INonBlockingConnection clientToProxyConnection) throws IOException {
			super.onConnect(clientToProxyConnection);
			
			clientToProxyConnection.setFlushmode(FlushMode.ASYNC); // set flush mode async for performance reasons
			
			Executor workerPool = srv.getWorkerpool();   // performance optimization -> using server worker pool for client connection, too
			INonBlockingConnection proxyToServerConnection = new NonBlockingConnection(forwardHost, forwardPort, new ProxyHandler(prestartSSL), workerPool);
			proxyToServerConnection.setFlushmode(FlushMode.ASYNC); // set flush mode async for performance reasons
			proxyToServerConnection.setAttachment(clientToProxyConnection);
			
			clientToProxyConnection.setAttachment(proxyToServerConnection);
			
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection clientToProxyConnection) throws IOException {
			return super.onDisconnect(clientToProxyConnection);
		}
		
		
		public boolean onData(INonBlockingConnection clientToProxyConnection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return super.onData(clientToProxyConnection);
		}
	}	
}
