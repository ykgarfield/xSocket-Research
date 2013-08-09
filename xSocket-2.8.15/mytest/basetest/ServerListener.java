package basetest;

import java.io.IOException;
import java.nio.BufferUnderflowException;


import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.IServerListener;
import org.xsocket.connection.Server;

/**
 * 增加一个IServerListener
 */
public class ServerListener {
	private static final int PORT = 1234;
	public static void main(String[] args) throws Exception {
		IServer srv = new Server(PORT, new ServerHandler());
		srv.addListener(new MyServerListener());
		
		try {
			srv.start(); 
			System.out.println("服务器" + srv.getLocalAddress() + ":" + PORT);
			System.out.println("日志: " + srv.getStartUpLogMessage());
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	
	private static class MyServerListener implements IServerListener {
		@Override
		public void onInit() {
			System.out.println("服务器启动了...");
		}

		@Override
		public void onDestroy() throws IOException {
			
		}
	}
	
	
	private static class ServerHandler implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			return false;
		}
	}
	
}
