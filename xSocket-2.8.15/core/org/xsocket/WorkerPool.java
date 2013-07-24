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
package org.xsocket;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 继承ThreadPoolExecutor,自定义线程池实现	</br></br>
 * 
 * WorkerPool implementation
 * 
 * @author grro@xsocket.org
 */
public final class WorkerPool extends ThreadPoolExecutor {

    private static final Logger LOG = Logger.getLogger(WorkerPool.class.getName());

    
    
    /**
     * constructor 
     * 
     * @param maxSize   max worker size
     */
    public WorkerPool(int maxSize) {
        this(0, maxSize, 60, TimeUnit.SECONDS, false);
    }
    
    
    /**
     * constructor 
     * 
     * @param minSize   min worker size
     * @param maxSize   max worker size
     */
    public WorkerPool(int minSize, int maxSize) {
        this(minSize, maxSize, 60, TimeUnit.SECONDS, false);
    }
    

    
    /**
     * constructor 
     * 
     * @param minSize       min worker size
     * @param maxSize       max worker size
     * @param taskqueuesize the task queue size 
     */
    public WorkerPool(int minSize, int maxSize, int taskqueuesize) {
        this(minSize, maxSize, 60, TimeUnit.SECONDS, taskqueuesize, false);
    }
        
    
    
    /**
     * constructor 
     * 
     * @param minSize   min worker size
     * @param maxSize   max worker size
     * @param keepalive the keepalive
     * @param timeunit  the timeunit
     * @param isDaemon  true, if worker threads are daemon threads
     */
    public WorkerPool(int minSize, int maxSize, long keepalive, TimeUnit timeunit, boolean isDaemon) {
        this(minSize, maxSize, keepalive, timeunit, Integer.MAX_VALUE, isDaemon);
    }
    
    
    /**
     * 其它构造函数调用此构造函数		</br></br>
     * 
     * constructor 
     * 
     * @param minSize       min worker size
     * 						工作者池最小数目
     * 
     * @param maxSize       max worker size
     * 						工作者池最大数目
     * 
     * @param keepalive     the keepalive
     * @param timeunit      the timeunit
     * 
     * @param taskqueuesize the task queue size
     * 						任务队列 
     * 
     * @param isDaemon  true, if worker threads are daemon threads
     * 						是否为后台线程
     */
    public WorkerPool(int minSize, int maxSize, long keepalive, TimeUnit timeunit, int taskqueuesize, boolean isDaemon) {
    	// 1. WorkerPoolAwareQueue构造
    	// 2. DefaultThreadFactory构造
    	// 3. 父类ThreadPoolExecutor构造
        super(minSize, maxSize, keepalive, timeunit, new WorkerPoolAwareQueue(taskqueuesize), new DefaultThreadFactory(isDaemon));
        
        ((WorkerPoolAwareQueue) getQueue()).init(this);
    }
        

    /**
     * 继承LinkedBlockingQueue,覆盖了offer()方法
     *
     */
    @SuppressWarnings("serial")
    private static final class WorkerPoolAwareQueue extends LinkedBlockingQueue<Runnable> {
        
        private WorkerPool workerPool;
        
        public WorkerPoolAwareQueue(int capacity) {
            super(capacity);
        }
        
        public void init(WorkerPool workerPool) {
            this.workerPool = workerPool;
        }
        
        @Override
        public boolean offer(Runnable task) {

            // active count smaller than pool size?
        	// 活动线程数目小于池大小
            if (workerPool.getActiveCount() < workerPool.getPoolSize()) {

            	// 将任务加入到队列中,任务应该被立即处理
            	// 因为空闲的工作者线程可用(工作者池的所有线程监听新任务的对立)
            	// add the task to the queue. The task should be handled immediately
                // because free worker threads should be available (all threads of 
                // the workerpool listens the queue for new tasks)
                return super.offer(task);
            }

            // max size reached?
            // 池的最大数目已经达到
            if (workerPool.getPoolSize() >= workerPool.getMaximumPoolSize()) {
            	// 将任务加入到队尾.
            	// 新任务必须等待直到有线程空闲了,来处理这个任务
                // add the task to the end of the queue. The task have to wait
                // until one of the threads becomes free to handle the task
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("add task to queue waiting for the next free one");
                }

                return super.offer(task);
            // ... no
            } else {
            	// 强制启动一个新的线程,新线程执行任务,然后在执行完任务后加入到工作者池中
            	// 由于工作者池增加了.如果线程达到了它的keepalive超时时间(等待新任务),这个线程将终止它自己
            	// return false, which forces starting a new thread. The new
                // thread performs the task and will be added to workerpool
                // after performing the task 
                // As result the workerpool is increased. If the thread reach
                // its keepalive timeout (waiting for new tasks), the thread 
                // will terminate itself
                
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("initiate creating a new thread");
                }
                
                return false;
            }
        }
    }
    
    
    /** 线程工厂 */
    private static class DefaultThreadFactory implements ThreadFactory {
        
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final boolean isDaemon;

        DefaultThreadFactory(boolean isDaemon) {
            this.isDaemon = isDaemon;
            // 名称前缀
            namePrefix = "xWorkerPool-" + poolNumber.getAndIncrement() + "-thread-";
        }

        @Override
        public Thread newThread(Runnable r) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("creating new thread");
            }
            
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(isDaemon);

            // 设置优先级
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
