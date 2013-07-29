package system.prop;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

public class SystemPropertyLoad {
	@SuppressWarnings("unchecked")
	public static void loadSystemProperty() {
		Properties props = new Properties();
		
		InputStream in = SystemPropertyLoad.class.getResourceAsStream("system_prop.properties");
		try {
			props.load(in);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (props != null && props.size() > 0) {
			Enumeration<String> keys = (Enumeration<String>) props.propertyNames();
			while (keys.hasMoreElements()) {
				String key = keys.nextElement();
				String value = (String) props.get(key);
				System.setProperty(key, value);
			}
			
			// 直接使用下面的代码有问题,慎用
			//System.setProperties(props);
		}
	}

}
