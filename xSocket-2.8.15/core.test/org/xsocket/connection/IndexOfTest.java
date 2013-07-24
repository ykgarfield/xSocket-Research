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


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class IndexOfTest {

	private static final String DELIMITER = "\r\n\r\n\r";
	
	private static final String TEST_MESSAGE_1 = "123456789";
	private static final String TEST_MESSAGE_2 = "1";
	

	
	@Test 
	public void testNonBlocking() throws Exception {
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);	


		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		int length = 233;
		byte[] testData = QAUtil.generateByteArray(length);
		connection.write((int) 45);
		connection.write(testData);
		connection.write(EchoHandler.DELIMITER);
		connection.flush();
		QAUtil.sleep(1000);

		int index = connection.indexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == (233 + 4));
		
		connection.readInt();
		
		index = connection.indexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == 233);
		
		connection.readBytesByDelimiter(EchoHandler.DELIMITER);
		
		index = connection.indexOf(EchoHandler.DELIMITER);
		Assert.assertEquals(-1, index);
		
		connection.close();
		server.close();
		
		System.out.println("OK");
	}

	
	
	@Test 
	public void testNonBlocking2() throws Exception {
		IServer server = new Server(new EchoHandler());
		ConnectionUtils.start(server);	


		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);
		
		int length = 233;
		byte[] testData = QAUtil.generateByteArray(length);
		connection.write(testData);
		connection.write(EchoHandler.DELIMITER);
		connection.flush();
		
		QAUtil.sleep(1000);

		int index = connection.indexOf(EchoHandler.DELIMITER);
		Assert.assertTrue(index == 233);

		byte[] response = connection.readBytesByDelimiter(EchoHandler.DELIMITER);
		Assert.assertArrayEquals(testData, response);
		
		connection.close();
		server.close();
	}
}
