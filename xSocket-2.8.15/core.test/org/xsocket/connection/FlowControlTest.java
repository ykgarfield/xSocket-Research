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


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.channels.ReadableByteChannel;


import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.INonBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public class FlowControlTest {

	

	@Test
	public void testSimple() throws Exception {
		
		// there is no guarantee by setting the SO_RCVBUF that the underlying OS 
		// will use this size. For this reasons this test can fail on dedicacted machines
		
	
	/*	
		IServer server = new Server(new ServerHandler());
		ConnectionUtils.start(server);
		
		
		INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
		con.setMaxReadBufferThreshold(8000);
		
		int rcvbuf = (Integer) con.getOption(IConnection.SO_RCVBUF);
		System.out.println("socket rcvbuf " + rcvbuf);
		
		con.write("test\r\n");
		
		QAUtil.sleep(200);
		
		int loops = 0;
		while (con.available() > 0) { 
			Assert.assertFalse(con.isReceivingSuspended());  // external suspend mode is false (internal true)
			
			if (con.available() > (8000 + (3 * rcvbuf))) {
				String msg = "error: " + con.available() + " is larger than " + (8000 + (rcvbuf *  3));
				System.out.println("Error " + msg);
				Assert.fail(msg);
			}
			
			con.readByteBufferByLength(con.available());
			
			loops++;
		}
		
		
		if (loops < 3) {
			String msg = "more loops than 3 expected";
			System.out.println(msg);
			Assert.fail(msg);
		}
		
		con.close();
		server.close();*/
	}	
	
	
	
	private static final class ServerHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			
			connection.readStringByDelimiter("\r\n");
			
			File file = QAUtil.createTestfile_400k();
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			ReadableByteChannel in = raf.getChannel();
			connection.transferFrom(in);
			
			in.close();
			raf.close();
			file.delete();
			
			return true;
		}
	}
}
