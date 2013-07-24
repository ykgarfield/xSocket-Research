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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;


import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;



/**
*
* @author grro@xsocket.org
*/
public final class DatagramReadWriteDataTest {

	private static final int SIZE = 600;



	@Test
	public void testDataTypes() throws Exception {
		
		System.out.println("testDataTypes");
		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new Endpoint(SIZE, hdl);
		
		IConnectedEndpoint clientEndpoint = new ConnectedEndpoint(new InetSocketAddress("localhost", serverEndpoint.getLocalPort()), SIZE);

		UserDatagram packet = new UserDatagram(SIZE);


		System.out.println("perform packet write ");
		packet.write((byte) 56);
		packet.write(45.9);
		packet.write(45);
		packet.write(446456456555L);
		byte[] data = QAUtil.generateByteArray(10);
		packet.write(data);
		byte[] data2 = QAUtil.generateByteArray(17);
		ByteBuffer buffer2 = ByteBuffer.wrap(data2);
		packet.write(buffer2);
		buffer2.flip();
		packet.write(new ByteBuffer[] {buffer2});
		buffer2.flip();
		packet.write("hello datagram\r\n");
		packet.write(34);
		packet.write("\r\n");
		packet.write(4);
		packet.write("hello endpoint");
		byte[] data3 = QAUtil.generateByteArray(4);
		packet.write(data3);
		packet.write("this is the end");

		System.out.println("send packet");
		clientEndpoint.send(packet);
		
		System.out.println("wait for response");
		UserDatagram serverSidePacket = clientEndpoint.receive(3000);
		
		if (serverSidePacket == null) {
			String txt = "server has not responed (receive timeout)";
			System.out.println(txt);
			Assert.fail(txt);
		}
		
		Assert.assertTrue(serverSidePacket.readByte() == ((byte) 56));
		Assert.assertTrue(serverSidePacket.readDouble() == 45.9);
		Assert.assertTrue(serverSidePacket.readInt() == 45);
		Assert.assertTrue(serverSidePacket.readLong() == 446456456555L);

		Assert.assertTrue(QAUtil.isEquals(serverSidePacket.readBytesByLength(data.length), data));
		Assert.assertTrue(QAUtil.isEquals(serverSidePacket.readSingleByteBufferByLength(data2.length), buffer2));
		Assert.assertTrue(QAUtil.isEquals(serverSidePacket.readSingleByteBufferByLength(data2.length), buffer2));
		Assert.assertTrue(serverSidePacket.readStringByDelimiter("\r\n").equals("hello datagram"));
		Assert.assertTrue(serverSidePacket.readInt() == 34);
		Assert.assertTrue(serverSidePacket.readStringByDelimiter("\r\n").equals(""));
		Assert.assertTrue(serverSidePacket.readInt() == 4);
		Assert.assertTrue(serverSidePacket.readStringByLength(14).equals("hello endpoint"));
		Assert.assertTrue(QAUtil.isEquals(serverSidePacket.readBytesByLength(4), data3));
		try {
			serverSidePacket.readStringByDelimiter("\r\n");
			Assert.fail("BufferUnderflowException should haven been occured");
		} catch (BufferUnderflowException expected) { }

		clientEndpoint.close();
		serverEndpoint.close();
	}



	@Test
	public void testString() throws Exception {
		
		System.out.println("testDataTypes");
		
		Handler hdl = new Handler();
		IEndpoint serverEndpoint = new Endpoint(SIZE, hdl);
		IEndpoint clientEndpoint = new Endpoint(SIZE);

		String msg = "hello";
		String msg2 = "hello2";

		System.out.println("create endpoint");

		UserDatagram packet = new UserDatagram("localhost", serverEndpoint.getLocalPort(), msg.getBytes().length + msg2.getBytes().length);

		System.out.println("perform packet write ");
		packet.write(msg);
		packet.write(msg2);

		System.out.println("send packet");
		clientEndpoint.send(packet);
		
		System.out.println("wait for response");
		UserDatagram serverSidePacket = clientEndpoint.receive(3000);

		if (serverSidePacket == null) {
			String txt = "server has not responed (receive timeout)";
			System.out.println(txt);
			Assert.fail(txt);
		}
		
		Assert.assertTrue(serverSidePacket.readStringByLength(msg.getBytes().length).equals("hello"));
		Assert.assertTrue(serverSidePacket.readString().equals(msg2));

		clientEndpoint.close();
		serverEndpoint.close();
	}





	private static final class Handler implements IDatagramHandler {

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			UserDatagram request = localEndpoint.receive();
			
			UserDatagram response = new UserDatagram(request.getRemoteAddress(), request.getRemotePort(), request.getSize());
			ByteBuffer[] data = request.readByteBufferByLength(request.getSize());
			response.write(data);
			
			localEndpoint.send(response);
			return true;
		}
	}
}
