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
 * Call back handler. Example:
 * 
 * <pre>
 * class MyWriteCompletionHandler implements IWriteCompletionHandler {
 * 
 *    public void onWritten(int written) throws IOException {
 *       // ...
 *    }
 *    
 *    public void onException(IOException ioe) {
 *       // ...
 *    }
 * }
 *
 * 
 * ReadableByteChannel channel = ...
 * INonBlockingConnection con = ...
 * MyWriteCompletionHandler writeCompletionHandler = new MyWriteCompletionHandler();
 * 
 * con.setFlushmode(FlushMode.ASYNC);
 * con.write(transferBuffer, writeCompletionHandler);  // returns immediately
 * 
 * // ...
 * 
 * </pre>
 * 
 * @author grro@xsocket.org
 */
public interface IWriteCompletionHandler {
 
    
    /**
     * call back, which will be called after the data is written
     * 
     * @param written    the written size  
     * @throws IOException  if an exception occurs. By throwing this 
     *                      exception the connection will be closed by xSocket 
     */
	public void onWritten(int written) throws IOException;

	
	/**
	 * call back to sginal a write error 
	 * 
	 * @param ioe the exception 
	 */
	public void onException(IOException ioe);
}
