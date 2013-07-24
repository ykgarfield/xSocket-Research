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


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class FlushOnCloseTest {

	private static final String DELIMITER = "x";

	private static final String QUIT_COMMAND = "QUIT";
	private static final String OK_RESPONSE = "OK";
	private static final String CLOSED_RESPONSE = "THIS Connection has been CLOSED";







	@Test
	public void testSimple() throws Exception {

		IServer server = new Server(new ServerHandler());
		server.start();


		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);

		System.out.println("send hello");
		connection.write("hello" + DELIMITER);
		connection.flush();

		System.out.println("read ok response");
		Assert.assertEquals(OK_RESPONSE, connection.readStringByDelimiter(DELIMITER));

		System.out.println("send some command");
		connection.write("some command" + DELIMITER);
		connection.flush();

		System.out.println("read ok response");
		Assert.assertEquals(OK_RESPONSE, connection.readStringByDelimiter(DELIMITER));

		System.out.println("send quit");
		connection.write(QUIT_COMMAND + DELIMITER);
		connection.flush();

		System.out.println("read close response");
		Assert.assertTrue("close response for quit expected", connection.readStringByDelimiter(DELIMITER).equals(CLOSED_RESPONSE));

		System.out.println("close");
		connection.close();

		server.close();
	}




	private static class ServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);

			String request = connection.readStringByDelimiter(DELIMITER);
			connection.setWriteTransferRate(5);

			if (request.equals(QUIT_COMMAND)) {
				connection.write(CLOSED_RESPONSE + DELIMITER);
				connection.close();
			} else {
				connection.write(OK_RESPONSE + DELIMITER);
			}

			connection.flush();
			return true;
		}
	}
}
