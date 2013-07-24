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
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.xsocket.DataConverter;




/**
 * ssl processor
 * 
 * @author grro@xsocket.org
 */
final class IoSSLProcessor {
	
	private static final Logger LOG = Logger.getLogger(IoSSLProcessor.class.getName());
	
	private final static ByteBuffer NULL_BYTE_BUFFER = ByteBuffer.allocate(0);
	
	private final static ExecutorService WORKERPOOL = Executors.newCachedThreadPool(new DefaultThreadFactory());
	
	private final SSLEngine sslEngine;
	
	private final boolean isClientMode;
	private final AbstractMemoryManager memoryManager;
	private final EventHandler eventHandler;

	// buffer size
	private int minNetBufferSize;
	private int minEncryptedBufferSize;
	
	private ByteBuffer unprocessedInNetData = NULL_BYTE_BUFFER; 
	private final Object unprocessedInNetDataGuard = new Object();

	private final LinkedList<ByteBuffer> outAppDataList = new LinkedList<ByteBuffer>(); 
	
	private AtomicBoolean isOutboundClosed = new AtomicBoolean();
	private AtomicBoolean isInboundClosed = new AtomicBoolean();
	
	
	/**
	 * constructor 
	 * 
	 * @param sslContext    the ssl context
	 * @param isClientMode  true, is ssl processor runs in client mode
	 */
	IoSSLProcessor(SSLContext sslContext, boolean isClientMode, AbstractMemoryManager memoryManager, EventHandler eventHandler) {
		this.isClientMode = isClientMode;
		this.memoryManager = memoryManager;
		this.eventHandler = eventHandler;
		
		sslEngine = sslContext.createSSLEngine();

		if (isClientMode) {
			if (IoProvider.getSSLEngineClientEnabledCipherSuites() != null) {
				sslEngine.setEnabledCipherSuites(IoProvider.getSSLEngineClientEnabledCipherSuites());
			}
			
			if (IoProvider.getSSLEngineClientEnabledProtocols() != null) {
				sslEngine.setEnabledProtocols(IoProvider.getSSLEngineClientEnabledProtocols());
			}
			
			
		} else {
			if (IoProvider.getSSLEngineServerEnabledCipherSuites() != null) {
				sslEngine.setEnabledCipherSuites(IoProvider.getSSLEngineServerEnabledCipherSuites());
			}
			
			if (IoProvider.getSSLEngineServerEnabledProtocols() != null) {
				sslEngine.setEnabledProtocols(IoProvider.getSSLEngineServerEnabledProtocols());
			}

			
			if (IoProvider.getSSLEngineServerWantClientAuth() != null) {
				sslEngine.setWantClientAuth(IoProvider.getSSLEngineServerWantClientAuth());
			}
			
			if (IoProvider.getSSLEngineServerNeedClientAuth() != null) {
				sslEngine.setNeedClientAuth(IoProvider.getSSLEngineServerNeedClientAuth());
			}
		}

		
		
	
		minEncryptedBufferSize = sslEngine.getSession().getApplicationBufferSize();
		minNetBufferSize = sslEngine.getSession().getPacketBufferSize();
		
		if (LOG.isLoggable(Level.FINE)) {
			if (isClientMode) {
				LOG.fine("initializing ssl processor (client mode)");
			} else {
				LOG.fine("initializing ssl processor (server mode)");
			}
			LOG.fine("app buffer size is " + minEncryptedBufferSize);
			LOG.fine("packet buffer size is " + minNetBufferSize);
		}
		
		
		sslEngine.setUseClientMode(isClientMode);
	}
	
	
	
	/**
	 * start ssl processing 
	 * 
	 * @param  inNetData  already received net data
	 * @throws IOException If some other I/O error occurs
	 */
	void start() throws IOException {
		
		if (LOG.isLoggable(Level.FINE)) {
			if (isClientMode) {
				LOG.fine("calling sslEngine beginHandshake and calling encncrypt to initiate handeshake (client mode)");
			} else {
				LOG.fine("calling sslEngine beginHandshake (server mode)");
			}
		}
		
		try {
			sslEngine.beginHandshake();
		} catch (SSLException sslEx) {
			throw new RuntimeException(sslEx);
		}

		
		if (isClientMode) {
 			encrypt();
		}
	}
	
	
	void stop() throws IOException {
		sslEngine.closeOutbound();
		wrap();
	}


