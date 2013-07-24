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
import org.xsocket.connection.multiplexed.IBlockingPipeline;
import org.xsocket.connection.multiplexed.IMultiplexedConnection;
import org.xsocket.connection.multiplexed.INonBlockingPipeline;
import org.xsocket.connection.multiplexed.IPipelineDataHandler;
import org.xsocket.connection.multiplexed.MultiplexedConnection;
import org.xsocket.connection.multiplexed.MultiplexedProtocolAdapter;




/**
*
* @author grro@xsocket.org
*/
public final class PipelineExampleTest {
	
	private static final String DELIMITER = "\r\n";
	
	private static final int CMD_HELO = 1;
	private static final int CMD_DATA_START = 2;
	private static final int CMD_DATA_END = 3;

	private static final int RESPONSE_OK = 99;
	
	@Test 
	public void testExample() throws Exception {
		IServer server = new Server(new MultiplexedProtocolAdapter(new CommandPipelineHandler()));
		server.start();
		
		ConnectionUtils.registerMBean(server);
		
		IMultiplexedConnection connection = new MultiplexedConnection(new NonBlockingConnection(InetAddress.getByName("localhost"), server.getLocalPort()));
		String commandPipelineId = connection.createPipeline();
		IBlockingPipeline commandPipeline = connection.getBlockingPipeline(commandPipelineId);
			
		commandPipeline.write(CMD_HELO);
		int greetingResponse = commandPipeline.readInt();
		Assert.assertEquals(RESPONSE_OK,  greetingResponse);
		
		commandPipeline.write(CMD_DATA_START);
		String dataPipelineID = commandPipeline.readStringByDelimiter(DELIMITER);
		IBlockingPipeline dataPipeline = connection.getBlockingPipeline(dataPipelineID);
		
		
		byte[] data = QAUtil.generateByteArray(20);
		dataPipeline.write(data);

		data = QAUtil.generateByteArray(90);
		dataPipeline.write(data);

		data = QAUtil.generateByteArray(40);
		dataPipeline.write(data);
		
		commandPipeline.write(CMD_DATA_END);
		int response = commandPipeline.readInt();
		Assert.assertEquals(RESPONSE_OK,  response);
		
		Assert.assertFalse(dataPipeline.isOpen());
		
		commandPipeline.close();
		
		connection.close();
		server.close();
	}
	

	
	private static final class CommandPipelineHandler implements IPipelineDataHandler {
		
		private int countHandledCommands = 0;
		
		public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			int cmd = pipeline.readInt();
			
			if (cmd == CMD_HELO) {
				pipeline.write(RESPONSE_OK);
				
			} else if (cmd == CMD_DATA_START) {
				IMultiplexedConnection connection = pipeline.getMultiplexedConnection();
				
				String dataPipelineId = connection.createPipeline();
				pipeline.setAttachment(dataPipelineId);
				
				INonBlockingPipeline dataPipeline = connection.getNonBlockingPipeline(dataPipelineId);
				dataPipeline.setHandler(new DataHandler());
				
				
				pipeline.write(dataPipelineId + DELIMITER);
			
			} else if(cmd == CMD_DATA_END) {
				String dataPipelineID = (String) pipeline.getAttachment();

				IMultiplexedConnection connection = ((INonBlockingPipeline) pipeline).getMultiplexedConnection();
				INonBlockingPipeline dataPipeline = connection.getNonBlockingPipeline(dataPipelineID);
				dataPipeline.close();
				
				pipeline.write(RESPONSE_OK);
			}
			
			countHandledCommands++;
			
			return true;
		}
		
		int getCountHandledCommands() {
			return countHandledCommands;
		}
	}
	
	
	
	private static final class DataHandler implements IDataHandler {
		
		public boolean onData(INonBlockingConnection pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			pipeline.readBytesByLength(pipeline.available());
			// do something with the data
			
			return true;
		}
	}
	
}