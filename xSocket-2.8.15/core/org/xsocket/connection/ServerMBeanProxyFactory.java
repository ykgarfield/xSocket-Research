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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.IntrospectionBasedDynamicMBean;
 




/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean
 * for a given {@link Server} instance.
 *
 * E.g.
 * <pre>
 *   ...
 *   IServer smtpServer = new Server(port, new SmtpProtcolHandler());
 *   ConnectionUtils.start(server);
 *
 *   // create a mbean for the server and register it
 *   ServerMBeanProxyFactory.createAndRegister(server);
 *
 * </pre>
 *
 *
 * @author grro@xsocket.org
 */
final class ServerMBeanProxyFactory {

    
	
	/**
	 * creates and registers a mbean for the given server on the given MBeanServer
	 * under the given domain name
	 *
	 * @param server       the server to register
	 * @param domain       the domain name to use
 	 * @param mbeanServer  the mbean server to use
	 * @return the objectName
	 * @throws JMException  if an jmx exception occurs
	 */
	public static ObjectName createAndRegister(IServer server, String domain, MBeanServer mbeanServer) throws Exception {

		String address = server.getLocalAddress().getCanonicalHostName() + ":" + server.getLocalPort();

		ObjectName serverObjectName = registerMBeans(server, domain, address, mbeanServer);
		server.addListener(new Listener(server, domain, address, mbeanServer));

		return serverObjectName;
	}


	private static ObjectName registerMBeans(IServer server, String domain, String address, MBeanServer mbeanServer) throws Exception {
		address = address.replace(":", "_");
		
		// create and register handler
		IHandler hdl = server.getHandler();

		
		ObjectName hdlObjectName = new ObjectName(domain + ".server." + address + ":type=" + hdl.getClass().getSimpleName());
		if (hdl instanceof MBeanRegistration) {
			((MBeanRegistration) hdl).preRegister(mbeanServer, hdlObjectName);
			
		} else {
			mbeanServer.registerMBean(new IntrospectionBasedDynamicMBean(hdl), hdlObjectName);
		}


		// register the server
		ObjectName serverObjectName = null;
		

		if (server instanceof Server) {
		    IoSocketDispatcherPool dispatcherPool = ((Server) server).getAcceptor().getDispatcherPool();
		        
		    DispatcherPoolMBeanProxyFactory.createAndRegister(dispatcherPool, domain + ".server." + address, mbeanServer);
		}
        
        serverObjectName = new ObjectName(domain + ".server." + address + ":type=xServer,name=" + server.hashCode());
        mbeanServer.registerMBean(new IntrospectionBasedDynamicMBean(server), serverObjectName);



		// create and register workerpool
		ObjectName workerpoolObjectName = new ObjectName(domain + ".server." + address + ":type=Workerpool");
		mbeanServer.registerMBean(new IntrospectionBasedDynamicMBean(server.getWorkerpool()), workerpoolObjectName);

		
		if (hdl instanceof MBeanRegistration) {
			((MBeanRegistration) hdl).postRegister(true);
		}
		
		return serverObjectName;
	}
	
	
	

	private static void unregisterMBeans(IServer server, String domain, String address, MBeanServer mbeanServer) throws Exception {
		address = address.replace(":", "_");
		
		// create and register handler
		IHandler hdl = server.getHandler();

		
		// unregister handler
		ObjectName hdlObjectName = new ObjectName(domain + ".server." + address + ":type=" + server.getHandler().getClass().getSimpleName());
		if (hdl instanceof MBeanRegistration) {
			((MBeanRegistration) hdl).preDeregister();
		} 
		mbeanServer.unregisterMBean(hdlObjectName);


		// unregister worker pool
		ObjectName workerpoolObjectName = new ObjectName(domain + ".server." + address +  ":type=Workerpool");
		mbeanServer.unregisterMBean(workerpoolObjectName);
		
		if (hdl instanceof MBeanRegistration) {
			((MBeanRegistration) hdl).postDeregister();
		} 
	}


	private static final class Listener implements IServerListener {

		private static final Logger LOG = Logger.getLogger(Listener.class.getName());

		private final IServer server;
		private final String domain;
		private final String address;
		private final MBeanServer mbeanServer;

		Listener(IServer server, String domain, String address, MBeanServer mbeanServer) {
			this.server = server;
			this.domain = domain;
			this.address = address;
			this.mbeanServer = mbeanServer;

			server.addListener(this);
		}

		public void onInit() {
		}

		public void onDestroy() {
			try {
				unregisterMBeans(server, domain, address, mbeanServer);
				
			} catch (InstanceNotFoundException ignore) {
			    // eat exception
			    
			} catch (Exception e) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by deregistering the server (domain=" + domain + "). reason: " + e.toString());
				}
				throw new RuntimeException(e);
			}
		}
	}
}
