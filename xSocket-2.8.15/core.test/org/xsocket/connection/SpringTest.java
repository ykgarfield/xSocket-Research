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
import java.io.FileWriter;

import org.junit.Assert;
import org.junit.Test;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.xsocket.QAUtil;
import org.xsocket.connection.BlockingConnection;
import org.xsocket.connection.EchoHandler;
import org.xsocket.connection.IBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.ConnectionUtils;



/**
*
* @author grro@xsocket.org
*/
public final class SpringTest {
	
	
	private static final String SPRING_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\r" +
									  		 "<beans xmlns=\"http://www.springframework.org/schema/beans\"" +
									  		 " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
									  		 " xsi:schemaLocation=\"http://www.springframework.org/schema/beans" +
									  		 " http://www.springframework.org/schema/beans/spring-beans-2.0.xsd\">\r\n" +
									  		 " <bean id=\"server\" class=\"org.xsocket.connection.Server\" scope=\"singleton\" init-method=\"start\" destroy-method=\"close\">\n\r" +
									  		 "  <constructor-arg type=\"int\" value=\"8787\"/>\n\r" +
									  		 "  <constructor-arg type=\"org.xsocket.connection.IHandler\" ref=\"Handler\"/>\n\r" +
									  		 "\n\r" +
										  	 " </bean>\n\r" +
										  	 " <bean id=\"Handler\" class=\"org.xsocket.connection.EchoHandler\" scope=\"prototype\"/>\n\r" +
										  	 "</beans>";


	@Test 
	public void testSimple() throws Exception {	
		
		File file = QAUtil.createTempfile();
		FileWriter fw = new FileWriter(file);
		fw.write(SPRING_XML);
		fw.close();
		
		BeanFactory beanFactory = new XmlBeanFactory(new FileSystemResource(file));
		IServer server = (IServer) beanFactory.getBean("server");
		Assert.assertTrue(server.isOpen());
		assert (server.isOpen() == true);
		
		
		IBlockingConnection connection = new BlockingConnection("localhost", server.getLocalPort());
		connection.setAutoflush(true);
		String request = "3453543535";
		connection.write(request + EchoHandler.DELIMITER);
		
		String response = connection.readStringByDelimiter(EchoHandler.DELIMITER, Integer.MAX_VALUE);
		connection.close();
		
		Assert.assertTrue(request.equals(response));

		file.delete();
		server.close();
	}

}
