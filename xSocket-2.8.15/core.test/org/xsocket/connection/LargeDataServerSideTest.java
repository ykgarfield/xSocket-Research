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
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class LargeDataServerSideTest {


	
	@Test 
	public void tesSimple1() throws Exception {
		
		IServer server = new Server(new Handler());
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		int size = 50000;
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write(size);
		
		con.readBytesByLength(size);

		con.close();
		server.close();
		
		System.gc();
	}


	@Test 
	public void tesSimple2() throws Exception {
		
		IServer server = new Server(new Handler2());
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		int size = 50000;
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write(size);
		
		con.readBytesByLength(size);

		con.close();
		server.close();
		
		System.gc();
	}

	


	@Test 
	public void tesSimple3() throws Exception {
		
		IServer server = new Server(new Handler3());
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		int size = 50000;
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.write(size);
		

	
		con.readBytesByLength(size);

		con.close();
		server.close();
	}
	
	
	
	@Test 
	public void tesSimple4() throws Exception {
		
		IServer server = new Server(new Handler3());
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		int size = 150000;
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
		con.suspendReceiving();
		  
		con.write(size);
		
		QAUtil.sleep(500);
		con.resumeReceiving();
		
		while (con.available() < size) {
			QAUtil.sleep(100);
		}
		
		con.readBytesByLength(size);

		con.close();
		server.close();
		
		System.gc();
	}
	
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class Handler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
			
			int size = connection.readInt();
			
			byte[] data = QAUtil.generateByteArray(size);
			connection.write(data);
			
			return true;
		}
	}
	
	
	@Execution(Execution.NONTHREADED)
	private static final class Handler2 implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
			
			int size = connection.readInt();
			connection.suspendReceiving();
			
			byte[] data = QAUtil.generateByteArray(size);
			connection.write(data);
			
			return true;
		}
	}
	
	
	@Execution(Execution.NONTHREADED)
	private static final class Handler3 implements IDataHandler {
		
		public boolean onData(final INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
			
			final int size = connection.readInt();
			connection.suspendReceiving();
			
			Thread t = new Thread() {
			
				@Override
				public void run() {
					try {
						QAUtil.sleep(300);
						byte[] data = QAUtil.generateByteArray(size);
						connection.write(data);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
			t.start();
			
			return true;
		}
	}
}
