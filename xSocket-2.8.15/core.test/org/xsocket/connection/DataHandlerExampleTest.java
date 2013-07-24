/*
 *  Copyright (c) xlightweb.org2006 - 2009 All rights reserved.
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
 * The latest copy of this software may be found on http://www.xlightweb.org/
 */
package org.xsocket.connection;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.Assert;

import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IServer;




/**
*
* @author grro@xlightweb.org
*/
public final class DataHandlerExampleTest  {
	
	

	@Test
	public void echoHandlerTest() throws Exception {
		/*
		Logger logger = Logger.getLogger("org.xsocket.connection.EchoHandler");
		logger.setLevel(Level.FINE);
		
		ConsoleHandler ch = new ConsoleHandler();
		ch.setLevel(Level.FINE);
		logger.addHandler(ch);
		*/

		IServer server = new Server(new LineEchoHandler());
		server.start();
		
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		
		for (int i = 0; i < 10000; i++) {
			String request = "request#" + i;
			con.write(request + "\r\n");
			String response = con.readStringByDelimiter("\r\n");
			
			Assert.assertEquals(request, response);
		}
		
		con.close();
		server.close();
	}
	
	

	@Test
	public void fileStreamingHandlerTest() throws Exception {

	
		IServer server = new Server(new FileStreamingHandler());
		server.start();
		
		
		File file = QAUtil.createTestfile_400k();
		
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setAutoflush(false);
		
		
		for (int i = 0; i < 5; i++) {
			
			FileInputStream fis = new FileInputStream(file);
			FileChannel fc = fis.getChannel();
			int length = (int) fc.size();
			
			// write the file length
			con.write(length);
			
			// write the file
			con.transferFrom(fc);
			con.flush();
			
			fc.close();
			fis.close();
		
			
			// read the server response (path of the server side written file)
			String fname = con.readStringByDelimiter("\r\n");
			
			QAUtil.isEquals(file, new File(fname));
			
			new File(fname).delete();
		}

		file.delete();
		con.close();
		server.close();
	}

	
	
	
	private static final class LineEchoHandler implements IConnectHandler, IDataHandler {
		
		private static final Logger LOG = Logger.getLogger(EchoHandler.class.getName());
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.setAutoflush(false);
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {

			// read the line  
			ByteBuffer[] data = connection.readByteBufferByDelimiter("\r\n");

			
			// logging if activated 
			if (LOG.isLoggable(Level.FINE)) {
				ByteBuffer[] dataCopy = new ByteBuffer[data.length];
				for (int i = 0; i < data.length; i++) {
					dataCopy[i] = data[i].duplicate();
				}
				
				LOG.fine(DataConverter.toString(dataCopy));
			}
			
			
			// returning the response 
			connection.write(data);
			connection.write("\r\n");
			connection.flush();
			
			return true;
		}		
	}
	
	
	
	
	
	private static final class FileStreamingHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {

			// read the length field
			int length = connection.readInt();
			
			
			// create a temp file
			File file = QAUtil.createTempfile();
			
			// replace this handler by a file streaming handler which handles the file data
			connection.setHandler(new FileStreamer(this, length, file.getAbsolutePath()));
			return true;
		}
	}

	
	private static final class FileStreamer implements IDataHandler {
		
		private IDataHandler orgDataHandler;
		private int remaining;
		private String filename;
		private RandomAccessFile raf;
		private FileChannel fc;
		
		public FileStreamer(IDataHandler orgDataHandler, int length, String filename) throws IOException {
			this.orgDataHandler = orgDataHandler;
			this.remaining = length;
			this.filename = filename;
			
			raf = new RandomAccessFile(filename, "rw");
			fc = raf.getChannel();
		}
		
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {

			int available = connection.available();
			if (available <= 0) {
				return true;
			}
			
			int lengthToRead = remaining;
			if (available < remaining) { 
				lengthToRead = available;
			}

			
			connection.transferTo(fc, lengthToRead);
			remaining -= lengthToRead;

			
			// file read?
			if (remaining == 0) {
				
				// close file 
				fc.close();
				raf.close();
				
				// write location
				connection.write(filename + "\r\n");
				
				// reset to old handler
				connection.setHandler(orgDataHandler);
			}

			return true; 
		}
	}


}