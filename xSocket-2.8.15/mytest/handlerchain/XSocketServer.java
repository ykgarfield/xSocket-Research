package handlerchain;

import java.io.IOException;
import java.nio.BufferUnderflowException;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnection.FlushMode;
import org.xsocket.connection.HandlerChain;
import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.IServer;
import org.xsocket.connection.Server;

/**
 * IHandler链.	</br>
 * {@link HandlerChain}		</br>
 * {@link HandlerChain#onConnect(INonBlockingConnection)}	</br>
 */
public class XSocketServer {
	private static final int PORT = 1234;
	public static void main(String[] args) throws Exception {
		HandlerChain chain = new HandlerChain();
		// 先调用Filter1, 然后调用Filter2, 最后调用Filter3
		// 如果其中一个返回true,那么剩余的将不再被调用
		// 比如Filter2的onConnect()方法返回了true,那么Filter3不会被调用
		chain.addLast(new Filter1());
		chain.addLast(new Filter2());
		chain.addLast(new Filter3());
		
		IServer srv = new Server(PORT, chain);
		// 设置当前的采用的异步模式
		srv.setFlushmode(FlushMode.ASYNC);
		
		try {
			srv.start(); 
			System.out.println("服务器启动, " + srv.getLocalAddress() + ":" + PORT);
		} catch (Exception e) {
			System.out.println(e);
		}
	}

	
	private static class Filter1 implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection)
				throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("==========Filter1==========");
			
			return false;
		}
		
	}
	
	private static class Filter2 implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection)
				throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("==========Filter2==========");
			
			// 返回值true/false决定了Filter3是否会被调用
			return true;
		}
		
	}
	
	
	private static class Filter3 implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection)
				throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("==========Filter3==========");
			
			return false;
		}
		
	}
}
