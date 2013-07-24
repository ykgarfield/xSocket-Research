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
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;



/**
 * Delayed write IO handler
 *
 * @author grro@xsocket.org
 */
final class IoThrottledWriteHandler extends IoChainableHandler {

	private static final Logger LOG = Logger.getLogger(IoThrottledWriteHandler.class.getName());

	private static final int PERIOD_MILLIS = 500; 

	// write queue
	private final IoQueue writeQueue = new IoQueue();
	private final ArrayList<ByteBuffer> throttledSendQueue = new ArrayList<ByteBuffer>(1);


	// timer handling
	private int writeSize = Integer.MAX_VALUE; 
	private TimerTask delayedDelivererTask;



	/**
	 * constructor
	 * @param successor  the successor
	 */
	IoThrottledWriteHandler(IoChainableHandler successor) {
		super(successor);
	}



	/**
	 * {@inheritDoc}
	 */
	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(callbackHandler);
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean reset() {
		throttledSendQueue.clear();

		writeSize = Integer.MAX_VALUE;
		
		if (delayedDelivererTask != null) {
			delayedDelivererTask.cancel();
			delayedDelivererTask = null;
		}

		return super.reset();
	}


	/**
	 * set the write rate in sec
	 *
	 * @param writeRateSec  the write rate
	 */
	void setWriteRateSec(int writeRateSec) {
		
		writeSize = (PERIOD_MILLIS * writeRateSec) / 1000;
		if (writeSize <= 0) {
			writeSize = 1;
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("write transfer rate set to " + writeRateSec);
		}
	}




    /**
     * {@inheritDoc}
     */
    @Override
    public int getPendingWriteDataSize() {
    	return getSendQueueSize() + super.getPendingWriteDataSize();
    }

    @Override
    public boolean hasDataToSend() {
    	return ((getSendQueueSize() > 0) || super.hasDataToSend());
    }


    @SuppressWarnings("unchecked")
	private int getSendQueueSize() {
    	int size = 0;

    	ArrayList<ByteBuffer> copy = null;
    	synchronized (throttledSendQueue) {
    		copy = (ArrayList<ByteBuffer>) throttledSendQueue.clone();
		}

    	for (ByteBuffer buffer : copy) {
			size += buffer.remaining();
		}

    	return size;
    }


	/**
	 * {@inheritDoc}
	 */
	public void close(boolean immediate) throws IOException {
		if (!immediate) {
			hardFlush();
		}

		getSuccessor().close(immediate);
	}


	@Override
	public void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException {
		writeQueue.append(buffers);
	}
	
	
	@Override
	public void flush() throws IOException {

		synchronized (writeQueue) {
			for (ByteBuffer buffer : writeQueue.drain()) {
				writeOutgoing(buffer);
			}
		}
	}
	
	

	private void writeOutgoing(ByteBuffer buffer) {

		// append to delay queue
		int size = buffer.remaining();
		if (size > 0) {

 			if (LOG.isLoggable(Level.FINE)) {
 				LOG.fine("[" + getId() + "] add buffer (" + buffer.remaining() + " bytes) to delay queue");
 			}
	 		synchronized (throttledSendQueue) {
	 			throttledSendQueue.add(buffer);
	 		}
		}

		// create delivery task if not exists
		if (delayedDelivererTask == null) {
 			if (LOG.isLoggable(Level.FINE)) {
 				LOG.fine("[" + getId() + "] delay delivery task is null. Starting task (period=" + DataConverter.toFormatedDuration(PERIOD_MILLIS) + ")");
 			}
 			
			delayedDelivererTask = new DeliveryTask(this);
			IoProvider.getTimer().schedule(delayedDelivererTask, 0, PERIOD_MILLIS);
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public void hardFlush() throws IOException {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("flush all remaning data (" + getSendQueueSize() + ")");
		}
		
		write();


		synchronized (throttledSendQueue) {
			if (!throttledSendQueue.isEmpty()) {
				ByteBuffer[] entries = throttledSendQueue.toArray(new ByteBuffer[throttledSendQueue.size()]);
				throttledSendQueue.clear();

				ByteBuffer[] buffers = new ByteBuffer[entries.length];
				for (int i = 0; i < buffers.length; i++) {
					buffers[i] = entries[i];
				}

	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] flushing " + buffers.length + " buffers of delay queue");
	 			}

				try {
					IoThrottledWriteHandler.this.getSuccessor().write(buffers);
					IoThrottledWriteHandler.this.getSuccessor().flush();
				} catch (Exception e) {
				    
		 			if (LOG.isLoggable(Level.FINE)) {
		 				LOG.fine("[" + getId() + "] error occured while writing. Reason: " + e.toString());
		 			}
		 			getSuccessor().close(true);
				}
			}
		}

		getSuccessor().hardFlush();
	}


	
	private static final class DeliveryTask extends TimerTask {

		private WeakReference<IoThrottledWriteHandler> ioThrottledWriteHandlerRef = null;
		
		public DeliveryTask(IoThrottledWriteHandler ioThrottledWriteHandler) {
			ioThrottledWriteHandlerRef = new WeakReference<IoThrottledWriteHandler>(ioThrottledWriteHandler);
		}
		
		
		@Override
		public void run() {
			
			IoThrottledWriteHandler ioThrottledWriteHandler = ioThrottledWriteHandlerRef.get();
			
			if (ioThrottledWriteHandler == null) {
				cancel();
				
			} else  {
				ioThrottledWriteHandler.writeChunk();
			}
		}
	}
	
	
	void writeChunk() {
		
		int sizeToWrite = writeSize; 
		
		try {
			synchronized (throttledSendQueue) {

				while ((sizeToWrite > 0) && (!throttledSendQueue.isEmpty())) {
					ByteBuffer buffer = throttledSendQueue.remove(0);
					
					if (buffer.remaining() > sizeToWrite) {
						int saveLimit = buffer.limit();
						buffer.limit(sizeToWrite);
						
						ByteBuffer newBuffer = buffer.slice();
						buffer.position(buffer.limit());
						buffer.limit(saveLimit);
						throttledSendQueue.add(0, buffer.slice());
						
						buffer = newBuffer;
					} 
						
					sizeToWrite -= buffer.remaining();
						
					if (LOG.isLoggable(Level.FINE)) {
		 				LOG.fine("[" + getId() + "] release " + buffer.remaining() + " bytes from delay queue (remaining size = " + getSendQueueSize() + ")");
		 			}

					getSuccessor().write(new ByteBuffer[] { buffer });
					getSuccessor().flush();
				}			
			}
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] error occured by writing queue data " + DataConverter.toString(ioe));
			}
		}
	}
	



	/**
	 * {@inheritDoc}
	 */
   	@Override
	public String toString() {
   		return this.getClass().getSimpleName() + "(pending delayQueueSize=" + DataConverter.toFormatedBytesSize(getPendingWriteDataSize()) + ") ->" + "\r\n" + getSuccessor().toString();
	}
}
