package xscoket.concurrency;

import java.net.Socket;

public class XSocketClient {
	public static void main(String[] args) {
		final String host = "localhost";
		
		int connectionNum = 100;
		if (args.length == 1) {
			connectionNum = Integer.parseInt(args[0]);
		}
		
		for (int i= 0; i < connectionNum; i++) {
			try {
				// java.net.BindException: Address already in use: connect
				Socket socket = new Socket(host, 1234);
				socket.setReuseAddress(true);
				System.out.println("connect : " + socket.getLocalPort());
				socket.close();
			} catch (Exception e) {
				
			}
		}
	}
}

