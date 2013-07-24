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

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.xsocket.DataConverter;
import org.xsocket.IntrospectionBasedDynamicMBean;
 




/**
 * A Mbean proxy factory, which creates and registers an appropriated mbean
 * for a given {@link IoSocketDispatcherPool} instance.
 * </pre>
 *
 *
 * @author grro@xsocket.org
 */
final class DispatcherPoolMBeanProxyFactory {

    private static final Logger LOG = Logger.getLogger(DispatcherPoolMBeanProxyFactory.class.getName());
    
    
	public static ObjectName createAndRegister(IoSocketDispatcherPool dispatcherPool, String domain, MBeanServer mbeanServer) throws JMException {
		DispatcherPoolListener dispatcherPoolListener = new DispatcherPoolListener(domain, mbeanServer);
		dispatcherPool.addListener(dispatcherPoolListener);

		for (IoSocketDispatcher dispatcher : dispatcherPool.getDispatchers()) {
			dispatcherPoolListener.onDispatcherAdded(dispatcher);
		}
		
		return null;
	}
	

	

    private static final class DispatcherPoolListener implements IIoDispatcherPoolListener {

        private final String domain;
        private final MBeanServer mbeanServer;

        DispatcherPoolListener(String domain, MBeanServer mbeanServer) {
            this.domain = domain;
            this.mbeanServer = mbeanServer;
        }



        public void onDispatcherAdded(IoSocketDispatcher dispatcher) {

            try {
                ObjectName objectName = new ObjectName(domain + ":type=xDispatcher,name=" + dispatcher.getName());
                mbeanServer.registerMBean(new IntrospectionBasedDynamicMBean(dispatcher), objectName);
                
            } catch (InstanceAlreadyExistsException ignore) { 
                // ignore because global dispatcher pool could have been already registered  
            	
            } catch (Exception e) { 
                // eat and log exception
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.warning("error occured by adding mbean for new dispatcher: " + DataConverter.toString(e));
                }
            }
        }


        public void onDispatcherRemoved(IoSocketDispatcher dispatcher) {
            try {
                ObjectName objectName = new ObjectName(domain + ":type=xDispatcher,name=" + dispatcher.getName());
                mbeanServer.unregisterMBean(objectName);
            } catch (Exception e) { 
                // eat and log exception
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.warning("error occured by removing mbean of dispatcher: " + DataConverter.toString(e));
                }
            }
        }	
    }
}
