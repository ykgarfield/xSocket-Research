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

import junit.framework.Assert;

import org.junit.Test;
import org.xsocket.SSLTestContextFactory;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.Server;


public class SSLOnConnectTest {
	
	

    @Test
    public void simpleTest() throws Exception {


        Server server = new Server(0, new ServerHandler(), SSLTestContextFactory.getSSLContext(), true);
        server.start();
        
        
        IBlockingConnection con =  new BlockingConnection("localhost", server.getLocalPort(), SSLTestContextFactory.getSSLContext(), true);
        String greeting = con.readStringByDelimiter("\r\n");
        Assert.assertEquals("Hello", greeting);
        
        con.write("clean\r\n");
        String response = con.readStringByDelimiter("\r\n");
        Assert.assertEquals("clean", response);
        
        con.close();
        server.close();
	}
	

    private static final class ServerHandler implements IConnectHandler, IDataHandler {
        
        public boolean onConnect(INonBlockingConnection connection) throws IOException {
            connection.write("Hello\r\n");
            return true;
        }
        
        public boolean onData(INonBlockingConnection connection) throws IOException {
            
            String cmd = connection.readStringByDelimiter("\r\n");
            connection.write(cmd + "\r\n");
            return true;
        }
    }
	
}
