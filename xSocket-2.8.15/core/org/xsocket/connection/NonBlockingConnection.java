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
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.SerializedTaskQueue;
import org.xsocket.connection.ConnectionManager.TimeoutMgmHandle;
import org.xsocket.connection.ConnectionUtils.CompletionHandlerInfo;

/**
 * Implementation of the <code>INonBlockingConnection</code> interface. <br><br>
 *
 * Depending on the constructor parameter waitForConnect a newly created connection is in the open state. 
 * Write or read methods can be called immediately. 
 * Absence of the waitForConnect parameter is equals to waitForConnect==true.  
 *
 * <br><br><b>The methods of this class are not thread-safe.</b>
 *
 * @author grro@xsocket.org
 */
public final class NonBlockingConnection extends AbstractNonBlockingStream implements INonBlockingConnection {

	private static final Logger LOG = Logger.getLogger(NonBlockingConnection.class.getName());

	public static final String SEND_TIMEOUT_KEY  = "org.xsocket.connection.sendFlushTimeoutMillis";
	public static final long DEFAULT_SEND_TIMEOUT_MILLIS = 60L * 1000L;

    private static final boolean IS_SUPPRESS_SYNC_FLUSH_WARNING = IoProvider.getSuppressSyncFlushWarning();
    private static final boolean IS_SUPPRESS_SYNC_FLUSH_COMPLITIONHANDLER_WARNING = IoProvider.getSuppressSyncFlushCompletionHandlerWarning();
	
	private static Executor defaultWorkerPool;
	private static IoConnector defaultConnector;

    

	private static long sendTimeoutMillis = DEFAULT_SEND_TIMEOUT_MILLIS;
	static {
		try {
			sendTimeoutMillis = Long.valueOf(System.getProperty(SEND_TIMEOUT_KEY, Long.toString(DEFAULT_SEND_TIMEOUT_MILLIS)));
    	} catch (Exception e) {
    		LOG.warning("invalid value for system property " + SEND_TIMEOUT_KEY + ": "
    				+ System.getProperty(SEND_TIMEOUT_KEY) + " (valid is a int value)"
    				+ " using default");
    		sendTimeoutMillis = DEFAULT_SEND_TIMEOUT_MILLIS;
    	}

    	if (LOG.isLoggable(Level.FINE)) {
    		LOG.fine("non blocking connection send time out set with " + DataConverter.toFormatedDuration(sendTimeoutMillis));
    	}
	}

	// 是否为服务器端
	private final boolean isServerSide;

	
	// flags
	private final AtomicBoolean isOpen = new AtomicBoolean(true);
	// init()方法中设置为true
	private final AtomicBoolean isConnected = new AtomicBoolean(false);
	private final AtomicBoolean isSuspended = new AtomicBoolean(false);
	private final Object disconnectedGuard = false;
	private final AtomicBoolean isDisconnected = new AtomicBoolean(false);


	
	// connection manager
	private static final ConnectionManager DEFAULT_CONNECTION_MANAGER = new ConnectionManager();
	private TimeoutMgmHandle timeoutMgmHandle;
	

	// io handler
	private final IoHandlerCallback ioHandlerCallback = new IoHandlerCallback();
	private IoChainableHandler ioHandler;


	// app handler
	private final AtomicReference<HandlerAdapter> handlerAdapterRef = new AtomicReference<HandlerAdapter>(null);
    private final AtomicReference<IHandlerChangeListener> handlerReplaceListenerRef = new AtomicReference<IHandlerChangeListener>();

	// execution
	private Executor workerpool;
	
	// task Queue
	// 任务队列
	private final SerializedTaskQueue taskQueue = new SerializedTaskQueue();
	
    // write transfer rate
    private int bytesPerSecond = UNLIMITED;
    
    
    // sync write support
    private final SynchronWriter synchronWriter = new SynchronWriter();


	// write thread handling
	private final WriteCompletionManager writeCompletionManager = new WriteCompletionManager();
	private final Object asyncWriteGuard = new Object();


	// timeout support
	private long idleTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;
	private long idleTimeoutDateMillis = Long.MAX_VALUE;
	private long connectionTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;
	private long connectionTimeoutDateMillis = Long.MAX_VALUE;
	private final AtomicBoolean idleTimeoutOccured = new AtomicBoolean(false);
	private final AtomicBoolean connectionTimeoutOccured = new AtomicBoolean(false);
	private final AtomicBoolean connectExceptionOccured = new AtomicBoolean(false);
//	private final AtomicBoolean disconnectOccured = new AtomicBoolean(false);


