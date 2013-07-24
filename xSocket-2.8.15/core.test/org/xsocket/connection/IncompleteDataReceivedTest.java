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
import java.util.ArrayList;
import java.util.List;


import org.junit.Assert;
import org.junit.Test;

import org.xsocket.QAUtil;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.NonBlockingConnection;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class IncompleteDataReceivedTest  {
	
	private static final String DELIMITER = "\r\n"; 


	@Test 
	public void testDelimiter() throws Exception {
		IServer server = new Server(new ServerHandler());
		server.start();

		
		ClientHandler cltHdl = new ClientHandler(ClientHandler.STANDARD);
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), cltHdl);
		QAUtil.sleep(2000);
		
		Assert.assertEquals(37, cltHdl.getData().length());

		
		ClientHandler cltHdl2 = new ClientHandler(ClientHandler.MARK_AND_RESET);
		INonBlockingConnection connection2 = new NonBlockingConnection("localhost", server.getLocalPort(), cltHdl2);
		QAUtil.sleep(2000);
		
		Assert.assertEquals(37, cltHdl2.getData().length());

		
		
		connection.close();
		connection2.close();
		server.close();
		
	}

	
	
	@Test 
	public void testLengthField() throws Exception {
		IServer server = new Server(new ServerHandlerLengthField());
		server.start();

		
		ClientHandlerLengthField cltHdl = new ClientHandlerLengthField();
		INonBlockingConnection connection = new NonBlockingConnection("localhost", server.getLocalPort(), cltHdl);
		QAUtil.sleep(2000);
		
		Assert.assertEquals(3, cltHdl.getData().size());
		
		
		connection.close();
		server.close();
		
	}

	
	
	
	
	private static final class ClientHandler implements IConnectHandler, IDataHandler {
		
		private StringBuilder sb = new StringBuilder();
		
		private static final int STANDARD = 0;
		private static final int MARK_AND_RESET = 1;
		private static final int MARK_AND_RESET_EXCEPTION = 2;
		private static final int STREAM_UTILS_SUPPORT = 3;
		
		private int mode = STANDARD;
		
		ClientHandler(int mode) {
			this.mode = mode;
		}
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			
			connection.write("GET Data" + DELIMITER);
			connection.flush();
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			if (mode == STANDARD) {
				String line = connection.readStringByDelimiter(DELIMITER);
				sb.append(line + DELIMITER);
				
			} else if (mode == MARK_AND_RESET) {
				connection.resetToReadMark();    // reset former read mark
				connection.markReadPosition();
				
				String prefix = connection.readStringByLength(1);
				String line = connection.readStringByDelimiter(DELIMITER);
				connection.removeReadMark();    // remove read mark, because data has been read  
				
				sb.append(prefix + line + DELIMITER);
				
				
			} else if (mode == STREAM_UTILS_SUPPORT) {
				ConnectionUtils.validateSufficientDatasizeByIntLengthField(connection);
			}
			
			return true;
		}
		
		
		String getData() {
			return sb.toString();
		}
	}

	
	

	private static final class ClientHandlerLengthField implements IConnectHandler, IDataHandler {
		
		private List<Integer> data = new ArrayList<Integer>();
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			
			connection.write("GET Data" + DELIMITER);
			connection.flush();
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {

			int length = ConnectionUtils.validateSufficientDatasizeByIntLengthField(connection);
			for (int i = 0; i < length; i++) {
				data.add(connection.readInt());
			}
			return true;
		}		
		
		
		List<Integer> getData() {
			return data;
		}
		
	}

	
	
	
	
	private static final class ServerHandler implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			String request = connection.readStringByDelimiter(DELIMITER);
			
			connection.write("+firstLine" + DELIMITER);
			connection.write("+secondLine" + DELIMITER);
			connection.flush();
			
			QAUtil.sleep(100);
			connection.write("+thirdLine" + DELIMITER);
			connection.write("+fourthL");
			connection.flush();
			
			return true;
		}		
	}
	
	
	
	private static final class ServerHandlerLengthField implements IConnectHandler, IDataHandler {
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException {
			connection.setAutoflush(false);
			connection.setFlushmode(FlushMode.ASYNC);
			return true;
		}
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
			
			String request = connection.readStringByDelimiter(DELIMITER);
			
			connection.write((int) (3 * 4));
			
			connection.write((int) 1);
			connection.write((int) 2);
			connection.write((int) 3);
			connection.flush();
			
			QAUtil.sleep(100);
			
			connection.write((int) (7 * 4));
			
			connection.write((int) 4);
			connection.write((int) 5);
			connection.write((int) 6);
			connection.write((int) 7);
			connection.write((int) 8);
			connection.flush();
			
			return true;
		}		
	}
}
