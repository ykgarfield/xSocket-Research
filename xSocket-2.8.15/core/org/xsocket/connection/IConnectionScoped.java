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




/**
 * Defines that the {@link IHandler} is connection scoped. 
 * A connection scoped handler will be used by the server 
 * as a prototype. That means, for each new incoming connection
 * this handler will be cloned and the cloned handler will be assigned 
 * to the new connection to perform the handling. <br> 
 * The prototype handler will never be used to handle 
 * connections. It will only be used as a clone base. <br><br>
 * 
 * By having such a dedicate handler instance for each connection,
 * all variables of the cloned handler become session specific
 * variables. <br><br>  
 * 
 * Take care by implementing the clone interface. Just calling the 
 * super.clone() method within the handler clone method lead to 
 * a shallow copy. That means all fields that refer to other 
 * objects will point to the same objects in both the original and 
 * the clone. To avoid side effects a deep copy has to be implemented. 
 * All attributes beside primitives, immutable or global manager/service 
 * references has also to be cloned
 * <br>
 * E.g. 
 * <pre>
 *
 *   class MyHandler implements IDataHandler, IConnectionScoped {
 *      private int state = 0;
 *      private ConnectionRecordQueue recordQueue = new ConnectionRecordQueue();
 *      ...
 *   
 *   
 *      public boolean onData(INonBlockingConnection connection) throws IOException, BufferUnderflowException {
 *         ...
 *         return true;
 *      }   
 *      
 *      
 *      &#064Override
 *      public Object clone() throws CloneNotSupportedException {
 *          MyHandler copy = (MyHandler) super.clone();
 *          copy.recordQueue = new ConnectionRecordQueue(); // deep "clone"
 *          return copy;
 *      }
 *   }
 * </pre> 

 * 
 * @author grro@xsocket.org
 */
public interface IConnectionScoped extends Cloneable {
	
	/**
	 * {@inheritDoc}
	 */
	Object clone() throws CloneNotSupportedException;
}
