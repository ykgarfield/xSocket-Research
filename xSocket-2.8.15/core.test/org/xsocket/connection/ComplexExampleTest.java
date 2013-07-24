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
public final class ComplexExampleTest {

	private static final byte RECORD_TYPE_A = 01;

	
	@Test 
	public void testSimple() throws Exception {
		
		ProtocolHandler hdl = new ProtocolHandler();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		int length = 2000;
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setAutoflush(false);

		
		for (int i = 0; i < 5; i++) {
			con.write(RECORD_TYPE_A);         // record type
			con.flush();
			
			con.write("1.0.1\r\n");           // version
			con.write("MBwGA1UEChM...");  // signature part I
			
			
			con.flush();
			QAUtil.sleep(400);
			Assert.assertTrue(hdl.getCountOnDataCalled() <= 3);
			
			
			con.write("...MBwGA1UEChM\r\n");  // signature partII
			
			con.write(length);                // data length
			con.flush();
			QAUtil.sleep(100);
			Assert.assertTrue(hdl.getCountOnDataCalled() <= 4);			

			
			ByteBuffer data = QAUtil.generateByteBuffer(length);
			con.write(data);                  // data
			con.flush();
			QAUtil.sleep(100);
			Assert.assertTrue(hdl.getCountOnDataCalled() <= 6);

			
			String status = con.readStringByDelimiter("\r\n");
			Assert.assertEquals("ACCEPTED", status);
			
			System.out.print(".");
			
			hdl.resetCountOnDataCalled();
			QAUtil.sleep(200);
		}
		
		con.close();	
		server.close();
	}
	

	
	@Test 
	public void testSimple2() throws Exception {
		
		ProtocolHandler2 hdl = new ProtocolHandler2();
		IServer server = new Server(hdl);
		ConnectionUtils.start(server);

		int length = 2000;
		
		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		con.setAutoflush(false);

		
		for (int i = 0; i < 5; i++) {
			con.write(RECORD_TYPE_A);         // record type
			con.flush();
			
			con.write("1.0.1\r\n");           // version
			con.write("MBwGA1UEChM...");  // signature part I
			
			
			con.flush();
			QAUtil.sleep(400);
			Assert.assertTrue(hdl.getCountOnDataCalled() <= 3);
			
			
			con.write("...MBwGA1UEChM\r\n");  // signature partII
			
			con.write(length);                // data length
			con.flush();
			QAUtil.sleep(100);
			Assert.assertTrue(hdl.getCountOnDataCalled() <= 4);			

			
			ByteBuffer data = QAUtil.generateByteBuffer(length);
			con.write(data);                  // data
			con.flush();
			QAUtil.sleep(100);
			Assert.assertTrue(hdl.getCountOnDataCalled() <= 6);

			
			String status = con.readStringByDelimiter("\r\n");
			Assert.assertEquals("ACCEPTED", status);
			
			System.out.print(".");
			
			hdl.resetCountOnDataCalled();
			QAUtil.sleep(200);
		}
		
		con.close();	
		server.close();
	}

	
	
	private static final class ProtocolHandler implements IDataHandler {
		
		private int countOnDataCalled = 0;
		
		public boolean onData(INonBlockingConnection nbc) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			countOnDataCalled++;
			
			// in content receiving mode? 
			IDataHandler contentHandler = (IDataHandler) nbc.getAttachment();
			if (contentHandler != null) {
				return contentHandler.onData(nbc);
			}
			
			
			// reset to read position (if former reads failed)
			// and mark it
			nbc.resetToReadMark();
			nbc.markReadPosition();

			
			// try to read the header data
			byte recordType = nbc.readByte();
			String version = nbc.readStringByDelimiter("\r\n");
			String signature = nbc.readStringByDelimiter("\r\n");
			int	dataLength = nbc.readInt();
				
			
			// got the complete header -> remove read mark
			nbc.removeReadMark();
			
			
			// set content handler 
			nbc.setAttachment(new ContentHandler(dataLength, signature));
			
			return true;
		}
		
		
		int getCountOnDataCalled() {
			return countOnDataCalled;
		}
		
		void resetCountOnDataCalled() {
			countOnDataCalled = 0;
		}
	}

	
	private static final class ProtocolHandler2 implements IDataHandler {
		
		private int countOnDataCalled = 0;
		
		public boolean onData(INonBlockingConnection nbc) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			countOnDataCalled++;
			
			// in content receiving mode? 
			IDataHandler contentHandler = (IDataHandler) nbc.getAttachment();
			if (contentHandler != null) {
				return contentHandler.onData(nbc);
			}
			
			
			// .. no -> reading header 
			byte recordType = 0;
			String version = null;
			String signature = null;
			int dataLength = 0;
			
			nbc.markReadPosition();
			try {
				recordType = nbc.readByte();
				version = nbc.readStringByDelimiter("\r\n");
				signature = nbc.readStringByDelimiter("\r\n");
				dataLength = nbc.readInt();
				
			} catch (BufferUnderflowException bue) { 
				nbc.resetToReadMark(); // not enough data is available
				return true;
			}
			nbc.removeReadMark();
			
			// set content handler 
			nbc.setAttachment(new ContentHandler(dataLength, signature));
			
			return true;
		}
		
		
		int getCountOnDataCalled() {
			return countOnDataCalled;
		}
		
		void resetCountOnDataCalled() {
			countOnDataCalled = 0;
		}
	}

	
	private static final class ContentHandler implements IDataHandler {
		
		private int remaining = 0;
		
		
		public ContentHandler(int dataLength, String signature) {
			remaining = dataLength;
			//this.signature = signature;
		}
		
		public boolean onData(INonBlockingConnection nbc) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			int lengthToRead = remaining;
			if (nbc.available() < remaining) {
				lengthToRead = nbc.available();
			}
			
			ByteBuffer[] buffers = nbc.readByteBufferByLength(lengthToRead);
			remaining -= lengthToRead;
			
			// processing the data
		
			if (remaining == 0) {
				nbc.setAttachment(null);
				nbc.write("ACCEPTED\r\n");
			}
			
			return true;
		}
	}
}
