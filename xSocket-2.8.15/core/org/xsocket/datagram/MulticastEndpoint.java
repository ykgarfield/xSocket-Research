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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;




/**
 * non blocking Mutlicast endpoint <br><br>
 *
 * Caused by the missing channel support for multicast Datagram (JSE 6.0) this
 * class is implemented by using the "classic" MulticastSocket
 *
 * @author grro@xsocket.org
 */
public final class MulticastEndpoint extends AbstractEndpoint {

	/*
	 *  * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4527345
     */


	private static Logger LOG = Logger.getLogger(MulticastEndpoint.class.getName());


	@SuppressWarnings("unchecked")
	private static final Map<String ,Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();

	static {
		SUPPORTED_OPTIONS.put(SO_RCVBUF, Integer.class);
		SUPPORTED_OPTIONS.put(SO_SNDBUF, Integer.class);
		SUPPORTED_OPTIONS.put(IP_TOS, Integer.class);
		SUPPORTED_OPTIONS.put(SO_REUSEADDR, Boolean.class);
		SUPPORTED_OPTIONS.put(SO_REUSEADDR, Boolean.class);
		SUPPORTED_OPTIONS.put(IP_MULTICAST_TTL, Integer.class);
		SUPPORTED_OPTIONS.put(IP_MULTICAST_LOOP, Boolean.class);
	}



	// run flag
	private volatile boolean isRunning = true;


