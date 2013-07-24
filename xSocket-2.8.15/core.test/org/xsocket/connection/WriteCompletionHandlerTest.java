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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.QAUtil;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;


/**
*
* @author grro@xsocket.org
*/
public final class WriteCompletionHandlerTest {

	

	@Test 
	public void testSimple() throws Exception {
	    IServer server = new Server(new ServerHandler());
	    server.start();

	    
	    INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
	    con.setFlushmode(FlushMode.ASYNC);

	  

	    ByteBuffer data = DataConverter.toByteBuffer("test\r\n", "US-ASCII");

	    MyWriteCompletionHandler hdl = new MyWriteCompletionHandler();
	    con.write(data, hdl);
	           
	    QAUtil.sleep(1000);
	    Assert.assertEquals(6, hdl.getWritten());
	    Assert.assertTrue(hdl.getThreadname().startsWith("xNbcPool"));
	    
	    con.close();

	    
	    server.close();
	}
	
	

	
	@Test 
    public void testNonThreaded() throws Exception {
        IServer server = new Server(new ServerHandler());
        server.start();

        
        INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
        con.setFlushmode(FlushMode.ASYNC);

      

        ByteBuffer data = DataConverter.toByteBuffer("test\r\n", "US-ASCII");

        MyNonThreadedWriteCompletionHandler hdl = new MyNonThreadedWriteCompletionHandler();
        con.write(data, hdl);
               
        QAUtil.sleep(1000);
        Assert.assertEquals(6, hdl.getWritten());
        Assert.assertTrue(hdl.getThreadname().startsWith("xDispatcher"));
        
        con.close();

        
        server.close();
    }

	
	

    @Test 
    public void testNonThreaded2() throws Exception {
        IServer server = new Server(new ServerHandler());
        server.start();

        
        INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
        con.setFlushmode(FlushMode.ASYNC);

      

        ByteBuffer data = DataConverter.toByteBuffer("test\r\n", "US-ASCII");

        MyNonThreadedWriteCompletionHandler2 hdl = new MyNonThreadedWriteCompletionHandler2();
        con.write(data, hdl);
               
        QAUtil.sleep(1000);
        Assert.assertEquals(6, hdl.getWritten());
        Assert.assertTrue(hdl.getThreadname().startsWith("xDispatcher"));
        
        con.close();

        
        server.close();
    }

    
	
	
    @Test 
    public void testWriteFromStream() throws Exception {
        System.setProperty("org.xsocket.connection.suppressReuseBufferWarning", "true");
        
        IServer server = new Server(new ServerEchoHandler());
        server.start();
        
        INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());
        con.setFlushmode(FlushMode.ASYNC);

      
        File file = QAUtil.createTestfile_40k();
       
        Writer writer = new Writer(file.getAbsolutePath(), con);
        writer.write();

        do {
            QAUtil.sleep(300);
        } while (!writer.isComplete());
        
        
        
        File tempFile = QAUtil.createTempfile();
        
        RandomAccessFile raf = new RandomAccessFile(tempFile, "rw");
        FileChannel channel = raf.getChannel();
        
        con.transferTo(channel, (int) file.length());
        channel.close();
        raf.close();
        
        Assert.assertTrue(QAUtil.isEquals(file, tempFile));
        
        file.delete();
        tempFile.delete();
        
        con.close();
        server.close();
    }


    private static final class Writer implements IWriteCompletionHandler {
        
        
        private final INonBlockingConnection con;
        private final RandomAccessFile raf;
        private final ReadableByteChannel channel;
        
        private AtomicBoolean isComplete = new AtomicBoolean(false); 
        private ByteBuffer transferBuffer = ByteBuffer.allocate(4096);
        private IOException ioe = null;
        
        
        Writer(String filename, INonBlockingConnection con) throws IOException {
            this.con = con;
            raf =  new RandomAccessFile(filename, "r");
            channel = raf.getChannel();
        }
        
        
        void write() throws IOException {
            writeChunk();
        }


        private void writeChunk() throws IOException {
            transferBuffer.clear();
            
            int read = channel.read(transferBuffer);
            transferBuffer.flip();
            
            if (read > 0) {
                con.write(transferBuffer, this);
            } else {
            	channel.close();
            	raf.close();
                isComplete.set(true);
            }
        }
        
        
        public void onWritten(int written) throws IOException {
            writeChunk();
        }

        
        public void onException(IOException ioe) {
            this.ioe = ioe;
            isComplete.set(true);
        }

        
        boolean isComplete() throws IOException {
            if (ioe != null) {
                throw ioe;
            }
            
            return isComplete.get();
        }
    }

	

	
	private static final class MyWriteCompletionHandler implements IWriteCompletionHandler {
        
	    private String threadname;
        private long written;
        private IOException ioe;
            
        public void onWritten(int written) {
            threadname = Thread.currentThread().getName();
            this.written = written;
        }
                
        public void onException(IOException ioe) {
            threadname = Thread.currentThread().getName();
            this.ioe = ioe;
        }
      
      
        long getWritten() {
            return written;
        }
        
        IOException getIOException() {
            return ioe;
        }
        
        String getThreadname() {
            return threadname;
        }
     };
            
     
     @Execution(Execution.NONTHREADED)
     private static final class MyNonThreadedWriteCompletionHandler implements IWriteCompletionHandler {
         
         private String threadname;
         private long written;
         private IOException ioe;
             
         public void onWritten(int written) {
             threadname = Thread.currentThread().getName();
             this.written = written;
         }
                 
         public void onException(IOException ioe) {
             threadname = Thread.currentThread().getName();
             this.ioe = ioe;
         }
       
       
         long getWritten() {
             return written;
         }
         
         IOException getIOException() {
             return ioe;
         }
         
         String getThreadname() {
             return threadname;
         }
      };
             
     
      private static final class MyNonThreadedWriteCompletionHandler2 implements IWriteCompletionHandler {
          
          private String threadname;
          private long written;
          private IOException ioe;
              
          
          @Execution(Execution.NONTHREADED)
          public void onWritten(int written) {
              threadname = Thread.currentThread().getName();
              this.written = written;
          }
                  
          public void onException(IOException ioe) {
              threadname = Thread.currentThread().getName();
              this.ioe = ioe;
          }
        
        
          long getWritten() {
              return written;
          }
          
          IOException getIOException() {
              return ioe;
          }
          
          String getThreadname() {
              return threadname;
          }
       };	
	
	private static class ServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.write(connection.readByteBufferByDelimiter("\r\n") + "\r\n");
			return true;
		}
	}
	
	private static class ServerEchoHandler implements IDataHandler {

        public boolean onData(INonBlockingConnection connection) throws IOException {
            connection.write(connection.readByteBufferByLength(connection.available()));
            return true;
        }
    }
}
