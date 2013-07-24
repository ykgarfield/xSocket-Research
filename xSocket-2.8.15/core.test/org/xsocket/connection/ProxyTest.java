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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class ProxyTest  {

	private static final Logger LOG = Logger.getLogger(ProxyTest.class.getName());
	
	
	private AtomicInteger running = new AtomicInteger(0);
	private final List<String> errors = new ArrayList<String>();


	@Test
	public void testLiveSimple() throws Exception {	
	    
		final String host = "www.web.de";

		System.out.println("run proxy test by calling " + host);

		final Proxy proxy = new Proxy(0, host, 80);
		proxy.start();
		ConnectionUtils.registerMBean(proxy);
		
		
		
		for (int i = 0; i < 5; i++) {
			
			final int num = i;
			
			Thread t = new Thread() {
				@Override
				public void run() {
					running.incrementAndGet();
					System.out.println("starting " + num);

					for (int j = 0; j < 20; j++) {
						try {
							LOG.fine("creating connection to proxy and send request");
							IBlockingConnection con = new BlockingConnection("localhost", proxy.getLocalPort());
							con.write("GET / HTTP/1.1\r\n" +
									  "Host: " + host + "\r\n" + 
									  "User-Agent: me\r\n\r\n");
							
							LOG.fine("[" + con.getId() + "] reading response header");
							String responseHeader = con.readStringByDelimiter("\r\n\r\n");

							if ((responseHeader.contains("200")) || (responseHeader.contains("301"))) {
							    System.out.print(".");
							} else {
							    errors.add("got " + responseHeader);
							}
							
							LOG.fine("[" + con.getId() + "] closing connection");
							con.close();
						} catch (IOException e) {
							e.printStackTrace();
							errors.add(e.toString());
							break;
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
			System.out.println("ERROR: " + error);
		}
		
		Assert.assertTrue(errors.isEmpty());
	}
}
