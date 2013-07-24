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
import java.util.ArrayList;
import java.util.List;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
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
public final class NonBlockingWriteLoopTest {

	private static final int LOOPS = 5;
	private static final int BUFFER_SIZE = 40;
	private static final byte[] record = QAUtil.generateByteArray(BUFFER_SIZE - 1);
	private static final String DELIMITER = "?";
	
	
	@Test 
	public void testReadWriteLoop() throws Exception {
		Dev0Handler hdl = new Dev0Handler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);
		
		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());

		ByteBuffer transferBuffer = ByteBuffer.allocateDirect(4096);
		for (int i = 0; i < LOOPS; i++) {
			// simulate read
			transferBuffer.put(record);
			transferBuffer.put(DELIMITER.getBytes("US-ASCII"));
			transferBuffer.flip();
			
			connection.write(transferBuffer);
			//connection.flush();
			
			transferBuffer.clear();
		}
		
		connection.close();
		
		QAUtil.sleep(1000);
		
		if (!hdl.errors.isEmpty()) {
			for (String error : hdl.errors) {
				System.out.println(error);
			}
			Assert.fail("received " + hdl.errors.size() + " invalid errors");
		}
		
		
		server.close();
	}

	
	

	
	private static final class Dev0Handler implements IDataHandler {
		
		private final List<String> errors = new ArrayList<String>();
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			byte[] bytes = connection.readBytesByDelimiter(DELIMITER);
			if (!QAUtil.isEquals(bytes, record)) {
				errors.add("reveived invalid record " + new String (bytes));
			} else {
				System.out.print(".");
			}
			return true;
		}
	}
}
