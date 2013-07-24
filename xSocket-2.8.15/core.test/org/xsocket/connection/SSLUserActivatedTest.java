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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import javax.net.SocketFactory;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class SSLUserActivatedTest {

	private static final Logger LOG = Logger.getLogger(SSLUserActivatedTest.class.getName());


	private static final String SSL_ON = "SSL_ON";
	private static final String DELIMITER = System.getProperty("line.separator");
	private static final String GREETING = "HELO";


	
	public static void main(String[] args) throws Exception {
		SSLUserActivatedTest test =  new SSLUserActivatedTest();
		
		for (int i = 0; i < 1000; i++) {
			test.testActivateSslOnConnect4();
		}
	}
	

	
	@Test
	public void testNotActivatable() throws Exception {

		IServer sslTestServer = new Server(0, new AdHocSSLHandler());
		sslTestServer.start();


		IBlockingConnection connection = new BlockingConnection("localhost", sslTestServer.getLocalPort());
		connection.setAutoflush(true);

		connection.write("test" + DELIMITER);
		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals("test", response);

		try {
			connection.activateSecuredMode();
			Assert.fail("IOException expected");
		} catch (IOException expected) { }

		connection.close();
        sslTestServer.close();
	}
	

	@Test
	public void testRepeatedActivation() throws Exception {
			
		Server sslTestServer = new Server(0, new SSLHandlerRepeatedActivation(), SSLTestContextFactory.getSSLContext(), false);
		sslTestServer.start();

		INonBlockingConnection connection = new NonBlockingConnection("localhost", sslTestServer.getLocalPort(),SSLTestContextFactory.getSSLContext(), false);
		connection.setAutoflush(true);

		connection.activateSecuredMode();
		connection.write("testi" + DELIMITER);

		String response = receive(connection, DELIMITER);
		Assert.assertEquals("testi", response);

		connection.close();
		sslTestServer.close();

	}



	@Test
	public void testActivateSslOnConnect() throws Exception {
		System.out.println("testActivateSslOnConnect...");

		IServer sslTestServer = new Server(0, new OnConnectSSLHandler(), SSLTestContextFactory.getSSLContext(), false);
		sslTestServer.start();

        SocketFactory socketFactory = SSLTestContextFactory.getSSLContext().getSocketFactory();
        Socket socket = socketFactory.createSocket("localhost", sslTestServer.getLocalPort());

        InputStream is = socket.getInputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

        StringBuilder requests = new StringBuilder();
        for (int i = 0; i < 3; i++) {
        	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf " + i + DELIMITER;
        	requests.append(req);
        	
        	pw.write(req);
        	pw.flush();
        }
            	
    	String res = QAUtil.toString(is, requests.toString().getBytes().length);

    	if (!requests.toString().equals(res)) {
    		System.out.println("response : " + requests.toString() + " is not equals request: "+ res);
    		Assert.fail("request != response");
    	}

    	pw.close();
    	socket.close();
        
        sslTestServer.close();
	}



	@Test
	public void testActivateSslOnConnect2() throws Exception {
		System.out.println("testActivateSslOnConnect2...");

		IServer sslTestServer = new Server(0, new OnConnectSSLHandler2(), SSLTestContextFactory.getSSLContext(), false);
		ConnectionUtils.start(sslTestServer);

		IBlockingConnection connection = new BlockingConnection("localhost", sslTestServer.getLocalPort(), SSLTestContextFactory.getSSLContext(), false);

		String greeting = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals(greeting, GREETING);

		connection.activateSecuredMode();

        for (int i = 0; i < 3; i++) {
        	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf " + i;
        	connection.write(req + DELIMITER);

        	String res = connection.readStringByDelimiter(DELIMITER);

        	if (!req.equals(res)) {
        		System.out.println("response : " + res + " is not equals request: "+ req);
        		Assert.fail("request != response");
        	}
        }

		connection.close();
		sslTestServer.close();
	}


	@Test
	public void testActivateSslOnConnect3() throws Exception {
		System.out.println("testActivateSslOnConnect3...");

		IServer sslTestServer = new Server(0, new OnConnectSSLHandler3(), SSLTestContextFactory.getSSLContext(), false);
		ConnectionUtils.start(sslTestServer);

		IBlockingConnection connection = new BlockingConnection("localhost", sslTestServer.getLocalPort(), SSLTestContextFactory.getSSLContext(), false);

		String greeting = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals(greeting, GREETING);

		connection.activateSecuredMode();

        for (int i = 0; i < 3; i++) {
        	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf " + i;
        	connection.write(req + DELIMITER);

        	String res = connection.readStringByDelimiter(DELIMITER);

        	if (!req.equals(res)) {
        		System.out.println("response : " + res + " is not equals request: "+ req);
        		Assert.fail("request != response");
        	}
        }

		connection.close();
		sslTestServer.close();
	}

	@Test
	public void testActivateSslOnConnect4() throws Exception {
		System.out.println("testActivateSslOnConnect4...");

		IServer sslTestServer = new Server(0, new OnConnectSSLHandler4(), SSLTestContextFactory.getSSLContext(), false);
		ConnectionUtils.start(sslTestServer);

        SocketFactory socketFactory = SSLTestContextFactory.getSSLContext().getSocketFactory();
        LOG.info("creating socket");
        Socket socket = socketFactory.createSocket("localhost", sslTestServer.getLocalPort());

        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));

        LOG.info("reading greeting");
        String greeting = lnr.readLine();
        Assert.assertEquals(greeting, GREETING);

        LOG.info("start write-read loop");
        for (int i = 0; i < 3; i++) {
        	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf " + i;
        	pw.write(req + DELIMITER);
        	pw.flush();

        	String res = lnr.readLine();

        	if (!req.equals(res)) {
        		System.out.println("response : " + res + " is not equals request: "+ req);
        		Assert.fail("request != response");
        	}
        }

        lnr.close();
        pw.close();
        socket.close();

        sslTestServer.close();
	}




	@Test
	public void testActivateSslOnConnect5() throws Exception {
		System.out.println("testActivateSslOnConnect5...");

		IServer sslTestServer = new Server(0, new OnConnectSSLHandler4(), SSLTestContextFactory.getSSLContext(), false);
		sslTestServer.start();


		IBlockingConnection connection = new BlockingConnection("localhost", sslTestServer.getLocalPort(), SSLTestContextFactory.getSSLContext(), false);

		connection.activateSecuredMode();

		String encyptedGreeting = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals(GREETING, encyptedGreeting);

        for (int i = 0; i < 3; i++) {
        	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf " + i;
        	connection.write(req + DELIMITER);

        	String res = connection.readStringByDelimiter(DELIMITER);

        	if (!req.equals(res)) {
        		System.out.println("response : " + res + " is not equals request: "+ req);
        		Assert.fail("request != response");
        	}
        }


		connection.close();
        sslTestServer.close();
	}

	
	

	@Test
	public void testActivateSslOnConnect5Bulk() throws Exception {
		for (int i = 0; i < 20; i++) {
			testActivateSslOnConnect5();
		}
	}
	
	

	@Test
	public void testBlocking() throws Exception {
		System.out.println("testblocking...");

		IServer sslTestServer = new Server(0, new AdHocSSLHandler(), SSLTestContextFactory.getSSLContext(), false);
		ConnectionUtils.start(sslTestServer);

		IBlockingConnection connection = new BlockingConnection("localhost", sslTestServer.getLocalPort(), SSLTestContextFactory.getSSLContext(), false);
		connection.setAutoflush(true);

		connection.write("test" + DELIMITER);
		String response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals("test", response);


		connection.write(SSL_ON + DELIMITER);
		response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals(SSL_ON, response);
		connection.activateSecuredMode();


		connection.write("a protected text" + DELIMITER);

		response = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
		Assert.assertEquals("a protected text", response);

		connection.close();
		sslTestServer.close();
	}


	@Test
	public void testNonBlockingMissingSSLFactory() throws Exception {
		System.out.println("testNonBlockingMissingSSLFactory...");

		IServer sslTestServer = new Server(0, new AdHocSSLHandler(), SSLTestContextFactory.getSSLContext(), false);
		ConnectionUtils.start(sslTestServer);


		INonBlockingConnection connection = new NonBlockingConnection("localhost", sslTestServer.getLocalPort());
		connection.setAutoflush(true);

		connection.write(SSL_ON + DELIMITER);

		String response = receive(connection, DELIMITER);
		Assert.assertEquals(SSL_ON, response);

		try {
			connection.activateSecuredMode();
			connection.write("testi" + DELIMITER);

			receive(connection, DELIMITER);

			Assert.fail("exception should have been thrown");
		} catch (IOException ioe) {
			// should been thrown because sslFactory is missing
		}


		connection.close();
		sslTestServer.close();
	}



	@Test
	public void testNonBlocking() throws Exception {
		System.out.println("testNonBlockingMissingSSLFactory...");

		IServer sslTestServer = new Server(0, new AdHocSSLHandler(), SSLTestContextFactory.getSSLContext(), false);
		ConnectionUtils.start(sslTestServer);

		INonBlockingConnection connection = new NonBlockingConnection("localhost", sslTestServer.getLocalPort(), SSLTestContextFactory.getSSLContext(), false);
		connection.setAutoflush(true);

		connection.write(SSL_ON + DELIMITER);

		String response = receive(connection, DELIMITER);
		Assert.assertEquals(SSL_ON, response);

		connection.activateSecuredMode();
		connection.write("testi" + DELIMITER);

		response = receive(connection, DELIMITER);
		Assert.assertEquals("testi", response);

		connection.close();
		sslTestServer.close();
	}

	@Test
	public void testLengthField() throws Exception {
	

		IServer sslTestServer = new Server(0, new OnConnectLengthFieldHandler(), SSLTestContextFactory.getSSLContext(), false);
		ConnectionUtils.start(sslTestServer);

        SocketFactory socketFactory = SSLTestContextFactory.getSSLContext().getSocketFactory();
        LOG.info("creating socket");
        Socket socket = socketFactory.createSocket("localhost", sslTestServer.getLocalPort());

        InputStream is = socket.getInputStream();
        OutputStream os = socket.getOutputStream();

        for (int i = 0; i < 3; i++) {
        	String req = "hello how are how sdfsfdsf sf sdf sf s sf sdf " + i;
        	byte[] data = req.getBytes();

        	ByteBuffer length = ByteBuffer.allocate(4);
        	length.putInt(data.length);
        	length.flip();
        	byte[] lengthBytes = length.array();

        	System.out.println("Client: writing length field");
        	os.write(lengthBytes);
        	
        	System.out.println("client: writing data");
        	os.write(data);

        	System.out.println("Client reading length field");
        	byte[] l = new byte[4];
        	is.read(l);

        	int rs = ByteBuffer.wrap(l).getInt();
        	byte[] responseBytes = new byte[rs];
        	
        	System.out.println("Client try to read " + rs + " content bytes");
        	is.read(responseBytes);

        	String res = new String(responseBytes);

        	if (!req.equals(res)) {
        		System.out.println("response : " + res + " is not equals request: "+ req);
        		Assert.fail("request != response");
        	}
        	
        	System.out.print(".");
        }

        is.close();
        os.close();
        socket.close();

        sslTestServer.close();
	}




	private String receive(INonBlockingConnection connection, String delimiter) throws IOException {
		String response = null;
		do {
			try {
				response = connection.readStringByDelimiter(delimiter, Integer.MAX_VALUE);
			} catch (BufferUnderflowException bue) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException ignore) { }
			}
		} while (response == null);

		return response;
	}


	private static final class OnConnectSSLHandler implements IDataHandler, IConnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.activateSecuredMode();
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);
			return true;
		}
	}


	private static final class OnConnectSSLHandler2 implements IDataHandler, IConnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.write(GREETING + DELIMITER);   // plain greeting
			connection.activateSecuredMode();
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER);
			connection.write(word + DELIMITER);
			return true;
		}
	}


	private static final class OnConnectSSLHandler3 implements IDataHandler, IConnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);

			connection.write(GREETING + DELIMITER);   // plain greeting
			connection.activateSecuredMode();

			connection.flush();
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER);
			connection.write(word + DELIMITER);
			connection.flush();
			return true;
		}
	}


	private static final class OnConnectSSLHandler4 implements IDataHandler, IConnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.activateSecuredMode();
			
			connection.write(GREETING + DELIMITER);   // encrypted greeting
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);
			return true;
		}
	}



	private static final class OnConnectLengthFieldHandler implements IDataHandler, IConnectHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);

			connection.activateSecuredMode();
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			System.out.println("Server onData available=" + connection.available());
			
			int length = ConnectionUtils.validateSufficientDatasizeByIntLengthField(connection);
			String data = connection.readStringByLength(length);

			connection.markWritePosition();
			connection.write((int) 0);

        	System.out.println("Server writing " + data.length() + " content bytes");
			int written = connection.write(data);

			System.out.println("Server reset to write mark and writing length");
			connection.resetToWriteMark();
			connection.write(written);

			connection.flush();
			return true;
		}
	}




	private static final class AdHocSSLHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			
			
			if (word.equals(SSL_ON)) {
				connection.suspendReceiving();
				
				connection.write(word + DELIMITER);
				connection.activateSecuredMode();
				
				connection.resumeReceiving();
				
				
			} else {
				connection.write(word + DELIMITER);
			}


			return true;
		}
	}


	private static final class SSLHandlerRepeatedActivation implements IConnectHandler, IDataHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.activateSecuredMode();

			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.activateSecuredMode();  // repeated call (will be ignored)

			String word = connection.readStringByDelimiter(DELIMITER, Integer.MAX_VALUE);
			connection.write(word + DELIMITER);
			return true;
		}
	}


}
