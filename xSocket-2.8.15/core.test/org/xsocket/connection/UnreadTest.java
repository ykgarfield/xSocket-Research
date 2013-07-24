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
import java.nio.channels.ClosedChannelException;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class UnreadTest {
	
	
	
	@Test 
	public void testSimpleText() throws Exception {
	    IServer server = new Server(new ServerHandler());
	    server.start();
	    
	    
	    IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
	    con.write("CA record: d-tdf");
	    QAUtil.sleep(1000);

	    String txt = con.readStringByLength(15);
	    
	    // check if new type AA
	    if (txt.indexOf("type=AA") != -1) {
	        int length = con.readInt();
	        // ...
	        
	    // .. no
	    } else {
	        // perform unread and check for type CA
	        con.unread(txt);
	        
	        // read CA record
	        String caHeader = con.readStringByDelimiter(":");
	        Assert.assertEquals("CA record", caHeader);
	        
	    }

	    
	    con.close();
	    server.close();
	}
	
	

    @Test 
    public void testSimple() throws Exception {
        IServer server = new Server(new ServerHandler());
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
        con.write("line one\r\n");
        con.write("line two\r\n");
        con.write("line three\r\n");
        
        
        Assert.assertEquals("line one", con.readStringByDelimiter("\r\n"));
        Assert.assertEquals("line two", con.readStringByDelimiter("\r\n"));

        con.unread(new ByteBuffer[] { DataConverter.toByteBuffer("line two\r\n", "US-ASCII") });
        con.unread(new ByteBuffer[] { DataConverter.toByteBuffer("line one\r\n", "US-ASCII") });

        Assert.assertEquals("line one", con.readStringByDelimiter("\r\n"));
        Assert.assertEquals("line two", con.readStringByDelimiter("\r\n"));
        Assert.assertEquals("line three", con.readStringByDelimiter("\r\n"));

        
        con.close();
        server.close();
    }
	
	
	
	@Test 
    public void testMarked() throws Exception {
        IServer server = new Server(new ServerHandler());
        server.start();
        
        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
        con.write("line one\r\n");
        con.write("line two\r\n");
        con.write("line three\r\n");
        
        
        
        Assert.assertEquals("line one", con.readStringByDelimiter("\r\n"));
        
        con.markReadPosition();

        Assert.assertEquals("line two", con.readStringByDelimiter("\r\n"));

        try {
            con.unread(new ByteBuffer[] { DataConverter.toByteBuffer("line two\r\n", "US-ASCII") });
            Assert.fail("IOException excepted");
        } catch (IOException excepted) { }
        
        
        con.close();
        server.close();
    }
	
	
	
	private static final class ServerHandler implements IDataHandler {
	    
	    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
	        connection.write(connection.readByteBufferByLength(connection.available()));
	        return true;
	    }
	    
	}
}
