package log;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public class JdkLogInit {
	public static void init() {
		LogManager logManager = LogManager.getLogManager();
		InputStream inputStream = JdkLogInit.class.getResourceAsStream("logging.properties");
		
		try {
			// 重新初始化日志属性并重新读取日志配置
			logManager.readConfiguration(inputStream);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
