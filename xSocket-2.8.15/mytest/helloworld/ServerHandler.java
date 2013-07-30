package helloworld;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.connection.AbstractNonBlockingStream;
import org.xsocket.connection.IConnectHandler;
import org.xsocket.connection.IConnectionTimeoutHandler;
import org.xsocket.connection.IDataHandler;
import org.xsocket.connection.IDisconnectHandler;
import org.xsocket.connection.IIdleTimeoutHandler;
import org.xsocket.connection.INonBlockingConnection;

/**
 * 服务端定义数据的处理类.		</br>
 * 
 * 写事件完全是由服务器端控制.
 * 可调用INonBlockingConnection.write();INonBlockingConnection.flush();
 * 来注册OP_WRITE事件执行向客户端写的操作
 */
public class ServerHandler implements IDataHandler, IConnectHandler,
		IIdleTimeoutHandler, IConnectionTimeoutHandler, IDisconnectHandler {

	/**
	 * 连接逻辑. 
	 * 这里返回true/false没有区别.
	 */
	@Override
	public boolean onConnect(INonBlockingConnection nbc) throws IOException,
			BufferUnderflowException, MaxReadSizeExceededException {
		InetAddress address = nbc.getRemoteAddress();
		System.out.println("a new connection : " + address);
		return true;
	}

	@Override
	public boolean onDisconnect(INonBlockingConnection nbc) throws IOException {
		return false;
	}

	/**
	 * 读/写的操作都在 {@link AbstractNonBlockingStream } 类中实现.
	 */
	@Override
	public boolean onData(INonBlockingConnection nbc) throws IOException,
			BufferUnderflowException, ClosedChannelException,
			MaxReadSizeExceededException {
		// readByte() : 读到一个字节就进行打印
//		char data = (char) nbc.readByte();
		
		// readInt()：读4个字节
		// 如果客户端发送的字节为5个,那么会出现BufferUnderflowException异常,不过会被xSocket吞掉
		// 其实这个时候readInt()方法相当于使用了1次读取4个字节作为分隔符.
		// xSocket会对接收到的数据做4个字节的拆分,然后读取.
//		int data = nbc.readInt();
		
		// readStringByDelimiter：指定分隔符
		// AbstractNonBlockingStream
		// 如果没有找到指定的分隔符,会抛出BufferUnderflowException异常
		// 不过xSocket会吞掉这个异常
		String data = nbc.readStringByDelimiter("\r\n");
		
		System.out.println("从客户端接收到 : " + data);
	
		// 设置为自动刷新
//		nbc.setAutoflush(true);
		// 多次调用,那么xSocket会将每次调用的write()里的数据作为一个ByteBuffer存储
		// 在实际写入的时候会将它们合并
		nbc.write("hello\r\n");
//		nbc.write("world");
		// 触发OP_WRITE事件
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
