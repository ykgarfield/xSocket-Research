package file;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.AbstractNonBlockingStream;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;
import org.xsocket.connection.IConnection.FlushMode;

public class GetFileContent {
	public static void main(String[] args) throws UnknownHostException, IOException {
		IServer server = new Server(8989, new FileServerHandler());
		server.setFlushmode(FlushMode.ASYNC);
		server.start();
	}
	
	private static class FileServerHandler implements IConnectHandler, IDataHandler {
		private static final String PATH = "D:\\Work\\";

		/**
		 * <pre>
		 * {@link AbstractNonBlockingStream#transferFrom(FileChannel)} 直接将文件的内容传输给客户端.
		 * 如果是异步方式的话,那么最终调用的是下面的方法：
		 * {@link AbstractNonBlockingStream#transferTo(java.nio.channels.WritableByteChannel, int)}
		 * 可以看到这个方法调用了WritableByteChannel.write(ByteBuffer)方法,而AbstractNonBlockingStream实现了WritableByteChannel接口.
		 * 那么就覆盖了write(ByteBuffer)方法,将数据追加到了WriteQueue中.
		 * </pre>
		 */
		@Override
		public boolean onData(INonBlockingConnection con) throws IOException, BufferUnderflowException,
				ClosedChannelException, MaxReadSizeExceededException {
			String fileName = con.readStringByDelimiter("\r\n");
			String name = PATH + fileName; 
			System.out.println("read from " + name);
			RandomAccessFile file = new RandomAccessFile(name, "r");
			FileChannel fc = file.getChannel();
			
			// 直接将文件内容传输给客户端.
			long size = con.transferFrom(fc);
			
			System.out.println("transfer " + size + " bytes. ");
			con.flush();
			return false;
		}

		@Override
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("a new Connection");
			
			return false;
		}
		
	}
}

