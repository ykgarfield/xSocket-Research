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
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import static org.junit.Assert.*;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
*/
public final class MarkAndResetWithDelimiterTest {

	private static final int MAX_LENGTH_FIRST_LINE = 255;
    private static final String CRLF = "\r\n";

    private static final String GET  = "GET /favicon.ico HTTP/1.1"
                                             + CRLF
                                             + "User-Agent: Opera/9.21 (Windows NT 5.1; U; en)"
                                             + CRLF
                                             + "Host: localhost"
                                             + CRLF
                                             + "Accept: application/xhtml+voice+xml;version=1.2, application/x-xhtml+voice+xml;version=1.2, text/html, application/xml;q=0.9, application/xhtml+xml, image/png, image/jpeg, image/gif, image/x-xbitmap, */*;q=0.1"
                                             + CRLF + "Accept-Language: de-DE,de;q=0.9,en;q=0.8" + CRLF
                                             + "Accept-Charset: iso-8859-1, utf-8, utf-16, *;q=0.1" + CRLF
                                             + "Accept-Encoding: deflate, gzip, x-gzip, identity, *;q=0" + CRLF
                                             + "Referer: http://localhost/VCSApplication/" + CRLF + "Connection: Keep-Alive, TE" + CRLF
                                             + "TE: deflate, gzip, chunked, identity, trailers" + CRLF + CRLF;

    
    @Test
    public void testMark() throws Exception {
    	DataHandler handler = new DataHandler();
        IServer server = new Server(handler);
        ConnectionUtils.start(server);
        
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress("localhost", server.getLocalPort()));
        channel.write(ByteBuffer.wrap(GET.getBytes()));
        channel.close();

        // give the server time to handle the data 
        QAUtil.sleep(300);
        
        server.close();


        // must match
        assertEquals(handler.firstSecondLineSecondReadResult, handler.repeatedSecondLineReadResult);
    }

 

    
    private static final class DataHandler implements IDataHandler, IConnectHandler {

    	private enum State { FIRST_READ, REPEATED_READ, FINISHED };
    	
        private State state = State.FIRST_READ;
        
        private String firstSecondLineSecondReadResult = null;
        private String repeatedSecondLineReadResult  = null;


        public boolean onConnect(INonBlockingConnection connection) throws IOException {
            connection.setAutoflush(true);
            connection.setFlushmode(FlushMode.ASYNC);
            return true;
        }

        
        public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
        	
        	switch (state) {

        	// read first header line 
			case FIRST_READ:
        		// read first line from header until CRLF
        		String firstLineReadResult = connection.readStringByDelimiter(CRLF, "UTF-8", MAX_LENGTH_FIRST_LINE);

        		
        		// mark position
        		connection.markReadPosition();
        		
        		// read second line from header until CRLF
        		firstSecondLineSecondReadResult = connection.readStringByDelimiter(CRLF, "UTF-8", MAX_LENGTH_FIRST_LINE);
                
        		// reset to marked position
        		connection.resetToReadMark();
        		
        		state = State.REPEATED_READ;
				break;


        	// read same second header line again
			case REPEATED_READ:
	        	repeatedSecondLineReadResult = connection.readStringByDelimiter(CRLF, "UTF-8", MAX_LENGTH_FIRST_LINE);
        		
        		state = State.FINISHED;
				break;
				
				
			default:
				break;
			}
        	
        	return true;
        }
    }
}
