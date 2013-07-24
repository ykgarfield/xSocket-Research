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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import org.apache.commons.codec.binary.Base64;




/**
*
* @author grro@xsocket.org
*/
public final class SimpleSmtpClient {

	
	private String host = null;
	private int port = -1;
	private String username = null;
	private String password = null;


	
	
	
	
	
	public SimpleSmtpClient(String host, int port) throws IOException {
		this(host, port, null, null);
	}
	
	
	
	public SimpleSmtpClient(String host, int port, String username, String password) throws IOException {
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
	}
	
	
	
	public void send(String contentType, String text, String from, String to) throws IOException, MessagingException {
	    MimeMessage msg = new MimeMessage((Session) null);
	    msg.setContent(text, contentType);
	    msg.addFrom(InternetAddress.parse(from));
	    msg.addRecipients(RecipientType.TO, InternetAddress.parse(to));
	    
	    send(msg);
	}
	

	public void send(MimeMessage message) throws IOException, MessagingException {

		IBlockingConnection con = new BlockingConnection(host, port);
		
		// read greeting
		readResponse(con);

		sendCmd(con, "Helo mailserver");
		readResponse(con);
		

		if (username != null) {
			String userPassword = new String(Base64.encodeBase64(new String("\000" + username + "\000" + password).getBytes()));
			sendCmd(con, "AUTH PLAIN " + userPassword);
			readResponse(con);
		}

		Address sender = message.getFrom()[0];
		sendCmd(con, "Mail From: " + sender.toString());
		readResponse(con);
		
		Address[] tos = message.getRecipients(RecipientType.TO);
		for (Address to : tos) {
			sendCmd(con, "Rcpt To: " + to.toString());
			readResponse(con);
		}

		sendCmd(con, "Data");
		readResponse(con);
	
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		message.writeTo(os);
		os.close();
		
		String s = new String(os.toByteArray());
		con.write(s);
		con.write("\r\n.\r\n");
		
		
		sendCmd(con, "Quit");
		readResponse(con);
		
		con.close();
	}
	
	
	private void sendCmd(IBlockingConnection con, String cmd) throws IOException {
		System.out.println("sending " + cmd);
		con.write(cmd + "\r\n");
	}
	
	private String readResponse(IBlockingConnection con) throws IOException {
		String response = con.readStringByDelimiter("\r\n");
		System.out.println("receiving " + response);
		
		return response;
	}
}
