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


import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.xsocket.DataConverter;
import org.xsocket.connection.AbstractNonBlockingStream.ISink;



 
/**
 * the WriteQueue
 * 
 * @author grro
 */
final class WriteQueue implements Cloneable {

	// queue
	private final Queue queue = new Queue(); 
	
	
	// mark support
	private RewriteableBuffer writeMarkBuffer = null;
	private boolean isWriteMarked = false;
	
	
	public void reset() {
		queue.reset();
		
		writeMarkBuffer = null;
		isWriteMarked = false;
	}
	
	
	
	/**
	 * returns true, if empty
	 *
	 * @return true, if empty
	 */
	public boolean isEmpty() {
		return queue.isEmpty() && (writeMarkBuffer == null);
	}
	

	/**
	 * return the current size
	 *
	 * @return  the current size
	 */
	public int getSize() {

		int size = queue.getSize();
				
		if (writeMarkBuffer != null) {
			size += writeMarkBuffer.size();
		}
					
		return size;
	}
	
	

	/**
	 * drain the queue
	 *
	 * @return the queue content
	 */
	public ByteBuffer[] drain() {
		return queue.drain();
	}

		
	
	/**
	 * append a byte buffer to this queue.
	 *
	 * @param data the ByteBuffer to append
	 */
	public void append(ByteBuffer data) {
		
		if (data == null) {
			return;
		}
				
		if (isWriteMarked) {
			writeMarkBuffer.append(data);

		} else {
			queue.append(data);
		}
	}
	
	
	
	
	/**
	 * append a list of byte buffer to this queue. By adding a list,
	 * the list becomes part of to the buffer, and should not be modified outside the buffer
	 * to avoid side effects
	 *
	 * @param bufs  the list of ByteBuffer
	 */
	public void append(ByteBuffer[] bufs) {
		
		if (bufs == null) {
			return;
		}
		
		if (bufs.length < 1) {
			return;
		}
		

		if (isWriteMarked) {
			for (ByteBuffer buffer : bufs) {
				writeMarkBuffer.append(buffer);	
			}
			
		} else {
			queue.append(bufs);
		}
	}
	
	


	
	/**
	 * mark the current write position  
	 */
	public void markWritePosition() {
		removeWriteMark();

		isWriteMarked = true;
		writeMarkBuffer = new RewriteableBuffer();
	}



	/**
	 * remove write mark 
	 */
	public void removeWriteMark() {
		if (isWriteMarked) {
			isWriteMarked = false;
			
			append(writeMarkBuffer.drain());
			writeMarkBuffer = null;
		}
	}
	
	
	/**
	 * reset the write position the the saved mark
	 * 
	 * @return true, if the write position has been marked  
	 */
	public boolean resetToWriteMark() {
		if (isWriteMarked) {
			writeMarkBuffer.resetWritePosition();
			return true;

		} else {
			return false;
		}
	}

	
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		WriteQueue copy = new WriteQueue();
		copy.queue.append(this.queue.copy());
	
		if (this.writeMarkBuffer != null) {
			copy.writeMarkBuffer = (RewriteableBuffer) this.writeMarkBuffer.clone();
		}
		
