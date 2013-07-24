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


import java.util.concurrent.RejectedExecutionException;

import junit.framework.Assert;

import org.junit.Test;




/**
*
* @author grro@xsocket.org
*/
public final class WorkerPoolTest {

	

	@Test 
	public void testSimple() throws Exception {
	    
	    WorkerPool wp = new WorkerPool(0, 4);

	    for (int i = 0; i < 10; i++) {
	        wp.execute(new Worker(0));
	        QAUtil.sleep(200);
	    }
	    
	    Assert.assertEquals(1, wp.getLargestPoolSize());
	}
	
	
	
	@Test 
    public void testQueueSizeExceeded() throws Exception {
        
        WorkerPool wp = new WorkerPool(1, 2, 2);
        
        wp.execute(new Worker(1000000));
        QAUtil.sleep(200);
        
        Assert.assertEquals(1, wp.getActiveCount());
        Assert.assertEquals(1, wp.getLargestPoolSize());
        Assert.assertEquals(0, wp.getQueue().size());

        
        wp.execute(new Worker(1000));
        QAUtil.sleep(200);

        Assert.assertEquals(2, wp.getActiveCount());
        Assert.assertEquals(2, wp.getLargestPoolSize());
        Assert.assertEquals(0, wp.getQueue().size());
        

        wp.execute(new Worker(1000));
        QAUtil.sleep(200);

        Assert.assertEquals(2, wp.getActiveCount());
        Assert.assertEquals(2, wp.getLargestPoolSize());
        Assert.assertEquals(1, wp.getQueue().size());

        
        wp.execute(new Worker(1000));
        QAUtil.sleep(200);

        Assert.assertEquals(2, wp.getActiveCount());
        Assert.assertEquals(2, wp.getLargestPoolSize());
        Assert.assertEquals(2, wp.getQueue().size());        
        
        
        try {
            wp.execute(new Worker(1000000));
            Assert.fail("RejectedExecutionException expected");
        } catch (RejectedExecutionException expected) { }
       
        
        QAUtil.sleep(2000);
        
        wp.execute(new Worker(1000000));
        QAUtil.sleep(250);

        Assert.assertEquals(2, wp.getActiveCount());
        Assert.assertEquals(2, wp.getLargestPoolSize());
        Assert.assertEquals(1, wp.getQueue().size());                
    }
    
	
	
	
	
	private static final class Worker implements Runnable {
	    
	    private int sleepTimeMillis;
	    
	    public Worker(int sleepTimeMillis) {
	        this.sleepTimeMillis = sleepTimeMillis;
        }
	    
	    public void run() {
	        System.out.println("worker is running");
	        QAUtil.sleep(sleepTimeMillis);
	        System.out.println("worker is finished");
	    }
	}
}
