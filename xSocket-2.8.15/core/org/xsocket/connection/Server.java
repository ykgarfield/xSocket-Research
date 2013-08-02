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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;


import org.xsocket.DataConverter;
import org.xsocket.WorkerPool;
import org.xsocket.connection.IConnection.FlushMode;

/**
 * 服务器的实现.
 * IServer接口继承了Runnable接口.所以此类实现了run()方法,作为一个线程启动.	</br></br>
 * 
 * Implementation of a server. For more information see
 * {@link IServer}
 *
 * @author grro@xsocket.org
 */
public class Server implements IServer {

	private static final Logger LOG = Logger.getLogger(Server.class.getName());

	// xSocket的实现版本和时间
	private static String implementationVersion = ConnectionUtils.getImplementationVersion();
	private static String implementationDate;

	/** 可设置系统属性  */
	/** 工作者池大小, 默认100 */
	protected static final int SIZE_WORKER_POOL = Integer.parseInt(System.getProperty("org.xsocket.connection.server.workerpoolSize", "100"));
	/** 工作者池最小数目, 默认2 */
    protected static final int MIN_SIZE_WORKER_POOL = Integer.parseInt(System.getProperty("org.xsocket.connection.server.workerpoolMinSize", "2"));
    /** 任务队列大小,默认100 */
    protected static final int TASK_QUEUE_SIZE = Integer.parseInt(System.getProperty("org.xsocket.connection.server.taskqueuesize", Integer.toString(SIZE_WORKER_POOL)));
    /** 超过最大连接的等待时间,默认500毫秒 */
    private static final int WAITTIME_MAXCONNECTION_EXCEEDED = Integer.parseInt(System.getProperty("org.xsocket.connection.server.waittimeMaxConnectionExceeded", Integer.toString(500)));

    // 刷新模式
	private FlushMode flushMode = IConnection.DEFAULT_FLUSH_MODE;
	// 是否自动刷新
	private boolean autoflush = IConnection.DEFAULT_AUTOFLUSH;
	private Integer writeRate;
	

	// is open flag
	/** {@link Server.LifeCycleHandler#onConnected()} 将设置为true */
	private final AtomicBoolean isOpen = new AtomicBoolean(false);

	// name
	private String name = "server";
	
	// acceptor
	private IoAcceptor acceptor;
	
	// workerpool
	// 构造函数中设置
	private ExecutorService defaultWorkerPool;
	private Executor workerpool;

	
	//connection manager
	// XXX 创建Watchdog任务
	/** {@link ConnectionManager.WachdogTask}*/
	private ConnectionManager connectionManager = new ConnectionManager();
	// 最大并发连接数
	private int maxConcurrentConnections = Integer.MAX_VALUE;
	private boolean isMaxConnectionCheckAvtive = false;

	// handler replace Listener
	// 处理器替换
    private final AtomicReference<IHandlerChangeListener> handlerReplaceListenerRef = new AtomicReference<IHandlerChangeListener>();
	
	// app handler
    // 在构造函数中调用setHandler()方法被处理
	private HandlerAdapter handlerAdapter = HandlerAdapter.newInstance(null);
	
	// thresholds
	// 最大的读缓冲区
	private Integer maxReadBufferThreshold = null;
	
	// timeouts
	// 空闲超时时间, 默认为long类型的最大值
	private long idleTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;
	// 这里的连接超时指的不是等待客户端连接的超时时间.
	// 也就是指的不是设置的ServerSocket.setSoTimeOut()的超时时间.
	// xSocket的连接超时时间为0,也就是永远不会超时.一直等待连接.
	// FIXME：此参数名称有点歧义
	private long connectionTimeoutMillis = IConnection.MAX_TIMEOUT_MILLIS;

	// server listeners
	// 服务器监听,可自定义实现并设置到Server类中
	/** {@link Server#addListener(IServerListener)} */
	/** {@link Server.LifeCycleHandler#onConnected()} 中执行*/
	private final ArrayList<IServerListener> listeners = new ArrayList<IServerListener>();

