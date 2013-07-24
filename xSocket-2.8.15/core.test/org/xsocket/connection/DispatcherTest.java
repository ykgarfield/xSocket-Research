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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.ConnectionUtils;




/**
*
* @author grro@xsocket.org
*/
public final class DispatcherTest {


	private final List<String> errors = new ArrayList<String>();
	private AtomicInteger running = new AtomicInteger(0);


	@Test
	public void testThreaded() throws Exception {

		final ExecutorService executor = Executors.newCachedThreadPool();


		CallbackBaseFactory factory = new CallbackBaseFactory() {

			public CallbackBase create() {
				CallbackBase callback = new CallbackBase() {

					public void onData(final ByteBuffer[] data, int size) {

						Runnable task = new Runnable() {

							public void run()  {
								try {
									getHandler().addToWriteQueue(data);
									getHandler().write();
									getHandler().flush();
								} catch (IOException ioe) {
									ioe.printStackTrace();
								}
							}
						};
						executor.execute(task);

					}

				};

				return callback;
			}
		};

		Server server = new Server(factory);
		Thread t = new Thread(server);
		t.setDaemon(true);
		t.start();

		QAUtil.sleep(2000);

		runClient(server.getPort(), 500, 10);

		server.terminate();
	}


	@Test
	public void testNonThreaded() throws Exception {

		CallbackBaseFactory factory = new CallbackBaseFactory() {

			public CallbackBase create() {
				CallbackBase callback = new CallbackBase() {

					public void onData(final ByteBuffer[] data, int size) {
						try {
							getHandler().addToWriteQueue(data);
							getHandler().write();
							getHandler().flush();
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}
				};


				return callback;
			}
		};


		Server server = new Server(factory);
		Thread t = new Thread(server);
		t.setDaemon(true);
		t.start();

		QAUtil.sleep(2000);


		runClient(server.getPort(), 500, 10);


		server.terminate();
	}

	@Test
	public void testNonThreadedSuspendAndResume() throws Exception {

		CallbackBaseFactory factory = new CallbackBaseFactory() {

			public CallbackBase create() {
				CallbackBase callback = new CallbackBase() {

					public void onData(final ByteBuffer[] data, int size) {

						try {
							getHandler().suspendRead();
							getHandler().addToWriteQueue(data);
							getHandler().write();
							getHandler().flush();
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}


					public void onWritten(ByteBuffer data) {
						try {
							getHandler().resumeRead();
							System.out.print(".");
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}
				};


				return callback;
			}
		};


		Server server = new Server(factory);
		Thread t = new Thread(server);
		t.setDaemon(true);
		t.start();

		QAUtil.sleep(500);


		runClient(server.getPort(), 50, 10);


		server.terminate();
	}




	private void runClient(final int port, final int loops, int countThreads) {

		for (int t = 0; t < countThreads; t++) {

			Thread th = new Thread() {

				public void run() {
					running.incrementAndGet();

					try {
						IBlockingConnection con = new BlockingConnection("localhost", port);

						for (int i = 0; i < loops; i++) {
							con.write("test\r\n");
							String resp = con.readStringByDelimiter("\r\n");

							Assert.assertEquals("test", resp);
						}

						con.close();
					} catch (Exception e) {
						System.out.println("error occured " + e.toString());
						errors.add(e.toString());
					}

					running.decrementAndGet();
				}
			};
			th.setDaemon(true);
			th.start();
		}

		do {
			QAUtil.sleep(100);
		} while (running.get() > 0);

	}


	private static interface CallbackBaseFactory {

		CallbackBase create();
	}


	private static class CallbackBase implements IIoHandlerCallback {

		private IoChainableHandler hdl = null;

		void init(IoChainableHandler hdl) {
			this.hdl = hdl;
		}

		IoChainableHandler getHandler() {
			return hdl;
		}

		public void onConnect() {
//			System.out.println("on connected");

		}

		public void onConnectException(IOException ioe) {
		    
		}
		
		public void onConnectionAbnormalTerminated() {
//			System.out.println("onConnectionAbnormalTerminated");
		}

		public void onData(ByteBuffer[] data, int size) {
//			System.out.println("onData");
		}
		
		public void onPostData() {
		}

		public void onDisconnect() {
//			System.out.println("ondisconnected");
		}

		public void onWriteException(IOException ioException, ByteBuffer data) {
//			System.out.println("onWriteException");
		}

		public void onWritten(ByteBuffer data) {
//			System.out.println("onWritten");
		}

	}



	private static final class Server implements Runnable {

		private boolean isTerminated = false;
		private boolean isOpen = true;
		private ServerSocketChannel serverChannel = null;


		private CallbackBaseFactory callbackFactory = null;


		public Server(CallbackBaseFactory callbackFactory) {
			this.callbackFactory = callbackFactory;
		}

		public void run() {

			try {

				IoSocketDispatcher dispatcher = new IoSocketDispatcher(IoUnsynchronizedMemoryManager.createPreallocatedMemoryManager(65536, 64, true), "test");
				Thread t = new Thread(dispatcher);
				t.setDaemon(false);
				t.start();

				serverChannel = ServerSocketChannel.open();
				serverChannel.configureBlocking(true);
				serverChannel.socket().setSoTimeout(0);
				serverChannel.socket().setReuseAddress(true);
		        serverChannel.socket().bind(new InetSocketAddress(0), 0);

		        while(isOpen) {
		        	SocketChannel channel = serverChannel.accept();
		        	IoChainableHandler hdl = ConnectionUtils.getIoProvider().createIoHandler(false, dispatcher, channel, null, false);

		        	CallbackBase callback = callbackFactory.create();
		        	callback.init(hdl);
		        	hdl.init(callback);
		        }

			} catch (Exception e) {
				if (!isTerminated) {
					e.printStackTrace();
				}
			}
		}


		int getPort() {
			return serverChannel.socket().getLocalPort();
		}

		public void terminate() throws IOException {
			isTerminated = true;
			serverChannel.close();
			isOpen = false;
		}
	}

}
