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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;

/**
 * 先执行静态方法块.	
 * 定义了一系列的属性.
 * </br></br>
 * 
 * IoProvider
 * 
 * @author grro@xsocket.org
 */
final class IoProvider  {

	private static final Logger LOG = Logger.getLogger(IoProvider.class.getName());

	// socket选项
	// 客户端可设置以下的所有选项
	static final String SO_SNDBUF = IConnection.SO_SNDBUF;
	static final String SO_RCVBUF = IConnection.SO_RCVBUF;
	static final String SO_REUSEADDR = IConnection.SO_REUSEADDR;
	static final String SO_TIMEOUT = "SOL_SOCKET.SO_TIMEOUT";
	static final String SO_KEEPALIVE = IConnection.SO_KEEPALIVE;
	static final String SO_LINGER = IConnection.SO_LINGER;
	static final String TCP_NODELAY = IConnection.TCP_NODELAY;

	
	static final int UNLIMITED = INonBlockingConnection.UNLIMITED;
	static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = IConnection.DEFAULT_CONNECTION_TIMEOUT_MILLIS;
	static final long DEFAULT_IDLE_TIMEOUT_MILLIS = IConnection.DEFAULT_IDLE_TIMEOUT_MILLIS;

	
	private static final Timer TIMER = new Timer("xIoTimer", true);
	// 客户端使用
	private static IoSocketDispatcherPool globalClientDispatcherPool;

	// transfer props
    static final String TRANSFER_MAPPED_BYTE_BUFFER_MAX_MAP_SIZE_KEY = "org.xsocket.connection.transfer.mappedbytebuffer.maxsize";
    static final int DEFAULT_TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE = 16384;
    private static Integer transferByteBufferMaxSize;

		
	// connection props
    // 压制同步刷新警告
	public static final String SUPPRESS_SYNC_FLUSH_WARNING_KEY = "org.xsocket.connection.suppressSyncFlushWarning";	// boolean
	public static final String DEFAULT_SUPPRESS_SYNC_FLUSH_WARNING = "false";
    public static final String SUPPRESS_REUSE_BUFFER_WARNING_KEY = "org.xsocket.connection.suppressReuseBufferWarning";
    public static final String DEFAULT_SUPPRESS_REUSE_BUFFER_WARNING = "false";
    public static final String SUPPRESS_SYNC_FLUSH_COMPLETION_HANDLER_WARNING_KEY = "org.xsocket.connection.suppressSyncFlushCompletionHandlerWarning";
    public static final String DEFAULT_SUPPRESS_SYNC_FLUSH_COMPLETION_HANDLER_WARNING = "false";

    
    private static boolean IS_REUSE_ADDRESS = Boolean.parseBoolean(System.getProperty("org.xsocket.connection.server.reuseaddress", "true"));
    
	
	// dispatcher props
    // 调度属性
    // 调度器的初始数目：创建IoSocketDispatcher的数目(一个IoSocketDispatcher对应一个Selector)
	public static final String COUNT_DISPATCHER_KEY                = "org.xsocket.connection.dispatcher.initialCount";	// int
	// 服务器端调度器的初始数目
	private static final String COUNT_SERVER_DISPATCHER_KEY        = "org.xsocket.connection.server.dispatcher.initialCount";	// int
	// 客户端调度器的初始数目
	private static final String COUNT_CLIENT_DISPATCHER_KEY        = "org.xsocket.connection.client.dispatcher.initialCount";	// int
	// 调度器处理的最大数量
	private static final String MAX_HANDLES                        = "org.xsocket.connection.dispatcher.maxHandles";	// int
	private static final String DETACH_HANDLE_ON_NO_OPS            = "org.xsocket.connection.dispatcher.detachHandleOnNoOps";	// boolean
	private static final String DEFAULT_DETACH_HANDLE_ON_NO_OPS    = "false";
	private static final String IS_BYPASSING_WRITE_ALLOWED         = "org.xsocket.connection.dispatcher.bypassingWriteAllowed";	// boolean
	private static final String DEFAULT_IS_BYPASSING_WRITE_ALLOWED = "true";
	
	
	   
