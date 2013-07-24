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
import org.xsocket.DataConverter;
import org.xsocket.connection.ReadQueue;



/**
*
* @author grro@xsocket.org
*/
public class ReadQeueTest {

	
	
	@Test
	public void testRead() throws Exception {
		ReadQueue readQueue = new ReadQueue();

		int count = 1000;
		insert(count, readQueue);
		
		int read = 0;
		do {
			
			read(readQueue, read);
			read++;
			System.out.print(".");
			
		}  while (read < count);
	}

	
	
	@Test
	public void testFind() throws Exception {
		byte[] delimiter = new byte[] { 0x0d, 0x0a, 0x0d, 0x0a };
		ByteBuffer input = ByteBuffer.wrap(new byte[] { 0x36, 0x2e, 0x0d, 0x0a, 0x31, 0x2e, 0x78, 0x29 });
		ByteBuffer input2 = ByteBuffer.wrap(new byte[] { 0x0d, 0x0a, 0x0d, 0x0a, 0x74, 0x72, 0x75, 0x65 });
		
		ReadQueue readQueue = new ReadQueue();
		readQueue.append(new ByteBuffer[] { input }, input.remaining());
		
		
		try {
			readQueue.readByteBufferByDelimiter(delimiter, Integer.MAX_VALUE);
		} catch (BufferUnderflowException bue) { }
		
		readQueue.append(new ByteBuffer[] { input2 }, input2.remaining());
		ByteBuffer[] buffers = readQueue.readByteBufferByDelimiter(delimiter, Integer.MAX_VALUE);
		
		String s1 = DataConverter.toString(input.duplicate());
		String s2 = DataConverter.toString(buffers);
		
		Assert.assertEquals(s1, s2);	
	}
	


	 
	
	static final void read(ReadQueue readQueue, int run) throws IOException {
		readText(readQueue, run);
		readInt(readQueue);
	}

	
	
	private static void readText(ReadQueue readQueue, int run) throws IOException {

		while(true) {
			try {
				ByteBuffer[] buffer = readQueue.readByteBufferByDelimiter(";".getBytes("UTF-8"), Integer.MAX_VALUE);
				String msg = DataConverter.toString(buffer);
				Assert.assertEquals("[run " + run + "] msg is not TEST it is " + msg, "TEST", msg);
				return;
			} catch (BufferUnderflowException bue) {}
		}
	}


	private static void readInt(ReadQueue readQueue) throws IOException {
		while(true) {
			try {
				ByteBuffer[] data = readQueue.readByteBufferByLength(4);
				int i = DataConverter.toByteBuffer(data).getInt();
				Assert.assertEquals(5, i);
				return;
			} catch (BufferUnderflowException bue) {}
		}
	}
	
	
	
	
	
	static final void insert(int count, ReadQueue readQueue) {
		for (int i = 0; i < count; i++) {
			ByteBuffer buf = DataConverter.toByteBuffer("TEST", "UTF-8");
			readQueue.append(new ByteBuffer[] { buf }, buf.remaining());
			ByteBuffer buf2 = DataConverter.toByteBuffer(";", "UTF-8");
			readQueue.append(new ByteBuffer[] { buf2 }, buf2.remaining());
			ByteBuffer buf3 = DataConverter.toByteBuffer((int) 5);
			readQueue.append(new ByteBuffer[] { buf3 }, buf3.remaining());
		}
	}
}
