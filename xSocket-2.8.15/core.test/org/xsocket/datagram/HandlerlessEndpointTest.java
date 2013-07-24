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


import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;




import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;



/**
*
* @author grro@xsocket.org
*/
public final class HandlerlessEndpointTest {

	private int packageSize = 655;



	@Test
	public void testNonConnectedEndpointBothSites() throws Exception {

		IEndpoint e1 = new Endpoint(0);
		e1.setReceiveSize(packageSize);

		IEndpoint e2 = new Endpoint();
		e2.setReceiveSize(packageSize - 10);


		for (int i = 0; i < 10; i++) {
			// data e2 -> e1
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram(new InetSocketAddress("localhost", e1.getLocalPort()), data);

			e2.send(packet);

			UserDatagram received = e1.receive(1000);
			Assert.assertTrue("reveived datagram is null", received != null);

			Assert.assertTrue(received.getRemotePort() == e2.getLocalPort());
			Assert.assertTrue(QAUtil.isEquals(data, received.readBytes()));


			// data e1 -> e2
			byte[] data2 = QAUtil.generateByteArray(packageSize - 10);
			UserDatagram packet2 = new UserDatagram(new InetSocketAddress("localhost", e2.getLocalPort()), data2);

			e1.send(packet2);

			UserDatagram received2 = e2.receive(1000);
			Assert.assertTrue(received2.getRemotePort() == e1.getLocalPort());
			Assert.assertTrue(QAUtil.isEquals(data2, received2.readBytes()));
		}

		e1.close();
		e2.close();
	}




	@Test
	public void testMixedEndpoint() throws Exception {

		IEndpoint e = new Endpoint(packageSize);
		ConnectedEndpoint ce = new ConnectedEndpoint(new InetSocketAddress("localhost", e.getLocalPort()));
		ce.setReceiveSize(packageSize - 10);

		Assert.assertTrue(ce.getRemoteSocketAddress().equals(new InetSocketAddress("localhost", e.getLocalPort())));

		for (int i = 0; i < 10; i++) {
			// data ce -> e
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram(data);

			ce.send(packet);
			UserDatagram received = e.receive(1000);
			Assert.assertTrue("reveived datagram is null", received != null);

			Assert.assertTrue(received.getRemotePort() == ce.getLocalPort());
			Assert.assertTrue(QAUtil.isEquals(data, received.readBytes()));


			// data e -> ce
			byte[] data2 = QAUtil.generateByteArray(packageSize - 10);
			UserDatagram packet2 = new UserDatagram(new InetSocketAddress("localhost", ce.getLocalPort()), data2);

			e.send(packet2);

			UserDatagram received2 = ce.receive(1000);
			Assert.assertTrue(received2.getRemotePort() == e.getLocalPort());
			Assert.assertTrue(QAUtil.isEquals(data2, received2.readBytes()));
		}

		e.close();
		ce.close();
	}



	@Test
	public void testMixedEndpointWrongReponse() throws Exception {

		IEndpoint e = new Endpoint(packageSize);
		ConnectedEndpoint ce = new ConnectedEndpoint(new InetSocketAddress("localhost", e.getLocalPort()), packageSize - 10);

		IEndpoint eWrong = new Endpoint();


		for (int i = 0; i < 5; i++) {
			// data ce -> e
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram(new InetSocketAddress("localhost", eWrong.getLocalPort()), data);  // adress will be overriden by calling send

			ce.send(packet);
			UserDatagram received = e.receive(1000);
			Assert.assertTrue("reveived datagram is null", received != null);

			Assert.assertTrue(received.getRemotePort() == ce.getLocalPort());
			Assert.assertTrue(QAUtil.isEquals(data, received.readBytes()));


			// data e2! -> ce
			byte[] data2 = QAUtil.generateByteArray(packageSize - 10);
			UserDatagram packet2 = new UserDatagram(new InetSocketAddress("localhost", ce.getLocalPort()), data2);

			eWrong.send(packet2);

			try {
				ce.receive(150);
				Assert.fail("time out exception expected");
			} catch (SocketTimeoutException expected) { }
		}

		e.close();
		ce.close();
	}



	@Test
	public void testMixedEndpointIndividualReceiveTimeout() throws Exception {

		IEndpoint e = new Endpoint(packageSize);
		ConnectedEndpoint ce = new ConnectedEndpoint(new InetSocketAddress("localhost", e.getLocalPort()), packageSize);

		for (int i = 0; i < 5; i++) {
			// data ce -> e
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram(data);

			ce.send(packet);
			try {
				ce.receive(100);
				Assert.fail("time out excpected");
			} catch (SocketTimeoutException expected) {
				Assert.assertTrue(expected.getMessage().equals("timeout 100 millis reached"));
			}
		}

		e.close();
		ce.close();
	}
}
