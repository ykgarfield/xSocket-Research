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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;



/**
*
* @author grro@xsocket.org
*/
public final class DebugPrintStream extends PrintStream {

	private ByteArrayOutputStream os = new ByteArrayOutputStream();
		
	public DebugPrintStream(PrintStream delegee) {
		super(delegee);
	}
	
	@Override
	public void write(byte[] data) throws IOException {
		os.write(data);
		super.write(data);
	}
	
	
	@Override
	public void write(byte[] data, int off, int len) {
		os.write(data, off, len);
		super.write(data, off, len);
	}
	
	@Override
	public void write(int data) {
		os.write(data);
		super.write(data);
	}	
	
	
	public byte[] getData() {
		return os.toByteArray();
	}
	
	public void clear() {
		os = new ByteArrayOutputStream();
	}
}
