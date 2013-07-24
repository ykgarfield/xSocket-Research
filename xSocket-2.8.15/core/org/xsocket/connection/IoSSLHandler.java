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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;




/**
 * SSL io handler
 *
 * @author grro@xsocket.org
 */
final class IoSSLHandler extends IoChainableHandler implements IoSSLProcessor.EventHandler {

	private static final Logger LOG = Logger.getLogger(IoSSLHandler.class.getName());

	// receive & send queue
	private final IoQueue outAppDataQueue = new IoQueue();
	private final IoQueue outNetDataQueue = new IoQueue();


	// sync write support
	private final PendingWriteMap pendingWriteMap = new PendingWriteMap();



	private final IOEventHandler ioEventHandler = new IOEventHandler();


	// ssl stuff
	private final IoSSLProcessor sslProcessor;
	private final AtomicBoolean isSSLConnected = new AtomicBoolean(false);
	private final Object initGuard = new Object();
    private final boolean isClientMode;

	
	private IOException readException;



	/**
	 * constructor
	 *
	 * @param successor      the successor
	 * @param sslContext     the ssl context to use
	 * @param isClientMode   true, if is in client mode
	 * @param memoryManager  the memory manager to use
	 * @throws IOException If some other I/O error occurs
	 */
	IoSSLHandler(IoChainableHandler successor, SSLContext sslContext,boolean isClientMode, AbstractMemoryManager memoryManager) throws IOException {
		super(successor);

		this.isClientMode = isClientMode;
		sslProcessor = new IoSSLProcessor(sslContext, isClientMode, memoryManager, this);
	}


	public void init(IIoHandlerCallback callbackHandler) throws IOException {
		setPreviousCallback(callbackHandler);
		getSuccessor().init(ioEventHandler);

		startSSL();
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean reset() {
		outAppDataQueue.drain();
		outNetDataQueue.drain();
		pendingWriteMap.clear();

		return super.reset();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public boolean isSecure() {
		return isSSLConnected.get();
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



	/**
	 * start the ssl mode
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	void startSSL() throws IOException {
		if (!isSSLConnected.get()) {
			sslProcessor.start();
		}

		if (isClientMode) {
			synchronized (initGuard) {
				
				while (!isSSLConnected.get()) {
					if (readException != null) {
						IOException ex = readException;
						readException = null;
						throw ex;
					}
					
					try {
						if (ConnectionUtils.isDispatcherThread()) {
							LOG.warning("try to initialize ssl client within xSocket I/O thread (" + Thread.currentThread().getName() + "). This will cause a deadlock");
						}
						
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] waiting until ssl handeshake has been finished");
						}
						
						initGuard.wait();
						
						
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + getId() + "] ssl handeshake has been finished continue processing");
						}
					} catch (InterruptedException ie) { 
						// Restore the interrupted status
						Thread.currentThread().interrupt();
					}
				}
			}
		}
	}

	
	public void onHandshakeFinished() throws IOException {
		if (!isSSLConnected.get()) {
			if (LOG.isLoggable(Level.FINE) && (isClientMode)) {
				LOG.fine("[" + getId() + "] wakeup waiting processes for handeshake");
			}

			isSSLConnected.set(true);
			synchronized (initGuard) {
				initGuard.notifyAll();
			}

			getPreviousCallback().onConnect();
		}
		
		boolean isEncryptRequired = false;
		synchronized (outAppDataQueue) {
			if (!outAppDataQueue.isEmpty()) {
				sslProcessor.addOutAppData(outAppDataQueue.drain());
				isEncryptRequired = true;
			}
		}
		
		if (isEncryptRequired) {
			sslProcessor.encrypt();
		}
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
		outAppDataQueue.append(buffers);
		flush();
	}
	
	@Override
	public void flush() throws IOException {
		
		synchronized (outAppDataQueue) {
			if (!outAppDataQueue.isEmpty()) {
				ByteBuffer[] dataToEncrypt = outAppDataQueue.drain();
				
				if (LOG.isLoggable(Level.FINE)) {
					int size = 0;
					List<ByteBuffer> dataToEncryptCopy = new ArrayList<ByteBuffer>();
					for (ByteBuffer buffer : dataToEncrypt) {
						dataToEncryptCopy.add(buffer.duplicate());
						size += buffer.remaining();
					}
					
					LOG.fine("encrypting out app data (" + size + "): " + DataConverter.toTextOrHexString(dataToEncryptCopy.toArray(new ByteBuffer[dataToEncryptCopy.size()]), "US-ASCII", 500));
				}
				
				sslProcessor.addOutAppData(dataToEncrypt);
			}
		}
		
		sslProcessor.encrypt();
	}


	/**
	 * {@inheritDoc}
	 */
	public void hardFlush() throws IOException {
		flush();
	}



	private void readIncomingEncryptedData(ByteBuffer[] inNetDataList) throws ClosedChannelException, IOException {
	
		if (LOG.isLoggable(Level.FINE)) {
			int size = 0;
			for (ByteBuffer buffer : inNetDataList) {
				size += buffer.remaining();
			}
			LOG.fine("[" + getId() + "] " + size + " encrypted data received");
		}
		
		if (inNetDataList != null) {
			sslProcessor.decrypt(inNetDataList);
		}
	}



	public void onDestroy() throws IOException {
		close(true);
	}
	
	public void onInboundClosed() throws IOException {
		close(true);
	}

	
	/**
	 * has to be called within a synchronized context
	 */
	public void onDataDecrypted(ByteBuffer decryptedBuffer) {
		
		
		if ((decryptedBuffer == null) || !decryptedBuffer.hasRemaining()) {
			return;
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("in app data decrypted: " + DataConverter.toTextOrHexString(decryptedBuffer.duplicate(), "US-ASCII", 500));
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
				readIncomingEncryptedData(data);
			} catch (IOException ioe) {
	 			if (LOG.isLoggable(Level.FINE)) {
	 				LOG.fine("[" + getId() + "] error occured while receiving data. Reason: " + ioe.toString());
	 			}

	 			synchronized (initGuard) {
					readException = ioe;
					initGuard.notifyAll();
				}
			}
		}
		
		public void onPostData() {
			
		}


		public void onConnect() {

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
			} else {
				// else case shouldn't occur, handle it nevertheless
				getPreviousCallback().onWritten(data);
			}
		}

		public void onDisconnect() {
			sslProcessor.destroy();
			getPreviousCallback().onDisconnect();
		}

		public void onConnectionAbnormalTerminated() {
			getPreviousCallback().onConnectionAbnormalTerminated();
		}
	}



	 static final class PendingWriteMap {

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
