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
import java.nio.channels.FileChannel;


import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class SimpleFileChannelPerformanceTest  {

	private static File file;

	@BeforeClass
	public static void setUp() {
		file = QAUtil.createTestfile_400k();
	}

	
	@AfterClass
	public static void tearDown() {
		file.delete();
	}



	@Test
	public void testTransferConnectionToFileChannel() throws Exception {

		long length = file.length();

		IServer server1 = new Server(new UploadHandler1());
		ConnectionUtils.start(server1);

		IServer server2 = new Server(new UploadHandler2());
		ConnectionUtils.start(server2);


		IBlockingConnection con1 = new BlockingConnection("localhost", server1.getLocalPort());
		IBlockingConnection con2 = new BlockingConnection("localhost", server2.getLocalPort());


		// warm up
		load(con1, length, 50);
		QAUtil.sleep(500);
		System.gc();

		QAUtil.sleep(1000);
		
		load(con2, length, 50);

		QAUtil.sleep(500);
		System.gc();
		QAUtil.sleep(1000);

		long elapsed1 = load(con1, length, 100);
		
		QAUtil.sleep(500);
		System.gc();
		QAUtil.sleep(1000);

		long elapsed2 = load(con2, length, 100);


		int p = (int) (elapsed2 * 100 / elapsed1);
		System.out.println("elapsed fc controlled " + elapsed1 + " millis, elapsed xSocket connection controlled " +
				           elapsed2 + " millis (" + p +"%)");


		if (elapsed2 > (elapsed1 * 110)) {
			String msg = "connection.transferFrom(fc) should be faster than fc.transferTo(0, fc.size(), connection)";
			System.out.println(msg);
			Assert.fail(msg);
		}

		
		System.gc();

		con1.close();
		con2.close();

		server1.close();
		server2.close();

	}


	private long load(IBlockingConnection con, long length, int loops) throws IOException {
		long elapsed = 0;

		for (int i = 0; i < loops; i++) {
			long start = System.currentTimeMillis();
			con.write("load\r\n");
			ByteBuffer[] buffers = con.readByteBufferByLength((int) length);
			elapsed += System.currentTimeMillis() - start;
		}


		return elapsed;
	}




	private static final class UploadHandler1 implements IDataHandler {


		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			RandomAccessFile raf = new RandomAccessFile(file, "r");
			FileChannel fc = raf.getChannel();
			fc.transferTo(0, fc.size(), connection);
			fc.close();
			raf.close();

			return true;
		}
	}


	private static final class UploadHandler2 implements IDataHandler {


		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			RandomAccessFile raf = new RandomAccessFile(file, "r");
			FileChannel fc = raf.getChannel();
			connection.transferFrom(fc);
			fc.close();
			raf.close();

			return true;
		}

	}
}
