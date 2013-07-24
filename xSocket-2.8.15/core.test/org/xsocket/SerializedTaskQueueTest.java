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


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;



/**
*
* @author grro@xsocket.org
*/
public final class SerializedTaskQueueTest {

	private ExecutorService workerpool;
	private SerializedTaskQueue taskqueue;
	private static AtomicInteger running;
	private static List<String> errors; 
	private static StringBuffer orderInfo; 
	

	
	@Before
	public void setUp() {
		workerpool = Executors.newCachedThreadPool();
		taskqueue = new SerializedTaskQueue();
		running = new AtomicInteger(0);
		errors = new ArrayList<String>();
		orderInfo = new StringBuffer();
	}
	
	
	@After
	public void tearDown() {
		workerpool.shutdown();
	}


	@Ignore
	@Test 
	public void testTwoNonThreaded() throws Exception {
	
		final Task task1 = new Task(1, 500);
		final Task task2 = new Task(2, 500);
		
		
		new Thread() {
			public void run() {
				Thread.currentThread().setName("TEST1");
				taskqueue.performNonThreaded(task1, workerpool);
			}
		}.start();

		QAUtil.sleep(100);
		Assert.assertEquals(1, running.get());
		Assert.assertTrue(task1.isRunning());
		Assert.assertTrue("TEST1".equals(task1.getThreadname()));
		
		new Thread() {
			public void run() {
				Thread.currentThread().setName("TEST2");
				taskqueue.performNonThreaded(task2, workerpool);
			}
		}.start();
		
		QAUtil.sleep(100);
		Assert.assertEquals(1, running.get());
		Assert.assertTrue(task1.isRunning());
		Assert.assertFalse(task2.isRunning());

		

		QAUtil.sleep(500);
		Assert.assertEquals(1, running.get());
		Assert.assertFalse(task1.isRunning());
		Assert.assertTrue(task2.isRunning());
		Assert.assertFalse("TEST2".equals(task2.getThreadname()));


	
		waitForCompletion();
	}


	@Ignore
	@Test 
	public void testTwoMultiThreaded() throws Exception {

		final Task task1 = new Task(1, 500);
		final Task task2 = new Task(2, 500);
		
		
		new Thread() {
			public void run() {
				Thread.currentThread().setName("TEST1");
				taskqueue.performMultiThreaded(task1, workerpool);
			}
		}.start();

		QAUtil.sleep(100);
		Assert.assertEquals(1, running.get());
		Assert.assertTrue(task1.isRunning());
		Assert.assertFalse("TEST1".equals(task1.getThreadname()));
		
		new Thread() {
			public void run() {
				Thread.currentThread().setName("TEST2");
				taskqueue.performMultiThreaded(task2, workerpool);
			}
		}.start();
		
		QAUtil.sleep(100);
		Assert.assertEquals(1, running.get());
		Assert.assertTrue(task1.isRunning());
		Assert.assertFalse(task2.isRunning());

		
		QAUtil.sleep(500);
		Assert.assertEquals(1, running.get());
		Assert.assertFalse(task1.isRunning());
		Assert.assertTrue(task2.isRunning());
		Assert.assertFalse("TEST2".equals(task2.getThreadname()));

	
		waitForCompletion();
	}

	
	@Ignore
	@Test 
	public void testOneMultithreadedAndOneNonThreaded() throws Exception {
		
		final Task task1 = new Task(1, 500);
		final Task task2 = new Task(2, 500);
		final Task task3 = new Task(3, 500);
		
		
		new Thread() {
			public void run() {
				Thread.currentThread().setName("TEST1");
				taskqueue.performMultiThreaded(task1, workerpool);
			}
		}.start();

		QAUtil.sleep(100);
		Assert.assertEquals(1, running.get());
		Assert.assertTrue(task1.isRunning());
		Assert.assertFalse("TEST1".equals(task1.getThreadname()));
		
		new Thread() {
			public void run() {
				Thread.currentThread().setName("TEST2");
				taskqueue.performNonThreaded(task2, workerpool);
			}
		}.start();
		
		QAUtil.sleep(100);
		Assert.assertEquals(1, running.get());
		Assert.assertTrue(task1.isRunning());
		Assert.assertFalse(task2.isRunning());

	
		new Thread() {
			public void run() {
				Thread.currentThread().setName("TEST3");
				taskqueue.performNonThreaded(task3, workerpool);
			}
		}.start();
		

		QAUtil.sleep(500);
		Assert.assertEquals(1, running.get());
		Assert.assertFalse(task1.isRunning());
		Assert.assertTrue(task2.isRunning());
		Assert.assertFalse("TEST2".equals(task2.getThreadname()));

	
		waitForCompletion();
	}

