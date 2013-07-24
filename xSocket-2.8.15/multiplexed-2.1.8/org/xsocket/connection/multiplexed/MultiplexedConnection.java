/*
 *  Copyright (c) xsocket.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection.multiplexed;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.SerializedTaskQueue;
import org.xsocket.connection.AbstractNonBlockingStream;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnection;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IWriteCompletionHandler;
import org.xsocket.connection.multiplexed.MultiplexedUtils.CompletionHandlerInfo;
import org.xsocket.connection.multiplexed.multiplexer.SimpleMultiplexer;
import org.xsocket.connection.multiplexed.multiplexer.IMultiplexer;
import org.xsocket.connection.multiplexed.multiplexer.IMultiplexer.IDemultiplexResultHandler;




/**
 * Implementation of the {@link IMultiplexedConnection} 
 * 
 * @author grro@xsocket.org
 */
public final class MultiplexedConnection implements IMultiplexedConnection {

	private static final Logger LOG = Logger.getLogger(MultiplexedConnection.class.getName());

	// timer
	private static final Timer TIMER = new Timer("xPipelineTimer", true); 

	private static final long MIN_WATCHDOG_PERIOD_MILLIS = 30 * 1000;
	
	// pipelines
	private final HashMap<String, NonBlockingPipeline> pipelines = new HashMap<String, NonBlockingPipeline>();

	
	// underlying connection
	private INonBlockingConnection connection = null;
    private final Object disconnectedGuard = false;
    private boolean isDisconnected = false;
    private final AtomicBoolean isOpen = new AtomicBoolean(true);

	
	// multiplexer
	private IMultiplexer multiplexer = null;
	private final Object multiplexerWriteGuard = new Object();
	private final Object multiplexerReadGuard = new Object();

	// demultiplex result handler
	private final DemultiplexResultHandler demultiplexResultHandler = new DemultiplexResultHandler();
	
	
	// handler adapter
	private PipelineHandlerAdapter handlerAdapter = PipelineHandlerAdapter.newInstance(null);
	
	
	// task Queue
    private final SerializedTaskQueue taskQueue = new SerializedTaskQueue();

	
	// write completion handler support
	private final Map<WriteCompletionHolder, List<ByteBuffer>> pendingCompletionConfirmations = new HashMap<WriteCompletionHolder, List<ByteBuffer>>();
	private boolean isWriteCompletionSupportActivated = false;


	
	
