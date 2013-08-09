package basetest;

import java.io.IOException;
import java.nio.BufferUnderflowException;


import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.Server;

/**
 * 替换handler
 */
public class SetHandlerAfterConstructor {
	private static final int PORT = 1234;
	public static void main(String[] args) throws Exception {
		// 构造函数中设置
		Server srv = new Server(PORT, new ServerHandler1());
		// 调用setHandler方法设置
		srv.setHandler(new ServerHandler12());
		
		try {
			srv.start(); 
			System.out.println("服务器" + srv.getLocalAddress() + ":" + PORT);
			System.out.println("日志: " + srv.getStartUpLogMessage());
		} catch (Exception e) {
			System.out.println(e);
		}
	}
	
	
	private static class ServerHandler1 implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("==========ServerHandler1==========");
			return false;
		}
	}
	
	private static class ServerHandler12 implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("==========ServerHandler2==========");
			
			return false;
		}
	}
	
}
