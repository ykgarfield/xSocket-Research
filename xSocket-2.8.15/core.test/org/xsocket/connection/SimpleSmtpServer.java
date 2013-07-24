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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import javax.management.JMException;



import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.ConnectionUtils;
import org.xsocket.connection.IConnection.FlushMode;



/**
*
* @author grro@xsocket.org
*/
public final class SimpleSmtpServer extends Server {


	public static void main(String[] args) throws Exception {
		
		
		if (args.length != 2) {
			System.out.println("usage org.xsocket.connection.SimpleSmtpServer <port> <message file dir>");
			System.exit(-1);
		}
		
		int port = Integer.parseInt(args[0]);
		String outDir = args[1];
		
		System.out.println("writing mails to directory " + outDir);
		
		IServer server = new SimpleSmtpServer(port, outDir);
		server.run();
	}
	
	
	public SimpleSmtpServer(int port, String outDir) throws IOException, JMException {
		super(port, new SmtpProtocolHandler(outDir));
		
		if (!new File(outDir).exists()) {
			System.out.println(outDir + " does not exists. creating directory");
			new File(outDir).mkdirs();
		}
		
		System.out.println("writing mails to directory " + outDir);
		setFlushmode(FlushMode.ASYNC);
		
		ConnectionUtils.registerMBean(this);
	}
	
	
	
	/**
	 * simple, minimal SMTP protocol handler, which doesn't implement any security/spam protection rule  
	 *
	 */
	@Execution(Execution.NONTHREADED)
	private static final class SmtpProtocolHandler implements IConnectHandler, IDataHandler {
		
		private String msgFileDir = null;
		private int countReceivedMessages = 0;
		
		public SmtpProtocolHandler(String msgFileDir) {
			this.msgFileDir = msgFileDir;
		}
		
		
		
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
			connection.setAttachment(new SessionData());
			connection.write("220 SMTP ready \r\n");
			
			return true;
		}
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			SessionData sessionData = (SessionData) connection.getAttachment();
			
			String cmd = connection.readStringByDelimiter("\r\n").toUpperCase();
			
			if (cmd.startsWith("HELO")) {
				connection.write("250 SMTP Service\r\n");
				
			} else if (cmd.startsWith("MAIL FROM:")) {
				String originator = cmd.substring("MAIL FROM:".length(), cmd.length()).trim();
				sessionData.setOriginator(originator);
				connection.write("250 " + originator + " is syntactically correct\r\n");
				
			} else if (cmd.startsWith("RCPT TO:")) {
				String recipient = cmd.substring("RCPT TO:".length(), cmd.length());
				sessionData.addRecipients(recipient);
				connection.write("250 " + recipient + " verified\r\n");
				
			} else if(cmd.equals("DATA")) {
				String timeStamp = "Received: from " + connection.getRemoteAddress() + "\r\n" +
				                   "  by " + connection.getLocalAddress() + " with xSocket/SimpleMailServer;\r\n" + 
				                   "  " + DataConverter.toFormatedRFC822Date(System.currentTimeMillis()) + "\r\n";
				
				String msgId = connection.getId() + "." + sessionData.nextId();
				File msgFile = new File(msgFileDir + File.separator + msgId + ".eml");

				connection.setHandler(new DataHandler(msgFile, timeStamp, this));	
				connection.write("354 Enter message, ending with \".\"\r\n");
				
				
			} else if (cmd.startsWith("QUIT")) {
				connection.write("221 SMTP service closing connection\r\n");
                connection.close();
                
			} else {
				connection.write("500 Unrecognized command\r\n");
			}
			
			return true;
		}		
		
		
		String getMessageFileDirectory() {
			return msgFileDir;
		}
		
		void setMessageFileDirectory(String msgFileDir) {
			this.msgFileDir = msgFileDir;
		}
		
		int getCountReceivedMessages() {
			return countReceivedMessages;
		}
		
		void incCountReceiveMessages() {
			countReceivedMessages++;
		}
	}

	
	
	@Execution(Execution.MULTITHREADED)
	private static final class DataHandler implements IDataHandler {
	
		private SmtpProtocolHandler smtpHandler;
		private File file;
		private String timeStamp;
		private RandomAccessFile raf;
		private FileChannel fc;

		
		public DataHandler(File file, String timeStamp, SmtpProtocolHandler smtpHandler) {
			this.file = file;
			this.timeStamp = timeStamp;
			this.smtpHandler = smtpHandler;
		}
		
		
		
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {

			if (fc == null) {
				file.createNewFile();

				raf = new RandomAccessFile(file, "rw");
				fc = raf.getChannel();
				fc.write(DataConverter.toByteBuffer(timeStamp, "US-ASCII"));
			}
			

			int delimiterPos = connection.indexOf("\r\n.\r\n");
					
			if (delimiterPos != -1) {
				connection.transferTo(fc, delimiterPos);
				connection.readByteBufferByLength(5);  // remove delimiter
						
				fc.close();
				raf.close();
				fc = null;

				smtpHandler.incCountReceiveMessages();
				connection.setHandler(smtpHandler);
				
				connection.write("250 OK\r\n");
		
						
			} else if (connection.available() > 5) {
				int size = connection.available() - 5;
				connection.transferTo(fc, size);
			}
				
			return true;
		}
	}

	
	private static final class SessionData {
		
		private long id = 0;
		private String originator = null;
		private List<String> recipients = new ArrayList<String>();
		
		long nextId() {
			return id++;
		}

		String getOriginator() {
			return originator;
		}
		
		void setOriginator(String originator) {
			this.originator = originator;
		}

		List<String> getRecipients() {
			return recipients;
		}
		
		void addRecipients(String recipient) {
			recipients.add(recipient);
		}
		
		void reset() {
			originator = null;
			recipients.clear();
		}
	}
}
