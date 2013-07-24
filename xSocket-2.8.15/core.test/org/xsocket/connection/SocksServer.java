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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;


import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnection.FlushMode;





public final class SocksServer extends Server {
	
    private static final int SOCKS_VERSION5 = 0x05;

    	
	
	public static void main(String[] args) throws IOException, JMException {  
	    int port = Integer.parseInt(args[0]);
        new SocksServer(port).run();
    }
	
	
	public SocksServer() throws IOException, JMException {
	    this(0);
	}

	
	public SocksServer(int port) throws IOException, JMException {
	    super(port, new AuthHandler()); // Authhandler will be assigned to each new incoming connection
	    
	    setAutoflush(false);
	    setFlushmode(FlushMode.ASYNC);  // performance optimization
	    
	    ConnectionUtils.registerMBean(this);  // export the server as JMX artefact
    }
	
	
	

	/**
	 * Handles the authentication selection interaction
	 * 
	 * | VER | NMETHODS | METHODS |
	 *
	 */
	@Execution(Execution.NONTHREADED)   // performance optimization
	private static final class AuthHandler implements IDataHandler {
        
        private static final int NO_AUTHENTICATION_REQUIRED = 0x00;
        private static final int NO_ACCEPTABLE_METHODS = 0xFF;
    
        private final static ConnectHandler CONNECT_HANDLER = new ConnectHandler();
        
        
        // statistics
        private int numSuccessAuth = 0;
        private int numFaildAuth = 0;
        
     
        public boolean onData(INonBlockingConnection con) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {

            ////////////////
            // open read transaction 
            con.resetToReadMark();   // will be ignore if no read mark is set
            con.markReadPosition();  // save the read position to restore it in case of BufferUnderflowException by reading  
            
            
            List<Integer> supportedMethods = new ArrayList<Integer>();
            
            // read SOCKS version
            byte version = con.readByte();
            if (version == SOCKS_VERSION5) {
                
                // read the supported methods  
                byte numSupportedMethods = con.readByte();
                for (int i = 0; i < numSupportedMethods; i++) {
                    supportedMethods.add((con.readByte() & 0xFF)); 
                }
            } 
            
            
            con.removeReadMark();    // total record has been read -> remove read mark 
            // close read transaction
            ////////////////

            
            
            // only unauthenticated is supported by this server  
            if (supportedMethods.contains(NO_AUTHENTICATION_REQUIRED)) {
                // write success response
                con.write((byte) SOCKS_VERSION5);
                con.write((byte) NO_AUTHENTICATION_REQUIRED);
                con.flush();
                
                numSuccessAuth++;
                
                con.setHandler(CONNECT_HANDLER);  // replace the asigned handler of the connection by the connect handler
               
            // Required method is not supported or bad SOCKS version 
            } else {
                // write error response
                con.write((byte) SOCKS_VERSION5);
                con.write((byte) NO_ACCEPTABLE_METHODS);
               
                con.flush();
                con.close();
                con.setHandler(null);
                
                numFaildAuth++;
            }
            
            return true;
        }
        
        
        int getNumSucessAuth() {
            return numSuccessAuth;
        }
        
        int getNumFailedAuth() {
            return numFaildAuth;
        }
    }
	
	
	
	/**
	 * handles the connect interaction 
	 * 
	 * | VER | CMD | RSV | ATYP | DST.ADDR | DST.PORT |
	 *
	 */
	@Execution(Execution.MULTITHREADED)  // have to be performed multithreaded because body performs a blocking call -> new NonBlockingConnection(dstAddr, dstPort);
	private static final class ConnectHandler implements IDataHandler {
	    
	    private static final int ESTABLISH_TCP_CONNECTION = 0x01;
	    
	    private static final int IPV4 = 0x01;
	    private static final int DOMAINNAME = 0x03;
	    private static final int IPV6 = 0x04;
	    
	    private static final int SUCCEEDED = 0x00;
        private static final int COMMAND_NOT_SUPPORTED = 0x07;
        private static final int HOST_NOT_REACHABLE = 0x04;
	    
        
        private final static ForwardHandler FORWARD_HANDLER = new ForwardHandler();
	 
        
        
