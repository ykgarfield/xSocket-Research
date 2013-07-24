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

import java.io.File;
import java.net.URL;

import javax.activation.DataHandler;
import javax.activation.URLDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;



import org.xsocket.QAUtil;



/**
*
* @author grro@xsocket.org
*/
public final class SimpleMailClientExample {

    
    public static void main(String[] args) throws Exception {
		  
		  File tempFile = QAUtil.createTempfile();
		  tempFile.createNewFile();
		  
		  File dir = tempFile.getParentFile();
		  tempFile.delete();
		  
		  IServer server = new SimpleSmtpServer(0, dir.getAbsolutePath() + File.separator + "mails");
		  server.start();
	      
		  
		  SimpleSmtpClient smtpClient = new SimpleSmtpClient("localhost", server.getLocalPort());
		  
		  
	      MimeMessage msg = new MimeMessage((Session) null);

	      msg.setFrom(new InternetAddress("user1@xsocket.org"));
	      msg.addRecipient(Message.RecipientType.TO, new InternetAddress("user2@xsocket.org"));
	      
	      msg.setSubject("test mail 2");

	      Multipart mp = new MimeMultipart();
	      msg.setContent(mp);
	      
	      BodyPart textPart = new MimeBodyPart();
	      textPart.setText("I added the slides");
	      mp.addBodyPart(textPart);
	      
	      BodyPart binPart = new MimeBodyPart();
	      binPart.setDataHandler(new DataHandler(new URLDataSource(new URL("http://xsocket.sourceforge.net/Diagramms.ppt"))));
	      mp.addBodyPart(binPart);
	      
	      smtpClient.send(msg);
		  
		  server.close();
	  }
}
