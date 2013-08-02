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
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.xsocket.DataConverter;

/**
 * 负责接收连接和注册到dispatcher中	</br>
 * accept()、listen()		</br>
 * 
 * The acceptor is responsible to accept new incoming connections, and
 * register these on the dispatcher.<br><br>
 *
 * @author grro@xsocket.org
 */
final class IoAcceptor  {

    private static final Logger LOG = Logger.getLogger(IoAcceptor.class.getName());

	private static final String SO_RCVBUF = IServer.SO_RCVBUF;
	private static final String SO_REUSEADDR = IServer.SO_REUSEADDR;

	@SuppressWarnings("rawtypes")
	private static final Map<String, Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();

    static {
        SUPPORTED_OPTIONS.put(SO_RCVBUF, Integer.class);
        SUPPORTED_OPTIONS.put(SO_REUSEADDR, Boolean.class);
    }


    // io handler
    // 构造函数中设置
    /** {@link Server.LifeCycleHandler} */
    private IIoAcceptorCallback callback;

    
    // open flag
    // 无限循环接收的标志:accept()方法
    // 在close()方法中设置为false
    private final AtomicBoolean isOpen = new AtomicBoolean(true);



    // Socket
    // 在accept()方法中接收请求,阻塞方式
    // 通过无限循环的方式等待客户端的连接
    // 而不是一般的通过在Selector中注册连接事件
    /** 只能设置的Socket选项 {@link #setOption(String, Object)}  */
    private final ServerSocketChannel serverChannel;


    // SSL
    private final boolean sslOn;
    private final SSLContext sslContext;


    // dispatcher pool
    // 调度池
    private final IoSocketDispatcherPool dispatcherPool;

    
    // statistics
    private long acceptedConnections;	// 连接数
	private long lastRequestAccpetedRate = System.currentTimeMillis();


