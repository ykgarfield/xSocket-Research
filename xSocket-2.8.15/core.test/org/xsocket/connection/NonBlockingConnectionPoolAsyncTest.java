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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;

 
  
/**
*
* @author grro@xsocket.org
*/
public final class NonBlockingConnectionPoolAsyncTest {


	@Test
	public void testBlocking() throws Exception {
		IDataHandler dh = new IDataHandler() {
			public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
				String msg = connection.readStringByDelimiter("\r\n");
				
				connection.write(msg + "\r\n");
				if (msg.equals("close")) {
					connection.close();
				}
				return true;
			}
		};
		
		IServer server = new Server(dh);
		server.start();

		NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
		
		ConnectHandler hdl = new ConnectHandler();
		
		INonBlockingConnection con = pool.getNonBlockingConnection("localhost", server.getLocalPort(), hdl);

		
		while(!hdl.isHandled()) {
		    QAUtil.sleep(300);
		}
		
		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumCreated());

		con.write("test\r\n");
		QAUtil.sleep(1000);

		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		con.close();
		
		QAUtil.sleep(1000);

		Assert.assertEquals(0, pool.getNumActive());
		
		
		con = pool.getNonBlockingConnection("localhost", server.getLocalPort());

		Assert.assertEquals(1, pool.getNumActive());
		Assert.assertEquals(1, pool.getNumCreated());

		con.write("test\r\n");
		QAUtil.sleep(1000);

		Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
		con.close();
		
		QAUtil.sleep(1000);

		Assert.assertEquals(0, pool.getNumActive());

		pool.close();
	}
	
    @Test
    public void testNonBlocking() throws Exception {
        
        
        IDataHandler dh = new IDataHandler() {
            public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
                String msg = connection.readStringByDelimiter("\r\n");
                
                connection.write(msg + "\r\n");
                if (msg.equals("close")) {
                    connection.close();
                }
                return true;
            }
        };
        
        IServer server = new Server(dh);
        server.start();

        NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
        
        ConnectHandler hdl = new ConnectHandler();
        
        
        INonBlockingConnection con = pool.getNonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort(), hdl, false, 1000);
        
        while(!hdl.isHandled()) {
            QAUtil.sleep(300);
        }
        
        Assert.assertTrue(con.isOpen());
        
        Assert.assertEquals(1, pool.getNumActive());
        Assert.assertEquals(1, pool.getNumCreated());

        con.write("test\r\n");
        QAUtil.sleep(1000);

        Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
        con.close();
        
        QAUtil.sleep(1000);

        Assert.assertEquals(0, pool.getNumActive());
        
        
        con = pool.getNonBlockingConnection("localhost", server.getLocalPort());

        Assert.assertEquals(1, pool.getNumActive());
        Assert.assertEquals(1, pool.getNumCreated());

        con.write("test\r\n");
        QAUtil.sleep(1000);

        Assert.assertEquals("test", con.readStringByDelimiter("\r\n"));
        con.close();
        
        QAUtil.sleep(1000);

        Assert.assertEquals(0, pool.getNumActive());

        pool.close();
    }

    
    @Test
    public void testNonBlockingFailed() throws Exception {
        
        NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
        
        ConnectHandler hdl = new ConnectHandler();
        
        
        INonBlockingConnection con = pool.getNonBlockingConnection(InetAddress.getByName("165.34.1.33"), 6543, hdl, false, 1000);
        
        while(!hdl.isHandled()) {
            QAUtil.sleep(300);
        }

        Assert.assertNotNull(hdl.getExcception());

        pool.close();
    }    
	
    @Execution(Execution.NONTHREADED)
	private static final class ConnectHandler implements IConnectHandler, IConnectExceptionHandler {
	 
	    private final AtomicBoolean isHandled = new AtomicBoolean(false);
	    private final AtomicReference<IOException> exepctionRef = new AtomicReference<IOException>();
	    
	    public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
	            MaxReadSizeExceededException {

	        isHandled.set(true);
	        return true;
	    }
	    
	    public boolean onConnectException(INonBlockingConnection connection, IOException ioe) throws IOException {
	        exepctionRef.set(ioe);
	        isHandled.set(true);
            return true;
	    }
	    
	    
	    boolean isHandled() {
	        return isHandled.get();
	    }
	    
	    IOException getExcception() {
	        return exepctionRef.get();
	    }
	}
}
