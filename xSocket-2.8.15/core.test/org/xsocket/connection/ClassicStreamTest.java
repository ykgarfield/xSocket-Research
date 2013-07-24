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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.BufferUnderflowException;
import java.nio.channels.Channels;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;




/**
*
* @author grro@xsocket.org
*/
public final class ClassicStreamTest {

	
	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new EchoHandler());
		server.start();
		
		// a IBlockingConnection implements the nio interfaces ReadableByteChannel and WritableByteChannel
		IBlockingConnection bc = new BlockingConnection("localhost", server.getLocalPort());
		
		// using the NIO Channels class to map the channel to stream
		InputStream is = Channels.newInputStream(bc);
		OutputStream os = Channels.newOutputStream(bc);
		
		LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
		
		pw.write("hello" + "\r\n");
		pw.flush();
		Assert.assertEquals("hello", lnr.readLine());
		
		
		pw.write("what I have to say is ..." + "\r\n");
		pw.flush();
		Assert.assertEquals("what I have to say is ...", lnr.readLine());
		
		pw.write("quit" + "\r\n");
		pw.flush();
		
		QAUtil.sleep(300);
		
		bc.close();
		server.close();
	}
	
	
	
	private static final class EchoHandler implements IConnectHandler {
		
		
		public boolean onConnect(INonBlockingConnection nbc) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			    
			IBlockingConnection bc = null;
			try {
				// wrap non blocking connection by a blocking connection
				bc = new BlockingConnection(nbc);
				
				// using the NIO Channels class to map the channel to stream
				InputStream is = Channels.newInputStream(bc);
				OutputStream os = Channels.newOutputStream(bc);
				
				LineNumberReader lnr = new LineNumberReader(new InputStreamReader(is));
				PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
	
				
				String line = null;
				do {
					// blocking read call (-> will suspend the current thread if no data is available)
				    System.out.println("reading line");
					line = lnr.readLine();
					System.out.println(line);
					
					// echoing the received line
					pw.write(line + "\r\n");
					pw.flush();
					
				} while (!line.equals("quit"));
				
				
			} catch (Exception e) {
				System.out.println("EXCEPTION");
				e.printStackTrace();
				System.out.println("nbc = " + nbc);				
			}

			bc.close();

			
			return true;
		}		
	}
}
