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
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;






/**
 * Endpoint implementation base
 *
 * @author grro@xsocket.org
 */
abstract class AbstractChannelBasedEndpoint extends AbstractEndpoint {

	private static final Logger LOG = Logger.getLogger(AbstractChannelBasedEndpoint.class.getName());

	private static final MemoryManager memoryManager = new MemoryManager(65536, false);
	private static IoSocketDispatcher dispatcher = createDispatcher();

	@SuppressWarnings("unchecked")
	private static final Map<String ,Class> SUPPORTED_OPTIONS = new HashMap<String, Class>();

	static {
		SUPPORTED_OPTIONS.put(SO_RCVBUF, Integer.class);
		SUPPORTED_OPTIONS.put(SO_SNDBUF, Integer.class);
		SUPPORTED_OPTIONS.put(IP_TOS, Integer.class);
		SUPPORTED_OPTIONS.put(SO_REUSEADDR, Boolean.class);
	}



	// socket
	private final DatagramSocket socket;
	private final DatagramChannel channel;
	private final ByteOrder byteOrder = ByteOrder.BIG_ENDIAN;


	// send queue
	private final List<UserDatagram> sendQueue = Collections.synchronizedList(new LinkedList<UserDatagram>());




	/**
	 * constructor
	 *
	 * @param addr                     the local address
	 * @param options                  the socket options
     * @param datagramHandler          the datagram handler
     * @param receivePacketSize        the receive packet size
     * @param workerPool               the workerpool to use
     * @throws IOException If some I/O error occurs
	 */
	AbstractChannelBasedEndpoint(InetSocketAddress addr, Map<String, Object> options, IDatagramHandler datagramHandler, int receivePacketSize, Executor workerPool) throws IOException {
		super(datagramHandler, receivePacketSize, workerPool);

		channel = DatagramChannel.open();
		channel.configureBlocking(false);

		socket = channel.socket();

		for (Entry<String, Object> entry : options.entrySet()) {
			setOption(entry.getKey(), entry.getValue());
		}

		socket.bind(addr);

		dispatcher.register(this);

		logFine("enpoint has been bound to locale port " + getLocalPort() + " (server mode)");
	}



	private static IoSocketDispatcher createDispatcher() {
		IoSocketDispatcher disp = new IoSocketDispatcher();
		Thread t = new Thread(disp);
		t.setName("DispatcherThread#" + disp.hashCode());
		t.setDaemon(true);
		t.start();

		return disp;
	}


	protected final DatagramChannel getChannel() {
		return channel;
	}




	/**
	 * {@inheritDoc}
	 */
	public final void close() {
		if (isOpen()) {
			try {
				logFine("closing " + toCompactString());
				channel.close();
			} catch (IOException ioe) {
				logFine("error occured by closing connection. Reason " + ioe.toString());
			}

			super.close();
		}
	}



	/**
	 * {@inheritDoc}
	 */
	public final InetAddress getLocalAddress() {
		return socket.getLocalAddress();
	}


	/**
	 * {@inheritDoc}
	 */
	public final int getLocalPort() {
		return socket.getLocalPort();
	}


	/**
	 * {@inheritDoc}
	 */
	public final boolean isOpen() {
		return channel.isOpen();
	}



	/**
	 * log a fine msg
	 *
	 * @param msg the log message
	 */
	private void logFine(String msg) {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] " + msg);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	public void send(UserDatagram packet) throws IOException {
		if (packet.getRemoteAddress() == null) {
			throw new IOException("remote socket adress has to be set");
		}

		logFine("add datagram packet (" + packet + ") to write queue");

		packet.prepareForSend();

		sendQueue.add(packet);
		logFine("update interest ops to write");
		
		dispatcher.initiateWrite(this);
	}



	/**
	 * write the outgoing data to socket
	 *
	 */
	private void writePhysical() {
		if (!sendQueue.isEmpty()) {
			synchronized (sendQueue) {
				for (UserDatagram packet : sendQueue) {
					try {
						if (LOG.isLoggable(Level.FINE)) {
							LOG.fine("[" + "/:" + getLocalPort() + " " + getId() + "] sending datagram " + packet.toString());
						}

						int dataToSend = packet.getSize();
						int written = channel.send(packet.getData(), packet.getRemoteSocketAddress());

						if (LOG.isLoggable(Level.FINE) && (dataToSend != written)) {
							LOG.fine("Error occured by sending datagram. Size DataToSend=" + dataToSend + ", written=" + written);
						}
					} catch (IOException ioe) {
						LOG.warning("could not write datagram to " + packet.getRemoteAddress() + " .Reason: " + DataConverter.toString(ioe));
					}
				}

				sendQueue.clear();
			}
		}
	}



	/**
	 * a compact string of this endpoint
	 */
	public String toCompactString() {
		return this.getClass().getSimpleName() + " " + socket.getLocalAddress().getCanonicalHostName() + ":" + getLocalPort();
	}



	final void onReadableEvent() {
		if (isOpen()) {

			try {
				// perform non-blocking read operation
				if (getReceiveSize() > 0) {
					ByteBuffer readBuffer = memoryManager.acquireMemory(getReceiveSize());
					readBuffer.order(byteOrder);
					SocketAddress address = channel.receive(readBuffer);
					
					// datagram is not immediately available
					if (address == null) {
						return;

					// datagram is available
					} else {

						// nothing has been read
						if (readBuffer.position() == 0) {
							return;
						}

						readBuffer.flip();
						onData(address, readBuffer);
					}
				}
			} catch (IOException ioe) {
				logFine("error occured while receiving. Reason: " + ioe.toString());
			}
		}
	}


	final void onWriteableEvent() throws IOException {
		dispatcher.setSelectionKeyToReadImmediately(this);
		writePhysical();
	}

	
	final void onDispatcherClose() {
		close();
	}



	/**
	 * {@inheritDoc}
	 */
	protected AbstractChannelBasedEndpoint setOption(String name, Object value) throws IOException {

		if (name.equals(IEndpoint.SO_SNDBUF)) {
			socket.setSendBufferSize((Integer) value);

		} else if (name.equals(IEndpoint.SO_REUSEADDR)) {
			socket.setReuseAddress((Boolean) value);

		} else if (name.equals(IEndpoint.SO_RCVBUF)) {
			socket.setReceiveBufferSize((Integer) value);

		} else if (name.equals(IEndpoint.IP_TOS)) {
			socket.setTrafficClass((Integer) value);

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
