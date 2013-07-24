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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.util.logging.Level;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.SSLTestContextFactory;
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
public final class HandlerAutoflushTest {

	private static final String DELIMITER = "\n";
	private static final String TEXT = "text";



	@Test
	public void testActivateSslOnConnect() throws Exception {
		IServer server = new Server(new Handler());
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		int count = 100;
		connection.write(count);

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append(TEXT);
		}

		String response = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals(sb.toString(), response);

		server.close();
	}


	private static final class Handler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			int count = connection.readInt();

			for (int i = 0; i < count; i++) {
				connection.write(TEXT);  // implicit autoflush
			}

			connection.write(DELIMITER);
			return true;
		}
	}
}
