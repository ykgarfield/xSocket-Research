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

import java.io.Closeable;



import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnection.FlushMode;


 

/**
 * implementation base of a data stream.   
 * 
 * <br/><br/><b>This is a xSocket internal class and subject to change</b> 
 *  
 * @author grro@xsocket.org
 */
public abstract class AbstractNonBlockingStream implements WritableByteChannel, Closeable {

	private static final Logger LOG = Logger.getLogger(AbstractNonBlockingStream.class.getName());

	private final ReadQueue readQueue = new ReadQueue();
	private final WriteQueue writeQueue = new WriteQueue();


	static final int TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE = IoProvider.getTransferByteBufferMaxSize();
	private final AtomicReference<String> defaultEncodingRef = new AtomicReference<String>(IConnection.INITIAL_DEFAULT_ENCODING);


	// open flag
	private final AtomicBoolean isOpen = new AtomicBoolean(true);
	
	
	//flushing
	private final AtomicBoolean autoflush = new AtomicBoolean(IConnection.DEFAULT_AUTOFLUSH);
	private final AtomicReference<FlushMode> flushmodeRef = new AtomicReference<FlushMode>(IConnection.DEFAULT_FLUSH_MODE);

		
	// attachment
	private AtomicReference<Object> attachmentRef = new AtomicReference<Object>(null);


	// illegal async write detection support
	private boolean isSuppressReuseBufferWarning = IoProvider.getSuppressReuseBufferWarning();
	private WeakReference<ByteBuffer> previousWriteByteBuffer;  
	private WeakReference<ByteBuffer[]> previousWriteByteBuffers;
	private WeakReference<ByteBuffer[]> previousWriteByteBuffers2;
	
	
	
	
	public void close() throws IOException {
		isOpen.set(false);
	}
	