	// cached values
	private Integer cachedSoSndBuf;

	
	// max app buffer size
	private final Object suspendGuard = new Object(); 
	private Integer maxReadBufferSize;
	


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
     * @param hostname  the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port) throws IOException {
		this(InetAddress.getByName(hostname), port);
	}
	
	
	


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address   the remote host
	 * @param port		the port of the remote host to connect
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool(), null);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address   the remote host address
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetSocketAddress address) throws IOException {
		this(address, true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool(), null);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
     *
	 * @param address                the remote host
	 * @param port		             the port of the remote host to connect
	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), null, false, null, getDefaultWorkerpool(), null);
	}

	

	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address              the remote host
	 * @param port	          	   the port of the remote host to connect
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, options, null, false, null, getDefaultWorkerpool(), null);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param address                the remote host
	 * @param port	          	     the port of the remote host to connect
	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, options, null, false, null, getDefaultWorkerpool(), null);
	}





	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address          the remote address
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), null);
	}


	/**
     * constructor. This constructor will be used to create a non blocking
     * client-side connection.<br><br>
     *
     *
     * @param address          the remote address
     * @param port             the remote port
     * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
     * @param attachment       the attacjment or <null>
     * @throws IOException If some other I/O error occurs
     */
    public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, Object attachment) throws IOException {
        this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), attachment);
    }

	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), null);
	}

	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param waitForConnect         true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), null);
	}
	
	
	



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param sslContext             the ssl context
	 * @param sslOn                  true, if ssl should be activated
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, appHandler, getDefaultWorkerpool(), null);
	}

	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param sslContext             the ssl context
	 * @param sslOn                  true, if ssl should be activated
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, getDefaultWorkerpool(), null);
	}

	
	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param sslContext             the ssl context
	 * @param sslOn                  true, if ssl should be activated
     * @param waitForConnect         true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, getDefaultWorkerpool(), null);
	}

	
	


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, options, null, false, appHandler, getDefaultWorkerpool(), null);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, options, null, false, appHandler, getDefaultWorkerpool(), null);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 *
	 * @param address                the remote address
	 * @param port                   the remote port
	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
     * @param waitForConnect         true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
 	 * @param connectTimeoutMillis   the timeout of the connect procedure
	 * @param options                the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, options, null, false, appHandler, getDefaultWorkerpool(), null);
	}

	


	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), null);
	}


	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param hostname         the remote host
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param workerPool       the worker pool to use 
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler, Executor workerPool) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, workerPool, null);
	}





	/**
	 * constructor <br><br>
	 *
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param appHandler           the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param options              the socket options
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, IHandler appHandler, Map<String, Object> options) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, options, null, false, appHandler, getDefaultWorkerpool(), null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, null, getDefaultWorkerpool(), null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 * @param address              the remote address
	 * @param port                 the remote port
	 * @param options              the socket options
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, options, sslContext, sslOn, null, getDefaultWorkerpool(), null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), sslContext, sslOn, null, getDefaultWorkerpool(), null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection.<br><br>
	 *
	 *
	 *
	 * @param hostname             the remote host
	 * @param port                 the remote port
	 * @param options              the socket options
	 * @param sslContext           the ssl context to use
	 * @param sslOn                true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(String hostname, int port, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		this(new InetSocketAddress(hostname, port), true, Integer.MAX_VALUE, options, sslContext, sslOn, null, getDefaultWorkerpool(), null);
	}



	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 *
	 * @param address          the remote address
	 * @param port             the remote port
	 * @param appHandler       the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param workerPool       the worker pool to use
	 * @throws IOException If some other I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, workerPool, null);
	}


	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 * 
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param connectTimeoutMillis  the timeout of the connect procedure  
	 * @param workerPool            the worker pool to use
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, workerPool, null);
	}
	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 * 
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
     * @param waitForConnect        true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
	 * @param connectTimeoutMillis  the timeout of the connect procedure  
	 * @param workerPool            the worker pool to use
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, new HashMap<String, Object>(), null, false, appHandler, workerPool, null);
	}


	
	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)r
	 * @param connectTimeoutMillis  the timeout of the connect procedure 
	 * @param sslContext            the ssl context to use
	 * @param sslOn                 true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @param workerPool            the worker pool to use
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), true, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, workerPool, null);
	}
	
	
	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 *
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)r
     * @param waitForConnect        true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
	 * @param connectTimeoutMillis  the timeout of the connect procedure 
	 * @param sslContext            the ssl context to use
	 * @param sslOn                 true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
	 * @param workerPool            the worker pool to use
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, SSLContext sslContext, boolean sslOn, Executor workerPool) throws IOException {
		this(new InetSocketAddress(address, port), waitForConnect, connectTimeoutMillis, new HashMap<String, Object>(), sslContext, sslOn, appHandler, workerPool, null);
	}
	
	
	/**
     * constructor. This constructor will be used to create a non blocking
     * client-side connection. <br><br>
     *
     * @param remoteAddress         the remote address
     * @param localAddress          the localAddress
     * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)r
     * @param waitForConnect        true, if the constructor should block until the connection is established. If the connect fails, the handler's onData and onDisconnect method will be called (if present) 
     * @param connectTimeoutMillis  the timeout of the connect procedure 
     * @param options               the socket options
     * @param sslContext            the ssl context to use
     * @param sslOn                 true, activate SSL mode. false, ssl can be activated by user (see {@link IReadWriteableConnection#activateSecuredMode()})
     * @throws IOException if a I/O error occurs
     */
    public NonBlockingConnection(InetSocketAddress remoteAddress, InetSocketAddress localAddress, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
        this(remoteAddress, localAddress, waitForConnect, connectTimeoutMillis, options, sslContext, sslOn, appHandler, getDefaultWorkerpool(), DEFAULT_AUTOFLUSH, DEFAULT_FLUSH_MODE, null);
    }
    
	

	/**
	 * constructor. This constructor will be used to create a non blocking
	 * client-side connection. <br><br>
	 * 
	 * @param address               the remote address
	 * @param port                  the remote port
	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param autoflush             the auto flush mode
	 * @param flushmode             the flush mode
	 * @throws IOException if a I/O error occurs
	 */
	public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean autoflush, FlushMode flushmode) throws IOException {
		this(new InetSocketAddress(address, port), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), autoflush, flushmode, null);
	}



    /**
     * constructor. This constructor will be used to create a non blocking
     * client-side connection. <br><br>
     * 
     * @param address               the remote address
     * @param port                  the remote port
     * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
     * @param autoflush             the auto flush mode
     * @param flushmode             the flush mode
     * @param attachment            the attachment or <null> 
     * @throws IOException if a I/O error occurs
     */
    public NonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean autoflush, FlushMode flushmode, Object attachment) throws IOException {
        this(new InetSocketAddress(address, port), null, true, Integer.MAX_VALUE, new HashMap<String, Object>(), null, false, appHandler, getDefaultWorkerpool(), autoflush, flushmode, attachment);
    }

	/**
	 *  intermediate client constructor, which uses a specific dispatcher
	 */
	NonBlockingConnection(InetSocketAddress remoteAddress, boolean waitForConnect, int connectTimeoutMillis, Map<String, Object> options, SSLContext sslContext, boolean sslOn, IHandler appHandler, Executor workerpool, Object attachment) throws IOException {
		this(remoteAddress, null, waitForConnect, connectTimeoutMillis, options, sslContext, sslOn, appHandler, workerpool, DEFAULT_AUTOFLUSH, DEFAULT_FLUSH_MODE, attachment);
	}


	/**
	 * 客户端的构造,使用指定的调度器.		</br>
	 * client constructor, which uses a specific dispatcher
	 * 
	 * @param remoteAddress			要连接的地址
	 * @param localAddress			本地地址,可以为null
	 * @param waitForConnect		同步还是异步
	 * @param connectTimeoutMillis	连接超时时间
	 * @param options				Socket选项
	 * @param sslContext
	 * @param isSecured
	 * @param appHdl				客户端的处理器
	 * @param workerpool
	 * @param autoflush				是否自动刷新
	 * @param flushmode				刷新模式
	 * @param attachment			附件,可为null
	 */
	private NonBlockingConnection(InetSocketAddress remoteAddress, InetSocketAddress localAddress, boolean waitForConnect, int connectTimeoutMillis, final Map<String, Object> options, final SSLContext sslContext, boolean isSecured,  IHandler appHdl, Executor workerpool, boolean autoflush, FlushMode flushmode, Object attachment) throws IOException {
		setFlushmode(flushmode);
		setAutoflush(autoflush);
		setWorkerpool(workerpool);
		
		setAttachment(attachment);
		
		handlerAdapterRef.set(HandlerAdapter.newInstance(appHdl));
		isServerSide = false;
		
		// 创建客户端SocketChannel
		SocketChannel channel = openSocket(localAddress, options);
		
		// 创建IoConnector线程(打开Selector),并启动
		IoConnector connector = getDefaultConnector();
		
		// XXX connectAsync() -> 添加连接任务 -> 唤醒IoConnector#run()处的等待, 处理连接事件 
		// -> 连接成功, 触发IIoConnectorCallback的onConnectionEstablished()回调
		// -> IoSocketHandler#init()方法,注册OP_READ事件
		// 同步连接, 可先看这个
		// sync connect
		if (waitForConnect && (connectTimeoutMillis > 0)) {
		    SyncIoConnectorCallback callback = new SyncIoConnectorCallback(remoteAddress, channel, sslContext, isSecured, connectTimeoutMillis);
		    connector.connectAsync(channel, remoteAddress, connectTimeoutMillis, callback);
		    callback.connect();

        // async connect			
		} else {
		    IIoConnectorCallback callback = new AsyncIoConnectorCallback(remoteAddress, channel, sslContext, isSecured, connectTimeoutMillis);
		    connector.connectAsync(channel, remoteAddress, connectTimeoutMillis, callback);
		}
	}

	
	private final class SyncIoConnectorCallback implements IIoConnectorCallback, IIoHandlerCallback {

		private final AtomicBoolean isConnected = new AtomicBoolean(false);
	    private final InetSocketAddress remoteAddress;
	    private final SocketChannel channel;
	    private final SSLContext sslContext;
	    private final boolean isSecured;
	    private final long connectTimeoutMillis;
	    
	    private boolean isOperationClosed = false;
	    private IOException ioe = null;
	    
	   
	    public SyncIoConnectorCallback(InetSocketAddress remoteAddress, SocketChannel channel, SSLContext sslContext, boolean isSecured, long connectTimeoutMillis) {
	        this.remoteAddress = remoteAddress;
	        this.channel = channel;
	        this.sslContext = sslContext;
	        this.isSecured = isSecured;
	        this.connectTimeoutMillis = connectTimeoutMillis;
        }
	    
	    
        void connect() throws IOException {
            
            synchronized (this) {
                while (!isOperationClosed) {
                    try {
                        wait(500);
                    } catch (InterruptedException ie) {
                    	// Restore the interrupted status
                        Thread.currentThread().interrupt();
                    }
                }
            } 
            
            if (ioe != null) {
                throw ioe;
            }            
        }

	    
        private void notifyWaiting() {
            synchronized (this) {
                isOperationClosed = true;
                this.notifyAll();
            }
        }
        

	    
	    ////////////////////////////////////
	    // IIoConnectorCallback methods

        /**
         * {@link IoConnector#handleConnect()} 处被调用.
         */
        @Override
	    public void onConnectionEstablished() throws IOException {
	    	assert (ConnectionUtils.isConnectorThread());
	    	// 创建IoHandler, 注册OP_READ事件
            register(channel, sslContext, isSecured, this);     
        }        
	       
        @Override
        public void onConnectError(IOException ioe) {
            this.ioe = ioe;
            
            NonBlockingConnection.this.onConnectException(ioe);
            notifyWaiting();
        }
        
        @Override
        public void onConnectTimeout() {
            this.ioe = new SocketTimeoutException("connect timeout " + DataConverter.toFormatedDuration(connectTimeoutMillis) +  " occured by connecting " + remoteAddress);
 
            NonBlockingConnection.this.onConnectException(ioe);
            notifyWaiting();
        }
        

        
        ////////////////
        // IIoHandlerCallback  methods 
        @Override
        public void onConnect() {
        	if (!isConnected.getAndSet(true)) {
	        	// assign the  native callback handler
	        	ioHandler.setPreviousCallback(ioHandlerCallback);
	        	
	        	notifyWaiting();
	        	ioHandlerCallback.onConnect();
        	}
        }

        @Override
        public void onConnectException(IOException ioe) {
        	ioHandlerCallback.onConnectException(ioe);
        }
        
        @Override
        public void onConnectionAbnormalTerminated() {
        	ioHandlerCallback.onConnectionAbnormalTerminated();        	
        }
        
        
        public void onData(ByteBuffer[] data, int size) {
        	ioHandlerCallback.onData(data, size);
        }
        
        public void onPostData() {
        	ioHandlerCallback.onPostData();
        }
        
        
        public void onWritten(ByteBuffer data) {
        	ioHandlerCallback.onWritten(data);
        }
        
        public void onWriteException(IOException ioException, ByteBuffer data) {
        	ioHandlerCallback.onWriteException(ioException, data);
        }
        
        
        public void onDisconnect() {
        	ioHandlerCallback.onDisconnect();
        }
	}
	
	
	private final class AsyncIoConnectorCallback implements IIoConnectorCallback {
        
	    private final InetSocketAddress remoteAddress;
        private final SocketChannel channel;
        private final SSLContext sslContext;
        private final boolean isSecured;
        private final long connectTimeoutMillis;
        
       
        public AsyncIoConnectorCallback(InetSocketAddress remoteAddress, SocketChannel channel, SSLContext sslContext, boolean isSecured, long connectTimeoutMillis) {
            this.remoteAddress = remoteAddress;
            this.channel = channel;
            this.sslContext = sslContext;
            this.isSecured = isSecured;
            this.connectTimeoutMillis = connectTimeoutMillis;
        }
        

        public void onConnectionEstablished() throws IOException {
            register(channel, sslContext, isSecured, ioHandlerCallback);                   
        }
        
        public void onConnectError(IOException ioe) {
            onConnectException(ioe);
        }
        
        public void onConnectTimeout() {
            onConnectException(new SocketTimeoutException("connect timeout " + DataConverter.toFormatedDuration(connectTimeoutMillis) +  " occured by connecting " + remoteAddress));
        }
    }




	/**
	 * 服务器端构造.先执行静态代码块		</br>
	 * {@link Server.LifeCycleHandler#onConnectionAccepted(IoChainableHandler)} 处被调用	</br></br>
	 * 
	 *  server-side constructor
	 */
	protected NonBlockingConnection(ConnectionManager connectionManager, HandlerAdapter hdlAdapter) throws IOException {
		handlerAdapterRef.set(hdlAdapter);
		
		isServerSide = true;
		isConnected.set(true);
		
		// 超时处理
		timeoutMgmHandle = connectionManager.register(this);
	}

	

	/**
	 * 只是给客户端的连接使用的.		</br>
	 * {@link NonBlockingConnection.SyncIoConnectorCallback#onConnectionEstablished() }处连接建立成功后调用. </br></br>
	 * 
	 * will be used for client-side connections only 
	 */
	private void register(SocketChannel channel, SSLContext sslContext, boolean isSecured, IIoHandlerCallback handleCallback) throws IOException, SocketTimeoutException {
		IoChainableHandler ioHdl = createClientIoHandler(channel, sslContext, isSecured);
		
		timeoutMgmHandle = DEFAULT_CONNECTION_MANAGER.register(this);
		
		init(ioHdl, handleCallback);

		// "true" set of the timeouts
		setIdleTimeoutMillis(idleTimeoutMillis);
		setConnectionTimeoutMillis(connectionTimeoutMillis);
	}
	
	
	
	SerializedTaskQueue getTaskQueue() {
	    return taskQueue;
	}


	long getLastTimeReceivedMillis() {
		return ioHandler.getLastTimeReceivedMillis();
	}
	
	long getLastTimeSendMillis() {
	    return ioHandler.getLastTimeSendMillis();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public int getMaxReadBufferThreshold() {
		if (maxReadBufferSize == null) {
			return Integer.MAX_VALUE;
		} else {
			return maxReadBufferSize;
		}
	}
	
	
	/**
	 * 设置最大的读缓冲阀值.	</br>
	 * 此方法可以在服务器端的处理器中调用.  </br></br>
	 * 
	 * {@inheritDoc}
	 */
	public void setMaxReadBufferThreshold(int size) {
		if (size == Integer.MAX_VALUE) {
			maxReadBufferSize = null;
		} else {
			maxReadBufferSize = size;
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	/*void setMaxWriteBufferThreshold(int size) {
		
		if (size == Integer.MAX_VALUE) {
			maxWriteBufferSize = null;
		} else {
			maxWriteBufferSize = size;
		}
	}*/
	
	

	/**
	 * returns the default workerpool 
	 *  
	 * @return  the default worker pool
	 */
	synchronized static Executor getDefaultWorkerpool() {
		if (defaultWorkerPool == null) {
			defaultWorkerPool = Executors.newCachedThreadPool(new DefaultThreadFactory());
		}

		return defaultWorkerPool;
	}

	
	/**
	 * 私有构造函数中被调用.		</br></br>
	 * 
     * returns the default connector 
     *  
     * @return  the default connector
     */
    private synchronized static IoConnector getDefaultConnector() {
        if (defaultConnector == null) {
            defaultConnector = new IoConnector("default");

            // 创建IoConnector线程,并启动
            Thread t = new Thread(defaultConnector);
            t.setDaemon(true);
            t.start();
        }

        return defaultConnector;
    }
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean isMoreInputDataExpected() {
		if (ioHandler != null) {
			return ioHandler.isOpen();
		} else {
			return false;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean isDataWriteable() {
		if (ioHandler != null) {
			return ioHandler.isOpen();
		} else {
			return false;
		}
	}

	
	/**
	 * 初始化连接.	</br>
	 * {@link Server.LifeCycleHandler#init(NonBlockingConnection, IoChainableHandler)}
	 */
	void init(IoChainableHandler ioHandler) throws IOException, SocketTimeoutException {
		init(ioHandler, ioHandlerCallback);
	}
	
	private void init(IoChainableHandler ioHandler, IIoHandlerCallback handlerCallback) throws IOException, SocketTimeoutException {
		this.ioHandler = ioHandler;
		
		// IoSocketHandler
		// OP_READ事件
		ioHandler.init(handlerCallback);
		
		isConnected.set(true);

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + getId() + "] connection " + getId() + " created. IoHandler: " + ioHandler.toString());
		}
	}
	


	/**
	 * {@inheritDoc}
	 */
	public void setHandler(IHandler hdl) throws IOException {	

	    HandlerAdapter oldHandlerAdapter = handlerAdapterRef.get();
	    IHandlerChangeListener changeListener = handlerReplaceListenerRef.get();

	    // call old handler change listener  
        if ((changeListener != null) && (oldHandlerAdapter != null)) {
            IHandler oldHandler = oldHandlerAdapter.getHandler();
            changeListener.onHanderReplaced(oldHandler, hdl);
        }


        boolean isChangeListener = false;
        if (hdl != null) {
            isChangeListener = (hdl instanceof IHandlerChangeListener);
        }
        HandlerAdapter adapter = HandlerAdapter.newInstance(hdl);
        
        boolean callDisconnect = false;
        synchronized (disconnectedGuard) {
            handlerAdapterRef.set(adapter);
            if (isChangeListener) {
                handlerReplaceListenerRef.set((IHandlerChangeListener) hdl);
            }
            
            if (isDisconnected.get()) {
                callDisconnect = true;
            }
        }
        
        // is data available
        if (getReadQueueSize() > 0) {
            adapter.onData(NonBlockingConnection.this, taskQueue, workerpool, false, false);
        }

        // is disconnected
        if (callDisconnect) {
            adapter.onDisconnect(NonBlockingConnection.this, taskQueue, workerpool, false);             
        }
	}

	
	/**
	 * {@inheritDoc}
	 */
	public IHandler getHandler() {	
		HandlerAdapter hdlAdapter = handlerAdapterRef.get();
		if (hdlAdapter == null) {
			return null;
		} else {
			return hdlAdapter.getHandler();
		}
	}
	
	/**
	 * {@link Server.LifeCycleHandler#init(NonBlockingConnection, IoChainableHandler) }		</br>
	 * 
	 * sets the worker pool 
	 * @param workerpool  the worker pool
	 */
	public void setWorkerpool(Executor workerpool) {
		this.workerpool = workerpool;
	}


	
	/**
	 * gets the workerpool
	 * 
	 * @return the workerpool
	 */
	public Executor getWorkerpool() {
		return workerpool;
	}
	
	
	Executor getExecutor() {
	    return workerpool;
	}

	/**
	 * pool support (the connection- and idle timeout and handler will not be reset)
	 *
	 * @return true, if connection has been reset
	 */
	protected boolean reset() {
		try {
			if (writeCompletionManager.reset() == false) {
			    return false;
			}
			
			if (getReadQueueSize() > 0){
				return false;
			}
			
			if (getPendingWriteDataSize() > 0){
                return false;
            }
			
			if (!synchronWriter.isReusable()){
				return false;
			}
			
			boolean isReset = ioHandler.reset();
			if (!isReset) {
				return false;
			}
	
			return super.reset();

		} catch (Exception e) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] error occured by reseting connection " + getId() + " " + e.toString());
			}
			return false;
		}
	}






	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
	    if (isDisconnected.get() && (getReadQueueSize() == 0)) {
	        return false;
	    }

	    return isOpen.get();
	}


	
	public boolean isServerSide() {
	    return isServerSide;
	}
	
	
	boolean isConnected() {
		return isConnected.get();
	}
	
	
	
	/**
	 * 将接收到的数据追加到读缓冲.		</br></br>
	 * 
	 * {@link IoHandlerCallback#onData(ByteBuffer[], int)}
	 */
	private void onData(ByteBuffer[] data, int size) {
	    /**
	     * if data is received, the data will be appended to the read buffer by 
	     * using the super method appendDataToReadBuffer. Within the super method
	     * the onPostAppend method of this class will be called, before the 
	     * super method returns.
	     * 
	     * The onPostAppend method implementation checks if the max read buffer 
	     * size is reached. If this is true, the receiving will be suspended
	     * 
	     * The caller (e.g. IoSocketHandler) will always perform the onPostData method 
	     * the after calling this method. Within the onPostData the handler's onData 
	     * method will be called
	     * 
	     * 
	     * 如果接受到数据,将使用父类的appendDataToReadBuffer()方法将数据追加到读缓冲.
	     * 在父类方法中,这个类的onPostAppend()方法将被调用在父类方法返回之前.
	     * 
	     * onPostAppend()方法检查最大的读缓冲是否达到.如果为true,接收将中断.
	     * 
	     * 调用者(比如,IoSocketHandler)将总是执行在调用这个方法之后执行onPostData()方法.
	     * 在onPostData()方法中handler的onData()方法将被调用.
	     */
	    
		if (data != null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] adding " + size + " to read buffer");
			}
			super.appendDataToReadBuffer(data, size);
		}
	}
	
	/**
	 * {@link AbstractNonBlockingStream#appendDataToReadBuffer(ByteBuffer[], int)}	</br>
	 * 
	 * 将在接收到的数据加入到ReadQueue之后被调用.
	 * 将检查最大的读缓冲是否已经能达到.如果达到,接收将暂停.	</br></br>
	 * 
     * this method will be called within the I/O thread after the recevied data 
     * is append to the read buffer. Here it will be check if the max read buffer 
     * size is reached. If this is true, receiving will be suspended
     * 
     * To avoid race conditions the code is protected by the suspend guard
     */
    @Override
    protected void onPostAppend() {

        // auto suspend support?
    	// 是否支持终止
//    	System.out.println("maxReadBufferSize：" + maxReadBufferSize);
        if (maxReadBufferSize != null) {
            
            // check if receiving has to be suspended
            synchronized (suspendGuard) {
                if (getReadQueueSize() >= maxReadBufferSize) {
                    try {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("[" + getId() + "] suspending read, because max read buffers size " + maxReadBufferSize + " is execced (" + getReadQueueSize() + ")");
                        }
                            
                        ioHandler.suspendRead();
                    } catch (IOException ioe) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("[" + getId() + "] error occured by suspending read (cause by max read queue size " + maxReadBufferSize + " " + ioe.toString());
                        }
                    }
                }
            }
        }   
    }

    
    
    @Override
    protected ByteBuffer[] onRead(ByteBuffer[] readBufs) throws IOException {
        
       /**
         * this method will be called after data is read from the read buffer. 
         * If the read buffer size lower than the max read buffer size 
         * the receiving will be resumed (if suspended)
         * 
         * To avoid race conditions the code is protected by the suspend guard  
         */


        // auto suspend support?
        if (maxReadBufferSize != null) {
            
            // check if receiving has to be resumed
            synchronized (suspendGuard) {
                if (ioHandler.isReadSuspended() && ((getReadQueueSize() < maxReadBufferSize))) {
                    try {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("[" + getId() + "] resuming read, because read buffer size is lower than max read buffers size " + maxReadBufferSize);
                        }

                        if (!isSuspended.get()) {
                            ioHandler.resumeRead();
                        }
                    } catch (IOException ioe) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("[" + getId() + "] error occured by suspending read (cause by max read queue size " + maxReadBufferSize + " " + ioe.toString());
                        }
                    }
                }
            }
        }
        
        return readBufs;
    }
    
	

    /**
     * {@link IoHandlerCallback#onPostData()}	</br>
     * 
     * 这个方法在调用onData()方法后调用.
     * 执行hander的onData().交由线程池去处理.
     * </br></br>
     * 
     * This method will be called (by IoSocketHandler) after the onData method 
     * is called
     */
	private void onPostData() {
	    handlerAdapterRef.get().onData(NonBlockingConnection.this, taskQueue, workerpool, false, false);
	}
	
	
	
	private void onWritten(ByteBuffer data) {
		synchronWriter.onWritten(data);
		writeCompletionManager.onWritten(data);
	}

	
	
	private void onWriteException(IOException ioException, ByteBuffer data) {
		isOpen.set(false);
		
		synchronWriter.onException(ioException);
		writeCompletionManager.onWriteException(ioException, data);
	}
	
	
	private void onConnect() {
		try {
			handlerAdapterRef.get().onConnect(NonBlockingConnection.this, taskQueue, workerpool, false);
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] error occured by performing onConnect callback on " + handlerAdapterRef.get() + " " + ioe.toString());
			}
		}
	}
	
	
	private void onConnectionAbnormalTerminated() {
		forceClose();
	}
	
	
	private void onDisconnect() {
	    
		HandlerAdapter adapter = null;
		synchronized (disconnectedGuard) {
		    if (!isDisconnected.getAndSet(true)) {
		        adapter = handlerAdapterRef.get();
		    }
		}

        isConnected.set(false);
	        
		TimeoutMgmHandle hdl = timeoutMgmHandle; 
		if (hdl != null) {
		    hdl.destroy();
		}
	        
		if (adapter != null) {
            // call first onData
		    adapter.onData(NonBlockingConnection.this, taskQueue, workerpool, true, false);
    
            // then onDisconnect
            adapter.onDisconnect(NonBlockingConnection.this, taskQueue, workerpool, false);
		}
	}

	
	private void onConnectException(IOException ioe) {
	    
	    if (LOG.isLoggable(Level.FINE)) {
	        LOG.fine("connecting failed " + ioe.toString());
	    }
	    
        if (timeoutMgmHandle != null) {
            timeoutMgmHandle.destroy();
        }
        
        if (!connectExceptionOccured.getAndSet(true)) {
            try {
                handlerAdapterRef.get().onConnectException(NonBlockingConnection.this, taskQueue, workerpool, ioe);
            } catch (IOException e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + getId() + "] error occured by performing onDisconnect callback on " + handlerAdapterRef.get() + " " + e.toString());
                }
            }
        }
    }
	
	
	
	boolean checkIdleTimeout(Long currentMillis) {
		if ((idleTimeoutMillis != IConnection.MAX_TIMEOUT_MILLIS) && (getRemainingMillisToIdleTimeout(currentMillis) <= 0)) {
			onIdleTimeout();
			return true;
		}
		return false;
	}

	
	void onIdleTimeout() {
		if (!idleTimeoutOccured.getAndSet(true)) {
			try {
				handlerAdapterRef.get().onIdleTimeout(NonBlockingConnection.this, taskQueue, workerpool);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured by performing onIdleTimeout callback on " + handlerAdapterRef.get() + " " + ioe.toString());
				}
			}
		} else {
			setIdleTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
		}
	}


		

	boolean checkConnectionTimeout(Long currentMillis) {
		
		if ((connectionTimeoutMillis != IConnection.MAX_TIMEOUT_MILLIS) && (getRemainingMillisToConnectionTimeout(currentMillis) <= 0)) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + getId() + "] connection timeout occured");
			}
			onConnectionTimeout();
			return true;
		}
		
		return false;
	}
	
	
	private void onConnectionTimeout() {
		if (!connectionTimeoutOccured.getAndSet(true)) {
			try {
				handlerAdapterRef.get().onConnectionTimeout(NonBlockingConnection.this, taskQueue, workerpool);
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] error occured by performing onConnectionTimeout callback on " + handlerAdapterRef.get() + " " + ioe.toString());
				}
			}
		} else {
			setConnectionTimeoutMillis(IConnection.MAX_TIMEOUT_MILLIS);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToConnectionTimeout() {
		return getRemainingMillisToConnectionTimeout(System.currentTimeMillis());
	}


	/**
	 * {@inheritDoc}
	 */
	public long getRemainingMillisToIdleTimeout() {
		return getRemainingMillisToIdleTimeout(System.currentTimeMillis());
	}


	private long getRemainingMillisToConnectionTimeout(long currentMillis) {
		return connectionTimeoutDateMillis - currentMillis;
	}


	public long getNumberOfReceivedBytes() {
		return ioHandler.getNumberOfReceivedBytes();
	}
	
	public long getNumberOfSendBytes() {
		return ioHandler.getNumberOfSendBytes();
	}
	

	private long getRemainingMillisToIdleTimeout(long currentMillis) {
		
		long remaining = idleTimeoutDateMillis - currentMillis;

		// time out not received
		if (remaining > 0) {
			return remaining;

		// time out received
		} else {

			// ... but check if meantime data has been received!
			return (Math.max(getLastTimeReceivedMillis(), getLastTimeSendMillis()) + idleTimeoutMillis) - currentMillis;
		}
	}
	
	String getRegisteredOpsInfo() {
		return ioHandler.getRegisteredOpsInfo();
	}
	
	


	/**
	 * {@inheritDoc}
	 */
	public void setWriteTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException {

		if ((bytesPerSecond != UNLIMITED) && ((getFlushmode() != FlushMode.ASYNC))) {
			LOG.warning("setWriteTransferRate is only supported for FlushMode ASYNC. Ignore update of the transfer rate");
			return;
		}

		if (this.bytesPerSecond == bytesPerSecond) {
			return;
		}
		
		this.bytesPerSecond = bytesPerSecond;
		ioHandler = ConnectionUtils.getIoProvider().setWriteTransferRate(ioHandler, bytesPerSecond);
	}
 

	/**
	 * {@inheritDoc}
	 */
	public int getWriteTransferRate() throws ClosedChannelException, IOException {
		return bytesPerSecond;
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isSecuredModeActivateable() {
		return ConnectionUtils.getIoProvider().isSecuredModeActivateable(ioHandler);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void activateSecuredMode() throws IOException {

		boolean isPrestarted = ConnectionUtils.getIoProvider().preActivateSecuredMode(ioHandler);

		if (isPrestarted) {
			FlushMode currentFlushMode = getFlushmode();
			setFlushmode(FlushMode.ASYNC);
			internalFlush(null);
			setFlushmode(currentFlushMode);

			ByteBuffer[] buffer = readByteBufferByLength(available());
			ConnectionUtils.getIoProvider().activateSecuredMode(ioHandler, buffer);
		}
	}
	
	
	public void deactivateSecuredMode() throws IOException {
		FlushMode currentFlushMode = getFlushmode();
		setFlushmode(FlushMode.ASYNC);
		internalFlush(null);
		setFlushmode(currentFlushMode);

		ConnectionUtils.getIoProvider().deactivateSecuredMode(ioHandler);
		
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean isSecure() {
		return ioHandler.isSecure();
	}


	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
		try {
			return super.readByteBufferByDelimiter(delimiter, encoding, maxLength);

		} catch (MaxReadSizeExceededException mre) {
			if (isOpen()) {
				throw mre;

			} else {
				throw new ClosedChannelException();
			}
			
		} catch (BufferUnderflowException bue) {
			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedChannelException();
			}
		} 
	}



	/**
	 * {@inheritDoc}
	 */
	public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException {
		try {
			return super.readByteBufferByLength(length);

		} catch (BufferUnderflowException bue) {
			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedChannelException();
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	protected ByteBuffer readSingleByteBuffer(int length) throws IOException, ClosedChannelException, BufferUnderflowException {
		try {
			return super.readSingleByteBuffer(length);

		} catch (BufferUnderflowException bue) {

			if (isOpen()) {
				throw bue;

			} else {
				throw new ClosedChannelException();
			}
		}
	}




	/**
	 * {@inheritDoc}
	 *
	 */
	public void setIdleTimeoutMillis(long timeoutMillis) {
		idleTimeoutOccured.set(false);
	
		
		if (timeoutMillis <= 0) {
			LOG.warning("idle timeout " + timeoutMillis + " millis is invalid");
			return;
		}

		this.idleTimeoutMillis = timeoutMillis;
		this.idleTimeoutDateMillis = System.currentTimeMillis() + idleTimeoutMillis;
		
		if (idleTimeoutDateMillis < 0) {
			idleTimeoutDateMillis = Long.MAX_VALUE;
		}

		
		if ((timeoutMillis != IConnection.MAX_TIMEOUT_MILLIS) && (isConnected.get())) {
			long period = idleTimeoutMillis;
			if (idleTimeoutMillis > 500) {
				period = idleTimeoutMillis / 5;
			}
			
			timeoutMgmHandle.updateCheckPeriod(period);
		}
	}
	


	/**
	 * {@inheritDoc}
	 */
	public void setConnectionTimeoutMillis(long timeoutMillis) {
		connectionTimeoutOccured.set(false);
	
		if (timeoutMillis <= 0) {
			LOG.warning("connection timeout " + timeoutMillis + " millis is invalid");
			return;
		}

		this.connectionTimeoutMillis = timeoutMillis;
		this.connectionTimeoutDateMillis = System.currentTimeMillis() + connectionTimeoutMillis;


		if ((timeoutMillis != IConnection.MAX_TIMEOUT_MILLIS) && (isConnected.get())) {
			long period = connectionTimeoutMillis;
			if (connectionTimeoutMillis > 500) {
				period = connectionTimeoutMillis / 5;
			}
			
			timeoutMgmHandle.updateCheckPeriod(period);
		}
	}

	

	/**
	 * {@inheritDoc}
	 */
	public long getConnectionTimeoutMillis() {
		return connectionTimeoutMillis;
	}



	/**
	 * {@inheritDoc}
	 *
	 */
	public long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}



	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return ioHandler.getLocalAddress();
	}



	/**
	 * {@inheritDoc}
	 */
	public String getId() {
		return ioHandler.getId();
	}




	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return ioHandler.getLocalPort();
	}



	/**
	 * {@inheritDoc}
	 */
	public InetAddress getRemoteAddress() {
		return ioHandler.getRemoteAddress();
	}



	/**
	 * {@inheritDoc}
	 */
	public int getRemotePort() {
		return ioHandler.getRemotePort();
	}


	/**
	 * {@inheritDoc}
	 */
	public int getPendingWriteDataSize() {
		return getWriteBufferSize() + ioHandler.getPendingWriteDataSize();
	}


	/**
	 * {@inheritDoc}
	 */
	public void suspendReceiving() throws IOException {
	    synchronized (suspendGuard) {
	        ioHandler.suspendRead();
	        isSuspended.set(true);
	    }
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isReceivingSuspended() {
		return isSuspended.get();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void resumeReceiving() throws IOException {
	    synchronized (suspendGuard) {
	    	if (isReceivingSuspended()) {
		        ioHandler.resumeRead();
		        isSuspended.set(false);
			
		        if (getReadQueueSize() > 0) {
		            ioHandlerCallback.onPostData();
		        }
	    	}
	    }
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
        
        if (!IS_SUPPRESS_SYNC_FLUSH_COMPLITIONHANDLER_WARNING && (getFlushmode() == FlushMode.SYNC)) {
            String msg = "synchronized flush mode/completion handler combination could cause raced conditions (hint: set flush mode to ASYNC). This message can be suppressed by setting system property " + IoProvider.SUPPRESS_SYNC_FLUSH_COMPLETION_HANDLER_WARNING_KEY;
            LOG.warning("[" + getId() + "] " + msg);
        }
        
        synchronized (asyncWriteGuard) {
            boolean isSuppressReuseBuffer = isSuppressReuseBufferWarning();
            setSuppressReuseBufferWarning(true);
            
            writeCompletionManager.registerCompletionHandler(writeCompletionHandler, buffer);
            write(buffer);
            
            setSuppressReuseBufferWarning(isSuppressReuseBuffer);
        }
    }
    
    
    /**
     * {@inheritDoc}
     */
    public void write(ByteBuffer[] buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {


        synchronized (asyncWriteGuard) {
            boolean isSuppressReuseBuffer = isSuppressReuseBufferWarning();
            setSuppressReuseBufferWarning(true);
    
            writeCompletionManager.registerCompletionHandler(writeCompletionHandler, buffers);
            write(buffers);
            
            setSuppressReuseBufferWarning(isSuppressReuseBuffer);
        }
    }

    
    
    /**
     * {@inheritDoc}
     */
    public void write(List<ByteBuffer> buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
        write(buffers.toArray(new ByteBuffer[buffers.size()]), writeCompletionHandler);
    }

	

    public long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException, BufferOverflowException {
        if (getFlushmode() == FlushMode.SYNC) {
            return transferFromSync(source);
            
        } else {
            return super.transferFrom(source, chunkSize);
        }
    };
    

    private long transferFromSync(ReadableByteChannel sourceChannel) throws ClosedChannelException, IOException, SocketTimeoutException, ClosedChannelException {
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("transfering data by using WriteCompletionHandler");
        }
        
        boolean isSuppressReuseBufferWarning = isSuppressReuseBufferWarning();
        
        try {
            setSuppressReuseBufferWarning(true);
            setFlushmode(FlushMode.ASYNC);
        
            final Object guard = new Object();
            final ByteBuffer copyBuffer = ByteBuffer.allocate(getSoSndBufSize());

            final SendTask sendTask = new SendTask(guard, sourceChannel, copyBuffer);
            sendTask.onWritten(0);
            
            synchronized (guard) {
                if (!sendTask.isComplete()) {
                    try {
                        guard.wait(sendTimeoutMillis);
                    } catch (InterruptedException ie) {
                    	// Restore the interrupted status
                        Thread.currentThread().interrupt();
                    	
                        closeQuietly(NonBlockingConnection.this);
                        throw new SocketTimeoutException("timeout reached");
                    }
                }
            }
        
            if (sendTask.getException() != null) {
                throw sendTask.getException();
            } 
            
            return sendTask.getWritten();
            
        } finally {
            setSuppressReuseBufferWarning(isSuppressReuseBufferWarning);
            setFlushmode(FlushMode.SYNC);
        }
    }

    
    
    @Execution(Execution.NONTHREADED)
    private final class SendTask implements IWriteCompletionHandler {
        
        private final Object guard;
        private final ReadableByteChannel sourceChannel;
        private final ByteBuffer copyBuffer;
        private boolean isComplete = false;
        private long written = 0;
        private IOException ioe;

        public SendTask(Object guard, ReadableByteChannel sourceChannel, ByteBuffer copyBuffer) {
            this.guard = guard;
            this.sourceChannel = sourceChannel;
            this.copyBuffer = copyBuffer;
        }
        
        
        public void onWritten(int written) {
            if (isComplete) {
                return;
            }
            
            try {
                copyBuffer.clear();
                                
                int read = sourceChannel.read(copyBuffer);
                if (read > 0) {
                    copyBuffer.flip();
                    
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("writing next chunk (" + copyBuffer.remaining() + " bytes)");
                    }
                    write(copyBuffer, this);
                    if (!isAutoflush()) {
                        flush();
                    }

                } else {
                    synchronized (guard) {
                        isComplete = true;
                        guard.notifyAll();
                    }
                }
                
            } catch (IOException ioe) {
                onException(ioe);
            }
        }
        
        public void onException(IOException ioe) {
            if (isComplete) {
                return;
            }
            
            this.ioe = ioe;
            synchronized (guard) {
                isComplete = true;
                guard.notifyAll();
            }
        }
        
        
        IOException getException() {
            return ioe;
        }
        
        boolean isComplete() {
            return isComplete;
        }
        
        long getWritten() {
            return written;
        }
    }
    
    

	private int getSoSndBufSize() throws IOException {
		if (cachedSoSndBuf == null) {
			cachedSoSndBuf = (Integer) getOption(SO_SNDBUF);
		}

		return cachedSoSndBuf;
	}


	/**
	 * 如果设置了自动刷新, 那么立即调用internalFlush()方法(OP_WRITE事件).
	 * 
	 * {@inheritDoc}
	 */
	@Override
	protected void onWriteDataInserted() throws IOException, ClosedChannelException {
		if (isAutoflush()) {
			internalFlush(null);
		}
	}




	/**
	 * {@inheritDoc}
	 */
	public Object getOption(String name) throws IOException {
		return ioHandler.getOption(name);
	}


	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("rawtypes")
	public Map<String, Class> getOptions() {
		return ioHandler.getOptions();
	}


	/**
	 * {@inheritDoc}
	 */
	public void setOption(String name, Object value) throws IOException {
		if (name.equalsIgnoreCase(SO_SNDBUF)) {
			cachedSoSndBuf = (Integer) value;
		}

		ioHandler.setOption(name, value);
	}
	
	
	@Override
	protected int getWriteTransferChunkeSize() {
		try {
			return getSoSndBufSize();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by retrieving SoSndBufSize " + ioe.toString());
			}
			return super.getWriteTransferChunkeSize();
		}
	} 



	private void forceClose() {
		try {
			isOpen.set(false);
			if (ioHandler != null) {
				ioHandler.close(true);
			}
			
			writeCompletionManager.close();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("Error occured by closing " + ioe.toString());
			}
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public void close() throws IOException {
		super.close(); 
		
		if (isOpen.getAndSet(false)) {
			if (getWriteTransferRate() != UNLIMITED) {
			    setWriteTransferRate(UNLIMITED);
			}
			
			synchronWriter.close();

			ByteBuffer[] buffers = drainWriteQueue();
			
			 if (LOG.isLoggable(Level.FINE)) {
			     if (buffers != null) {
			         LOG.fine("[" + getId() + "] closing connection -> flush all remaining data: " + DataConverter.toString(ConnectionUtils.copy(buffers)));
			     } else {
			         LOG.fine("[" + getId() + "] closing connection (no remaining data)");
			     }
			 }
			
			if (buffers != null) {
				ioHandler.write(buffers);
				ioHandler.flush();
			}
			
			if (ioHandler != null) {
			    ioHandler.close(false);
			}
			
			writeCompletionManager.close();
		}
	}
	
	
	/**
	 * closes the connection quitly 
	 */
	public void closeQuietly()  {
	    closeQuietly(this);
	}
	

	/**
	 * {@inheritDoc}
	 */
	static void closeQuietly(INonBlockingConnection con) {
		try {
			con.close();
		} catch (IOException ioe) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + con.getId() + " " + DataConverter.toString(ioe));
			}
		}
	}

	
	void performMultithreaded(Runnable task) {
	    taskQueue.performMultiThreaded(task, workerpool);
	}

	void performNonTHreeaded(Runnable task) {
	    taskQueue.performNonThreaded(task, workerpool);
	}



	/**
	 * <pre>
	 * 服务器端调用：
	 * 比如{@link helloworld.ServerHandler#onData(INonBlockingConnection)}	
	 * XXX 从这里才会去注册OP_WRITE事件.
	 * </pre>	
	 * 
	 * <pre>
	 * 客户端调用:
	 * 比如 {@link helloworld.XSocketClient#main(String[])}
	 * 客户端先做OP_WRITE事件.
	 * </pre>
	 * 
	 * {@inheritDoc}
	 */
	public void flush() throws ClosedChannelException, IOException {
		internalFlush(null);
	}

	/**
     * {@inheritDoc}
     */
    void flush(List<ByteBuffer> recoveryBuffer) throws ClosedChannelException, IOException {
        internalFlush(recoveryBuffer);
    }

	private void internalFlush(List<ByteBuffer> recoveryBuffer) throws ClosedChannelException, IOException {
		// 检查是否已关闭
		if (!isOpen.get()) {
		    if (getReadQueueSize() > 0) {
		        throw new ClosedChannelException();
		    } else {
		        return;
		    }
		}

		removeWriteMark();
		
		// is there data to flush?
		// 是否由数据要刷新 -> 检查WriteQueue中的buffers是否为null
		if (!isWriteBufferEmpty()) {
			
			// 同步刷新模式
			// sync flush mode?
    		if (getFlushmode() == FlushMode.SYNC) {
    			synchronWriter.syncWrite(recoveryBuffer);
    			
    		// 异步刷新模式, 先看这个
    		// ... no, async
    		} else {
    		    ByteBuffer[] bufs = drainWriteQueue();	// 所有写的数据
    		    
    		    if ((bufs != null) && (recoveryBuffer != null)) {
    		        for (ByteBuffer buf : bufs) {
    		            recoveryBuffer.add(buf.duplicate());
    		        }
    		    }
    		    
    		    // IoSocketHandler
    		    // 将写的数据加入到队列
    			ioHandler.write(bufs);
    			// XXX 注册OP_WRITE事件
    			// 交由IoSocketDispatcher去处理写操作
    		    ioHandler.flush();
    		}
		}
		
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("[" + getId() + "] flushed");
        }
	}
	
	
	

	private final class SynchronWriter {

	    private final AtomicBoolean isCallPendingRef = new AtomicBoolean(false); 
		private final ArrayList<ByteBuffer> pendingBuffers = new ArrayList<ByteBuffer>();
	    
		private IOException ioe = null;
		
		private int countOnWritten = 0;
		private int countOnException = 0;
		private int countUnknownOnWritten = 0;
		
		
		boolean isReusable() {
			synchronized (this) {
			    return !isCallPendingRef.get();
			}
		}
		
		
		
		void close() {
			synchronized (this) {
				pendingBuffers.clear();
			}
		}
		
	
		
		void onWritten(ByteBuffer data) {

            if (!isCallPendingRef.get()) {
                return;
            }

			synchronized (this) {
			    countOnWritten++;
    						
			    boolean isRemoved = pendingBuffers.remove(data);
			    if (!isRemoved) {
			        countUnknownOnWritten++; 
			    }
    				
			    if (pendingBuffers.isEmpty()) {
			        this.notifyAll();
			    }
			}
		}
	
		
		
		public void onException(IOException ioe) {
		    
            if (isCallPendingRef.get()) {
                return;
            }

			synchronized (this) {
			    countOnException++;
    				
			    this.ioe = ioe;
			    pendingBuffers.clear();
			    this.notifyAll();
			}
		}
		
		
		
		void syncWrite(List<ByteBuffer> recoveryBuffer) throws IOException, SocketTimeoutException {

			if (!IS_SUPPRESS_SYNC_FLUSH_WARNING && ConnectionUtils.isDispatcherThread()) {
				String msg = "synchronized flushing in NonThreaded mode could cause dead locks (hint: set flush mode to ASYNC). This message can be suppressed by setting system property " + IoProvider.SUPPRESS_SYNC_FLUSH_WARNING_KEY;
				LOG.warning("[" + getId() + "] " + msg);
			}
			
			
			long start = System.currentTimeMillis();

			
			synchronized (this) {
			    assert (pendingBuffers.isEmpty()) : "no pending buffers should exists";
			    isCallPendingRef.set(true);
			    
				// drain the queue 
    			ByteBuffer[] data = drainWriteQueue();
    			if (data == null) {
    				return;
    			}
    			
    			 if (recoveryBuffer != null) {
                     for (ByteBuffer buf : data) {
                         recoveryBuffer.add(buf.duplicate());
                     }
                 }

    			
				try {					
	    			// add the data to write into the pending list
	    			pendingBuffers.addAll(Arrays.asList(data));
	    			
	    			// write the data and flush it
	    			// IoSocketHandler
	    			ioHandler.write(data);
	    			ioHandler.flush();

					
					// wait until the response is received
					while (true) {		
						
						 // all data written?
		                 if (pendingBuffers.isEmpty()) {
		                	 if (LOG.isLoggable(Level.FINE)) {
		                		 LOG.fine("[" + getId() + "] data written");
		                	 }
		                	 return;
		                	 
		                	 
		                 // or got an exception?
		                 } else if (ioe != null) {
		                	 throw ioe;
	
		                	 
		                 // no, check send timeout
		                 } else {
			                 long remainingTime = (start + sendTimeoutMillis) - System.currentTimeMillis();
			                 if (remainingTime < 0) { 
			                	 String msg = "[" + getId() + "] send timeout " + DataConverter.toFormatedDuration(sendTimeoutMillis) + 
		                         			  " reached. returning from sync flushing (countIsWritten=" + countOnWritten + 
		                         			  ", countOnException=" + countOnException + ", sendBytes=" + ioHandler.getNumberOfSendBytes() + 
		                         			  ", receivedBytes=" + ioHandler.getNumberOfReceivedBytes() + 
		                         			  ", sendQueueSize=" + ioHandler.getPendingWriteDataSize() + ", countUnknownOnWritten=" + countUnknownOnWritten +
		                         			  ", " + ioHandler.getInfo() + ")";
			                	 if (LOG.isLoggable(Level.FINE)) {
			                		 LOG.fine(msg);
			                	 }
			                	 throw new SocketTimeoutException(msg);
			                 }
			                 
			                 // wait
			    			 try {
			                     this.wait(remainingTime);
			                 } catch (InterruptedException ie) { 
			                	 //	Restore the interrupted status
			                     Thread.currentThread().interrupt();
			                 }
		                 }	                 
					}
					
				} finally {
		            pendingBuffers.clear();
		            ioe = null;
		            
		            countOnWritten = 0;
		            countOnException = 0;
		            
		            isCallPendingRef.set(false);
				}
			} // synchronized
		}
	}

	
	@Override
	protected String getInfo() {
		return toDetailedString();
	}



	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
	    StringBuilder sb = new StringBuilder("id=" + getId());
	
	    try {
	        if (isOpen()) {
	            sb.append("remote=" + getRemoteAddress() + "(" + getRemoteAddress() + ":" + getRemotePort() + ")");
				
	        } else {
	            sb.append("(closed)");
	        }
	    } catch (Exception ignore)  { }
	    
	    return sb.toString();
	}
	
	
	String toDetailedString() {
		if (isOpen()) {
			SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss,S");

			return "id=" + getId() + ", remote=" + getRemoteAddress().getCanonicalHostName() + "(" + getRemoteAddress() + ":" + getRemotePort() + 
			       ") lastTimeReceived=" + df.format(new Date(getLastTimeReceivedMillis())) + " reveived=" + getNumberOfReceivedBytes() +
			       " lastTimeSent=" + df.format(new Date(getLastTimeSendMillis())) + 
			       " send=" + getNumberOfSendBytes() + " ops={" + getRegisteredOpsInfo() + "}";
		} else {
			return "id=" + getId() + " (closed)";
		}
	}

	
	/**
	 * 创建客户端的IoHandler	</br>
	 * {@link #register(SocketChannel, SSLContext, boolean, IIoHandlerCallback)}
	 */
	private static IoChainableHandler createClientIoHandler(SocketChannel channel, SSLContext sslContext, boolean sslOn) throws IOException {
		if (sslContext != null) {
			return ConnectionUtils.getIoProvider().createSSLClientIoHandler(channel, sslContext, sslOn);
		} else {
			return ConnectionUtils.getIoProvider().createClientIoHandler(channel);
		}
	}


	private static SocketChannel openSocket(InetSocketAddress localAddress, Map<String ,Object> options) throws IOException {
		// 客户端Socket
        SocketChannel channel = SocketChannel.open();

        for (Entry<String, Object> entry : options.entrySet()) {
            IoProvider.setOption(channel.socket(), entry.getKey(), entry.getValue());
        }
        
        if (localAddress != null) {
        	// 绑定本地地址
            channel.socket().bind(localAddress);
        }

        return channel;
    }

    

	/**
	 * {@link IoSocketHandler#onReadableEvent()}
	 */
	private final class IoHandlerCallback implements IIoHandlerCallback {

		@Override
		public void onWritten(ByteBuffer data) {
			NonBlockingConnection.this.onWritten(data);
		}
		
		@Override
		public void onWriteException(IOException ioException, ByteBuffer data) {
			NonBlockingConnection.this.onWriteException(ioException, data);
		}
		
		/**
		 * {@link IoSocketHandler#onReadableEvent()}} 处被调用
		 */
		@Override
		public void onData(ByteBuffer[] data, int size) {
			NonBlockingConnection.this.onData(data, size);
		}
		
		/**
		 * {@link IoSocketHandler#onReadableEvent()}} 处被调用
		 */
		@Override
		public void onPostData() {
			NonBlockingConnection.this.onPostData();
		}

		@Override
		public void onConnectionAbnormalTerminated() {
			NonBlockingConnection.this.onConnectionAbnormalTerminated();
		}

		/**
		 * {@link IoSocketHandler.onRegisteredEvent()}
		 */
		@Override
		public void onConnect() {
			NonBlockingConnection.this.onConnect();
		}
		
		@Override
		public void onConnectException(IOException ioe) {
		    NonBlockingConnection.this.onConnectException(ioe);		    
		}

		@Override
		public void onDisconnect() {
			NonBlockingConnection.this.onDisconnect();
		}
	}

	
	

	private static class DefaultThreadFactory implements ThreadFactory {
		private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		DefaultThreadFactory() {
			namePrefix = "xNbcPool-" + POOL_NUMBER.getAndIncrement() + "-thread-";
        }

		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
	

	
	
	final class WriteCompletionManager {

	    // write completion handler support
	    private final Map<WriteCompletionHolder, List<ByteBuffer>> pendingCompletionConfirmations = new HashMap<WriteCompletionHolder, List<ByteBuffer>>();
	    private AtomicBoolean isWriteCompletionSupportActivated = new AtomicBoolean(false);

	    
	
	        
	    void registerCompletionHandler(IWriteCompletionHandler writeCompletionHandler, ByteBuffer... buffersToWrite) {
	        
	        WriteCompletionHolder holder = new WriteCompletionHolder(writeCompletionHandler, buffersToWrite);
	        
	        synchronized (this) {
	            isWriteCompletionSupportActivated.set(true);
	            pendingCompletionConfirmations.put(holder, new ArrayList<ByteBuffer>(Arrays.asList(buffersToWrite)));
            }
	        
            if (LOG.isLoggable(Level.FINE)) {
                int size = 0;
                for (ByteBuffer byteBuffer : buffersToWrite) {
                    size += byteBuffer.remaining();
                }
                
                LOG.fine("[" + getId() + "] registering " + writeCompletionHandler.getClass().getSimpleName() + "#" + writeCompletionHandler.hashCode() + " waiting for " + size + " bytes");
            }
	    }
	    
	    
	   void onWritten(ByteBuffer[] data) {	       
	       if (isWriteCompletionSupportActivated.get()) {
	           for (ByteBuffer byteBuffer : data) {
	               onWritten(byteBuffer);
	           }
	       }
	   }   
	    	    
	    
	    
	    private void onWritten(ByteBuffer data) {

	        WriteCompletionHolder holderToExecute = null;

	        if (data != null) {
	        	
	        	synchronized (this) {
    	            for (Entry<WriteCompletionHolder, List<ByteBuffer>> entry : pendingCompletionConfirmations.entrySet()) {
    	                List<ByteBuffer> buffers = entry.getValue();
    	                for (ByteBuffer buf : buffers) {
    	                    if (buf == data) {
    	                        buffers.remove(data);
    	                        break;
    	                    }
    	                }
    	                
    	                if (buffers.isEmpty()) {
    	                    holderToExecute = entry.getKey();
    	                    pendingCompletionConfirmations.remove(holderToExecute);
    	                    break;
    	                }
    	            }
    	        }
	        }
	        
	        if (holderToExecute != null) {
	            holderToExecute.performOnWritten();
	        }
	    }

	        

	    void onWriteException(IOException ioException, ByteBuffer[] data) {
	        if (isWriteCompletionSupportActivated.get()) {
	            for (ByteBuffer byteBuffer : data) {
	                onWriteException(ioException, byteBuffer);
	            }
            }
	    }
	        

	        
	    private void onWriteException(IOException ioException, ByteBuffer data) {

	        WriteCompletionHolder holderToExecute = null;
	        
	        synchronized (this) {
    	        if (data != null) {
    	            outer : for (Entry<WriteCompletionHolder, List<ByteBuffer>> entry : pendingCompletionConfirmations.entrySet()) {
    	                
    	                List<ByteBuffer> buffers = entry.getValue();
                        for (ByteBuffer buf : buffers) {
                            if (buf == data) {
                                holderToExecute = entry.getKey();
                                pendingCompletionConfirmations.remove(holderToExecute);
                                break outer;
                            }
                        }
    	            }
    	        }
	        }
	        
	        if (holderToExecute != null) {
	            holderToExecute.performOnException(ioException);
	        }
	    }
	    
	    
        boolean reset() {
            synchronized (this) {
                if (!pendingCompletionConfirmations.isEmpty()) {
                    for (WriteCompletionHolder handler : pendingCompletionConfirmations.keySet()) {
                        handler.callOnException(new ClosedChannelException());
                    }
    
                    pendingCompletionConfirmations.clear();
                    return false;
                }
    
                return true;
            }
        }
        
	    
        void close() {
            synchronized (this) {
                for (WriteCompletionHolder holder : pendingCompletionConfirmations.keySet()) {
                    holder.performOnException(new ExtendedClosedChannelException("[" + getId() + "] is closed"));
                }
            }
	    }
	}
	


	private final class WriteCompletionHolder implements Runnable {
        
        private final IWriteCompletionHandler handler;
        private final CompletionHandlerInfo handlerInfo;
        private final int size;
        

        public WriteCompletionHolder(IWriteCompletionHandler handler, ByteBuffer[] bufs) {
            this.handler = handler;
            this.handlerInfo = ConnectionUtils.getCompletionHandlerInfo(handler);
            
            int l = 0;
            for (ByteBuffer byteBuffer : bufs) {
                l += byteBuffer.remaining();
            }
            size = l;
        }
        
        
        void performOnWritten() {
            if (handlerInfo.isOnWrittenMultithreaded()) {
                taskQueue.performMultiThreaded(this, getWorkerpool());
                
            } else {
                taskQueue.performNonThreaded(this, getWorkerpool());
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
                closeQuietly(NonBlockingConnection.this);
            }

        }

        
        void performOnException(final IOException ioe) {
            
            if (handlerInfo.isOnExceptionMutlithreaded()) {
                Runnable task = new Runnable() {
                    public void run() {
                        callOnException(ioe);
                    }
                };
                taskQueue.performMultiThreaded(task, workerpool);
                
            } else {
                Runnable task = new Runnable() {
                    public void run() {
                        callOnException(ioe);
                    }
                };
                taskQueue.performNonThreaded(task, workerpool);
            }
        }
        
        
        private void callOnException(IOException ioe) {
            handler.onException(ioe);
        }
    }
}
