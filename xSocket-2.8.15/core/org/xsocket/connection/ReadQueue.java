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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.AbstractNonBlockingStream.ISource;

/**
 *
 * 
 * @author grro
 */
final class ReadQueue {
	
	private static final Logger LOG = Logger.getLogger(ReadQueue.class.getName());

	
	// queue
	private final Queue queue = new Queue(); 
	

	// mark support
	private ByteBuffer[] readMarkBuffer;
	private boolean isReadMarked = false;
	private int readMarkVersion = -1;

	
	public void reset() {
		readMarkBuffer = null;
		isReadMarked = false;
		queue.reset();
	}

	
	/**
	 * returns true, if empty
	 *
	 * @return true, if empty
	 */
	public boolean isEmpty() {
		return (queue.getSize() == 0);
	}

	
	/**
	 * return a int, which represent the modification version.
	 * this value will increase with each modification
	 *
	 * @return the modify version
	 */
	public int geVersion() {
		return queue.getVersion(false);
	}

	

	/**
	 * append a byte buffer array to this queue. By adding a list,
	 * the list becomes part of to the buffer, and should not be modified outside the buffer
	 * to avoid side effects
	 *
	 * @param bufs  the ByteBuffers
	 * @param size  the size
	 */
	public void append(ByteBuffer[] bufs, int size) {
		
		assert (size == computeSize(bufs));
		
		//is data available?
		if (size > 0) {
			queue.append(bufs, size);
		}
	}
	
	
	// 计算bufs中的可用数据
	private static int computeSize(ByteBuffer[] bufs) {
		int size = 0;
		for (ByteBuffer buf : bufs) {
			size += buf.remaining();
		}
		
		return size;
	}

	
	
	/**
	 * return the index of the delimiter or -1 if the delimiter has not been found 
	 * 
	 * @param delimiter         the delimiter
	 * @param maxReadSize       the max read size
	 * @return the position of the first delimiter byte
	 * @throws IOException in an exception occurs
	 * @throws MaxReadSizeExceededException if the max read size has been reached 
	 */
	public int retrieveIndexOf(byte[] delimiter, int maxReadSize) throws IOException, MaxReadSizeExceededException {
		return queue.retrieveIndexOf(delimiter, maxReadSize);
	}

	

	/**
	 * return the current size
	 *
	 * @return  the current size
	 */
	public int getSize() {
		return queue.getSize();
	}

	
	
	public ByteBuffer[] readAvailable() {
		ByteBuffer[] buffers = queue.drain();
		onExtracted(buffers);

		return buffers;
	}


	public ByteBuffer[] copyAvailable() {
		ByteBuffer[] buffers = queue.copy();
		onExtracted(buffers);

		return buffers;
	}

	
	/**
	 * 根据指定分隔符和最大长度读取读缓冲
	 */
	public ByteBuffer[] readByteBufferByDelimiter(byte[] delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		ByteBuffer[] buffers = queue.readByteBufferByDelimiter(delimiter, maxLength);
		onExtracted(buffers, delimiter);
		
		return buffers;
	}


	public void unread(ByteBuffer[] buffers) throws IOException  {
	    if (isReadMarked) {
	        throw new IOException("unread() is not supported in marked read mode");
	    }
            
        queue.addFirst(buffers);
	}
	
	
	public ByteBuffer[] readByteBufferByLength(int length) throws BufferUnderflowException {
		ByteBuffer[] buffers = queue.readByteBufferByLength(length);
		onExtracted(buffers);
		
		return buffers;
	}
	

	public ByteBuffer readSingleByteBuffer(int length) throws BufferUnderflowException {
		if (getSize() < length) {
			throw new BufferUnderflowException();
		}
		

		ByteBuffer buffer = queue.readSingleByteBuffer(length);
		onExtracted(buffer);
		
		return buffer;
	}

		

	public void markReadPosition() {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("mark read position");
		}
		removeReadMark();

