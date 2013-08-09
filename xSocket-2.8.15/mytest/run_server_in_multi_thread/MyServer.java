package run_server_in_multi_thread;

import java.io.IOException;
import java.net.UnknownHostException;

import org.xsocket.connection.IHandler;
import org.xsocket.connection.Server;

public class MyServer extends Server {
	public static ThreadLocal<String> serverInThreadName = RunServerInMultiThread.ServerThread.serverInThreadName;
	
	public MyServer(int port, IHandler handler) throws UnknownHostException, IOException {
		super(port, handler);
	}

	
	@Override
	public void run() {
		super.run();
	}
}

