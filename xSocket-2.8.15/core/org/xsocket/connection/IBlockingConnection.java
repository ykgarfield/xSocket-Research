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

import java.io.Flushable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;

import org.xsocket.IDataSink;
import org.xsocket.IDataSource;
import org.xsocket.MaxReadSizeExceededException;


/**
 * A connection which accesses the underlying channel in a non-blocking manner. Every read I/O operation
 * will block until it completes. The blocking behavior of write operations will be controlled by the 
 * flush configuration.   
 *
 * @author grro@xsocket.org
 */
public interface IBlockingConnection extends IConnection, IDataSource, IDataSink, GatheringByteChannel, ReadableByteChannel, WritableByteChannel, Flushable {

	public static final int DEFAULT_READ_TIMEOUT = Integer.MAX_VALUE;

	
	
	/**
	 * set autoflush. If autoflush is activated, each write call
	 * will cause a flush. <br><br>
	 *
	 * @param autoflush true if autoflush should be activated
	 */
	void setAutoflush(boolean autoflush);



	/**
	 * get autoflush
	 * 
	 * @return true, if autoflush is activated
	 */
	boolean isAutoflush();
	
	

	
	/**
	 * flush the send buffer. The method call will block until
	 * the outgoing data has been flushed into the underlying
	 * os-specific send buffer.
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the timeout has been reached
	 * @throws ClosedChannelException if the underlying channel is closed
	 */
	void flush() throws ClosedChannelException, IOException, SocketTimeoutException;



	
	/**
	 * ad hoc activation of a secured mode (SSL). By performing of this
	 * method all remaining data to send will be flushed.
	 * After this all data will be sent and received in the secured mode
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	void activateSecuredMode() throws IOException;
	
	
	/**
	 * ad hoc deactivation of a secured mode (SSL). By performing of this
	 * method all remaining data to send will be flushed.
	 * After this all data will be sent and received in the plain mode
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	void deactivateSecuredMode() throws IOException;

	
	
	/**
	 * returns if the connection is in secured mode
	 * @return true, if the connection is in secured mode
	 */
	boolean isSecure();

	
	/**
	 * returns the size of the data which have already been written, but not
	 * yet transferred to the underlying socket.
	 *
	 * @return the size of the pending data to write
	 */
	int getPendingWriteDataSize();

	
	/**
	 * set the timeout for calling read methods in millis
	 *
	 * @param timeout  the timeout in millis
	 * @throws IOException If some other I/O error occurs
	 */
	void setReadTimeoutMillis(int timeout) throws IOException;


	/**
	 * get the timeout for calling read methods in millis
	 *
	 * @return the timeout in millis
	 * @throws IOException If some other I/O error occurs
	 */
	int getReadTimeoutMillis() throws IOException;
	

	
	
	/**
	 * suspend receiving data from the underlying subsystem
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	void suspendReceiving() throws IOException;

	
	/**
	 * resume receiving data from the underlying subsystem
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	void resumeReceiving() throws IOException;
	

	

	/**
	 * returns true if receiving is suspended
	 * 
	 * @return true, if receiving is suspended
	 */
	boolean isReceivingSuspended();
	
	
	

    /**
     * returns the ByteBuffer to the <i>top</i> of the read queue.
     * 
     * @param buffers  the buffers to return
     * @throws IOException if an exception occurs
     */
    void unread(ByteBuffer[] buffers) throws IOException;

    
    
    /**
     * returns the ByteBuffer to the <i>top</i> of the read queue.
     * 
     * @param buffer  the buffer to return
     * @throws IOException if an exception occurs
     */
    void unread(ByteBuffer buffer) throws IOException;
    
    
    
    /**
     * returns the bytes to the <i>top</i> of the read queue.
     * 
     * @param bytes  the bytes to return
     * @throws IOException if an exception occurs
     */
    void unread(byte[] bytes) throws IOException;

    
    
    /**
     * returns the text to the <i>top</i> of the read queue.
     * 
     * @param text  the text to return
     * @throws IOException if an exception occurs
     */
    void unread(String text) throws IOException;

    
	
	
	/**
	 * Resets to the marked write position. If the connection has been marked,
	 * then attempt to reposition it at the mark.
	 *
	 * @return true, if reset was successful
	 */
	boolean resetToWriteMark();

	
	
	/**
	 * Resets to the marked read position. If the connection has been marked,
	 * then attempt to reposition it at the mark.
	 *
	 * @return true, if reset was successful
	 */
	boolean resetToReadMark();

	
	/**
	 * Marks the write position in the connection. 
	 */
	void markWritePosition();
	

