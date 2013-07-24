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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.junit.Assert;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;




/**
*
* @author grro@xsocket.org
*/
public final class EncodingTest {

	
	@Test 
	public void testSimple() throws Exception {
		
	    Server server = new Server(new Handler());
	    server.start();
	    
	    Socket socket = new Socket("localhost", server.getLocalPort());
	    InputStream is = socket.getInputStream();
	    OutputStream os = socket.getOutputStream();

	    os.write("ISO-8859-1\r\n".getBytes());
	    os.flush();
	        
	    Assert.assertEquals(97, is.read());
	    Assert.assertEquals(225, is.read());
	    Assert.assertEquals('\r', (char) is.read());
	    Assert.assertEquals('\n', (char) is.read());
	        

	    
	    os.write("UTF-8\r\n".getBytes());
	    os.flush();
	        
	    Assert.assertEquals(97, is.read());
	    Assert.assertEquals(195, is.read());
	    Assert.assertEquals(161, is.read());
	    Assert.assertEquals('\r', (char) is.read());
	    Assert.assertEquals('\n', (char) is.read());

        
        os.write("US-ASCII\r\n".getBytes());
        os.flush();
        
        Assert.assertEquals(97, is.read());
        Assert.assertEquals(63, is.read());
        Assert.assertEquals('\r', is.read());
        Assert.assertEquals('\n', is.read());


		server.close();
	}

	
	
	private static final class Handler implements IDataHandler {
	    
	    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
	        
	        String encoding = connection.readStringByDelimiter("\r\n");
	        connection.setEncoding(encoding);
	        
	        connection.write("a");
	        connection.write("\u00E1");
	        connection.write("\r\n");
	        
	        return true;
	    }
	}

}
