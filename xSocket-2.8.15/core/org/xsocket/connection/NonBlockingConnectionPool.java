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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.SerializedTaskQueue;





/**
 * A connection pool implementation.<br> <br>
 *
 * This class is thread safe <br>
 *
 * <pre>
 *  // create a unbound connection pool
 *  NonBlockingConnectionPool pool = new NonBlockingConnectionPool();
 *
 *  INonBlockingCinnection con = null;
 *
 *  try {
 *     // retrieve a connection (if no connection is in pool, a new one will be created)
 *     con = pool.getNonBlockingConnection(host, port);
 *     con.write("Hello");
 *     ...
 *
 *     // always close the connection! (the connection will be returned into the connection pool)
 *     con.close();
 *
 * 	} catch (IOException e) {
 *     if (con != null) {
 *        try {
 *          // if the connection is invalid -> destroy it (it will not return into the pool)
 *          pool.destroy(con);
 *        } catch (Exception ignore) { }
 *     }
 *  }
 * </pre>
 *
 * @author grro@xsocket.org
 */
public final class NonBlockingConnectionPool implements IConnectionPool {

	private static final Logger LOG = Logger.getLogger(NonBlockingConnectionPool.class.getName());
	
	
	private static final long MIN_REMAINING_MILLIS_TO_IDLE_TIMEOUT = 3 * 1000;
	private static final long MIN_REMAINING_MILLIS_TO_CONNECTION_TIMEOUT = 3 * 1000;

	private static final int CONNECT_MAX_TRIALS = Integer.parseInt(System.getProperty("org.xsocket.connection.connectionpool.maxtrials", "3")); 
	private static final int CONNECT_RETRY_WAIT_TIME_MILLIS = Integer.parseInt(System.getProperty("org.xsocket.connection.connectionpool.retrywaittimemillis", "50"));
	

	// is open flag
	private final AtomicBoolean isOpen = new AtomicBoolean(true);

		
	// ssl support
	private final SSLContext sslContext;

	
	// settings
	private final AtomicInteger maxActive = new AtomicInteger(IConnectionPool.DEFAULT_MAX_ACTIVE);
	private final AtomicInteger maxActivePerServer = new AtomicInteger(IConnectionPool.DEFAULT_MAX_ACTIVE_PER_SERVER);
	private final AtomicInteger maxIdle = new AtomicInteger(IConnectionPool.DEFAULT_MAX_IDLE);
	private final AtomicInteger poolIdleTimeoutMillis = new AtomicInteger(IConnectionPool.DEFAULT_IDLE_TIMEOUT_MILLIS);
	private final AtomicInteger lifeTimeoutMillis = new AtomicInteger(IConnectionPool.DEFAULT_LIFE_TIMEOUT_MILLIS);

	
	private final Object limitGuard = new Object();
	private int numInitializingConnections = 0;
	private final Map<InetAddress, Integer> initializingConnectionMap = new HashMap<InetAddress, Integer>();


	// pool management
	private final Pool pool = new Pool();
	private final Object retrieveGuard = new Object();
	private Integer acquireTimeoutMillis = null;
	private Executor workerpool = NonBlockingConnection.getDefaultWorkerpool();

	
	// timeout checker
	private static final int DEFAULT_WATCHDOG_CHECK_PERIOD = 30 * 1000;
	private final Watchog watchdog;
	
	
	// listeners
	private final List<ILifeCycle> listeners = new ArrayList<ILifeCycle>();


	// statistics
	private final AtomicInteger countRejectedConnections = new AtomicInteger(0);
	private final AtomicInteger countUndetectedDisconnect = new AtomicInteger(0);
	private final AtomicInteger countPendingGet = new AtomicInteger(0);
	private final AtomicInteger countCreated = new AtomicInteger(0);
	private final AtomicInteger countDestroyed = new AtomicInteger(0);
	private final AtomicInteger countRemainingMillisToIdleTimeoutToSmall = new AtomicInteger(0);
	private final AtomicInteger countRemainingConnectionToIdleTimeoutToSmall = new AtomicInteger(0);
	private final AtomicInteger countCreationError = new AtomicInteger(0);
	private final AtomicInteger countIdleTimeout = new AtomicInteger(0);
	private final AtomicInteger countConnectionTimeout = new AtomicInteger(0);
	private final AtomicInteger countTimeoutPooledIdle = new AtomicInteger(0);
	private final AtomicInteger countTimeoutPooledLifetime = new AtomicInteger(0);
	
	

	/**
	 * constructor
	 *
	 */
	public NonBlockingConnectionPool() {
	    this(null);
	}

	
	/**
     * constructor
     *
     * @param sslContext   the ssl context or <code>null</code> if ssl should not be used
     */
    public NonBlockingConnectionPool(SSLContext sslContext) {
        this(sslContext, DEFAULT_WATCHDOG_CHECK_PERIOD);
    }

	
	
	/**
	 * constructor
	 *
	 * @param sslContext   the ssl context or <code>null</code> if ssl should not be used
	 */
	NonBlockingConnectionPool(SSLContext sslCtx, int watchdogCheckPeriod) {
	    
	    // autoload SSL context if not set (works only with Java 1.6 or higher)
        if (sslCtx == null) {
            try {
                Method m = SSLContext.class.getMethod("getDefault");
                sslCtx = (SSLContext) m.invoke(SSLContext.class);
                
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("default SSLContext -> SSLContext.getDefault() is loaded automatically");
                }
            } catch (Exception ignore) { }
        }
        
