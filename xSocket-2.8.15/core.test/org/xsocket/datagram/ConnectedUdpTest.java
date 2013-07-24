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
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;





import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.datagram.IConnectedEndpoint;



/**
*
* @author grro@xsocket.org
*/
public final class ConnectedUdpTest {

	private static final int ACCEPTOR_PAKET_SIZE = 4;

	private int countThreads = 2;
	private AtomicInteger runningThreads = new AtomicInteger(0);

	private Exception error = null;



	@Test
	public void testBlocking() throws Exception {
		final IEndpoint acceptorServer = new Endpoint(ACCEPTOR_PAKET_SIZE, new AcceptorHandler());

		for (int i = 0; i < countThreads; i++) {
			Thread t = new Thread() {
				@Override
				public void run() {
					runningThreads.incrementAndGet();
					try {
						clientCall(10, 600, "localhost", acceptorServer.getLocalPort());
					} catch (Exception e) {
						error = e;
					}
					runningThreads.decrementAndGet();
				}
			};

			t.start();
		}

		do {
			QAUtil.sleep(500);
		} while (runningThreads.get() > 0);


		if (error != null) {
			Assert.fail("error occured: " + error.toString());
		}

		acceptorServer.close();
	}


	private void clientCall(int callsCount, int exhangeDatasize, String acceptorAddress, int acceptorPort) throws Exception {
		DatagramSocket clientEndpoint = new DatagramSocket();

		ByteBuffer requestBB = ByteBuffer.allocate(ACCEPTOR_PAKET_SIZE);
		requestBB.putInt(exhangeDatasize);
		byte[] requestData = requestBB.array();

		DatagramPacket request = new DatagramPacket(requestData, requestData.length, new InetSocketAddress(acceptorAddress, acceptorPort));
		clientEndpoint.send(request);

		DatagramPacket serverAccept = new DatagramPacket(new byte[ACCEPTOR_PAKET_SIZE], ACCEPTOR_PAKET_SIZE);
		clientEndpoint.receive(serverAccept);
		int serverHandlerPort = ByteBuffer.wrap(serverAccept.getData()).getInt();
		InetAddress serverHandlerAddress = serverAccept.getAddress();

		for (int i = 0; i < callsCount; i++) {
			byte[] dataRequestBytes = QAUtil.generateByteArray(exhangeDatasize);
			DatagramPacket dataRequest = new DatagramPacket(dataRequestBytes, dataRequestBytes.length, serverHandlerAddress, serverHandlerPort);
			clientEndpoint.send(dataRequest);

			byte[] dataResponseBytes = new byte[dataRequestBytes.length];
			DatagramPacket dataResponse = new DatagramPacket(dataResponseBytes, dataRequestBytes.length);
			clientEndpoint.receive(dataResponse);

			Assert.assertTrue(QAUtil.isEquals(dataResponseBytes, dataResponseBytes));
		}


		clientEndpoint.close();
	}



	private static final class AcceptorHandler implements IDatagramHandler {

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			UserDatagram packet = localEndpoint.receive();
			int exhangeDataSize = packet.readInt();

			WorkerHandler handler = new WorkerHandler();
			IConnectedEndpoint workerEndpoint = new ConnectedEndpoint(packet.getRemoteSocketAddress(), exhangeDataSize, handler);

			int port = workerEndpoint.getLocalPort();

			ByteBuffer buffer = ByteBuffer.allocate(ACCEPTOR_PAKET_SIZE);
			buffer.putInt(port);
			buffer.clear();

			UserDatagram response = new UserDatagram(packet.getRemoteSocketAddress(), buffer);
			localEndpoint.send(response);

			return true;
		}
	}


	private static final class WorkerHandler implements IDatagramHandler {

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			UserDatagram packet = localEndpoint.receive();
			int exchangeDataSize = packet.getSize();
			UserDatagram response = new UserDatagram(exchangeDataSize);
			localEndpoint.send(response);
			return true;
		}
	}
}
