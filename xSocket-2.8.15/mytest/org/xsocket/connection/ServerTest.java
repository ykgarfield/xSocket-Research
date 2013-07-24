package org.xsocket.connection;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.xsocket.MaxReadSizeExceededException;

public class ServerTest {

	/**
	 * Socket选项设置的参数值类型转换异常.	</br>
	 * java.lang.ClassCastException: java.lang.String cannot be cast to java.lang.Integer 	</br>
	 * 
	 * {@link IoAcceptor#setOption(String, Object)}	应该进行类型判断,并给出更好的错误提示.		</br>
	 */
	@Test
	public void setOption() throws UnknownHostException, IOException {
		Map<String, Object> options = new HashMap<String, Object>();
		options.put(IConnection.SO_RCVBUF, "test");
		
		new Server(8989, options, new SimpleServerHandler());
	}
	
	class SimpleServerHandler implements IDataHandler {

		@Override
		public boolean onData(INonBlockingConnection connection)
				throws IOException, BufferUnderflowException,
				ClosedChannelException, MaxReadSizeExceededException {
			return false;
		}
		
	}
	
}