		isReadMarked = true;
		readMarkVersion = queue.getVersion(true);
	}

	

	
	public boolean resetToReadMark() {

	    if (isReadMarked) {
		
	        if (readMarkBuffer != null) {
				queue.addFirst(readMarkBuffer);
				removeReadMark();
				queue.setVersion(readMarkVersion);

			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("reset read mark. nothing to return to read queue");
				}
			}
			return true;

			
		} else {
			return false;
		}
	}

	

	public void removeReadMark() {
		isReadMarked = false;
		readMarkBuffer = null;
	}

	
	
	private void onExtracted(ByteBuffer[] buffers) {
		if (isReadMarked) {
			for (ByteBuffer buffer : buffers) {
				onExtracted(buffer);
			}
		}
	}


	private void onExtracted(ByteBuffer[] buffers, byte[] delimiter) {
		if (isReadMarked) {
			if (buffers != null) {
				for (ByteBuffer buffer : buffers) {
					onExtracted(buffer);
				}
			}
			
			onExtracted(ByteBuffer.wrap(delimiter));
		}
	}

	
	private void onExtracted(ByteBuffer buffer) {
		if (isReadMarked) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("add data (" + DataConverter.toFormatedBytesSize(buffer.remaining()) + ") to read mark buffer ");
			}
			
			if (readMarkBuffer == null) {
				readMarkBuffer = new ByteBuffer[1];
				readMarkBuffer[0] = buffer.duplicate();
				
			} else {
				ByteBuffer[] newReadMarkBuffer = new ByteBuffer[readMarkBuffer.length + 1];
				System.arraycopy(readMarkBuffer, 0, newReadMarkBuffer, 0, readMarkBuffer.length);
				newReadMarkBuffer[readMarkBuffer.length] = buffer.duplicate();
				readMarkBuffer = newReadMarkBuffer;
			}			
		}
	}
	
	
	@Override
	public String toString() {
		return queue.toString();
	}

	
	public String toString(String encoding) {
		return queue.toString(encoding);
	}
	


	
	private static final class Queue implements ISource {
		
		private static final int THRESHOLD_COMPACT_BUFFER_COUNT_TOTAL = 20;
		private static final int THRESHOLD_COMPACT_BUFFER_COUNT_EMPTY = 10;	// 空的ByteBuffer合并的阀值
		
		
		// queue
		private ByteBuffer[] buffers = null;	// 所有读取的数据
		
		private Integer currentSize = null; 	// 当前读取的数据的大小
		private int version = 0;				// 操作的版本
		private boolean isAppended = false;		// 是否追加

		
		// cache support
		// 缓存支持
		private Index cachedIndex = null;

		

		/**
		 * clean the queue
		 */
		public synchronized void reset() {
			buffers = null;
			currentSize = null;
			cachedIndex = null;		
			isAppended = false;
		}
	


		/**
		 * return a int, which represent the  version.
		 * this value will increase with modification
		 *
		 * @return the modify version
		 */
		public synchronized int getVersion(boolean mark) {
		    if (mark) {
		        isAppended = false;
		    }
			return version;
		}
		
		
		public synchronized void setVersion(int version) {
			if (!isAppended) {
				this.version = version;
			}
		}
		
	
		/**
		 * return the current size
		 *
		 * @return  the current size
		 */
		public synchronized int getSize() {
			return size();
		}

		
		/**
		 * 得到缓冲区的大小
		 */
		private int size() {
			// 性能优化：如果大小已经计算过
			// performance optimization if size has been already calculated
			if (currentSize != null) {
				return currentSize;
			}
				
				
			if (buffers == null) {
				return 0;
					
			} else {
				int size = 0;
				for (int i = 0; i < buffers.length; i++) {
					if (buffers[i] != null) {
						size += buffers[i].remaining();
					}
				}
					
				currentSize = size;
				return size;
			}
		}


		/**
		 * {@link ReadQueue#append(ByteBuffer[], int)}	</br>
		 * 
		 * 将ByteBuffer数组添加到队列中.	</br></br>
		 * 
		 * append a byte buffer array to this queue. By adding a array,
		 * the array becomes part of to the buffer, and should not be modified outside the buffer
		 * to avoid side effects
		 *
		 * @param bufs  the ByteBuffers
		 * @param size  the size 
		 */
		public synchronized void append(ByteBuffer[] bufs, int size) {
			isAppended = true;
			version++;
			
			assert (!containsEmptyBuffer(bufs));
			
			// 第一次为null
			if (buffers == null) {
				buffers = bufs;
				currentSize = size;
									
			}  else {
				// 第二次及以后
				currentSize = null;
				
				// 数据复制
				// 上一次和本次
				ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + bufs.length];
				// 将buffers中的数据复制到newBuffers中.
				// buffers存放的是上一次为止的数据.
				// 现在newBuffers中的最后bufs.length个元素为null
				System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);
				// 填充newBuffers中的最后bufs.length个元素为本次读取的bufs
				System.arraycopy(bufs, 0, newBuffers, buffers.length, bufs.length);
				buffers = newBuffers;
			}			
		}
		
		
		public synchronized void addFirst(ByteBuffer[] bufs) {
			version++;
			currentSize = null;
			cachedIndex = null;
			
			assert (!containsEmptyBuffer(bufs));

			addFirstSilence(bufs);	
		}

		
		/**
		 * 
		 */
		private void addFirstSilence(ByteBuffer[] bufs) {
			currentSize = null;
						
			if (buffers == null) {
				buffers = bufs;
							
			} else {
				ByteBuffer[] newBuffers = new ByteBuffer[buffers.length + bufs.length]; 
				System.arraycopy(bufs, 0, newBuffers, 0, bufs.length);
				System.arraycopy(buffers, 0, newBuffers, bufs.length, buffers.length);
				buffers = newBuffers;
			}			
		}

		
		/**
		 * 是否包含空的ByteBuffer
		 */
		private static boolean containsEmptyBuffer(ByteBuffer[] bufs) {
			boolean containsEmtpyBuffer = false;
			
			for (ByteBuffer buf : bufs) {
				if (buf == null) {
					containsEmtpyBuffer = true;
				}
			}
			
			return containsEmtpyBuffer;
		}

		
		
		/**
		 * drain the queue
		 * 
		 * @return the content
		 */
		public synchronized ByteBuffer[] drain() {
			currentSize = null;
			cachedIndex = null;
			
			ByteBuffer[] result = buffers;
			buffers = null;

			if (result != null) {
				version++;
			}
			
			return removeEmptyBuffers(result);
		}
		

		public synchronized ByteBuffer[] copy() {
			
			if (buffers == null) {
				return new ByteBuffer[0];
			}
			
			
			ByteBuffer[] result = new ByteBuffer[buffers.length];
			for (int i = 0; i < buffers.length; i++) {
				result[i] = buffers[i].duplicate();
			}
			
			return removeEmptyBuffers(result);
		}
		
	
		
		/**
		 * read bytes
		 *
		 * @param length  the length
		 * @return the read bytes
	 	 * @throws BufferUnderflowException if the buffer`s limit has been reached
		 */
		public synchronized ByteBuffer readSingleByteBuffer(int length) throws BufferUnderflowException {
			
			// data available?
			if (buffers == null) {
				throw new BufferUnderflowException();
			}
		
			// 字节数是否足够
			// enough bytes available ?
			if (!isSizeEqualsOrLargerThan(length)) {
				throw new BufferUnderflowException();
			}
		
			// length 0 requested?
			if (length == 0) {
				return ByteBuffer.allocate(0);
			}

			
			ByteBuffer result = null;
			int countEmptyBuffer = 0;	// 空的ByteBuffer的
			
			bufLoop : 
			for (int i = 0; i < buffers.length; i++) {
				if (buffers[i] == null) {
					countEmptyBuffer++;
					continue;
				}
					
				// 第一个ByteBuffer的长度 == 请求的长度
				// length first buffer == required length
				if (buffers[i].remaining() == length) {
					result = buffers[i];
					buffers[i] = null;
					break bufLoop;	// 结束循环
			
					
				// 第一个ByteBuffer的长度 > 请求的长度	
				// length first buffer > required length
				} else if(buffers[i].remaining() > length) {
					// 当前的limit和pos
					int savedLimit = buffers[i].limit();
					int savedPos = buffers[i].position();
			
					// 更新limit
					buffers[i].limit(buffers[i].position() + length);
			
					// 子缓冲区, 存放要读取的数据
					result = buffers[i].slice();
			
					// 更新ByteBuffer, 存放未读取的数据 
					buffers[i].position(savedPos + length);
					buffers[i].limit(savedLimit);
					buffers[i] = buffers[i].slice(); 
			
					break bufLoop; // 结束循环
			
				// 第一个ByteBuffer的长度 < 请求的长度	
				// length first buffer < required length
				} else {
					result = ByteBuffer.allocate(length);
					int written = 0;
			
					for (int j = i; j < buffers.length; j++) {
						if (buffers[j] == null) {
							countEmptyBuffer++;
							continue;
							
						} else {
							while(buffers[j].hasRemaining()) {
								result.put(buffers[j].get());
								written++;
									
								// all data written
								if (written == length) {
									if (buffers[j].position() < buffers[j].limit()) {
										// 更新ByteBuffer
										buffers[j] = buffers[j].slice();
									} else {
										// 一个ByteBuffer全部读完,置为null
										buffers[j] = null;		
									}
									// 准备数据
									result.clear();
										
									// 结束循环
									break bufLoop;
								}
							} // end while
						}
			
						buffers[j] = null;
					}
				} // end of else
			}
			
			
			if (result == null) {
				throw new BufferUnderflowException();
				
			} else {
				if (countEmptyBuffer >= THRESHOLD_COMPACT_BUFFER_COUNT_EMPTY) {
					compact();
				}
				
				currentSize = null;
				cachedIndex = null;

				version++;
				return result;
			}
		}

		
		
		
		public ByteBuffer[] readByteBufferByLength(int length) throws BufferUnderflowException {

			if (length == 0) {
				return new ByteBuffer[0];
			}
	 
			return extract(length, 0);
		}
		
		
		
		public ByteBuffer[] readByteBufferByDelimiter(byte[] delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			
			synchronized (this) {
				if (buffers == null) {
					throw new BufferUnderflowException();
				}
			}
			
			// 查找指定分隔符的索引位置
			int index = retrieveIndexOf(delimiter, maxLength);
				
			// delimiter found?
			// 是否找到了指定的分隔符
			if (index >= 0) {
				// 返回提取出的ByteBuffers
				return extract(index, delimiter.length);

			} else { 
				// 找不到抛异常, 不会被被xSocket吞掉
				/** {@link HandlerAdapter#performOnData()} */
				throw new BufferUnderflowException();
			}
		}

		/**
		 * 从缓冲区中提取出指定的长度的缓冲
		 * 
		 * @param length
		 * @param countTailingBytesToRemove		从尾部要删除的字节数目(分隔符数目)
		 */
		synchronized ByteBuffer[] extract(int length, int countTailingBytesToRemove) throws BufferUnderflowException {
			ByteBuffer[] extracted = null;
			
			int size = size();	// 缓冲区大小
					
			// 有可能是读取的时候是根据长度来读取的
			if (length == size) {
				return drain();
			}
			
			if (size < length) {
				throw new BufferUnderflowException();
			}
			
			// 根据长度提取出ByteBuffer
			extracted = extractBuffers(length);

			if (countTailingBytesToRemove > 0) {
				// 忽略返回值, 移除"分隔符"
				extractBuffers(countTailingBytesToRemove); // remove tailing bytes
			}
			
			// 
			compact();
			
			currentSize = null;
			cachedIndex = null;

			version++;
			return extracted;
		}

		
		/**
		 * 返回分隔符的索引, 找不到返回-1		</br></br>
		 * 
		 * return the index of the delimiter or -1 if the delimiter has not been found 
		 * 
		 * @param delimiter         the delimiter
		 * @param maxReadSize       the max read size
		 * @return the position of the first delimiter byte
		 * @throws IOException in an exception occurs
		 * @throws MaxReadSizeExceededException if the max read size has been reached 
		 */
		public int retrieveIndexOf(byte[] delimiter, int maxReadSize) throws IOException, MaxReadSizeExceededException {
			ByteBuffer[] bufs = null;	// 临时变量保存当前的读缓冲
			
			// get the current buffers
			// 读取当前的缓冲区
			// 要取锁,防止在读取的过程中客户端发送新的数据过来
			synchronized (this) {
				if (buffers == null) {
					return -1;
				}
					
				bufs = buffers;
				buffers = null;
			}

			// .. scan it
			// 扫描分隔符, 取得索引位置
			int index = retrieveIndexOf(delimiter, bufs, maxReadSize);
			
			// .. and return the buffers
			// 取锁, 如果此时已经有新的数据进来(读缓冲区已经改变), 那么此时要将其加入到缓冲区的最前面
			// 当前的缓冲区与后面的缓冲区合并
			synchronized (this) {
				addFirstSilence(bufs);
			}
				
			if (index == -2) {
				throw new MaxReadSizeExceededException();
					
			} else {
				return index;
			}
		}

		
		private int retrieveIndexOf(byte[] delimiter, ByteBuffer[] buffers, int maxReadSize) {
			Integer length = null;	// 接收的字节数目 - 分隔符字节数目 => 分隔符的索引位置

			// is data available?
			if (buffers == null) {
				return -1;
			}
				
			// 扫描分隔符
			Index index = scanByDelimiter(buffers, delimiter);
		
			// index found?
			// 是否已经找到
			if (index.hasDelimiterFound()) {
		
				// ... within the max range?
				// 在最大范围内
				if (index.getReadBytes() <= maxReadSize) {
					length = index.getReadBytes() - delimiter.length;

				// .. no
				} else {
					length = null;
				}
		
			// .. no
			} else {
 				length = null;
			}
		
			// 缓存Index
			cachedIndex = index;


			// delimiter not found (length is not set)
			// 分隔符没有找到
			if (length == null) {

				// 是否达到最大字节
				// check if max read size has been reached
				if (index.getReadBytes() >= maxReadSize) {
					return -2;
				}
				
				return -1;

			// delimiter found -> return length
			} else {
				return length;
			}
		}
		
		

		/**
		 * 在buffers中扫描指定的分隔符
		 */
		private Index scanByDelimiter(ByteBuffer[] buffers, byte[] delimiter) {

			// Index已经存在(以前扫描过的)
			// does index already exists (-> former scan) &  same delimiter?
			if ((cachedIndex != null) && (cachedIndex.isDelimiterEquals(delimiter))) {
				// delimiter already found?
				if (cachedIndex.hasDelimiterFound()) {
					return cachedIndex;
		
				// 	.. no
				} else {
					// cached index available -> use index to find
					// Cache Index已经存在 -> 使用index去查找
					// Index保存了分隔符的信息
					return find(buffers, cachedIndex);
				}
				
			// ... no cached index -> find by delimiter
			// 没有缓存的index, 根据分隔符查找
			} else {
				return find(buffers, delimiter);
			}
		}

		
			
		private boolean isSizeEqualsOrLargerThan(int requiredSize) {
			if (buffers == null) {
				return false;
			}

			int bufferSize = 0;
			for (int i = 0; i < buffers.length; i++) {
				if (buffers[i] != null) {
					bufferSize += buffers[i].remaining();
					if (bufferSize >= requiredSize) {
						return true;
					}
				}
			}

			return false;
		}

		  
		// 根据长度提取ByteBuffers
		private ByteBuffer[] extractBuffers(int length) {
			ByteBuffer[] extracted = null;

			int remainingToExtract = length;	// 剩余提取的大小
			ByteBuffer buffer = null;

			for (int i = 0; i < buffers.length; i++) {
			
				// get the first buffer 
				buffer = buffers[i];
				if (buffer == null) {
					continue;
				}

				
				// can complete buffer be taken?
				// 是否可以完全取出
				int bufLength = buffer.limit() - buffer.position();
				if (remainingToExtract >= bufLength) {

					// write taken into out channel
					// 数据的copy
					extracted = appendBuffer(extracted, buffer);
					remainingToExtract -= bufLength;
					buffers[i] = null;	// 清空已经读取过的ByteBuffer

				// .. no
				} else {
					int savedLimit = buffer.limit();

					// extract the takenable
					// 提取出可用的数据
					buffer.limit(buffer.position() + remainingToExtract);
					// 可用数据position~limit
					ByteBuffer leftPart = buffer.slice();
					extracted = appendBuffer(extracted, leftPart);
					
					buffer.position(buffer.limit());
					buffer.limit(savedLimit);
					// limit~capacity
					ByteBuffer rightPart = buffer.slice();
					
					// 可能为"分隔符"
					buffers[i] = rightPart;	
					remainingToExtract = 0;
				}

				if (remainingToExtract == 0) {
					return extracted;
				}
			}

			return new ByteBuffer[0];
		}
		

		/**
		 * 将数据copy到buffers中
		 */
		private static ByteBuffer[] appendBuffer(ByteBuffer[] buffers, ByteBuffer buffer) {
			
			if (buffers == null) {
				ByteBuffer[] result = new ByteBuffer[1];
				result[0] = buffer;
				return result;
				
			} else {
				ByteBuffer[] result = new ByteBuffer[buffers.length + 1];
				System.arraycopy(buffers, 0, result, 0, buffers.length);
				result[buffers.length] = buffer;
				return result;
			}		
		}


		
		// FIXME：		
		private void compact() {
			if ((buffers != null) && (buffers.length > THRESHOLD_COMPACT_BUFFER_COUNT_TOTAL)) {
				
				// count empty buffers
				int emptyBuffers = 0;
				for (int i = 0; i < buffers.length; i++) {
					if (buffers[i] == null) {
						emptyBuffers++;
					}
				}
						
				// enough empty buffers found? create new compact array
				if (emptyBuffers > THRESHOLD_COMPACT_BUFFER_COUNT_EMPTY) {
					if (emptyBuffers == buffers.length) {
						buffers = null;
								
					} else {
						ByteBuffer[] newByteBuffer = new ByteBuffer[buffers.length - emptyBuffers];
						int num = 0;
						for (int i = 0; i < buffers.length; i++) {
							if (buffers[i] != null) {
								newByteBuffer[num] = buffers[i];
								num++;
							}
						}
								
						buffers = newByteBuffer;
					}
				}
			}
		}
		
		
		
		private static ByteBuffer[] removeEmptyBuffers(ByteBuffer[] buffers) {
			if (buffers == null) {
				return buffers;
			}

			
			int countEmptyBuffers = 0;
			for (int i = 0; i < buffers.length; i++) {
				if (buffers[i] == null) {
					countEmptyBuffers++;
				}
			}
			
			if (countEmptyBuffers > 0) {
				if (countEmptyBuffers == buffers.length) {
					return new ByteBuffer[0];

				} else {
					ByteBuffer[] newBuffers = new ByteBuffer[buffers.length - countEmptyBuffers];
					int num = 0;
					for (int i = 0; i < buffers.length; i++) {
						if (buffers[i] != null) {
							newBuffers[num] = buffers[i];
							num++;
						}
					}
					return newBuffers;
				}
				
			} else {
				
				return buffers;
			}
		}
		

		
  		@Override
   		public String toString() {
			try {
				ByteBuffer[] copy = buffers.clone();
				for (int i = 0; i < copy.length; i++) {
					if (copy[i] != null) {
						copy[i] = copy[i].duplicate();
					}
				}
				return DataConverter.toTextAndHexString(copy, "UTF-8", 300);
				
			} catch (NullPointerException npe) {
   				return "";
   				
   			} catch (Exception e) {
   				return e.toString();
   			}
   		}
  		 
  	 
  		public synchronized String toString(String encoding) {
			try {
				ByteBuffer[] copy = buffers.clone();
				for (int i = 0; i < copy.length; i++) {
					if (copy[i] != null) {
						copy[i] = copy[i].duplicate();
					}
				}
				return DataConverter.toString(copy, encoding);
				
			} catch (NullPointerException npe) {
   				return "";
   				
   			} catch (Exception e) {
   			    throw new RuntimeException(e);
   			}
   		}
  		
  		
  		private Index find(ByteBuffer[] bufferQueue, byte[] delimiter) {
  			return find(bufferQueue, new Index(delimiter));
  		}

  		private Index find(ByteBuffer[] buffers, Index index) {
  			// 找到第一个要被扫描的buffer
  			// 如果有多个buffer,那么从前几个buffer中没有扫描到,那么就从最后一个开始扫描
  			int i = findFirstBufferToScan(buffers, index);
  			
  			for (; (i < buffers.length) && !index.hasDelimiterFound; i++) {
  			
  				ByteBuffer buffer = buffers[i];
  				if (buffer == null) {
  					continue;
  				} 

  				// save current buffer positions
  				// 保存当前的缓冲位置
  				int savedPos = buffer.position();
  				int savedLimit = buffer.limit();
  				
  				// 在ByteBuffer中查找
  				findInBuffer(buffer, index);
  				
  				// 恢复缓存位置
  				// restore buffer positions
  				buffer.position(savedPos);
  				buffer.limit(savedLimit);
  			}

  			return index;
  		}

  		private int findFirstBufferToScan(ByteBuffer[] buffers, Index index) {
  			int i = 0;
  			
  			// jump to next buffer which follows the cached one   
  			// 跳到下一个缓冲区
  			if (index.lastScannedBuffer != null) {
  				
  				// find the last scanned buffer 
  				// 找到最后一个扫描的buffer
  				for (int j = 0; j < buffers.length; j++) {
  					if (buffers[j] == index.lastScannedBuffer) {
  						i = j;
  						break;
  					}
  				}
  				
  				
  				// position to next buffer
  				// 要扫描的buffer的位置
  				i++;
  				
  				
  				// are there more buffers?
  				// 是否还有更多的buffers
  				if (i >= buffers.length) {
  					// ...no, do nothing
  					return i;
  				}
  				
  				
  				// filter the empty buffers
  				// 过滤空的buffers
  				for (int k = i; k < buffers.length; k++) {
  					if (buffers[k] != null) {
  						i = k;
  						break;
  					}
  				}
  				
  				
  				// are there more buffers?
  				if (i >= buffers.length) {
  					// ...no, do nothing
  					return i;
  				}
  			}
  			
  			return i;
  		}

  		
  		
  		private void findInBuffer(ByteBuffer buffer, Index index) {
  			// 最后一次扫描的buffer
  			index.lastScannedBuffer = buffer;
  			int dataSize = buffer.remaining();
  			
  			byte[] delimiter = index.delimiterBytes;				// 分隔字节
  			int delimiterLength = index.delimiterLength;			// 分隔符长度	
  			int delimiterPosition = index.delimiterPos;				// 分隔位置
  			byte nextDelimiterByte = delimiter[delimiterPosition];	// 单个的分隔字节
  			boolean delimiterPartsFound = delimiterPosition > 0;	// 分隔符部分已经找到(匹配的第一个字节true,在后面的匹配过程中有可能会置为false)
  			
  			
  			for (int i = 0; i < dataSize; i++) {
  				
  				byte b = buffer.get();
  				
  				// is current byte a delimiter byte?
  				// 当前字节是否为一个分隔符字节
  				if (b == nextDelimiterByte) {
  					delimiterPosition++;
  					
  					// is single byte delimiter?
  					// 分隔符是否只是一个字节, 是, 退出for循环, 退出该方法
  					if (delimiterLength  == 1) {
  						index.hasDelimiterFound = true;
  						index.delimiterPos = delimiterPosition;
  						index.readBytes += (i + 1);
  						return;
  					
  					// .. no, it is a multi byte delimiter
  					// 多字节的分隔符
  					} else {
  						index.delimiterPos = delimiterPosition;
  						
  						// last delimiter byte found?
  						// 最后的分隔符字节是否已经找到?
  						if (delimiterPosition == delimiterLength) {
  							index.hasDelimiterFound = true;
  							index.readBytes += (i + 1);
  							return;
  						}
  						
  						// 下一个分隔符的字节
  						nextDelimiterByte = delimiter[delimiterPosition];
  					}
  					
  					delimiterPartsFound = true;

  				// byte doesn't match
  				// 不匹配
  				} else {
  					
  					if (delimiterPartsFound) {
  						delimiterPosition = 0;
  						index.delimiterPos = 0;
  						nextDelimiterByte = delimiter[delimiterPosition];
  						// 如果前面已经匹配上, 要置为false
  						delimiterPartsFound = false;
  						
  						// check if byte is equals to first delimiter byte 
  						// 检查字节是否与第一个分隔符字节相等
  						if ((delimiterLength  > 1) && (b == nextDelimiterByte)) {
  							delimiterPosition++;
  							nextDelimiterByte = delimiter[delimiterPosition];
  							index.delimiterPos = delimiterPosition;
  						}
  					}
  				}
  			}
  			
  			index.readBytes += dataSize;
  		}
	}
	
	
	/**
	 * 缓存分隔符
	 */
	private static final class Index implements Cloneable {
			private boolean hasDelimiterFound = false;	// 分隔符是否已经找到
			private byte[] delimiterBytes = null;		// 分隔符字节
			private int delimiterLength = 0;			// 分隔符长度
			private int delimiterPos = 0;				// 分隔符位置(在最先找到分隔符的ByteBuffer)
			
			// consumed bytes
			private int readBytes = 0;
					
			// cache support
			// 缓存支持：最后扫描的ByteBuffer
			/*
			 * 扫描分隔符的时候从最后一次的ByteBuffer开始.
			 * eg：
			 * 第一次发送：h
			 * 第二次发送：a
			 * 第三次发送：h
			 * 第四次发送：a
			 * 
			 * 因为第一、二、三次都没有指定的分隔符(已经扫描过), 那么就从最后一次(第四次)开始扫描
			 */
			/** {@link Queue#findInBuffer(ByteBuffer, Index)} */
			ByteBuffer lastScannedBuffer = null;	

			
			Index(byte[] delimiterBytes) {
				this.delimiterBytes = delimiterBytes;
				this.delimiterLength =  delimiterBytes.length;
			}
			
			public boolean hasDelimiterFound() {
				return hasDelimiterFound;
			}
		
			public int getReadBytes() {
				return readBytes;
			}

			
			public boolean isDelimiterEquals(byte[] other) {

				if (other.length != delimiterLength) {
					return false;
				}

				for (int i = 0; i < delimiterLength; i++) {
					if (other[i] != delimiterBytes[i]) {
						return false;
					}
				}

				return true;
			}

			
			@Override
			protected Object clone() throws CloneNotSupportedException {
				Index copy = (Index) super.clone();			
				return copy;
			}
		
			@Override
			public String toString() {
				return "found=" + hasDelimiterFound + " delimiterPos=" + delimiterPos 
				       + " delimiterLength="+ delimiterLength + " readBytes=" + readBytes;
			}
		}
}