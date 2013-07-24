/*
 *  Copyright (c) xlightweb.org, 2008 - 2009. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xlightweb.org/
 */
package org.xsocket.connection;




import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;




/**
*
* @author grro@xlightweb.org
*/
public final class ServerSideCloseTest  {
	

	private final AtomicInteger running = new AtomicInteger(0);
	private final List<String> errors  = new ArrayList<String>();
	
	
	
	@Before
	public void setup() {
		running.set(0);
		errors.clear();
	}

	
	
	@After
	public void teardown() {
		System.gc();
	}
	
	
	

	@Test
	public void testNonPooledWebServer() throws Exception {
		
		System.out.println("testNonPooledWebServer");
		final IServer server = new Server(new WebHandler());
		server.start();
		
		QAUtil.sleep(3000);

	
		for (int i =0; i < 5; i++) {
			new Thread() {
				@Override
				public void run() {

					running.incrementAndGet();
					try {
						WebClient webClient = new WebClient(server.getLocalPort());

						for (int j = 0; j< 1000; j++) {
							webClient.call("200", "OK");
						}
						
						webClient.close();

						
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.toString());
						
					} finally {
						running.decrementAndGet();
					}
					
				}
			}.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);
		
		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
		
		server.close();
	}
	
	
	
	@Test
	public void testApacheClientPooledWebServer() throws Exception {
		
		System.out.println("testApacheClientPooledWebServer");
		
		final IServer server = new Server(new WebHandler());
		server.start();
		
	
		for (int i =0; i < 5; i++) {
			new Thread() {
				@Override
				public void run() {
	
					running.incrementAndGet();
					try {
						org.apache.commons.httpclient.HttpClient httpClient = new org.apache.commons.httpclient.HttpClient();
						httpClient.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(0, false));
						
						for (int j = 0; j< 1000; j++) {
							
							GetMethod getMeth = new GetMethod("http://localhost:" + server.getLocalPort() + "/");
							httpClient.executeMethod(getMeth);
							
							Assert.assertEquals(200, getMeth.getStatusCode());
							Assert.assertEquals("OK", getMeth.getResponseBodyAsString());
							
							getMeth.releaseConnection();
						}
	
						
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.toString());
						
					} finally {
						running.decrementAndGet();
					}
					
				}
			}.start();
		}
	
		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);
		
		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
		
		server.close();
	
	}



	@Test
	public void testApacheClientPooledWebServer2() throws Exception {
		
		System.out.println("testApacheClientPooledWebServer");
		
		final IServer server = new Server(new WebHandler2());
		server.start();
		
	
		for (int i =0; i < 2; i++) {
			new Thread() {
				@Override
				public void run() {

					running.incrementAndGet();
					try {
						org.apache.commons.httpclient.HttpClient httpClient = new org.apache.commons.httpclient.HttpClient();
						httpClient.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(0, false));
						
						for (int j = 0; j< 3; j++) {
							
							GetMethod getMeth = new GetMethod("http://localhost:" + server.getLocalPort() + "/");
							httpClient.executeMethod(getMeth);
							
							Assert.assertEquals(200, getMeth.getStatusCode());
							Assert.assertEquals(50000, getMeth.getResponseBodyAsString().getBytes().length);
							
							getMeth.releaseConnection();
						}

						
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.toString());
						
					} finally {
						running.decrementAndGet();
					}
					
				}
			}.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);
		
		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
		
		server.close();

	}
	
	
	
	

		
	

	@Test
	public void testnonPooledJetty() throws Exception {
		
		System.out.println("testnonPooledJetty");

		final WebContainer webContainer = new WebContainer(new MyServlet());
		webContainer.start();

	
		for (int i =0; i < 5; i++) {
			new Thread() {
				@Override
				public void run() {

					running.incrementAndGet();
					try {
						
						WebClient webClient = new WebClient(webContainer.getLocalPort());

						for (int j = 0; j< 1000; j++) {
							webClient.call("200", "OK");
						}
							
						webClient.close();
						
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.toString());
						
					} finally {
						running.decrementAndGet();
					}
					
				}
			}.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);
		
		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
		
		webContainer.stop();
	}
	

	

	@Test
	public void testPooledJetty() throws Exception {
		
		System.out.println("testPooledJetty");

		final WebContainer webContainer = new WebContainer(new MyServlet());
		webContainer.start();
		
	
		for (int i =0; i < 5; i++) {
			new Thread() {
				@Override
				public void run() {

					running.incrementAndGet();
					try {
						WebClient webClient = new WebClient(webContainer.getLocalPort());

						for (int j = 0; j< 1000; j++) {
							webClient.callPooled("200", "OK");
						}
							
						webClient.close();
						
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.toString());
						
					} finally {
						running.decrementAndGet();
					}
					
				}
			}.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);
		
		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
		
		webContainer.stop();
	}
	

	
	
	private static final class MyServlet extends HttpServlet {
		
		private static final long serialVersionUID = -2014260619889214833L;

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			resp.setContentType("text/plain");
			resp.setHeader("Connection", "close");
			resp.getWriter().write("OK");
		}
	}
	

	@Ignore
	@Test
	public void testPooledWebServer() throws Exception {
		
		final IServer server = new Server(new WebHandler());
		server.start();
		
	
		for (int i =0; i < 3; i++) {
			new Thread() {
				@Override
				public void run() {

					running.incrementAndGet();
					try {
						WebClient webClient = new WebClient(server.getLocalPort());

						for (int j = 0; j< 1000; j++) {
							webClient.callPooled("200", "OK");
						}
							
						webClient.close();

						
					} catch (Exception e) {
						e.printStackTrace();
						errors.add(e.toString());
						
					} finally {
						running.decrementAndGet();
					}
					
				}
			}.start();
		}

		do {
			QAUtil.sleep(200);
		} while (running.get() > 0);
		
		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
		
		server.close();
	}
	
	
	

	
	
	
	private static final class WebClient {
		
		private final int port;
		private final BlockingConnectionPool pool = new BlockingConnectionPool(); 
		
		public WebClient(int port) {
			this.port = port;
		}
		
		public void close() throws IOException {
			pool.close();
		}

		public void call(String expectedStatus, String expectedBody) throws IOException {
			IBlockingConnection con  = new BlockingConnection("localhost", port);
			try {
				call(expectedStatus, expectedBody, con);
			} finally {
				con.close();
			}
		}
		
		
		public void callPooled(String expectedStatus, String expectedBody) throws IOException {
			
			IBlockingConnection con  = null;
			try {
				con = pool.getBlockingConnection("localhost", port);
				call(expectedStatus, expectedBody, con);				
			} finally {
				con.close();
			}
		}

		
		private void call(String expectedStatus, String expectedBody, IBlockingConnection con) throws IOException {
			
			con.write("GET / HTTP/1.1\r\n" + 
					  "Host: localhost:" + port + "\r\n" +
					  "User-Agent: meT\r\n" +
					  "\r\n");
			
			String header = con.readStringByDelimiter("\r\n\r\n");
			if (header.indexOf(expectedStatus) == -1) {
				System.out.println("did not get status " + expectedStatus  + ": " + header);
				throw new IOException("did not get status " + expectedStatus  + ": " + header);
			}
			
			String msg = con.readStringByLength(expectedBody.getBytes().length);
			if (!msg.equals(expectedBody)) {
				System.out.println("did not get body " + expectedBody + ": " + msg);
				throw new IOException("did not get body " + expectedBody + ": " + msg);
			}
			
			if (header.indexOf("Connection: close") != -1) {
				pool.destroy(con);
			}
		}
	}
	
	
	private static final class WebHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.readStringByDelimiter("\r\n\r\n");
			
			try {
				connection.write("HTTP/1.1 200 OK\r\n" + 
						         "Server: its me\r\n" +
						         "Content-Length: 2\r\n" +
						         "Connection: close\r\n" +
						         "Content-Type: text/plain; charset=UTF-8\r\n" +
						         "\r\n" +
						         "OK");
			} catch (SocketTimeoutException ste) {
				System.out.println("write timeout reached");
				ste.printStackTrace();
			}
			connection.close();
			return true;
		}
	}
	
	
	private static final class WebHandler2 implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.setOption(IConnection.SO_SNDBUF, 512);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.readStringByDelimiter("\r\n\r\n");
			
			try {
				connection.write("HTTP/1.1 200 OK\r\n" + 
						         "Server: its me\r\n" +
						         "Content-Length: 100000\r\n" +
						         "Connection: close\r\n" +
						         "Content-Type: text/plain; charset=UTF-8\r\n" +
						         "\r\n" +
						         new String(QAUtil.generateByteArray(50000)));
			} catch (SocketTimeoutException ste) {
				System.out.println("write timeout reached");
				ste.printStackTrace();
			}
			connection.close();
			return true;
		}
	}
}