		return copy;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public String toString(String encoding) {
		return queue.toString(encoding);
	}
	
	
	
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return queue.toString();
	}
	
	
	

	
	private static final class RewriteableBuffer implements Cloneable {
		private ArrayList<ByteBuffer> bufs = new ArrayList<ByteBuffer>();
		private int writePosition = 0;
		
		
		public void append(ByteBuffer buffer) {
			
			if (buffer.remaining() < 1) {
				return;
			}
			
			if (writePosition == bufs.size()) {
				bufs.add(buffer);
				writePosition++;
				
			} else {
				ByteBuffer currentBuffer = bufs.remove(writePosition);
				
				if (currentBuffer.remaining() == buffer.remaining()) {
					bufs.add(writePosition, buffer);
					writePosition++;
					
				} else if (currentBuffer.remaining() > buffer.remaining()) {
					currentBuffer.position(currentBuffer.position() + buffer.remaining());
					bufs.add(writePosition, currentBuffer);
					bufs.add(writePosition, buffer);
					writePosition++;
					
				} else { // currentBuffer.remaining() < buffer.remaining()
					bufs.add(writePosition, buffer);
					writePosition++;
					
					int bytesToRemove = buffer.remaining() - currentBuffer.remaining();
					while (bytesToRemove > 0) {
						// does tailing buffers exits?
						if (writePosition < bufs.size()) {
							
							ByteBuffer buf = bufs.remove(writePosition);
							if (buf.remaining() > bytesToRemove) {
								buf.position(buf.position() + bytesToRemove);
								bufs.add(writePosition, buf);
							} else {
								bytesToRemove -= buf.remaining();
							}
							
						// ...no
						} else {
							bytesToRemove = 0;
						}
					}
				}
				
			}	
		}
	
		public void resetWritePosition() {
			writePosition = 0;
		}
		
	
		public ByteBuffer[] drain() {
			ByteBuffer[] result = bufs.toArray(new ByteBuffer[bufs.size()]);
			bufs.clear();
			writePosition = 0;
			
			return result;
		}
		
		
		public int size() {
			int size = 0;
			for (ByteBuffer buffer : bufs) {
				size += buffer.remaining();
			}
			
			return size;
		}
		
		@Override
		protected Object clone() throws CloneNotSupportedException {
			RewriteableBuffer copy = (RewriteableBuffer) super.clone();
			
			copy.bufs = new ArrayList<ByteBuffer>();
			for (ByteBuffer buffer : this.bufs) {
				copy.bufs.add(buffer.duplicate());
			}

			return copy;
		}
	}
	
	
	
	private static final class Queue implements ISink {

		private ByteBuffer[] buffers;

		
		/**
		 * clean the queue
		 */
		public synchronized void reset() {
			buffers = null;
		}
	
		

		/**
		 * returns true, if empty
		 *
		 * @return true, if empty
		 */
		public synchronized boolean isEmpty() {
			return empty();
		}
		

		private boolean empty() {
			return (buffers == null);
		}
			

		/**
		 * return the current size
		 *
		 * @return  the current size
		 */
		public synchronized int getSize() {
			if (empty()) {
				return 0;
				
			} else {
				int size = 0;
				if (buffers != null) {
					for (int i = 0; i < buffers.length; i++) {
						if (buffers[i] != null) {
							size += buffers[i].remaining();
						}
					}
				}
				
				return size;
			}
		}
		
		
		public synchronized void append(ByteBuffer data) {			
			if (buffers == null) {
				buffers = new ByteBuffer[1];
				buffers[0] = data;
									
			} else {
				ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + 1];
				System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
				newBuffers[buffers.length] = data;
				buffers = newBuffers;
			}
		}
		
		
		/**
		 * append a list of byte buffer to this queue. By adding a list,
		 * the list becomes part of to the buffer, and should not be modified outside the buffer
		 * to avoid side effects
		 *
		 * @param bufs  the list of ByteBuffer
		 */
		public synchronized void append(ByteBuffer[] bufs) {
			if (buffers == null) {
				buffers = bufs;
										
			}  else {
				ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + bufs.length];
				System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
				System.arraycopy(bufs, 0, newBuffers, buffers.length, bufs.length);
				buffers = newBuffers;
			}
		}
		


		/**
		 * drain the queue
		 *
		 * @return the queue content
		 */
		public synchronized ByteBuffer[] drain() {
			ByteBuffer[] result = buffers;
			buffers = null;
						
			return result;
		}
		
		
		
		public synchronized ByteBuffer[] copy()  {
			return ConnectionUtils.copy(buffers);
		}
		
		
		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return toString("US-ASCII");
		}
		
		
		/**
		 * {@inheritDoc}
		 */
		public synchronized String toString(String encoding) {
			StringBuilder sb = new StringBuilder();
			if (buffers != null) {
				ByteBuffer[] copy = new ByteBuffer[buffers.length];
				try {
					for (int i = 0; i < copy.length; i++) {
						if (buffers[i] != null) {
							copy[i] = buffers[i].duplicate();
						}
					}
					sb.append(DataConverter.toString(copy, encoding, Integer.MAX_VALUE));
					
				} catch (UnsupportedEncodingException use) { 
					sb.append(DataConverter.toHexString(copy, Integer.MAX_VALUE));
				}
			}

			return sb.toString();
		}
	}
}