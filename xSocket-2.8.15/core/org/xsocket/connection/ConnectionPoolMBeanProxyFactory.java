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

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.ILifeCycle;
import org.xsocket.IntrospectionBasedDynamicMBean;






/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean 
 * for a given connction pool instance.  
 * 
 * <br><br><b>This class is for test purpose only, and will be modified or discarded in future versions</b>
 * 
 * @author grro@xsocket.org
 */
final class ConnectionPoolMBeanProxyFactory {

	
	
	/**
	 * creates and registers a mbean for the given pool on the given MBeanServer
	 * under the given domain name 
	 *
	 * @param pool         the pool to register 
	 * @param domain       the domain name to use
 	 * @param mbeanServer  the mbean server to use 
	 * @throws JMException  if an jmx exception occurs 
	 */
	public static ObjectName createAndRegister(IConnectionPool pool, String domain, MBeanServer mbeanServer) throws JMException {
		ObjectName objectName = new ObjectName(domain + ".client:type=" + pool.getClass().getSimpleName() + ",name=" + pool.hashCode());
		mbeanServer.registerMBean(new IntrospectionBasedDynamicMBean(pool), objectName);
		
		new ResourcePoolListener(pool, domain, mbeanServer);
		
		registerGlobalDispatcherPool(domain, mbeanServer);
		
		return objectName;
	}

	
	private static void registerGlobalDispatcherPool(String domain, MBeanServer mbeanServer) throws JMException {
		IoSocketDispatcherPool dispatcherPool = IoProvider.getGlobalClientDisptacherPool();
		DispatcherPoolMBeanProxyFactory.createAndRegister(dispatcherPool, domain + ".client", mbeanServer);
	}


	private static void unregister(IConnectionPool pool, String domain, MBeanServer mbeanServer) throws JMException {
		ObjectName objectName = new ObjectName(domain + ".client:type=" + pool.getClass().getSimpleName() + ",name=" + pool.hashCode());
		mbeanServer.unregisterMBean(objectName);
	}
	

	
	private static final class ResourcePoolListener implements ILifeCycle {
		
		private static final Logger LOG = Logger.getLogger(ResourcePoolListener.class.getName());
		
		private final IConnectionPool pool;
		private final String domain;
		private final MBeanServer mbeanServer;
		
		ResourcePoolListener(IConnectionPool pool, String domain, MBeanServer mbeanServer) {
			this.pool = pool;
			this.domain = domain;
			this.mbeanServer = mbeanServer;
			
			pool.addListener(this);
		}
		
		public void onInit() {
		}
		
		public void onDestroy() {
			try {
				unregister(pool, domain, mbeanServer);
			} catch (Exception ex) { 
	            // eat and log exception
			    
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("error occured by deregistering the pool (domain=" + domain + "). reason: " + ex.toString());
				}
			} 
		}
	}
}