	// socket
	private final MulticastSocket socket;
	private final InetSocketAddress multicastAddress;


    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address
     *
     * @param address                 the group address
     * @param port                    the port
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, final int port) throws IOException {
		this(address, port, new HashMap<String, Object>(), 0, null, getGlobalWorkerPool());
	}



    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address
     *
     * @param address                 the group address
     * @param port                    the port
     * @param receiveSize             the size of the data packet to receive
     * @param datagramHandler         the datagram handler
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(String address, final int port, int receiveSize, IDatagramHandler datagramHandler) throws IOException {
		this(InetAddress.getByName(address), port, new HashMap<String, Object>(), receiveSize, datagramHandler, getGlobalWorkerPool());
	}





    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address
     *
     * @param address                 the group address
     * @param port                    the port
     * @param receiveSize             the size of the data packet to receive
     * @param datagramHandler         the datagram handler
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, final int port, int receiveSize, IDatagramHandler datagramHandler) throws IOException {
		this(address, port, new HashMap<String, Object>(), receiveSize, datagramHandler, getGlobalWorkerPool());
	}


	/**
  	 * Constructs a datagram socket and
  	 * connects it to the given address
     *
     * @param address                 the group address
     * @param port                    the port
     * @param receiveSize             the size of the data packet to receive
     * @param datagramHandler         the datagram handler
     * @param workerPool              the workerPool
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, final int port, int receiveSize, IDatagramHandler datagramHandler, Executor workerPool) throws IOException {
		this(address, port, new HashMap<String, Object>(), receiveSize, datagramHandler, workerPool);
	}




    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address
     *
     * @param address                 the group address
     * @param port                    the port
	 * @param options                 the socket options
     * @param receiveSize             the size of the data packet to receive
     * @param datagramHandler         the datagram handler
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(String address, int port, Map<String, Object> options, int receiveSize, IDatagramHandler datagramHandler) throws IOException {
		this(InetAddress.getByName(address), port, options, receiveSize, datagramHandler, getGlobalWorkerPool());
	}



    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address
     *
     * @param address                 the group address
     * @param port                    the port
	 * @param options                 the socket options
     * @param receiveSize             the size of the data packet to receive
     * @param datagramHandler         the datagram handler
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, int port, Map<String, Object> options, int receiveSize, IDatagramHandler datagramHandler) throws IOException {
		this(address, port, options, receiveSize, datagramHandler, getGlobalWorkerPool());
	}


    /**
  	 * Constructs a datagram socket and
  	 * connects it to the given address
     *
     * @param address                 the group address
     * @param port                    the port
	 * @param options                 the socket options
     * @param receiveSize             the size of the data packet to receive
     * @param datagramHandler         the datagram handler
     * @param workerPool              the workerPool
     * @throws IOException If some I/O error occurs
     */
	public MulticastEndpoint(InetAddress address, int port, Map<String, Object> options, int receiveSize, IDatagramHandler datagramHandler, Executor workerPool) throws IOException {
		super(datagramHandler, receiveSize, workerPool);

		socket = new MulticastSocket(port);

		for (Entry<String, Object> entry : options.entrySet()) {
			setOption(entry.getKey(), entry.getValue());
		}

		socket.joinGroup(address);
		multicastAddress = new InetSocketAddress(address, port);

		if (datagramHandler != null) {
			startReceiver();
		}

		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("upd multicast endpoint bound to " + address.getCanonicalHostName() + "/" + port);
		}
	}



	private void startReceiver() {
		Thread receiverThread = new Thread() {
			public void run() {
				while (isRunning) {
					receiveData();
				}
			}
		};

		receiverThread.setDaemon(true);
		receiverThread.setName("MulticastReceiver#" + hashCode());
		receiverThread.start();
	}



	private void receiveData() {
	    try {
			byte[] buf = new byte[getReceiveSize()];
			DatagramPacket dp = new DatagramPacket(buf, buf.length);
			socket.receive(dp);

            ByteBuffer data = ByteBuffer.wrap(dp.getData());
            data.limit(dp.getLength());   // handles if received byte size is smaller than predefined receive size

			onData(new InetSocketAddress(dp.getAddress(), dp.getPort()), data);

	    } catch(IOException e) {
	    	if (!socket.isClosed() && (LOG.isLoggable(Level.FINE))) {
	    		LOG.fine("error occured by receiving data. Reason: " + e.toString());
	    	}
	    }
	}



	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return multicastAddress.toString() + " (ID=" + getId() + ")";
	}


	/**
	 * {@inheritDoc}
	 */
	public void close() {
		if (isRunning) {
			isRunning = false;

			try {
				socket.leaveGroup(multicastAddress.getAddress());
				socket.close();
			} catch (Exception e) {
                // eat and log exception
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by closing multicast socket. Reason: " + e.toString());
				}
			}

			super.close();
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public InetAddress getLocalAddress() {
		return multicastAddress.getAddress();
	}


	/**
	 * {@inheritDoc}
	 */
	public int getLocalPort() {
		return multicastAddress.getPort();
	}



	/**
	 * {@inheritDoc}
	 */
	public boolean isOpen() {
		return !socket.isClosed();
	}


	/**
	 * {@inheritDoc}
	 */
	public void send(UserDatagram packet) throws ClosedChannelException, IOException {
		if (LOG.isLoggable(Level.FINER)) {
			LOG.finer("[" + "/:" + getLocalPort() + " " + getId() + "] sending datagram " + packet.toString());
		}

		packet.prepareForSend();

		byte[] bytes = new byte[packet.getData().remaining()];
		packet.getData().get(bytes);
		DatagramPacket dataPacket = new DatagramPacket(bytes, bytes.length, multicastAddress);
		socket.send(dataPacket);
	}


	/**
	 * {@inheritDoc}
	 */
	protected MulticastEndpoint setOption(String name, Object value) throws IOException {

		if (name.equals(IEndpoint.SO_SNDBUF)) {
			socket.setSendBufferSize((Integer) value);

		} else if (name.equals(IEndpoint.SO_REUSEADDR)) {
			socket.setReuseAddress((Boolean) value);

		} else if (name.equals(IEndpoint.SO_RCVBUF)) {
			socket.setReceiveBufferSize((Integer) value);

		} else if (name.equals(IEndpoint.IP_TOS)) {
			socket.setTrafficClass((Integer) value);

		} else if (name.equals(IEndpoint.IP_MULTICAST_TTL)) {
			socket.setTimeToLive((Integer) value);

		} else if (name.equals(IEndpoint.IP_MULTICAST_LOOP)) {
			socket.setLoopbackMode((Boolean) value);

		} else {
			LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
		}

		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public Object getOption(String name) throws IOException {

		if (name.equals(IEndpoint.SO_SNDBUF)) {
			return socket.getSendBufferSize();

		} else if (name.equals(IEndpoint.SO_REUSEADDR)) {
			return socket.getReuseAddress();

		} else if (name.equals(IEndpoint.SO_RCVBUF)) {
			return socket.getReceiveBufferSize();

		} else if (name.equals(IEndpoint.IP_TOS)) {
			return socket.getTrafficClass();

		} else if (name.equals(IEndpoint.IP_MULTICAST_TTL)) {
			return socket.getTimeToLive();

		} else if (name.equals(IEndpoint.IP_MULTICAST_LOOP)) {
			return socket.getLoopbackMode();

		} else {
			LOG.warning("option " + name + " is not supproted for " + this.getClass().getName());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, Class> getOptions() {
		return Collections.unmodifiableMap(SUPPORTED_OPTIONS);
	}
}
