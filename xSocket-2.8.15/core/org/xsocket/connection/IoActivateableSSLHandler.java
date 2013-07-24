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
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;




/**
 * activateable SSL io handler
 *
 * @author grro@xsocket.org
 */
final class IoActivateableSSLHandler extends IoChainableHandler implements IoSSLProcessor.EventHandler {


	private static final Logger LOG = Logger.getLogger(IoActivateableSSLHandler.class.getName());

	private enum Mode { PLAIN, SSL, BUFFERING };
	private AtomicReference<Mode> outboundModeRef = new AtomicReference<Mode>(Mode.PLAIN);
	private AtomicReference<Mode> inboundModeRef = new AtomicReference<Mode>(Mode.PLAIN);



	// receive & send queue
	private IoQueue inNetDataQueue = new IoQueue();
	private IoQueue outNetDataQueue = new IoQueue();
	private IoQueue outAppDataQueue = new IoQueue();
	

	// sync write support
	private final PendingWriteMap pendingWriteMap = new PendingWriteMap();


	// event handling
	private final IOEventHandler ioEventHandler = new IOEventHandler();

	// ssl stuff
	private final SSLContext sslContext;
	private final boolean isClientMode;
	private final AbstractMemoryManager memoryManager;
	private final AtomicReference<IoSSLProcessor> sslProcessorRef = new AtomicReference<IoSSLProcessor>();



