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

import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;




/**
 * Serialized Task Queue
 * 
 * <br/><br/><b>This is a xSocket internal class and subject to change</b> 
 * 
 * @author grro@xsocket.org
 */
public final class SerializedTaskQueue  {
	
	private static final Logger LOG = Logger.getLogger(SerializedTaskQueue.class.getName());

	private final LinkedList<Runnable> multithreadedTaskQueue = new LinkedList<Runnable>();
	
	private final ReentrantLock processLock = new ReentrantLock(false);
	private final MultithreadedTaskProcessor multithreadedTaskProcessor = new MultithreadedTaskProcessor();

	

	
	/**
	 * process a task non threaded synchronized by the internal task queue. If the 
	 * task queue is empty the task will be processed by the current thread. If a task running 
	 * or the task queue size is not empty, the task will be executed by a dedicated thread.   
	 * 
	 * @param task        the task to process
	 * @param workerpool  the workerpool
	 */
	public void performNonThreaded(Runnable task,  Executor workerpool) {

		
		// got lock -> process nonthreaded
		if (processLock.tryLock()) {

			try {
				
				// are there pending tasks -> run task multithreaded
				synchronized (multithreadedTaskQueue) {
					if (!multithreadedTaskQueue.isEmpty()) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("multithreaded tasks are in queue . register non threaded task " + task.toString() + " to multithreaded queue (non threaded task will be performed multithreaded)");
						}	

						performMultiThreaded(task, workerpool);
						return;
					}
				}
				
				task.run();
				
			} finally {
				processLock.unlock();
			}
			
		
		// task are running -> perform task multithreaded
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("a task is already running. register non threaded task " + task.toString() + " to perform it multithreaded");
			}		
			performMultiThreaded(task, workerpool);
		}
	}

	
	
	
	/**
	 * process a task multi threaded synchronized by the internal task queue. The task will
	 * be processed by a dedicated thread. The given worker pool <i>can</i> be used to perform this 
	 * tasks (as well as other task of the queue).  
	 * 
	 * @param task the task to process
	 * @param workerpool  the workerpool 
	 */
	public void performMultiThreaded(Runnable task, Executor workerpool) {
	
		// add task to queue 
		synchronized (multithreadedTaskQueue) {
			
			
			// (Multithreaded) worker is not running
			if (multithreadedTaskQueue.isEmpty()) {
				multithreadedTaskQueue.addLast(task);
				
				try {
					workerpool.execute(multithreadedTaskProcessor);
				} catch (RejectedExecutionException ree) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("task has been rejected by worker pool " + workerpool + " (worker pool cosed?) performing task by starting a new thread");
					}
					Thread t = new Thread(multithreadedTaskProcessor, "SerializedTaskQueueFallbackThread");
					t.setDaemon(true);
					t.start();
				}
				
			} else {
				multithreadedTaskQueue.addLast(task);
			}
		}
	}

	
	
	
	
	
	private void performPendingTasks() {
		
		// lock process
		processLock.lock();
			
		try {
			// handle all pending tasks
			while (true) {
				
				// get task from queue
				Runnable task = null;
				
				synchronized (multithreadedTaskQueue) {
					if (!multithreadedTaskQueue.isEmpty()) {
						task = multithreadedTaskQueue.get(0);
					}
				}
	
					
				// perform it
				if (task != null) {
					try {
						task.run();
					} catch (Throwable t) {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("error occured by processing " + task + " " + t.toString());
						}
					}
				}
					
	
				// is queue empty?
				synchronized (multithreadedTaskQueue) {			
					multithreadedTaskQueue.remove(task);					
					if (multithreadedTaskQueue.isEmpty()) {
						return;
					}
				}
			}
			
		} finally {
			processLock.unlock();
		}
	}
	

	
	private final class MultithreadedTaskProcessor implements Runnable {
		
		public void run() {
			performPendingTasks();
		}
	}	
}
