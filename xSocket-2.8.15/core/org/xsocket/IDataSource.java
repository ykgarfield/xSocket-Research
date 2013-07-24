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
import java.io.UnsupportedEncodingException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;




/**
 * A data source is an I/O resource capable of providing data.
 * 
 * @author grro@xsocket.org
 */
public interface IDataSource {



	
	/**
	 * see {@link ReadableByteChannel#read(ByteBuffer)}
	 */
	int read(ByteBuffer buffer) throws IOException;
	
	
	/** 
	 * read a byte
	 * 
	 * @return the byte value
	 * @throws IOException If an I/O error occurs
	 */
	byte readByte() throws IOException;
	
	
	
	/**
	 * read a short value
	 * 
	 * @return the short value
	 * @throws IOException If an I/O error occurs
	 */
	short readShort() throws IOException;
	
	
	/**
	 * read an int
	 * 
	 * @return the int value
	 * @throws IOException If an I/O error occurs
	 */
	int readInt() throws IOException;

	
	/**
	 * read a long
	 * 
	 * @return the long value
	 * @throws IOException If an I/O error occurs
	 */
	long readLong() throws IOException;

	
	/**
	 * read a double
	 * 
	 * @return the double value
	 * @throws IOException If an I/O error occurs
	 */
	double readDouble() throws IOException;
	
	

	/**
	 * read bytes by using a length definition<br><br>
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is generally preferable to get bytes  
	 *  
	 * @param length the amount of bytes to read  
	 * @return the read bytes
	 * @throws IOException If some other I/O error occurs
     * @throws IllegalArgumentException if the length parameter is negative 
	 */		
	byte[] readBytesByLength(int length) throws IOException;

	
	
	/**
	 * read a ByteBuffer by using a delimiter. 
	 * To avoid memory leaks the {@link IDataSource#readByteBufferByDelimiter(String, int)} method is generally preferable.  
	 * 
	 * @param delimiter   the delimiter (by using the default encoding)
	 * @return the ByteBuffer
	 * @throws IOException If an I/O error occurs
	 */
	ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException;
	
	
	
	
	
	

	/**
	 * read a ByteBuffer by using a delimiter
	 * 
	 * 
	 * @param delimiter   the delimiter (by using the default encoding)
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found     
	 * @throws IOException If some other I/O error occurs
	 */
	ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException;

	
	

	

	/**
	 * read a ByteBuffer by using a length definition
	 * 
	 * @param length the amount of bytes to read
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
     * @throws IllegalArgumentException, if the length parameter is negative 
	 */
	ByteBuffer[] readByteBufferByLength(int length) throws IOException;



	
	
	/**
	 * read a string by using a length definition
	 * 
	 * @param length the amount of bytes to read  
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
     * @throws IllegalArgumentException, if the length parameter is negative 
	 */
	String readStringByLength(int length) throws IOException, BufferUnderflowException;




	
	/**
	 * read a string by using a delimiter 
	 * 
	 * @param delimiter   the delimiter (by using the default encoding)
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
	 */
	String readStringByDelimiter(String delimiter) throws IOException, UnsupportedEncodingException;
	
	

	
	/**
	 * read a byte array by using a delimiter <br><br>
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter (by using the default encoding) 
	 * @return the read bytes
	 * @throws IOException If an I/O error occurs
	 */		
	byte[] readBytesByDelimiter(String delimiter) throws IOException;

	
	

	/**
	 * read a byte array by using a delimiter<br><br>
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is generally preferable to get bytes 
	 *
	 * @param delimiter   the delimiter (by using the default encoding)
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 
	 * @throws IOException If some other I/O error occurs
	 */
	byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException;




	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter (by using the default encoding)
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn’t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
	 */
	String readStringByDelimiter(String delimiter, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException;



	

	/**
	 * transfer the data of the this source channel to the given data sink
	 * 
	 * @param dataSink   the data sink
	 * @param length     the size to transfer
	 * 
	 * @return the number of transfered bytes
	 * @throws ClosedChannelException If either this channel or the target channel is closed
	 * @throws IOException If some other I/O error occurs
	 */
	long transferTo(WritableByteChannel target, int length) throws IOException, ClosedChannelException;
}
