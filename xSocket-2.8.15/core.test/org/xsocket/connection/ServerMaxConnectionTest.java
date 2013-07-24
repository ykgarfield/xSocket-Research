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

import junit.framework.Assert;

import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.Server;




/**
*
* @author grro@xsocket.org
*/
public final class ServerMaxConnectionTest {
	


	@Test 
	public void testSimple() throws Exception {

		Server server = new Server(new EchoHandler());
		server.setMaxConcurrentConnections(3);
		server.start();

		try {
		    for (int i = 0; i < 1000; i++) {
		        new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort(), 1000);
		    }
		    
		    Assert.fail("Exception expected");
 		} catch (Exception expected) { }
 		
 		
 		server.close();
	}
	
	
	
    @Test 
    public void testSimple2() throws Exception {

        IConnectHandler ch = new IConnectHandler() {
            
            public boolean onConnect(INonBlockingConnection connection) throws IOException {
                QAUtil.sleep(250);
                connection.close();
                
                return true;
            }
        };
        
        Server server = new Server(ch);
        server.setMaxConcurrentConnections(3);
        server.start();

        try {
            for (int i = 0; i < 1000; i++) {
                new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort(), 1000);
            }
            
            Assert.fail("Exception expected");
        } catch (Exception expected) { }
        
        QAUtil.sleep(2000);
        
        new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort(), 1000);
        
        server.close();
    }
}
