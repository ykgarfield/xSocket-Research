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

/**
 * 客户端连接回调.		</br></br>
 * 
 * connector callback <br><br>
 *   
 * @author grro@xsocket.org
 */
interface IIoConnectorCallback {

	/**
	 * 连接建立.		</br></br>
	 * 
	 * notifies that the connection has been established
	 *
	 * @throws IOException if an exception occurs
     */
	void onConnectionEstablished() throws IOException;

	
	
	/**
	 * 连接错误.		</br></br>
	 * 
	 * notfifies a connect error
	 * 
	 * @param ioe the error
	 */
	void onConnectError(IOException ioe);
	

    /**
     * 连接超时.		</br>
     * 
     * notifies that a connection timeout is occured 
     *
     */
    void onConnectTimeout();
}
