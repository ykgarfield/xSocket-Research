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


import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class HandlerSynchronizeTest {

	private static final String DELIMITER = "\n";
	
	private static final int LOOPS = 20;
	private static final int SLEEP_TIME = 50;


	
	@Test 
	public void testSynchronized() throws Exception {
		SynchronizedHandler hdl = new SynchronizedHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);;

		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
			
		for (int j = 0; j < LOOPS; j++) {
			String request = "record";
			connection.write(request + DELIMITER);
				
			QAUtil.sleep(SLEEP_TIME / 5);
		}
			
		connection.close();
		server.close();
		
		Assert.assertEquals(1, hdl.counter.getMax());
	}

	
	
	
	
	@Execution(Execution.MULTITHREADED)
	private final class SynchronizedHandler implements IDataHandler {
		
		private final Counter counter = new Counter();
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			counter.inc();
			try {
				connection.readStringByDelimiter(DELIMITER);
				QAUtil.sleep(SLEEP_TIME);

			} finally {
				counter.dec();
			}
			
			return true;
		}
	}
	
	
	private static final class Counter {
		
		private int i = 0;
		private int max = 0;
		
		public synchronized void inc() {
			i++;
			if (i > max) {
				max = i;
			}
		}
		
		public synchronized void dec() {
			i--;
		}
		
		public synchronized int getMax() {
		    return max;
		}
	}
}
