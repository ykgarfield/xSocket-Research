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
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;



/**
*
* @author grro@xsocket.org
*/
public final class DatagramOverUnderflowTest {

	private static final int SIZE = 10;



	@Test
	public void testPacketWriteOverflow() throws Exception {
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new Endpoint(SIZE, hdl);
		IConnectedEndpoint clientEndpoint = new ConnectedEndpoint(new InetSocketAddress("localhost", serverEndpoint.getLocalPort()), SIZE);

		UserDatagram packet = new UserDatagram(SIZE);
		Assert.assertTrue(packet.getSize() == SIZE);
		Assert.assertTrue(packet.getRemaining() == SIZE);
		Assert.assertTrue(packet.getRemoteAddress() == null);
		Assert.assertTrue(packet.getRemoteSocketAddress() == null);

		packet.write((int) 4);
		Assert.assertTrue(packet.getSize() == SIZE);
		Assert.assertTrue(packet.getRemaining() == (SIZE - 4));

		packet.write((int) 5);
		Assert.assertTrue(packet.getSize() == SIZE);
		Assert.assertTrue(packet.getRemaining() == (SIZE - 8));



		try {
			packet.write((int) 7);
			Assert.fail("BufferOverflow should have been occured");
		} catch (BufferOverflowException shouldOccur) { }


		clientEndpoint.send(packet);

		QAUtil.sleep(500);

		Assert.assertTrue(hdl.lastReceived.getSize() == SIZE);
		Assert.assertTrue(hdl.lastReceived.getRemaining() == SIZE);

		clientEndpoint.close();
		serverEndpoint.close();
	}




	@Test
	public void testPacketPreSetOverflow() throws Exception {
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new Endpoint(SIZE, hdl);
		IConnectedEndpoint clientEndpoint = new ConnectedEndpoint(new InetSocketAddress("localhost", serverEndpoint.getLocalPort()), SIZE);


		byte[] data = QAUtil.generateByteArray(SIZE);
		UserDatagram packet = new UserDatagram(data);
		Assert.assertTrue(packet.getSize() == SIZE);
		Assert.assertTrue(packet.getRemaining() == 0);
		Assert.assertTrue(packet.getRemoteAddress() == null);
		Assert.assertTrue(packet.getRemoteSocketAddress() == null);


		try {
			packet.write((int) 7);
			Assert.fail("BufferOverflow should have been occured");
		} catch (BufferOverflowException shouldOccur) { }


		clientEndpoint.send(packet);

		QAUtil.sleep(500);

		Assert.assertTrue(hdl.lastReceived.getSize() == SIZE);
		Assert.assertTrue(hdl.lastReceived.getRemaining() == SIZE);

		byte[] response = hdl.lastReceived.readBytes();
		Assert.assertTrue(hdl.lastReceived.getSize() == SIZE);
		Assert.assertTrue(hdl.lastReceived.getRemaining() == 0);

		Assert.assertTrue(QAUtil.isEquals(data, response));



		clientEndpoint.close();
		serverEndpoint.close();
	}



	private final class Handler implements IDatagramHandler {

		private UserDatagram lastReceived = null;

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			lastReceived = localEndpoint.receive();

			return true;
		}
	}
}
