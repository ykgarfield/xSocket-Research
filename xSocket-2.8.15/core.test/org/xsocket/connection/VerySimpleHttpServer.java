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


import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class VerySimpleHttpServer extends Server {


	public static void main(String[] args) throws Exception {
		
		
		if (args.length != 1) {
			System.out.println("usage org.xsocket.connection.VerySimpleHttpServer <port>");
			System.exit(-1);
		}
		
		int port = Integer.parseInt(args[0]);
		
	
		
		IServer server = new VerySimpleHttpServer(port);
		server.setFlushmode(FlushMode.ASYNC);
		
		ConnectionUtils.registerMBean(server);
		server.run();
	}
	
	
	public VerySimpleHttpServer(int port) throws IOException {
		//super(port, new HttpProtocolHandler());
		super(port, new HttpProtocolHandler(), SSLTestContextFactory.getSSLContext(), true);
	}
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class HttpProtocolHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			connection.readStringByDelimiter("\r\n\r\n");
			connection.write("HTTP/1.0 200 OK\r\n" + 
					         "Content-Type: text/plain\r\n" +
					         "content-length: 10\r\n" +
					         "\r\n" +
					         "1234567890");
			
			return true;
		}
	}
}
