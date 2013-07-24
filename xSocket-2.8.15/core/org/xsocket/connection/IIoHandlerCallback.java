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
import java.nio.ByteBuffer;




/**
 * Call back interface to notify io events of the {@link IIoHandler}. The IIoHandler
 * is responsible to notify the events in the occured order. <br><br>
 * 
 * 
 * 
 * @author grro@xsocket.org
 */
interface IIoHandlerCallback {
		
	
	/**
	 * notifies that data has been read from the socket. <br><br>
	 * 
	 * @param data  the received data
	 * @param size  the received data size
	 *
	 */
	void onData(ByteBuffer[] data, int size);
	
	
	
	/**
	 * notifies that data read taks has been completed <br><br>
	 *
	 */
	void onPostData();
		
	
	/**
	 * notifies that the underlying connection has been established. <br>
	 *
	 */
	void onConnect();
	

	/**
	 * notifies that the underlying connection has not been established caused by an error
	 * 
	 * @param ioe  the error
	 */
	void onConnectException(IOException ioe);
		
	
	/**
	 * notifies that the underlying connection has been disconnected (closed).<br>
	 *
	 */
	void onDisconnect();
		
	
	/**
	 * notifies that the connection has to be closed (connection is corrupt, 
	 * selector has be closed, ...). This call back method will NOT be called in the case of 
	 * idle or connection time out.<br><br>
	 * 
	 */
	void onConnectionAbnormalTerminated();
	

	
	/**
	 * notifies that data has been written on the socket.<br><br>
	 *
	 * @param data  the written data
	 */
	void onWritten(ByteBuffer data);
	
	
	/**
	 * notifies that an error has been occurred by writing data on the socket.<br><br>
	 * 
	 * @param ioe   ioException an io exception
	 * @param data  the data, which has been tried to write
	 */	
	void onWriteException(IOException ioException, ByteBuffer data);
}
