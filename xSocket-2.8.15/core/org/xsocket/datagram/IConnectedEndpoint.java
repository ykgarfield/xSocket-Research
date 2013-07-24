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
package org.xsocket.datagram;


import java.net.SocketAddress;



/**
 * An connected endpoint, which receives/sends data only from/to from the assigned connected endpoint. E.g.
 * 
 * <pre>
 *  ...
 *  IConnectedEndpoint endpoint = new ConnectedEndpoint(remoteHostname, remotePort, packageSize);
 *  
 *  UserDatagram request = new UserDatagram(packageSize);
 *  request.write("Hello peer, how are you?");
 *  
 *  endpoint.send(request);
 *  UserDatagram response = endpoint.receive(1000);  // receive (timeout 1 sec)
 *  
 *  endpoint.close();
 *  ...
 * </pre>
 * 
 * @author grro@xsocket.org
 */
public interface IConnectedEndpoint extends IEndpoint {
	
	
	/**
	 * return the connected remote address or null if not connected
	 * 
	 * @return the connected address
	 */
	SocketAddress getRemoteSocketAddress();	
}
