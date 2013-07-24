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
package org.xsocket.connection;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.ClosedChannelException;

import org.xsocket.MaxReadSizeExceededException;

/**
 * 读取和处理传入的数据.每次当数据可用或者连接关闭的时候这个方法将被调用.
 * 因为这个依赖于底层的tcp协议,它不会预测多少和合适这个方法被调用.
 * 注意：在网络层, 数据可以被分解成若干个TCP片段也可能被组合打包成一个TCP包.	</br></br>
 * 
 * 执行一个写的操作,比如在客户端执行connection.write()并不精确地意味着onData()方法在服务器端发生.
 * 一个解决这个问题的通用模式是使用分隔符或者一个领先的长度的字段去标识一个逻辑部分.
 * xSocket提供了方法来支持这个模式.
 * 比如INonBlockingConnection#readStringByDelimiter(String)方法只返回一个记录,如果整个部分(使用分隔符标识)已经接收到,
 * 如果没有,将抛出BufferUnderflowException异常.与IBlockingConnection对比,INonBlockingConnection读方法总是立即返回
 * 并不会抛出BufferUnderflowException异常.如果DataHander不捕获这个异常,BufferUnderflowException将被xSocket吐掉.
 * 一个通用的模式DataHandler不处理这样的一个异常.		</br></br>
 * 
 * 
 * Reads and processes the incoming data. This method will be called
 * each time when data is available or the connection is closed. Because 
 * this depends on the underlying tcp protocol, it is not predictable 
 * how often and when this method will be call. Please note, that on network level
 * data can be fragmented on several TCP packets as well as data can
 * be bundled into one TCP packet. <br><br>
 *
 * Performing a write operation like <code>connection.write("hello it's me. What I have to say is.")</code>
 * on the client side doesn�t mean that exact one onData call occurs on
 * the server side.  A common pattern to solve this is to identify logical
 * parts by a delimiter or a leading length field.
 * xSocket provides methods to support this pattern. E.g. the {@link INonBlockingConnection#readStringByDelimiter(String)}
 * method only returns a record if the whole part (identified by the delimiter) has
 * been received, or if not, a BufferUnderflowException will be thrown. In
 * contrast to {@link IBlockingConnection}, a {@link INonBlockingConnection} read
 * method always returns immediately and could thrown a BufferUnderflowException.
 * The {@link BufferUnderflowException} will be swallowed by the framework, if
 * the DataHandler doesn�t catch this exception. It is a common pattern
 * not to handle such an exception by the DataHandler.
 *
 * <pre>
 * public final class MyHandler implements IDataHandler, IConnectionScoped {
 *   ...
 *   public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
 *   	...
 *   	// 如果没有找到分隔符,将抛出BufferUnderflowException
 *   	// 如果超过了读的最大大小,将抛出MaxReadSizeExceededException.不处理这个异常将引起服务器关闭底层的连接.
 *   
 *      // BufferUnderflowException will been thrown if delimiter has not been found.
 *      // A MaxReadSizeExceededException will be thrown if the max read size has been exceeded. Not handling this exception causes
 *      // that the server closes the underlying connection
 *      String command = connection.readStringByDelimiter("\r\n", "US-ASCII", 5000);
 *      ...
 *      connection.write(response, "US-ASCII");
 *      return true;
 *   }
 * }
 * </pre>
 *
 * @author grro@xsocket.org
 */
public interface IDataHandler extends IHandler {

	/**
	 * 处理基于给定连接的数据. <br>
	 * processes the incoming data based on the given connection. <br><br>
	 *
	 * 注意：onData()回调方法也会被调用,如果一个连接关闭.在这种情况下,在onData方法中调用isOpen()方法将返回false.
	 * 已经接收的数据读取不会失败.	</br>
	 * 为了察觉一个连接已经关闭.回调方法onDisconnect()方法应该被实现.正确的回调顺序将由xSocket管理.	</br></br>
	 *
	 * Please note, that the <code>onData</code> call back method can also be called
	 * for if an connection will be closed. In this case the <code>isOpen</code> call 
	 * within the <code>onData</code> Method will return false. Reading of already 
	 * received data will not fail. 
	 * To detect if a connection has been closed the callback method <code>onDisconnect</code>
	 * should be implemented. The correct call back order will be managed by the xSocket.
	 *
	 * @param connection the underlying connection
	 * 				底层的连接
	 * 
	 * @return true for positive result of handling, false for negative result of handling.
	 *              The return value will be used by the {@link HandlerChain} to interrupted
	 *              the chaining (if result is true)	</br>
	 *              处理的结果是正数,返回true,负数返回false.返回值将被HandlerChian使用去中断链(如果结果为true) </br></br>
	 *              
	 * @throws IOException If some other I/O error occurs. Throwing this exception causes that the underlying connection will be closed. </br>
	 * 				如果I/O错误发生.抛出这个异常将引起底层连接的关闭. </br></br>
	 * 
 	 * @throws BufferUnderflowException if more incoming data is required to process (
 	 * 				e.g. delimiter hasn't yet received -> readByDelimiter methods or size of the available, 
 	 * 				received data is smaller than the required size -> readByLength). 
 	 * 				The BufferUnderflowException will be swallowed by the framework	</br>
 	 * 				如果没有更多的数据需要处理.比如：(没有接收到分隔符 -> readByDelimiter方法或者可用的大小.
 	 * 				接收的数据小于需要的大小 -> readByLength).
 	 * 				BufferUnderflowException异常将被xSocket吞掉.	</br></br>
 	 * 				
 	 * @throws ClosedChannelException if the connection is closed	</br>
 	 * 				连接已经关闭		</br></br>
 	 * 
 	 * @throws MaxReadSizeExceededException if the max read size has been reached (e.g. by calling method {@link INonBlockingConnection#readStringByDelimiter(String, int)}).
 	 *                                      Throwing this exception causes that the underlying connection will be closed.	</br>
 	 *              已经读取到最大的数目(INonBlockingConnection#readStringByDelimiter(String, int)).
 	 *              抛出这个异常将引起底层的连接被关闭.		</br></br>                        
 	 *                                      
     * @throws RuntimeException if an runtime exception occurs. Throwing this exception causes that the underlying connection will be closed.   </br>  
     * 				运行时异常发生.抛出这个异常将引起底层的连接被关闭
	 */
	boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException, ClosedChannelException, MaxReadSizeExceededException;
}
