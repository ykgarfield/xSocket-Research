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
package org.xsocket;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;




/**
 * A data sink is an I/O resource capable of receiving data.
 * 
 * @author grro@xsocket.org
 */
public interface IDataSink {



	/**
	 * writes a byte to the data sink
	 *  
	 * @param b   the byte to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available
	 * @throws IOException If some other I/O error occurs
	 */
	int write(byte b) throws IOException, BufferOverflowException;

	
	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes   the bytes to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	int write(byte... bytes) throws IOException, BufferOverflowException;
	

	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes    the bytes to write
	 * @param offset   The offset of the sub array to be used; must be non-negative and no larger than array.length. The new buffer`s position will be set to this value.
	 * @param length   The length of the sub array to be used; must be non-negative and no larger than array.length - offset. The new buffer`s limit will be set to offset + length.
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException;


	/**
	 * see {@link WritableByteChannel#write(ByteBuffer)}
	 */
	int write(ByteBuffer buffer) throws IOException, BufferOverflowException;
	
	
	
	/**
	 * see {@link GatheringByteChannel#write(ByteBuffer[])}
	 */
	long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException;

	
	/**
	 * see {@link GatheringByteChannel#write(ByteBuffer[], int, int)}
	 */
	long write(ByteBuffer[] srcs, int offset, int length) throws IOException;
	


	/**
	 * writes a list of bytes to the data sink
	 *  
	 * @param buffers    the bytes to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	long write(List<ByteBuffer> buffers) throws IOException, BufferOverflowException;

	

	/**
	 * writes a int to the data sink
	 *  
	 * @param i   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	int write(int i) throws IOException, BufferOverflowException;

	
	/**
	 * writes a short to the data sink
	 *  
	 * @param s   the short value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	int write(short s) throws IOException, BufferOverflowException;

	
	/**
	 * writes a long to the data sink
	 *  
	 * @param l   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	int write(long l) throws IOException, BufferOverflowException;

	
	/**
	 * writes a double to the data sink
	 *  
	 * @param d   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	int write(double d) throws IOException, BufferOverflowException;
	
	
	
	/**
	 * writes a message
	 * 
	 * @param message  the message to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	int write(String message) throws IOException, BufferOverflowException;
	
	
	/**
	 * transfer the data of the source file channel to this data sink
	 * 
	 * @param source the source channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	long transferFrom(FileChannel source) throws IOException, BufferOverflowException;
	
	
	/**
	 * transfer the data of the source channel to this data sink by using the default chunk size 
	 * 
	 * @param source the source channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	long transferFrom(ReadableByteChannel source) throws IOException, BufferOverflowException;
	
	
	/**
	 * transfer the data of the source channel to this data sink
	 * 
	 * @param source     the source channel
	 * @param chunkSize  the chunk size to use
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException, BufferOverflowException;
}
