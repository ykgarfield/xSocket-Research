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



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class ConnectionUtilsTest {



	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new Handler());
		server.start();
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		String request = "hallo";
		
		connection.markWritePosition();
		connection.write((int) 0);
		
		int written = connection.write(request);
		connection.resetToWriteMark();
		connection.write(written);
		connection.flush();

		String response = connection.readStringByLength(written);
		
		Assert.assertEquals(request, response);
		
		connection.close();
		server.close();
	}
	
	private static final class Handler implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int length = ConnectionUtils.validateSufficientDatasizeByIntLengthField(connection);
			String text = connection.readStringByLength(length);
		       
			connection.write(text);
			connection.flush();
			return false;
		}
	}
}
