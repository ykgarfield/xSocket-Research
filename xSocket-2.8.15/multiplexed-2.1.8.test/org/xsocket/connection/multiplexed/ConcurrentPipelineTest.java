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
import java.util.ArrayList;
import java.util.List;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.multiplexed.IBlockingPipeline;
import org.xsocket.connection.multiplexed.IMultiplexedConnection;
import org.xsocket.connection.multiplexed.INonBlockingPipeline;
import org.xsocket.connection.multiplexed.IPipelineConnectHandler;
import org.xsocket.connection.multiplexed.IPipelineDataHandler;
import org.xsocket.connection.multiplexed.MultiplexedConnection;
import org.xsocket.connection.multiplexed.MultiplexedProtocolAdapter;




/**
*
* @author grro@xsocket.org
*/
public final class ConcurrentPipelineTest {

	private static final String DELIMITER = "\r\n";

	private List<String> errors = new ArrayList<String>();
	private int running = 0;


	private static final int LOOPS = 100;
	private static final int WORKERS = 3;
	private static final int DATA_SIZE = 5000;


	@Test
	public void testSimple() throws Exception {
		IServer server = new Server(new MultiplexedProtocolAdapter(new MultiplexedConnectionHandler()));
		ConnectionUtils.start(server);

		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));


		for (int i = 0; i < WORKERS; i++) {
			Thread t = new Thread(new PipelineSender(connection));
			t.start();
		}

		do {
			QAUtil.sleep(1050);
		} while (running > 0);


		connection.close();
		server.close();

		Assert.assertTrue(errors.isEmpty());
	}


	private static final class MultiplexedConnectionHandler implements IPipelineConnectHandler, IPipelineDataHandler {


		public boolean onConnect(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			return true;
		}


		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			byte[] data = pipeline.readBytesByDelimiter(DELIMITER);
			pipeline.write(data);
			pipeline.write(DELIMITER);
			pipeline.flush();

			return true;
		}
	}



	private final class PipelineSender implements Runnable {

		private IBlockingPipeline pipeline = null;

		public PipelineSender(IMultiplexedConnection connection) throws IOException {
			String pipelineId = connection.createPipeline();
			this.pipeline = connection.getBlockingPipeline(pipelineId);
		}


		public void run() {

			running++;


			pipeline.setAutoflush(false);
			byte[] testData = QAUtil.generateByteArray(DATA_SIZE);

			try {
				for (int i = 0; i < LOOPS; i++) {
					pipeline.write(testData);
					pipeline.write(DELIMITER);
					pipeline.flush();

					byte[] result = pipeline.readBytesByDelimiter(DELIMITER);

					if (!QAUtil.isEquals(testData, result)) {
						errors.add("response is not equals request");
					} else {
						System.out.print(".");
					}
				}

				pipeline.close();

			} catch (Exception e) {
				errors.add(e.toString());
			}

			running--;
		}
	}
}