	/**
	 * constructor. The {@link SimpleMultiplexer} class will be used to (de)multiplex the data  
	 * 
	 * @param connection   the underlying connection
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection) throws IOException {
		this(connection, new SimpleMultiplexer());
	}

	
	/**
	 * constructor.
	 * 
	 * @param connection   the underlying connection
	 * @param multiplexer  the multiplexer to use
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection, IMultiplexer multiplexer) throws IOException {
		this(connection, PipelineHandlerAdapter.newInstance(null), multiplexer);
	}


	/**
	 * constructor. 
	 * 
	 * @param connection       the underlying connection
	 * @param pipelineHandler  the pipeline handler
	 * @param multiplexer  the multiplexer to use
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection, IHandler pipelineHandler, IMultiplexer multiplexer) throws IOException {
		this(connection, PipelineHandlerAdapter.newInstance(pipelineHandler), multiplexer);
	}
	
	
	/**
	 * constructor. The {@link SimpleMultiplexer} class will be used to (de)multiplex the data
	 *  
	 * @param connection       the underlying connection
	 * @param pipelineHandler  the pipeline handler
	 * @throws IOException  if an exception occurs
	 */
	public MultiplexedConnection(INonBlockingConnection connection, IHandler pipelineHandler) throws IOException {
		this(connection, PipelineHandlerAdapter.newInstance(pipelineHandler), new SimpleMultiplexer());
	}
	
	
	/**
	 * internal constructor  
	 * 
	 */
	MultiplexedConnection(INonBlockingConnection connection, PipelineHandlerAdapter handlerAdapter, IMultiplexer multiplexer) throws IOException {
		this.connection = connection;
		this.multiplexer = multiplexer;		
		this.handlerAdapter = handlerAdapter;
	
		connection.setAutoflush(false);
		connection.setFlushmode(FlushMode.SYNC);
	
		connection.setHandler(new MultiplexedConnectionHandler(this));
	}


	
	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return (isOpen.get() && connection.isOpen());
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public void close() throws IOException {
		
	    if (isOpen.getAndSet(false)) {
    		HashMap<String, NonBlockingPipeline> copy = null;
    		synchronized (pipelines) {
    			copy = (HashMap<String, NonBlockingPipeline>) pipelines.clone();
    		}
    		
    		for (NonBlockingPipeline pipeline : copy.values()) {
    			pipeline.closeSilence();
    		}
    
    		try {
    			connection.close();
    		} catch (Exception e) { 
    			if (LOG.isLoggable(Level.FINE)) {
    				LOG.fine("[" + getId() + "] error occured by closing connection " +e.toString());
    			}
    		}
	    }
	}
	
	
    private void closeSilence() {
        try {
            close();
        } catch (Exception e) { 
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + getId() + "] error occured by closing connection " +e.toString());
            }
        }
    }
    
   
	
	/**
	 * {@inheritDoc}
	 */
	public String getId() {
		return connection.getId();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void activateSecuredMode() throws IOException {
		synchronized (pipelines) {
			for (NonBlockingPipeline pipline : pipelines.values()) {
				pipline.flush();
			}
		}
		
		connection.activateSecuredMode();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void deactivateSecuredMode() throws IOException {
		synchronized (pipelines) {
			for (NonBlockingPipeline pipline : pipelines.values()) {
				pipline.flush();
			}
		}
		
		connection.deactivateSecuredMode();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isSecure() {
		return connection.isSecure();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getDefaultEncoding() {
		return connection.getEncoding();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setDefaultEncoding(String encoding) {
		connection.setEncoding(encoding);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return connection.getLocalAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return connection.getLocalPort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public InetAddress getRemoteAddress() {
		return connection.getRemoteAddress();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getRemotePort() {
		return connection.getRemotePort();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Object getOption(String name) throws IOException {
		return connection.getOption(name);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions() {
		return connection.getOptions();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setOption(String name, Object value) throws IOException {
		connection.setOption(name, value);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutMillis(long timeoutMillis) {
		connection.setConnectionTimeoutMillis(timeoutMillis);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getConnectionTimeoutMillis() {
		return connection.getConnectionTimeoutMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutMillis(long timeoutMillis) {
		connection.setIdleTimeoutMillis(timeoutMillis);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getIdleTimeoutMillis() {
		return connection.getIdleTimeoutMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToConnectionTimeout() {
		return connection.getRemainingMillisToConnectionTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToIdleTimeout() {
		return connection.getRemainingMillisToIdleTimeout();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void setAttachment(Object obj) {
		connection.setAttachment(obj);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Object getAttachment() {
		return connection.getAttachment();
	}
	
	
	/**
     * {@inheritDoc}
     */
	public boolean isServerSide() {
	    return connection.isServerSide();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String createPipeline() throws IOException {
		String pipelineId = registerNewPipeline();

		NonBlockingPipeline pipeline = new NonBlockingPipeline(pipelineId, handlerAdapter);
		synchronized (pipelines) {
			pipelines.put(pipelineId, pipeline);
		}
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] pipeline " + pipelineId + " created");
		}
		
		return pipelineId;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public String[] listOpenPipelines() throws ClosedChannelException {
		String[] result = null;
		
		synchronized (pipelines) {
			Set<String> ids = pipelines.keySet();
			result = ids.toArray(new String[ids.size()]);
		}
		
		return result;
	}
	
	
	private void closePipeline(NonBlockingPipeline pipeline) throws IOException {
		
		if (pipeline == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning try to close a <null> pipeline");
			}
			return;
		}

		deregisterPipeline(pipeline.getId());
		removePipeline(pipeline);
	}

	
	private void removePipeline(NonBlockingPipeline pipeline) {
		
		NonBlockingPipeline pipe = null;
		synchronized (pipelines) {
			pipe = pipelines.remove(pipeline.getId());
		}
		
		if (pipe != null) {
			pipeline.onClose();
		
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] pipeline " + pipeline.getId() + " destroyed");
			}
		}
	}
	
	

	/**
	 * {@inheritDoc}
	 */	
	public INonBlockingPipeline getNonBlockingPipeline(String pipelineId) throws ClosedChannelException {
		if (connection.isOpen()) {
			synchronized (pipelines) {
				return pipelines.get(pipelineId);
			}
		} else {
			throw new ClosedChannelException();
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	public IBlockingPipeline getBlockingPipeline(String pipelineId) throws ClosedChannelException, IOException {
		return new BlockingPipeline(getNonBlockingPipeline(pipelineId));
	}
	
	
	
	private void onData() throws IOException {
		synchronized (multiplexerReadGuard) {
			multiplexer.demultiplex(connection, demultiplexResultHandler);
		}
	}
	
	
	
	private void onPipelineOpened(final String pipelineId) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] pipeline " + pipelineId + " opened by peer");
		}

		NonBlockingPipeline pipeline = new NonBlockingPipeline(pipelineId, handlerAdapter);
		synchronized (pipelines) {
			pipelines.put(pipelineId, pipeline);
		}	
	}
	
	
	
	
	private void onPipelineClosed(final String pipelineId) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] pipeline " + pipelineId + " closed by peer");
		}

		NonBlockingPipeline pipeline = null;
		synchronized (pipelines) {
			pipeline = pipelines.get(pipelineId);
		}

		if (pipeline != null) {
			removePipeline(pipeline);
		}
	}
	

	
	
	private void onPipelineData(final String pipelineId, ByteBuffer[] data) {
		NonBlockingPipeline pipeline = null;
		synchronized (pipelines) {
			pipeline = pipelines.get(pipelineId);
		}

		if (pipeline != null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("notifying pipeline data handler");
			}
			pipeline.onData(data);
	
		} else {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("data received for non existing pipeline " + pipelineId);
			}
		}
	}
	
	
	
	private String registerNewPipeline() throws ClosedChannelException, IOException {
		synchronized (multiplexerWriteGuard) {
			return multiplexer.openPipeline(connection);
		}
	}

	
	
	private void deregisterPipeline(String pipelineId) throws ClosedChannelException, IOException {
		synchronized (multiplexerWriteGuard) {
			multiplexer.closePipeline(connection, pipelineId);
		}
	}

	private void sendPipelineData(String pipelineId, ByteBuffer[] dataToWrite, FlushMode flushMode, IWriteCompletionHandler  completionHandler) throws ClosedChannelException, IOException {
		if (dataToWrite == null) {
			return;
		}
		
		if (dataToWrite.length == 0) {
			return;
		}
		
		synchronized (multiplexerWriteGuard) {
			connection.setFlushmode(flushMode);
			multiplexer.multiplex(connection, pipelineId, dataToWrite, completionHandler);
			connection.setFlushmode(FlushMode.SYNC);
		}
	}
	
	
	
	
	
    private void registerCompletionHandler(IWriteCompletionHandler writeCompletionHandler, boolean isLongResult, ByteBuffer... buffersToWrite) {
        isWriteCompletionSupportActivated = true;
        
        WriteCompletionHolder holder = new WriteCompletionHolder(writeCompletionHandler, buffersToWrite);
        synchronized (pendingCompletionConfirmations) {
            pendingCompletionConfirmations.put(holder, new ArrayList<ByteBuffer>(Arrays.asList( buffersToWrite)));
        }
    }

    
    

    private void onWritten(ByteBuffer[] data) {
        if (isWriteCompletionSupportActivated) {
            for (ByteBuffer byteBuffer : data) {
                onWritten(byteBuffer);
            } 
        }
    }
    
    
    
    private void onWritten(ByteBuffer data) {

        WriteCompletionHolder holderToExecute = null;
        synchronized (pendingCompletionConfirmations) {
            if (data != null) {
                for (Entry<WriteCompletionHolder, List<ByteBuffer>> entry : pendingCompletionConfirmations.entrySet()) {
                    List<ByteBuffer> buffers = entry.getValue();
                    buffers.remove(data);
                    if (buffers.isEmpty()) {
                        pendingCompletionConfirmations.remove(entry.getKey());
                        holderToExecute = entry.getKey();
                        break;
                    }
                }
            }
        }
        
        if (holderToExecute != null) {
            holderToExecute.performOnWritten();
        }
    }

    

    private void onWriteException(IOException ioException, ByteBuffer[] data) {

        closeSilence();
        
        if (isWriteCompletionSupportActivated) {
            for (ByteBuffer byteBuffer : data) {
                onWriteException(ioException, byteBuffer);
            }
        }
    }
    

    
    private void onWriteException(IOException ioException, ByteBuffer data) {

        WriteCompletionHolder holderToExecute = null;
        synchronized (pendingCompletionConfirmations) {
            if (data != null) {
                for (Entry<WriteCompletionHolder, List<ByteBuffer>> entry : pendingCompletionConfirmations.entrySet()) {
                    if (entry.getValue().contains(data)) {
                        pendingCompletionConfirmations.remove(entry.getKey());
                        holderToExecute = entry.getKey();
                        break;
                    }
                }
            }
        }
        
        if (holderToExecute != null) {
            holderToExecute.performOnException(ioException);
        }
    }


	
	private static final class BlockingPipeline extends BlockingConnection implements IBlockingPipeline {
		private INonBlockingPipeline delegee = null;
		
		BlockingPipeline(INonBlockingPipeline delegee) throws IOException {
			super(delegee);
			this.delegee = delegee;
		}
		

		public IMultiplexedConnection getMultiplexedConnection() {
			return delegee.getMultiplexedConnection();
		}
	}		
	
	
	private final class DemultiplexResultHandler implements IDemultiplexResultHandler {
		public void onPipelineOpend(String pipelineId) {
			MultiplexedConnection.this.onPipelineOpened(pipelineId);
		}
		
		public void onPipelineClosed(String pipelineId) {
			MultiplexedConnection.this.onPipelineClosed(pipelineId);
		}
		
		public void onPipelineData(String pipelineId, ByteBuffer[] data) {
			MultiplexedConnection.this.onPipelineData(pipelineId, data);			
		}
	}

	
	
	
	
	@Execution(Execution.NONTHREADED)
	private static final class MultiplexedConnectionHandler implements IConnectHandler, IDataHandler, IDisconnectHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler {
					
		private MultiplexedConnection multiplexedConnection = null;
		
		private MultiplexedConnectionHandler(MultiplexedConnection multiplexedConnection) {
			this.multiplexedConnection = multiplexedConnection;
		}
				

		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			if (connection.available() > 0) {
				onData(connection);
			}
			
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			multiplexedConnection.onData();
			return true;
		}
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			multiplexedConnection.close();
			return true;
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
			multiplexedConnection.close();
			return true;
		}
		
		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
			multiplexedConnection.close();
			return true;
		}
	}
	
	
	
	
	 
	
	
	
	private final class NonBlockingPipeline extends AbstractNonBlockingStream implements INonBlockingPipeline {
		
		
		// the pipeline id
		private String pipelineId = null;

		// close flag
		private final AtomicBoolean isOpen = new AtomicBoolean(true);
		
		// suspend support
		private boolean isSuspendRead = false;
		private final ArrayList<ByteBuffer> suspendBuffer = new ArrayList<ByteBuffer>();


		
		// timeouts 
		private long connectionTimeoutMillis = IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS;
		private long idleTimeoutMillis = IConnection.DEFAULT_IDLE_TIMEOUT_MILLIS;
		private long idleTimeoutDateMillis = Long.MAX_VALUE;
		private long connectionTimeoutDateMillis = Long.MAX_VALUE;
		private long lastReceivedMillis = System.currentTimeMillis();
		
		
		private boolean idleTimeoutOccured = false;
		private boolean connectionTimeoutOccured = false;
	
		
		private WatchDogTask watchDogTask = null;
		
	
		// handler
		private final AtomicReference<PipelineHandlerAdapter> handlerRef = new AtomicReference<PipelineHandlerAdapter>(null);
		
		
		NonBlockingPipeline(String pipelineId, PipelineHandlerAdapter handlerAdapter) {
			this.pipelineId = pipelineId;
			
			handlerRef.set(handlerAdapter.getConnectionInstance());
			onOpen();
		}

		
		private void onOpen() {
			try {
				handlerRef.get().onConnect(this, taskQueue);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) 
					LOG.fine("error occured by performing onConnect call back on " + handlerRef.get() + " " + ioe.toString());
			}
		}

		

		@Override
		protected boolean isDataWriteable() {
			try {
				return (getNonBlockingPipeline(pipelineId) != null);
			} catch (ClosedChannelException ce) {
				return false;
			}
		}
		
		
		@Override
		protected boolean isMoreInputDataExpected() {
			try {
				return (getNonBlockingPipeline(pipelineId) != null);
			} catch (ClosedChannelException ce) {
				return false;
			}
		}
		
		
		public boolean isOpen() {
			if (isOpen.get()) {
				if (!isReadBufferEmpty()) {
					return true;
				}
				
				try {
					return (getNonBlockingPipeline(pipelineId) != null);
				} catch (ClosedChannelException ce) {
					return false;
				}
			} else {
	             return false;
            }			 
		}
		
		
		public void close() throws IOException {
			super.close();

			if (isOpen.getAndSet(false)) {
				closePipeline(this);
			}
		}

		
		private void onClose() {
			terminateWatchDog();
			
			try {
				handlerRef.get().onDisconnect(this, taskQueue);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) 
					LOG.fine("error occured by performing onDisconnect call back on " + handlerRef.get() + " " + ioe.toString());
			}

		}

		
		public void onData(ByteBuffer[] data) {
			
			if (isSuspendRead) {
				for (ByteBuffer byteBuffer : data) {
					suspendBuffer.add(byteBuffer);	
				}
				return;
			}
			
			int size = 0;
			if (data != null) {
				for (ByteBuffer byteBuffer : data) {
					size += byteBuffer.remaining();
				}
			}
				
			if (size > 0) {
				appendDataToReadBuffer(data, size);
			}
			
			try {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("notifying handler " + handlerRef.get());
				}
				handlerRef.get().onData(this, taskQueue);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by calling onData for pipeline " + getId() + " " + ioe.toString());
				}
			}
		}
		
		
		
		
		private void onConnectionTimeout() {
			if (!connectionTimeoutOccured) {
				connectionTimeoutOccured = true;
				try {
					handlerRef.get().onConnectionTimeout(this, taskQueue);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) 
						LOG.fine("error occured by performing onConnectionTimeout call back on " + handlerRef.get() + " " + ioe.toString());
				}
				
			} else {
				setConnectionTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
			}

		}
				
		
		private void onIdleTimeout() {
			if (!idleTimeoutOccured) {
				idleTimeoutOccured = true;
				try {
					handlerRef.get().onIdleTimeout(this, taskQueue);
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) 
						LOG.fine("error occured by performing onIdleTimeout call back on " + handlerRef.get() + " " + ioe.toString());
				}
				
			} else {
				setIdleTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
			}
		}
		
	

		
		void closeSilence() {
			try {
				close();
			} catch (ClosedChannelException cce) {
			    onClose();
			    
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("Error occured by closing connection " + getId() + " " + ioe.toString());
				}
			}
		}
		
		
		public String getId() {
			return pipelineId;
		}

	
		public boolean isServerSide() {
		    return connection.isServerSide();
		}
		
		
		public Executor getWorkerpool() {
			return connection.getWorkerpool();
		}
		
		public void setWorkerpool(Executor workerpool) {
			LOG.warning("setWorkerpool is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}
		
		public IMultiplexedConnection getMultiplexedConnection() {
			return MultiplexedConnection.this;
		}
		
		public void setMaxReadBufferThreshold(int size) {
			throw new UnsupportedOperationException("setMaxReadBufferThreshold is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}
		
		public int getMaxReadBufferThreshold() {
			return connection.getMaxReadBufferThreshold();
		}
		
		
		public long getConnectionTimeoutMillis() {
			return connectionTimeoutMillis;
		}
		
		
		public void setConnectionTimeoutMillis(long timeoutMillis) {
			connectionTimeoutDateMillis = System.currentTimeMillis() + timeoutMillis;
			
			if (connectionTimeoutMillis != timeoutMillis) {
				connectionTimeoutMillis = timeoutMillis;
				updateWatchdog(connectionTimeoutMillis, idleTimeoutMillis);
			}
			
			connectionTimeoutOccured = false;
		}
		
		
		public long getIdleTimeoutMillis() {
			return idleTimeoutMillis;
		}
		
		
		public void setIdleTimeoutMillis(long timeoutMillis) {
			idleTimeoutDateMillis = System.currentTimeMillis() + timeoutMillis;
			
			if (idleTimeoutMillis != timeoutMillis) {
				idleTimeoutMillis = timeoutMillis;
				updateWatchdog(connectionTimeoutMillis, idleTimeoutMillis);
			}
			
			idleTimeoutOccured = false;
		}
		

	    /**
	     * {@inheritDoc}
	     */ 
	    public void write(String message, String encoding, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	        write(DataConverter.toByteBuffer(message, encoding), writeCompletionHandler);
	    }
	    
	    
	    /**
	     * {@inheritDoc}
	     */ 
	    public void write(byte[] bytes, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	        write(ByteBuffer.wrap(bytes), writeCompletionHandler);
	    }

	    
	    /**
	     * {@inheritDoc}
	     */ 
	    public void write(byte[] bytes, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	        write(DataConverter.toByteBuffer(bytes, offset, length), writeCompletionHandler);
	    }  
	    
	    
	    /**
	     * {@inheritDoc}
	     */
	    public void write(ByteBuffer[] srcs, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	        write(DataConverter.toByteBuffers(srcs, offset, length), writeCompletionHandler);
	    }
	     
	    
	    
	    /**
	     * {@inheritDoc}
	     */ 
	    public void write(ByteBuffer buffer, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	        boolean isSuppressReuseBuffer = isSuppressReuseBufferWarning();
	        setSuppressReuseBufferWarning(true);
	        
	        registerCompletionHandler(writeCompletionHandler, false, buffer);
	        write(buffer);
	        
	        setSuppressReuseBufferWarning(isSuppressReuseBuffer);
	    }
	    
	    
	    /**
	     * {@inheritDoc}
	     */
	    public void write(ByteBuffer[] buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	        boolean isSuppressReuseBuffer = isSuppressReuseBufferWarning();
	        setSuppressReuseBufferWarning(true);

	        registerCompletionHandler(writeCompletionHandler, true, buffers);
	        write(buffers);
	        
	        setSuppressReuseBufferWarning(isSuppressReuseBuffer);
	    }

	    /**
	     * {@inheritDoc}
	     */
	    public void write(List<ByteBuffer> buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
	        boolean isSuppressReuseBuffer = isSuppressReuseBufferWarning();
	        setSuppressReuseBufferWarning(true);
	        
	        registerCompletionHandler(writeCompletionHandler, true, buffers.toArray(new ByteBuffer[buffers.size()]));
	        write(buffers);
	        
	        setSuppressReuseBufferWarning(isSuppressReuseBuffer);
	    }


	
		

		
		private synchronized void updateWatchdog(long connectionTimeoutMillis, long idleTimeoutMillis) {
			
			long watchdogPeriod = connectionTimeoutMillis;
			if (idleTimeoutMillis < watchdogPeriod) {
				watchdogPeriod = idleTimeoutMillis; 
			}

			if (watchdogPeriod > 500) {
				watchdogPeriod = watchdogPeriod / 5;
			}
			
			if (watchdogPeriod > MIN_WATCHDOG_PERIOD_MILLIS) {
				watchdogPeriod = MIN_WATCHDOG_PERIOD_MILLIS;
			}
			
			terminateWatchDog();
			
	        watchDogTask = new WatchDogTask(this);
	        TIMER.schedule(watchDogTask, watchdogPeriod, watchdogPeriod);
		}
		
		
		private synchronized void terminateWatchDog() {
	        if (watchDogTask != null) {
	            watchDogTask.cancel();
	        }			
		}

		

		private void checkTimeouts() {
			long currentMillis = System.currentTimeMillis();
					
			if (getRemainingMillisToConnectionTimeout(currentMillis) <= 0) {
				onConnectionTimeout();
			}
					
			if (getRemainingMillisToIdleTimeout(currentMillis) <= 0) {
				onIdleTimeout();
			}
		}

		
		/**
		 * {@inheritDoc}
		 */
		public long getRemainingMillisToConnectionTimeout() {
			return getRemainingMillisToConnectionTimeout(System.currentTimeMillis());
		}

		
		private long getRemainingMillisToConnectionTimeout(long currentMillis) {
			return connectionTimeoutDateMillis - currentMillis;
		}

		
		/**
		 * {@inheritDoc}
		 */
		public long getRemainingMillisToIdleTimeout() {
			return getRemainingMillisToIdleTimeout(System.currentTimeMillis());
		}
		
		
		private long getRemainingMillisToIdleTimeout(long currentMillis) {
			long remaining = idleTimeoutDateMillis - currentMillis;
			
			// time out received
			if (remaining > 0) {
				return remaining;	
				
			// ... yes 
			} else {
				
				// ... but check if meantime data has been received! 
				return (lastReceivedMillis + idleTimeoutMillis) - currentMillis;
			}	
		}
		
		
		
		public void setWriteTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException {
			throw new UnsupportedOperationException("setWriteTransferRate is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}
		
		public int getWriteTransferRate() throws ClosedChannelException, IOException {
			return connection.getWriteTransferRate();
		}
		
		public void setHandler(IHandler hdl) throws IOException {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] set handler " + hdl);
			}
		
			PipelineHandlerAdapter adapter = PipelineHandlerAdapter.newInstance(hdl);
			
			
			boolean callDisconnect = false;
			synchronized (disconnectedGuard) {
			    handlerRef.set(adapter);
		            
			    if (isDisconnected) {
			        callDisconnect = true;
			    }
			}
		
			// calling onConnect
			onOpen();
			
			// is data available
			if (available() > 0) {
			    onData(null);
			}

			// is disconnected
			if (callDisconnect) {
			    close();             
			}			
		}
		
		public IHandler getHandler() {
			PipelineHandlerAdapter hdlAdapter = handlerRef.get();
			if (hdlAdapter == null) {
				return null;
			} else {
				return hdlAdapter.getHandler();
			}
		}
		
		
		public void setOption(String name, Object value) throws IOException {
			LOG.warning("set option is vaild for all pipelines. Better use <MultiplexedcCnnection>.serOptions(String, Object)");
			connection.setOption(name, value);
		}
		
		public Object getOption(String name) throws IOException {
			return connection.getOption(name);
		}
		
		@SuppressWarnings("unchecked")
		public Map<String, Class> getOptions() {
			return connection.getOptions();
		}
		
		
		public InetAddress getLocalAddress() {
			return connection.getLocalAddress();
		}
		
		public int getLocalPort() {
			return connection.getLocalPort();
		}
		
		public InetAddress getRemoteAddress() {
			return connection.getRemoteAddress();
		}
		
		public int getRemotePort() {
			return connection.getRemotePort();
		}
		
		public void suspendReceiving() throws IOException {
			isSuspendRead = true;
		}
		
		public boolean isReceivingSuspended() {
			return isSuspendRead;
		}
		
		@SuppressWarnings("unchecked")
		public void resumeReceiving() throws IOException {
			isSuspendRead = false;
			ArrayList<ByteBuffer> data = (ArrayList<ByteBuffer>) suspendBuffer.clone();
			suspendBuffer.clear();
			onData(data.toArray(new ByteBuffer[data.size()]));
		}
		
		public int getPendingWriteDataSize() {
			return getWriteBufferSize();
		}
	
	
		
		public void activateSecuredMode() throws IOException {
			throw new UnsupportedOperationException("activateSecuredMode is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}

		
		
		public void deactivateSecuredMode() throws IOException {
			throw new UnsupportedOperationException("deactivateSecuredMode is not supported for a pipeline. perform this operation on the MultiplexedConnection");			
		}

		
		public boolean isSecuredModeActivateable() {
			return connection.isSecuredModeActivateable();
		}
		
		public boolean isSecure() {
			return connection.isSecure();
		}
	
		
		
		public long transferFrom(ReadableByteChannel sourceChannel) throws ClosedChannelException, IOException, SocketTimeoutException {
			int chunkSize = (Integer) getOption(SO_SNDBUF);
			long transfered = 0;

			int read = 0;
			do {
				ByteBuffer transferBuffer = ByteBuffer.allocate(chunkSize);
				read = sourceChannel.read(transferBuffer);
					
				if (read > 0) { 
					if (transferBuffer.remaining() == 0) {
						transferBuffer.flip();
						write(transferBuffer);
							
					} else {
						transferBuffer.flip();
						write(transferBuffer.slice());
					}
						
					transfered += read;
				}
			} while (read > 0);
				
			return transfered;
		}

	
	
		@Override
		protected void onWriteDataInserted() throws IOException, ClosedChannelException {
			if (isAutoflush()) {
				flush();
			}
		}

		
		
		public void flush() throws ClosedChannelException, IOException {
			removeWriteMark();
			
			final ByteBuffer[] dataToWrite = drainWriteQueue();
			
			IWriteCompletionHandler completionHandler = null;
			
			synchronized (pendingCompletionConfirmations) {
			    if (!pendingCompletionConfirmations.isEmpty()) {
			        
			        completionHandler = new IWriteCompletionHandler() {
			            
			            @Execution(Execution.NONTHREADED)
			            public void onWritten(int written) throws IOException {
			                MultiplexedConnection.this.onWritten(dataToWrite);
			            }
			            
			            @Execution(Execution.NONTHREADED)
			            public void onException(IOException ioe) {
			                MultiplexedConnection.this.onWriteException(ioe, dataToWrite);
			            }
			        };
			    }
            }
			
			sendPipelineData(getId(), dataToWrite, getFlushmode(), completionHandler);
		}

		
		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			if (isOpen()) {
				return "id=" + getId() + ", remote=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")";
			} else {
				return "id=" + getId() + " (closed)";
			}
		}
	}
	
	
	
    private final class WriteCompletionHolder implements Runnable {
        
        private final IWriteCompletionHandler handler;
        private final CompletionHandlerInfo handlerInfo;
        private final int size;
        
        public WriteCompletionHolder(IWriteCompletionHandler handler, ByteBuffer[] bufs) {
            this.handler = handler;
            this.handlerInfo = MultiplexedUtils.getCompletionHandlerInfo(handler);
            
            int l = 0;
            for (ByteBuffer byteBuffer : bufs) {
                l += byteBuffer.remaining();
            }
            size = l;
        }
        
        
        void performOnWritten() {
            
            if (handlerInfo.isOnWrittenMultithreaded()) {
                taskQueue.performMultiThreaded(this, connection.getWorkerpool());
                
            } else {
                taskQueue.performNonThreaded(this, connection.getWorkerpool());
            }
        }
        
        
        public void run() {
            callOnWritten();
        }

        private void callOnWritten() {
            try {
                handler.onWritten(size);
            } catch (Exception e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("error occured by calling onWritten " + e.toString() + " closing connection");
                }
                closeSilence();
            }

        }

        
        void performOnException(final IOException ioe) {
            
            if (handlerInfo.isOnExceptionMutlithreaded()) {
                Runnable task = new Runnable() {
                    public void run() {
                        callOnException(ioe);
                    }
                };
                taskQueue.performMultiThreaded(task, connection.getWorkerpool());
                
            } else {
                Runnable task = new Runnable() {
                    public void run() {
                        callOnException(ioe);
                    }
                };
                taskQueue.performNonThreaded(task, connection.getWorkerpool());
            }
        }
        
        
        private void callOnException(IOException ioe) {
            handler.onException(ioe);
        }
    }


	
	
	private static final class WatchDogTask extends TimerTask {
		
		private WeakReference<NonBlockingPipeline> nonBlockingPipelineRef = null;
		
		public WatchDogTask(NonBlockingPipeline nonBlockingPipeline) {
			nonBlockingPipelineRef = new WeakReference<NonBlockingPipeline>(nonBlockingPipeline);
		}
	
		
		@Override
		public void run() {
			NonBlockingPipeline nonBlockingPipeline = nonBlockingPipelineRef.get();
			
			if (nonBlockingPipeline == null)  {
				this.cancel();
				
			} else {
				nonBlockingPipeline.checkTimeouts();
			}
		}		
	}
}
