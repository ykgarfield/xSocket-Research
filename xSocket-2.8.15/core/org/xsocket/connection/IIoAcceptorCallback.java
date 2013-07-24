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
 * 接收的回调接口.	</br>
 * xsocket中的唯一实现类是   {@link Server.LifeCycleHandler}    </br></br>
 * 
 * acceptor callback <br><br>
 *   
 * @author grro@xsocket.org
 */
interface IIoAcceptorCallback {

	/**
	 * 提醒acceptor已经绑定到socket,可以接受连接.	</br></br>
	 * 
	 * Server启动时将会调用.		</br>
	 * 
	 * notifies that the acceptor has been bound to the socket, and 
	 * accepts incoming connections
	 */
	void onConnected();
	
	
	/**
	 * 提醒acceptor已经解除绑定.	</br></br>	
	 * 
	 * notifies that the acceptor has been unbound
	 */
	void onDisconnected();
	
	
	/**
	 * 提醒有新的连接.	</br></br>
	 * 
	 * notifies a new incoming connection
	 * 
	 * @param ioHandler      the assigned io handler of the new connection
	 * @throws IOException If some other I/O error occurs
	 */
	void onConnectionAccepted(IoChainableHandler ioHandler) throws IOException;	
}
