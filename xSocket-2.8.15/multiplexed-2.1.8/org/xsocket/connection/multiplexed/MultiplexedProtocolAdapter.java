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

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;


import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.Resource;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.multiplexed.multiplexer.SimpleMultiplexer;
import org.xsocket.connection.multiplexed.multiplexer.IMultiplexer;



/**
 * A helper class to register {@link IPipelineHandler} on the {@link Server}
 * 
 * <pre>
 *   ...
 *   IServer server = new Server(new MultiplexedProtocolAdapter(new MyHandler()));
 *   server.start();
 *   ...
 *
 *   
 *   class MyHandler implements IPipelineDataHandler {
 *
 *      public boolean onData(INonBlockingPipeline pipeline) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
 *          byte[] data = pipeline.readBytesByDelimiter(DELIMITER);
 *          pipeline.write(data);
 *          pipeline.write(DELIMITER);
 *          pipeline.flush();
 *          
 *          return true;
 *      }
 *   }
 *
 * </pre>
 * 
 * 
 * 
 * @author grro
 */
@Execution(Execution.NONTHREADED)
public final class MultiplexedProtocolAdapter implements IConnectHandler, ILifeCycle, MBeanRegistration {
	
	
	
	@Resource
	private IServer server = null;

	private IHandler handler = null;
	private PipelineHandlerAdapter handlerAdapter = null;
	private IMultiplexer multiplexer = null;
	
	/**
	 * constructor
	 * 
	 * @param handler     the handler (supported: IPipelineConnectHandler, IPipelineDisconnectHandler, IPipelineDataHandler, IPipelineIdleTimeoutHandler, IPipelineConnectionTimeoutHandlerIConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 */
	public MultiplexedProtocolAdapter(IHandler handler) {
		this(handler, new SimpleMultiplexer());
	}
	
	  

	/**
	 * constructor
	 * 
	 * @param handler      the handler (supported: IPipelineConnectHandler, IPipelineDisconnectHandler, IPipelineDataHandler, IPipelineIdleTimeoutHandler, IPipelineConnectionTimeoutHandlerIConnectHandler, IDisconnectHandler, IDataHandler, IIdleTimeoutHandler and IConnectionTimeoutHandler)
	 * @param multiplexer  the multiplexer
	 */
	public MultiplexedProtocolAdapter(IHandler handler, IMultiplexer multiplexer) {
		this.handler = handler;
		this.multiplexer = multiplexer;
	}
		

	/**
	 * {@inheritDoc}
	 */
	public void onInit() {
		server.setStartUpLogMessage(server.getStartUpLogMessage() + "; multiplexed " + MultiplexedUtils.getImplementationVersion());
		
		handlerAdapter = PipelineHandlerAdapter.newInstance(handler);
		
		MultiplexedUtils.injectServerField(server, handlerAdapter.getHandler());
		handlerAdapter.onInit();
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public void onDestroy() {
		handlerAdapter.onDestroy();
		
		server = null;
		handler = null;
		handlerAdapter = null;
		multiplexer = null;
	}
	
	
	/**
	 * {@inheritDoc}
	 */	
	public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
		return MultiplexedUtils.exportMbean(server, name, handler);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void postRegister(Boolean registrationDone) {
		
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void postDeregister() {
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void preDeregister() throws Exception {
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public boolean onConnect(INonBlockingConnection connection) throws IOException {
		// the multiplexed connection replaces the current handler by a own one
		new MultiplexedConnection(connection, handlerAdapter, multiplexer);		
		return true;
	}	
}
