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


import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class BlockingClient  {
	
	public static void main(String... args) throws Exception {
		if (args.length != 3) {
			System.out.println("usage org.xsocket.stream.BlockingClient <host> <port> <path>");
			System.exit(-1);
		}
		
		new BlockingClient().call(args[0], Integer.parseInt(args[1]), args[2]);
	}
	
	
	public void call(String host, int port, String path) throws IOException {
	
		IBlockingConnection connection = null;
		try {
			connection = new BlockingConnection(host, port);
			connection.write("GET " + path + " HTTP/1.1\r\n" +
			                 "Host: " + host + "\r\n" +
			                 "User-agent: me\r\n\r\n");
			
			int bodyLength = 0;	
				
			// print header
			String line = null;
			do {
				line = connection.readStringByDelimiter("\r\n").trim();
				if (line.startsWith("Content-Length:")) {
					bodyLength = new Integer(line.substring("Content-Length:".length(), line.length()).trim());
				}
				
				if (line.length() > 0) {
					System.out.println(line);
				}
			} while (line.length() > 0);
			
			
			// print body 
			if (bodyLength > 0) {
				System.out.println(connection.readStringByLength(bodyLength));
			}
			
		} finally {
			if (connection != null) {
				connection.close();
			}
		}

	}
		
}
