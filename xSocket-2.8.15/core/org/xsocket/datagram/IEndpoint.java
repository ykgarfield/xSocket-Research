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
package org.xsocket.datagram;


import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Map;





/**
 * An endpoint, which can be used to send and receive {@link UserDatagram}. E.g.
 *
 * <pre>
 *
 *  // without datagram handler
 *  ...
 *  IEndpoint endpoint = new Endpoint(packageSize);
 *
 *  UserDatagram request = new UserDatagram(remoteHostname, remotePort, packageSize);
 *  request.write("Hello peer, how are you?");
 *
 *  endpoint.send(request);
 *  UserDatagram response = endpoint.receive(1000);  // receive (timeout 1 sec)
 *
 *  endpoint.close();
 *  ...
 *
 *
 *  // by using a handler
 *  ...
 *  MyHandler hdl = new MyHandler();
 *  IEndpoint endpoint = new Endpoint(packageSize, hdl);
 *
 *  UserDatagram request = new UserDatagram(remoteHostname, remotePort, packageSize);
 *  request.write("Hello peer, how are you?");
 *
 *  endpoint.send(request);
 *  // response will be handled by MyHandler
 *
 *  // wait
 *  ...
 *  endpoint.close();
 *
 *
 *  class MyHandler implements IDatagramHandler {
 *
 *     public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
 *          UserDatagram datagram = localEndpoint.receive();  // get the datagram
 *          ...
 *          return true;
 *     }
 *  }
 * </pre>
 *
 * @author grro@xsocket.org
 */
public interface IEndpoint extends Closeable {

	public static final String SO_SNDBUF = "SOL_SOCKET.SO_SNDBUF";
	public static final String SO_RCVBUF = "SOL_SOCKET.SO_RCVBUF";
	public static final String SO_REUSEADDR = "SOL_SOCKET.SO_REUSEADDR";
	public static final String SO_BROADCAST = "SOL_SOCKET.SO_BROADCAST";
	public static final String IP_TOS = "IPPROTO_IP.IP_TOS";
	public static final String IP_MULTICAST_TTL =  "IPPROTO_IP.IP_MULTICAST_TTL";
	public static final String IP_MULTICAST_LOOP =  "IPPROTO_IP.IP_MULTICAST_LOOP";


	/**
	 * returns, if the endpoint is open
	 *
	 *
	 * @return true if the endpoint is open
	 */
	boolean isOpen();


	/**
	 * returns the address of the endpoint
	 *
	 * @return the address
	 */
	InetAddress getLocalAddress();


	/**
	 * returns the port of the endpoint
	 *
	 * @return the port
	 */
	int getLocalPort();


	/**
	 * sets the default encoding used by this endpoint
	 *
	 * @param encoding the default encoding
	 */
	void setDefaultEncoding(String encoding);


	/**
	 * gets the default encoding used by this endpoint
	 *
	 * @return the default encoding
	 */
	String getDefaultEncoding();


    /**
	 * send a datagram to the remote endpoint
	 *
	 * @param datagram the datagram to send
	 * @throws IOException If some other I/O error occurs
	 * @throws ClosedException if the underlying channel is closed
	 */
	void send(UserDatagram datagram) throws IOException;


	/**
	 * set the size of the datagram that will be received
	 *
	 * @param receiveSize  the receive size
	 */
	void setReceiveSize(int receiveSize);


	/**
	 * get the size of the datagram that will be received
	 * @return the receive size
	 */
	int getReceiveSize();


	/**
	 * receive a datagram packet (receive timeout = 0)
	 *
	 * @return the received datagram packet or null if no datagram is available
     * @throws IOException If some other I/O error occurs
	 */
	UserDatagram receive() throws IOException;



	/**
	 * receive a datagram packet
	 *
	 * @param timeoutMillis       the receive timeout in millis
	 * @return the received datagram packet
	 * @throws SocketTimeoutException If the receive timeout has been reached
     * @throws IOException If some other I/O error occurs
	 */
	UserDatagram receive(long timeoutMillis) throws IOException, SocketTimeoutException;




	/**
	 * return the endpoint id
	 * @return the endpoint id
	 */
	String getId();



	/**
	 * returns the vlaue of a option
	 *
	 * @param name  the name of the option
	 * @return the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	Object getOption(String name) throws IOException;



	/**
	 * Returns an unmodifiable map of the options supported by this endpont.
	 *
	 * The key in the returned map is the name of a option, and its value
	 * is the type of the option value. The returned map will never contain null keys or values.
	 *
	 * @return An unmodifiable map of the options supported by this channel
	 */
	@SuppressWarnings("unchecked")
	Map<String,Class> getOptions();
}
