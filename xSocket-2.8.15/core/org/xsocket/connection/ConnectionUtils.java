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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.Resource;

/**
 * 工具类	</br></br>
 * utility class 
 * 
 * @author grro@xsocket.org
 */
@SuppressWarnings("unchecked")
public final class ConnectionUtils {
	
	private static final Logger LOG = Logger.getLogger(ConnectionUtils.class.getName());
	
	public static final String DEFAULT_DOMAIN = "org.xsocket.connection";
	public static final String SERVER_TRHREAD_PREFIX = "xServer";
	
	private static final IoProvider IO_PROVIDER = new IoProvider();	// 单例
	
	@SuppressWarnings("rawtypes")
	private static final Map<Class, HandlerInfo> handlerInfoCache = newMapCache(25);
	@SuppressWarnings("rawtypes")
	private static final Map<Class, CompletionHandlerInfo> completionHandlerInfoCache = newMapCache(25);

	private static String implementationVersion;
	private static String implementationDate;
	

	// 私有构造
	private ConnectionUtils() { }

	
	static IoProvider getIoProvider() {
		return IO_PROVIDER;
	}
	
	
	/**
	 * validate, based on a leading int length field. The length field will be removed
	 * 
	 * @param connection     the connection
	 * @return the length 
	 * @throws IOException if an exception occurs
	 * @throws BufferUnderflowException if not enough data is available
	 */
	public static int validateSufficientDatasizeByIntLengthField(INonBlockingConnection connection) throws IOException, BufferUnderflowException {

		connection.resetToReadMark();
		connection.markReadPosition();
		
		// check if enough data is available
		int length = connection.readInt();
		if (connection.available() < length) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "]insufficient data. require " + length + " got "  + connection.available());
			}
			throw new BufferUnderflowException();
	
		} else { 
			// ...yes, remove mark
			connection.removeReadMark();
			return length;
		}
	}  


	
	/**
	 * validate, based on a leading int length field, that enough data (getNumberOfAvailableBytes() >= length) is available. If not,
	 * an BufferUnderflowException will been thrown. Example:
	 * <pre>
	 * //client
	 * connection.setAutoflush(false);  // avoid immediate write
	 * ...
	 * connection.markWritePosition();  // mark current position
	 * connection.write((int) 0);       // write "emtpy" length field
	 *  
	 * // write and count written size
	 * int written = connection.write(CMD_PUT);
	 * written += ...
	 *  
	 * connection.resetToWriteMark();  // return to length field position
	 * connection.write(written);      // and update it
	 * connection.flush(); // flush (marker will be removed implicit)
	 * ...
	 * 
	 * 
	 * // server
	 * class MyHandler implements IDataHandler {
	 *    ...
	 *    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
	 *       int length = ConnectionUtils.validateSufficientDatasizeByIntLengthField(connection);
	 *       
	 *       // enough data (BufferUnderflowException hasn`t been thrown)
	 *       byte cmd = connection.readByte();
	 *       ...
	 *    }
	 *  }      
	 * </pre>
	 * 
	 * @param connection         the connection
	 * @param removeLengthField  true, if length field should be removed
	 * @return the length 
	 * @throws IOException if an exception occurs
	 * @throws BufferUnderflowException if not enough data is available
	 */
	public static int validateSufficientDatasizeByIntLengthField(INonBlockingConnection connection, boolean removeLengthField) throws IOException, BufferUnderflowException {

		connection.resetToReadMark();
		connection.markReadPosition();
		
		// check if enough data is available
		int length = connection.readInt();
		if (connection.available() < length) {
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("[" + connection.getId() + "]insufficient data. require " + length + " got "  + connection.available());
			}
			throw new BufferUnderflowException();
	
		} else { 
			// ...yes, remove mark
			if (!removeLengthField) {
				connection.resetToReadMark();
			}
			
			connection.removeReadMark();
			return length;
		}
	}  
	
	
	/**
	 * 在一个专用的线程中启动给定的服务器.这个方法将阻塞直到服务器打开.
	 * 如果在60秒内服务器没有启动,那么将抛出超时异常	</br></br>
	 * 
	 * starts the given server within a dedicated thread. This method blocks 
	 * until the server is open. If the server hasn't been started within 
	 * 60 sec a timeout exception will been thrown. 
	 * 
	 * @param server  the server to start
	 * @throws SocketTimeoutException is the timeout has been reached 
	 */
	public static void start(IServer server) throws SocketTimeoutException  {
		start(server, 60);
	}
	
	/**
	 * 在一个专用的线程中启动给定的服务器.这个方法将阻塞直到服务器打开	</br></br>
	 * 
	 * starts the given server within a dedicated thread. This method blocks 
	 * until the server is open.
	 * 
	 * @param server     the server to start
	 * @param timeoutSec the maximum time to wait
	 * 
	 * @throws SocketTimeoutException is the timeout has been reached
	 */
	public static void start(IServer server, int timeoutSec) throws SocketTimeoutException {
		final CountDownLatch startedSignal = new CountDownLatch(1);
		
		// create and add startup listener 
		// 创建启动监听
		/** {@link Server.LifeCycleHandler}中会被调用 */
		IServerListener startupListener = new IServerListener() {
			@Override
			public void onInit() {
				startedSignal.countDown();
			};
			
			@Override
			public void onDestroy() {};
		};
		server.addListener(startupListener);
		
		// start server within a dedicated thread 
		// XXX 启动Server线程
		Thread t = new Thread(server);
		t.setName("xServer");
		t.start();
		
		// wait until server has been started (onInit has been called)
		// 直到startupListener的onInit()方法被调用
		boolean isStarted = false;
		try {
			// 等待60秒
			// 也可以自定义,直接在服务器端程序调用ConnectionUtils.start(IServer, timeoutSec)
			// 和调用server.start()是一样的效果
			isStarted = startedSignal.await(timeoutSec, TimeUnit.SECONDS);
		} catch (InterruptedException ie) {
			// Restore the interrupted status
            Thread.currentThread().interrupt();
            // 如果这里抛出异常的话,那么上面的恢复中断状态也就没有什么用处
			throw new RuntimeException("start signal doesn't occured. " + ie.toString());
		}

		// timeout occurred?
		// 超时抛异常
		// 虽然这里抛出了异常,但是却不影响程序的继续执行, 服务器仍然能够正常启动
		// 只是下面的代码不会被执行
		if (!isStarted) {
			throw new SocketTimeoutException("start timeout (" + DataConverter.toFormatedDuration((long) timeoutSec * 1000) + ")");
		}
		
		// update thread name
		t.setName(SERVER_TRHREAD_PREFIX + ":" + server.getLocalPort());
		
		// remove the startup listener
		// 移除启动监听
		server.removeListener(startupListener);
	}
	
	
	/**
	 * (deep) copy of the byte buffer array
	 *   
	 * @param buffers the byte buffer array
	 * @return the copy
	 */
	public static ByteBuffer[] copy(ByteBuffer[] buffers) {
		if (buffers == null) {
			return null;
		}
		
		ByteBuffer[] copy = new ByteBuffer[buffers.length];
		for (int i = 0; i < copy.length; i++) {
			copy[i] = copy(buffers[i]);
		}
		return copy;
	}
	
	
	/**
	 * Returns a synchronized (thread-safe) connection backed by the specified connection. All methods
     * of the wapper are synchronized based on the underyling connection.  
	 *  
	 * @param con the connection to be "wrapped" in a synchronized connection 
	 * @return the synchronized (thread-safe) connection
	 */
	public static INonBlockingConnection synchronizedConnection(INonBlockingConnection con) {
	    if (con instanceof SynchronizedNonBlockingConnection) {
	        return con;
	    } else {
	        return new SynchronizedNonBlockingConnection(con);
	    }
	}
	

	/**
     * Returns a synchronized (thread-safe) connection backed by the specified connection. All methods
     * of the wapper are synchronized based on the underyling connection.  
     *  
     * @param con the connection to be "wrapped" in a synchronized connection 
     * @return the synchronized (thread-safe) connection
     */
    public static IBlockingConnection synchronizedConnection(IBlockingConnection con) {
        if (con instanceof SynchronizedBlockingConnection) {
            return con;
        } else {
            return new SynchronizedBlockingConnection(con);
        }
    }

	
    /**
     * duplicates the byte buffer array
     *   
     * @param buffers the byte buffer array
     * @return the copy
     */
    static ByteBuffer[] duplicate(ByteBuffer[] buffers) {
        if (buffers == null) {
            return null;
        }
        
        ByteBuffer[] copy = new ByteBuffer[buffers.length];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = duplicate(buffers[i]);
        }
        return copy;
    }
    

    /**
     * duplicate the given buffer 
     * 
     * @param buffer  the buffer to copy
     * @return the copy
     */
    static ByteBuffer duplicate(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        
        return buffer.duplicate();
    }

	
	/**
	 * copies the given buffer 
	 * 
	 * @param buffer  the buffer to copy
	 * @return the copy
	 */
	static ByteBuffer copy(ByteBuffer buffer) {
		if (buffer == null) {
			return null;
		}
		
		return ByteBuffer.wrap(DataConverter.toBytes(buffer));
	}
	
	
	
	
	
	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer
	 *
	 * @param server  the server to register
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName registerMBean(IServer server) throws JMException {
		return registerMBean(server, DEFAULT_DOMAIN);
	}


	/**
	 * creates and registers a mbean for the given server on the platform MBeanServer
	 * under the given domain name
	 * 
	 * @param server   the server to register
	 * @param domain   the domain name to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName registerMBean(IServer server, String domain) throws JMException {
		return registerMBean(server, domain, ManagementFactory.getPlatformMBeanServer());
	}


	/**
	 * creates and registers a mbean for the given server on the given MBeanServer
	 * under the given domain name
	 * 
 	 * @param mbeanServer  the mbean server to use
	 * @param server       the server to register
	 * @param domain       the domain name to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName registerMBean(IServer server, String domain, MBeanServer mbeanServer) {
		try {
			return ServerMBeanProxyFactory.createAndRegister(server, domain, mbeanServer);
		} catch (Exception e) {
			throw new RuntimeException(DataConverter.toString(e));
		}
	}

	
	
	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer
	 * 
	 * @param pool  the pool to register
	 * @return the objectName  
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName  registerMBean(IConnectionPool pool) throws JMException {
		return registerMBean(pool, DEFAULT_DOMAIN);
	}

	
	/**
	 * creates and registers a mbean for the given connection pool on the platform MBeanServer 
	 * under the given domain name 
	 * 
	 * @param pool     the pool to register 
	 * @param domain   the domain name to use
	 * @return the objectName 
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName  registerMBean(IConnectionPool pool, String domain) throws JMException {
		return registerMBean(pool, domain, ManagementFactory.getPlatformMBeanServer());
	}
	

	
	
	/**
	 * creates and registers a mbean for the given pool on the given MBeanServer
	 * under the given domain name 
	 * 
 	 * @param mbeanServer  the mbean server to use
	 * @param pool         the pool to register 
	 * @param domain       the domain name to use
	 * @return the objectName 
	 * @throws JMException  if an jmx exception occurs 
	 */
	public static ObjectName  registerMBean(IConnectionPool pool, String domain, MBeanServer mbeanServer) throws JMException {
		return ConnectionPoolMBeanProxyFactory.createAndRegister(pool, domain, mbeanServer);
	}
	
	

	/**
	 * 
	 * checks if the current version matchs with the required version by using the maven version style 
	 * 
	 * @param currentVersion     the current version
	 * @param requiredVersion    the required version
	 * @return true if the version matchs
	 */
    public static boolean matchVersion(String currentVersion, String requiredVersion) {

    	try {
    		requiredVersion = requiredVersion.trim();
	        currentVersion = currentVersion.split("-")[0].trim(); // remove qualifier
	        
	        // dedicated version notation?
	        if (requiredVersion.indexOf(",") == -1) {
	            requiredVersion = requiredVersion.split("-")[0].trim(); // remove qualifier
	            return currentVersion.equalsIgnoreCase(requiredVersion);
	
	            
	        // .. no it is range notation
	        } else {
	            String[] range = requiredVersion.split(",");
	            for (int i = 0; i < range.length; i++) {
	                range[i] = range[i].trim();
	            }
	            
	            if (range.length < 2) {
	                return false;
	            }
	
	            
	            String requiredMinVersion = range[0].substring(1, range[0].length()).trim();
	            requiredMinVersion = requiredMinVersion.split("-")[0];  // remove qualifier
	
	            String requiredMaxVersion = range[1].substring(0, range[1].length() - 1).trim();
	            requiredMaxVersion = requiredMaxVersion.split("-")[0];  // remove qualifier
	            
	            // check min version            
	            if (range[0].startsWith("[") && (!currentVersion.equalsIgnoreCase(requiredMinVersion) && !isGreater(currentVersion, requiredMinVersion))) {
	                return false;
	            }
	            
	            
	            // check max version
	            if (range[1].endsWith(")")) {
	                return isSmaller(currentVersion, requiredMaxVersion);
	                
	            } else if (range[1].endsWith("]")) {
	                return (currentVersion.equalsIgnoreCase(requiredMaxVersion)|| isSmaller(currentVersion, requiredMaxVersion));
	            }
	        } 
	        
	        return false;
	        
        } catch (Throwable t) {
        	return false;
        }
    }
    
    
    private static boolean isGreater(String version, String required) {
        
        String[] numsVersion = version.split("\\.");
        String[] numsRequired = required.split("\\.");
            
        for (int i = 0; i < numsVersion.length; i++) {
            int numVersion = Integer.parseInt(numsVersion[i]);
            if (numsRequired.length <= i) {
                return true;
            }
            int numRequired = Integer.parseInt(numsRequired[i]);
                
            if (numVersion > numRequired) {
                return true;
            } 
        }
            
        return false;
    }
    
    
    private static boolean isSmaller(String version, String required) {
        
       String[] numsVersion = version.split("\\.");
       String[] numsRequired = required.split("\\.");
            
       for (int i = 0; i < numsVersion.length; i++) {
           int numVersion = Integer.parseInt(numsVersion[i]);
           if (numsRequired.length < i) {
               return true;
           }
           int numRequired = Integer.parseInt(numsRequired[i]);
                
           if (numVersion < numRequired) {
               return true;
           } 
       }
            
       return false;
    }
    
    


	static IOException toIOException(Throwable t) {
		return toIOException(t.getMessage(), t);
	}
	
	static IOException toIOException(String text, Throwable t) {
		IOException ioe = new IOException(text);
		ioe.setStackTrace(t.getStackTrace());
		return ioe;
	}

	
	/**
	 * 取得xSocket的实现版本.不重要	</br>
	 * 
	 * get the implementation version
	 * 
	 * @return the implementation version
	 */
	public static String getImplementationVersion() {
		
		if (implementationVersion == null) {
			readVersionFile();
		}
		
		return implementationVersion;
	}

	
	/**
	 * 取得xSocket的实现时间.不重要	</br>
	 * 
	 * get the implementation date
	 * 
	 * @return the implementation date
	 */
	public static String getImplementationDate() {
		
		if (implementationDate== null) {
			readVersionFile();
		}
		
		return implementationDate;
	}

	
	/**
	 * 读取版本文件：/org/xsocket/version.txt
	 */
	private static void readVersionFile() {
		
		implementationVersion = "<unknown>";
		implementationDate = "<unknown>";
			
		InputStreamReader isr = null;
		LineNumberReader lnr = null;
		try {
			isr = new InputStreamReader(ConnectionUtils.class.getResourceAsStream("/org/xsocket/version.txt"));
			if (isr != null) {
				lnr = new LineNumberReader(isr);
				String line = null;
				do {
					line = lnr.readLine();
					if (line != null) {
						if (line.startsWith("Implementation-Version=")) {
							implementationVersion = line.substring("Implementation-Version=".length(), line.length()).trim();
						} else if (line.startsWith("Implementation-Date=")) {
							implementationDate = line.substring("Implementation-Date=".length(), line.length()).trim();
						}
					}
				} while (line != null);
				lnr.close();
			}
			
		} catch (IOException ioe) { 
		    
		    implementationVersion = "<unknown>";
		    implementationDate = "<unknown>";
              
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("could not read version file. reason: " + ioe.toString());
			}
			
		} finally {
			try {
				if (lnr != null) {
					lnr.close();
				}
					
				if (isr != null) {
					isr.close();
				}
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("exception occured by closing version.txt file stream " + ioe.toString());
				}
			}
		} 
	}
	
	
	static long computeSize(ByteBuffer[] buffers) {
	    long size = 0;
	    for (ByteBuffer byteBuffer : buffers) {
            size += byteBuffer.remaining();
        }
	    
	    return size;
	}
	
	
	/**
	 * creates a thread-safe new bound cache 
	 *  
	 * @param <T>      the map value type     
	 * @param maxSize  the max size of the cache 
	 * @return the new map cache 
	 */
	public static <T> Map<Class, T> newMapCache(int maxSize) {
		return Collections.synchronizedMap(new MapCache<T>(maxSize));
	}
	
	/**
	 * 注入服务器的字段,默认情况下不会在IHander实现中声明IServer或者Server字段	</br>
	 */
	static void injectServerField(Server server, Object handler) {
		
		Field[] fields = handler.getClass().getDeclaredFields();
		for (Field field : fields) {
			if (field.isAnnotationPresent(Resource.class)) {
				Resource res = field.getAnnotation(Resource.class);

				if ((field.getType() == IServer.class) || (res.type() == IServer.class) || 
				    (field.getType() == Server.class) || (res.type() == Server.class)) {
					try {
						field.setAccessible(true);
						field.set(handler, server);
					} catch (IllegalAccessException iae) {
						LOG.warning("could not inject server for attribute " + field.getName() + ". Reason " + DataConverter.toString(iae));
					}
				}
			}
		}
	}
	
    
	
	/**
     * returns if current thread is  dispatcher thread
     * 
     * @return true, if current thread is a dispatcher thread
     */
    public static boolean isDispatcherThread() {
        return Thread.currentThread().getName().startsWith(IoSocketDispatcher.DISPATCHER_PREFIX);
    }

    
    /**
     * returns if current thread is connector thread
     * @return true, if current thread is a connector thread
     */
    static boolean isConnectorThread() {
        return Thread.currentThread().getName().startsWith(IoConnector.CONNECTOR_PREFIX);
    }
    
    
    static String printSelectionKey(SelectionKey key) {
        if (key != null) {
            try {
                int i = key.interestOps();
                return printSelectionKeyValue(i) + " isValid=" + key.isValid();
            } catch (CancelledKeyException cke) {
                return "canceled";
            }
        } else {
            return "<null>";
        }
    }

    
    static String printSelectionKeyValue(int ops) {

        StringBuilder sb = new StringBuilder();

        if ((ops & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
            sb.append("OP_ACCEPT, ");
        }

        if ((ops & SelectionKey.OP_CONNECT) == SelectionKey.OP_CONNECT) {
            sb.append("OP_CONNECT, ");
        }

        if ((ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE) {
            sb.append("OP_WRITE, ");
        }

        if ((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ) {
            sb.append("OP_READ, ");
        }

        String txt = sb.toString();
        txt = txt.trim();

        if (txt.length() > 0) {
            txt = txt.substring(0, txt.length() - 1);
        }

        return txt + " (" + ops + ")";
    }


    /**
     * IHandler实现类信息
     */
	static IHandlerInfo getHandlerInfo(IHandler handler) {
	    if (handler instanceof HandlerChain) {
	        return ((HandlerChain) handler).getHandlerInfo();
	    
	    } else {
    		HandlerInfo handlerInfo = handlerInfoCache.get(handler.getClass());
    		if (handlerInfo == null) {
    			handlerInfo = new HandlerInfo(handler);
    			// 缓存Handler
    			handlerInfoCache.put(handler.getClass(), handlerInfo);
    		}
    
    		return handlerInfo;
	    }
	}

	

    static CompletionHandlerInfo getCompletionHandlerInfo(IWriteCompletionHandler handler) {
        CompletionHandlerInfo completionHandlerInfo = completionHandlerInfoCache.get(handler.getClass());

        if (completionHandlerInfo == null) {
            completionHandlerInfo = new CompletionHandlerInfo(handler);
            completionHandlerInfoCache.put(handler.getClass(), completionHandlerInfo);
        }

        return completionHandlerInfo;
    }
    
	
    /**
     * 方法上没有Execution,则和类的Execution一致.		</br>
     */
	private static boolean isMethodThreaded(Class clazz, String methodname, boolean dflt, Class... paramClass) {
		try {
			Method meth = clazz.getMethod(methodname, paramClass);
			Execution execution = meth.getAnnotation(Execution.class);
			if (execution != null) {
				if(execution.value() == Execution.NONTHREADED) {
					return false;
				} else {
					return true;
				}
			} else {
				return dflt;
			}
			
		} catch (NoSuchMethodException nsme) {
			return dflt;
		}
	}
	
	/**
	 * IoHandler的实现是否加了注解Execution,如果加了,是否为MULTITHREADED
	 */
	private static boolean isHandlerMultithreaded(Object handler) {
		Execution execution = handler.getClass().getAnnotation(Execution.class);
		if (execution != null) {
			if(execution.value() == Execution.NONTHREADED) {
				return false;
			} else {
				return true;
			}

		} else {
			// 类上没有注解Execution,默认为多线程模式,返回true
			return true;
		}
	}
	 

	private static final class MapCache<T> extends LinkedHashMap<Class , T> {
		
		private static final long serialVersionUID = 4513864504007457500L;
		
		private int maxSize = 0;
		
		MapCache(int maxSize) {
			this.maxSize = maxSize;
		}
		

		@Override
		protected boolean removeEldestEntry(Entry<Class, T> eldest) {
			return size() > maxSize;
		}	
	}
	
	
	/**
	 * Handler信息, 实现的业务逻辑处理是否实现了某些接口.
	 * 覆盖接口的方法时是否加了注解Execution </br>
	 */
	private static final class HandlerInfo implements IHandlerInfo {
		
		private boolean isConnectHandler = false; 
		private boolean isDataHandler = false;
		private boolean isDisconnectHandler = false;
		private boolean isIdleTimeoutHandler = false;
		private boolean isConnectionTimeoutHandler = false;
		private boolean isConnectExceptionHandler = false;
		private boolean isLifeCycle = false;
		
		private boolean isConnectionScoped = false;
		
		private boolean isUnsynchronized = false;
		
		private boolean isHandlerMultithreaded = false;
		private boolean isConnectHandlerMultithreaded = false;
		private boolean isDataHandlerMultithreaded = false;
		private boolean isDisconnectHandlerMultithreaded = false;
		private boolean isIdleTimeoutHandlerMultithreaded = false;
		private boolean isConnectionTimeoutHandlerMultithreaded = false;
		private boolean isConnectExceptionHandlerMultithreaded = false;
		
		HandlerInfo(IHandler handler) {
			isConnectHandler = (handler instanceof IConnectHandler);
			isDataHandler = (handler instanceof IDataHandler);
			isDisconnectHandler = (handler instanceof IDisconnectHandler);
			isIdleTimeoutHandler = (handler instanceof IIdleTimeoutHandler);
			isConnectionTimeoutHandler = (handler instanceof IConnectionTimeoutHandler);
			isConnectExceptionHandler = (handler instanceof IConnectExceptionHandler);
			isLifeCycle = (handler instanceof ILifeCycle);
			
			isConnectionScoped = (handler instanceof IConnectionScoped);
			
			isUnsynchronized = (handler instanceof IUnsynchronized); 

			isHandlerMultithreaded = ConnectionUtils.isHandlerMultithreaded(handler);
            if (isConnectHandler) {
                isConnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnect", isHandlerMultithreaded, INonBlockingConnection.class);
            }
            
            if (isDataHandler) {
                isDataHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onData", isHandlerMultithreaded, INonBlockingConnection.class);
            }
            
            if (isDisconnectHandler) {
                isDisconnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onDisconnect", isHandlerMultithreaded, INonBlockingConnection.class);
            }
            
            if (isIdleTimeoutHandler) {
                isIdleTimeoutHandlerMultithreaded =isMethodThreaded(handler.getClass(), "onIdleTimeout", isHandlerMultithreaded, INonBlockingConnection.class);
            }
            
            if (isConnectionTimeoutHandler) {
                isConnectionTimeoutHandlerMultithreaded =isMethodThreaded(handler.getClass(), "onConnectionTimeout", isHandlerMultithreaded, INonBlockingConnection.class);
            }


            if (isConnectionTimeoutHandler) {
                isConnectHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnectionTimeout", isHandlerMultithreaded, INonBlockingConnection.class);
            }
            
            if (isConnectExceptionHandler) {
                isConnectExceptionHandlerMultithreaded = isMethodThreaded(handler.getClass(), "onConnectException", isHandlerMultithreaded, INonBlockingConnection.class, IOException.class);
            }
        }


		public boolean isConnectHandler() {
			return isConnectHandler;
		}

		public boolean isDataHandler() {
			return isDataHandler;
		}

		public boolean isDisconnectHandler() {
			return isDisconnectHandler;
		}

		public boolean isIdleTimeoutHandler() {
			return isIdleTimeoutHandler;
		}

		public boolean isConnectionTimeoutHandler() {
			return isConnectionTimeoutHandler;
		}
		
		public boolean isLifeCycle() {
			return isLifeCycle; 
		}

		public boolean isConnectionScoped() {
			return isConnectionScoped;
		}
		
		public boolean isConnectExceptionHandler() {
            return isConnectExceptionHandler;
        }

		public boolean isConnectExceptionHandlerMultithreaded() {
		    return isConnectExceptionHandlerMultithreaded;
		}

		public boolean isUnsynchronized() {
		    return isUnsynchronized;
		}
		
		public boolean isConnectHandlerMultithreaded() {
			return isConnectHandlerMultithreaded;
		}

		public boolean isDataHandlerMultithreaded() {
			return isDataHandlerMultithreaded;
		}

		public boolean isDisconnectHandlerMultithreaded() {
			return isDisconnectHandlerMultithreaded;
		}

		public boolean isIdleTimeoutHandlerMultithreaded() {
			return isIdleTimeoutHandlerMultithreaded;
		}

		public boolean isConnectionTimeoutHandlerMultithreaded() {
			return isConnectionTimeoutHandlerMultithreaded;
		}
	}

	
	static final class CompletionHandlerInfo {
        
        private boolean isOnWrittenMultithreaded = false;
        private boolean isOnExceptionMultithreaded = false;
        
        private boolean isUnsynchronized = false;

        public CompletionHandlerInfo(IWriteCompletionHandler handler) {
            
            isUnsynchronized = (handler instanceof IUnsynchronized);
            
        	boolean isHandlerMultithreaded = ConnectionUtils.isHandlerMultithreaded(handler);
                
        	isOnWrittenMultithreaded = isMethodThreaded(handler.getClass(), "onWritten", isHandlerMultithreaded, int.class);
        	isOnExceptionMultithreaded = isMethodThreaded(handler.getClass(), "onException", isHandlerMultithreaded, IOException.class);
        }
        
        public boolean isUnsynchronized() {
            return isUnsynchronized;
        }

        public boolean isOnWrittenMultithreaded() {
            return isOnWrittenMultithreaded;
        }

        public boolean isOnExceptionMutlithreaded() {
            return isOnExceptionMultithreaded;
        }
    }
}
