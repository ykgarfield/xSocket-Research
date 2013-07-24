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
public final class ReceiveAndSendSizeTest {

	private int packageSize = 655;



	@Test
	public void testMulticastEndpointSmallerSize() throws Exception {

		IEndpoint mcEndpoint1 = new MulticastEndpoint("233.128.0.195", 4433, packageSize, new ConsumerHandler());

		ConsumerHandler handler = new ConsumerHandler();
		IEndpoint mcEndpoint2 = new MulticastEndpoint("233.128.0.195", 4433, packageSize, handler);

		for (int i = 0; i < 3; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize - 10);
			UserDatagram packet = new UserDatagram(data);

			mcEndpoint1.send(packet);

			QAUtil.sleep(1000);


			UserDatagram serverSidePacket = handler.lastReceived;
			Assert.assertTrue(serverSidePacket.getSize() == data.length);

			Assert.assertTrue(QAUtil.isEquals(data, handler.lastReceived.readBytes()));
		}

		mcEndpoint1.close();
		mcEndpoint2.close();
	}


	@Test
	public void testMulticastEndpointLargerSize() throws Exception {

		IEndpoint mcEndpoint1 = new MulticastEndpoint("233.128.0.195", 4433, packageSize, new ConsumerHandler());

		ConsumerHandler handler = new ConsumerHandler();
		IEndpoint mcEndpoint2 = new MulticastEndpoint("233.128.0.195", 4433, packageSize, handler);

		for (int i = 0; i < 3; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize + 10);
			UserDatagram packet = new UserDatagram(data);

			mcEndpoint1.send(packet);

			QAUtil.sleep(1000);


			UserDatagram serverSidePacket = handler.lastReceived;
			Assert.assertTrue(serverSidePacket.getSize() == (packageSize));

			byte[] reducedData = new byte[packageSize];
			System.arraycopy(data, 0, reducedData, 0, reducedData.length);
			Assert.assertTrue(QAUtil.isEquals(reducedData, handler.lastReceived.readBytes()));
		}

		mcEndpoint1.close();
		mcEndpoint2.close();
	}


	@Test
	public void testNonBlockingEndpoint() throws Exception {

		ConsumerHandler handler = new ConsumerHandler();
		IEndpoint serverEndpoint = new Endpoint(packageSize, handler);

		DatagramSocket clientSocket = new DatagramSocket();

		for (int i = 0; i < 3; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize);
			DatagramPacket requestPackage = new DatagramPacket(data, data.length, new InetSocketAddress("localhost", serverEndpoint.getLocalPort()));
			clientSocket.send(requestPackage);

			QAUtil.sleep(1000);


			UserDatagram serverSidePacket = handler.lastReceived;
			Assert.assertTrue(serverSidePacket.getSize() == (packageSize));

			Assert.assertTrue(QAUtil.isEquals(data, handler.lastReceived.readBytes()));
		}

		clientSocket.close();
		serverEndpoint.close();
	}



	@Test
	public void testNonBlockingEndpointSmallerSize() throws Exception {

		ConsumerHandler handler = new ConsumerHandler();
		IEndpoint serverEndpoint = new Endpoint(packageSize, handler);

		IEndpoint clientEndpoint = new Endpoint(packageSize);

		for (int i = 0; i < 3; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize - 6);
			UserDatagram packet = new UserDatagram(new InetSocketAddress("localhost", serverEndpoint.getLocalPort()), data);
			clientEndpoint.send(packet);

			QAUtil.sleep(1000);


			UserDatagram serverSidePacket = handler.lastReceived;
			Assert.assertTrue(serverSidePacket.getSize() == data.length);

			Assert.assertTrue(QAUtil.isEquals(data, handler.lastReceived.readBytes()));
		}

		clientEndpoint.close();
		serverEndpoint.close();
	}



	@Test
	public void testNonBlockingEndpointLargerSize() throws Exception {

		ConsumerHandler handler = new ConsumerHandler();
		IEndpoint serverEndpoint = new Endpoint(packageSize, handler);

		DatagramSocket clientSocket = new DatagramSocket();

		for (int i = 0; i < 3; i++) {
			byte[] data = QAUtil.generateByteArray(packageSize + 10);
			DatagramPacket requestPackage = new DatagramPacket(data, data.length, new InetSocketAddress("localhost", serverEndpoint.getLocalPort()));
			clientSocket.send(requestPackage);

			QAUtil.sleep(1000);


			UserDatagram serverSidePacket = handler.lastReceived;
			Assert.assertTrue(serverSidePacket.getSize() == (packageSize));

			byte[] reducedData = new byte[packageSize];
			System.arraycopy(data, 0, reducedData, 0, reducedData.length);
			Assert.assertTrue(QAUtil.isEquals(reducedData, handler.lastReceived.readBytes()));
		}

		clientSocket.close();
		serverEndpoint.close();
	}


	private final class ConsumerHandler implements IDatagramHandler {

		private UserDatagram lastReceived = null;

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			lastReceived = localEndpoint.receive();
			return true;
		}
	}

}