        sslContext = sslCtx;
        
		
		watchdog = new Watchog();
        IoProvider.getTimer().schedule(watchdog, watchdogCheckPeriod, watchdogCheckPeriod);
	}
	
	
	



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param host   the server address
	 * @param port   the server port
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(host, port), null, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS),  false);
	}




	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param host   the server address
	 * @param port   the server port
	 * @param isSSL  true, if ssl connection

	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(host, port), null, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS), isSSL);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param host                   the server address
	 * @param port                   the server port
     * @param connectTimeoutMillis   the connection timeout
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port, int connectTimeoutMillis) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(host, port), null, workerpool, true, connectTimeoutMillis, false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param host                   the server address
	 * @param port                   the server port
     * @param connectTimeoutMillis   the connection timeout
	 * @param isSSL                  true, if ssl connection
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port, int connectTimeoutMillis, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(host, port), null, workerpool, true, connectTimeoutMillis, isSSL);
	}

	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 * 
	 * @param address  the server address
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetSocketAddress address) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(address, null, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS), false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param address  the server address
	 * @param port     the sever port
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), null, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS), false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param address  the server address
	 * @param port     the sever port
	 * @param isSSL    true, if ssl connection
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), null, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS), isSSL);
	}

	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param address                the server address
	 * @param port                   the server port
     * @param connectTimeoutMillis   the connection timeout
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), null, workerpool, true, connectTimeoutMillis, false);
	}



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created
	 *
	 * @param address                the server address
	 * @param port                   the server port
     * @param connectTimeoutMillis   the connection timeout
	 * @param isSSL                  true, if ssl connection
	 * @return the connection          
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, int connectTimeoutMillis, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), null, workerpool, true, connectTimeoutMillis, isSSL);
	}

   /**
     * get a pool connection for the given address. If no free connection is in the pool,
     * a new one will be created
     *
     * @param address                the server address
     * @param port                   the server port
     * @param waitForConnect         true, if the call should block until the connection is established
     * @param connectTimeoutMillis   the connection timeout
     * @param isSSL                  true, if ssl connection
     * @return the connection          
     * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
     * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
     */
    public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, boolean waitForConnect, int connectTimeoutMillis, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
        return getConnection(new InetSocketAddress(address, port), null, workerpool, waitForConnect, connectTimeoutMillis, isSSL);
    }

	
	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param address     the server address
	 * @param port        the server port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS), false);
	}



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param address     the server address
	 * @param port        the server port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param isSSL       true, if ssl connection
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS), isSSL);
	}



	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param host        the server address
	 * @param port        the server port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port, IHandler appHandler) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(host, port), appHandler, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS), false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param host        the server address
	 * @param port        the server port
 	 * @param appHandler  the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param isSSL       true, if ssl connection
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(String host, int port, IHandler appHandler, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(host, port), appHandler, workerpool, true, toInt(IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS), isSSL);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param address                the server address
	 * @param port                   the server port
 	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param connectTimeoutMillis  the connection creation timeout
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, true, connectTimeoutMillis, false);
	}

	
    /**
     * get a pool connection for the given address in an asynchronous wa. If no free connection is in the pool,
     *  a new one will be created
     *
     * @param address               the server address
     * @param port                  the server port
     * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
     * @param waitForConnect        true, if the method should block until the connection is established
     * @param connectTimeoutMillis  the connection creation timeout
     * @return the connection
     * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
     * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
     */
    public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
        return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, waitForConnect, connectTimeoutMillis, false);
    }


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 *  a new one will be created
	 *
	 * @param address                the server address
	 * @param port                   the server port
 	 * @param appHandler             the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param connectTimeoutMillis  the connection creation timeout
	 * @param isSSL                 true, if ssl connection
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, int connectTimeoutMillis, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, true, connectTimeoutMillis, isSSL);
	}

	
    /**
     * get a pool connection for the given address. If no free connection is in the pool,
     *  a new one will be created
     *
     * @param address               the server address
     * @param port                  the server port
     * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
     * @param waitForConnect        true, if the method should block until the connection is established
     * @param connectTimeoutMillis  the connection creation timeout
     * @param isSSL                 true, if ssl connection
     * @return the connection
     * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
     * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
     */
    public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, boolean waitForConnect, int connectTimeoutMillis, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
        return getConnection(new InetSocketAddress(address, port), appHandler, workerpool, waitForConnect, connectTimeoutMillis, isSSL);
    }

	


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param address               the server address
	 * @param port                  the sever port
 	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and ConnectionTimeoutHandler)
	 * @param workerPool            the worker pool to use
	 * @param connectTimeoutMillis  the connection creation timeout
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool, int connectTimeoutMillis) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerPool, true, connectTimeoutMillis, false);
	}


	/**
	 * get a pool connection for the given address. If no free connection is in the pool,
	 * a new one will be created <br> <br>
	 *
	 * This method is thread safe
	 *
	 * @param address               the server address
	 * @param port                  the sever port
 	 * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and ConnectionTimeoutHandler)
	 * @param workerPool            the worker pool to use
	 * @param connectTimeoutMillis  the connection creation timeout
	 * @param isSSL                 true, if ssl connection
	 * @return the connection
	 * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
	 * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
	 */
	public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool, int connectTimeoutMillis, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
		return getConnection(new InetSocketAddress(address, port), appHandler, workerPool, true, connectTimeoutMillis, isSSL);
	}

	
    /**
     * get a pool connection for the given address. If no free connection is in the pool,
     * a new one will be created <br> <br>
     *
     * This method is thread safe
     *
     * @param address               the server address
     * @param port                  the sever port
     * @param appHandler            the application handler (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and ConnectionTimeoutHandler)
     * @param workerPool            the worker pool to use
     * @param waitForConnect        true, if the method should block until the connection is established 
     * @param connectTimeoutMillis  the connection creation timeout
     * @param isSSL                 true, if ssl connection
     * @return the connection
     * @throws SocketTimeoutException if the wait timeout has been reached (this will only been thrown if wait time has been set)
     * @throws IOException  if an exception occurs
     * @throws MaxConnectionsExceededException if the max number of active connections (per server) is reached 
     */
    public INonBlockingConnection getNonBlockingConnection(InetAddress address, int port, IHandler appHandler, Executor workerPool, boolean waitForConnect, int connectTimeoutMillis, boolean isSSL) throws IOException, SocketTimeoutException, MaxConnectionsExceededException {
        return getConnection(new InetSocketAddress(address, port), appHandler, workerPool, waitForConnect, connectTimeoutMillis, isSSL);
    }
	
	
	private int toInt(long l) {
	    if (l > Integer.MAX_VALUE) {
	        return Integer.MAX_VALUE;
	    } else {
	        return (int) l; 
	    }
	}
	
		
	private INonBlockingConnection getConnection(InetSocketAddress address, IHandler appHandler, Executor workerPool, boolean waitForConnect, int connectTimeoutMillis, boolean isSSL) throws IOException,  SocketTimeoutException, MaxConnectionsExceededException, MaxConnectionsExceededException {
	  
	    NonBlockingConnectionProxy proxy = new NonBlockingConnectionProxy(address);
	    
	    
        Connector connector;
        if (waitForConnect) {
            connector = new SyncConnector(proxy, appHandler, isSSL, CONNECT_MAX_TRIALS, connectTimeoutMillis, CONNECT_RETRY_WAIT_TIME_MILLIS);
        } else {
            connector = new Connector(proxy, appHandler, isSSL, CONNECT_MAX_TRIALS, connectTimeoutMillis, CONNECT_RETRY_WAIT_TIME_MILLIS);
        }

        
        try {
            if (isSSL && (sslContext == null)) {
                throw new IOException("could not create a SSL connection to " + address.toString() + ". SSLContext is not set");
            }
            
            if (isOpen.get()) {
                connector.connect();
                return proxy;
            } else  {
                throw new IOException("pool is already closed");
            }
            
        } catch (IOException ioe) {
            connector.onConnectFailed(ioe);
            throw ioe;
        }
	}
	
	
	private void checkLimit(InetAddress addr) throws MaxConnectionsExceededException {
	    synchronized (limitGuard) {
            if ((pool.getNumActive() + numInitializingConnections) > maxActive.get()) {
                if(LOG.isLoggable(Level.FINE)) {
                    LOG.fine("max active connections " + maxActive.get() + "  exceeded");
                }
                countRejectedConnections.incrementAndGet();
                throw new MaxConnectionsExceededException("max active connections " + maxActive.get() + "  exceeded");
            }

            
            // limit per host activated?                
            if (maxActivePerServer.get() != Integer.MAX_VALUE) {
                Integer connectionAddr = initializingConnectionMap.get(addr);
                if (connectionAddr == null) {
                    connectionAddr = 0;
                }
                
                if (pool.isActiveExceeded(addr, (maxActivePerServer.get() - connectionAddr))) {
                    if(LOG.isLoggable(Level.FINE)) {
                        LOG.fine("max active connections " + maxActivePerServer.get() + " (initializing: " + connectionAddr + ") to " + addr + " exceeded");
                    }
                    countRejectedConnections.incrementAndGet();
                    throw new MaxConnectionsExceededException("max active connections " + maxActivePerServer.get() + " (initializing: " + connectionAddr + ") to " + addr + " exceeded");
                }
            }
        }
	}  

	    
    private void incCountInitializingConnections(InetAddress addr) {
        synchronized (limitGuard) {
            numInitializingConnections++;                

            Integer connectionAddr = initializingConnectionMap.get(addr);
            if (connectionAddr == null) {
                connectionAddr = 0;
            }
                
            connectionAddr++;
            initializingConnectionMap.put(addr, connectionAddr);
        }
    }  
	    
	    

    private void decCountInitializingConnections(InetAddress addr) {
        synchronized (limitGuard) {
            numInitializingConnections--;
            if (numInitializingConnections < 0) {
                numInitializingConnections = 0;
            }
            
            Integer connectionAddr = initializingConnectionMap.get(addr);
            if (connectionAddr != null) {
                connectionAddr--;
                if (connectionAddr <= 0) {
                    initializingConnectionMap.remove(addr);
                } else {
                    initializingConnectionMap.put(addr, connectionAddr);
                }
            }
        }
    }   
    
	
    
    private void newConnection(Connector connector) {
        
        try {
            // try to get a connection from the pool
            NativeConnectionHolder nativeConnectionHolder;
            do {
                // try to get a pooled native connection
                nativeConnectionHolder = pool.getAndRemoveIdleConnection(connector.getAddress(), connector.isSSL());
        
                // got it?
                if (nativeConnectionHolder != null) {
        
                    // reset resource (connection will be closed if invalid)
                    boolean isValid = nativeConnectionHolder.isVaild(System.currentTimeMillis(), true);
                    if (isValid && nativeConnectionHolder.getConnection().reset()) {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("got connection from pool (" + pool.toString() + ", idleTimeoutMillis=" + getPooledMaxIdleTimeMillis() + ", lifeTimeout=" + getPooledMaxLifeTimeMillis() + "): " + nativeConnectionHolder.getConnection());
                        }
                        
                        connector.onConnected(nativeConnectionHolder);
                        return;
                            
                    } else {
                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("get a invalid connection try another one");
                        }
                    }
                }
            } while (nativeConnectionHolder != null);
                                
                
            // no resource available in pool -> create a new one
            nativeConnectionHolder = newNativeConnection(connector, connector.getConnectTimeoutMillis(), CONNECT_MAX_TRIALS, CONNECT_RETRY_WAIT_TIME_MILLIS, connector.isSSL());
            
        } catch (IOException ioe) {
            connector.onConnectError(ioe);
        }
    }
    
    
	


    private class Connector {
        
        private final NonBlockingConnectionProxy proxy;
        private final IHandler appHandler;
        private final boolean isSSL;
        private boolean isConnectionLimited;
        private final long startMillis;
        private final int connectTimeoutMillis;
        private final long retryWaittimeMillis;
        private final int maxTrials;
        private int trials = 0;

        
        public Connector(NonBlockingConnectionProxy proxy, IHandler appHandler, boolean isSSL, int maxTrials, int connectTimeoutMillis, long retryWaittimeMillis) {
            this.proxy = proxy;
            this.appHandler = appHandler;
            this.isSSL = isSSL;
            this.maxTrials = maxTrials;
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.retryWaittimeMillis = retryWaittimeMillis;
            startMillis = System.currentTimeMillis();
            
            isConnectionLimited = ((maxActive.get() != Integer.MAX_VALUE) || (maxActivePerServer.get() != Integer.MAX_VALUE));
        }
        
        
        boolean isSSL() {
            return isSSL;
        }
        
        Executor getWorkerpool() {
            return workerpool;
        }
        
        int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        InetSocketAddress getAddress() {
            return proxy.getAddress();
        }
        
        void connect() throws IOException {
            if (isConnectionLimited) {
                incCountInitializingConnections(proxy.getAddress().getAddress());
            }
            
            performConnect();
        }

        
        final void performConnect() {
            trials++;
            
            if (isConnectionLimited) {
                try {
                    checkLimit(proxy.getAddress().getAddress());
                } catch (MaxConnectionsExceededException mcr) {
                    onConnectError(mcr);
                    return;
                }
            }
            
            newConnection(this);
        }
        
        
        final void onConnected(NativeConnectionHolder holder) throws IOException {
            proxy.onConnected(holder, appHandler);
            onConnectionEstablished();
        }
        
        
        final void onConnectError(IOException ioe) {
            countCreationError.incrementAndGet();

            // is timeout reached?
            long remainingTimeMillis = connectTimeoutMillis - ((int) (System.currentTimeMillis() - startMillis));
            if (remainingTimeMillis <= 0) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("error occured by creating connection to " + proxy.getAddress()
                            + ". connect timeout " + DataConverter.toFormatedDuration(connectTimeoutMillis) + " reached (trials: " + trials + " maxTrials: " + maxTrials + ")");
                }
    
                onConnectFailed(new SocketTimeoutException("connect timeout " + connectTimeoutMillis + " millis reached (trials: " + trials + " maxTrials: " + maxTrials + "). Could not connect to " + proxy.getAddress() + " " + ioe.toString()));
            } 
                

            if (ioe instanceof MaxConnectionsExceededException) {
                if ((acquireTimeoutMillis == null) || (acquireTimeoutMillis <= 0)) {
                    onConnectFailed((MaxConnectionsExceededException) ioe);
                    
                } else {
                    long remaining = (startMillis + acquireTimeoutMillis) - System.currentTimeMillis();
                
                    if (remaining > 0) {
                        synchronized (retrieveGuard) {
                            try {
                                retrieveGuard.wait(remaining);
                            } catch (InterruptedException ie) { 
                                // Restore the interrupted status
                                Thread.currentThread().interrupt();
                            }
                        }
                        performConnect();
                        
                    } else {
                        onConnectFailed((MaxConnectionsExceededException) ioe);
                    }
                }
                
            } else {
                
                // max trials reached?
                if (trials >= maxTrials) {  
                    onConnectFailed(new SocketTimeoutException("creation failed. Max trials " + maxTrials + " reached. Elapsed time " + DataConverter.toFormatedDuration(System.currentTimeMillis() - startMillis) +
                                                               " (connection timeout " + DataConverter.toFormatedDuration(connectTimeoutMillis) + "). Could not connect to " + proxy.getAddress() + " " + ioe.toString()));
                
                // .. no, retry connect
                } else {
                    if (remainingTimeMillis > retryWaittimeMillis) {
                        // after  
                        TimerTask tt = new TimerTask() {
                            @Override
                            public void run() {
                                performConnect();
                            }
                        };
                        IoProvider.getTimer().schedule(tt, retryWaittimeMillis);
                        
                    } else {
                        onConnectFailed(new SocketTimeoutException("connect timeout " + connectTimeoutMillis + " millis reached (trials: " + trials + " maxTrials: " + maxTrials + "). Could not connect to " + proxy.getAddress() + " " + ioe.toString()));
                    }
                }
            }
        }
        
        
        void onConnectionEstablished() {
            if (isConnectionLimited) {
                decCountInitializingConnections(proxy.getAddress().getAddress());
            }
        }
        
        void onConnectFailed(IOException ioe) {
            if (isConnectionLimited) {
                decCountInitializingConnections(proxy.getAddress().getAddress());
            }
            proxy.onConnectFailed(workerpool, ioe, appHandler);
        }
    }
	

    
    private final class SyncConnector extends Connector {
        
        private boolean isConnected = false;
        private IOException ioe = null;
        
        
        public SyncConnector(NonBlockingConnectionProxy proxy, IHandler appHandler, boolean isSSL, int maxTrials, int connectTimeoutMillis, long retryWaittimeMillis) {
            super(proxy, appHandler, isSSL, maxTrials, connectTimeoutMillis, retryWaittimeMillis);
        }
        
        void connect() throws IOException {
            
            synchronized (this) {
                super.connect();
                
                while (isConnected == false) {
                    try {
                        this.wait();
                    } catch (InterruptedException ignore) { }
                }
                
                if (ioe != null) {
                    throw ioe;
                }
            }
        }

        
        @Override
        void onConnectionEstablished() {
            super.onConnectionEstablished(); 
            
            synchronized (this) {
                this.ioe = null;
                isConnected = true;
                this.notifyAll();
            }
        }

        @Override
        void onConnectFailed(IOException ioe) {
            super.onConnectFailed(ioe);
            
            synchronized (this) {
                this.ioe = ioe;
                isConnected = true;
                this.notifyAll();
            }
        }
    }
    


    
	
	private void returnToIdlePool(NativeConnectionHolder nativeConnectionHolder) {
		pool.returnIdleConnection(nativeConnectionHolder);

		if (maxActive.get() < Integer.MAX_VALUE) {
			wakeupPendingRetrieve();
		}
	}
	
	
	private void wakeupPendingRetrieve() {
		synchronized (retrieveGuard) {
			retrieveGuard.notifyAll();
		}
	}
	

	private NativeConnectionHolder newNativeConnection(Connector connector, final int creationTimeoutMillis, int maxTrials, int retryWaittimeMillis, boolean isSSL) throws IOException {
		long start = System.currentTimeMillis();
		int remainingTimeMillis = creationTimeoutMillis;
  
		int trials  = 0;

		
		while (true) {
		    trials++;
    	
		    try {
    				
    		    // create a new tcp connection and the assigned holder
    		    NativeConnectionHolder nativeConnectionHolder = new NativeConnectionHolder(connector);
    		    new NonBlockingConnection(connector.getAddress(), false, remainingTimeMillis, new HashMap<String, Object>(), sslContext, isSSL, nativeConnectionHolder, connector.getWorkerpool(), null);
    				
    		    return nativeConnectionHolder;
    
    		} catch (IOException ioe) {
    		    countCreationError.incrementAndGet();

    		    
    		    // is timeout reached?
    		    remainingTimeMillis = creationTimeoutMillis - ((int) (System.currentTimeMillis() - start));
    		    if (remainingTimeMillis <= 0) {
        	        if (LOG.isLoggable(Level.FINE)) {
        	            LOG.fine("error occured by creating connection to " + connector.getAddress()
        	                    + ". creation timeout " + DataConverter.toFormatedDuration(creationTimeoutMillis) + " reached (trials: " + trials + " maxTrials: " + maxTrials + ")");
        	        }
    
        	        throw new SocketTimeoutException("creation timeout " + creationTimeoutMillis + " millis reached (trials: " + trials + " maxTrials: " + maxTrials + "). Could not connect to " + connector.getAddress() + " " + ioe.toString());
    		    } 
    		    
    		    
                // max trials reached?
                if (trials >= maxTrials) {  
                    throw new SocketTimeoutException("creation failed. Max trials " + maxTrials + " reached. Elapsed time " + DataConverter.toFormatedDuration(System.currentTimeMillis() - start) +
                                                     " (creation timeout " + DataConverter.toFormatedDuration(creationTimeoutMillis) + "). Could not connect to " + connector.getAddress() + " " + ioe.toString());
                } else {
                    if (remainingTimeMillis > retryWaittimeMillis) {
                        try {
                            Thread.sleep(retryWaittimeMillis);
                        } catch (InterruptedException ie) { 
                        	// Restore the interrupted status
                            Thread.currentThread().interrupt();
                        }
                        
                        retryWaittimeMillis += retryWaittimeMillis;
                    } else {
                        throw new SocketTimeoutException("creation timeout " + creationTimeoutMillis + " millis reached (trials: " + trials + " maxTrials: " + maxTrials + "). Could not connect to " + connector.getAddress() + " " + ioe.toString());
                    }
                }   		     
    		} 
		} 
	}

	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return isOpen.get();
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void close() {

		if (isOpen.getAndSet(false)) {
			pool.close();
			
            watchdog.cancel();

			for (ILifeCycle lifeCycle : listeners) {
				try {
					lifeCycle.onDestroy();
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("exception occured by destroying " + lifeCycle + " " + ioe.toString());
					}
				}
			}
			listeners.clear();
						
			// unset references
			workerpool = null;
		}
	}
	
	

	/**
	 * destroy the pool by killing idle and active connections
	 */
	public void destroy() {

		if (isOpen.getAndSet(false)) {
			pool.destroy();

            watchdog.cancel();
	
			for (ILifeCycle lifeCycle : listeners) {
				try {
					lifeCycle.onDestroy();
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("exception occured by destroying " + lifeCycle + " " + ioe.toString());
					}
				}
			}
			listeners.clear();
						
			// unset references
			workerpool = null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void addListener(ILifeCycle listener) {
		listeners.add(listener);
	}


	/**
	 * {@inheritDoc}
	 */
	public boolean removeListener(ILifeCycle listener) {
		boolean result = listeners.remove(listener);

		return result;
	}

	
	/**
	 * set the worker pool which will be assigned to the connections for call back handling
	 * @param workerpool the worker pool
	 */
	public void setWorkerpool(Executor workerpool) {
		this.workerpool = workerpool;
	}
	
	
	/**
	 * get the worker pool which will be assigned to the connections for call back handling
	 * @return the worker pool
	 */
	public Executor getWorkerpool() {
		return workerpool;
	}
	


	/**
	 * {@inheritDoc}
	 */
	public int getMaxActive() {
		return maxActive.get();
    }

	
	/**
	 * {@inheritDoc}
	 */
	public int getNumRejectedConnections() {
	    return countRejectedConnections.get();
	}
	
 

	/**
	 * {@inheritDoc}
	 */
    public void setMaxActive(int maxActive) {
        if (maxActive < 0) {
            maxActive = 0;
        }
    	this.maxActive.set(maxActive);
    	wakeupPendingRetrieve(); 
    }

  
    /**
     * {@inheritDoc}
     */
    public void setMaxActivePerServer(int maxActivePerServer) {
        if (maxActivePerServer < 0) {
            maxActivePerServer = 0;
        }
        this.maxActivePerServer.set(maxActivePerServer);
        wakeupPendingRetrieve();         
    }

    
    /**
     * {@inheritDoc}
     */    
    public int getMaxActivePerServer() {
        return maxActivePerServer.get();
    }
    

    /**
	 * {@inheritDoc}
	 */
    public int getMaxIdle() {
    	return maxIdle.get();
    }


    /**
	 * {@inheritDoc}
	 */
    public void setMaxIdle(int maxIdle) {
    	this.maxIdle.set(maxIdle);
    }


    /**
	 * {@inheritDoc}
	 */
    public int getNumActive() {
        return pool.getNumActive();
    }

    
    /**
     * returns the acquire timeout. Default is null 
     * 
     * @return the acquire timeout
     */
    public Integer getAcquireTimeoutMillis() {
        return acquireTimeoutMillis;
    }
    
    
    /**
     * sets the acquire time out. Null deactivates the acquire timeout. 
     * 
     * @param aquireTimeoutMillis the acquire timeout or <code>null</code> 
     */
    public void setAcquireTimeoutMillis(Integer aquireTimeoutMillis) {
        this.acquireTimeoutMillis = aquireTimeoutMillis;
    }
    
 
    public List<String> getActiveConnectionInfos() {
    	List<String> result = new ArrayList<String>();
	    	
    	List<NativeConnectionHolder> connectionHolders = pool.newManagedPoolCopy();
    	connectionHolders.removeAll(pool.newIdleCopySet());
    	
    	for (NativeConnectionHolder connectionHolder : connectionHolders) {
    		result.add(connectionHolder.toString());
    	}
	        
    	return result;
    }
    
    
    public List<String> getIdleConnectionInfos() {
    	List<String> result = new ArrayList<String>();

    	for (NativeConnectionHolder nativeConnectionHolder : pool.newIdleCopySet()) {
    		result.add(nativeConnectionHolder.toString());
    	}
	        
    	return result;
    }
    

    /**
	 * {@inheritDoc}
	 */
    public int getNumIdle() {
    	return pool.getNumIdle();
	}

    /**
	 * {@inheritDoc}
	 */
    public int getNumCreated() {
    	return countCreated.get();
    }
    
    
    /**
	 * get the number of the creation errors 
	 * 
	 * @return the number of creation errors 
	 */
    public int getNumCreationError() {
    	return countCreationError.get();
    }
    
    /**
	 * {@inheritDoc}
	 */
    public int getNumDestroyed() {
    	return countDestroyed.get();
    }

    
    int getNumIdleTimeout() {
    	return countIdleTimeout.get();
    }
    
    int getNumConnectionTimeout() {
    	return countConnectionTimeout.get(); 
    }
  
    
    int getNumPoolIdleTimeout() {
    	return countTimeoutPooledIdle.get();
    }

    int getNumPoolLifetimeTimeout() {
    	return countTimeoutPooledLifetime.get();
    }
    
    /**
	 * {@inheritDoc}
	 */
    public int getNumTimeoutPooledMaxIdleTime() {
    	return countTimeoutPooledIdle.get();
    }

    /**
	 * {@inheritDoc}
	 */
    public int getNumTimeoutPooledMaxLifeTime() {
    	return countTimeoutPooledLifetime.get();
    }
    
    
    
    int getNumUndetectedDisconnect() {
        return countUndetectedDisconnect.get();
    }
    
        
    /**
	 * {@inheritDoc}
	 */
	public int getPooledMaxIdleTimeMillis() {
		return poolIdleTimeoutMillis.get();
	}


	/**
	 * {@inheritDoc}
	 */
	public void setPooledMaxIdleTimeMillis(int idleTimeoutMillis) {
		this.poolIdleTimeoutMillis.set(idleTimeoutMillis);
	}


	/**
	 * {@inheritDoc}
	 */
	public int getPooledMaxLifeTimeMillis() {
		return lifeTimeoutMillis.get();
	}


	/**
	 * {@inheritDoc}
	 */
	public void setPooledMaxLifeTimeMillis(int lifeTimeoutMillis) {
		this.lifeTimeoutMillis.set(lifeTimeoutMillis);
	}



	/**
	 * get the current number of pending get operations to retrieve a resource
	 *
	 * @return the current number of pending get operations
	 */
	public int getNumPendingGet() {
		return countPendingGet.get();
	}

	


	/**
	 * {@inheritDoc}
	 */
	public static void destroy(INonBlockingConnection connection) throws IOException {
		if (connection == null) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("warning trying to destroy a <null> connection. destroy will be ignored");
			}
			return;
		}

		if (connection instanceof NonBlockingConnectionProxy) {
			((NonBlockingConnectionProxy) connection).destroy();

		} else {
			connection.close();
		}
	}
	
	
	static boolean isDestroyed(INonBlockingConnection connection) {
		if (connection instanceof NonBlockingConnectionProxy) {
			return ((NonBlockingConnectionProxy) connection).isDestroyed();
		
		} else {
			return connection.isOpen();
		}
	}
	
	
	private void checkIdleConnections() {
		long currentMillis = System.currentTimeMillis();
		
		for (NativeConnectionHolder nativeConnectionHolder : pool.newIdleCopySet()) {
			if (!nativeConnectionHolder.isVaild(currentMillis, false)) {
				boolean isRemoved = pool.removeIdleConnection(nativeConnectionHolder);
				if (isRemoved) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + nativeConnectionHolder.getId() + "] closing connection because it is invalid (e.g. idle timeout, connection timeout reached)");
					}
					nativeConnectionHolder.isVaild(currentMillis, true);  // true -> closes the connection 
					nativeConnectionHolder.close();  /// sanity close
				}
			}
		}		
	}
	   
	
	
	@Override
	public String toString() {
		
		StringBuilder sb = new StringBuilder();
		
		try {
    		sb.append("active=" + getNumActive() + ", idle=" + getNumIdle() + " "); 
    		sb.append("created=" + countCreated + ", destroyed=" + countDestroyed + " ");
    		sb.append("connectionTimeout=" + countConnectionTimeout + ", idleTimeout=" + countIdleTimeout + " (");
    		sb.append("maxActive=" + maxActive + ", maxIdle=" + maxIdle + ", ");
    		sb.append("poolIdleTimeout=" + poolIdleTimeoutMillis + ", poollifetimeTimeout=" + lifeTimeoutMillis + ")");
		} catch (Exception ignore) {
		    
		}
    		
		return sb.toString();
	}
	
	
	private final class Watchog extends TimerTask {
		
		@Override
		public void run() {
			try {
				checkIdleConnections();
			} catch (Throwable t) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by checking connections " + t.toString());
				}
			}
		}
	}
	


	private static final class Pool {
		
		private final ArrayList<NativeConnectionHolder> managedPool = new ArrayList<NativeConnectionHolder>();
		private final HashMap<InetSocketAddress, List<NativeConnectionHolder>> idlePool = new HashMap<InetSocketAddress, List<NativeConnectionHolder>>();

		private boolean isOpen = true;
		
		
		public void register(NativeConnectionHolder nativeConnectionHolder) {

		    if (isOpen) {
		        synchronized (this) {
					managedPool.add(nativeConnectionHolder);
				}
	            
	            if (LOG.isLoggable(Level.FINE)) {
	                LOG.fine("[" + nativeConnectionHolder.getId() + "] added to managed pool (active=" + getNumActive() + ", idle=" + getNumIdle() + ")");
	            }
		        
			} else {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("ignore registering connection " + nativeConnectionHolder.toString() +  " because pool is already closed");
                }
            }
		}
		

	    public boolean remove(NativeConnectionHolder nativeConnectionHolder) {
	        
	    	boolean isRemoved = false;

	    	if (isOpen)  {
	    	    removeIdleConnection(nativeConnectionHolder);
    	    	synchronized (this) {    	    	    
    		    	isRemoved = managedPool.remove(nativeConnectionHolder);
    	    	}
    	    	
    			if (LOG.isLoggable(Level.FINE)) {
    				if (isRemoved) {
    					LOG.fine("[" + nativeConnectionHolder.getId() + "] connection removed from managed pool (active=" + getNumActive() + ", idle=" + getNumIdle() + ")");
    				} else {
    					LOG.fine("[" + nativeConnectionHolder.getId() + "] could not removed connection from managed pool. Connection already removed? (active=" + getNumActive() + ", idle=" + getNumIdle() + ")");					
    				}
    			}
	    	} 
	    	
			return isRemoved;
	    }

	    
	    public boolean removeIdleConnection(NativeConnectionHolder connectionHolder) {
	    	
	        if (isOpen)  {
    	    	synchronized (this) {
    	    		List<NativeConnectionHolder> idleList = idlePool.get(connectionHolder.getAddress());
    		    	if (idleList != null) {
    
    					for (NativeConnectionHolder nativeConnectionHolder : idleList) {
    						if (nativeConnectionHolder == connectionHolder) {
    							boolean isRemoved = idleList.remove(nativeConnectionHolder);
    							if (idleList.isEmpty()) {
    								idlePool.remove(connectionHolder.getAddress());
    							}
    							
    							return isRemoved;
    						}
    					}
    				}
    			}
	        }
	        
	    	return false;
	    }

	    
	    
	    
	    
	    public NativeConnectionHolder getAndRemoveIdleConnection(InetSocketAddress address, boolean isSSL) {
	        
	        if (isOpen)  {
    	    	synchronized (this) {
    	    		List<NativeConnectionHolder> idleList = idlePool.get(address);
    		    	if (idleList != null) {
    
    					for (NativeConnectionHolder nativeConnectionHolder : idleList) {
    						if (nativeConnectionHolder.isSSL == isSSL) {
    							idleList.remove(nativeConnectionHolder);
    							if (idleList.isEmpty()) {
    								idlePool.remove(address);
    							}
    
    							if (LOG.isLoggable(Level.FINE)) {
    								LOG.fine("[" + nativeConnectionHolder.getId() + "] got from idle pool (active=" + getNumActive() + ", idle=" + getNumIdle() + ")");
    							}
    
    							return nativeConnectionHolder;
    						}
    					}
    				}
    			}
	        }
	    	
	    	return null;
	    }

	    
	    
	    public void returnIdleConnection(NativeConnectionHolder nativeConnectionHolder) {
	        
	        if (isOpen)  {
    	  		InetSocketAddress address = nativeConnectionHolder.getAddress();
    	    	
    	    	synchronized (this) {
    	    		List<NativeConnectionHolder> idleList = idlePool.get(address);
    		    	if (idleList == null) {
    		    		idleList = new ArrayList<NativeConnectionHolder>();
    		    		idlePool.put(address, idleList);
    		    	}
    		    	
    		    	if (!idleList.contains(nativeConnectionHolder)) {
    		    		idleList.add(nativeConnectionHolder);
    		    	} else {
    		    		if (LOG.isLoggable(Level.FINE)) {
    						LOG.fine("[" + nativeConnectionHolder.getId() + "] will not be returned to pool  because it already exits (active=" + getNumActive() + ", idle=" + getNumIdle() + ")");
    					}		
    		    	}
    			}
    	    	
    			if (LOG.isLoggable(Level.FINE)) {
    				LOG.fine("[" + nativeConnectionHolder.getId() + "] added to idle pool (active=" + getNumActive() + ", idle=" + getNumIdle() + ")");
    			}
    			
	        } else { 
	            if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + nativeConnectionHolder.getId() + "] will not be returned to pool, because pool is already closed. destroying connection");
                }   
	            nativeConnectionHolder.close();
	        }
	    }

	    
    
	    
	    public void close() {	    	
	    	synchronized (this) {
	    		if (isOpen) {
	    			isOpen = false;
	    		} else {
	    			return;
	    		}
	    	}

	
	    	List<NativeConnectionHolder> idleSet = newIdleCopySet();
	    	
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing " + idleSet.size() + " idle conection(s); " + managedPool.size() + " connection(s) stay open unmanaged");
			}
	    	
	    	for (NativeConnectionHolder nativeConnectionHolder : idleSet) {
				nativeConnectionHolder.close();
			}
	    	
	    	idlePool.clear();
	    	managedPool.clear();
		}

	    
	    public void destroy() {	    	
	    	synchronized (this) {
	    		if (isOpen) {
	    			isOpen = false;
	    		} else {
	    			return;
	    		}
	    	}

	
	    	List<NativeConnectionHolder> connections = newManagedPoolCopy();
	    	
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("closing " + connections.size() + " managed connections");
			}
	    	
	    	for (NativeConnectionHolder nativeConnectionHolder : connections) {
				nativeConnectionHolder.close();
			}
	    	
	    	synchronized (this) {
		    	idlePool.clear();
		    	managedPool.clear();
	    	}
		}

	    
		private List<NativeConnectionHolder> newIdleCopySet() {
			List<NativeConnectionHolder> idleList = new ArrayList<NativeConnectionHolder>();
			
			for (List<NativeConnectionHolder> nativeConnectionHolderList : newIdlePoolCopy().values()) {
				idleList.addAll(nativeConnectionHolderList);
			}
			
			return idleList;
		}
		
		
		@SuppressWarnings("unchecked")
		List<NativeConnectionHolder> newManagedPoolCopy() {
			List<NativeConnectionHolder> managedPoolCopy = null;
			
			synchronized (this) {
				managedPoolCopy = (List<NativeConnectionHolder>) managedPool.clone();
			}
			
			return managedPoolCopy;
		}
		
		
	    
		@SuppressWarnings("unchecked")
		boolean isActiveExceeded(InetAddress address, int maxSize) {
            List<NativeConnectionHolder> managedPoolCopy;
            synchronized (this) {
                managedPoolCopy = (List<NativeConnectionHolder>) managedPool.clone();
            }


            // if connections is smaller than max size, return not exceeded 
            if (managedPoolCopy.size() <= maxSize) {
                return false;
            }
            

            int managedMatched = 0;
            for (NativeConnectionHolder holder : managedPoolCopy) {
                if (holder.getAddress().getAddress().equals(address)) {
                    managedMatched++;
                }
            }
                    
            // if connections of the address is smaller than max size, return not exceeded            
            if (managedMatched <= maxSize) {
                return false;
            }
            
            
            HashMap<InetSocketAddress, List<NativeConnectionHolder>> idlePoolCopy;
            synchronized (this) {
                idlePoolCopy = (HashMap<InetSocketAddress, List<NativeConnectionHolder>>) idlePool.clone();
            }
                    
            for (Entry<InetSocketAddress, List<NativeConnectionHolder>> entry : idlePoolCopy.entrySet()) {
                if (entry.getKey().getAddress().equals(address)) {
                    managedMatched = managedMatched - entry.getValue().size(); 
                    if (managedMatched <= maxSize) {
                        return false;
                    }
                }
            }
		    
		    return true;
		}       	

        
		@SuppressWarnings("unchecked")
		HashMap<InetSocketAddress, List<NativeConnectionHolder>> newIdlePoolCopy() {
			HashMap<InetSocketAddress, List<NativeConnectionHolder>> idlePoolCopy = null;
			
			synchronized (this) {
				idlePoolCopy = (HashMap<InetSocketAddress, List<NativeConnectionHolder>>) idlePool.clone();
			}
			
			return idlePoolCopy;
		}
		

	    
		public int getSize() {
			synchronized (this) {
				return managedPool.size();
			}
		}


		public int getNumIdle() {
			synchronized (this) {
				return computeNumIdle();
			}
		}
		
		public int getNumActive() {
			synchronized (this) {
				return (managedPool.size() - computeNumIdle());
			}
		}

		
		private int computeNumIdle() {
			int size = 0;
			
			for (List<NativeConnectionHolder> nativeConnectionHolderList : idlePool.values()) {
				size += nativeConnectionHolderList.size();
			}
			
			return size;
		}


		
	    
	    @Override
	    public String toString() {
	    	return "size=" + getSize() + ", active=" + getNumActive();
	    }
	}
	
	

	
	

	private final class NativeConnectionHolder implements IConnectHandler, IConnectExceptionHandler, IDataHandler, IDisconnectHandler, IConnectionTimeoutHandler, IIdleTimeoutHandler, IUnsynchronized {

		private final AtomicBoolean isClosed = new AtomicBoolean(false);
		private final AtomicBoolean isReusable = new AtomicBoolean(true);
		private final AtomicReference<NonBlockingConnectionProxy> proxyRef = new AtomicReference<NonBlockingConnectionProxy>(null);

		private Connector connector;
		private NonBlockingConnection connection;
		private InetSocketAddress address;
		private boolean isSSL;
		

		private int usage = 0;
		private long creationTimeMillis = System.currentTimeMillis();
		private long lastUsageTimeMillis = System.currentTimeMillis();

		
	      
        public NativeConnectionHolder(Connector connector) {
            this.connector = connector;
        }
        
        
        
        public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
            this.address = new InetSocketAddress(connection.getRemoteAddress(), connection.getRemotePort());
            this.connection = (NonBlockingConnection) connection;
            isSSL = connection.isSecure();
            
            pool.register(this);
            countCreated.incrementAndGet();

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] pooled connection created (" + pool.toString() + ", pooledIdleTimeoutMillis=" + getPooledMaxIdleTimeMillis() + ", pooledLifeTimeout=" + getPooledMaxLifeTimeMillis() + "): " + connection);
            }
            
            connector.onConnected(this);
            return true;
        }
        
        public boolean onConnectException(INonBlockingConnection connection, IOException ioe) throws IOException {
            connector.onConnectError(ioe);
            return true;
        }
		

		String getId() {
			return connection.getId();
		}
		

	    int getPooledMaxLifeTimeMillis() {
	        return lifeTimeoutMillis.get();
	    }

        int getPooledMaxIdleTimeMillis() {
            return poolIdleTimeoutMillis.get();
        }


		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			NonBlockingConnectionProxy cp = proxyRef.get();
			if (cp != null) {
				try {
					return cp.onData();
				} catch (MaxReadSizeExceededException mre) {
					isReusable.set(false);
					throw mre;
				} catch (IOException ioe) {
					isReusable.set(false);
					throw ioe;
				}
			} else {
				return true;
			}
		}
		
		
		
		public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
			isReusable.set(false);
			
		    if (LOG.isLoggable(Level.FINE)) {
		    	LOG.fine("onDisconnect occured. Removing connection from pool (" + address.toString() + ")  (" + pool.toString() + ", idleTimeoutMillis=" + getPooledMaxIdleTimeMillis() + ", lifeTimeout=" + getPooledMaxLifeTimeMillis() + "): " + connection);
		    }

		    pool.remove(this);
		    
			try {
				NonBlockingConnectionProxy cp = proxyRef.get();
				if (cp!= null) {
					return cp.onDisconnect();					
				} else {
				    if (LOG.isLoggable(Level.FINE)) {
		                LOG.fine("could not call onDisconnect on proxy (proxy already dregistered?)");
		            }
					return true;
				}				
			} finally {
				countDestroyed.incrementAndGet();				
			}
		}
		

		
		public boolean onConnectionTimeout(INonBlockingConnection connection) throws IOException {
		    isReusable.set(false);

		    countConnectionTimeout.incrementAndGet();
		    
		    if (LOG.isLoggable(Level.FINE)) {
		    	LOG.fine("[" + getId() + "] connection timeout occured");
		    }

		    
		    NonBlockingConnectionProxy cp = proxyRef.get();
			if (cp != null) {
				return cp.onConnectionTimeout();
			} else {
				return false;  // let the connection be destroyed
			}
		}
		
		
		public boolean onIdleTimeout(INonBlockingConnection connection) throws IOException {
		    isReusable.set(false);

		    countIdleTimeout.incrementAndGet();

		    if (LOG.isLoggable(Level.FINE)) {
		    	LOG.fine("[" + getId() + "] idle timeout (" + DataConverter.toFormatedDuration(getConnection().getIdleTimeoutMillis()) + ") occured");
		    }
		    
		    NonBlockingConnectionProxy cp = proxyRef.get();
			if (cp != null) {
				return cp.onIdleTimeout();
			} else {
				return false;  // let the connection be destroyed
			}
		}
		
		
		
		int getUsage() {
			return usage;
		}
		
		
		long getCreationTimeMillis() {
			return creationTimeMillis;
		}

		NonBlockingConnection getConnection() {
			return connection;
		}

		InetSocketAddress getAddress() {
			return address;
		}

		
		
		void registerProxy(NonBlockingConnectionProxy proxy) {
			assert (proxyRef.get() == null);
			
			usage++;
			lastUsageTimeMillis = System.currentTimeMillis();
			
			proxyRef.set(proxy);
		}
		
		void unregister() {
		    proxyRef.set(null);
            lastUsageTimeMillis = System.currentTimeMillis();
		    
			try {
				// is connected?
				if (connection.isConnected()) {
					
					if (connection.isOpen() && isOpen() && (connection.available() == 0)) {
						
						// reset resource
						boolean isValid = isVaild(System.currentTimeMillis(), true);
						if (isValid) {
			                // .. and return it to the pool only if max idle size is not reached
							if ((maxIdle.get() != Integer.MAX_VALUE) || (pool.getNumIdle() >= maxIdle.get())) {
								return;
							}
							
							boolean isReset = connection.reset();
							if (isReset) {
								if (LOG.isLoggable(Level.FINE)) {
									LOG.fine("[" + connection.getId() + "] releasing connection (for reuse)");
								}
								returnToIdlePool(this);
								return;
							}
						}					
					}
					
					close();
				}
				

			} catch (Exception e) {
                // eat and log exception
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by releasing a pooled connection (" + address.toString() + ") " + e.toString());
				}
			}
		}
		
	

		/**
		 * check if the connection is valid
		 * 
		 * @return true, if reuseable
		 */
		private boolean isVaild(long currentTimeMillis, boolean closeIfInvalid) {

			// is open?
			if (isClosed.get()) {
			    if (LOG.isLoggable(Level.FINE)) {
			        LOG.fine("[" + getId() + "] is invalid (closed)");
			    }
				return false;
			}
			
			
			if (!isReusable.get()) {
				if (closeIfInvalid) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] closing connection because it is marked as non reuseable");
					}
					close();
				}
				return false;
			}

			if (!connection.isConnected() || !connection.isOpen()) {
				if (closeIfInvalid) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] closing connection because it is disconnected or closed");
					}
					close();
				}
				return false;
			}
	
			
			if (connection.getRemainingMillisToIdleTimeout() < MIN_REMAINING_MILLIS_TO_IDLE_TIMEOUT) {
			    if (closeIfInvalid) {
			        if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] closing connection because remaining time to idle timeout (" + connection.getRemainingMillisToIdleTimeout() + " millis) is to small");
					}
			        countRemainingMillisToIdleTimeoutToSmall.incrementAndGet();
					close();
			    }
				return false;
			}
	
				
			if (connection.getRemainingMillisToConnectionTimeout() < MIN_REMAINING_MILLIS_TO_CONNECTION_TIMEOUT) {
				if (closeIfInvalid) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] closing connection because remaining time to connection timeout (" + connection.getRemainingMillisToConnectionTimeout() + " millis)  is to small");
					}
					countRemainingConnectionToIdleTimeoutToSmall.incrementAndGet();
					close();
				}
				return false;
			}


			if ((poolIdleTimeoutMillis.get() != Integer.MAX_VALUE) && (currentTimeMillis > (lastUsageTimeMillis + poolIdleTimeoutMillis.get()))) {
				if (closeIfInvalid) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + connection.getId() + "] connection (" + address + ") pool idle timeout reached (" + poolIdleTimeoutMillis + ")"); 
					}
					countTimeoutPooledIdle.incrementAndGet();
					close();
				}
				return false;
			}


			
			
			if ((lifeTimeoutMillis.get() != Integer.MAX_VALUE) && (currentTimeMillis > (creationTimeMillis + lifeTimeoutMillis.get()))) {
				if (closeIfInvalid) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] connection (" + address + ") pool life timeout reached (" + lifeTimeoutMillis + ")"); 
					}
					countTimeoutPooledLifetime.incrementAndGet();
					close();
				}
				return false;
			}
	
			return true;
		}

		
		

		void close() {
			if (!isClosed.getAndSet(true)) {
			    pool.remove(this);
			    NonBlockingConnection.closeQuietly(connection);
			    
			    wakeupPendingRetrieve();
			}
		}		    

		

		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			try {
    			if (connection.isReceivingSuspended()) {
    				sb.append("[suspended] ");
    			}
    			
    			try {
        			sb.append(connection.getLocalAddress() + ":" + connection.getLocalPort() + " -> " +
        		   			  connection.getRemoteAddress() + ":" + connection.getRemotePort() + " " +
        		   			  "[" + connection.getId() + "]");
    			} catch (Exception e) {
    			    sb.append("[" + connection.getId() + "]");
    			}
    			
    		
    			SimpleDateFormat df = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
    
    			sb.append(" creationTime=" + df.format(getCreationTimeMillis()) + 
    					  ", ageMillis=" + (System.currentTimeMillis() - creationTimeMillis) +
    					  ", elapsedLastUsageMillis=" + (System.currentTimeMillis() - lastUsageTimeMillis) +
    					  ", countUsage=" + getUsage() +
    					  ", isReusable=" + isReusable.get());
			} catch (Exception ignore) {
			    
			}

			return sb.toString();
		}
	}

	
	
	


	private static final class NonBlockingConnectionProxy implements INonBlockingConnection {

		private volatile boolean isOpen = false;
		
		private final AtomicReference<NativeConnectionHolder> nativeConnectionHolderRef = new AtomicReference<NativeConnectionHolder>(null);
		private String id;

		
		private final AtomicReference<HandlerAdapter> handlerAdapterRef = new AtomicReference<HandlerAdapter>(null);
		private final AtomicReference<IHandlerChangeListener> handlerReplaceListenerRef = new AtomicReference<IHandlerChangeListener>();
		
		private Object attachment = null;
		
		private boolean isAutoflush = IConnection.DEFAULT_AUTOFLUSH;
		
		
		private final InetSocketAddress address;
		
		
		// statisitcs 
		private int countReuse;
		private long initialSendBytes;
		private long initialReceivedBytes;
		private long creationTime;
		private long elapsedLastUsage;
		
		// timeout support
		private final AtomicBoolean isIdleTimeoutOccured = new AtomicBoolean(false);
		private final AtomicBoolean isConnectionTimeoutOccured = new AtomicBoolean(false);

	    private final Object disconnectedGuard = false;
	    private boolean isDisconnected = false;


		
		
	    public NonBlockingConnectionProxy(InetSocketAddress address) {
	        this.address = address;
	    }
	        
	        
	    InetSocketAddress getAddress() {
	        return address;
	    }
	        
	        
	            
	    void onConnected(NativeConnectionHolder holder, IHandler handler) throws IOException {
	        isOpen = true;
            
            nativeConnectionHolderRef.set(holder);
            holder.getConnection().setAutoflush(false);
            
            creationTime = System.currentTimeMillis();
            elapsedLastUsage = holder.lastUsageTimeMillis;
            countReuse = holder.usage;
            id = holder.getConnection().getId() + "I" + Integer.toHexString(countReuse);
            
            initialReceivedBytes = holder.getConnection().getNumberOfReceivedBytes();
            initialSendBytes = holder.getConnection().getNumberOfSendBytes();
            
            setHandler(handler);
            
            onConnect();
            
            holder.registerProxy(this); 
            
            onData();
	    }
	        
	        
	    private boolean onConnect() {
	        NativeConnectionHolder holder = nativeConnectionHolderRef.get();
            HandlerAdapter handlerAdapter = handlerAdapterRef.get(); 
            if ((holder != null) && (handlerAdapter != null)) {
                try {
                    return handlerAdapter.onConnect(this, holder.connection.getTaskQueue(), holder.connection.getExecutor(), false);
                } catch (IOException ioe) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("[" + getId() + "] Error occured by perform onConnect callback on " + handlerAdapter + " " + ioe.toString());
                    }
                    return false;
                }
            } else {
                return false;
            }
	    }
	        
	    

	    void onConnectFailed(Executor workerpool, IOException ioe, IHandler appHandler) {

            HandlerAdapter handlerAdapter = HandlerAdapter.newInstance(appHandler);
            try {
                handlerAdapter.onConnectException(this, new SerializedTaskQueue(), workerpool, ioe);
            } catch (IOException e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + getId() + "] Error occured by perform onConnect callback on " + handlerAdapter + " " + e.toString());
                }
            }
        }
	    
	    
		private void ensureOpen() {
			if (!isOpen) {
				throw new RuntimeException("channel " + getId() + " is closed");
			}
		}
		

		public void setHandler(IHandler hdl) throws IOException {
			ensureOpen();
			
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			
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
				
		        if (hdl instanceof IHandlerChangeListener) {
		            handlerReplaceListenerRef.set((IHandlerChangeListener) hdl);
		        }
		        
		        boolean callDisconnect = false;
		        synchronized (disconnectedGuard) {
		            handlerAdapterRef.set(adapter);
		            if (isChangeListener) {
		                handlerReplaceListenerRef.set((IHandlerChangeListener) hdl);
		            }
		            
		            if (isDisconnected) {
		                callDisconnect = true;
		            }
		        }
		        
		        onData();

		        // is disconnected
		        if (callDisconnect) {
                    adapter.onDisconnect(this, holder.connection.getTaskQueue(), holder.connection.getExecutor(), false);
                }
			}
		}
		
		
		public IHandler getHandler() {
			ensureOpen();
			
			if (nativeConnectionHolderRef.get() == null) {
				return null;
			}
			
			HandlerAdapter handlerAdapter = handlerAdapterRef.get();
			if (handlerAdapter == null) {
				return null;
			} else {
				return ((HandlerAdapter) handlerAdapter).getHandler();
			}
		}
		
		boolean isDestroyed() {
			
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
				return !holder.getConnection().isConnected();
			} else {
				return true;
			}
		}

		
		public boolean isOpen() {
			if (!isOpen) {
				return false;
			}
		
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
				return holder.getConnection().isOpen();
			} else {
				return false;
			}
		}

		
		public void close() throws IOException {
		    initiateOnDisconnect();
		    			
            NativeConnectionHolder holder = nativeConnectionHolderRef.getAndSet(null);
            if (holder != null) {
                holder.unregister();
            }
		}
		

		void destroy() {
		    initiateOnDisconnect();
		    
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
				try {
					holder.close();
				} catch (Exception e) {
	                // eat and log exception
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("[" + getId() + "] error occured while destroying pooledConnectionHolder " + nativeConnectionHolderRef.get() + " reason: " + e.toString());
					}
				}
			}
		}

		private void initiateOnDisconnect() {
            try {
                onData();
            } catch (IOException ignore) { }
            onDisconnect();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			try {
    			sb.append(getId());
    			if (!isOpen()) {
    				sb.append(" closed");
    			}
    
    			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
    			if (holder != null) {
    				sb.append(" (" + holder.getAddress() + ") ");
    			}
    
    			sb.append(" (proxy ");
    			if (holder == null) {
    				sb.append("closed " +
                              ", countReuse=" + countReuse + 
                              ", ageProxyMillis=" + (System.currentTimeMillis() - creationTime) +
                              ", elapsedLastUsageMillis=" + (System.currentTimeMillis() - elapsedLastUsage));
    			} else {	
    				sb.append("received=" + getNumberOfReceivedBytes() + 
    				          ", sent=" + getNumberOfSendBytes() + 
    				          ", countReuse=" + countReuse + 
    				          ", agePhysicalMillis=" + (System.currentTimeMillis() - holder.creationTimeMillis) +
    				          ", pooledLifeTimeout=" + holder.getPooledMaxLifeTimeMillis() +
    				          ", pooledIdleTimeout=" + holder.getPooledMaxIdleTimeMillis() +
    				          ", ageProxyMillis=" + (System.currentTimeMillis() - creationTime) +
    				          ", elapsedLastUsageMillis=" + (System.currentTimeMillis() - elapsedLastUsage) +
                              ", elapsedTimeLastSent=" + (System.currentTimeMillis() - holder.getConnection().getLastTimeSendMillis()) +
    				          ", elapsedTimeLastReceived=" + (System.currentTimeMillis() - holder.getConnection().getLastTimeReceivedMillis()));
    			}
			} catch (Exception ignore) {
			    
			}
			
			return sb.toString();
		}
		

		public String getId() {
			return id;
		}
		
		
		
		private boolean onDisconnect() {
		      
	        HandlerAdapter adapter = null;
	        synchronized (disconnectedGuard) {
	            if (isDisconnected) {
	                return true;
	            } else {
	                isDisconnected = true;
	                adapter = handlerAdapterRef.get();
	            }
	        }

            NativeConnectionHolder holder = nativeConnectionHolderRef.get();
            if ((holder != null) && (adapter != null)) {
                adapter.onDisconnect(this, holder.connection.getTaskQueue(), holder.connection.getExecutor(), false);
            }
			
			return true;
		}
		
		
	
		
		private boolean onData() throws IOException, MaxReadSizeExceededException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
            HandlerAdapter handlerAdapter = handlerAdapterRef.get(); 
            if ((holder != null) && (handlerAdapter != null)) {
				return handlerAdapter.onData(this, holder.connection.getTaskQueue(), holder.connection.getExecutor(), false, false);
			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] onData called even though proxy is closed");
				}
				return true;
			}
		}

		
		
		private boolean onConnectionTimeout() throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
            HandlerAdapter handlerAdapter = handlerAdapterRef.get(); 
            if ((holder != null) && (handlerAdapter != null)) {
				if (!isConnectionTimeoutOccured.getAndSet(true)) {
					return handlerAdapter.onConnectionTimeout(this, holder.connection.getTaskQueue(), holder.connection.getExecutor());
				} else {
					return true;
				}
			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] onConnectionTimeout called even though proxy is closed");
				}
				return true;
			}
		}

		
		private boolean onIdleTimeout() throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
            HandlerAdapter handlerAdapter = handlerAdapterRef.get(); 
            if ((holder != null) && (handlerAdapter != null)) {
				if (!isIdleTimeoutOccured.getAndSet(true)) {
					return handlerAdapter.onIdleTimeout(this, holder.connection.getTaskQueue(), holder.connection.getExecutor());
				} else {
					return true;
				}
			} else {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + getId() + "] onIdletimeout called even though proxy is closed");
				}
				return true;
			}
		}

		
		public boolean isServerSide() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().isServerSide();
			} else {
				throw newClosedChannelRuntimeException();
			}		
		}
		
		
		public Executor getWorkerpool() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
				return holder.getConnection().getWorkerpool();
			} else {
				return NonBlockingConnection.getDefaultWorkerpool();
			}
		}

		public void setWorkerpool(Executor workerpool) {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().setWorkerpool(workerpool);
			} 
		}


		public void setAttachment(Object obj) {
			this.attachment = obj;
		}

		
		public Object getAttachment() {
			return attachment;
		}

		
		public void setAutoflush(boolean autoflush) {
		    isAutoflush = autoflush;
		}

		public boolean isAutoflush() {
		    return isAutoflush;
		}

		
		public void setEncoding(String defaultEncoding) {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().setEncoding(defaultEncoding);
			} 			
		}

		public String getEncoding() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getEncoding();
			} else {
				return IConnection.INITIAL_DEFAULT_ENCODING;
			}								
		}

		public void setFlushmode(FlushMode flushMode) {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().setFlushmode(flushMode);
			} 										
		}

		public FlushMode getFlushmode() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getFlushmode();
			} else {
				return IConnection.DEFAULT_FLUSH_MODE;
			}															
		}
		
		public void setOption(String name, Object value) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().setOption(name, value);
			} 																		
		}

		public Object getOption(String name) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        return holder.getConnection().getOption(name);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }			        
			} else {
				throw newClosedChannelRuntimeException();
			}																							
		}
		
		public long getNumberOfReceivedBytes() {
		    NativeConnectionHolder holder = nativeConnectionHolderRef.get();
            if ((holder != null)) {
                return holder.getConnection().getNumberOfReceivedBytes() - initialReceivedBytes;
            } else {
                throw newClosedChannelRuntimeException();
            }   
	    }
	    
	    public long getNumberOfSendBytes() {
	        NativeConnectionHolder holder = nativeConnectionHolderRef.get();
            if ((holder != null)) {
                return holder.getConnection().getNumberOfSendBytes() - initialSendBytes;
            } else {
                throw newClosedChannelRuntimeException();
            }
	    }
		

		@SuppressWarnings("unchecked")
		public Map<String, Class> getOptions() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getOptions();
			} else {
				throw newClosedChannelRuntimeException();
			}																											
		}

		public void setWriteTransferRate(int bytesPerSecond) throws ClosedChannelException, IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().setWriteTransferRate(bytesPerSecond);
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }
			} 																													
		}
		
		public int getWriteTransferRate() throws ClosedChannelException, IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        return holder.getConnection().getWriteTransferRate();
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			} else {
				return INonBlockingConnection.UNLIMITED;
			}																																			
		}
		
		public int getMaxReadBufferThreshold() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getMaxReadBufferThreshold();
			} else {
				return Integer.MAX_VALUE;
			}																																							
		}
		
		public void setMaxReadBufferThreshold(int size) {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().setMaxReadBufferThreshold(size);
			} 																																						
		}

		public void setConnectionTimeoutMillis(long timeoutMillis) {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().setConnectionTimeoutMillis(timeoutMillis);
			} 																																										
		}

		public long getConnectionTimeoutMillis() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getConnectionTimeoutMillis();
			} else {
				return Long.MAX_VALUE;
			}
		}

		public void setIdleTimeoutMillis(long timeoutInMillis) {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().setIdleTimeoutMillis(timeoutInMillis);
				isIdleTimeoutOccured.set(false);
			} else {
				throw newClosedChannelRuntimeException();
			}																																															
		}

		public long getIdleTimeoutMillis() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getIdleTimeoutMillis();
			} else {
				return Long.MAX_VALUE;
			}																																											
		}

		public long getRemainingMillisToConnectionTimeout() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getConnectionTimeoutMillis();
			} else {
				return Long.MAX_VALUE;
			}																																							
		}

		public long getRemainingMillisToIdleTimeout() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getRemainingMillisToIdleTimeout();
			} else {
				return Long.MAX_VALUE;
			}																																											
		}

		public boolean isSecure() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().isSecure();
			} else {
				throw newClosedChannelRuntimeException();
			}		
		}

		
		public void activateSecuredMode() throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().activateSecuredMode();
	             } catch (IOException ioe) {
	                 destroy();
	                 throw ioe;
	             }
			} 																																										
		}
		
		public void deactivateSecuredMode() throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().deactivateSecuredMode();
	             } catch (IOException ioe) {
	                 destroy();
	                 throw ioe;
	             }
			}			
		}
		
		public boolean isSecuredModeActivateable() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().isSecuredModeActivateable();
			} else {
				return false;
			}																																															
		}

		
		public void suspendReceiving() throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().suspendReceiving();
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }
			} 																																														
		}

	
		public boolean isReceivingSuspended() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().isReceivingSuspended();
			} else {
				return false;
			}																																															
		}
		
		public void resumeReceiving() throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().resumeReceiving();
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }
			} 																																																	
		}
		
		
		public InetAddress getLocalAddress() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
				return holder.getConnection().getLocalAddress();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}

		public int getLocalPort() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
				return holder.getConnection().getLocalPort();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}

		public InetAddress getRemoteAddress() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
				return holder.getConnection().getRemoteAddress();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}

		public int getRemotePort()  {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
				return holder.getConnection().getRemotePort();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																							
		}


		public void write(ByteBuffer[] buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().write(buffers, writeCompletionHandler);
			        if (isAutoflush) {
			            flush();
			        }
                } catch (IOException ioe) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("error occured by writing " + DataConverter.toString(buffers) + " deregistering ");
                    }
                    destroy();
                    throw ioe;
                }
			} else {
				throw newClosedChannelException();
			}																																															
		}
		
		public long write(ByteBuffer[] buffers) throws IOException, BufferOverflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			
			if (isOpen && (holder != null)) {
			    try {
    			    long written = holder.getConnection().write(buffers);
                    if (isAutoflush) {
                        flush();
                    }
                    return written;
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
				
			} else {
				throw newClosedChannelException();
			}																																																			
		}

		public int write(ByteBuffer buffer) throws IOException, BufferOverflowException {
		    return (int) write(new ByteBuffer[] { buffer });
        }
        
		public void write(ByteBuffer buffer, IWriteCompletionHandler writeCompletionHandler) throws IOException {
		    write(new ByteBuffer[] { buffer }, writeCompletionHandler);
		}
		
		public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
		    return write(DataConverter.toByteBuffers(srcs, offset, length));																																																			
		}
		
		public void write(ByteBuffer[] srcs, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
            write(DataConverter.toByteBuffers(srcs, offset, length), writeCompletionHandler);																																																			
        }

		public long write(List<ByteBuffer> buffers) throws IOException, BufferOverflowException {
		    return write(buffers.toArray(new ByteBuffer[buffers.size()]));																															
		}
		
		public void write(List<ByteBuffer> buffers, IWriteCompletionHandler writeCompletionHandler) throws IOException {
		    write(buffers.toArray(new ByteBuffer[buffers.size()]), writeCompletionHandler);
		}

        public void write(byte[] bytes, IWriteCompletionHandler writeCompletionHandler) throws IOException {
            write(DataConverter.toByteBuffer(bytes), writeCompletionHandler);                                                                                                                                                                                           
        }

        public int write(byte[] bytes, int offset, int length) throws IOException, BufferOverflowException {
            return write(DataConverter.toByteBuffer(bytes, offset, length));
        }
        
        public void write(byte[] bytes, int offset, int length, IWriteCompletionHandler writeCompletionHandler) throws IOException {
            write(DataConverter.toByteBuffer(bytes, offset, length), writeCompletionHandler);                                                                                                                                                      
        }
		
		public int write(byte b) throws IOException, BufferOverflowException {
		    return write(DataConverter.toByteBuffer(b));                                                                                                                                                                                                           
		}

		public int write(byte... bytes) throws IOException, BufferOverflowException {
		    return write(DataConverter.toByteBuffer(bytes));                                                                                                                                                                                           
		}

		
		public int write(long l) throws IOException, BufferOverflowException {
		    return write(DataConverter.toByteBuffer(l));
		}

		public int write(double d) throws IOException, BufferOverflowException {
            return write(DataConverter.toByteBuffer(d));
		}

		public int write(int i) throws IOException, BufferOverflowException {
		    return write(DataConverter.toByteBuffer(i));                                                                                                                                                                                                            
		}

		public int write(short s) throws IOException, BufferOverflowException {
		    return write(DataConverter.toByteBuffer(s));
		}

		public int write(String message) throws IOException, BufferOverflowException {
		    return write(DataConverter.toByteBuffer(message, getEncoding()));
		}

		public int write(String message, String encoding) throws IOException, BufferOverflowException {
		    return write(DataConverter.toByteBuffer(message, encoding));
		}
		
		public void write(String message, String encoding, IWriteCompletionHandler writeCompletionHandler) throws IOException {
		    write(DataConverter.toByteBuffer(message, encoding), writeCompletionHandler);
		}

		public void flush() throws ClosedChannelException, IOException, SocketTimeoutException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().flush();
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }

			} else {
				throw newClosedChannelException();
			}																																																		
		}
		
		
		public long transferFrom(FileChannel fileChannel) throws IOException, BufferOverflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			
			if (isOpen && (holder != null)) {
			    try {
    			    if (getFlushmode() == FlushMode.SYNC) {
    		            if (LOG.isLoggable(Level.FINE)) {
    		                LOG.fine("tranfering file by using MappedByteBuffer (MAX_MAP_SIZE=" + AbstractNonBlockingStream.TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE + ")");
    		            }
    		            
    		            final long size = fileChannel.size();
    		            long remaining = size;
    		                
    		            long offset = 0;
    		            long length = 0;
    		                
    		            do {
    		                if (remaining > AbstractNonBlockingStream.TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE) {
    		                    length = AbstractNonBlockingStream.TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE;
    		                } else {
    		                    length = remaining;
    		                }
    		                    
    		                MappedByteBuffer buffer = fileChannel.map(MapMode.READ_ONLY, offset, length);
    		                long written = write(buffer);
    		                    
    		                offset += written;
    		                remaining -= written;
    		            } while (remaining > 0);
    		                    
    		            return size;
    		            
    		        } else {
    		            return transferFrom((ReadableByteChannel) fileChannel);
    		        }
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			    
			} else {
				throw newClosedChannelException();
			}																																																			
		}

		public long transferFrom(ReadableByteChannel source) throws IOException, BufferOverflowException {
		    return transferFrom(source, AbstractNonBlockingStream.TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE);																																																	
		}

		public long transferFrom(ReadableByteChannel source, int chunkSize) throws IOException, BufferOverflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();

			if (isOpen && (holder != null)) {
			    try {
    		        long transfered = 0;
    
    		        int read = 0;
    		        do {
    		            ByteBuffer transferBuffer = ByteBuffer.allocate(chunkSize);
    		            read = source.read(transferBuffer);
    
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
    		        
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }

			} else {
				throw newClosedChannelException();
			}																																																			
		}

		
		public int getPendingWriteDataSize() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().getPendingWriteDataSize();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}

		public void markWritePosition() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().markWritePosition();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}

		public void removeWriteMark() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().removeWriteMark();
			}																																																		
		}

		public boolean resetToWriteMark() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().resetToWriteMark();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																							
		}

		public void markReadPosition() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().markReadPosition();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}

		public void removeReadMark() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				holder.getConnection().removeReadMark();
			} 																																																		
		}

		public boolean resetToReadMark() {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
				return holder.getConnection().resetToReadMark();
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}
		
	
		public int available() throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    return holder.getConnection().available();
			        
			} else {
				return -1;
			}
		}

				
		public int getReadBufferVersion() throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    return holder.getConnection().getReadBufferVersion();
			        
			} else {
				return -1;
			}
		}

		public int indexOf(String str) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().indexOf(str);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				return -1;
			}
		}

		public int indexOf(String str, String encoding) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().indexOf(str, encoding);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				return -1;
			}
		}
		
		
		public void unread(ByteBuffer[] buffers) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().unread(buffers);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}
		
		
		public void unread(byte[] bytes) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().unread(bytes);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}

		

		public void unread(ByteBuffer buffer) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().unread(buffer);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}

		
		public void unread(String text) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (isOpen && (holder != null)) {
			    try {
			        holder.getConnection().unread(text);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelRuntimeException();
			}																																																			
		}
		
		
		public int read(ByteBuffer buffer) throws IOException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().read(buffer);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				return -1;
			}
		}

		public byte readByte() throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readByte();
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readByteBufferByDelimiter(delimiter);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readByteBufferByDelimiter(delimiter, maxLength);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readByteBufferByDelimiter(delimiter, encoding);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public ByteBuffer[] readByteBufferByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readByteBufferByDelimiter(delimiter, encoding, maxLength);
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }

			} else {
				throw newClosedChannelException();
			}																																															
		}

		public ByteBuffer[] readByteBufferByLength(int length) throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readByteBufferByLength(length);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public byte[] readBytesByDelimiter(String delimiter) throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readBytesByDelimiter(delimiter);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public byte[] readBytesByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readBytesByDelimiter(delimiter, maxLength);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public byte[] readBytesByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readBytesByDelimiter(delimiter, encoding);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public byte[] readBytesByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readBytesByDelimiter(delimiter, encoding, maxLength);
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }

			} else {
				throw newClosedChannelException();
			}																																															
		}

		public byte[] readBytesByLength(int length) throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readBytesByLength(length);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public double readDouble() throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readDouble();
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }

			} else {
				throw newClosedChannelException();
			}																																															
		}

		public int readInt() throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readInt();
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public long readLong() throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readLong();
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public short readShort() throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readShort();
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public String readStringByDelimiter(String delimiter) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readStringByDelimiter(delimiter);
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }

			} else {
				throw newClosedChannelException();
			}																																															
		}

		public String readStringByDelimiter(String delimiter, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readStringByDelimiter(delimiter, maxLength);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public String readStringByDelimiter(String delimiter, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readStringByDelimiter(delimiter, encoding);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public String readStringByDelimiter(String delimiter, String encoding, int maxLength) throws IOException, BufferUnderflowException, UnsupportedEncodingException, MaxReadSizeExceededException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readStringByDelimiter(delimiter, encoding, maxLength);
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }

			} else {
				throw newClosedChannelException();
			}																																															
		}

		public String readStringByLength(int length) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readStringByLength(length);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public String readStringByLength(int length, String encoding) throws IOException, BufferUnderflowException, UnsupportedEncodingException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().readStringByLength(length, encoding);
                } catch (IOException ioe) {
                    destroy();
                    throw ioe;
                }
			        
			} else {
				throw newClosedChannelException();
			}																																															
		}

		public long transferTo(WritableByteChannel target, int length) throws IOException, BufferUnderflowException {
			NativeConnectionHolder holder = nativeConnectionHolderRef.get();
			if (holder != null) {
			    try {
			        return holder.getConnection().transferTo(target, length);
			    } catch (IOException ioe) {
			        destroy();
			        throw ioe;
			    }

			} else {
				throw newClosedChannelException();
			}																																															
		}
		
		private ExtendedClosedChannelException newClosedChannelException() {
			return new ExtendedClosedChannelException("channel " + getId() + " is already closed");
		}
		
		private RuntimeException newClosedChannelRuntimeException() {
			return new RuntimeException("channel " + getId() + " is already closed");
		}
	}
}
