package file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;

/**
 * 向服务器发命令：上传、下载文件
 */
public class FileUpDownClient {
	private static final int PORT = 8989;

	public static void main(String[] args) throws IOException {
		INonBlockingConnection nbc = new NonBlockingConnection("localhost",
				PORT, new FileUpDownHandler());

		nbc.setEncoding("UTF-8");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String msg = null;
		while ((msg = br.readLine()) != null) {
			nbc.write(msg + "\r\n");
			
			nbc.flush();
			
			if ("bye".equals(msg)) {
				nbc.close();
				System.exit(1);
			}
		}
		
	}

	private static class FileUpDownHandler implements IConnectHandler, IDataHandler {
		private static final String SAVE_PATH = "E:\\temp\\my\\";

		@Override
		public boolean onData(INonBlockingConnection con) throws IOException, BufferUnderflowException,
				ClosedChannelException, MaxReadSizeExceededException {
			int size = con.readInt();						// 先读取文件的大小
			System.out.println("file size : " + size);
			
			// 以时间戳作为文件名称
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhmmss");
			String name = SAVE_PATH + sdf.format(new Date()) + ".jpg";
			RandomAccessFile rcf = new RandomAccessFile(name, "rw");
			FileChannel fc = rcf.getChannel();
			
			long transferSize = con.transferTo(fc, size);	// 往Channel中传输数据, 这里就是写入文件
			System.out.println("transfer " + transferSize + " bytes.");
			fc.close();
			rcf.close();
			
			return false;
		}

		@Override
		public boolean onConnect(INonBlockingConnection con) throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("Connect to " + PORT);
			
			return false;
		}
		
	}
}

