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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class UncleanDisconnectProcessKillTest {

	private static final String DELIMITER = "\r\n";


	@Test
	public void testLiveBlockingClientServerClose() throws Exception {

		// start the server
		JavaProcess jp = new JavaProcess();
		InputStream is = jp.start("EchoServer", "http://xsocket.sourceforge.net/test2.jar");
		LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
		String port = lnr.readLine();
		
		QAUtil.sleep(3000);

		IBlockingConnection connection = new BlockingConnection("localhost", Integer.parseInt(port));
		connection.setAutoflush(true);

		String request = "request";
		connection.write(request + DELIMITER);
		String response = connection.readStringByDelimiter(DELIMITER);

		Assert.assertTrue(request.endsWith(response));

		// kill server process
		jp.terminate();
		QAUtil.sleep(3000);

		try {
			connection.write("rt");
			String msg = "testBlockingClientServerClose: an ClosedConnectionException should have been thrown";
			System.out.println(msg);
			Assert.fail(msg);
		} catch (ClosedChannelException expected) { }
	}
	

	@Test
	public void testLiveNonBlockingClientServerClose() throws Exception {

		// start the server
		JavaProcess jp = new JavaProcess();
		InputStream is = jp.start("EchoServer", "http://xsocket.sourceforge.net/test2.jar");
		LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
		String port = lnr.readLine();
		
		QAUtil.sleep(3000);


		ClientHandler cHdl = new ClientHandler();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", Integer.parseInt(port), cHdl);
		connection.setAutoflush(true);

		connection.write("test");


		// kill server process
		jp.terminate();
		QAUtil.sleep(3000);

		if (!cHdl.isDisconnected) {
			String msg = "testNonBlockingClientServerClose: hdl is not disconnected";
			System.out.println(msg);
			Assert.fail(msg);
		}

		try {
			connection.write("rt");
			String msg = "testNonBlockingClientServerClose: an ClosedConnectionException should have been thrown";
			System.out.println(msg);
			Assert.fail(msg);
		} catch (ClosedChannelException expected) { }
	}




	@Test
	public void testLiveClientClose() throws Exception {
	/*    
	    
		String host = "localhost";
		String port = "9989";

		ServerHandler hdl = new ServerHandler();
		IServer server = new Server(hdl);
		server.start();


		// start the client
		JavaProcess jp = new JavaProcess();
		jp.start("org.xsocket.stream.TestClient", "http://xsocket.sourceforge.net/test.jar", "localhost", Integer.toString(server.getLocalPort()));
		QAUtil.sleep(1500);

		if (hdl.isDisconnected) {
			String msg = "testClientClose: hdl should been connected";
			System.out.println(msg);
			Assert.fail(msg);
		}


		// kill the client
		jp.terminate();
		QAUtil.sleep(3000);

		if (!hdl.isDisconnected) {
			String msg = "testClientClose: hdl should been disconnected";
			System.out.println(msg);
			Assert.fail(msg);
		}

		server.close();
		*/
	}


	private static final class ServerHandler implements IDataHandler, IDisconnectHandler {

		private boolean isDisconnected = false;
		private int countReceived = 0;

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.readStringByDelimiter(DELIMITER);
			countReceived++;
			return true;
		}
	}




	private static final class ClientHandler implements IDisconnectHandler {
		private boolean isDisconnected = false;

		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isDisconnected = true;
			return true;
		}
	}
}
