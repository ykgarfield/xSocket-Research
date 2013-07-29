package system.prop;

import java.util.Properties;

import org.junit.Test;

/**
 * 慎用System.setProperties(props);方法.
 */
public class SystemTest {
	
	/**
	 * 查看System.setProperties(props)方法的源码.
	 * 可以看到调用了initProperties(props)方法.这个方法需要保证设置一系列的系统属性.
	 */
	@Test
	public void getSystemProps() {
		// 打印信息属性
		// 这里会打印一堆的系统属性
		System.out.println(System.getProperties());
		
		System.out.println("==================================");
		
		Properties props = new Properties();
		props.setProperty("name", "Tom");
		// 设置系统属性
		System.setProperties(props);
		// 打印信息属性
		// 上面的方法只会打印设置的系统属性.
		// 只打印了：{name=Tom}
		System.out.println(System.getProperties());
	}
}

