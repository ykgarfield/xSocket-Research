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


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.junit.Assert;
import org.junit.Test;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IConnection.FlushMode;



/**
 *
 * @author grro@xsocket.org
 */
public final class ObjectserializationTest {

	
	@Test 
	public void testSimple() throws Exception {

	    // start the server 
		Server server = new Server(new DataHandler());
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
		
		
		
		// start the client
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		
		Structure obj = new Structure();
		obj.setTxt("Hello");
		obj.setCounter(2);
		
		byte[] data = serialize(obj);
        
        connection.write(data.length);
        connection.write(data);
		
        int length = connection.readInt();
        data = connection.readBytesByLength(length);
        obj = (Structure) deserialize(data);

        Assert.assertEquals("Hello", obj.getTxt());
        Assert.assertEquals(3, obj.getCounter());
		
		connection.close();
		server.close();
	}

	
	
	private static byte[] serialize(Serializable obj) throws IOException {
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(bos);
	    oos.writeObject(obj);
	    oos.close();
	    
	    return bos.toByteArray();
	}

	
	private static Serializable deserialize(byte[] data) throws IOException {
	    try {
	        ByteArrayInputStream bis = new ByteArrayInputStream(data);
	        ObjectInputStream ois = new ObjectInputStream(bis);
	        return (Serializable) ois.readObject();
	    } catch (ClassNotFoundException cfe) {
	        throw new IOException(cfe.toString());
	    }
	}

	

	private final static class DataHandler implements IDataHandler {
	    
	    public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException {
	        
	        connection.resetToReadMark();
	        connection.markReadPosition();
	        
	        int length = connection.readInt();
	        byte[] data = connection.readBytesByLength(length);
	        
	        connection.removeReadMark();

	        
	        Structure obj = (Structure) deserialize(data);
	        obj.setCounter(obj.getCounter() + 1);
	        
	        data = serialize(obj);
	        
	        connection.write(data.length);
	        connection.write(data);
	        
	        return true;
	    }
	}
	
	
	public static final class Structure implements Serializable {
	    
        private static final long serialVersionUID = -7734721258202638847L;
        
        private String txt;
        private int counter;

        public String getTxt() {
            return txt;
        }

        public void setTxt(String txt) {
            this.txt = txt;
        }

        public int getCounter() {
            return counter;
        }

        public void setCounter(int counter) {
            this.counter = counter;
        }
	}
}
