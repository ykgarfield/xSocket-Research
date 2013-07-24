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

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingClient  {
	
	public static void main(String... args) throws Exception {
		if (args.length != 3) {
			System.out.println("usage org.xsocket.stream.NonBlockingClient <host> <port> <path>");
			System.exit(-1);
		}
		
		new NonBlockingClient().call(args[0], Integer.parseInt(args[1]), args[2]);
	}
	
	
	public void call(String host, int port, String path) throws IOException {
		
		INonBlockingConnection connection = null;
		try {
			connection = new NonBlockingConnection(host, port, new DataHandler());
			connection.write("GET " + path + " HTTP\r\n\r\n");
			
			// do somthing else
			try {
				Thread.sleep(300);
			} catch (InterruptedException ignore) { }
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}
	
	
	private static final class DataHandler implements IDataHandler {

		private boolean isHeader = true;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			String line = null;
			do {
				line = connection.readStringByDelimiter("\r\n").trim();;
				if ((line.length() > 0) && isHeader) {
					System.out.println(line);
				}
			} while (line.length() > 0);
			isHeader = false;
			
			return true;
		}
	}
		
}
