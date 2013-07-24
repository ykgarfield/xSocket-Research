/*
 *  Copyright (c) xlightweb.org, 2008 - 2009. All rights reserved.
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
 * The latest copy of this software may be found on http://www.xlightweb.org/
 */
package org.xsocket.connection;


import javax.servlet.Servlet;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.HashSessionManager;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.SessionHandler;

  

/**
* 
* WebContainer for test purposes 
*  
* @author grro@xlightweb.org
*/
public final class WebContainer  {

	
	private int port = 0;
	private Servlet servlet = null;
	private Server jettyServer = null;
	private String servletPath = "";
	private String context = "";
	
	
	public WebContainer(Servlet servlet) {
		this(servlet, "");
	}
	
	public WebContainer(Servlet servlet, String servletPath) {
		this.servlet = servlet;
		this.servletPath = servletPath;
	}

	public WebContainer(Servlet servlet, String servletPath, String context) {
		this.servlet = servlet;
		this.servletPath = servletPath;
		this.context = context;
	}


	public void start() throws Exception {
		jettyServer = new Server(0);
		Context rootCtx = new Context(jettyServer, context);
		rootCtx.setSessionHandler(new SessionHandler(new HashSessionManager()));
		ServletHolder servletHolder = new ServletHolder(servlet);
		rootCtx.addServlet(servletHolder, servletPath + "/*");
		jettyServer.start();
		
		port = jettyServer.getConnectors()[0].getLocalPort();
	}
	
	
	public int getLocalPort() {
	    return port;
	}
	

	public void stop() throws Exception {
		jettyServer.stop();
	}	
}
