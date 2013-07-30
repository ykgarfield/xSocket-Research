package serializableObj;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;

public class XSocketClient {
	public static void main(String[] args) throws IOException {
		INonBlockingConnection nbc = new NonBlockingConnection("localhost", 1234, new ClientHandler());
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
	
	private static class ClientHandler implements IDataHandler {

		@Override
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
				ClosedChannelException, MaxReadSizeExceededException {
			
			Msg msg = null;
			// 先读取字节的数目
			int length = connection.readInt();
			// 根据字节数目读取对应的长度的字节
	        byte[] data = connection.readBytesByLength(length);
	        
	        // 反序列化对象
	        msg = (Msg) SerializableUtil.deserialize(data);
	        System.out.println("response：" + msg);
			
			return false;
		}
		
	}
}