	/**
	 * Marks the read position in the connection. Subsequent calls to resetToReadMark() will attempt
	 * to reposition the connection to this point.
	 *
	 */
	void markReadPosition();

	
	/**
	 * remove the read mark
	 */
	void removeReadMark();

	
	/**
	 * remove the write mark
	 */
	void removeWriteMark();
	
	
	/**
	 * gets the encoding (used by string related methods like write(String) ...)
	 *
	 * @return the encoding
	 */
	String getEncoding();
	
	

	
	/**
	 * sets the encoding  (used by string related methods like write(String) ...)
	 *
	 * @param encoding the encoding
	 */
	void setEncoding(String encoding);


	
	/**
	 * write a message
	 * 
	 * @param message    the message to write
	 * @param encoding   the encoding which should be used th encode the chars into byte (e.g. `US-ASCII` or `UTF-8`)
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	int write(String message, String encoding) throws IOException, BufferOverflowException;

	
   
    
    /**
     * writes a byte buffer array. Typically this write mthod will be used in async flush mode 
     * 
     * @param buffers                 the buffers to write 
     * @param writeCompletionHandler  the completionHandler
     * @throws IOException If some I/O error occurs
     */
    void write(ByteBuffer[] buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException;

    
    /**
     * writes a byte buffer. Typically this write mthod will be used in async flush mode 
     * 
     * @param buffer                 the buffer to write 
     * @param writeCompletionHandler  the completionHandler
     * @throws IOException If some I/O error occurs
     */
    void write(ByteBuffer buffer, IWriteCompletionHandler writeCompletionHandler) throws IOException;

    
    /**
     * writes a list of bytes to the data sink. Typically this write mthod will be used in async flush mode
     *  
     * @param buffers    the bytes to write
     * @param writeCompletionHandler  the completionHandler
     * @return the number of written bytes 
     * @throws BufferOverflowException  If the no enough space is available 
     * @throws IOException If some other I/O error occurs
     */
    void write(List<ByteBuffer> buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException;

    
   
    /**
     * writes a byte buffer array. Typically this write mthod will be used in async flush mode 
     * 
     * @param srcs                    the buffers
     * @param offset                  the offset 
     * @param length                  the length 
     * @param writeCompletionHandler  the completionHandler
     * @throws IOException If some I/O error occurs
     */
    void write(ByteBuffer[] srcs, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException;
    
    
    
    /**
     * writes bytes to the data sink
     *  
     * @param bytes                   the bytes to write
     * @param writeCompletionHandler  the completion handler 
     * @return the number of written bytes
     * @throws BufferOverflowException  If the no enough space is available 
     * @throws IOException If some other I/O error occurs
     */
    void write(byte[] bytes, IWriteCompletionHandler writeCompletionHandler) throws IOException;
    

    /**
     * writes bytes to the data sink. Typically this write mthod will be used in async flush mode
     *  
     * @param bytes                   the bytes to write
     * @param offset                  the offset of the sub array to be used; must be non-negative and no larger than array.length. The new buffer`s position will be set to this value.
     * @param length                  the length of the sub array to be used; must be non-negative and no larger than array.length - offset. The new buffer`s limit will be set to offset + length.
     * @param writeCompletionHandler  the completion handler
     * @return the number of written bytes
     * @throws BufferOverflowException  If the no enough space is available 
     * @throws IOException If some other I/O error occurs
     */
    void write(byte[] bytes, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException;

    
    /**
     * writes a message. Typically this write mthod will be used in async flush mode
     * 
     * @param message                 the message to write
     * @param encoding                the encoding which should be used th encode the chars into byte (e.g. `US-ASCII` or `UTF-8`)
     * @param writeCompletionHandler  the completion handler
     * @return the number of written bytes
     * @throws BufferOverflowException  If the no enough space is available 
     * @throws IOException If some other I/O error occurs
     */
    void write(String message, String encoding, IWriteCompletionHandler writeCompletionHandler) throws IOException;

    
	
	
	/**
	 * read a ByteBuffer by using a delimiter. The default encoding will be used to decode the delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use	 
	 * @return the ByteBuffer
	 * @throws BufferUnderflowException If not enough data is available     
	 * @throws IOException If some other I/O error occurs
	 */
	ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, SocketTimeoutException;


	
	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding of the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws BufferUnderflowException If not enough data is available 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found     
	 * @throws IOException If some other I/O error occurs
	 */
	ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, SocketTimeoutException;

	
	/**
	 * read a byte array by using a delimiter
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter  
	 * @param encoding    the encoding to use	  
	 * @return the read bytes
	 * @throws BufferUnderflowException If not enough data is available   
	 * @throws IOException If some other I/O error occurs
	 */		
	byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException;
	
	
	

	/**
	 * read a byte array by using a delimiter
	 *
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use 
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws BufferUnderflowException If not enough data is available 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	
	 * @throws IOException If some other I/O error occurs
	 */
	byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, SocketTimeoutException;




	/**
	 * read a string by using a delimiter
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found   
	 * @throws IOException If some other I/O error occurs
	 * @throws BufferUnderflowException If not enough data is available 
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
	 */
	String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException;

	

	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws BufferUnderflowException If not enough data is available 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	 * @throws ClosedConnectionException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
	 */
	String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException;




	/**
	 * read a string by using a length definition 
	 * 
	 * @param length the amount of bytes to read.  
	 * @param encoding the encoding to use
	 * @return the string
	 * @throws IOException If some other I/O error occurs
	 * @throws BufferUnderflowException If not enough data is available 
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
     * @throws IllegalArgumentException, if the length parameter is negative
	 */
	String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, SocketTimeoutException;
	
	
	/**
	 * transfer the data of the file channel to this data sink
	 * 
	 * @param source the source channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
	 */
	long transferFrom(FileChannel source) throws IOException, BufferOverflowException;
	
	/**
	 * get the max app read buffer size. If the read buffer size exceeds this limit the 
	 * connection will stop receiving data. The read buffer size can be higher the limit 
	 * (max size = maxReadBufferThreshold + socket read buffer size  * 2) 
	 * 
	 * @return the max read buffer threshold
	 */
	int getMaxReadBufferThreshold();
	
	
	/**
	 * set the max app read buffer threshold
	 * @param maxSize the max read buffer threshold
	 */
	void setMaxReadBufferThreshold(int size);
	
	


	/**
	 * see {@link INonBlockingConnection#getFlushmode()}
	 */
	void setFlushmode(FlushMode flushMode);
	
	

	/**
	 * see {@link INonBlockingConnection#setFlushmode(FlushMode)}
	 */
	FlushMode getFlushmode();
}
