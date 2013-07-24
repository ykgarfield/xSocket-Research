package xscoket.concurrency;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import log.JdkLogInit;

import org.python.modules.synchronize;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnection.FlushMode;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;

public class XSocketServer {
	private static int maxConnectionNum = 50000;
	
	private static final int PORT = 1234;
	private static AtomicInteger n = new AtomicInteger(0);
	private static String firstConnectionDate = null;
	private static String lastConnectionDate = null;
	public static void main(String[] args) throws Exception {
		JdkLogInit.init();
		
		// 创建一个服务端的对象
		IServer srv = new Server(PORT, new ServerHandler());
		// 设置当前的采用的异步模式
		srv.setFlushmode(FlushMode.ASYNC);
		try {
			srv.start(); 
			System.out.println("服务器" + srv.getLocalAddress() + ":" + PORT);
		} catch (Exception e) {
			System.out.println(e);
		}
	}
	
	private static class ServerHandler implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection) throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			Date date = new Date();
			
			synchronized (this) {
				if (n.get() == 0) {
					firstConnectionDate = date.toString();
					System.out.println("a new connection " + n.getAndIncrement() + " at " + date);
				} else if(n.get() == maxConnectionNum - 1) {
					lastConnectionDate = date.toString();
					System.out.println("a new connection " + n.getAndIncrement() + " at " + date);
				}
			}
			
			n.getAndIncrement();
			
			return true;
		}
		
	}

}
