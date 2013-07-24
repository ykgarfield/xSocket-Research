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




import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;


import org.junit.Ignore;
import org.junit.Test;
import org.xsocket.QAUtil;
import org.xsocket.Resource;
import org.xsocket.connection.IConnection.FlushMode;




/**
*
* @author grro@xlightweb.org
*/
public final class ChatTest  {
    
    private static final int NUM_CLIENTS = 2;
    private static final int NUMBER = 10;
    
    
    
    
    @Ignore
    @Test
    public void testSimple() throws Exception {
        
        System.out.println("running chat test...");

        final List<ChatClient> clients = new ArrayList<ChatClient>();
        
        ServerDataHandler dh = new ServerDataHandler();
        final Server srv = new Server(0, dh);
	    srv.setWorkerpool(Executors.newFixedThreadPool(10));
	    srv.setFlushmode(FlushMode.ASYNC);
	    srv.start();
	    
	    for (int i = 0; i < NUM_CLIENTS; i++) {
	        new Thread() {
	            
	            @Override
	            public void run() {
	                ChatClient client = new ChatClient();
	                clients.add(client);
	                client.launch("localhost", srv.getLocalPort());
	            }
	            
	        }.start();
	    }
	    
	    QAUtil.sleep(10000);
	    
	    Assert.assertEquals(NUM_CLIENTS * NUMBER, srv.getOpenConnections().size());
	    Assert.assertEquals(0, dh.getNumErrors());

	    for (ChatClient client : clients) {
	        client.close();
	        
	        Assert.assertEquals("errors occured", 0, client.getNumErrors());
	        Assert.assertTrue(client.getNumSend() > 100);
	        Assert.assertTrue(client.getNumReceived() > 1000);
	    }
	    
	    
	    QAUtil.sleep(1000);
	    
	    srv.close();
	}

	
	private static class ServerDataHandler implements IDataHandler {
	    
	    @Resource
	    private Server server;
	    
	    private final AtomicInteger numErrorsRef = new AtomicInteger(0);
	    
	    
	    public int getNumErrors() {
	        return numErrorsRef.get();
	    }
	    
	    public boolean onData(INonBlockingConnection nbc) throws IOException {
	        try {
	            String data = nbc.readStringByDelimiter("\0");
	            if (data.trim().length() > 0) {
	                if (data.equalsIgnoreCase("<policy-file-request/>")) {
	                    nbc.write("<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"8090\"/></cross-domain-policy>\0");
	                    return true;
	                }
	                
	                String[] message = data.split("-");
	                sendMessageToAll(message[0], message[1]);
	            }
	        } catch (Exception ex) {
	            numErrorsRef.incrementAndGet();
	        }
	        
	        return true;
	    }
	    
	    
	    private void sendMessageToAll(String user, String message) {
	        try {
	            for (INonBlockingConnection nbc : server.getOpenConnections()) {
	                nbc.write("<b>" + user+ "</b>: " + message+ "<br/>\0");
	            }
	        } catch (Exception ex) {
	            System.out.println("sendMessageToAll: "+ex.getMessage());
	        }
	    }
	}

	
	
	private static final class ChatClient {
	    
	    private static final Timer TIMER = new Timer(false);
	    private static final Executor WORKERPOOL = Executors.newFixedThreadPool(10);
	    
	    private final AtomicBoolean isOpenRef = new AtomicBoolean(true);
	    private final AtomicInteger numSendRef = new AtomicInteger(0);
	    private final AtomicInteger numReceivedRef = new AtomicInteger(0);
	    private final AtomicInteger numErrorsRef = new AtomicInteger(0);
	    
	    
	    public void close() {
	        isOpenRef.set(false);
	    }
	    
	    public int getNumErrors() {
	        return numErrorsRef.get();
	    }
	    
	    public int getNumSend() {
	       return numSendRef.get(); 
	    }
	    
	    public int getNumReceived() {
	        return numReceivedRef.get();
	    }
	    
	    public void launch(String host, int port) {

	        try {
	            for (int i = 0; i < NUMBER; i++) {
	                final int num = i;
	                
	                IDataHandler clientHandler = new IDataHandler() {

	                    public boolean onData(INonBlockingConnection nbc) throws IOException {
	                        try {
	                            nbc.readStringByDelimiter("\0");
	                            numReceivedRef.incrementAndGet();
	                        } catch (Exception ex) {
	                            numErrorsRef.incrementAndGet();
	                        }
	                        
	                        return true;
	                    }
	                };
	                
	                final INonBlockingConnection nbc = new NonBlockingConnection(host, port, clientHandler, WORKERPOOL);
	                nbc.setFlushmode(FlushMode.ASYNC);
	                
	                TimerTask tt = new TimerTask() {
	                    
	                    private int j = 0; 
	                    
	                    @Override
	                    public void run() {
	                        if (isOpenRef.get()) {
    	                        try {
    	                            String msg = num + "-Message #" + (j++); 
    	                            nbc.write(msg + "\0");
    	                            numSendRef.incrementAndGet();
    	                        } catch (Exception ex) {
    	                            numErrorsRef.incrementAndGet();
    	                        }
	                        } else {
	                            this.cancel();
	                        }
	                    }
	                };
	                TIMER.schedule(tt, 500, 500);
	            }
	        } catch (IOException ioe) {
	            numErrorsRef.incrementAndGet();
	        }
	    }
	}
}