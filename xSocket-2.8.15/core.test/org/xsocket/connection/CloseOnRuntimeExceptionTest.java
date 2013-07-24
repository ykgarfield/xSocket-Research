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
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class CloseOnRuntimeExceptionTest {

	
	@Test 
	public void testSimple() throws Exception {
		
		IServer server = new Server(new BadHandlerHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write("test");
		
		QAUtil.sleep(1000);
		
		try {
			con.readByte();
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }
		
		
		
		server.close();
	}
	
	
	@Test 
	public void testDelayed() throws Exception {
		
		IServer server = new Server(new DelayedBadHandlerHandler());
		server.start();
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write("test");
		
		try {
			con.readByte();   // within call the server will close the connection
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }
		
		
		
		server.close();
	}
	
	
	
	private static final class BadHandlerHandler implements IDataHandler {
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
			
			Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
				
					public void uncaughtException(Thread t, Throwable e) {
						
					}
				}
			);
			
			throw new RuntimeException("error");
		}		
	}
	
	
	private static final class DelayedBadHandlerHandler implements IDataHandler {
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
			
			Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
				
					public void uncaughtException(Thread t, Throwable e) {
						
					}
				}
			);
			
			QAUtil.sleep(1000);
			
			throw new RuntimeException("error");
		}		
	}
}