	/**
	 * constructor
	 *
	 * @param successor      the successor
	 * @param sslContext     the ssl context to use
	 * @param isClientMode   true, if is in client mode
	 * @param memoryManager  the memory manager to use
	 * @throws IOException If some other I/O error occurs
	 */
	IoActivateableSSLHandler(IoChainableHandler successor, SSLContext sslContext, boolean isClientMode, AbstractMemoryManager memoryManager) throws IOException {
		super(successor);

		this.sslContext = sslContext;
		this.isClientMode = isClientMode;
		this.memoryManager = memoryManager;
	}



	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(ioEventHandler);
	}

	
	@Override
	public boolean isSecure() {
		return (outboundModeRef.get() == Mode.SSL) && (inboundModeRef.get() == Mode.SSL);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean reset() {
		inNetDataQueue.drain();
		outNetDataQueue.drain();
		outAppDataQueue.drain();

		pendingWriteMap.clear();

		return super.reset();
	}



	/**
	 * {@inheritDoc}
	 */
	public void setPreviousCallback(IIoHandlerCallback callbackHandler) {
		super.setPreviousCallback(callbackHandler);
		getSuccessor().setPreviousCallback(ioEventHandler);
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




    /**
     * {@inheritDoc}
     */
    @Override
    public int getPendingWriteDataSize() {
    	return outAppDataQueue.getSize() + super.getPendingWriteDataSize();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDataToSend() {
    	return (!outAppDataQueue.isEmpty() || super.hasDataToSend());
    }

    
    
    @Override
    public void write(ByteBuffer[] buffers) throws ClosedChannelException, IOException {
    	outAppDataQueue.append(buffers);
	}
    
    @Override
    public void hardFlush() throws IOException {
    	flush();
    }

    
	/**
	 * {@inheritDoc}
	 */
	public void flush() throws IOException {
	
		Mode outboundMode = outboundModeRef.get();
			
		if (outboundMode == Mode.SSL) {
			IoSSLProcessor sslProcessor = sslProcessorRef.get();
			
			synchronized (outAppDataQueue) {
				if (!outAppDataQueue.isEmpty()) {
					sslProcessor.addOutAppData(outAppDataQueue.drain());
				} 
			}
			sslProcessor.encrypt();

		} else if (outboundMode == Mode.PLAIN) {
			synchronized (outAppDataQueue) {
				if (!outAppDataQueue.isEmpty()) {
					ByteBuffer[] data = outAppDataQueue.drain();
					getSuccessor().write(data);
				}
			}
			getSuccessor().flush();
			
		} else {
			assert (outboundMode == Mode.BUFFERING);
		} 
	}


	/**
	 * set mode to plain write and encrypted read
	 *
	 * @return true, if mode has been set
	 */
	public boolean preActivateSecuredMode() {
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] switch to prestart secured mode (interpret incomming as SSL, writing plain outgoing plain)");
		}
		
		IoSSLProcessor sslProcessor = sslProcessorRef.get();

		if (sslProcessor == null) {
			sslProcessor = new IoSSLProcessor(sslContext, isClientMode, memoryManager, this);
			sslProcessorRef.set(sslProcessor);
			
			inboundModeRef.set(Mode.SSL);
			return true;
			
		} else {
			LOG.warning("connection is already in ssl mode (" + printSSLState() + "). Ignore (pre)startSecured Mode");
			return false;
		}
	}
	
	
	private String printSSLState() {
		return "inbound: " + inboundModeRef.get() + " outbound: " + outboundModeRef.get();
	}


	/**
	 * Return already received data to ssl handler (this data
	 * will be interpreted as encrypted data). <br>
	 *
	 * @param readQueue      the queue with already received data
	 * @throws IOException if an io exception occurs
	 */
	public void activateSecuredMode(ByteBuffer[] data) throws IOException {
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] switch to secured mode (handle incomming and outgoing as SSL)");
		}

		outboundModeRef.set(Mode.BUFFERING);
		
		if ((data != null) && (data.length > 0)) {
			inNetDataQueue.addFirst(data);
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] start ssl processor");
		}
		
		IoSSLProcessor sslProcessor = sslProcessorRef.get();
		sslProcessor.start();
		
		hardFlush();
		readIncomingEncryptedData(inNetDataQueue.drain());
	}

	
	public void deactivateSecuredMode() throws IOException {
		IoSSLProcessor sslProcessor = sslProcessorRef.get();

		if (sslProcessor == null) {
			LOG.warning("connection is already in plain mode (" + printSSLState() + "). Ignore deactivate");

		} else {
			hardFlush();
			outboundModeRef.set(Mode.PLAIN);
			sslProcessorRef.get().stop();
		}

	}

	
	public void onInboundClosed() throws IOException {
		inboundModeRef.set(Mode.PLAIN);
	}



	public void onHandshakeFinished() throws IOException {
		outboundModeRef.set(Mode.SSL);
		hardFlush();
	}




	private void readIncomingEncryptedData(ByteBuffer[] inNetDataList) throws ClosedChannelException, IOException {
		if (inNetDataList != null) {
			if (LOG.isLoggable(Level.FINE)) {
				int size = 0;
				for (ByteBuffer buffer : inNetDataList) {
					size += buffer.remaining();
				}

				LOG.fine("received " + size + " bytes encrypted data");
			}

			sslProcessorRef.get().decrypt(inNetDataList);
		}
	}


	public void onDestroy() throws IOException {
		close(true);
	}

	
	/**
	 * has to be called within a synchronized context
	 */
	public void onDataDecrypted(ByteBuffer decryptedBuffer) {
		
		if ((decryptedBuffer == null) || !decryptedBuffer.hasRemaining()) {
			return;
		}

		getPreviousCallback().onData(new ByteBuffer[] { decryptedBuffer }, decryptedBuffer.remaining());
	}

	
	public void onPostDataDecrypted() {
		getPreviousCallback().onPostData();		
	}
	

	public void onDataEncrypted(ByteBuffer plainData, ByteBuffer encryptedData) throws IOException {
		
		if (encryptedData.hasRemaining()) {
			pendingWriteMap.add(plainData, encryptedData);
		}
		
		synchronized (outNetDataQueue) {
			outNetDataQueue.append(encryptedData);
		}
	}

	
	
	public void onPostDataEncrypted() throws IOException {
		
		synchronized (outNetDataQueue) {
			ByteBuffer[] data = outNetDataQueue.drain();
			
			if (LOG.isLoggable(Level.FINE)) {
				if (data != null) {
					int size = 0;
					for (ByteBuffer buffer : data) {
						size += buffer.remaining();
					}
					
					LOG.fine("sending out app data (" + size + ")");
				}
			}

			getSuccessor().write(data);
		}
		getSuccessor().flush();
	}
	
	


	private final class IOEventHandler implements IIoHandlerCallback {


		public void onData(ByteBuffer[] data, int size) {
			try {

				Mode inboundMode = inboundModeRef.get();
				
				if (inboundMode == Mode.PLAIN) {
					getPreviousCallback().onData(data, size);
					getPreviousCallback().onPostData();

				} else if (inboundMode == Mode.SSL) {				
					readIncomingEncryptedData(data);

				} else {
					assert (inboundMode == Mode.BUFFERING);
					inNetDataQueue.append(data);
					return;
				}
			} catch (IOException e) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + e.toString());
	 			}
			}
		}
		
		public void onPostData() {
			
		}

		public void onConnect() {
			getPreviousCallback().onConnect();
		}
		
		public void onConnectException(IOException ioe) {
		    getPreviousCallback().onConnectException(ioe);		    
		}

		public void onWriteException(IOException ioException, ByteBuffer data) {
			getPreviousCallback().onWriteException(ioException, data);
		}
		


		public void onWritten(ByteBuffer data) {
			ByteBuffer plainData = pendingWriteMap.getPlainIfWritten(data);
			if (plainData != null) {
				getPreviousCallback().onWritten(plainData);
			} else{
				getPreviousCallback().onWritten(data);
			}
		}

		public void onDisconnect() {
			//getSSLProcessor().destroy();
			getPreviousCallback().onDisconnect();
		}

		public void onConnectionAbnormalTerminated() {
			getPreviousCallback().onConnectionAbnormalTerminated();
		}
	}
	
	private static final class PendingWriteMap {
		private Map<ByteBuffer, List<ByteBuffer>> plainEncryptedMapping = new IdentityHashMap<ByteBuffer, List<ByteBuffer>>();
		private Map<ByteBuffer, ByteBuffer> encryptedPlainMapping = new IdentityHashMap<ByteBuffer, ByteBuffer>();


		public synchronized void add(ByteBuffer plain, ByteBuffer encrypted) {
			// ignore system data (plain is empty)
			if (plain.limit() > 0) {
				List<ByteBuffer> encryptedList = plainEncryptedMapping.get(plain);
				if (encryptedList == null) {
					encryptedList = new ArrayList<ByteBuffer>();
					plainEncryptedMapping.put(plain, encryptedList);
				}

				encryptedList.add(encrypted);
				encryptedPlainMapping.put(encrypted, plain);
			}
		}


		public synchronized ByteBuffer getPlainIfWritten(ByteBuffer encrypted) {
			ByteBuffer plain = encryptedPlainMapping.remove(encrypted);
			if (plain != null) {
				List<ByteBuffer> encryptedList = plainEncryptedMapping.get(plain);
				encryptedList.remove(encrypted);
				if (encryptedList.isEmpty()) {
					plainEncryptedMapping.remove(plain);
					return plain;
				}
			}
			return null;
		}

		public synchronized void clear() {
			plainEncryptedMapping.clear();
			encryptedPlainMapping.clear();
		}
	}	
}
