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
import java.util.concurrent.Executor;

import org.xsocket.MaxReadSizeExceededException;



/**
 * Thread-safe wrapper of a NonBlockingConnection
 *
 * @author grro@xsocket.org
 */
final class SynchronizedNonBlockingConnection extends AbstractSynchronizedConnection implements INonBlockingConnection {

    private final INonBlockingConnection delegate; 
    
    public SynchronizedNonBlockingConnection(INonBlockingConnection delegate) {
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

    public int available() throws IOException {
        synchronized(delegate) {
            return delegate.available();
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

    public IHandler getHandler() {
        synchronized(delegate) {
            return delegate.getHandler();
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

    public int getReadBufferVersion() throws IOException {
        synchronized(delegate) {
            return delegate.getReadBufferVersion();
        }
    }

    public Executor getWorkerpool() {
        synchronized(delegate) {
            return delegate.getWorkerpool();
        }
    }

    public int getWriteTransferRate() throws ClosedChannelException, IOException {
        synchronized(delegate) {
            return delegate.getWriteTransferRate();
        }
    }

    public int indexOf(String str) throws IOException {
        synchronized(delegate) {
            return delegate.indexOf(str);
        }
    }

    public int indexOf(String str, String encoding) throws IOException {
        synchronized(delegate) {
            return delegate.indexOf(str, encoding);
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

    public boolean isSecuredModeActivateable() {
        synchronized(delegate) {
            return delegate.isSecuredModeActivateable();
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

    public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
        synchronized(delegate) {
            return delegate.readByteBufferByDelimiter(delimiter, encoding);
        }
    }

    public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
        synchronized(delegate) {
            return delegate.readByteBufferByDelimiter(delimiter, encoding, maxLength);
        }
    }

    public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
        synchronized(delegate) {
            return delegate.readBytesByDelimiter(delimiter, encoding);
        }
    }

    public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
        synchronized(delegate) {
            return delegate.readBytesByDelimiter(delimiter, encoding, maxLength);
        }
    }

    public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
        synchronized(delegate) {
            return delegate.readStringByDelimiter(delimiter, encoding);
        }
    }

    public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
        synchronized(delegate) {
            return delegate.readStringByDelimiter(delimiter, encoding, maxLength);
        }
    }

    public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
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

    public void setHandler(IHandler handler) throws IOException {
        synchronized(delegate) {
            delegate.setHandler(handler);
        }        
    }

    public void setMaxReadBufferThreshold(int size) {
        synchronized(delegate) {
            delegate.setMaxReadBufferThreshold(size);
        }                
    }

    public void setWorkerpool(Executor workerpool) {
        synchronized(delegate) {
            delegate.setWorkerpool(workerpool);
        }                
    }

    public void setWriteTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException {
        synchronized(delegate) {
            delegate.setWriteTransferRate(bytesPerSecond);
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

    public void write(ByteBuffer[] srcs, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(srcs, offset, length, writeCompletionHandler);
        }        
    }

    public void write(List<ByteBuffer> buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        synchronized(delegate) {
            delegate.write(buffers, writeCompletionHandler);
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
