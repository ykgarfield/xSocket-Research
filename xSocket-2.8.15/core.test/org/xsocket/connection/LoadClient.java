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

import java.nio.ByteBuffer;


import org.junit.Assert;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;



/**
*
* @author grro@xsocket.org
*/
public final class LoadClient {

	
	
	public static void main(String... args) throws Exception {
		if (args.length != 4) {
			System.out.println("usage org.xsocket.stream.LoadClient <hostname> <port> <numWorkers> <packetSize>");
			System.exit(-1);
		}

		new LoadClient(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[2]));
	}


	LoadClient(final String hostname, final int port, int numWorkers, final int dataSize) throws Exception {

		for (int i = 0; i < numWorkers; i++) {
			
			Thread t = new Thread() {
				@Override
				public void run() {
					try {
						
						IBlockingConnection con = new BlockingConnection(hostname, port);
						con.setAutoflush(false);
						ByteBuffer data = QAUtil.generateByteBuffer(dataSize);
						
						while (true) {
							con.write(data);
							con.flush();
							data.rewind();
							
							ByteBuffer[] response = con.readByteBufferByLength(dataSize);
							if (!QAUtil.isEquals(new ByteBuffer[] { data }, response)) {
								System.out.print("E");
							} else {
								System.out.print(".");
							}
						}
						
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
			t.start();
		}
		
	}

}
