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
	private static final String FOLDER = "D:\\我的\\图片\\猫\\";
	
	public static void main(String[] args) throws UnknownHostException, IOException {
		IServer server = new Server(8989, new FileUpDownHandler());
		server.start();
	}

	
	private static class FileUpDownHandler implements IConnectHandler, IDataHandler {

		@Override
		public boolean onData(INonBlockingConnection connection)
				throws IOException, BufferUnderflowException,
				ClosedChannelException, MaxReadSizeExceededException {
			
			String msg = connection.readStringByDelimiter("\r\n");
			System.out.println("receive msg : " + msg);
			if (msg.startsWith("down")) {
				System.out.println("start to down...");
				// 下面文件的命令格式：down;文件名称;存放路径
				String[] arr = msg.split(";");
				if (arr.length != 3) {
					System.out.println("usage : down;文件名称;存放路径 ");
				}
				
				String fileName = arr[1];
				String savePath = arr[2];
				String fullName = FOLDER + fileName;
				String saveFullName = savePath + File.separator + fileName;
				File file = new File(fullName);
				
				RandomAccessFile from = new RandomAccessFile(fullName, "r");
				RandomAccessFile to = new RandomAccessFile(saveFullName, "rw");
				if(!file.exists()) {
					System.out.println("file not exists");
				}
				
				System.out.println(fullName + " down to " + saveFullName);
				
				FileChannel fc = from.getChannel();
				int writer = (int) connection.transferFrom(fc);
				System.out.println(writer + " bytes read");
				
				FileChannel toFc = to.getChannel();
				connection.transferTo(toFc, writer);
				
				fc.close();
				from.close();
				toFc.close();
				to.close();
				
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
