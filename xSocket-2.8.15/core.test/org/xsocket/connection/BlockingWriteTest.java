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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class BlockingWriteTest {


	@Test 
	public void testSimple() throws Exception {
		IServer server = new Server(new EchoHandler());
		server.start();

		File file = QAUtil.createTestfile_400k();
	        
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setFlushmode(FlushMode.SYNC);
		

		RandomAccessFile raf = new RandomAccessFile(file, "r");
		FileChannel channel = raf.getChannel();
		ByteBuffer transferBuffer = ByteBuffer.allocate(4096);
		   
		int read = 0;
		do { 
		    transferBuffer.clear();
		    read = channel.read(transferBuffer);
	        transferBuffer.flip();
	            
	        if (read > 0) {
	            con.write(transferBuffer);
	        } 
	    } while (read > 0); 
		
		channel.close();
		raf.close();
		
		
		File tempFile = QAUtil.createTempfile();
		
		RandomAccessFile raf2 = new RandomAccessFile(tempFile, "rw");
		FileChannel fc2 = raf2.getChannel();
		con.transferTo(fc2, (int) file.length());
		
		fc2.close();
		raf2.close();

		QAUtil.isEquals(file, tempFile);
		
		file.delete();
		tempFile.delete();
		con.close();
		server.close();
	}
	
		
	private static final class EchoHandler implements IDataHandler {
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}
	}
}
