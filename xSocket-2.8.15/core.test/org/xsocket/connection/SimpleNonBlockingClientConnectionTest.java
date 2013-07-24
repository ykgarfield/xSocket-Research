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

import org.junit.Test;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class SimpleNonBlockingClientConnectionTest {

	private static String DELIMITER ="\r\n";


	@Test 
	public void testByUsingHandler() throws Exception {
		
		// start the server
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);
		
		
		// define the client side handler
		IDataHandler clientSideHandler = new IDataHandler() {
			public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
				connection.setAutoflush(false);
				String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
				System.out.println("response: " + response);
				return true;
			}
		};

		
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), clientSideHandler);
		con.setAutoflush(false);
		con.setEncoding("ISO-8859-1");

		// send the request 
		System.out.println("sending the request");
		con.write("NICK maneh" + DELIMITER);
		con.write("USER asdasd"+ DELIMITER);
		con.flush();

		// the request will be handled by cleint event handler, just wait some millis
		try {
			Thread.sleep(500);
		} catch (InterruptedException ignore) { }

		con.close();
		server.close();
	}
	
	
	@Test 
	public void testByUsingPooling() throws Exception {
		
		// start the server
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);
		
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
		con.setAutoflush(false);
		con.setEncoding("ISO-8859-1");

		// send the request 
		System.out.println("sending the request");
		con.write("NICK maneh" + DELIMITER);
		con.write("USER asdasd"+ DELIMITER);
		con.flush();

		// pooling, while getting the request
		// (the event driven approach is often a better choice that this pooling-driven approach!)
		boolean received = false;
		do {
			try {
				String response = con.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
				System.out.println("response: " + response);
				received = true;
			} catch (BufferUnderflowException bue) {
				// not enough data received -> wait some millis and try it again
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) { }
			}
		} while (!received);

		con.close();
		server.close();
	}
	
	


	private static class EchoHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.setAutoflush(false);
			connection.write(connection.readByteBufferByDelimiter(DELIMITER, Integer.MAX_VALUE));
			connection.write(DELIMITER);
			
			connection.flush();
			return true;
		}
	}
}
