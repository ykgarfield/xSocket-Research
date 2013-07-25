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
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xsocket.DataConverter;
import org.xsocket.Execution;
import org.xsocket.ILifeCycle;
import org.xsocket.MaxReadSizeExceededException;
import org.xsocket.SerializedTaskQueue;

/**
* 保存了IHandler实现类和其信息.	</br>
* @author grro@xsocket.org
*/
class HandlerAdapter  {
	
	private static final Logger LOG = Logger.getLogger(HandlerAdapter.class.getName());
	
	// 空处理器
	private static final IHandler NULL_HANDLER = new NullHandler();
	private static final HandlerAdapter NULL_HANDLER_ADAPTER = new HandlerAdapter(NULL_HANDLER, ConnectionUtils.getHandlerInfo(NULL_HANDLER));
	

	private final IHandler handler;
	private final IHandlerInfo handlerInfo;
	

	HandlerAdapter(IHandler handler, IHandlerInfo handlerInfo) {
		this.handler = handler;
		this.handlerInfo = handlerInfo;
	}
	
	
	/**
	 * 根据逻辑处理器返回HandlerAdapter实例
	 */
	static HandlerAdapter newInstance(IHandler handler) {
		if (handler == null) {
			return NULL_HANDLER_ADAPTER;
		} else {
			// IHandler实现类的信息
			IHandlerInfo handlerInfo = ConnectionUtils.getHandlerInfo(handler);
			return new HandlerAdapter(handler, handlerInfo);
		}
	}
	
	
	final IHandler getHandler() {
		return handler;
	}
	
	final IHandlerInfo getHandlerInfo() {
		return handlerInfo;
	}
	
	
	private static String printHandler(Object handler) {
	    return handler.getClass().getName() + "#" + handler.hashCode();
	}
	   
    
    public boolean onConnect(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, final Executor workerpool, boolean isUnsynchronized) throws IOException, BufferUnderflowException, MaxReadSizeExceededException {
    	// 是否实现了IConnectHandler接口
        if (handlerInfo.isConnectHandler()) {
    
            if (handlerInfo.isUnsynchronized() || (!getHandlerInfo().isConnectHandlerMultithreaded() && ConnectionUtils.isDispatcherThread())) { 
                performOnConnect(connection, taskQueue, (IConnectHandler) handler);
                
            } else {
            	// 默认执行这里(一般情况不实现IUnsynchronized接口,且不加Execution注解)
                if (getHandlerInfo().isConnectHandlerMultithreaded()) {
                	// 由线程池执行PerformOnConnectTask任务
                    taskQueue.performMultiThreaded(new PerformOnConnectTask(connection, taskQueue, (IConnectHandler) handler), workerpool);
                
                } else {
                    if (isUnsynchronized) {
                        performOnConnect(connection, taskQueue, (IConnectHandler) handler);
                        
                    } else {
                        taskQueue.performNonThreaded(new PerformOnConnectTask(connection, taskQueue, (IConnectHandler) handler), workerpool);
                    }
                }
            }
            
        } 
        
        return true;
    }
    
    
    /**
     * 执行连接任务
     */
    private static final class PerformOnConnectTask implements Runnable {

    	private final IConnectHandler handler;
        private final INonBlockingConnection connection;
        private final SerializedTaskQueue taskQueue;
        
        
        public PerformOnConnectTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IConnectHandler handler) {
            this.connection = connection;
            this.taskQueue = taskQueue;
            this.handler = handler;
        }
        
