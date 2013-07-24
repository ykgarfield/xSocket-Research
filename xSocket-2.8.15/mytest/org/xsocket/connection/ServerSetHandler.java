package org.xsocket.connection;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;

import org.xsocket.MaxReadSizeExceededException;

public class ServerSetHandler {
	public static void main(String[] args) throws UnknownHostException, IOException {
		Server srv = new Server(1234, new ServerHandler1());
		srv.start();
		srv.setHandler(new ServerHandler2());
		// 当有客户端连接时打印ServerHandler2
	}
	
	private static class ServerHandler1 implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection)
				throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("ServerHandler1");
			return false;
		}
	}
	
	private static class ServerHandler2 implements IConnectHandler {

		@Override
		public boolean onConnect(INonBlockingConnection connection)
				throws IOException, BufferUnderflowException,
				MaxReadSizeExceededException {
			System.out.println("ServerHandler2");
			return false;
		}
	}
}
