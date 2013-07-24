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
public final class AttachmentTest {


	@Test 
	public void testSimple() throws Exception {
		Handler hdl = new Handler();
		final IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		bc.setAutoflush(false);
		
		bc.setEncoding("US-ASCII");
		
		bc.write("24524232432");
		bc.flush();
		QAUtil.sleep(200);
		
		bc.write("22432");
		bc.flush();
		QAUtil.sleep(200);
		
		bc.write("224wwrwerwrrw32");
		bc.flush();
		QAUtil.sleep(200);
		
		
		bc.write("224w3253242");
		bc.flush();

		QAUtil.sleep(1000);
		
		
		Assert.assertTrue(hdl.read == 42);
		
		bc.close();
		server.close();
	}

	
	private static final class Handler implements IDataHandler, IConnectHandler {
		
		private int read = 0;
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAttachment(Integer.valueOf(0));
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.setAutoflush(false);
			
			Integer i = (Integer) connection.getAttachment();
			int av = connection.available();
			
			connection.readBytesByLength(av);
			
			i += av;
			connection.setAttachment(i);
			read = i;
			return true;
		}
	}
}