    ////////////////////////////////////////////////
    // use direct buffer or non-direct buffer?
    //
    // current vm implementations (Juli/2007) seems to have
    // problems by gc direct buffers.
    //
    // links
    // * [Java bugdatabase] http://bugs.sun.com/bugdatabase/view_bug.do;jsessionid=94d5403110224b692e5354bd87a92:WuuT?bug_id=6210541
    // * [forum thread]     http://forums.java.net/jive/thread.jspa?messageID=223706&tstart=0
    // * [mina]             https://issues.apache.org/jira/browse/DIRMINA-391
    //
    ////////////////////////////////////////////////

	
	// direct buffer?
	public static final String DEFAULT_USE_DIRECT_BUFFER = "false";
	public static final String CLIENT_READBUFFER_USE_DIRECT_KEY             = "org.xsocket.connection.client.readbuffer.usedirect";
	public static final String SERVER_READBUFFER_USE_DIRECT_KEY             = "org.xsocket.connection.server.readbuffer.usedirect";
	private static final String WRITE_BUFFER_USE_DIRECT_KEY                 = "org.xsocket.connection.writebuffer.usedirect";
	
	
	// preallocation params
	// 预分配参数
	public static final String DEFAULT_READ_BUFFER_PREALLOCATION_ON = "true";
	// 默认读缓冲区大小
	public static final int DEFAULT_READ_BUFFER_PREALLOCATION_SIZE = 16384;
	public static final int DEFAULT_READ_BUFFER_MIN_SIZE = 64;
	
	
	public static final String CLIENT_READBUFFER_PREALLOCATION_ON_KEY       = "org.xsocket.connection.client.readbuffer.preallocation.on";
	public static final String CLIENT_READBUFFER_PREALLOCATION_SIZE_KEY     = "org.xsocket.connection.client.readbuffer.preallocation.size";
	public static final String CLIENT_READBUFFER_PREALLOCATION_MIN_SIZE_KEY = "org.xsocket.connection.client.readbuffer.preallocated.minSize";
	
	public static final String SERVER_READBUFFER_PREALLOCATION_ON_KEY       = "org.xsocket.connection.server.readbuffer.preallocation.on";
	public static final String SERVER_READBUFFER_PREALLOCATION_SIZE_KEY     = "org.xsocket.connection.server.readbuffer.preallocation.size";
	public static final String SERVER_READBUFFER_PREALLOCATION_MIN_SIZE_KEY = "org.xsocket.connection.server.readbuffer.preallocated.minSize";
	
	
	public static final String DEFAULT_CLIENT_MAX_READBUFFER_SIZE_KEY       = "org.xsocket.connection.client.readbuffer.defaultMaxReadBufferThreshold";
	public static final String DEFAULT_SERVER_MAX_READBUFFER_SIZE_KEY       = "org.xsocket.connection.server.readbuffer.defaultMaxReadBufferThreshold";

	public static final String DEFAULT_CLIENT_MAX_WRITEBUFFER_SIZE_KEY       = "org.xsocket.connection.client.readbuffer.defaultMaxWriteBufferThreshold";
	public static final String DEFAULT_SERVER_MAX_WRITEBUFFER_SIZE_KEY       = "org.xsocket.connection.server.readbuffer.defaultMaxWriteBufferThreshold";

	
	private static Integer defaultClientMaxReadbufferSize;
	private static Integer defaultServerMaxReadbufferSize;
	private static Integer defaultClientMaxWritebufferSize;
	private static Integer defaultServerMaxWritebufferSize;

	
	private static final String SSLENGINE_CLIENT_ENABLED_CIPHER_SUITES_KEY  = "org.xsocket.connection.client.ssl.sslengine.enabledCipherSuites";
	private static final String SSLENGINE_SERVER_ENABLED_CIPHER_SUITES_KEY  = "org.xsocket.connection.server.ssl.sslengine.enabledCipherSuites";
	private static final String SSLENGINE_CLIENT_ENABLED_PROTOCOLS_KEY  = "org.xsocket.connection.client.ssl.sslengine.enabledProtocols";
	private static final String SSLENGINE_SERVER_ENABLED_PROTOCOLS_KEY  = "org.xsocket.connection.server.ssl.sslengine.enabledProtocols";
	private static final String SSLENGINE_SERVER_WANT_CLIENT_AUTH_KEY  = "org.xsocket.connection.server.ssl.sslengine.wantClientAuth";
	private static final String SSLENGINE_SERVER_NEED_CLIENT_AUTH_KEY  = "org.xsocket.connection.server.ssl.sslengine.needClientAuth";
	
	
	private static String[] sSLEngineClientEnabledCipherSuites;
	private static String[] sSLEngineServerEnabledCipherSuites;
	private static String[] sSLEngineClientEnabledProtocols;
	private static String[] sSLEngineServerEnabledProtocols;
	private static Boolean sSLEngineWantClientAuth;
	private static Boolean sSLEngineNeedClientAuth; 
	
	
	private static Integer countDispatcher;
	private static Integer countClientDispatcher;
	private static Integer countServerDispatcher;
	private static Integer maxHandles;
	private static boolean detachHandleOnNoOps = true; 
	private static boolean bypassingWriteAllowed = false;
    
