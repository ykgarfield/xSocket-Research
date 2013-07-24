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
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class ReadableByteBufferTest {

	
	private static final int SIZE = 1000;
	
	
	
	public static void main(String[] args) throws Exception {
        for (int i = 0; i < 100; i++) {
            new ReadableByteBufferTest().testSimple();
        }
    }
	

	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server);
	
		
		INonBlockingConnection nbc = new NonBlockingConnection("localhost", server.getLocalPort());
		
		QAUtil.sleep(300);
		
		ByteBuffer buffer = ByteBuffer.allocate(300);
		int i = nbc.read(buffer);
		
		Assert.assertEquals(300, i);

		
		buffer = ByteBuffer.allocate(300);
		i = nbc.read(buffer);
		
		Assert.assertEquals(300, i);
		
		
		buffer = ByteBuffer.allocate(600);
		i = nbc.read(buffer);
		
		Assert.assertEquals(400, i);
		
		
		buffer = ByteBuffer.allocate(300);
		i = nbc.read(buffer);
		
		Assert.assertEquals(0, i);
		
		
		server.close();
		QAUtil.sleep(500);
		
		buffer = ByteBuffer.allocate(300);
		i = nbc.read(buffer);
		
		Assert.assertEquals(-1, i);
		

		nbc.close();

		buffer = ByteBuffer.allocate(300);
		i = nbc.read(buffer);
		Assert.assertEquals(-1, i);
	}


	
	
	private static final class ServerHandler implements IConnectHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(QAUtil.generateByteArray(SIZE));
			return true;
		}
	}
}
