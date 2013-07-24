/*
 *  Copyright (c) xlightweb.org2006 - 2009 All rights reserved.
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
 * The latest copy of this software may be found on http://www.xlightweb.org/
 */
package org.xsocket.connection;


import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IServer;




/**
*
* @author grro@xlightweb.org
*/
public final class HanderDisconnectTest  {
	
	

	@Test
	public void disconnectTest() throws Exception {
		
		IServer server = new Server(new EchoHandler());
		server.start();
		
		
		Handler hdl = new Handler(); 
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);
		QAUtil.sleep(500);
		
		server.close();
        QAUtil.sleep(500);

        Assert.assertFalse(hdl.isOpen());
		
		con.close();
	}
	
	
	private final class Handler implements IDataHandler {
	    
	    private final AtomicBoolean isOpen = new AtomicBoolean(true);
	    
	    @Execution(Execution.NONTHREADED)
	    public boolean onData(INonBlockingConnection connection) throws IOException {
	        isOpen.set(connection.isOpen());
	        return true;
	    }
	    
	    
	    boolean isOpen() {
	        return isOpen.get();
	    }
	}
	

	
	
	private static final class EchoHandler implements IDataHandler {
	
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
		    connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}		
	}
}