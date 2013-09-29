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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.Executor;

import org.xsocket.connection.IConnection.FlushMode;

/**
 * 一个服务器接收连接,然后委托INonBlockingConnection去分配处理器.	</br>
 * 这个服务器包括了调度程序,负责执行socket I/O操作.一个连接分配到一个调度程序上		</br>
 * 为了处理应用程序相关的事件,比如, onDate, onClose或者onConnect,分配的IHandler的适当的回调方法将调用.	</br>
 * 处理器支持的回调方法将在服务器启动期间使用反射技术分析.回调方法将将被标记,实现指定的接口,
 * 比如IDataHandler或者IConnectHandler.通常一个处理器将实现几个接口.		</br></br>
 * 
 * A server accepts new incoming connections, and delegates the handling of the
 * {@link INonBlockingConnection} to the assigned handler.
 *
 * The server includes dispatchers, which are responsible to perform the
 * socket I/O operations. A connection is assigned to one dispatcher. <br>
 * To handle application-relevant events like <code>onData</code>,
 * <code>onClose</code> or <code>onConnect</code> the appropriated callback method
 * of the assigned {@link IHandler} will be called. The supported callback
 * methods of the handler will be analyzed by using reflection during  the server start-up
 * phase. The callback method will be marked by implementing the specific interface
 * like {@link IDataHandler} or {@link IConnectHandler}. Often a
 * handler will implement several handler interfaces.<br>
 * <br>
 * E.g.
 * <pre>
 *   ...
 *   IServer smtpServer = new Server(25, new SmtpProtcolHandler());
 *   
 *   smtpServer.start();
 *   ...
 *
 *
 *   // Handler definition
 *   class SmtpProtcolHandler implements IDataHandler, IConnectHandler {
 *
 *      SessionData sessionData = (SessionData) connection.getAttachment();
 *      
 *      String cmd = connection.readStringByDelimiter("\r\n").toUpperCase();
 *      
 *      if (cmd.startsWith("HELO")) {
 *         connection.write("250 SMTP Service\r\n");
 *         
 *      } else if(cmd.equals("DATA")) {
 *         String msgId = connection.getId() + "." + sessionData.nextId();
 *         File msgFile = new File(msgFileDir + File.separator + msgId + ".msg");
 *         
 *         connection.setHandler(new DataHandler(msgFile, this));
 *         connection.write("354 Enter message, ending with \".\"\r\n");
 *         
 *      } else {
 *       
 *         ...         
 *      }
 *      
 *      ...
 *   }
 * </pre>
 *
 *
 * @author grro@xsocket.org
 */
public interface IServer extends Runnable, Closeable {

	/**
	 * 除了SO_RCVBUF、SO_REUSEADDR外,其它的几个变量都没有任何地方使用.
	 */

	/**
	 * 默认的空闲超时时间,1个小时		</br></br>
	 * 
	 * the default idle timeout
	 */
	public static final int DEFAULT_IDLE_TIMEOUT_SEC = 1 * 60 * 60;  // one hour

	/**
	 * 默认的连接超时时间,整型最大值		</br></br>
	 * 
	 * the default connection timeout
	 */
	public static final int DEFAULT_CONNECTION_TIMEOUT_SEC = Integer.MAX_VALUE;  // no timeout

	/** 读/写缓冲区大小  */
	public static final String SO_RCVBUF = IConnection.SO_RCVBUF;
	public static final String SO_REUSEADDR = IConnection.SO_REUSEADDR;

	/** 默认读/传输预分配大小  */
	public static final int DEFAULT_READ_TRANSFER_PREALLOCATION_SIZE = 65536;
	/** 默认读/传输预分配最小值  */
	public static final int DEFAULT_READ_TRANSFER_PREALLOCATION_MIN_SIZE = 64; 
	/** 读传输是否使用直接内存分配  */
	public static final boolean DEFAULT_READ_TRANSFER_USE_DIRECT = true;

	
	/**
	 * 标识服务是否在运行		</br></br>
	 * 
	 * signals, if service is running
	 *
	 * @return true, if the server is running
	 */
	boolean isOpen();
	
	
	/**
	 * 在一个专用(dedicated)的线程内启动一个给定的服务器.这个方法将阻塞直到服务器打开.
	 * 可能会抛出连接超时异常.
	 * </br></br>
	 * 
	 * starts the given server within a dedicated thread. This method blocks 
	 * until the server is open.  
	 * 
	 * @throws SocketTimeoutException is the timeout has been reached 
	 */
	void start() throws IOException;
	

	/**
	 * 得到空闲超时时间(毫秒)		</br></br>
	 * 
	 * returns the idle timeout in millis.
	 *
	 * @return idle timeout in millis
	 */
	long getIdleTimeoutMillis();