    public IoAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, boolean isReuseAddress) throws IOException {
        this(callback, address, backlog, null, false, isReuseAddress);
    }
    
    /**
     * 创建ServerSocketChannel,阻塞模式.	</br>
     * 创建IoSocketDispatcher线程,并启动	{@link IoSocketDispatcherPool#updateDispatcher()} </br>
     */
    public IoAcceptor(IIoAcceptorCallback callback, InetSocketAddress address, int backlog, SSLContext sslContext, boolean sslOn, boolean isReuseAddress) throws IOException {
        this.callback = callback;
        this.sslContext = sslContext;
        this.sslOn = sslOn;

        LOG.fine("try to bind server on " + address);

        // create a new server socket
        // 创建ServerSocketChannel
        serverChannel = ServerSocketChannel.open();
        assert (serverChannel != null);
        
        // XXX 为阻塞模式
        serverChannel.configureBlocking(true);
        // 等待客户端连接永远不会超时
        serverChannel.socket().setSoTimeout(0);  // accept method never times out
        serverChannel.socket().setReuseAddress(isReuseAddress); 
        
        try {
        	// 绑定地址
            serverChannel.socket().bind(address, backlog);
            
            // 默认大小为2
            // XXX 创建IoSocketDispatcher线程,并启动
            // IoSocketDispatcherPool#updateDispatcher()
            dispatcherPool = new IoSocketDispatcherPool("Srv" + getLocalPort(), IoProvider.getServerDispatcherInitialSize());
        	
        } catch (BindException be) {
        	serverChannel.close();
            LOG.warning("could not bind server to " + address + ". Reason: " + DataConverter.toString(be));
            throw be;
        }

    }

    
    /**
     * 设置服务器端的Socket选项	</br>
     * 服务器端只能设置接收缓冲大小和地址是否可以重用两个选项.		</br>
     * 
     * {@link IoProvider#createAcceptor(IIoAcceptorCallback, InetSocketAddress, int, Map)}	</br>
     * {@link IoProvider#createAcceptor(IIoAcceptorCallback, InetSocketAddress, int, Map, SSLContext, boolean)}	</br>
     * 
     * 没有进行参数类型的判断,会导致异常,应该进行判断并给出更好的错误提示.	</br>
     * 
     * 测试程序：{@link ServerTest#setOption()}
     */
    void setOption(String name, Object value) throws IOException {
        if (name.equals(SO_RCVBUF)) {
            serverChannel.socket().setReceiveBufferSize((Integer) value);
        } else if (name.equals(SO_REUSEADDR)) {
            serverChannel.socket().setReuseAddress((Boolean) value);
        } else {
            LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
        }
    }

    
    boolean isSSLSupported() {
    	return (sslContext != null);
    }
		
    boolean isSSLOn() {
    	return sslOn;
    }

    

    /**
     * {@inheritDoc}
     */
    public Object getOption(String name) throws IOException {

        if (name.equals(SO_RCVBUF)) {
            return serverChannel.socket().getReceiveBufferSize();

        } else if (name.equals(SO_REUSEADDR)) {
            return serverChannel.socket().getReuseAddress();

        } else {
            LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
            return null;
        }
    }


	@SuppressWarnings("rawtypes")
	public Map<String, Class> getOptions() {
        return Collections.unmodifiableMap(SUPPORTED_OPTIONS);
    }
    
    
    IoSocketDispatcherPool getDispatcherPool() {
    	return dispatcherPool;
    }

	

    /**
     * {@inheritDoc}
     */
    public InetAddress getLocalAddress() {
    	return serverChannel.socket().getInetAddress();
    }


    /**
     * {@inheritDoc}
     */
    public int getLocalPort() {
    	return serverChannel.socket().getLocalPort();
    }
    
        

    /**
     * 在 {@link Server#run()} 处被调用.	</br>
     * 
     * 调用 {@link Server.LifeCycleHandler#onConnected()}	</br>
     */
    public void listen() throws IOException {
    	// Server类的内部私有类的LifeCycleHandler的onConnected()方法
    	// 执行IServerListener的onInit()方法
    	callback.onConnected();
    	// 接收请求
    	accept();
    }
    
    private final SimpleDateFormat sdf = new SimpleDateFormat("mm:ss.SSS");

    /**
     * 无限循环接收请求.
     * 有1个连接就分配一个IoSocketDispatcher去处理此连接的OP_READ、OP_WRITE事件
     * 	</br>
     */
    private void accept() {
    	// 默认为true
        while (isOpen.get()) {
            try {
                // blocking accept call
            	// 阻塞接收
                SocketChannel channel = serverChannel.accept();

                System.out.println("接收到连接预处理开始：" + sdf.format(new Date()));
                // create IoSocketHandler
                // 取出一个IoSocketDispatcher
                // 默认情况只创建两个IoSocketDispatcher
                IoSocketDispatcher dispatcher = dispatcherPool.nextDispatcher();
                // SocketChannel设置为非阻塞
                // IoSocketHandler
                IoChainableHandler ioHandler = ConnectionUtils.getIoProvider().createIoHandler(false, dispatcher, channel, sslContext, sslOn);

                // notify call back
                // XXX 注册OP_READ事件
                /** {@link Server.LifeCycleHandler} */
                callback.onConnectionAccepted(ioHandler);
    			acceptedConnections++;
    			System.out.println("接收到连接预处理结束：" + sdf.format(new Date()));

            } catch (Exception e) {
                // if acceptor is running (<socket>.close() causes that any
                // thread currently blocked in accept() will throw a SocketException)
                if (serverChannel.isOpen()) {
                	LOG.warning("error occured while accepting connection: " + DataConverter.toString(e));
                }
            }
        }
    }

    

	
    

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (isOpen.get()) {
            isOpen.set(false);

            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("closing acceptor");
            }
         
            try {
                // closes the server socket
                serverChannel.close();
            } catch (Exception e) {
                // eat and log exception
            	if (LOG.isLoggable(Level.FINE)) {
            		LOG.fine("error occured by closing " + e.toString());
            	}
            }

            dispatcherPool.close();

            callback.onDisconnected();
            callback = null;   // unset reference to server
        }
    }

    
     	
	void setDispatcherSize(int size) {
		dispatcherPool.setDispatcherSize(size);
    }
    
	int getDispatcherSize() {
    	return dispatcherPool.getDispatcherSize();
    }
	
    
	boolean getReceiveBufferIsDirect() {
		return dispatcherPool.getReceiveBufferIsDirect();
	}

	void setReceiveBufferIsDirect(boolean isDirect) {
		dispatcherPool.setReceiveBufferIsDirect(isDirect);
	}

	
	boolean isReceiveBufferPreallocationMode() {
    	return dispatcherPool.isReceiveBufferPreallocationMode();
	}
	
	
	void setReceiveBufferPreallocationMode(boolean mode) {
		dispatcherPool.setReceiveBufferPreallocationMode(mode);	}

	
	void setReceiveBufferPreallocatedMinSize(Integer minSize) {
		dispatcherPool.setReceiveBufferPreallocatedMinSize(minSize);
	}
	
	
	Integer getReceiveBufferPreallocatedMinSize() {
   		return dispatcherPool.getReceiveBufferPreallocatedMinSize();
 	}
	
	
	/**
	 * get the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @return preallocation buffer size
	 */
	Integer getReceiveBufferPreallocationSize() {
		return dispatcherPool.getReceiveBufferPreallocationSize();
	}

	/**
	 * set the size of the preallocation buffer,
	 * for reading incoming data
	 *
	 * @param size the preallocation buffer size
	 */
	void setReceiveBufferPreallocationSize(int size) {
		dispatcherPool.setReceiveBufferPreallocationSize(size);
	}

	  
    double getAcceptedRateCountPerSec() {
     	double rate = 0;
		
    	long elapsed = System.currentTimeMillis() - lastRequestAccpetedRate;
    	
    	if (acceptedConnections == 0) {
    		rate = 0;
    		
    	} else if (elapsed == 0) {
    		rate = Integer.MAX_VALUE;
    		
    	} else {
    		rate = (((double) (acceptedConnections * 1000)) / elapsed);
    	}
    		
    	lastRequestAccpetedRate = System.currentTimeMillis();
    	acceptedConnections = 0;

    	return rate;
    }
    
	
	long getSendRateBytesPerSec() {
		return dispatcherPool.getSendRateBytesPerSec();
	}
	
	
	long getReceiveRateBytesPerSec() {
		return dispatcherPool.getReceiveRateBytesPerSec();
	}
}