	private void closeSilence() {
		try {
			close();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + this + " " + ioe.toString());
			}
		}
	}
	
	
	
	/**
	 * Attaches the given object to this connection
	 *
	 * @param obj The object to be attached; may be null
	 * @return The previously-attached object, if any, otherwise null
	 */
	public final void setAttachment(Object obj) {
		attachmentRef.set(obj);
	}


	/**
	 * Retrieves the current attachment.
	 *
	 * @return The object currently attached to this key, or null if there is no attachment
	 */
	public final Object getAttachment() {
		return attachmentRef.get();
	}


	/**
	 * sets the default encoding 
	 * 
	 * @param defaultEncoding  the default encoding 
	 */
	public final void setEncoding(String defaultEncoding) {
		this.defaultEncodingRef.set(defaultEncoding);
	}


	/**
	 * gets the default encoding 
	 * 
	 * @return  the default encoding
	 */
	public final String getEncoding() {
		return defaultEncodingRef.get();
	}


	/**
	 * see {@link IConnection#setFlushmode(FlushMode)} 
	 */
	public void setFlushmode(FlushMode flushMode) {
		this.flushmodeRef.set(flushMode);
	} 


	/**
	 * see {@link IConnection#getFlushmode()}
	 */
	public final FlushMode getFlushmode() {
		return flushmodeRef.get();
	}

	
	/**
	 * set true if ReuseBufferWarning is suppressed 
	 * 
	 * @param isSuppressReuseBufferWarning  true if ReuseBufferWarning is suppressed
	 */
	protected final void setSuppressReuseBufferWarning(boolean isSuppressReuseBufferWarning) {
	    this.isSuppressReuseBufferWarning = isSuppressReuseBufferWarning;
	}
	
	
	/**
	 * return true if ReuseBufferWaring is suppresses
	 * 
	 * @return true if ReuseBufferWaring is suppresses
	 */
	protected final boolean isSuppressReuseBufferWarning() {
	    return isSuppressReuseBufferWarning;
	}
	
	
	

	/**
	 * returns the default chunk size for writing
	 * 
	 * @return write chunk size
	 */
	protected int getWriteTransferChunkeSize() {
		return 8196;
	}



	/**
	 * set autoflush. If autoflush is activated, each write call
	 * will cause a flush. <br><br>
	 *
	 * @param autoflush true if autoflush should be activated
	 */
	public final void setAutoflush(boolean autoflush) {
		this.autoflush.set(autoflush);
	}

	
	/**
	 * get autoflush
	 * 
	 * @return true, if autoflush is activated
	 */
	public final boolean isAutoflush() {
		return autoflush.get();
	}


	/**
	 * returns true, if the underlying data source is open 
	 * 
	 * @return true, if the underlying data source is open 
	 */
	protected abstract boolean isMoreInputDataExpected();

	
	/**
	 * returns true, if the underlying data sink is open 
	 * 
	 * @return true, if the underlying data sink is open 
	 */
	protected abstract boolean isDataWriteable();
		

	/**
	 * Returns the index of the first occurrence of the given string.
	 *
	 * @param str any string
	 * @return if the string argument occurs as a substring within this object, then
	 *         the index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
 	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed
	 */
	public int indexOf(String str) throws IOException, ClosedChannelException {
		return indexOf(str, getEncoding());
	}


	/**
	 * Returns the index  of the first occurrence of the given string.
	 *
	 * @param str          any string
	 * @param encoding     the encoding to use
	 * @return if the string argument occurs as a substring within this object, then
	 *         the index of the first character of the first such substring is returned;
	 *         if it does not occur as a substring, -1 is returned.
 	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed 
	 */
	public  int indexOf(String str, String encoding) throws IOException, ClosedChannelException {
		ensureStreamIsOpen();
			
		return readQueue.retrieveIndexOf(str.getBytes(encoding), Integer.MAX_VALUE);
	}


	
	
	/**
	 * get the number of available bytes to read
	 * 
	 * @return the number of available bytes or -1 if the end of stream is reached
	 * @throws IOException if an exception has been occurred 
	 */
	public int available() {
		if (!isOpen.get()) {
			return -1;
		}
			
		int size = readQueue.getSize();
		if (size == 0) {
			if (isMoreInputDataExpected()) {
				return 0;
			} else {
				return -1;
			}
		} 
		
		return size;
	}
	
	/**
	 * returns the read queue size without additional check
	 * 
	 * @return the read queue size
	 */
	protected int getReadQueueSize() {
		return readQueue.getSize();
	}
	
	
	/**
	 * get the version of read buffer. The version number increases, if
	 * the read buffer queue has been modified 
	 *
	 */
	public int getReadBufferVersion()  {
		return readQueue.geVersion();
	}



	/**
	 * notification method which will be called after data has been read internally   
	 * 
	 * @param readBufs  the read buffers
	 * @param the buffers to return to the caller
 	 * @throws IOException If some other I/O error occurs
	 */
	protected ByteBuffer[] onRead(ByteBuffer[] readBufs) throws IOException {
		return readBufs;
	}


	
	public void unread(ByteBuffer[] buffers) throws IOException {
	    if ((buffers == null) || (buffers.length == 0)) {
	        return;
	    }
	    readQueue.unread(buffers);
	}

	
	public void unread(ByteBuffer buffer) throws IOException {
	    if (buffer == null) {
	        return;
	    }
	    unread(new ByteBuffer[] { buffer });
	}
	
	
    public void unread(byte[] bytes) throws IOException {
        unread(ByteBuffer.wrap(bytes));
    }


    public void unread(String text) throws IOException {
        unread(DataConverter.toByteBuffer(text, getEncoding()));
    }


	

	/** 
	 * read a byte
	 * 
	 * @return the byte value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed 
	 */
	public byte readByte() throws IOException, BufferUnderflowException, ClosedChannelException { 		
		return readSingleByteBuffer(1).get();
	}


	/**
	 * read a short value
	 * 
	 * @return the short value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed  
	 */
	public short readShort() throws IOException, BufferUnderflowException, ClosedChannelException {
		return readSingleByteBuffer(2).getShort();
	}

	
	/**
	 * read an int
	 * 
	 * @return the int value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed  
	 */
	public int readInt() throws IOException, BufferUnderflowException, ClosedChannelException {
		return readSingleByteBuffer(4).getInt();
	}


	/**
	 * read a long
	 * 
	 * @return the long value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed 
	 */
	public long readLong() throws IOException, BufferUnderflowException, ClosedChannelException {
		return readSingleByteBuffer(8).getLong();
	}


	/**
	 * read a double
	 * 
	 * @return the double value
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed  
	 */
	public double readDouble() throws IOException, BufferUnderflowException, ClosedChannelException {
		return readSingleByteBuffer(8).getDouble();
	}


	
	/**
	 * see {@link ReadableByteChannel#read(ByteBuffer)}
	 */
	public int read(ByteBuffer buffer) throws IOException, ClosedChannelException {
		int size = buffer.remaining();
		int available = available();
		
		if ((available == 0) && !isMoreInputDataExpected()) {
			closeSilence();
			return -1;
		}
		
		if (available < size) {
			size = available;
		}

		if (size > 0) {
			copyBuffers(readByteBufferByLength(size), buffer);
		}

		if (size == -1) {
			closeSilence();
		}
		
		return size;
	}
	
	
	private void copyBuffers(ByteBuffer[] source, ByteBuffer target) {
		for (ByteBuffer buf : source) {
			if (buf.hasRemaining()) {
				target.put(buf);
			}
		}
	}


	/**
	 * read a ByteBuffer by using a delimiter. The default encoding will be used to decode the delimiter 
	 * To avoid memory leaks the {@link IReadWriteableConnection#readByteBufferByDelimiter(String, int)} method is generally preferable  
	 * <br> 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException, ClosedChannelException {
		return readByteBufferByDelimiter(delimiter, getEncoding());
	}


	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found     
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available	 
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, ClosedChannelException {
		return readByteBufferByDelimiter(delimiter, getEncoding(), maxLength);
	}


	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the delimiter encoding
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found     
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, ClosedChannelException {
		return readByteBufferByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}


	/**
	 * read a ByteBuffer by using a delimiter 
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is 
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter
	 * @param encoding    the delimiter encoding
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown	 
	 * @return the ByteBuffer
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found     
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed  
 	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, ClosedChannelException {
		ensureStreamIsOpen();
		
		int version = getReadBufferVersion();
		try {
			ByteBuffer[] buffers = readQueue.readByteBufferByDelimiter(delimiter.getBytes(encoding), maxLength);
			return onRead(buffers);

		} catch (MaxReadSizeExceededException mre) {
			if (isMoreInputDataExpected()) {
				throw mre;

			} else {
				closeSilence();
				throw new ClosedChannelException();
			}
			
		} catch (BufferUnderflowException bue) {
			if (isMoreInputDataExpected() || (version != getReadBufferVersion())) {
				throw bue;

			} else {
				closeSilence();
				throw new ExtendedClosedChannelException("channel is closed (read buffer size=" + readQueue.getSize() + ")");
			}
		}
	}
	
	


	/**
	 * read a ByteBuffer  
	 * 
	 * @param length   the length could be negative, in this case a empty array will be returned
	 * @return the ByteBuffer
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException, ClosedChannelException {
		ensureStreamIsOpen();
		
		if (length <= 0) {
			if (!isMoreInputDataExpected()) {
				closeSilence();
				throw new ClosedChannelException();
			}
			
			return onRead(new ByteBuffer[0]);
		}

        int version = getReadBufferVersion();
		try {		
			ByteBuffer[] buffers = readQueue.readByteBufferByLength(length);
			return onRead(buffers);
			
		} catch (BufferUnderflowException bue) {
			if (isMoreInputDataExpected() || (version != getReadBufferVersion())) {
				throw bue;

			} else {
				closeSilence();
				throw new ClosedChannelException();
			}
		}
	}


	/**
	 * read a byte array by using a delimiter
	 * 
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes 
	 * 
	 * @param delimiter   the delimiter  
	 * @return the read bytes
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed    
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, BufferUnderflowException, ClosedChannelException {
		return readBytesByDelimiter(delimiter, getEncoding());
	}


	
	
	/**
	 * read a byte array by using a delimiter
	 *
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes
	 *
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, ClosedChannelException {
		return readBytesByDelimiter(delimiter, getEncoding(), maxLength);
	}


	/**
	 * read a byte array by using a delimiter
	 *
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the delimiter encoding
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, ClosedChannelException {
		return readBytesByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}


	/**
	 * read a byte array by using a delimiter
	 *
     * For performance reasons, the ByteBuffer readByteBuffer method is
     * generally preferable to get bytes
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the delimiter encoding
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the read bytes
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, ClosedChannelException {
		return DataConverter.toBytes(readByteBufferByDelimiter(delimiter, encoding, maxLength));
	}

	

	/**
	 * read bytes by using a length definition 
	 *  
	 * @param length the amount of bytes to read  
	 * @return the read bytes
	 * @throws IOException If some other I/O error occurs
     * @throws IllegalArgumentException, if the length parameter is negative 
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed   
	 */		
	public byte[] readBytesByLength(int length) throws IOException, BufferUnderflowException, ClosedChannelException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}


	
	/**
	 * read a string by using a delimiter 
	 * 
	 * @param delimiter   the delimiter
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the default encoding is not supported
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed   
 	 */
	public String readStringByDelimiter(String delimiter) throws IOException, BufferUnderflowException, UnsupportedEncodingException, ClosedChannelException {
		return readStringByDelimiter(delimiter, Integer.MAX_VALUE);
	}



	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, ClosedChannelException {
		return readStringByDelimiter(delimiter, getEncoding(), maxLength);
	}



	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, ClosedChannelException {
		return readStringByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}



	/**
	 * read a string by using a delimiter
	 *
	 * @param delimiter   the delimiter
	 * @param encoding    the encoding 
	 * @param maxLength   the max length of bytes that should be read. If the limit is exceeded a MaxReadSizeExceededException will been thrown
	 * @return the string
	 * @throws MaxReadSizeExceededException If the max read length has been exceeded and the delimiter hasn�t been found 	 
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException If the given encoding is not supported
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, ClosedChannelException {
		return DataConverter.toString(readByteBufferByDelimiter(delimiter, encoding, maxLength), encoding);
	}


	
	/**
	 * read a string by using a length definition
	 * 
	 * @param length the amount of bytes to read  
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
     * @throws IllegalArgumentException, if the length parameter is negative 
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByLength(int length) throws IOException, BufferUnderflowException, UnsupportedEncodingException, ClosedChannelException {
		return readStringByLength(length, getEncoding());
	}


	
	/**
	 * read a string by using a length definition
	 * 
	 * @param length      the amount of bytes to read  
	 * @param encoding    the encoding 
	 * @return the string
	 * @throws IOException If some other I/O error occurs
 	 * @throws UnsupportedEncodingException if the given encoding is not supported 
     * @throws IllegalArgumentException, if the length parameter is negative 
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, ClosedChannelException {
		return DataConverter.toString(readByteBufferByLength(length), encoding);
	}

	
	
	

	/**
	 * transfer the data of the this source channel to the given data sink
	 * 
	 * @param dataSink   the data sink
	 * @param length     the size to transfer
	 * 
	 * @return the number of transfered bytes
	 * @throws ClosedChannelException If either this channel or the target channel is closed
	 * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public long transferTo(WritableByteChannel target, int length) throws IOException, ClosedChannelException, BufferUnderflowException, ClosedChannelException {
		
		if (length > 0) {
			long written = 0;

			int available = available();
			if (available < length) {
				length = available;
			}
			
			ByteBuffer[] buffers = readByteBufferByLength(length);
			for (ByteBuffer buffer : buffers) {
				while(buffer.hasRemaining()) {
					written += target.write(buffer);
				}
			}

			return written;

		} else {
			return 0;
		}
	}


	/**
	 * read a byte buffer by length. If the underlying data is fragmented over several ByteBuffer,
	 * the ByteBuffers will be merged
	 * 
	 * @param length   the length 
	 * @return the byte buffer
     * @throws IOException If some other I/O error occurs
 	 * @throws BufferUnderflowException if not enough data is available 
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	protected ByteBuffer readSingleByteBuffer(int length) throws IOException, ClosedChannelException, BufferUnderflowException, ClosedChannelException {
		ensureStreamIsOpen();
		
		int version = getReadBufferVersion();
		try {
			ByteBuffer buffer = readQueue.readSingleByteBuffer(length);
			return DataConverter.toByteBuffer(new ByteBuffer[] { buffer });

		} catch (BufferUnderflowException bue) {
			if (isMoreInputDataExpected() || (version != getReadBufferVersion())) {
				throw bue;

			} else {
				closeSilence();
				throw new ClosedChannelException();
			}
		}
	}




	/**
	 * writes a byte to the data sink
	 *  
	 * @param b   the byte to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available
	 * @throws IOException If some other I/O error occurs	 
 	 * @throws ClosedChannelException If the stream is closed    
	 */
	public int write(byte b) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		writeQueue.append(DataConverter.toByteBuffer(b));
		onWriteDataInserted();

		return 1;
	}


	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes   the bytes to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public int write(byte... bytes) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();

		if (bytes.length > 0) {
			writeQueue.append(DataConverter.toByteBuffer(bytes));
			onWriteDataInserted();

			return bytes.length;
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning length of byte array to send is 0");
			}

			return 0;
		}
	}


	/**
	 * writes bytes to the data sink
	 *  
	 * @param bytes    the bytes to write
	 * @param offset   The offset of the sub array to be used; must be non-negative and no larger than array.length. The new buffer`s position will be set to this value.
	 * @param length   The length of the sub array to be used; must be non-negative and no larger than array.length - offset. The new buffer`s limit will be set to offset + length.
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		if (bytes.length > 0) {
			ByteBuffer buffer = DataConverter.toByteBuffer(bytes, offset, length);
			int written = buffer.remaining();

			writeQueue.append(buffer);
			onWriteDataInserted();
			
			return written;
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning length of buffer array to send is 0");
			}

			return 0;
		}
	}



	/**
	 * writes a short to the data sink
	 *  
	 * @param s   the short value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public int write(short s) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		writeQueue.append(DataConverter.toByteBuffer(s));
		onWriteDataInserted();

		return 2;
	}
	
	

	/**
	 * writes a int to the data sink
	 *  
	 * @param i   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public int write(int i) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		writeQueue.append(DataConverter.toByteBuffer(i));
		onWriteDataInserted();

		return 4;
	}


	/**
	 * writes a long to the data sink
	 *  
	 * @param l   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public final int write(long l) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		writeQueue.append(DataConverter.toByteBuffer(l));
		onWriteDataInserted();

		return 8;
	}


	
	/**
	 * writes a double to the data sink
	 *  
	 * @param d   the int value to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public int write(double d) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
			
		writeQueue.append(DataConverter.toByteBuffer(d));
		onWriteDataInserted();

		return 8;
	}


	/**
	 * writes a message
	 * 
	 * @param message  the message to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public int write(String message) throws IOException, BufferOverflowException, ClosedChannelException {
		return write(message, getEncoding());
	}


	/**
	 * writes a message
	 * 
	 * @param message   the message to write
	 * @param encoding  the encoding
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public int write(String message, String encoding) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		ByteBuffer buffer = DataConverter.toByteBuffer(message, encoding);
		int written = buffer.remaining();

		writeQueue.append(buffer);
		onWriteDataInserted();

		return written;
	}



	/**
	 * writes a list of bytes to the data sink
	 *  
	 * @param buffers    the bytes to write
	 * @return the number of written bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public long write(List<ByteBuffer> buffers) throws IOException, BufferOverflowException, ClosedChannelException {
		if (buffers == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning buffer list to send is null");
			}
			return 0;
		}

		return write(buffers.toArray(new ByteBuffer[buffers.size()]));
	}






	/**
	 * see {@link GatheringByteChannel#write(ByteBuffer[], int, int)} and
	 * {@link INonBlockingConnection#setFlushmode(FlushMode)} 
	 */
	public long write(ByteBuffer[] srcs, int offset, int length) throws IOException, ClosedChannelException {
		if (srcs == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning buffer array to send is null");
			}
			return 0;
		}


		if ((getFlushmode() == FlushMode.ASYNC) &&  (previousWriteByteBuffers2 != null)) {
			ByteBuffer[] previous = previousWriteByteBuffers2.get();
			if ((previous != null) && (previous == srcs)) {
				LOG.warning("reuse of the byte buffer by calling the write(ByteBuffer[], ...) method in FlushMode.ASYNC can lead to race conditions (Hint: use FlushMode.SYNC)");
			}
		}
		

		long written = write(DataConverter.toByteBuffers(srcs, offset, length));
		
		if (flushmodeRef.get() == FlushMode.ASYNC) {
			previousWriteByteBuffers2 = new WeakReference<ByteBuffer[]>(srcs);
		}
		
		return written;
	}



	
	/**
	 * see {@link GatheringByteChannel#write(ByteBuffer[])} and
	 * {@link INonBlockingConnection#setFlushmode(FlushMode)} 
	 */
	public long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		if (!isSuppressReuseBufferWarning && (getFlushmode() == FlushMode.ASYNC) && (previousWriteByteBuffers != null)) {
			ByteBuffer[] previous = previousWriteByteBuffers.get();
			if ((previous != null) && (previous == buffers)) {
				LOG.warning("reuse of the byte buffer by calling the write(ByteBuffer[]) method in FlushMode.ASYNC can lead to race conditions (Hint: use FlushMode.SYNC)");
			}
		}
		
		
		if ((buffers == null) || (buffers.length == 0)) {
			return 0;
		}
		
		
		long written = 0;
		for (ByteBuffer buffer : buffers) {
			int size = buffer.remaining();
			
			if (size > 0) {
				onPreWrite(size);
				writeQueue.append(buffer);

				written += size;
				onWriteDataInserted();
			}
		}
		
		if (flushmodeRef.get() == FlushMode.ASYNC) {
			previousWriteByteBuffers = new WeakReference<ByteBuffer[]>(buffers);
		}

		return written;
	}

	

	/**
	 * see {@link WritableByteChannel#write(ByteBuffer)} and 
	 * {@link INonBlockingConnection#setFlushmode(FlushMode)} 
	 */
	public int write(ByteBuffer buffer) throws IOException, BufferOverflowException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();

		if (!isSuppressReuseBufferWarning && (getFlushmode() == FlushMode.ASYNC) && (previousWriteByteBuffer != null)) {
			ByteBuffer previous = previousWriteByteBuffer.get();
			if ((previous != null) && (previous == buffer)) {
				LOG.warning("reuse of the byte buffer by calling the write(ByteBuffer) method in FlushMode.ASYNC can lead to race conditions (Hint: use FlushMode.SYNC or deactivate log out put by setting system property " + IoProvider.SUPPRESS_REUSE_BUFFER_WARNING_KEY + " to true)");
			}
		}
		
	    
		if (buffer == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning buffer is null");
			}
			return 0;
		}

		int size = buffer.remaining();
		
		if (size > 0) {
			onPreWrite(size);
			writeQueue.append(buffer);
			onWriteDataInserted();
		}

		if (flushmodeRef.get() == FlushMode.ASYNC) {
			previousWriteByteBuffer = new WeakReference<ByteBuffer>(buffer);
		}


		return size;
	}
	

	
	/**
	 * call back method which will be called before writing data into the write queue 
	 * 
	 * @param size the size to write 
	 * @throws BufferOverflowException  if the write buffer max size is exceeded 
	 */
	protected void onPreWrite(int size) throws BufferOverflowException {
		
	}

	

	/**
	 * transfer the data of the file channel to this data sink
	 * 
	 * @param fileChannel the file channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public long transferFrom(FileChannel fileChannel) throws ClosedChannelException, IOException, SocketTimeoutException, ClosedChannelException {
	    
	    ensureStreamIsOpenAndWritable();
	    
	    if (getFlushmode() == FlushMode.SYNC) {
	        if (LOG.isLoggable(Level.FINE)) {
	            LOG.fine("tranfering file by using MappedByteBuffer (MAX_MAP_SIZE=" + TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE + ")");
	        }
	        
	        final long size = fileChannel.size();
	        long remaining = size;
	            
	        long offset = 0;
	        long length = 0;
	            
	        do {
	            if (remaining > TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE) {
	                length = TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE;
	            } else {
	                length = remaining;
	            }
	                
	            MappedByteBuffer buffer = fileChannel.map(MapMode.READ_ONLY, offset, length);
	            long written = write(buffer);
	                
	            offset += written;
	            remaining -= written;
	        } while (remaining > 0);
	    	        
	        return size;
	        
	    } else {
	        return transferFrom((ReadableByteChannel) fileChannel);
	    }
	}



	/**
	 * transfer the data of the source channel to this data sink
	 * 
	 * @param source the source channel
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public long transferFrom(ReadableByteChannel source) throws IOException, BufferOverflowException, ClosedChannelException {
		return transferFrom(source, TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE);
	}


	
	/**
	 * transfer the data of the source channel to this data sink
	 * 
	 * @param source     the source channel
	 * @param chunkSize  the chunk size to use
	 * @return the number of transfered bytes
	 * @throws BufferOverflowException  If the no enough space is available 
	 * @throws IOException If some other I/O error occurs
 	 * @throws ClosedChannelException If the stream is closed   
	 */
	public long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException, BufferOverflowException, ClosedChannelException {		
		return transfer(source, this, chunkSize);
	}
	
	

	private long transfer(ReadableByteChannel source, WritableByteChannel target, int chunkSize) throws IOException, ClosedChannelException {
		ensureStreamIsOpenAndWritable();
		
		long transfered = 0;

		int read = 0;
		do {
			ByteBuffer transferBuffer = ByteBuffer.allocate(chunkSize);
			read = source.read(transferBuffer);

			if (read > 0) {
				if (transferBuffer.remaining() == 0) {
					transferBuffer.flip();
					target.write(transferBuffer);

				} else {
					transferBuffer.flip();
					target.write(transferBuffer.slice());
				}

				transfered += read;
			}
		} while (read > 0);

		return transfered;
	}

	

	/**
	 * Marks the read position in the connection. Subsequent calls to resetToReadMark() will attempt
	 * to reposition the connection to this point.
	 *
	 */
	public final void markReadPosition() {
		readQueue.markReadPosition();
	}



	
	/**
	 * Marks the write position in the connection. 
	 */
	public final void markWritePosition() {
		if (isAutoflush()) {
			throw new UnsupportedOperationException("write mark is only supported for mode autoflush off");
		}

		writeQueue.markWritePosition();
	}


	/**
	 * Resets to the marked write position. If the connection has been marked,
	 * then attempt to reposition it at the mark.
	 *
	 * @return true, if reset was successful
	 */
	public final boolean resetToWriteMark() {
		return writeQueue.resetToWriteMark();
	}


	
	/**
	 * Resets to the marked read position. If the connection has been marked,
	 * then attempt to reposition it at the mark.
	 *
	 * @return true, if reset was successful
	 */
	public final boolean resetToReadMark() {
		return readQueue.resetToReadMark();
	}


	
	/**
	 * remove the read mark
	 */
	public final void removeReadMark() {
		readQueue.removeReadMark();
	}

	
	/**
	 * remove the write mark
	 */
	public final void removeWriteMark() {
		writeQueue.removeWriteMark();
	}


	/**
	 * resets the stream
	 * 
	 * @return true, if the stream has been reset
	 */
	protected boolean reset() {
		readQueue.reset();
		writeQueue.reset();
		
		defaultEncodingRef.set(IConnection.INITIAL_DEFAULT_ENCODING);
		autoflush.set(IConnection.DEFAULT_AUTOFLUSH);
		flushmodeRef.set(IConnection.DEFAULT_FLUSH_MODE);
		attachmentRef.set(null);

		return true;
	}
	


	/**
	 * notification, that data has been inserted 
	 * 
	 * @throws IOException  if an exception occurs 
	 * @throws ClosedChannelException  if the stream is closed
	 */
	protected void onWriteDataInserted() throws IOException, ClosedChannelException {

	}
	

	/**
	 * gets the write buffer size
	 * 
	 * @return the write buffer size 
	 */
	protected final int getWriteBufferSize() {
		return writeQueue.getSize();
	}

	
	/**
	 * returns if the write buffer is empty
	 * @return true, if the write buffer is empty
	 */
	protected final boolean isWriteBufferEmpty() {
		return writeQueue.isEmpty();
	}

	
	/**
	 * drains the write buffer 
	 * 
	 * @return the write buffer content
	 */
	protected ByteBuffer[] drainWriteQueue() {
		return writeQueue.drain();
	}
	
	/**
	 * drains the read buffer 
	 * 
	 * @return the read buffer content
	 */
	protected ByteBuffer[] drainReadQueue() {
		return readQueue.readAvailable();
	}
	
	

	/**
	 * copies the read buffer 
	 * 
	 * @return the read buffer content
	 */
	protected ByteBuffer[] copyReadQueue() {
		return readQueue.copyAvailable();
	}


	/**
	 * returns if the read buffer is empty
	 * 
	 * @return  true, if the read buffer is empty
	 */
	protected final boolean isReadBufferEmpty() {
		return readQueue.isEmpty();
	}


	/**
	 * append data to the read buffer
	 * 
	 * @param data the data to append
	 */
	protected final void appendDataToReadBuffer(ByteBuffer[] data, int size) {
		readQueue.append(data, size);
		onPostAppend();
	}
	

	

	protected void onPostAppend() {
		
	}

 

	/**
	 * prints the read buffer content
	 *  
	 * @param encoding the encoding
	 * @return the read buffer content
	 */
	protected final String printReadBuffer(String encoding) {
		return readQueue.toString(encoding);
	}

	/**
	 * prints the write buffer content 
	 * 
	 * @param encoding the encoding 
	 * @return the write buffer content
	 */
	protected final String printWriteBuffer(String encoding) {
		return writeQueue.toString(encoding);
	}
	
	

	private void ensureStreamIsOpen() throws ClosedChannelException {
		if (!isOpen.get()) {			
			throw new ExtendedClosedChannelException("channel is closed (read buffer size=" + readQueue.getSize() + ")");
		}		
	}
	

	private void ensureStreamIsOpenAndWritable() throws ClosedChannelException {
		if (!isOpen.get()) {
			throw new ExtendedClosedChannelException("could not write. Channel is closed (" + getInfo() + ")");
		}		
		
		if(!isDataWriteable()) {
		    if (LOG.isLoggable(Level.FINE)) {
		        LOG.fine("is not writeable clsoing connection " + getInfo());
		    }
			closeSilence();
			throw new ExtendedClosedChannelException("could not write. channel is close or not initialized (" + getInfo() + ")");
		}
	}
	
	/**
	 * returns a info string 
	 * @return a info string
	 */
	protected String getInfo() {
		return "readBufferSize=" + readQueue.getSize();
	}
	
	
	public static interface ISink {
        
        /**
         * clean the sink
         */
        void reset();
    
        

        /**
         * returns true, if empty
         *
         * @return true, if empty
         */
        boolean isEmpty();        

        
        /**
         * return the current size
         *
         * @return  the current size
         */
        int getSize();
        
        
        void append(ByteBuffer data);
        
        
        /**
         * append a list of byte buffer to this sink. By adding a list,
         * the list becomes part of to the buffer, and should not be modified outside the buffer
         * to avoid side effects
         *
         * @param bufs  the list of ByteBuffer
         */
        void append(ByteBuffer[] bufs);

        
        /**
         * drain the sink
         *
         * @return the queue content
         */
        ByteBuffer[] drain();
        
        
        ByteBuffer[] copy();
        

        String toString(String encoding);
	}	
	
	
	public static interface ISource {
	        

        /**
         * clean the source
         */
        void reset();


        /**
         * return a int, which represent the  version.
         * this value will increase with modification
         *
         * @return the modify version
         */
        int getVersion(boolean mark);
        
        
        void setVersion(int version);
        
    
        /**
         * return the current size
         *
         * @return  the current size
         */
        int getSize();



        /**
         * append a byte buffer array to this source. By adding a array,
         * the array becomes part of to the buffer, and should not be modified outside the buffer
         * to avoid side effects
         *
         * @param bufs  the ByteBuffers
         * @param size  the size 
         */
        void append(ByteBuffer[] bufs, int size); 
        
        
        void addFirst(ByteBuffer[] bufs);
        
        
        
        /**
         * drain the source
         * 
         * @return the content
         */
        ByteBuffer[] drain();        

        
        ByteBuffer[] copy();
        
    
        
        /**
         * read bytes
         *
         * @param length  the length
         * @return the read bytes
         * @throws BufferUnderflowException if the buffer`s limit has been reached
         */
        ByteBuffer readSingleByteBuffer(int length) throws BufferUnderflowException;        
        
        
        ByteBuffer[] readByteBufferByLength(int length) throws BufferUnderflowException;        
        
        
        ByteBuffer[] readByteBufferByDelimiter(byte[] delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException;

        
        /**
         * return the index of the delimiter or -1 if the delimiter has not been found 
         * 
         * @param delimiter         the delimiter
         * @param maxReadSize       the max read size
         * @return the position of the first delimiter byte
         * @throws IOException in an exception occurs
         * @throws MaxReadSizeExceededException if the max read size has been reached 
         */
        int retrieveIndexOf(byte[] delimiter, int maxReadSize) throws IOException, MaxReadSizeExceededException;
        
     
        String toString(String encoding);
	}
}
