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
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;

import org.xsocket.MaxReadSizeExceededException;




/**
 * Thread-safe wrapper of a BlockingConnection
 *
 * @author grro@xsocket.org
 */
final class SynchronizedBlockingConnection extends AbstractSynchronizedConnection implements IBlockingConnection {

    private final IBlockingConnection delegate; 
    
    public SynchronizedBlockingConnection(IBlockingConnection delegate) {
        super(delegate);
        
        this.delegate = delegate;
    }

    public void activateSecuredMode() throws IOException {
        synchronized(delegate) {
            delegate.activateSecuredMode();
        }                
    }
    
    public void deactivateSecuredMode() throws IOException {
    	synchronized(delegate) {
            delegate.deactivateSecuredMode();
        }    	
    }

    public void flush() throws ClosedChannelException, IOException, SocketTimeoutException {
        synchronized(delegate) {
            delegate.flush();
        }                
    }

    public String getEncoding() {
        synchronized(delegate) {
            return delegate.getEncoding();
        }        
    }

    public FlushMode getFlushmode() {
        synchronized(delegate) {
            return delegate.getFlushmode();
        }        
    }

    public int getMaxReadBufferThreshold() {
        synchronized(delegate) {
            return delegate.getMaxReadBufferThreshold();
        }        
    }

    public int getPendingWriteDataSize() {
        synchronized(delegate) {
            return delegate.getPendingWriteDataSize();
        }        
    }

    public int getReadTimeoutMillis() throws IOException {
        synchronized(delegate) {
            return delegate.getReadTimeoutMillis();
        }        
    }


    public boolean isAutoflush() {
        synchronized(delegate) {
            return delegate.isAutoflush();
        }        
    }

    public boolean isReceivingSuspended() {
        synchronized(delegate) {
            return delegate.isReceivingSuspended();
        }        
    }

    public boolean isSecure() {
        synchronized(delegate) {
            return delegate.isSecure();
        }        
    }

    public void markReadPosition() {
        synchronized(delegate) {
            delegate.markReadPosition();
        }                
    }

    public void markWritePosition() {
        synchronized(delegate) {
            delegate.markWritePosition();
        }                
    }

    public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, SocketTimeoutException {
        synchronized(delegate) {
            return delegate.readByteBufferByDelimiter(delimiter, encoding);
        }        
    }

    public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, SocketTimeoutException {
        synchronized(delegate) {
            return delegate.readByteBufferByDelimiter(delimiter, encoding, maxLength);
        }        
    }

