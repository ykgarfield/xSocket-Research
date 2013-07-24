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
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class ExceptionInHandlerTest {
	

	@Test 
	public void testBufferUnderflow() throws Exception {
	    Server server = new Server(new DataHandler());
	    server.start();

	    
	    IDataHandler dh = new IDataHandler() {
	        public boolean onData(INonBlockingConnection connection) throws IOException {
	            throw new BufferUnderflowException();
	        }
	    };
	    
	    INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), dh);
	    con.write("test");
	    
	    QAUtil.sleep(1000);
	    Assert.assertTrue(con.isOpen());
	    
	    con.close();
	    server.close();
	}
	
	
	@Test 
    public void testRuntime() throws Exception {
        Server server = new Server(new DataHandler());
        server.start();

        
        IDataHandler dh = new IDataHandler() {
            public boolean onData(INonBlockingConnection connection) throws IOException {
                throw new RuntimeException("error");
            }
        };
        
        INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), dh);
        con.write("test");
        
        QAUtil.sleep(1000);
        Assert.assertFalse(con.isOpen());
        
        con.close();
        server.close();
    }
	
	
    @Test 
    public void testIO() throws Exception {
        Server server = new Server(new DataHandler());
        server.start();

        
        IDataHandler dh = new IDataHandler() {
            public boolean onData(INonBlockingConnection connection) throws IOException {
                throw new IOException("error");
            }
        };
        
        INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), dh);
        con.write("test");
        
        QAUtil.sleep(1000);
        Assert.assertFalse(con.isOpen());
        
        con.close();
        server.close();
    }    
	
	private static final class DataHandler implements IDataHandler {
	    
	    public boolean onData(INonBlockingConnection connection) throws IOException {
	        ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
	        connection.write(data);
	        return true;
	    }
	}
}
