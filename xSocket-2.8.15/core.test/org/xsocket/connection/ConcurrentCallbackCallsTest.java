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
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
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
public final class ConcurrentCallbackCallsTest {



	@Test 
	public void testSimple() throws Exception {
		
		TestHandler hdl = new TestHandler();
		final IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort());
					
		for (int i = 0; i < 20; i++) {
			connection.write((int) 4);
		}
		QAUtil.sleep(300);

		connection.close();
		server.close();

		Assert.assertTrue(hdl.errors.isEmpty());
	}
	



	private static class TestHandler implements IDataHandler {

		private int concurrent = 0;
		private List<String> errors = new ArrayList<String>();
		

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			try {
				concurrent++;
				
				if (concurrent != 1) {
					errors.add(concurrent + " concurrent calls");
				}
				int i = connection.readInt();
				connection.write(i);
				QAUtil.sleep(100);

			} finally {
				concurrent--;
			}
			
			return true;
		}
	}
}
