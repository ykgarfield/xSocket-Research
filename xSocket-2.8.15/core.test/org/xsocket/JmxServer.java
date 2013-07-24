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
package org.xsocket;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import javax.management.MBeanServer;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;


/**
* Helper class to run a JMXConnectorServer by using rmi
*
* @author grro@xsocket.org
*/
public final class JmxServer {

	private JMXConnectorServer server = null;

	/**
	 * start the server
	 *
	 * @param name the name space
	 */
	public void start(String name) {
		start(name, 1199);
	}


	/**
	 * start the server
	 *
	 * @param name      the name space
	 * @param rmiPort   the rmi port to use
	 * @return jmxservice url
	 */
	public JMXServiceURL start(String name, int rmiPort) {
		try {
			Registry registry = LocateRegistry.createRegistry(rmiPort);
			registry.unbind(name);
		} catch (Exception ignore) {  }

		try {
		    JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + "localhost" + ":" + rmiPort + "/" + name);

		    MBeanServer mbeanSrv = ManagementFactory.getPlatformMBeanServer();
		    server = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbeanSrv);
		    server.start();
		    System.out.println("JMX RMI Agent has been bound on address");
		    System.out.println(url);

		    return url;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}


	/**
	 * stops the server
	 *
	 */
	public void stop() {
		try {
			server.stop();
		} catch (IOException ioe) {
			// ignore
		}
	}
	
	public static void main(String[] args) {
		JmxServer server = new JmxServer();
		server.start("JMX");
	}
}