	/**
	 * 设置空闲超时时间(毫秒)		</br></br>
	 * 
	 * sets the idle timeout in millis
	 *
	 * @param timeoutInSec idle timeout in millis
	 */
	void setIdleTimeoutMillis(long timeoutInMillis);


	/**
	 * 取得连接超时时间(毫秒)		</br></br>
	 * 
	 * gets the connection timeout
	 *
	 * @return connection timeout
	 */
	long getConnectionTimeoutMillis();

	/**
	 * 设置连接超时的最大时间(毫秒).超过这个时间连接将终止	</br></br>
	 * 
	 * sets the max time for a connections. By
	 * exceeding this time the connection will be
	 * terminated
	 *
	 * @param timeoutSec the connection timeout in millis
	 */
	void setConnectionTimeoutMillis(long timeoutMillis);

	
	/**
	 * set the send delay time for a connection. Data to write will be buffered
	 * internally and be written to the underlying subsystem
	 * based on the given write rate.
	 * The write methods will <b>not</b> block for this time. <br>
	 *
	 * By default the write transfer rate is set with UNLIMITED <br><br>
	 *
	 * Reduced write transfer is only supported for FlushMode.ASYNC. see
	 * {@link INonBlockingConnection#setFlushmode(org.xsocket.connection.IConnection.FlushMode))}
	 *
	 * @param bytesPerSecond the transfer rate of the outgoing data
	 * @throws IOException If some other I/O error occurs
	 */
	void setWriteTransferRate(int bytesPerSecond) throws IOException;
		

	/**
	 * set the read rate. By default the read transfer rate is set with UNLIMITED <br><br>
	 *
	 * @param bytesPerSecond the transfer rate of the outgoing data
	 * @throws IOException If some other I/O error occurs
	 */
//	public void setReadTransferRate(int bytesPerSecond) throws IOException;
		
	
	/**
	 * get the server port
	 *
	 * @return the server port
	 */
	int getLocalPort();


	/**
	 * return the worker pool
	 *
	 * @return the worker pool
	 */
	Executor getWorkerpool();

	
	/**
	 * sets the worker pool
	 * @param workerpool  the workerpool 
	 */
	void setWorkerpool(Executor workerpool);
	
	
	/**
	 * 返回处理器		</br></br>
	 * 
	 * gets the handler 
	 * @return the handler
	 */
	IHandler getHandler();

	
	/**
	 * 
	 * sets the flush mode for new connections. See {@link INonBlockingConnection#setFlushmode(FlushMode)} 
	 * for more information
	 * 
	 * @param flusmode the flush mode
	 */
	void setFlushmode(FlushMode flusmode);
	
	
	/**
	 * return the flush mode for new connections 
	 * 
	 * @return the flush mode
	 */
	FlushMode getFlushmode();
	

	/**
	 * set autoflush for new connections.  See {@link IReadWriteableConnection#setAutoflush(boolean)} 
	 * for more information
	 *
	 * @param autoflush true if autoflush should be activated
	 */
	void setAutoflush(boolean autoflush);
	

	/**
	 * get autoflush. See {@link IReadWriteableConnection#setAutoflush(boolean)} 
	 * for more information
	 * 
	 * @return true, if autoflush is activated
	 */
	boolean getAutoflush();



	/**
	 * adds a listener
	 * @param listener gthe listener to add
	 */
	void addListener(IServerListener listener);


	/**
	 * removes a listener
	 * @param listener the listener to remove
	 * @return true, is the listener has been removed
	 */
	boolean removeListener(IServerListener listener);


	/**
	 * get the local address
	 * @return the local address
	 */
	InetAddress getLocalAddress();


	/**
	 * 取得选择的值		</br></br>
	 * 
	 * returns the vlaue of a option
	 *
	 * @param name  the name of the option
	 * @return the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	Object getOption(String name) throws IOException;


	
	/**
	 * 设置启动日志		</br></br>
	 * 
	 * set the log message, which will be printed out during the start up 
	 * 
	 * @param message the startUp log message
	 */
	void setStartUpLogMessage(String message);
	
	
	
	/**
	 * 取得启动日志		</br></br>
	 * 
	 * returns the startUp log message
	 * 
	 * @return the startUp log message
	 */
	String getStartUpLogMessage();
	

	/**
	 * Returns an unmodifiable map of the options supported by this endpont.
	 *
	 * The key in the returned map is the name of a option, and its value
	 * is the type of the option value. The returned map will never contain null keys or values.
	 *
	 * @return An unmodifiable map of the options supported by this channel
	 */
	@SuppressWarnings("rawtypes")
	Map<String, Class> getOptions();
}
