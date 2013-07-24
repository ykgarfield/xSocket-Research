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
import java.nio.channels.ClosedChannelException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class HandlerThrowsIOExceptionTest {
	
	private static final String DELIMITER = "\r";
	private static final String THROW_EXCPTION_CMD = "throw a io exception!";
	
	
	@Test 
	public void testDataHandler() throws Exception {

		Handler hdl = new Handler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		
		connection.write("some cmd" + DELIMITER);
		String result = connection.readStringByDelimiter(DELIMITER);
		Assert.assertEquals(result, "some cmd");
		
		try {
			connection.write(THROW_EXCPTION_CMD + DELIMITER);
			
			QAUtil.sleep(1000);
			Assert.assertFalse(hdl.getConnection().isOpen());
			
			String result2 = connection.readStringByDelimiter(DELIMITER);
			Assert.fail("a ClosedConnectionException should have been thrown");
		} catch (ClosedChannelException shouldbeThrown) { }
		
		
		connection.close();
		server.close();		
	}

	
	
	private static final class Handler implements IConnectHandler, IDataHandler {
		
		private INonBlockingConnection connection = null;
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			this.connection = connection;
			
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			String cmd = connection.readStringByDelimiter(DELIMITER); 
			if (cmd.equals(THROW_EXCPTION_CMD)) {
				throw new IOException("kill");
			} else {
				connection.write(cmd + DELIMITER);
			} 
			return true;
		}	
		
		
		INonBlockingConnection getConnection() {
			return connection;
		}
	}
}
