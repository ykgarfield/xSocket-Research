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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;


import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.QAUtil;
import org.xsocket.connection.INonBlockingConnection;


/**
*
* @author grro@xsocket.org
*/
public final class MultipleConnectionAsyncTest {

	private static final Logger LOG = Logger.getLogger(MultipleConnectionAsyncTest.class.getName());

	static final String ipPrefix = "10.2.0.";



	@Ignore
	@Test
	public void testAsync() throws Exception {
		
		// in test environment a service runs on 10.2.0.175:9988 only 
		
		List<AsyncWorker> workers = new ArrayList<AsyncWorker>();
		
		for(int i = 1; i < 190; i++) {
			AsyncWorker worker = new AsyncWorker(i);
			workers.add(worker);
			worker.start();
		}
		
		
		QAUtil.sleep(3000);
		
		int numRunning = 0;
		for (AsyncWorker worker : workers) {
			if ((worker.getLastIPSegment() == 175) && (!worker.isConnected())) {
				Assert.fail("ip 10.2.0.175 should be connected which is not");
			}

			if (worker.isRunning()) {
				numRunning++;
			}
		}
		
		Assert.assertEquals(numRunning + " are still running", 0, numRunning);
	}
		
	
	

	private static final class AsyncWorker extends Thread {
		
		private final AtomicBoolean isRunning = new AtomicBoolean(true);
		private final AtomicBoolean isConnected = new AtomicBoolean(false);

		private final int i;
		
		
		public AsyncWorker(int i) {
			this.i = i;
		}
		
        public void run(){
        	InetAddress ia = null;
            try{
            	long start = System.currentTimeMillis();
            	ia = InetAddress.getByName(ipPrefix + i);
            	LOG.info("try to connect " + ia);
            	
                NonBlockingConnection conn = new NonBlockingConnection(ia, 9988, new ConnectHandler(isConnected, start, ia), false, 5000000);
                
            } catch (Exception e) {
            	LOG.info("Connection to '" + ia + "' failed " + e.toString());
            }
            
            isRunning.set(false);
        }
        
        
        boolean isRunning() {
        	return isRunning.get();
        }
        
        boolean isConnected() {
        	return isConnected.get();
        }
        
        int getLastIPSegment() {
        	return i;
        }
    }	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class ConnectHandler implements IConnectHandler, IConnectExceptionHandler {
		
		private final AtomicBoolean isConnected;
		private final long start;
		private final InetAddress ia;
		
		public ConnectHandler(AtomicBoolean isConnected, long start, InetAddress ia) {
			this.isConnected = isConnected;
			this.start = start;
			this.ia = ia;
		}
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
            long elapsed = System.currentTimeMillis() - start;
            isConnected.set(true);
            
            LOG.info("Connection to '" + ia + "' established! (elapsed " + elapsed + ")");
			return true;
		}
		
		public boolean onConnectException(INonBlockingConnection connection,IOException ioe) throws IOException {
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("Connection to '" + ia + "' failed! (elapsed " + elapsed + ") "  + ioe);

			return true;
		}
	}
}
