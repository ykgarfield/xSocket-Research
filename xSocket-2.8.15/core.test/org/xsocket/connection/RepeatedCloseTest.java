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
import java.nio.channels.ClosedChannelException;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;






/**
*
* @author grro@xsocket.org
*/
public final class RepeatedCloseTest  {



	@Test 
	public void testRepeatedClose() throws Exception {
		IServer server = new Server(new Handler());
		server.start();
		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		connection.write("GET /index.html \r\n");
		connection.write("User-Agent: xSocket\r\n");
		connection.write("Accept: text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5\r\n");
		connection.write("Accept-Language: de-de,de;q=0.8,en-us;q=0.5,en;q=0.3\r\n");
		connection.write("Accept-Encoding: gzip,deflate\r\n");
		connection.write("Accept-Charset: ISO-8859-1,utf-8;q=0.7,*;q=0.7\r\n");
		connection.write("Keep-Alive: 300\r\n");
		connection.write("Connection: close\r\n");
		connection.write("\r\n\r\n");
		connection.flush();
		
		

		do {
			QAUtil.sleep(100);
		} while (connection.indexOf("\r\n\r\n") == -1);
		
		
		String response = connection.readStringByLength(connection.available());

		Assert.assertFalse(connection.isOpen());
		
		try {
			connection.readByte();
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		Assert.assertFalse(connection.isOpen());
		
		// repeated close should work
		connection.close();
		
		
		server.close();
	}
	
	
	private static final class Handler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			String header = connection.readStringByDelimiter("\r\n\r\n");
			connection.write(header + "\r\n\r\n");
			connection.close();
			
			return true;
		}
		
	}
}
