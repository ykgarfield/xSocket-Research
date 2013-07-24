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
import java.net.InetAddress;
import java.nio.BufferUnderflowException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class WaitForConnectTest {

 

	
	@Test 
	public void testNonWait() throws Exception {
		Handler hdl = new Handler();
		
		new NonBlockingConnection(InetAddress.getByName("199.9.9.9"), 999, hdl, false, 1000);
		
		QAUtil.sleep(5000);
		
		Assert.assertEquals(0, hdl.onConnectCalled);
		Assert.assertEquals(0, hdl.onDataCalled);
		Assert.assertEquals(0, hdl.onDisconnectCalled);
		Assert.assertEquals(1, hdl.onConnectExceptionCalled);
	}


	
	
	private static final class Handler implements IConnectHandler, IConnectExceptionHandler, IDataHandler, IDisconnectHandler {
		
		private int available = 0;
		private int onConnectCalled = 0;
		private int onConnectExceptionCalled = 0;
		private int onDisconnectCalled = 0;
		private int onDataCalled = 0;
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			onConnectCalled++;
			return true;
		}
		
		public boolean onConnectException(INonBlockingConnection connection, IOException ioe) throws IOException {
		    onConnectExceptionCalled++;
            return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			onDataCalled++;
			available = connection.available();
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			onDisconnectCalled++;
			return true;
		}

		public int getOnConnectCalled() {
			return onConnectCalled;
		}

		public int getOnConnectExceptionCalled() {
		    return onConnectExceptionCalled;
		}

		
		public int getOnDisconnectCalled() {
			return onDisconnectCalled;
		}

		public int getOnDataCalled() {
			return onDataCalled;
		}
		
		public int getAvailable() {
			return available;
		}
	}

}
