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
package org.xsocket.datagram;


import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.DataConverter;



/**
 * Endpoint implementation base 
 *
 * @author grro@xsocket.org
 */
abstract class AbstractEndpoint implements IEndpoint {
	
	private static final Logger LOG = Logger.getLogger(AbstractEndpoint.class.getName());
	
	
	private static Executor GLOBAL_WORKERPOOL = Executors.newCachedThreadPool();
	
	private static String idPrefix;
	
	
	// ids
	private static long nextId = 0; 
	private final String id;
	
	
	
	// encoding
	private String defaultEncoding = "UTF-8";

	
	// receive data handling
	private final Object readGuard = new Object();
	private final ReceiveQueue receiveQueue = new ReceiveQueue();
	private int receiveSize = -1;  



	// datagram handler
	private final IDatagramHandler datagramHandler;

	
	// worker pool
	private Executor workerPool;

	
	// statistics & jmx
	private long openTime = -1;
	private long lastTimeReceived = System.currentTimeMillis();
	private long receivedBytes = 0;


 
    static {
    	String base = null;
    	try {
    		base = InetAddress.getLocalHost().getCanonicalHostName();
    	} catch (Exception e) {
    		base = "locale";
    	}
  
    	
 		int random = 0;
   		Random rand = new Random();
   		do {
   			random = rand.nextInt();
   		} while (random < 0);
   		idPrefix = Integer.toHexString(base.hashCode()) + "." + Long.toHexString(System.currentTimeMillis()) + "." + Integer.toHexString(random);
   	}
	
	
	
	
    /**
     * constructor
     *  
     * @param useGlobalWorkerpool  true, ifglobal worker pool should be used
     * @param datagramHandler      the datagram handler
     * @param receiveSize          the receive packet size
     * @param workerPool           the workerpool to use  
     */
    AbstractEndpoint(IDatagramHandler datagramHandler, int receiveSize, Executor workerPool) {
    	this.datagramHandler = datagramHandler;
    	this.receiveSize = receiveSize;
    	this.workerPool = workerPool;
    	
    	id = idPrefix + "." + (++nextId);
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				close();
			}
		});
		
		openTime = System.currentTimeMillis();
    }

    
    
    protected static Executor getGlobalWorkerPool() {
    	return GLOBAL_WORKERPOOL;
    }
    
    
    public void close() {
    	
    }
    
    
   

	/**
	 * return the worker pool
	 *
	 * @return the worker pool
	 */
	public Executor getWorkerpool() {
		return workerPool;
	}

    
    
	/**
	 * {@inheritDoc}
	 */	
	public final void setReceiveSize(int receivePacketSize) {
		this.receiveSize = receivePacketSize;
	}

	
	/**
	 * {@inheritDoc}
	 */	
	public final int getReceiveSize() {
		return receiveSize;
	}
	
	protected final void onData(SocketAddress address, ByteBuffer data) {
		UserDatagram packet = new UserDatagram(address, data, getDefaultEncoding());
		receiveQueue.offer(packet);
			
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] datagram received: " + packet.toString());
		}
		
		lastTimeReceived = System.currentTimeMillis();
		receivedBytes += data.remaining();

		
		if (datagramHandler != null) {
			workerPool.execute(new HandlerProcessor());
		}
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public final UserDatagram receive(long timeoutMillis) throws IOException, SocketTimeoutException {
		UserDatagram datagram = null;
		
		if (getReceiveSize() <= 0) {
			throw new IOException("the receive packet size has not been set");
		}
		
		// no timeout set
		if (timeoutMillis <= 0) {
			datagram = receive();
		
			
		// timeout set
		} else {
			long start = System.currentTimeMillis();
			
			synchronized (readGuard) {
				do {
					datagram = receive();
					if (datagram != null) {
						break;
					} else {
						try {
							readGuard.wait(timeoutMillis / 10);
						} catch (InterruptedException ie) { 
							// Restore the interrupted status
							Thread.currentThread().interrupt();
						}					
					}
				} while (System.currentTimeMillis() < (start + timeoutMillis));
			}
		}
			
		if (datagram == null) {
			throw new SocketTimeoutException("timeout " + DataConverter.toFormatedDuration(timeoutMillis) + " reached");
		} else {
			return datagram;
		}	
	}
	

	public UserDatagram receive() {
		return receiveQueue.poll();
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final String getDefaultEncoding() {
		return defaultEncoding;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setDefaultEncoding(String defaultEncoding) {
		this.defaultEncoding = defaultEncoding;
	}
	
	

	/**
	 * return the id 
	 * 
	 * @return the id
	 */
	public final String getId() {
		return id;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
   		return " received=" + DataConverter.toFormatedBytesSize(receivedBytes)  
	         + ", age=" + DataConverter.toFormatedDuration(System.currentTimeMillis() - openTime)
	         + ", lastReceived=" + DataConverter.toFormatedDate(lastTimeReceived)
	         + " [" + id + "]"; 
	}
	
	
	
	
	private static final class ReceiveQueue {
		private List<UserDatagram> receiveQueue = new ArrayList<UserDatagram>();
		private int modifyVersion = 0;
	
		public synchronized void offer(UserDatagram userDatagram) {
			modifyVersion++;
			receiveQueue.add(userDatagram);
		}
		
		public synchronized UserDatagram poll() {
			if (receiveQueue.isEmpty()) {
				return null;
			} else {
				modifyVersion++;
				return receiveQueue.remove(0);
			}
		}
		
		public synchronized boolean isEmpty() {
			modifyVersion++;
			return receiveQueue.isEmpty();
		}

		
		@Override
		public String toString() {
			return receiveQueue.size() + " (modifyVersion=" + modifyVersion + ")";
		}
	}
	
	
	private final class HandlerProcessor implements Runnable {
		
		public void run() {
			
			try {
				if (!receiveQueue.isEmpty()) {
					datagramHandler.onDatagram(AbstractEndpoint.this);
				}
					
				
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onData task. Reason: " + e.toString());
				}
				
				throw new RuntimeException(e);
			}
		}
	}
}
