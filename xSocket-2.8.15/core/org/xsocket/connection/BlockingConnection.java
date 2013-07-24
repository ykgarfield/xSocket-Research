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



import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;




/**
 * Implementation of the <code>IBlockingConnection</code> interface. Internally a {@link INonBlockingConnection}
 * will be used. A <code>BlockingConnection</code> wraps a <code>INonBlockingConnection</code>. There are two ways to 
 * create a <code>BlockingConnection</code>: 
 * <ul>
 *   <li>by passing over the remote address (e.g. host name & port), or</li>
 *   <li>by passing over a <code>INonBlockingConnection</code>, which will be wrapped</li>
 * </ul>
 * <br><br>
 * 
 * A newly created connection is in the open state. Write or read methods can be called immediately <br><br>
 *
 * <b>The methods of this class are not thread-safe.</b>
 *
 * @author grro@xsocket.org
 */
public class BlockingConnection implements IBlockingConnection {
	
	private static final Logger LOG = Logger.getLogger(BlockingConnection.class.getName());
	
	public static final String READ_TIMEOUT_KEY  = "org.xsocket.connection.readtimeoutMillis";
	public static final int DEFAULT_TIMEOUT = Integer.parseInt(System.getProperty(READ_TIMEOUT_KEY, Integer.toString(DEFAULT_READ_TIMEOUT)));


 
	private final ReadNotificationHandler handler = new ReadNotificationHandler();
	private final Object readGuard = new Object();
	
	private final INonBlockingConnection delegate;
	private int readTimeout = DEFAULT_TIMEOUT;
	
	private AtomicBoolean disconnected = new AtomicBoolean(false);
	private AtomicBoolean idleTimeout = new AtomicBoolean(false);
	private AtomicBoolean connectionTimeout = new AtomicBoolean(false);
	
	private AtomicBoolean isClosed = new AtomicBoolean(false);

	
	
