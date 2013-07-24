package helloworld;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;

/**
 * 服务端定义数据的处理类
 */
public class ServerHandler implements IDataHandler, IConnectHandler,
		IIdleTimeoutHandler, IConnectionTimeoutHandler, IDisconnectHandler {

	@Override
	public boolean onConnect(INonBlockingConnection nbc) throws IOException,
			BufferUnderflowException, MaxReadSizeExceededException {
		InetAddress address = nbc.getRemoteAddress();
		System.out.println("a new connection + " + address);
		return true;
	}

	@Override
	public boolean onDisconnect(INonBlockingConnection nbc) throws IOException {
		return false;
	}

	@Override
	public boolean onData(INonBlockingConnection nbc) throws IOException,
			BufferUnderflowException, ClosedChannelException,
			MaxReadSizeExceededException {
		// 读到一个字节就进行打印
//		char data = (char) nbc.readByte();
//		String data = nbc.readStringByDelimiter("|");
		String data = nbc.readStringByDelimiter("\r\n");
		
		System.out.println("从客户端接收到 : " + data);
		
		nbc.write("--|server:receive data from client sucessful| -----");
		nbc.flush();
		return true;
	}

	/**
	 * 请求处理超时的处理事件
	 */
	@Override
	public boolean onIdleTimeout(INonBlockingConnection connection)
			throws IOException {
		return true;
	}

	/**
	 * 连接超时处理事件
	 */
	@Override
	public boolean onConnectionTimeout(INonBlockingConnection connection)
			throws IOException {
		return true;
	}
}
