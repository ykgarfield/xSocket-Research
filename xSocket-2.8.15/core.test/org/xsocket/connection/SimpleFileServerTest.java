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



import org.junit.Assert;
import org.junit.Test;
import org.xsocket.QAUtil;




/**
*
* @author grro@xsocket.org
*/
public final class SimpleFileServerTest {

    @Test
    public void testSimple() throws Exception {
        IServer server = new SimpleFileServer(0, new File("").getAbsolutePath());
        server.start();
                
        SimpleFileServerClient client = new SimpleFileServerClient("localhost", server.getLocalPort());
        File file = QAUtil.createTestfile_40k(); 
        client.send(file.getName(), file);
        
        File file2 = QAUtil.createTempfile();
        
        client.read(file.getName(), file2);
        
        Assert.assertTrue(QAUtil.isEquals(file, file2));

        file2.delete();
        file.delete();
        client.close();
        server.close();
    }
}