	/**
	 * destroy this instance
	 *
	 */
	void destroy() {
		try {
			eventHandler.onDestroy();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by calling ssl processor closed caal back on " + eventHandler + " reason: " + DataConverter.toString(ioe));
			}
		}
	}
	

	/**
	 * decrypt  data
	 *  
	 * @param encryptedBufferList   the encrypted data 
	 * @throws IOException if an io exction occurs 
	 * @throws ClosedChannelException if the connection is already closed
	 */
	void decrypt(ByteBuffer[] encryptedBufferList) throws IOException, ClosedChannelException {
		
		try {
			for (int i = 0; i < encryptedBufferList.length; i++) {
				synchronized (unprocessedInNetDataGuard) {
					unprocessedInNetData = mergeBuffer(unprocessedInNetData, encryptedBufferList[i]);
				}
				
				if (!isInboundClosed.get()) {
					unwrap();
				} else {
					ByteBuffer data;
					synchronized (unprocessedInNetDataGuard) {
						data = unprocessedInNetData;
						unprocessedInNetData = NULL_BYTE_BUFFER;
					}	
					eventHandler.onDataDecrypted(data);
				}
			}

			
		} catch (SSLException sslEx) {
			destroy();
			throw sslEx;
		}		
	}
	
	

	
	/**
	 * return true, if the connection is handshaking
	 * 
	 * @return true, if the connection is handshaking
	 */
	boolean isHandshaking() {
		synchronized (sslEngine) {
			HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
			return !(handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) 
				 || (handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED);
		}
	}
	
	
	
	
	/**
	 * add out app data
	 *  
	 * @param plainBuffer  the plain data to encrypt 
	 */
	void addOutAppData(ByteBuffer[] plainBufferList) throws ClosedChannelException, IOException {

		if (plainBufferList != null) {
			for (ByteBuffer buffer : plainBufferList) {
				synchronized (outAppDataList) {
					outAppDataList.add(buffer);
				}
			}
		}
	}


	/**
	 * encrypt data
	 *  
	 * @throws IOException if an io exction occurs  
	 * @throws ClosedChannelException if the connection is already closed
	 */	
	void encrypt() throws ClosedChannelException, IOException {
		try {
			wrap();
		} catch (IOException sslEx) {
			destroy();
		}
	}

	
	private void unwrap() throws SSLException, ClosedChannelException, IOException {
				
		synchronized (unprocessedInNetDataGuard) {
			if (unprocessedInNetData.remaining() == 0) {
				return;
			}
		}
		
		int minAppSize = minEncryptedBufferSize;
		boolean isDataDecrypted = false;
		boolean needUnwrap = true;
		boolean needWrap = false;
		boolean nodifyHandshakeFinished = false;
		
		while (needUnwrap) {
			needUnwrap = false;
			
			// perform unwrap
			ByteBuffer inAppData = memoryManager.acquireMemoryMinSize(minAppSize);
			
			synchronized (sslEngine) {
				
				SSLEngineResult engineResult = null;
				synchronized (unprocessedInNetDataGuard) {
					engineResult = sslEngine.unwrap(unprocessedInNetData, inAppData);
				}

				
				if (LOG.isLoggable(Level.FINE)) {
					
					if ((inAppData.position() == 0) && (engineResult.bytesConsumed() > 0)) {
						LOG.fine("incoming ssl system message (size " + engineResult.bytesConsumed() + ") encrypted by ssl engine");
					}
					
					
					if (unprocessedInNetData.remaining() > 0) {
						LOG.fine("remaining not decrypted incoming net data (" + unprocessedInNetData.remaining() + ")");
					}
					
					if (inAppData.position() > 0) {
						LOG.fine("incoming app data (size " + inAppData.position() + ") encrypted by ssl engine");
					}
				}
				
				if ((unprocessedInNetData.remaining() > 0) && (engineResult.bytesConsumed() > 0)) {
					needUnwrap = true;
				}
				

		        SSLEngineResult.Status status = engineResult.getStatus();
				
				switch (status) {
				
					case BUFFER_UNDERFLOW:
						needUnwrap = false;
						memoryManager.recycleMemory(inAppData);
						
						/*
						 * There is not enough data on the input buffer to perform the operation. The application should read
						 * more data from the network. If the input buffer does not contain a full packet BufferUnderflow occurs
						 * (unwrap() can only operate on full packets)
						 */
						
						if (LOG.isLoggable(Level.FINEST)) {
							LOG.finest("BufferUnderflow occured (not enough InNet data)");
						}	

						break;

						
					case CLOSED:
						needUnwrap = false; 
						
						isInboundClosed.set(true);
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("ssl engine inbound closed");
						}
						memoryManager.recycleMemory(inAppData);

						ByteBuffer data;
						synchronized (unprocessedInNetDataGuard) {
							data = unprocessedInNetData;
							unprocessedInNetData = NULL_BYTE_BUFFER;
						}
						
						eventHandler.onInboundClosed();
						
						if (data.remaining() > 0) {
							isDataDecrypted = true;
							eventHandler.onDataDecrypted(data);
						}
						break;

						
					case BUFFER_OVERFLOW:
						needUnwrap = true;
						memoryManager.recycleMemory(inAppData);
						
						// expanding the min buffer size
						minAppSize += minAppSize;

						break;

						
					case OK:
						
						// extract unwrapped app data
						inAppData = memoryManager.extractAndRecycleMemory(inAppData, engineResult.bytesProduced());
						
						if (inAppData.hasRemaining()) {
							isDataDecrypted = true;
							eventHandler.onDataDecrypted(inAppData);
						}
						
						HandshakeStatus handshakeStatus = engineResult.getHandshakeStatus();
						
						switch (handshakeStatus) {

							case NOT_HANDSHAKING:
								break;
						
								
							case NEED_UNWRAP:
								needUnwrap = true;
								break;
								
								
								
							case NEED_WRAP:
								needWrap = true;
								break;

								
							case NEED_TASK:
								needUnwrap = true;
								
								Runnable task = null;
								while ((task = sslEngine.getDelegatedTask()) != null) {
								    task.run();
								}
								
								break;
							
								
							case FINISHED:
								needUnwrap = true;
								nodifyHandshakeFinished = true;
								
								break;			
								
							default:
								needUnwrap = false;
								break;

						}
						break;
				}
			} // synchronized(sslEngine)
			
			

			
			if (isDataDecrypted) {
				eventHandler.onPostDataDecrypted();
			}
			
			boolean outAppDataListEmpty = false;
			synchronized (outAppDataList) {
				outAppDataListEmpty = outAppDataList.isEmpty();
			}

			if (!outAppDataListEmpty) {
				needWrap = true;
			}
			
			if (needWrap) {
				wrap();
			}
			
			if (nodifyHandshakeFinished) {
				notifyHandshakeFinished();
			}
			
		} // while(needUnwrap)		
	}
	
	
	private void wrap() throws SSLException, ClosedChannelException, IOException {
		
		int minNetSize = minNetBufferSize;
		boolean isDataEncrypted = false;
		boolean needWrap = true;
		boolean needUnwrap = false;
		boolean nodifyHandshakeFinished = false;
		
		
		while (needWrap) {
			needWrap = false;

			ByteBuffer outNetData = memoryManager.acquireMemoryMinSize(minNetSize);

			
			synchronized (sslEngine) {
	
				ByteBuffer outAppData = null;
				synchronized (outAppDataList) {
					if (outAppDataList.isEmpty()) {
						outAppData = ByteBuffer.allocate(0);
					} else {
						outAppData = outAppDataList.remove(0);
					}
				}
				
				int sizeToEncrypt = outAppData.remaining();

				
				SSLEngineResult engineResult = sslEngine.wrap(outAppData, outNetData);

				
				if (LOG.isLoggable(Level.FINE)) {
					if ((sizeToEncrypt == 0) && (engineResult.bytesProduced() > 0)) {
						LOG.fine("outgoing ssl system message (size " + engineResult.bytesProduced() + ") created by ssl engine");
						
					} else if ((sizeToEncrypt > 0)) {
						LOG.fine("outgoing app data (size " + sizeToEncrypt + ") encrypted (encrypted size " + outNetData.position() + ")");
						
					} 
					
					if (outAppData.remaining() > 0) {
						LOG.fine("remaining not encrypted outgoing app data (" + outAppData.remaining() + ")");
					}					
				}
				
				
				if ((outAppData.remaining() > 0) && (engineResult.bytesConsumed() > 0)) {
					needWrap = true;
				}
				
				
				if (outAppData.hasRemaining()) {
					synchronized (outAppDataList) {
						outAppDataList.addFirst(outAppData);
					}
				}	
				
				if ((outAppDataList.size() > 0) && (engineResult.bytesConsumed() > 0)) {
					needWrap = true;
				}
				
				
		        SSLEngineResult.Status status = engineResult.getStatus();
				
				switch (status) {
				
					case BUFFER_UNDERFLOW:
						needWrap = false;
						outNetData = memoryManager.extractAndRecycleMemory(outNetData, engineResult.bytesProduced());
						
						if (outNetData.hasRemaining()) {
							isDataEncrypted = true;
							eventHandler.onDataEncrypted(outAppData, outNetData);
						}

						break;
				
						
					case CLOSED:
						isOutboundClosed.set(true);
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("ssl engine outbound closed");
						}
						outNetData = memoryManager.extractAndRecycleMemory(outNetData, engineResult.bytesProduced());
						if (outNetData.hasRemaining()) {
							isDataEncrypted = true;
							eventHandler.onDataEncrypted(outAppData, outNetData);
						}
						
						memoryManager.recycleMemory(outNetData);
						break;

						
					case BUFFER_OVERFLOW:
						needWrap = true;
						memoryManager.recycleMemory(outNetData);
						
						// expanding the min buffer size
						minNetSize += minNetSize;

						break;						


					case OK:
						outNetData = memoryManager.extractAndRecycleMemory(outNetData, engineResult.bytesProduced());
						if (outNetData.hasRemaining()) {
							isDataEncrypted = true;
							eventHandler.onDataEncrypted(outAppData, outNetData);
						}
						
						HandshakeStatus handshakeStatus = engineResult.getHandshakeStatus();
						
						switch (handshakeStatus) {

							case NOT_HANDSHAKING:
								break;
						
								
							case NEED_UNWRAP:
								needUnwrap = true;
								break;
								
								
								
							case NEED_WRAP:
								needWrap = true;
								break;
	
								
							case NEED_TASK:
								needWrap = true;
								
								Runnable task = null;
								while ((task = sslEngine.getDelegatedTask()) != null) {
								    task.run();
								}
								
								break;
							
								
							case FINISHED:
								needWrap = true;
								nodifyHandshakeFinished = true;
								
								break;			
								
							default:
								needWrap = false;
								break;

						}
						break;					
				
				}
			}  // synchronized(sslEngine)
			
			

			if (needUnwrap) {
				
				// call unwrap by a dedicated worker thread to prevent 
				// unwrap -> wrap -> unwrap -> ... loops
				Runnable unwrapTask = new Runnable() {
					
					public void run() {
						try {
							unwrap();
						} catch (IOException e) {
							destroy();
						}
					}
				};
				WORKERPOOL.execute(unwrapTask);
			}
			
			if (isDataEncrypted) {
				eventHandler.onPostDataEncrypted();
			}
			
			
			if (nodifyHandshakeFinished) {
				notifyHandshakeFinished();
			}			
			
		} // while (needWrap)				
	}
	
	
	
	
	private void notifyHandshakeFinished() throws IOException {
		if (LOG.isLoggable(Level.FINE)) {
			if (isClientMode) {
				LOG.fine("handshake has been finished (clientMode)");
			} else {
				LOG.fine("handshake has been finished (serverMode)");
			}
		}
		
		eventHandler.onHandshakeFinished();	
	}
	
	

	private ByteBuffer mergeBuffer(ByteBuffer first, ByteBuffer second) {
		
		if (first.remaining() == 0) {
			return second;
		}
		
		if (second.remaining() == 0) {
			return first;
		}
		
		ByteBuffer mergedBuffer = ByteBuffer.allocate(first.remaining() + second.remaining());
		mergedBuffer.put(first);
		mergedBuffer.put(second);
		mergedBuffer.flip();
		
		return mergedBuffer;
	}
	
	
	
	/**
	 * SSLProcessor call back interface
	 * 
	 * @author grro
	 */
	static interface EventHandler {

		/**
		 * signals that the handshake has been finished
		 * 
	     * @throws IOException if an io exception occurs
		 */
		public void onHandshakeFinished() throws IOException;
		
		
		/**
		 * signals data has been decrypted
		 * 
		 * @param decryptedBuffer the decrypted data
		 */
		public void onDataDecrypted(ByteBuffer decryptedBuffer);

		
		
		
		/**
		 * signals the the decrypted task has been completed
		 */
		public void onPostDataDecrypted();
		
		
		
		/**
		 * signals that data has been encrypted
		 * 
		 * @param plainData      the plain data
		 * @param encryptedData  the encrypted data
	     * @throws IOException if an io exception occurs
		 */
		public void onDataEncrypted(ByteBuffer plainData,  ByteBuffer encryptedData) throws IOException ;
		
		
		/**
		 * signals that data encrypt task has been completed
		 * 
		 */
		public void onPostDataEncrypted() throws IOException ;
		
		
		/**
		 * signals, that the SSLProcessor has been closed 
		 * 
	     * @throws IOException if an io exception occurs
		 */
		public void onDestroy() throws IOException;
		
		/**
		 * signals, that the inbound has been closed
		 * 
		 * @throws IOException
		 */
		public void onInboundClosed() throws IOException;
	}
	
	
	
    private static class DefaultThreadFactory implements ThreadFactory {
        
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DefaultThreadFactory() {
            namePrefix = "xSSL-" + poolNumber.getAndIncrement() + "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }	
}
