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


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
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
public final class DecoupledReceiveAndConsumeTest  {


	private static final String DELIMITER = "\n";
	private static final int SLEEPTIME = 300;
		
	


	@Test 
	public void testSimple() throws Exception {
		TestHandler hdl = new TestHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		bc.setAutoflush(false);
		
		// send first package & wait 
		bc.write(QAUtil.generateByteArray(600));
		bc.write(DELIMITER);
		bc.flush();

		QAUtil.sleep(SLEEPTIME);
		
		
		// send second one
		bc.write(QAUtil.generateByteArray(1200));
		bc.write(DELIMITER);
		bc.flush();
		
		QAUtil.sleep(SLEEPTIME);
	
		
		
		// 600 byte (first call) should have been read by the server handler
		Assert.assertTrue("consumed data size is " + hdl.readSize + " but should be 600", hdl.readSize == 600);

		// and 1200 of the second call should already be in the receive queue
		Assert.assertTrue("receivedAndonConsumend data size is " + hdl.connection.available() + " but should be " + (1200 + DELIMITER.length()), hdl.connection.available() == (1200 + DELIMITER.length()));


		bc.close();
		server.close();
	}
	

	private static final class TestHandler implements IConnectHandler, IDataHandler {
		
		private int readSize = 0;
		private NonBlockingConnection connection = null;
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			this.connection = (NonBlockingConnection) connection;
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			byte[] bytes = connection.readBytesByDelimiter(DELIMITER);
			readSize = bytes.length;
			
			// simulate blocking
			try {
				Thread.sleep(1000000);
			} catch (InterruptedException ignore) {  }
		

			
			return true;
		}
	}
}