    private static Boolean suppressSyncFlushWarning;
    private static boolean suppressSyncFlushCompletionHandlerWarning;

    private static Boolean suppressReuseBufferWarning;
    
	private static Boolean clientReadBufferUseDirect;
	private static Boolean serverReadBufferUseDirect;
	private static Boolean writeBufferUseDirect;
	
	private static Boolean clientReadBufferPreallocationOn;
	private static int clientReadBufferPreallocationsize = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	private static int clientReadBufferMinsize = DEFAULT_READ_BUFFER_MIN_SIZE;
	
	private static Boolean serverReadBufferPreallocationOn;
	private static int serverReadBufferPreallocationsize = DEFAULT_READ_BUFFER_PREALLOCATION_SIZE;
	private static int serverReadBufferMinsize = DEFAULT_READ_BUFFER_MIN_SIZE;

	private final static String idPrefix;


    static {
    	// 调度属性
    	countDispatcher = readIntProperty(COUNT_DISPATCHER_KEY);
    	countClientDispatcher = readIntProperty(COUNT_CLIENT_DISPATCHER_KEY);
    	countServerDispatcher = readIntProperty(COUNT_SERVER_DISPATCHER_KEY);
    	maxHandles = readIntProperty(MAX_HANDLES);
    	detachHandleOnNoOps = readBooleanProperty(DETACH_HANDLE_ON_NO_OPS, DEFAULT_DETACH_HANDLE_ON_NO_OPS);
    	bypassingWriteAllowed = readBooleanProperty(IS_BYPASSING_WRITE_ALLOWED, DEFAULT_IS_BYPASSING_WRITE_ALLOWED);
    	
    	// transfer props
    	transferByteBufferMaxSize = readIntProperty(TRANSFER_MAPPED_BYTE_BUFFER_MAX_MAP_SIZE_KEY, DEFAULT_TRANSFER_BYTE_BUFFER_MAX_MAP_SIZE);

    	// coonection
    	suppressSyncFlushWarning = readBooleanProperty(IoProvider.SUPPRESS_SYNC_FLUSH_WARNING_KEY, DEFAULT_SUPPRESS_SYNC_FLUSH_WARNING);
        suppressSyncFlushCompletionHandlerWarning = readBooleanProperty(IoProvider.SUPPRESS_SYNC_FLUSH_COMPLETION_HANDLER_WARNING_KEY, DEFAULT_SUPPRESS_SYNC_FLUSH_COMPLETION_HANDLER_WARNING);
    	suppressReuseBufferWarning = readBooleanProperty(IoProvider.SUPPRESS_REUSE_BUFFER_WARNING_KEY, DEFAULT_SUPPRESS_REUSE_BUFFER_WARNING);    	
    	
    	// direct buffer?
   		clientReadBufferUseDirect = readBooleanProperty(IoProvider.CLIENT_READBUFFER_USE_DIRECT_KEY, DEFAULT_USE_DIRECT_BUFFER);
   		serverReadBufferUseDirect = readBooleanProperty(IoProvider.SERVER_READBUFFER_USE_DIRECT_KEY, DEFAULT_USE_DIRECT_BUFFER);
   		writeBufferUseDirect = readBooleanProperty(IoProvider.WRITE_BUFFER_USE_DIRECT_KEY, DEFAULT_USE_DIRECT_BUFFER);
   		
   		// thresholds
   		defaultClientMaxReadbufferSize = readIntProperty(IoProvider.DEFAULT_CLIENT_MAX_READBUFFER_SIZE_KEY);
   		defaultServerMaxReadbufferSize = readIntProperty(IoProvider.DEFAULT_SERVER_MAX_READBUFFER_SIZE_KEY);
   		defaultClientMaxWritebufferSize = readIntProperty(IoProvider.DEFAULT_CLIENT_MAX_WRITEBUFFER_SIZE_KEY);
   		defaultServerMaxWritebufferSize = readIntProperty(IoProvider.DEFAULT_SERVER_MAX_WRITEBUFFER_SIZE_KEY);
    	
   		
   		// ssl props 
   		sSLEngineServerEnabledProtocols = readStringArrayProperty(IoProvider.SSLENGINE_SERVER_ENABLED_PROTOCOLS_KEY, null);
   		sSLEngineClientEnabledProtocols = readStringArrayProperty(IoProvider.SSLENGINE_CLIENT_ENABLED_PROTOCOLS_KEY, null);
   		sSLEngineServerEnabledCipherSuites = readStringArrayProperty(IoProvider.SSLENGINE_SERVER_ENABLED_CIPHER_SUITES_KEY, null);
   		sSLEngineClientEnabledCipherSuites = readStringArrayProperty(IoProvider.SSLENGINE_CLIENT_ENABLED_CIPHER_SUITES_KEY, null);
   		sSLEngineWantClientAuth = readBooleanProperty(IoProvider.SSLENGINE_SERVER_WANT_CLIENT_AUTH_KEY, null);
   		sSLEngineNeedClientAuth = readBooleanProperty(IoProvider.SSLENGINE_SERVER_NEED_CLIENT_AUTH_KEY, null);
    	
   		// preallocation
   		clientReadBufferPreallocationOn = readBooleanProperty(IoProvider.CLIENT_READBUFFER_PREALLOCATION_ON_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_ON);
    	if (clientReadBufferPreallocationOn) {
	    	clientReadBufferPreallocationsize = readIntProperty(IoProvider.CLIENT_READBUFFER_PREALLOCATION_SIZE_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_SIZE);
	    	clientReadBufferMinsize = readIntProperty(IoProvider.CLIENT_READBUFFER_PREALLOCATION_MIN_SIZE_KEY, DEFAULT_READ_BUFFER_MIN_SIZE);
    	}

    	// 默认为true
    	serverReadBufferPreallocationOn = readBooleanProperty(IoProvider.SERVER_READBUFFER_PREALLOCATION_ON_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_ON);
    	if (serverReadBufferPreallocationOn) {   		
    		serverReadBufferPreallocationsize = readIntProperty(IoProvider.SERVER_READBUFFER_PREALLOCATION_SIZE_KEY, DEFAULT_READ_BUFFER_PREALLOCATION_SIZE);
    		serverReadBufferMinsize = readIntProperty(IoProvider.SERVER_READBUFFER_PREALLOCATION_MIN_SIZE_KEY, DEFAULT_READ_BUFFER_MIN_SIZE);
    	}


    	// prepare id prefix
    	// 准备ID的前缀
    	String base = null;
    	try {
    		base = InetAddress.getLocalHost().getHostAddress();
    	} catch (Exception e) {
    		base = UUID.randomUUID().toString();
    	}

   		int random = 0;
   		Random rand = new Random();
   		do {
   			random = rand.nextInt();
   		} while (random < 0);
   		
   		// ipaddress hash + currentTime + random 
   		idPrefix = Integer.toHexString(base.hashCode()) + Long.toHexString(System.currentTimeMillis()) + Integer.toHexString(random);
   		
		if (LOG.isLoggable(Level.FINE)) {
			StringBuilder sb = new StringBuilder();
			sb.append(IoProvider.class.getName() + " initialized (");
			
			// disptacher params
			sb.append("countDispatcher=" + countDispatcher + " ");
			sb.append("maxHandles=" + maxHandles + " ");
			sb.append("detachHandleOnNoOps=" + detachHandleOnNoOps + " ");
		 	
			
			// client params
			// 如果是服务器端的话,client的参数没有必要打印
			sb.append("client: directMemory=" + clientReadBufferUseDirect);
			sb.append(" preallocation=" + clientReadBufferPreallocationOn);
			if (clientReadBufferPreallocationOn) {
				sb.append(" preallocationSize=" + DataConverter.toFormatedBytesSize(clientReadBufferPreallocationsize));
				sb.append(" minBufferSize=" + DataConverter.toFormatedBytesSize(clientReadBufferMinsize));
			} 
			
			// server params 
			sb.append(" & server: directMemory=" + serverReadBufferUseDirect);
			sb.append(" preallocation=" + serverReadBufferPreallocationOn);
			if (serverReadBufferPreallocationOn) {
				sb.append(" preallocationSize=" + DataConverter.toFormatedBytesSize(serverReadBufferPreallocationsize));
				sb.append(" minBufferSize=" + DataConverter.toFormatedBytesSize(serverReadBufferMinsize));
			} 
			
			sb.append(")");
			LOG.fine(sb.toString());
		}
     }


