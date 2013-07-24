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
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class ThreadedSSLProxyTest  {


	private AtomicInteger running = new AtomicInteger(0);
	private final List<String> errors = new ArrayList<String>();


	@Test
	public void testPrestarted() throws Exception {

		IServer server = new Server(new BusinessService());
		server.start();
		
		final IServer proxy = new ThreadedSSLProxy(0, "localhost", server.getLocalPort(), true);
		proxy.start();
		
		
		

		for (int i = 0; i < 3; i++) {
			
			final int num = i;
			
			Thread t = new Thread() {
				@Override
				public void run() {
					running.incrementAndGet();
					System.out.println("starting " + num);

					for (int j = 0; j < 10; j++) {
						try {
							IBlockingConnection con = new BlockingConnection("localhost", proxy.getLocalPort(), SSLTestContextFactory.getSSLContext(), true);
							String text = "test234567";
							con.write(text + "\r\n");
							String response =  con.readStringByDelimiter("\r\n");

							if (!response.equals(text)) {
								errors.add(text + " != " + response);
							} else {
								System.out.print(".");
							}

							con.close();
						} catch (IOException e) {
							e.printStackTrace();
							errors.add(e.toString());
						}
					}

					System.out.println("closing " + num);
					running.decrementAndGet();
				}

			};

			t.start();

		}


		do {
			QAUtil.sleep(100);
		} while (running.get() > 0);

		proxy.close();

		for (String error : errors) {
			System.out.println(error);
		}
		
		Assert.assertTrue(errors.isEmpty());
	}
	

	
	private static final class BusinessService implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			try {
				String text = connection.readStringByDelimiter("\r\n");
				connection.write(text + "\r\n");
			} catch (SocketTimeoutException se) {
				se.printStackTrace();
				throw se;
			}
			
			return true;
		}
		
	}
}
