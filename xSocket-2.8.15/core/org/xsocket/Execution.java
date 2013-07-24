/*
 * Copyright (c) xlightweb.org, 2006 - 2010. All rights reserved.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Please refer to the LGPL license at: http://www.gnu.org/copyleft/lesser.txt
 * The latest copy of this software may be found on http://www.xsocket.org/
 */
package org.xsocket;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义了一个方法是否应该在多线程环境中被调用.</br>
 * 这个annotation可以用来声明一个回调方法希望执行的方式.	</br>
 * 比如onConnect或者onData方法.xSocket自由地去一个一个NONTHREADED-标记的方法在多线程模式中.		</br>
 * 如果类的注解和方法的注解不一致会有什么样的结果?
 * </br></br>
 * 
 * Annotation which defines if a method <i>should</i> be called in 
 * multithreaded context (default) or not. This annotation can be used
 * to declare the desired execution modus of a call back method such as 
 * <code>onConnect</code> or <code>onData</code>. xSocket is free to
 * perform a NONTHREADED-annotated method in a MULTITHREADED mode.  
 * 
 * E.g.
 * <pre>
 *   class SmtpProtcolHandler implements IDataHandler, IConnectHandler {
 *      ...
 *
 *      &#064Execution(Execution.NONTHREADED)   // default is multithreaded
 *      public boolean onConnect(INonBlockingConnection connection) throws IOException {
 *          connection.write("220 my smtp server" + LINE_DELIMITER);
 *          return true;
 *      }
 *
 *
 *
 *      public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
 *        ...
 *      }
 *   }
 * </pre>  
 * 
 * @author grro@xsocket.org
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Execution {
	
	public static final int NONTHREADED = 0;
	public static final int MULTITHREADED = 1;
	
	int value() default MULTITHREADED;
}
