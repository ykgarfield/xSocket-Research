package helloworld;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.INonBlockingConnection;

/**
 * 客户端定义数据的处理类
 */
public class ClientHandler implements IDataHandler, IConnectHandler,
		IDisconnectHandler {

	/**
	 * 连接的成功时的操作
	 */
	@Override
	public boolean onConnect(INonBlockingConnection nbc) throws IOException,
			BufferUnderflowException, MaxReadSizeExceededException {
		String remoteName = nbc.getRemoteAddress().getHostName();
		System.out.println("remoteName " + remoteName + " has connected ！");
		return true;
	}

	/**
	 * 连接断开时的操作
	 */
	@Override
	public boolean onDisconnect(INonBlockingConnection nbc) throws IOException {
		return false;
	}

	/**
	 * 接收到数据时候的处理
	 */
	@Override
	public boolean onData(INonBlockingConnection nbc) throws IOException,
			BufferUnderflowException, ClosedChannelException,
			MaxReadSizeExceededException {
		String data = nbc.readStringByDelimiter("|");
		nbc.write("--|Client:receive data from server sucessful| -----");
		nbc.flush();
		System.out.println("receive data : " + data);
		return true;
	}

}
