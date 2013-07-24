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
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.INonBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
@Execution(Execution.NONTHREADED)
public final class AttachmentAvailableAfterDisconnectTest  {


    @Test
    public void testSync() throws Exception {

        IServer server = new Server(new ServerHandler());
        server.start();

        Handler hdl = new Handler();
        INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort(), hdl);

        QAUtil.sleep(500);
        con.close();

        QAUtil.sleep(1000);
        
        Assert.assertTrue(hdl.isAttachmentAvailable());
        
        
        con.close();
        server.close();
    }

    
    @Test
    public void testAsync() throws Exception {
                
        IServer server = new Server(new ServerHandler());
        server.start();

        Handler hdl = new Handler();
        INonBlockingConnection con = new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort(), hdl, false, 100);
        
        QAUtil.sleep(500);
        con.close();

        QAUtil.sleep(500);
        
        Assert.assertTrue(hdl.isAttachmentAvailable());
        
        
        con.close();
        server.close();
    }


    private static final class Handler implements IConnectHandler, IDisconnectHandler {
        
        private boolean isAttachmentAvailable = false;
        
        public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
            connection.setAttachment("attachment");
            return true;
        }
        
        public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
            isAttachmentAvailable = (connection.getAttachment() != null);
            return true;
        }
        
        boolean isAttachmentAvailable() {
            return isAttachmentAvailable;
        }
    }
    
    
    
    private static final class ServerHandler implements IDataHandler {
        
        public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
            connection.write(connection.readByteBufferByDelimiter("\r\n"));
            return true;
        }
    }
    
}
