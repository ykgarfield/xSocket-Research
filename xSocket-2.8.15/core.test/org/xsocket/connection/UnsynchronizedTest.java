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
import java.util.concurrent.atomic.AtomicInteger;


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
public final class UnsynchronizedTest {

 
	private static final String DELIMITER = "\n\r";

	
	
	
	@Test 
	public void testSynchronizedHandler() throws Exception {

		SynchronizedServerHandler hdl = new SynchronizedServerHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		for (int j = 0; j < 100; j++) {
			connection.write("data " + DELIMITER);
			QAUtil.sleep(10);
		}
		connection.close();
		
		
		QAUtil.sleep(2000);
		
		Assert.assertTrue("Max concurrent is not 1 (" + hdl.maxConcurrent + ")", hdl.maxConcurrent == 1);
		
		server.close();
	}

	
	
	@Test 
	public void testDefaultSyncBehavior() throws Exception {

		DefaultServerHandler hdl = new DefaultServerHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		
		for (int j = 0; j < 100; j++) {
			connection.write("data " + DELIMITER);
			QAUtil.sleep(10);
		}
		connection.close();
		
		
		QAUtil.sleep(2000);
		
		Assert.assertTrue("Max concurrent is not 1 (" + hdl.maxConcurrent + ")", hdl.maxConcurrent == 1);
		
		server.close();
	}
	

	
	
	@Execution(Execution.MULTITHREADED)
	private static final class SynchronizedServerHandler implements IDataHandler {
		
		private AtomicInteger concurrent = new AtomicInteger();
		private int maxConcurrent = 0;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			int i = concurrent.incrementAndGet();
			if (i > maxConcurrent) {
				maxConcurrent = i;
			}
			
			try {
				connection.readByteBufferByDelimiter(DELIMITER);
				QAUtil.sleep(20);	
				
				return true;
			} finally {
				concurrent.decrementAndGet();				
			}

		}
	}
	
	
	// by default handlers are synchronized 
	private static final class DefaultServerHandler implements IDataHandler {
		
		private AtomicInteger concurrent = new AtomicInteger();
		private int maxConcurrent = 0;
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int i = concurrent.incrementAndGet();
			if (i > maxConcurrent) {
				maxConcurrent = i;
			}
			
			try {
				connection.readByteBufferByDelimiter(DELIMITER);
				QAUtil.sleep(20);
				
				return true;
			} finally {
				concurrent.decrementAndGet();				
			}
		}
	}

}