    public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
        synchronized(delegate) {
            return delegate.readBytesByDelimiter(delimiter, encoding);
        }        
    }

    public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException, SocketTimeoutException {
        synchronized(delegate) {
            return delegate.readBytesByDelimiter(delimiter, encoding, maxLength);
        }        
    }

    public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException {
        synchronized(delegate) {
            return delegate.readStringByDelimiter(delimiter, encoding);
        }        
    }

    public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException, SocketTimeoutException {
        synchronized(delegate) {
            return delegate.readStringByDelimiter(delimiter, encoding, maxLength);
        }        
    }

    public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, SocketTimeoutException {
        synchronized(delegate) {
            return delegate.readStringByLength(length, encoding);
        }        
    }

    public void removeReadMark() {
        synchronized(delegate) {
            delegate.removeReadMark();
        }                
    }

    public void removeWriteMark() {
        synchronized(delegate) {
            delegate.removeWriteMark();
        }                
    }

    public boolean resetToReadMark() {
        synchronized(delegate) {
            return delegate.resetToReadMark();
        }        
    }

    public boolean resetToWriteMark() {
        synchronized(delegate) {
            return delegate.resetToWriteMark();
        }        
    }

    public void resumeReceiving() throws IOException {
        synchronized(delegate) {
            delegate.resumeReceiving();
        }        
    }

    public void setAutoflush(boolean autoflush) {
        synchronized(delegate) {
            delegate.setAutoflush(autoflush);
        }                
    }

    public void setEncoding(String encoding) {
        synchronized(delegate) {
            delegate.setEncoding(encoding);
        }                
    }

    public void setFlushmode(FlushMode flushMode) {
        synchronized(delegate) {
            delegate.setFlushmode(flushMode);
        }                
    }

    public void setMaxReadBufferThreshold(int size) {
        synchronized(delegate) {
            delegate.setMaxReadBufferThreshold(size);
        }        
    }

    public void setReadTimeoutMillis(int timeout) throws IOException {
        synchronized(delegate) {
            delegate.setReadTimeoutMillis(timeout);
        }                
    }

    public void suspendReceiving() throws IOException {
        synchronized(delegate) {
            delegate.suspendReceiving();
        }                
    }

    public long transferFrom(FileChannel source) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.transferFrom(source);
        }        

    }

    public void unread(ByteBuffer[] buffers) throws IOException {
        synchronized(delegate) {
            delegate.unread(buffers);
        }                
    }

    public void unread(ByteBuffer buffer) throws IOException {
        synchronized(delegate) {
            delegate.unread(buffer);
        }                
    }

    public void unread(byte[] bytes) throws IOException {
        synchronized(delegate) {
            delegate.unread(bytes);
        }                
    }

    public void unread(String text) throws IOException {
        synchronized(delegate) {
            delegate.unread(text);
        }                
    }

    public int write(String message, String encoding) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(message, encoding);
        }        
    }

    public void write(ByteBuffer[] buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(buffers, writeCompletionHandler);
        }                
    }

    public void write(ByteBuffer buffer, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(buffer, writeCompletionHandler);
        }        
        
    }

    public void write(List<ByteBuffer> buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(buffers, writeCompletionHandler);
        }        
    }

    public void write(ByteBuffer[] srcs, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(srcs, offset, length);
        }                
    }

    public void write(byte[] bytes, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(bytes, writeCompletionHandler);
        }                
    }

    public void write(byte[] bytes, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(bytes, offset, length, writeCompletionHandler);
        }                
    }

    public void write(String message, String encoding, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(message, encoding, writeCompletionHandler);
        }                
    }

    public int read(ByteBuffer buffer) throws IOException {
        synchronized(delegate) {
            return delegate.read(buffer);
        }        
    }

    public byte readByte() throws IOException {
        synchronized(delegate) {
            return delegate.readByte();
        }        
    }

    public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException {
        synchronized(delegate) {
            return delegate.readByteBufferByDelimiter(delimiter);
        }        
    }

    public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException {
        synchronized(delegate) {
            return delegate.readByteBufferByDelimiter(delimiter, maxLength);
        }        
    }

    public ByteBuffer[] readByteBufferByLength(int length) throws IOException {
        synchronized(delegate) {
            return delegate.readByteBufferByLength(length);
        }        
    }

    public byte[] readBytesByDelimiter(String delimiter) throws IOException {
        synchronized(delegate) {
            return delegate.readBytesByDelimiter(delimiter);
        }        
    }

    public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, MaxReadSizeExceededException {
        synchronized(delegate) {
            return delegate.readBytesByDelimiter(delimiter, maxLength);
        }        
    }

    public byte[] readBytesByLength(int length) throws IOException {
        synchronized(delegate) {
            return delegate.readBytesByLength(length);
        }        

    }

    public double readDouble() throws IOException {
        synchronized(delegate) {
            return delegate.readDouble();
        }        
    }

    public int readInt() throws IOException {
        synchronized(delegate) {
            return delegate.readInt();
        }        
    }

    public long readLong() throws IOException {
        synchronized(delegate) {
            return delegate.readLong();
        }        
    }

    public short readShort() throws IOException {
        synchronized(delegate) {
            return delegate.readShort();
        }        
    }

    public String readStringByDelimiter(String delimiter) throws IOException, UnsupportedEncodingException {
        synchronized(delegate) {
            return delegate.readStringByDelimiter(delimiter);
        }        
    }

    public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, UnsupportedEncodingException, MaxReadSizeExceededException {
        synchronized(delegate) {
            return delegate.readStringByDelimiter(delimiter, maxLength);
        }        
    }

    public String readStringByLength(int length) throws IOException, BufferUnderflowException {
        synchronized(delegate) {
            return delegate.readStringByLength(length);
        }        
    }

    public long transferTo(WritableByteChannel target, int length) throws IOException, ClosedChannelException {
        synchronized(delegate) {
            return delegate.transferTo(target, length);
        }        
    }

    public long transferFrom(ReadableByteChannel source) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.transferFrom(source);
        }        
    }

    public long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.transferFrom(source, chunkSize);
        }        
    }

    public int write(byte b) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(b);
        }        
    }

    public int write(byte... bytes) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(bytes);
        }        
    }

    public int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(bytes, offset, length);
        }        
    }

    public int write(ByteBuffer buffer) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(buffer);
        }        
    }

    public long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(buffers);
        }        
    }

    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        synchronized(delegate) {
            return delegate.write(srcs, offset, length);
        }        
    }

    public long write(List<ByteBuffer> buffers) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(buffers);
        }        
    }

    public int write(int i) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(i);
        }        
    }

    public int write(short s) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(s);
        }        
    }

    public int write(long l) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(l);
        }        
    }

    public int write(double d) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(d);
        }        
    }

    public int write(String message) throws IOException, BufferOverflowException {
        synchronized(delegate) {
            return delegate.write(message);
        }        
    }
}
