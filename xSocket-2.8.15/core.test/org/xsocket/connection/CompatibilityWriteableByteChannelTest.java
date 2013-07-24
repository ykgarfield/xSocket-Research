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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xsocket.org
*/
public final class CompatibilityWriteableByteChannelTest {


	@Test
	public void testNonBlockingWriteClientClose() throws Exception {

		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		ConnectionUtils.start(server);


		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());

		QAUtil.sleep(500);
		INonBlockingConnection serverCon = srvHdl.getConection();


		ByteBuffer buffer = QAUtil.generateByteBuffer(40);
		int written = clientCon.write(buffer);
		Assert.assertEquals(40, written);

		QAUtil.sleep(1000);
		Assert.assertEquals(40, serverCon.available());

		clientCon.close();

		buffer = QAUtil.generateByteBuffer(40);
		try {
			written = clientCon.write(buffer);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		server.close();
	}


	@Test
	public void testNonBlockingWriteServerConnectionClose() throws Exception {

		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		ConnectionUtils.start(server);


		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());

		QAUtil.sleep(200);
		INonBlockingConnection serverCon = srvHdl.getConection();


		ByteBuffer buffer = QAUtil.generateByteBuffer(40);
		int written = clientCon.write(buffer);
		Assert.assertEquals(40, written);

		QAUtil.sleep(1000);
		Assert.assertEquals(40, serverCon.available());

		serverCon.close();

		QAUtil.sleep(1000);
		buffer = QAUtil.generateByteBuffer(40);
		try {
			written = clientCon.write(buffer);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		clientCon.close();
		server.close();
	}


	@Test
	public void testNonBlockingWriteServerConClose() throws Exception {

		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		ConnectionUtils.start(server);


		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());

		QAUtil.sleep(200);
		INonBlockingConnection serverCon = srvHdl.getConection();


		ByteBuffer buffer = QAUtil.generateByteBuffer(40);
		int written = clientCon.write(buffer);
		Assert.assertEquals(40, written);

		QAUtil.sleep(1000);
		Assert.assertEquals(40, serverCon.available());

		server.close();

		QAUtil.sleep(300);

		Assert.assertFalse(clientCon.isOpen());
		
		buffer = QAUtil.generateByteBuffer(40);
		try {
			written = clientCon.write(buffer);
			Assert.fail("ClosedChannelException expected");
		} catch (ClosedChannelException expected) { }

		clientCon.close();
	}


	@Test
	public void testTransferFrom() throws Exception {
		ServerHandler srvHdl = new ServerHandler();
		IServer server = new Server(srvHdl);
		ConnectionUtils.start(server);


		INonBlockingConnection clientCon = new NonBlockingConnection("localhost", server.getLocalPort());

		QAUtil.sleep(200);
		INonBlockingConnection serverCon = srvHdl.getConection();

		clientCon.setFlushmode(FlushMode.ASYNC);

		File file = QAUtil.createTestfile_40k();
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		FileChannel fc = raf.getChannel();
		clientCon.transferFrom(fc);

		QAUtil.sleep(500);
		ByteBuffer[] bufs = serverCon.readByteBufferByLength(serverCon.available());
		Assert.assertTrue(QAUtil.isEquals(file, bufs));

		
		raf.close();
		file.delete();
		clientCon.close();
		server.close();
	}



	private static final class ServerHandler implements IConnectHandler {

		private INonBlockingConnection connection = null;

		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			this.connection = connection;
			return true;
		}

		INonBlockingConnection getConection() {
			return connection;
		}
	}
}
