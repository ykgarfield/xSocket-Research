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
import java.net.InetSocketAddress;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class EndpointTest {

	private int packageSize = 655;


	@Test
	public void testNonConnectedEndpointDefaultWorkerSize() throws Exception {
		IEndpoint serverEndpoint = new Endpoint(packageSize, new EchoHandler());

		DatagramSocket clientSocket = new DatagramSocket();

		for (int i = 0; i < 10; i++) {
			byte[] request = QAUtil.generateByteArray(packageSize);
			DatagramPacket requestPackage = new DatagramPacket(request, packageSize, new InetSocketAddress("localhost", serverEndpoint.getLocalPort()));

			clientSocket.send(requestPackage);

			DatagramPacket responsePackage = new DatagramPacket(new byte[packageSize], packageSize);
			clientSocket.receive(responsePackage);
			byte[] response = responsePackage.getData();

			Assert.assertTrue(QAUtil.isEquals(request, response));
		}

		clientSocket.close();
		serverEndpoint.close();
	}


	@Test
	public void testNonConnectedEndpointDedicatedWorkerSize() throws Exception {

		IEndpoint serverEndpoint = new Endpoint(packageSize, new EchoHandler());

		DatagramSocket clientSocket = new DatagramSocket();

		for (int i = 0; i < 10; i++) {
			byte[] request = QAUtil.generateByteArray(packageSize);
			DatagramPacket requestPackage = new DatagramPacket(request, packageSize, new InetSocketAddress("localhost", serverEndpoint.getLocalPort()));

			clientSocket.send(requestPackage);

			DatagramPacket responsePackage = new DatagramPacket(new byte[packageSize], packageSize);
			clientSocket.receive(responsePackage);
			byte[] response = responsePackage.getData();

			Assert.assertTrue(QAUtil.isEquals(request, response));
		}

		clientSocket.close();
		serverEndpoint.close();
	}




	@Test
	public void testNonConnectedEndpointClientMode() throws Exception {

		EchoHandler handler = new EchoHandler();
		IEndpoint serverEndpoint = new Endpoint(packageSize, handler);

		IEndpoint clientEndpoint = new Endpoint(packageSize);

		for (int i = 0; i < 5; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram("localhost", serverEndpoint.getLocalPort(), packageSize);
			packet.write(new String(data));

			clientEndpoint.send(packet);

			QAUtil.sleep(300);

			Assert.assertTrue(QAUtil.isEquals(data, handler.lastReceived));
		}

		clientEndpoint.close();
		serverEndpoint.close();
	}



	@Test
	public void testConnectedEndpoint() throws Exception {

		EchoHandler handler = new EchoHandler();
		IEndpoint serverEndpoint = new Endpoint(packageSize, handler);

		IConnectedEndpoint clientEndpoint = new ConnectedEndpoint("localhost", serverEndpoint.getLocalPort(), packageSize);

		for (int i = 0; i < 5; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram(data);

			clientEndpoint.send(packet);

			QAUtil.sleep(300);

			Assert.assertTrue(QAUtil.isEquals(data, handler.lastReceived));
		}

		clientEndpoint.close();
		serverEndpoint.close();
	}


	@Test
	public void testClientNonConnectedEndpoint() throws Exception {
		EchoHandler handler = new EchoHandler();
		IEndpoint serverEndpoint = new Endpoint(packageSize, handler);

		IEndpoint clientEndpoint = new Endpoint(packageSize);

		for (int i = 0; i < 10; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram(packageSize);
			packet.write(data);
			packet.setRemoteAddress(new InetSocketAddress("localhost", serverEndpoint.getLocalPort()));

			clientEndpoint.send(packet);
			UserDatagram response = clientEndpoint.receive(1000);

			Assert.assertTrue(QAUtil.isEquals(data, response.readBytes()));
		}

		clientEndpoint.close();
		serverEndpoint.close();
	}


	@Test
	public void testMulticastEndpoint() throws Exception {

		IEndpoint mcEndpoint1 = new MulticastEndpoint("233.128.0.195", 4433, packageSize, new ConsumerHandler());

		ConsumerHandler handler = new ConsumerHandler();
		IEndpoint mcEndpoint2 = new MulticastEndpoint("233.128.0.195", 4433, packageSize, handler);

		for (int i = 0; i < 5; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram(data);

			mcEndpoint1.send(packet);

			QAUtil.sleep(500);

			Assert.assertTrue(QAUtil.isEquals(data, handler.lastReceived.readBytes()));
		}

		mcEndpoint1.close();
		mcEndpoint2.close();
	}


	private final class ConsumerHandler implements IDatagramHandler {

		private UserDatagram lastReceived = null;

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			lastReceived = localEndpoint.receive();
			return true;
		}
	}



	private final class EchoHandler implements IDatagramHandler {

		private byte[] lastReceived = null;

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			UserDatagram datagram = localEndpoint.receive();
			lastReceived = datagram.readBytes();


			UserDatagram response = new UserDatagram(datagram.getRemoteSocketAddress(), lastReceived);
			localEndpoint.send(response);
			return true;
		}
	}
}
