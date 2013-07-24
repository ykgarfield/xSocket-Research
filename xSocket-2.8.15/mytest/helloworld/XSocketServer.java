package helloworld;

import log.JdkLogInit;

import org.xsocket.connection.IConnection.FlushMode;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;

public class XSocketServer {
	private static final int PORT = 1234;
	public static void main(String[] args) throws Exception {
		JdkLogInit.init();
		
		// 创建一个服务端的对象 
		IServer srv = new Server(PORT, new ServerHandler());
		// 设置当前的采用的异步模式
		srv.setFlushmode(FlushMode.ASYNC);
		//srv.setConnectionTimeoutMillis(3 * 1000);
		try {
			srv.start(); 
			System.out.println("服务器" + srv.getLocalAddress() + ":" + PORT);
			System.out.println("日志: " + srv.getStartUpLogMessage());
		} catch (Exception e) {
			System.out.println(e);
		}
	}

}