	private AbstractMemoryManager sslMemoryManagerServer;
	private AbstractMemoryManager sslMemoryManagerClient;

	private final AtomicInteger nextId = new AtomicInteger();

	
	/** 先执行静态方法块 */
	IoProvider() {
		// 同时创建服务器端和客户端的MemoryManager
		// 在createIoHandler()方法中会使用到
		// FIXME：这两个都是SSL的MemoryManager,如果不是SSL,那么在这里实例化这两个对象是没有必要的.
		if (serverReadBufferPreallocationOn) {
			sslMemoryManagerServer = IoSynchronizedMemoryManager.createPreallocatedMemoryManager(serverReadBufferPreallocationsize, serverReadBufferMinsize, serverReadBufferUseDirect);
		} else {
			sslMemoryManagerServer = IoSynchronizedMemoryManager.createNonPreallocatedMemoryManager(serverReadBufferUseDirect);
		}
		
		if (clientReadBufferPreallocationOn) {
			sslMemoryManagerClient = IoSynchronizedMemoryManager.createPreallocatedMemoryManager(clientReadBufferPreallocationsize, clientReadBufferMinsize, clientReadBufferUseDirect);
		} else {
			sslMemoryManagerClient = IoSynchronizedMemoryManager.createNonPreallocatedMemoryManager(clientReadBufferUseDirect);
		}

	}
	
	
	static int getTransferByteBufferMaxSize() {
	    return transferByteBufferMaxSize;
	}
	
	
	static String[] getSSLEngineServerEnabledCipherSuites() {
		return sSLEngineServerEnabledCipherSuites;
	}
	
