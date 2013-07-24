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
package org.xsocket.datagram;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;


import org.xsocket.DataConverter;
import org.xsocket.IDataSink;
import org.xsocket.IDataSource;
import org.xsocket.MaxReadSizeExceededException;



/**
 * a datagram packet 
 * 
 * 
 * @author grro@xsocket.org
 */
public final class UserDatagram implements IDataSource, IDataSink {
	
	private SocketAddress remoteSocketAddress = null;
	private ByteBuffer data;
	private String defaultEncoding = "UTF-8";
		
	
	/**
	 * constructor. creates an empty packet  
	 * 
	 * @param size         the packet size
	 */
	public UserDatagram(int size) {
		init(remoteSocketAddress, ByteBuffer.allocate(size));
	}
	
	
	/**
	 * constructor. creates an empty packet by setting the target remote address 
	 * 
	 * @param remoteHost   the destination hostname
	 * @param remotePort   the destination port number
	 * @param size         the packet size
	 */
	public UserDatagram(String remoteHost, int remotePort, int size) {
		init(new InetSocketAddress(remoteHost, remotePort), ByteBuffer.allocate(size));
	}
	
	
	/**
	 * constructor. creates an empty packet by setting the target remote address 
	 * 
	 * @param remoteAddress the destination address
	 * @param remotePort    the destination port number
	 * @param size         the packet size
	 */
	public UserDatagram(InetAddress remoteAddress, int remotePort, int size) {
		init(new InetSocketAddress(remoteAddress, remotePort), ByteBuffer.allocate(size));
	}
	
	/**
	 * constructor. creates an empty packet by setting the target remote address 
	 * 
	 * @param address      the destination address
	 * @param size         the packet size
	 */
	public UserDatagram(SocketAddress address, int size) {
		init(address, ByteBuffer.allocate(size));
	}
	
	
	/**
	 * constructor. creates packet, and sets the content with the given buffer
	 * 
	 * @param data    the data which will be written into the buffer
	 */
	public UserDatagram(ByteBuffer data) {
		this(null, data);
	}
	

	/**
	 * constructor. creates packet by setting the target remote address,
	 * and sets the content with the given buffer
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 */
	public UserDatagram(SocketAddress remoteSocketAddress, ByteBuffer data) {
		this(remoteSocketAddress, data, "UTF-8");
	}

	
	/**
	 * constructor. creates packet by setting the target remote address,
	 * and sets the content with the given buffer
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 * @param defaultEncoding      the default encoding to use
	 */
	UserDatagram(SocketAddress remoteSocketAddress, ByteBuffer data, String defaultEncoding) {
		init(remoteSocketAddress, data);
		this.defaultEncoding = defaultEncoding;
	}

	
	/**
	 * constructor. creates packet sets the content with the given byte array
	 * 
	 * @param data                 the data which will be written into the buffer
	 */
	public UserDatagram(byte[] data) {
		this(null, data);
	}
		
	
	/**
	 * constructor. creates packet by setting the target remote address,
	 * and sets the content with the given byte array 
	 * 
	 * @param remoteHost   the destination hostname
	 * @param remotePort   the destination port number
	 * @param data         the data which will be written into the buffer
	 */
	public UserDatagram(String remoteHost, int remotePort, byte[] data) {
		this(new InetSocketAddress(remoteHost, remotePort), data);
	}
	
	

	
	/**
	 * constructor. creates packet by setting the target remote address,
	 * and sets the content with the given byte array 
	 * 
	 * @param remoteSocketAddress  the destination address
	 * @param data                 the data which will be written into the buffer
	 */
	public UserDatagram(SocketAddress remoteSocketAddress, byte[] data) {
		ByteBuffer buffer = ByteBuffer.wrap(data);
		buffer.position(buffer.limit());
		init(remoteSocketAddress, buffer);
	}
	
	
	
	private void init(SocketAddress remoteSocketAddress, ByteBuffer data) {
		this.remoteSocketAddress = remoteSocketAddress;
		this.data = data;
	}
	
	
	/**
	 * prepares the packet to send
	 *
	 */
	void prepareForSend() {
		data.clear();
	}
	