        @Override
        public void run() {
            performOnConnect(connection, taskQueue, handler);
        }
    }
    
    
    
    private static boolean performOnConnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IConnectHandler handler) {
        
        try {
        	// XXX 执行真正的连接逻辑
            handler.onConnect(connection);
            
        } catch (MaxReadSizeExceededException mee) {
            LOG.warning("[" + connection.getId() + "] closing connection because max readsize is reached by handling onConnect by appHandler. " + printHandler(handler) + " Reason: " + DataConverter.toString(mee));            
            closeSilence(connection);

        } catch (BufferUnderflowException bue) {
            //  ignore

        } catch (IOException ioe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnect by appHandler. " + printHandler(handler) + " Reason: " + DataConverter.toString(ioe));
            }
            closeSilence(connection);
            
        } catch (Throwable t) {
            LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onConnect of " + printHandler(handler) +  " " + DataConverter.toString(t));
            closeSilence(connection);
        }
        
        return false;
    }
    
    
    
    

    public boolean onData(INonBlockingConnection connection, SerializedTaskQueue taskQueue, Executor workerpool, boolean ignoreException, boolean isUnsynchronized) {
        if (handlerInfo.isDataHandler()) {
            
            if (handlerInfo.isUnsynchronized()) {
                performOnData(connection, taskQueue, ignoreException, (IDataHandler) handler);
            
            } else {
                
            	 if (getHandlerInfo().isDataHandlerMultithreaded()) {
                     taskQueue.performMultiThreaded(new PerformOnDataTask(connection, taskQueue, ignoreException, (IDataHandler) handler), workerpool);
                     
                 } else {
                	 if (isUnsynchronized) {
                		 performOnData(connection, taskQueue, ignoreException, (IDataHandler) handler);
                		 
                	 } else {
                		 taskQueue.performNonThreaded(new PerformOnDataTask(connection, taskQueue, ignoreException, (IDataHandler) handler), workerpool);
                	 }
                 }
            }
            
        } else {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] assigned handler " + printHandler(handler) + " is not a data handler");
            }
        }
        return true;
    }   

	
    private static final class PerformOnDataTask implements Runnable {
    	
    	private final IDataHandler handler;
    	private final INonBlockingConnection connection;
    	private final SerializedTaskQueue taskQueue;
    	private final boolean ignoreException; 
    	
    	public PerformOnDataTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue, boolean ignoreException, IDataHandler handler) {
    		this.connection = connection;
    		this.taskQueue = taskQueue;
    		this.ignoreException = ignoreException;
    		this.handler = handler;
		}
    	
    	
        public void run() {
            performOnData(connection, taskQueue, ignoreException, handler);
        }
        
        @Override
        public String toString() {
            return "PerformOnDataTask#" + hashCode() + " "  + connection.getId();
        }
    }
    

	
	private static void performOnData(INonBlockingConnection connection, SerializedTaskQueue taskQueue, boolean ignoreException, IDataHandler handler) {

        try {
            
            // loop until readQueue is empty or nor processing has been done (readQueue has not been modified)
            while ((connection.available() != 0) && !connection.isReceivingSuspended()) {
                
                if (connection.getHandler() != handler) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("[" + connection.getId() + "] handler " + " replaced by " + connection.getHandler() + ". stop handling data for old handler");
                    }
                    return;
                }
                
                int version = connection.getReadBufferVersion();
                
                // calling onData method of the handler (return value will be ignored)
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] calling onData method of handler " + printHandler(handler));
                }
                
                handler.onData(connection);

                if (version == connection.getReadBufferVersion()) {
                    return;
                }
            }
        
        } catch (MaxReadSizeExceededException mee) {
            if (!ignoreException) {
                LOG.warning("[" + connection.getId() + "] closing connection because max readsize is reached by handling onData by appHandler. " + printHandler(handler) + " Reason: " + DataConverter.toString(mee));            
                closeSilence(connection);
            }

        } catch (BufferUnderflowException bue) {
            // ignore
                        
        } catch (IOException ioe) {
            if (!ignoreException) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling data by appHandler. " + printHandler(handler) + " Reason: " + DataConverter.toString(ioe));
                }
                closeSilence(connection);
            }

        } catch (Throwable t) {
            if (!ignoreException) {
                LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onData of " + printHandler(handler) +  " " + DataConverter.toString(t));
                closeSilence(connection);
            }
        }
        
	}
	


    public boolean onDisconnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue, Executor workerpool, boolean isUnsynchronized) {
        if (handlerInfo.isDisconnectHandler()) {
    
            if (handlerInfo.isUnsynchronized()) { 
                performOnDisconnect(connection, taskQueue, (IDisconnectHandler) handler);
                
            } else {
                if (getHandlerInfo().isDisconnectHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnDisconnectTask(connection, taskQueue, (IDisconnectHandler) handler), workerpool);
                    
                } else {
                	if (isUnsynchronized) {
                		performOnDisconnect(connection, taskQueue, (IDisconnectHandler) handler);
                	} else {
                		taskQueue.performNonThreaded(new PerformOnDisconnectTask(connection, taskQueue, (IDisconnectHandler) handler), workerpool);
                	}
                }
            }
        }
        
        return true;
    }
    
        
    
    private static final class PerformOnDisconnectTask implements Runnable {

    	private final IDisconnectHandler handler;
    	private final INonBlockingConnection connection;
    	private final SerializedTaskQueue taskQueue;
    	
    	
    	public PerformOnDisconnectTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IDisconnectHandler handler) {
    		this.connection = connection;
    		this.taskQueue = taskQueue;
    		this.handler = handler;
		}
    	
    	
        public void run() {
            performOnDisconnect(connection, taskQueue, handler);
        }
        
        @Override
        public String toString() {
            return "PerformOnDisconnectTask#" + hashCode() + " "  + connection.getId();
        }
    }
    

    
    
	
	private static void performOnDisconnect(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IDisconnectHandler handler) {

	    try {
	        
            handler.onDisconnect(connection);
            
            

        } catch (MaxReadSizeExceededException mee) {
            // ignore
            
        } catch (BufferUnderflowException bue) {
            // ignore

        } catch (IOException ioe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] io exception occured while performing onDisconnect multithreaded " + printHandler(handler) + " " + DataConverter.toString(ioe));
            }
            
        } catch (Throwable t) {
            LOG.warning("[" + connection.getId() + "] Error occured by performing onDisconnect off " + printHandler(handler) +  " " + DataConverter.toString(t));
        }
	}
	
	
	

    public boolean onConnectException(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, Executor workerpool, final IOException ioe) throws IOException {

        if (handlerInfo.isConnectExceptionHandler()) {
            
            if (handlerInfo.isUnsynchronized()) { 
                performOnConnectException(connection, taskQueue, ioe, (IConnectExceptionHandler) handler);
                
            } else {
                if (getHandlerInfo().isConnectExceptionHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnConnectExceptionTask(connection, taskQueue, ioe, (IConnectExceptionHandler) handler), workerpool);
                    
                } else {
                    taskQueue.performNonThreaded(new PerformOnConnectExceptionTask(connection, taskQueue, ioe, (IConnectExceptionHandler) handler), workerpool);
                }
            }
        }
        
        return true;
    }

    
    private static final class PerformOnConnectExceptionTask implements Runnable {
        
    	private final IConnectExceptionHandler handler;
        private final INonBlockingConnection connection;
        private final SerializedTaskQueue taskQueue;
        private final IOException ioe;
        
        
        public PerformOnConnectExceptionTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IOException ioe, IConnectExceptionHandler handler) {
            this.connection = connection;
            this.taskQueue = taskQueue;
            this.ioe = ioe;
            this.handler = handler;
        }
        
        
        public void run() {
            
            try {
                
                performOnConnectException(connection, taskQueue, ioe, handler);

            } catch (MaxReadSizeExceededException mee) {
                LOG.warning("[" + connection.getId() + "] closing connection because max readsize is reached by handling onConnectException by appHandler. " + printHandler(handler) + " Reason: " + DataConverter.toString(mee));            
                closeSilence(connection);

            } catch (BufferUnderflowException bue) {
                // ignore
                                
            } catch (IOException e) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection.  An io exception occured while performing onConnectException multithreaded " + printHandler(handler) + " " + DataConverter.toString(e));
                }
                closeSilence(connection);
                
            } catch (Throwable t) {
                LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onConnectionException of " + printHandler(handler) +  " " + t.toString());
                closeSilence(connection);
            }
        }
    }
    

    
	
	
	private static boolean performOnConnectException(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IOException ioe, IConnectExceptionHandler handler) throws IOException {

	    try {
            return handler.onConnectException(connection, ioe);
            
        }  catch (RuntimeException re) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + printHandler(handler) + " Reason: " + re.toString());
            }
            closeSilence(connection);
            throw re;
            
        }  catch (IOException e) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection because an error has been occured by handling onDisconnect by appHandler. " + printHandler(handler) + " Reason: " + ioe.toString());
            }
            closeSilence(connection);
            throw ioe;
        }   
	}
	
	
	    
    public boolean onIdleTimeout(final INonBlockingConnection connection, final SerializedTaskQueue taskQueue, Executor workerpool) throws IOException {
        
        if (handlerInfo.isIdleTimeoutHandler()) {
            
            if (handlerInfo.isUnsynchronized()) { 
                performOnIdleTimeout(connection, taskQueue, (IIdleTimeoutHandler) handler);
                
            } else {            
                if (getHandlerInfo().isIdleTimeoutHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnIdleTimeoutTask(connection, taskQueue, (IIdleTimeoutHandler) handler), workerpool);
                    
                } else {
                    taskQueue.performNonThreaded(new PerformOnIdleTimeoutTask(connection, taskQueue, (IIdleTimeoutHandler) handler), workerpool);
                }
            }
        } else {
            closeSilence(connection);
        }
            
        return true;
    }
    
	
    
    private static final class PerformOnIdleTimeoutTask implements Runnable {
        
    	private final IIdleTimeoutHandler handler;
        private final INonBlockingConnection connection;
        private final SerializedTaskQueue taskQueue;
        
        
        public PerformOnIdleTimeoutTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IIdleTimeoutHandler handler) {
            this.connection = connection;
            this.taskQueue = taskQueue;
            this.handler = handler;
        }
        
        
        public void run() {
            performOnIdleTimeout(connection, taskQueue, handler);
        }
    }
    

    
	
	private static void performOnIdleTimeout(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IIdleTimeoutHandler handler) {
        try {
            boolean isHandled = handler.onIdleTimeout(connection);
            if (!isHandled) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection because idle timeout has been occured and timeout handler returns true)");
                }
                closeSilence(connection);
            }
            
        } catch (MaxReadSizeExceededException mee) {
            LOG.warning("[" + connection.getId() + "] closing connection because max readsize is reached by handling onIdleTimeout by appHandler. " + printHandler(handler) + " Reason: " + DataConverter.toString(mee));            
            closeSilence(connection);

        } catch (BufferUnderflowException bue) {
            // ignore
                                                    
        } catch (IOException ioe) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("[" + connection.getId() + "] closing connection. An IO exception occured while performing onIdleTimeout multithreaded " + printHandler(handler) + " " + ioe.toString());
            }
            closeSilence(connection);
                   
        } catch (Throwable t) {
            LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onIdleTimeout of " + printHandler(handler) +  " " + DataConverter.toString(t));
            closeSilence(connection);
        }
	}
	
	
	

    public boolean onConnectionTimeout(INonBlockingConnection connection, SerializedTaskQueue taskQueue, Executor workerpool) throws IOException {
        
        if (handlerInfo.isConnectionTimeoutHandler()) {
            
            if (handlerInfo.isUnsynchronized()) { 
                performOnConnectionTimeout(connection, taskQueue, (IConnectionTimeoutHandler) handler);
                
            } else {
                if (getHandlerInfo().isConnectionTimeoutHandlerMultithreaded()) {
                    taskQueue.performMultiThreaded(new PerformOnConnectionTimeoutTask(connection, taskQueue, (IConnectionTimeoutHandler) handler), workerpool);
                    
                } else {
                    taskQueue.performNonThreaded(new PerformOnConnectionTimeoutTask(connection, taskQueue, (IConnectionTimeoutHandler) handler), workerpool);
                }
            }
            
        } else {
            closeSilence(connection);
        }
        
        return true;
    }
    
	
    
    private static final class PerformOnConnectionTimeoutTask implements Runnable {
        
    	private final IConnectionTimeoutHandler handler;
        private final INonBlockingConnection connection;
        private final SerializedTaskQueue taskQueue;
        
        
        public PerformOnConnectionTimeoutTask(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IConnectionTimeoutHandler handler) {
            this.connection = connection;
            this.taskQueue = taskQueue;
            this.handler = handler;
        }
        
        
        public void run() {
            performOnConnectionTimeout(connection, taskQueue, handler);
        }
    }

    
    
	private static void performOnConnectionTimeout(INonBlockingConnection connection, SerializedTaskQueue taskQueue, IConnectionTimeoutHandler handler) {
        try {
            boolean isHandled = handler.onConnectionTimeout(connection);
            if (!isHandled) {
                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("[" + connection.getId() + "] closing connection because connection timeout has been occured and timeout handler returns true)");
                }
                closeSilence(connection);
            }

        } catch (MaxReadSizeExceededException mee) {
            LOG.warning("[" + connection.getId() + "] closing connection because max readsize hasbeen reached by handling onConnectionTimeout by appHandler. " + printHandler(handler) + " Reason: " + DataConverter.toString(mee));            
            closeSilence(connection);

        } catch (BufferUnderflowException bue) {
            //  ignore

        } catch (IOException ioe) {
            LOG.warning("[" + connection.getId() + "] closing connection because an error has been occured by handling onConnectionTimeout by appHandler. " + handler + " Reason: " + DataConverter.toString(ioe));
            closeSilence(connection);
            
        } catch (Throwable t) {
            LOG.warning("[" + connection.getId() + "] closing connection. Error occured by performing onConnectionTimeout of " + printHandler(handler) +  " " + DataConverter.toString(t));
            closeSilence(connection);
        }
	}
	
	
	public final void onInit() {
		// 是否实现了ILifeCycle接口
		if (handlerInfo.isLifeCycle()) {
			((ILifeCycle) handler).onInit();
		}		
	}
	
	
	public final void onDestroy() {
		if (handlerInfo.isLifeCycle()) {
			try {
				((ILifeCycle) handler).onDestroy();
			} catch (IOException ioe) {
				if (LOG.isLoggable(Level.FINE)) {
					LOG.fine("exception occured by destroying " + printHandler(handler) + " " + ioe.toString());
				}
			}
		}		
	}
	
	
	HandlerAdapter getConnectionInstance() {
		if (handlerInfo.isConnectionScoped()) {
			try {
				IHandler hdlCopy = (IHandler) ((IConnectionScoped) handler).clone();
				return new HandlerAdapter(hdlCopy, handlerInfo);
			} catch (CloneNotSupportedException cnse) {
				throw new RuntimeException(cnse.toString());
			}
			
		} else {
			return this;
		}
	}
	
	
	private static void closeSilence(INonBlockingConnection connection) {
		try {
			connection.close();
		} catch (Exception e) {
		    // eat and log exception
			if (LOG.isLoggable(Level.FINE)) {
				LOG.fine("error occured by closing connection " + connection + " " + e.toString());
			}
		}
	}

	
	/**
	 * 空的处理器
	 */
	@Execution(Execution.NONTHREADED)
	private static final class NullHandler implements IHandler {
	}
}
