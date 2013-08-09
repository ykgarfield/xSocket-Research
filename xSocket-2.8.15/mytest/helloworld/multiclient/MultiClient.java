package helloworld.multiclient;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class MultiClient {
	public static void main(String[] args) throws UnknownHostException, IOException {
		int size = 1000;
		if(args.length == 1) {
			size = Integer.parseInt(args[0]);
		}
		
		for (int i = 0; i < size; i++) {
			// java.net.BindException: Address already in use: connect
			Socket socket = new Socket("localhost", 1234);
			socket.setSoLinger(true, 0);
			socket.close();
		}
	}
}

