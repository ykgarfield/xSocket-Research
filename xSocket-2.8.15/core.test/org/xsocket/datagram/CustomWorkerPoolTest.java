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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;


/**
*
* @author grro@xsocket.org
*/
public final class CustomWorkerPoolTest {

	private int packageSize = 655;


	@Test
	public void testCustomWorkerPool() throws Exception {

		Handler hdl = new Handler();
		Endpoint eServer = new Endpoint(packageSize, hdl, new WorkerPool());

		IEndpoint eClient = new Endpoint(packageSize);

		int loops = 10;

		for (int i = 0; i < loops; i++) {
			// data e2 -> e1
			byte[] data = QAUtil.generateByteArray(packageSize);
			UserDatagram packet = new UserDatagram(new InetSocketAddress("localhost", eServer.getLocalPort()), data);

			eClient.send(packet);

			QAUtil.sleep(200);
		}

		Assert.assertTrue(((WorkerPool) eServer.getWorkerpool()).created == loops);

		eServer.close();
		eClient.close();
	}


	private static final class WorkerPool implements Executor {

		private int created = 0;

		public void execute(Runnable command) {
			created++;
			Thread t = new Thread(command);
			t.start();
		}

		public <T> List<Future<T>> invokeAll(Collection<Callable<T>> tasks) throws InterruptedException {
			return null;
		}

		public int getActiveCount() {
			return 0;
		}

		public int getMaximumPoolSize() {
			return 0;
		}

		public int getMinimumPoolSize() {
			return 0;
		}

		public int getPoolSize() {
			return 0;
		}

		public boolean isOpen() {
			return true;
		}

		public int getLoad() {
			return 0;
		}

		public void close() {

		}
	}


	private final class Handler implements IDatagramHandler {

		private byte[] lastReceived = null;

		public boolean onDatagram(IEndpoint localEndpoint) throws IOException {
			UserDatagram datagram = localEndpoint.receive(0);
			lastReceived = datagram.readBytes();


			UserDatagram response = new UserDatagram(datagram.getRemoteSocketAddress(), lastReceived);
			localEndpoint.send(response);
			return true;
		}
	}
}