	// local address
	private String localHostname = "";
	private int localPort = -1;
	
	// start up message
	// 启动消息
	private String startUpLogMessage = "xSocket " + implementationVersion;
	
	// startup date
	// 启动时间
	private long startUpTime = 0;


	/**
	 * 端口0.		</br></br>
	 * 
	 * constructor <br><br>
	 *
	 * @param handler the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the local host cannot determined
	 */
	public Server(IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(0), new HashMap<String, Object>(), handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws IOException If some other I/O error occurs
	 * @throws UnknownHostException if the local host cannot determined
	 */
	public Server(Map<String, Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(0), options, handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor  <br><br>
	 *
	 *
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}

	
	/**
     * constructor  <br><br>
     *
     *
     * @param port         the local port
     * @param handler      the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param minPoolsize  the min workerpool size
     * @param maxPoolsize  the max workerpool size
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    public Server(int port, IHandler handler, int minPoolsize, int maxPoolsize) throws UnknownHostException, IOException {
        this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, null, false, 0, minPoolsize, maxPoolsize, maxPoolsize);
    }
	

	/**
	 * constructor  <br><br>
	 *
	 *
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param backlog     The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler, int backlog) throws UnknownHostException, IOException {
		this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, null, false, backlog, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param port                 the local port
	 * @param options              the acceptor socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, Map<String , Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(port), options, handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param address     the local address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, IHandler handler) throws UnknownHostException, IOException {
		this(address, port, new HashMap<String, Object>(), handler, null, false,0);
	}


	/**
	 * constructor  <br><br>
	 *
	 * @param ipAddress   the local ip address
	 * @param port        the local port
	 * @param handler     the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(ipAddress, port), new HashMap<String, Object>(), handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 *
	 * @param ipAddress            the local ip address
	 * @param port                 the local port
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, Map<String, Object> options, IHandler handler) throws UnknownHostException, IOException {
		this(new InetSocketAddress(ipAddress, port), options, handler, null, false, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(int port, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


    /**
     * constructor <br><br>
     *
     * @param port               local port
     * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param sslOn              true, is SSL should be activated
     * @param sslContext         the ssl context to use
     * @param minPoolsize  the min workerpool size
     * @param maxPoolsize  the max workerpool size
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    public Server(int port, IHandler handler, SSLContext sslContext, boolean sslOn, int minPoolsize, int maxPoolsize) throws UnknownHostException, IOException {
        this(new InetSocketAddress(port), new HashMap<String, Object>(), handler, sslContext, sslOn, 0, minPoolsize, maxPoolsize, maxPoolsize);
    }	
	

	/**
	 * constructor <br><br>
	 *
	 * @param port                 local port
	 * @param options              the acceptor socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(int port,Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(new InetSocketAddress(port), options, handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param ipAddress          local ip address
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(new InetSocketAddress(ipAddress, port), new HashMap<String, Object>(), handler,  sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param ipAddress            local ip address
	 * @param port                 local port
	 * @param options              the acceptor socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(String ipAddress, int port, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(new InetSocketAddress(ipAddress, port), options, handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param address            local address
	 * @param port               local port
	 * @param handler            the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn              true, is SSL should be activated
	 * @param sslContext         the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(new InetSocketAddress(address, port), new HashMap<String, Object>(), handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param address              local address
	 * @param port                 local port
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn) throws UnknownHostException, IOException {
		this(new InetSocketAddress(address, port), options, handler, sslContext, sslOn, 0, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}


	/**
	 * constructor <br><br>
	 *
	 * @param address              local address
	 * @param port                 local port
	 * @param options              the socket options
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used. 
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	public Server(InetAddress address, int port, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog) throws UnknownHostException, IOException {
		this(new InetSocketAddress(address, port), options, handler, sslContext, sslOn, backlog, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
	}

	
    /**
     * constructor <br><br>
     *
     * @param address              local address
     * @param port                 local port
     * @param options              the socket options
     * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param sslOn                true, is SSL should be activated
     * @param sslContext           the ssl context to use
     * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
     * @param minPoolsize          The min workerpool size
     * @param maxPoolsize          The max workerpool size
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    public Server(InetAddress address, int port, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog, int minPoolsize, int maxPoolsize) throws UnknownHostException, IOException {
        this(new InetSocketAddress(address, port), options, handler, sslContext, sslOn, backlog, minPoolsize, maxPoolsize, maxPoolsize);
    }
	
    
    /**
     * constructor 
     * 
     * @param address              local address
     * @param options              the socket options
     * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param sslOn                true, is SSL should be activated
     * @param sslContext           the ssl context to use
     * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    protected Server(InetSocketAddress address, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog) throws UnknownHostException, IOException {
        this(address, options, handler, sslContext, sslOn, backlog, MIN_SIZE_WORKER_POOL, SIZE_WORKER_POOL, TASK_QUEUE_SIZE);
    }
      
    
    /**
     * constructor 
     * 
     * @param address              local address
     * @param options              the socket options
     * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
     * @param sslOn                true, is SSL should be activated
     * @param sslContext           the ssl context to use
     * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
     * @param minPoolsize          The min workerpool size
     * @param maxPoolsize          The max workerpool size
     * @throws UnknownHostException if the local host cannot determined
     * @throws IOException If some other I/O error occurs
     */
    protected Server(InetSocketAddress address, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog, int minPoolsize, int maxPoolsize) throws UnknownHostException, IOException {
        this(address, options, handler, sslContext, sslOn, backlog, minPoolsize, maxPoolsize, maxPoolsize);
    }
                
        

	/**
	 * 其它的构造函数都是调用此构造函数		</br></br>
	 * 
	 * constructor 
	 * 
	 * @param address              local address
	 * 							      本地地址
	 * 
	 * @param options              the socket options
	 * 							   Socket选项
	 * 
	 * @param handler              the handler to use (supported: IConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler, IConnectionTimeoutHandler, IConnectionScoped, ILifeCycle)
	 * 							      逻辑处理器
	 * 
	 * @param sslOn                true, is SSL should be activated
	 * @param sslContext           the ssl context to use
	 * 
	 * @param backlog              The maximum number number of pending connections. If has the value 0, or a negative value, then an implementation specific default is used.
     * 							      等待连接的最大数目(0或者负使用默认值50)
     * 
     * @param minPoolsize          The min workerpool size
     * 							      工作者池最小数目(默认为2)
     * 	
     * @param maxPoolsize          The max workerpool size
     * 							      工作者池最大数目(默认100)
     * 
     * @param taskqueueSize        The taskqueue size
     * 							      任务队列(默认100)
     * 
	 * @throws UnknownHostException if the local host cannot determined
	 * @throws IOException If some other I/O error occurs
	 */
	protected Server(InetSocketAddress address, Map<String, Object> options, IHandler handler, SSLContext sslContext, boolean sslOn, int backlog, int minPoolsize, int maxPoolsize, int taskqueueSize) throws UnknownHostException, IOException {
		// 工作队列、线程工厂、创建线程执行者
	    defaultWorkerPool = new WorkerPool(minPoolsize, maxPoolsize, taskqueueSize);
	    workerpool = defaultWorkerPool;

	    // 1. IoProvider实例化
		// 2. LifeCycleHandler实例化
		// 3. IoProvider的createAcceptor()方法
		// XXX 创建IoAcceptor(创建ServerSocketChannel, 阻塞模式)
	    // XXX 创建IoSocketDispatcher线程,并启动(处理读/写事件,作为后台线程等待读/写操作)
		if (sslContext != null) {
			acceptor = ConnectionUtils.getIoProvider().createAcceptor(new LifeCycleHandler(), address, backlog, options, sslContext, sslOn);
		} else {
			acceptor = ConnectionUtils.getIoProvider().createAcceptor(new LifeCycleHandler(), address, backlog, options);
		}
			
		// 主机、端口
		localHostname = acceptor.getLocalAddress().getHostName();
		localPort = acceptor.getLocalPort();
		
		// 设置处理器 
		setHandler(handler);
	}


	/**
	 * 设置逻辑处理器,构造方法中调用.	</br>
	 * 可以在Server启动后再次设置IHandler.这将覆盖在构造函数中设置的IHandler. </br></br>
	 * 
	 * set the handler 
	 * @param handler the handler
	 */
    public void setHandler(IHandler handler) {
        // 默认false
        // isOpen默认为false,在实例化Server的时候也不会改变这个值
    	/* {@link Server.LifeCycleHandler#onConnected} 将设置isOpen为true 
    	 * 也就是在执行了Server.start()之后isOpen()值才会设置为true.
    	 * */
		if (isOpen.get()) {
			// 可以在执行了Server.start()之后重新设置IHander的实现
			callCurrentHandlerOnDestroy();
		}
		
		/**
		 * FIXME：这段代码貌似无用,IHandlerChangeListener接口连实现类都没有.
		 * 		    这是要让使用自己实现? 就算实现了,如果设置?
		 * 		  handlerReplaceListenerRef已经被声明为final,只能是xSocket内部自己处理.
		 * 		    无解!!!
		 */
		IHandlerChangeListener changeListener = handlerReplaceListenerRef.get();
		if (changeListener != null) {
		    IHandler oldHandler = handlerAdapter.getHandler();
		    changeListener.onHanderReplaced(oldHandler, handler);
		}
		
		handlerAdapter = HandlerAdapter.newInstance(handler);

        // init app handler
        initCurrentHandler();
	}
    
	// 初始化当前的IHandler
	private void initCurrentHandler() {
		ConnectionUtils.injectServerField(this, handlerAdapter.getHandler());
		// 如果实现而来ILifeCycle接口,那么将调用其onInit()方法. 一般不实现此接口
		handlerAdapter.onInit();
	}
	
	
	private void callCurrentHandlerOnDestroy() {
		handlerAdapter.onDestroy();
	}
	

	/**
	 * the the server name. The server name will be used to print out the start log message.<br>
	 *
	 * E.g.
	 * <pre>
	 *   IServer cacheServer = new Server(port, new CacheHandler());
	 *   ConnectionUtils.start(server);
	 *   server.setServerName("CacheServer");
	 *
	 *
	 *   // prints out
	 *   // 01::52::42,756 10 INFO [Server$AcceptorCallback#onConnected] CacheServer listening on 172.25.34.33/172.25.34.33:9921 (xSocket 2.0)
     * </pre>
	 *
	 * @param name the server name
	 */
	public final void setServerName(String name) {
		this.name = name;
	}


	/**
	 * return the server name
	 *
	 * @return the server name
	 */
	public final String getServerName() {
		return name;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public String getStartUpLogMessage() {
		return startUpLogMessage;
	}
	
	/**
	 * {@inheritDoc}
	 */	
	public void setStartUpLogMessage(String message) {
		this.startUpLogMessage = message;
	}

	
	/**
	 * {@link ConnectionUtils#start(IServer, int)}	</br>
	 * 
	 * 会在start()方法中会创建Server线程,此方法将被调用	</br></br>
	 * 
	 */
	@Override
	public void run() {

		try {
			if (getHandler() == null) {
				LOG.warning("no handler has been set. Call setHandler-method to assign a handler");
			}
			
			// 启动时间
			startUpTime = System.currentTimeMillis();

			ShutdownHookHandler shutdownHookHandler = new ShutdownHookHandler(this);
			try {
				// register shutdown hook handler
				shutdownHookHandler.register();
				
				// listening in a blocking way  
				// XXX 阻塞方法监听, 无限循环接收客户端连接请求, 并注册OP_READ事件
				acceptor.listen();
			} finally {
				// deregister shutdown hook handler
				shutdownHookHandler.deregister();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	
	private static final class ShutdownHookHandler extends Thread {
	
		private Runtime runtime;
		private Server server;
		
		public ShutdownHookHandler(Server server) {
			this.server = server;
		}
		
		void register() {
			runtime = Runtime.getRuntime();
			runtime.addShutdownHook(this);
		}

		
		public void run() {
		    if (server != null) {
		        server.close();
		    }
		}
		
		
		void deregister() {
			if (runtime != null) {
				try {
					runtime.removeShutdownHook(this);
				} catch (Exception e) {
					// eat and log exception
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("error occured by derigistering shutdwon hook " + e.toString());
					}
					
				}
				runtime = null;
				server = null;
			}
		}
	}
	
	
	
	/**
	 * 启动服务	</br></br>
	 * 
	 * starts the given server within a dedicated thread. This method blocks 
	 * until the server is open. This method is equals to {@link ConnectionUtils#start(IServer)} 
	 * 
	 * @throws SocketTimeoutException is the timeout has been reached 
	 */
	@Override
	public void start() throws IOException {
		ConnectionUtils.start(this);
	}


	/**
	 * {@inheritDoc}
	 */
	public final Object getOption(String name) throws IOException {
		return acceptor.getOption(name);
	}


	public IHandler getHandler() {
		return handlerAdapter.getHandler();
	}


	/**
	 */
	@SuppressWarnings("rawtypes")
	public final Map<String, Class> getOptions() {
		return acceptor.getOptions();
	}



	/**
	 * {@inheritDoc}
	 */
	public final void close() {

		// is open?
		if (isOpen.getAndSet(false)) {
			// close connection manager
			try {
			    connectionManager.close();
			} catch (Throwable e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("error occured by closing acceptor " + DataConverter.toString(e));
                }
            }
			connectionManager = null;

			
			// closing acceptor
			try {
				acceptor.close();  // closing of dispatcher will be initiated by acceptor
			} catch (Throwable e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by closing acceptor " + DataConverter.toString(e));
				}
			}
			acceptor = null;
			
			
			// notify listeners 
			try {
				onClosed();
			} catch (Throwable e) { 
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by performing onClosed method " + DataConverter.toString(e));
				}
			}
			

			// unset references 
			handlerAdapter = null;
		}
	}

	
	protected void onClosed() throws IOException {
		
	}

	protected void onPreRejectConnection(NonBlockingConnection connection) throws IOException {
	    
	}



	final IoAcceptor getAcceptor() {
		return acceptor;
	}
	

	
	final long getConnectionManagerWatchDogPeriodMillis() {
		return connectionManager.getWatchDogPeriodMillis();
	}
	
	final int getConnectionManagerWatchDogRuns() {
		return connectionManager.getWatchDogRuns();
	}
	


	/**
	 * {@inheritDoc}
	 */
	public final void addListener(IServerListener listener) {
		listeners.add(listener);
	}



	/**
	 * {@inheritDoc}
	 */
	public final boolean removeListener(IServerListener listener) {
		boolean result = listeners.remove(listener);
		return result;
	}




	/**
	 * {@inheritDoc}
	 */
	public final Executor getWorkerpool() {
		return workerpool;
	}


	/**
	 * {@inheritDoc}
	 */
	public final void setWorkerpool(Executor executor) {
		if (executor == null) {
			throw new NullPointerException("executor has to be set");
		}
		
		if (isOpen.get()) {
			LOG.warning("server is already running");
		}
		
		workerpool = executor;
		
		if (defaultWorkerPool != null) {
			defaultWorkerPool.shutdown();
			defaultWorkerPool = null;
		}
	}




	/**
	 * {@inheritDoc}
	 */
	public final boolean isOpen() {
		return isOpen.get();
	}



	/**
	 * sets the max number of concurrent connections
	 * 
	 * @param maxConcurrentConnections the max number of concurrent connections
	 */
	public final void setMaxConcurrentConnections(int maxConcurrentConnections) {
		this.maxConcurrentConnections = maxConcurrentConnections;
		
		if (maxConcurrentConnections == Integer.MAX_VALUE) {
		    isMaxConnectionCheckAvtive = false;
		} else {
		    isMaxConnectionCheckAvtive = true;
		}
	}
	
	
	/**
     * set the max app read buffer threshold
     * 
     * @param maxSize the max read buffer threshold
     */
    public void setMaxReadBufferThreshold(int maxSize) {
        this.maxReadBufferThreshold = maxSize;
    }
    
	
	/**
	 * returns the number of max concurrent connections 
	 * @return the number of max concurrent connections 
	 */
	int getMaxConcurrentConnections() {
		return maxConcurrentConnections;
	}
	

	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return acceptor.getLocalPort();
	}


	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return acceptor.getLocalAddress();
	}

	final int getNumberOfOpenConnections() {
		return connectionManager.getSize();
	}
	
    final int getDispatcherPoolSize() {
        return acceptor.getDispatcherSize();
    }
    
    final void setDispatcherPoolSize(int size) {
        acceptor.setDispatcherSize(size);
    }
    
    
    final boolean getReceiveBufferIsDirect() {
        return acceptor.getReceiveBufferIsDirect();
    }
    
    final void setReceiveBufferIsDirect(boolean isDirect) {
        acceptor.setReceiveBufferIsDirect(isDirect);
    }
    
    
    final Integer getReceiveBufferPreallocatedMinSize() {
        if (acceptor.isReceiveBufferPreallocationMode()) {
            return acceptor.getReceiveBufferPreallocatedMinSize();
        } else {
            return null;
        } 
    }
	
	
	public Set<INonBlockingConnection> getOpenConnections() {
		HashSet<INonBlockingConnection> cons = new HashSet<INonBlockingConnection>();

		if (connectionManager != null) {
    		for (INonBlockingConnection con: connectionManager.getConnections()) {
    			if (con.isOpen()) {
    				cons.add(con);
    			}
    		}
		}
		
		return cons;
	}
	
	final List<String> getOpenConnectionInfos() {
		List<String> infos = new ArrayList<String>();
		for (NonBlockingConnection con : connectionManager.getConnections()) {
			infos.add(con.toDetailedString());
		}
		
		return infos;
	}
	
	
	
	final int getNumberOfIdleTimeouts() {
		return connectionManager.getNumberOfIdleTimeouts();
	}
	
	final int getNumberOfConnectionTimeouts() {
		return connectionManager.getNumberOfConnectionTimeouts();
	}
	

	final Date getStartUPDate() {
	    return new Date(startUpTime); 
	}
	
	
	

	/**
	 * {@inheritDoc}
	 */
	public final FlushMode getFlushmode() {
		return flushMode;
	}
	
	

	/**
	 * {@inheritDoc}
	 */
	public final void setFlushmode(FlushMode flusmode) {
		this.flushMode = flusmode;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public final void setAutoflush(boolean autoflush) {
		this.autoflush = autoflush;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public final boolean getAutoflush() {
		return autoflush;
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public final void setConnectionTimeoutMillis(long timeoutMillis) {
		this.connectionTimeoutMillis = timeoutMillis;
	}

	
	/**
	 * {@inheritDoc}
	 */	
	public void setWriteTransferRate(int bytesPerSecond) throws IOException {
		
		if ((bytesPerSecond != INonBlockingConnection.UNLIMITED) && (flushMode != FlushMode.ASYNC)) {
			LOG.warning("setWriteTransferRate is only supported for FlushMode ASYNC. Ignore update of the transfer rate");
			return;
		}
		
		this.writeRate = bytesPerSecond;
	}
	
	

//	public void setReadTransferRate(int bytesPerSecond) throws IOException {
//		this.readRate = bytesPerSecond;
//	}

	
	/**
	 * {@inheritDoc}
	 */
	public void setIdleTimeoutMillis(long timeoutMillis) {
		this.idleTimeoutMillis = timeoutMillis;
	}



	/**
	 * {@inheritDoc}
	 */
	public final long getConnectionTimeoutMillis() {
		return connectionTimeoutMillis;
	}


	/**
	 * {@inheritDoc}
	 */
	public final long getIdleTimeoutMillis() {
		return idleTimeoutMillis;
	}

	

	
	/**
	 * returns the implementation version
	 *  
	 * @return the implementation version
	 */
	public String getImplementationVersion() {
		return implementationVersion;
	}
	
	
	
	/**
	 * returns the implementation date
	 * 
	 * @return  the implementation date 
	 */
	public String getImplementationDate() {
		if (implementationDate == null) {
			implementationDate = ConnectionUtils.getImplementationDate();
		}

		return implementationDate;
	}
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getServerName() + " on " + getLocalAddress().toString() + " Port " + getLocalPort());
		sb.append("\r\nopen connections:");
		for (NonBlockingConnection con : connectionManager.getConnections()) {
			sb.append("\r\n  " + con.toString());
		}
		
		return sb.toString();
	}
	

	/**
	 * 客户端有连接的回调
	 */
	private final class LifeCycleHandler implements IIoAcceptorCallback {

		/**
		 * {@link IoAcceptor#listen()}		</br>
		 * 在接收请求之前被调用
		 */
        @SuppressWarnings("unchecked")
        @Override
		public void onConnected() {
        	// 服务已启动
			isOpen.set(true);
			
	        // notify listeners
			// 通知监听器
			// 如果不自定义IServerListener,那么就只有一个IServerListener的实现:
			// ConnectionUtils.start(IServer, int)方法中实现了startupListener
	        for (IServerListener listener : (ArrayList<IServerListener>) listeners.clone()) {
	            listener.onInit();
	        }
	        
	        // 打印启动日志
			// print out the startUp log message
			if (acceptor.isSSLSupported()) {
				if (acceptor.isSSLOn()) {
					LOG.info(name + " listening on " + localHostname + ":" + localPort + " - SSL (" + startUpLogMessage + ")");
				} else {
					LOG.info(name + " listening on " + localHostname + ":" + localPort + " - activatable SSL (" + startUpLogMessage + ")");
				}
			} else {
				LOG.info(name + " listening on " + localHostname + ":" + localPort + " (" + startUpLogMessage + ")");
			}
		}


		@SuppressWarnings("unchecked")
		public void onDisconnected() {
			
			// perform handler callback 
			callCurrentHandlerOnDestroy();

		
			// calling server listener 
			for (IServerListener listener : (ArrayList<IServerListener>)listeners.clone()) {
				try {
					listener.onDestroy();
				} catch (IOException ioe) {
					if (LOG.isLoggable(Level.FINE)) {
						LOG.fine("exception occured by destroying " + listener + " " + ioe.toString());
					}
				}
			}
			listeners.clear();

			
			// close default worker pool if exists
			if (defaultWorkerPool != null) {
				WorkerpoolCloser workerpoolCloser = new WorkerpoolCloser(Server.this);
				workerpoolCloser.start();
			}

			
			// unset workerpool reference 
			workerpool = null;			
			
			// print log message
			LOG.info("server (" + localHostname + ":" + localPort + ") has been shutdown");
		}

		
		/**
		 * {@link IoAcceptor#accept()}, 接收到客户端的请求后,会调用此方法	</br>
		 * OP_READ事件的注册：{@link IoSocketHandler#init(IIoHandlerCallback)}
		 * 
		 * @param ioHandler 没有SSL：IoSocketHandler
		 */
		@Override
		public void onConnectionAccepted(IoChainableHandler ioHandler) throws IOException {
		    
	        // if max connection size reached  -> reject connection & sleep
			// 超过了连接最大数
		    if (isMacConnectionSizeExceeded()) {
		        
		    	// 创建连接
		        // create connection
	            NonBlockingConnection connection = new NonBlockingConnection(connectionManager, HandlerAdapter.newInstance(null));
	            // XXX 注册OP_READ事件
	            /** {@link IoSocketHandler#init(IIoHandlerCallback)} */
	            init(connection, ioHandler);
	            
	            // first call pre reject 
	            try {
	            	// 空的实现
	                onPreRejectConnection(connection);
	            } catch (IOException e) {
	                if (LOG.isLoggable(Level.FINE)) {
	                    LOG.fine("[" + connection.getId() + "] error occured by calling onPreRejectConnection " + e.toString());
	                }
	            }
	            
	            // ... and than reject it
	            if (LOG.isLoggable(Level.FINE)) {
	                LOG.fine("[" + connection.getId() + "] rejecting connection. Max concurrent connection size (" + maxConcurrentConnections + ") exceeded");
	            }
	            connection.closeQuietly();
	            

	            // 无限等待,直到最大的连接数超过
                // wait while max connection size is exceeded
	            do {
    	            try {
    	                Thread.sleep(WAITTIME_MAXCONNECTION_EXCEEDED);
    	            } catch (InterruptedException ie) {
    	                // Restore the interrupted status
    	                Thread.currentThread().interrupt();
    	            }
	            } while (isMacConnectionSizeExceeded());
	            

	        // .. not exceeded
	        } else {
	        	// 没有超过最大连接.创建一个新的connection
	            // create a new connection 
	        	// handlerAdapter在构造函数中调用setHandler()方法经过处理
	            NonBlockingConnection connection = new NonBlockingConnection(connectionManager, handlerAdapter.getConnectionInstance());
	            init(connection, ioHandler);
	        }
		}
		
		/**
		 * 是否超过了连接最大数
		 */
		private boolean isMacConnectionSizeExceeded() {
		    return (isMaxConnectionCheckAvtive && 
                    isOpen.get() && 
                    (acceptor.getDispatcherPool().getRoughNumRegisteredHandles() >= maxConcurrentConnections) &&
                    (acceptor.getDispatcherPool().getNumRegisteredHandles() >= maxConcurrentConnections));
		}
		
		
		/**
		 * 初始化.
		 */
		private void init(NonBlockingConnection connection, IoChainableHandler ioHandler) throws SocketTimeoutException, IOException {
		    // set default flush properties
            connection.setAutoflush(autoflush);
            connection.setFlushmode(flushMode);
            // 设置线程执行者
            connection.setWorkerpool(workerpool);
                
            // XXX 初始化连接
            // initialize the connection
            connection.init(ioHandler);
                
            // set timeouts  (requires that connection is already initialized)
            connection.setIdleTimeoutMillis(idleTimeoutMillis);
            connection.setConnectionTimeoutMillis(connectionTimeoutMillis);
            
            // set transfer rates
            if (writeRate != null) {
                connection.setWriteTransferRate(writeRate);
            }

            // and threshold
            if (maxReadBufferThreshold != null) {
                connection.setMaxReadBufferThreshold(maxReadBufferThreshold);
            }
		}
	}
	
	

	private static final class WorkerpoolCloser extends Thread {

		private Server server;
		
		public WorkerpoolCloser(Server server) {
			super("workerpoolCloser");
			setDaemon(true);
			
			this.server = server;
		}
		
		public void run() {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException ie) { 
				// Restore the interrupted status
				Thread.currentThread().interrupt();
			}
				
			try {
				server.defaultWorkerPool.shutdownNow();
			} finally { 
				server.defaultWorkerPool = null;
				server = null;
			}
		}
	}
}
