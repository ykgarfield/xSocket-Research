/*
 *  Copyright (c) xsocket.org, 2006 - 2010. All rights reserved.
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
package org.xsocket.connection.multiplexed;




import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.multiplexed.IMultiplexedConnection;
import org.xsocket.connection.multiplexed.INonBlockingPipeline;
import org.xsocket.connection.multiplexed.MultiplexedConnection;
import org.xsocket.connection.multiplexed.MultiplexedProtocolAdapter;




/**
*
* @author grro@xsocket.org
*/
public final class MarkTest {



	@Test
	public void testSimple() throws Exception {
		IServer server = new Server(new MultiplexedProtocolAdapter(new MyEchoHandler()));
		ConnectionUtils.start(server);

		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));

		String pipelineId = connection.createPipeline();
		INonBlockingPipeline pipeline = connection.getNonBlockingPipeline(pipelineId);
		pipeline.setAutoflush(false);

		pipeline.markWritePosition();
		pipeline.write((int) 0);  // write length field
		int written = pipeline.write("test");
		pipeline.resetToWriteMark();
		pipeline.write(written);
		pipeline.flush();

		QAUtil.sleep(400);


		int length = pipeline.readInt();
		String data = pipeline.readStringByLength(length);

		Assert.assertEquals("test", data);

		pipeline.close();

		connection.close();
		server.close();
	}



	private static final class MyEchoHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}

	}
}