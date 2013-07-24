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
import java.util.ArrayList;



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





/**
*
* @author grro@xsocket.org
*/
public final class DataTypesTest {

	

	@Test 
	public void testShort() throws Exception {
		IServer server = new Server(new ShortHandler());
		server.start();
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.write((short) 4);
		Assert.assertEquals(connection.readShort(), (short) 4);
		
		connection.close();
		server.close();
	}

	
	@Test 
	public void testMixed() throws Exception {
		
		DataTypesTestServerHandler hdl = new DataTypesTestServerHandler();
		IServer server = new Server(hdl);
		server.start();
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);

		double d = connection.readDouble();
		Assert.assertTrue("received value ist not excepted value ", d == 45.45);

		int i = connection.readInt();
		Assert.assertTrue("received value ist not excepted value ", i == 56);

		long l = connection.readLong();
		Assert.assertTrue("received value ist not excepted value ", l == 11);

		byte[] bytes = connection.readBytesByDelimiter("r", Integer.MAX_VALUE);
		byte[] expected = new byte[] {2, 78, 45, 78, 23, 11, 45, 78, 12, 56};
		for(int j = 0; j < bytes.length; j++) {
			if (bytes[j] != expected[j]) {
				Assert.fail("received value ist not excepted value ");
			}
		}
		
		String w = connection.readStringByLength(5);
		Assert.assertEquals(w, "hello");

		
		connection.write(33.33);
		connection.flush();
		
		connection.write((int) 11);
		connection.flush();
		
		connection.write((long) 33);
		connection.flush();
		
		connection.write("\r\n");
		connection.flush();
		
		connection.write("this is the other end tt");
		connection.flush();
		
		connection.write((byte) 34);
		connection.flush();
		
		connection.write(bytes);
		connection.flush();
		
		connection.write("r");
		connection.flush();
				
		connection.write("you");
		connection.flush();
		
		
		Assert.assertTrue(hdl.error.isEmpty());

		double d2 = connection.readDouble();
		
		Assert.assertTrue("received value ist not excepted value ", d2 == 65.65);
		
		server.close();
	}

	
	private static final class DataTypesTestServerHandler implements IDataHandler, IConnectHandler {

		int state = 0;
		private ArrayList<String> error = new ArrayList<String>(); 

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			
			connection.write(45.45);
			connection.write((int) 56);
			connection.write((long) 11);
			connection.write(new byte[] {2, 78, 45, 78, 23, 11, 45, 78, 12, 56});
			connection.write("r");
			connection.write("hello");
			connection.flush();

			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException {

			do {
				switch (state) {
				case 0:
					double d = connection.readDouble();
					if (d != 33.33) {
						error.add("received double ist not excepted value ");
					}
					state = 1;
					break;
	
				case 1:
					int i = connection.readInt();
					if (i != 11) {
						error.add("received int ist not excepted value ");
					}

					state = 2;
					break;
	
				case 2:
					long l = connection.readLong();
					if (l != 33) {
						error.add("received long ist not excepted value ");
					}

					state = 3;
					break;
	
				case 3:
					String s1 = connection.readStringByDelimiter("\r\n", Integer.MAX_VALUE);
					if (!s1.equals("")) {
						error.add("received string ist not excepted value ");
					}

					state = 4;
					break;
	
				case 4:
					String s2 = connection.readStringByDelimiter("tt", Integer.MAX_VALUE);

					if (!s2.equals("this is the other end ")) {
						error.add("received message ist not excepted value ");
					}
	
					state = 5;
					break;
	
				case 5:
					byte b = connection.readByte();
					if (b != (byte) 34) {
						error.add("received byte ist not excepted value ");						
					}
	
					state = 6;
					break;
	
				case 6:
					byte[] bytes = connection.readBytesByDelimiter("r", Integer.MAX_VALUE);
					byte[] expected = new byte[] {2, 78, 45, 78, 23, 11, 45, 78, 12, 56};
					for(int j = 0; j < bytes.length; j++) {
						if (bytes[j] != expected[j]) {
							error.add("received byte array ist not excepted value ");		
						}
					}
	
					state = 7;
					break;
					
				case 7:	
					String w = connection.readStringByLength(3);
					if (!w.equals("you")) {
						error.add("received you message ist not excepted value ");		
					}
	
					state = 99;
					connection.write(65.65);
					
					break;
				}
			} while (state != 99);

			connection.flush();
			return true;
		}
	}
	
	
	private static final class ShortHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readShort());
			return true;
		}
	}
}
