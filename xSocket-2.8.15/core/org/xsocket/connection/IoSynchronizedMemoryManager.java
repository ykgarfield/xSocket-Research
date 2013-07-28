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
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;

/**
 * 用于多线程中.		<br></br>
 * 
 * a Memory Manager implementation  
 *  
 * @author grro@xsocket.org
 */
final class IoSynchronizedMemoryManager extends AbstractMemoryManager { 
	
	private static final Logger LOG = Logger.getLogger(IoSynchronizedMemoryManager.class.getName());
	
	private final List<SoftReference<ByteBuffer>> memoryBuffer = new ArrayList<SoftReference<ByteBuffer>>();

		
	/**
	 * constructor
	 * 
	 * @param allocationSize               the buffer to allocate
	 * 									      分配的buffer大小
	 * 
	 * @param preallocate                  true, if buffer should be preallocated
	 * 									      是否预分配
	 * 
	 * @param minPreallocatedBufferSize    the minimal buffer size
	 * 									      预分配的buffer的最小值
	 * 
	 * @param useDirectMemory  true, if direct memory should be used
	 * 									      是否使用直接内存分配
	 */
	private IoSynchronizedMemoryManager(int preallocationSize, boolean preallocate, int minPreallocatedBufferSize, boolean useDirectMemory) {
		super(preallocationSize, preallocate, minPreallocatedBufferSize, useDirectMemory);
	}
	

	public static IoSynchronizedMemoryManager createPreallocatedMemoryManager(int preallocationSize, int minBufferSze, boolean useDirectMemory) {
		return new IoSynchronizedMemoryManager(preallocationSize, true, minBufferSze, useDirectMemory);
	}
	
	
	public static IoSynchronizedMemoryManager createNonPreallocatedMemoryManager(boolean useDirectMemory) {
		return new IoSynchronizedMemoryManager(0, false, 1, useDirectMemory);
	}
	
	

	/**
	 * return the free memory size
	 * 
	 * @return the free memory size
	 */
	public int getCurrentSizePreallocatedBuffer() {
		int size = 0;
		
		synchronized (memoryBuffer) {
			for (SoftReference<ByteBuffer> bufferRef: memoryBuffer) {
				ByteBuffer buffer = bufferRef.get();
				if (buffer != null) {
					size += buffer.remaining();
				}
			}
		}
		
		return size;
	}
			
	

	
	/**
	 * recycle free memory
	 * 
	 * @param buffer the buffer to recycle
	 */
	public void recycleMemory(ByteBuffer buffer) {
		if (isPreallocationMode()) {
			int remaining = buffer.remaining(); 
			if (remaining >= getPreallocatedMinBufferSize()) {
				synchronized (memoryBuffer) {

					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("recycling " + DataConverter.toFormatedBytesSize(buffer.remaining()));
					}

					memoryBuffer.add(new SoftReference<ByteBuffer>(buffer));
				}
			}
		}
	}
	
	
	public void preallocate() {
		
	}

			
	/**
	 * acquire free memory
	 */
	public ByteBuffer acquireMemoryStandardSizeOrPreallocated(int standardSize) throws IOException {		
		ByteBuffer buffer = null;
		

		if (isPreallocationMode()) {
			
			synchronized (memoryBuffer) {
				if (!memoryBuffer.isEmpty()) {
					SoftReference<ByteBuffer> freeBuffer = memoryBuffer.remove(0);
					buffer = freeBuffer.get();
		
					// size sufficient?
					if ((buffer != null) && (buffer.limit() < getPreallocatedMinBufferSize())) {
						buffer = null;			
					}
				} 
			}
							
						
			if (buffer == null) {
				buffer = newBuffer(standardSize);
			}
						
			return buffer;
			
		} else {
			return newBuffer(standardSize);
		}
	}	
}