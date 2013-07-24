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
import java.net.InetAddress;
import java.util.Map;



/**
 * Base class of the synchronize wrapper
 *
 * @author grro@xsocket.org
 */
abstract class AbstractSynchronizedConnection implements IConnection {

    private final IConnection delegate; 
    
    public AbstractSynchronizedConnection(IConnection delegate) {
        this.delegate = delegate;
    }

    public final Object getAttachment() {
        synchronized(delegate) {
            return delegate.getAttachment();
        }        
    }

    public final long getConnectionTimeoutMillis() {
        synchronized(delegate) {
            return delegate.getConnectionTimeoutMillis();
        }        
    }

    public final String getId() {
        synchronized(delegate) {
            return delegate.getId();
        }        
    }

    public final long getIdleTimeoutMillis() {
        synchronized(delegate) {
            return delegate.getIdleTimeoutMillis();
        }        
    }

    public final InetAddress getLocalAddress() {
        synchronized(delegate) {
            return delegate.getLocalAddress();
        }        
    }

    public final int getLocalPort() {
        synchronized(delegate) {
            return delegate.getLocalPort();
        }        
    }

    public final Object getOption(String name) throws IOException {
        synchronized(delegate) {
            return delegate.getOption(name);
        }        
    }

    @SuppressWarnings("unchecked")
    public final Map<String, Class> getOptions() {
        synchronized(delegate) {
            return delegate.getOptions();
        }        
    }

    public final long getRemainingMillisToConnectionTimeout() {
        synchronized(delegate) {
            return delegate.getRemainingMillisToConnectionTimeout();
        }        
    }

    public final long getRemainingMillisToIdleTimeout() {
        synchronized(delegate) {
            return delegate.getRemainingMillisToIdleTimeout();
        }        
    }

    public final InetAddress getRemoteAddress() {
        synchronized(delegate) {
            return delegate.getRemoteAddress();
        }        
    }

    public final int getRemotePort() {
        synchronized(delegate) {
            return delegate.getRemotePort();
        }        
    }

    public final boolean isOpen() {
        synchronized(delegate) {
            return delegate.isOpen();
        }        
    }

    public final boolean isServerSide() {
        synchronized(delegate) {
            return delegate.isServerSide();
        }        
    }

    public final void setAttachment(Object obj) {
        synchronized(delegate) {
            delegate.setAttachment(obj);
        }
    }

    public final void setConnectionTimeoutMillis(long timeoutMillis) {
        synchronized(delegate) {
            delegate.setConnectionTimeoutMillis(timeoutMillis);
        }        
    }

    public final void setIdleTimeoutMillis(long timeoutInMillis) {
        synchronized(delegate) {
            delegate.setIdleTimeoutMillis(timeoutInMillis);
        }        
    }

    public final void setOption(String name, Object value) throws IOException {
        synchronized(delegate) {
            delegate.setOption(name, value);
        }        
    }

    public final void close() throws IOException {
        synchronized(delegate) {
            delegate.close();
        }        
    }
}
