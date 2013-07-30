package serializableObj;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnection.FlushMode;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;

public class XSocketServer {
	private static final int PORT = 1234;
	public static void main(String[] args) throws Exception {
		IServer srv = new Server(PORT, new ServerHandler());
		srv.setFlushmode(FlushMode.ASYNC);
		
		try {
			srv.start(); 
			System.out.println("服务器" + srv.getLocalAddress() + ":" + PORT);
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	private static class ServerHandler implements IDataHandler {

		@Override
		public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
				ClosedChannelException, MaxReadSizeExceededException {
			String rec = connection.readStringByDelimiter("\r\n");
			System.out.println("receive : " + rec);
			
			Msg msg = new Msg(12345678, "Tom", "You are welcome");
			
			// 序列化对象
			byte[] data = SerializableUtil.serialize(msg);
	        
			// 先写入要发送的字节的数目
	        connection.write(data.length);
	        connection.write(data);
	        connection.flush();
	        
			return false;
		}
		
	}
}
