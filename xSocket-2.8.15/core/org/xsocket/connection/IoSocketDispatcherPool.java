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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 管理IoSocketDispatcher	</br>
 * updateDispatcher()		</br>
 * 
 * Dispacher pool
 * 
 * @author grro@xsocket.org
 */
final class IoSocketDispatcherPool implements Closeable {

	private static final Logger LOG = Logger.getLogger(IoSocketDispatcherPool.class.getName());
	
    
    // open flag
    private volatile boolean isOpen = true;

    // name 
    private final String name;

    
	// listeners
    // updateDispatcher方法中添加
    // nextDispatcher方法中取出
    private final ArrayList<IIoDispatcherPoolListener> listeners = new ArrayList<IIoDispatcherPoolListener>();

    
	// memory management
	private int preallocationSize = IoProvider.DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;	// 默认16384
	private int bufferMinsize = IoProvider.DEFAULT_READ_BUFFER_MIN_SIZE;	// 默认64
	private boolean preallocation = true; 
	private boolean useDirect = false;

	// dispatcher management
	private final LinkedList<IoSocketDispatcher> dispatchers = new LinkedList<IoSocketDispatcher>();
	// 在setDispatcherSize(int)方法中设置, 创建的IoSocketDispatcher的数目
	private int size;
	// 从dispatchers取出的索引
	private int pointer;
	
    // statistics
    private long acceptedConnections;
	private long lastRequestAccpetedRate = System.currentTimeMillis();


	/**
	 * 唯一的构造函数
	 */
	public IoSocketDispatcherPool(String name, int size) {
		this.name = name;
		// 设置调度程序的大小, 创建IoSocketDispatcher线程
		// 
		setDispatcherSize(size);
    }
    

    void addListener(IIoDispatcherPoolListener listener) {
		listeners.add(listener);
	}


	boolean removeListener(IIoDispatcherPoolListener listener) {
		return listeners.remove(listener);
	}



	IoSocketDispatcher nextDispatcher() throws IOException {
		return nextDispatcher(0);
	}
	
	
	/**
	 * 有1个新的连接, 取出一个IoSocketDispatcher
	 */
	private IoSocketDispatcher nextDispatcher(int currentTrial) throws IOException {
		IoSocketDispatcher dispatcher = null;
		
		// isOpen默认为true
		if (!isOpen) {
		    throw new IOException("dispatcher is already closed");
		}
		
		try {
			// round-robin approach
			// 循环调用
			// 比如,如果IoSocketDispatcherPool中有3个IoSocketDispatcher
			// 那么这里取出IoSocketDispatcher的顺序为：
			// 1 -> 2 -> 0 -> 1 -> 2 -> 0 -> 1 -> 2 -> 0 ...
			// 但是如果在多线程环境中,那么就可能不会顺序调用.
			// 不过在多线程环境中,pointer++的操作肯定会产生并发问题的,导致了pointer的值超过了size
			// 不过这里做了pointer >= size的操作,所以也不会产生从dispatchers.get(pointer)的时候出现IndexOutOfBoundsException问题
			pointer++;
			if (pointer >= size) { // unsynchronized access of size is OK here (findbugs will criticize this)
				// 继续从0开始取
				pointer = 0;
			}
	
			System.out.println("IoSocketDispatcher pointer：" + pointer);
			dispatcher = dispatchers.get(pointer);
			// 默认返回true
			boolean peregistered = dispatcher.preRegister();
			
			if (peregistered) {
				return dispatcher;
				
			} else {
				if (currentTrial < size) {
					return nextDispatcher(++currentTrial);
				} else {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("increasing dispatcher size because max handle size " + dispatcher.getMaxRegisterdHandles() + " of all " + size + " dispatcher reached");
					}
					incDispatcherSize();
					return nextDispatcher(0);
				}
			}
			
		} catch (Exception concurrentException) {
			if (isOpen) {
				if (currentTrial < 3) {
					dispatcher = nextDispatcher(++currentTrial);
				} else {
					throw new IOException(concurrentException.toString());
				}
			} 
		}
		
