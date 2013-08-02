package org.xsocket.connection;

import java.io.IOException;
import java.net.UnknownHostException;

import helloworld.ServerHandler;

public class CustomerServerStartTimeout {
	public static void main(String[] args) throws UnknownHostException, IOException {
		IServer srv = new Server(8989, new ServerHandler());
		// 设定为0秒启动好,会抛出异常,但是程序仍然能够正常执行
		ConnectionUtils.start(srv, 0); 
	}
}

/**
Exception in thread "main" java.net.SocketTimeoutException: start timeout (0 millis)
	at org.xsocket.connection.ConnectionUtils.start(ConnectionUtils.java:247)
	at org.xsocket.connection.CustomerServerStartTimeout.main(CustomerServerStartTimeout.java:11)
2013-8-2 15:58:21 org.xsocket.connection.Server$LifeCycleHandler onConnected
信息: server listening on 0.0.0.0:8989 (xSocket 2.8.15) 
 */

