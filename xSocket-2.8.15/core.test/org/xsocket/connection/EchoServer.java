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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;


import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class EchoServer implements Closeable {
	private IServer server = null;

	EchoServer(int listenPort) throws Exception {

		IHandler hdl = new EchoHandler();


		////////////////////////
		// uncomment following code for using the first visit throttling filter
		// FirstVisitThrottlingFilter firstVisitFilter = new FirstVisitThrottlingFilter(5);
		// HandlerChain chain = new HandlerChain();
		// chain.addLast(firstVisitFilter);
		// chain.addLast(hdl);
		// hdl = chain;


		server = new Server(listenPort, hdl);
		server.setFlushmode(FlushMode.ASYNC);  // performance improvement 
		
		ConnectionUtils.start(server);
		ConnectionUtils.registerMBean(server);
	}

	
	
	public static void main(String... args) throws Exception {
		
		System.setProperty(IoProvider.DEFAULT_USE_DIRECT_BUFFER, "true");
		
		if (args.length != 1) {
			System.out.println("usage org.xsocket.stream.EchoServer <listenport>");
			System.exit(-1);
		}
		
		System.setProperty("org.xsocket.connection.server.workerpoolSize", "10");

		new EchoServer(Integer.parseInt(args[0]));
	}


	public InetAddress getLocalAddress() {
		return server.getLocalAddress();
	}
	
	public int getLocalPort() {
	    return server.getLocalPort();
	}

	public void close() throws IOException {
		if (server != null) {
			server.close();
		}
	}


	@Execution(Execution.NONTHREADED) // performance improvement, but don't set to 'single threaded' mode if long running (I/O, network) operations will be performed within onData 
	private static final class EchoHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
			connection.write(data);
			return true;
		}
	}



	private static final class FirstVisitThrottlingFilter implements IConnectHandler {

		private final Set<String> knownIps = new HashSet<String>();

		private int writeRate = 0;


		FirstVisitThrottlingFilter(int writeRate) {
			this.writeRate = writeRate;
		}

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			String ipAddress = connection.getRemoteAddress().getHostAddress();
			if (!knownIps.contains(ipAddress)) {
				knownIps.add(ipAddress);
				connection.setFlushmode(FlushMode.ASYNC);
				connection.setWriteTransferRate(writeRate);
			}


			return false;  // false -> successor element in handler chain will be called (true -> chain processing will be terminated)
		}
	}
}
