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

import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IWriteCompletionHandler;


/**
 * The mulitplexer is responsible to multiplex and demultiplex th pipeline stream based on the underlying
 * {@link INonBlockingConnection}  
 * 
 * @author grro@xsocket.org
 */
public interface IMultiplexer  {
	
	/**
	 * opens a new pipelinie
	 *  
	 * @param connection the underlying connection
	 * @return the global (over the time) unique pipeline id 
	 * @throws IOException if an exception occurs 
	 * @throws ClosedChannelException if the connection is closed 
	 */
	public String openPipeline(INonBlockingConnection connection) throws IOException, ClosedChannelException;

	
	
	/**
	 * closes a open pipeline 
	 * 
	 * @param connection  the underlyind connection
	 * @param pipelineId the pipeline id
	 * @throws IOException if an exception occurs 
	 * @throws ClosedChannelException if the connection is closed 
	 */
	public void closePipeline(INonBlockingConnection connection, String pipelineId) throws IOException, ClosedChannelException;

	
	/**
	 * writes (multiplexes) the pipeline data to the underlying connection  
	 * 
	 * @param connection         the underlying connection
	 * @param pipelineId         the pipeline id 
	 * @param dataToWrite        the pipeline data to write 
	 * @throws IOException if an exception occurs 
	 * @throws ClosedChannelException if the connection is closed 
	 */
	public void multiplex(INonBlockingConnection connection, String pipelineId, ByteBuffer[] dataToWrite) throws IOException, ClosedChannelException;

	
	   /**
     * writes (multiplexes) the pipeline data to the underlying connection  
     * 
     * @param connection         the underlying connection
     * @param pipelineId         the pipeline id 
     * @param dataToWrite        the pipeline data to write 
     * @param completionHandler  the completion handler
     * @throws IOException if an exception occurs 
     * @throws ClosedChannelException if the connection is closed 
     */
    public void multiplex(INonBlockingConnection connection, String pipelineId, ByteBuffer[] dataToWrite, IWriteCompletionHandler  completionHandler) throws IOException, ClosedChannelException;

	
	/**
	 * reads (demultiplexes) pipeline data/open/close events from the underlying connection
	 *  
	 * @param connection      the underlying connection
	 * @param resultHandler   the result handler 
	 * @throws IOException if an exception occurs 
	 * @throws ClosedChannelException if the connection is closed 
	 */
	public void demultiplex(INonBlockingConnection connection, IDemultiplexResultHandler resultHandler) throws IOException, ClosedChannelException;
	
	
	
	
	/**
	 * The result handler to consume the specific call back notifications 
	 * 
	 * @author grro@xsocket.org
	 */
	public interface IDemultiplexResultHandler {
	
		/**
		 * call back if a pipeline has been opened by the peer 
		 * 
		 * @param pipelineId  the pipeline id
		 */
		public void onPipelineOpend(String pipelineId);
		
		
		/**
		 * call back if a pipeline has been closed by the peer
		 * 
		 * @param pipelineId  the pipeline id
		 */
		public void onPipelineClosed(String pipelineId);
		
		
		/**
		 * call back if data has been received for a pipeline 
		 * 
		 * @param pipelineId  the pipeline id 
		 * @param data        the received pipeline data
		 */
		public void onPipelineData(String pipelineId, ByteBuffer[] data);
	}
}
