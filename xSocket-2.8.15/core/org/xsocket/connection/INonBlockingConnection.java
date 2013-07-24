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
import java.util.concurrent.Executor;

import org.xsocket.IDataSink;
import org.xsocket.IDataSource;
import org.xsocket.MaxReadSizeExceededException;



/**
 * A connection which accesses the underlying channel in a non-blocking manner. <br><br>
 *
 * @author grro@xsocket.org
 */
public interface INonBlockingConnection extends IConnection, IDataSource, IDataSink, GatheringByteChannel, ReadableByteChannel, WritableByteChannel, Flushable {

	public static final int UNLIMITED = Integer.MAX_VALUE;


	/**
	 * set the connection handler. <br><br>
	 * 
	 * @param handler the handler
	 * @throws IOException If some other I/O error occurs  
	 */
	void setHandler(IHandler handler) throws IOException;

	
	/**
	 * gets the connection handler
	 * 
	 *  @return the handler
	 * 
	 */
	IHandler getHandler();




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
	 *
	 * @throws IOException If some other I/O error occurs
	 * @throws SocketTimeoutException If the timeout has been reached
	 * @throws ClosedChannelException if the underlying channel is closed
	 */
	void flush() throws ClosedChannelException, IOException, SocketTimeoutException;


	
	/**
	 * returns if secured mode is activateable
	 * @return true, if secured mode is activateable
	 */
	boolean isSecuredModeActivateable();
	
	
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
     * @param buffer                  the buffer to write 
     * @param writeCompletionHandler  the completionHandler
     * @throws IOException If some I/O error occurs
     */
    void write(ByteBuffer buffer, IWriteCompletionHandler writeCompletionHandler) throws IOException;

    
   
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
     * writes bytes to the data sink. Typically this write mthod will be used in async flush mode
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
	 * returns the ByteBuffers to the <i>top</i> of the read queue.
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
	ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException;


	
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
	ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException;

	
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
	byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException;




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
	String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException;

	

	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding to use
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws BufferUnderflowException If not enough data is available 
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
	 */
	String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException;




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
	String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException;
	
	
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
	 * Returns the index  of the first occurrence of the given string.
	 *
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then
	 *         the index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
 	 * @throws IOException If some other I/O error occurs
	 */
	int indexOf(String str) throws IOException;


	
	/**
	 * Returns the index  of the first occurrence of the given string.
	 *
	 * @param str          any string
	 * @param encoding     the encoding to use
	 * @return if the string argument occurs as a substring within this object, then
	 *         the index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
 	 * @throws IOException If some other I/O error occurs
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found
	 */
	int indexOf(String str, String encoding) throws IOException;


	
	
	/**
	 * set the send delay time. Data to write will be buffered
	 * internally and be written to the underlying subsystem
	 * based on the given write rate.
	 * The write methods will <b>not</b> block for this time. <br>
	 *
	 * By default the write transfer rate is set with UNLIMITED <br><br>
	 *
	 * Reduced write transfer is only supported for FlushMode.ASYNC. see
	 * {@link INonBlockingConnection#setFlushmode(org.xsocket.connection.IConnection.FlushMode))}
	 *
	 * @param bytesPerSecond the transfer rate of the outgoing data
	 * @throws ClosedChannelException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	void setWriteTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException;

	
	/**
	 * gets the send delay time. 
	 *
	 * @return the transfer rate of the outgoing data
	 * @throws ClosedChannelException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
	int getWriteTransferRate() throws ClosedChannelException, IOException;
		


	/**
	 * set the read rate. By default the read transfer rate is set with UNLIMITED <br><br>
	 *
	 * @param bytesPerSecond the transfer rate of the outgoing data
	 * @throws ClosedChannelException If the underlying socket is already closed
	 * @throws IOException If some other I/O error occurs
	 */
//	public void setReadTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException;
		



	/**
	 * get the number of available bytes to read
	 * 
	 * @return the number of available bytes, possibly zero, or -1 if the channel has reached end-of-stream
	 */
	int available() throws IOException;

	
	
	/**
	 * get the version of read buffer. The version number increases, if
	 * the read buffer queue has been modified 
	 *
	 * @return the version of the read buffer
	 * @throws IOException If some other I/O error occurs 
	 */
	int getReadBufferVersion() throws IOException;
	
	
	/**
	 * return if the data source is open. Default is true
	 * @return true, if the data source is open
	 */
	boolean isOpen();
	
	
	/**
	 * return the worker pool which is used to process the call back methods
	 *
	 * @return the worker pool
	 */
	Executor getWorkerpool();
	
	
	
	/**
	 * sets the worker pool which is used to process the call back methods
	 *
	 * @param workerpool  the workerpool
	 */
	void setWorkerpool(Executor workerpool);

	

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
	 * get the max app read buffer size. If the read buffer size exceeds this limit the 
	 * connection will stop receiving data. The read buffer size can be higher the limit  
	 * (max size = maxReadBufferThreshold + socket read buffer size  * 2)
	 * 
	 * @return the max read buffer threshold
	 */
	int getMaxReadBufferThreshold();
	
	
	/**
	 * set the max app read buffer threshold
	 * 
	 * @param maxSize the max read buffer threshold
	 */
	void setMaxReadBufferThreshold(int size);
	


	/**
	 * sets the flush mode. If the flush mode is set toASYNC (default is SYNC), 
	 * the data will be transferred to the underlying connection in a asynchronous way. <br> <br>
	 * By using the {@link WritableByteChannel} interface methods write(ByteBuffer) and
	 *  write(ByteBuffer[]) some restriction exits. Calling such a write method in mode 
	 *  ASYNC causes that the byte buffer will be read asynchronously by the internal I/O thread. 
	 *  If the byte buffer will be accessed (reused) after calling the write method, race 
	 *  conditions will occur. The write(ByteBuffer) and write(ByteBuffer[]) should only 
	 *  called in ASNC mode, if the byte buffer will not be accessed (reused) 
	 *  after the write operation. E.g.
	 * 
	 * <pre>
	 * 
	 *   File file = new File(filename);
	 *   RandomAccessFile raf = new RandomAccessFile(file, "r");
	 *   ReadableByteChannel fc = raf.getChannel();
	 *  
	 *   INonBlockingConnection connection = new NonBlockingConnection(host, port);
	 *
	 *   // using a copy buffer (which will be reused for the read operations) 
	 *   // requires FlushMode SYNC which is default (for writing)!  
	 *   ByteBuffer copyBuffer = ByteBuffer.allocate(4096); 
	 *   
	 *   int read = 0;
	 *   while (read >= 0) {
	 *      // read channel
	 *      read = fc.read(copyBuffer);
	 *      copyBuffer.flip();
	 *      
	 *      if (read > 0) {
	 *         // write channel
	 *         connection.write(copyBuffer);
	 *         if (copyBuffer.hasRemaining()) {
	 *            copyBuffer.compact();
	 *         } else {
	 *            copyBuffer.clear();
	 *         }
	 *      }
	 *   }
	 * </pre>
	 *
	 * @param flushMode {@link FlushMode#ASYNC} if flush should be performed asynchronous,
	 *                  {@link FlushMode#SYNC} if flush should be perform synchronous
	 */
	void setFlushmode(FlushMode flushMode);
	
	

	/**
	 * return the flush mode
	 * @return the flush mode
	 */
	FlushMode getFlushmode();
}
