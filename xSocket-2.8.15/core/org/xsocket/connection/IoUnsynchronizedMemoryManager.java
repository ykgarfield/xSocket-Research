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
 * Single thread memory manager
 * 
 * 
 * @author grro@xsocket.org
 */
final class IoUnsynchronizedMemoryManager extends AbstractMemoryManager {
		
	private static final Logger LOG = Logger.getLogger(IoUnsynchronizedMemoryManager.class.getName());
	
	private ByteBuffer freeBuffer;

	
	
	/**
	 * constructor
	 * 
	 * @param allocationSize               the buffer to allocate
	 * @param preallocate                  true, if buffer should be preallocated
	 * @param minPreallocatedBufferSize    the minimal buffer size
	 * @param useDirectMemory  true, if direct memory should be used
	 */
	private IoUnsynchronizedMemoryManager(int preallocationSize, boolean preallocate, int minPreallocatedBufferSize, boolean useDirectMemory) {
		super(preallocationSize, preallocate, minPreallocatedBufferSize, useDirectMemory);
	}
	
	
	public static IoUnsynchronizedMemoryManager createPreallocatedMemoryManager(int preallocationSize, int minBufferSze, boolean useDirectMemory) {
		return new IoUnsynchronizedMemoryManager(preallocationSize, true, minBufferSze, useDirectMemory);
	}
	
	
	public static IoUnsynchronizedMemoryManager createNonPreallocatedMemoryManager(boolean useDirectMemory) {
		return new IoUnsynchronizedMemoryManager(0, false, 1, useDirectMemory);
	}
	
	


	/**
	 * {@inheritDoc}
	 */
	public int getCurrentSizePreallocatedBuffer() {
		if (freeBuffer != null) {
			return freeBuffer.remaining();
		} else {
			return 0;
		}
	}
	
		

	/**
	 * {@inheritDoc}
	 */
	public void recycleMemory(ByteBuffer buffer) {
		
		// preallocate mode?
		if (isPreallocationMode() && (buffer.remaining() >= getPreallocatedMinBufferSize())) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("recycling " + DataConverter.toFormatedBytesSize(buffer.remaining()));
			}
			freeBuffer = buffer;
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer acquireMemoryStandardSizeOrPreallocated(int standardSize) throws IOException {
		if (isPreallocationMode()) {
			preallocate();
		} else {
			freeBuffer = newBuffer(standardSize);
		}

		ByteBuffer buffer = freeBuffer;
		freeBuffer = null;

		return buffer;
	}
	

	

	/**
	 * {@inheritDoc}
	 */
	public void preallocate() throws IOException {
		if (isPreallocationMode()) {
			
			// sufficient size?
			if ((freeBuffer != null) && (freeBuffer.remaining() >= getPreallocatedMinBufferSize())) {
				return;
			}
				
			// no, allocate new 
			freeBuffer = newBuffer(getPreallocationBufferSize());
		}
	}
	

}
