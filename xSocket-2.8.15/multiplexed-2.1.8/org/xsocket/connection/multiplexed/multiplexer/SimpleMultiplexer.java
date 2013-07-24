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
package org.xsocket.connection.multiplexed.multiplexer;



import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IWriteCompletionHandler;
import org.xsocket.connection.IConnection.FlushMode;



/**
 * the simple implementation of the {@link IMultiplexer} 
 * 
 * @author grro@xsocket.org
 */
public final class SimpleMultiplexer implements IMultiplexer {
	
	private static final Logger LOG = Logger.getLogger(SimpleMultiplexer.class.getName());
	
	private static byte VERSION = 1;
	
	private static final byte PIPELINE_OPENED = 0;
	private static final byte PIPELINE_CLOSED = 1;
	private static final byte PIPELINE_DATA = 99;
	
	
	/**
	 * {@inheritDoc}
	 */
	public String openPipeline(INonBlockingConnection connection) throws IOException, ClosedChannelException {
		UUID uuid = UUID.randomUUID(); 
		String pipelineId = uuid.toString();
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + connection.getId() + "] sending on pipeline " + pipelineId + " opened notification"); 
		}
			

		FlushMode flushMode = connection.getFlushmode();
		connection.setFlushmode(FlushMode.ASYNC);
		
		ByteBuffer header = ByteBuffer.allocate(4 +1 + 1 + 16);
		header.putInt(1 + 1 + 16);                       // packet length field
		header.put(VERSION);
		header.put(PIPELINE_OPENED);                     // command
		header.putLong(uuid.getMostSignificantBits());   // uuid 
		header.putLong(uuid.getLeastSignificantBits());  // uuid
		header.rewind();
		connection.write(header);
		connection.flush();
		
		connection.setFlushmode(flushMode);
		
		return pipelineId;
	}

	
	/**
	 * {@inheritDoc}
	 */
	public void closePipeline(INonBlockingConnection connection, String pipelineId) throws IOException, ClosedChannelException {
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + connection.getId() + "] sending on pipeline " + pipelineId + " closed notification"); 
		}
		
		FlushMode flushMode = connection.getFlushmode();
		connection.setFlushmode(FlushMode.ASYNC);
		
		UUID uuid = UUID.fromString(pipelineId);
				
		ByteBuffer header = ByteBuffer.allocate(4 + 1 + 1 + 16);
		header.putInt(1 + 1 + 16);                       // packet length field
		header.put(VERSION);
		header.put(PIPELINE_CLOSED);                     // command
		header.putLong(uuid.getMostSignificantBits());   // uuid 
		header.putLong(uuid.getLeastSignificantBits());  // uuid
		header.rewind();
		connection.write(header);
		connection.flush();
		
		connection.setFlushmode(flushMode);
	}
	
	

   /**
     * {@inheritDoc}
     */
	public void multiplex(INonBlockingConnection connection, String pipelineId, ByteBuffer[] dataToWrite) throws IOException, ClosedChannelException {
	    multiplex(connection, pipelineId, dataToWrite, null);
	}

	
	/**
     * {@inheritDoc}
     */
	public void multiplex(INonBlockingConnection connection, String pipelineId, ByteBuffer[] dataToWrite, IWriteCompletionHandler completionHandler) throws IOException, ClosedChannelException {
		int dataLength = 0;
		for (ByteBuffer buffer : dataToWrite) {
			dataLength += buffer.remaining();
		}
	
		if (LOG.isLoggable(Level.FINE)) {
			int size = 0;
			ByteBuffer[] buffers = new ByteBuffer[dataToWrite.length];
			for (int i = 0; i < buffers.length; i++) {
				buffers[i] = dataToWrite[i].duplicate();
				size += buffers[i].remaining();
			}
			
			LOG.fine("[" + connection.getId() + "] sending data on pipeline " + pipelineId + ": (" + DataConverter.toFormatedBytesSize(size) + ") " 
 					 + DataConverter.toString(buffers, "UTF-8", 200)); 
		}

		
		UUID uuid = UUID.fromString(pipelineId);
		
		ByteBuffer header = ByteBuffer.allocate(4 + 1 + 1 + 16);
		header.putInt(1 + 1 + 16 + dataLength);              // packet length field
		header.put(VERSION);
		header.put(PIPELINE_DATA);                           // command
		header.putLong(uuid.getMostSignificantBits());       // uuid 
		header.putLong(uuid.getLeastSignificantBits());      // uuid
		header.rewind();
		connection.write(header);
		if (completionHandler == null) {
		    connection.write(dataToWrite);                       // data
		} else {
		    connection.write(dataToWrite, completionHandler);    // data
		}
		connection.flush();
	}
		
		
	/**
	 * {@inheritDoc}
	 */
	public void demultiplex(INonBlockingConnection connection, IDemultiplexResultHandler resultHandler) throws IOException, ClosedChannelException {
		byte dataType = 0;
		String pipelineId = null; 
		ByteBuffer[] data = null;
		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("receive queue size " + connection.available());
		}
		int length = ConnectionUtils.validateSufficientDatasizeByIntLengthField(connection);
			
		byte version = connection.readByte();
		if (version != VERSION) {
			throw new IOException("message with version " + version + " received. Expected is " + VERSION);
		}
		
		dataType = connection.readByte();
		long uuidMost = connection.readLong();
		long uuidLeast = connection.readLong();
		UUID uuid = new UUID(uuidMost, uuidLeast);
		pipelineId = uuid.toString(); 
		data = connection.readByteBufferByLength(length - (1 + 1 + 16));

		
		if (LOG.isLoggable(Level.FINE)) {
			LOG.fine("[" + connection.getId() + "] got message for " + pipelineId);
		}
		
		switch (dataType) {
			case PIPELINE_DATA:
				if (LOG.isLoggable(Level.FINE)) {
					int size = 0;
					ByteBuffer[] buffers = new ByteBuffer[data.length];
					for (int i = 0; i < buffers.length; i++) {
						buffers[i] = data[i].duplicate();
						size += buffers[i].remaining();
					}
					LOG.fine("[" + connection.getId() + "] received data packet on pipeline " + pipelineId + ": (" + DataConverter.toFormatedBytesSize(size) + ") " 
							 + DataConverter.toString(buffers, "UTF-8", 200)); 
				}
				resultHandler.onPipelineData(pipelineId, data);
				break;
				
			case PIPELINE_OPENED:
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] received on pipeline " + pipelineId + " opened notification"); 
				}
				resultHandler.onPipelineOpend(pipelineId);
				break;

			case PIPELINE_CLOSED:
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("[" + connection.getId() + "] received on pipeline " + pipelineId + " closed notification"); 
				}
				resultHandler.onPipelineClosed(pipelineId);
				break;
								
			default:
				LOG.warning("received unknown message type " + dataType);
				break;	
		}
	}
}