	static String[] getSSLEngineClientEnabledCipherSuites() {
		return sSLEngineClientEnabledCipherSuites;
	}
	
	static String[] getSSLEngineClientEnabledProtocols() {
		return sSLEngineClientEnabledProtocols;
	}

	static String[] getSSLEngineServerEnabledProtocols() {
		return sSLEngineServerEnabledProtocols;
	}

	
	static Boolean getSSLEngineServerWantClientAuth() {
		return sSLEngineWantClientAuth;
	}
	
	
	static Boolean getSSLEngineServerNeedClientAuth() {
		return sSLEngineNeedClientAuth;
	}

	
	
	static Integer getDefaultClientMaxReadbufferSize() {
		return defaultClientMaxReadbufferSize;
	}
	
	static Integer getDefaultServerMaxReadbufferSize() {
		return defaultServerMaxReadbufferSize;
	}
	
	
	static Integer getDefaultClientMaxWritebufferSize() {
		return defaultClientMaxWritebufferSize;
	}
	
	static Integer getDefaultServerMaxWritebufferSize() {
		return defaultServerMaxWritebufferSize;
	}
	
	static boolean getSuppressSyncFlushWarning() {
	    return suppressSyncFlushWarning;
	}
	
	static boolean getSuppressSyncFlushCompletionHandlerWarning() {
        return suppressSyncFlushCompletionHandlerWarning;
    }
	

	static boolean getSuppressReuseBufferWarning() {
	    return suppressReuseBufferWarning;
	}
	
	/**
	 * 服务器端创建IoSocketDispatcher的数目.
	 */
	static int getServerDispatcherInitialSize() {
		if (countServerDispatcher == null) {
			return getDispatcherInitialSize();
		} else {
			return countServerDispatcher;
		}
	}

	/**
	 * 客户端端创建IoSocketDispatcher的数目.
	 */
	static int getClientDispatcherInitialSize() {
		if (countClientDispatcher == null) {
			return  getDispatcherInitialSize();
		} else {
			return countClientDispatcher;
		}
	}

	
	/**
	 * 创建IoSocketDispatcher的数目.
	 */
	private static int getDispatcherInitialSize() {
		
	    if (countDispatcher == null) {
		    return 2;
		} else {
			return countDispatcher;
		}
	}

	
    static Integer getMaxHandles() {
    	return maxHandles;
    }
    
    static boolean getDetachHandleOnNoOps() {
    	return detachHandleOnNoOps;
    }
    
    
    static boolean isBypassingWriteAllowed() {
    	return bypassingWriteAllowed;
    }

	/**
	 * Return the version of this implementation. It consists of any string assigned
	 * by the vendor of this implementation and does not have any particular syntax
	 * specified or expected by the Java runtime. It may be compared for equality
	 * with other package version strings used for this implementation
	 * by this vendor for this package.
	 *
	 * @return the version of the implementation
	 */
	public String getImplementationVersion() {
		return "";
	}


