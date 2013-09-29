package file;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;

public class FileUpDownServer {
	private static final String FOLDER = "E:\\temp\\image\\";
	
	public static void main(String[] args) throws UnknownHostException, IOException {
		IServer server = new Server(8989, new FileUpDownHandler());
		server.start();
	}

	
	private static class FileUpDownHandler implements IConnectHandler, IDataHandler {

		@Override
		public boolean onData(INonBlockingConnection con)
				throws IOException, BufferUnderflowException,
				ClosedChannelException, MaxReadSizeExceededException {
			
			// 从ReadQueue中读取数据
			String msg = con.readStringByDelimiter("\r\n");
			System.out.println("receive msg : " + msg);
			
			if (msg.startsWith("get")) {
				System.out.println("start to download...");
				// 下面文件的命令格式：down;文件名称;存放路径
				String[] arr = msg.split(";");
				if (arr.length != 2) {
					System.out.println("usage : down;文件名称 ");
				}
				
				String fileName = arr[1];
				String fullName = FOLDER + fileName;						// 要下载的文件
				File file = new File(fullName);
				
				RandomAccessFile from = new RandomAccessFile(fullName, "r");
				if(!file.exists()) {
					System.out.println("file not exists");
				}
				
				FileChannel fc = from.getChannel();
				con.write(fc.size());					// 先写入文件大小
				long writer = con.transferFrom(fc);		// 再写入文件的内容
				System.out.println(writer + " bytes read.");
				con.flush();
				
				fc.close();
				from.close();
			} else {
				System.out.println("unknow cmd");
			}
			
			return false;
		}

		@Override
		public boolean onConnect(INonBlockingConnection connection)
				throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			
			System.out.println("a new connection");
			
			return false;
		}
		
	}
}