	@Ignore
	@Test 
	public void testOneMultithreadedAndOneNonThreaded2() throws Exception {
		
		final Task task1 = new Task(1, 200);
		final Task task2 = new Task(2, 200);
		
		for (int i = 0; i < 10; i++) {
			Thread.currentThread().setName("TEST");
			taskqueue.performMultiThreaded(task1, workerpool);
			taskqueue.performNonThreaded(task2, workerpool);
			
			QAUtil.sleep(100);
			Assert.assertEquals(1, running.get());
			Assert.assertTrue(task1.isRunning());
			Assert.assertFalse(task2.isRunning());
		
			waitForCompletion();
			
			Assert.assertFalse(task1.getThreadname().endsWith("TEST"));
			Assert.assertFalse(task2.getThreadname().endsWith("TEST"));
		}
	}
	
	
	@Ignore
	@Test 
	public void testNonthreadedMultithreadedNonThreaded() throws Exception {
		
		final Task task1 = new Task(1, 300);
		final Task task2 = new Task(2, 300);
		final Task task3 = new Task(3, 300);
		
		Thread.currentThread().setName("TEST");
		
		new Thread() {
			public void run() {
				QAUtil.sleep(100);
				taskqueue.performMultiThreaded(task2, workerpool);
			}
		}.start();
		
		taskqueue.performNonThreaded(task1, workerpool);
		
		taskqueue.performNonThreaded(task3, workerpool);
			
		waitForCompletion();
			
		Assert.assertEquals("123", orderInfo.toString());
		Assert.assertTrue(task1.getThreadname().endsWith("TEST"));
		Assert.assertFalse(task2.getThreadname().endsWith("TEST"));	
		Assert.assertFalse(task2.getThreadname().endsWith("TEST"));
	}
	
	
	@Ignore
	@Test 
	public void testNonThreadedRecursive() throws Exception {

		Thread.currentThread().setName("TEST");
		RecursiveTask task = new RecursiveTask(workerpool, taskqueue);
		
		taskqueue.performNonThreaded(task, workerpool);
		
		QAUtil.sleep(500);
		Assert.assertTrue(task.getInnerthreadname().equals("TEST"));		
	}

	
	
	private void waitForCompletion() {
		do {
			QAUtil.sleep(50);
		} while (running.get() > 0);
		
		
		if (!errors.isEmpty()) {
			for (String s : errors) {
				System.out.println("Error: " + s);
			}
			
			Assert.fail("errors");
		}
	}

	
	private static final class RecursiveTask implements Runnable {
		
		private final Executor workerpool;
		private final SerializedTaskQueue taskqueue;
		
		private String innerThreadname;
		
		public RecursiveTask(Executor workerpool, SerializedTaskQueue taskqueue) {
			this.workerpool = workerpool;
			this.taskqueue = taskqueue;
		}
		
		
		public void run() {
			
			Task task = new Task(1, 100);
			taskqueue.performNonThreaded(task, workerpool);
			
			QAUtil.sleep(200);
			innerThreadname = task.getThreadname();
		}
		
		String getInnerthreadname() {
			return innerThreadname;
		}
	}
	
	
	
	private static final class Task implements Runnable {

		private int num;
		private final int waittimeMillis;
		private String threadname = null;
		private AtomicBoolean isRunning = new AtomicBoolean(false);
		
		public Task(int num, int waittimeMillis) {
			this.num = num;
			this.waittimeMillis = waittimeMillis;
		}
		
		
		public void run() {
			System.out.println("task " + num + " started");
			orderInfo.append(num);
			
			isRunning.set(true);
			int r = running.incrementAndGet();
			if (r > 1) {
				System.out.println("more than 1 thread is running");
			}
			
			threadname = Thread.currentThread().getName();
			QAUtil.sleep(waittimeMillis);

			isRunning.set(false);
			running.decrementAndGet();
			System.out.println("task " + num + " finished");
		}
		
		boolean isRunning() {
			return isRunning.get();
		}
		
		String getThreadname() {
			return threadname;
		}
		
		
		@Override
		public String toString() {
			return num + "(isRunning=" + isRunning.get() + ")";
		}
	}
	
}
