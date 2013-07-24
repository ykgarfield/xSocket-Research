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


import java.text.SimpleDateFormat;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;


/**
 * 
 * @author grro
 */
public class LogFormatter extends Formatter {

	private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("kk:mm:ss,S"); 
	 
	
	@Override
	public String format(LogRecord record) {
		StringBuffer sb = new StringBuffer();

		sb.append(DATEFORMAT.format(record.getMillis()));
		
		sb.append(" ");
		sb.append(record.getThreadID());

		sb.append(" ");
		sb.append(record.getLevel());
		
		sb.append(" [");
		String clazzname = record.getSourceClassName();
		int i = clazzname.lastIndexOf(".");
		clazzname = clazzname.substring(i + 1, clazzname.length());
		sb.append(clazzname);

		sb.append("#");
		sb.append(record.getSourceMethodName());

		sb.append("] ");
		sb.append(record.getMessage() + "\n");

		return sb.toString();
	}

}
