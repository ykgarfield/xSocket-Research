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
import java.util.logging.Logger;


import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.Resource;
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
public final class Proxy extends Server {
	
	private static final Logger LOG = Logger.getLogger(Proxy.class.getName());
	

	public Proxy(int listenPort, String forwardHost, int forwardPort) throws Exception {
		super(listenPort, new ClientToProxyHandler(InetAddress.getByName(forwardHost), forwardPort));
	}
	
	
	public static void main(String... args) throws Exception {
		if (args.length != 3) {
			System.out.println("usage org.xsocket.stream.Proxy <listenport> <forwardhost> <forwardport>");
			System.exit(-1);
		}
		
		
		Proxy proxy = new Proxy(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
		ConnectionUtils.registerMBean(proxy);
		
		proxy.run();
	}
	
	
	private static abstract class ProxyHandlerBase implements IDataHandler, IDisconnectHandler {
	    
	
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			INonBlockingConnection reverseConnection = (INonBlockingConnection) connection.getAttachment();
			if (reverseConnection != null) {
				LOG.fine("closing peer connection " + reverseConnection.getId());
				connection.setAttachment(null);
				reverseConnection.close();
			}
			
			return true;
		}
		
		
		public int forward(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			INonBlockingConnection forwardConnection = (INonBlockingConnection) connection.getAttachment();
			int available = connection.available();
				
			if (available > 0) {
				ByteBuffer[] data = connection.readByteBufferByLength(available);
				
				LOG.fine("writing available");
				forwardConnection.write(data);
			}
				
			LOG.fine(available + " written");
			return available;
		}
	}
	
		
	@Execution(Execution.NONTHREADED)
	private static class ProxyToServerHandler extends ProxyHandlerBase implements IConnectHandler {
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			LOG.fine("[" + connection.getId()+ "] {proxy2client} connection established");
			return false;
		}
		
	
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			LOG.fine("[" + connection.getId()+ "] {proxy2client} connection closed");
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int size = forward(connection);
			if (size > 0) {
				LOG.fine("[" + connection.getId()+ "] {proxy2server} " + size + " transfered from proxy -> client");
			}
			
			return true;
		}
	}
	
	
	@Execution(Execution.NONTHREADED)
	private static final class ClientToProxyHandler extends ProxyHandlerBase implements IConnectHandler {
		
		private InetAddress forwardHost = null;
		private int forwardPort = 0;
		
		
		@Resource
		private IServer srv = null;
		
		
		public ClientToProxyHandler(InetAddress forwardHost, int forwardPort) {
			this.forwardHost = forwardHost;
			this.forwardPort = forwardPort;
		}
		
		
		@Execution(Execution.MULTITHREADED)
		public boolean onConnect(INonBlockingConnection clientToProxyConnection) throws IOException {
			LOG.fine("[" + clientToProxyConnection.getId()+ "] {client2proxy} connection established");
			
			clientToProxyConnection.setFlushmode(FlushMode.ASYNC); // set flush mode async for performance reasons
			
			Executor workerPool = srv.getWorkerpool();   // performance optimization -> using server worker pool for client connection, too
			
			LOG.fine("[" + clientToProxyConnection.getId()+ "] {client2proxy} creating peer connection");
			INonBlockingConnection proxyToServerConnection = new NonBlockingConnection(forwardHost, forwardPort, new ProxyToServerHandler(), workerPool);
			proxyToServerConnection.setFlushmode(FlushMode.ASYNC); // set flush mode async for performance reasons
			LOG.fine("[" + proxyToServerConnection.getId()+ "] register peer connection " + clientToProxyConnection.getId());
			proxyToServerConnection.setAttachment(clientToProxyConnection);
			
			LOG.fine("[" + clientToProxyConnection.getId()+ "] register peer connection " + proxyToServerConnection.getId());
			clientToProxyConnection.setAttachment(proxyToServerConnection);
			
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection clientToProxyConnection) throws IOException {
			LOG.fine("[" + clientToProxyConnection.getId()+ "] {client2proxy} connection closed");
			return super.onDisconnect(clientToProxyConnection);
		}
		
		
		public boolean onData(INonBlockingConnection clientToProxyConnection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int size = forward(clientToProxyConnection);
			if (size > 0) {
				LOG.fine("[" + clientToProxyConnection.getId()+ "] {client2proxy} " + size + " transfered from client -> proxy");
			}
			
			return true;
		}
	}	
}
