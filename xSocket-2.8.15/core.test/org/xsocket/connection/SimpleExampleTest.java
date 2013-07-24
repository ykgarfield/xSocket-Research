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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;




/**
*
* @author grro@xsocket.org
*/
public final class SimpleExampleTest {
	

	@Test 
	public void testAsyncConnect() throws Exception {

		IHandler hdl = new EchoHandler();
		IServer srv = new Server(0, hdl);
		srv.start();
		
		int serverPort = srv.getLocalPort();

		
		AsyncConnectClientHandler clientHdl = new AsyncConnectClientHandler();
		INonBlockingConnection nbc = new NonBlockingConnection(InetAddress.getByName("localhost"), serverPort, clientHdl, false, 2000);
		// greeting will be send implicitly (by the onConnect method)
		
		
		QAUtil.sleep(1000);
		Assert.assertTrue(clientHdl.isEchoReceived());

		
		nbc.close();
		srv.close();
	}
	

	@Test 
	public void testSyncConnect() throws Exception {

		IHandler hdl = new EchoHandler();
		IServer srv = new Server(0, hdl);
		srv.start();
		
		int serverPort = srv.getLocalPort();

		
		SyncConnectClientHandler clientHdl = new SyncConnectClientHandler();
		INonBlockingConnection nbc = new NonBlockingConnection(InetAddress.getByName("localhost"), serverPort, clientHdl, true, 2000);

		// send greeting
		nbc.write("Hello\r\n");
		
		QAUtil.sleep(1000);
		Assert.assertTrue(clientHdl.isEchoReceived());

		
		nbc.close();
		srv.close();
	}

	
	

	
	public static class AsyncConnectClientHandler implements IConnectHandler, IDataHandler, IConnectExceptionHandler  {
		
		private final AtomicBoolean isEchoReceived = new AtomicBoolean(false);

	    public boolean onConnect(final INonBlockingConnection nbc) throws IOException {
	    	System.out.println("hello server i'm connected");

	    	// send greeting
	    	nbc.write("Hello\r\n");
	    	return true;
	    }

	       

	    public boolean onConnectException(INonBlockingConnection nbc,IOException ioe) throws IOException {
	    	System.out.println("Error: " + ioe.getMessage());
	    	return true;
	    }

	           

	    public boolean onData(INonBlockingConnection nbc) throws  IOException {
	    	System.out.println(nbc.readStringByDelimiter("\r\n"));
	    	isEchoReceived.set(true);
	    	return true;
	    }
	    
	    
	    boolean isEchoReceived() {
	    	return isEchoReceived.get();
	    }
	}	  

	

	
	public static class SyncConnectClientHandler implements IDataHandler, IConnectExceptionHandler  {
		
		private final AtomicBoolean isEchoReceived = new AtomicBoolean(false);

	    public boolean onConnectException(INonBlockingConnection nbc,IOException ioe) throws IOException {
	    	System.out.println("Error: " + ioe.getMessage());
	    	return true;
	    }

	           

	    public boolean onData(INonBlockingConnection nbc) throws  IOException {
	    	System.out.println(nbc.readStringByDelimiter("\r\n"));
	    	isEchoReceived.set(true);
	    	return true;
	    }
	    
	    
	    boolean isEchoReceived() {
	    	return isEchoReceived.get();
	    }
	}	  

	
	
	
	
	public static final class EchoHandler implements IDataHandler {
	         
		public boolean onData(INonBlockingConnection nbc) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			String data = nbc.readStringByDelimiter("\r\n");
			nbc.write("Line received: "+ data + "\r\n");
			System.out.println(data);
			return true;
		}
	}
}
