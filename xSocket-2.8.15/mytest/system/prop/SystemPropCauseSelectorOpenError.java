package system.prop;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.Properties;

/**
 * 覆盖系统属性导致的调用Selector.open()错误.	</br>
 * 主要是java.nio.Bits.unaligned(Bits.java:592).
 * 这里要获取系统属性new sun.security.action.GetPropertyAction("os.arch");
 * 因为没有"os.arch"从而导致的问题
 **/
public class SystemPropCauseSelectorOpenError {

	public static void main(String[] args) {
		Properties props = new Properties();
		props.setProperty("name", "Tom");
		// 覆盖系统属性
		System.setProperties(props);
		System.out.println(System.getProperties());
		
		try {
			Selector.open();	// 出现异常
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

/**
Exception in thread "main" java.lang.ExceptionInInitializerError
	at java.nio.ByteBuffer.allocateDirect(ByteBuffer.java:288)
	at sun.nio.ch.Util.getTemporaryDirectBuffer(Util.java:57)
	at sun.nio.ch.IOUtil.write(IOUtil.java:69)
	at sun.nio.ch.SocketChannelImpl.write(SocketChannelImpl.java:334)
	at sun.nio.ch.PipeImpl$Initializer.run(PipeImpl.java:83)
	at java.security.AccessController.doPrivileged(Native Method)
	at sun.nio.ch.PipeImpl.<init>(PipeImpl.java:122)
	at sun.nio.ch.SelectorProviderImpl.openPipe(SelectorProviderImpl.java:27)
	at java.nio.channels.Pipe.open(Pipe.java:133)
	at sun.nio.ch.WindowsSelectorImpl.<init>(WindowsSelectorImpl.java:105)
	at sun.nio.ch.WindowsSelectorProvider.openSelector(WindowsSelectorProvider.java:26)
	at java.nio.channels.Selector.open(Selector.java:209)
	at system.prop.SystemPropCauseSelectorOpenError.main(SystemPropCauseSelectorOpenError.java:20)
Caused by: java.lang.NullPointerException
	at java.nio.Bits.unaligned(Bits.java:592)
	at java.nio.DirectByteBuffer.<clinit>(DirectByteBuffer.java:33)
	... 13 more
*/
