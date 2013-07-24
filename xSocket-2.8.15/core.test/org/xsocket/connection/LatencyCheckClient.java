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
public final class LatencyCheckClient {

	
	
	public static void main(String... args) throws Exception {
		System.setProperty(IoProvider.DEFAULT_USE_DIRECT_BUFFER, "true");
		
		if (args.length != 4) {
			System.out.println("usage org.xsocket.stream.LatencyCheckClient <hostname> <port> <packetSize>");
			System.exit(-1);
		}

		new LatencyCheckClient(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
	}


	LatencyCheckClient(String hostname, int port, int dataSize) throws Exception {

		IBlockingConnection con = new BlockingConnection(hostname, port);
		con.setAutoflush(false);
		ByteBuffer data = QAUtil.generateByteBuffer(dataSize);
						
		while (true) {
			long start = System.nanoTime();
			con.write(data);
			con.flush();
			data.rewind();
							
			ByteBuffer[] response = con.readByteBufferByLength(dataSize);
			long end = System.nanoTime();
			double elapsedMillis = ((double) (end - start)) / 1000000; 
			if (!QAUtil.isEquals(new ByteBuffer[] { data }, response)) {
				System.out.println("E");
			} else {
				System.out.println(elapsedMillis  + " millis");
			}
		}
	}

}
