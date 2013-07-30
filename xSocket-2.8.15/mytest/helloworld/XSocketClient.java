package helloworld;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.xsocket.connection.INonBlockingConnection;
import org.xsocket.connection.NonBlockingConnection;

/**
 * 客户端也是先注册OP_READ事件.	</b></br>
 * 
 * IBlockingConnection：这个的话就是不支持事件回调处理机制的！
 * INonBlockingConnection:这个连接支持回调机制
 * 
 *  非阻塞的客户端是能够支持事件处理的方法的。即如果从网络通道中没有取到想要的数据就会自动退出程序
 */
public class XSocketClient {
	private static final int PORT = 1234;

	public static void main(String[] args) throws IOException {
		// 采用非阻塞式的连接
		INonBlockingConnection nbc = new NonBlockingConnection("localhost",
				PORT, new ClientHandler());

		// 采用阻塞式的连接
		// IBlockingConnection bc = new BlockingConnection("localhost", PORT);
		// 一个非阻塞的连接是很容易就变成一个阻塞连接
		//IBlockingConnection bc = new BlockingConnection(nbc);
		
		// 设置编码格式
		nbc.setEncoding("UTF-8");
		// 设置是否自动清空缓存
		nbc.setAutoflush(true);
		
		// 向服务端写数据信息
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String msg = null;
		while ((msg = br.readLine()) != null) {
			// 这里从屏幕上输入换行是不会作为整个消息的字符串的.
			nbc.write(msg + "\r\n");
			
			// 向服务器端发送消息, OP_WRITE事件
			nbc.flush();
			
			if ("bye".equals(msg)) {
				nbc.close();
				System.exit(1);
			}
		}
		
	}

}