		return dispatcher;
	}
	
	
	/**
	 * FIXME 此方法是否一定要synchronized进行同步?
	 * 此方法为私有,且在另一个同步方法中被调用.	</br>
	 * 
	 * 创建IoSocketDispatcher线程,并设置为后台线程,等待处理读/写事件		</br>
	 */
	private synchronized void updateDispatcher() {
		// isOpen默认为true
		if (isOpen) {
			synchronized (dispatchers) {
				int currentRunning = dispatchers.size();	// 默认为0
	
				if (currentRunning != size) {
					if (currentRunning > size) {
						for (int i = size; i <  currentRunning; i++) {
							IoSocketDispatcher dispatcher = dispatchers.getLast();
							dispatchers.remove(dispatcher);
							try {
								dispatcher.close();
							} catch (IOException ioe) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("error occured by closing the dispatcher " + dispatcher + ". reason " + ioe.toString());
								}
							}
	
							for (IIoDispatcherPoolListener listener : listeners) {
								listener.onDispatcherRemoved(dispatcher);
							}
						}
	
					} else if ( currentRunning < size) {	// 默认运行这里
						// 创建IoSocketDispatcher线程,并设置为后台线程
						for (int i = currentRunning; i < size; i++) {
							IoUnsynchronizedMemoryManager memoryManager = null;
							// 默认为true
							if (preallocation) {
								// preallocationSize默认为：16384
								// bufferMinsize默认为：64
								// useDirect默认false
								memoryManager = IoUnsynchronizedMemoryManager.createPreallocatedMemoryManager(preallocationSize, bufferMinsize, useDirect);
							} else {
								memoryManager = IoUnsynchronizedMemoryManager.createNonPreallocatedMemoryManager(useDirect);
							}
							
							// 实例化IoSocketDispatcher,实例化过程中会打开Selector
							// 一个IoSocketDispatcher对应一个Selector
							IoSocketDispatcher dispatcher = new IoSocketDispatcher(memoryManager, name + "#" + i);
							dispatchers.addLast(dispatcher);
	
							// XXX 创建IoSocketDispatcher线程,并启动
							Thread t = new Thread(dispatcher);
							// 后台线程
							t.setDaemon(true);
							t.start();
	
							for (IIoDispatcherPoolListener listener : listeners) {
								listener.onDispatcherAdded(dispatcher);
							}
	
						}
					}
				}
	
				// FIXME：貌似没有任何用处
//				IoSocketDispatcher[] connectionDispatchers = new IoSocketDispatcher[dispatchers.size()];
//				for (int i = 0; i < connectionDispatchers.length; i++) {
//					connectionDispatchers[i] = dispatchers.get(i);
//				}
			}
		}
	}


    

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (isOpen) {
            isOpen = false;

            shutdownDispatcher();
        }
    }

    
    

	/**
	 * shutdown the pool
	 *
	 */
	private void shutdownDispatcher() {
        if (LOG.isLoggable(Level.FINER)) {
			LOG.fine("terminate dispatchers");
		}

        synchronized (dispatchers) {
			for (IoSocketDispatcher dispatcher : dispatchers) {
				try {
					dispatcher.close();
	
					for (IIoDispatcherPoolListener listener : listeners) {
						listener.onDispatcherRemoved(dispatcher);
					}
	
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by closing the dispatcher " + dispatcher + ". reason " + ioe.toString());
					}
				}
	        }
        }

		dispatchers.clear();
	}

	
	int getNumRegisteredHandles() {
	    int num = 0;
	    for (IoSocketDispatcher dispatcher : getDispatchers()) {
	        num += dispatcher.getNumRegisteredHandles();
        }
	    return num;
	}

	    
	int getRoughNumRegisteredHandles() {
        int num = 0;
        for (IoSocketDispatcher dispatcher : getDispatchers()) {
            num += dispatcher.getRoughNumRegisteredHandles();
        }
        return num;
	}


    
    public List<String> getOpenConntionInfos() {
    	List<String> result = new ArrayList<String>();
    	
        for (IoSocketDispatcher dispatcher : getDispatchers()) {
            for (IoSocketHandler handler : dispatcher.getRegistered()) {
                result.add(handler.toString());
            }
        }
        return result;
    }
    
    
	@SuppressWarnings("unchecked")
	List<IoSocketDispatcher> getDispatchers() {
		List<IoSocketDispatcher> result = null;
		synchronized (dispatchers) {
			result = (List<IoSocketDispatcher>) dispatchers.clone();
		}
		return result;
	}

    
	/**
	 * 设置大小.</br>
	 * FIXME :这里为什么需要同步? 		</br>
	 * <pre>
	 * 1. 在构造函数中调用此方法：
	 * IoSocketDispatcherPool的实例化过程是在实例化Server的过程中实现的.
	 * 实例化Server不会发生在两个线程之中.那么也就没有必要在此进行同步.
	 * </pre>
	 */
	synchronized void setDispatcherSize(int size) {
    	this.size = size;
		updateDispatcher();
    }
    
	synchronized int getDispatcherSize() {
    	return size;
    }
	
	synchronized void incDispatcherSize() {
    	setDispatcherSize(getDispatcherSize() + 1);
    }
	

    
	boolean getReceiveBufferIsDirect() {
		return useDirect;
	}

	void setReceiveBufferIsDirect(boolean isDirect) {
		this.useDirect = isDirect; 
		for (IoSocketDispatcher dispatcher: dispatchers) {
			dispatcher.setReceiveBufferIsDirect(isDirect);
		}
	}

	
	boolean isReceiveBufferPreallocationMode() {
    	return preallocation;
	}
	    
	void setReceiveBufferPreallocationMode(boolean mode) {
		this.preallocation = mode;

		synchronized (dispatchers) {
			for (IoSocketDispatcher dispatcher: dispatchers) {
				dispatcher.setReceiveBufferPreallocationMode(mode);
			}
		}
	}
	
	void setReceiveBufferPreallocatedMinSize(Integer minSize) {
		this.bufferMinsize = minSize;
		
		synchronized (dispatchers) {
			for (IoSocketDispatcher dispatcher: dispatchers) {
				dispatcher.setReceiveBufferPreallocatedMinSize(minSize);
			}
		}
	}
	
	
	Integer getReceiveBufferPreallocatedMinSize() {
   		return bufferMinsize;
 	}
	
	
	
	/**
	 * get the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @return preallocation buffer size
	 */
	Integer getReceiveBufferPreallocationSize() {
		return preallocationSize;
	}

	/**
	 * set the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @param size the preallocation buffer size
	 */
	void setReceiveBufferPreallocationSize(int size) {
		preallocationSize = size;
		
		for (IoSocketDispatcher dispatcher: dispatchers) {
			dispatcher.setReceiveBufferPreallocatedSize(size);
		}
	}


	  
    double getAcceptedRateCountPerSec() {
     	double rate = 0;
		
    	long elapsed = System.currentTimeMillis() - lastRequestAccpetedRate;
    	
    	if (acceptedConnections == 0) {
    		rate = 0;
    		
    	} else if (elapsed == 0) {
    		rate = Integer.MAX_VALUE;
    		
    	} else {
    		rate = (((double) (acceptedConnections * 1000)) / elapsed);
    	}
    		
    	lastRequestAccpetedRate = System.currentTimeMillis();
    	acceptedConnections = 0;

    	return rate;
    }
    
	
	long getSendRateBytesPerSec() {
		long rate = 0;
		for (IoSocketDispatcher dispatcher : dispatchers) {
			rate += dispatcher.getSendRateBytesPerSec();
		}
		
		return rate;
	}
	
	
	long getReceiveRateBytesPerSec() {
		long rate = 0;
		for (IoSocketDispatcher dispatcher : dispatchers) {
			rate += dispatcher.getReceiveRateBytesPerSec();
		}
		
		return rate;
	}
	


	@SuppressWarnings("unchecked")
	long getNumberOfConnectionTimeouts() {
		long timeouts = 0;

		LinkedList<IoSocketDispatcher> copy = null;
		synchronized (dispatchers) {
			copy = (LinkedList<IoSocketDispatcher>) dispatchers.clone();
		}
		for (IoSocketDispatcher dispatcher : copy) {
			timeouts += dispatcher.getCountConnectionTimeout();
		}
		return timeouts;
	}


	@SuppressWarnings("unchecked")
	public long getNumberOfIdleTimeouts() {
		long timeouts = 0;

		LinkedList<IoSocketDispatcher> copy = null;
		synchronized (dispatchers) {
			copy = (LinkedList<IoSocketDispatcher>) dispatchers.clone();
		}
		for (IoSocketDispatcher dispatcher : copy) {
			timeouts += dispatcher.getCountIdleTimeout();
		}
		return timeouts;
	}


}
