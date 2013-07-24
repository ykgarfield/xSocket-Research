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
package org.xsocket;


import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;


/**
*
* @author grro@xsocket.org
*/
public final class DataConverterTest {
	

	@Test 
	public void testByteBufferToString() throws Exception {

	    String msg = "test";
	    ByteBuffer msgBB = ByteBuffer.wrap(msg.getBytes());
	    
	    String s = DataConverter.toString(msgBB);
	    Assert.assertEquals(msg, s);
	}
	
	
		
	
	@Test 
    public void testStringToByteBuffer() throws Exception {

        String msg = "test";
        ByteBuffer buf = DataConverter.toByteBuffer(msg, "UTF-8");

        ByteBuffer b = ByteBuffer.wrap(msg.getBytes("UTF-8"));
        
        Assert.assertArrayEquals(buf.array(), b.array());
    }
	
	
	@Test 
    public void testByteBuffersToBytes() throws Exception {

		ByteBuffer buf1 = ByteBuffer.wrap("test".getBytes());
		ByteBuffer buf2 = ByteBuffer.wrap("One".getBytes());
		ByteBuffer buf3 = ByteBuffer.wrap("Two".getBytes());
		ByteBuffer buf4 = ByteBuffer.wrap("Three".getBytes());
		
		ByteBuffer[] bufs = new ByteBuffer[] { buf1, buf2, null, buf3, buf4 };  
		
		byte[] data = DataConverter.toBytes(bufs);
        
        Assert.assertArrayEquals(data, "testOneTwoThree".getBytes());
    }
	
	

	@Test 
	public void testByteBufferToBytes() throws Exception {

		String msg = "test1234";
		byte[] bs = msg.getBytes();
		ByteBuffer data = ByteBuffer.wrap(bs);
		Assert.assertEquals('t', (char) data.get());
		
		byte[] bytes = DataConverter.toBytes(data);
		String msg2 = new String(bytes); 
		Assert.assertEquals("est1234", msg2);
	}
}
