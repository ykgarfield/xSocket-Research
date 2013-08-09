package run_server_in_multi_thread;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.UUID;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.Server;

/**
 * Server运行在多个线程中
 */
public class RunServerInMultiThread {
	private static final int PORT = 1234;
	public static void main(String[] args) throws Exception {
		MyServer srv = new MyServer(PORT, new ServerHandler());
		
		try {
			System.out.println("服务器" + srv.getLocalAddress() + ":" + PORT);
			new ServerThread(srv).start();
			new ServerThread(srv).start();
			//new ServerThread(srv).start();
			//new ServerThread(srv).start();
		} catch (Exception e) {
			System.out.println(e);
		}
	}
	
	public static class ServerThread extends Thread {
		// 线程标识
		public static ThreadLocal<String> serverInThreadName = new ThreadLocal<String>() {
			@Override
			public String initialValue() {
				String uuid = UUID.randomUUID().toString();
				return uuid;
			}
		};
		
		private Server server;
		public ServerThread(Server server) {
			this.server = server;
		}
		
		@Override
		public void run() {
			try {
				System.out.println("ServerThread：" + serverInThreadName.get());
				server.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	private static class ServerHandler implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("ServerHandler");
			
			return false;
		}
	}
}

