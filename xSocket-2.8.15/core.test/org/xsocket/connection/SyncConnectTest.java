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
import java.nio.channels.ClosedChannelException;

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
public final class SyncConnectTest {


	@Test
	public void testSyncConnect() throws Exception {

	    IServer server = new Server(new ServerHandler());
		server.start();


		ClientHandler clientHdl = new ClientHandler();
		INonBlockingConnection clientCon = new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort(), clientHdl);

		QAUtil.sleep(500);
		Assert.assertEquals(1, clientHdl.getOnConnectCalled());
		Assert.assertEquals(0, clientHdl.getOnConnectException());
		
		clientCon.close();
		server.close();
	}

	
    
    @Test
    public void testSyncConnectTimeout() throws Exception {

        try {
            new BlockingConnection(InetAddress.getByName("localhost"), 40222, 500);
            Assert.fail("Exception expected");
        } catch (Exception expected) { }
    }

    
    
	
	
	
	private static final class ClientHandler implements IConnectHandler, IDataHandler, IConnectExceptionHandler {
	    
	    private int onConnectCalled = 0;
	    private int onConnectException = 0;
	    private String onConnectExceptionThread;
	    private String record;
	    

	    public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
	        onConnectCalled++;
	        return true;
	    }
	    
	    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
	        record = connection.readStringByDelimiter("\r\n");
	        return true;
	    }
	    
	    
	    
	    public boolean onConnectException(INonBlockingConnection connection, IOException ioe) throws IOException {
	        onConnectExceptionThread = Thread.currentThread().getName();
	        onConnectException++;
	        return true;
	    }
	    
	    String getRecord() {
	        return record; 
	    }
	    
	    
	    int getOnConnectCalled() {
	        return onConnectCalled;
	    }
	    
	    String getOnConnectExceptionThread() {
	        return onConnectExceptionThread;
	    }
	    
	    int getOnConnectException() {
	        return onConnectException;
	    }
	}
	
	
	private static final class ServerHandler implements IConnectHandler, IDataHandler {
	    
	    
	    public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

	        return true;
	    }

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
		    connection.write(connection.readStringByDelimiter("\r\n") + "\r\n");
		    return true;
		}
	}
}
