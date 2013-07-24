package org.xsocket.connection;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;
import org.xsocket.Execution;
import org.xsocket.QAUtil;
import org.xsocket.connection.IConnection.FlushMode;


/**
*
* @author grro@xsocket.org
*/
public final class WriteCompletionHandlerBulkTest {


    public static void main(String[] args) throws Exception {
        
        WriteCompletionHandlerBulkTest test = new WriteCompletionHandlerBulkTest();
        
        System.out.println("test upload");
        test.testClientSideAsyncUploadBulk();
        
        System.out.println("test download");
        test.testServerSideAsyncDownloadBulk();
        
        System.out.println("tests PASSED");
    }

    
    
    @Test
    public void testClientSideAsyncUploadBulk() throws Exception {
        for (int i = 0; i < 50; i++) {
            System.out.println("run " + i);
            testClientSideAsyncUpload();
        }
        
        System.gc();
    }
    

    @Test
	public void testClientSideAsyncUpload() throws Exception {
	    IServer server = new Server(new UploadServerHandler());
	    server.start();

	    
	    INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());

	    File file = QAUtil.createTestfile_40k(); 
	  
	    ChannelAsyncWriter hdl = new ChannelAsyncWriter(file.getAbsolutePath(), con);
	    hdl.onWritten(0);
	           
	    
	    int available = 0;
        do {
            QAUtil.sleep(100);
            available = con.available();
        } while (available < file.length());

	    
	    
	    ByteBuffer[] data = con.readByteBufferByLength((int) file.length());
	    Assert.assertTrue(QAUtil.isEquals(file, data));

	    System.out.println("test passed");
	    
	    file.delete();
	    con.close();
	    server.close();
	}
	
    
   
    
    @Test
    public void testClientSideAsyncUploadNonThreaded() throws Exception {
        IServer server = new Server(new UploadServerHandler());
        server.start();

        
        INonBlockingConnection con = new NonBlockingConnection("localhost", server.getLocalPort());

        File file = QAUtil.createTestfile_400k(); 
      
        NonThreadedChannelAsyncWriter hdl = new NonThreadedChannelAsyncWriter(file.getAbsolutePath(), con);
        hdl.onWritten(0);
               
        int available = 0;
        do {
            QAUtil.sleep(100);
            available = con.available();
        } while (available < file.length());

        
        ByteBuffer[] data = con.readByteBufferByLength((int) file.length());
        Assert.assertTrue(QAUtil.isEquals(file, data));

        System.out.println("test passed");
        
        file.delete();
        con.close();
        server.close();
    }
    
    
    
    @Test
    public void testServerSideAsyncDownloadBulk() throws Exception {
        for (int i = 0; i < 20; i++) {
            System.out.println("run " + i);
            testServerSideAsyncDownload();
        }
        System.gc();
    }
    
    
    @Test
    public void testServerSideAsyncDownload() throws Exception {
        IServer server = new Server(new DownloadServerHandler());
        server.start();

        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

        File file = QAUtil.createTestfile_40k(); 

        con.write(file.getAbsolutePath() + "\r\n");
        
        ByteBuffer[] data = con.readByteBufferByLength((int) file.length());
        Assert.assertTrue(QAUtil.isEquals(file, data));

        System.out.println("test passed");
        
        file.delete();
        con.close();
        server.close();
    }
    
    
    @Test
    public void testServerSideAsyncDownloadNonthreaded() throws Exception {
        IServer server = new Server(new NonthreadedDownloadServerHandler());
        server.start();

        
        IBlockingConnection con = new BlockingConnection("localhost", server.getLocalPort());

        File file = QAUtil.createTestfile_400k(); 

        con.write(file.getAbsolutePath() + "\r\n");
        
        ByteBuffer[] data = con.readByteBufferByLength((int) file.length());
        Assert.assertTrue(QAUtil.isEquals(file, data));

        System.out.println("test passed");
        file.delete();
        con.close();
        server.close();
    }
    
    
    
    

	private static final class ChannelAsyncWriter implements IWriteCompletionHandler {

	    private final INonBlockingConnection con;
	    private final RandomAccessFile raf;
	    private final ReadableByteChannel channel;
	         
	    private AtomicBoolean isComplete = new AtomicBoolean(false); 
	    private ByteBuffer transferBuffer = ByteBuffer.allocate(1024);
	    private IOException ioe = null;
	              
	    ChannelAsyncWriter(String filename, INonBlockingConnection con) throws IOException {
	       this.con = con;
	       raf = new RandomAccessFile(filename, "r");
	       channel = raf.getChannel();
	       con.setFlushmode(FlushMode.ASYNC);
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

	
	@Execution(Execution.NONTHREADED)
	private static final class NonThreadedChannelAsyncWriter implements IWriteCompletionHandler {

        private final INonBlockingConnection con;
        private final ReadableByteChannel channel;
             
        private AtomicBoolean isComplete = new AtomicBoolean(false); 
        private ByteBuffer transferBuffer = ByteBuffer.allocate(4096);
        private IOException ioe = null;
                  
        NonThreadedChannelAsyncWriter(String filename, INonBlockingConnection con) throws IOException {
           this.con = con;
           channel = new RandomAccessFile(filename, "r").getChannel();
           con.setFlushmode(FlushMode.ASYNC);
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
	
	
	
	
	
	private static class UploadServerHandler implements IDataHandler {

		public boolean onData(INonBlockingConnection connection) throws IOException {
			connection.write(connection.readByteBufferByLength(connection.available()));
			return true;
		}
	}
	
	
	private static class DownloadServerHandler implements IDataHandler {

	    public boolean onData(INonBlockingConnection connection) throws IOException {
	        String filename = connection.readStringByDelimiter("\r\n");
	        
	        ChannelAsyncWriter hdl = new ChannelAsyncWriter(filename, connection);
	        hdl.onWritten(0);
	               
	        return true;
	    }
	}
	
	private static class NonthreadedDownloadServerHandler implements IDataHandler {

        public boolean onData(INonBlockingConnection connection) throws IOException {
            String filename = connection.readStringByDelimiter("\r\n");
            
            NonThreadedChannelAsyncWriter hdl = new NonThreadedChannelAsyncWriter(filename, connection);
            hdl.onWritten(0);
                   
            return true;
        }
    }
}