	    public boolean onData(INonBlockingConnection con) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {

	        
            ////////////////
            // open read transaction 
	        con.resetToReadMark();   // will be ignore if no read mark is set
	        con.markReadPosition();  // save the read position to restore it in case of BufferUnderflowException by reading
	        
	        
	        int cmd = -1; 
            int atyp = -1; 
            String dstAddr = null;
            int dstPort = -1;
	        
	        
            // read SOCKS version
            if ((con.readByte()  & 0xFF) == SOCKS_VERSION5) {
	        
    	        cmd = con.readByte() & 0xFF;   // read the command
    	        con.readByte();                // ignore reserved field
    	        atyp = con.readByte() & 0xFF;  // read the address type
    	        
    	        // read the address 
    	        switch (atyp) {
                    case IPV4:
                        dstAddr = (con.readByte() & 0xFF) + "." + (con.readByte() & 0xFF) + "." + (con.readByte() & 0xFF) + "." + (con.readByte() & 0xFF);
                        break;
                        
                    case DOMAINNAME:
                        // not supported yet
                        break;
                        
                    case IPV6:
                        // not supported yet
                        break;                    
                        
                    default:
                        break;
                }
    	        
    	        // read the port
    	        dstPort = ((con.readByte() & 0xFF) * 256) + (con.readByte() & 0xFF);
            }
    	        
	        con.removeReadMark();
	        // Transaction close
            ////////////////


	       
	        // establish the forwarding connection
	        if (cmd == ESTABLISH_TCP_CONNECTION) {
	            
	            INonBlockingConnection forwardCon = null;
	            try {
	                // establish new forward connection
	                forwardCon = new NonBlockingConnection(dstAddr, dstPort);
                    forwardCon.setFlushmode(FlushMode.ASYNC);  // performance optimization
                    forwardCon.setAutoflush(false);
                    
	                // attach the peer connection
	                forwardCon.setAttachment(con);
	                con.setAttachment(forwardCon);
	                
	                // assign the forward handler to the peer connections 
	                forwardCon.setHandler(FORWARD_HANDLER);
	                con.setHandler(FORWARD_HANDLER);
	                
	                // write success response
	                con.write((byte) SOCKS_VERSION5);
	                con.write((byte) SUCCEEDED);
	                con.flush();
	                
	            } catch (IOException ioe) {
	                // write error response
	                con.write((byte) SOCKS_VERSION5);
	                con.write((byte) HOST_NOT_REACHABLE);
	                con.flush();
	            }
	            
	            
	        // Required command is not supported or bad SOCKS version	            
	        } else {
	            // write error response
                con.write((byte) SOCKS_VERSION5);
                con.write((byte) COMMAND_NOT_SUPPORTED);
                con.flush();
	        }
	        	        
	        return true;
	    }
	}
	
	
	

	/**
	 * streams the received data of a connection to the peer connection 
	 *
	 */
	@Execution(Execution.NONTHREADED) // performance optimization
    private static class ForwardHandler implements IDataHandler, IDisconnectHandler {
        
	    private static final Logger LOG = Logger.getLogger(ForwardHandler.class.getName());
	    
	    
        public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
            INonBlockingConnection peerConnection = (INonBlockingConnection) connection.getAttachment();
                
            // read available data 
            int available = connection.available();
            if (available > 0) {
                ByteBuffer[] data = connection.readByteBufferByLength(connection.available());
                
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine(DataConverter.toTextOrHexString(ConnectionUtils.copy(data), "utf-8", 5000));
                }
                
                // .. and forward it to the peer connection
                peerConnection.write(data);
                peerConnection.flush();
                
            } else if (available == -1) {
                connection.close();
            }
            
            return true;
        }

        
        public boolean onDisconnect(INonBlockingConnection connection) throws IOException {
            INonBlockingConnection peerConnection = (INonBlockingConnection) connection.getAttachment();
            
            // also disconnect the peer connection 
            if (peerConnection != null) {
                connection.setAttachment(null);
                peerConnection.close();
            }
            return true;
        }
    }   
}