	/**
	 * FIXME 此构造函数和下面的带有ssl参数的构造函数可以进行重构	</br></br>
	 * 
	 * @param callback
	 * 		  {@link Server.LifeCycleHandler}实现
	 * 
	 * @param address
	 * @param backlog
	 * @param options	
	 * 		  Socket选项
	 * 
	 * @return
	 * @throws IOException
	 */
	public IoAcceptor createAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, Map<String, Object> options) throws IOException {
		// 地址是否可重用, 默认为true
        Boolean isReuseAddress = (Boolean) options.get(IConnection.SO_REUSEADDR);
        if (isReuseAddress == null) {
        	// 默认可以
            isReuseAddress = IS_REUSE_ADDRESS;
        }
        	        
        // XXX 创建IoAcceptor => 创建ServerSocketChannel,阻塞模式
        // XXX 创建IoSocketDispatcher线程,并启动
        IoAcceptor acceptor = new IoAcceptor(callback, address, backlog, isReuseAddress);
        // 设置属性选项
        // 设置Socket选项
		for (Entry<String, Object> entry : options.entrySet()) {
			acceptor.setOption(entry.getKey(), entry.getValue());
		}
		
		acceptor.setReceiveBufferIsDirect(serverReadBufferUseDirect);
		acceptor.setReceiveBufferPreallocationMode(serverReadBufferPreallocationOn);
		acceptor.setReceiveBufferPreallocatedMinSize(serverReadBufferMinsize);
		acceptor.setReceiveBufferPreallocationSize(serverReadBufferPreallocationsize);
		
		return acceptor;
	}


	/**
	 * {@inheritDoc}
	 */
	public IoAcceptor createAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, Map<String, Object> options, SSLContext sslContext, boolean sslOn) throws IOException {
		// FIXME : 这个属性应该是搞错了,这样会导致属性设置了却无法起作用的问题
//		Boolean isReuseAddress = (Boolean) options.get("SO_REUSEADDR");
		Boolean isReuseAddress = (Boolean) options.get(IConnection.SO_REUSEADDR);
	    if (isReuseAddress == null) {
	        isReuseAddress = IS_REUSE_ADDRESS;
	    }
	    
		IoAcceptor acceptor = new IoAcceptor(callback, address, backlog, sslContext, sslOn, isReuseAddress);
		for (Entry<String, Object> entry : options.entrySet()) {
			acceptor.setOption(entry.getKey(), entry.getValue());
		}

		acceptor.setReceiveBufferIsDirect(serverReadBufferUseDirect);
		acceptor.setReceiveBufferPreallocationMode(serverReadBufferPreallocationOn);
		acceptor.setReceiveBufferPreallocatedMinSize(serverReadBufferMinsize);
		acceptor.setReceiveBufferPreallocationSize(serverReadBufferPreallocationsize);
		
		return acceptor;
	}


    /**
	 * {@inheritDoc}
	 */
	public IoChainableHandler createClientIoHandler(SocketChannel channel) throws IOException {
		return createIoHandler(true, getGlobalClientDisptacherPool().nextDispatcher(), channel, null, false);
	}

    /**
	 * {@inheritDoc}
	 */
    public IoChainableHandler createSSLClientIoHandler(SocketChannel channel, SSLContext sslContext, boolean sslOn) throws IOException {
    	return createIoHandler(true, getGlobalClientDisptacherPool().nextDispatcher(), channel, sslContext, sslOn);
    }

    /**
     * 服务器端和客户端都会调用.	</br>
     * 
     * SocketChannel设置为非阻塞.		</br>
     * 
     * {@link IoAcceptor#accept()}
	 */
    IoChainableHandler createIoHandler(boolean isClient, IoSocketDispatcher dispatcher, SocketChannel channel, SSLContext sslContext, boolean sslOn) throws IOException {
    	String connectionId = null;

    	// 客户端还是服务器端
    	if (isClient) {
    		connectionId = idPrefix + "C" + Integer.toHexString(nextId.incrementAndGet());
    	} else {
    		connectionId = idPrefix + "S" + Integer.toHexString(nextId.incrementAndGet());
    	}

    	// SocketChannel, 非阻塞
    	// 这段操作放到 if(sslContext != null)的else块里更好点.
		IoChainableHandler ioHandler = new IoSocketHandler(channel, dispatcher, connectionId);

		// ssl connection?
		if (sslContext != null) {

			AbstractMemoryManager mm = null;
			if (isClient) {
				mm = sslMemoryManagerClient;
			} else {
				mm = sslMemoryManagerServer;
			}

			if (sslOn) {
				ioHandler = new IoSSLHandler(ioHandler, sslContext, isClient, mm);
			} else {
				ioHandler = new IoActivateableSSLHandler(ioHandler, sslContext, isClient, mm);
			}
		}

		return ioHandler;
	}


    /**
     * {@inheritDoc}
     */
    public IoChainableHandler setWriteTransferRate(IoChainableHandler ioHandler, int bytesPerSecond) throws IOException {

    	// unlimited? remove throttling handler if exists
    	if (bytesPerSecond == UNLIMITED) {
    		IoThrottledWriteHandler delayWriter = (IoThrottledWriteHandler) getHandler((IoChainableHandler) ioHandler, IoThrottledWriteHandler.class);
    		if (delayWriter != null) {
    			
    			if (LOG.isLoggable(Level.FINE)) {
    				LOG.fine("write transfer rate is set to unlimited. flushing throttle write handler");
    			}
    			
    			delayWriter.hardFlush();
    			IoChainableHandler successor = delayWriter.getSuccessor();
    			return successor;
    		} else {
    			return ioHandler;
    		}

       	// ...no -> add throttling handler if not exists and set rate
    	} else {
			IoThrottledWriteHandler delayWriter = (IoThrottledWriteHandler) getHandler((IoChainableHandler) ioHandler, IoThrottledWriteHandler.class);
			if (delayWriter == null) {
				delayWriter = new IoThrottledWriteHandler((IoChainableHandler) ioHandler);
			}

			delayWriter.setWriteRateSec(bytesPerSecond);
			return delayWriter;
    	}
	}

    public boolean isSecuredModeActivateable(IoChainableHandler  ioHandler) {
    	IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler(ioHandler, IoActivateableSSLHandler.class);
		if (activateableHandler != null) {
			return true;
		} else {
			return false;
		}	
    }
  

	public boolean preActivateSecuredMode(IoChainableHandler ioHandler) throws IOException {
		IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler(ioHandler, IoActivateableSSLHandler.class);
		if (activateableHandler != null) {
			return activateableHandler.preActivateSecuredMode();
		} else {
			throw new IOException("connection is not SSL activatable (non IoActivateableHandler in chain)");
		}
	}

	public void activateSecuredMode(IoChainableHandler ioHandler, ByteBuffer[] buffers) throws IOException {
		ioHandler.hardFlush();

		IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler((IoChainableHandler) ioHandler, IoActivateableSSLHandler.class);
		if (activateableHandler != null) {
			activateableHandler.activateSecuredMode(buffers);
		} else {
			LOG.warning("connection is not SSL activatable (non IoActivateableHandler in chain");
		}
	}

	public void deactivateSecuredMode(IoChainableHandler ioHandler) throws IOException {
		ioHandler.hardFlush();

		IoActivateableSSLHandler activateableHandler = (IoActivateableSSLHandler) getHandler((IoChainableHandler) ioHandler, IoActivateableSSLHandler.class);
		if (activateableHandler != null) {
			activateableHandler.deactivateSecuredMode();
		} else {
			LOG.warning("connection is not SSL (de)activatable (non IoActivateableHandler in chain");
		}
	}


	static Timer getTimer() {
		return TIMER;
	}

	
	static boolean isUseDirectWriteBuffer() {
		return writeBufferUseDirect;
	}


	static int getReadBufferPreallocationsizeServer() {
		return serverReadBufferPreallocationsize;
	}

	static int getReadBufferMinSizeServer() {
		return serverReadBufferMinsize;
	}


	static boolean isReadBufferPreallocationActivated() {
		return serverReadBufferPreallocationOn;
	}

	


    /**
     * 设置Socket选项	</br>
     * 
     * set a option
     *
     * @param socket    the socket
     * @param name      the option name
     * @param value     the option value
     * @throws IOException if an exception occurs
     */
	static void setOption(Socket socket, String name, Object value) throws IOException {
		if (name.equals(SO_SNDBUF)) {
			socket.setSendBufferSize(asInt(value));
		} else if (name.equals(SO_REUSEADDR)) {
			socket.setReuseAddress(asBoolean(value));
		} else if (name.equals(SO_TIMEOUT)) {
			socket.setSoTimeout(asInt(value));
		} else if (name.equals(SO_RCVBUF)) {
			socket.setReceiveBufferSize(asInt(value));
		} else if (name.equals(SO_KEEPALIVE)) {
			socket.setKeepAlive(asBoolean(value));
		} else if (name.equals(SO_LINGER)) {
			try {
				socket.setSoLinger(true, asInt(value));
			} catch (ClassCastException cce) {
				socket.setSoLinger(Boolean.FALSE, 0);
			}
		} else if (name.equals(TCP_NODELAY)) {
			socket.setTcpNoDelay(asBoolean(value));
		} else {
			LOG.warning("option " + name + " is not supported");
		}
	}


	/**
	 * get a option
	 *
	 * @param socket    the socket
	 * @param name      the option name
	 * @return the option value
     * @throws IOException if an exception occurs
	 */
	static Object getOption(Socket socket, String name) throws IOException {

		if (name.equals(SO_SNDBUF)) {
			return socket.getSendBufferSize();

		} else if (name.equals(SO_REUSEADDR)) {
			return socket.getReuseAddress();

		} else if (name.equals(SO_RCVBUF)) {
			return socket.getReceiveBufferSize();

		} else if (name.equals(SO_KEEPALIVE)) {
			return socket.getKeepAlive();

		} else if (name.equals(SO_TIMEOUT)) {
			return socket.getSoTimeout();

		} else if (name.equals(TCP_NODELAY)) {
			return socket.getTcpNoDelay();

		} else if (name.equals(SO_LINGER)) {
			return socket.getSoLinger();


		} else {
			LOG.warning("option " + name + " is not supported");
			return null;
		}
	}



	private static int asInt(Object obj) {
		if (obj instanceof Integer) {
			return (Integer) obj;
		}

		return Integer.parseInt(obj.toString());
	}

	private static boolean asBoolean(Object obj) {
		if (obj instanceof Boolean) {
			return (Boolean) obj;
		}

		return Boolean.parseBoolean(obj.toString());
	}



	@SuppressWarnings("unchecked")
	private IoChainableHandler getHandler(IoChainableHandler head, Class clazz) {
		IoChainableHandler handler = head;
		do {
			if (handler.getClass() == clazz) {
				return handler;
			}

			handler = handler.getSuccessor();
		} while (handler != null);

		return null;
	}

	/**
	 * {@link #createClientIoHandler(SocketChannel)}
	 */
	static synchronized IoSocketDispatcherPool getGlobalClientDisptacherPool() {
		if (globalClientDispatcherPool == null) {
			globalClientDispatcherPool = new IoSocketDispatcherPool("ClientGlb", getClientDispatcherInitialSize());
			
			globalClientDispatcherPool.setReceiveBufferIsDirect(clientReadBufferUseDirect);
			globalClientDispatcherPool.setReceiveBufferPreallocationMode(clientReadBufferPreallocationOn);
			globalClientDispatcherPool.setReceiveBufferPreallocatedMinSize(clientReadBufferMinsize);
			globalClientDispatcherPool.setReceiveBufferPreallocationSize(clientReadBufferPreallocationsize);
		}
		
		return globalClientDispatcherPool;
	}
	
	
	
	private static Integer readIntProperty(String key) { 
		try {
			String property = readProperty(key);
			if (property != null) {
				return Integer.parseInt(property);
			} else {
				return null;
			}
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + ": " + 
				    System.getProperty(key) + " " + e.toString());
			return null;
		}
	}

	private static Integer readIntProperty(String key, Integer dflt) { 
		try {
			String property = readProperty(key);
			if (property != null) {
				return Integer.parseInt(property);
			} else {
				return dflt;
			}
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + ": "
					+ System.getProperty(key) + " (valid is int)"
					+ " using " + dflt);
			return null;
		}
	}
	
	
	private static Boolean readBooleanProperty(String key, String dflt) { 
		try {
			String property = readProperty(key);
			if (property != null) {
				return Boolean.parseBoolean(property);
			} else {
				if (dflt == null) {
					return null;
				} else {
					return Boolean.parseBoolean(dflt);
				}
			}
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + ": "
					+ System.getProperty(key) + " (valid is true|false)"
					+ " using " + dflt);
			if (dflt != null) {
				return Boolean.parseBoolean(dflt);
			} else {
				return null;
			}
		}
	}
	
	
	private static String[] readStringArrayProperty(String key, String[] dflt) { 
		String property = readProperty(key);
		if (property != null) {
			String[] props = property.split(",");
			String[] result = new String[props.length];
			for (int i = 0; i < props.length; i++) {
				result[i] = props[i].trim(); 
			}
			return result;
		} else {
			return dflt;
		}
	}

	
	private static String readProperty(String key) { 
		try {
			return System.getProperty(key);
		} catch (Exception e) {
			LOG.warning("invalid value for system property " + key + " " + e.toString());
			return null;
		}
	}	
}