	/**
	 * set the remote socket address
	 * @param remoteSocketAddress   the remote socket address
	 */
	void setRemoteAddress(SocketAddress remoteSocketAddress) {
		this.remoteSocketAddress = remoteSocketAddress;
	}
	
	
	/**
	 * return the underlying data buffer
	 *  
	 * @return the underlying data buffer
	 */
	protected ByteBuffer getData() {
		return data;
	}

	
	public String getEncoding() {
		return defaultEncoding;
	}
	
	
	public void setEncoding(String encoding) {
		this.defaultEncoding = encoding;
	}
		
	/**
	 * Returns the socket address of the machine to which this packet is being sent 
	 * or from which the packet was received.
	 *  
	 * @return the socket address
	 */
	public SocketAddress getRemoteSocketAddress() {
		return remoteSocketAddress;
	}
	
	
	/**
     * Returns the address of the machine to which this packet is being sent 
	 * or from which the packet was received.
	 * 
	 * @return  the address
	 */
	public InetAddress getRemoteAddress() {
		if (remoteSocketAddress instanceof InetSocketAddress) {
			return ((InetSocketAddress) remoteSocketAddress).getAddress(); 
		} else {
			return null;
		}
	}
	
	
	/**
     * Returns the port number of the machine to which this packet is being sent 
	 * or from which the packet was received.
	 * 
	 * @return the port number
	 */
	public int getRemotePort() {
		if (remoteSocketAddress instanceof InetSocketAddress) {
			return ((InetSocketAddress) remoteSocketAddress).getPort(); 
		} else {
			return -1;
		}
	}
	
	
	/**
	 * read a byte
	 * 
	 * @return the byte value
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */	
	public byte readByte() throws IOException, BufferUnderflowException {
		return data.get();
	}
	
	

	
	/**
	 * read a ByteBuffer by using a length defintion 
     *
	 * @param length the amount of bytes to read
	 * @return the ByteBuffer
 	 * @throws BufferUnderflowException if the buffer`s limit has been reached  
	 * @throws IOException If some other I/O error occurs
	 */
	public ByteBuffer readSingleByteBufferByLength(int length) throws IOException, BufferUnderflowException {
		
		if (length <= 0) {
			if (length == 0) {
				return ByteBuffer.allocate(0);
			} else {
				throw new IllegalArgumentException("length has to be positive");
			}
		}
		
		int savedLimit = data.limit();
		int savedPosition = data.position();
		
		data.limit(data.position() + length);
		ByteBuffer sliced = data.slice();
		
		data.position(savedPosition + length);
		data.limit(savedLimit);
		return sliced;
	}
	
	

	
	
