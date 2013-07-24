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

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.DataConverter;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class LargeDataTransferTest  {

	private static final Logger LOG = Logger.getLogger(LargeDataTransferTest.class.getName());

	private static final int LENGTH = 700000;
	private static final String DELIMITER = "\r";


	@Test
	public void testByLength() throws Exception {
		IServer server = new Server(new LengthHandler());
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);

		ByteBuffer request = QAUtil.generateByteBuffer(LENGTH - 1);

		for (int i = 0; i < 10; i++) {
			long start = System.currentTimeMillis();
			connection.write(request);
			connection.write((byte) 143);
			connection.flush();
	
	
			request.flip();
	
			ByteBuffer[] response = connection.readByteBufferByLength(LENGTH -1);
			Assert.assertTrue(DataConverter.toString(request).equals(DataConverter.toString(response)));
	
			Assert.assertEquals((byte) 143, connection.readByte());
	
			long elapsed = System.currentTimeMillis() - start;
	
			request.clear();
			System.out.println(DataConverter.toFormatedBytesSize(LENGTH) + " bytes has been send and returned (elapsed time " + DataConverter.toFormatedDuration(elapsed) + ")");
	
	
			System.gc();
		}

		connection.close();
		server.close();
	}


	@Test
	public void testByDelimiter() throws Exception {
		IServer server = new Server(new DelimiterHandler());
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);

		ByteBuffer request = QAUtil.generateByteBuffer(LENGTH);

		long start = System.currentTimeMillis();
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();

		ByteBuffer[] response = connection.readByteBufferByDelimiter(DELIMITER);
		long elapsed = System.currentTimeMillis() - start;

		request.clear();
		Assert.assertTrue(DataConverter.toString(request).equals(DataConverter.toString(response)));
		System.out.println(DataConverter.toFormatedBytesSize(LENGTH) + " bytes has been send and returned (elapsed time " + DataConverter.toFormatedDuration(elapsed) + ")");


		System.gc();

		server.close();
	}

	
	@Test
	public void testByDelimiterSmallReceivebuffer() throws Exception {
		
		IServer server = new Server(new DelimiterHandler());
		ConnectionUtils.start(server);

		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(false);

		ByteBuffer request = QAUtil.generateByteBuffer(LENGTH);

		long start = System.currentTimeMillis();
		connection.write(request);
		connection.write(DELIMITER);
		connection.flush();

		ByteBuffer[] response = connection.readByteBufferByDelimiter(DELIMITER);
		long elapsed = System.currentTimeMillis() - start;

		request.clear();
		Assert.assertTrue(DataConverter.toString(request).equals(DataConverter.toString(response)));
		System.out.println(DataConverter.toFormatedBytesSize(LENGTH) + " bytes has been send and returned (elapsed time " + DataConverter.toFormatedDuration(elapsed) + ")");


		System.gc();

		server.close();
	}



	private static final class DelimiterHandler implements IConnectHandler, IDataHandler {

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			return true;
		}

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			ByteBuffer[] buffers = connection.readByteBufferByDelimiter(DELIMITER);

			int size = 0;
			for (ByteBuffer buffer : buffers) {
				size += buffer.remaining();
			}

			connection.write(buffers);
			connection.write(DELIMITER);
			connection.flush();
			return true;
		}
	}


	private static final class LengthHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			ByteBuffer[] buffers = connection.readByteBufferByLength(LENGTH);

			int size = 0;
			for (ByteBuffer buffer : buffers) {
				size += buffer.remaining();
			}

			connection.write(buffers);
			return true;
		}
	}
}
