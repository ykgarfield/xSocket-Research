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


import org.junit.Test;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class SimpleImapClient {
	
	
	private int i = 0;
	
	public static void main(String[] args) throws IOException {
		
		if (args.length != 4) {
			System.out.println("usage org.xsocket.connection.SimpleImapClient <host> <port> <user> <pwd>");	
			System.exit(-1);
		}
		
		new SimpleImapClient().launch(args[0], Integer.parseInt(args[1]), args[2], args[3]);
	}
	
	
	public void launch(String host, int port, String user, String pwd) throws IOException {
		
		IBlockingConnection con = new BlockingConnection(host, port);
		
		System.out.println(con.readStringByDelimiter("\r\n"));
		
		String response = call(con, "LOGIN " + user + " " +pwd);
		response = call(con, "SELECT INBOX");
		response = call(con, "FETCH 1:1 (INTERNALDATE BODY[HEADER.FIELDS (RECEIVED FROM SUBJECT)])");
		
		con.close();

	}
	
	private String call(IBlockingConnection con, String cmd) throws IOException {
		StringBuilder sb = new StringBuilder();
		
		String tag = "A" + (i++);
		
		String request = tag + " " + cmd + "\r\n";
		System.out.println(request);
		con.write(request);
		
		String line = null;
		do {
			line = con.readStringByDelimiter("\r\n");
			sb.append(line + "\r\n");
		} while (!line.trim().startsWith(tag));
		
		String response = sb.toString();
		System.out.println(response);
		
		return response;		
	}

}
