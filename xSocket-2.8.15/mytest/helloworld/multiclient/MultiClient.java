package helloworld.multiclient;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class MultiClient {
	public static void main(String[] args) throws UnknownHostException, IOException {
		for (int i = 0; i < 5; i++) {
			new Socket("localhost", 1234);
		}
	}
}

