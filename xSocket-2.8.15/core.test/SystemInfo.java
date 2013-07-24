import java.util.Enumeration;
import java.util.Properties;



/**
*
* @author grro@xsocket.org
*/
public class SystemInfo  {

    
    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        Properties sysprops   = System.getProperties();
        Enumeration propnames = sysprops.propertyNames();
        while (propnames.hasMoreElements()) {
            String propname = (String)propnames.nextElement();
            System.out.println(propname + "=" + System.getProperty(propname));
        }
        
        Runtime runtime = Runtime.getRuntime();
        System.out.println("availableProcessors=" + runtime.availableProcessors());
    }
}