	/**
	 * {@inheritDoc}
	 */		
	public byte[] readBytesByLength(int length) throws IOException, BufferUnderflowException {
		return DataConverter.toBytes(readByteBufferByLength(length));
	}

	
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return DataConverter.toString(readByteBufferByLength(length), encoding);
	}

	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public String readStringByLength(int length) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByLength(length, defaultEncoding);
	}
	

	
	
	/**
	 * {@inheritDoc}
	 */		
	public double readDouble() throws IOException, BufferUnderflowException {
		return data.getDouble();
	}
	
	
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public int readInt() throws IOException, BufferUnderflowException {
		return data.getInt();
	}
	
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public short readShort() throws IOException, BufferUnderflowException {
		return data.getShort();
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public long readLong() throws IOException, BufferUnderflowException {
		return data.getLong();
	}

		

	
	/**
	 * {@inheritDoc}
	 */		
	public ByteBuffer readByteBuffer() throws IOException, BufferUnderflowException {
		ByteBuffer sliced = data.slice();
		data.position(data.limit());
		return sliced;
	}

	
	
	/**
	 * {@inheritDoc}
	 */		
	public byte[] readBytes() throws IOException, BufferUnderflowException {
		return DataConverter.toBytes(readByteBuffer());
	}
	
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public String readString() throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return readString(defaultEncoding);
	}
	

	
	/**
	 * {@inheritDoc}
	 */		
	public String readString(String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return DataConverter.toString(readByteBuffer(), encoding);
	}

	
	/**
	 * {@inheritDoc}
	 */	
	public int read(ByteBuffer buffer) throws IOException {
		int size = buffer.remaining();

		int available = buffer.remaining();
		
		if (available == 0) {
			return -1;
		}
		
		if (available < size) {
			size = available;
		}

		if (size > 0) {
			ByteBuffer[] bufs = readByteBufferByLength(size);
			for (ByteBuffer buf : bufs) {
				while (buf.hasRemaining()) {
					buffer.put(buf);
				}
			}
		}

		return size;
	}

	
	
	/**
	 * {@inheritDoc}
	 */		
	public String readStringByDelimiter(String delimiter) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByDelimiter(delimiter, defaultEncoding);
	}
	
	
	/**
	 * {@inheritDoc}
	 */		
	public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
		return readStringByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}
	

	/**
	 * {@inheritDoc}
	 */		
	public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
		return readStringByDelimiter(delimiter, defaultEncoding, maxLength);
	}
	
	
	/**
	 * {@inheritDoc}
	 */		
	public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
		return DataConverter.toString(readByteBufferByDelimiter(delimiter, encoding, maxLength), encoding);
	}
	
	
	/**
	 * {@inheritDoc}
	 */		
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
		return readByteBufferByDelimiter(delimiter, Integer.MAX_VALUE);
	}
	
	
	/**
	 * {@inheritDoc}
	 */		
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
		return readByteBufferByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}

	
	/**
	 * {@inheritDoc}
	 */		
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return readByteBufferByDelimiter(delimiter, defaultEncoding, maxLength);
	}
	
	
	/**
	 * {@inheritDoc}
	 */		
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return new ByteBuffer[] { readSingleByteBufferByDelimiter(delimiter, encoding, maxLength) };
	}
	
	
	/**
	 * {@inheritDoc}
	 */		
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException {
		return new ByteBuffer[] { readSingleByteBufferByLength(length) };
	}


	
	/**
	 * {@inheritDoc}
	 */			
	public ByteBuffer readSingleByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
		return readSingleByteBufferByDelimiter(delimiter, defaultEncoding);
	}

	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer readSingleByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
		return readSingleByteBufferByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer readSingleByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		byte[] delimiterBytes = delimiter.getBytes(encoding);
		int startPos = findDelimiter(data, delimiterBytes, maxLength);
		if (startPos >= 0) {			
			int savedLimit = data.limit();
			data.limit(startPos);
			ByteBuffer result = data.slice();
			data.limit(savedLimit);
			data.position(startPos + delimiterBytes.length);

			return result; 
		} else {
			throw new BufferUnderflowException();
		}
	}

	

	
	/**
	 * {@inheritDoc}
	 */		
	public byte[] readBytesByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
		return readBytesByDelimiter(delimiter, defaultEncoding);
	}


	
	/**
	 * {@inheritDoc}
	 */		
	public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return readBytesByDelimiter(delimiter, defaultEncoding, maxLength);
	}

	
	/**
	 * {@inheritDoc}
	 */		
	public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
		return readBytesByDelimiter(delimiter, encoding, Integer.MAX_VALUE);
	}
	

	/**
	 * {@inheritDoc}
	 */			
	public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		return DataConverter.toBytes(readByteBufferByDelimiter(delimiter, defaultEncoding, maxLength));
	}
	
	
	
	public long transferTo(WritableByteChannel target, int length) throws IOException, ClosedChannelException, BufferUnderflowException {
		long written = 0;
		
		ByteBuffer[] buffers = readByteBufferByLength(length);
		for (ByteBuffer buffer : buffers) {
			written += target.write(buffer);
		}
		
		return written;
	}
	
	

	
	
	private static int findDelimiter(ByteBuffer buffer, byte[] delimiter, int maxLength) throws MaxReadSizeExceededException {
		int result = -1;
		
		int delimiterPosition = 0;
		
		// iterator over buffer content
		for (int pos = buffer.position(); pos < buffer.limit(); pos++) {
			
			byte b = buffer.get(pos);
			if (b == delimiter[delimiterPosition]) {
				delimiterPosition++;
				if (delimiterPosition == delimiter.length) {
					result = (pos - delimiterPosition + 1);
					break;
				}
			} else {
				delimiterPosition = 0;
			}
			
		}
		
		if (result > maxLength) {
			throw new MaxReadSizeExceededException();
		}
		
		return result;
	} 
	
	
	

	
	/**
	 * {@inheritDoc}
	 */	
	public int write(byte b) throws IOException, BufferOverflowException {
		data.put(b);
		return 1;
	}
	

	
	/**
	 * {@inheritDoc}
	 */		
	public int write(short s) throws IOException, BufferOverflowException {
		data.putShort(s);
		return 2;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public int write(byte... bytes) throws IOException, BufferOverflowException {
		data.put(bytes);
		return bytes.length;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException {
		data.put(bytes, offset, length);
		return length;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public int write(ByteBuffer buffer) throws IOException, BufferOverflowException {
		int length = buffer.remaining();
		data.put(buffer);
		return length;
	}
	
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException {
		int length = 0;
		for (ByteBuffer buffer : buffers) {
			length += write(buffer);
		}
		return length;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		return write(DataConverter.toByteBuffers(srcs, offset, length));
	}


	
	/**
	 * {@inheritDoc}
	 */		
	public long write(List<ByteBuffer> buffers) throws IOException, BufferOverflowException {
		int length = 0;
		for (ByteBuffer buffer : buffers) {
			length += write(buffer);
		}
		return length;
	}
	


	
	/**
	 * {@inheritDoc}
	 */		
	public int write(double d) throws IOException, BufferOverflowException {
		data.putDouble(d);
		return 8;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public int write(int i) throws IOException, BufferOverflowException {
		data.putInt(i);
		return 4;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public int write(long l) throws IOException, BufferOverflowException {
		data.putLong(l);
		return 8;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public int write(String message) throws IOException, BufferOverflowException  {
		return write(message, defaultEncoding);
	}
	
	
	
	/**
	 * {@inheritDoc}
	 */		
	public int write(String message, String encoding) throws IOException, BufferOverflowException  {
		byte[] bytes = message.getBytes(encoding);
		data.put(bytes);
		
		return bytes.length;
	}


	
	/**
	 * {@inheritDoc}
	 */		
	public long transferFrom(FileChannel source) throws IOException, BufferOverflowException {
		return transferFrom((ReadableByteChannel) source);
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public long transferFrom(ReadableByteChannel sourceChannel) throws IOException, BufferOverflowException {
		return transferFrom(sourceChannel, 8196);
	}
	

	
	
	public long transferFrom(ReadableByteChannel sourceChannel, int chunkSize) throws IOException, BufferOverflowException {
		long transfered = 0;


		int read = 0;
		do {
			ByteBuffer transferBuffer = ByteBuffer.allocate(chunkSize);
			read = sourceChannel.read(transferBuffer);
				
			if (read > 0) { 
				if (transferBuffer.remaining() == 0) {
					transferBuffer.flip();
					write(transferBuffer);
						
				} else {
					transferBuffer.flip();
					write(transferBuffer.slice());
				}
					
				transfered += read;
			}
		} while (read > 0);
			
			
		return transfered;
	}
	
	
	/**
	 * get the packet size
	 * 
	 * @return the packet size
	 */
	public int getSize() {
		return data.limit();
	}

	/**
	 * get the remaining, unwritten packet size
	 * 
	 * @return the remaining, unwritten packet size
	 */
	public int getRemaining() {
		return data.remaining();
	}

	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		if (remoteSocketAddress != null) {
			sb.append("remoteAddress=" + remoteSocketAddress.toString() + " ");
		} else {
			sb.append("remoteAddress=null ");
		}
		
		if (data != null) {
		    ByteBuffer copy = data.duplicate();
		    copy.clear();
			sb.append("data=" + DataConverter.toHexString(new ByteBuffer[] {copy}, 500) + " ");
		} else {
			sb.append("data=null ");
		}		
		
		return sb.toString();
	}
}