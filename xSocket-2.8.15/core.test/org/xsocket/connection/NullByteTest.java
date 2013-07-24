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
import java.util.logging.Level;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class NullByteTest {

	
	@Test 
	public void testSimple() throws Exception {
	    
	    IDataHandler dh = new IDataHandler() {
            
            public boolean onData(INonBlockingConnection connection) throws IOException {
                ByteBuffer[] bufs = connection.readByteBufferByLength(4);
                
                connection.write(bufs);
                return true;
            }
        };
        
        Server server = new Server(dh);
        server.start();
        
        
        BlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());

        for (int i = 0; i < 10; i++) {
            byte[] req = new byte[] { 0x00, 0x54, 0x66, (byte) 0xFF };
            bc.write(req);
    
            byte[] resp = bc.readBytesByLength(4);
            Assert.assertArrayEquals(req, resp);
        }
        
        
        bc.close();
        server.close();
	}
}
