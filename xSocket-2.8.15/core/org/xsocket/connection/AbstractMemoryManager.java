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
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;



/**
 * implementation base for a Memory Manager implementation  
 *  
 * @author grro@xsocket.org
 */
abstract class AbstractMemoryManager { 
	
	private static final Logger LOG = Logger.getLogger(AbstractMemoryManager.class.getName());
	

	// direct or non-direct buffer
	// 是否直接内存分配
	private boolean useDirectMemory = false;

	
	// preallocation support
	private int preallocationSize = 65536;
	private int minPreallocatedBufferSize = 1;
	private boolean preallocate = false;
	
	
	
	/**
	 * constructor
	 * 
	 * @param allocationSize               the buffer to allocate
	 * @param preallocate                  true, if buffer should be preallocated
	 * @param minPreallocatedBufferSize    the minimal buffer size
	 * @param useDirectMemory  true, if direct memory should be used
	 */
	protected AbstractMemoryManager(int preallocationSize, boolean preallocate, int minPreallocatedBufferSize, boolean useDirectMemory) {
		this.preallocationSize = preallocationSize;
		this.preallocate = preallocate;
		this.minPreallocatedBufferSize = minPreallocatedBufferSize;
		this.useDirectMemory = useDirectMemory;
	}
	
	

	/**
	 * 使用空闲的内存获取ByteBuffer	</br></br>
	 * 
	 * acquire ByteBuffer with free memory
	 *
	 * @param standardsize the standard size
	 * @return the ByteBuffer with free memory 
	 * 
	 * @throws IOException if an exception occurs
	 */
	public abstract ByteBuffer acquireMemoryStandardSizeOrPreallocated(int standardsize) throws IOException;
	

	
	/**
	 * recycle a ByteBuffer.  
	 * 
	 * @param buffer  the ByteBuffer to recycle 
	 */
	public abstract void recycleMemory(ByteBuffer buffer);

	


	
	/**
	 * preallocate, if preallocated size is smaller the given minSize
	 * 
	 * @throws IOException if an exception occurs 
	 */
	public abstract void preallocate() throws IOException;

	

	/**
	 * get the current free preallocated buffer size 
	 * @return the current free preallocated buffer size
	 */
	public abstract int getCurrentSizePreallocatedBuffer();

	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isPreallocationMode() {
		return preallocate;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setPreallocationMode(boolean mode) {
		this.preallocate = mode;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setPreallocatedMinBufferSize(Integer minSize) {
		this.minPreallocatedBufferSize = minSize;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final Integer getPreallocatedMinBufferSize() {
		return minPreallocatedBufferSize;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final Integer getPreallocationBufferSize() {
		return preallocationSize;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setPreallocationBufferSize(Integer minSize) {
		this.preallocationSize = minSize;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final boolean isDirect() {
		return useDirectMemory;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final void setDirect(boolean isDirect) {
		this.useDirectMemory = isDirect;
	}
	
		

	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer extractAndRecycleMemory(ByteBuffer buffer, int read) {
		
		ByteBuffer readData = null;
		
		if (read > 0) {
			buffer.limit(buffer.position());
			buffer.position(buffer.position() - read);
			
			// slice the read data
			// 子缓冲区,读取的数据
			readData = buffer.slice();
			
			// preallocate mode? and does buffer contain remaining free data? -> recycle these
			if (preallocate && (buffer.limit() < buffer.capacity())) {
				// 重置属性
				buffer.position(buffer.limit());
				buffer.limit(buffer.capacity());
			
				recycleMemory(buffer);
			}
			
		} else {
			readData = ByteBuffer.allocate(0);
			
			if (preallocate) {
				recycleMemory(buffer);
			}
		}
		
		return readData;
	}
	
	


	/**
	 * {@inheritDoc}
	 */
	public final ByteBuffer acquireMemoryMinSize(int minSize) throws IOException {
		
		// preallocation mode?
		if (preallocate) {
			
			// ... yes, but is required size larger than preallocation size?
			if (preallocationSize < minSize) {
				// ... yes. create a new buffer
				return newBuffer(minSize);
				
			// ... no, call method to get preallocated buffer first 
			} else {
				ByteBuffer buffer = acquireMemoryStandardSizeOrPreallocated(minSize);
				
				// buffer to small?
				if (buffer.remaining() < minSize) {
					// yes, create a new one
					return newBuffer(minSize);
				}
				return buffer;
			}
			
		// .. no 	
		} else {
			return newBuffer(minSize);
		}
	}
	
	
	/**
	 * creates a new buffer
	 * @param size  the size of the new buffer
	 * @return the new buffer
	 */
	protected final ByteBuffer newBuffer(int size) throws IOException {
		return newBuffer(size, useDirectMemory);
	}
	

	
    private ByteBuffer newBuffer(int size, boolean isUseDirect) throws IOException {
        try {
            if (isUseDirect) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("allocating " + DataConverter.toFormatedBytesSize(size) + " direct memory");
                }
    
                return ByteBuffer.allocateDirect(size);
    
            } else {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("allocating " + DataConverter.toFormatedBytesSize(size) + " heap memory");
                }
    
                return ByteBuffer.allocate(size);
            }
            
        } catch (OutOfMemoryError oome) {
            
            if (isUseDirect) {
                String msg = "out of memory exception occured by trying to allocated direct memory " + DataConverter.toString(oome);
                LOG.warning(msg);
                throw new IOException(msg);
                
            } else {
                String msg = "out of memory exception occured by trying to allocated non-direct memory " + DataConverter.toString(oome);
                LOG.warning(msg);
                throw new IOException(msg);
            }
        }
    }
    
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("useDirect=" + useDirectMemory + " preallocationOn=" 
				  + preallocate + " preallcoationSize=" + DataConverter.toFormatedBytesSize(preallocationSize)
				  + " preallocatedMinSize=" + DataConverter.toFormatedBytesSize(minPreallocatedBufferSize));		
		return sb.toString();
	}
}