	/**
	 * constructor. <br><br>
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port) throws IOException {
		this(new InetSocketAddress(hostname, port), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false);
	}




	/**
	 * constructor. <br><br>
	 *
     * @param hostname             the remote host
	 * @param port		           the port of the remote host to connect
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(hostname, port), null, true, Integer.MAX_VALUE, options,  null, false);
	}


	/**
	 * constructor
	 *
	 * @param address  the remote host address
	 * @param port     the remote host port
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port) throws IOException {
		this(address, port, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false);
	}



	/**
	 * constructor
	 *
	 * @param address                the remote host address
	 * @param port                   the remote host port
	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, int connectTimeoutMillis) throws IOException{
		this(new InetSocketAddress(address, port), null, true, connectTimeoutMillis, new HashMap<String, Object>(), null, false);
	}


	/**
	 * constructor
	 *
	 * @param address              the remote host name
	 * @param port                 the remote host port
	 * @param sslContext           the sslContext to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn);
	}


	/**
	 * constructor
	 *
	 * @param address                the remote host name
	 * @param port                   the remote host port
     * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param sslContext             the sslContext to use
	 * @param sslOn                  true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, true, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn);
	}




	/**
	 * constructor
	 *
	 * @param address              the remote host name
	 * @param port                 the remote host port
	 * @param options              the socket options
	 * @param sslContext           the sslContext to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
     * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, true, Integer.MAX_VALUE, options, sslContext, sslOn);
	}


	/**
	 * constructor
	 *
	 * @param address                the remote host name
	 * @param port                   the remote host port
     * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @param sslContext             the sslContext to use
	 * @param sslOn                  true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
     * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(InetAddress address, int port, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), null, true, connectTimeoutMillis, options, sslContext, sslOn);
	}



	/**
	 * constructor
	 *
	 * @param hostname             the remote host name
	 * @param port                 the remote host port
	 * @param sslContext           the sslContext to use or <null>
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
     * @throws IOException If some other I/O error occurs
	 */
	public BlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn);
	}



	/**
	 * constructor
	 * 
	 * @param remoteAddress           the remote address
	 * @param localAddress            the local address
     * @param waitForConnect          true, if the constructor should block until the connection is established. 
	 * @param connectTimeoutMillis    the connection timeout
	 * @param options                 the socket options
	 * @param sslContext              the ssl context or <null>
	 * @param sslOn                   true, if ssl mode 
     * @throws IOException If some other I/O error occurs            
	 */
	public BlockingConnection(InetSocketAddress remoteAddress, InetSocketAddress localAddress, boolean waitForConnect, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this.delegate= new NonBlockingConnection(remoteAddress, localAddress, handler, waitForConnect, connectTimeoutMillis, options, sslContext, sslOn);
	}


	/**
	 * Constructor. <br><br>
	 * 
	 * By creating a BlokingConnection based on the given NonBlockingConnection the 
	 * assigned handler of the NonBlockingConnection will be removed (if exists). Take
	 * care by using this on the server-side (see tutorial for more informations). 
	 * 
	 * @param delegate    the underlying non blocking connection
     * @throws IOException If some other I/O error occurs  
	 */
	public BlockingConnection(INonBlockingConnection delegate) throws IOException {
		this.delegate = delegate;
		delegate.setHandler(handler);
	}
	
	
	final INonBlockingConnection getDelegate() {
		return delegate;
	}


	
	/**
	 * {@inheritDoc}
	 */
	public void setReadTimeoutMillis(int timeout) throws IOException {
		this.readTimeout = timeout;

		int soTimeout = (Integer) delegate.getOption(SO_TIMEOUT);
		if (timeout > soTimeout) {
			delegate.setOption(SO_TIMEOUT, timeout);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void setReceiveTimeoutMillis(int timeout) throws IOException {
		setReadTimeoutMillis(timeout);
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getReceiveTimeoutMillis() throws IOException {
		return getReadTimeoutMillis();
	}	
	

	

	/**
	 * {@inheritDoc}
	 */
	public int getReadTimeoutMillis() throws IOException {
		return readTimeout;
	}	
	
	/**
	 * {@inheritDoc}
	 */	
	public int getMaxReadBufferThreshold() {
		return delegate.getMaxReadBufferThreshold();
	}
	
	/**
	 * {@inheritDoc}
	 */		
	public void setMaxReadBufferThreshold(int size) {
		delegate.setMaxReadBufferThreshold(size);
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setEncoding(String defaultEncoding) {
		delegate.setEncoding(defaultEncoding);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final String getEncoding() {
		return delegate.getEncoding();
	}
	
	
	/**
	 * return if the data source is open. Default is true
	 * @return true, if the data source is open
	 */
	public final boolean isOpen() {
		return !isClosed.get() && delegate.isOpen();
	}
		 
	/**
     * {@inheritDoc}
     */
    public boolean isServerSide() {
        return delegate.isServerSide();
    }
    
	
	/**
	 * {@inheritDoc}
	 */
	public final void close() throws IOException {
	    isClosed.set(true);
		delegate.close();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final void flush() throws ClosedChannelException, IOException, SocketTimeoutException {
		delegate.flush();
	}
	
	
	/**
	 * {@inheritDoc}
	 */

	public String getId() {
		return delegate.getId();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getRemoteAddress() {
		return delegate.getRemoteAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int getRemotePort() {
		return delegate.getRemotePort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return delegate.getLocalAddress();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return delegate.getLocalPort();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final int getPendingWriteDataSize() {
		return delegate.getPendingWriteDataSize();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void suspendRead() throws IOException {
		delegate.suspendReceiving();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isReadSuspended() {
		return delegate.isReceivingSuspended();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void resumeRead() throws IOException {
		delegate.resumeReceiving();
	}
	
	/**
	 * {@inheritDoc}
	 */	
	public boolean isReceivingSuspended() {
		return delegate.isReceivingSuspended();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void resumeReceiving() throws IOException {
		delegate.resumeReceiving();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void suspendReceiving() throws IOException {
		delegate.suspendReceiving();
	}
	
		
	/**
	 * {@inheritDoc}
	 */	
	public void setFlushmode(FlushMode flushMode) {
		delegate.setFlushmode(flushMode);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public FlushMode getFlushmode() {
		return delegate.getFlushmode();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setOption(String name, Object value) throws IOException {
		delegate.setOption(name, value);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Object getOption(String name) throws IOException {
		return delegate.getOption(name);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public final Map<String, Class> getOptions() {
		return delegate.getOptions();
	}



	/**
	 * {@inheritDoc}
	 */
	public final void setIdleTimeoutMillis(long timeoutInMillis) {
		delegate.setIdleTimeoutMillis(timeoutInMillis);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final long getIdleTimeoutMillis() {
		return delegate.getIdleTimeoutMillis();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void setConnectionTimeoutMillis(long timeoutMillis) {
		delegate.setConnectionTimeoutMillis(timeoutMillis);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long getConnectionTimeoutMillis() {
		return delegate.getConnectionTimeoutMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToConnectionTimeout() {
		return delegate.getRemainingMillisToConnectionTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToIdleTimeout() {
		return delegate.getRemainingMillisToIdleTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setAttachment(Object obj) {
		delegate.setAttachment(obj);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Object getAttachment() {
		return delegate.getAttachment();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final void setAutoflush(boolean autoflush) {
		delegate.setAutoflush(autoflush);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isAutoflush() {
		return delegate.isAutoflush();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void activateSecuredMode() throws IOException {
		delegate.activateSecuredMode();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public void deactivateSecuredMode() throws IOException {
		delegate.deactivateSecuredMode();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isSecure() {
		return delegate.isSecure();
	}


	
	
	/**
	 * {@inheritDoc}
	 */
	public final void markReadPosition() {
		delegate.markReadPosition();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void markWritePosition() {
		delegate.markWritePosition();
	}
	
	
	/**
     * {@inheritDoc}.
     */
	public void unread(ByteBuffer[] buffers) throws IOException {
	    delegate.unread(buffers);
	}
	
	
	/**
     * {@inheritDoc}.
     */
	public void unread(byte[] bytes) throws IOException {
	    delegate.unread(bytes);
	}
	
	
	/**
     * {@inheritDoc}.
     */
	public void unread(ByteBuffer buffer) throws IOException {
	    delegate.unread(buffer);
	}
	
	
	/**
     * {@inheritDoc}.
     */
	public void unread(String text) throws IOException {
	    delegate.unread(text);
	    
	}
	
	
	/**
	 * {@inheritDoc}.
	 */
	public final int read(ByteBuffer buffer) throws IOException, ClosedChannelException {
		int size = buffer.remaining();
		if (size < 1) {
			return 0;
		}

		return new ByteBufferReadTask(buffer).read();
	}

	
    private final class ByteBufferReadTask extends ReadTask<Integer> {
        private final ByteBuffer buffer;
        
        public ByteBufferReadTask(ByteBuffer buffer) {
            this.buffer = buffer;
        }
        
        @Override
        Integer doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
            try {
                available(1); // ensure that at minimum the required length is available
                return delegate.read(buffer);
                
            } catch (ClosedChannelException cce) {
                if (!isClosed.getAndSet(true)) {
                    return -1;
                } else {
                    throw cce;
                }
            }
        }
    }


    	  
	private Integer getSize() {
		try {
			return delegate.available();
		} catch (IOException ioe) {
			return null;
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final byte readByte() throws IOException, SocketTimeoutException {
	    return new ByteReadTask().read(); 
	}
	
    private final class ByteReadTask extends ReadTask<Byte> {
        
        @Override
        Byte doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
            return delegate.readByte();
        }
    }

	

	/**
	 * {@inheritDoc}
	 */
	public final short readShort() throws IOException, SocketTimeoutException {
	    return new ShortReadTask().read(); 
	}

    private final class ShortReadTask extends ReadTask<Short> {
        
        @Override
        Short doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
            return delegate.readShort();
        }
    }

	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int readInt() throws IOException, SocketTimeoutException {
		return new IntegerReadTask().read(); 
	}
	
    private final class IntegerReadTask extends ReadTask<Integer> {
        
        @Override
        Integer doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
            return delegate.readInt();
        }
    }

	
	
	/**
	 * {@inheritDoc}
	 */
	public final long readLong() throws IOException, SocketTimeoutException {
	    return new LongReadTask().read();
	}
	
    private final class LongReadTask extends ReadTask<Long> {
        
        @Override
        Long doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
            return delegate.readLong();
        }
    }

	
	/**
	 * {@inheritDoc}
	 */
	public final double readDouble() throws IOException, SocketTimeoutException {
	    return new DoubleReadTask().read(); 
	}
		
	private final class DoubleReadTask extends ReadTask<Double> {
        
        @Override
        Double doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
            return delegate.readDouble();
        }
    }
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, SocketTimeoutException {
		return readByteBufferByDelimiter(delimiter, getEncoding());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException, SocketTimeoutException {
		return readByteBufferByDelimiter(delimiter, getEncoding(), maxLength);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, SocketTimeoutException {
		return readByteBufferByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, MaxReadSizeExceededException, SocketTimeoutException {
	    return new ByteBuffersByDelimiterReadTask(delimiter, encoding, maxLength).read();
	}
		
    private final class ByteBuffersByDelimiterReadTask extends ReadTask<ByteBuffer[]> {
        private final String delimiter;
        private final String encoding;
        private final int maxLength;
        
        public ByteBuffersByDelimiterReadTask(String delimiter, String encoding, final int maxLength) {
            this.delimiter = delimiter;
            this.encoding = encoding;
            this.maxLength = maxLength;
        }
        
        @Override
        ByteBuffer[] doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
            return delegate.readByteBufferByDelimiter(delimiter, encoding, maxLength);
        }
    }
	
	
	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer[] readByteBufferByLength(int length) throws IOException, SocketTimeoutException {
		if (length <= 0) {
			return new ByteBuffer[0];
		}
		
		return new ByteBuffersByLengthReadTask(length).read();
	}
	
	
	private final class ByteBuffersByLengthReadTask extends ReadTask<ByteBuffer[]> {
	    private final int length;
	    
	    public ByteBuffersByLengthReadTask(int length) {
	        this.length = length;
        }
	    
	    @Override
	    ByteBuffer[] doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
	        return delegate.readByteBufferByLength(length);
	    }
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter) throws IOException, SocketTimeoutException {
		return readBytesByDelimiter(delimiter, getEncoding());
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException, SocketTimeoutException {
		return readBytesByDelimiter(delimiter, getEncoding(), maxLength);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, SocketTimeoutException {
		return readBytesByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, MaxReadSizeExceededException, SocketTimeoutException {
		return DataConverter.toBytes(readByteBufferByDelimiter(delimiter, encoding, maxLength));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final byte[] readBytesByLength(int length) throws IOException,  SocketTimeoutException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter) throws IOException, UnsupportedEncodingException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, Integer.MAX_VALUE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, getEncoding(), maxLength);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, String encoding) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException {
		return readStringByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException {
		return DataConverter.toString(readByteBufferByDelimiter(delimiter, encoding, maxLength), encoding);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByLength(int length) throws IOException, UnsupportedEncodingException, SocketTimeoutException {
		return readStringByLength(length, getEncoding());
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final String readStringByLength(int length, String encoding) throws IOException, UnsupportedEncodingException, SocketTimeoutException {
		return DataConverter.toString(readByteBufferByLength(length), encoding);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long transferTo(WritableByteChannel target, int length) throws IOException, SocketTimeoutException {
		long written = 0;
		
		ByteBuffer[] buffers = readByteBufferByLength(length);
		for (ByteBuffer buffer : buffers) {
			written += target.write(buffer);
		}
		
		return written;
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public final boolean resetToWriteMark() {
		return delegate.resetToWriteMark();
	}



	/**
	 * {@inheritDoc}
	 */
	public final boolean resetToReadMark() {
		return delegate.resetToReadMark();
	}


	/**
	 * {@inheritDoc}
	 */
	public final void removeReadMark() {
		delegate.removeReadMark();
	}


	/**
	 * {@inheritDoc}
	 */
	public final void removeWriteMark() {
		delegate.removeWriteMark();
	}

	

	/**
	 * {@inheritDoc}
	 */
	public final int write(byte b) throws IOException, BufferOverflowException {
		return delegate.write(b);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(byte... bytes) throws IOException {
		return delegate.write(bytes);
	}
	
	
	/**
     * {@inheritDoc}
     */
	public void write(byte[] bytes, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegate.write(bytes, writeCompletionHandler);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(byte[] bytes, int offset, int length) throws IOException {
		return delegate.write(bytes, offset, length);
	}

	
	
	/**
     * {@inheritDoc}
     */
	public void write(byte[] bytes, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        delegate.write(bytes, offset, length, writeCompletionHandler);	    
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(short s) throws IOException {
		return delegate.write(s);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(int i) throws IOException {
		return delegate.write(i);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(long l) throws IOException {
		return delegate.write(l);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(double d) throws IOException {
		return delegate.write(d);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(String message) throws IOException {
		return delegate.write(message);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(String message, String encoding) throws IOException {
		return delegate.write(message, encoding);
	}
	
	
	/**
     * {@inheritDoc}
     */
	public void write(String message, String encoding, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegate.write(message, encoding, writeCompletionHandler);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final long write(ArrayList<ByteBuffer> buffers) throws IOException {
		return delegate.write(buffers);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long write(List<ByteBuffer> buffers) throws IOException {
		return delegate.write(buffers);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final long write(ByteBuffer[] buffers) throws IOException {
		return delegate.write(buffers);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		return delegate.write(srcs, offset, length);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */
	public final int write(ByteBuffer buffer) throws IOException {
		return delegate.write(buffer);
	}
	

	/**
     * {@inheritDoc}
     */
	public void write(ByteBuffer buffer, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegate.write(buffer, writeCompletionHandler);
	}


	/**
     * {@inheritDoc}
     */
	public void write(ByteBuffer[] buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegate.write(buffers, writeCompletionHandler);	    
	}
	
	
    /**
     * {@inheritDoc}
     */
	public void write(ByteBuffer[] srcs, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegate.write(srcs, offset,length, writeCompletionHandler);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final long transferFrom(ReadableByteChannel source) throws IOException {
		return delegate.transferFrom(source);
	}
	
	/**
     * {@inheritDoc}
     */
	public void write(List<ByteBuffer> buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	    delegate.write(buffers, writeCompletionHandler);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException {
		return delegate.transferFrom(source, chunkSize);
	}
	
	

	
	public long transferFrom(FileChannel source) throws IOException {
		return delegate.transferFrom(source);
	}
	
		
	@Override
	public String toString() {
		return delegate.toString();
	}

	
	private void onReadDataInserted() {
	    synchronized (readGuard) {
	        readGuard.notifyAll();
	    }
	}
	    	

	final class ReadNotificationHandler implements IDataHandler, IDisconnectHandler, IConnectionTimeoutHandler, IIdleTimeoutHandler, IUnsynchronized {
			
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			onReadDataInserted();
			return true;
		}
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			disconnected.set(true);
			onReadDataInserted();
			return true;
		}
		
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			connectionTimeout.set(true);
			onReadDataInserted();
			
			connection.close();
			return true;
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			idleTimeout.set(true);
			onReadDataInserted();
			
			connection.close();
			return true;
		}		
	}
	
    private abstract class ReadTask<T extends Object> {
        
        final T read() throws IOException, SocketTimeoutException, MaxReadSizeExceededException {
            long start = System.currentTimeMillis();
            long remainingTime = readTimeout;
                
            do {
                int revision = delegate.getReadBufferVersion();
                try {
                    
                    try {
                        return doRead();
                    } catch (RevisionAwareBufferUnderflowException rbe) {
                        synchronized (readGuard) {
                            if (rbe.getRevision() != delegate.getReadBufferVersion()) {
                                continue;
                            } else { 
                                throw new BufferUnderflowException();  // "jump" into catch (BufferUnderflowException)
                            }
                        }
                    }

                            
                } catch (BufferUnderflowException bue) {
                    
                    synchronized (readGuard) {
                            
                        // no notification event is occurred meanwhile
                        if (revision == delegate.getReadBufferVersion()) {

                            // no more data expected? (connection is destroyed)
                            if (disconnected.get()) {
                                throw new ExtendedClosedChannelException("channel " + getId() + " is closed (read buffer size=" + getSize() + ")");
                                    
                            } else {
                                if (LOG.isLoggable(Level.FINE)) {
                                    LOG.fine("waiting for more reveived data (guard: " + readGuard + ")");
                                }
                                    
                                try {
                                    readGuard.wait(remainingTime);
                                } catch (InterruptedException ie) { 
                                    // Restore the interrupted status
                                    Thread.currentThread().interrupt();
                                }
                            }
                        } 
                    }
                }

                long elpased = System.currentTimeMillis() - start;
                remainingTime -= elpased;
            } while (remainingTime > 0);
            
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("receive timeout " + readTimeout + " sec reached. throwing timeout exception");
            }

            throw new SocketTimeoutException("timeout " + readTimeout + " millis reached");          
        }
            
         
        protected final int available(int required) throws IOException, BufferUnderflowException, ClosedChannelException {
            
            synchronized (readGuard) {
                int available = delegate.available();
                
                if (available == -1) {
                    throw new ClosedChannelException();

                } else if (available < required) {
                     throw new RevisionAwareBufferUnderflowException(delegate.getReadBufferVersion());

                }  else {
                    return available;
                }
            }            
        }
        
        abstract T doRead() throws IOException, SocketTimeoutException, MaxReadSizeExceededException;
    }   
    
    private static final class RevisionAwareBufferUnderflowException extends BufferUnderflowException {
        private static final long serialVersionUID = -5623771436953505286L;
        
        private final int revision;
        
        public RevisionAwareBufferUnderflowException(int revision) {
            this.revision = revision;
        }
        
        public int getRevision() {
            return revision;
        }
    }
}
