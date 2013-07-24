/*
 *  Coorg.xsocket.connection xsocket.org, 2006 - 2009. All rights reserved.
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
import java.net.InetSocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.util.Timer;
import java.util.TimerTask;

import javax.management.JMException;



import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.QAUtil;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnectionPool;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class HttpClientApp  {
	
	
	private int counts = 0;
	private long lastTime = System.currentTimeMillis(); 
	
	private final NonBlockingConnectionPool pool;
	
	
	public HttpClientApp() throws JMException {
		 pool = new NonBlockingConnectionPool();
		 ConnectionUtils.registerMBean(pool);		 
	}
	
	
    public static void main(String[] args) throws Exception {
    	
    	System.setProperty("org.xsocket.connection.client.readbuffer.usedirect", "true");
    	
    	
    	if (args.length < 3) {
    		System.out.println("usage java org.xsocket.connection.HttpClientApp <host> <port> <waitTimeBetweenRequests> [<maxActiveConnections>]");
    	}
    		
    	
    	String host = args[0];
    	int port = Integer.parseInt(args[1]);
    	int waittime = Integer.parseInt(args[2]);
    	int maxActive = Integer.MAX_VALUE;
    	if (args.length > 3) {
    		maxActive = Integer.parseInt(args[3]);
    	}
    	
    	new HttpClientApp().launch(host, port, waittime, maxActive);
    	
    }

    public void launch(String host, int port, int waittime, int maxActive) throws Exception {
    	
    	System.out.println("calling " + host + ":" + port + " (waitime between requests " + waittime + " millis; maxActive=" + maxActive + ")");
    	
    	TimerTask printTask = new TimerTask() {
    		
    		@Override
    		public void run() {
    			printRate();
    		}
    	};
    	
    	new Timer(false).schedule(printTask, 3 * 1000, 3 * 1000); 
    	
    	
    	pool.setMaxActive(maxActive);
    	
    	String request = "GET /?cmd=login HTTP/1.1\r\n" +
    	                 "Host: " + host + ":" + port + "\r\n" +
    	                 "User-Agent: me\r\n" +
    	                 "\r\n";

    	
    	InetSocketAddress addr = new InetSocketAddress(host, port);
    	
    	while (true) {
    		INonBlockingConnection con = null;
    		try {
    			con = pool.getNonBlockingConnection(addr);
    			con.setFlushmode(FlushMode.ASYNC);
    			    			
    			con.setHandler(new ResponseReader(System.currentTimeMillis()));
    			con.write(request);
    			
    			QAUtil.sleep(waittime);
    			
    		} catch (IOException ioe) {
    			NonBlockingConnectionPool.destroy(con);
    		}		
    	}
	}
    
    
    private synchronized void registerResponse(long sendTime) {
    	counts++;
    }
    
    
    
    private synchronized void printRate() {
    	
    	long current = System.currentTimeMillis();
    	
    	int c = counts;
    	long t = lastTime;
    	
    	counts = 0;
    	lastTime = current;
    	
    	
    	try {
    		System.out.println((c * 1000) / (current - t) + " req/sec (active cons " + pool.getNumActive() + ", idle cons " + pool.getNumIdle() + ", destroyed cons " + pool.getNumDestroyed() + ")");
    	} catch (Exception ignore) { }    	
    }
  
    
    
    @Execution(Execution.NONTHREADED)
    private final class ResponseReader implements IDataHandler {
    	
    	private final long sendTime;
    	
    	public ResponseReader(long sendTime) {
    		this.sendTime = sendTime;
		}
    	
		public boolean onData(INonBlockingConnection connection) throws IOException {
			String header = connection.readStringByDelimiter("\r\n\r\n");
			
			int startPosContentLength = header.indexOf("Content-Length");
			int endPosContentLength = header.indexOf("\r\n", startPosContentLength);
			int contentLength = Integer.parseInt(header.substring(startPosContentLength + "Content-Length:".length(), endPosContentLength).trim());
		
			boolean destroyOnRead = false;
			if (header.indexOf("Connection: close") != -1) {
				destroyOnRead = true;
			}
			
			connection.setHandler(new BodyReader(sendTime, contentLength, destroyOnRead));
			return true;
		}
	};
	
    
    
    
	@Execution(Execution.NONTHREADED)
    private final class BodyReader implements IDataHandler {

    	private final long sendTime;
    	private final boolean destroyOnRead;
    	private int remaining;

    	
    	public BodyReader(long sendTime, int contentLength, boolean destroyOnRead) { 
    		this.sendTime = sendTime;
    		this.remaining = contentLength;
    		this.destroyOnRead = destroyOnRead;
		}
    	
    	public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
    		int available = connection.available();
    		
    		int readSize = remaining;
    		if (available > 0) {
    			if (available < readSize) {
    				readSize = available;
    			}
    		}
    		
    		connection.readByteBufferByLength(readSize);
    		remaining -= readSize;
    		
    		if (remaining == 0) {
    			registerResponse(sendTime);
    			
    			if (destroyOnRead) {
    				NonBlockingConnectionPool.destroy(connection);
    			} else {
    				connection.close();
    			}
    		}
    		
    		return true;
    	}
    	
    	
    }
}
