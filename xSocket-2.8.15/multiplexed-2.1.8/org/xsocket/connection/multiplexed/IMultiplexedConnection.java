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
import java.nio.channels.ClosedChannelException;

import org.xsocket.connection.IConnection;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;



/**
 * The MultiplexedConnection manages the pipelines. Internally the multiplexed connection uses a 
 * {@link INonBlockingConnection}  
 * 
 * <br><br>
 * The implementation class of this interface have to be thread safe
 * 
 * @author grro@xsocket.org
 */
public interface IMultiplexedConnection extends IConnection {
	

	/**
	 * returns, if the connection is open. <br><br>
	 *
	 * Please note, that a connection could be closed, but reading of already
	 * received (and internally buffered) data wouldn`t fail. See also
	 * {@link IDataHandler#onData(INonBlockingConnection)}
	 *
	 *
	 * @return true if the connection is open
	 */
	public boolean isOpen();

	
	
	

	/**
	 * creates a new pipeline. The newly created pipeline can be retrieved by the 
	 * get pipeline methods.   
	 *  
	 * @return the pipeline id 
	 * @throws IOException if some io exception occurs
	 */
	String createPipeline() throws IOException;
	
	
	
	
	/**
	 * returns the ids of the open pipelines 
	 * 
	 * @return  the array of the ids 
	 * @throws ClosedChannelException if the connection is closed 
	 */
	String[] listOpenPipelines() throws ClosedChannelException;
	
	
	
	/**
	 * get a non blocking pipeline 
	 * 
	 * @param pipelineId  the pipeline id
	 * @return the pipeline
	 * @throws ClosedChannelException if the pipeline is already closed or does not exist
	 * @throws IOException if some io exception occurs 
	 */
	INonBlockingPipeline getNonBlockingPipeline(String pipelineId) throws ClosedChannelException, IOException;

	
	/**
	 * get a blocking pipeline 
	 * 
	 * @param pipelineId  the pipeline id
	 * @return the pipeline
	 * @throws ClosedChannelException if the pipeline is already closed or does not exist
	 * @throws IOException if some io exception occurs 
	 */
	IBlockingPipeline getBlockingPipeline(String pipelineId) throws ClosedChannelException, IOException;
	
	
	/**
	 * returns the id
	 *
	 * @return id
	 */
	public String getId();
	
	
	/**
	 * sets the value of a option. <br><br>
	 *
	 * A good article for tuning can be found here {@link http://www.onlamp.com/lpt/a/6324}
	 *
	 * @param name   the name of the option
	 * @param value  the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	public void setOption(String name, Object value) throws IOException;



	/**
	 * returns the value of a option
	 *
	 * @param name  the name of the option
	 * @return the value of the option
	 * @throws IOException In an I/O error occurs
	 */
	public Object getOption(String name) throws IOException;



	/**
	 * Returns an unmodifiable map of the options supported by this endpont.
	 *
	 * The key in the returned map is the name of a option, and its value
	 * is the type of the option value. The returned map will never contain null keys or values.
	 *
	 * @return An unmodifiable map of the options supported by this channel
	 */
	@SuppressWarnings("unchecked")
	public java.util.Map<String, Class> getOptions();
	


	/**
	 * returns the local port
	 *
	 * @return the local port
	 */
	public int getLocalPort();



	/**
	 * returns the local address
	 *
	 * @return the local IP address or InetAddress.anyLocalAddress() if the socket is not bound yet.
	 */
	public InetAddress getLocalAddress();




	/**
	 * returns the remote address
	 *
	 * @return the remote address
	 */
	public InetAddress getRemoteAddress();


	/**
	 * returns the port of the remote endpoint
	 *
	 * @return the remote port
	 */
	public int getRemotePort();


	/**
	 * sets the default encoding for this connection (used by string related methods like readString...)
	 *
	 * @param encoding the default encoding
	 */
	public void setDefaultEncoding(String encoding);



	/**
	 * gets the default encoding for this connection (used by string related methods like readString...)
	 *
	 * @return the default encoding
	 */
	public String getDefaultEncoding();



	
	/**
	 * ad hoc activation of a secured mode (SSL). By performing of this
	 * method all remaining data to send will be flushed.
	 * After this all data will be sent and received in the secured mode
	 *
	 * @throws IOException If some other I/O error occurs
	 */
	public void activateSecuredMode() throws IOException;
}
