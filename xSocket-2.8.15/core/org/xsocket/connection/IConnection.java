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

/**
 * 两个端点之间的连接(session).封装了底层的socket channel.	<br><br>
 * 
 * A connection (session) between two endpoints. It encapsulates the underlying socket channel. <br><br>
 *
 * @author grro@xsocket.org
 */
public interface IConnection extends Closeable {

	public static final String INITIAL_DEFAULT_ENCODING = "UTF-8";

	/** Socket选项 */
	public static final String SO_SNDBUF = "SOL_SOCKET.SO_SNDBUF";
	public static final String SO_RCVBUF = "SOL_SOCKET.SO_RCVBUF";
	public static final String SO_REUSEADDR = "SOL_SOCKET.SO_REUSEADDR";
	public static final String SO_KEEPALIVE = "SOL_SOCKET.SO_KEEPALIVE";
	public static final String SO_LINGER = "SOL_SOCKET.SO_LINGER";
	public static final String TCP_NODELAY = "IPPROTO_TCP.TCP_NODELAY";
	static final String SO_TIMEOUT = "SOL_SOCKET.SO_TIMEOUT";

	// 最大超时时间
	public static final long MAX_TIMEOUT_MILLIS = Long.MAX_VALUE;
	// 默认超时时间
	public static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = MAX_TIMEOUT_MILLIS;
	// 最大空闲时间
	public static final long DEFAULT_IDLE_TIMEOUT_MILLIS = MAX_TIMEOUT_MILLIS;
	
	public enum FlushMode { SYNC, ASYNC };
	public static final FlushMode DEFAULT_FLUSH_MODE = FlushMode.SYNC;
	public static final boolean DEFAULT_AUTOFLUSH = true;



	/**
	 * returns the id
	 *
	 * @return id
	 */
	String getId();
	
	
	/**
     * returns true id connection is server side
     * 
     * @return true, if is server side
     */
    boolean isServerSide();


	/**
	 * returns, if the connection is open. <br><br>
	 *
	 * Please note, that a connection could be closed, but reading of already
	 * received (and internally buffered) data would not fail. See also
	 * {@link IDataHandler#onData(INonBlockingConnection)}
	 *
	 *
	 * @return true if the connection is open
	 */
	boolean isOpen();





	/**
	 * returns the local port
	 *
	 * @return the local port
	 */
	int getLocalPort();



	/**
	 * returns the local address
	 *
	 * @return the local IP address or InetAddress.anyLocalAddress() if the socket is not bound yet.
	 */
	InetAddress getLocalAddress();




	/**
	 * returns the remote address
	 *
	 * @return the remote address
	 */
	InetAddress getRemoteAddress();


	/**
	 * returns the port of the remote end point
	 *
	 * @return the remote port
	 */
	int getRemotePort();





	
	/**
	 * returns the idle timeout in millis.
	 *
	 * @return idle timeout in millis
	 */
	long getIdleTimeoutMillis();


	/**
	 * sets the idle timeout in millis
	 *
	 * @param timeoutInSec idle timeout in millis
	 */
	void setIdleTimeoutMillis(long timeoutInMillis);



	/**
	 * gets the connection timeout
	 *
	 * @return connection timeout
	 */
	long getConnectionTimeoutMillis();


	/**
	 * sets the max time for a connections. By
	 * exceeding this time the connection will be
	 * terminated
	 *
	 * @param timeoutSec the connection timeout in millis
	 */
	void setConnectionTimeoutMillis(long timeoutMillis);
	
	
	/**
	 * returns the remaining time before a idle timeout occurs 
	 *
	 * @return the remaining time 
	 */
	long getRemainingMillisToIdleTimeout();

	
	/**
	 * returns the remaining time before a connection timeout occurs 
	 *
	 * @return the remaining time 
	 */
	long getRemainingMillisToConnectionTimeout();


	
	/**
	 * sets the value of a option. <br><br>
	 *
	 * A good article for tuning can be found here {@link http://www.onlamp.com/lpt/a/6324}
	 *
	 * @param name   the name of the option
	 * @param value  the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	void setOption(String name, Object value) throws IOException;



	/**
	 * returns the value of a option
	 *
	 * @param name  the name of the option
	 * @return the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	Object getOption(String name) throws IOException;



	/**
	 * Returns an unmodifiable map of the options supported by this end point.
	 *
	 * The key in the returned map is the name of a option, and its value
	 * is the type of the option value. The returned map will never contain null keys or values.
	 *
	 * @return An unmodifiable map of the options supported by this channel
	 */
	@SuppressWarnings("rawtypes")
	java.util.Map<String, Class> getOptions();
	

	
	/**
	 * Attaches the given object to this connection
	 *
	 * @param obj The object to be attached; may be null
	 * @return The previously-attached object, if any, otherwise null
	 */
	void setAttachment(Object obj);


	/**
	 * Retrieves the current attachment.
	 *
	 * @return The object currently attached to this key, or null if there is no attachment
	 */
	Object getAttachment();
}
