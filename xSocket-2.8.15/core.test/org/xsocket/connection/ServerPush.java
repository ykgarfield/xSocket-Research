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
import java.util.Timer;
import java.util.TimerTask;

import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class ServerPush  {

	
	public static void main(String... args) throws Exception {
		IServer server = new Server(0, new Handler());
		ConnectionUtils.start(server);

		IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());
		for (int i = 0; i < 3; i++) {
			System.out.println(con.readStringByDelimiter("\r\n"));
		}
		
		con.close();
		server.close();
	}
	
		

	
	private static class Handler implements IConnectHandler {
		
		private final Timer timer = new Timer(true);

		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			
			Notifier notifier = new Notifier(connection);
			timer.schedule(notifier, 500, 500);
			return true;
		}
	}
	
	

	private static final class Notifier extends TimerTask {

		private INonBlockingConnection connection = null;
		
		
		public Notifier(INonBlockingConnection connection) {
			this.connection = connection;
		}
		
		
		@Override
		public void run() {
			try {
				connection.write("pong\r\n");
			} catch (Exception e) {
				System.out.println("error occured by sending pong to " + connection.getRemoteAddress() + ":" + connection.getRemotePort());
			}
		}
	}	
}
